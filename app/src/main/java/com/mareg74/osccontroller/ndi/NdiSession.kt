package com.mareg74.osccontroller.ndi

class NdiSession(
    private val receiver: NdiReceiver,
    private val prefs: NdiPrefs,
) {
    var status: NdiStatus = NdiStatus.IDLE
        private set
    var errorMessage: String? = null
        private set
    var sources: List<NdiSource> = emptyList()
        private set
    var currentSource: NdiSource? = null
        private set
    var lastFrame: NdiFrame? = null
        private set
    var settings: NdiSettings = NdiSettings()
        private set

    private val listeners = mutableSetOf<() -> Unit>()
    /** Active NdiMonitorPage instances (top and/or bottom). */
    private var viewerCount: Int = 0

    init {
        settings = prefs.load().settings
        receiver.setFrameListener { frame ->
            lastFrame = frame
            notifyListeners()
        }
    }

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    /** Call when a monitor page becomes visible. Pair with [releaseViewer]. */
    fun acquireViewer() {
        viewerCount += 1
    }

    /**
     * Call when a monitor page leaves. Disconnects only when no viewers remain
     * so top+bottom can share one source.
     */
    fun releaseViewer() {
        viewerCount = (viewerCount - 1).coerceAtLeast(0)
        if (viewerCount == 0) {
            disconnect()
        }
    }

    fun refreshSources() {
        if (!ensureAvailable()) return
        val prior = status
        status = NdiStatus.DISCOVERING
        notifyListeners()
        sources = receiver.discover()
        status = when (prior) {
            NdiStatus.CONNECTING,
            NdiStatus.LIVE,
            NdiStatus.PAUSED,
            NdiStatus.ERROR,
            -> prior
            else -> NdiStatus.IDLE
        }
        notifyListeners()
    }

    fun connect(source: NdiSource) {
        if (!ensureAvailable()) return
        status = NdiStatus.CONNECTING
        notifyListeners()
        val ok = receiver.connect(source, settings.clamped())
        if (ok) {
            currentSource = source
            status = NdiStatus.LIVE
            errorMessage = null
            persistConnectionPrefs(source)
        } else {
            currentSource = source
            status = NdiStatus.ERROR
            errorMessage = "Connexion NDI échouée"
        }
        notifyListeners()
    }

    fun connectLastOrIdle() {
        if (!ensureAvailable()) return
        // Already streaming for another viewer — keep the shared session.
        if (status == NdiStatus.LIVE ||
            status == NdiStatus.PAUSED ||
            status == NdiStatus.CONNECTING
        ) {
            return
        }
        val data = prefs.load()
        val url = data.lastSourceUrl
        if (url.isNullOrBlank()) {
            status = NdiStatus.IDLE
            notifyListeners()
            return
        }
        val discovered = receiver.discover()
        val source = discovered.find { it.url == url }
            ?: NdiSource(data.lastSourceName ?: url, url)
        connect(source)
    }

    fun pause() {
        if (status != NdiStatus.LIVE) return
        receiver.pause()
        status = NdiStatus.PAUSED
        notifyListeners()
    }

    fun resume() {
        if (status != NdiStatus.PAUSED) return
        receiver.resume()
        status = NdiStatus.LIVE
        notifyListeners()
    }

    /** Force-stop regardless of viewer count (e.g. app background). */
    fun disconnect() {
        viewerCount = 0
        receiver.disconnect()
        currentSource = null
        status = NdiStatus.IDLE
        errorMessage = null
        notifyListeners()
    }

    fun applySettings(s: NdiSettings) {
        val previousSettings = settings
        val data = prefs.load()
        val next = s.clamped()
        settings = next
        val source = currentSource
        if (source != null && receiver.isConnected()) {
            receiver.disconnect()
            if (!receiver.isAvailable()) {
                settings = previousSettings
                status = NdiStatus.ERROR
                errorMessage = SDK_UNAVAILABLE_MESSAGE
                notifyListeners()
                return
            }
            status = NdiStatus.CONNECTING
            notifyListeners()
            val ok = receiver.connect(source, next)
            if (ok) {
                status = NdiStatus.LIVE
                errorMessage = null
                prefs.save(data.copy(settings = next))
                persistConnectionPrefs(source)
            } else {
                settings = previousSettings
                currentSource = source
                status = NdiStatus.ERROR
                errorMessage = "Connexion NDI échouée"
            }
            notifyListeners()
            return
        }
        prefs.save(data.copy(settings = next))
        notifyListeners()
    }

    fun retry() {
        when (status) {
            NdiStatus.ERROR -> {
                val source = currentSource
                if (source != null) {
                    connect(source)
                } else {
                    connectLastOrIdle()
                }
            }
            else -> { /* no-op */ }
        }
    }

    private fun persistConnectionPrefs(source: NdiSource) {
        val data = prefs.load()
        prefs.save(
            data.copy(
                lastSourceUrl = source.url,
                lastSourceName = source.name,
                settings = settings,
            ),
        )
    }

    private fun ensureAvailable(): Boolean {
        if (receiver.isAvailable()) return true
        status = NdiStatus.ERROR
        errorMessage = SDK_UNAVAILABLE_MESSAGE
        notifyListeners()
        return false
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }

    companion object {
        const val SDK_UNAVAILABLE_MESSAGE = "SDK NDI indisponible (plan B non activé)"
    }
}
