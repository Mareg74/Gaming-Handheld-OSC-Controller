# THOR OSC Controller — Resolume real smoke

Date: 2026-09-13  
Device: AYN Thor (`e653283c`) @ `192.168.1.73`  
Mac / Arena: `192.168.1.192` — Resolume Arena 7.16.0 (OSC Input UDP `7000`)  
App: `com.mareg74.osccontroller` — branch `cursor/fix-osc-bundle-decode-84e2`

## Setup

| Direction | Config |
|-----------|--------|
| Thor → Arena | Target `192.168.1.192:7000`, mappings `BTN_A → /composition/columns/1/connect`, `AXIS_LX → /composition/layers/1/video/opacity` |
| Arena → Thor | `Preferences/osc.xml` Output → `192.168.1.73:7001` + OSC Output Shortcuts in `~/Documents/Resolume Arena/Shortcuts/OSC/Default.xml` |

## Checklist

- [x] Arena OSC Input up (UDP `*:7000`)
- [x] Thor Settings: IP/ports Apply (`192.168.1.192` / `7000` / `7001`)
- [x] `BTN_A` → Last OSC `/composition/columns/1/connect` on Thor
- [x] `AXIS_LX` → Last OSC float path on Thor
- [x] Arena UI changed after Thor OSC
- [x] Arena → Thor feedback — **PASS** after OSC bundle decode fix
- [x] Unmapped path previously validated (mock smoke)

## Feedback path (final)

Prefs:

```xml
<Output enabled="1" sendBundles="1" targetType="4" targetPort="7001" targetAddress="192.168.1.73"/>
```

| Check | Result |
|-------|--------|
| Thor listens `:7001` | **PASS** |
| Direct Mac → Thor OSC | **PASS** |
| Arena emits UDP to Thor | **PASS** — from `192.168.1.192` |
| Arena → Thor decode | **PASS** — logcat `ThorOscRx: rx /composition/layers/…` (25+ msgs, 0 drops) |

### Bug found & fixed

Arena Output sends **OSC bundles** (`#bundle`, prefs `sendBundles="1"`). The codec only accepted bare `/address` messages and dropped every Arena packet as malformed (hex head `23 62 75 6e 64 6c 65 00`).

Fix: `OscCodec.decodeAll` unwraps bundles (and `T`/`F` tags); `OscUdpReceiver` applies every message in the UDP packet.

### Play labels (aligned)

Device mapping updated to Arena shortcuts:

- `BTN_A` → `/composition/layers/1/clips/1/connect`
- `BTN_B` → `/composition/layers/1/clips/2/connect`

`FeedbackStore.getForMappedAddress` also accepts Resolume siblings (`connect`↔`connected`, `select`↔`selected`). Retest showed **`BTN_A ← 1`** on Play after Arena clip connect output.

## Evidence

- logcat `ThorOscRx` after fix: decoded Arena messages, 0 malformed drops  
- Prior dropped packet head = `#bundle`  
- Shortcuts file mtime aligned with Mareg74 creating OSC Output Shortcuts  
