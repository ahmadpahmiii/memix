# Photo editor shell: start, canvas, layers · P2-01

First spec for the photo editor. It reuses, and does not repeat:
- `P1-04-editor-and-preview.md`: editor frame, saving and the save-failure banner, undo and redo, back order, toasts.
- `P1-10-text.md`: caption gestures (one finger, handle, two fingers), snapping, the value bubble, the Position panel.
- `P1-11-canvas.md`: the ratio pill, the Canvas panel, custom ratios, the frame edge, the ratio-change rule.
- `P1-02-gallery-picker.md`: the system photo picker, the copy into the app, the import sheet, "Not enough space".
- `P1-14-drafts.md`: the Drafts list, opening a draft, the cover.

Siblings: `P2-12-canvas-background-image.md` (a picture behind the layers, both editors) and `P2-13-image-layers.md` (add a second photo as a layer). Both build on this file.

## Job
"Make a classic photo meme" (PRD → Jobs): a photo, then words on it. P2-01 is the base every later photo ticket stands on. It gets a photo (or a blank canvas) onto a canvas of the right shape, and makes **layers** behave: select, move, scale, turn, fade, lock, hide, reorder, delete, duplicate. Text, stickers, drawing, filters and formats arrive in P2-02 to P2-07; this ticket only builds what works today.

**Done when** (TICKETS): a 10-layer scene stays smooth while dragging; LeakCanary shows no leaks. The PM's method is in `docs/pm/acceptance-log.md` (P2-01 ready check). Measured items are at the end of this file.

**The one blue action:** none in P2-01. The board's blue **Save** stays out until P2-08 builds photo export, the same rule as P1-04, which left Export empty until P1-12: a control that does nothing is a dead control (P0-05 rule). P2-08 puts the button in the top bar's end slot and names it (a note for that spec: the video editor says "Export"; one word for both editors is the better default). The board's **Make it a video meme** pill waits for P2-10.

**Fast path, with tap counts:**
- **Photo meme from the gallery:** Home or Create → Photo meme (1) → a photo in the picker (2) → the editor, photo already on the canvas. Same three steps as the video editor.
- **Fix a shape:** the ratio pill (1), a ratio (2).
- **Protect the photo from stray drags:** tap the photo (1), Lock (2).
- **Fade a layer:** tap it (1), Opacity (2), drag the slider.

**Color roles:** blue appears nowhere new. White is selection (the frame and handle on the canvas, the selected row, the selected ratio tile, the selected swatch ring). The ratio pill takes the active look while its panel is open (P1-11).

## Entry and exit

### Starting a photo meme
```
Home "Photo meme" card ───┐
Create sheet "Photo meme" ─┴─> system photo picker (one image) ── nothing picked / back ──> where you were
                                     │ one photo picked
                                     v
                       Memix copies it ── under 300 ms ──> photo editor
                                     │ still copying at 300 ms
                                     v
                       import sheet (P1-02) ──> photo editor, or a result state

Create sheet "Start with a blank canvas" ──> photo editor, empty canvas (see Blank)
```
- **Decision: Photo meme opens the picker at once; Blank is its own row.** The PRD's Create row reads "gallery picker or a blank layout". The shipped copy already says "Pick a photo from your gallery" (`create_photo_body`), and most photo memes start from a photo, so the common path stays at three taps. A second sheet asking "photo or blank?" would cost every gallery start one more tap and a second sheet (Assumption: the split is mostly gallery; no data yet). A "Back out of the picker" cannot lead to Blank, because the system picker can't carry a Memix button, so Blank needs a door of its own.
- **The picker:** the same system picker, no permission prompt and no explainer (P1-02). Settings differ in two ways: **images only** (`PickVisualMedia.ImageOnly`; videos belong to the video editor) and **one item**. Animated GIF and WebP go in as their first frame (P1-02; moving stickers are P4-12). Phones without the system picker get the file chooser, images only, one file. Taps on the entry are ignored while the picker launches.
- **After the pick:** the import runs exactly as P1-02 describes (300 ms rule, the "Adding your photo" sheet with progress and Cancel, "Not enough space" and Free up space, "None went in" with **Pick again**, "No picker app"), with photo wording (Copy) and one change for a single item: the count line is dropped (it would read "1 of 1"); the percent stays. The Create sheet slides away and its scrim stays, as for video.
- **New project:** type photo, empty name (the Drafts list shows "Untitled photo meme"), one `ImageLayer` holding the copy, the canvas from the photo's own shape (below), a white background. It is saved once the photo lands, and `project_create` fires then (`editor: photo`, `source: gallery`), as P1-02 does for video. Nothing is saved for a pick that was backed out of, cancelled or where nothing went in.
- **The canvas from the photo's shape.** A photo meme should keep the picture whole, so the canvas takes the photo's own shape:
  1. The photo's shape is its width ÷ height as shown (EXIF turn applied, `MediaRef.pixelSize`).
  2. Within 1% of a preset ratio (9:16, 1:1, 4:5, 3:4, 16:9): that preset.
  3. Otherwise a **Custom** ratio: the one with the fewest terms (1 to 99 each) whose shape is within 1% of the photo's. A 4:3 camera photo gives "4:3", a 3:2 photo "3:2", a 1170 × 2532 phone screenshot "6:13".
  4. A photo more extreme than a custom canvas may be (over 2.39:1 either way, P1-11) gets the widest allowed shape (31:13 or 13:31) and sits whole inside it, with the background showing above and below, or left and right. It is never cropped silently.
  5. Canvas size follows P1-11's size table (shorter side about 1080 px), so captions keep one size across every shape. The photo is drawn from its full-size copy; its pixel size doesn't set the canvas (export scales the canvas, P2-08).
- **Blank canvas:** 1:1, 1080 × 1080, white. Square is the shape that posts everywhere without a crop (Assumption), and white shows the frame against the black stage, which a black canvas wouldn't (P1-11's frame edge fixes that only while a panel or gesture is active). Defaults are constants (`design/tokens.json` → `photo.defaults`), so the owner can change them in one place.
  - **The Create row ships with P2-03.** A blank canvas can hold nothing until a ticket can add a layer (text, P2-03), so its row stays out of the Create sheet until then, by the P0-05 rule. P2-01 builds the code path (use case, defaults, empty state, `source: blank`) and reaches it by the `photo-empty` debug hook; P2-03 turns the row on with one constant.
  - **Saving a blank project** follows the PM's 27 Oct clarification: it is made in memory and saved at its first committed step that leaves at least one layer, which fires `project_create` (`editor: photo`, `source: blank`). Changing the ratio or color alone doesn't save it. Closing with no layer discards it with no message: nothing of value was made.

### Opening and closing
- **Arrives from:** the start flows above and the Drafts list (a photo row opens the photo editor; `App.kt` routes by project type). Every entry opens the same state: nothing selected, no panel open, the undo history empty.
- **Close** (top-left) or system back, after the back order below → the screen the flow started from. Any pending save is written first; there is no "Save?" question (autosave, P1-04). The one exception is a failing save (P1-04 → Saving: the banner and the unsaved-changes sheet apply here unchanged).
- **System back order:** an open menu closes → an open panel or sheet closes → a gesture in progress is cancelled (the layer returns to where it was) → a row lifted in the Layers panel is dropped back → the selection clears → the editor closes. Predictive back shows the previous screen only at the last step.
- **App to the background:** the pending save is flushed. On return the editor is unchanged.
- **Kept:** the project. Not kept: selection, open panel, undo history.
- **Leaving with no layer** (a blank canvas, or every layer deleted): the draft is deleted, as the PM's clarification says. No message.

## Layout
Board: `design/screens/PhotoEditor.dc.html` (390 × 844). The regions, top to bottom:

| Region | Height | Notes |
| --- | --- | --- |
| Status bar inset | system | `canvas` behind it (edge to edge) |
| Top bar | 56 | Close · ratio pill · Undo · Redo (below) |
| Stage | the rest | `stage` black; holds the canvas frame |
| Tool bar, or a tool panel | `toolbar-height` (72), or the panel's height | below |
| Navigation inset | system | `surface`, below the bar |

**Differences from the board, and why:**
1. **No Save button, no "Make it a video meme" pill** until P2-08 and P2-10 (see the blue action above).
2. **No format bar.** Formats are P2-02's. It adds a row between the stage and the tool bar; the stage gets shorter by its height then. The board's text bar becomes P2-03's Style panel (P1-10 spec, difference 1).
3. **The tool bar holds one tool, Layers.** The board's other tools (Text, Stickers, Remove BG, Draw, Adjust, Censor, Crop) come with their tickets and never appear as dead placeholders.
4. **Undo and Redo move to the end of the top bar, and the ratio pill takes the center** (P1-04 reserved the center for it). The board centers undo and redo. At the end of the bar they cost the stage no height; the trade-off is thumb reach (top-right, Assumption), accepted because undo follows a mistake and is not the primary action.
5. **The sample content** (the figure, the two captions) is replaced by the user's layers.
6. **The stage refits above a panel** (below), unlike the video editor, whose timeline reserves a fixed space.

### Top bar
`canvas`, no divider, as P1-04. **Start:** Close (`MemixIcons.Close`, 48 dp target; spoken "Close editor"). **Center:** the ratio pill (SettingPill, `P1-11-canvas.md`; label the current ratio in lowest terms, "4:3"; open look while the Canvas panel is open). **End:** Undo, Redo (IconButtons, 48 dp, `text`, disabled `text-muted` with a spoken "disabled", exactly as P1-04 → Undo and redo). P2-08's button joins them at the end.
- **Collision rule (P1-12's lesson):** the bar is a custom layout. Close and the end group are measured first and never shrink; the pill is centered on the screen but clamped between them with `space-2` to spare. At 200% font the pill is the only part that grows, and 360 dp leaves room (Close 48, pill about 120, Undo and Redo 96). **P2-08 must re-check this at 200% in es and pt when it adds its button**; if the pill no longer fits, it moves under the bar, not under the button.

### Stage and canvas frame
- **Stage:** `stage` black, from under the top bar to the top of the tool bar or panel.
- **Canvas frame:** the largest rectangle of the canvas's ratio that fits the stage, centered, inset **`space-4`** on every side. The video editor insets `space-2`; here the frame needs more, because a full-bleed photo's scale-and-rotate handle sits on the frame's corner, and a 24 dp handle (12 dp radius) plus the frame's `space-1` outset must lie whole on the stage. `space-4` makes it tangent.
- **Frame content:** the canvas background (white by default, a color, or P2-12's picture), then the layers bottom to top, each drawn by the one scene-drawing function (request 5), clipped at the frame. Anything a layer draws beyond the frame is cut, as in export.
- **Before the first draw:** the frame shows the background color at once. Each layer fades in over 120 ms when its picture is decoded (no spinner and no shimmer: nothing loops, MOTION 2).
- **Frame edge:** as P1-11 (a `text-muted` hairline just outside the frame, 6.9:1 on `stage`), shown only while the Canvas panel is open and while a layer is being moved, sized or turned. Never otherwise: the meme is the most colorful thing on screen.
- **Toasts and the save banner** sit at the top of the stage (Toast component).

Worked sizes (dp, 100% font; stage = window − system bars − 56 − 72):

| Phone | Stage | 9:16 frame | 1:1 frame | With the Layers panel (stage; 1:1 frame) |
| --- | --- | --- | --- | --- |
| 360 × 640, 3-button nav | 440 | 229 × 408 | 328 × 328 | 256; 224 × 224 |
| 360 × 800, gesture nav | 624 | 328 × 583 | 328 × 328 | 392; 328 × 328 |
| 412 × 915, gesture nav | 739 | 380 × 676 | 380 × 380 | 507; 380 × 380 |

The Layers panel row assumes three layers, one selected (its height is capped at 40% of the window, below). On the smallest phones the canvas is small while a panel is open; Done gives it back. That is the trade for seeing the effect of every change (Principle 1 against Principle 3; the video editor makes the same trade at 123 × 219).

### Tool bars
Component ToolBar (`design/system/components/ToolBar/README.md`, updated with this spec).

**Main bar (nothing selected):** **Layers** only (icon `layers`, new). It opens the Layers panel. Final order of the photo editor's main bar, so each tool keeps its place as tickets add it: **Formats (P2-02) · [Layout (Panels) or Add bubble (Bubbles), P2-02] · Text (P2-03) · Stickers (P2-14) · Add photo (P2-13) · Layers** · Draw (P2-05) · Adjust and Filters (P2-04) · Remove BG (P2-07) · Censor (P2-05) · Crop (P2-04). **Decided in the P2-02 spec: Formats is the bar's first tool** (a tool and a panel, not the board's permanent format row). Before a tool exists, the others sit one place to the left; users only ever see the final order.

> **Hook, not built in P2-01:** the main bar's **Add photo** tool (just before Layers) and a first row **Add photo** at the top of the Layers panel's list belong to P2-13. Nothing in P2-01 shows, reserves space for, or announces them.

**Selected-layer bar (a layer is selected):** the same bar, as P1-06's clip bar: a 48 dp **Close layer tools** button (`arrow_back`, spoken "Close layer tools"), a `hairline` divider, then the tools, scrolling sideways:

**Position · Opacity · Delete · Duplicate · Lock · Hide · Layers**

- **Why this order:** Position is the non-gesture route for move, size and turn (WCAG 2.5.1 and 2.5.7), so it is always one tap away; Delete is third and stays in view at 360 dp (ToolBar rule); Layers is last, since it is a shortcut (Close layer tools, then Layers does the same).
- **Lock and Hide swap their label and state:** a locked layer's bar **starts with Unlock**, in front of Position (the Lock place is empty then); the other tools follow in their usual order. Why first: while a layer is locked, Position, Opacity and Delete only explain (see Locked layers), so the first three tools would be dead ends, and Unlock would sit fifth, off screen at 360 dp and not even in the first three tools at 200% font in es and pt (review P2-01, 28 Oct). Lock and Unlock never swap places by themselves: tapping Lock on an unlocked layer reorders the bar, so **the bar scrolls back to its start** at the same moment (instant), and the new first tool is the one in view. If the file is missing as well, Unlock is first and Delete second. A hidden layer shows **Show** in the Hide place. Each tool's icon shows the layer's **current state** (a closed lock on a locked layer, a slashed eye on a hidden one) and its label says the action ("Unlock", "Show"). This is the P1-09 pattern for the original-audio toggle (icon = state) with the action named in the label. TalkBack reads the bar in this visual order.
- **A layer whose file is missing:** Delete comes first (it is what fixes it; the toast names it), then the usual order.
- **Look:** tools `text-secondary` icon and label; Delete `danger` (with its word); every label keeps two lines of room (ToolBar rule). The bar crossfades with the main bar over 120 ms.
- **Later tickets** add each layer type's own tools to this bar. Text (P2-03) is **Edit text · Style · Position · Delete · Opacity · Duplicate · Lock · Hide · Layers**: Delete stays fourth so it is in view at 360 dp (see `P2-03-photo-text-and-stickers.md`).

### Layers panel (ToolPanel, sized by its content)
Opened by the Layers tool. ToolPanel rules apply (`design/system/components/ToolPanel/README.md`): `surface`, `hairline` top border, `space-4` padding, no scrim, no shadow, title in `title`, **Done** (Ghost). **Photo-editor exception:** the panel is as tall as its content up to **40% of the window height** (256 dp at 360 × 640), then the list scrolls inside; and since no timeline reserves that space, the stage refits above the panel (Stage behavior below).

Top row: **Layers** (a heading) at the start; at the end **Move layer up** and **Move layer down** (IconButtons, `arrow` turned up and down, 48 dp, shown only while a layer is selected), then **Done**. Where the row doesn't fit (200% font), the two buttons wrap onto a second line under the title, never cut.

Then, while a layer is selected, **Opacity** (Slider: label "Opacity", readout the percent in `timecode`; range 10% to 100%, default 100%). Then the **list**: one LayerRow per layer, **front layer first** (the top row is the top-most layer, as every layers panel does), `space-1` apart, scrolling.

**Where the Opacity row lives (review P2-01, 28 Oct).** The list is the panel's job, so it must always have room for at least two rows (2 × 56 + `space-1` = 113 dp). Beside a fixed Opacity row it has about 96 dp at 360 × 640 and about 40 dp at 200% font (the renders show half a row), so:
- **Roomy** (panel cap 300 dp or more and font scale under 1.3, for example 360 × 800 at 100%): the Opacity row is fixed above the list, as drawn.
- **Tight** (cap under 300 dp, or font scale 1.3 or more): the Opacity row is **the list's first item** and scrolls with the rows. Opening the panel brings the selected row into view as always, so the slider may start scrolled out of sight; the **Opacity tool on the selected-layer bar** is the route to it from there (one tap, a panel of its own, always roomy). The Move buttons stay in the fixed top row.

**LayerRow** (`design/system/components/LayerRow/README.md`, new): at least 56 dp tall (`layer-thumb-size` 40 plus `space-2` above and below), `surface-raised` fill, `radius-md`. From the start:
1. **Thumbnail:** `layer-thumb-size` (40, new token) square, `radius-sm`, `space-2` from the row's start: the layer's picture, fitted inside. No checkerboard (a transparent picture shows on `surface`).
2. **Text:** `space-3` after the thumbnail. Name in `body-strong` `text` (one line, ellipsis at the end). An image layer is named "Photo", the same for every one: the thumbnail and the position tell them apart, and a number that changes when you reorder would mislead. Below it, a meta line in `label` `text-secondary`, only when something is worth saying: parts from this list in this order, joined with the separator string: **Locked · Hidden · File missing · 40% opacity**.
3. **Lock:** IconButton, 48 dp: `unlock` icon in `text-muted` when free (4.6:1, quiet but discoverable), `lock` in `text` when locked.
4. **Visibility:** IconButton, 48 dp: `eye` in `text-secondary` when shown, `eye_off` in `text` when hidden.

Row states:

| State | What shows |
| --- | --- |
| Normal | As above; one line of text |
| Selected | A `stroke-selection` white ring inside the row's edge (17.2:1 on `surface`). `surface-raised` can never mark selection (1.4:1) |
| Pressed | `surface-raised` → `hairline`, 120 ms |
| Locked | Meta "Locked"; `lock` icon in `text` |
| Hidden | Thumbnail at 40%, name in `text-secondary`, meta "Hidden", `eye_off` in `text` |
| Faded (opacity under 100%) | Thumbnail drawn at the layer's opacity but never below 40% (so it stays recognizable); meta "40% opacity". Both effects are needed: at 10% the thumbnail alone would be a ghost |
| File missing | Thumbnail is a `surface` square with a `photo` icon in `text-muted`; meta "File missing" |
| Lifted (dragging) | The white ring, the row follows the finger vertically; the others slide 200 ms to open the slot; LongPress haptic at the lift. No shadow and no scale (a shadow is invisible on `surface` and dirt on content) |
| Several at once | Parts join: "Locked · 40% opacity" |

**Interactions in the panel:**
- **Tap a row:** selects that layer (the canvas shows its frame). Tapping the lock or the eye toggles only that, and **doesn't select the row**.
- **Selecting on the canvas** (a tap on a layer while the panel is open) selects its row and scrolls it into view (`BringIntoView`). The same after undo or redo.
- **Reorder by drag:** long-press a row (LongPress haptic), drag up or down, drop. Near the panel's top or bottom edge the list auto-scrolls.
- **Reorder without dragging** (WCAG 2.5.7): **Move layer up** and **Move layer down** in the top row, for the selected layer; each tap moves it one place (200 ms). At an end the button explains. TalkBack and Switch Access get the same moves as actions on every row (Accessibility).
- **Layer tools the panel doesn't have** (Delete, Duplicate, Position) are on the selected-layer bar: close the panel with Done, and the layer stays selected.
- **Opening it** with a layer selected shows its Opacity row and keeps its row in view; with none selected there is no Opacity row (more room for the list).
- **One layer:** Move up and down explain "Reordering needs at least two layers."
- **Locked layer selected:** the Opacity row becomes a message with an action (P1's "errors say what to do next"): `lock` icon, "Locked. Unlock this layer to change its opacity." and a secondary **Unlock** button. Move up and down explain.

### Opacity panel (ToolPanel)
Opened by Opacity on the selected-layer bar. Title "Opacity", Done, one Slider (`design/system/components/Slider/README.md`): the same row as in the Layers panel. About 130 dp tall, so the canvas stays large while the user judges the fade. Live on the canvas while dragging; one undo step on release ("Opacity"); snaps to 100% within ±3% with one SegmentTick; keyboard and TalkBack steps of 10% landing on multiples of 10, so 100% is reachable (Slider rules). Minimum 10%: a layer at 0% would be invisible but still selectable, a state worse than Hide, which exists for that.

### Position panel (ToolPanel)
The P1-10 Position panel for layers (`P1-10-text.md` → Position panel): Size, Rotation, Move, Center. It is the single-pointer, no-drag route for the canvas gestures (WCAG 2.5.1 and 2.5.7) and for exact nudges; the video editor already built it for text, and the ticket lifts it into `:core:ui`. Differences for layers:
- **Size ladder:** 10 · 15 · 25 · 33 · 50 · 67 · 75 · 90 · 100 · 110 · 125 · 150 · 175 · 200 · 250 · 300 · 400 · 500%. **100% means the picture fitted inside the frame** (Gestures → Size).
- **Limits** explain on tap, as P1-10: "This is the smallest size." at 10%; "This is the biggest size." at 500%; a Move arrow with the center already at that edge: "This layer can't go further this way."
- **Undo steps:** each tap, each hold (on release) and Center is one step, "Position and size".
- It covers what's behind it and the stage refits above it, as every panel here.

### Canvas panel (ToolPanel, the photo version)
Opened by the ratio pill; P1-11's panel with these rows only:
1. **Ratio:** the six OptionTiles (9:16, 1:1, 4:5, 3:4, 16:9, Custom), as P1-11. With a photo-derived canvas the Custom tile is selected from the start; its steppers show the ratio's terms. (The video's panel scrolls the selected tile into view; do the same.)
2. **Custom:** the Width and Height steppers, as P1-11 (1 to 99, at most 2.39:1, hold to repeat, one step on release).
3. **Background:** a Swatch row: the ten caption-palette colors in palette order, **after P2-12's Image swatch**, which comes first. White is the default.
4. **The hint line**, under the swatches, in `caption` `text-secondary`: "Your photo covers the whole frame, so the background doesn't show." Shown when a visible, fully opaque, unturned layer covers the whole frame (its box reaches every edge). The swatches still work. It explains instead of disabling (design system), because tapping Yellow with nothing visibly changing reads as a broken control. A turned or faded layer never triggers it (the engineer can't cheaply prove a turned layer covers; conservative is fine).

**Not in the photo panel:** Blur (it blurs a playing clip; there is none) and Fit or Fill (a layer's size is the layer's own). The PRD says "color or image".
- **Choices apply at once**, each one undo step ("Ratio", "Background"). The canvas stays visible above the panel; touches on the canvas do nothing while it is open (the panel is about the whole frame, P1-11), and the selection is kept but its frame and handle are hidden.
- **Panel height:** about 236 dp with Custom closed (title 48, Ratio 84, Background 72, gaps), capped at 40% of the window like every panel here; it scrolls inside. Custom adds about 112.

### Stage behavior around panels
- Opening or closing a panel **refits** the canvas frame: it takes the largest size that fits above the panel, and animates there over 200 ms (`duration-sheet`) as a scale about the frame's center, so no layer moves relative to the canvas. Reduce motion: instant.
- The frame never gets larger than its no-panel size.
- **The pictures are not re-decoded for the animation:** the bitmaps stay as decoded for the no-panel size, and draw scaled down while a panel is open (Performance).
- **Why refit and not overlay:** a panel over the canvas would hide the layer being adjusted, and a scrimmed sheet is worse (ToolPanel README). The video editor can promise "the preview never jumps" because its timeline reserves the space; the photo editor has nothing else to reserve it with. The text-typing flow (P1-10) already refits the stage above the keyboard, so the behavior is precedent.

## Layers on the canvas

### What a layer is
The model's `Layer` list, bottom to top (P2-01 draws image layers; the model also has text and sticker layers, which P2-03 draws). Each layer has `transform` (center X and Y as fractions of the canvas, scale, rotation clockwise) and, new in this ticket, `locked`, `hidden` and `opacity` (Model).
- **The first photo is an ordinary layer.** Nothing marks it as "the base": it is the first image layer, at the back. It can be moved, locked, hidden, faded and deleted like any other.
- **Hidden layers** aren't drawn, in the editor or in export, and can't be hit on the canvas. **Faded layers** draw at their opacity. Locking changes neither.

### Size at scale 1
- **An image layer at scale 1 is the picture fitted inside the frame**: scaled uniformly to the largest size that lies wholly inside the canvas, centered. It is recomputed from the canvas's current shape every time the layer is drawn, so it follows a ratio change (below) and a second photo "fitted inside the frame" (P2-13) is simply scale 1.
- **Cover within 1%:** a picture whose shape is within 1% of the canvas's (P1-11's `SAME_SHAPE_TOLERANCE`) is drawn to cover the frame at scale 1, so a photo-derived canvas never shows a sliver of background on one edge (a 1% crop, never a 1% bar).
- **Scale limits for images: 10% to 500%** of the fitted size. Text keeps its own 25% to 500% (P1-10). 10% of a 4:3 photo on a 1080-px canvas is about 108 px wide, still readable as a small inset. Above 100% the picture extends past the frame and is cropped at its edge in preview and export.
- **Center limits:** the center stays within 0…1 of the canvas on both axes, so a layer is never lost off the frame.

### Hit testing: which layer a tap selects
- The **top-most visible layer under the finger** wins. Hidden layers are skipped. A layer's hit area is its picture's box, turned with it, **grown to at least `touch-target` (48 dp) in each direction** so small layers can be hit (P1-10 rule).
- Transparent parts of a picture count as part of its box. Picking through a layer to the one beneath is done in the Layers panel, which lists every layer, hidden ones included. No "tap again to cycle" in v1.
- **The handle** of the selected layer wins over any layer beneath it.
- A touch that hits no layer is on the background.

### Gestures
Same feel as P1-10's captions, on every layer type (`P1-10-text.md` → On the canvas):

| Gesture | On | Result |
| --- | --- | --- |
| Tap | A visible layer | Selects it (frame, handle, selected-layer bar). Tapping the selected layer does nothing in P2-01 |
| Tap | The background, or the stage outside the frame | **Clears the selection.** Unlike the video editor there is no play/pause to compete with a tap, so a tap on nothing is how you put a layer down |
| Drag (one finger) | Any visible layer | Selects it if it isn't, then moves it 1:1 with the finger |
| Drag | The handle | Scales and turns the layer around its center (distance sets size, angle sets turn) |
| Two fingers (pinch, twist, pan) | Anywhere on the stage, with a layer selected | Scale, rotation and position together. With nothing selected, two fingers do nothing |
| Long-press | A layer | Same as a tap |

- **The selection frame** is P1-10's: `stroke-selection` white with a `stroke-hairline` `on-primary` edge, `space-1` outside the layer's box and turned with it; it may extend onto the stage beyond the canvas and is clipped at the stage edge (P1-10 review M1). The handle: `canvas-handle-size` white circle with the `on-primary` edge and the `scale_rotate` icon, at the frame's bottom-right corner (nearest inside corner if that one is off the stage).
- **Snapping** (one SegmentTick per engagement and its guide): the layer's center to the canvas's vertical and horizontal center lines within `space-2`; rotation to 0°, 90°, 180°, 270° within 4°; size to 100% within 3%. For a photo, 100% is "fits the frame", the size people want to get back to. Edge snapping (a layer's side to the canvas edge) is not in P2-01 (Proposals, Later).
- **Value bubble** while sizing or turning: "120% · 15°" (P1-10), centered at the top of the stage.
- **One gesture = one undo step**, committed on release: "Position and size". During the gesture only the drawn layer moves, in the draw phase; the project changes on release. Undo and redo are ignored while a finger is down.
- **No toast for limits while dragging:** the layer stops at 10% / 500% and at the canvas edges.

### Locked layers
Locked means **"don't change this by accident"**: the typical use is locking the photo so a drag aimed at a caption never moves it.
- **A tap** selects it: the frame shows without a handle, a lock badge (a `badge-size` `scrim` chip with `lock` at `icon-small` in `text`, 7.5:1 or more over any picture) sits where the handle would, and the bar starts with **Unlock**. No toast: selecting isn't changing.
- **A one-finger drag that starts on it, a pinch or twist with it selected, and the handle's place**: nothing moves, and a toast shows once per touch: "This layer is locked. Unlock it to change it." The stage doesn't scroll or select anything else.
- **What still works on a locked layer:** select, Unlock, Hide and Show, Duplicate (the copy is unlocked, a new layer to play with), and the Layers panel's own lock and eye buttons.
- **What explains instead:** Position, Opacity, Delete, Move layer up and down (the panel's buttons and the TalkBack actions): the same toast. Locking is not a "read-only" mode for the whole project; it protects one layer.
- **Why Delete is blocked:** a locked layer says "I'm protected", and deleting it would contradict that. Undo exists, so the cost of one extra tap (Unlock, Delete) is small against a protected layer that disappears (Assumption; this is the common reading of lock in layered editors).
- Lock and Unlock are one undo step each.

### Ratio change: what happens to layers
One tap on a ratio is **one undo step** ("Ratio") that changes the canvas and, in the same step, anything the rules below move (P1-11's step, with P1-11's rule adapted to images).
1. **Every layer keeps its `centerX`, `centerY`, `scale` and rotation.** An image layer's size at scale 1 is recomputed from the new shape (Size at scale 1), so a photo that fitted still fits: 4:3 → 9:16 gives the same photo, whole, centered, with the background above and below, and 9:16 → 4:3 gives it back exactly. **"Keeps its size" for an image means its scale**, the same fraction of the fitted size.
2. **A layer that was fully inside the old frame must be fully inside the new one.** Its box is the picture's box turned by its rotation. If it now crosses an edge, it moves **straight in, just far enough to touch** that edge: images use a **margin of 0**, not P1-11's 2%, because a 2% gap beside a photo reads as a mistake (the 2% margin exists so words don't kiss the edge, which a picture doesn't need). Text and stickers (P2-03 and later) keep the 2% margin. If it is too big for the new frame (a turned layer, say), it first shrinks, keeping its center, until it fits, never below 10% for images, then moves in.
3. **A layer placed partly outside** (a zoomed-in photo, a layer hung off the edge on purpose) keeps its center and scale. It was outside on purpose, and its center is within the frame (center limits), so it's never lost.
4. If any layer moved or shrank, a toast explains: "Layers moved to stay inside the frame." Nothing else toasts: a photo that merely re-fitted didn't move. Undo restores everything in one step.
- A **locked layer** follows the same rule: a ratio change is about the canvas, not an edit of that layer, and leaving it outside the frame would break the Done when.
- **Round trips:** scale 1 photos return exactly. Moved-in layers may stay where they were pulled to; Undo is the exact way back.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Picker open | The system picker over the current screen | (system) |
| Nothing picked | Nothing changes; no project | none |
| Copying (over 300 ms) | P1-02's import sheet, photo wording, percent only, Cancel | `import_title_photo`, `import_percent`, `import_cancel` |
| Not enough space, can't read, no picker | P1-02's sheets, unchanged; "None went in" has photo wording | `import_storage_*`, `import_none_title_photo`, `import_none_body_photo`, `import_pick_again`, `import_no_picker_*` |
| Opening a draft | Frame in the background color at once; each layer fades in (120 ms) when decoded | none |
| Default, one layer, nothing selected | The photo on the frame; main bar with Layers; Undo and Redo disabled | `photo_stage_a11y`, `tool_layers` |
| Layer selected | Frame, handle, selected-layer bar | `tool_position`, `tool_layer_opacity`, `tool_delete`, `tool_duplicate`, `tool_lock`, `tool_hide_layer`, `tool_layers`, `layer_tools_close` |
| Layer selected, locked | Frame, lock badge (no handle); the bar starts with Unlock | `tool_unlock`, `explain_layer_locked` |
| Layer selected, hidden | Nothing on the canvas for it; the bar shows Show | `tool_show_layer` |
| Moving, sizing, turning | The layer follows; guides when snapped; the bubble; the frame edge | `text_transform_bubble` |
| Overlapping layers | A tap picks the top-most visible; the others via the panel | none |
| Layers panel, default | Top row; list front first; no Opacity row when nothing is selected | `layers_title`, `panel_done` |
| Layers panel, a layer selected | Opacity row; Move layer up and down; its row ringed | `layers_opacity`, `layers_move_up`, `layers_move_down` |
| Layers panel, locked selected | The Opacity row becomes the message and an Unlock button | `layers_locked_opacity`, `tool_unlock` |
| Layers panel, one layer | Move up and down explain | `explain_layer_one` |
| Layers panel, at the front or back | That move explains | `explain_layer_front`, `explain_layer_back` |
| Layers panel, lifting a row | Ring, follows the finger, others slide | none |
| Layers panel with no layers (tool tapped on an empty canvas) | The tool explains with a toast; the panel does not open | `explain_layers_empty` |
| Opacity panel | One slider | `layers_opacity`, `panel_done` |
| Position panel | P1-10's rows for layers | `position_*` |
| Canvas panel, preset or Custom | As P1-11 without Blur and Fit or Fill | `canvas_*`, `color_*` |
| Ratio changed, a layer moved | The new frame; the toast | `photo_layers_moved` |
| Background can't show | The hint line | `photo_background_hidden` |
| Empty canvas (no layers) | Over the stage, a `scrim` card with a title and one line (below); the frame shows the background. The Layers tool explains. Hidden while the Canvas panel is open | `photo_empty_title`, `photo_empty_body` |
| A layer's file is missing or unreadable | The layer isn't drawn, in preview or export (the same, so what you see is what you export); its row says so; a toast once per open; selecting it works | `photo_missing_media`, `layer_meta_missing` |
| Save failing, leaving while failing | P1-04's banner and sheet, unchanged | `editor_save_*`, `editor_unsaved_*` |
| Draft from a newer Memix, damaged draft | P1-14's states: a toast, nothing opens | `drafts_needs_update`, `drafts_cant_open` |
| Long content (5 languages, 200% font) | See Accessibility | none |
| Offline, permission | Don't apply: everything is on the phone; the picker needs no permission | none |
| First run | Same as every run: no coach marks | none |

**Empty canvas card.** Centered on the **stage** (not the frame: P1-04's lesson, because a 9:16 frame's width cut the Hindi body at 200%), `space-4` in from the stage's sides, `scrim` fill, `radius-lg`, `space-4` padding, a title in `title` `text` and a body in `body` `text` (7.5:1 or more over any canvas color, the same pairing as the badges). It scrolls if still taller than the stage. It sits over the frame, so the canvas color stays visible around it. P2-03 changes its body when a blank start exists ("Add text to start", with the tool named); P2-13 adds Add photo.

## Interactions
- **Press feedback:** the token pressed states, 120 ms. Nothing moves.
- **Haptics:** SegmentTick for each snap engagement (gestures, the opacity slider's 100% snap); LongPress when a row lifts in the Layers panel. Nothing else.
- **Playback:** none; there is nothing to play. The stage's taps are selection.
- **Duplicate:** the copy goes directly above the original in the stack, offset by 3% of the canvas's shorter side right and down (never past the center limits), so the user can see something happened (P1-06's duplicate lands the same place, but on a timeline the new item is visible; on a canvas a same-place copy looks like nothing happened). It is selected, unlocked and keeps opacity and hidden state. It uses the **same media file** as the original (no new copy; request 4). One step "Duplicate".
- **Delete:** the layer goes; the selection clears and the main bar returns. No confirmation: Undo brings it back (P1-06). Deleting the last layer shows the empty-canvas card.
- **Undo and redo:** P1-04's rules. After undo or redo the selection stays if the layer still exists; the Layers panel, the Opacity row and any panel show the restored values; a panel editing a layer that no longer exists closes. A toast names the step ("Undo: Delete").
- **Keyboard (basic, P1):** Tab reaches the top bar, the tool bar and every panel control; Enter activates; Escape is system back. The canvas layers are TalkBack nodes (below); shortcuts such as nudging with arrow keys are Later (L-01).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Editor opens | 320 ms push | 120 ms fade |
| A layer appears once decoded | 120 ms fade in | same |
| Selection frame and handle | 120 ms fade | same |
| Main ↔ selected-layer bar | 120 ms crossfade | same |
| Panel opens or closes | 200 ms slide | 120 ms fade |
| The canvas refits above a panel | 200 ms scale about the frame's center | instant |
| Frame edge, guides, bubble | 120 ms fade | same |
| Rows slide to open a slot; Move up or down | 200 ms | instant (Android reads reduce motion as animator scale 0; DESIGN_SYSTEM → Motion) |
| Delete, hide, show, lock, ratio change | At once | same |
| Toasts | 120 ms fade | same |

## Copy
English source; the engineer machine-drafts id, es, pt and hi (until P5). Budgets are characters. Reused keys keep their strings: `editor_close`, `editor_undo`, `editor_redo`, `editor_undo_toast`, `editor_redo_toast`, `editor_save_*`, `editor_unsaved_*`, `panel_done`, `canvas_*`, `color_*`, `position_*`, `text_transform_bubble`, `a11y_text_transformed`, `a11y_text_position` (generic in meaning: the caption's center becomes the layer's), `explain_text_smallest`, `explain_text_biggest`, `a11y_edit_on_item`, `edit_delete`, `edit_duplicate`, `edit_reorder`, `edit_canvas_*`, `tool_position`, `tool_delete`, `tool_duplicate`, `tool_replace`, `import_*`. **Shared keys the video editor uses move to `:core:ui` in the same commit** (the lift in request 8); a duplicate translation of the same words in two modules is a drift waiting to happen.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `create_blank` | Start with a blank canvas | 34 | Create sheet row, from P2-03. Sits beside `create_template` ("Start from a template"); keep the same sentence pattern |
| `import_title_photo` | Adding your photo | 28 | Import sheet title for one photo (P1-02 `import_title` says "media") |
| `import_none_title_photo` | Couldn't add your photo | 32 | |
| `import_none_body_photo` | That photo couldn't be added. Try another one. | 80 | |
| `tool_layers` | Layers | 20 | Tool and panel name. A layer is one thing on the canvas (a photo, a piece of text) stacked over the others. Use your language's word from photo editors |
| `tool_layer_opacity` | Opacity | 20 | How see-through a layer is |
| `tool_lock` | Lock | 20 | Stops the layer being moved or changed by accident |
| `tool_unlock` | Unlock | 20 | Same key as the L-02 draft |
| `tool_hide_layer` | Hide | 20 | Hides the layer from the canvas and the export, without deleting it |
| `tool_show_layer` | Show | 20 | Shows a hidden layer. The pair Hide/Show must read as opposites |
| `layer_tools_close` | Close layer tools | 24 | Screen reader only |
| `layers_title` | Layers | 20 | Panel title |
| `layers_move_up` | Move layer up | 24 | Spoken label of the up button: toward the front |
| `layers_move_down` | Move layer down | 24 | Toward the back |
| `layers_opacity` | Opacity | 20 | Slider label and Opacity panel title |
| `layers_locked_opacity` | Locked. Unlock this layer to change its opacity. | 60 | Replaces the slider on a locked layer; its button is `tool_unlock` |
| `layer_name_photo` | Photo | 16 | Row name of an image layer |
| `layer_meta_locked` | Locked | 16 | Row meta |
| `layer_meta_hidden` | Hidden | 16 | |
| `layer_meta_missing` | File missing | 20 | |
| `layer_meta_opacity` | %1$d% opacity | 18 | Write a single %: Compose resources don't double it. Put a no-break space between the number and the word (` ` in the XML, which Compose resources decode), so a wrapped meta line never leaves "40%" alone at the end of a line |
| `explain_canvas_widest` | This is as wide as a canvas can be. | 40 | Shared with the video editor; replaces "…as a video can be" (it named the wrong editor). Same pattern as `explain_canvas_tallest` |
| `explain_canvas_tallest` | This is as tall as a canvas can be. | 40 | Shared with the video editor. "Canvas" is the same word as `canvas_title` in your language |
| `layer_meta_separator` | · | 4 | Joins the meta parts with spaces around it. A resource so a language can change it |
| `layer_lock_a11y` | Lock layer | 24 | Spoken label of the row's lock button |
| `layer_unlock_a11y` | Unlock layer | 24 | |
| `layer_hide_a11y` | Hide layer | 24 | |
| `layer_show_a11y` | Show layer | 24 | |
| `photo_stage_a11y` | Meme canvas, %1$s | 30 | Name of the canvas on the stage; %1$s = the ratio ("4:3"). Differs from the pill's "Canvas, 4:3" so the two aren't read alike |
| `a11y_layer_row` | %1$s, layer %2$d of %3$d | 50 | Name, position from the front (1 = front), count. Also the name of a layer on the canvas |
| `a11y_layer_locked` | Locked. | 20 | Spoken after the row name when true; sentences join with a space |
| `a11y_layer_hidden` | Hidden. | 20 | |
| `a11y_layer_missing` | File missing. | 24 | |
| `a11y_layer_opacity` | %1$d percent opacity. | 30 | Also announced after the slider moves |
| `a11y_layer_order` | Layer %1$d of %2$d | 24 | Announced after a move: the new place, from the front |
| `explain_layer_locked` | This layer is locked. Unlock it to change it. | 60 | |
| `explain_layer_one` | Reordering needs at least two layers. | 50 | |
| `explain_layer_front` | This layer is already in front. | 40 | |
| `explain_layer_back` | This layer is already at the back. | 40 | |
| `explain_layer_edge` | This layer can't go further this way. | 50 | Replaces `explain_text_edge` for layers |
| `explain_layers_empty` | Nothing here yet. Layers you add show up in this list. | 60 | |
| `photo_missing_media` | A photo's file is missing, so it isn't drawn. Delete that layer to fix it. | 90 | Once per open. P2-13 may add Replace (Proposals) |
| `explain_layer_missing` | This photo's file is missing. Delete the layer to fix it. | 70 | When Duplicate is tapped on a missing layer |
| `photo_layers_moved` | Layers moved to stay inside the frame. | 50 | After a ratio change moved or shrank a layer |
| `photo_background_hidden` | Your photo covers the whole frame, so the background doesn't show. | 70 | |
| `photo_empty_title` | This canvas is empty | 32 | |
| `photo_empty_body` | Tap Undo to bring your layer back, or close this and start a new photo meme. | 90 | Until P2-03 (a blank start exists then). "Undo" must match `editor_undo` |
| `edit_transform_layer` | Position and size | 24 | Edit name after "Undo: "; includes turning |
| `edit_layer_opacity` | Opacity | 24 | |
| `edit_lock` | Lock | 24 | |
| `edit_unlock` | Unlock | 24 | |
| `edit_layer_hide` | Hide | 24 | |
| `edit_layer_show` | Show | 24 | |

Translator notes:
- **"Layer"** is the PRD's word for "a thing stacked on the canvas". Photo editors in your language have a settled word; use it for `tool_layers`, `layers_*` and the spoken strings, and the same word everywhere.
- **Lock and Hide** are about protecting and showing a layer, not about the whole project or about hiding the app.
- **Spoken rows** are sentences read after a name; keep them short and complete.
- **Length:** the tool labels are two lines of about 10 characters (P1-06). Spanish and Portuguese "Desbloquear" is one word of 11 characters: the tool widens rather than cutting it, which the ToolBar allows. Hindi needs the extra line height of the system Devanagari face.
- **"Photo"** in `layer_name_photo` is the same word as the Photo meme row.

## Accessibility
- **TalkBack and focus order:** Close → ratio pill → Undo → Redo → (P2-08's button) → save banner → the canvas → the empty-canvas card, when shown → tool bar or panel. **The canvas** is one container, "Meme canvas, 4:3", whose children are the **visible layers from front to back** (the same order as the Layers panel), each "Photo, layer 1 of 3" (`a11y_layer_row`), with "selected" as its state and the extra words (locked, opacity) after the name, never in the state description (P1-11 review M2). Double-tap selects.
- **Actions on a layer node and on every row** (TalkBack's action menu, Switch Access): Position, Opacity, Lock or Unlock, Hide or Show, Duplicate, Delete, **Move up**, **Move down** (`layers_move_up`, `layers_move_down`). These are the single-pointer routes that replace dragging (WCAG 2.5.7) and pinching and twisting (2.5.1), together with the Position panel. A locked layer's blocked actions explain (the same toast, announced). After a move, "Layer 2 of 3" is announced politely.
- **The handle** is hidden from accessibility: it is drag-only and the Position panel is the full alternative (P1-10).
- **Layers panel:** opening moves focus to the title (the pane title); closing returns it to the Layers tool. The list is lazy: off-screen rows aren't in the tree (P1-05's lesson), and Move up and down plus the announcements reach every place. **Each row is one node** with the row's actions; its lock and eye buttons stay focusable for keyboards and Switch Access but are not extra TalkBack stops, so ten layers cost ten swipes, not thirty (the same pattern as P1-14's rows; check TalkBack's focus on the device).
- **What a panel covers is gone** for TalkBack and keyboard focus (`coveredByModal`, ToolPanel rule): the tool bar under it.
- **Announcements, polite, never taking focus (SC 4.1.3):** `a11y_edit_on_item` after Delete, Duplicate, Lock, Hide; `a11y_layer_opacity` after the slider; `a11y_text_transformed` and `a11y_text_position` after the Position panel; toasts. Gestures announce nothing while moving.
- **Touch targets:** every tool, button, row, tile and swatch is 48 dp or more; a layer's hit area grows to 48 dp; the handle's target is 48 dp (cut by the stage edge when it sits at a corner, where the inner 24 dp still works).
- **Contrast:** row name `text` on `surface-raised` 12.4:1; meta `text-secondary` 5.7:1; a hidden row's name `text-secondary` 5.7:1; the free lock icon `text-muted` 4.6:1; the ring `selection` on `surface` 17.2:1; the lock badge `text` on `scrim` 7.5:1 or more; the empty card `text` on `scrim` over a white canvas 7.5:1; the frame and handle as P1-10 (4.3:1 or more over any content).
- **200% font:** the top bar keeps its height (icons only; only the pill grows). Tool labels wrap and the bar grows. Row text wraps, rows grow and the thumbnail stays 40; the Layers panel's top row wraps its two buttons under the title; the panel still caps at 40% of the window, the Opacity row moves into the list (Layers panel → Where the Opacity row lives), and the list scrolls with room for two rows at least. The empty card scrolls. Nothing is cut. Hindi at 200% is a screenshot (QA compares 7).
- **Reduce motion:** see Motion.

## Analytics
`tool_use` with `editor: "photo"`, once per committed undo step (never while dragging or holding; never when a tool only explains; never for opening a panel, selecting, or tapping a selected option).

| Event | When | Parameters |
| --- | --- | --- |
| `project_create` | The photo lands (gallery), or a blank project's first committed step that leaves a layer (blank) | `editor: "photo"`, `source: "gallery"` or `"blank"` |
| `tool_use` | A gesture releases (move, scale, turn) | `tool: "transform_layer"` |
| `tool_use` | One Position-panel step | `tool: "position_layer"` (PM, 27 Oct; mirrors video's `position_text`) |
| `tool_use` | The opacity slider is released | `tool: "layer_opacity"` |
| `tool_use` | Lock, Unlock | `tool: "lock_layer"`, `"unlock_layer"` |
| `tool_use` | Hide, Show | `tool: "hide_layer"`, `"show_layer"` |
| `tool_use` | A drop in the Layers panel, or one Move layer up or down | `tool: "reorder_layer"` |
| `tool_use` | Delete, Duplicate | `tool: "delete"`, `"duplicate"` |
| `tool_use` | Undo, Redo (when they change the project) | `tool: "undo"`, `"redo"` |
| `tool_use` | A ratio or background step in the Canvas panel | `tool: "canvas_ratio"` (`value`: `9:16`, `1:1`, `4:5`, `3:4`, `16:9`, `custom`), `"canvas_background"` (`value`: `color`; `image` with P2-12) |

- `move` and `reorder` already mean time order in the video editor (`EditorTool`), so they aren't reused; the layer ids are new `EditorTool` values.
- Starting a photo meme is not a `tool_use`: the canvas chosen from the photo's shape isn't a user's ratio choice, so no `canvas_ratio` fires then.
- `value` stays for the canvas tools only (owner, 21 Oct). Analytics stay off until consent (P5-01).

## QA compares
Use the debug drafts `photo-layers-10`, `photo-empty`, `photo-missing-media` (request 1) and a gallery start with a 4:3 photo.
1. **After a gallery pick (4:3 photo)** at 360 × 800: the canvas in "4:3" shape with the photo covering it; the pill "4:3"; Undo and Redo disabled; the bar with Layers only. Then the same at 360 × 640.
2. **Layer selected** on `photo-layers-10`: two-tone frame and handle, the selected-layer bar with Position, Opacity, Delete visible at 360 dp. A locked layer selected: the lock badge in place of the handle, Unlock first.
3. **Layers panel** open on `photo-layers-10` at 360 × 800 and 360 × 640: front row first; a row each of normal, selected, locked, hidden, faded (40%), several states at once; the canvas refitted above the panel. A row lifted mid-drag.
4. **Locked layer touched:** the toast after a drag attempt; the layer unmoved.
5. **Canvas panel (photo):** Ratio tiles with Custom selected from a photo start, then Background swatches, the hint line when the photo covers the frame. No Blur, no Fit and Fill.
6. **Empty canvas** (`photo-empty`, and the last layer deleted) on a white canvas and a black one; **a missing photo** (`photo-missing-media`): the toast, the row, nothing drawn.
7. **Hindi at 200% font**, 360 × 640: the top bar, the Layers panel with its top row wrapped, the Position panel scrolled, the empty card.
8. **Mid-pinch:** the bubble "…% · …°" and the frame edge on a white canvas.

**Measured: the Done when** (the PM's method in `docs/pm/acceptance-log.md`; the designer adds):
- **Smooth drag:** the 10-layer scene, `gfxinfo` janky frames under 5% in each of three 3-second drags, and a 60 fps recording of pinch and turn with no stutter, on the owner's phone, release-type build.
- **No leaks:** LeakCanary, 5 open-and-close cycles on the scene (the PM's cycle list), plus the Layers panel opened, a row dragged and the panel closed in each cycle.
- **Preview = render:** the debug render hook (request 5) writes the open scene to a PNG. On `photo-layers-10`, screenshot the canvas (cropped to the frame) and compare with the render scaled to the same width: every layer's position within 1% of the width, size within 2%, rotation within 1°, the same opacity (sampled inside a faded layer over the background), hidden layers absent, the same stacking order. Frames go in `docs/qa/references/P2-01-photo-scene/` (CLAUDE.md rule 5), for P2-08 and iOS parity.
- **Ratio sequence:** on `photo-layers-10`, 4:3 → 9:16 → 16:9 → 1:1 → custom 9:21 → back to 4:3: every layer that started fully inside is fully inside after each step (edges to the pixel, margin 0 for images); a layer placed partly outside keeps its center and scale; the scale-1 photo returns exactly after the round trip; one Undo restores the previous ratio and every layer exactly.
- **Photo-shape canvas:** gallery starts with a 4:3, a 3:2, a 1:1, a 9:16 and a 6:13 (screenshot) photo give "4:3" (Custom), "3:2" (Custom), "1:1", "9:16" and "6:13" (Custom), with no sliver of background on any edge; a 3:1 panorama gives 31:13 with the photo whole.
- **Reopen:** kill mid-edit, reopen from Drafts (the photo badge and the picked photo as the cover): the scene is the same, including locked, hidden and faded states.
- **Empty draft:** delete the only layer, then close: the draft is gone from Drafts and its folder is removed at the next start (no message). Undo before closing brings the layer back.
- **Locked layer:** lock the photo, drag a caption-sized layer that overlaps it: the top layer moves, the locked one never does; drag the locked one: it stays and the toast shows once per touch; every blocked tool explains; Unlock restores all of them.
- **Responsiveness:** a committed edit shows within 100 ms; opening the editor from Drafts shows the first frame in under 1 s.

## Model
Changes needed from the design (the engineer owns the exact code; the PM's must-know 4 already carries the version bump):

| Need | Model |
| --- | --- |
| Lock, hide, fade | `Layer` gains `locked: Boolean = false`, `hidden: Boolean = false`, `opacity: Float = 1f` (0.1 to 1). Defaults mean an old layer is unchanged. Bump `CURRENT_SCHEMA_VERSION` 2 → 3 with a version-only migration |
| Z-order | List order, bottom to top (already) |
| Names | Derived from the layer's type, never saved. No `name` field |
| Starting canvas | `Project.canvas` set at creation from the photo's shape, or the blank defaults |
| Cover | Not saved: derived (Drafts cover, below) |

**Constants** (not in `MemixTheme`; one commonMain place next to `CaptionCatalog` and `CanvasCatalog`, say `PhotoCatalog`, with a comment pointing to this spec and `design/tokens.json` → `photo`): the blank defaults, the photo-shape rule, the image scale limits, the opacity range, the duplicate offset, the image ratio-change margin, the decode cap. They are part of every saved draft's look.

**Drafts cover** (P1-14 request 5 for photo): the **lowest image layer's copy**, a square JPEG center-cropped from its picture (192 px, the existing thumbnail reader), ignoring its transform, opacity and hidden state. Written at the first save and when the editor closes or backgrounds if that source changed. A project with no image layer shows P1-14's placeholder (the type icon, no badge). No scene render: it would run at every close, and the shared scene function makes a rendered cover cheap to add later ("a cover that shows the meme as rendered" is P1-14's Later idea; P2-08 will have the machinery).

## Requests to the principal mobile engineer
I don't edit code, so these are requests. They sit beside the PM's must-knows, which already list the hooks, the edit session, the migration, the media rules and the lift into `:core:ui`.
1. **Debug scenes**, opened with `--es memix.openEditor`:
   - `photo-layers-10`: **ten visible image layers** plus one hidden (11 in all; PM, 27 Oct: a hidden layer proves nothing about drag speed): a 12 MP-class photo at the back (the canvas from its shape), nine more large images, three at 60%, 40% and 10% opacity, two turned (one 30°, one 90°), one partly off the frame, **one locked**, and an 11th, **hidden**, all overlapping, so every row state has an example.
   - `photo-empty`: no layers, white 1:1 (the blank defaults).
   - `photo-missing-media`: two layers, one whose file is gone.
   - Also an image set: a 4:3, a 3:2, a 1:1, a 9:16, a 6:13 and a 3:1 for the photo-shape check.
2. **Start flow:** a photo-meme import (a sibling of `videoMemeImport`) for Home and the Create sheet; `PickVisualMedia.ImageOnly`, one item; the import sheet with the percent only for one item; `SaveGalleryProjectUseCase` for photo. The photo-shape function (Start) in `:core:domain`, pure, with a JVM check against the QA list. The blank use case and the empty-canvas path are built now; the Create row stays behind one constant until P2-03.
3. **Layer behavior as pure edits** in `:core:domain` (each one `Project` change, one undo step): set transform, set opacity, set locked, set hidden, move layer up or down, reorder by drop, delete, duplicate (offset, same media file), and the ratio-change layer rule (Ratio change) alongside `setCanvasRatio`. The locked layer's refusals live here too, so gestures, tools and TalkBack actions all reach one rule.
4. **Media:** a duplicate shares the original's media file. `mediaCopyPaths()` and the cleaner must treat two layers with one file as one file in use, and deleting one layer must never delete a file another layer or an undo state still uses.
5. **One scene-drawing function** (PM must-know 6): bottom to top, hidden skipped, opacity applied, transform applied, clipped at the canvas; used by the editor at screen size, by P2-08 at export size and by the render hook. **Image size at scale 1** is the fitted size, with the 1% cover rule, from the canvas shape at draw time (Layers on the canvas → Size at scale 1). It is the same function everywhere, so preview and export can't disagree.
6. **Performance** (Done when, and the PM's must-know 7):
   - Decode each image at the size it is drawn at the canvas's **no-panel size**: never above the source's own size and never above 4096 px on the long side. Re-decode only on gesture release, and only if the drawn size changed by 25% or more. Never during a drag or an opacity slide.
   - One decoded bitmap per source file; layers keyed by id; a gesture moves the layer in the draw phase (a state read inside the draw lambda), so the scene is not recomposed per frame.
   - Opening or closing a panel scales the existing bitmaps; it never re-decodes.
   - Layer row thumbnails are **not** from the thumbnail reader (it returns square center crops, and the row wants the picture fitted inside): they come from the scene decoder at about 128 px on the long side (never the original, never above 192), one per file, cached while the editor is open and released at close; the list is lazy (accepted in review P2-01, 28 Oct).
   - Release bitmaps when the editor closes.
   - Report the phone model, Android version and median and worst janky % (the PM's method), plus `dumpsys meminfo` Graphics and Native Heap on `photo-layers-10`.
7. **Stage:** the canvas refit above a panel with the 200 ms scale; the frame inset `space-4`; the handle and guides clipped at the stage (P1-10 review M1).
8. **Lift, don't copy** (PM must-know 8): caption gestures, selection frame, snap guides, the Position panel, `RatioShape`, the stepper rows and the Canvas panel pieces move into `:core:ui`; the video editor uses them in the same commit with no behavior change. The Position panel's size ladder gains 10% and 15% for images.
9. **Tokens and components:** `MemixSize.layerThumbSize` (40 dp, `layer-thumb-size`); LayerRow in `:core:designsystem` and the debug catalog in every state; ToolPanel's "sized by content, up to 40% of the window" option; the ToolBar's trailing behavior is unchanged.
10. **Icons**, drawn to the grammar (DESIGN_SYSTEM → Iconography lists them): `layers`, `lock`, `unlock`, `eye`, `eye_off`, `opacity`. The Move buttons reuse `arrow` turned up and down.
11. **Analytics:** new `EditorTool` values `transform_layer`, `position_layer`, `layer_opacity`, `lock_layer`, `unlock_layer`, `hide_layer`, `show_layer`, `reorder_layer`; the photo editor passes `editor: photo`.
12. **Routing:** `PhotoEditorRoute(projectId)`; `onOpenDraft` by project type (PM must-know 9). Photo is portrait-locked on phones like the video editor (P1-04).

## Changes to other specs (made with this spec)
- `P0-05-navigation-and-browse-screens.md` → Create sheet: gains the "Start with a blank canvas" row (from P2-03) and says the Photo meme row opens the picker.
- `P1-02-gallery-picker.md` → "Photo meme stays on its placeholder": now points here.
- `P1-14-drafts.md` → the photo cover and the photo open path point here.
- `design/system/components/ToolBar` and `ToolPanel`, new `LayerRow`, Swatch's Image variant (P2-12): see the CHANGELOG row.

## Proposals and questions for the PM
1. **`tool_use` ids** (your D5): confirm `transform_layer` (gesture release or one Position-panel step), `layer_opacity`, `lock_layer`, `unlock_layer`, `hide_layer`, `show_layer`, `reorder_layer`, plus `delete`, `duplicate`, `undo`, `redo`. Note that the Position panel's steps log `transform_layer` too, since the PM suggested one id; say if you want `position_layer` separate, as video has `position_text`.
2. **Reading of "keeps every layer inside" for photo** (P1-11's reading, adapted): a layer fully inside stays fully inside; a layer placed partly outside keeps its center and **scale** (not its pixel size, because an image's size at scale 1 follows the canvas's shape); images use a margin of 0, text and stickers 2%. Please confirm so QA tests the same thing.
3. **The Position panel is in P2-01.** It isn't named in the ticket, but WCAG 2.5.1 and 2.5.7 need a single-pointer route for pinch, twist and drag, and the video editor already built it for text; the lift makes it near-free. Please confirm it isn't extra scope.
4. **The Create row for Blank ships with P2-03**, not P2-01 (Start → Blank canvas), and P2-01 builds the code path behind the `photo-empty` hook. A blank start before any layer can be added would be a dead end.
5. **Scale 10% to 500% and opacity 10% to 100% for images** are design constants; confirm they're fine as fixed rules (changing one after P2 ships needs a schema bump).
6. **Photo draft cover** = the lowest image layer's copy (your D6), no scene render. A rendered cover is an easy Later item once P2-08's machinery exists; no ticket.
7. **Size watch.** The additions to the L-sized ticket from this spec are LayerRow, six icons, two ToolPanel variants and the lock rules. If P2-01 runs past two sessions, the natural split is **01a** (start flow, model and migration, scene drawing, canvas panel, gestures, selection, Position and Opacity panels, Delete, Duplicate, Lock, open and reopen, cover) and **01b** (the Layers panel: rows, drag reorder, Hide and Show). Hide waits for the panel, because a hidden layer can't be tapped on the canvas, so it needs the panel to be shown again (undo and the selected bar's Show still bring it back right after hiding).
8. **P2-13 and Replace.** A layer whose file is missing can only be deleted in P2-01. A **Replace photo** tool on image layers (same pick path as P2-12 and P2-13, keeps the transform) would fix it and let a user swap the base photo. It is a small addition to P2-13 (`P2-13-image-layers.md` → Proposals); the spec doesn't build it.

**Later, no ticket:** edge snapping to the canvas sides; tap-again to cycle through overlapping layers; a rendered Drafts cover; moving the Undo and Redo pair into thumb reach if the P2 test shows trouble.

No owner decision is needed from this spec. Three defaults the owner can change in one constant each: the blank canvas (1:1, white), the 10% scale floor, and the images' margin of 0.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| `PickVisualMedia` picks a single item; `ImageOnly` restricts it to images; the result is null when nothing is chosen; it falls back where the system picker is missing | Guidance (API reference) | [PickVisualMedia](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickVisualMedia), [Android photo picker](https://developer.android.com/training/data-storage/shared/photopicker) |
| Layers panels list layers front first, with an eye to hide, a lock, an opacity control and drag to reorder | Guidance (vendor help) | [Photoshop on the web: layers panel](https://helpx.adobe.com/photoshop/web/edit-images/manage-layers/layers-panel-overview.html), [Flow: layers](https://bonobolabs.com/support/flow/layers/), [Tiled: layers panel](https://support.tiled.co/layers-panel-2.0) |
| "Lock means protected from edits" and "a photo meme keeps the photo's shape" | Assumption (product knowledge of layered editors and meme makers; not confirmed from vendor pages) | none |
| Dragging needs a single-pointer alternative; multi-point gestures need a single-pointer alternative | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements), [SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures) |
| A visible label is part of the accessible name | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Focus must not be entirely hidden by other content | Guidance | [WCAG 2.2 SC 2.4.11](https://www.w3.org/WAI/WCAG22/Understanding/focus-not-obscured-minimum.html) |
| Non-text indicators need 3:1 against adjacent colors | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Undo beats confirmation for reversible actions | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
