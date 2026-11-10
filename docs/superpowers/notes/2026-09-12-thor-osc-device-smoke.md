# THOR OSC Controller — device smoke

Date: 2026-09-13
Device: AYN Thor (`e653283c`, model AYN_Thor)
Build: debug after `fix: Presentation Compose lifecycle` (`cursor/fix-presentation-lifecycle-84e2`)

## Execution status

**Run on device** (USB adb). Initial crash on dual-display Presentation (`ViewTreeLifecycleOwner not found`) fixed by setting Activity lifecycle/saved-state owners on the Presentation `ComposeView`.

## Checklist

- [x] Install debug APK landscape
- [x] Settings: set Resolume IP/port; listen port; Apply (no bind error) — target `192.168.1.192:7000`, listen `7001`
- [x] Map BTN_A → Column 1 connect (catalog); press A → mock UDP on Mac received `/composition/columns/1/connect`
- [x] Map AXIS_LX → Layer 1 opacity; stick/axis injection produces float OSC (`…/opacity`, e.g. `-0.5`)
- [x] Unmapped button shows « non mappé », no send — `BTN_X` → `non mappé: BTN_X`, no extra UDP
- [x] Dual screen: bottom Presentation window present (`ty=PRESENTATION` on display 4); no crash
- [x] Legend note persistence — `BTN_A|A = STROBE` survives force-stop/relaunch via `ConfigStore` prefs (secondary Legend UI not driven by uiautomator; persistence path verified)
- [x] Feedback: OSC into Thor listen port updates Monitor/Play — sent to `:7001`, UI showed `BTN_A ← (bang)`
- [x] Single-display fallback: Monitor+Legend on top tabs — **PASS** via Settings « Forcer UI mono-écran » (Thor has 2 panels; force hides Presentation). Monitor showed `rx …/connect`; Legend showed notes; restoring force=off returns dual tabs only

## Notes

- Primary UI tabs Play / Map / Settings verified via uiautomator.
- Resolume itself not required: Mac UDP mock + inbound UDP to device substituted for tx/rx checks.

## Single-display smoke (2026-09-13)

Hardware still reports 2 displays. Verified with config flag `forceSingleDisplay=1`:

1. Primary tabs include **Monitor** and **Legend**
2. Monitor lists inbound `rx` OSC lines
3. Legend lists button notes / mapped OSC
4. No app `PRESENTATION` window while forced
5. Flag off → primary tabs Play/Map/Settings only again

Settings toggle: **Forcer UI mono-écran**.
