# P4-08 transitions: design review (17 Nov 2026)

Verdict: **CHANGES REQUESTED** (3 small must-fixes, no rework). Reviewed: 28 desktop renders, 20 reference strips, strings x5, `ic_transition.xml`, handoff. Not a device: shaders and timing are for the QA list.

Strings: all 27 keys present in en, id, es, pt, hi and identical to the spec drafts. `transition_removed_toast` is present. Hindi at 200% wraps to two lines, Duration still reads, toast fits. No change.
Panel: layout matches the spec at 640 (Duration half in view), 800, 915. Cue badge: right place, hidden with a neighbor selected. Tokens only.

## Must-fix
1. **Icon** (builder flag 1: not approved as drawn; in the cue render it reads as a "sign in" door glyph because the stem starts inside the stroked clip and the head touches its edge). Replace `ic_transition.xml` paths with:
   - stroked clip: `M2,6 L7.5,6 L7.5,18 L2,18 Z`, stroke 2, join round
   - arrowhead only: `M10,8.8 L13.8,12 L10,15.2 Z`, fill
   - solid clip: `M16.5,5 L22,5 Q23,5 23,6 L23,18 Q23,19 22,19 L16.5,19 Q15.5,19 15.5,18 L15.5,6 Q15.5,5 16.5,5 Z`, fill
   (air: 1.5 to the stroked clip's outer edge at 8.5, 1.7 to the solid clip.) Re-render the cue and bar images.
2. **Dip tiles** (flag 2): make the Dip to black and Dip to white tiles at progress 0.3 (A at 40% brightness / 60% toward white) so the tile shows a picture. Other tiles stay at 0.5. The 20 reference strips are unchanged.
3. **Tile name alignment**: single-line names (Dissolve) are centered while wrapped names are start-aligned. Use the Filters tile's alignment for all (spec: the Filters tile pattern). No spec line changes.

## Decisions on the builder flags
3. **Glitch cut / flash rate: no guard, no new copy.** SC 2.3.1 counts general flashes (a 10% luminance swing over a quarter of a 10-degree field); sliced offsets and a 1% channel shift fail the area test, and one window is at most 3 bursts. Dip to white is one swell. Residual risk (two Glitch cuts under 1 s apart, 6 bursts in a second) goes to QA to measure; if over threshold we cap in the engine, still no copy.
4. Black outside the picture (Twirl, Zoom through, Flip) and black strips in Glitch cut: accepted for v1; Pixel swap from plain A: accepted (spec amended). Re-judge on the phone.
5. Cover-cropped tiles, None tile = first frame at 40%: accepted.
6. Tool hidden for a missing-file clip: accepted, spec amended.
7. Silent removal when the last clip's neighbor is deleted: accepted (the user caused it and sees it; the toast is only for the "too short" case).
8. Slider snaps to 0.1 s while dragging; toast 3 s: accepted.
9. Unreadable held frame becomes a dark dip: accepted; QA to confirm it does not stall the export.

## Deferrals for the QA list
- Every transition on a phone against its strip (99% within 3/255), then replace the strips with exports; first run pins Glitch cut and Twirl.
- Audio null test and sync (spec Measured), at 30 fps, leaks.
- Worst-case flash check: two Glitch cuts 0.5 s apart; Dip to white twice 0.5 s apart, luminance trace.
- Selected white tile (Dip to white) in the strip: confirm the white ring is still visible against `surface` on the phone.
- Hindi danda spacing in the toast (the render shows a gap before the full stop); check in the app font.
- Tile pictures with the real stage live (renders have a black stage).
