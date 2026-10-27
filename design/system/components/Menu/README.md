# Menu

A small floating list of actions anchored to where the user pressed, for options that are useful but not worth permanent space. The first use is the timeline's zoom menu (long-press the ruler, P1-05): Zoom in, Zoom out, Show whole video.

- **Look:**
  - `surface-raised`, `radius-md`, `shadow-float`. No border.
  - Items `touch-target` tall, `body` `text`, `space-4` side padding.
  - Width fits the longest item, at least 3 × `touch-target`.
- **Placement:** above the press point when there's room, otherwise below. It's kept `space-2` inside the screen edges.
- **Items:**
  - Pressed: one value step lighter (`hairline`).
  - An item that can't act right now shows its label in `text-muted` and does nothing (Zoom in when fully zoomed in).
  - Choosing an item closes the menu, except zoom steps, which keep it open for repeated taps.
- **Closes on:** a tap outside, system back, Escape. No scrim.
- **Motion:** 120 ms fade. Reduce motion: the same.
- **Accessibility:**
  - It's a popup with a pane title.
  - Focus moves to the first item and returns to the anchor on close.
  - Items are buttons.
- **Don't:** use it for the only way to reach an action. A menu item always has another route (a gesture, a keyboard shortcut, an accessibility action).
