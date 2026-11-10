package com.mareg74.osccontroller.config

class MemoryPrefs : KeyValuePrefs {
    private val m = mutableMapOf<String, String>()
    override fun getString(k: String, d: String) = m[k] ?: d
    override fun putString(k: String, v: String) { m[k] = v }
    override fun remove(k: String) { m.remove(k) }
    override fun clear() = m.clear()
}
