package com.mareg74.osccontroller.ndi

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real NDI SDK receiver (libndi + JNI). Capture runs on a background thread.
 * When [NdiSettings.audioEnabled] is true, planar float audio is interleaved and
 * written to an [AudioTrack]. On JVM unit tests [isAvailable] is false.
 */
class SdkNdiReceiver : NdiReceiver {
    private val lock = Any()
    private var connected = false
    private var listener: ((NdiFrame) -> Unit)? = null
    private var captureThread: Thread? = null
    private val captureRunning = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var audioRate = 0
    private var audioChannels = 0

    override fun isAvailable(): Boolean = available

    override fun discover(): List<NdiSource> {
        if (!available) return emptyList()
        if (!ensureInit()) return emptyList()
        val flat = synchronized(lock) { NdiNative.nativeDiscover(1000) }
        val out = ArrayList<NdiSource>(flat.size / 2)
        var i = 0
        while (i + 1 < flat.size) {
            val name = flat[i]
            val url = flat[i + 1]
            if (name.isNotBlank() || url.isNotBlank()) {
                out.add(NdiSource(name.ifBlank { url }, url))
            }
            i += 2
        }
        return out
    }

    override fun connect(source: NdiSource, settings: NdiSettings): Boolean {
        if (!available) return false
        if (!ensureInit()) return false
        stopCapture()
        val ok = synchronized(lock) {
            NdiNative.nativeDisconnect()
            val s = settings.clamped()
            val success = NdiNative.nativeConnect(
                source.name,
                source.url,
                s.quality,
                s.audioEnabled,
                s.maxWidth,
                s.maxHeight,
                s.maxFps,
            )
            connected = success
            success
        }
        if (ok) startCapture()
        return ok
    }

    override fun pause() {
        if (!available) return
        synchronized(lock) { NdiNative.nativeSetPaused(true) }
        runCatching { audioTrack?.pause() }
    }

    override fun resume() {
        if (!available) return
        synchronized(lock) { NdiNative.nativeSetPaused(false) }
        runCatching { audioTrack?.play() }
    }

    override fun disconnect() {
        stopCapture()
        if (!available) {
            connected = false
            return
        }
        synchronized(lock) {
            NdiNative.nativeDisconnect()
            connected = false
        }
    }

    override fun isConnected(): Boolean = synchronized(lock) {
        connected && (!available || NdiNative.nativeIsConnected())
    }

    override fun setFrameListener(listener: ((NdiFrame) -> Unit)?) {
        this.listener = listener
    }

    private fun ensureInit(): Boolean = synchronized(lock) {
        try {
            NdiNative.nativeInit()
        } catch (_: Throwable) {
            false
        }
    }

    private fun startCapture() {
        if (!captureRunning.compareAndSet(false, true)) return
        captureThread = Thread({
            while (captureRunning.get()) {
                val polled = try {
                    NdiNative.nativePoll()
                } catch (_: Throwable) {
                    null
                }
                if (polled == null || polled.size < 2) {
                    try {
                        Thread.sleep(5)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
                val kind = (polled[0] as? Number)?.toInt() ?: continue
                when (kind) {
                    NdiNative.KIND_VIDEO -> {
                        val raw = polled[1] as? IntArray ?: continue
                        if (raw.size >= 2) {
                            val w = raw[0]
                            val h = raw[1]
                            if (w > 0 && h > 0 && raw.size >= 2 + w * h) {
                                val pixels = IntArray(w * h)
                                System.arraycopy(raw, 2, pixels, 0, w * h)
                                listener?.invoke(NdiFrame(w, h, pixels))
                            }
                        }
                    }
                    NdiNative.KIND_AUDIO -> {
                        val raw = polled[1] as? FloatArray ?: continue
                        if (raw.size >= 2) {
                            playAudio(raw)
                        }
                    }
                }
            }
            releaseAudioTrack()
        }, "ndi-capture").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun playAudio(raw: FloatArray) {
        val rate = raw[0].toInt()
        val srcCh = raw[1].toInt().coerceIn(1, 8)
        if (rate <= 0 || raw.size < 2 + srcCh) return
        // AudioTrack: mono or stereo only — fold extra channels.
        val outCh = if (srcCh >= 2) 2 else 1
        val frames = (raw.size - 2) / srcCh
        if (frames <= 0) return
        val pcm = if (srcCh == outCh) {
            raw
        } else {
            FloatArray(2 + frames * outCh).also { dst ->
                dst[0] = rate.toFloat()
                dst[1] = outCh.toFloat()
                var di = 2
                var si = 2
                repeat(frames) {
                    dst[di++] = raw[si]
                    dst[di++] = raw[si + 1]
                    si += srcCh
                }
            }
        }
        ensureAudioTrack(rate, outCh)
        val track = audioTrack ?: return
        val written = track.write(
            pcm,
            2,
            frames * outCh,
            AudioTrack.WRITE_NON_BLOCKING,
        )
        if (written < 0) {
            releaseAudioTrack()
        }
    }

    private fun ensureAudioTrack(sampleRate: Int, channelCount: Int) {
        if (audioTrack != null && audioRate == sampleRate && audioChannels == channelCount) {
            return
        }
        releaseAudioTrack()
        val channelMask = if (channelCount >= 2) {
            AudioFormat.CHANNEL_OUT_STEREO
        } else {
            AudioFormat.CHANNEL_OUT_MONO
        }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(sampleRate * channelCount * 4 / 10) // ~100ms float frames
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        audioTrack = track
        audioRate = sampleRate
        audioChannels = channelCount
    }

    private fun releaseAudioTrack() {
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
        audioRate = 0
        audioChannels = 0
    }

    private fun stopCapture() {
        captureRunning.set(false)
        captureThread?.interrupt()
        captureThread?.join(500)
        captureThread = null
        releaseAudioTrack()
    }

    companion object {
        private val available: Boolean = try {
            System.loadLibrary("ndi")
            System.loadLibrary("thor_ndi")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}
