package com.mareg74.osccontroller.feedback

import com.mareg74.osccontroller.osc.OscMessage

class FeedbackStore {
    private val values = LinkedHashMap<String, Any>()
    var dropCount: Int = 0
        private set

    fun apply(msg: OscMessage) {
        values[msg.address] = msg.args.firstOrNull() ?: Unit
    }

    fun noteDrop() {
        dropCount++
    }

    fun get(address: String): Any? = values[address]

    /**
     * Resolume often emits a sibling path for state feedback
     * (`…/connect` ↔ `…/connected`, `…/select` ↔ `…/selected`).
     * Prefer the exact mapped address, then the sibling.
     */
    fun getForMappedAddress(address: String): Any? {
        get(address)?.let { return it }
        for (alias in siblingAddresses(address)) {
            get(alias)?.let { return it }
        }
        return null
    }

    fun snapshot(): Map<String, Any> = values.toMap()

    companion object {
        fun siblingAddresses(address: String): List<String> {
            val pairs = listOf("connect" to "connected", "select" to "selected")
            for ((a, b) in pairs) {
                if (address.endsWith("/$a")) return listOf(address.removeSuffix(a) + b)
                if (address.endsWith("/$b")) return listOf(address.removeSuffix(b) + a)
            }
            return emptyList()
        }
    }
}
