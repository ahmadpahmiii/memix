# Button

Flat, rounded buttons. Color says importance: sky blue for the one main action, neutral gray for everything else.

- **Provide:** a label (verb first, sentence case), optional leading icon, and an action.
- **Variants:** `primary` (the one main action on a screen: Create, Export), `secondary` (default), `danger` (delete, discard), `ghost` (cancel, dismiss), `quiet` (text-only `primary` label for inline actions such as Remove or See all).
- **Do:** keep one `primary` per screen; keep labels to 1–3 words; reach `touch-target` height.
- **Don't:** add outlines, shadows or gradients; use `primary` for a second action.
- **Pressed:** `primary` switches to `primary-pressed`; neutral buttons go from `surface-raised` to `hairline`. Nothing moves.
