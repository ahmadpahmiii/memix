# ToolPanel

The settings area for an editor tool that needs more than one tap: Volume (P1-06), Style and Position for text (P1-10), and later Canvas (P1-11). It takes the place of the timeline and the tool bar while the preview and the transport row stay visible and working. That way the user can play and see or hear each change. A sheet with a scrim would hide the very thing being adjusted.

- **Bounds:** exactly the region the timeline and tool bar occupy, so the preview never moves or resizes. The navigation inset sits below it in `surface`.
- **Look:**
  - `surface`, with a `stroke-hairline` `hairline` top border.
  - No scrim, no shadow, no grabber.
  - Content padding `space-4`.
- **Top row:** the tool's name in `title` on the left; **Done** (Ghost button, `touch-target` tall) on the right.
- **Content:** Slider, Toggle, FontTile and Swatch rows, stepper rows (label, IconButton, readout, IconButton) and other rows, `space-4` apart. It scrolls inside the panel when taller; the top row stays in place.
- **Behavior:**
  - Changes apply live. Each committed change (a slider release, a toggle) is one undo step.
  - There's no Cancel: undo is the way back.
  - Done, system back and Escape close the panel; the selection is kept.
  - If undo removes the item the panel edits, the panel closes.
- **Active tool:** while the panel is open, its tool shows the active look (`primary` on `primary-subtle`) if it's visible.
- **Motion:** slides up or down over 200 ms (`duration-sheet`). Reduce motion: 120 ms fade.
- **Accessibility:** opening it moves focus to the title. Closing it returns focus to the tool that opened it.
- **Don't:** stack panels, put a primary button in a panel, or open a sheet from a panel.
