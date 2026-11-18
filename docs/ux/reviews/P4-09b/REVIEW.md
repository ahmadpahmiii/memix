# Design review: P4-09b speed curves

Verdict: **CHANGES REQUESTED** (3 must-fix, all small). Reviewed on the 26 desktop renders (not a phone), strings in 5 languages, tokens.

## Must-fix
1. **Length caption collides with the curve (spec: graph, contrast).** `curve-hi-200-360x800` ("10.0 सेकंड" over a point and the line) and `curve-es-200-360x800` ("16.6 s" over a point). Fix: move the caption to its own right-aligned row above the graph (spec amended). Cost about 20 dp, within the scrolling Curve tab.
2. **Axis labels collide with the first point at 200% font** (`curve-hi-200`, `curve-es-200`: "1x" touches the 0% point). Fix: cap the three axis labels at 130% font scale (spec amended).
3. **Silent no-op past 10 minutes breaks "no dead ends".** Add toast `explain_speed_too_long` (copy in the spec: en, id, es, pt, hi; use the existing toast path; keep `explanation` analytics off).

## Confirmed
- Flag 1: graph constants stay in SpeedCurveGraph.kt (documented in tokens.json `speed.curve.graph`).
- Flag 2: both token files updated (`audioCrossfadeMs` removed, `mergeNeighborsWithinRatio`, `audioJoin`, `graph` added; `segmentsPerClip` 60 kept); spec rules amended. I could not run a JSON parser here (no shell); the edited block was re-read by eye (commas and braces balanced), please parse.
- Flag 3: ear-test criteria are in the spec (listening on speech and music, speaker and headphones, PASS/FAIL rule).
- Flag 4: yes, as built.
- Flag 5: Reverse+curve allowed, extra points after a split or trim, held edge speed on outward trim, flat line on a constant clip, speed icon on the chip: all accepted. Extra points after a split can reach the 8-point cap; then the "most points" toast is the route (acceptable).

## Passes
Tabs, preset tiles (white selected, none after an edit), the 8-point state, Add point and Remove point placement, steppers, 200% layout wrapping of Reset and Done, Hindi and Spanish strings (all 5 languages complete, budgets met), no new color.

## Notes and deferrals for QA
- Flat-line render: the line sat slightly above the 1x line; QA confirm it equals the clip's constant speed (a 1x clip must lie exactly on 1x).
- `speed_point_time_value` is `%1$d%` (a bare percent sign): confirm it shows "50%" on device in all 5 languages (desktop shows it right).
- Renders are desktop, not phone: QA re-shoot on a phone at 360 x 640, 360 x 800 and 412 x 915.
- Ear test as in the spec; TalkBack pass on graph nodes and their actions.
- 09b preview vs export source-time check at ten output times per preset (spec "Done when").
- Token change logged in `docs/ux/CHANGELOG.md`, marked sync to design canvas.
