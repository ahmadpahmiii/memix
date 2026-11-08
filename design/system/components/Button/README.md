# Button

Flat, rounded buttons. Color says importance: sky blue for the one main action, neutral gray for everything else.

- **Provide:** a label (verb first, sentence case), optional leading icon, and an action.
- **Variants:** `primary` (the one main action on a screen: Create, Export), `secondary` (default), `danger` (delete, discard), `ghost` (cancel, dismiss), `quiet` (text-only `primary` label for inline actions such as Remove or See all, a toast's action, and the Done that commits a mode, such as reorder mode in the video editor; a ToolPanel's Done, which only closes, stays `ghost`).
- **Do:** keep one `primary` per screen; keep labels to 1–3 words; reach `touch-target` height.
- **Don't:** add outlines, shadows or gradients; use `primary` for a second action.
- **Busy** (P2-08 review; the builder's `enabled = false`, renamed): while the action this button started is running, the button and any neighbour that can't act meanwhile take `surface-raised` fill and a `text-secondary` label (5.7:1; **not** `text-muted`, which is under 4.5:1 on `surface-raised`), and the running one changes its label ("Saving…"). It is a progress state, not a disabled variant: **a control that can't act for any other reason keeps its normal look and explains on tap** (P2-07 review). Busy never lasts more than a few seconds and always has a way out (the sheet's Close cancels).
- **Pressed:** `primary` switches to `primary-pressed`; neutral buttons go from `surface-raised` to `hairline`. Nothing moves.
