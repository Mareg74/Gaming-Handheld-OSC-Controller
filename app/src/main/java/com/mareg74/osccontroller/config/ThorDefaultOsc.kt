package com.mareg74.osccontroller.config

/**
 * Factory defaults for `/thor/trigger|fader/{n}` addresses used by the Nouveau projet
 * template and by Mapping « + Trigger / + Fader ».
 */
object ThorDefaultOsc {
    const val TRIGGER_PREFIX = "/thor/trigger/"
    const val FADER_PREFIX = "/thor/fader/"

    data class AddSpec(
        val index: Int,
        val address: String,
        val label: String,
        val argKind: ArgKind,
    )

    /** First free positive index for [prefix] among existing OSC addresses. */
    fun firstFreeIndex(addresses: Collection<String>, prefix: String): Int {
        val used = addresses.mapNotNull { raw ->
            val addr = raw.trim()
            if (!addr.startsWith(prefix)) return@mapNotNull null
            addr.removePrefix(prefix).toIntOrNull()?.takeIf { it > 0 }
        }.toSet()
        var n = 1
        while (n in used) n++
        return n
    }

    fun firstFreeIndexInMappings(mappings: Collection<ControlMapping>, prefix: String): Int =
        firstFreeIndex(mappings.flatMap { it.allAddresses() }, prefix)

    /** Defaults for a new tactile widget (label + OSC + mode). */
    fun defaultAddSpec(kind: TactileKind, mappings: Collection<ControlMapping>): AddSpec {
        return when (kind) {
            TactileKind.TRIGGER -> {
                val n = firstFreeIndexInMappings(mappings, TRIGGER_PREFIX)
                AddSpec(
                    index = n,
                    address = "$TRIGGER_PREFIX$n",
                    label = "Trig $n",
                    argKind = ArgKind.MOMENTARY,
                )
            }
            TactileKind.FADER -> {
                val n = firstFreeIndexInMappings(mappings, FADER_PREFIX)
                AddSpec(
                    index = n,
                    address = "$FADER_PREFIX$n",
                    label = "Fader $n",
                    argKind = ArgKind.FLOAT,
                )
            }
        }
    }
}
