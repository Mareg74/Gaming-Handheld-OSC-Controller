# NDI Monitor (écran bas) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter la page écran bas « NDI Monitor » (sous Moniteur) avec découverte NDI, réception live via SDK natif (abstraction + fake pour tests), popup réglages bande passante, disconnect au quit/background.

**Architecture:** `NdiPrefs` (SharedPreferences) + `NdiReceiver` (interface) + `FakeNdiReceiver` (tests/fallback) + `SdkNdiReceiver` (JNI NDI) + `NdiSession` (machine d’états) + UI Compose branchée dans `SecondaryScreen` / mono `PrimaryScreen`. Un receiver à la fois. Centre Play hors scope.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, JUnit 4, NDI SDK Android (natif, licence Mareg74), minSdk 26 / compileSdk 34.

**Spec:** `docs/superpowers/specs/2026-09-15-ndi-play-monitor-design.md`

## Global Constraints

- Package root: `com.mareg74.osccontroller`
- Label drawer: **« NDI Monitor »** immédiatement sous **« Moniteur »** (secondary) et sous **« Monitor »** (primary mono)
- Transport: **NDI SDK Android natif** ; plan B JPEG **non implémenté** (message UI seulement si SDK absent)
- Audio défaut: **off**
- Quit page / app background: **disconnect** (pas pause)
- Un seul receiver NDI à la fois
- Persistance: SharedPreferences `thor_osc` (même prefs que `ConfigStore`), clé dédiée NDI
- Pas de centre Play, pas de Settings NDI séparés, pas de multi-sources simultanées
- `JAVA_HOME`: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (si présent)
- Tests unitaires JVM sans libs natives (FakeNdiReceiver)

---

## File Structure (target)

```
app/src/main/java/com/mareg74/osccontroller/
  ndi/
    NdiModels.kt          # NdiStatus, NdiSource, NdiSettings, NdiFrame
    NdiPrefs.kt           # load/save JSON-like line format via KeyValuePrefs
    NdiReceiver.kt        # interface
    FakeNdiReceiver.kt    # deterministic fake for tests + SDK-missing fallback
    SdkNdiReceiver.kt     # wrap native SDK (stub throws / unavailable until libs present)
    NdiSession.kt         # state machine + listeners
  ui/
    NdiMonitorPage.kt     # page + popup + picker
    SecondaryScreen.kt    # + destination
    PrimaryScreen.kt      # + destination mono
  ThorOscApp.kt           # expose ndiSession / ndiPrefs
  MainActivity.kt         # onStop → ndiSession.disconnect()
app/src/test/java/com/mareg74/osccontroller/ndi/
  NdiPrefsTest.kt
  NdiSessionTest.kt
docs/superpowers/notes/2026-09-15-ndi-monitor-manual-smoke.md  # checklist device
```

---

### Task 1: Modèles + NdiPrefs (persist)

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/ndi/NdiModels.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/ndi/NdiPrefs.kt`
- Test: `app/src/test/java/com/mareg74/osccontroller/ndi/NdiPrefsTest.kt`

**Interfaces:**
- Consumes: `com.mareg74.osccontroller.config.KeyValuePrefs`
- Produces:
  - `enum class NdiStatus { IDLE, DISCOVERING, CONNECTING, LIVE, PAUSED, ERROR }`
  - `data class NdiSource(val name: String, val url: String)`
  - `data class NdiSettings(val quality: Int = 50, val maxWidth: Int = 1280, val maxHeight: Int = 720, val maxFps: Int = 30, val audioEnabled: Boolean = false)`
  - `data class NdiPrefsData(val lastSourceUrl: String? = null, val lastSourceName: String? = null, val settings: NdiSettings = NdiSettings())`
  - `class NdiPrefs(prefs: KeyValuePrefs)` with `load(): NdiPrefsData`, `save(data: NdiPrefsData)`
  - Format prefs: single string key `ndi_prefs_v1`, lines `k=v` (`sourceUrl=`, `sourceName=`, `quality=`, `maxWidth=`, `maxHeight=`, `maxFps=`, `audio=0|1`). Clamp quality 0…100, maxFps 1…60, sizes ≥ 160. Default audio `0`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mareg74.osccontroller.ndi

import com.mareg74.osccontroller.config.MemoryPrefs
import org.junit.Assert.*
import org.junit.Test

// If MemoryPrefs is package-private to config tests, duplicate a tiny MemoryPrefs
// in this file OR move MemoryPrefs to config/MemoryPrefs.kt production-test helper.
// Prefer: copy the same MemoryPrefs class used in ConfigStoreTest into this test file.

class NdiPrefsTest {
    @Test fun defaultsThenRoundTrip() {
        val prefs = MemoryPrefs()
        val store = NdiPrefs(prefs)
        val d = store.load()
        assertNull(d.lastSourceUrl)
        assertEquals(50, d.settings.quality)
        assertEquals(1280, d.settings.maxWidth)
        assertEquals(720, d.settings.maxHeight)
        assertEquals(30, d.settings.maxFps)
        assertFalse(d.settings.audioEnabled)

        store.save(
            NdiPrefsData(
                lastSourceUrl = "ndi://PC (PGM)",
                lastSourceName = "PC (PGM)",
                settings = NdiSettings(quality = 25, maxWidth = 960, maxHeight = 540, maxFps = 15, audioEnabled = true),
            ),
        )
        val loaded = store.load()
        assertEquals("ndi://PC (PGM)", loaded.lastSourceUrl)
        assertEquals("PC (PGM)", loaded.lastSourceName)
        assertEquals(25, loaded.settings.quality)
        assertEquals(960, loaded.settings.maxWidth)
        assertEquals(540, loaded.settings.maxHeight)
        assertEquals(15, loaded.settings.maxFps)
        assertTrue(loaded.settings.audioEnabled)
    }

    @Test fun clampsOutOfRange() {
        val store = NdiPrefs(MemoryPrefs())
        store.save(
            NdiPrefsData(
                settings = NdiSettings(quality = 999, maxWidth = 10, maxHeight = 10, maxFps = 0, audioEnabled = false),
            ),
        )
        val s = store.load().settings
        assertEquals(100, s.quality)
        assertEquals(160, s.maxWidth)
        assertEquals(160, s.maxHeight)
        assertEquals(1, s.maxFps)
    }
}
```

Note: `MemoryPrefs` vit aujourd’hui dans `ConfigStoreTest.kt`. **Avant le test**, extraire `MemoryPrefs` vers `app/src/test/java/com/mareg74/osccontroller/config/MemoryPrefs.kt` (même package `config`) pour réutilisation — changement de test only, pas de prod.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/greg/THOR-OSC-Controller
./gradlew :app:testDebugUnitTest --tests com.mareg74.osccontroller.ndi.NdiPrefsTest
```

Expected: FAIL (class `NdiPrefs` missing)

- [ ] **Step 3: Write minimal implementation**

`NdiModels.kt`:
```kotlin
package com.mareg74.osccontroller.ndi

enum class NdiStatus { IDLE, DISCOVERING, CONNECTING, LIVE, PAUSED, ERROR }

data class NdiSource(val name: String, val url: String)

data class NdiSettings(
    val quality: Int = 50,
    val maxWidth: Int = 1280,
    val maxHeight: Int = 720,
    val maxFps: Int = 30,
    val audioEnabled: Boolean = false,
)

data class NdiPrefsData(
    val lastSourceUrl: String? = null,
    val lastSourceName: String? = null,
    val settings: NdiSettings = NdiSettings(),
)

/** Optional ARGB8888 frame for UI / fake receiver. */
data class NdiFrame(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val timestampNs: Long = 0L,
)
```

`NdiPrefs.kt`:
```kotlin
package com.mareg74.osccontroller.ndi

import com.mareg74.osccontroller.config.KeyValuePrefs

class NdiPrefs(private val prefs: KeyValuePrefs) {
    fun load(): NdiPrefsData {
        val raw = prefs.getString(KEY, "")
        if (raw.isBlank()) return NdiPrefsData()
        val map = raw.lineSequence()
            .map { it.trim() }
            .filter { it.contains('=') }
            .associate { line ->
                val i = line.indexOf('=')
                line.substring(0, i) to line.substring(i + 1)
            }
        val settings = NdiSettings(
            quality = map["quality"]?.toIntOrNull()?.coerceIn(0, 100) ?: 50,
            maxWidth = map["maxWidth"]?.toIntOrNull()?.coerceAtLeast(160) ?: 1280,
            maxHeight = map["maxHeight"]?.toIntOrNull()?.coerceAtLeast(160) ?: 720,
            maxFps = map["maxFps"]?.toIntOrNull()?.coerceIn(1, 60) ?: 30,
            audioEnabled = map["audio"] == "1",
        )
        return NdiPrefsData(
            lastSourceUrl = map["sourceUrl"]?.ifBlank { null },
            lastSourceName = map["sourceName"]?.ifBlank { null },
            settings = settings,
        )
    }

    fun save(data: NdiPrefsData) {
        val s = data.settings
        val q = s.quality.coerceIn(0, 100)
        val w = s.maxWidth.coerceAtLeast(160)
        val h = s.maxHeight.coerceAtLeast(160)
        val fps = s.maxFps.coerceIn(1, 60)
        val body = buildString {
            data.lastSourceUrl?.let { append("sourceUrl=").append(it).append('\n') }
            data.lastSourceName?.let { append("sourceName=").append(it).append('\n') }
            append("quality=").append(q).append('\n')
            append("maxWidth=").append(w).append('\n')
            append("maxHeight=").append(h).append('\n')
            append("maxFps=").append(fps).append('\n')
            append("audio=").append(if (s.audioEnabled) "1" else "0").append('\n')
        }
        prefs.putString(KEY, body)
    }

    companion object {
        const val KEY = "ndi_prefs_v1"
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests com.mareg74.osccontroller.ndi.NdiPrefsTest
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/ndi/NdiModels.kt \
  app/src/main/java/com/mareg74/osccontroller/ndi/NdiPrefs.kt \
  app/src/test/java/com/mareg74/osccontroller/ndi/NdiPrefsTest.kt \
  app/src/test/java/com/mareg74/osccontroller/config/MemoryPrefs.kt \
  app/src/test/java/com/mareg74/osccontroller/config/ConfigStoreTest.kt
git commit -m "feat(ndi): add NdiPrefs persistence and models"
```

---

### Task 2: NdiReceiver + Fake + NdiSession (états)

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/ndi/NdiReceiver.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/ndi/FakeNdiReceiver.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/ndi/NdiSession.kt`
- Test: `app/src/test/java/com/mareg74/osccontroller/ndi/NdiSessionTest.kt`

**Interfaces:**
- Consumes: `NdiPrefs`, models Task 1
- Produces:
```kotlin
interface NdiReceiver {
    /** false if native SDK missing / not linked */
    fun isAvailable(): Boolean
    fun discover(): List<NdiSource>
    fun connect(source: NdiSource, settings: NdiSettings): Boolean
    fun pause()
    fun resume()
    fun disconnect()
    fun isConnected(): Boolean
    fun setFrameListener(listener: ((NdiFrame) -> Unit)?)
}

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

    fun addListener(l: () -> Unit)
    fun removeListener(l: () -> Unit)

    fun refreshSources()
    fun connect(source: NdiSource)
    fun connectLastOrIdle()
    fun pause()
    fun resume()
    fun disconnect()           // also clears LIVE/PAUSED → IDLE
    fun applySettings(s: NdiSettings)  // persist + reconnect if connected
    fun retry()
}
```

Rules:
- `refreshSources`: status DISCOVERING → set sources → if was IDLE stay IDLE (or restore prior non-connecting status); on empty list keep sources empty (UI shows empty).
- `connect`: CONNECTING → success LIVE else ERROR with message.
- `pause` only from LIVE → PAUSED ; `resume` PAUSED → LIVE.
- `disconnect` always → IDLE, clears currentSource connection via receiver, keeps prefs.
- `connectLastOrIdle`: if prefs has url, find in discover or synthesize `NdiSource(name, url)` and connect ; else IDLE.
- `applySettings`: save prefs ; if connected, disconnect+reconnect with new settings.
- Frame listener updates `lastFrame` and notifies UI listeners.
- If `!receiver.isAvailable()` and connect attempted → ERROR « SDK NDI indisponible (plan B non activé) ».

- [ ] **Step 1: Write failing tests**

```kotlin
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
        assertTrue(s.errorMessage!!.contains("SDK"))
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
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests com.mareg74.osccontroller.ndi.NdiSessionTest
```

- [ ] **Step 3: Implement interface, FakeNdiReceiver, NdiSession**

`NdiReceiver.kt` — interface exacte ci-dessus.

`FakeNdiReceiver.kt`:
```kotlin
package com.mareg74.osccontroller.ndi

class FakeNdiReceiver(
    private val available: Boolean = true,
    private var sources: List<NdiSource> = emptyList(),
) : NdiReceiver {
    private var connected: Boolean = false
    private var paused: Boolean = false
    private var listener: ((NdiFrame) -> Unit)? = null
    var lastConnectedSettings: NdiSettings? = null
        private set

    override fun isAvailable() = available
    override fun discover() = sources
    override fun connect(source: NdiSource, settings: NdiSettings): Boolean {
        if (!available) return false
        connected = true
        paused = false
        lastConnectedSettings = settings
        // Emit one solid gray frame so UI path can be exercised later.
        listener?.invoke(
            NdiFrame(16, 9, IntArray(16 * 9) { 0xFF888888.toInt() }),
        )
        return true
    }
    override fun pause() { if (connected) paused = true }
    override fun resume() { if (connected) paused = false }
    override fun disconnect() { connected = false; paused = false }
    override fun isConnected() = connected
    override fun setFrameListener(listener: ((NdiFrame) -> Unit)?) {
        this.listener = listener
    }

    fun setSources(next: List<NdiSource>) { sources = next }
}
```

`NdiSession.kt` — implémenter les règles listées ; notifier listeners sur tout changement d’état / frame / sources. Sur `connect` réussi: `prefs.save` avec url/name + settings courants.

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests com.mareg74.osccontroller.ndi.NdiSessionTest
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/ndi/NdiReceiver.kt \
  app/src/main/java/com/mareg74/osccontroller/ndi/FakeNdiReceiver.kt \
  app/src/main/java/com/mareg74/osccontroller/ndi/NdiSession.kt \
  app/src/test/java/com/mareg74/osccontroller/ndi/NdiSessionTest.kt
git commit -m "feat(ndi): add NdiSession state machine with fake receiver"
```

---

### Task 3: Brancher ThorOscApp + lifecycle disconnect

**Files:**
- Modify: `app/src/main/java/com/mareg74/osccontroller/ThorOscApp.kt`
- Modify: `app/src/main/java/com/mareg74/osccontroller/MainActivity.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/ndi/SdkNdiReceiver.kt` (stub `isAvailable()=false` jusqu’à Task 5)
- Create: `app/src/main/java/com/mareg74/osccontroller/ndi/NdiReceiverFactory.kt`

**Interfaces:**
- Consumes: Task 2
- Produces: `ThorOscApp.ndiSession: NdiSession`, `ThorOscApp.ndiPrefs: NdiPrefs`
- Factory: si `SdkNdiReceiver().isAvailable()` → SDK, sinon `FakeNdiReceiver(available=false)` pour forcer ERROR clair en prod sans SDK — **ou** Fake available=true seulement en debug? Spec: SDK missing = message bloquant. Donc prod sans SDK: `FakeNdiReceiver(available=false)`.

```kotlin
object NdiReceiverFactory {
    fun create(): NdiReceiver {
        val sdk = SdkNdiReceiver()
        return if (sdk.isAvailable()) sdk else FakeNdiReceiver(available = false)
    }
}
```

`SdkNdiReceiver` stub:
```kotlin
class SdkNdiReceiver : NdiReceiver {
    override fun isAvailable() = false
    override fun discover() = emptyList()
    override fun connect(source: NdiSource, settings: NdiSettings) = false
    override fun pause() {}
    override fun resume() {}
    override fun disconnect() {}
    override fun isConnected() = false
    override fun setFrameListener(listener: ((NdiFrame) -> Unit)?) {}
}
```

MainActivity: dans `onStop()` appeler `app.ndiSession.disconnect()` (en plus du comportement existant).

- [ ] **Step 1: Write a small unit test for factory preference** — optional skip if awkward; prefer manual compile smoke. Instead add test:

```kotlin
@Test fun factoryFallsBackWhenSdkUnavailable() {
    // Sdk stub always unavailable → factory must return non-available receiver
    val r = NdiReceiverFactory.create()
    assertFalse(r.isAvailable())
}
```

Until Task 5 links real SDK, this stays true.

- [ ] **Step 2: Run test / compile**

```bash
./gradlew :app:testDebugUnitTest --tests com.mareg74.osccontroller.ndi.NdiSessionTest
./gradlew :app:assembleDebug
```

Expected: PASS / BUILD SUCCESSFUL

- [ ] **Step 3: Wire ThorOscApp**

```kotlin
val ndiPrefs = NdiPrefs(object : KeyValuePrefs { /* same adapter as ConfigStore.fromSharedPreferences */ })
// Better: reuse prefs adapter — extract or call:
val ndiPrefs = NdiPrefs(ConfigStore.fromSharedPreferences(prefs).let { /* no */ })
```

Pattern exact — dupliquer l’adapter anonyme SharedPreferences→KeyValuePrefs déjà dans `ConfigStore.fromSharedPreferences`, ou ajouter:

```kotlin
fun KeyValuePrefs.Companion.fromSharedPreferences(sp: SharedPreferences): KeyValuePrefs
```

Minimal: dans `ThorOscApp`:
```kotlin
private val kv = object : KeyValuePrefs {
    override fun getString(k: String, d: String) = prefs.getString(k, d) ?: d
    override fun putString(k: String, v: String) { prefs.edit().putString(k, v).apply() }
    override fun remove(k: String) { prefs.edit().remove(k).apply() }
    override fun clear() { prefs.edit().clear().apply() }
}
val ndiPrefs = NdiPrefs(kv)
val ndiSession = NdiSession(NdiReceiverFactory.create(), ndiPrefs)
```

- [ ] **Step 4: MainActivity onStop → disconnect**

Lire `MainActivity.kt` et ajouter `app.ndiSession.disconnect()` dans `onStop`.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(ndi): wire NdiSession into ThorOscApp and disconnect onStop"
```

---

### Task 4: UI NdiMonitorPage (Compose)

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/ui/NdiMonitorPage.kt`
- Modify: `app/src/main/java/com/mareg74/osccontroller/ui/SecondaryScreen.kt`
- Modify: `app/src/main/java/com/mareg74/osccontroller/ui/PrimaryScreen.kt`

**Interfaces:**
- Consumes: `ThorOscApp.ndiSession`
- Produces: composable `NdiMonitorPage(app: ThorOscApp)`

UI requirements (spec):
- Full-frame video area (letterbox). Sans frame: fond sombre + texte selon status.
- Overlay: nom source, status label FR (`Inactif`, `Recherche…`, `Connexion…`, `Live`, `Pause`, `Erreur: …`).
- Boutons: **On/Pause** (toggle), **Engrenage**.
- Engrenage → `AlertDialog` / popup: sliders ou champs qualité (0–100), maxWidth, maxHeight, maxFps, switch audio ; bouton Source (ouvre liste) ; Appliquer.
- Source picker: `AlertDialog` list + Refresh ; empty → « Aucune source NDI ».
- `DisposableEffect(Unit)`: `onDispose { app.ndiSession.disconnect() }` quand on quitte la composition de la page.
- `LaunchedEffect(Unit) { app.ndiSession.connectLastOrIdle() }` à l’entrée.
- Listener session → `mutableState` refresh.

Secondary destinations:
```kotlin
val destinations = listOf("Moniteur", "NDI Monitor", "Mapping", "Projets", "Réglages")
```
`when`: `"NDI Monitor" -> NdiMonitorPage(app)`

Primary mono (`!dualDisplay`):
```kotlin
add("Monitor")
add("NDI Monitor")
```
après Monitor ; brancher `NdiMonitorPage(app)`.

- [ ] **Step 1: Implement `NdiMonitorPage.kt`** (pas de test Compose instrumenté en v1 — checklist manuelle Task 6). Compile gate.

Minimal structure:
```kotlin
@Composable
fun NdiMonitorPage(app: ThorOscApp) {
    val session = app.ndiSession
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(session) {
        val listener = { tick++ }
        session.addListener(listener)
        session.connectLastOrIdle()
        onDispose {
            session.removeListener(listener)
            session.disconnect()
        }
    }
    // read session.status etc.; use tick to trigger recomposition
    // Box video + overlay controls + dialogs
}
```

Frame display v1: if `lastFrame != null`, build `ImageBitmap` from ARGB (or show placeholder color if conversion heavy). Acceptable v1: solid placeholder + status until SDK frames ; Fake emits gray frame — convert with `Bitmap.createBitmap` + `asImageBitmap()` sur thread UI (copie défensive du IntArray).

- [ ] **Step 2: Wire SecondaryScreen + PrimaryScreen**

- [ ] **Step 3: Assemble**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/ui/NdiMonitorPage.kt \
  app/src/main/java/com/mareg74/osccontroller/ui/SecondaryScreen.kt \
  app/src/main/java/com/mareg74/osccontroller/ui/PrimaryScreen.kt
git commit -m "feat(ndi): add NDI Monitor page under Moniteur/Monitor"
```

---

### Task 5: Intégration SDK NDI Android (natif)

**Files:**
- Modify: `app/build.gradle.kts` (jniLibs / ndk si besoin)
- Modify: `app/src/main/java/com/mareg74/osccontroller/ndi/SdkNdiReceiver.kt`
- Add (si fourni par Mareg74): `app/src/main/jniLibs/**`, headers/wrappers sous `app/src/main/cpp/` **seulement si SDK présent**

**Prérequis (bloquant humain):**
- Mareg74 fournit le **NDI SDK Advanced** Android (licence) + emplacement des `.so` / jar.
- Sans SDK: laisser stub `isAvailable()=false` ; UI ERROR plan B ; **ne pas** committer de binaires non licenciés.

**Mapping réglages → SDK (cible):**
- `quality` 0…100 → bandwidth NDI (`lowest` / `balanced` / `highest` par paliers 0–33 / 34–66 / 67–100)
- `maxWidth`/`maxHeight` → scale receiver / crop policy SDK si dispo, sinon documenter best-effort
- `maxFps` → throttle côté session (ignorer frames trop fréquentes) si SDK sans cap
- `audioEnabled=false` → ne pas connecter audio / mute

**Interfaces SdkNdiReceiver:**
- `isAvailable()`: `true` seulement si `System.loadLibrary` OK.
- `discover()`: find sources réseau.
- `connect`: create receiver for URL.
- Frame callback → `NdiFrame` ARGB (conversion YUV→ARGB dans native ou Kotlin).

- [ ] **Step 1: Document dans README** comment dropper le SDK (chemin `third_party/ndi/` gitignored)

Ajouter à `.gitignore`:
```
third_party/ndi/
app/src/main/jniLibs/
```

- [ ] **Step 2: Implémenter SdkNdiReceiver réel OU garder stub + note**

Si SDK absent au moment de l’exécution: commit message `feat(ndi): keep SdkNdiReceiver stub pending licensed SDK` et checklist manuelle marque BLOCKED.

Si SDK présent: implémenter + `isAvailable()=true` après loadLibrary.

- [ ] **Step 3: Unit test factory** — si SDK absent, `assertFalse(NdiReceiverFactory.create().isAvailable())` toujours.

- [ ] **Step 4: Assemble + commit**

```bash
./gradlew :app:assembleDebug
git commit -am "feat(ndi): integrate NDI SDK receiver (or stub if SDK pending)"
```

---

### Task 6: Smoke manuelle + note

**Files:**
- Create: `docs/superpowers/notes/2026-09-15-ndi-monitor-manual-smoke.md`
- Modify: `README.md` — section courte « NDI Monitor »

Checklist:
1. Dual-écran: drawer bas → NDI Monitor sous Moniteur.
2. Mono: NDI Monitor sous Monitor.
3. Sans SDK: ERROR explicite, pas de crash.
4. Avec SDK + Resolume PGM NDI: Live image.
5. Popup: baisser FPS/qualité.
6. Quit page / home: plus de réception (obs. CPU/réseau).
7. Relancer page: reconnect dernière source.

- [ ] **Step 1: Écrire la note** (cases à cocher PASS/FAIL)
- [ ] **Step 2: Exécuter sur Thor si device dispo ; sinon noter BLOCKED device**
- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/notes/2026-09-15-ndi-monitor-manual-smoke.md README.md
git commit -m "docs: NDI Monitor manual smoke checklist"
```

---

## Spec coverage (self-review)

| Spec | Task |
|------|------|
| Page bas NDI Monitor sous Moniteur | 4 |
| Mono sous Monitor | 4 |
| Discover + choix manuel | 2, 4 |
| Popup engrenage réglages | 4 |
| Persist source + settings | 1, 2 |
| NDI natif + plan B message only | 3, 5 |
| Quit/background disconnect | 3 (`onStop`), 4 (`DisposableEffect`) |
| Audio off défaut | 1 |
| États UI | 2, 4 |
| Tests unit prefs + session | 1, 2 |
| Centre Play hors v1 | — (aucune task) |

**Placeholders:** aucun TBD opérationnel ; Task 5 peut rester stub si SDK non fourni (comportement ERROR documenté).

**Types:** `NdiSettings` / `NdiSource` / `NdiStatus` / `NdiSession` cohérents Tasks 1–4.
