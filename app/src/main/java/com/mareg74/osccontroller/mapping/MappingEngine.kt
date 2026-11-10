package com.mareg74.osccontroller.mapping

import com.mareg74.osccontroller.config.AppConfig
import com.mareg74.osccontroller.config.ArgKind
import com.mareg74.osccontroller.config.ControlMapping
import com.mareg74.osccontroller.osc.OscMessage

/** Result of resolving a control event; [updatedMapping] set when Toggle latch changes. */
data class ResolveResult(
    val messages: List<OscMessage> = emptyList(),
    val updatedMapping: ControlMapping? = null,
) {
    /** First message (compat with single-address callers/tests). */
    val message: OscMessage? get() = messages.firstOrNull()

    companion object {
        fun single(message: OscMessage?, updatedMapping: ControlMapping? = null) =
            ResolveResult(listOfNotNull(message), updatedMapping)
    }
}

class MappingEngine {
    fun resolve(
        config: AppConfig,
        control: ControlId,
        value: Float
    ): OscMessage? = resolveId(
        config = config,
        controlId = control.name,
        value = value,
        continuous = control.name.startsWith("AXIS_"),
        bipolarHardware = !isTriggerAxis(control) && control.name.startsWith("AXIS_"),
    )

    /**
     * Resolve by mapping [controlId] string (gamepad enum name or tactile widget id).
     * [continuous] true = fader/axis (always emit float); false = trigger/button.
     * [bipolarHardware] true = stick −1…1 before optional min/max remap.
     */
    fun resolveId(
        config: AppConfig,
        controlId: String,
        value: Float,
        continuous: Boolean,
        bipolarHardware: Boolean = false,
        page: Int? = null,
    ): OscMessage? = resolveResult(
        config = config,
        controlId = controlId,
        value = value,
        continuous = continuous,
        bipolarHardware = bipolarHardware,
        page = page,
    ).message

    fun resolveResult(
        config: AppConfig,
        controlId: String,
        value: Float,
        continuous: Boolean,
        bipolarHardware: Boolean = false,
        page: Int? = null,
    ): ResolveResult {
        val pageIndex = page ?: config.activePage
        val mapping = config.mappings.firstOrNull {
            it.controlId == controlId && it.page == pageIndex
        } ?: return ResolveResult()
        return messageFor(mapping, value, continuous, bipolarHardware)
    }

    private fun messageFor(
        mapping: ControlMapping,
        value: Float,
        continuous: Boolean,
        bipolarHardware: Boolean,
    ): ResolveResult {
        val addrs = mapping.allAddresses()
        if (addrs.isEmpty()) return ResolveResult()
        return when (mapping.argKind) {
            ArgKind.MOMENTARY -> {
                if (continuous) ResolveResult()
                else {
                    val logicalOn = value >= 0.5f
                    val arg = mapping.digitalInt(logicalOn)
                    ResolveResult(addrs.map { OscMessage(it, listOf(arg)) })
                }
            }
            ArgKind.TOGGLE -> {
                if (continuous) return ResolveResult()
                // Release: no OSC.
                if (value < 0.5f) return ResolveResult()
                val nextOn = !mapping.toggleOn
                val updated = mapping.copy(toggleOn = nextOn)
                val arg = updated.digitalInt(nextOn)
                ResolveResult(
                    messages = addrs.map { OscMessage(it, listOf(arg)) },
                    updatedMapping = updated,
                )
            }
            ArgKind.FLOAT -> {
                if (continuous) {
                    val out = remapFloat(
                        value = value,
                        bipolarHardware = bipolarHardware,
                        min = mapping.floatMin,
                        max = mapping.floatMax,
                    )
                    ResolveResult(addrs.map { OscMessage(it, listOf(out)) })
                } else {
                    if (value < 0.5f) ResolveResult()
                    else {
                        val fv = mapping.floatValue.coerceIn(0f, 1f)
                        ResolveResult(addrs.map { OscMessage(it, listOf(fv)) })
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Sticks are bipolar (−1…1); triggers / tactile faders are unipolar (0…1).
         * If [min]/[max] are both set, hardware is remapped into that free range.
         * Otherwise the hardware value is forwarded unchanged (legacy behaviour).
         */
        fun remapAxisFloat(
            control: ControlId,
            hardware: Float,
            min: Float?,
            max: Float?,
        ): Float = remapFloat(
            value = hardware,
            bipolarHardware = !isTriggerAxis(control),
            min = min,
            max = max,
        )

        fun remapFloat(
            value: Float,
            bipolarHardware: Boolean,
            min: Float?,
            max: Float?,
        ): Float {
            if (min == null || max == null) return value
            val t01 = if (bipolarHardware) {
                ((value.coerceIn(-1f, 1f) + 1f) * 0.5f)
            } else {
                value.coerceIn(0f, 1f)
            }
            return min + t01 * (max - min)
        }

        fun isTriggerAxis(control: ControlId): Boolean =
            control == ControlId.AXIS_L2 || control == ControlId.AXIS_R2

        fun defaultAxisMin(control: ControlId): Float =
            if (isTriggerAxis(control)) 0f else -1f

        fun defaultAxisMax(control: ControlId): Float = 1f
    }
}
