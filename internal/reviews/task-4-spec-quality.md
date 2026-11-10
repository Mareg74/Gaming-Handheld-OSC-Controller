# Task 4 review — spec + quality

**Base:** 83cb92d · **Head:** 64c54ae · **Diff:** `.superpowers/sdd/review-83cb92d..64c54ae.diff`

## Spec compliance

| Requirement | Verdict |
|-------------|---------|
| Secondary drawer: `Moniteur`, **NDI Monitor**, `Mapping`, `Projets`, `Réglages` | OK |
| Primary mono (`!dualDisplay`): `Monitor` then **NDI Monitor** | OK |
| `when` → `NdiMonitorPage(app)` on both screens | OK |
| Letterboxed video; dark placeholder + status when no frame | OK |
| Overlay: source name + FR status labels (all `NdiStatus` cases) | OK |
| On/Pause toggle + gear → settings popup (not separate Settings NDI) | OK |
| Settings: quality 0–100, max W/H/FPS, audio switch, Source → picker, Appliquer | OK |
| Source picker: list, Actualiser, empty « Aucune source NDI » | OK |
| `DisposableEffect`: listener, `connectLastOrIdle()` on enter, `disconnect()` on leave | OK |
| Consumes `app.ndiSession`; frame via ARGB + defensive copy | OK |
| Global: audio default off | OK (`NdiSettings.audioEnabled = false`) |
| Global: quit destination → disconnect | OK (`onDispose { disconnect() }`) |
| Global: single receiver (session) | N/A (pre-existing) |

**Note:** Brief lists `LaunchedEffect` for connect; implementation follows the brief’s own minimal `DisposableEffect(session)` block (connect inside effect) — acceptable.

**Nit (non-blocking):** Gear control is `⚙` on an outlined button, not the literal label « Engrenage ».

## Task quality

**Approved**

- Scope matches brief (3 files, one commit message).
- Dialog UX coherent with FR strings elsewhere; settings live only on page.
- Report accurately notes main-thread bitmap v1 and drawer label EN string per spec.

**Follow-ups (not Task 4 blockers):** main-thread frame conversion at full PGM; app-background disconnect if required outside composition lifecycle (brief emphasizes page quit via `DisposableEffect`).
