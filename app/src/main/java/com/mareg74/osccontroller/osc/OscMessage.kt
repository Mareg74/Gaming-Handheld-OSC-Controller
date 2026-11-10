package com.mareg74.osccontroller.osc

data class OscMessage(val address: String, val args: List<Any> = emptyList())
