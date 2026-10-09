# Text, basic · P1-10

Fourth spec for the video editor. It adds to the frame and regions of the other three and doesn't repeat them:
- `P1-04-editor-and-preview.md`: frame, preview stage, transport, undo/redo, back order, toasts.
- `P1-05-timeline.md`: lanes (text is lane 3, `track-text`), selection, labels, TalkBack items.
- `P1-06-clip-edits-and-audio.md`: tool bars, ToolPanel, Split/Trim/Delete/Duplicate/Move, snapping, value bubble, edit names.
- **P1-10 (this file):** adding and editing text, the five caption fonts, color and outline, moving, sizing and turning text on the preview, the non-gesture route for all of that, timing on the timeline, and the caption rendering rules that make preview and export match.

## Job
The caption is half of most memes: "When the build passes first try" over the clip. The job is to put words on the exact moment, readable over any video, in under a minute.

**Done when** (TICKETS): text looks the same in preview and export.

**Performance bar (owner, 8 Oct):** no visible lag and no memory leaks, on Android and iOS. Typing, dragging and pinching follow the finger at 60 fps on the owner's phone (release-type build); a committed text edit reaches the preview within 100 ms (the P1-06 rule).

**Fast path, with tap counts:**
- **Add a caption:** Text (1), type, Done (2). It lands at the playhead, in the classic style, at the top of the frame.
- **Change its look:** Style (3), a font or color (4). Changes show live; Done or back closes the panel.
- **Second caption in the same style:** Text, type, Done. A new text takes the last style used in this editor visit.

**Color roles:**
- **Blue:** no blue in this ticket. The editor's one blue action stays Export (P1-12). Text tools use the normal tool look; an open panel's tool gets the active look (`primary` on `primary-subtle`).
- **White:** selection: the frame around the selected text on the preview, its handle, the selected font tile and the selected swatch ring; on the timeline, the usual outline and trim handles.
- **Orange:** text items on the timeline (`track-text`).
- **Caption colors** (white, yellow, …) are the user's content, not chrome. They live in their own palette (Caption rendering → Palette) and never appear in the interface except inside swatches.

**Later, not P1:** keyboard shortcuts (L-01). Shadow and background box are PRD v1 features with no ticket yet (Decision for the owner). Animations and presets are P4-11; quick top/bottom caption is P4-14.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Typing (entry bar above the keyboard) | The Text tool (new text); Edit text; a tap on the selected text on the preview; double-tap on a text node in TalkBack | Done, system back, Escape, or the keyboard is dismissed. All of them **keep** what was typed (Typing → Commit). |
| Text clip tools | A text is selected: a tap on it in the preview or on the timeline, or Done after typing | P1-06 rules: back button, tap on empty timeline, system back, or the text is deleted |
| Style panel (ToolPanel) | The Style tool | Done, system back, Escape. The text stays selected. |
| Position panel (ToolPanel) | The Position tool | Same |

- **Back order** (P1-04) gains one step at the front: **typing commits and the keyboard closes** → menu → panel → drag in progress (canvas or timeline) is cancelled → reorder → selection → editor.
- **Opening Edit text, Style or Position while the playhead is outside the text's time** first moves the playhead to the text's start (the 200 ms ruler-tap scroll), so the user sees what they're changing. Selecting alone still never moves the playhead (P1-05).
- **Kept:** every text edit is in the project (autosave, undo). Not kept: the "last style used" memory, which lasts for one editor visit.

## Layout
Boards: `design/screens/VideoEditor.dc.html` (the caption on the preview, the Text tool in slot 3) and `design/screens/PhotoEditor.dc.html` (the selected text frame and the board's text bar, which this spec turns into the Style panel).

**Differences from the boards, and why:**
1. **The photo board's text bar** (an "Impact style" chip, three 32 px swatches, an "Outline" chip in a 52 px bar) becomes the Style ToolPanel. A one-row bar can't hold five fonts and two color rows at 48 dp targets, and the Chip component isn't for editors.
2. **One handle, not four.** The photo board draws four 10 px corner squares. On a 186 dp preview, four handles crowd the text and none is a real target. The video editor shows the frame plus one 48 dp scale-and-rotate handle.
3. **The selection frame gets a dark edge** (below), because plain white vanishes over a white wall or sky.
4. **Captions are sized by the canvas, not by `meme-caption`'s 36 sp.** The board caption is 20 px only because the board's frame is small. In the editors, size follows Caption rendering → Geometry. `meme-caption` stays the token for interface samples.

### Main tool bar: Text (slot 3, reserved by P1-06)
- Icon `text` (new: a "T", the crossbar as the solid core), label "Text", the normal tool look (`text-secondary`).
- **Tap:** pauses playback and goes straight to Typing for a new text. No intermediate menu: CapCut asks for a second tap ("Add text"), and TikTok and Instagram open the keyboard at once (Assumption from third-party guides; see Evidence).

### Text clip tools (a text is selected)
The P1-06 clip tool bar (Close clip tools, divider, tools). Order:

**Edit text · Style · Position · Delete · Split · Move here · Duplicate**

- At 360 dp the first four are fully visible, so Delete stays in view without scrolling (ToolBar rule).
- Edit text, Style and Position come first because they're what people do with a caption. Delete moves ahead of Split because a caption is deleted far more often than cut in two (Assumption). This replaces P1-06's provisional text row.
- New icons: `edit_text` (a "T" beside a text cursor bar), `style` (an "Aa": the "A" stroked, the "a" solid), `position` (four arrows from a solid center square). Delete, Split, Move here and Duplicate reuse P1-06's.
- Edit text, Style and Position have no "explain" state: they always act.

### Typing: the entry bar
While typing, the editor shows, top to bottom:
1. **Top bar** (unchanged; Close works, and commits first).
2. **Preview stage.** It fills the space between the top bar and the entry bar. The canvas frame refits inside it with the usual `space-2` inset, but **never gets bigger** than it was before typing. So on tall phones the preview doesn't move, and on short phones it shrinks instead of hiding under the keyboard.
3. **Entry bar,** riding on top of the keyboard.
4. **The keyboard.**

The transport row, timeline and tool bar sit under the keyboard and aren't shown. Playback is paused.

**Entry bar look:** `surface`, `stroke-hairline` `hairline` top border, `space-2` vertical and `space-4` side padding.
- **Text field** on the left, filling the width: `surface-raised`, `radius-md`, `space-3` inner padding, at least `touch-target` tall. Text in `body` `text` (Space Grotesk; Devanagari falls back to the system face), so every language reads plainly while typing; the preview shows the styled caption live.
  - Placeholder: "Type your text" in `text-muted`.
  - It grows up to 4 lines, then scrolls inside itself.
  - Enter starts a new line. Line breaks are part of the caption, as in every meme ("Me:\nAlso me:").
- **Done** on the right: Ghost button, `touch-target` tall, bottom-aligned with the field.
- **Counter:** from 200 characters on, "230/250" in `caption` `text-muted` shows under the field, end-aligned. At 250 the field takes no more characters (Typing → Limit).

**Worked sizes** (dp, 100% font, a 280 dp keyboard):

| Phone | Stage while typing | 9:16 frame | Normal frame (P1-04) |
| --- | --- | --- | --- |
| 360 × 640, 3-button nav | 168 | 86 × 152 | 123 × 219 |
| 360 × 800, gesture nav | 352 → kept at 346 | 186 × 330 | 186 × 330 |
| 412 × 915, gesture nav | 467 → kept at 415 | 224 × 399 | 224 × 399 |

On the smallest phones the caption is small while typing. That's acceptable: the field is where the words are read while typing, and the preview shows place and style.

### The selected text on the preview
- **Frame:** a rectangle around the text's laid-out block, `space-1` outside it, turned with the text.
  - `stroke-selection` in `selection` (white), with a `stroke-hairline` line in `on-primary` just outside it.
  - **Why two tones:** over arbitrary video, plain white can be 1:1 against a white sky. White plus near-black guarantees at least 4.3:1 against any background color, one tone or the other (WCAG 2.2 SC 1.4.11 needs 3:1).
  - The frame is drawn in the stage's coordinate space, so it may extend past the canvas edge onto the black stage. Text outside the canvas is clipped, exactly as in the export.
- **Scale-and-rotate handle:** a `canvas-handle-size` circle in `selection` with a `stroke-hairline` `on-primary` edge, at the frame's bottom-right corner in every language (the canvas never mirrors). Icon `scale_rotate` (a curved two-way arrow) at `icon-small` in `on-primary`. Target: `touch-target`, centered on it.
  - If that corner is outside the stage, the handle moves to the nearest corner of the frame that's inside it.
- **Snap guides:** while snapped, a full-height line at the canvas's vertical center and/or a full-width line at its horizontal center, inside the canvas frame. Same two-tone build as the frame (`stroke-hairline` `selection` beside `stroke-hairline` `on-primary`).
- **Value bubble** while sizing or turning: the P1-06 Value bubble (`surface-raised`, `radius-full`, `shadow-float`, `timecode` in `text`), centered at the top of the stage, `space-2` below its top edge, so fingers don't cover it. It reads "120% · 15°".
- Text that isn't selected has no frame. A text that's selected but not visible at the playhead shows nothing on the preview; it's still selected on the timeline.

### Style panel (ToolPanel)
ToolPanel (`design/system/components/ToolPanel/README.md`): title "Style", Done. Rows `space-4` apart; each row has a `label` `text-secondary` heading above a horizontally scrolling row with `space-2` gaps, `space-4` side padding, scrolling edge to edge.

1. **Font:** five FontTiles (`design/system/components/FontTile/README.md`, new), in this order: Classic, Modern, Bubbly, Handwritten, Serif. Each tile shows its name **set in its own font** at `caption-sample`.
2. **Color:** ten Swatches (`design/system/components/Swatch/README.md`, new), in palette order.
3. **Outline:** a "No outline" Swatch first, then the same ten colors. "No outline" sets `outlineColor` to null.

- **Selected:** the current font's tile is white (`selection` fill, `on-primary` text); the current color and outline swatches have the white ring.
- **Every tap is applied at once,** and is one undo step ("Font", "Color", "Outline"). Tapping the already-selected option does nothing and records nothing.
- **Panel height:** about 292 dp of content. On 360 × 640 (229 dp region) it scrolls; on 360 × 800 it fits.
- The preview stays visible above the panel (ToolPanel rule), so each choice is seen on the real frame.

### Position panel (ToolPanel)
The non-gesture route for move, size and rotation, for anyone who can't or won't pinch and drag (WCAG 2.2 SC 2.5.1 and 2.5.7), and for exact nudges. Title "Position", Done. Four rows, each with a `body` `text-secondary` label at the start and its controls at the end; a row wraps under its label when it doesn't fit (200% font).

| Row | Controls | Step |
| --- | --- | --- |
| Size | IconButton "Smaller" (`minus`), readout "100%", IconButton "Bigger" (`plus`) | The next value on the ladder 25 · 33 · 50 · 67 · 75 · 90 · 100 · 110 · 125 · 150 · 175 · 200 · 250 · 300 · 400 · 500%, strictly below or above the current size. 100% is always reachable. |
| Rotation | IconButton "Rotate left" (`rotate_left`), readout "0°", IconButton "Rotate right" (`rotate_right`) | To the next multiple of 15° in that direction, so 0° is always reachable. The readout shows −180° to 180°, whole degrees. |
| Move | Four IconButtons: Move left, Move up, Move down, Move right (`arrow`, one drawing turned four ways) | 1% of the canvas width (left, right) or height (up, down) per tap. Holding repeats: after 400 ms, every 50 ms. |
| (no label) | Secondary Button "Center", end-aligned | Puts the text's center at the canvas center |

- Readouts: `timecode` in `text`, at least `touch-target` wide so the row doesn't jitter.
- **Undo:** each tap, each hold (on release) and Center is one step: "Size and rotation" for Size and Rotation, "Position" for Move and Center.
- **Limits explain on tap** (P1-06 rule): Smaller at 25% → "This is the smallest size."; Bigger at 500% → "This is the biggest size."; a Move arrow with the text's center already at that edge → "Your text can't go further this way."
- About 296 dp of content: it scrolls on 360 × 640 and fits on 360 × 800.
- No sliders: a slider is itself a drag, and the stepper reaches every useful value in a few taps.

## Caption rendering (the "Done when" rules)
Preview and export match because **one renderer** draws every caption, everywhere: the engine's overlay in the preview, the stand-in that follows the finger during typing and gestures, the export, and later the photo editor (P2-03, "the same style looks identical in both editors"). These rules are part of the saved look of every draft, so they're fixed. Changing one after P1 ships changes old drafts, which needs a schema bump and a compensating migration (CLAUDE.md rule 6). The values are also in `design/tokens.json` → `caption`.

### Geometry
| Rule | Value | Why |
| --- | --- | --- |
| Font size at scale 1 | 8% of the canvas's **shorter side** (86 px on a 1080 × 1920 canvas) | The short side is the same for 9:16 and 16:9, so a ratio change (P1-11) moves text but never resizes or re-wraps it. 8% fits about 25 Anton characters per line, so a classic two-line caption looks right. |
| Wrap width at scale 1 | 90% of the shorter side | Lines break at spaces; a word longer than a line breaks between letters. Explicit line breaks are kept. No line limit. |
| Scale | Uniform scale of the laid-out block, 25% to 500% | Scaling never re-wraps (pinching a paragraph keeps its lines). |
| Alignment | Each line centered | The meme convention. Left and right alignment would need a new field (Model). |
| Letter spacing | 0 | Tracking breaks the Devanagari headline bar (P1-17). |
| Line spacing | Each font's own metrics; a line with Devanagari takes the taller marks into account | Nothing above the headline bar or below the baseline is ever cut. |
| Outline | Stroke `font size ÷ 12`, round joins and caps, drawn first; the fill is drawn over it | Same ratio as `meme-caption` (3 px at 36 px). Round joins avoid the spikes miter joins put on Anton's sharp "M", "W" and "A". Drawn under the fill, so letters keep their shape. |
| Position | Block center at `Transform.centerX/centerY` (fractions of the canvas); turned by `rotationDegrees` clockwise around its center | Already the model's meaning |
| Center limits | `centerX` and `centerY` stay within 0…1 | The text can hang off the frame, but never get lost entirely |
| Layout resolution | Laid out once, in canvas pixels (`Canvas.widthPx` × `heightPx`), then drawn scaled to the preview or the export size | Line breaks never change with the output resolution (480p to 4K) or the preview size |
| Clipping | At the canvas edge, in preview and export | WYSIWYG |
| Draw order | Text draws above the main video. Between text lanes, a lower lane draws over a higher one, so the newest overlapping caption is on top. | P4-01 and P4-12 place overlays and stickers relative to text in their own specs. |
| Case | Exactly as typed | No stored "all caps" flag (Model). Classic opens the keyboard in caps instead (Interactions). |

### Fonts
Five caption fonts, the PRD's "Anton plus 4 more". Every one covers all five launch languages: English, Indonesian, Spanish and Portuguese need Latin with accents (á ã ç ê ñ õ ¿ ¡), and Hindi needs Devanagari.

| Id (saved, never renamed) | Tile name | File | Size | Scripts | Look | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `anton` | Classic | `anton_regular.ttf` (bundled since P0-04) | already bundled | Latin, Latin Ext, Vietnamese. **Devanagari from Teko Bold** (below). | The Impact meme: heavy, condensed caps | [google/fonts ofl/anton](https://github.com/google/fonts/tree/main/ofl/anton) |
| `poppins` | Modern | `poppins_bold.ttf` = `Poppins-Bold.ttf` | 152 KB | Latin, Latin Ext, Devanagari | Clean geometric sans: the TikTok and Instagram caption look and the "caption bar" meme | [google/fonts ofl/poppins](https://github.com/google/fonts/tree/main/ofl/poppins) (Indian Type Foundry) |
| `baloo2` | Bubbly | `baloo2_extrabold.ttf` = `Baloo2-ExtraBold.ttf` | 348 KB | Latin, Latin Ext, Vietnamese, Devanagari | Round, heavy, playful: cartoon and reaction memes | [yanone/Baloo2-Variable `fonts/ttf/Baloo2/`](https://github.com/yanone/Baloo2-Variable/tree/master/fonts/ttf/Baloo2), the upstream google/fonts names (Ek Type) |
| `kalam` | Handwritten | `kalam_bold.ttf` = `Kalam-Bold.ttf` | 450 KB | Latin, Latin Ext, Devanagari | Felt-tip handwriting: notes, labels, "me:" annotations | [google/fonts ofl/kalam](https://github.com/google/fonts/tree/main/ofl/kalam) (Indian Type Foundry) |
| `rozha` | Serif | `rozha_one_regular.ttf` = `RozhaOne-Regular.ttf` | 315 KB | Latin, Latin Ext, Devanagari | High-contrast display serif: dramatic and ironic "movie title" captions | [google/fonts ofl/rozhaone](https://github.com/google/fonts/tree/main/ofl/rozhaone) (Indian Type Foundry) |

- **Licenses:** all SIL OFL 1.1, none with a Reserved Font Name (checked in each family's `OFL.txt`, 9 Oct 2026). Each `OFL.txt` ships with the app's font licenses (PRD → Credits screen).
- **Added size:** 1,265 KB of TTF (less once compressed in the bundle), about 2.5% of the 50 MB download budget. Teko adds nothing here; P1-17 already bundles it.
- **Anton's Devanagari partner is Teko Bold** (P1-17's file, 251 KB, already bundled). With the Classic font, every Devanagari character is drawn in Teko and everything else in Anton, so a Hinglish caption ("जब BUILD PASS हो") keeps the classic heavy, condensed look in both scripts.
  - Teko is a fallback, not a sixth tile: a "Teko" tile would draw Hindi text exactly like Classic.
  - Whole Devanagari clusters (consonant, conjuncts, vowel signs) always stay in one font, or shaping breaks.
  - Teko runs use the same font size. The engineer's hand-check screenshots a mixed line; if Teko looks smaller than Anton's capitals, I'll set a size factor in this table before the ticket closes.
- **Anything none of the five fonts has** (emoji, other scripts) falls back to the system font: Noto Color Emoji and Noto on Android, Apple's on iOS. Emoji are drawn without an outline. Because the same renderer draws preview and export, they match on that phone.
- **Choosing the five** (9 Oct 2026; sizes from the google/fonts file pages): the five cover the main caption styles users know from TikTok and Instagram (classic, clean sans, rounded, handwriting, serif), each draws every launch language natively, and the total stays near 1 MB.

| Considered | Why not |
| --- | --- |
| Teko as its own tile (0 KB, already bundled) | It's Anton's Devanagari partner, so for Hindi text it would look exactly like Classic |
| Bangers (comic book, 91 KB) | No Devanagari. Hindi captions would fall back to another face and look different from the tile. |
| Yatra One (brush sign painting, Devanagari, 270 KB) | A strong but very regional look. Second choice if Serif tests badly. |
| Baloo 2 variable (667 KB) | The static ExtraBold is about half the size, and only one weight is used |
| Permanent Marker, Luckiest Guy | Apache 2.0, not OFL (the brief asks for OFL) |

### Palette
Ten caption colors, used for both text and outline, opaque. They're content, kept in `design/tokens.json` → `caption.palette`, never in `MemixColors`. They deliberately avoid the rejected v1 values.

| Order | Name | Hex |
| --- | --- | --- |
| 1 | White | `#FFFFFF` |
| 2 | Black | `#000000` |
| 3 | Yellow | `#FFD60A` |
| 4 | Orange | `#FF9500` |
| 5 | Red | `#FF3B30` |
| 6 | Pink | `#FF6BC1` |
| 7 | Purple | `#AF52DE` |
| 8 | Blue | `#0A84FF` |
| 9 | Light blue | `#64D2FF` |
| 10 | Green | `#30D158` |

- **Default style:** Classic, White, Black outline: the meme look that stays readable over any video.
- **Custom colors** (a picker, an eyedropper) aren't in P1.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Nothing selected | Main tool bar with Text in slot 3 | `tool_text` |
| Typing a new text, empty | Entry bar with the placeholder and Done; on the preview, an empty frame (`touch-target` square) at the landing spot shows where the text will go; transport, timeline and tool bar under the keyboard | `text_entry_hint`, `text_entry_label`, `panel_done` |
| Typing, with text | The caption renders live in its style on the preview with its frame | none |
| Typing, near the limit (200+) | Counter under the field | `text_entry_count`, `text_entry_count_a11y` |
| Typing, at the limit (250) | Field takes no more; TalkBack hears it once | `text_entry_limit_a11y` |
| Committed new text | Keyboard closes; the text is selected (frame, handle); its orange item bonks in on the text lane, which slides in if it's new (P1-05); text clip tools | `a11y_text_added` |
| Text selected | Frame and handle on the preview (if visible at the playhead); white outline and trim handles on the timeline; text clip tools | `tool_edit_text`, `tool_style`, `tool_position`, P1-06 `tool_*` |
| Selected text not visible at the playhead | Nothing on the preview; timeline selection only. Edit text, Style and Position first move the playhead to its start. | none |
| Moving on the preview | Text follows the finger; guides when snapped | none |
| Sizing or turning | Value bubble at the top of the stage; guides when snapped | `text_transform_bubble` |
| Style panel | Font tiles, color and outline swatches with the current ones selected | `style_*`, `font_*`, `color_*`, `outline_none` |
| Position panel | Size, Rotation, Move rows and Center, readouts current | `position_*` |
| Size or move limit | Toast explains | `explain_text_smallest`, `explain_text_biggest`, `explain_text_edge` |
| Several texts at once | Each on the preview; a tap picks the top one; overlapping ones on separate text lanes | none |
| Text past the end of the video | Timeline wash (P1-05); not drawn in preview or export after the end | P1-05 |
| Long text (250 characters, 5 languages) | Wraps at the wrap width; may run past the frame (clipped); the timeline label shows the first line with an ellipsis | none |
| Hindi or mixed text | Devanagari in each font's own Devanagari (Teko for Classic); lines taller where marks need it | none |
| A draft names a font that isn't bundled (defensive: a newer build's draft) | Drawn in Classic; no tile selected in Style; the id is kept until the user picks a font | none |
| Empty project (P1-04) | Tool bar has no tools, so no Text | none |
| Offline, permissions | Don't apply: everything is on the phone | none |
| First run | Same as every run. No coach marks. | none |

## Interactions

### Typing → commit
- **New text,** on Done, back, Escape, Close or the keyboard going away:
  - With any non-blank text: one undo step ("Add text"). The item lands (below) and becomes the selection.
  - Blank (empty or only spaces and line breaks): nothing is added and nothing is recorded. Back to the main tool bar.
- **Editing a text,** same triggers:
  - Changed: one undo step ("Edit text").
  - Unchanged: nothing recorded.
  - Cleared to blank: the text is deleted, as one undo step ("Delete"). Undo brings it back. No confirmation (P1-06: undo instead of warnings).
- **Why closing keeps the text:** what someone typed is their work, and undo removes it in one tap. Losing it to a stray back gesture would break the Forgiving principle.
- **Live preview while typing:** each keystroke shows on the preview within one frame with no visible lag, through the renderer's stand-in. The project changes once, on commit.
- **Keyboard:** multi-line, Enter makes a new line, no autocorrect override (the system keyboard's own settings apply). **Capitalization:** with the Classic font, the field asks the keyboard for all caps (Compose `KeyboardCapitalization.Characters`); other fonts ask for sentence case. The user can switch either way, and the text is stored exactly as typed. Impact-style memes are all caps by convention, and this gets that without a stored flag. Keyboards without case (Hindi) ignore it.
- **Editing** opens with the cursor at the end, nothing pre-selected, so a first keystroke can't wipe the text.

### Landing (new text)
- **When:** starts at the playhead and lasts 3 s or to the end of the video, whichever comes first. If that leaves under 1 s (the playhead is near the end), it's placed to end at the end of the video: start = the larger of 0:00 and end − 3 s. A caption at the very end would otherwise flash by. 3 s matches the still-photo length (P1-02).
- **Where on the frame:** centered horizontally, scale 100%, rotation 0°. Vertically, the first free slot of: top (`centerY` 0.25), bottom (0.75), middle (0.5). A slot is taken when a text visible at the playhead has its center within 0.1 of it. If all are taken, the middle. Top first because a video meme's caption usually sits above the action, and TikTok's and Reels' own buttons cover the bottom and right edges (PRD → Watermark, Assumption).
- **Which lane:** the first text lane free for the whole span, otherwise a new text lane under the last one (the P1-06 lane rule).
- **Style:** the last style the user picked in this editor visit (font, color, outline), otherwise the default. Duplicate copies the style too.
- **Then:** the timeline item bonks (`duration-bonk`), the lanes scroll to show it if needed, TalkBack hears "Text added at 3.2 seconds". No haptic: Confirm is reserved for meme sounds landing (P1-06).

### On the preview
| Gesture | On | Result |
| --- | --- | --- |
| Tap | A text visible at the playhead | Pauses; selects it (frame, timeline selection, text clip tools). Topmost text wins where they overlap. The hit area is the text's frame, grown to at least `touch-target` in each direction. |
| Tap | The selected text | Opens Typing to edit it |
| Tap | The stage outside every text | Play or pause (P1-04), whatever is selected. A text selection is cleared the P1-06 ways (back button, empty timeline, system back), not by a stage tap, so a tap meant to play never throws away the selection. |
| Drag (one finger) | Any visible text | Pauses, selects it if it isn't, and moves it. The text follows the finger 1:1. |
| Drag | The handle | Scales and turns the text around its center: the distance from the center sets size, the angle sets rotation |
| Two fingers (pinch, twist, pan) | Anywhere on the stage, while a text is selected | Scale, rotation and position together. The fingers don't need to be on the text: on a small preview, the text is often smaller than two fingertips. With nothing selected, two fingers do nothing. |
| Long-press | A text | Same as tap (no separate meaning) |

- **Snapping** (each engagement fires one SegmentTick haptic and shows its guide; moving past the range releases it):
  - Move: the text's center snaps to the canvas's vertical center line and to its horizontal center line, each within `space-2` on screen.
  - Rotation snaps to 0°, 90°, 180° and 270° within 4°.
  - Size snaps to 100% within 3%.
- **One gesture = one undo step,** committed on release: "Position" if only the place changed, otherwise "Size and rotation". During the gesture only the stand-in moves; the engine gets the change on release (P1-06 rule), with no visible jump or flash when the engine's render takes over.
- **Limits during gestures:** size stops at 25% and 500%, the center stops at the canvas edges. No toast while dragging; the text simply stops.
- **Playback:** any touch on a text pauses first (P1-04 rule for edits).
- **Undo during a gesture** is ignored, as on the timeline.

### On the timeline
Text items follow every P1-05/P1-06 rule for non-main items. Specifically:
- **Label:** the first line of the text, trimmed, in `caption` `on-track` on `track-text` (7.5:1).
- **Trim:** handles, with no source limit (like photos); at least 0.1 s; snapping as P1-06. The value bubble shows the duration. While trimming, the preview stays on the playhead's frame.
- **Move in time:** long-press and drag, or Move here (to the playhead; at the very end, it's placed to end there). Lane rule as P1-06.
- **Split:** two texts with the same words, style and transform, meeting at the playhead. Selection goes to the right piece.
- **Delete, Duplicate:** as P1-06. A duplicate goes right after the original, same place on the frame.
- **Main-track ripple doesn't move text** (P1-06 rule, the same open question as sounds).

### Haptics
| Event | Type |
| --- | --- |
| A move, rotation or size snap engages | SegmentTick, once per engagement |
| Long-press lifts a text item on the timeline | LongPress (P1-06) |
| Anything else, including adding text | none |

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Entering or leaving Typing | The entry bar rides on the keyboard's own animation; the stage refits with the keyboard inset frame by frame | Same (the system drives the keyboard) |
| Text item lands on the timeline | The bonk (`duration-bonk`); a new lane slides in over 200 ms (P1-05) | 120 ms fade-in |
| Selection frame and handle appear or go | 120 ms fade | Same |
| Style or Position panel | 200 ms slide (ToolPanel) | 120 ms fade |
| Playhead moves to the text's start (Edit text, Style, Position) | 200 ms scroll, like a ruler tap | Jumps |
| Font, color, outline, Position steps | Apply instantly on the preview | Same |
| Snap guides, value bubble | 120 ms fade | Same |
| Toasts | 120 ms fade (P1-04) | Same |

## Copy
English source; the engineer machine-drafts id, es, pt and hi until P5. Budgets in characters. Tool labels: two lines of about 10 characters (P1-06).

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `tool_text` | Text | 20 | Main tool: adds a caption |
| `tool_edit_text` | Edit text | 20 | Change the words |
| `tool_style` | Style | 20 | Font and colors |
| `tool_position` | Position | 20 | Place, size and angle on the frame. Not time. |
| `text_entry_label` | Your text | 20 | Screen reader name of the field |
| `text_entry_hint` | Type your text | 30 | Placeholder |
| `text_entry_count` | %1$d/%2$d | 9 | "230/250"; use your language's digits |
| `text_entry_count_a11y` | %1$d of %2$d characters | 40 | Spoken with the counter |
| `text_entry_limit_a11y` | Character limit reached | 32 | Spoken once at 250 |
| `style_title` | Style | 20 | Panel title |
| `style_font` | Font | 20 | Row heading |
| `style_color` | Color | 20 | Row heading: the letters' color |
| `style_outline` | Outline | 20 | Row heading: the line around the letters |
| `font_classic` | Classic | 14 | Shown in the font itself, so keep it short. The classic meme font. |
| `font_modern` | Modern | 14 | Clean, simple letters |
| `font_bubbly` | Bubbly | 14 | Round, playful letters |
| `font_handwritten` | Handwritten | 14 | Looks written with a marker. A shorter word ("Hand") is fine. |
| `font_serif` | Serif | 14 | Letters with small feet, like a newspaper or film title. TikTok's word for it in your language is a good guide. |
| `color_white` | White | 16 | Spoken names of swatches |
| `color_black` | Black | 16 | |
| `color_yellow` | Yellow | 16 | |
| `color_orange` | Orange | 16 | |
| `color_red` | Red | 16 | |
| `color_pink` | Pink | 16 | |
| `color_purple` | Purple | 16 | |
| `color_blue` | Blue | 16 | |
| `color_light_blue` | Light blue | 16 | |
| `color_green` | Green | 16 | |
| `outline_none` | No outline | 20 | Spoken name of the first outline swatch |
| `position_title` | Position | 20 | Panel title |
| `position_size` | Size | 16 | Row label |
| `position_rotation` | Rotation | 16 | Row label |
| `position_move` | Move | 16 | Row label |
| `position_center` | Center | 16 | Button: put the text in the middle of the frame |
| `position_smaller` | Smaller | 20 | Spoken label of − |
| `position_bigger` | Bigger | 20 | Spoken label of + |
| `position_rotate_left` | Rotate left | 20 | Counterclockwise |
| `position_rotate_right` | Rotate right | 20 | Clockwise |
| `position_move_left` | Move left | 20 | |
| `position_move_up` | Move up | 20 | |
| `position_move_down` | Move down | 20 | |
| `position_move_right` | Move right | 20 | |
| `position_size_value` | %1$d%% | 6 | "120%"; your language's percent format |
| `position_rotation_value` | %1$d° | 6 | "15°" |
| `a11y_degrees` | one: %d degree · other: %d degrees | 20 | Plural resource; spoken rotation |
| `text_transform_bubble` | %1$s · %2$s | 16 | "120% · 15°" |
| `a11y_text_transformed` | %1$s, %2$s | 40 | Announced after a size or rotation change: "120%, 15 degrees" |
| `explain_text_smallest` | This is the smallest size. | 40 | |
| `explain_text_biggest` | This is the biggest size. | 40 | |
| `explain_text_edge` | Your text can't go further this way. | 50 | |
| `a11y_text_on_canvas` | Text: %1$s | 270 | Name of a caption on the preview; %1$s = all its words |
| `a11y_text_added` | Text added at %1$s | 50 | %1$s = spoken time (P1-04 `a11y_time_*`) |
| `edit_add_text` | Add text | 24 | Edit names, after "Undo: " (P1-04) |
| `edit_edit_text` | Edit text | 24 | |
| `edit_text_font` | Font | 24 | |
| `edit_text_color` | Color | 24 | |
| `edit_text_outline` | Outline | 24 | |
| `edit_move_text` | Position | 24 | Moved on the frame |
| `edit_transform_text` | Size and rotation | 24 | |

Translator notes:
- **"Text"** here means a caption the user adds to the video. Use the word your language's video apps use for that tool (CapCut's local label is a good guide).
- **Font names** are style descriptions, not brand names. They're drawn in the font they describe, and the tile grows to fit, but long words crowd the row.
- **Position vs Move here:** "Position" is where the text sits on the picture. "Move here" (P1-06) moves an item in time to the playhead. Keep them clearly different.
- **Hindi:** the font tiles show these names in each font's Devanagari; Classic's comes from Teko.

## Accessibility
- **Focus and TalkBack order** (P1-04's list, with the preview's children):
  1. Preview ("Preview, 9:16", click = Play/Pause), then **each text visible at the playhead**, top of the frame to bottom: "Text: WHEN THE BUILD PASSES", state "selected" when selected. Double-tap selects it, or opens editing if it's already selected. Actions: Style, Position.
  2. Timeline (P1-05): text items read `a11y_item` ("Text, WHEN THE BUILD…, 3 seconds long, starts at 1.2 seconds") with P1-06's actions (Split, Delete, Duplicate, Move to playhead), plus **Edit text**.
  3. Tool bar.
- **The handle** is hidden from accessibility services: it's drag-only, and the Position panel is the full alternative. No node that can't be operated.
- **Single-pointer, no-drag alternatives** (WCAG 2.2 SC 2.5.1 and 2.5.7):

  | Gesture | Alternative |
  | --- | --- |
  | Drag to move on the frame | Position → Move arrows and Center |
  | Pinch or handle drag to size | Position → Smaller / Bigger |
  | Twist or handle drag to turn | Position → Rotate left / right |
  | Drag in time, trim | P1-06: Move here, Split, "Trim to playhead" |

- **Typing:** opening it moves focus to the field, which reads "Your text". The keyboard is the system's. Done is the next focus stop. On commit, focus goes to the text on the preview (or, if it isn't visible, its timeline item), and the announcement plays.
- **Panels:** ToolPanel rules: focus to the title on open, back to the tool on close. Font tiles and swatches are single-choice groups ("Font", "Color", "Outline"): each option has its name, the selected one reads "selected", and the group reads its position ("2 of 5"). Steppers: each IconButton has its spoken label, and the readout is the row's state ("Size, 120%"). After each step, `a11y_text_transformed` or the new position is announced politely.
- **Keyboard in P1** (basic access, not shortcuts): Tab reaches the preview's text nodes, the field, Done, every tool and every panel control; Enter activates. In the field, Enter makes a new line and Tab moves on. Escape = system back (commit while typing). The Position panel is how a keyboard user moves, sizes and turns text. **Shortcuts (arrow-key nudges and so on) are Later (L-01).**
- **Touch targets:** tools, Done, tiles, swatches (32 dp drawn in 48 dp targets), stepper buttons, the handle and every text's hit area are 48 dp or more.
- **Contrast:**
  - Selection frame, handle and guides: white with a near-black edge, at least 4.3:1 against any video color. The handle's icon: `on-primary` on white, 18.9:1.
  - Field text `text` on `surface-raised` 12.4:1; placeholder and counter `text-muted` 4.6:1 or more.
  - Font tiles: `text` on `surface-raised` 12.4:1; selected `on-primary` on `selection` 18.9:1.
  - Swatches: the selected ring `selection` on `surface` 17.2:1. Black (1.2:1 on `surface`) and "No outline" get a `stroke-hairline` ring in `text-muted` (5.7:1), so they don't vanish.
  - Value bubble `text` on `surface-raised` 12.4:1. Timeline label `on-track` on `track-text` 7.5:1.
  - Caption colors are the user's content. The default (white with a black outline) is readable on any frame.
- **200% font scale:**
  - The entry field and its counter grow; the stage shrinks further while typing (it never covers the field).
  - Tool labels wrap (P1-06). Panel headings and labels grow; font tiles grow taller and wider (`caption-sample` scales); Position rows wrap their controls under the label.
  - **Captions on the preview don't follow the system font size.** They're the meme's content, sized by the canvas, and must match the export.
  - Devanagari in the interface uses the system face with its taller line height; nothing has a fixed text height.
- **Reduce motion:** see Motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | Once per committed undo step (never during typing, a drag or a hold; never when a tool only explains) | `editor: "video"`, `tool`: `add_text`, `edit_text`, `text_font`, `text_color`, `text_outline`, `position_text`, `transform_text`; and P1-06's `split`, `trim`, `delete`, `duplicate`, `move` when they act on text. A text cleared to blank logs `delete`. With `add_text` and `text_font` it also sends `font` (the font id: `anton`, `poppins`, `baloo2`, `kalam`, `rozha`; owner, 9 Oct 2026). |

- These ids are the edit names in the undo history (TECHNICAL_DESIGN → Editing), so each must be confirmed by the PM at the ready check, as P1-06's were.
- Opening Typing, Style or Position, selecting, and tapping an already-selected option fire nothing.
- Analytics stay off until consent (P5-01).

## QA compares
1. **Typing a new caption** at 360 × 800: entry bar on the keyboard, the live caption in Classic at the top slot, the frame. Then the same on 360 × 640, where the preview shrinks above the entry bar.
2. **Text selected:** two-tone frame and handle on the preview; the orange item selected on the timeline; text clip tools with Edit text, Style, Position and Delete visible at 360 dp.
3. **Style panel:** Classic tile white, White swatch and Black outline swatch ringed; preview visible above. Then each of the five fonts on the same caption.
4. **Position panel** at 360 × 640 (scrolled) and in Hindi at 200% font.
5. **Mid-pinch:** value bubble "…% · …°", the center guides when snapped.
6. **Mixed scripts:** "जब BUILD PASS हो 😂" in all five fonts (debug project), at 100%, with and without outline. Hindi Devanagari in Classic is Teko; nothing clipped above or below.

**Measured: the Done when** (note in the QA report):
- **Project:** the debug `text-check` draft (request 9): six captions covering every font, Devanagari, Latin with accents and emoji, outline on and off, scale 50%, 100% and 250%, rotation −15°, 0° and 30°, one partly off the frame, two overlapping.
- **Method:** pause the preview at three set times and screenshot it. Export at 1080p30 and at 720p30, and pull the same frames with ffmpeg on the Mac. Crop the screenshot to the canvas frame and scale it to the export width. Compare side by side and as a 50% overlay.
- **Pass:**
  - The same line breaks and glyphs, in the same fonts (Teko for Devanagari under Classic).
  - Position within 1% of the canvas width, size within 2%, rotation within 1°.
  - The same colors, sampled inside a letter. The outline present or absent alike, at the same relative width.
  - The off-frame caption clipped at the same place. The overlap order the same.
  - 720p and 1080p have identical layout.
- **Reference render:** the three 1080p frames go in `docs/qa/references/P1-10-text/` (CLAUDE.md rule 5), for iOS parity in P7.
- **Responsiveness:** typing a 100-character caption shows no visible lag on the owner's phone. Dragging and pinching a caption keep janky frames under 5% (`dumpsys gfxinfo`). Each committed text edit reaches the engine's preview within 6 frames (100 ms) of the screen recording.
- **Leaks:** LeakCanary on a debug build. Open the editor, add and style 5 captions, edit one, pinch one, close; repeat 5 times. No leak.

## Model
**No new fields and no schema bump.** P1-10 fits the model as it is:

| Need | Model |
| --- | --- |
| Words | `TextItem.text` |
| Font | `CaptionStyle.fontId` (`anton`, `poppins`, `baloo2`, `kalam`, `rozha`) |
| Color | `CaptionStyle.color` |
| Outline on/off and color | `CaptionStyle.outlineColor` (null = none) |
| Move, size, rotation | `TextItem.transform` (`centerX`, `centerY`, `scale`, `rotationDegrees`) |
| Timing | `startUs`, `durationUs` on a `TEXT` track |

Everything else is a fixed rule (Caption rendering → Geometry): base size, wrap width, outline width, alignment, spacing, case. Each of these would be a new `CaptionStyle` field, and a schema bump, if a later ticket makes it a choice: alignment, outline thickness, text box width, an all-caps toggle, and the PRD's shadow and background box (Decision for the owner).

## Requests to the principal mobile engineer
I don't edit code, so these are requests.
1. **One caption renderer** used by the preview overlay, the typing and gesture stand-in, the export, and later the photo editor (P2-03). It follows Caption rendering → Geometry exactly: layout once in canvas pixels, then draw scaled; never re-flow per output size. TECHNICAL_DESIGN already plans text "rendered to bitmaps and composited as overlay effects, re-rendered only when they change"; please add these rules to it and remove text from P1-03's "Not rendered yet" list.
2. **Fonts:**
   - Bundle the four files in the Fonts table, next to `teko_bold.ttf`, where both the renderer and the Style panel can load them. Add each `OFL.txt` to the font licenses.
   - Classic is a fallback chain, Anton → Teko Bold → system. On Android, `Typeface.CustomFallbackBuilder` (API 29, our minimum) does this per character cluster. Check the Compose Multiplatform and iOS route at ticket start, and never split a Devanagari cluster across fonts.
   - The other four use the system fallback only (emoji, other scripts).
   - Font ids are saved in drafts and must never change.
3. **Caption constants:** the font ids and files, the palette, the default style and the geometry numbers live in one commonMain place (for example a `CaptionCatalog`). They're content shared by the engines and both editors, so they don't belong in `MemixTheme`. Add a comment pointing to this spec and `design/tokens.json` → `caption`.
4. **Live editing:**
   - Typing and gestures draw the stand-in every frame.
   - The engine gets one `PreviewSession.update` per commit (Done, gesture release, a panel tap).
   - When the engine's render replaces the stand-in, nothing jumps or flashes: same renderer, same size.
   - The engine's copy of a text being dragged is hidden until release, so it never shows twice.
5. **Typing layout:**
   - The editor handles the keyboard inset itself: the stage refit (never larger), the entry bar above the keyboard, and the transport, timeline and tool bar hidden.
   - Every way out of typing commits (Done, back, Escape, Close, keyboard dismissed). Blank new text adds nothing; blank edited text deletes.
   - Capitalization by font, as specified. Limit 250 user-perceived characters (grapheme clusters, so an emoji counts as one).
6. **Gestures:**
   - One-finger move, two-finger scale, rotate and pan anywhere on the stage, and the handle. Snapping as specified.
   - Stage taps outside text keep toggling play.
   - Hit testing on the turned frame grown to 48 dp; the topmost text wins.
7. **Tokens:** add `MemixSize.canvasHandleSize` (24 dp), `MemixSize.swatchSize` (32 dp) and `MemixType.captionSample` (18/28 sp; its family is set per tile to the caption font it previews). Values are in `design/tokens.json`.
8. **Icons**, drawn to the grammar: `text`, `edit_text`, `style`, `position`, `minus`, `plus`, `rotate_left`, `rotate_right`, `arrow` (one drawing, turned for the four directions), `scale_rotate`.
9. **Debug sample `text-check`** for the Done-when comparison (QA compares), opened with the existing `memix.openEditor` hook.
10. **Components:** FontTile and Swatch (new READMEs) in `:core:designsystem` and the debug catalog. Style and Position use ToolPanel.
11. **Analytics:** as in the table, once the PM confirms the ids.

## Proposals and questions for the PM
1. **New `tool_use` ids** for text: `add_text`, `edit_text`, `text_font`, `text_color`, `text_outline`, `move_text`, `transform_text`. Confirmed by the PM (9 Oct) with `move_text` renamed `position_text`.
2. **Which fonts get used** (proposal, a PRD change): an optional `font` parameter (the font id) on `tool_use` when `tool` is `add_text` or `text_font`. **Approved by the owner (9 Oct, 2A)**; in the analytics table above.
3. **Owner decision below** on shadow and background box.

## Decision for the owner: text shadow and background box
- **Problem:** the PRD promises "shadow" and "background box" for text in v1. No ticket builds them. P1-10 covers fonts, color and outline; P4-11 covers animations and presets. The background box (colored band behind the words) is TikTok's most familiar caption style, so users will look for it.
- **Considerations:**
  - **A. Add them to P1-10 now:** about 1 to 2 more days in Phase 1, which is already full, and a schema bump now (two new `CaptionStyle` fields). Users see boxed captions in the first test build.
  - **B. Add them to P4-11** (animations and presets, M → L): no cost before January. But the photo editor (P2) ships without them, and the most common TikTok look waits about three months.
  - **C. A new Phase 2 ticket right after P2-03** (text in the photo editor), size M: both editors get shadow and box together on the shared renderer, one schema bump, and P4-11's presets can use them. The cost is about 2 to 3 days in Phase 2.
- **Recommendation: C.** It puts the look where both editors gain it at once, without stretching Phase 1.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Poppins, Kalam and Rozha One (Indian Type Foundry), Baloo 2 (Ek Type) and Teko are SIL OFL with Latin, Latin Ext and Devanagari subsets. Anton has Latin, Latin Ext and Vietnamese but no Devanagari. | Evidence (google/fonts metadata) | [Poppins](https://github.com/google/fonts/blob/main/ofl/poppins/METADATA.pb), [Baloo 2](https://github.com/google/fonts/blob/main/ofl/baloo2/METADATA.pb), [Kalam](https://github.com/google/fonts/blob/main/ofl/kalam/METADATA.pb), [Rozha One](https://github.com/google/fonts/blob/main/ofl/rozhaone/METADATA.pb), [Teko](https://github.com/google/fonts/blob/main/ofl/teko/METADATA.pb), [Anton](https://github.com/google/fonts/blob/main/ofl/anton/METADATA.pb) |
| File sizes: Poppins-Bold 152 KB, Kalam-Bold 450 KB, RozhaOne-Regular 315 KB, Baloo2-ExtraBold 348 KB static (the google/fonts variable file is 667 KB), Bangers 91 KB, Yatra One 270 KB | Evidence (GitHub file pages, 9 Oct 2026) | [Poppins-Bold.ttf](https://github.com/google/fonts/blob/main/ofl/poppins/Poppins-Bold.ttf), [Kalam-Bold.ttf](https://github.com/google/fonts/blob/main/ofl/kalam/Kalam-Bold.ttf), [RozhaOne-Regular.ttf](https://github.com/google/fonts/blob/main/ofl/rozhaone/RozhaOne-Regular.ttf), [Baloo2-ExtraBold.ttf](https://github.com/yanone/Baloo2-Variable/blob/master/fonts/ttf/Baloo2/Baloo2-ExtraBold.ttf), [Baloo2 upstream](https://github.com/google/fonts/blob/main/ofl/baloo2/upstream_info.md) |
| No Reserved Font Name in the Poppins, Baloo 2, Kalam and Rozha One licenses | Evidence (license text) | [Poppins OFL](https://github.com/google/fonts/blob/main/ofl/poppins/OFL.txt), [Baloo 2 OFL](https://github.com/google/fonts/blob/main/ofl/baloo2/OFL.txt), [Kalam OFL](https://github.com/google/fonts/blob/main/ofl/kalam/OFL.txt), [Rozha One OFL](https://github.com/google/fonts/blob/main/ofl/rozhaone/OFL.txt) |
| Kalam is felt-tip handwriting, 1,025 glyphs with Devanagari conjuncts; Baloo 2 is a playful, rounded display face; Yatra One is brush lettering from Mumbai railway signs | Evidence (family descriptions) | [Kalam](https://github.com/google/fonts/blob/main/ofl/kalam/DESCRIPTION.en_us.html), [Baloo 2](https://github.com/google/fonts/blob/main/ofl/baloo2/DESCRIPTION.en_us.html), [Yatra One](https://github.com/google/fonts/blob/main/ofl/yatraone/DESCRIPTION.en_us.html) |
| Android 10+ can chain fonts per character with `Typeface.CustomFallbackBuilder`; Compose has no documented per-script fallback, and splitting runs into spans can break complex-script shaping | Guidance (Android blog) plus community reports | [What's new for text in Android Q](https://android-developers.googleblog.com/2019/07/whats-new-for-text-in-android-q.html), [Compose MP fallback discussion](https://discuss.kotlinlang.org/t/compose-multiplatform-fontfamily-fallback/29651), [Arabic shaping with spans in Compose](https://dev.to/iredox10/solving-complex-arabic-script-tajweed-rendering-in-jetpack-compose-a-technical-deep-dive-3hc0) |
| Story and short-video apps add text by opening the keyboard straight away, then Done; drag to move, pinch to size, twist to turn; colors as a row of circles | Assumption-level evidence (third-party guides, not vendor help) | [TikTok text guide](https://invideo.io/blog/how-to-add-text-to-tiktok), [Instagram story text guide](https://www.guidingtech.com/instagram-story-text-tips-tricks/) |
| CapCut puts text behind a Text panel and edits style, font and color after placing it; text timing is dragged on its timeline | Assumption-level evidence (third-party guides) | [Tella: add text in CapCut](https://www.tella.com/blog/how-to-add-text-on-capcut.md), [Miracamp: add text in CapCut](https://www.miracamp.com/learn/capcut/how-to-add-text) |
| The classic caption is white Impact with a black outline, all caps, because the contrast reads on any picture; Imgflip's API defaults to Impact and exposes outline color | Assumption-level evidence (user comments, API docs) | [imgflip library reference](https://imgflip.readthedocs.io/en/latest/reference.html), [Kapwing white Impact template](https://www.kapwing.com/explore/meme-template-with-white-impact-text-on-top-and-bottom) |
| Multipoint gestures need a single-pointer alternative; dragging needs a no-drag alternative | Guidance | [WCAG 2.2 SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures), [SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| Non-text indicators need 3:1 against adjacent colors | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
