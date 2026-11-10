package com.mareg74.osccontroller.ndi

/**
 * JNI surface for [SdkNdiReceiver]. Loaded via `System.loadLibrary("thor_ndi")`.
 * Unavailable on JVM unit tests (no native libs).
 */
internal object NdiNative {
    const val KIND_VIDEO = 1
    const val KIND_AUDIO = 2

    external fun nativeInit(): Boolean
    external fun nativeShutdown()
    /** Flattened [name0, url0, name1, url1, …] */
    external fun nativeDiscover(timeoutMs: Int): Array<String>
    external fun nativeConnect(
        name: String,
        url: String,
        quality: Int,
        audioEnabled: Boolean,
        maxWidth: Int,
        maxHeight: Int,
        maxFps: Int,
    ): Boolean
    external fun nativeDisconnect()
    external fun nativeSetPaused(paused: Boolean)
    external fun nativeIsConnected(): Boolean

    /**
     * `[kind: Integer, payload]` where payload is:
     * - video: [width, height, argb…]
     * - audio: [sampleRate, channels, interleaved float…]
     */
    external fun nativePoll(): Array<Any>?
}
