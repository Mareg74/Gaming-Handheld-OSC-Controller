package com.mareg74.osccontroller.ui

import org.junit.Assert.*
import org.junit.Test

class MonitorLogSnapshotTest {
    @Test fun snapshotTrafficNewestFirstLimited() {
        val log = MonitorLog(capacity = 100)
        repeat(5) { i -> log.appendOut("/out/$i", listOf(i.toFloat())) }
        repeat(5) { i -> log.appendIn("/in/$i", listOf(i)) }
        val snap = log.snapshotTraffic(4)
        assertEquals(4, snap.size)
        assertEquals("/in/4", snap[0].address)
        assertEquals(MonitorDir.IN, snap[0].dir)
        assertTrue(snap.all { it.address.isNotEmpty() })
    }

    @Test fun lastEntryIdTracksAppends() {
        val log = MonitorLog()
        assertEquals(0L, log.lastEntryId())
        log.appendIn("/a", emptyList())
        assertTrue(log.lastEntryId() > 0L)
    }
}
