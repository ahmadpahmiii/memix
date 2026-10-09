# Toast

A short message that doesn't interrupt: what just happened ("Undo: Split"), or why a tool can't act right now ("To split, move the playhead inside this clip."). In the editors it sits at the top of the preview stage, away from the thumb and the timeline.

- **Look:**
  - `surface-raised`, `radius-md`, `shadow-float`.
  - Text in `label` `text`. It wraps as far as it needs to and is never cut off (WCAG 2.2 SC 1.4.4 at 200% font). The copy budgets keep it to about 3 lines at 100% on a 360 dp phone.
  - Padding `space-2` vertical, `space-3` horizontal.
  - At most the screen width minus `space-4` on each side; centered.
- **Placement:**
  - Editors: `space-2` below the top of the preview stage.
  - Elsewhere: above the bottom navigation, with the same gap.
- **Timing:** the base is 2 s for "what happened" and 4 s for explanations. It's never shorter than the user's "Time to take action" setting (Compose `calculateRecommendedTimeoutMillis`). A new toast replaces the current one.
- **Persistent variant** (save failures, P1-04):
  - It stays until resolved.
  - It may carry one Quiet button (the action, in `primary`) and a Dismiss icon button (×, IconButton, `touch-target`).
  - **Layout:** the message and × share the first row, with × at the end. The Quiet action sits on its own row below, aligned to the end, so the message keeps almost the full width in every language. Compose Material 3 does the same with `Snackbar(actionOnNewLine = true)` for long actions ([reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/Snackbar.composable)).
  - The × presses to `hairline`, because the toast's own fill is `surface-raised` (IconButton README).
  - Hide the action when it can't work (e.g. no storage screen on this phone). Never show a button that does nothing.
  - The text says what happened and what happens next.
- **One slot:** a screen shows one toast at a time. While a persistent toast shows, a transient one is not shown and not queued (a late "Undo: Split" is stale). It is still announced politely to screen readers.
- **Motion:** 120 ms fade in and out, the same with reduce motion.
- **Accessibility:** announced politely, as a status message. It never takes focus (WCAG 2.2 SC 4.1.3). Its buttons are focusable.
- **Compose:** `Toast(message, action, dismiss)` draws one toast. `FadingToast(value) { … }` wraps it with the 120 ms fade, keeps the last value on screen while it fades out, and swaps in a new value at once (no stacking). Both are in `:core:designsystem`, and the debug catalog shows both variants.
- **Don't:** use it for errors that block work (use a Sheet), stack two, or put an undo button in it in the editors (Undo is always visible in the transport row).
