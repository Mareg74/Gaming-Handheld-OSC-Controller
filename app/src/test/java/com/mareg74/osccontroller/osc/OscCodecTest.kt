package com.mareg74.osccontroller.osc

import org.junit.Assert.*
import org.junit.Test

class OscCodecTest {
    @Test fun encodeDecodeBang() {
        val msg = OscMessage("/composition/columns/1/connect", emptyList())
        val round = OscCodec.decode(OscCodec.encode(msg))
        assertEquals(msg.address, round!!.address)
        assertTrue(round.args.isEmpty())
    }

    @Test fun encodeDecodeFloat() {
        val msg = OscMessage("/composition/layers/1/video/opacity", listOf(0.5f))
        val round = OscCodec.decode(OscCodec.encode(msg))!!
        assertEquals(msg.address, round.address)
        assertEquals(0.5f, round.args[0] as Float, 0.0001f)
    }

    @Test fun decodeMalformedReturnsNull() {
        assertNull(OscCodec.decode(byteArrayOf(1, 2, 3)))
    }

    @Test fun decodeBundleExtractsMessages() {
        val inner = OscCodec.encode(OscMessage("/composition/layers/1/clips/1/connected", listOf(1)))
        val bundle = ArrayList<Byte>()
        fun writeInt(v: Int) {
            bundle += ((v ushr 24) and 0xff).toByte()
            bundle += ((v ushr 16) and 0xff).toByte()
            bundle += ((v ushr 8) and 0xff).toByte()
            bundle += (v and 0xff).toByte()
        }
        bundle += "#bundle".toByteArray(Charsets.US_ASCII).toList()
        bundle += 0
        repeat(8) { bundle += 0 } // timetag (immediate-ish zeros + last byte set in real packets)
        bundle[bundle.size - 1] = 1
        writeInt(inner.size)
        bundle += inner.toList()
        val msgs = OscCodec.decodeAll(bundle.toByteArray())
        assertEquals(1, msgs.size)
        assertEquals("/composition/layers/1/clips/1/connected", msgs[0].address)
        assertEquals(1, msgs[0].args[0])
    }

    @Test fun encodeDecodeBoolTags() {
        val msg = OscMessage("/flag", listOf(true, false))
        val round = OscCodec.decode(OscCodec.encode(msg))!!
        assertEquals(true, round.args[0])
        assertEquals(false, round.args[1])
    }
}
