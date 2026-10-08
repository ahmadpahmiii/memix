# ToolBar

The bottom tool bar in both editors: a horizontally scrolling row of icon-plus-label tools.

- **Provide:** the tool list for the current editor and selection, and the active tool.
- **Video editor tools (nothing selected), each added with its ticket:**
  - Edit
  - Meme sounds
  - Text (P1-10)
  - then later: Audio, Stickers, Effects, Filters, Overlay, Remove BG, Speed
  - Canvas and ratio are entered from the top-bar chip. Tools are left-aligned so each keeps its place as more arrive.
- **Clip mode (an item is selected):**
  - The same bar switches to that item's tools: a leading 48 dp Close clip tools button (`arrow_back`), a `hairline` divider, then the tools.
  - Delete sits among the first tools, visible without scrolling at 360 dp, and uses `danger` for its icon and label.
  - The switch is a 120 ms cross-fade. Full lists: `docs/ux/specs/P1-06-clip-edits-and-audio.md`.
- **Photo editor tools:** Formats, Text, Stickers, Draw, Adjust, Filters, Remove BG, Censor, Crop.
- **Meme sounds tool:** always `primary` icon and label, because it's meme-sound UI.
- **Active tool** (its panel or sheet is open): `primary` icon and label on a `primary-subtle` `radius-md` highlight.
- **Other tools:** `text-secondary`. A tool that can't act right now keeps its look and explains on tap with a Toast. It never shows as a disabled placeholder.
- **Layout:**
  - `surface` bar, `toolbar-height`, `hairline` top border.
  - Items 64 wide and at least `touch-target` tall; labels in `caption`, up to two lines.
  - At large font scales, items widen and the bar grows taller rather than cutting labels.
  - No outlines or shadows.
