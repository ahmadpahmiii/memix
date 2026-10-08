# Toast

A short message that doesn't interrupt: what just happened ("Undo: Split"), or why a tool can't act right now ("To split, move the playhead inside this clip."). In the editors it sits at the top of the preview stage, away from the thumb and the timeline.

- **Look:**
  - `surface-raised`, `radius-md`, `shadow-float`.
  - Text in `label` `text`, up to 3 lines, then it wraps no further.
  - Padding `space-2` vertical, `space-3` horizontal.
  - At most the screen width minus `space-4` on each side; centered.
- **Placement:**
  - Editors: `space-2` below the top of the preview stage.
  - Elsewhere: above the bottom navigation, with the same gap.
- **Timing:** the base is 2 s for "what happened" and 4 s for explanations. It's never shorter than the user's "Time to take action" setting (Compose `calculateRecommendedTimeoutMillis`). A new toast replaces the current one.
- **Persistent variant** (save failures, P1-04):
  - It stays until resolved.
  - It may carry one Quiet button (the action, in `primary`) and a Dismiss icon button (×, `touch-target`).
  - The text says what happened and what happens next.
- **Motion:** 120 ms fade in and out, the same with reduce motion.
- **Accessibility:** announced politely, as a status message. It never takes focus (WCAG 2.2 SC 4.1.3). Its buttons are focusable.
- **Don't:** use it for errors that block work (use a Sheet), stack two, or put an undo button in it in the editors (Undo is always visible in the transport row).
