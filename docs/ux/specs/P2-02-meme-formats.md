# Photo editor: meme formats · P2-02

Fourth spec for the photo editor. It reuses, and does not repeat:
- `P2-01-photo-editor-shell.md`: the stage and canvas frame, layers and their gestures, the selected-layer bar, the Layers panel, the Opacity and Position panels, lock, hide, the ratio-change rule, `drawPhotoScene`, the render hook.
- `P2-03-photo-text-and-stickers.md`: Typing, the Text tool, caption rendering and landing, the Style panel, text layers (built before this ticket, owner 28 Oct, order A).
- `P2-12-canvas-background-image.md`: the one-photo pick path and the canvas background (a gutter or a bar shows it).
- `P2-13-image-layers.md`: Add photo and image layers.
- `P1-10-text.md`, `P1-11-canvas.md`: captions, the Canvas panel and `OptionTile`.

## Job
"Make a classic photo meme" and its relatives (PRD → Jobs, → Photo editor): choose a **format** and the meme's shape is made for you. Five formats (PRD): **Classic** (top and bottom text), **Caption bar** (a white bar above the image), **Panels** (2 to 6 cells, grid, side by side or stacked, draggable dividers), **Demotivational** (a frame with a title) and **Speech bubbles**. A format is a layout that holds **slots** (a place waiting for text or a picture); it never takes the user's words or pictures away.

**Done when** (TICKETS): each format exports correctly at 1080 px and 2160 px. **Reading** (PM, 28 Oct; owner can veto): the two sizes are the canvas's **shorter side** (the export scales the canvas by 1 and by 2); "exports correctly" means the render equals the on-screen canvas (every layer and format part within 1% of the width in position, 2% in size, 1° in rotation, the same wrap and stacking), nothing is clipped, and the 2160 render equals the 1080 render scaled. Until P2-08 builds photo export the check is the P2-01 render hook at both sizes; the final check runs with P2-08's exporter at QA (method at the end).

**The one blue action:** none. Formats and Layout are tools; blue appears nowhere new. White is selection (the selected tile, the selected cell's ring, the selected divider, frames and handles).

**Fast path, with tap counts:**
- **A classic meme** (the default): Photo meme (1), a photo (2), tap the top hint (3), type, Done (4); tap the bottom hint (5), type, Done (6). Two captions in six taps; the hints say where to tap, so no tool needs finding.
- **A caption bar meme:** Formats (1), Caption bar (2), Done (3), tap the bar's hint (4), type, Done (5).
- **Two photos side by side:** Formats (1), Panels (2), Done, tap the empty cell (3), pick a photo (4). The first photo is already in cell 1.
- **Go back to a plain look:** Formats, Classic. (Classic with empty slots is the plain canvas: empty slots aren't drawn in the file.)

## Entry and exit
- **The Formats tool** is the first tool of the photo editor's main bar (icon `formats`, label "Formats"). It opens the **Formats panel**. **Decision (D1): a tool and a panel, not the board's permanent format row.** The board puts five chips between the stage and the tool bar. At 360 × 640 the stage is 440 dp, so a 52 dp row costs 12% of it on every screen, for a choice most memes make once, at the start. The panel refits the stage above it (P2-01) so every change is seen. Formats sits first so it is found without scrolling (the final main-bar order is in Layout).
- **Starts:** every new photo project starts in **Classic** (gallery or Blank). A draft saved before this ticket has no format and opens as Classic (D2, below).
- **Closing the panel:** Done, system back, Escape (the P2-01 back order). The format stays.
- **Kept:** the format and its parameters are in the project (autosave, undo). Not kept: selection, open panel.
- **Back order** (P2-01, with P2-03's typing step): typing commits → menu → panel or sheet → gesture in progress cancelled → a lifted row dropped → selection (layer, cell or divider) clears → editor closes.

## How a format works (the rules every format shares)
These rules are saved with every draft's look. All sizes are in **S, the canvas's shorter side** (1080 px at 1×, 2160 px at 2×), so a format scales exactly with the export size and never re-flows (`design/tokens.json` → `photo.formats`).

1. **Formats draw inside the canvas; they never change its ratio or size** (D4). The ratio pill still shows the canvas the user chose, and a later ratio change re-lays the format out inside the new canvas (all parts are fractions or multiples of S). Why not "the bar grows the canvas": it would change the ratio and size under the user's hands, every layer's coordinates (fractions of the canvas) and every caption's size (S) would move with each tap through the formats, and tapping through formats is how people look at them. The cost: a Caption bar or Demotivational frame leaves the photo a little smaller, and a photo whose shape doesn't match the room left shows the canvas background beside it. The panel says so (a hint line) and a taller canvas fixes it in two taps.
2. **Draw order** (`drawPhotoScene`): background (color or image) → the format's parts under the layers (bar, poster, frame, panels) → the layers bottom to top → nothing else. Editor-only marks (slot hints, dividers' handles, selection) are never in the render or the export.
3. **Switching format never deletes or moves the user's text, stickers, bubbles or extra photos** (D7). One undo step, "Format", covers everything the switch does. What it does do:
   - **Picture-area formats** (Caption bar, Demotivational): the **main photo** (the lowest image layer) is placed in the **picture area**: scale and center set so the whole photo lies inside it, centered. Leaving them, it goes back to filling the canvas (scale 1, centered). This only happens while the photo is **where the format put it** (its center within 0.5% of the canvas and its scale within 1% of the expected value); a photo the user has moved or zoomed stays where it is and the ratio-change rule keeps it inside. No toast.
   - **Panels:** the main photo **moves into cell 1** (its layer is removed, its file is kept in the cell). Other layers stay above the panels. Leaving Panels, **cell 1's photo comes back as the main photo layer** (fitted inside the canvas, scale 1); photos in cells 2 to 6 are dropped from the meme, with the toast "Photos in removed panels are gone from the meme. Undo brings them back." (the files stay while Undo can use them).
   - **Bubbles, Classic:** nothing moves.
4. **Slots** are empty places a format shows as hints; they are **not content**. A slot is **filled** when a visible text layer's center is within 0.1 of the slot's center, in canvas fractions (the same "taken" rule as P2-03's landing), so a caption the user already placed there fills it, and a text added by any route fills it. Deleting the text empties the slot again. **Empty slots draw nothing in the render or the export** (D3).
5. **The empty project rule** (P2-01): a project with no layers is empty whatever its format; choosing a format alone doesn't save a blank project, and empty slots and cells are not content. (Panels: a photo in a cell counts as content, so a blank project that gets a cell photo is saved at that commit.)
6. **Text added by the Text tool** (P2-03) first fills the format's first empty text slot, in the order of the format's slots; only when none is empty does P2-03's rule (top 0.25, bottom 0.75, middle 0.5) apply. A slot's **default style** applies to the new text (below), except in Classic, where the "last style used this visit" rule of P1-10 stays.

## Layout
Board: `design/screens/PhotoEditor.dc.html`. **Differences from the board, and why:** (1) no permanent format row: a Formats tool and panel (above); (2) the tile is named **Bubbles**, as the board's chip says (the PRD calls the format "Speech bubbles"); (3) the board's text bar is P2-03's Style panel.

### Main bar
Final order, replacing the line in P2-01: **Formats · [format tool] · Text · Stickers (P2-14) · Add photo (P2-13) · Layers · Draw (P2-05) · Adjust and Filters (P2-04) · Remove BG (P2-07) · Censor (P2-05) · Crop (P2-04)**. The **format tool** exists only for two formats and sits right after Formats because it belongs to it:
- **Panels:** **Layout** (icon `panels`), opens the Layout panel.
- **Bubbles:** **Add bubble** (icon `bubble`), adds a bubble and opens Typing at once.
- Classic, Caption bar, Demotivational: none.
At 360 dp, five tools show whole (Formats, Layout or Add bubble, Text, Add photo, Layers): the main bar never scrolls in this ticket.

### Formats panel (ToolPanel)
Title "Formats", Done. One row: five **OptionTiles** (`design/system/components/OptionTile/README.md`, stacked), in this order: **Classic · Caption bar · Panels · Demotivational · Bubbles**, in a horizontally scrolling row (padding inside the scroll, `space-2` gaps); the selected tile is scrolled into view when the panel opens. Each tile's picture is **drawn from data** like the ratio shapes (`FormatShape`, single color, solid, 1 dp corners, long side 20 dp):

| Format | Picture |
| --- | --- |
| Classic | A stroked rectangle with a solid bar inside at the top and one at the bottom |
| Caption bar | A solid bar across the top of a stroked rectangle |
| Panels | Four solid squares in a 2 × 2 grid with gaps |
| Demotivational | A stroked rectangle inside a larger stroked rectangle, with two solid bars beneath |
| Bubbles | A solid rounded rectangle with a tail at its lower left |

- A tap applies the format at once (one undo step "Format"), the panel stays open, the canvas above shows it. Tapping the selected tile does nothing and records nothing. Selected: white fill, `on-primary` label (OptionTile).
- **Hint line** under the tiles, in `caption` `text-secondary`: for **Caption bar** and **Demotivational**, when the main photo doesn't fill the picture area's width (the canvas background shows beside it): "The photo fits inside what the format leaves. A taller canvas gives it more room." (`formats_hint_room`). It explains instead of disabling (design system). Not shown when the shapes match within 1%.
- **Panel height:** title 48, tiles about 84, hint about 40: under 190 dp, inside the 40% cap (256 dp at 360 × 640).
- **Canvas touches do nothing while the panel is open** (as the Canvas panel, P2-01: it is about the whole frame); the selection is kept, its frame and handle hidden.

### Classic
- **Parts:** none (no bar, no frame).
- **Slots:** two text slots, **top** and **bottom**: center `x` 0.5, center `y` at **0.14 S from the canvas edge** (so 0.14 H on a square, 0.079 H on a 9:16, 0.14 H on a 16:9): a caption of up to two lines sits inside the canvas, never against its edge. Default style: the last style used this visit, else Classic, White, Black outline (P1-10).
- **Order of filling:** top, then bottom.

### Caption bar
- **Parts:** a **white bar** (`#FFFFFF`) across the top, full width, **0.25 S tall** (270 px at 1080). Drawn under the layers.
- **Picture area:** the canvas below the bar.
- **Slot:** one text slot, centered in the bar (center `y` 0.125 S). Default style: **Modern, black (`#000000`), no outline**, scale 100%, so a new caption reads on the white bar whatever style was last used. (White text with an outline on a white bar is invisible, so this slot ignores the last-style rule.)
- The bar's color is fixed in this ticket (Later: a color row).

### Demotivational
- **Parts:** the whole canvas is painted **black** (`#000000`: the poster; it covers the canvas background, see the hint below). A **white frame** line (`#FFFFFF`, stroke 0.005 S) is drawn around a rectangle **inset 0.10 S from the canvas edges on the left, right and top, and ending 0.40 S above the bottom** (the text zone below it is 0.30 S tall with 0.10 S under it). All drawn under the layers.
- **Picture area:** inside the frame, inset a further 0.02 S (a 0.015 S gap plus the stroke). The poster's black shows inside the frame if the photo's shape differs from the area's.
- **Slots:** two text slots, centered: **title** at 0.09 S below the frame's bottom edge, **subtitle** at 0.21 S below it. Default styles: **title** Serif (Rozha One), white, no outline, scale 100%; **subtitle** Modern (Poppins), white, no outline, scale 45%. A two-line title overlaps the subtitle: the user scales it down (nothing re-flows by itself).
- **The Canvas panel's Background row** still works but the poster hides the color: the hint line under its swatches reads "Demotivational paints its own black background, so this color doesn't show." (`canvas_background_format`), the P2-01 "explain, don't disable" pattern. With an image background, the same.

### Panels
- **Parts:** 2 to 6 **cells** that fill the canvas edge to edge except a **gutter of 0.012 S** between cells and the same margin around them. The gutter and margin show the **canvas background** (color or image), so the Canvas panel's Background row styles them. Drawn under the layers; a cell clips its photo.
- **Cell contents:** a cell holds **one photo or nothing**. A photo is drawn **cover** (fills the cell, centered, overflow clipped) at zoom 100%; zoom 100% to 500%; its center can move so the cell stays covered (no gap ever). **An empty cell draws the gutter color in the render and the export** (it is background, D3); in the editor it shows a pick tile (below).
- **Layouts** (D5): per cell count, three families, **Grid**, **Side by side** (columns), **Stacked** (rows). Dividers start even.

  | Cells | Grid | Side by side | Stacked |
  | --- | --- | --- | --- |
  | 2 | (same as Side by side, not offered twice) | 2 columns | 2 rows |
  | 3 | one wide cell on top, two below | 3 columns | 3 rows |
  | 4 | 2 × 2 | 4 columns | 4 rows |
  | 5 | two on top, three below | 5 columns | 5 rows |
  | 6 | 2 rows of 3 | 6 columns | 6 rows |

  Cells are numbered in reading order (left to right, top to bottom). Choosing a count keeps the family when the count offers it (else Side by side) and **keeps each photo with its number**; a lower count drops the highest-numbered cells' photos with the toast above (Undo brings them back).
- **Dividers:** every gap between neighbours is a **divider**, draggable along its axis. A layout is a tree of splits, so in a Grid each row has its own vertical dividers (the 2 × 2 grid has three dividers: one horizontal, two vertical). Limits: each side of a divider keeps at least **15% of the split's extent**; a drag stops there, with no toast. **Snap to even** (the layout's default position) within 3% of the extent, one SegmentTick.
- **Handles:** each divider shows a **white pill** (`selection`, a `stroke-hairline` `on-primary` edge, 5 dp wide and 32 dp long, turned along the gutter) centered on its gutter, with a **48 × 48 dp** target; the handle wins over any layer beneath it (as the selected layer's handle does). Handles show while nothing is selected, or a cell or divider is selected; they hide while a layer is selected or a panel covers the stage. A selected divider draws its whole gutter in the two-tone selection line.
- **The pick tile** in an empty cell: a `surface-raised` rounded rectangle (`radius-md`, `space-3` from the cell's edges, at least 48 dp each way; never larger than the cell) with the `create` icon (`text-secondary`) over the label **Add photo** (`label`, `text`, 12.4:1). Editor only. A tap opens P2-12's one-photo pick path with the destination **this cell** (the sheet, the copy, the failures, all photo wording).
- **Canvas touches in Panels:** a tap on an empty cell opens the picker; a tap on a filled cell **selects it** (a `stroke-selection` white ring inside the cell's edge, 17.2:1 on `surface`, drawn over the content with the two-tone edge); a one-finger drag in a filled cell selects it and **pans** the photo 1:1; two fingers anywhere on the stage with a cell selected **zoom** (no rotation); a tap on the gutter or outside clears the selection. Layers above the cells take their own touches first (the top-most visible layer wins), except dividers' handles (above).
- **Selected-cell bar:** Close panel tools (`arrow_back`) · divider · **Replace** (`tool_replace`, opens the picker for this cell) · **Position** (opens the cell Position panel) · **Remove** (empties the cell, one undo step). No Delete: removing a photo is Remove, and a cell itself goes with the count.
- **Cell Position panel** (ToolPanel; P1-10's Position panel for a cell): **Size** (the zoom) with Smaller and Bigger on the ladder 100 · 110 · 125 · 150 · 175 · 200 · 250 · 300 · 400 · 500%, **Move** (four arrows, 1% of the cell's width or height per tap, hold repeats) and **Center**. No Rotation row. Limits explain on tap ("This is the smallest size.", "This is the biggest size.", "This layer can't go further this way."). Each tap, hold and Center is one step "Panel photo".
- **Selected-divider panel:** tapping a divider (not dragging) opens a small **Divider** ToolPanel (about 140 dp): title "Divider", Done, a Move row (two arrows along the divider's axis, 2% of the extent per tap, hold repeats) and an **Even** button (back to its default position). This is the single-pointer route for dragging (WCAG 2.5.7). A drag moves the divider live and commits on release; the panel doesn't open for a drag.
- **Layout panel** (ToolPanel, opened by the Layout tool): title "Layout", Done. Row **Number of panels**: five OptionTiles "2" to "6" (numbers are not translated), shape = the count's cells drawn small; row **Layout**: three OptionTiles **Grid · Side by side · Stacked** drawn from the layout (two for 2 cells); a secondary **Make panels even** button (every divider to its default). About 250 dp, inside the 40% cap at 800 (320), scrolling at 640 (256). Each tap is one undo step "Layout" (`panels_layout`); the cell photos follow their numbers.

### Bubbles
- **A bubble is a layer** (a new kind, `BubbleLayer`; D6): it moves, scales, turns, fades, locks, hides, reorders, duplicates and deletes like any layer (P2-01), is listed in the Layers panel (name: its first line, as text; thumbnail: the `bubble` icon on `surface`), and draws in layer order. **No limit** on how many.
- **Three shapes:** **Speech** (a rounded rectangle, corner radius 0.35 of its shorter side, with a pointed tail), **Round** (an ellipse with a pointed tail), **Thought** (an ellipse with a trail of three circles to the tip, radii 1.0, 0.65, 0.4 of 0.08 S, spaced evenly).
- **Body and words:** the body grows around its words: the words laid out by the caption renderer at **60% of the caption base size** (4.8% of S), wrapping at **55% of S** (at scale 1), centered, plus padding of 0.45 em at the sides and 0.35 em above and below; an empty bubble is at least 0.25 S wide and 0.15 S tall. The caption renderer's rules (fonts, Devanagari in Teko, emoji, the 250-character limit) apply otherwise. Default text style: **Bubbly (Baloo 2), black, no outline**.
- **Fill and stroke:** the body and tail are **one shape**, filled with the bubble's fill (default **white**; ten palette colors) and stroked in **black** at 0.006 S with round joins, so the stroke runs round the whole outline and never across the join between body and tail. The stroke is fixed (Later: a color).
- **The tail:** its tip is stored as an offset from the body's center in body widths and heights (`tailX`, `tailY`), **default** (−0.25, +0.85) for a bubble in the top half and (−0.25, −0.85) in the bottom half (it points toward the canvas center). The tail leaves the body at the boundary point on the line to the tip, **0.25 of the body's shorter side** wide at its base. Limits: the tip stays within 2 body widths and heights of the center.
- **The tail handle:** a selected bubble shows two handles: the scale-and-rotate handle at the frame's bottom-right (as every layer) and a **tail handle** at the tip: a `canvas-handle-size` white circle with the `on-primary` edge and the `position` icon at `icon-small` in `on-primary`, target 48 dp. Dragging it moves the tip 1:1 and commits on release (one step "Tail"). Where the tail handle and the other handle would overlap, the tail handle wins.
- **Selection frame:** around the body **and** the tail (the box of the whole drawn shape), two-tone as P1-10.
- **Add bubble tool:** lands a new bubble **at the first free of top (center `y` 0.25), bottom (0.75), middle (0.5)** (taken within 0.1 by a visible bubble), centered, scale 100%, default tail, at the front of the stack, and **opens Typing at once** (as the Text tool). Committed non-blank: one step "Add bubble"; blank: nothing is added (as P2-03). It uses the last bubble style of the visit (shape, font, color, fill), else the defaults.
- **Slot:** one **bubble slot** at the top position (the hint "Tap to add a bubble"); it is filled when a visible bubble's center is within 0.1 of it. A tap on the hint is the same as Add bubble.
- **Selected-bubble bar:** the P2-03 text bar: **Edit text · Style · Position · Delete · Opacity · Duplicate · Lock · Hide · Layers** (Unlock first when locked). **Style panel** for a bubble: **Shape** (three OptionTiles drawn from the shape, first row), **Font**, **Color** (the words' color), **Fill** (ten Swatches). No Outline row. **Position panel** for a bubble: Size, Rotation, Move, **Tail** (four arrows moving the tip 1% of the canvas per tap, hold repeats, and a **Reset tail** button), Center. Tail steps are one step "Tail"; the single-pointer route for the tail handle (WCAG 2.5.7).
- A bubble's words are edited through Typing exactly like text (a tap on the selected bubble, Edit text, TalkBack's double-tap).

### The slot hint
An empty text or bubble slot shows an **editor-only hint**: a `scrim` rounded rectangle (`radius-md`), `space-3` padding, at least `touch-target` tall, centered on the slot, with the label **Tap to add text** (`format_slot_hint`; **Tap to add a bubble** for the bubble slot) in `label` `text` (7.5:1 or more over any canvas color). Never wider than 90% of S. It is not saved, not rendered, not exported. A tap opens **Typing for a new text placed in that slot** (the slot's default style, the slot's position: for a text slot the block is centered on the slot's center). The hint is shown **while nothing is selected and no panel is open**, and is hidden while a gesture is in progress or Typing is open; the hint of the slot being typed into gives way to the live caption. When every slot is filled, no hint shows.

### The empty-canvas card
P2-03's card (title "This canvas is empty") shows only when the canvas has **no layers and nothing invites**: no empty slot hint and no empty cell. Every format has at least one hint or pick tile, so **once this ticket is in the card never shows**: the hints and pick tiles are the call to action, on a fresh blank canvas and after the last layer is deleted alike (Undo stays in the top bar, enabled). The engineer may remove the card and its two bodies (`photo_empty_*`) in this ticket or leave them dormant; if P2-02 is split and 02a ships first, Classic's hints already take over.

## Stage and panels
As P2-01: opening a panel refits the frame above it, 200 ms scale, no re-decode. The Formats, Layout, Divider and cell Position panels are about the format and the frame; their heights are content-sized up to the 40% cap.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Main bar | Formats · [Layout or Add bubble] · Text · Add photo · Layers | `tool_formats`, `tool_panels_layout`, `tool_add_bubble` |
| Formats panel | Five tiles, the current one white; the hint line when the photo doesn't fill the area | `formats_title`, `format_*`, `formats_hint_room`, `panel_done` |
| Classic, nothing placed | The photo on the canvas; two slot hints (top, bottom) | `format_slot_hint` |
| Caption bar | The white bar; the photo below; one slot hint in the bar | `format_slot_hint` |
| Demotivational | Black poster, white frame, the photo inside it, two slot hints below | `format_slot_hint` |
| Panels, empty cells | Cells with pick tiles; cell 1 holds the photo; dividers' handles | `tool_add_photo` |
| Panels, a photo picking | P2-12's pick sheets over the editor; nothing changes if cancelled | `import_*` |
| Panels, a cell selected | White ring in the cell, the cell bar | `tool_replace`, `tool_position`, `tool_panel_remove`, `panels_close_cell` |
| Panels, a divider moving | The divider follows the finger; snaps to even with a tick | none |
| Panels, a divider selected | The gutter in the selection line; the Divider panel | `divider_title`, `panels_even` |
| Panels, a limit | The divider stops at 15%; nothing else | none |
| Panels layout panel | Number tiles, layout tiles, Make panels even | `panels_count`, `panels_layout_label`, `panels_*` |
| Photos dropped | The count went down or Panels was left; the toast | `panels_photo_dropped` |
| Bubbles, no bubble yet | One slot hint | `format_slot_bubble_hint` |
| A bubble selected | Frame around body and tail, the two handles, the bar | `tool_edit_text`, `tool_style`, `tool_position`, … |
| Bubble, Style panel | Shape tiles, Font, Color, Fill | `bubble_*`, `style_*` |
| Bubble, tail limit | The tip stops at 2 body sizes; no toast while dragging; a Tail step at the limit explains | `explain_layer_edge` |
| Locked bubble or cell | As P2-01: drag, Style, Position, tail and Remove explain; Unlock first on a locked bubble | `explain_layer_locked` |
| Ratio changed | The format re-lays itself; layers follow P2-01 and P2-03; the as-placed photo follows the picture area; the toast if a layer moved | `photo_layers_moved` |
| A cell's file missing | The cell shows the pick tile with the line "File missing" instead of the label; draws as background in the render | `layer_meta_missing`, `photo_missing_media` |
| A draft from a newer Memix names a format this build doesn't know | Opens as Classic; the unknown format's parts are not drawn; the id is kept until the user chooses a format | none |
| Long content (5 languages, 200% font) | Tile labels widen the tile (never cut); the hint wraps; panels scroll | none |
| Offline, permission | Don't apply | none |
| First run | Same as every run: no coach marks | none |

## Interactions
- **Applying a format:** at once, one undo step "Format", the panel stays open; an Undo toast names it ("Undo: Format"). After undo or redo the panel shows the restored format.
- **Taps while the picker launches** are ignored (P1-02).
- **Undo, redo:** P2-01's rules for every edit here ("Format", "Layout", "Divider", "Panel photo", "Add photo", "Remove", "Tail", "Add bubble", "Shape", "Fill" and the text edits of P2-03).
- **Haptics:** SegmentTick for each snap (a divider to even, a layer to its guides); LongPress when a row lifts (P2-01). Nothing else.
- **Keyboard (basic):** Tab reaches the top bar, the tool bar, the panel controls, every cell and every divider; Enter activates a cell (pick or select) and a tile; **a focused divider moves with the arrow keys** (2%); Escape is system back.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| A format appears or changes | At once (like every canvas choice, P1-11) | Same |
| The panel opens; the canvas refits | 200 ms slide; 200 ms scale about the frame's center | 120 ms fade; instant |
| A new photo lands in a cell | 120 ms fade-in of the picture | Same |
| Selection ring, divider line, handles | 120 ms fade | Same |
| Slot hints appear or go | 120 ms fade | Same |
| A divider follows the finger | 1:1, no easing | Same |
| Toasts | 120 ms fade | Same |

## Copy
English source; the engineer machine-drafts id, es, pt and hi (until P5). Budgets are characters. Reused, unchanged: `tool_replace`, `tool_position`, `tool_add_photo`, `tool_text`, `tool_edit_text`, `tool_style`, `tool_delete`, `tool_layer_opacity`, `tool_duplicate`, `tool_lock`, `tool_unlock`, `tool_hide_layer`, `tool_layers`, `style_*`, `font_*`, `color_*`, `position_*`, `explain_text_smallest`, `explain_text_biggest`, `explain_layer_edge`, `explain_layer_locked`, `photo_layers_moved`, `layer_meta_*`, `panel_done`, `import_*`, `canvas_background`.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `tool_formats` | Formats | 20 | Tool and panel name. A format is a ready-made meme layout |
| `formats_title` | Formats | 20 | Panel title |
| `formats_group` | Format | 16 | Spoken name of the tile group |
| `format_classic` | Classic | 14 | Tile. The familiar meme: words at the top and bottom |
| `format_caption_bar` | Caption bar | 16 | Tile. A white strip above the picture holding the words |
| `format_panels` | Panels | 14 | Tile. A picture split into several frames |
| `format_demotivational` | Demotivational | 16 | Tile. A black poster with a thin frame and a title (use your language's word for this meme style) |
| `format_bubbles` | Bubbles | 14 | Tile. Comic speech bubbles |
| `formats_hint_room` | The photo fits inside what the format leaves. A taller canvas gives it more room. | 90 | Under the tiles |
| `canvas_background_format` | Demotivational paints its own black background, so this color doesn't show. | 80 | Under the Canvas panel's swatches while that format is on |
| `format_slot_hint` | Tap to add text | 24 | Editor-only hint on an empty text slot |
| `format_slot_bubble_hint` | Tap to add a bubble | 28 | Hint on the bubble slot |
| `a11y_format_slot` | %1$s, empty. Tap to add text. | 40 | Spoken name of a slot; %1$s from the position words below |
| `format_slot_top` | Top | 12 | Spoken position of a slot |
| `format_slot_bottom` | Bottom | 12 | |
| `format_slot_bar` | Caption bar | 16 | |
| `format_slot_title` | Title | 12 | |
| `format_slot_subtitle` | Subtitle | 14 | |
| `a11y_format_set` | Format: %1$s | 30 | Announced politely after a tile tap |
| `tool_panels_layout` | Layout | 20 | Tool and panel title for the Panels format. How the frames are arranged |
| `panels_count` | Number of panels | 24 | Row label |
| `panels_layout_label` | Layout | 16 | Row label |
| `panels_grid` | Grid | 14 | Tile |
| `panels_columns` | Side by side | 16 | Tile |
| `panels_rows` | Stacked | 14 | Tile |
| `panels_even` | Make panels even | 24 | Button: every divider back to an even split |
| `tool_panel_remove` | Remove | 20 | Selected-cell tool: takes the photo out of the panel |
| `panels_close_cell` | Close panel tools | 24 | Screen reader only (the back arrow) |
| `divider_title` | Divider | 16 | Panel title and the spoken name |
| `divider_even` | Even | 16 | Button: this divider back to its even position |
| `a11y_divider` | Divider %1$d of %2$d, %3$d percent | 50 | Spoken name and value of a divider |
| `a11y_panel_cell` | Panel %1$d of %2$d | 24 | Spoken name of a cell |
| `a11y_panel_empty` | Empty. Double-tap to add a photo. | 50 | Appended for an empty cell |
| `panels_photo_dropped` | Photos in removed panels are gone from the meme. Undo brings them back. | 80 | Toast after a lower count or leaving Panels |
| `tool_add_bubble` | Add bubble | 20 | Tool for the Bubbles format. A comic speech bubble |
| `bubble_shape` | Shape | 16 | Row heading in a bubble's Style panel |
| `bubble_speech` | Speech | 14 | Tile |
| `bubble_round` | Round | 14 | Tile |
| `bubble_thought` | Thought | 14 | Tile. The cloud-like bubble for thinking |
| `bubble_fill` | Fill | 16 | Row heading: the bubble's color |
| `position_tail` | Tail | 16 | Row label in a bubble's Position panel. The pointed part that shows who is speaking |
| `position_tail_left` | Move tail left | 24 | Spoken labels of the four arrows |
| `position_tail_up` | Move tail up | 24 | |
| `position_tail_down` | Move tail down | 24 | |
| `position_tail_right` | Move tail right | 24 | |
| `position_tail_reset` | Reset tail | 20 | Button |
| `a11y_bubble_on_canvas` | Speech bubble: %1$s | 270 | Name of a bubble on the canvas; %1$s = its words |
| `a11y_layer_bubble_added` | Bubble added | 20 | Announced politely |
| `edit_format` | Format | 24 | Edit names after "Undo: " |
| `edit_panels_layout` | Layout | 24 | |
| `edit_panels_divider` | Divider | 24 | |
| `edit_panel_photo` | Panel photo | 24 | A photo added, replaced, removed, zoomed or moved in a cell |
| `edit_add_bubble` | Add bubble | 24 | |
| `edit_bubble_shape` | Shape | 24 | |
| `edit_bubble_fill` | Fill | 24 | |
| `edit_bubble_tail` | Tail | 24 | |

Translator notes:
- **Format names** are the names of meme styles, shown on small tiles: keep them short, and use the word your language's meme apps use (Classic and Demotivational especially). "Caption bar" is a strip, not a toolbar.
- **Panels** are the frames of a collage or comic page. **Cell** is never shown to the user.
- **"Bubble", "Tail", "Thought":** comic-book words (speech bubble, its pointer, the thinking cloud).
- **"Divider"** is the line between two panels.
- **Placeholders:** `%1$d` numbers and `%1$s` words are used as they are; the percent in `a11y_divider` is read "N percent".

## Accessibility
- **Formats panel:** ToolPanel rules (focus to the title on open, back to the tool on close; what it covers is gone for TalkBack). The tiles are a single-choice group "Format"; each reads its label, "selected" and "2 of 5". After a tap, "Format: Caption bar" is announced politely (`a11y_format_set`). Focus return after a panel closes is the open device check of P2-01 (review P4).
- **TalkBack order on the canvas** (P2-01's list): Close → pill → Undo → Redo → banner → **the canvas** → panel or tool bar. The canvas container's children: **cells** in reading order ("Panel 2 of 4", then "Empty. Double-tap to add a photo." or the photo state, "selected"), then **dividers** ("Divider 1 of 3, 50 percent"), then the **layers front to back** (P2-01, with bubbles reading `a11y_bubble_on_canvas` and "layer 2 of 4"), then the **slot hints** ("Top, empty. Tap to add text.", buttons).
- **Dividers** are adjustable nodes (a slider role, the range being the divider's limits, step 2% of its extent): TalkBack's up and down actions, volume keys and the arrow keys on a keyboard move them; after a move the new value is announced politely. The Divider panel and the Layout panel's Make panels even are the visible single-pointer routes (WCAG 2.5.7).
- **Actions on a cell node:** Add or Replace photo, Position, Remove (a filled cell). On a bubble node: the text actions of P2-03 plus **Move tail** is the Position panel's Tail row. Every drag has a button route: a divider (Divider panel), a cell's pan and zoom (cell Position panel), a bubble's tail (Position panel).
- **The handles** (scale-and-rotate, tail, divider pill) are hidden from accessibility services: drag-only, with the panels as the full alternative.
- **Touch targets:** every tool, tile, button, hint, pick tile and divider handle is 48 dp or more; a cell's hit area is the cell; a bubble's hit area is its body and tail box, grown to 48 dp.
- **Contrast:** the pick tile label `text` on `surface-raised` 12.4:1, its icon 5.7:1; the slot hint `text` on `scrim` 7.5:1 or more over any color; the selected cell ring white on `surface` 17.2:1 and 4.3:1 or more over any picture (two-tone); the divider pill white with a near-black edge, 4.3:1 or more over any color; tile labels as OptionTile; the hint lines `text-secondary` on `surface` 7.1:1.
- **200% font:** tile labels widen the tile (the row scrolls); hint lines wrap; panels scroll inside their cap; the slot hint wraps to two lines at most inside 90% of S; the cell bar is a ToolBar (labels wrap, the bar grows). Hindi at 200% is a screenshot (QA compares 7).
- **Reduce motion:** see Motion.

## Analytics
`tool_use` with `editor: "photo"`, once per committed undo step (never while dragging or holding; never when a tool only explains; never for opening a panel, selecting, or tapping the active format). `value` stays for the canvas tools only (owner, 21 Oct): **one id per format, no parameter**.

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A format is chosen (a tap on the active tile fires nothing) | `tool`: `format_classic`, `format_caption_bar`, `format_panels`, `format_demotivational`, `format_bubbles` |
| `tool_use` | A count or layout tap in the Layout panel, or Make panels even | `tool: "panels_layout"` |
| `tool_use` | A divider drag releases, a Divider-panel step, Even | `tool: "panels_divider"` |
| `tool_use` | A photo lands in a cell (pick or Replace) | `tool: "add_cell_photo"` |
| `tool_use` | Remove in a selected cell | `tool: "remove_cell_photo"` |
| `tool_use` | A cell's pan or zoom commits (gesture release or a Position-panel step) | `tool: "transform_layer"`, `"position_layer"` (P2-01's ids, as the PM clarified) |
| `tool_use` | A new bubble is committed | `tool: "add_bubble"`, `font` (the font id, as `add_text`) |
| `tool_use` | A bubble's words, font, color are edited | `edit_text`, `text_font` (with `font`), `text_color` (P1-10's ids) |
| `tool_use` | A bubble's shape, fill or tail changes (the tail drag release or a Tail step) | `tool: "bubble_shape"`, `"bubble_fill"`, `"bubble_tail"` |
| `tool_use` | A bubble gesture releases or a Position step | `transform_layer`, `position_layer` (PM, 28 Oct: bubbles reuse the layer ids) |
| `tool_use` | Anything else (text in a slot, lock, hide, opacity, reorder, delete, duplicate, undo, redo) | P2-01 and P2-03's ids, unchanged |

- **New ids for the PM to confirm:** `panels_layout`, `panels_divider`, `add_cell_photo`, `remove_cell_photo`, `add_bubble`, `bubble_shape`, `bubble_fill`, `bubble_tail` (the five `format_*` ids are the PM's of 28 Oct). They are the edit names' siblings, in the same style as `add_text`.
- A format chosen by a state restore (a draft opening) fires nothing. Analytics stay off until consent (P5-01).

## QA compares
Use the debug drafts `photo-format-classic`, `photo-format-caption-bar`, `photo-format-demotivational`, `photo-format-panels`, `photo-format-bubbles` (request 5) and a gallery start with a 4:3 photo.
1. **Formats panel** at 360 × 800 and 360 × 640: five tiles with Classic white, the hint line, the stage refit above it; then each tile selected (scrolled into view).
2. **Classic, Caption bar, Demotivational on a 4:3 photo start** at 360 × 800: the format parts, the photo placed, the slot hints; then a caption typed into a slot in each (Caption bar: black on white; Demotivational: serif title, small subtitle).
3. **Panels**: 4 cells at 2 × 2 with one photo and three pick tiles, dividers' handles; a cell selected (ring and the cell bar); the Layout panel; a divider selected (the Divider panel). Then the six-cell Grid and a Stacked layout.
4. **Bubbles:** the slot hint; a Speech, a Round and a Thought bubble with text; a selected bubble with both handles; the Style panel with Shape tiles; the Position panel with the Tail row.
5. **Switching:** Classic with a typed caption → Caption bar (the caption stays where it was; the photo placed) → Panels (the photo in cell 1, the caption above the panels) → back to Classic (the photo back, the toast about dropped photos if more cells were filled) → Undo of each step.
6. **Ratio change** on each format (4:3 → 9:16 → 16:9 → 1:1): every part re-lays out inside the new canvas, the as-placed photo follows the picture area, a moved photo stays, the toast when a layer moved.
7. **Hindi at 200% font**, 360 × 640: the Formats panel (tile labels, hint), the Layout panel, a slot hint, a Devanagari line in a bubble and in a Caption bar.
8. **Locked layers and cells:** a locked bubble (Unlock first, the tail handle inert), a locked photo layer in Panels.

**Measured: the Done when** (the PM's method in `docs/pm/acceptance-log.md`; the designer adds):
- **Reference renders:** for each format, on a 4:3 photo canvas (1440 × 1080 at 1×), the P2-01 render hook at **shorter side 1080 and 2160** (request 4): ten frames in `docs/qa/references/P2-02-formats/` (CLAUDE.md rule 5), with real text (P2-03): Classic with top and bottom captions; Caption bar with a two-line caption; Demotivational with title and subtitle; Panels as a 2 × 2 grid with three filled cells and one empty (the empty cell draws the background); Bubbles with one of each shape. Each is compared with a screenshot of the same scene on the phone, cropped to the frame and scaled to the same width.
- **Pass, per format and size:** every part (bar, poster, frame, cells, gutters, bubbles and tails) and every layer within 1% of the width in position, 2% in size, 1° in rotation; the same line breaks and glyphs; nothing clipped that the canvas shows; empty slots and empty cells draw nothing but the background; **the 2160 frame equals the 1080 frame scaled by 2** (lines, bars, corners, the frame stroke and the bubble stroke scale with S).
- **Final check with P2-08's exporter** (gate): the files open, are exactly 1080 and 2160 on the shorter side, and match the references.
- **Switching:** the sequence in compare 5 gives the specified results; one Undo reverses each switch exactly (the as-placed photo returns to its place; dropped cell photos come back).
- **Old drafts:** a draft saved at the schema before this ticket (the last P2-12 draft with a photo and text) opens as Classic, nothing moved; a Phase 1 video draft opens unchanged.
- **Speed and memory** (the PM's must-know 9): a Panels project with **six 12 MP cells** (a debug draft): each cell decodes at the size it is drawn at (cap 4096), never six full decodes; three scripted divider drags of 3 s each stay under 5% janky frames on the owner's phone (release-type build); report peak Graphics and Native Heap.
- **Leaks:** LeakCanary, 5 cycles of open, choose Panels, fill a cell, drag a divider, switch to Bubbles, add a bubble, close.

## Model
Changes needed from the design (the engineer owns the exact code; the PM's must-knows carry the version bump):

| Need | Model |
| --- | --- |
| The format | `PhotoScene.format`, a sealed type with five subtypes (`classic`, `caption_bar`, `demotivational`, `panels`, `bubbles`; stable `@SerialName`s), **default Classic**. A draft without the field is Classic. An unknown subtype (a newer draft) reads as Classic. Declarative and slot-based, so Phase 4's "photo format" templates (P4-05, P4-15) can carry it (PRD → Templates) |
| Panels | `panels(layout: String, dividers: List<Float>, cells: List<PanelCell>)`. `layout` is one of the layout ids in the table (`grid-3`, `cols-3`, …); `dividers` are the split positions in the layout's fixed order, each 0 to 1 of its split's extent; `PanelCell(source: MediaRef?, zoom: Float = 1, centerX: Float = 0.5, centerY: Float = 0.5)`, the center being the point of the picture shown at the cell's center |
| Bubbles | a new layer kind `BubbleLayer(id, text, style: CaptionStyle, shape, fill, tailX, tailY, transform, locked, hidden, opacity)`, `@SerialName("bubble_layer")`; `shape` one of `speech`, `round`, `thought`; `fill` a palette color |
| Slots | **Not saved.** Derived from the format and the layers (a slot is filled when a text or bubble is near it) |
| Media | `PanelCell.source` goes into `Project.mediaCopyPaths()` and `withMediaCopyPaths()` (`MediaInUse.kt`): otherwise the cleaner deletes the picture at the next start and a duplicate shares one file with its original |
| Version | Bump `CURRENT_SCHEMA_VERSION` (read the constant: 5 if P2-12 landed as 4) with a version-only step: the new field defaults to Classic and the new layer kind leaves old JSON untouched. Check by opening a draft saved at the previous schema |

**Constants** (not in `MemixTheme`; one commonMain place, say `PhotoFormatCatalog`, with a comment pointing to this spec and `design/tokens.json` → `photo.formats`): every number in this spec (bar height, poster margins, frame stroke, gutter, divider limits and snap, zoom range, bubble sizes and tail rules, slot positions, default slot styles). They are part of every saved draft's look.

## Requests to the principal mobile engineer
I don't edit code, so these are requests. The PM's must-knows (28 Oct) carry the checklist; these are what the design needs.
1. **Write the chosen model in `docs/TECHNICAL_DESIGN.md` before building** (the PM's must-know 1): the five subtypes, the layout table as data (a layout id gives the split tree, cell rects and divider order), `PanelCell`, `BubbleLayer`.
2. **Everything draws in `drawPhotoScene`:** background → format parts → layers, so the editor, the render hook and P2-08 can't disagree. Format parts are pure functions of (canvas, format, the main photo's shape): bar, poster and frame, the picture area, panels' cell rects and gutters, the slot positions. Bubbles draw as one path (body plus tail, one stroke), the words through the caption renderer with the bubble's wrap width and 60% size. A cell clips its picture at its rect.
3. **Decode cells at the size they are drawn at** (the P2-01 rule, cap 4096); cell photos share the scene's bitmap cache; release at close. Cell pan and zoom, divider drags and tail drags move in the draw phase and commit once on release.
4. **The hook:** `memix.renderScene` gains a shorter-side option (`memix.renderShortSide`, 1080 or 2160) beside the width (the caveat in the PM's row: a width of 1080 on a 4:3 landscape gives 1080 × 810). Debug scenes `photo-format-classic`, `-caption-bar`, `-demotivational`, `-panels` (a 2 × 2 with three photos) and `-bubbles` (all three shapes), each with real P2-03 text, plus `photo-panels-6` (six 12 MP cells) for the speed check.
5. **The main photo's placement** (rule 3 of How a format works) as one pure function (`placeMainPhoto(format, canvas, photoSize)` and `isAsPlaced(...)`) with a JVM check against the QA list; the as-placed test uses the 0.5% and 1% tolerances. Panels' move of the main photo into cell 1 and back is part of the same undo step.
6. **Ratio change:** the one `LayerFit` rule gains the format's re-layout (parts are derived, so nothing to move) and the as-placed photo's follow; a bubble's box is its body and tail; margin 2%, shrink floor 25% (as text). **P2-03's `TODO` for text is already in; bubbles join it.**
7. **Slots** are not state: compute filled or empty from the layers each frame in the draw or compose phase cheaply (a distance test on the text and bubble layers). The Text tool, the hints and "Add bubble" share the one landing function (P2-03 request).
8. **Tools and panels:** the Formats tool and panel, the Layout and Add bubble tools (format-specific, second place), the selected-cell bar, the Divider panel, the cell Position panel (P2-01's lifted Position panel without Rotation), the bubble Style (Shape row, Fill row) and Position (Tail row) variants. Components: **OptionTile** takes a drawn shape (`FormatShape`, layout shapes, bubble shapes), as it does for ratios; no new component. **New icons** (to the grammar, in DESIGN_SYSTEM): `formats`, `panels`, `bubble`.
9. **Analytics:** the ids above as new `EditorTool` values (`format_*`, `panels_layout`, `panels_divider`, `add_cell_photo`, `remove_cell_photo`, `add_bubble`, `bubble_shape`, `bubble_fill`, `bubble_tail`), once the PM confirms.
10. **Strings:** the table above in five languages; `photo_missing_media` (P2-03 wording) is reused for a cell's missing file.
11. **Size watch** (the PM's): past two build sessions, split as the PM proposes. **My suggestion for the line:** **02a** Classic, Caption bar, Demotivational (the model with all five subtypes so there is one schema bump, the Formats panel showing only the tiles that work, slots and hints, parts and the picture area, the as-placed rule, reference renders); **02b** Panels and Bubbles (dividers, cells, the pick destination, the new layer kind, the tail). Order inside the ticket: Classic, Caption bar, Demotivational, Bubbles, Panels last. A tile never appears before its format works (P0-05 rule: no dead controls).

## Changes to other specs (made with this spec)
- `P2-01-photo-editor-shell.md` → Main bar: the final order now starts with Formats and says how the format tool sits second.
- `P2-03-photo-text-and-stickers.md` → Landing and the empty-canvas card: a pointer to this spec (slots fill first; the card gives way to the hints).
- `P2-13-image-layers.md` → What lands: a new photo lands inside the picture area when the format has one.

## Proposals and questions

**For the PM**
1. **Confirm the ids:** `panels_layout`, `panels_divider`, `add_cell_photo`, `remove_cell_photo`, `add_bubble`, `bubble_shape`, `bubble_fill`, `bubble_tail`; the five `format_*` ids are yours. A switch to the active format fires nothing.
2. **Confirm the default** (below) and that a draft without a format opens as Classic.
3. **The reading of "1080 and 2160 px"** is yours (shorter side); the spec scales every part with S so the 2160 frame equals the 1080 frame scaled by 2.
4. **The schema bump** carries all five subtypes even if the ticket is split, so only one bump happens.
5. **Size:** L at the top and likely over: Panels and Bubbles are each M. The split line is in request 11.
6. **P2-11 (shadow and box):** follows this ticket. Its Style rows apply to text and bubble words alike (the bubble's words are a caption). Nothing here reserves space.
7. **Bubble words are a caption:** the renderer takes the wrap width and the size factor as parameters (55% of S and 60% for bubbles; 90% and 100% for text). The Done-when equality for captions (P2-03) is unaffected.

**For the owner** (each: the problem, what each option costs and gives, a recommendation; short answers are fine, "1A, 2B")

**1. Should a Caption bar or a Demotivational frame add room to the picture, or fit inside the canvas?**
- *Problem:* a caption bar normally makes the output taller (photo plus bar). This app's canvas has a chosen shape, so there are two ways to fit a bar in.
- *Considerations:* **A. Fit inside the canvas** (this spec): the canvas shape never changes under the user's hands, switching between formats to compare them is free and exact, and the build is the simplest (every part is a fixed share of the canvas). The cost: the photo is a little smaller, and with a photo-shaped canvas the background shows beside it (white by default, which looks like a white margin; the panel tells the user a taller canvas fixes it). **B. The format grows the canvas** (taller output, photo untouched): the look many caption-bar tools have, but every tap through the formats changes the canvas size and ratio, moves every layer and resizes every caption, and leaving a format has to undo that growth; roughly 2 to 3 more days and more risk of drift between editor and export.
- *Recommendation:* **A.** B can be added later as a "Fit canvas to format" button without changing saved drafts.

**2. Which format does a new photo start in?**
- *Problem:* the board shows Classic selected; Classic needs no layout change but can show two "Tap to add text" hints.
- *Considerations:* **A. Classic, with the hints** (this spec): the primary job, a photo with top and bottom words, gets two taps per caption and the hints show where to tap; the hints are editor-only and never exported. A user who wants a clean canvas sees two small gray hints until they type or pick another format (they cost nothing in the file). **B. No format (plain)**: nothing on the photo at start; the Text tool does the job in three taps per caption, and Classic would be an explicit extra tile that does little the Text tool doesn't.
- *Recommendation:* **A.**

**Later, no ticket:** a bar color row for Caption bar; a bubble stroke color; cell rotation and flip; dragging a photo from one cell to another; photo templates (P4-05).

No other owner decision is needed. The constants (bar height, frame, gutter, bubble sizes) can change in one place each before the closed test (about 25 Jan 2027); after it a change needs a schema bump.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Meme and collage tools offer a small set of ready layouts (top and bottom text, a caption strip, a grid of frames) chosen at the start | Assumption-level (product knowledge of Imgflip, Mematic, InShot; not confirmed from their help pages) | none |
| A drag needs a single-pointer alternative; a multi-point gesture too | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements), [SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures) |
| Non-text indicators need 3:1 against adjacent colors | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| A visible label is part of the accessible name | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Undo beats confirmation for reversible actions | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| Slot hints on an empty template cut the taps to a first caption from three to two | Assumption (tap-count reasoning; the P2 test decides) | none |
| Five tools fit whole in the main bar at 360 dp | Computed from the P2-01 renders (a tool is 64 dp wide, so 5.6 fit in a bar with no close button) | `docs/ux/reviews/P2-01/02-selected-layers10.png`, `docs/ux/reviews/P2-01.md` |
