# ToolPanel

The settings area for an editor tool that needs more than one tap: Volume (P1-08 for sounds, P1-09 for clips), Style and Position for text (P1-10), and later Canvas (P1-11). It takes the place of the timeline and the tool bar while the preview and the transport row stay visible and working. That way the user can play and see or hear each change. A sheet with a scrim would hide the very thing being adjusted.

- **Bounds:** exactly the region the timeline and tool bar occupy, so the preview never moves or resizes. The navigation inset sits below it in `surface`.
- **Look:**
  - `surface`, with a `stroke-hairline` `hairline` top border.
  - No scrim, no shadow, no grabber.
  - Content padding `space-4`.
- **Top row:** the tool's name in `title` on the left (a heading); **Done** (Ghost button, `touch-target` tall, white text) on the right. Done is Ghost, not blue, because it only closes a panel whose changes already applied; reorder mode's Done (Quiet, blue) commits, so the two differ on purpose.
- **Content:** Slider, Toggle, FontTile and Swatch rows, stepper rows (label, IconButton, readout, IconButton) and other rows, `space-4` apart. It scrolls inside the panel when taller; the top row stays in place. Rows wrap at large font sizes.
  - **Horizontal rows scroll edge to edge** (FontTile and Swatch rows; P1-10 review P1): the row spans the panel's full width, and the `space-4` side inset is content padding inside the scroll. At rest the first item lines up with the heading; scrolled items slide out at the screen edge, not `space-4` before it, where a hard cut reads as a layout bug and hides part of the next item.
- **Behavior:**
  - Changes apply live (Volume is heard while dragging). Each committed change (a slider release, a toggle) is one undo step.
  - There's no Cancel: undo is the way back.
  - Done, system back and Escape close the panel; the selection is kept.
  - If undo removes the item the panel edits, the panel closes.
  - It takes every touch in its region, so nothing reaches the timeline underneath.
- **What it covers is gone for everyone, not just the eye** (P1-08 review): while the panel is open, the timeline and the tool bar under it leave the accessibility tree and can't take keyboard focus. Otherwise TalkBack swipes, Switch Access and Shift+Tab reach controls nobody can see, and can activate them (WCAG 2.2 SC 2.4.11 Focus Not Obscured). The preview and the transport row above stay reachable. In Compose: `Modifier.coveredByModal(panelOpen)` (`:core:designsystem` `component/CoveredByModal.kt`, df57c04) on the root of the covered region (the timeline and the tool bar), never on the panel itself. Sheets use the same modifier (Sheet README).
- **Active tool:** its tool would show the active look (`primary` on `primary-subtle`), but in the video editor the panel covers the tool bar, so that look is never seen there. Don't build anything for it.
- **Motion:** slides up or down over 200 ms (`duration-sheet`). Reduce motion: 120 ms fade (instant on Android, DESIGN_SYSTEM → Motion).
- **Accessibility:** opening it moves focus to the title, which is also the pane title. Closing it returns focus to the tool that opened it (Volume, Style…), which is back in view once the panel has gone.
- **Photo editor variant (P2-01):** there is no timeline to reserve a fixed region, so the panel is **as tall as its content, up to 40% of the window height** (then its content scrolls under the fixed top row), and **the stage refits above it**: the canvas frame takes the largest size that fits and animates there over 200 ms as a scale about its center (reduce motion: instant), never larger than its no-panel size. The pictures aren't re-decoded for it. Panels there: Layers, Opacity, Position (the P1-10 panel for layers) and Canvas. The panel still covers the tool bar (`coveredByModal`) and the canvas stays visible and live above it, except under the Canvas panel, where canvas touches do nothing (P2-01 → Canvas panel). The top row may carry two IconButtons between the title and Done (the Layers panel's Move layer up and down); they wrap under the title at large font.
- **Don't:** stack panels, put a primary button in a panel, or open a sheet from a panel.
