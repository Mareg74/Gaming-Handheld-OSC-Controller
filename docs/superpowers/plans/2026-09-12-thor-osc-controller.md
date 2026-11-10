# THOR OSC Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a native Kotlin/Compose app on AYN Thor that maps gamepad controls to OSC UDP (tx+rx), with dual-screen monitor/legend and Resolume Arena presets.

**Architecture:** Single landscape Activity (top) + optional `Presentation` (bottom). Hot path: `GamepadInput` → `MappingEngine` → `OscUdpSender`. Feedback: `OscUdpReceiver` → `FeedbackStore` → UI. Config and button notes persist in SharedPreferences; Resolume commands ship as an embedded JSON asset.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Android Gradle Plugin 8.x, minSdk 26 / compileSdk 34, JUnit 4 (JVM unit tests), no Capacitor/WebView.

**Spec:** `docs/superpowers/specs/2026-09-12-thor-osc-controller-design.md`

## Global Constraints

- Package root: `com.mareg74.osccontroller`
- Product name: **THOR OSC Controller** ; vendor: **Mareg74**
- Language: Kotlin + Jetpack Compose (no Capacitor, no WebView UI)
- Transport v1: **OSC UDP only** (send + listen)
- Input priority: **physical gamepad first**; screen is labels / pages / mapping / settings
- Dual-screen: top = controls/mapping/settings ; bottom Presentation = Monitor + Button legend tabs
- Resolume: **generic OSC** + **embedded Arena preset catalog** (asset JSON)
- Reuse from `ThorOscControlDeck` / LXCTRL: **ideas only** — ask Mareg74 before copying concrete code
- `JAVA_HOME`: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (if present)
- No modal dialogs on the gamepad hot path
- Do not implement TCP, Companion bridge, Art-Net/DMX, or LXCTRL integration

---

## File Structure (target)

```
settings.gradle.kts
build.gradle.kts
gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/assets/resolume_arena_catalog.json
app/src/main/java/com/mareg74/osccontroller/
  MainActivity.kt
  ThorOscApp.kt
  osc/
    OscMessage.kt
    OscCodec.kt
  config/
    AppConfig.kt
    ConfigStore.kt
  mapping/
    ControlId.kt
    MappingEngine.kt
  net/
    OscUdpSender.kt
    OscUdpReceiver.kt
  feedback/
    FeedbackStore.kt
  catalog/
    ResolumeCatalog.kt
  input/
    GamepadInput.kt
  ui/
    PrimaryScreen.kt
    SecondaryScreen.kt
    SecondaryDisplayHost.kt
    MonitorLog.kt
app/src/test/java/com/mareg74/osccontroller/
  osc/OscCodecTest.kt
  mapping/MappingEngineTest.kt
  feedback/FeedbackStoreTest.kt
  config/ConfigStoreTest.kt
  catalog/ResolumeCatalogTest.kt
README.md
```

---

### Task 1: Scaffold Android project + README

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/mareg74/osccontroller/MainActivity.kt`, `README.md`, gradle wrapper (via `gradle wrapper` or Android Studio template copy)
- Test: `./gradlew :app:assembleDebug` (compile smoke)

**Interfaces:**
- Consumes: nothing
- Produces: runnable empty Activity `MainActivity` package `com.mareg74.osccontroller`

- [ ] **Step 1: Create Gradle root**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "THOR-OSC-Controller"
include(":app")
```

Root `build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 2: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.mareg74.osccontroller"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mareg74.osccontroller"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
```

If Kotlin 2.x Compose compiler plugin is required by the chosen Kotlin version, switch to `org.jetbrains.kotlin.plugin.compose` instead of `composeOptions.kotlinCompilerExtensionVersion` and keep the project compiling.

- [ ] **Step 3: Manifest + empty Activity**

`AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:label="THOR OSC Controller"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:configChanges="orientation|screenSize|keyboard|keyboardHidden|navigation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`MainActivity.kt`:
```kotlin
package com.mareg74.osccontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("THOR OSC Controller") }
    }
}
```

- [ ] **Step 4: README**

```markdown
# THOR OSC Controller

Native Kotlin/Compose OSC controller for the AYN Thor (gamepad-first, dual-screen, UDP).

## Run
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Spec: `docs/superpowers/specs/2026-09-12-thor-osc-controller-design.md`
Plan: `docs/superpowers/plans/2026-09-12-thor-osc-controller.md`
```

- [ ] **Step 5: Compile smoke**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app gradle/ gradlew gradlew.bat README.md
git commit -m "chore: scaffold Android Kotlin Compose app"
```

---

### Task 2: OSC codec (encode / decode)

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/osc/OscMessage.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/osc/OscCodec.kt`
- Test: `app/src/test/java/com/mareg74/osccontroller/osc/OscCodecTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `data class OscMessage(val address: String, val args: List<Any>)`
  - `object OscCodec { fun encode(msg: OscMessage): ByteArray; fun decode(bytes: ByteArray): OscMessage? }`
  - Supported args v1: `Int`, `Float`, `String` (bang = empty args or single `Int 1` — use empty args for bang)

- [ ] **Step 1: Write failing tests**

```kotlin
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
}
```

- [ ] **Step 2: Run tests — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests com.mareg74.osccontroller.osc.OscCodecTest`
Expected: FAIL (classes missing)

- [ ] **Step 3: Implement codec**

```kotlin
package com.mareg74.osccontroller.osc

data class OscMessage(val address: String, val args: List<Any> = emptyList())

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
            }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): OscMessage? {
        if (bytes.size < 4 || bytes[0] != '/'.code.toByte()) return null
        try {
            var i = 0
            fun readString(): String {
                val start = i
                while (i < bytes.size && bytes[i] != 0.toByte()) i++
                val s = bytes.copyOfRange(start, i).toString(Charsets.US_ASCII)
                i++ // null
                while (i % 4 != 0) i++
                return s
            }
            fun readInt(): Int {
                val v = ((bytes[i].toInt() and 0xff) shl 24) or
                    ((bytes[i + 1].toInt() and 0xff) shl 16) or
                    ((bytes[i + 2].toInt() and 0xff) shl 8) or
                    (bytes[i + 3].toInt() and 0xff)
                i += 4
                return v
            }
            val address = readString()
            val tags = readString()
            if (!tags.startsWith(",")) return null
            val args = ArrayList<Any>()
            for (t in tags.drop(1)) {
                when (t) {
                    'i' -> args += readInt()
                    'f' -> args += java.lang.Float.intBitsToFloat(readInt())
                    's' -> args += readString()
                    else -> return null
                }
            }
            return OscMessage(address, args)
        } catch (_: Exception) {
            return null
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests com.mareg74.osccontroller.osc.OscCodecTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/osc app/src/test/java/com/mareg74/osccontroller/osc
git commit -m "feat: add OSC encode/decode codec"
```

---

### Task 3: ConfigStore

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/config/AppConfig.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/config/ConfigStore.kt`
- Test: `app/src/test/java/com/mareg74/osccontroller/config/ConfigStoreTest.kt`

**Interfaces:**
- Consumes: Android `SharedPreferences` (Robolectric **not** required — use in-memory fake for JVM tests)
- Produces:
  - `data class ControlMapping(val controlId: String, val address: String, val argKind: ArgKind)`
  - `enum class ArgKind { BANG, FLOAT }`
  - `data class AppConfig(val targetIp: String, val targetPort: Int, val listenPort: Int, val activePage: Int, val mappings: List<ControlMapping>, val buttonNotes: Map<String, String>)`
  - `class ConfigStore(prefs: KeyValuePrefs)` with `load(): AppConfig`, `save(AppConfig)`, `resetToDefaults()`
  - `interface KeyValuePrefs { fun getString(k: String, d: String): String; fun putString(k: String, v: String); fun clear() }`
  - Defaults: `127.0.0.1:7000` out, listen `7001`, page `0`, empty mappings/notes

- [ ] **Step 1: Write failing tests with in-memory prefs**

```kotlin
package com.mareg74.osccontroller.config

import org.junit.Assert.*
import org.junit.Test

class MemoryPrefs : KeyValuePrefs {
    private val m = mutableMapOf<String, String>()
    override fun getString(k: String, d: String) = m[k] ?: d
    override fun putString(k: String, v: String) { m[k] = v }
    override fun clear() = m.clear()
}

class ConfigStoreTest {
    @Test fun defaultsThenRoundTrip() {
        val store = ConfigStore(MemoryPrefs())
        val d = store.load()
        assertEquals("127.0.0.1", d.targetIp)
        assertEquals(7000, d.targetPort)
        assertEquals(7001, d.listenPort)
        val next = d.copy(
            targetIp = "192.168.1.10",
            mappings = listOf(ControlMapping("BTN_A", "/composition/columns/1/connect", ArgKind.BANG)),
            buttonNotes = mapOf("BTN_A" to "A = STROBE")
        )
        store.save(next)
        val loaded = store.load()
        assertEquals("192.168.1.10", loaded.targetIp)
        assertEquals("/composition/columns/1/connect", loaded.mappings.single().address)
        assertEquals("A = STROBE", loaded.buttonNotes["BTN_A"])
    }

    @Test fun resetSoftDefaults() {
        val store = ConfigStore(MemoryPrefs())
        store.save(store.load().copy(targetIp = "9.9.9.9"))
        store.resetToDefaults()
        assertEquals("127.0.0.1", store.load().targetIp)
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests com.mareg74.osccontroller.config.ConfigStoreTest`

- [ ] **Step 3: Implement**

```kotlin
package com.mareg74.osccontroller.config

enum class ArgKind { BANG, FLOAT }

data class ControlMapping(
    val controlId: String,
    val address: String,
    val argKind: ArgKind
)

data class AppConfig(
    val targetIp: String = "127.0.0.1",
    val targetPort: Int = 7000,
    val listenPort: Int = 7001,
    val activePage: Int = 0,
    val mappings: List<ControlMapping> = emptyList(),
    val buttonNotes: Map<String, String> = emptyMap()
)

interface KeyValuePrefs {
    fun getString(k: String, d: String): String
    fun putString(k: String, v: String)
    fun clear()
}

class ConfigStore(private val prefs: KeyValuePrefs) {
    fun load(): AppConfig {
        val raw = prefs.getString(KEY, "")
        if (raw.isBlank()) return AppConfig()
        return try {
            parse(raw)
        } catch (_: Exception) {
            AppConfig()
        }
    }

    fun save(config: AppConfig) = prefs.putString(KEY, serialize(config))

    fun resetToDefaults() {
        prefs.clear()
        save(AppConfig())
    }

    private fun serialize(c: AppConfig): String {
        val maps = c.mappings.joinToString(";") {
            "${it.controlId}|${it.address}|${it.argKind.name}"
        }
        val notes = c.buttonNotes.entries.joinToString(";") { "${it.key}|${it.value.replace(';', ',')}" }
        return listOf(c.targetIp, c.targetPort, c.listenPort, c.activePage, maps, notes).joinToString("\n")
    }

    private fun parse(raw: String): AppConfig {
        val lines = raw.split("\n")
        val mappings = if (lines.size > 4 && lines[4].isNotBlank()) {
            lines[4].split(";").map {
                val p = it.split("|")
                ControlMapping(p[0], p[1], ArgKind.valueOf(p[2]))
            }
        } else emptyList()
        val notes = if (lines.size > 5 && lines[5].isNotBlank()) {
            lines[5].split(";").associate {
                val p = it.split("|", limit = 2)
                p[0] to p.getOrElse(1) { "" }
            }
        } else emptyMap()
        return AppConfig(
            targetIp = lines[0],
            targetPort = lines[1].toInt(),
            listenPort = lines[2].toInt(),
            activePage = lines[3].toInt(),
            mappings = mappings,
            buttonNotes = notes
        )
    }

    companion object {
        const val KEY = "app_config_v1"
        fun fromSharedPreferences(sp: android.content.SharedPreferences): ConfigStore =
            ConfigStore(object : KeyValuePrefs {
                override fun getString(k: String, d: String) = sp.getString(k, d) ?: d
                override fun putString(k: String, v: String) {
                    sp.edit().putString(k, v).apply()
                }
                override fun clear() {
                    sp.edit().clear().apply()
                }
            })
    }
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/config app/src/test/java/com/mareg74/osccontroller/config
git commit -m "feat: add ConfigStore with mappings and button notes"
```

---

### Task 4: MappingEngine

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/mapping/ControlId.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/mapping/MappingEngine.kt`
- Test: `app/src/test/java/com/mareg74/osccontroller/mapping/MappingEngineTest.kt`

**Interfaces:**
- Consumes: `AppConfig`, `OscMessage`, `ArgKind`
- Produces:
  - `enum class ControlId { BTN_A, BTN_B, BTN_X, BTN_Y, BTN_L1, BTN_R1, BTN_L2, BTN_R2, BTN_START, BTN_SELECT, AXIS_LX, AXIS_LY, AXIS_RX, AXIS_RY }`
  - `class MappingEngine { fun resolve(config: AppConfig, control: ControlId, value: Float): OscMessage? }`
  - Unmapped → `null`; `BANG` ignores axis magnitude (fires on press value≥0.5); `FLOAT` sends `value`

- [ ] **Step 1: Failing tests**

```kotlin
package com.mareg74.osccontroller.mapping

import com.mareg74.osccontroller.config.*
import org.junit.Assert.*
import org.junit.Test

class MappingEngineTest {
    private val engine = MappingEngine()

    @Test fun unmappedReturnsNull() {
        assertNull(engine.resolve(AppConfig(), ControlId.BTN_A, 1f))
    }

    @Test fun bangOnPress() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(ControlId.BTN_A.name, "/composition/columns/1/connect", ArgKind.BANG)
        ))
        val msg = engine.resolve(cfg, ControlId.BTN_A, 1f)!!
        assertEquals("/composition/columns/1/connect", msg.address)
        assertTrue(msg.args.isEmpty())
        assertNull(engine.resolve(cfg, ControlId.BTN_A, 0f))
    }

    @Test fun floatAxis() {
        val cfg = AppConfig(mappings = listOf(
            ControlMapping(ControlId.AXIS_LX.name, "/composition/layers/1/video/opacity", ArgKind.FLOAT)
        ))
        val msg = engine.resolve(cfg, ControlId.AXIS_LX, 0.25f)!!
        assertEquals(0.25f, msg.args.single() as Float, 0.0001f)
    }
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

```kotlin
package com.mareg74.osccontroller.mapping

enum class ControlId {
    BTN_A, BTN_B, BTN_X, BTN_Y,
    BTN_L1, BTN_R1, BTN_L2, BTN_R2,
    BTN_START, BTN_SELECT,
    AXIS_LX, AXIS_LY, AXIS_RX, AXIS_RY
}

class MappingEngine {
    fun resolve(
        config: com.mareg74.osccontroller.config.AppConfig,
        control: ControlId,
        value: Float
    ): com.mareg74.osccontroller.osc.OscMessage? {
        val mapping = config.mappings.firstOrNull { it.controlId == control.name } ?: return null
        return when (mapping.argKind) {
            com.mareg74.osccontroller.config.ArgKind.BANG -> {
                if (value < 0.5f) null
                else com.mareg74.osccontroller.osc.OscMessage(mapping.address, emptyList())
            }
            com.mareg74.osccontroller.config.ArgKind.FLOAT ->
                com.mareg74.osccontroller.osc.OscMessage(mapping.address, listOf(value))
        }
    }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/mapping app/src/test/java/com/mareg74/osccontroller/mapping
git commit -m "feat: add MappingEngine for bang and float controls"
```

---

### Task 5: FeedbackStore

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/feedback/FeedbackStore.kt`
- Test: `app/src/test/java/com/mareg74/osccontroller/feedback/FeedbackStoreTest.kt`

**Interfaces:**
- Consumes: `OscMessage`
- Produces: `class FeedbackStore { fun apply(msg: OscMessage); fun get(address: String): Any?; fun dropCount: Int; fun snapshot(): Map<String, Any> }` — malformed path is handled by codec returning null before apply

- [ ] **Step 1: Failing tests**

```kotlin
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
}
```

- [ ] **Step 2–4: Implement + PASS**

```kotlin
package com.mareg74.osccontroller.feedback

import com.mareg74.osccontroller.osc.OscMessage

class FeedbackStore {
    private val values = LinkedHashMap<String, Any>()
    var dropCount: Int = 0
        private set

    fun apply(msg: OscMessage) {
        values[msg.address] = msg.args.firstOrNull() ?: Unit
    }

    fun noteDrop() {
        dropCount++
    }

    fun get(address: String): Any? = values[address]

    fun snapshot(): Map<String, Any> = values.toMap()
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/feedback app/src/test/java/com/mareg74/osccontroller/feedback
git commit -m "feat: add FeedbackStore for inbound OSC state"
```

---

### Task 6: OscUdpSender + OscUdpReceiver

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/net/OscUdpSender.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/net/OscUdpReceiver.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/ui/MonitorLog.kt`
- Test: cover validation helpers in sender (IP/port) via small pure functions — put `object NetValidation` in same package and unit-test it; live sockets are device/smoke only

**Interfaces:**
- Consumes: `OscCodec`, `AppConfig`, `FeedbackStore`, `MonitorLog`
- Produces:
  - `object NetValidation { fun isValidIpv4(ip: String): Boolean; fun isValidPort(p: Int): Boolean }`
  - `class OscUdpSender { fun send(config: AppConfig, msg: OscMessage): Boolean }` — returns false on invalid target or IO error; never throws to callers
  - `class OscUdpReceiver(feedback: FeedbackStore, log: MonitorLog) { fun start(listenPort: Int): Boolean; fun stop() }`
  - `class MonitorLog { fun lines(): List<String>; fun append(line: String); var listenError: String? }` — cap 200 lines

- [ ] **Step 1: Failing validation tests**

```kotlin
package com.mareg74.osccontroller.net

import org.junit.Assert.*
import org.junit.Test

class NetValidationTest {
    @Test fun ipAndPort() {
        assertTrue(NetValidation.isValidIpv4("192.168.0.1"))
        assertFalse(NetValidation.isValidIpv4("302.1.1.1"))
        assertTrue(NetValidation.isValidPort(7000))
        assertFalse(NetValidation.isValidPort(0))
        assertFalse(NetValidation.isValidPort(70000))
    }
}
```

- [ ] **Step 2: Implement validation + sender/receiver/log**

```kotlin
package com.mareg74.osccontroller.net

object NetValidation {
    private val IPV4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
    fun isValidIpv4(ip: String): Boolean {
        if (!IPV4.matches(ip)) return false
        return ip.split(".").all { it.toInt() in 0..255 }
    }
    fun isValidPort(p: Int) = p in 1..65535
}
```

```kotlin
package com.mareg74.osccontroller.net

import com.mareg74.osccontroller.config.AppConfig
import com.mareg74.osccontroller.osc.OscCodec
import com.mareg74.osccontroller.osc.OscMessage
import com.mareg74.osccontroller.ui.MonitorLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class OscUdpSender(private val log: MonitorLog) {
    fun send(config: AppConfig, msg: OscMessage): Boolean {
        if (!NetValidation.isValidIpv4(config.targetIp) || !NetValidation.isValidPort(config.targetPort)) {
            log.append("error: invalid target ${config.targetIp}:${config.targetPort}")
            return false
        }
        return try {
            val data = OscCodec.encode(msg)
            DatagramSocket().use { socket ->
                val addr = InetAddress.getByName(config.targetIp)
                socket.send(DatagramPacket(data, data.size, addr, config.targetPort))
            }
            log.append("tx ${msg.address} ${msg.args}")
            true
        } catch (e: Exception) {
            log.append("error: send ${e.message}")
            false
        }
    }
}
```

```kotlin
package com.mareg74.osccontroller.net

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
                        val msg = OscCodec.decode(bytes)
                        if (msg == null) {
                            feedback.noteDrop()
                            log.append("rx drop malformed")
                        } else {
                            feedback.apply(msg)
                            log.append("rx ${msg.address} ${msg.args}")
                        }
                    } catch (_: Exception) {
                        if (running) { /* swallow transient */ }
                    }
                }
            }
            true
        } catch (e: Exception) {
            log.listenError = e.message
            log.append("error: bind ${e.message}")
            false
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
```

```kotlin
package com.mareg74.osccontroller.ui

class MonitorLog(private val capacity: Int = 200) {
    private val buffer = ArrayDeque<String>()
    var listenError: String? = null

    @Synchronized fun append(line: String) {
        if (buffer.size >= capacity) buffer.removeFirst()
        buffer.addLast(line)
    }

    @Synchronized fun lines(): List<String> = buffer.toList()
}
```

- [ ] **Step 3: Run validation tests PASS**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/net app/src/main/java/com/mareg74/osccontroller/ui/MonitorLog.kt app/src/test/java/com/mareg74/osccontroller/net
git commit -m "feat: add OSC UDP sender, receiver, and monitor log"
```

---

### Task 7: Resolume Arena catalog asset

**Files:**
- Create: `app/src/main/assets/resolume_arena_catalog.json`
- Create: `app/src/main/java/com/mareg74/osccontroller/catalog/ResolumeCatalog.kt`
- Test: `app/src/test/java/com/mareg74/osccontroller/catalog/ResolumeCatalogTest.kt`

**Interfaces:**
- Consumes: JSON string
- Produces: `data class CatalogEntry(val label: String, val address: String, val argKind: ArgKind)` ; `object ResolumeCatalog { fun parse(json: String): List<CatalogEntry> }`
- Ship a **starter** list (≥8 entries) covering columns connect, layer opacity, layer bypass, composition opacity — enough for mapping UX; expand later with Mareg74

- [ ] **Step 1: Asset JSON**

```json
[
  {"label": "Column 1 connect", "address": "/composition/columns/1/connect", "argKind": "BANG"},
  {"label": "Column 2 connect", "address": "/composition/columns/2/connect", "argKind": "BANG"},
  {"label": "Column 3 connect", "address": "/composition/columns/3/connect", "argKind": "BANG"},
  {"label": "Column 4 connect", "address": "/composition/columns/4/connect", "argKind": "BANG"},
  {"label": "Layer 1 opacity", "address": "/composition/layers/1/video/opacity", "argKind": "FLOAT"},
  {"label": "Layer 1 bypass", "address": "/composition/layers/1/bypassed", "argKind": "BANG"},
  {"label": "Layer 2 opacity", "address": "/composition/layers/2/video/opacity", "argKind": "FLOAT"},
  {"label": "Composition opacity", "address": "/composition/video/opacity", "argKind": "FLOAT"}
]
```

- [ ] **Step 2: Failing parse test + implement**

```kotlin
package com.mareg74.osccontroller.catalog

import com.mareg74.osccontroller.config.ArgKind
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

data class CatalogEntry(val label: String, val address: String, val argKind: ArgKind)

object ResolumeCatalog {
    fun parse(json: String): List<CatalogEntry> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    CatalogEntry(
                        label = o.getString("label"),
                        address = o.getString("address"),
                        argKind = ArgKind.valueOf(o.getString("argKind"))
                    )
                )
            }
        }
    }
}

class ResolumeCatalogTest {
    @Test fun parsesStarter() {
        val json = """[{"label":"Column 1 connect","address":"/composition/columns/1/connect","argKind":"BANG"}]"""
        val e = ResolumeCatalog.parse(json).single()
        assertEquals("/composition/columns/1/connect", e.address)
        assertEquals(ArgKind.BANG, e.argKind)
    }
}
```

Note: `org.json` is on the Android classpath; for JVM unit tests add `testImplementation("org.json:json:20240303")` to `app/build.gradle.kts` if needed.

- [ ] **Step 3: PASS + commit**

```bash
git add app/src/main/assets/resolume_arena_catalog.json app/src/main/java/com/mareg74/osccontroller/catalog app/src/test/java/com/mareg74/osccontroller/catalog app/build.gradle.kts
git commit -m "feat: embed Resolume Arena OSC preset catalog"
```

---

### Task 8: GamepadInput

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/input/GamepadInput.kt`
- Test: pure mapping from Android keycodes — extract `fun mapKeyCode(keyCode: Int): ControlId?` and `fun mapAxis(axis: Int): ControlId?` for JVM tests

**Interfaces:**
- Consumes: `ControlId`, Android `KeyEvent` / `MotionEvent` constants
- Produces: `object GamepadInput { fun mapKeyCode(keyCode: Int): ControlId?; fun mapAxis(axis: Int): ControlId?; fun normalizeAxis(value: Float): Float }` — clamp −1…1; deadzone 0.05 → 0

- [ ] **Step 1: Failing tests**

```kotlin
package com.mareg74.osccontroller.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.mareg74.osccontroller.mapping.ControlId
import org.junit.Assert.*
import org.junit.Test

class GamepadInputTest {
    @Test fun buttons() {
        assertEquals(ControlId.BTN_A, GamepadInput.mapKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(ControlId.BTN_B, GamepadInput.mapKeyCode(KeyEvent.KEYCODE_BUTTON_B))
        assertNull(GamepadInput.mapKeyCode(KeyEvent.KEYCODE_A))
    }

    @Test fun axesAndDeadzone() {
        assertEquals(ControlId.AXIS_LX, GamepadInput.mapAxis(MotionEvent.AXIS_X))
        assertEquals(0f, GamepadInput.normalizeAxis(0.02f), 0.0001f)
        assertEquals(0.5f, GamepadInput.normalizeAxis(0.5f), 0.0001f)
    }
}
```

Note: `KeyEvent`/`MotionEvent` constants need `testOptions.unitTests.isReturnDefaultValues = true` in `app/build.gradle.kts` **or** duplicate numeric constants in `GamepadInput` for unit tests. Prefer `isReturnDefaultValues = true`.

- [ ] **Step 2: Implement**

```kotlin
package com.mareg74.osccontroller.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.mareg74.osccontroller.mapping.ControlId
import kotlin.math.abs

object GamepadInput {
    private const val DEADZONE = 0.05f

    fun mapKeyCode(keyCode: Int): ControlId? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ControlId.BTN_A
        KeyEvent.KEYCODE_BUTTON_B -> ControlId.BTN_B
        KeyEvent.KEYCODE_BUTTON_X -> ControlId.BTN_X
        KeyEvent.KEYCODE_BUTTON_Y -> ControlId.BTN_Y
        KeyEvent.KEYCODE_BUTTON_L1 -> ControlId.BTN_L1
        KeyEvent.KEYCODE_BUTTON_R1 -> ControlId.BTN_R1
        KeyEvent.KEYCODE_BUTTON_L2 -> ControlId.BTN_L2
        KeyEvent.KEYCODE_BUTTON_R2 -> ControlId.BTN_R2
        KeyEvent.KEYCODE_BUTTON_START -> ControlId.BTN_START
        KeyEvent.KEYCODE_BUTTON_SELECT -> ControlId.BTN_SELECT
        else -> null
    }

    fun mapAxis(axis: Int): ControlId? = when (axis) {
        MotionEvent.AXIS_X -> ControlId.AXIS_LX
        MotionEvent.AXIS_Y -> ControlId.AXIS_LY
        MotionEvent.AXIS_Z -> ControlId.AXIS_RX
        MotionEvent.AXIS_RZ -> ControlId.AXIS_RY
        else -> null
    }

    fun normalizeAxis(value: Float): Float {
        val v = value.coerceIn(-1f, 1f)
        return if (abs(v) < DEADZONE) 0f else v
    }
}
```

- [ ] **Step 3: PASS + commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/input app/src/test/java/com/mareg74/osccontroller/input app/build.gradle.kts
git commit -m "feat: map Thor gamepad keys and axes to ControlId"
```

---

### Task 9: Primary UI (settings, mapping, pages, fallback tabs)

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/ui/PrimaryScreen.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/ThorOscApp.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Consumes: `ConfigStore`, `ResolumeCatalog`, `MonitorLog`, `FeedbackStore`, `MappingEngine`, `OscUdpSender`
- Produces: Compose UI with tabs/sections: **Play** (last fired + page), **Map** (pick control + catalog/free address), **Settings** (IP/port out/listen + Resolume feedback hint), and when single-display: **Monitor** + **Legend** tabs

- [ ] **Step 1: Implement `ThorOscApp` holder**

```kotlin
package com.mareg74.osccontroller

import android.content.Context
import com.mareg74.osccontroller.catalog.ResolumeCatalog
import com.mareg74.osccontroller.config.ConfigStore
import com.mareg74.osccontroller.feedback.FeedbackStore
import com.mareg74.osccontroller.mapping.MappingEngine
import com.mareg74.osccontroller.net.OscUdpReceiver
import com.mareg74.osccontroller.net.OscUdpSender
import com.mareg74.osccontroller.ui.MonitorLog

class ThorOscApp(context: Context) {
    val log = MonitorLog()
    val feedback = FeedbackStore()
    val configStore = ConfigStore.fromSharedPreferences(
        context.getSharedPreferences("thor_osc", Context.MODE_PRIVATE)
    )
    var config = configStore.load()
    val engine = MappingEngine()
    val sender = OscUdpSender(log)
    val receiver = OscUdpReceiver(feedback, log)
    val catalog: List<com.mareg74.osccontroller.catalog.CatalogEntry> =
        ResolumeCatalog.parse(context.assets.open("resolume_arena_catalog.json").bufferedReader().readText())

    fun persist() = configStore.save(config)

    fun restartReceiver() {
        receiver.start(config.listenPort)
    }
}
```

- [ ] **Step 2: PrimaryScreen Compose (minimal but complete)**

Implement `PrimaryScreen(app: ThorOscApp, dualDisplay: Boolean, onConfigChanged: () -> Unit)` with:
- Play: show `lastFired` text state updated from Activity
- Map: Dropdown/list of `ControlId.entries`, catalog dropdown OR free address `TextField`, ArgKind, Save mapping into `app.config` + `persist()`
- Settings: fields for IP, targetPort, listenPort; button Apply → persist + `restartReceiver()`; text hint: « Dans Resolume → OSC output vers IP du Thor et ce port listen »
- If `!dualDisplay`: extra tabs Monitor (`app.log.lines()`) and Legend (edit `buttonNotes` per `ControlId`)

Keep styling simple Material3 dark; no card spam.

- [ ] **Step 3: Wire Activity input**

In `MainActivity`:
- create `ThorOscApp`
- `setContent { PrimaryScreen(...) }`
- override `onKeyDown`/`onKeyUp`/`onGenericMotionEvent` → `GamepadInput` → `MappingEngine.resolve` → `sender.send` ; on unmapped keyDown set a short « non mappé » label state
- `onStart`: `restartReceiver()` ; `onStop`: `receiver.stop()`

- [ ] **Step 4: Compile**

Run: `./gradlew :app:assembleDebug`
Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller
git commit -m "feat: primary Compose UI for play, map, settings, fallback"
```

---

### Task 10: Secondary Presentation (Monitor + Legend)

**Files:**
- Create: `app/src/main/java/com/mareg74/osccontroller/ui/SecondaryScreen.kt`
- Create: `app/src/main/java/com/mareg74/osccontroller/ui/SecondaryDisplayHost.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Consumes: `ThorOscApp`, `DisplayManager`
- Produces: `SecondaryDisplayHost.attach(activity, app)` / `detach()` — no-op if `< 2` displays; Presentation shows tabbed Monitor + Legend (editable notes → `app.config.buttonNotes` + persist)

- [ ] **Step 1: Implement host**

```kotlin
package com.mareg74.osccontroller.ui

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.compose.ui.platform.ComposeView
import com.mareg74.osccontroller.ThorOscApp

class SecondaryDisplayHost {
    private var presentation: Presentation? = null

    fun attach(activity: Activity, app: ThorOscApp) {
        detach()
        val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondary = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY } ?: return
        val p = object : Presentation(activity, secondary) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(ComposeView(context).apply {
                    setContent { SecondaryScreen(app) }
                })
            }
        }
        presentation = p
        p.show()
    }

    fun detach() {
        try { presentation?.dismiss() } catch (_: Exception) {}
        presentation = null
    }
}
```

- [ ] **Step 2: SecondaryScreen**

Compose with two tabs:
1. **Moniteur** — lazy list of `app.log.lines()` + listenError banner if set
2. **Légende** — for each `ControlId` button (exclude axes or include all): `TextField` note bound to `app.config.buttonNotes`; on change update + `persist()`

Refresh monitor every ~250ms via `LaunchedEffect` + polling `lines()`, or expose a tick `State` from Activity. Polling is fine for v1.

- [ ] **Step 3: Wire in MainActivity**

- `onStart`: `host.attach(this, app)` after receiver start
- `onStop`: `host.detach()`
- Pass `dualDisplay = (displayManager.displays.size >= 2)` into PrimaryScreen for fallback tabs

- [ ] **Step 4: assembleDebug SUCCESS + commit**

```bash
git add app/src/main/java/com/mareg74/osccontroller/ui app/src/main/java/com/mareg74/osccontroller/MainActivity.kt
git commit -m "feat: secondary Presentation monitor and button legend"
```

---

### Task 11: Device smoke checklist (Thor)

**Files:**
- Create: `docs/superpowers/notes/2026-09-12-thor-osc-device-smoke.md`

**Interfaces:** none (manual)

- [ ] **Step 1: Write checklist**

```markdown
# THOR OSC Controller — device smoke

Date:
Device: AYN Thor
Build:

- [ ] Install debug APK landscape
- [ ] Settings: set Resolume IP/port; listen port; Apply (no bind error)
- [ ] Map BTN_A → Column 1 connect (catalog); press A → Resolume reacts / mock receives
- [ ] Map AXIS_LX → Layer 1 opacity; stick moves float
- [ ] Unmapped button shows « non mappé », no send
- [ ] Dual screen: bottom Monitor shows tx/rx lines
- [ ] Legend tab: note `A = STROBE` editable and persisted after relaunch
- [ ] Feedback: Resolume OSC out → Thor listen port updates Monitor
- [ ] Single-display fallback: Monitor+Legend on top tabs (test by disabling secondary if possible, or note N/A)
```

- [ ] **Step 2: Run on Thor if adb available; otherwise mark deferred with reason**

- [ ] **Step 3: Commit checklist**

```bash
git add docs/superpowers/notes/2026-09-12-thor-osc-device-smoke.md
git commit -m "docs: add Thor device smoke checklist"
```

---

## Self-review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| Gamepad-first OSC UDP out | 4, 6, 8, 9 |
| Generic + Resolume Arena presets | 7, 9 |
| Dual-screen Presentation monitor + legend notes | 10 |
| Feedback OSC in | 5, 6, 9 |
| Fallback 1 screen | 9 |
| Config persistence | 3 |
| Error behaviors (invalid IP, bind fail, malformed, unmapped) | 6, 9 |
| Unit tests codec/mapping/feedback/config | 2–5, 7–8 |
| Device smoke | 11 |
| Greenfield Kotlin/Compose, no Capacitor | 1 + Global Constraints |
| Ask Mareg74 before reusing prototype code | Global Constraints |

Placeholder scan: none intentional. Types aligned: `ControlId`, `AppConfig`, `OscMessage`, `ArgKind`, `CatalogEntry`.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-12-thor-osc-controller.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute tasks in this session with executing-plans and checkpoints  

Which approach?
