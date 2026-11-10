# NDI Monitor v1 — final branch review

**Range:** `5ef6108` → `cee69bf` (10 commits)
**Branch:** `cursor/ndi-play-monitor-design-4e93`
**Verdict:** **With fixes** — 1 critical spec gap, 7 important issues, none blocking the build.

**Verification run (read-only):** `./gradlew --offline :app:testDebugUnitTest` → BUILD SUCCESSFUL,
54 tests / 0 failures (new: `NdiPrefsTest` 2, `NdiSessionTest` 5, `NdiReceiverFactoryTest` 1);
`./gradlew --offline :app:assembleDebug` → BUILD SUCCESSFUL. No lint/CI config in repo.

## Strengths

- Layering matches the plan exactly (`NdiModels` / `NdiPrefs` / `NdiReceiver` + Fake + Sdk / `NdiSession` / `NdiMonitorPage`); the receiver interface keeps the state machine testable on the JVM with no native libs.
- Licensed-binary hygiene is right: `.gitignore` covers `third_party/ndi/` and `app/src/main/jniLibs/`, README documents the drop-in procedure and where `isAvailable()` must flip.
- `MemoryPrefs` extracted from `ConfigStoreTest.kt` into `config/MemoryPrefs.kt` instead of duplicated — clean refactor, existing 13 `ConfigStoreTest` cases still pass.
- Prefs use a versioned key (`ndi_prefs_v1`) with clamping on both read and write; defaults match the spec (audio off, 1280×720, 30 fps, quality 50).
- Drawer placement matches the spec verbatim: secondary `Moniteur, NDI Monitor, Mapping, Projets, Réglages`; mono primary inserts `NDI Monitor` directly after `Monitor` and only when `!dualDisplay`, so only one page can ever hold the receiver.
- Disconnect is belt-and-braces: `DisposableEffect.onDispose` plus `MainActivity.onStop`, and `receiver.disconnect()` is idempotent.
- FR status labels match the spec wording (`Inactif`, `Recherche…`, `Connexion…`, `Live`, `Pause`, `Erreur: …`).
- The listener + `tick` recomposition pattern mirrors the existing `MainActivity.uiTick` convention rather than inventing a new one.
- Commits are one-per-plan-task with an explicit follow-up fix (`add3f8b`, routing `applySettings` reconnect through `connect()`).

## Issues

### Critical

**C1. The mandated "SDK indisponible" blocking message is unreachable on a fresh install.**
Spec §6 requires `SDK indispo / licence → message bloquant + note plan B`, and that is the *only* configuration this branch ships. But the message is produced solely inside `NdiSession.connect()`, and on a fresh install there is no way to reach `connect()`:

- `NdiReceiverFactory.create()` returns `FakeNdiReceiver(available = false)` whose `sources` defaults to `emptyList()`.
- Page entry calls `connectLastOrIdle()`; prefs are empty → `IDLE`, no error.
- `refreshSources()` never consults `receiver.isAvailable()`, so ⚙ → `Source…` shows "Aucune source NDI".
- The bottom button in `IDLE` calls `connectLastOrIdle()` → still `IDLE`.

Net effect: the user sees `Inactif` + "Aucune source NDI", which reads as a network problem, never as a missing SDK. This also invalidates manual-smoke row 3 as written. Fix: have `refreshSources()` (and page entry) set `ERROR` with the SDK message when `!receiver.isAvailable()`.

### Important

**I2. `retry()` loses the attempted source and silently drops to `IDLE`.**
`connect()` does not assign `currentSource` on the failure path, and `persistConnectionPrefs()` only runs on success. So after a first failed attempt: `retry()` → `status == ERROR` → `currentSource == null` → `connectLastOrIdle()` → prefs empty → `IDLE`. Tapping **On** on an error therefore clears the error instead of retrying, contradicting spec §6 (`error + retry`) and the smoke note's claim that "On / retry re-attempts connect → same ERROR". `retry()` has no test at all (the triage item understates this — it is untested, not lightly tested). Fix: record the attempted source in `connect()` before dispatching, or pass it into `retry()`.

**I3. `NdiSession` is not thread-safe and every receiver call runs on the main thread.**
`discover()` / `connect()` are invoked synchronously from the `DisposableEffect` body and from click handlers; NDI discovery and connect are multi-second network operations, so this becomes an ANR the moment `SdkNdiReceiver` is real. Symmetrically, the frame callback will arrive on an SDK thread and writes `lastFrame`/`status` and iterates `listeners` (a plain `mutableSetOf`) with no synchronization — `ConcurrentModificationException` risk when a page disposes mid-notify, plus off-main-thread Compose state writes. The codebase already establishes the convention (`MonitorLog` uses `@Synchronized`). Also `setFrameListener` is installed in `init` and never cleared, so in-flight frames after `disconnect()` still update `lastFrame` and notify listeners while `IDLE`. Retrofitting this after the SDK lands is invasive (the UI reads plain `var`s synchronously); worth doing before, or at minimum documenting as a hard prerequisite on the SDK task.

**I4. `ndiFrameToImageBitmap` can crash composition on a malformed frame.**
The `frame.width > 0 && frame.height > 0` guard sits in the `if` *after* `remember(tick) { … ndiFrameToImageBitmap(it) }` has already run. A zero-dimension frame throws `IllegalArgumentException` from `Bitmap.createBitmap`, and an undersized `argb` throws from `setPixels` — both inside composition, violating spec §6 "pas de crash silencieux". The fake emits a valid 16×9 frame so this is latent today. Fix: validate `width > 0 && height > 0 && argb.size >= width * height` inside the converter and return `null`.

**I5. `applySettings` commits and persists before the reconnect can fail.**
Spec §6: `Changement réglages échoue → garder anciens réglages + toast erreur`. The implementation assigns `settings = s` and calls `prefs.save(...)` first, then reconnects; on failure the new (bad) settings are already live and persisted, and the session is left in `ERROR`. `applySettingsWhenSdkUnavailableErrors` asserts the `ERROR` state but never asserts rollback, so the gap is invisible in the suite. Fix: snapshot the old settings and restore + persist them if `connect()` does not reach `LIVE`.

**I6. Settings dialog and `NdiPrefs` clamp to different ranges.**
The dialog coerces width/height/fps to `>= 1` with no upper bound; `NdiPrefs` clamps sizes to `>= 160` and fps to `1..60`. Entering width `10` leaves `session.settings.maxWidth == 10` in memory (and that is what gets handed to `receiver.connect`), while the persisted value is `160` — so the value silently jumps after a restart. Fix: clamp once in a single place (e.g. an `NdiSettings.sanitized()` used by both the dialog and `applySettings`), or re-read `prefs.load().settings` after saving.

**I7. No resume after background → foreground.**
`MainActivity.onStop` disconnects, but nothing reconnects on `onStart` — even though the same method already calls `app.restartReceiver()` for OSC. In mono mode the activity's composition survives stop/start, so `DisposableEffect(session)` does not re-run: the user returns from home to a page that reads `Inactif` with no explanation and must tap **On**. (Dual mode is masked because `detach()`/re-attach resets the bottom drawer to `Moniteur`.) Spec only mandates disconnect, so this is a UX gap rather than a violation, but it makes success criterion 1 feel broken on the device. Fix: a lifecycle observer that calls `connectLastOrIdle()` on `ON_START` when status is `IDLE`, or mirror `restartReceiver()` in `onStart`.

**I8. Manual-smoke rows 1–3 are marked PASS but were not observed on device.**
The note itself states the secondary panel "remained on keyguard/launcher during automated pass", and the evidence column for those rows cites source files and a unit test. Code-inferred checks marked PASS in a *device* checklist overstate verification — spec success criteria 1 and 5 are in fact unverified on hardware, and row 3's expected path is wrong given C1 and I2. Fix: re-mark rows 1–3 as BLOCKED (or run the unlock pass) and correct row 3's expectation.

### Minor

- **M9. Per-frame bitmap churn.** Each tick re-runs `argb.copyOf()` + `createBitmap` + `setPixels` — two full-frame copies. At 1280×720/30 fps that is ~7 MB allocated per frame on the UI thread, and it also re-allocates on every unrelated status change since the key is `tick`. The plan explicitly accepts this for v1, but a latency-oriented monitor should move to a retained `Bitmap` or a `SurfaceView`/`TextureView` before the SDK lands.
- **M10. `NdiFrame` is a `data class` holding an `IntArray`** (triage item), so `equals`/`hashCode` are identity-based and misleading. Nothing depends on it today (`remember` keys on `tick`), so this is cosmetic until someone keys state on a frame. Either hand-write `equals`/`hashCode` or drop `data`.
- **M11. `lastFrame` survives `disconnect()`.** Re-entering the page briefly shows the previous session's image before reconnect, and a full-resolution frame stays retained for the app's lifetime because `NdiSession` is app-scoped.
- **M12. `frame` and `imageBitmap` are read from `session.lastFrame` twice** (once directly, once inside `remember`), so with background frame delivery the aspect ratio can come from a different frame than the bitmap.
- **M13. Accessibility.** The ⚙ `OutlinedButton` is a bare `Text("⚙")` with no `contentDescription`/semantics label, and the video `Image` passes `contentDescription = null`. The glyph-vs-word triage item is fine as-is (the spec asks for a gear button, not the word "Engrenage"), but it needs a semantics label.
- **M14. `ERROR`-state affordances.** The button reads "On" rather than "Réessayer", and the source-name overlay shows `—` during `ERROR` because `currentSource` was never set (see I2).
- **M15. Unimplemented spec §6 recovery rows.** No periodic discovery ("refresh manuel / périodique"), no limited auto-reconnect with backoff on network loss, no source-disappeared detection. The plan omitted these too, so this is a plan/spec coverage miss rather than an implementation slip.
- **M16. `refreshSources` flips `LIVE → DISCOVERING → LIVE`,** so the overlay flickers through "Recherche…" during a refresh while live.
- **M17. Settings dialog edits are silently discarded** when the user taps `Source…`, and there is no way back to the settings dialog from the picker.
- **M18. Spec §7 Compose UI tests** (idle invite, live overlay, error retry, popup apply) are not implemented. The plan deliberately deferred them to the manual checklist, so this is an accepted deviation — worth tracking rather than fixing here.
- **M19. `audioEnabled` and `maxFps` have no effect anywhere.** Expected while the SDK is stubbed, but the plan assigned the `maxFps` throttle to the session layer ("throttle côté session"), which could land now and is the setting most likely to matter for bandwidth.
- **M20. `NdiReceiverFactoryTest` is tautological** while the stub exists, and it will keep passing on the JVM even after the real SDK lands (because `loadLibrary` fails there), so it will never catch a factory regression.
- **M21. `ThorOscApp` imports are out of alphabetical order** (`ndi.*` inserted between two `config.*` imports). Cosmetic; no lint or CI in the repo to enforce it.

## Recommendations

**Before merge:** C1 and I2 — together they mean the only behavior that ships today (no SDK) is wrong in both directions: the user never learns the SDK is missing, and the retry affordance clears the error instead of retrying. Both are small, local changes in `NdiSession` plus a correction to the smoke note (I8).

**Before dropping in the licensed SDK** (these are prerequisites, not follow-ups, because retrofitting them is invasive): I3 (move `discover`/`connect` off the main thread and make session state synchronized) and I4 (validate frame geometry). M9 belongs here too — decide on the frame path (retained bitmap vs. `SurfaceView`) before wiring real frames rather than after.

**Cheap correctness cleanups:** I5 and I6 are each a few lines and both are currently masked by tests that assert the wrong half of the behavior; add the rollback and clamp assertions alongside. Add a `retry()` test while fixing I2.

**Track, do not fix here:** M15 (backoff/periodic discovery) and M18 (Compose UI tests) are spec items the plan consciously left out; they should become explicit v1.1 items rather than silently dropping off the spec-coverage table. The real-SDK stub (triage item 4) is correctly blocked on Mareg74 and correctly documented — no action.

## Spec coverage

| Spec requirement | Status |
|---|---|
| Page bas « NDI Monitor » sous « Moniteur » | Met |
| Mono sous « Monitor » | Met |
| Discovery + choix manuel | Met (empty without SDK; see C1) |
| Popup engrenage (qualité/rés/FPS/audio) | Met (clamp divergence I6) |
| Persist source + réglages | Met |
| NDI natif ; plan B message only | **Partial — C1** |
| Quit page / background → disconnect | Met (no resume, I7) |
| Audio off par défaut | Met (no effect yet, M19) |
| États UI explicites | Met (error affordance I2/M14) |
| Tests unit prefs + session | Met (gaps: `retry`, rollback, listeners) |
| Erreurs §6: réglages échoue / réseau perdu / source disparue | **Partial — I5, M15** |
| Centre Play hors v1 | Correctly absent |
