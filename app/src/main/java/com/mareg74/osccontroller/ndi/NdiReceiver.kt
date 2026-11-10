package com.mareg74.osccontroller.ndi

interface NdiReceiver {
    /** false if native SDK missing / not linked */
    fun isAvailable(): Boolean
    fun discover(): List<NdiSource>
    fun connect(source: NdiSource, settings: NdiSettings): Boolean
    fun pause()
    fun resume()
    fun disconnect()
    fun isConnected(): Boolean
    fun setFrameListener(listener: ((NdiFrame) -> Unit)?)
}
