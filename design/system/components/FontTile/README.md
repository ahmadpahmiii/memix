# FontTile

A single-choice tile that shows a caption font by writing its name in that font. First used in the Style panel (P1-10); the photo editor reuses it (P2-03).

- **Provide:** the font's tile name (a string resource), the font to draw it in, and whether it's selected.
- **Look:**
  - Height at least `touch-target`; `space-4` side padding; `radius-md`. The width follows the name.
  - Name in `caption-sample` (18/28), set in the caption font itself.
  - Unselected: `surface-raised` fill, name in `text` (12.4:1).
  - Selected: `selection` (white) fill, name in `on-primary` (18.9:1), like a selected chip.
  - No outline, no shadow, no check mark: the white fill is the selection.
- **Layout:** a horizontally scrolling row with `space-2` gaps that scrolls edge to edge, so the cut-off tile shows there's more.
- **Behavior:**
  - A tap selects it and applies the font at once (one undo step).
  - Tapping the selected tile does nothing.
  - Pressed: unselected goes `surface-raised` → `hairline`. Nothing moves.
- **Accessibility:** the row is a single-choice group named by its heading ("Font"). Each tile reads its name, "selected" when it is, and its position ("2 of 5"). At 200% font the tiles grow taller and wider; the name never truncates.
- **Hindi:** the name is translated and drawn in the font's own Devanagari (Classic's comes from Teko).
- **Don't:** use it for anything but choosing a font, or show a font name the user doesn't see in that font.
