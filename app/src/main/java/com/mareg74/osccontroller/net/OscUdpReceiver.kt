package com.mareg74.osccontroller.net

import android.util.Log
import com.mareg74.osccontroller.feedback.FeedbackStore
import com.mareg74.osccontroller.osc.OscCodec
import com.mareg74.osccontroller.ui.MonitorLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class OscUdpReceiver(
    private val feedback: FeedbackStore,
    private val log: MonitorLog
) {
    companion object {
        private const val TAG = "ThorOscRx"
    }
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var running = false

    fun start(listenPort: Int): Boolean {
        stop()
        if (!NetValidation.isValidPort(listenPort)) {
            log.listenError = "invalid listen port"
            return false
        }
        return try {
            val s = DatagramSocket(listenPort)
            socket = s
            running = true
            log.listenError = null
            thread(name = "osc-udp-rx", isDaemon = true) {
                val buf = ByteArray(65535)
                while (running) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        s.receive(packet)
                        val bytes = packet.data.copyOf(packet.length)
                        val messages = OscCodec.decodeAll(bytes)
                        if (messages.isEmpty()) {
                            feedback.noteDrop()
                            log.appendError("rx drop malformed")
                            val head = bytes.take(16).joinToString(" ") { b -> "%02x".format(b) }
                            Log.i(TAG, "drop malformed from ${packet.address}:${packet.port} (${bytes.size}B) head=$head")
                        } else {
                            for (msg in messages) {
                                feedback.apply(msg)
                                log.appendIn(msg.address, msg.args)
                                Log.i(TAG, "rx ${msg.address} ${msg.args} from ${packet.address}:${packet.port}")
                            }
                        }
                    } catch (_: Exception) {
                        if (running) { /* swallow transient */ }
                    }
                }
            }
            true
        } catch (e: Exception) {
            log.listenError = e.message
            log.appendError("bind ${e.message}")
            false
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
