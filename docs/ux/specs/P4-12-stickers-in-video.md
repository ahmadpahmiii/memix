# Stickers in video: packs, animated GIF and WebP, your own files, and photo GIF export · P4-12

Tenth Phase 4 spec (written 18 Nov 2026). It reuses, and does not repeat:
- `P2-14-sticker-picker.md`: the Stickers sheet, the grid, tiles, My stickers, the device-wide sticker store, delete with Undo, the "own copy in the project" rule, empty state. **The sheet is built once and lifted for the video editor**; this spec adds the tab row that P2-14 reserved ("built for packs", hidden with one source).
- `P2-03-photo-text-and-stickers.md`: the sticker size rule (longer side 35% of the canvas's shorter side at scale 1, scale 10% to 500%), gestures, snapping. `P2-08-photo-export.md`: the Export sheet, the format group ("a third segment, GIF, with a frame-count estimate", note 3), Saving, the Exported screen.
- `P4-01b-overlay-layers.md`: landing slots, on-frame gestures, Position and Opacity panels, lane rule, the pinned "Add a meme sound" row. `P4-02b-keyframes.md`: sticker keyframes (position, scale, rotation, opacity). `P4-06-video-effects.md`: the "at most N at once" rule and its toast pattern.
- `P3-07-my-sounds.md`: the Import row (`folder`, `nav-row-height`), the import failure copy. `P3-04-download-states.md`: catalog downloads and offline. `P2-09-whatsapp-sticker-pack.md`.
- Component READMEs: Sheet, Tabs, Menu, Toast, ToolBar, ToolPanel, Timeline, SegmentedChoice, Slider, ProgressBar.

## Job
"Put the skull on the punchline": open Stickers, tap one, it is on the video at the playhead and it moves. PRD → Video editor → Stickers: static and animated (GIF, WebP) from catalog packs or the user's own files. PRD → Photo: GIF export when a layer is animated. **One main action: none on the sheet** (a tile tap is the action); the one blue action stays Export (P1-12) and, in the photo Export sheet, Save to gallery (P2-08).

**Done when** (TICKETS P4-12): animated stickers loop in sync with the timeline. Made checkable in Measured. Photo GIF export (moved from P2-08) is checked by the same bar: the file plays the same loops.

**Split (decided here, for the PM).** The ticket holds a lot (watch M):

| Part | Contains | Why |
| --- | --- | --- |
| **P4-12a** | Video editor: Stickers tool and sheet with tabs, packs (fixtures), Add from your files, the animated-image engine in preview and export, the sticker lane and bar, opacity, `trimIn`, the 4-at-once rule, the schema bump | The Done when lives here |
| **P4-12b** | Photo editor: animated stickers in the photo canvas with the Play control, the Export sheet's GIF segment and the GIF encoder, the animated flag in the WhatsApp builder | Photo-only; needs 12a's decoder and store changes |

**Fast path, with tap counts.**
- **A sticker on the moment:** scrub to it, **Stickers (1)**, a tile (2). It lands at the playhead, selected, moving if it is animated. Two taps.
- **From my own GIF:** Stickers (1), **Add from your files** (2), pick in the system picker (3): it lands at once and joins My stickers.
- **Bigger, turned, moved:** pinch, twist, drag on the stage (the P4-01b gestures); or Position (the no-drag route).
- **Off again:** Delete, or Undo.

**Principles check.** Content first: the sticker is the most colorful thing on screen; the sheet holds pictures, no chrome text beyond names. Sound first: the pinned "Add a meme sound" row is untouched (the sticker lane scrolls into view like every lane). Fast path: two taps for the headline case. One-handed: sheet, tabs and tiles are in the lower two thirds. Forgiving: Undo everywhere; delete from My stickers has Undo. Accessible: tiles are labeled; the photo editor has a Pause control for moving pictures (SC 2.2.2); respects "remove animations". Five languages: pack names are catalog titles in five languages.

**Color roles:** the sticker item is `track-sticker` gold; no new color. White is selection. The sheet has no blue. Sticker pictures are the user's content.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| **Stickers sheet** (Sheet, up to 70% of the window) | The **Stickers** tool of the video main bar (nothing selected); in the photo editor the same tool (P2-14) | A tile tap (lands it), a successful import (lands it), close, scrim, drag down, back, Escape (nothing changes) |
| **System file picker** (Android's document picker, images only) | **Add from your files** | The user picks or cancels; cancel returns to the open sheet |

- **Video main bar:** **Stickers** (icon `sticker`) after Voiceover, before Effects (final order set in P4-07). Click label "Add a sticker at the playhead". **Not shown in an empty project** (as Effects). Arrives only from the main bar (with an item selected the bar shows that item's tools: tap empty timeline first, as Effects).
- **Opens on:** the tab last used in the visit; else **My stickers** if it has stickers; else the first pack (the sheet never opens on an empty list when a pack exists). With no packs and no stickers it is P2-14's empty state.
- **Kept:** the sticker items in the project with their **own copy of the file** in the project's folder; My stickers in the device-wide store. Not kept: the sheet's tab beyond the visit.
- **Back order:** a sheet closes, then the sticker's bar closes (selection clears), then the editor.

## Layout
Board: none (`VideoEditor.dc.html` has no sticker lane item or sheet). P2-14's sheet is the base; the differences are below.

### The Stickers sheet (video and photo editors, one component)
Standard Sheet (grabber, close, `surface`, `radius-lg`, `shadow-sheet`, `scrim`). Title "Stickers" (`title`, heading).
- **Tab row** (the Tabs component, scrolling): **My stickers** first, then one tab per catalog pack in the catalog's order, the pack's title (catalog `title`, five languages). **Shown only when at least one pack is available** (cached or live); otherwise it is hidden and the sheet is P2-14's. In debug builds the P4-15a fixtures supply two packs (below).
- **My stickers tab:** the **Add from your files** row first (`nav-row-height` 56, `surface-raised`, `radius-md`, the `folder` icon, label `body-strong`, and under it `label` `text-secondary` "GIF, WebP or PNG, up to 15 MB"), then the P2-14 grid (newest first). Long-press a tile: the Delete Menu with Undo toast (P2-14, unchanged). The empty state keeps P2-14's text and its one action and **adds Add from your files** as a Secondary button under it (the blue action stays Remove background or Add photo in the photo editor; in the **video editor** the empty state's one blue action is none: its body there reads the video variant below, with Add from your files as Secondary).
- **A pack tab:** under the tabs, one `label` `text-secondary` line **"By <credit>"** when the pack has a credit (CC BY packs; `credit` is in the catalog, PRD licensing), then the grid of the pack's stickers in catalog order. No Delete, no long-press.
- **Tiles** (P2-14): `surface-raised` square, `radius-md`, picture fitted inside with `space-2` padding, 3 columns at 360 dp (`grid-tile-min`, `space-2` gaps, `space-4` side padding), whole tile is the target. **Animated stickers** show **their first frame** (MOTION 2: nothing loops in the chrome) with a `badge-size` chip at the top-right (`scrim`, `play` icon at `icon-small` in `text`, `space-1` from the corner, 7.6:1 or more over any picture); the spoken name says "Animated".
- **Budget** (window 640 / 800 / 915; sheet 70% = 448 / 560 / 640; frame about 110, tabs 48, bottom padding 24 → **266 / 378 / 458 dp** of grid area): tile row 104 + `space-2`. **Two rows show whole at every size** (216), a third from 800. In My stickers the 56 dp import row plus `space-2` takes the first view's first row at 640 (two rows then show 266 − 64 = 202: one whole row and a half; the grid scrolls). At 200% font tab names size to their text and scroll sideways; the credit line and the empty-state body wrap.
- **Catalog tiles and the phone:** pictures come from the catalog cache (P3-01, 24 h list; files by P3-04's rules, the 500 MB least-recently-used cache). A tile not yet loaded is the plain `surface-raised` square (no spinner); it fills in as it scrolls into view. **Landing waits for the file** (below).

### The sticker on the timeline and the bar
- **Lane:** the existing Stickers lane (lane 4, `track-sticker`, 32 dp row, item 28 dp, P1-05). An item shows its picture fitted in a 24 dp square at its left, then the label (the sticker's name in `caption`, `on-track` 9.0:1, sticky at the item's left, ellipsis); under 56 dp wide: the picture alone; under 32 dp: nothing (the playhead-width minimum, P1-05). An animated item's picture is its first frame (static in the chrome). Overlapping items go on their own lanes (the P1-06 lane rule: first lane free for the whole span; later lane = in front).
- **Selected:** white outline and handles as every item; past the end of the video: the `scrim` wash (P1-05). Missing file: the "file missing" look and spoken state of P1-05, not drawn in preview or export.
- **Sticker bar (clip mode: Close, divider, then):** **Position · Opacity · Delete · Split · Keyframes · Move here · Duplicate · Replace**. The first three stay whole beside Close at 360 dp (ToolBar rule). Icons all exist (`position`, `opacity`, `delete`, `split`, `keyframe`, `move_here`, `duplicate`, `replace`); Replace's click label is "Pick a different sticker". No Flip, no Speed for stickers in v1 (Later, no ticket).
- **The stage:** the P1-10 / P4-01b gestures (drag, handle, two fingers) with the two-tone frame and handle; the sticker's size rule is P2-03's. **The frame, handle and hit area are the sticker's un-animated box** (the picture's pixel size, so a transparent area is part of the box, as in P2-03). Tap on the stage outside every item toggles play, as everywhere.
- **Panels:** **Position** is P4-01b's panel (Size ladder, Rotation, Move, Center); **Opacity** is P4-01b's panel (10% to 100% static, 0% to 100% with keyframes). Keyframes: the keyframe bar of P4-02b, unchanged.

### Landing a sticker (video editor)
- **When:** at the playhead. **Length:** a still sticker **3 s**. An animated sticker: **a whole number of its loops** *n* = max(1, round(3 s / loop length)), length = *n* × loop, at most **10 s**; a loop over 10 s lands as 10 s. If less than 1 s of video remains, it **ends at the end** (start = the larger of 0:00 and end − length), as P1-10's text rule.
- **Where on the frame:** **centered, scale 1** (the P2-03 size rule), rotation 0, opacity 100%, in the first free slot of center, lower right, upper right, lower left, upper left (P4-01b's slots; a slot is taken when a visible sticker or overlay's center is within 0.1). *Why this differs from the photo editor, where a second sticker lands in the same place (P2-14):* a timeline item stacked exactly on another at the same moment is invisible and looks like a failed tap.
- **Then:** selected, the item bonks (`duration-bonk`) and its lane slides in over 200 ms, lanes scroll to show it; the sheet closes (200 ms); TalkBack hears "Sticker added at 3.2 seconds". No haptic (Confirm belongs to sounds). **One undo step "Add sticker".** The sticker's file is **copied into the project's folder first** (P2-14's rule; a failure keeps the sheet open, `stickers_add_failed`).
- **A catalog sticker not yet on the phone:** the tap shows the tile pressed; the file downloads (small); after 300 ms nothing else shows (no spinner); it lands when ready. Offline or failed: the sheet stays open and a toast says `stickers_offline` or `stickers_add_failed`.

### Animated stickers play in sync with the timeline (the rule)
- **Time base.** The picture shown at timeline time *t* is a pure function of *t*: τ = (*t* − the item's start) + the item's **trim-in** (a new field, 0 when placed); frame = the frame whose span holds (τ mod *loop length*). So preview, scrub, seek, a paused frame and the export agree to the frame.
- **Loop length** = the sum of the file's frame delays, measured once at import. **A frame delay under 20 ms counts as 100 ms** (the long-standing browser convention for 0 and 1 centisecond GIF delays; Assumption, verified at build). The file's own loop count is ignored: a sticker **always loops** while its item lasts (a meme sticker repeating is the point; a "play once" switch is Later).
- **It behaves like a video clip, not like text.** *Move* (drag, Move here): the picture moves with the item (the same frame at the same offset from its start). *Trim the start:* the visible part gets shorter and **trim-in grows by the same amount**, so the frame at any given timeline time does not change; *trim the end* just shortens. *Split:* the right piece's trim-in continues where the left ended, so the loop is seamless across the cut. *Duplicate:* the copy starts where the original ends, its own frame 0. *Replace:* the new sticker starts at frame 0 at the item's start (trim-in 0).
- **Why this way:** "hit the explosion on the beat" works by placing the item where its first frame should be and never moves again when trimmed.
- **Speed and reverse** don't apply to stickers (Later). Effects (P4-06) apply to the whole frame, stickers included; the watermark is last.

### Limits (fixed rules, `design/tokens.json` → `stickerAnimation`)
| Rule | Value | Why |
| --- | --- | --- |
| Formats in | GIF, WebP (static or animated), PNG | Transparency; the photo picker and chat apps produce these |
| File size in | **15 MB** | Memory and project copies; a typical 3 s GIF is 1 to 8 MB |
| Pixel size in | longer side **2048 px** | Decode memory |
| Animation length in | **300 frames** and **30 s** loop | Same |
| Moving at once | **4** animated stickers overlapping in time; a fifth explains with `explain_sticker_animated_max` and does not land (also for move, trim, duplicate, Split-into-overlap: refused with the item back) | Decoder and 30 fps budget; static stickers are unlimited |
| Flashing | Catalog packs are checked at intake (no more than 3 flashes in any second; P4-15 content rule, proposal to the PM); a user's own file is the user's content | SC 2.3.1 |

## Add from your files
- **The row** opens Android's document picker (`ACTION_OPEN_DOCUMENT`) filtered to `image/gif`, `image/webp`, `image/png`. **No permission** is asked (the picker hands over one file). On a phone whose picker lists other types, a refused type gets `stickers_import_unsupported` ("That file isn't a GIF, WebP or PNG. For a photo, use Overlay.").
- **What happens:** the file is read once; its type, size, pixel size, frame count and loop length are measured; if it passes the limits it is **copied into My stickers** (the device-wide store, so it is also in the photo editor and survives the project) **and placed** in the project (own copy), in one undo step "Add sticker". **It lands at once** (the pick was the intent; no extra tap to add it; Question 2).
- **Failures keep the sheet open** with a toast and write nothing: unsupported type, too big (`stickers_import_too_big`: file over 15 MB or a side over 2048 px), too long (`stickers_import_too_long`: over 300 frames or 30 s), unreadable (`stickers_import_unreadable`: a damaged file or one the phone can't decode), storage full (`stickers_add_failed`). Nothing is uploaded; `stickers_import_privacy` is not shown (no network involved).
- **Wait:** under 300 ms nothing; after 300 ms the row shows a pressed look and, for a file over 5 MB, the line "Adding…" (`stickers_import_working`) in the row's second line. No spinner (MOTION 2).

## Photo editor changes (P4-12b)
### Animated stickers in the photo canvas
- A sticker layer whose file is animated **plays its loop on the canvas** (frame from the editor's own clock, starting at frame 0 when the layer lands or the editor opens). The 4-at-once limit applies (`explain_sticker_animated_max`). Layers panel thumbnails are the first frame. Everything else about a sticker layer is P2-03's.
- **Animation control (WCAG 2.2.2):** when the canvas holds at least one visible animated layer, a **Pause / Play animation** button shows at the stage's **bottom-end corner**: a 48 dp IconButton on a `scrim` disc, `play` or `pause` in `text` (7.6:1 or more), `space-2` inside the stage. Tapping toggles all animated layers. It is a control over content: the corner of the stage is not a layer hit area. If the system's animations are off (Android "Remove animations", animator scale 0) the editor opens **paused on the first frame** and Play starts it. Spoken: "Pause animation" / "Play animation".
- **Make it a video meme** (P2-10): the lift rule needs one line, a proposal below (animated stickers are lifted to the video's sticker lane like text, or baked as their first frame).

### Export: GIF
P2-08's sheet gains **GIF** as a third segment when (and only when) a visible animated layer exists: **JPG · PNG · GIF** (names are constants). With JPG or PNG selected on such a canvas, one hint line (`label`, `text-secondary`) sits under the Format group: "Animated stickers show their first frame. Choose GIF to keep them moving."
- **When GIF is selected:** the quality slot shows one line "Up to 256 colors. Plays in a loop." (`photo_export_gif_hint`, no slider); **Size** becomes **480 px · 720 px** (the canvas's shorter side; default 480; 1080 would be huge and chat apps refuse it); **Copy is not shown** (clipboard GIFs are patchy across chat apps; Share on the Exported screen is the route) and **Save to gallery** is the whole button row.
- **What the file is:** every layer drawn per frame by the one scene function; **frame rate 20 fps, every frame delay 5 centiseconds** (above the 2 cs browsers clamp); **length = the longest animated layer's loop, at most 10 s** (a longer one is cut to 10 s with the notice `photo_export_gif_cut`; shorter loops repeat inside it, and a seam at the end is accepted); loops forever (the NETSCAPE loop count 0); 256-color palette per file with dithering (a GIF limit); flattened onto the canvas background (no transparency); the watermark on every frame (P1-13, same mark and place as PNG and JPG); name `Memix_<yyyyMMdd_HHmmss>.gif` in `Pictures/Memix`, MIME `image/gif`; no metadata.
- **Facts line:** "GIF · 480 × 600 · 3.2 s" on the left, "About 5 MB" on the right (`timecode`; the estimate is frames × pixels × a measured bytes-per-pixel table, refined after the first export on this phone, "About" as P2-08). **Notice** when the estimate passes 10 MB: "This GIF is big and some chat apps refuse files over 10 MB. Try 480 px." (`photo_export_gif_big`; informational, Save stays enabled). Free-space rule as P2-08 (twice the estimate plus 100 MB).
- **Saving:** the tapped button reads "Making GIF… 40%" with a **determinate ProgressBar** under the buttons (the encoder knows its frames; MOTION 2 allows determinate bars); the controls are inert; **Close, scrim and back cancel** with no confirmation, the partial file is deleted and the toast "Export stopped." shows (P2-08). A polite live region announces 25%, 50%, 75%.
- **Exported screen (photo, GIF):** the saved GIF **plays in a loop** on the result (decoded at screen size) with the whole picture as a **tap target for pause and play** (TalkBack action "Pause" / "Play"; the first frame stays still if animations are off). Status pill "Saved to your gallery · GIF · 480 × 600". Share opens the system sheet with `image/gif`. Everything else is P2-08.

### The WhatsApp builder
P2-09 packs are static 512 × 512: **animated stickers are not offered in the builder** (not drawn; no disabled tiles). An animated pack is Later.

## Fixtures for packs (debug builds only; P4-15a)
No catalog packs exist before P4-15b. Until then the debug build supplies **two original fixture packs** (drawn for Memix, `license_type: own`, every license field filled, shown in the sheet like real ones): **Fixture reactions** (8 static PNG on transparency: a thumbs-up, a skull, a heart, a star, a "?" mark, a crown, a tear, a flame, drawn as simple solid shapes) and **Fixture motion** (6 animated: 3 GIF and 3 animated WebP: a spinning star, a bouncing ball, a pulsing heart; one GIF with a frame delay of 0 to test the 100 ms rule, one with a loop count of 1 to test "always loops"; one animated WebP with a variable frame delay). They load through the P4-15a loader, never from the app's resources in a release build. The numbered debug sticker `sticker-loop-check` (12 frames, 100 ms each, the frame number burned in) is the sync reference (Measured).

## States
| State | What shows | Copy |
| --- | --- | --- |
| Main bar | `sticker` icon after Voiceover; absent in an empty project | `tool_stickers` |
| Sheet, My stickers with stickers | Tab row (when packs exist), the Add from your files row, the grid | `stickers_title`, `stickers_tab_mine`, `stickers_import_row` |
| Sheet, nothing at all | P2-14's empty state; in the video editor Add from your files is its action | `stickers_empty_*`, `stickers_empty_body_video` |
| Pack tab | Credit line (if any), the grid; animated tiles with the badge | `stickers_pack_credit` |
| Pack loading (over 300 ms) | Six static placeholder squares | none |
| Pack failed, no cache | The tab shows the message and **Try again** (Secondary) | `stickers_pack_failed_title`, `stickers_pack_failed_body`, `stickers_retry` |
| Offline with cached packs | Cached packs and files show; a sticker whose file is not on the phone: a plain tile with `rotate_right` in `text-muted`; a tap says `stickers_offline` | `stickers_offline` |
| Tile pressed | `hairline` fill, 120 ms | none |
| Landing | Sheet closes; item selected on the lane; bonk | `a11y_sticker_added_at` |
| Sticker selected | The sticker bar; frame and handle on the stage | `tool_*` |
| Fifth animated sticker overlapping | Toast; nothing lands or moves; from the sheet, it closes first | `explain_sticker_animated_max` |
| Import: working | Row pressed; "Adding…" for files over 5 MB | `stickers_import_working` |
| Import failures | Toast; sheet stays | `stickers_import_*`, `stickers_add_failed` |
| Missing file in a project | Not drawn; the lane item says so; toast once per open | P1-05 / P2-03 strings |
| Delete from My stickers | P2-14's Menu and Undo toast; placed copies unaffected | P2-14 |
| Photo editor: animated layer | Plays; Pause control at the stage corner | `anim_pause`, `anim_play` |
| Photo editor: animations off in the system | Opens paused on frame 0 | none |
| Photo export, GIF | Third segment; hint; two sizes; facts with length; no Copy | `photo_export_gif_*` |
| GIF cut to 10 s | Notice in the sheet | `photo_export_gif_cut` |
| GIF big | Notice, informational | `photo_export_gif_big` |
| GIF saving | Label with percent, determinate bar, cancel works | `photo_export_gif_making` |
| GIF failed | The P2-08 notices (space, size, media, other); the segment stays | `export_fail_*`, `photo_export_fail_*` |
| An old draft | No stickers in video; photo stickers unchanged (static) | none |
| Long content (5 languages, 200% font) | Tab names scroll sideways; the import row grows (two lines); empty state and credit wrap; grid has no names; export hints wrap | none |
| First run | No coach mark | none |

## Interactions
- **Tile tap:** lands it (above), one step "Add sticker". **Long-press** on My stickers: Delete menu (P2-14); on a pack tile: nothing.
- **On the stage:** drag, pinch, handle, snapping with SegmentTick, guides, one step per gesture ("Position", "Size and rotation"), as P4-01b. Stage taps play or pause.
- **Keyboard (basic, P1):** Tab reaches the tool, tabs, the import row, tiles; Enter lands; the Menu key opens Delete in My stickers; Escape is back.
- **Haptics:** LongPress for the Delete menu (P2-14), SegmentTick at gesture snaps; nothing at landing.
- **Undo steps:** "Add sticker", "Position", "Size and rotation", "Opacity", "Replace sticker", "Delete", trims, splits as P1-06.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Sheet opens, closes; tab change | 200 ms slide; 200 ms cross-fade | 120 ms fade (instant on Android) |
| Tile picture arrives, tile pressed | 120 ms fade; 120 ms | same |
| Item lands | `duration-bonk`; lane slide 200 ms | no bonk, lane at once |
| **The sticker's own animation** | **Content, not interface motion**: plays with the video (video editor), loops on the canvas (photo editor) and in the saved GIF | In the **video editor** it still plays (the user presses Play). In the **photo editor and the GIF result** it starts **paused** when the system removes animations; the Pause / Play control starts it |
| Pause / Play control | 120 ms fade | same |
| Toasts, status | 120 ms fade | same |

Nothing in the chrome loops (MOTION 2): tiles show the first frame; the progress bar of GIF saving is determinate.

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5; a native read before the closed test). Budgets are characters. Reused: `tool_stickers`, `stickers_title`, `stickers_tab_mine`, `stickers_empty_*`, `stickers_add_failed`, `stickers_deleted`, `a11y_sticker_tile`, `edit_add_sticker`, `tool_replace`, `tool_delete`, `tool_split`, `tool_keyframes`, `tool_move_here`, `tool_duplicate`, `explain_*` of P2-14, `photo_export_*` of P2-08.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_stickers_click` | Add a sticker at the playhead | 40 | Tambah stiker di playhead · Añadir un sticker en el cabezal · Adicionar uma figurinha na posição atual · प्लेहेड पर स्टिकर जोड़ें | Click label; 29 en. Playhead word as `tool_effects_click`; "figurinha" is Brazilian Portuguese for chat stickers (check `layer_name_sticker`) |
| `stickers_import_row` | Add from your files | 24 | Tambah dari file · Añadir desde archivos · Adicionar de arquivos · फ़ाइल से जोड़ें | Row label; 19 en, es 21 |
| `stickers_import_hint` | GIF, WebP or PNG, up to %1$d MB | 40 | GIF, WebP atau PNG, hingga %1$d MB · GIF, WebP o PNG, hasta %1$d MB · GIF, WebP ou PNG, até %1$d MB · GIF, WebP या PNG, %1$d MB तक | %1$d = 15 (a constant); en 31 |
| `stickers_import_working` | Adding… | 12 | Menambahkan… · Añadiendo… · Adicionando… · जोड़ रहे हैं… | Row's second line for files over 5 MB |
| `stickers_empty_body_video` | Add a GIF or an image from your files, or pick a pack above. | 80 | Tambah GIF atau gambar dari file, atau pilih paket di atas. · Añade un GIF o una imagen de tus archivos, o elige un paquete arriba. · Adicione um GIF ou imagem dos seus arquivos, ou escolha um pacote acima. · अपनी फ़ाइलों से GIF या तस्वीर जोड़ें, या ऊपर से पैक चुनें। | Video editor's empty My stickers (the packs sentence only when packs exist; otherwise "Add a GIF or an image from your files." 38) |
| `stickers_pack_credit` | By %1$s | 40 | Oleh %1$s · De %1$s · Por %1$s · %1$s द्वारा | %1$s = the pack's `credit` from the catalog (a name; never translated) |
| `stickers_pack_failed_title` | Couldn't load this pack | 30 | Paket gagal dimuat · No se pudo cargar este paquete · Não foi possível carregar este pacote · यह पैक लोड नहीं हुआ | 23 en |
| `stickers_pack_failed_body` | Check your connection and try again. | 50 | Periksa koneksi lalu coba lagi. · Revisa tu conexión e inténtalo de nuevo. · Verifique sua conexão e tente de novo. · कनेक्शन जाँचें और फिर कोशिश करें। | 36 en |
| `stickers_retry` | Try again | 14 | Coba lagi · Reintentar · Tentar de novo · फिर कोशिश करें | Button |
| `stickers_offline` | You're offline. Connect to use this sticker. | 60 | Kamu offline. Sambungkan internet untuk memakai stiker ini. · Sin conexión. Conéctate para usar este sticker. · Você está offline. Conecte-se para usar esta figurinha. · आप ऑफ़लाइन हैं। यह स्टिकर इस्तेमाल करने के लिए कनेक्ट करें। | Toast; 44 en, id 59 |
| `stickers_import_unsupported` | That file isn't a GIF, WebP or PNG. For a photo, use Overlay. | 70 | File itu bukan GIF, WebP, atau PNG. Untuk foto, pakai Overlay. · Ese archivo no es GIF, WebP ni PNG. Para una foto, usa Superponer. · Esse arquivo não é GIF, WebP nem PNG. Para uma foto, use Sobrepor. · यह फ़ाइल GIF, WebP या PNG नहीं है। फ़ोटो के लिए ओवरले इस्तेमाल करें। | Toast; 59 en, es 70. Use the shipped word of `tool_overlay` in each language |
| `stickers_import_too_big` | That animation is too big. Try one under %1$d MB and %2$d px. | 70 | Animasinya terlalu besar. Coba yang di bawah %1$d MB dan %2$d px. · La animación es demasiado grande. Prueba una de menos de %1$d MB y %2$d px. · A animação é grande demais. Tente uma abaixo de %1$d MB e %2$d px. · एनिमेशन बहुत बड़ा है। %1$d MB और %2$d px से छोटा चुनें। | Toast; %1$d = 15, %2$d = 2048 (constants); en 61 |
| `stickers_import_too_long` | That animation is too long. Memix takes up to %1$d seconds. | 70 | Animasinya terlalu panjang. Memix menerima hingga %1$d detik. · La animación es demasiado larga. Memix admite hasta %1$d segundos. · A animação é longa demais. O Memix aceita até %1$d segundos. · एनिमेशन बहुत लंबा है। Memix %1$d सेकंड तक की लेता है। | Toast; %1$d = 30; en 58 |
| `stickers_import_unreadable` | Memix can't read that file. Try another one. | 60 | Memix tidak bisa membaca file itu. Coba file lain. · Memix no puede leer ese archivo. Prueba con otro. · O Memix não consegue ler esse arquivo. Tente outro. · Memix यह फ़ाइल नहीं पढ़ सकता। कोई और फ़ाइल चुनें। | Toast; 44 en |
| `explain_sticker_animated_max` | Four moving stickers at once is the most. Remove one first. | 70 | Empat stiker bergerak sekaligus adalah batasnya. Hapus satu dulu. · Cuatro stickers en movimiento a la vez es el máximo. Quita uno antes. · Quatro figurinhas em movimento ao mesmo tempo é o máximo. Remova uma antes. · एक साथ चार चलते स्टिकर ही हो सकते हैं। पहले एक हटाएं। | Toast; 58 en, es 65, pt 69 |
| `a11y_sticker_tile_animated` | Animated sticker %1$d of %2$d | 40 | Stiker bergerak %1$d dari %2$d · Sticker animado %1$d de %2$d · Figurinha animada %1$d de %2$d · एनिमेटेड स्टिकर %1$d / %2$d | Spoken name; the plain one is `a11y_sticker_tile` |
| `a11y_sticker_added_at` | Sticker added at %1$s | 40 | Stiker ditambahkan di %1$s · Sticker añadido en %1$s · Figurinha adicionada em %1$s · %1$s पर स्टिकर जुड़ा | %1$s = the time spoken as P1-04 |
| `a11y_state_animated` | Animated, loops | 24 | Bergerak, berulang · Animado, se repite · Animado, em loop · एनिमेटेड, दोहराता है | Appended to an animated sticker item's spoken description |
| `anim_pause` | Pause animation | 24 | Jeda animasi · Pausar animación · Pausar animação · एनिमेशन रोकें | Photo editor control, spoken name; no visible label |
| `anim_play` | Play animation | 24 | Putar animasi · Reproducir animación · Reproduzir animação · एनिमेशन चलाएं | Same |
| `photo_export_anim_first_frame` | Animated stickers show their first frame. Choose GIF to keep them moving. | 80 | Stiker bergerak tampil di frame pertama. Pilih GIF agar tetap bergerak. · Los stickers animados muestran su primer fotograma. Elige GIF para que se muevan. · As figurinhas animadas ficam no primeiro quadro. Escolha GIF para movê-las. · एनिमेटेड स्टिकर पहला फ़्रेम दिखाते हैं। चलते रहने के लिए GIF चुनें। | Hint under Format; 71 en, es 76, pt 74 |
| `photo_export_gif_hint` | Up to 256 colors. Plays in a loop. | 50 | Hingga 256 warna. Diputar berulang. · Hasta 256 colores. Se repite en bucle. · Até 256 cores. Toca em loop. · 256 रंग तक। लूप में चलता है। | Quality slot line; 33 en |
| `photo_export_gif_cut` | Only the first 10 seconds fit in a GIF. | 50 | GIF hanya memuat 10 detik pertama. · En un GIF solo caben los primeros 10 segundos. · Em um GIF só cabem os primeiros 10 segundos. · GIF में पहले 10 सेकंड ही आते हैं। | Notice; 38 en |
| `photo_export_gif_big` | This GIF is big and some chat apps refuse files over 10 MB. Try 480 px. | 90 | GIF ini besar dan sebagian aplikasi chat menolak file di atas 10 MB. Coba 480 px. · Este GIF es grande y algunos chats rechazan archivos de más de 10 MB. Prueba 480 px. · Este GIF é grande e alguns apps de chat recusam arquivos acima de 10 MB. Tente 480 px. · यह GIF बड़ा है और कुछ चैट ऐप 10 MB से बड़ी फ़ाइलें नहीं लेते। 480 px चुनें। | Notice; 71 en, es 86, pt 85 |
| `photo_export_gif_making` | Making GIF… %1$d% | 24 | Membuat GIF… %1$d% · Creando GIF… %1$d% · Criando GIF… %1$d% · GIF बन रहा है… %1$d% | The busy button label; a single `%` after the number (Compose resources) |
| `a11y_gif_pause` | Pause | 12 | Jeda · Pausar · Pausar · रोकें | Custom action on the Exported GIF result (reuse the shipped Pause word) |

Translator notes:
- A **sticker** is a small picture, often moving, that you place on a meme (the same word as `layer_name_sticker`). A **pack** is a named set of stickers from Memix.
- **GIF, WebP, PNG** are file types: never translate. **Animated** means the picture moves in a loop.
- "Moving" in `explain_sticker_animated_max` means animated. Use the everyday word, not a technical one.
- **Hindi:** the interface uses the system Devanagari face; seconds in full ("सेकंड"); no fixed text heights.

## Accessibility
- **Focus and TalkBack order:** the sheet's title takes focus; the tabs (a tab group); the import row; then the tiles in reading order, each a button ("Sticker 3 of 12" or "Animated sticker 3 of 12"; the picture is decorative); closing after a landing moves focus to the **new item** on the timeline and announces `a11y_sticker_added_at`; cancelling returns to the Stickers tool. A pack's credit line is read before its first tile.
- **Sticker items are timeline nodes** (P1-05's `a11y_item`, with `a11y_state_animated` for animated ones) with actions Position (opens the panel), Opacity, Move here, Trim start to playhead, Trim end to playhead, Duplicate, Delete, Replace: the no-drag routes (WCAG 2.5.1, 2.5.7). The stage's handle stays hidden from accessibility services (drag only); the Position panel is the full alternative (P1-10).
- **Moving pictures (SC 2.2.2):** in the video editor motion exists only while the user plays the video. In the photo editor and on the GIF result, the Pause / Play control and the system's "remove animations" setting cover it; the control has a spoken name and a 48 dp target.
- **The covered region:** `coveredByModal` on the timeline and tool bar while the sheet is open.
- **Touch targets:** tiles 104 dp; tabs 48; the import row 56; bar tools 48 dp or more; Pause / Play 48; the lane item is 28 dp in a 32 dp row, the documented non-video exception with the bar and the actions as equal routes.
- **Contrast:** names `text` on `surface` 15.6:1; the credit line `text-secondary` on `surface` 7.1:1; the import row `text` on `surface-raised` 12.4:1 and its hint `text-secondary` 5.7:1; badge and control icons `text` on `scrim` 7.6:1 or more; the item label `on-track` on `track-sticker` 9.0:1; the white ring on `surface` 17.2:1.
- **200% font:** see States. **Reduce motion:** see Motion. **Flashing:** see Limits (catalog packs checked at intake).

## Analytics
`tool_use`, once per committed undo step (never while dragging; never for opening the sheet or a tab). Ids for the PM to confirm:

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A sticker lands in the video | `editor: "video"`, `tool: "add_sticker"`, `value: "mine"`, `"pack"` or `"file"` (just imported), and `pack_id` (the catalog id) when `value` is `pack` |
| `tool_use` | Replace sticker | `tool: "replace_sticker"` |
| `tool_use` | A stage gesture releases, a Position step, an Opacity release | `tool: "transform_sticker"`, `"position_sticker"`, `"sticker_opacity"` |
| `sticker_import` | A file import ends (new event) | `success`, `format` (`gif`, `webp`, `png`), `reason` (failure only: `unsupported`, `too_big`, `too_long`, `unreadable`, `storage`); never a file name or path |
| `export_start`, `export_complete` | A photo GIF export | `editor: "photo"`, `format: "gif"`, `resolution` (480 or 720), `length_s`; on complete `time_ms`, `size_mb` (the PRD rows with a new `gif` value) |

- Moves, trims, splits, duplicates and deletes fire the shared item ids. `add_sticker` in the photo editor keeps P2-14's form (no value). Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `stickers-check` (Requests 7), the fixture packs and the numbered sticker `sticker-loop-check`, at 360 × 640 and 360 × 800. Screenshots in `docs/ux/reviews/P4-12a/` and `P4-12b/`.
1. **The sheet:** My stickers with the import row and tabs at 640 and 800; a pack tab with its credit line and animated badges; Spanish and Hindi at 200%; the empty state in the video editor.
2. **A landed sticker** (still and animated): lane item with picture and label, bar (Position, Opacity, Delete whole), the frame and handle on the stage, the pinned "Add a meme sound" row with five lanes.
3. **Import failures:** each toast (unsupported, too big, too long, unreadable); a successful GIF import landing.
4. **Photo editor:** an animated layer with the Pause / Play control; the control with system animations off (opens paused).
5. **The GIF export:** the Export sheet on an animated canvas (JPG hint, the GIF segment, hint, sizes, facts, no Copy), the big notice, the saving state with percent, the Exported GIF result.
6. **The fifth animated sticker** refused with the toast.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Loops in sync with the timeline.** `sticker-loop-check` (12 frames, 100 ms each, number burned in), placed at 0.5 s on a 6 s clip: in the paused, stepped preview and in the 1080p30 **export**, the frame shown at timeline time *t* is the number ⌊((*t* − 0.5) mod 1.2) / 0.1⌋ at every checked time (0.5, 0.55, 1.0, 1.65, 1.7, 3.2, 5.9 s), preview equal to export. After trimming its start by 0.3 s the frame at *t* = 1.0 s is unchanged; after Move by +1 s the same frames appear 1 s later; after Split at 2.0 s the right piece continues the sequence without a repeat or skip; a seek to a random time shows the same frame as playing to it.
- **Variable delays and "always loops":** the fixture GIF with a 0-delay frame treats it as 100 ms (loop length matches the sum); the GIF with loop count 1 loops for the item's whole length; the animated WebP with variable delays matches its decoded timeline within 1 frame.
- **Landing length:** a 1.2 s loop lands as 3.6 s (n = 3); a 4 s loop as 4 s; a 12 s loop as 10 s; a still as 3 s; with 0.6 s left it ends at the end.
- **Four at once** hold 30 fps or more at 1080p on the owner's phone (release-type build); a fifth refuses; memory: five minutes of looping stays flat (LeakCanary and the memory profiler; decoded frames at display size, never source size).
- **Photo GIF export:** the file opens animated in the gallery and a chat app; 20 fps, every delay 5 cs, infinite loop (checked with a GIF reader or `ffprobe`); length = the longest loop (cut at 10 s); frame 0 equals the PNG export of the same canvas within a mean error of 6/255 after the 256-color palette (the first passing build is pinned and the number tightened); the watermark is on every frame; the 720 px estimate is within 40% of the real size.
- **Each edit is one undo step:** add, replace, gesture, opacity, delete: Undo once each. **Old drafts open;** a draft with the schema bump opens with no stickers in video; **leaks:** LeakCanary: open the sheet, switch tabs, land, import, delete, close the editor; repeat 5 times.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| Sticker items | `StickerItem(durationUs, source, transform, keyframes)` gains **`opacity: Float = 1f`** and **`trimInUs: Long = 0`** (the loop phase offset). One `schemaVersion` bump with an identity migration; old-draft check |
| Animation info | `MediaRef.animation: AnimationInfo?(frameCount, loopDurationUs)` measured at import and for catalog stickers (like `durationUs`), null for stills; unknown on old copies: read from the file when the editor opens it. Frame delays under 20 ms count as 100 ms |
| The store | `stickers/s<epoch>_<id>.<png|gif|webp>`, newest first by name as P2-14; the extension carries the type; animated-ness read from the file header (a WebP's VP8X flag). Import writes here and the project copy together |
| Limits | `StickerAnimationCatalog` (15 MB, 2048 px, 300 frames, 30 s, 4 at once, 3 s landing target, 10 s landing cap, GIF 20 fps / 5 cs / 480 and 720 / 10 s) in commonMain |
| Edits | `addStickerItem`, `replaceStickerItem`, `setStickerOpacity` (+ the shared move, trim, split, duplicate, delete, `setLookAt` keyframes); the 4-animated-at-once rule inside `commit` (as the effects overlap rule) |
| Frame lookup | One pure `stickerFrameAt(item, timeUs)` used by preview and export (the time base above) |
| Photo | `StickerLayer` gets the same `AnimationInfo` through its `MediaRef`; no new layer fields; the animated flag drives the control and the GIF segment |
| Catalog | `sticker_packs` and `stickers(animated)` exist (TECHNICAL_DESIGN); the app's repository reads them (24 h refresh). Credit text is the shared `credit` column |

## Requests to the principal mobile engineer
1. **Start-of-ticket checks:** how Media3 (1.11.x) can draw an animated bitmap per frame in `Transformer` and `CompositionPlayer` (a per-frame overlay such as a `TextureOverlay` that picks the texture for `presentationTimeUs`) and how decoded frames are kept (a bounded cache sized to the displayed size, not the source). Report if the sticker lane can't share the caption overlay's path. Android decoders: `ImageDecoder` (animated GIF and WebP from API 28) vs a library; our minimum is API 29 so `ImageDecoder` is available.
2. **Frame lookup** exactly as the time base; never wall-clock; a frame cache that survives seeks.
3. **The sheet** lifted from P2-14 into `:core:ui` with a source list (My stickers + packs) as P2-14 asked, the Tabs row, the import row (document picker, no permission), pack fetch and cache through the catalog use cases (features call use cases only); tiles' first frames from the scene decoder at about 128 px.
4. **Import use case:** validate (type, size, pixels, frames, length), measure `AnimationInfo`, copy to the store and the project in one atomic step; failure maps to the five reasons.
5. **Sticker lane, bar, stage:** reuse P4-01b's gestures and panels; the 4-at-once rule; `mediaCopyPaths()` / `withMediaCopyPaths()` cover the new items.
6. **Photo (12b):** canvas playback clock, the Pause / Play control and the system-animation check; GIF encoder (a pure-Kotlin or platform encoder: **no GPL/LGPL library, no FFmpeg**; verify the license of any encoder), determinate progress, cancel, `Pictures/Memix` through MediaStore with `image/gif`; the Export sheet changes; the Exported GIF result; the WhatsApp builder's filter.
7. **Debug scenes** `stickers-check` (several still and animated stickers, a fifth animated one) and `sticker-loop-check`; the fixture packs through P4-15a; frames saved to `docs/qa/references/P4-12-stickers/`.
8. **Strings** in the five files; **analytics:** `EditorTool` values `replace_sticker`, `transform_sticker`, `position_sticker`, `sticker_opacity`, the `sticker_import` event, the `gif` export value.

## Tokens and icons
- **No new color, size, radius or motion token.** Reused: `grid-tile-min`, `nav-row-height`, `badge-size`, `icon-small`, `track-sticker`, `duration-bonk`, `scrim`.
- **New content group** `stickerAnimation` in `design/tokens.json` and `design/system/tokens.json` (limits, landing, loop rules, GIF export constants). Kotlin: `StickerAnimationCatalog` next to `PhotoCatalog`.
- **No new icon:** `play` and `pause` (badge and control), `folder`, `sticker`, `rotate_right` (retry), the bar's tools all exist.
- **Pointer edits made with this spec:** DESIGN_SYSTEM (a stickers-in-video paragraph), the Timeline README (the sticker item look), the ToolBar README (sticker bar).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Which files can a user bring, and how big?**
- *Problem:* a GIF can be 100 MB and 4000 px; the phone must decode it at 30 fps beside a video.
- *Considerations:* **A. GIF, WebP and PNG; 15 MB, 2048 px, 300 frames, 30 s** (recommended; applied): covers nearly every meme GIF (most are under 8 MB); a refusal explains the limit. **B. Stricter (5 MB, 1024 px):** safer for slow phones, refuses many real GIFs. **C. No limits:** one huge GIF can freeze or crash the editor.
- *Reverse:* constants.

**2. Does a picked file land on the video at once?**
- *Problem:* P3-07 did not auto-add an imported sound; a sticker could do either.
- *Considerations:* **A. Land at once and save to My stickers** (recommended; applied): the pick is the intent, 3 taps total, no audition needed. **B. Save only; the user taps the new tile:** one more tap, safer if the pick was a mistake (Undo covers it).
- *Reverse:* a flow change, no migration.

**3. At most four moving stickers at once?**
- *Problem:* each animated sticker decodes a frame per output frame.
- *Considerations:* **A. Four** (recommended; applied; a constant): enough for a busy meme; a toast when a fifth is wanted. **B. Six:** more freedom, risk to 30 fps on mid phones. **C. Two:** safest, restrictive.
- *Reverse:* one constant.

**4. Photo editor: do animated stickers play automatically?**
- *Problem:* an auto-looping picture needs a pause control (WCAG 2.2.2) and respect for "remove animations".
- *Considerations:* **A. Plays, with a Pause / Play control at the stage corner; paused when the system removes animations** (recommended; applied): the user sees the meme as it will export. **B. First frame only in the editor, motion only in the exported GIF:** no control to build, but "what you see" is wrong. 
- *Reverse:* hide the control.

**5. GIF export limits.**
- *Problem:* GIFs get huge; chat apps refuse big ones.
- *Considerations:* **A. 480 or 720 px, 20 fps, up to 10 s, no Copy, a size notice over 10 MB** (recommended; applied). **B. Add 1080 px:** sharper, but files over 30 MB. **C. Add a frame-rate choice:** more control, a longer panel.
- *Reverse:* constants and a segment.

**6. Opacity for stickers in video.**
- *Problem:* the P4-02b table says "if the sticker has opacity"; the video model has none.
- *Considerations:* **A. Add it (10% to 100%, keyframable to 0)** (recommended; applied): a sticker that fades out is a common meme move; it rides in the same schema bump. **B. None:** one field less, fades impossible.

## Proposals for the PM (not decided here)
1. **Split P4-12 into 12a (video) and 12b (photo animation and GIF)** as the table above.
2. **P2-09:** the WhatsApp builder lists static stickers only (a filter; animated packs Later).
3. **P2-10 (Make it a video meme):** an animated sticker in the top run of the photo's layers is **lifted to the video's sticker lane** (like captions) and plays; any other animated sticker is baked as its first frame, with the existing one-time note. Recommended.
4. **P4-15 content rules:** every animated catalog sticker is checked for the flash limit (3 flashes a second, SC 2.3.1) at intake; a pack has a credit line when its license needs one (CC BY); a "Report pack" action (the `reports` table already allows `sticker_pack`) joins P4-15a.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Moving content that starts automatically and lasts over 5 seconds needs a way to pause, stop or hide it | Guidance | [WCAG 2.2 SC 2.2.2](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html) |
| Content with more than three flashes in any one second can trigger seizures | Guidance | [WCAG 2.2 SC 2.3.1](https://www.w3.org/WAI/WCAG22/Understanding/three-flashes-or-below-threshold.html) |
| GIF frame delays of 0 or 1 centisecond are shown as 10 centiseconds by browsers (so 20 ms or less counts as 100 ms) | Assumption (de facto behavior; verify with the fixture at build) | none |
| `ImageDecoder` decodes animated GIF and WebP from Android 9 (API 28) | Guidance (API docs; verify at the start) | [Android `ImageDecoder`](https://developer.android.com/reference/android/graphics/ImageDecoder) |
| A document picker hands over one file with no storage permission | Guidance | [Android: open files using the Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) |
| A sticker copy inside the project keeps drafts safe when the store changes | Reasoned from the media rules | `P2-14-sticker-picker.md`, `docs/TECHNICAL_DESIGN.md` |
| Chat apps refuse or recompress big GIFs (the 10 MB notice is a conservative line) | Assumption (product knowledge; limits vary by app) | none |
