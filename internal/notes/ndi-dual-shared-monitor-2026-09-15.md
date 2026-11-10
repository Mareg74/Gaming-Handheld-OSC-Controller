# NDI Monitor dual — une source partagée

**Date:** 2026-09-15  
**Branche:** `cursor/ndi-sdk-wire-4e93` @ `7a9e223`

## Demande

Garder une seule source NDI ; afficher le moniteur aussi sur l’écran haut (dual).

## Changements

- `PrimaryScreen` : entrée **NDI Monitor** toujours présente (plus seulement en mono).
- `NdiSession` : `acquireViewer` / `releaseViewer` — disconnect seulement quand plus aucun viewer ; `disconnect()` force count=0 (background).
- `connectLastOrIdle` : no-op si déjà LIVE / PAUSED / CONNECTING.
- `NdiMonitorPage` : acquire à l’entrée, release à la sortie (au lieu de disconnect systématique).
- Test `twoViewersShareSessionUntilLastRelease`.
- Spec / README alignés.

## Hors scope

Deux sources NDI simultanées (haut ≠ bas) — non supporté (`g_recv` unique).

## Vérif

- Unit tests NDI + `assembleDebug` OK
- APK installée sur Thor `e653283c`
---
