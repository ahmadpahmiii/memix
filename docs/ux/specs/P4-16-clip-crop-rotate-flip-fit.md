# Clip crop, rotate, flip and per-clip Fit or Fill · P4-16

Second Phase 4 spec, promised on 21 Oct (owner, proposal A) and missing until now. It reuses, and does not repeat:
- `P4-01b-overlay-layers.md`: the on-frame gestures, the two-tone selection frame and handle, snapping, the Position panel with the photo layers' size ladder, the overlay lanes and bars, the stage hit order. **Read it first.**
- `P2-04-image-tools.md` → The Crop screen: the screen itself (aspect tiles, rotate, flip, Edges, the frame rules, Done, Close, Reset). It is a `:core:ui` component that takes a picture, an edit and the canvas's shape and returns the edit; P4-16 gives it a video frame.
- `P1-11-canvas.md`: the Clips row (Fit and Fill for every main clip), the background rules, the ratio change.
- `P1-06-clip-edits-and-audio.md`: the clip tool bars, Replace, Split, Duplicate, Reorder.
- `P1-10-text.md` and `P2-01-photo-editor-shell.md`: Position panel, gestures, size ladder.

## Job
"My clip is the wrong shape for this meme": a landscape clip in a 9:16 meme (fill the screen, or keep the whole clip), a clip that needs a mirror for a gag, a sideways clip, a clip with a black edge to cut off. PRD → Video editor → Basic edits: crop, rotate, flip; Canvas: fit or fill for all clips **or per clip (P4-16)**. **One blue action: Done on the Crop screen** (the mode's one confirming action, as in P2-04). Everything else is a tool.

**Done when** (TICKETS): a cropped, rotated and flipped clip looks the same in preview and export and matches its reference render; Fit or Fill set on one clip changes only that clip; each change is one undo step. How QA checks each is under Measured; "each change is one undo step" is spelled out in Interactions → Undo steps (a visit to the Crop screen is **one** step, as in P2-04: Done applies the crop, the turns and the flips together).

**Fast path, with tap counts:**
- **Fill the screen with one landscape clip:** the clip (1), Fill (2). Nothing else changes.
- **Fix a sideways clip:** the clip (1), Crop (2), Rotate right (3), Done (4).
- **Mirror it:** the clip (1), Crop (2), Flip horizontally (3), Done (4).
- **Cut off a black edge:** the clip (1), Crop (2), drag a corner (3), Done (4); or the Edges steppers, no dragging.
- **Choose which part of a landscape clip shows in 9:16:** the clip (1), Crop (2), the **Canvas** tile (3), drag the frame sideways (4), Done (5); then Fill (6).
- **Nudge it a little:** with the clip selected, drag or pinch on the preview (1); or Position (1), Move arrows.

## Decisions (D1 to D8)
- **D1. Two tools, not four.** **Crop** opens the Crop screen, which already holds crop, rotate and flip (P2-04); **Fill** and **Fit** are one toggle tool. A separate Rotate tool and Flip tool would add two places to a bar that already scrolls, to save two taps on jobs that are rare in memes (a phone clip's own rotation is read from the file by the engine, so "sideways" is mostly a gallery quirk). The Crop screen shows the result live, which a button on the bar can't.
- **D2. Crop works on main clips and on overlays; Fit or Fill only on main clips.** An overlay's size is its own transform (P4-01b), so Fit and Fill would only duplicate a pinch.
- **D3. Geometry is the photo's, minus Straighten.** The crop, the quarter turns and the mirrors have the meaning P2-04 gave them (`ImageEdit`), in the same order. **No Straighten** for clips (Question 2): it is a free rotation by degrees that the PRD doesn't list for video, it interacts with Fit and Fill (a straightened clip must be scaled up to cover its frame), and a tilted overlay is already one twist away.
- **D4. Main clips can be moved, sized and turned on the preview** (Question 1): the same gestures and the same Position panel as an overlay, applied to the selected main clip, on top of its Fit or Fill. This is the ticket's "reuses P4-01's on-frame gestures", and it is what makes "choose which part of the clip shows" and "leave room for a caption" a drag instead of a crop. A main clip has no opacity and no stacking order.
- **D5. Tapping Fit or Fill resets the clip's framing** (its move, size and turn from D4) to the standard place, in the same step. Otherwise "150% of Fit" silently becomes "150% of Fill" and the clip jumps. The Canvas panel's Fit and Fill tiles (P1-11, every clip at once) do the same for every clip. Undo restores all of it (Question 3).
- **D6. Replace clears the crop, turn and flips, and keeps Fit or Fill and the framing.** They described the old picture; a new picture of another shape with the old crop would show a wrong part. The rest of Replace's "keeps the slot" promise (P1-16: id, start, length, volume) stands.
- **D7. The Crop screen shows one still frame:** the clip's frame at the playhead if the playhead is inside the clip, otherwise its first frame. The crop applies to the whole clip for its whole length (a moving crop is keyframes, P4-02, not here).
- **D8. Thumbnails show the geometry:** the timeline strip, the Reorder tiles and the drafts cover use the cropped, turned and mirrored picture (still a centered square), so a rotated clip looks rotated in every place it is drawn. Fit, Fill and framing are not drawn into thumbnails.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Crop screen (a mode over the editor) | The Crop tool of a selected main video, main photo or overlay clip | Done (applies, one step "Crop"); Close, system back or Escape (discards, as P2-04). The clip stays selected, playback stays paused. |
| Position panel (ToolPanel) | The Position tool of a selected main clip | Done, system back, Escape. The selection stays. |

- **Playback pauses** when the Crop screen opens (it shows a still). On return the playhead is where it was.
- **Kept:** the clip's crop, turns, mirrors, Fit or Fill and framing, in the project (autosave, undo). Not kept: the Crop screen's state when closed with Close.
- **Locked tracks** are L-02 (Later); nothing here checks them.
- **A clip whose file is missing:** Crop, Fill or Fit and Position explain with the existing missing-file toast (`editor_missing_media`; for an overlay `editor_missing_overlay`); they never open a screen with no picture.

## Layout
Board: none. The P2-04 Crop screen is the picture of the screen; the tool bars below are new.

### Tool bars (the P1-06 clip bar, new tools after Delete)
| Selected | Tools, in order |
| --- | --- |
| Main video clip | Split · Volume · Delete · **Crop · Fill or Fit · Position** · Duplicate · Detach audio · Reorder · Replace |
| Main photo clip | Split · Delete · **Crop · Fill or Fit · Position** · Duplicate · Reorder · Replace |
| Main clip with a missing file | Replace first, then the usual order without Crop, Fill or Fit and Position (they act on nothing) |
| Video overlay | Position · Opacity · Delete · Split · Volume · **Crop** · Layer order · Move here · Duplicate · Detach audio |
| Photo overlay | Position · Opacity · Delete · Split · **Crop** · Layer order · Move here · Duplicate |

- **Why after Delete:** Split, Volume and Delete are the cut-and-level tools used on every clip and keep their places (Delete is third and stays in view at 360 dp, ToolBar rule). The three framing tools are used a few times per meme; they sit right behind, so one swipe of the bar finds them, and the rare tools (Duplicate, Detach audio, Reorder, Replace) move right. Replace stays last. The bar scrolls anyway (up to ten tools).
- **Crop:** icon `crop` (P2-04, exists), label "Crop" (`tool_crop`, shared with the photo editor). Always acts.
- **Fill or Fit:** one tool whose **label and icon name what the tap will do**: on a clip that is Fit it reads **Fill** (icon `fill`); on a clip that is Fill it reads **Fit** (icon `fit`). Unlike Lock and Mute (P2-01, P1-09), whose icon shows a state the picture doesn't, the picture here already shows the current state, so the tool says the verb, as every other tool does. Name = visible label; the click label says the effect ("Cover the whole frame with this clip", "Show the whole clip inside the frame"). Tapping one clip changes only that clip.
  - **A clip already the frame's shape** (within 1%, after its crop and turn): the tool explains instead of changing nothing: "This clip is the same shape as the frame, so Fit and Fill look alike." The stored value does not change.
- **Position:** icon `position` (exists), the P1-10 panel (below).
- **Look:** as every clip tool: `text-secondary`, two lines of room per label; none is `danger`.

### The Crop screen for a clip (P2-04's screen, differences only)
- **The picture** is the clip's frame (D7), decoded at the crop stage's size (long side at most 2048 px, never above the source). Until it is ready the picture area is a `surface-raised` rectangle, static: no spinner (MOTION 2). If the frame can't be read, the screen does not open and a toast says so (`explain_crop_frame_failed`).
- **No Straighten slider.** The bottom area is the aspect row, the tool row (Rotate left, Rotate right, Flip horizontally, Flip vertically, Edges) and nothing under it.
- **Budget** (measured from the P2-04 build's pieces): the bottom area is the aspect tiles (stacked, about 60 dp) + `space-3` + the tool row (48) + `space-4` padding top and bottom (32) = **about 152 dp**, against 230 with the slider. At 360 × 640 the stage gets about 360 dp of the window (568 after the system bars, minus the 56 dp top bar and 152) against about 282 with the slider, so the picture is bigger than in the photo editor. At 200% font the tool row wraps and the area scrolls inside its cap (P2-04 rule); the stage never goes below 30% of the window.
- **Aspect tiles:** Free · Original · Canvas · 1:1 · 4:5 · 3:4 · 9:16 · 16:9, unchanged. **Canvas** is the canvas's current shape, so crop to it and tap Fill and the clip fills the frame exactly. **Original** is the clip's own shape after the turns.
- **Top bar, Reset, Done, Close, Edges panel:** exactly P2-04. Rotate and mirrors turn the picture and the frame together (200 ms; instant with reduce motion).
- **Reopening** shows the saved crop, turns and mirrors, so a crop can be adjusted, not only redone (P2-04: non-destructive).
- **For an overlay** the same screen; "Canvas" is still the canvas's shape.

### The selected main clip on the preview (D4)
- **Frame, handle, guides, bubble:** P4-01b → The selected overlay on the stage, unchanged, drawn around the clip's **drawn box** (its Fit or Fill size, framing applied, after the crop and turn). A Fit clip's frame hugs the picture inside the black bars; a Fill clip's frame sits on the canvas edge (clipped at the stage's edge; the handle clamp from P4-01b keeps it whole).
- **When it shows:** while the clip is selected (from the timeline; a stage tap never selects a main clip, because a stage tap is play and pause, P1-04) and the playhead is inside it (always true for the clip under the playhead).
- **Gestures:**

| Gesture | On | Result |
| --- | --- | --- |
| Drag (one finger) | **The selected main clip's box, where no caption or overlay is** | Pauses, moves it 1:1 |
| Drag | The handle | Scales and turns the clip around its center |
| Two fingers | Anywhere on the stage, a main clip selected | Scale, rotation and position together |
| Tap | Anywhere not on a caption or overlay | Play or pause (unchanged, whatever is selected) |

  An **unselected** main clip takes no drag: it can't be moved by accident. Hit order: the handle, captions, overlays front to back, then the selected main clip.
- **Snapping, limits, live feedback, one step per gesture ("Position and size"), undo while down, any touch pauses:** P4-01b. **100% is the clip's own Fit or Fill size**; size runs 10% to 500%; the center stays within 0…1; rotation snaps to 0°, 90°, 180°, 270°.
- **Frame edge** shows during the gesture, as for overlays.

### Position panel for a main clip
P4-01b's Position panel (Size, Rotation, Move, Center) with the photo layers' size ladder, plus **Reset** (Quiet button) between the title and Done: **Reset** returns the clip's framing to the standard place (100%, 0°, centered) in one step, "Reset position". It is not on overlays: an overlay has no standard place (it lands at 50% in a slot), a main clip does.
- **Budget:** content 240 dp (four rows and three gaps), against 133 / 206 / 252 dp at 360 × 640 / 360 × 800 / 412 × 915: **it scrolls on the first two and fits on the third**, as for overlays. The title row grows a line when Reset and Done don't fit beside it at 200% (they wrap under the title, P2-04 pattern).
- Limits explain on tap: "This is the smallest size." / "This is the biggest size." / "This clip can't go further this way." (`explain_clip_edge`).

## How it draws (preview and export match)
Declarative parameters on the clip, one fixed order, the same in the preview and the export. Part of every saved draft's look; in `design/tokens.json` → `clipFrame`.

**Pipeline:** 1. **Turn** (quarter turns clockwise) → 2. **Mirror** (as seen: left to right and top to bottom) → 3. **Crop** (fractions of the turned and mirrored picture, as P2-04's `CropRect`) → 4. **Fit or Fill** the result to the canvas (Fit: the whole cropped picture inside the canvas, centered; Fill: covering it, centered, the overflow cropped; a main clip only; an overlay is fitted inside at its own scale) → 5. **Framing** (scale, rotation about the clip's own center, then position; `Transform`, identity by default) → 6. **Opacity** (overlays) → drawn over the background (P1-11) or the layers beneath it (P4-01b).

| Rule | Value |
| --- | --- |
| Crop minimum | 10% of the picture's width and of its height (P2-04) |
| Straighten | none for clips (D3) |
| Fit and Fill | P1-11's rules (`LAYOUT_SCALE_TO_FIT`, `LAYOUT_SCALE_TO_FIT_WITH_CROP`) applied to the **cropped, turned** picture; a cropped clip's shape is the crop's |
| Framing | scale 10% to 500% of the Fit or Fill size; the center within 0…1 of the canvas; rotation clockwise |
| Blur background (P1-11) | the Fill-scaled blurred copy is of the clip **with its crop, turns and mirrors, without its framing**, so the blur doesn't slide with the picture |
| Missing file | plays black (main clip, P1-04); an overlay isn't drawn (P4-01b) |
| Same-shape clip | Fit and Fill are the same picture; the stored value is kept as it is |

**Reference renders** (CLAUDE.md rule 5), 1080p frames at 1.0 s in a 9:16 canvas, on the debug clip **`framing-check`** (request 7): a 1920 × 1080 clip with a 12 × 12 grid of thin lines, **a letter in each column (A to L) and a number in each row**, an orange triangle in the top-left corner and a blue circle in the bottom-right, so every flip, turn and crop reads at a glance (letters mirror). Saved in `docs/qa/references/P4-16-clip-frame/`:

| File | Parameters | The frame must show |
| --- | --- | --- |
| `identity-fit` | none, Fit | The whole clip, 1080 wide, black bars above and below, triangle top-left |
| `identity-fill` | none, Fill | Columns D to I, full height; triangle and circle both cropped away |
| `crop-square-right-fit` | Free crop to 1:1 on the right edge (columns G to L), Fit | A square, circle bottom-right, bars above and below |
| `rotate-right-fit` | one quarter turn right, Fit | The clip as 1080 × 1920 filling the frame; triangle now top-right; letters read downward |
| `flip-h-fit` | mirror left to right, Fit | Letters mirrored, triangle top-right, circle bottom-left |
| `all-three-fill` | crop 1:1 (right), turn right, flip horizontally, Fill | The square's picture covering the frame with its turn and mirror applied |
| `mixed-fit-fill` | two clips in one project, the first Fit, the second Fill (frames at 1.0 s and 4.0 s) | The first with bars, the second covering: one clip's setting never touches the other |
| `framing` | Fit, 150%, centered at (0.3, 0.7), 15° | The picture bigger, lower left, tilted; background around it |
| `overlay-crop-flip` | an overlay at 50%, cropped to 1:1 and mirrored | The overlay square and mirrored over the main video |

## Rules for the other edits
| Edit | What happens to a clip's crop, turns, mirrors, Fit or Fill and framing |
| --- | --- |
| **Split** | Both pieces keep all of it |
| **Duplicate** | The copy keeps all of it |
| **Trim, Move, Reorder, ripple** | Untouched |
| **Replace** (D6) | Crop, turns and mirrors are cleared; Fit or Fill and framing are kept; one step "Replace" |
| **Detach audio, Volume** | Untouched |
| **Ratio change** (P1-11) | Crop, turns and mirrors are about the picture, so they stay. Fit and Fill redraw to the new shape. **A main clip with non-standard framing follows the picture-layer rule** (P2-01 → Ratio change): fully inside stays fully inside (moved in to touch the edge, margin 0, shrunk first if too big, never below 10%); partly outside keeps its center and scale. A clip at the standard place needs nothing. The shared toast says "Layers moved to stay inside the frame." |
| **The Canvas panel's Fit or Fill** (every main clip) | Sets every main clip's fit and resets every clip's framing, in one step; crops, turns and mirrors stay |
| **Add media** (P1-16) | New clips take the Fit or Fill every main clip shares, Fit when mixed (P1-11), with standard framing and no crop |

## The Canvas panel after this ticket (P1-11 pointers)
- **Clips row:** its note "it applies to every main clip at once" gains "and puts each clip's framing back to the standard place". **Mixed is now a real state:** with one clip Fill and another Fit, neither tile is selected, and a tap sets every clip (P1-11 already specced it as defensive).
- **The "background doesn't show" line** (P1-11): computed per clip from its cropped and turned shape. A clip with non-standard framing counts as possibly showing the background (the line stays hidden), the conservative rule P2-01 uses for turned layers.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Main clip selected | The bar with Crop, Fill or Fit and Position after Delete; frame and handle on the stage | `tool_crop`, `canvas_fit` or `canvas_fill`, `tool_position` |
| Overlay selected | Crop after Volume (or Split on a photo); no Fill or Fit | `tool_crop` |
| Clip file missing | Replace first (overlay: Delete first); Crop, Fill or Fit and Position are absent | `editor_missing_media`, `editor_missing_overlay` |
| Fill or Fit on a clip already the frame's shape | Toast; nothing changes | `explain_clip_fit_same` |
| Fit applied | The whole clip with the background around it; framing back to standard | `edit_canvas_fit` |
| Fill applied | The clip covers the frame; framing back to standard | `edit_canvas_fill` |
| Crop screen opening | The picture area `surface-raised` and static until the frame is ready | none |
| Crop screen, ready | The clip's frame, the crop frame the whole picture, Free selected; Done enabled | `crop_title` |
| Crop screen, frame can't be read | The screen doesn't open; toast | `explain_crop_frame_failed` |
| Crop screen, a handle dragged, minimum reached, Edges panel | P2-04 | P2-04 |
| Crop applied | Back on the editor, the clip selected; the stage shows the result; thumbnails and Reorder tiles show it; TalkBack: "Crop applied" | `a11y_crop_applied`, `edit_crop` |
| Closed with Close, back or Escape | Nothing applied, no question (P2-04) | none |
| Clip moved, sized or turned on the stage | Frame follows; guides; bubble; frame edge | `text_transform_bubble` |
| Position panel on a main clip | Rows and Reset | `position_*`, `edit_photo_reset` |
| Position panel at a limit | Toast | `explain_text_smallest`, `explain_text_biggest`, `explain_clip_edge` |
| Reset | The clip returns to standard; one step | `edit_reset_position` |
| Canvas panel Clips row, mixed | Neither tile selected | `canvas_fit`, `canvas_fill` |
| Undo or redo | Applies at once; the screen or panel on a clip that no longer exists closes | `editor_undo_toast` |
| An old draft (before this ticket) | No crop, turns, mirrors or framing; each clip's saved fit as before | none |
| Long content (5 languages, 200% font) | Tool labels wrap and widen (Spanish "Rellenar", "Recortar" and Portuguese "Preencher" are 8 to 9 characters, under the 100%-font limit); the Crop screen's bottom area scrolls | none |
| Offline, permission | Don't apply | none |
| First run | Same as every run; no coach mark | none |

## Interactions
- **Undo steps** (each is one, with its edit name): a visit to the Crop screen ending in Done, with any mix of crop, turns and mirrors ("Crop"); a Fill or Fit tap ("Fill" or "Fit", which includes the framing reset); a stage gesture, each Position tap, hold and Center ("Position and size"); Reset ("Reset position"); the Canvas panel's Fit or Fill ("Fit" or "Fill", every clip); Replace ("Replace", which includes clearing the crop). Closing the Crop screen without Done records nothing.
- **Haptics:** SegmentTick on snaps, as P4-01b; nothing else.
- **Keyboard (basic, P1):** Tab reaches the new tools and every Crop screen control (P2-04); Escape closes; the frame's arrow keys are Later (L-01), the Edges panel is the keyboard route.
- **Playback:** the Crop screen pauses it; a stage gesture on a main clip pauses it first.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Crop screen opens or closes | 200 ms slide up (P2-04) | 120 ms fade |
| Rotate or flip on the Crop screen | 200 ms turn or mirror | instant |
| Fit or Fill applied | At once, like undo: the engine renders the new picture; the preview keeps its last frame until it is ready (P1-06) | same |
| Selection frame and handle | 120 ms fade | same |
| Panel opens or closes | 200 ms slide | 120 ms fade |
| Thumbnails after a crop | Reload by P1-05's loading rule (`track-video` until ready, 120 ms fade) | same |

## Copy
English source; drafts for id, es, pt and hi as in P4-01b; the engineer machine-drafts the rest until P5. Terms follow the shipped strings: crop = *Pangkas*, *Recortar*, *Cortar*, *क्रॉप*; Fit and Fill = *Pas* and *Penuh*, *Ajustar* and *Rellenar*, *Ajustar* and *Preencher*, *फ़िट* and *भरें*; frame = *bingkai*, *marco*, *quadro*, *फ़्रेम*. Budgets in characters, placeholders expanded.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_clip_fill_click` | Cover the whole frame with this clip | 50 | Tutupi seluruh bingkai dengan klip ini · Cubrir todo el marco con este clip · Cobrir o quadro todo com este clipe · इस क्लिप से पूरा फ़्रेम भरें | Click label of the Fill tool (TalkBack "double-tap to …"). 36 en. Never replaces the name "Fill". |
| `tool_clip_fit_click` | Show the whole clip inside the frame | 50 | Tampilkan seluruh klip di dalam bingkai · Mostrar el clip entero dentro del marco · Mostrar o clipe inteiro dentro do quadro · पूरी क्लिप फ़्रेम के अंदर दिखाएं | Click label of the Fit tool. 36 en |
| `explain_clip_fit_same` | This clip is the same shape as the frame, so Fit and Fill look alike. | 90 | Klip ini berbentuk sama dengan bingkai, jadi Pas dan Penuh terlihat sama. · Este clip tiene la misma forma que el marco, así que Ajustar y Rellenar se ven igual. · Este clipe tem o mesmo formato do quadro, então Ajustar e Preencher ficam iguais. · यह क्लिप फ़्रेम के आकार जैसी ही है, इसलिए फ़िट और भरें एक जैसे दिखते हैं। | 68 en; es 85, pt 80. The words "Fit" and "Fill" must match `canvas_fit` and `canvas_fill` exactly in each language. |
| `explain_crop_frame_failed` | Couldn't open this clip to crop it. Try again. | 70 | Klip ini tidak bisa dibuka untuk dipangkas. Coba lagi. · No se pudo abrir este clip para recortarlo. Inténtalo de nuevo. · Não foi possível abrir este clipe para cortar. Tente de novo. · क्रॉप करने के लिए यह क्लिप नहीं खुल सकी। फिर कोशिश करें। | 47 en; es 62, pt 61 |
| `explain_clip_edge` | This clip can't go further this way. | 60 | Klip ini tidak bisa bergerak lebih jauh ke arah ini. · Este clip no puede ir más lejos así. · Este clipe não pode ir mais longe assim. · यह क्लिप इस तरफ़ और आगे नहीं जा सकती। | 36 en; id 52. Position panel's Move arrows on a main clip |
| `position_reset_a11y` | Reset position and size | 32 | Setel ulang posisi dan ukuran · Restablecer posición y tamaño · Redefinir posição e tamanho · जगह और आकार रीसेट करें | Spoken name of Reset; contains the visible "Reset" (WCAG 2.5.3). The visible word is `edit_photo_reset`. |
| `edit_reset_position` | Reset position | 24 | Setel ulang posisi · Restablecer posición · Redefinir posição · जगह रीसेट | Edit name after "Undo: " |
| `a11y_crop_applied` | Crop applied | 24 | Pangkas diterapkan · Recorte aplicado · Corte aplicado · क्रॉप लागू हुआ | Polite announcement after Done |
| `a11y_state_cropped` | Cropped | 16 | Dipangkas · Recortado · Cortado · क्रॉप किया गया | Appended to the clip's spoken description when its crop isn't the whole picture |
| `a11y_state_turned` | Rotated | 16 | Diputar · Girado · Girado · घुमाया गया | Appended when the quarter turns aren't zero |
| `a11y_state_flipped` | Flipped | 16 | Dibalik · Volteado · Espelhado · पलटा गया | Appended when either mirror is on |
| `a11y_state_repositioned` | Moved or resized | 24 | Digeser atau diubah ukurannya · Movido o redimensionado · Movido ou redimensionado · खिसकाया या आकार बदला गया | Appended when a main clip's framing isn't standard |

Reused: `tool_crop`, `crop_*`, `edit_crop`, `edit_photo_reset`, `canvas_fit`, `canvas_fill`, `edit_canvas_fit`, `edit_canvas_fill`, `tool_position`, `position_*`, `explain_text_smallest`, `explain_text_biggest`, `editor_missing_media`, `editor_missing_overlay`, `edit_transform_layer`, `a11y_edit_on_item`, `panel_done`, `canvas_layers_moved` (now "Layers moved to stay inside the frame.").

Translator notes:
- **Fit** shows the whole clip with empty space around it; **Fill** makes the clip cover the whole frame and cuts off what doesn't fit. Use the pair your language's video editors use (the shipped words above), and keep them short and different.
- **Crop** cuts a picture down to the part you want; **Rotated** is a quarter turn; **Flipped** is a mirror.
- **Frame** is the video's frame, not a picture frame.
- **Hindi:** the interface uses the system Devanagari face; no row has a fixed text height.

## Accessibility
- **Tool names** are their visible labels (WCAG 2.5.3); the Fill or Fit click label carries the effect. The label flips after each tap and the bar's content changes: TalkBack announces the new label politely ("Fit"), and focus stays on the tool.
- **Crop screen:** P2-04's: the frame and handles are hidden from accessibility; the aspect tiles, rotate and flip buttons, Edges panel are the full single-pointer route (WCAG 2.5.1, 2.5.7); each rotate and flip is announced with its result. Focus order: Close, Reset, Done, the aspect tiles, the tool buttons, Edges. Closing returns focus to the Crop tool. On Done, "Crop applied".
- **The clip's spoken description** (P1-05 `a11y_clip_media`) gains, appended in this order, "Cropped", "Rotated", "Flipped", "Moved or resized" when they are true. Never in the state description (which stays "selected").
- **On-frame gestures on a main clip:** the Position panel is the alternative (WCAG 2.5.1, 2.5.7), reachable by the bar and by the clip's accessibility actions (add **Position** to the main clip's custom actions). The handle is hidden from accessibility.
- **Touch targets:** every tool, tile and button is 48 dp or more; the Crop screen's handles have 48 dp targets (P2-04).
- **Contrast:** frame and handle as P1-10 (4.3:1 or more over any picture); the Crop screen as P2-04 (frame white with a near-black edge; Done `on-primary` on `primary` 7.9:1; tile labels `text` on `surface-raised` 12.4:1; selected `on-primary` on white 18.9:1); tools `text-secondary` on `surface` 5.7:1.
- **200% font:** the bar grows and labels wrap; the Crop screen's top bar wraps Reset and Done under the title, the tool row wraps, the bottom area scrolls inside its cap; the Position panel scrolls with Reset and Done wrapping under the title. Nothing is cut. Hindi at 200% is a screenshot (QA 6).
- **Reduce motion:** see Motion.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging; never when a tool only explains; never for closing the Crop screen without Done). New ids for the PM to confirm (they are the edit names in the undo history):

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | The Crop screen's Done, **once for each part that changed** (as P2-04) | `tool: "crop_clip"` (the frame changed), `"rotate_clip"` (the turns changed), `"flip_clip"` (a mirror changed) |
| `tool_use` | Fill or Fit applied to one clip | `tool: "clip_fill"` or `"clip_fit"` |
| `tool_use` | A stage gesture on a main clip releases | `tool: "transform_clip"` |
| `tool_use` | A Position step on a main clip, or Reset | `tool: "position_clip"`, `"reset_position_clip"` |

- The Canvas panel's Fit or Fill keep `canvas_fit` (with `value`, P1-11) and fire once for the one step. An overlay's Crop uses the same three ids.
- `value` is not used (canvas tools only). Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `framing-check` (request 7) and a 360 × 800 and a 360 × 640 phone. Screenshots in `docs/ux/reviews/P4-16/`.
1. **The bars:** a main video clip selected (Crop, Fill, Position after Delete); then after Fill, the same tool reading **Fit**; a main photo clip; a video overlay with Crop after Volume.
2. **The Crop screen** on a video frame at 360 × 640: the aspect row, the tool row, no Straighten; the Canvas tile selected and the frame dragged; the Edges panel; Hindi at 200% font.
3. **Each reference frame** next to its screenshot from the editor (Measured).
4. **Fit on one clip only:** three clips, Fill on the second; the first and third unchanged; the Canvas panel with neither tile selected; the timeline thumbnails of the second unchanged.
5. **Fill on a same-shape clip:** the explaining toast.
6. **Position panel** for a main clip at 360 × 640 and 360 × 800 (scrolled, Reset in the header), Hindi at 200%.
7. **Rotated clip's thumbnails** in the strip and in Reorder mode (rotated like the preview).
8. **Ratio change with a moved clip:** 9:16 to 1:1 with a Fit clip moved to one side: still inside; the toast.

**Measured: the Done when** (note in the QA report; PM method in `docs/pm/acceptance-log.md`):
- **Preview = export:** export `framing-check` with a project holding each reference scene at 1080p30; pull the frames with ffmpeg at the times above and compare with the preview screenshots cropped to the frame and scaled to the export width. Pass: the same crop and placement within 1% of the width, the same orientation (letters read the same way round or mirrored alike), the same colors sampled in the triangle and the circle, the blur (if used) alike.
- **Matches the reference:** the same frames, compared with `docs/qa/references/P4-16-clip-frame/` for each file in the table above (the references are made from the first passing build and then pinned; the table says what each must show).
- **One clip only:** on a project of three clips, Fill the second; export; the first and third frames equal the no-change export.
- **Each change is one undo step:** perform each edit in Interactions → Undo steps, then Undo once after each: exactly that edit goes (a Crop screen visit with a turn, a mirror and a crop is one step; Fill's undo also restores the framing).
- **Old drafts open:** a draft saved before the ticket opens unchanged; one with a crop reopens with the Crop screen showing it.
- **Non-destructive:** the media file's size and hash don't change after any of it.
- **Responsiveness:** a Fill or Fit tap shows on the preview within 100 ms, or "Updating the preview" after 1 s (P1-06).
- **Leaks:** LeakCanary: open the Crop screen, rotate, flip, Done, Fill, drag, close the editor; repeat 5 times.

## Model needs (for the PM and the engineer)
| Need | Expected model |
| --- | --- |
| Geometry | `MediaClip.edit: ImageEdit?` reusing P2-04's type (quarter turns, mirrors, `crop`; `straighten` stays 0 and is not shown). **Reusing it, not copying it**, is what lets P4-07 add the clip's color edits (filters) to the same field, and the pure geometry functions (`shownSize`, `turnedRight`, `CropFrames`) already exist |
| Fit or Fill | `MediaClip.fit` (exists), main clips only |
| Framing | `MediaClip.transform: Transform` for a main clip, identity by default (the overlay field P4-01a adds, now also used on main clips) |
| Version | A `schemaVersion` bump with an identity migration (every new field defaults to none), checked by opening a draft saved before; if P4-01a's bump already carries `edit` and `transform` on `MediaClip`, none is needed |
| Missing | `Replace` clears `edit`, keeps `fit` and `transform` |

## Requests to the principal mobile engineer
1. **Crop screen as a clip tool:** pass the clip's frame as the picture (a use case in `:core:domain` that returns the frame at a time, decoded at the crop stage's size, long side at most 2048 px); a parameter that hides Straighten and its slider (`showStraighten = false`); the same `canvasAspect`; the result applied as one `commit` "Crop".
2. **Engine (preview and export from one plan):** the pipeline order above for every main clip and overlay; Fit and Fill of the cropped, turned picture; framing for main clips with the same transform path as overlays (P4-01a); the Blur background's copy with geometry and without framing; the old Fit and Fill behavior unchanged for clips with no edit.
3. **Edits** in `:core:domain` (pure, one `commit` each): `setClipEdit`, `setClipFit` (toggle, resets framing, refuses a same-shape clip), `setClipTransform`, `resetClipTransform`; Replace clears the geometry; the Canvas panel's `setClipsFit` also resets framing; the ratio rule for main clips with framing.
4. **Tool bars and stage:** the three tools after Delete on main clips and Crop on overlays; the Fill or Fit tool's flipping label and icon; the Position panel for main clips with Reset; hit order with the selected main clip last; the Position action on the clip's accessibility actions.
5. **Thumbnails:** the thumbnail provider takes the clip's geometry (D8); the cache key includes it.
6. **Constants:** one commonMain place (extend `OverlayCatalog` or add `ClipFrameCatalog`) with a comment pointing to this spec and `design/tokens.json` → `clipFrame`.
7. **Debug clip `framing-check`** and reference scenes: the lettered grid clip as described, as a video and as a still; a debug draft holding the scenes of the reference table.
8. **Analytics:** the new `EditorTool` values; the shared `value` on the canvas tools unchanged.
9. **Strings** in the five files.

## Tokens and icons
- **New content constants** in `design/tokens.json` and `design/system/tokens.json` → `clipFrame` (pipeline order, geometry rules, framing limits, the reset and replace rules). Not in `MemixTheme`.
- **No new color, size, radius or motion token. No new icon:** `crop`, `fit`, `fill`, `position`, `rotate_left`, `rotate_right` and `flip` all exist (P2-04, P1-11, P1-10). The Fit and Fill tool uses the pair `fit` and `fill`.
- **Pointer edits made with this spec:** P1-06's clip-bar table, ToolBar README (the new tools), P1-11's Clips row and mixed-state notes, P2-04's "P4-16 reuses it" line.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Can a main clip be moved, sized and turned on the preview?**
- *Problem:* the ticket says P4-16 "reuses P4-01's on-frame gestures", but its Done when lists only crop, rotate, flip and Fit or Fill. Gestures on a main clip add a `transform` to main clips and a path in the engine.
- *Considerations:* **A. Yes: drag, pinch and twist the selected main clip, plus Position and Reset** (recommended; applied): it is what the ticket text promises; it turns "show the part of a landscape clip I want" and "leave room for the caption" into one drag; the engine already does it for overlays (P4-01a), so the cost is mostly the main clip's own path and the QA reference (about half a day). Risk: preview and export parity for a path the main track didn't have. **B. No: main clips only get the Crop screen and Fit or Fill:** smaller, nothing new on the main track; the gestures serve overlays only; "choose the part" means crop to the Canvas shape and Fill. The ticket text would then be reworded.
- *If B:* remove the Position tool, Reset and the stage gestures from the main clip's rows above; nothing else changes. *Reverse of A later:* a saved transform is just ignored.

**2. Straighten for clips?**
- *Problem:* the photo editor's Crop screen has a Straighten slider (levels a tilted horizon). Video clips could have it.
- *Considerations:* **A. No** (recommended; applied): the PRD lists crop, rotate and flip for video; Straighten adds a free rotation that must be scaled up to cover its frame and agree between preview and export, for a job (a tilted phone clip) that is rare in memes. **B. Yes:** parity with the photo editor, the Crop screen unchanged, but a second rotation that fights Fit and Fill, and more to verify.
- *Reverse:* B can be added later without a migration (the field exists and is 0).

**3. Does tapping Fit or Fill reset the clip's position and size?**
- *Problem:* a clip the user moved and zoomed, then switched from Fit to Fill, would otherwise keep "150% of the old size" and jump.
- *Considerations:* **A. Reset to the standard place** (recommended; applied): predictable ("Fit and Fill are where a clip starts"), one undo step brings everything back; cost: a user who only wanted the other mode loses a framing they liked, and the Canvas panel's every-clip Fit or Fill resets every clip's framing at once. **B. Keep it:** nothing lost, but the result of the tap depends on the invisible old values.
- *Reverse:* a rule change, no migration.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Dragging a crop handle and pinching or twisting need a single-pointer alternative; the aspect tiles, the Edges steppers, the rotate and flip buttons and the Position panel are it | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements), [SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures) |
| A control's accessible name includes its visible label (the Fill or Fit tool's name is its label; the effect is the click label) | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Clipchamp sets Fit and Fill per clip (a precedent for the per-clip tool); it has no way to set all clips at once, which Memix's Canvas panel keeps | Guidance (vendor help, read for P1-11) | [Clipchamp: change the aspect ratio of a video](https://support.microsoft.com/en-us/clipchamp/how-to-change-the-aspect-ratio-of-a-video) |
| Media3 applies scale, rotation and position per input in a composition, which is where framing maps; the crop and mirror map to the same transform before it | Guidance (API docs; verify at the start of the build) | [Define layouts with a custom VideoCompositorSettings](https://developer.android.com/media/media3/transformer/videocompositorsettings) |
| Mobile editors offer Crop, Rotate and Mirror as clip tools and let a selected clip be pinched and dragged on the preview | Assumption (product knowledge of CapCut and VN; their help pages weren't reachable here) | none |
| Text in the reference clip mirrors with the picture, which makes a flip visible at a glance | Reasoning, for the test design | none |
