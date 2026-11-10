# Plan: Pages tactiles

**Spec:** `docs/superpowers/specs/2026-09-15-tactile-pages-design.md`

## Tasks

1. **Model** — `PageType`, `TactileKind`, `TactileWidget` ; `AppConfig.pageTypes` + `widgets` ; `addPage(name, type)` ; copy/delete shift ; `ConfigCodec` + tests.
2. **Engine** — `MappingEngine.resolveId` + `ThorOscApp.emitControl`.
3. **Create dialog** — étendre `NewPageNameOverlay` (titre + type) ; brancher MapPad + Settings.
4. **Tactile editor** — `TactileMapTab` dans Mapping si page TACTILE : canvas, add trigger/fader, drag, select, panneau OSC, delete.
5. **Play** — `PlayTactileLayout` ; `PlayTab` haut branche sur type ; Secondary drawer **Play** en premier (tactile only).
6. **Verify** — unit tests, assembleDebug, install Thor, commit/push.

## Fichiers clés

- [`AppConfig.kt`](app/src/main/java/com/mareg74/osccontroller/config/AppConfig.kt)
- [`ConfigCodec.kt`](app/src/main/java/com/mareg74/osccontroller/config/ConfigCodec.kt)
- [`MappingEngine.kt`](app/src/main/java/com/mareg74/osccontroller/mapping/MappingEngine.kt)
- [`MapPad.kt`](app/src/main/java/com/mareg74/osccontroller/ui/MapPad.kt)
- [`PlayGamepad.kt`](app/src/main/java/com/mareg74/osccontroller/ui/PlayGamepad.kt) / nouveau `PlayTactile.kt`
- [`SecondaryScreen.kt`](app/src/main/java/com/mareg74/osccontroller/ui/SecondaryScreen.kt)
- [`PrimaryScreen.kt`](app/src/main/java/com/mareg74/osccontroller/ui/PrimaryScreen.kt)
