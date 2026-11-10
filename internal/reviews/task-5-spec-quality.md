# Task 5 — Spec compliance & quality review

**Range:** `64c54ae` → `f567f24`  
**Verdict:** **Approved** (stub-pending-licensed-SDK path)

## Spec compliance (brief steps)

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Step 1: README drop path `third_party/ndi/` | Pass | README § NDI SDK |
| Step 1: `.gitignore` entries | Pass | `third_party/ndi/`, `app/src/main/jniLibs/` |
| Step 2: Stub `isAvailable() = false` | Pass | `SdkNdiReceiver.kt` |
| Step 2: Commit message when SDK absent | Pass | `feat(ndi): keep SdkNdiReceiver stub pending licensed SDK` |
| Step 2: Checklist BLOCKED | Pass | task-5-report.md |
| Step 3: Factory unit test | Pass | `NdiReceiverFactoryTest.factoryFallsBackWhenSdkUnavailable` |
| Step 4: `assembleDebug` | Pass | Re-run locally: success |
| No unlicensed binaries in diff | Pass | 3 files only; no `.so`/jniLibs |
| Global: plan B JPEG not implemented, UI message | Pass | Pre-existing `NdiSession` → `SDK NDI indisponible (plan B non activé)` |

## Out of scope until Mareg74 SDK (expected BLOCKED)

- `System.loadLibrary`, JNI/cpp, `discover`/`connect`/frame pipeline
- `app/build.gradle.kts` NDK/jniLibs wiring (“si besoin” — not required for stub)
- Settings → bandwidth / scale / fps / audio mapping (brief “cible”)

## Quality

- Minimal, focused diff; KDoc ties stub to README paths.
- Factory + session behavior unchanged and consistent with Tasks 2–3.
- README accurately states blocking UI path and no in-app JPEG fallback.

## Optional follow-up (non-blocking)

When SDK arrives: implement `isAvailable()` via successful `loadLibrary`, document best-effort for `maxWidth`/`maxHeight` if SDK lacks crop API, and add Gradle/ndk notes in README if CMake/ndk required.
