package com.mareg74.osccontroller.ndi

import com.mareg74.osccontroller.config.MemoryPrefs
import org.junit.Assert.*
import org.junit.Test

class NdiSessionTest {
    private fun session(fake: FakeNdiReceiver = FakeNdiReceiver()): NdiSession {
        val prefs = NdiPrefs(MemoryPrefs())
        return NdiSession(fake, prefs)
    }

    @Test fun discoverThenConnectPauseResumeDisconnect() {
        val fake = FakeNdiReceiver(
            available = true,
            sources = listOf(NdiSource("PGM", "ndi://pgm")),
        )
        val s = session(fake)
        s.refreshSources()
        assertEquals(1, s.sources.size)
        s.connect(s.sources.first())
        assertEquals(NdiStatus.LIVE, s.status)
        s.pause()
        assertEquals(NdiStatus.PAUSED, s.status)
        s.resume()
        assertEquals(NdiStatus.LIVE, s.status)
        s.disconnect()
        assertEquals(NdiStatus.IDLE, s.status)
        assertFalse(fake.isConnected())
    }

    @Test fun connectLastFromPrefs() {
        val mem = MemoryPrefs()
        val prefs = NdiPrefs(mem)
        prefs.save(NdiPrefsData(lastSourceUrl = "ndi://pgm", lastSourceName = "PGM"))
        val fake = FakeNdiReceiver(sources = listOf(NdiSource("PGM", "ndi://pgm")))
        val s = NdiSession(fake, prefs)
        s.connectLastOrIdle()
        assertEquals(NdiStatus.LIVE, s.status)
        assertEquals("ndi://pgm", s.currentSource?.url)
    }

    @Test fun unavailableSdkSetsError() {
        val s = session(FakeNdiReceiver(available = false))
        s.connect(NdiSource("x", "ndi://x"))
        assertEquals(NdiStatus.ERROR, s.status)
        assertEquals(NdiSession.SDK_UNAVAILABLE_MESSAGE, s.errorMessage)
    }

    @Test fun connectLastOrIdleWithoutSdkShowsErrorOnPageOpen() {
        val s = session(FakeNdiReceiver(available = false))
        s.connectLastOrIdle()
        assertEquals(NdiStatus.ERROR, s.status)
        assertEquals(NdiSession.SDK_UNAVAILABLE_MESSAGE, s.errorMessage)
    }

    @Test fun retryAfterConnectFailureKeepsSource() {
        val source = NdiSource("PGM", "ndi://pgm")
        val fake = FakeNdiReceiver(
            sources = listOf(source),
            connectSucceeds = false,
        )
        val s = session(fake)
        s.connect(source)
        assertEquals(NdiStatus.ERROR, s.status)
        assertEquals(source.url, s.currentSource?.url)
        fake.connectSucceeds = true
        s.retry()
        assertEquals(NdiStatus.LIVE, s.status)
    }

    @Test fun applySettingsWhenSdkUnavailableErrors() {
        val mem = MemoryPrefs()
        val prefs = NdiPrefs(mem)
        val fake = FakeNdiReceiver(
            available = true,
            sources = listOf(NdiSource("PGM", "ndi://pgm")),
        )
        val s = NdiSession(fake, prefs)
        s.connect(NdiSource("PGM", "ndi://pgm"))
        assertEquals(NdiStatus.LIVE, s.status)
        assertEquals(60, prefs.load().settings.maxFps)
        fake.available = false
        s.applySettings(NdiSettings(maxFps = 12, quality = 10))
        assertEquals(NdiStatus.ERROR, s.status)
        assertEquals(NdiSession.SDK_UNAVAILABLE_MESSAGE, s.errorMessage)
        assertEquals(60, prefs.load().settings.maxFps)
        assertEquals(60, s.settings.maxFps)
    }

    @Test fun applySettingsPersistsAndReconnects() {
        val mem = MemoryPrefs()
        val prefs = NdiPrefs(mem)
        val fake = FakeNdiReceiver(sources = listOf(NdiSource("PGM", "ndi://pgm")))
        val s = NdiSession(fake, prefs)
        s.connect(NdiSource("PGM", "ndi://pgm"))
        s.applySettings(NdiSettings(maxFps = 12, quality = 10))
        assertEquals(12, prefs.load().settings.maxFps)
        assertEquals(NdiStatus.LIVE, s.status)
        assertEquals(12, fake.lastConnectedSettings?.maxFps)
    }

    @Test fun twoViewersShareSessionUntilLastRelease() {
        val source = NdiSource("PGM", "ndi://pgm")
        val fake = FakeNdiReceiver(sources = listOf(source))
        val s = session(fake)
        s.acquireViewer()
        s.connect(source)
        assertEquals(NdiStatus.LIVE, s.status)
        s.acquireViewer()
        s.connectLastOrIdle()
        assertEquals(NdiStatus.LIVE, s.status)
        assertTrue(fake.isConnected())
        s.releaseViewer()
        assertEquals(NdiStatus.LIVE, s.status)
        assertTrue(fake.isConnected())
        s.releaseViewer()
        assertEquals(NdiStatus.IDLE, s.status)
        assertFalse(fake.isConnected())
    }
}
