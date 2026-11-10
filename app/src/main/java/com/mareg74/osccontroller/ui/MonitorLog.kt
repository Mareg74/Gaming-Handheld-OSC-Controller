package com.mareg74.osccontroller.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class MonitorDir { IN, OUT, ERR }

data class MonitorEntry(
    val id: Long,
    val timeMs: Long,
    val dir: MonitorDir,
    val address: String,
    val argsText: String,
) {
    fun timeLabel(): String = TIME_FMT.format(Date(timeMs))

    fun matches(
        showIn: Boolean,
        showOut: Boolean,
        mappedOnly: Boolean,
        mappedAddresses: Set<String>,
        selectedAddresses: Set<String>,
        contains: String,
    ): Boolean {
        if (dir == MonitorDir.ERR) return true // always visible
        if (dir == MonitorDir.IN && !showIn) return false
        if (dir == MonitorDir.OUT && !showOut) return false
        if (mappedOnly && address.isNotEmpty() && address !in mappedAddresses) return false
        if (selectedAddresses.isNotEmpty() && address !in selectedAddresses) return false
        val q = contains.trim()
        if (q.isNotEmpty()) {
            val hay = "$address $argsText"
            if (!hay.contains(q, ignoreCase = true)) return false
        }
        return true
    }

    companion object {
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}

class MonitorLog(private val capacity: Int = 200) {
    private val buffer = ArrayDeque<MonitorEntry>()
    private val nextId = AtomicLong(1)
    /** Last logged float payload per address (OUT debounce). */
    private val lastOutFloats = HashMap<String, List<Float>>()
    var listenError: String? = null

    @Synchronized
    fun appendOut(address: String, args: List<Any>): Boolean {
        if (shouldDebounceOut(address, args)) return false
        rememberOutFloats(address, args)
        add(
            MonitorEntry(
                id = nextId.getAndIncrement(),
                timeMs = System.currentTimeMillis(),
                dir = MonitorDir.OUT,
                address = address,
                argsText = OscDisplayFormat.formatArgs(args),
            ),
        )
        return true
    }

    @Synchronized
    fun appendIn(address: String, args: List<Any>) {
        add(
            MonitorEntry(
                id = nextId.getAndIncrement(),
                timeMs = System.currentTimeMillis(),
                dir = MonitorDir.IN,
                address = address,
                argsText = OscDisplayFormat.formatArgs(args),
            ),
        )
    }

    @Synchronized
    fun appendError(message: String) {
        add(
            MonitorEntry(
                id = nextId.getAndIncrement(),
                timeMs = System.currentTimeMillis(),
                dir = MonitorDir.ERR,
                address = "",
                argsText = message,
            ),
        )
    }

    /** Legacy string append used by older call sites — routes to ERR or raw OUT. */
    @Synchronized
    fun append(line: String) {
        when {
            line.startsWith("error:") || line.startsWith("rx drop") ->
                appendError(line.removePrefix("error:").trim().ifBlank { line })
            line.startsWith("tx ") -> {
                val rest = line.removePrefix("tx ").trim()
                val sp = rest.indexOf(' ')
                if (sp > 0) appendOut(rest.substring(0, sp), emptyList())
                else appendOut(rest, emptyList())
            }
            line.startsWith("rx ") -> {
                val rest = line.removePrefix("rx ").trim()
                val sp = rest.indexOf(' ')
                if (sp > 0) appendIn(rest.substring(0, sp), emptyList())
                else appendIn(rest, emptyList())
            }
            else -> appendError(line)
        }
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        lastOutFloats.clear()
    }

    @Synchronized
    fun entries(): List<MonitorEntry> = buffer.toList()

    /**
     * Snapshot of the newest [limit] IN/OUT entries that have an OSC address
     * (newest first). Used by Mapping Learn/Copy.
     */
    @Synchronized
    fun snapshotTraffic(limit: Int = 40): List<MonitorEntry> =
        buffer.asReversed()
            .asSequence()
            .filter { it.address.isNotEmpty() && (it.dir == MonitorDir.IN || it.dir == MonitorDir.OUT) }
            .take(limit.coerceAtLeast(0))
            .toList()

    /** Highest entry id currently in the buffer (0 if empty). */
    @Synchronized
    fun lastEntryId(): Long = buffer.lastOrNull()?.id ?: 0L

    /** Back-compat for simple UIs. */
    @Synchronized
    fun lines(): List<String> = buffer.map { e ->
        val tag = when (e.dir) {
            MonitorDir.IN -> "IN"
            MonitorDir.OUT -> "OUT"
            MonitorDir.ERR -> "ERR"
        }
        val addr = if (e.address.isNotEmpty()) " ${e.address}" else ""
        "${e.timeLabel()} $tag$addr ${e.argsText}".trimEnd()
    }

    private fun add(entry: MonitorEntry) {
        if (buffer.size >= capacity) buffer.removeFirst()
        buffer.addLast(entry)
    }

    private fun shouldDebounceOut(address: String, args: List<Any>): Boolean {
        if (args.isEmpty()) return false
        if (args.any { it !is Float && it !is Double }) return false
        val floats = args.map {
            when (it) {
                is Float -> it
                is Double -> it.toFloat()
                else -> return false
            }
        }
        val prev = lastOutFloats[address] ?: return false
        if (prev.size != floats.size) return false
        return prev.indices.all { i ->
            OscDisplayFormat.floatNear(prev[i], floats[i])
        }
    }

    private fun rememberOutFloats(address: String, args: List<Any>) {
        if (args.isEmpty() || args.any { it !is Float && it !is Double }) {
            lastOutFloats.remove(address)
            return
        }
        lastOutFloats[address] = args.map {
            when (it) {
                is Float -> it
                is Double -> it.toFloat()
                else -> return
            }
        }
    }
}
