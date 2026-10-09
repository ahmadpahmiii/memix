# IconButton

An icon-only control with no fill: the editor's Close, Play/Pause, Undo and Redo, and the × on a persistent Toast. First built in P1-04.

- **Provide:** a Memix icon, a screen-reader label (verb first, e.g. "Close editor", "Undo"), an action, and optionally `enabled`.
- **Look:**
  - Target `touch-target` square (48 dp). Icon 24 dp, centered, tinted `text`.
  - No fill at rest, no outline.
- **Pressed:**
  - A fill fades in behind the icon over `duration-press`, at `radius-md`, covering the full 48 × 48.
  - On `canvas`, `surface` or `stage`, the fill is `surface-raised`.
  - On `surface-raised` (inside a Toast), the fill is `hairline`, one value step lighter, per DESIGN_SYSTEM → States. Otherwise the press would be invisible.
  - Nothing moves or scales.
- **Icon change** (Play ↔ Pause): the two icons cross-fade over `duration-press`, the same with reduce motion.
- **Disabled:** the icon turns `text-muted` (4.6:1 or more on every surface, though disabled controls don't need it), taps do nothing, and screen readers hear "disabled". Use it only where greyed-out is the convention:
  - undo and redo with nothing to undo or redo
  - play with nothing to play (an empty draft, a preview error)

  Everywhere else, keep the control enabled and explain on tap (DESIGN_SYSTEM → States).
- **Focus:** `stroke-focus` ring in `focus-ring`, 2 dp outside, at `radius-md`. Keyboard only.
- **Accessibility:** one node with the label and the Button role. The label follows the state (Play or Pause), and it's never the icon's name ("Close editor", not "X").
- **Don't:** use it for the one main action (use a primary Button), put a label next to it (use a Button with a leading icon), or tint the icon `primary` (that's the active tool in the ToolBar, which has its own component).
- **Later, not blocking:** the Sheet's close button is still a private round-press variant (`radius-full`). Moving it to IconButton would give every no-fill icon control the same press shape.
