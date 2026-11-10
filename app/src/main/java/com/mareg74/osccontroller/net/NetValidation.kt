package com.mareg74.osccontroller.net

object NetValidation {
    private val IPV4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
    fun isValidIpv4(ip: String): Boolean {
        if (!IPV4.matches(ip)) return false
        return ip.split(".").all { it.toInt() in 0..255 }
    }
    fun isValidPort(p: Int) = p in 1..65535
}
