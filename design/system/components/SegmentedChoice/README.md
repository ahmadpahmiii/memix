# SegmentedChoice

A row of two to five equal segments for picking one setting value: the Export sheet's resolution (480p to 4K) and frame rate (24, 30, 60 fps), P1-12. Unlike SegmentedTabs, which switches between views of a screen, this sets a value.

- **Provide:** a short label per option (a few characters: "1080p", "30 fps"), the selected one, and for each option whether it can act now (with its explanation).
- **Look:**
  - A `canvas` track, `radius-md`, `space-1` padding; segments of equal width, `space-1` apart, each at least `touch-target` tall, `radius-sm`.
  - Unselected: no fill, label in `text` (16.9:1 on `canvas`).
  - Selected: `selection` (white) fill, label in `on-primary` (18.9:1), like a selected chip. This replaces the board's `surface-raised` pill: white on `canvas` makes the selection itself clear (SC 1.4.11), where `surface-raised` on `canvas` is only 1.4:1.
  - Labels in `body-strong`, centered, never truncated.
- **Can't act** (the phone can't export it): the normal look; a tap explains with a toast and keeps the current selection. Never dimmed, never hidden.
- **Pressed:** unselected segments fill `surface-raised` over `duration-press`. Nothing moves.
- **Large text:** when the labels don't fit their equal widths (200% font), the segments wrap into two rows of equal segments; the track grows.
- **Accessibility:** a single-choice group named by its heading ("Resolution"). Each segment reads its label (spoken form: "1080p", "30 frames per second"), "selected" when it is, and its position ("3 of 5").
- **Don't:** use it for more than five options (use OptionTiles in a scrolling row), for switching views (SegmentedTabs), or for filters on browse screens (Chip).
