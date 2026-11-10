package com.mareg74.osccontroller.ndi

class FakeNdiReceiver(
    var available: Boolean = true,
    var connectSucceeds: Boolean = true,
    private var sources: List<NdiSource> = emptyList(),
) : NdiReceiver {
    private var connected: Boolean = false
    private var paused: Boolean = false
    private var listener: ((NdiFrame) -> Unit)? = null
    var lastConnectedSettings: NdiSettings? = null
        private set

    override fun isAvailable() = available
    override fun discover() = sources
    override fun connect(source: NdiSource, settings: NdiSettings): Boolean {
        if (!available || !connectSucceeds) return false
        connected = true
        paused = false
        lastConnectedSettings = settings
        listener?.invoke(
            NdiFrame(16, 9, IntArray(16 * 9) { 0xFF888888.toInt() }),
        )
        return true
    }
    override fun pause() { if (connected) paused = true }
    override fun resume() { if (connected) paused = false }
    override fun disconnect() { connected = false; paused = false }
    override fun isConnected() = connected
    override fun setFrameListener(listener: ((NdiFrame) -> Unit)?) {
        this.listener = listener
    }

    fun setSources(next: List<NdiSource>) { sources = next }
}
