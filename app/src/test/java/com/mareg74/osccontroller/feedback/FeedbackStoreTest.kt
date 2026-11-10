package com.mareg74.osccontroller.feedback

import com.mareg74.osccontroller.osc.OscMessage
import org.junit.Assert.*
import org.junit.Test

class FeedbackStoreTest {
    @Test fun lastValueWins() {
        val s = FeedbackStore()
        s.apply(OscMessage("/a", listOf(1)))
        s.apply(OscMessage("/a", listOf(2)))
        assertEquals(2, s.get("/a"))
        assertEquals(0, s.dropCount)
    }

    @Test fun emptyArgsStoresUnit() {
        val s = FeedbackStore()
        s.apply(OscMessage("/bang", emptyList()))
        assertEquals(Unit, s.get("/bang"))
    }

    @Test fun mappedAddressUsesResolumeSibling() {
        val s = FeedbackStore()
        s.apply(OscMessage("/composition/layers/1/clips/1/connected", listOf(1)))
        assertEquals(
            1,
            s.getForMappedAddress("/composition/layers/1/clips/1/connect")
        )
        assertEquals(
            1,
            s.getForMappedAddress("/composition/layers/1/clips/1/connected")
        )
    }

    @Test fun exactMappedAddressWinsOverSibling() {
        val s = FeedbackStore()
        s.apply(OscMessage("/composition/layers/1/clips/1/connected", listOf(0)))
        s.apply(OscMessage("/composition/layers/1/clips/1/connect", listOf(1)))
        assertEquals(1, s.getForMappedAddress("/composition/layers/1/clips/1/connect"))
    }
}
