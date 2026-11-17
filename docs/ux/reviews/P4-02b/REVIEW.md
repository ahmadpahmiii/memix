# Design review: P4-02b keyframes editor

Reviewer: ux-designer, 17 Nov 2026. Against `docs/ux/specs/P4-02b-keyframes.md`. Looked at: the renders I opened (bar on a keyframe, wide bar, es 200% bar, marker rows 640 and 800, badge, status line, Opacity panel, Easing panel 640 and hi 200%), all five string files, the three icons, the handoff. Not opened one by one: the remaining renders (caption, sound, main-clip, position panel, status-added); the strings and bars they show follow the same code. Drag, snap, stop-at-neighbor and split continuity can't be judged from stills: they go to the End-of-build QA list.

## Verdict: CHANGES (one small must-fix; everything else approved or deferred)

## Must-fix
1. **Announce the selected keyframe after Previous, Next and a diamond tap.** Spec, Accessibility, Announcements: "after Previous or Next the new node's description". Without it a TalkBack user gets no sign that the playhead moved or which keyframe is selected (WCAG 4.1.3), and Previous/Next are their no-drag route (2.5.7). Fix: a polite announcement using the existing `a11y_keyframe_node` ("Keyframe 2 of 3, at 0:02.0, Smooth"); the easing name is the `easing_*` string. No new copy in any language.

## Calls on your flags
1. **Icons: approved.**
   - `ic_keyframe`: 13 px diamond plus 1 px round-joined stroke = 14 px, centered. Matches the spec.
   - `ic_easing`: two 2 px butt-capped curve halves and a 6 px core diamond. The gap around the diamond reads as one S-curve at bar size and is not mistaken for `audio_effects`. Approved.
   - `ic_arrow_left`: an exact mirror of `arrow`. Approved; recorded in DESIGN_SYSTEM → Iconography.
2. **Diamond animations: defer, not must-fix.** The 120 ms fade-in and the selected-size animation are polish: the selection is still white and larger at once, so state is not color alone and nothing is lost for reduce-motion users. **Edge auto-scroll while dragging a diamond: defer to the End-of-build QA list.** It matters at high zoom with a long item. The routes that cover it exist (Previous, Next, Move here, tap). If the phone check shows drags get stuck at the screen edge, it becomes a P5 polish ticket. The spec keeps the three; they are marked "deferred" in Motion.
3. **es at 200% wraps "Añadir fotograma clave" to 3 lines: accepted.** The spec already accepted es three-line labels at 200% ("the bar grows"), the bar grew, nothing clipped, and Previous and Next stay reachable by scroll. Keep the string: a shorter label ("Añadir") would lose the noun.
4. **Keys: approved.**
   - Folding `easing_title`, `edit_keyframe_add`, `edit_keyframe_remove`, `edit_keyframe_easing` into the existing keys is right (same words). `a11y_keyframe_added` folded into `keyframe_added`: fine, as long as the polite announcement of an add still fires.
   - New `keyframe_select_node` ("Go to"; id "Ke sini", es "Ir a", pt "Ir para", hi "यहाँ जाएं") is fine as a TalkBack action label.
   - New `a11y_keyframes_group` is fine (id "Keyframe", es "Fotogramas clave", pt "Quadros-chave", hi "कीफ़्रेम").
   - pt "cursor" for the playhead: **approved**, it matches the shipped `tool_move_here_click`. The spec's "posição atual" draft is replaced.
   - The five languages read correctly and are within budget. es `keyframe_note` "valen" is a little stiff; fine for a machine draft until P5.
5. **Main video clip Keyframes for volume only, main photo clip none: accepted until P4-16.** The tool is absent where it can't act (ToolBar rule). **Dependency for the PM:** when P4-16 builds the Fit or Fill tool, it must add `explain_fit_keyed` and the Canvas panel skip (spec, "How other edits treat keyframes"). Until then no keyed main clip can lose its keyframes by Fit or Fill, so nothing is unsafe now.
6. **Opacity and Volume panels pause playback on open for a keyed item: accepted, spec amended.** An edit "at the playhead" is ill-defined while the playhead moves. The readouts then need no 10 per second refresh. **Keyed Volume not heard live while sliding: defer to the QA list.** It is a real cost for ducking (the user hears the depth only after release). Check at the phone check whether it is a complaint.
7. **"Undo: <edit name>" for an edit that creates keyframes: accepted.** It is what the spec says ("named after the edit"); the analytics id is `keyframe_add` as specified.
8. **No spoken node after Previous or Next: must-fix, see above.**

## What works
- The bar order Close, Previous, Add/Remove, Next, Easing is in the thumb's reach at 360 dp, and the active look appears only on Remove keyframe, with its label changing (not color alone).
- The marker row is clear at 640 and 800: three diamonds, one white and large, the thin span line, the playhead over the selected one. The badge on an unselected clip reads over the thumbnails.
- No blue anywhere except the active Remove tool and the meme-sound pill. No outlines, no gradients, no hard shadows.
- Easing panel: five tiles, selected one white with dark curve, partial tile at the right edge shows it scrolls. Hindi at 200% wraps cleanly with no clipped matras.
- Opacity panel: note at the top, 0% reachable.

## Polish (optional)
- Opacity panel: the gap between the note and the slider looks bigger than the spec's `space-4`; check the Column spacing.
- Easing tile curves are small (20 dp) in the 200% Hindi render; fine, as the spec says they stay 20 dp.

## For the End-of-build QA list
- Diamond drag: value bubble, snap ticks, hard stop at a neighbor, LongPress haptic (screen recording).
- Edge auto-scroll while dragging a diamond at high zoom (deferred).
- Keyed Volume not audible live: is the ducking workflow still usable (deferred).
- Diamond fade-in and selected-size animation (deferred polish).
- TalkBack walk: group "Keyframes", node actions (Go to, Move here, Remove, Easing), the new announcement.
- Split continuity, Trim keeps the motion, undo-step counts, preview = export (spec "Measured").
- Smooth as the default easing and one-keyframe-for-everything (spec Assumptions) at the phone check.
