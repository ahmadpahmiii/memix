# Swatch

A round, single-choice color button for content colors: caption text and outline (P1-10), the video canvas background (P1-11), later brush colors (P2-05). Content colors come from `design/tokens.json` → `caption.palette`, never from chrome tokens.

- **Provide:** the color, its spoken name (a string resource), and whether it's selected. The "none" and "blur" variants have no color.
- **Look:**
  - A `swatch-size` (32) circle of the color, centered in a `touch-target` square, so neighbors sit 48 dp apart.
  - **Low-contrast colors** (under 3:1 against `surface`, in the current palette only Black at 1.2:1): a `stroke-hairline` ring in `text-muted` just inside the edge (5.7:1), so the swatch doesn't vanish.
  - **None** ("No outline"): a `surface-raised` circle with the same `text-muted` ring and one diagonal `stroke-selection` line in `text-secondary`, top-right to bottom-left.
  - **Blur** (canvas background, P1-11): a `surface-raised` circle with the `blur` icon at `icon-small` in `text-secondary` (5.7:1), first in the row. Spoken "Blur".
  - **Selected:** a `stroke-selection` ring in `selection` (white), 2 dp outside the circle (17.2:1 on `surface`).
  - No shadow, no check mark.
- **Layout:** a horizontally scrolling row that scrolls edge to edge, in palette order.
- **Behavior:**
  - A tap selects it and applies the color at once (one undo step).
  - Tapping the selected swatch does nothing.
  - Pressed: a `surface-raised` circle fades in behind it, the size of the selected ring, over `duration-press`. Nothing moves.
- **Accessibility:** the row is a single-choice group named by its heading ("Color", "Outline"). Each swatch reads its color name ("Yellow", "No outline"), "selected" when it is, and its position. Color is never the only cue: the name is always spoken, and the result shows on the preview.
- **Don't:** use swatches for interface colors or as a color legend, or show a swatch without a spoken name.
