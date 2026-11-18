# P4-10 design review: Reverse and freeze frame

Reviewed 18 Nov 2026 against `docs/ux/specs/P4-10-reverse-and-freeze.md`. Evidence: 28 Compose Desktop renders (not a phone; the engineer's README says so), the 5 stand-in references, strings in 5 languages, the two icon XMLs, `ToolBar.kt`.

**Verdict: Approved with polish.** Nothing blocks the commit. Two small fixes are recommended before it (P1, P2); the rest goes to QA.

## Checked and fine
- Bars: Freeze frame and Reverse sit right after Speed; Reverse on takes `primary` on `primary-subtle`, label unchanged. Photo clip bar has neither; freeze clip bar matches the spec list (no Replace).
- Spanish 200% ("Congelar fotograma" on two lines, "Invertir" one) and Hindi 200% wrap without clipping; the bar grows.
- Chips: reverse icon on a main clip and an overlay, readable, no color only cue.
- Status "Reversing 40%" and the toasts (en, es, hi): `text` on `surface-raised`, wrap well, no clipping.
- Icons: `ic_reverse` is two solid left triangles with 1.5 units of air, so it does not read as flip or undo. `ic_freeze` is a 2 px frame holding two 3 px bars with 3.5 px air, so it does not read as pause or mask. Both approved.

## Your 9 flags
1. **`explain_freeze_failed`: accepted.** A missing spec row is a spec gap, now added here (budget 55, toast, wraps). Drafts read correctly in id, es, pt, hi. Keep as built: en "Couldn't freeze this frame. Try again." · id "Tidak bisa membekukan frame ini. Coba lagi." · es "No se pudo congelar este fotograma. Inténtalo otra vez." · pt "Não foi possível congelar este quadro. Tente de novo." · hi "यह फ़्रेम रोका नहीं जा सका। फिर कोशिश करें।" Native review at P5.
2. **No "Free up space" action: accepted, QA list.** The toast already says what to do. Add the action when the Toast gets an action slot.
3. **1 px round joins: accepted.** Reads the same at 24 dp.
4. **`ToolBarItem.switchOn`: accepted.** Fits the system (reuses `Role.Switch`). ToolBar README updated.
5. **No render of the freeze clip's chip and the held first frame: QA list** (device).
6. **JPEG in the media folder: accepted**, spec amended. It stays on the phone (no upload rule). Condition: P3 below.
7. **Over 60 s after extending: see P1.**
8. **Selected clip only, playhead on it: accepted** (matches the spec; `explain_freeze_outside` covers the miss).
9. **More than 2 audio channels reverses silently: accepted as a v1 limit, QA list.** Rare on phone video. A downmix is the later fix.

## Fixes
- **P1 (before commit, small): wrong error on an over-limit extend.** Extending a reversed clip past 60 s today switches Reverse off with `reverse_failed` ("Try again"), which gives the user a retry that can't work. Spec rule: errors say what to do. Fix: when the failure is the length limit, show `explain_reverse_long` instead of `reverse_failed` (no new copy). Better still, refuse the extend handle at 60 s of source, but that is a bigger change; the toast is enough.
- **P2 (before commit, tiny): add the `explain_freeze_failed` row to the spec copy table** (done in the spec; the engineer need not change anything).
- **P3 (QA/PM list): cleanup of freeze JPEGs.** Check that deleting the project and removing a freeze clip (after undo history drops it) leaves no orphan files in `media/<project id>/`.

## Deferrals for the QA list
1. Freeze clip's chip (`freeze` icon) and "Frozen frame" spoken description on a phone, with TalkBack.
2. Stage holding the first frame, silent, while preparing; a stage tap still plays and pauses.
3. The "Reversing %" status on a real 60 s clip, and the percent announcements (at most every 25%).
4. A 360 dp wide bar (render exists; confirm on the phone) and Reverse reachable after one short swipe.
5. Reduce motion: the freeze lands with a cross-fade, no bonk.
6. "Free up space" action missing on the reverse-space toast (flag 2).
7. A source with more than 2 audio channels reverses silent (flag 9).
8. The references are ffmpeg stand-ins; the real-engine frames come at phase-end QA (burned-in time falling by one frame per output frame).
9. Native review of the pt `explain_freeze_outside` ("Mova antes a posição atual para o clipe."), which sounds stiff; proposed at P5: "Mova o cursor para o clipe primeiro."
