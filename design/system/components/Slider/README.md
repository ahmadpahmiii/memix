# Slider

A labeled slider with a live value, for volume, pitch, intensity, opacity and every adjustment.

- **Provide:** label, range, step, default value, current value and its formatted readout.
- **Look:**
  - Label in `label` (`text`) on the left, readout in `timecode` (`text-secondary`) on the right, above the rail.
  - **Rail:** `rail-height`, filled part `primary`, rest `hairline` (the slider's two-tone fill is the one hard-stop exception to "no gradients").
  - **Thumb:** 22px `selection` (white) disc, as in CapCut, so it's easy to see and grab. The whole row is `touch-target` tall.
- **Setting a value:** drag, or tap anywhere on the rail. **Double-tap** the thumb to reset to the default value.
- **Committing:** `onValueChangeFinished` runs once per finished change (a drag let go, a tap, a reset, a key press, a screen-reader step). Editors record one undo step there, never during the drag.
- **Snapping** (optional, `snapValue` and `snapDistance`): for a level worth finding again. While dragging or tapping, a value within `snapDistance` of `snapValue` becomes exactly `snapValue`, with one `SegmentTick` haptic each time it snaps (not on every move while it stays snapped). First use: the Volume panel snaps to 100% within ±3% (P1-08).
- **Keyboard and screen readers:** arrow keys and TalkBack's swipe up or down change the value by `step` (Volume: 10%). Steps land on multiples of `step`: from 147%, one step up is 150% and one step down 140%, so the default (100%) is always reachable without a drag or a double-tap (P1-08 review; WCAG 2.2 SC 2.1.1). The readout is the state description.
- **Don't:** use a slider where exact values matter more than feel (the Position panel uses steppers), or without a readout.
