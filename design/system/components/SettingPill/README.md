# SettingPill

A small pill in an editor's top bar that shows a current setting and opens where it's changed. First use: the ratio pill (P1-11, "9:16", opens the Canvas panel). The board's quality pill ("1080p") is dropped in the P1-12 draft spec: export settings live in the Export sheet.

- **Provide:** the value as short text ("9:16"), a spoken name that contains it ("Canvas, 9:16"), whether what it opens is open, and the action.
- **Look:**
  - `surface-raised` fill, `radius-full`, no border, no icon.
  - The value in `timecode` (`text`): settings are numbers, and Space Mono keeps their width steady.
  - `space-3` side padding, `space-2` above and below the text (32 dp drawn at 100% font), centered in a target at least `touch-target` tall and wide. It grows with the text at large font sizes; the value never truncates.
- **Open** (its panel or sheet is showing and the pill is still visible): `primary` text on `primary-subtle`, the active-tool look (6.2:1 over `canvas`).
- **Pressed:** `surface-raised` → `hairline`; open: `primary-subtle` → `surface-raised` (5.9:1). Nothing moves.
- **Behavior:** a tap opens its panel or sheet; a tap while open closes it. When the editor is in a mode that can't open it (reorder mode), it explains on tap with a toast instead of looking disabled.
- **Accessibility:** a button. Its name contains the visible value (WCAG 2.2 SC 2.5.3). When it opens a panel, its state is spoken through the **state description** ("Expanded" or "Collapsed", string resources in all five languages, the way Material 3's dropdown anchor does it). Compose's `expand`/`collapse` semantics are actions, not a state: TalkBack lists them but doesn't say whether the panel is open (P1-11 review M1). Keep the actions too.
- **Don't:** use it as a filter (that's Chip), for an action with no setting to show (that's Button or IconButton), or anywhere but an editor's top bar.
