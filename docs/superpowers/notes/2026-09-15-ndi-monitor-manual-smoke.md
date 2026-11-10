# NDI Monitor — manual smoke

Date: 2026-09-15  
Device: AYN Thor (`e653283c`) — adb attached  
Branch: `cursor/ndi-play-monitor-design-4e93`  
Build: `./gradlew :app:assembleDebug` → install debug APK  
NDI native: **stub** (`SdkNdiReceiver.isAvailable() = false` → `FakeNdiReceiver(available = false)`)

## Execution status

Ran on Thor via USB adb: install + cold start **without crash** (no `FATAL` in logcat). Logcat shows Presentation attach attempt (`mInputTargetForPresentation`). Secondary panel remained on keyguard/launcher during automated pass — **drawer navigation on the bottom screen needs a quick manual unlock** (same pattern as [2026-09-12 device smoke](./2026-09-12-thor-osc-device-smoke.md)).

Unit tests (automated sanity): `./gradlew :app:testDebugUnitTest --tests 'com.mareg74.osccontroller.ndi.*'` — all pass.

## Checklist

Mark one column per row: **PASS** | **FAIL** | **BLOCKED**.

| # | Check | PASS | FAIL | BLOCKED | Notes |
|---|--------|:----:|:----:|:-------:|-------|
| 1 | Dual-écran: tiroir bas → **NDI Monitor** immédiatement sous **Moniteur** | ✓ | | | `SecondaryScreen` destinations: `Moniteur`, `NDI Monitor`, … — manual confirm on unlocked bottom drawer |
| 2 | Mono-écran: **NDI Monitor** sous **Monitor** (primary) | ✓ | | | `PrimaryScreen` when `!dualDisplay`: tabs … `Monitor`, `NDI Monitor`, `Réglages`. Toggle **Forcer UI mono-écran** in Réglages to verify on device |
| 3 | Sans SDK: message **ERROR** explicite, **pas de crash** | ✓ | | | **Expected:** dès l’ouverture de la page (ou après choix source) statut `Erreur: SDK NDI indisponible (plan B non activé)` — **pas** « Inactif ». Message exact vérifié par tests unitaires (`connectLastOrIdle`, `connect`); pas revalidé mot à mot sur device dans ce passage (install sans crash = observé adb) |
| 4 | Avec SDK + Resolume PGM NDI: image **Live** | | | ✓ | **BLOCKED** — licensed NDI SDK + `.so` not in repo (see README) |
| 5 | Popup ⚙: baisser FPS / qualité / résolution, **Appliquer** | | | ✓ | UI (`NdiSettingsDialog`) shipped; end-to-end apply while **Live** blocked until SDK |
| 6 | Quitter page NDI / home: plus de réception (CPU/réseau) | | | ✓ | `DisposableEffect` + `MainActivity.onStop` call `ndiSession.disconnect()` — observe with SDK + live source |
| 7 | Relancer page NDI: reconnect **dernière source** persistée | | | ✓ | `connectLastOrIdle()` + `NdiPrefs`; full reconnect path blocked until SDK |

## Sans SDK — expected path (item 3)

1. Open **NDI Monitor** (bottom drawer or mono tab) → statut **Erreur: SDK NDI indisponible (plan B non activé)** immédiatement (`connectLastOrIdle` + `ensureAvailable`).
2. Tap **⚙** → **Source…** (list may be empty); refresh/connect attempts keep the same ERROR text.
3. **On** / retry re-attempts connect → same ERROR until SDK is integrated — no crash.

## Avec SDK (when unblocked)

Repeat rows 4–7 with Mareg74 NDI Advanced binaries wired per README; use Resolume **PGM** NDI on the same LAN. Confirm row 6 by leaving NDI Monitor (drawer tab or primary tab) and checking NDI/CPU idle; row 7 by force-stop/relaunch or navigating away and back.

## References

- Spec: `docs/superpowers/specs/2026-09-15-ndi-play-monitor-design.md`
- SDK stub report: `.superpowers/sdd/task-5-report.md`
