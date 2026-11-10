# Task 6 — Spec compliance & quality review

**Range:** `f567f24` → `cee69bf`  
**Verdict:** **Approved**

## Brief deliverables

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Create `docs/superpowers/notes/2026-09-15-ndi-monitor-manual-smoke.md` | Pass | New file in diff (43 lines) |
| Modify `README.md` — short « NDI Monitor » section | Pass | README +4 lines: placement + link to checklist |
| Step 1: Note with PASS/FAIL (checkbox columns) | Pass | Table columns PASS \| FAIL \| BLOCKED |
| Step 2: Thor smoke or note BLOCKED device | Pass | Device `e653283c`; install/cold start documented; SDK rows BLOCKED |
| Step 3: Commit message + staged paths | Pass | `cee69bf` — `docs: NDI Monitor manual smoke checklist`; only the two paths |

## Checklist (brief items 1–7)

| # | Brief check | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Dual-écran: tiroir bas → NDI Monitor sous Moniteur | Pass | Note ✓; `SecondaryScreen.kt` destinations order `Moniteur`, `NDI Monitor`, … |
| 2 | Mono: NDI Monitor sous Monitor | Pass | Note ✓; `PrimaryScreen.kt` tabs `Monitor`, `NDI Monitor`, … |
| 3 | Sans SDK: ERROR explicite, pas de crash | Pass | Note ✓; message matches `NdiSession.kt` + `NdiSessionTest` |
| 4 | Avec SDK + Resolume PGM: Live image | **BLOCKED** | Licensed SDK / `.so` not in repo — correctly marked |
| 5 | Popup ⚙: FPS / qualité | **BLOCKED** | E2E while Live blocked; `NdiSettingsDialog` referenced in code |
| 6 | Quit / home: stop réception | **BLOCKED** | Needs SDK + live source to observe CPU/réseau |
| 7 | Relancer page: reconnect dernière source | **BLOCKED** | `connectLastOrIdle()` + prefs in code; full path needs SDK |

SDK-dependent rows marked BLOCKED per brief placeholders — not failures.

## Spec coverage (brief self-review table)

Task 6 is documentation-only; it does not implement spec rows. The note and README align with Tasks 1–5 behavior (navigation, ERROR path, disconnect/reconnect hooks) and link to the design spec. No operational TBD introduced.

## Quality

- Diff is minimal (47 insertions, 2 files) — scope matches brief.
- Smoke note is actionable: build command, stub state, sans-SDK steps, « Avec SDK » repeat instructions, references to prior device smoke pattern.
- Honest device limits (keyguard on secondary during automated pass) without overstating PASS on drawer UX.
- README NDI Monitor blurb complements existing NDI SDK section without duplicating integration steps.
- Unit test sanity line (`ndi.*`) consistent with row 3 evidence.

## Non-blocking notes

- Rows 1–2: PASS leans on code + partial device; manual unlock on bottom drawer still recommended on Thor — already stated in note.
- When SDK unblocks: re-run rows 4–7 on same checklist file (update marks, do not remove BLOCKED history unless team prefers a new dated note).
