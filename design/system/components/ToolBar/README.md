# ToolBar

The bottom tool bar in both editors: a horizontally scrolling row of icon-plus-label tools.

- **Provide:** the tool list for the current editor and selection, and the active tool.
- **Video editor tools (nothing selected), each added with its ticket, in this final order:**
  - Edit
  - Meme sounds (P1-08)
  - Text (P1-10)
  - Add media (P1-16): adds videos or photos after the clip under the playhead
  - then later: Audio, Stickers, Effects, Filters, Overlay, Remove BG, Speed
  - Canvas and ratio are entered from the top-bar chip. Tools are left-aligned so each keeps its place as more arrive. Before launch a tool may sit one place to the left until the tool before it is built; users only ever see the final order.
  - Empty project (no clips): Add media is the only tool.
- **Clip mode (an item is selected):**
  - The same bar switches to that item's tools: a leading 48 dp Close clip tools button (`arrow_back`), a `hairline` divider, then the tools.
  - Delete sits among the first tools, visible without scrolling at 360 dp, and uses `danger` for its icon and label.
  - The switch is a 120 ms cross-fade. Full lists: `docs/ux/specs/P1-06-clip-edits-and-audio.md`; text items in `docs/ux/specs/P1-10-text.md` (Edit text · Style · Position · Delete · Split · Move here · Duplicate).
- **Reorder mode** (video editor): the bar shows Move earlier and Move later (Secondary buttons) and Done (Quiet button) in a row that wraps at large font sizes, on the same `surface` with the same `hairline` top border (P1-06 → Reorder bar).
- **Photo editor tools:** Formats, Text, Stickers, Draw, Adjust, Filters, Remove BG, Censor, Crop.
- **Meme sounds tool:** always `primary` icon and label, because it's meme-sound UI.
- **Sound clip tools** (P1-08): Volume · Split · Delete · Move here · Duplicate. Icons `volume` (three rising bars, the tallest solid) and `move_here` (an arrow into the playhead bar).
- **Main clip tools:** Split · Volume (P1-09) · Delete · Duplicate · Detach audio (P1-09) · Reorder · Replace (P1-16; photos have no Volume or Detach audio). On a clip whose file is missing, Replace comes first, because it's the one tool that fixes it and the missing-file toast names it. Once fixed, the usual order returns. Icon `replace`: a solid clip between two curved arrows (DESIGN_SYSTEM → Iconography).
- **Active tool** (its panel or sheet is open): `primary` icon and label on a `primary-subtle` `radius-md` highlight, where it can be seen. In the video editor the Volume panel and the half-height Meme sounds sheet both cover the bar, so the look never shows there.
- **Other tools:** `text-secondary`. A tool that can't act right now keeps its look and explains on tap with a Toast. It never shows as a disabled placeholder.
- **Spoken names:** a tool's accessible name is its visible label, in every language (WCAG 2.2 SC 2.5.3 Label in Name, Level A). When the label needs more context, add it as the click label, which TalkBack reads as "Double-tap to …": Move here is named "Move here" and its click label is "Move to playhead" (P1-08 review). Never replace the name with different words.
- **Layout:**
  - `surface` bar, `toolbar-height`, `hairline` top border.
  - Items at least `tool-item-width` wide and at least `touch-target` tall; labels in `caption`. Two lines is the copy budget, not a line cap: labels are never cut.
  - At large font scales, items widen when a word can't fit, and labels may take more lines; the bar grows taller rather than cutting labels.
  - No outlines or shadows.
