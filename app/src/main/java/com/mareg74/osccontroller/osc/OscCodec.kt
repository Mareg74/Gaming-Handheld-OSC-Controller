package com.mareg74.osccontroller.osc

object OscCodec {
    fun encode(msg: OscMessage): ByteArray {
        val out = ArrayList<Byte>()
        fun pad(n: Int) = (4 - (n % 4)) % 4
        fun writeString(s: String) {
            val b = s.toByteArray(Charsets.US_ASCII) + 0
            out += b.toList()
            repeat(pad(b.size)) { out += 0 }
        }
        writeString(msg.address)
        val tags = StringBuilder(",")
        msg.args.forEach {
            when (it) {
                is Int -> tags.append('i')
                is Float -> tags.append('f')
                is String -> tags.append('s')
                is Boolean -> tags.append(if (it) 'T' else 'F')
                else -> error("unsupported arg $it")
            }
        }
        writeString(tags.toString())
        msg.args.forEach {
            when (it) {
                is Int -> {
                    val v = it
                    out += ((v ushr 24) and 0xff).toByte()
                    out += ((v ushr 16) and 0xff).toByte()
                    out += ((v ushr 8) and 0xff).toByte()
                    out += (v and 0xff).toByte()
                }
                is Float -> {
                    val v = java.lang.Float.floatToIntBits(it)
                    out += ((v ushr 24) and 0xff).toByte()
                    out += ((v ushr 16) and 0xff).toByte()
                    out += ((v ushr 8) and 0xff).toByte()
                    out += (v and 0xff).toByte()
                }
                is String -> writeString(it)
                is Boolean -> Unit
            }
        }
        return out.toByteArray()
    }

    /** Decode a single OSC message, or the first message inside a bundle. */
    fun decode(bytes: ByteArray): OscMessage? = decodeAll(bytes).firstOrNull()

    /** Decode one message or every message contained in an OSC bundle (nested OK). */
    fun decodeAll(bytes: ByteArray): List<OscMessage> {
        if (bytes.size < 4) return emptyList()
        return try {
            when {
                bytes[0] == '/'.code.toByte() -> {
                    val msg = decodeMessage(bytes, 0) ?: return emptyList()
                    listOf(msg)
                }
                isBundle(bytes, 0) -> decodeBundle(bytes, 0)
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isBundle(bytes: ByteArray, offset: Int): Boolean {
        if (offset + 8 > bytes.size) return false
        return bytes[offset] == '#'.code.toByte() &&
            bytes[offset + 1] == 'b'.code.toByte() &&
            bytes[offset + 2] == 'u'.code.toByte() &&
            bytes[offset + 3] == 'n'.code.toByte() &&
            bytes[offset + 4] == 'd'.code.toByte() &&
            bytes[offset + 5] == 'l'.code.toByte() &&
            bytes[offset + 6] == 'e'.code.toByte() &&
            bytes[offset + 7] == 0.toByte()
    }

    private fun decodeBundle(bytes: ByteArray, offset: Int): List<OscMessage> {
        var i = offset + 16 // "#bundle\0" + 8-byte timetag
        val out = ArrayList<OscMessage>()
        while (i + 4 <= bytes.size) {
            val size = readInt(bytes, i)
            i += 4
            if (size < 0 || i + size > bytes.size) break
            val element = bytes.copyOfRange(i, i + size)
            i += size
            out += decodeAll(element)
        }
        return out
    }

    private fun decodeMessage(bytes: ByteArray, start: Int): OscMessage? {
        var i = start
        if (i >= bytes.size || bytes[i] != '/'.code.toByte()) return null
        val addressPair = readString(bytes, i)
        val address = addressPair.first
        i = addressPair.second
        val tagsPair = readString(bytes, i)
        val tags = tagsPair.first
        i = tagsPair.second
        if (!tags.startsWith(",")) return null
        val args = ArrayList<Any>()
        for (t in tags.drop(1)) {
            when (t) {
                'i' -> {
                    args += readInt(bytes, i)
                    i += 4
                }
                'f' -> {
                    args += java.lang.Float.intBitsToFloat(readInt(bytes, i))
                    i += 4
                }
                's' -> {
                    val s = readString(bytes, i)
                    args += s.first
                    i = s.second
                }
                'T' -> args += true
                'F' -> args += false
                'N', 'I' -> args += Unit
                else -> return null
            }
        }
        return OscMessage(address, args)
    }

    private fun readString(bytes: ByteArray, start: Int): Pair<String, Int> {
        var i = start
        while (i < bytes.size && bytes[i] != 0.toByte()) i++
        val s = bytes.copyOfRange(start, i).toString(Charsets.US_ASCII)
        i++ // null
        while (i % 4 != 0) i++
        return s to i
    }

    private fun readInt(bytes: ByteArray, i: Int): Int =
        ((bytes[i].toInt() and 0xff) shl 24) or
            ((bytes[i + 1].toInt() and 0xff) shl 16) or
            ((bytes[i + 2].toInt() and 0xff) shl 8) or
            (bytes[i + 3].toInt() and 0xff)
}
