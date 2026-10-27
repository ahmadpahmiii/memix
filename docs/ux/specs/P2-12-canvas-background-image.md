# Canvas background image, both editors · P2-12

Adds the PRD's "background color, blur **or image**" (video canvas) and "background color **or image**" (photo canvas) (owner, 21 Oct). It adds one option to the two Canvas panels and one drawing rule; it doesn't repeat what these specs own:
- `P1-11-canvas.md`: the video Canvas panel, the Background row, Fit and Fill, the rendering rules, "gaps and missing clips are black".
- `P2-01-photo-editor-shell.md`: the photo Canvas panel and the one scene-drawing function.
- `P1-02-gallery-picker.md`: the picker, the copy, the import sheet, "Not enough space".
- Sibling: `P2-13-image-layers.md` reuses this ticket's pick flow for a different destination.

## Job
Put a picture behind the meme: a landscape clip in a 9:16 frame over a photo instead of black bars, or a photo cut-out sitting on a backdrop. **Done when** (TICKETS): the picked image shows behind a Fit clip in the video editor and behind the layers in the photo editor, looks the same in preview and export, and an old draft still opens after the migration.

**The one blue action:** none. The Image swatch is a setting, like Blur. Nothing in this ticket is blue.

**Fast path, with tap counts:** the ratio pill (1), the Image swatch (2), a photo in the picker (3). Replacing it: the pill, Replace, a photo. Removing it: the pill, Remove.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Picker, then import | The Image swatch with no picture yet, or Replace | The picture lands, or Cancel, back or nothing picked (nothing changes) |

- **The picker:** the same system picker as P1-02: **images only, one item**, no permission, no explainer. Animated GIF and WebP go in as their first frame. Phones without it get the file chooser. Taps are ignored while it launches.
- **After the pick:** P1-02's import runs: the 300 ms rule, the "Adding your photo" sheet with percent and Cancel, "Not enough space" with Free up space, "None went in" with Pick again (photo wording, `P2-01` Copy), no picker app. The picture is copied into `files/media/<project id>/` and is never uploaded.
- **It isn't placed as a clip or a layer.** It becomes the canvas background.
- **In an existing project it is one committed edit** (one undo step, "Background"), not a first save. Cancelled, failed or nothing picked: nothing changes, no event, and the Canvas panel is still open under it.
- **The panel stays where it was.** While the picker and the sheet are open the editor is covered; when the picture lands the Canvas panel shows the new look (the canvas above it already updated) and the Image swatch selected.
- **Kept:** the picture is in the project (autosave, undo). A **replaced or removed picture's file stays** in the draft's folder while Undo can bring it back; nothing cleans unused copies in a saved draft yet (a storage cost only, known since P1-06).

## Layout
No board for the panels: the P1-11 panel and the photo panel in the P2-01 spec, plus the changes below.

### The Image swatch
In the Background row's horizontally scrolling Swatch row (`design/system/components/Swatch/README.md`, Image variant added with this ticket):
- **Video panel:** **Blur · Image · White · Black · …** (the ten palette colors). Image comes right after Blur: both are "a picture behind the clip", the colors follow.
- **Photo panel:** **Image · White · Black · …** (no Blur there).
- **Look**, a `swatch-size` circle in a `touch-target` square like every swatch:
  - **No picture yet:** a `surface-raised` circle with the `photo` icon at `icon-small` in `text-secondary` (5.7:1), as the Blur swatch carries `blur`.
  - **A picture set:** the picture itself, center-cropped into the circle. This is the swatch's whole meaning, so the user sees what is behind the meme without leaving the panel. Selected: the white ring (17.2:1 on `surface`).
  - **The picture's file is missing:** a `surface-raised` circle with the `photo` icon in `text-muted`, **with the white ring** (it is the current background), and the explanation line below.
- **A tap on the swatch:** with no picture, it opens the picker at once (the swatch isn't selected until a picture exists; if the pick is cancelled nothing changes). With a picture (selected), it does nothing, as every selected swatch; Replace and Remove are right below.

### The image row
Directly under the swatch row, only while the background is an image (set or missing), in the Position panel's row pattern (P1-10): a label **Image** (`body` `text-secondary`) at the start, two Secondary buttons at the end, `space-2` apart: **Replace** and **Remove** (`touch-target` tall). At 200% font they wrap under the label, and Remove under Replace if the pair is still too wide; nothing is cut.
- **Replace:** opens the picker. A new picture is a **new file**; the old one stays for Undo (Media copies are never changed in place). One step "Background".
- **Remove:** the background goes back to the editor's default color (video black, photo white) in one step "Background". The file stays for Undo.
- **Picking another background** (a color, or Blur in the video editor) also drops the picture from the project; Undo brings it back as one step. The Image swatch then shows its "no picture yet" look, and tapping it asks for a picture again. (The model holds one background at a time; keeping a hidden picture would be a second field and a schema change. A user who toggles Blur ↔ Image will pick again: Assumption that this is rare, tested at the P2 test.)
- **The hint line** (`canvas_background_hidden` in the video panel, `photo_background_hidden` in the photo panel) applies to an image exactly as it does to a color: when nothing would show the background, the swatches still work and the line explains.

### Panel height
The Image row adds about 64 dp (the row and its gap) to each panel while an image is the background, so the video panel reaches about 416 dp and the photo panel about 300 dp. Both already scroll inside their region (P1-11; P2-01 caps a panel at 40% of the window in the photo editor).

## How it draws (preview and export match)
These rules are part of every saved draft's look (`design/tokens.json` → `canvas.background.image`).

| Rule | Value |
| --- | --- |
| Fit | **Cover**: scaled uniformly to cover the whole canvas, centered, the overflow cropped. No pan, zoom, rotate or flip controls in v1 (new scope; Later) |
| Orientation | As shown: the file's EXIF turn applied (`MediaRef.pixelSize` is the size as shown) |
| Look | Full brightness, no blur, no dimming, no tint, no scrim |
| Place in the stack | The bottom: under every clip and every layer |
| Video, behind a Fit clip | The clip draws at its Fit size over the picture. A **Fill** clip hides it; a clip within 1% of the canvas's shape has no bars, so none shows (P1-11: `backgroundShows()` treats Image like Blur and the colors) |
| Video, gaps and missing clips | **Black, whatever the background**, as P1-11 says for the colors and Blur: a gap or a missing clip is an error state, and black keeps P1-04's "plays black" true. (A main track has no gaps by construction; this is the defensive case.) |
| Photo | Under every layer. A layer that covers the frame hides it (the hint line says so) |
| A picture smaller than the canvas | Cover scales it up. No warning: the user sees the result on the canvas above the panel |
| A picture whose file is missing | Draws **black** behind everything, in preview and export alike (the colors have no "missing"; black is the error look for a missing clip) |
| Decode size | Once, at the canvas's drawn size for the preview; once per export at the output size, never the original per frame. 2160 × 3840 is about 33 MB as a bitmap |
| Preview size | **Unchanged.** Image is a still, not a per-frame blur, so the video preview keeps its normal size (only Blur drops it to a 720 px shorter side, P1-11) |
| Ratio change | Nothing moves: the picture re-covers the new shape. Layers follow P1-11 and P2-01 |

## States
| State | What shows | Copy |
| --- | --- | --- |
| Default, no picture | The Image swatch with the `photo` icon, unselected | `canvas_background_image` |
| Picker open, copying, no space, failed | P1-02's sheets with photo wording; the panel unchanged under them | `import_*` |
| Picture set | The thumbnail swatch, ringed; the image row; the canvas shows the picture | `canvas_image_label`, `tool_replace`, `canvas_image_remove` |
| Picture set, nothing would show it | As above plus the hint line | `canvas_background_hidden` or `photo_background_hidden` |
| Picture's file missing | The icon swatch, ringed; the image row; a line under the swatches; the canvas draws black; a toast once per open | `canvas_image_missing`, `canvas_image_missing_toast` |
| Replaced | The swatch and the canvas show the new picture; one undo step | `edit_canvas_background` |
| Removed | The editor's default color is selected; the Image swatch is back to "no picture yet" | none |
| Another background chosen over the picture | As Removed; Undo brings the picture back | none |
| Reorder mode, typing, a sheet open (video) | The panel isn't reachable (P1-11 rules apply) | none |
| Long content (5 languages, 200% font) | The image row wraps under its label; the missing line wraps; nothing is cut | none |
| Offline, permission | Don't apply: the picker needs no permission and everything is on the phone | none |
| First run | Same as every run | none |

## Interactions
- **Swatch tap, Replace, Remove, Undo and Redo:** as above. After an undo the panel's swatches, ring and image row show the restored values (ToolPanel rule); if undo changes the picture, the thumbnail swatch updates.
- **A pick that lands while the panel is closed** can't happen: the picker is opened from the panel and the panel is still the editor's front layer when it returns.
- **Haptics:** none. **The preview while the panel is open:** as in P1-11 (video: a tap plays or pauses; photo: canvas touches do nothing).
- **Back during the import sheet:** Cancel's rules (P1-02): nothing is added.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| The picture appears on the canvas | At once, like every canvas choice (P1-11) | same |
| Image row appears or goes | 120 ms fade; rows below move at once | same |
| Swatch thumbnail appears | 120 ms fade | same |
| Sheets and toasts | As P1-02 and P1-04 | same |

## Copy
English source; machine drafts for id, es, pt and hi until P5. Budgets in characters. Reused: `import_*`, `import_title_photo`, `import_none_*_photo` (P2-01), `tool_replace` (P1-16), `edit_canvas_background`, `canvas_background`, `canvas_background_hidden`, `photo_background_hidden`, `panel_done`.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `canvas_background_image` | Image | 16 | Spoken name of the Image swatch (it has no visible label, like the colors). A picture the user picks to sit behind the meme |
| `canvas_image_label` | Image | 16 | Row label above Replace and Remove |
| `canvas_image_remove` | Remove | 18 | Takes the picture away; the background goes back to a plain color |
| `canvas_image_replace_a11y` | Replace image | 24 | Spoken name of the Replace button; starts with its visible label |
| `canvas_image_remove_a11y` | Remove image | 24 | Spoken name of the Remove button |
| `canvas_image_pick_a11y` | Pick an image | 24 | Click label of the empty swatch: "Double-tap to pick an image" |
| `canvas_image_missing` | The image is missing. Pick another one or remove it. | 70 | Line under the swatches |
| `canvas_image_missing_a11y` | Image, file missing | 30 | Spoken name of the swatch when its file is gone |
| `canvas_image_missing_toast` | The background image is missing. Tap %1$s at the top to replace or remove it. | 90 | Once per open. %1$s = the ratio as shown on the pill ("9:16", never translated) |
| `a11y_canvas_image_set` | Background image set | 30 | Announced politely after a pick lands |
| `a11y_canvas_image_removed` | Background image removed | 30 | Announced after Remove |

Translator notes:
- **"Image"** here is a picture from the phone's gallery, used as a backdrop. Use the word for a photo or picture, the same in the swatch, the row and the messages. It isn't the app's "Photo" layer name, but it may share the word.
- **Replace** is the same word as the clip tool (`tool_replace`), with the same note about sitting beside similar words.
- **Placeholders:** `%1$s` is a ratio like "9:16"; keep it as is.

## Accessibility
- **TalkBack order in the panel** (P1-11's list): title → Done → Ratio group → Custom rows → (video) Clips group → **Background group** → the image row (label, Replace, Remove) → the hint or missing line. The Background group is a single-choice group: each swatch reads its name and position ("Blur", "Image", "White"; "1 of 12" counts Image).
- **Image swatch:** "Image", "selected" when it is; with no picture, its click label is "Pick an image". When missing it reads "Image, file missing". The state description stays "selected" or not: the meaning goes in the name (P1-11 review M2).
- **Replace and Remove** are buttons whose names start with their visible labels (SC 2.5.3). After the picker or the import sheet closes, focus returns to the swatch or button that opened it (P1-16's rule; a TalkBack check on the device).
- **Announcements:** after a pick lands "Background image set"; after Remove "Background image removed"; toasts as always. Nothing takes focus.
- **Touch targets:** the swatch is a 48 dp target; Replace and Remove are at least 48 dp tall.
- **Contrast:** Replace and Remove `text` on `surface-raised` 12.4:1; the row label `text-secondary` on `surface` 7.1:1; the missing line `text-secondary` 7.1:1; the icon swatches 5.7:1 and 4.6:1; the ring 17.2:1.
- **200% font:** the row wraps under its label; nothing is cut; the panel scrolls.
- **Reduce motion:** see Motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A picture lands as the background (first pick or Replace); a Remove or another background (once per committed step) | `editor`: `video` or `photo`, `tool`: `canvas_background`, `value`: `image` (a picture), `blur` or `color` (never the color itself, never anything about the picture) |

- **`value: image` is a new value of an approved parameter on an approved tool** (owner, 21 Oct: `value` for the canvas tools). It reveals nothing about the picture. The PM updates PRD → Metrics (`blur`, `color` or `image`) at the ticket check.
- Remove fires `canvas_background` with `value: color`, since the result is a color. A cancelled pick, a failed import and a tap on the selected swatch fire nothing.
- Analytics stay off until consent (P5-01).

## QA compares
1. **Video Canvas panel, picture set** at 360 × 800: Blur, the thumbnail Image swatch ringed, the colors; the image row (Image · Replace · Remove); a 16:9 clip in 9:16 over the picture.
2. **Photo Canvas panel, picture set:** Image first in the row (no Blur); a smaller photo layer over the picture on the canvas above.
3. **A patterned picture** (a checkerboard or a grid, so any shift shows) behind a Fit clip: the preview frame and the exported frame side by side.
4. **Missing picture:** the icon swatch ringed, the line, the black canvas, the toast.
5. **Fill hides it:** the video with Fill and the hint line.
6. **Hindi at 200% font**, 360 × 640: the panel scrolled to the Background group and the image row wrapped.

**Measured: the Done when** (the PM's checklist for P2-12 is in `docs/pm/acceptance-log.md`; the designer adds):
- **Preview = export:** export `canvas-check` at 1080p30 with a patterned picture behind a Fit 16:9 clip in 9:16, and at 1:1. Pull three frames; compare with the preview screenshots cropped to the frame and scaled to the export width. Pass: the same crop of the picture within 1% of the width, the same brightness (no dimming), the picture absent behind a Fill clip, black in any gap. The 1080p frames go in `docs/qa/references/P2-12-background-image/`.
- **Photo half:** with the P2-01 render hook, render a scene with the picture behind three layers and compare with the screenshot as P2-01 does; the final export check waits for P2-08.
- **4K:** export at 2160 × 3840 with the picture set; report peak memory (the PM's must-know 4).
- **Old drafts:** open a draft saved at schema 2 and one at schema 3 after the bump: they load with their backgrounds unchanged.
- **Media rules:** set a picture, close, kill, start again: it is still there (the cleaner runs at start). Duplicate the draft, delete the original: the copy still shows its picture.
- **Undo:** pick, Replace and Remove each undo and redo exactly; the old file survives while Undo can use it.
- **Leaks:** LeakCanary, 5 cycles with the picture set, replaced and removed in both editors.

## Model
| Need | Model |
| --- | --- |
| The picture | `CanvasBackground.Image(source: MediaRef)`, a third subtype with its own stable `@SerialName("image")`, beside `color` and `blur`. A new subtype leaves old JSON untouched |
| Version | Bump `CURRENT_SCHEMA_VERSION` 3 → 4 (P2-01 does 2 → 3) with a version-only `ProjectMigration(fromVersion = 3)` |
| Media | `Project.mediaCopyPaths()` and `withMediaCopyPaths()` include `canvas.background`: otherwise the cleaner deletes the picture at the next start and a duplicate shares one file with its original |

Defaults after Remove: video black, photo white (`design/tokens.json` → `photo.defaults`, `canvas.defaults`).

## Requests to the principal mobile engineer
I don't edit code, so these are requests; the PM's must-knows for P2-12 carry the rest (model, media, drawing, memory, hand checks).
1. **One "pick one photo for an existing project" path** (picker, import sheet, space check, copy), parameterized by its destination: this ticket's background, and P2-13's new layer. Don't write it twice.
2. **Video drawing:** a still drawn **behind** Fit clips in preview and export, from the same plan. Verify at the Media3 tag how to put a still under the clips (an image sequence as the bottom layer, a custom compositor, or the existing `CanvasFraming` draw). If it needs a custom shader or can't hold 30 fps, tell the designer and the PM before building a workaround, as P1-11 did for Blur.
3. **Photo drawing:** the scene function draws the background first (P2-01 request 5).
4. **Swatch:** the Image variant in `:core:designsystem` and the debug catalog, in its three looks (no picture, picture, missing). The picture swatch uses a thumbnail from the thumbnail reader, never the original.
5. **Analytics:** `canvas_background` with `value: image`; Remove sends `color`.
6. **Hand checks to list** (the PM's must-know 7), with the designer's QA compares above (not a copy in the handoff).

## Proposals and questions for the PM
1. **`value: image`** is yours to confirm as a clarification (above); Remove sends `color`.
2. **Gaps stay black** for Image, as P1-11 says for the colors and Blur. A picture that showed through a gap would hide a real error.
3. **The editors' default colors differ:** video black (P1-11), photo white (P2-01). Remove returns each editor's own.
4. **Cover only; no pan or zoom.** Repositioning the background picture is Later, no ticket. A user who needs a different crop picks a different photo.
5. **Checks:** the photo half of "looks the same in preview and export" runs on P2-01's render hook now and on P2-08's exporter at QA, as your ready check says.
6. **Size watch:** if the video drawing needs more than a session (the still behind Fit clips), the split you proposed holds: the video side needs only P1-11, the photo side needs P2-01.

**Later, no ticket:** pan and zoom for the background picture; remembering a hidden picture while another background is tried.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| InShot's Canvas offers a background color, blur or image behind a clip that doesn't fill the frame | Assumption-level (third-party tutorials) | [CapCut's InShot tutorial](https://www.capcut.com/resource/inshot-tutorial), [InShot overview](https://discourse.weareopen.coop/news/inshot-your-go-to-video) |
| `PickVisualMedia` with `ImageOnly` picks one image without a permission | Guidance (API reference) | [PickVisualMedia](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickVisualMedia), [Android photo picker](https://developer.android.com/training/data-storage/shared/photopicker) |
| A visible label is part of the accessible name | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Non-text indicators need 3:1 against adjacent colors | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
