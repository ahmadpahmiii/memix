# ToolPanel

The settings area for an editor tool that needs more than one tap: Volume (P1-08 for sounds, P1-09 for clips), Style and Position for text (P1-10), and later Canvas (P1-11). It takes the place of the timeline and the tool bar while the preview and the transport row stay visible and working. That way the user can play and see or hear each change. A sheet with a scrim would hide the very thing being adjusted.

- **Bounds:** exactly the region the timeline and tool bar occupy, so the preview never moves or resizes. The navigation inset sits below it in `surface`.
- **Look:**
  - `surface`, with a `stroke-hairline` `hairline` top border.
  - No scrim, no shadow, no grabber.
  - Content padding `space-4`.
- **Top row:** the tool's name in `title` on the left (a heading); **Done** (Ghost button, `touch-target` tall, white text) on the right. Done is Ghost, not blue, because it only closes a panel whose changes already applied; reorder mode's Done (Quiet, blue) commits, so the two differ on purpose.
- **Content:** Slider, Toggle, FontTile and Swatch rows, stepper rows (label, IconButton, readout, IconButton) and other rows, `space-4` apart. It scrolls inside the panel when taller; the top row stays in place. Rows wrap at large font sizes.
- **Behavior:**
  - Changes apply live (Volume is heard while dragging). Each committed change (a slider release, a toggle) is one undo step.
  - There's no Cancel: undo is the way back.
  - Done, system back and Escape close the panel; the selection is kept.
  - If undo removes the item the panel edits, the panel closes.
  - It takes every touch in its region, so nothing reaches the timeline underneath.
- **What it covers is gone for everyone, not just the eye** (P1-08 review): while the panel is open, the timeline and the tool bar under it leave the accessibility tree and can't take keyboard focus. Otherwise TalkBack swipes, Switch Access and Shift+Tab reach controls nobody can see, and can activate them (WCAG 2.2 SC 2.4.11 Focus Not Obscured). The preview and the transport row above stay reachable.
- **Active tool:** its tool would show the active look (`primary` on `primary-subtle`), but in the video editor the panel covers the tool bar, so that look is never seen there. Don't build anything for it.
- **Motion:** slides up or down over 200 ms (`duration-sheet`). Reduce motion: 120 ms fade (instant on Android, DESIGN_SYSTEM → Motion).
- **Accessibility:** opening it moves focus to the title, which is also the pane title. Closing it returns focus to the tool that opened it (Volume, Style…), which is back in view once the panel has gone.
- **Don't:** stack panels, put a primary button in a panel, or open a sheet from a panel.
