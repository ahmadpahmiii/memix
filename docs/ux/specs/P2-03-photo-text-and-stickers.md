# Photo editor: text, stickers (the layer), and the Blank start · P2-03

Third spec for the photo editor. It reuses, and does not repeat:
- `P1-10-text.md`: **everything about a caption**: the Typing entry bar and its rules, the five fonts, the palette, the default style, the Style panel, the caption rendering rules (geometry, outline, wrap, alignment, Devanagari in Teko), the analytics ids. Where this file says "as P1-10", read that section.
- `P2-01-photo-editor-shell.md`: the stage, layers, gestures, the selected-layer bar, the Layers panel and LayerRow, the Opacity and Position panels, lock and hide, Duplicate and Delete, the ratio-change rule, the empty-canvas card.
- `P2-13-image-layers.md`: the **Add photo** tool, built before this ticket (owner, 28 Oct, order A). It sits in the main bar just before Layers.
- `P0-05-navigation-and-browse-screens.md`: the Create sheet (a row changes here).

## Job
"Make a classic photo meme" (PRD → Jobs): words on a photo, top and bottom, in under a minute. P2-03 puts **text** on the photo canvas and turns on the **Blank** start. It also builds the **sticker layer** (a picture with an alpha channel that behaves like any layer); **no sticker can be added from the interface in this ticket** (owner, 28 Oct, option C: the sticker picker and "My stickers" are P2-14, after P2-07). So the main bar shows **no Stickers tool** and nothing reserves a place for one.

**Done when** (TICKETS): the same caption style looks identical in the photo and video editors. **Reading** (PM, 28 Oct; owner can veto): the same style (each of the five fonts, a Hindi line in Teko, an emoji line, color, outline, size, a 15° turn) on a 9:16 photo canvas and a 9:16 video canvas of the same size gives the same picture: the same line breaks, and glyph size and outline width equal within 1% of the canvas width, compared between the photo editor's screen, the P2-01 render hook and the video export frame (P1-12). Text is judged **on a device**, never on the desktop harness (it has no text layout). Method at the end.

**The one blue action:** none. Done in the entry bar is a Ghost button, as P1-10. Blue appears nowhere new. White is selection (the frame, the handle, the selected font tile, swatch rings, the selected row).

**Fast path, with tap counts:**
- **A captioned photo from the gallery:** Photo meme (1) → a photo (2) → Text (3), type, Done (4). The caption lands at the top, Classic, in caps.
- **From nothing:** Create (1) → Start with a blank canvas (2) → Text (3), type, Done (4).
- **A second caption (the bottom one):** tap an empty spot of the canvas or Close layer tools (1: the selection clears and the main bar returns), Text (2), type, Done (3). It lands at the bottom (below). Assumption: one tap more than a bar with Text always visible would cost; tested at the P2 test. A shortcut in the selected-layer bar would push Delete out of view (measured in the P2-01 review), so it isn't added.
- **Change the look:** Style (1), a font or color (2), as P1-10.

## Entry and exit

### The Text tool
- **Main bar (nothing selected), final order from P2-01:** **Text** · [Add photo, P2-13] · Layers. Text is first: with Formats (P2-02) it will be second. Before the other tools exist they sit one place to the left; users only see the final order.
- **Icon** `text` (P1-10), label "Text". **Tap:** goes straight to **Typing** for a new text (no menu), as P1-10. Taps are ignored while the keyboard opens.
- **On a canvas with a selection:** the main bar is replaced by the selected-layer bar, so Text isn't there. Clear the selection first (above).

### Typing (new text, Edit text)
**As P1-10 → Typing, Interactions → Typing → commit, and Accessibility → Typing**, with these photo-editor differences:
- **Where the space comes from.** There is no timeline: the entry bar rides on the keyboard in place of the tool bar, and the stage is everything between the top bar and the entry bar. The canvas frame **refits** inside it (inset `space-4`, as P2-01) and animates with the keyboard frame by frame, as P1-10. It never gets bigger than the frame had with no panel open.
- **Worked sizes** (dp, 100% font, a 280 dp keyboard, a 1:1 canvas; P1-10's stage figures):

  | Phone | Stage while typing | 1:1 frame while typing | 1:1 frame normally (P2-01) |
  | --- | --- | --- | --- |
  | 360 × 640, 3-button nav | 168 | 136 × 136 | 328 × 328 |
  | 360 × 800, gesture nav | 352 | 320 × 320 | 328 × 328 |
  | 412 × 915, gesture nav | 467 | 380 × 380 | 380 × 380 |

  A 4:3 canvas at 360 × 640 is 181 × 136 while typing, a 9:16 one 76 × 136. On the smallest phones the caption is small while typing; the field is where the words are read.
- **The top bar while typing:** Close commits and closes. **Undo, Redo and the ratio pill are inert** (drawn disabled, spoken "disabled"): the typed words aren't in the project yet, so Undo would reach past them, and the canvas must not change under the keyboard. (The P1-10 spec left this open for video; the engineer checks the video editor does the same and aligns it if not.)
- **Opening from a tap on the selected text** (P1-10) works only with **no panel open**. With Style, Position, Opacity, Layers or Canvas open, a tap on the selected text does nothing special: the panel owns the bottom of the screen, and a keyboard and a panel never stack. Edit text on the bar, after Done, is the route.
- **While a text is edited,** its committed copy isn't drawn on the canvas (the live stand-in is), so it never shows twice.
- **Back order** (P2-01) gains P1-10's step at the front: **typing commits and the keyboard closes** → menu → panel or sheet → gesture in progress cancelled → a lifted row dropped → selection clears → editor closes.
- **Commit** (Done, back, Escape, Close, keyboard dismissed): a non-blank new text is one undo step "Add text" and becomes the selection; a blank new text adds nothing and records nothing; edited text unchanged records nothing; changed is "Edit text"; cleared to blank is "Delete" (Undo brings it back). All as P1-10.
- **Limit** 250 grapheme clusters, counter from 200, all as P1-10.

### The Blank start
- **The Create sheet's row turns on** (`BLANK_ROW_VISIBLE = true`, with this ticket's commit): **"Start with a blank canvas"** (`create_blank`, unchanged in all five languages), a 56 dp row like the template row, `create` icon (the plus; the existing build uses `photo`, which repeats the Photo meme row's icon), `surface-raised`, chevron. **Order: Video meme, Photo meme, Start with a blank canvas, Start from a template.** The Blank row goes straight under the Photo meme row because it opens the photo editor (the build already has this order; `P0-05` said "after the template row", and is corrected to match).
- **Tap:** the sheet slides away (the scrim stays, as for a video start) and the photo editor opens on a **blank canvas: 1:1, 1080 × 1080, white** (P2-01), nothing selected, the empty-canvas card showing. No picker, no import sheet, nothing saved.
- **Saving:** as P2-01: made in memory, saved at its **first committed step that leaves a layer**; that is `project_create` (`editor: photo`, `source: blank`). The ratio pill and the background swatches change the canvas without saving it. Closing with no layer discards it without a message.
- **Process death.** A blank route carries its project id. After the process is killed while the app is in the background: if the project was saved (a text was added), the editor **reopens that draft**: the text is there. If nothing was ever saved (an untouched blank canvas), a **fresh blank canvas** opens and nothing is left in Drafts. Never a second draft for one blank start.
- **Not changed:** the Home card, the Drafts list and the gallery start.

### The empty-canvas card (P2-01) gets its real words
Same card, same place and look as P2-01 (`scrim`, centered on the stage). Two bodies, chosen by whether there is history to undo:
- **A canvas that never had a layer** (a fresh blank start; Undo has nothing): title "This canvas is empty", body "Tap Text to add words, or Add photo to add a picture." (`photo_empty_body_blank`). The tools it names are the main bar's, with the same words.
- **A canvas that was emptied** (a layer was deleted; Undo has something): the same title, body "Tap Undo to bring your layer back, or add something new with Text or Add photo." (`photo_empty_body`, new wording; the old one said "close this and start a new photo meme", which is no longer the only way out).
- **Until P2-13 is in the build,** drop "or Add photo" and "or Add photo to add a picture". The order says P2-13 comes first, so the strings above are the real ones.
- The card is hidden while the Canvas panel is open, as P2-01. It scrolls if taller than the stage (Hindi at 200%).

## Layout
Board: `design/screens/PhotoEditor.dc.html`. **Differences from the board, and why:**
1. **The board's text bar** ("Impact style" chip, three swatches, "Outline" chip) **is replaced by P1-10's Style panel** (P1-10 difference 1): a one-row bar can't hold five fonts and two color rows at 48 dp targets. **The board's four corner handles** become the one handle, as P1-10 difference 2.
2. **The board's tool bar** (Text, Stickers, Remove BG, Draw, …) shows only what works: Text now; Stickers arrives with P2-14.
3. **The board's format bar** is P2-02's.

### Main bar
**Text · [Add photo] · Layers** (above). Text `text-secondary`, as every tool.

### Selected-text bar (a text layer is selected)
The P2-01 selected-layer bar: Close layer tools, a divider, then, scrolling sideways:

**Edit text · Style · Position · Delete · Opacity · Duplicate · Lock · Hide · Layers**

- **Why this order.** Edit text, Style and Position are what people do with a caption (P1-10). Delete stays 4th: the P2-01 review measured tools at 64 dp, so four fit in view at 360 dp and a fifth is cut (Delete in the fifth place showed its label clipped). The P2-01 order for other layers (Position, Opacity, Delete, …) is unchanged; text puts Opacity behind Delete because Edit text and Style come first.
- **A locked text** starts with **Unlock** (P2-01): **Unlock · Edit text · Style · Position · Delete · Opacity · Duplicate · Hide · Layers**. Edit text, Style, Position, Opacity and Delete only explain while locked (below).
- Hide and Show swap in the Hide place, as P2-01. Opacity opens P2-01's Opacity panel; Position opens P1-10's Position panel as lifted by P2-01.
- **Look:** as P2-01: `text-secondary` icon and label, Delete `danger`, two lines of label room. The bar crossfades with the main bar over 120 ms.

### Selected-sticker bar
Exactly the **P2-01 selected-layer bar**: Position · Opacity · Delete · Duplicate · Lock · Hide · Layers (Unlock first when locked). A sticker has no layer-type tools of its own until P2-14 and P4-12.

### Style panel (a text layer)
**As P1-10 → Style panel**: the ToolPanel with Font (five FontTiles), Color (ten Swatches), Outline ("No outline" and the ten). Every tap applies at once and is one undo step ("Font", "Color", "Outline"); tapping the selected option records nothing. Photo differences:
- **Height:** the photo ToolPanel is as tall as its content up to 40% of the window (P2-01): about 292 dp of content, so at 360 × 800 (cap 320) it fits; at 360 × 640 (cap 256) it scrolls inside. The stage refits above it (P2-01), so every choice is seen on the real canvas.
- **Opening it** on a selected text keeps the canvas touches live (a drag still moves the text), as P2-01 says for the Opacity and Position panels.
- P2-11 adds Shadow and Box rows to this panel later (its own spec); nothing here reserves space.

### Position panel and Opacity panel
**Position:** P2-01's panel (Size, Rotation, Move, Center), with P1-10's **size ladder for text**: 25 · 33 · 50 · 67 · 75 · 90 · 100 · 110 · 125 · 150 · 175 · 200 · 250 · 300 · 400 · 500% (the image ladder's 10 and 15 don't apply: text keeps 25 to 500%). **100% is the caption's base size** (8% of the canvas's shorter side). Limits explain as P2-01 ("This is the smallest size.", "This is the biggest size.", `explain_layer_edge`). Each tap, hold and Center is one step "Position and size". **Opacity:** P2-01's, 10% to 100%.

### Layers panel rows
LayerRow (P2-01), with two new kinds:

| Kind | Name | Thumbnail | Notes |
| --- | --- | --- | --- |
| **Text** | The text's **first non-blank line**, `bodyStrong` `text`, one line, ellipsis at the end (as typed, caps and all). Set in the interface font (Devanagari in the system face), not in the caption font | `layer-thumb-size` `surface` square with the `text` icon at `icon` size in `text-secondary` (5.7:1). Hidden: at 40% like every thumbnail; faded: at the layer's opacity, never below 40% | Meta line as P2-01 (Locked · Hidden · 40% opacity). A text can't be "File missing" (a font that isn't bundled draws in Classic, P1-10) |
| **Sticker** | "Sticker" (the same for every sticker, like "Photo") | The sticker's picture, **fitted inside** the square, on `surface` (a transparent sticker shows on `surface`, no checkerboard), from the scene decoder at about 128 px (P2-01) | Meta line as P2-01, including "File missing" (the `photo` icon on `surface`) |

- **Why an icon for text and not the words drawn small:** the caption's color can be black or white and its outline null, so on `surface` it could vanish or look like nothing; and the row's name already is the words. A text row's job is told by its name; the icon says "this is text". It costs no bitmap and no decode.
- Everything else in the panel (front first, drag to reorder, Move up and down, lock and eye, Opacity row) is P2-01's, for every kind. A hidden row is dimmed as P2-01 says: name `text-secondary`, thumbnail 40%.

## Text on the canvas

### Landing (a new text)
- **Where:** centered horizontally (`centerX` 0.5), scale 100%, rotation 0°. Vertically the first **free slot** of **top (`centerY` 0.25), bottom (0.75), middle (0.5)**. A slot is taken when a **visible** (not hidden) text layer has its center within 0.1 of it. All three taken: the middle. Top first, as P1-10: a meme's classic caption is top and bottom, and a second caption naturally takes the bottom.
- **In the stack:** the **front** (top-most layer), so the new words are over the photo and everything else. It becomes the selection: frame, handle, the selected-text bar. No bonk (nothing is on a timeline), no haptic; a 120 ms fade of its frame (P2-01). Announced "Text added" (below).
- **Style:** the last style used **in this editor visit** (font, color, outline), otherwise the default: Classic, White, Black outline (P1-10). A visit ends when the editor closes. **Duplicate** copies the style.

### How a caption draws (preview = render)
P1-10's **Caption rendering rules apply unchanged** (`design/tokens.json` → `caption`): size 8% of the canvas's **shorter side**, wrap at 90% of it, scale 25% to 500%, each line centered, no tracking, outline `size ÷ 12` with round joins drawn under the fill, laid out once in canvas pixels and then drawn scaled, clipped at the canvas edge, text exactly as typed, Anton with Teko Bold for Devanagari under Classic, the system font for emoji and other scripts. Photo canvases have the same shorter side (about 1080 px) as video canvases (P1-11, P2-01), so **the same style on the same canvas gives the same picture**: that is the Done when. Photo-only additions:
- **Draw order is layer order** (bottom to top), not lanes: a text draws over the layers beneath it and under the layers above it. A photo above a caption hides it.
- **Opacity (and fade) is drawn as one group:** the whole caption (outline and fill) is drawn at full strength into its own layer and that layer is composited at the layer's opacity, so the outline never shows through a translucent fill. Video captions have no opacity, so the Done-when comparison uses 100%.
- **Hidden text** isn't drawn, hit or exported (P2-01).
- **The preview never re-flows:** a ratio change, a panel opening (the refit) and the typing refit scale the already laid-out block; line breaks never change with the output size (P1-10 → Layout resolution).

### Gestures
**P2-01's gestures, on a text layer** (P1-10 → On the preview): one finger moves it 1:1; the handle sizes and turns; two fingers anywhere on the stage (a layer selected) scale, turn and pan; snapping (center lines within `space-2`, rotation 0/90/180/270 within 4°, size to 100% within 3%; one SegmentTick each); the value bubble "120% · 15°". One gesture is one undo step "Position and size". Size stops at 25% and 500% (text), the center stays within 0…1. Differences:
- **A tap on the selected text opens Typing** (P1-10), only with no panel open (above). Tapping an unselected text selects it; tapping empty canvas or stage clears the selection (P2-01). **The top-most visible layer under the finger wins whatever its kind**: a caption's frame (grown to 48 dp) on top of a full-bleed photo takes the touches inside it. Reaching the photo under a caption's frame is a tap outside the frame or a row in the Layers panel.
- **Hit area:** the text's laid-out block, turned with it, grown to at least `touch-target` in each direction (P1-10).
- **The selection frame and handle** are P1-10's: `stroke-selection` white with a `stroke-hairline` `on-primary` edge, `space-1` outside the block, the handle at its bottom-right corner (nearest inside corner if off the stage). Clipped at the stage edge (P1-10 review M1).
- **Locked text:** a tap selects it (frame without handle, the lock badge, the bar starting with Unlock). A drag that starts on it, a pinch or twist with it selected, and the handle's place move nothing; a toast once per touch: "This layer is locked. Unlock it to change it." (`explain_layer_locked`). **Tapping a locked, selected text doesn't open Typing:** the same toast. Edit text, Style, Position, Opacity and Delete on the bar, and the Layers panel's Move buttons, explain with the same toast. Select, Unlock, Hide, Show, Duplicate (the copy is unlocked) and the row's lock and eye buttons still work (P2-01).

### Duplicate and Delete
- **Duplicate:** a copy directly above the original, **offset 3% of the canvas's shorter side right and down** (never past the center limits), the same words and style, unlocked, opacity and hidden state kept, selected. One step "Duplicate".
- **Delete:** the layer goes, the selection clears, the main bar returns; one step "Delete", no confirmation. Deleting the last layer shows the empty card.

### Ratio change with text
**P2-01's rule, now for text** (the `TODO(P2-03)`): one tap on a ratio is one undo step ("Ratio") that changes the canvas and, in the same step, anything the rule moves.
1. Every layer keeps its center, scale and rotation. A caption's size follows the canvas's shorter side, so a ratio change moves text but never resizes or re-wraps it (P1-10).
2. **A text fully inside the old frame must be fully inside the new one.** Its box is the laid-out block (from the caption measurer), turned by its rotation. If it now crosses an edge it moves **straight in until it is 2% of the shorter side inside** that edge (text keeps P1-11's 2%; images have 0). If it is too big for the new frame it **shrinks first, keeping its center, not below 25%**, then moves in.
3. **A text placed partly outside** (hung off an edge on purpose) keeps its center and scale: it is never lost (center limits).
4. If any layer moved or shrank, the toast "Layers moved to stay inside the frame." shows once. Undo restores everything.
5. A **locked** text follows the same rule: a ratio change is about the canvas, not an edit of the layer.

## The sticker layer
A `StickerLayer` is a picture with transparency (PNG, WebP; an animated GIF or WebP draws its first frame until P4-12). **P2-03 builds how it draws, behaves, is listed, saved and reopened. There is no way to add one from the interface:** the picker and "My stickers" are P2-14, after P2-07 (owner, 28 Oct, C). The debug scene `photo-stickers` (below) is how it is checked.

### Size at scale 1 and range (fixed rules)
- **At scale 1 a sticker's longer side is 35% of the canvas's shorter side** (378 px on a 1080 canvas): the picture scaled uniformly, centered on its own center, with its shape kept. **It is not "fitted inside the frame"** like a photo layer: a sticker is a small thing on a meme, and a 512 px sticker at full frame would be a photo. The size is recomputed from the canvas's shorter side at draw time, so a ratio change never resizes it (the shorter side is about 1080 in every shape). The picture is never decoded above its own size or 4096 px (P2-01); drawn bigger than its source it is upscaled.
- **Scale range 10% to 500% of that size:** 3.5% to 175% of the shorter side. The smallest is about 38 px (still a 48 dp hit area), and 500% is enough to cover the canvas's width in any ratio. The same ladder as images (10 to 500%); 100% in the Position panel and the snap are "the landing size".
- **These are fixed rules**, saved with every draft's look (`design/tokens.json` → `photo.sticker`). Changing one after the closed test (about 25 Jan 2027) needs a schema bump; before it only the team's drafts exist. P4-12 (video stickers) and P2-14 use the same rule.
- **No cover rule** (the 1% same-shape rule is for photo layers).

### Behavior
- **Hit area:** the picture's box, turned with it, grown to at least 48 dp; transparent parts count (P2-01: pick through to a layer beneath in the Layers panel).
- **Gestures, snapping, the Position and Opacity panels, lock, hide, duplicate, reorder, delete:** exactly P2-01's for layers (a sticker has no text tools). Duplicate shares the same file (P2-01).
- **Ratio change:** the same rule as text, with the margin 2% and a shrink floor of 10%; the box is the picture's box at its scale. The toast is the same.
- **A missing sticker file:** not drawn in preview or export, its row says "File missing", the toast (once per open) says "A picture's file is missing, so it isn't drawn. Delete that layer to fix it." (`photo_missing_media`, reworded from "A photo's"; also `explain_layer_missing`), selecting works, Delete first on the bar. Exactly P2-01's image rule.
- **Drawn** by the one scene function (P2-01) with the layer's opacity, above the layers beneath it.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Main bar | Text · [Add photo] · Layers | `tool_text`, `tool_add_photo`, `tool_layers` |
| Typing a new text, empty | The entry bar and keyboard in place of the tool bar; the frame refit above; an empty `touch-target` square at the landing spot; Undo, Redo and the pill inert | `text_entry_hint`, `text_entry_label`, `panel_done` |
| Typing, with text; near the limit; at the limit | As P1-10: the caption live in its style; counter from 200; the field stops at 250 | `text_entry_count`, `text_entry_count_a11y`, `text_entry_limit_a11y` |
| Committed new text | The keyboard closes; the text is on the canvas at its slot, selected; the selected-text bar; announced | `a11y_layer_text_added` |
| Text selected | Frame and handle; the selected-text bar | `tool_edit_text`, `tool_style`, `tool_position`, `tool_delete`, `tool_layer_opacity`, `tool_duplicate`, `tool_lock`, `tool_hide_layer`, `tool_layers` |
| Text selected, locked | Frame, lock badge (no handle); the bar starts with Unlock | `tool_unlock`, `explain_layer_locked` |
| Text selected, hidden | Nothing for it on the canvas; the bar shows Show; the row is dimmed | `tool_show_layer`, `layer_meta_hidden` |
| Moving, sizing, turning | The text follows; guides when snapped; the bubble; the frame edge (P2-01) | `text_transform_bubble` |
| Style panel | Font tiles, color and outline swatches with the current ones selected; the canvas refit above | `style_*`, `font_*`, `color_*`, `outline_none` |
| Position panel (text) | Size ladder 25 to 500% | `position_*`, `explain_text_smallest`, `explain_text_biggest`, `explain_layer_edge` |
| Opacity panel (text) | One slider, 10% to 100% | `layers_opacity` |
| Layers panel with text and sticker rows | Rows per the table above | `layer_name_sticker`, `layers_title` |
| Blank canvas, nothing yet | 1:1 white; the empty card with "Tap Text to add words…" | `photo_empty_title`, `photo_empty_body_blank` |
| Canvas emptied | The empty card with the Undo body | `photo_empty_body` |
| A text that doesn't fit (250 characters, scale 500%) | Wraps at 90% of the shorter side; runs past the frame, clipped; the frame is cut at the stage edge | none |
| Hindi or mixed text | Devanagari in each font's own face (Teko for Classic); lines taller where marks need it; emoji without an outline | none |
| A draft names a font that isn't bundled | Drawn in Classic; no tile selected in Style; the id is kept until a font is picked | none |
| Ratio changed, a text or sticker moved or shrank | The new frame; the toast | `photo_layers_moved` |
| Sticker file missing | Not drawn; row says "File missing"; toast once per open | `photo_missing_media`, `layer_meta_missing` |
| Create sheet | Four rows, Blank under Photo meme | `create_blank` |
| Blank route restored after process death | The saved draft (the text is there), or a fresh blank canvas if nothing was saved | none |
| Offline, permission | Don't apply | none |
| First run | Same as every run; no coach marks | none |

## Interactions
- **Press feedback:** token pressed states, 120 ms. **Haptics:** SegmentTick for each snap, LongPress when a row lifts (P2-01); nothing else, not even on adding text.
- **Undo and redo:** P2-01's rules. After undo or redo the selection stays if the layer still exists; a panel editing a layer that no longer exists closes; an Undo toast names the step ("Undo: Add text", "Undo: Edit text", "Undo: Font", …). **Undo during a gesture or typing is ignored.**
- **Edit names after "Undo: "** (P1-04): `edit_add_text`, `edit_edit_text`, `edit_text_font`, `edit_text_color`, `edit_text_outline` (P1-10's keys); a gesture or a Position step on any layer kind, text included, is `edit_transform_layer` "Position and size" (P2-01). The analytics ids differ by kind (below), the edit name doesn't.
- **Keyboard:** Tab reaches the top bar, the tool bar, the field and every panel control; Enter activates; Escape is system back (commit while typing). Arrow-key nudges are L-01.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Entering or leaving Typing | The entry bar rides the keyboard's own animation; the frame refits with the keyboard inset frame by frame | Same (the system drives it) |
| Selection frame and handle | 120 ms fade | Same |
| Main ↔ selected-layer bar | 120 ms crossfade | Same |
| Panel opens or closes; the canvas refits | 200 ms slide; 200 ms scale about the frame's center | 120 ms fade; instant |
| Text appears (a new layer) | 120 ms fade-in | Same |
| Font, color, outline, Position steps | Apply instantly | Same |
| Snap guides, value bubble, toasts | 120 ms fade | Same |
| Create sheet closing into the editor | As the video start | Same |

## Copy
English source; the engineer machine-drafts id, es, pt and hi (until P5). Budgets are characters. **Every string from P1-10 keeps its key and moves to `:core:ui` in the same commit** when the typing bar and Style panel are lifted (the P2-01 rule): `tool_text`, `tool_edit_text`, `tool_style`, `text_entry_*`, `style_*`, `font_*`, `outline_none`, `a11y_text_on_canvas`, `edit_add_text`, `edit_edit_text`, `edit_text_font`, `edit_text_color`, `edit_text_outline`, plus P2-01's `explain_layer_*`, `tool_lock`, `tool_unlock`, `tool_hide_layer`, `tool_show_layer`, `layer_meta_*`, `a11y_layer_row`, `edit_transform_layer`. `a11y_text_added` ("Text added at %1$s") is video-only: a photo has no time.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `layer_name_sticker` | Sticker | 16 | Row name of a sticker layer, the same for every sticker. A small picture with a see-through background, placed on the meme |
| `a11y_layer_text_added` | Text added | 20 | Announced politely after a new text lands (the video editor's version adds a time) |
| `photo_empty_body_blank` | Tap Text to add words, or Add photo to add a picture. | 90 | Empty-canvas card on a fresh blank canvas. "Text" and "Add photo" must match `tool_text` and `tool_add_photo` exactly |
| `photo_empty_body` | Tap Undo to bring your layer back, or add something new with Text or Add photo. | 100 | Replaces the P2-01 wording. "Undo", "Text", "Add photo" match `editor_undo`, `tool_text`, `tool_add_photo` |
| `photo_missing_media` | A picture's file is missing, so it isn't drawn. Delete that layer to fix it. | 90 | Reworded from "A photo's" so it is true for stickers too. Once per open |
| `explain_layer_missing` | This picture's file is missing. Delete the layer to fix it. | 70 | Reworded from "This photo's"; shown when Duplicate is tapped on a missing layer |
| `create_blank` | Start with a blank canvas | 34 | Unchanged. Create sheet row. Keep the pattern of `create_template` |

Translator notes:
- **"Sticker"** is the word your language's chat and photo apps use for a small picture placed on another. It isn't "label" or "tag".
- **"Picture"** in the two missing-file messages covers a photo or a sticker. Use the general word, not the one used for the Photo meme row.
- **"Text", "Add photo" and "Undo"** in the empty-canvas card are the names of buttons on the same screen: copy their exact words from those strings.
- Everything about captions is P1-10's translator notes.

## Accessibility
- **TalkBack and focus order** (P2-01 → Accessibility, with text): Close → ratio pill → Undo → Redo → save banner → the canvas → the empty-canvas card, when shown → tool bar or panel. **The canvas's children are the visible layers front to back.** A text layer reads `a11y_text_on_canvas` ("Text: WHEN THE BUILD PASSES FIRST TRY", all its words) through `a11y_layer_row` ("…, layer 1 of 3"), with the state "selected" and the extra words (locked, opacity) after the name, never in the state description (P1-11 review M2). A sticker reads "Sticker, layer 2 of 3". Double-tap selects; double-tap on an already selected, unlocked text opens Typing.
- **Actions on a text node and on its Layers-panel row** (TalkBack's menu, Switch Access): **Edit text**, Style, Position, Opacity, Lock or Unlock, Hide or Show, Duplicate, Delete, Move up, Move down. A sticker's: the same without Edit text and Style. Blocked actions on a locked layer explain with the same toast, announced. **A row's spoken name** for a text is `a11y_text_on_canvas` with the row's first line only (the visible name), so ten captions don't read 250 characters each; the canvas node and Edit text carry all the words.
- **The handle** is hidden from accessibility: it is drag-only, and the Position panel is the full alternative (WCAG 2.5.1 and 2.5.7).
- **Typing:** opening it moves focus to the field ("Your text"); Done is the next stop; on commit, focus goes to the text on the canvas and "Text added" plays (`a11y_layer_text_added`). Undo, Redo and the pill read "disabled" while typing. Where TalkBack's focus actually lands after a panel closes is the same open device check as P2-01 (review P4).
- **Panels:** ToolPanel rules; Font tiles and swatches are single-choice groups ("Font", "Color", "Outline", "2 of 5"); steppers announce their new value politely, as P1-10.
- **Touch targets:** every tool, tile, swatch, row and button is 48 dp or more; a text's or a sticker's hit area grows to 48 dp; the handle's target is 48 dp.
- **Contrast:** frame, handle and guides as P1-10 (4.3:1 or more over any content); the row's name `text` on `surface-raised` 12.4:1; its `text` icon thumbnail `text-secondary` on `surface` 5.7:1; the empty card `text` on `scrim` 7.5:1 or more over any canvas color; field and counter as P1-10. Caption colors are the user's content.
- **200% font:** the entry field and counter grow and the stage shrinks further while typing; tool labels wrap and the bar grows; the Style and Position panels scroll; LayerRow text wraps and rows grow, the Opacity row moves into the list when the panel is tight (P2-01); the empty card scrolls. **Captions on the canvas don't follow the system font size**: they are the meme's content, sized by the canvas. Hindi is a screenshot (QA compares 6).
- **Reduce motion:** see Motion.

## Analytics
`tool_use` with `editor: "photo"`, once per committed undo step (never while typing, dragging or holding; never when a tool only explains; never for opening a panel or Typing, selecting, or tapping an already selected option). **No new ids:** the PM's rule (28 Oct) is that a text layer uses P1-10's ids so text analytics join across editors, and every other layer id is P2-01's.

| Event | When | Parameters |
| --- | --- | --- |
| `project_create` | A blank project's first committed step that leaves a layer (the first text) | `editor: "photo"`, `source: "blank"` |
| `tool_use` | A new text is committed | `tool: "add_text"`, `font`: the font id (`anton`, `poppins`, `baloo2`, `kalam`, `rozha`) |
| `tool_use` | An edit of the words is committed | `tool: "edit_text"` |
| `tool_use` | A font, color or outline tap | `tool: "text_font"` (with `font`), `"text_color"`, `"text_outline"` |
| `tool_use` | A gesture releases on a **text** layer | `tool: "transform_text"` |
| `tool_use` | One Position-panel step on a **text** layer | `tool: "position_text"` |
| `tool_use` | A gesture releases, or a Position-panel step, on an **image or sticker** layer | `tool: "transform_layer"`, `"position_layer"` (P2-01) |
| `tool_use` | Lock, Unlock, Hide, Show, Opacity release, a drop or Move up/down, Delete, Duplicate, Undo, Redo, on **any** layer kind | `lock_layer`, `unlock_layer`, `hide_layer`, `show_layer`, `layer_opacity`, `reorder_layer`, `delete`, `duplicate`, `undo`, `redo` |
| `tool_use` | A text cleared to blank | `tool: "delete"` (as P1-10) |

- Starting from Blank is not a `tool_use` and fires no `canvas_ratio`. **No `add_sticker`:** it belongs to the ticket that builds the picker (P2-14).
- `font` stays on `add_text` and `text_font` only; `value` stays for the canvas tools (owner, 21 Oct). Analytics stay off until consent (P5-01).

## QA compares
Use the debug drafts below and a gallery start with a 4:3 photo. Text is judged on the device.
1. **Typing a new caption** on a 4:3 photo at 360 × 800: the entry bar on the keyboard, the live caption in Classic at the top slot, the empty landing square before the first letter; Undo, Redo and the pill disabled. Then 360 × 640, where the frame is 181 × 136. Then a 200-character caption at the top: its frame is cut at the stage edge, never over the top bar or the entry bar.
2. **After Done:** the caption selected (two-tone frame, handle) and the selected-text bar with Edit text, Style, Position and Delete visible at 360 dp. A locked text: the lock badge in place of the handle and Unlock first.
3. **Create sheet and Blank:** the sheet with four rows (Blank under Photo meme, `create` icon); the blank canvas with the new empty card (white, 1:1); the same card after the only layer was deleted (the Undo body); the card on a black background.
4. **Style panel** at 360 × 800 and 360 × 640 (scrolled), the canvas refitted above it, Classic tile white, White color and Black outline ringed; then each of the five fonts on the same caption.
5. **Layers panel** on `photo-text-check` and `photo-layers-10`: text rows (first line, the `text` icon thumbnail), a hidden text row, a faded one, a sticker row with its picture; the panel's canvas refit.
6. **Hindi at 200% font**, 360 × 640: typing, the selected-text bar, the Layers panel with a Devanagari row name, the empty card with the new body.
7. **Mixed scripts:** "जब BUILD PASS हो 😂" in all five fonts, with and without outline: Devanagari in Classic is Teko; nothing clipped above or below; the emoji without a ring.
8. **Ratio change** with text fully inside (moves in to 2%), a big text (shrinks first), a text hung off the edge (keeps its place), a sticker (2%): the toast; one Undo restores all.
9. **`photo-stickers`:** a square, a wide, a tall and a tiny sticker with transparent backgrounds over a photo: landing size, a pinch to 500%, a locked one, a hidden one, one with a missing file (toast, row).
10. **Mid-pinch** on a text: the bubble "…% · …°" and the frame edge on a white canvas.

**Measured: the Done when** (the PM's method in `docs/pm/acceptance-log.md`; the designer adds):
- **Debug drafts** (request 1): `photo-text-check`, a 9:16 1080 × 1920 canvas with a plain background image layer and **the same six captions as the video editor's `text-check`** (P1-10 → QA compares): Classic "जब BUILD PASS हो 😂" over the top, Modern accented Latin at 50% and −15°, Bubbly two lines at 250% and 30° partly off the right edge, Handwritten with Devanagari, Serif "THE END", and Classic red "OVERLAP" above it in the stack (so the overlap order matches the video's lane order). Same words, styles and transforms, outline on and off, at 100% opacity.
- **Method:** (a) screenshot the photo editor with nothing selected, crop to the canvas frame, scale to 1080 wide; (b) the P2-01 render hook (`--es memix.renderScene photo-text-check --ei memix.renderWidth 1080`) PNG; (c) the video editor's `text-check` exported at 1080p30 (1080 × 1920), a frame where all six are visible. Compare side by side and as a 50% overlay.
- **Pass:** the same line breaks and glyphs in the same fonts (Teko for Devanagari under Classic); glyph size and outline width equal within 1% of the canvas width (10.8 px at 1080); position within 1% of the width, rotation within 1°; the same colors sampled inside a letter; the off-frame caption clipped at the same place; the overlap order the same. Frames go in `docs/qa/references/P2-03-text/` (CLAUDE.md rule 5), for P2-08 and iOS parity.
- **Landing:** on a photo: the first text goes to the top, the second to the bottom, the third to the middle, a fourth to the middle again; hide the top one and the next goes to the top.
- **Blank:** Create → Blank → Text → "Hello": Drafts has a draft; `project_create` (`photo`, `blank`) fires once at that commit and not for a ratio or color change alone; delete the only text and close: no draft. Kill the process (`adb shell am kill`) with the app in the background after the text: the text is back; kill on an untouched blank canvas: a fresh blank canvas and no draft.
- **Ratio sequence** on `photo-text-check`: every text that started fully inside is fully inside after each step (2% margin to the pixel), a partly-outside text keeps its center and scale, one Undo restores the previous ratio and every layer exactly.
- **Locked text:** lock a caption, drag it, tap it, use Style: it never changes and the toast shows; Unlock restores all of it.
- **Reopen:** kill mid-edit and reopen from Drafts: every text and sticker, including locked, hidden and faded states, is the same.
- **Speed:** with three text layers added to `photo-layers-10` (request 1), the P2-01 bars hold: three 3-second drags under 5% janky frames, and typing 100 characters shows no visible lag.
- **Leaks:** LeakCanary, 5 cycles of open, add and style a caption, edit it, pinch it, open the Layers panel, close.

## Model
**No new fields and no schema bump.** `TextLayer` (words, `CaptionStyle`, transform, locked, hidden, opacity) and `StickerLayer` (source, transform, the same flags) exist (P1-01, P2-01). New **fixed rules** (constants in `PhotoCatalog`, `design/tokens.json` → `photo.sticker` and `photo.text`; part of every draft's look):

| Rule | Value |
| --- | --- |
| Sticker size at scale 1 | Longer side 35% of the canvas's shorter side |
| Sticker scale range | 10% to 500% |
| Text scale range | 25% to 500% (`caption.geometry`, unchanged) |
| New text landing slots | `centerY` 0.25, 0.75, 0.5; a slot is taken within 0.1 |
| Text ratio-change margin | 2% of the shorter side; shrink floor 25% (sticker floor 10%) |

`PhotoCatalog.scaleRangeOf` gives stickers their own range (it treated them like images until now). Everything about a caption's look is `CaptionCatalog`'s.

## Requests to the principal mobile engineer
I don't edit code, so these are requests. The PM's must-knows for P2-03 carry the checklist; these are what the design needs.
1. **Debug scenes** (`--es memix.openEditor`): `photo-text-check` (above), `photo-stickers` (four generated stickers with alpha: a 512 px square, a 3:1, a 1:3, a 96 px tiny; over a photo and one text; one locked, one hidden, one with a deleted file), and **three text layers added to `photo-layers-10`**: Classic at the top, Bubbly at 250% and 30°, a Hindi line.
2. **Draw text in the scene with P1-10's renderer** through a small interface in `:core:ui` or `:core:domain` that draws one caption into a canvas, bound in the composition root as `CaptionStandIn` is. No engine depends on another. Layout once in canvas pixels, then scale. Draw a text layer into its own layer and composite it at the layer's opacity (outline under fill, no show-through). iOS: the same stub pattern as P1-10 (an estimated measurer, nothing drawn, marked for P7-04).
3. **The ratio-change rule for text and stickers** in `LayerFit`, replacing the `TODO(P2-03)`: margin from `PhotoCatalog.ratioChangeMarginOf`, text box from the caption measurer, floors 25% and 10%, one undo step, the toast.
4. **The Blank row:** `BLANK_ROW_VISIBLE = true`, the `create` icon, order Video, Photo, Blank, Template; update `docs/TECHNICAL_DESIGN.md` and the P0-05 hand checks. **The blank route carries its project id** and opens the saved draft first when restored.
5. **Lift the typing bar, Style panel and generic caption controls** (`captions/TextEntryBar.kt`, `TextPanels.kt`, `CaptionControl.kt` pieces) into `:core:ui`, the video editor using them in the same commit with no behavior change. List the P1-10 steps QA must re-run (typing, style, landing in video unchanged).
6. **Typing layout in the photo editor:** the entry bar in place of the tool bar; the frame refit (never bigger) animated with the keyboard; Undo, Redo and the pill inert while typing; no panel open when Typing opens; the committed copy of an edited text hidden while the stand-in draws.
7. **The selected-text and selected-sticker bars** as above (Delete fourth for text; Unlock first when locked, with P2-01's scroll reset). The row thumbnail for a text layer is the `text` icon on `surface` (no bitmap); a sticker's is the scene decoder's picture at about 128 px.
8. **The two missing-file strings** reworded for both kinds of layer, and `layer_name_sticker`, `a11y_layer_text_added`, `photo_empty_body_blank`, `photo_empty_body` in five languages.
9. **Analytics:** P1-10's ids with `editor: "photo"`; `transform_text` and `position_text` for text layers, `transform_layer` and `position_layer` for the rest; the layer ids for every kind; no new `EditorTool` value.
10. **Performance:** layout and glyph work happen once per commit or per typed character, never per frame of a drag (the P2-01 draw-phase rule); the text layer's group composite must not re-run the layout.

## Proposals and questions for the PM
1. **Confirm the analytics mapping** (your 28 Oct clarification, applied as written): no new ids; text gestures `transform_text`, text Position-panel steps `position_text`.
2. **Sticker rules are fixed rules** (35% of the shorter side at scale 1, 10% to 500%): confirm, so the engineer puts them in `PhotoCatalog` now (your must-know 8). The owner can change either before the closed test; after it, a change needs a schema bump. P2-14 and P4-12 must use the same rule.
3. **Create row:** order Video, Photo, **Blank**, Template with the `create` icon (the build already has the order; `P0-05` said "after the template row" and is corrected). Please confirm, since the PM's ready check owns the constant.
4. **Undo, Redo and the pill inert while typing** is new for the photo editor and unspecified for video: please have the engineer check the video editor and align it if it differs (a small P1-10 follow-up, no spec change for video).
5. **Two reworded P2-01 strings** (`photo_missing_media`, `explain_layer_missing`: "photo's" becomes "picture's") change a built ticket's copy; they are strings only.
6. **Second caption costs one tap more** than the video editor's (the selection must clear first). I did not add a Text shortcut to the selected-layer bar (it would push Delete out of view); the P2 test decides. If it matters, the cheapest fix is to keep the main bar's Text tool visible beside the selected-layer bar's Close, as a later change.
7. **Size watch:** M at the top: text add, edit and style in the photo editor, text drawn in the scene, the ratio rule, the Blank row and its route, a lift of the typing bar and Style panel, strings, a sticker layer. Past two build sessions, split text from the sticker layer (the sticker layer is the part that can wait).
8. **P2-11 (shadow and box)** follows P2-02; its spec adds Shadow and Box rows to the Style panel for both editors. Nothing here reserves space for them.

**Later, no ticket:** a Text shortcut in the selected-layer bar; flipping a sticker (rotation is in the Position panel; mirroring isn't anywhere yet).

No owner decision is needed from this spec. Three defaults the owner can change before the closed test: the sticker landing size (35%), the sticker scale range, and the caption landing slots.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Short-video and story apps open the keyboard straight away for a new text, then Done; drag to move, pinch to size, twist to turn | Assumption-level (third-party guides), as P1-10 | [TikTok text guide](https://invideo.io/blog/how-to-add-text-to-tiktok), [Instagram story text guide](https://www.guidingtech.com/instagram-story-text-tips-tricks/) |
| The classic caption is white Impact with a black outline, all caps, top and bottom | Assumption-level (user comments, API docs), as P1-10 | [imgflip library reference](https://imgflip.readthedocs.io/en/latest/reference.html), [Kapwing white Impact template](https://www.kapwing.com/explore/meme-template-with-white-impact-text-on-top-and-bottom) |
| Multipoint and dragging gestures need single-pointer alternatives | Guidance | [WCAG 2.2 SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures), [SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Four tools fit in view at 360 dp, a fifth is cut (Delete's label clipped) | Measured from the P2-01 renders (tools 64 dp wide; fifth center at 345 dp of 360) | `docs/ux/reviews/P2-01/02-selected-layers10.png`, `docs/ux/reviews/P2-01.md` |
| A sticker at 35% of the shorter side reads as a small addition (378 px on a 1080 canvas, about 4 lines of Classic caption tall) | Assumption (our reasoning; checked on `photo-stickers` at QA) | none |
| A second-caption cost of one extra tap is acceptable | Assumption (tap-count reasoning; the P2 test decides) | none |
