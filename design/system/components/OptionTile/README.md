# OptionTile

A single-choice tile with a small picture and a short label, for settings whose options read best as shapes. First used in the Canvas panel (P1-11): the ratio row (stacked) and Fit / Fill (inline).

- **Provide:** the label (a string resource), the picture (an icon, or a shape drawn from data, such as a ratio's rectangle), an optional spoken name that starts with the label ("9:16, tall", "Fit, shows the whole clip"), and whether it's selected.
- **Layouts:**
  - **Stacked:** the 24 dp picture over the label (`label`), `space-1` apart; `space-3` side padding, `space-2` above and below. At least `touch-target` wide. Used in a horizontally scrolling row with `space-2` gaps that scrolls edge to edge, so the cut-off tile shows there's more. The selected tile is scrolled into view when the row first shows and whenever the selection changes from outside the row (undo, redo), so the white tile is never off screen (P1-11 review).
  - **Inline:** the 24 dp picture beside the label, `space-2` apart; `space-3` side padding; at least `touch-target` tall. Used in pairs at the end of a labeled row; the pair wraps under the label when it doesn't fit, and the two tiles wrap onto their own lines when even that is too narrow, so a tile is never squeezed.
- **Look:**
  - `radius-md`. Unselected: `surface-raised` fill, label in `text` (12.4:1), picture in `text-secondary` (5.7:1).
  - Selected: `selection` (white) fill, label and picture in `on-primary` (18.9:1), like a selected chip or FontTile.
  - No outline, no shadow, no check mark: the white fill is the selection.
  - A drawn shape follows the icon grammar: solid, 1 dp corners, its long side 20 dp inside the 24 dp box. Shapes drawn from data so far: the ratio rectangles (P1-11), and, in the photo editor (P2-02), the five meme formats, Panels' cell counts and layouts, and a bubble's three shapes. A format shape may use one stroked outline beside its solid parts (single color).
- **Behavior:**
  - A tap selects it and applies the setting at once (one undo step in the editors).
  - Tapping the selected tile does nothing.
  - Pressed: unselected goes `surface-raised` → `hairline`. Nothing moves.
- **Accessibility:** the row is a single-choice group named by its heading ("Ratio", "Clips"). Each tile reads its name (the label, or a spoken name that starts with it), "selected" when it is, and its position ("1 of 6"). Never put a hint in the state description: on a selectable control it replaces the spoken "selected" (P1-11 review M2). At 200% font the label grows and the tile widens; the label never truncates.
- **Don't:** use it for fonts (FontTile), colors (Swatch) or filters on browse screens (Chip), or show a picture without a label.
