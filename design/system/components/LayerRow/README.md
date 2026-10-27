# LayerRow

One layer in the photo editor's Layers panel (P2-01): a picture, a name, what is special about it, and two buttons. First used in the Layers panel; spec `docs/ux/specs/P2-01-photo-editor-shell.md` → Layers panel.

- **Provide:** the layer's thumbnail (its picture, or a placeholder when the file is missing), its name (a string resource), its position from the front and the total, whether it is selected, locked, hidden, faded (its opacity) or missing, and the callbacks: select, toggle lock, toggle visibility, plus the actions Move up, Move down, Duplicate, Delete, Position, Opacity.
- **Look:**
  - At least `touch-target` plus `space-2` tall (56dp: `layer-thumb-size` 40 plus `space-2` above and below); grows with its text. `surface-raised` fill, `radius-md`, `space-1` between rows.
  - From the start: a `layer-thumb-size` square thumbnail in `radius-sm`, `space-2` from the row's edge; `space-3`; the text column; a lock IconButton and a visibility IconButton, each 48dp.
  - **Name:** `body-strong` `text`, one line, an ellipsis at the end. An image layer is "Photo"; a number is never added (it would change when rows are reordered).
  - **Meta line** (only when needed): `label` `text-secondary`, parts in this order joined by a separator resource: Locked · Hidden · File missing · "40% opacity". It wraps and is never cut.
  - **Lock button:** `unlock` icon in `text-muted` (4.6:1) when free, `lock` in `text` when locked. **Visibility button:** `eye` in `text-secondary` when shown, `eye_off` in `text` when hidden.
- **States:**
  - **Selected:** a `stroke-selection` white ring inside the row's edge (17.2:1 on `surface`). `surface-raised` can't mark selection (1.4:1).
  - **Pressed:** `surface-raised` → `hairline`, 120 ms. Nothing moves.
  - **Hidden:** the thumbnail at 40%, the name in `text-secondary` (5.7:1), meta "Hidden", `eye_off` in `text`.
  - **Faded:** the thumbnail drawn at the layer's opacity but never below 40%, so it stays recognizable; the meta line carries the number.
  - **Missing file:** a `surface` thumbnail square with the `photo` icon in `text-muted`; meta "File missing".
  - **Lifted:** the white ring; the row follows the finger vertically; the other rows slide 200 ms to open the slot. No shadow and no scale.
- **Behavior:**
  - A tap selects the layer. A tap on the lock or visibility button toggles only that and doesn't select the row (one undo step each).
  - Long-press lifts the row (LongPress haptic); drag up or down; drop commits one reorder step. The list auto-scrolls near its edges.
  - A locked layer's row still toggles lock and visibility; reordering it explains.
- **Accessibility:**
  - **One node per row**: "Photo, layer 2 of 4" then the extra words as sentences ("Locked. 40 percent opacity."), never in the state description (which stays "selected"); actions on the row: Position, Opacity, Lock or Unlock, Hide or Show, Duplicate, Delete, Move up, Move down. The two buttons stay focusable for keyboards and Switch Access but are not extra TalkBack stops.
  - Button names: "Lock layer", "Unlock layer", "Hide layer", "Show layer".
  - Color is never the only cue: locked, hidden, faded and missing all have words and different icons.
- **Don't:** use it outside the Layers panel, show a type color or a number in the name, put the layer's tools in the row (they live on the selected-layer bar), or draw a shadow on the lifted row.
