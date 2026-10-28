# Phase 2 · hand checks for the owner

The engineers' device steps for each ticket's "Done when", copied from their handoffs. Cloud sessions have no emulator, so nothing below has run on a device yet: these are the checks that move a ticket from "Building (hand check pending)" to Built. QA folds them into the device checklist.

Run from the repo root with a phone or emulator attached. For the numbers use the benchmark build (R8-minified, signed with the debug key), never debug: `./gradlew :androidApp:installBenchmark`. For the leak checks use debug: `./gradlew :androidApp:installDebug` (LeakCanary is in debug only). The photo scenes need no pushed files: the pictures are generated on first use under `files/debug-media/photo-scenes/` (the first open takes a few seconds for the 12 MP ones).

## P2-01 · photo editor shell: canvas, layers, gestures, layers panel

Spec: `docs/ux/specs/P2-01-photo-editor-shell.md`. Screenshots of every state (a desktop render, not a phone): `docs/ux/reviews/P2-01/`.

### Hooks

```bash
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-layers-10   # ten visible image layers plus a hidden 11th
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-empty       # blank 1:1 white canvas, no layer
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-missing-media
adb shell am start -S -n app.memix/.android.MainActivity --es memix.renderScene photo-layers-10 --ei memix.renderWidth 1080   # after opening it once
adb logcat -d -s MemixSceneRender:* MemixEditorCheck:*     # expect: renderScene: <path>.png (10 pictures, 1080 x 810)
adb exec-out run-as app.memix cat cache/scene-renders/photo-layers-10-1080.png > render.png
```
The photo-shape pictures (4:3, 3:2, 1:1, 9:16, 6:13, 3:1) are in `files/debug-media/photo-scenes/shape-*.jpg`; copy them to the gallery (`adb exec-out run-as app.memix cat files/debug-media/photo-scenes/shape-4x3.jpg > shape-4x3.jpg`, then `adb push` into `/sdcard/DCIM/MemixTest/` and a media scan) to pick them from Home or Create → Photo meme.

### Done when 1 · a 10-layer scene stays smooth while dragging (numbers)

Benchmark build, the owner's phone. Report the phone model, Android version, median and worst janky %.
1. `adb shell am start -S ... --es memix.openEditor photo-layers-10`, tap the top visible layer (MID 4) to select it.
2. Three runs: `adb shell dumpsys gfxinfo app.memix reset`, `adb shell input swipe 200 900 900 1300 3000` (a 3 s drag across the canvas), `adb shell dumpsys gfxinfo app.memix | grep -E "Janky|Total frames|50th|90th"`. Pass: janky frames under 5% in each run.
3. A 60 fps screen recording (`adb shell screenrecord --bit-rate 20000000 /sdcard/pinch.mp4`) of a two-finger pinch and turn on the selected layer and of the handle: no stutter, the value bubble and guides follow.
4. `adb shell dumpsys meminfo app.memix | grep -E "Graphics|Native Heap|TOTAL"` with the scene open and after a drag: report Graphics and Native Heap.

### Done when 2 · no leaks (debug build)

Five cycles of: open `photo-layers-10`; drag, pinch, turn; Hide, Show, Lock, Unlock; move the opacity slider; open the Layers panel, drag a row, close it; change the ratio and a color; Undo; close the editor; wait 10 s on Home. Pass: no LeakCanary notification and `adb logcat -d -s LeakCanary:*` has no leak. Report `dumpsys meminfo app.memix` Graphics and Native Heap after cycle 1 and cycle 5: growth that doesn't level off is a finding.

### The rest of the spec's list

- **Start flow:** Home → Photo meme and Create → Photo meme open the picker at once (photos only, one item); a quick copy goes straight to the editor, a slow one shows "Adding your photo" with the percent and no "1 of 1"; Cancel and a backed-out picker leave no draft; "Not enough space" and "Couldn't add your photo" read as spec'd. Canvas from the photo's shape: 4:3 → "4:3" (Custom), 3:2 → "3:2", 1:1 → "1:1", 9:16 → "9:16", the 6:13 screenshot → "6:13", 3:1 → "31:13" with the photo whole; no sliver of background on any edge except the panorama's bars.
- **Ratio sequence** on `photo-layers-10`: 4:3 → 9:16 → 16:9 → 1:1 → custom 9:21 → back to 4:3. Every layer that started fully inside stays fully inside (margin 0 for images), the partly-off layer keeps its center and scale, the base photo returns exactly, one Undo restores the previous ratio and every layer; the toast "Layers moved to stay inside the frame." only when something moved.
- **Locked layer:** lock the base photo, drag a layer over it (the top one moves, the locked one never does); drag the locked one (it stays, one toast per touch); pinch with it selected (toast); Position, Opacity, Delete, Move up and down each explain; Hide and Show, Duplicate (the copy is unlocked) and Unlock work. A locked layer whose file is missing (`photo-missing-media`): Delete explains until Unlock.
- **Reopen:** kill mid-edit (`adb shell am force-stop app.memix`), reopen from Drafts (photo badge, the base photo as the cover): locked, hidden and faded states are the same. Open a draft saved before the update (a Phase 1 video draft from `main`'s build, and a photo draft after the schema bump): both open.
- **Empty draft:** delete the only layer, Close: the draft is gone from Drafts and its folder is removed at the next start. Undo before closing brings the layer back. `photo-empty` closed with no layer leaves nothing.
- **Duplicate and media:** duplicate a layer, kill the app, restart: both layers still draw (the cleaner keeps the shared file); duplicate the draft in Drafts: one file copied, both layers draw in the copy.
- **Preview = render:** screenshot the canvas of `photo-layers-10` (cropped to the frame) and compare with `render.png` scaled to the same width: positions within 1% of the width, sizes within 2%, rotation within 1°, the same opacity, the hidden layer absent, the same stacking. Reference frames: `docs/qa/references/P2-01-photo-scene/`.
- **Photos:** a 12 MP photo decodes at the size it is drawn at (watch `Graphics` stay near the table above, not 480 MB); zoom a layer to 500%, release: it sharpens once; panels opening and closing never flash or re-decode (the picture doesn't blink).
- **Responsiveness:** a committed edit shows within 100 ms; opening from Drafts shows the first frame in under 1 s (`adb logcat -s MemixPreview`).
- **Locked bar (design review M1):** select the locked layer on `photo-layers-10`; 360 x 800, 360 x 640 and Spanish at 200%: Unlock is the first tool beside Close layer tools and fully visible; Lock then Unlock again: the bar returns to its start each time; a locked layer with a missing file (`photo-missing-media`, after locking it) reads Unlock, Delete, Position, Opacity; TalkBack reads the same order.
- **Layers panel tight mode (M3):** with a panel cap under 300 dp (360 x 640) or font scale 1.3 or more, the Opacity row is the list's first item; the header keeps the Move buttons. Check 360 x 640 at 100% (at least two whole rows), Hindi and Spanish at 200% (at least one whole row with its lock and eye buttons), the selected row in view on open, the Opacity item scrolls with the list and the slider works there, and a row lifted and dragged lands on the right place with the Opacity item above it (the drop index must not shift by one). The Opacity tool on the selected-layer bar is the way back to the slider.
- **Canvas panel from a photo start (P3, P5):** is the Background row discoverable at 360 x 800 and 360 x 640 with Custom selected? Does the hint line show for the 4:3 photo and go after a ratio change that leaves visible background?
- **Accessibility:** TalkBack focus order top bar → pill → Undo → Redo → canvas (layers front to back, "Photo, layer 1 of 11") → tool bar or panel; actions on a layer node and on every Layers row (Position, Opacity, Lock or Unlock, Hide or Show, Duplicate, Delete, Move up, Move down); each row is one stop; a closed panel returns focus to its tool (not built yet: report what happens). 200% font and Hindi on a 360 × 640 emulator: the screenshots `docs/ux/reviews/P2-01/08-*`.

### Re-run because P2-01 moved P1-10 and P1-11 code into `:core:ui` (no behavior change intended)

- **P1-10 text in the video editor:** tap selects a caption, a drag moves it, the handle sizes and turns, two fingers pinch and turn, snaps tick (center lines, 0/90/180/270°, 100%), the value bubble, the frame clipped at the stage edge, back during a gesture puts the caption back, TalkBack actions Style and Position, the Position panel (Size ladder, Rotation, Move arrows repeat when held and commit once, Center, the limit toasts), the Style panel's palette.
- **P1-11 canvas in the video editor:** ratio tiles, Custom steppers (hold to repeat, the limit toasts), Fit and Fill, Blur and the color swatches, the hint line, a ratio change keeps captions inside.
- **Editor frame:** the save banner and "Changes saved", the unsaved-changes sheet, the frame edge while the Canvas panel is open or a caption gesture runs, Undo and Redo toasts and their names ("Undo: Ratio").
- **Import:** Create → Video meme still says "Adding your media" with "1 of N"; Add media and Replace unchanged.

## P2-12 · canvas background image, both editors

Spec: `docs/ux/specs/P2-12-canvas-background-image.md` (its "QA compares" and "Measured: the Done when" lists are the checks; this section only adds the hooks and the engineer's own steps). Screenshots of the states (desktop renders): `docs/ux/reviews/P2-12/`. Reference frames: `docs/qa/references/P2-12-background-image/`.

### Hooks
```bash
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor canvas-image-check       # video: 16:9 video, 9:16 video, 4:3 photo (all Fit) over the patterned picture on a 9:16 canvas, no captions
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-background-image   # photo: a 1:1 canvas, three layers over the picture
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-background-missing # photo: the picture's file is gone
adb shell am start -S -n app.memix/.android.MainActivity --es memix.renderScene photo-background-image --ei memix.renderWidth 1080   # after opening it once
```
`canvas-image-check` needs the P1 test videos pushed as for `canvas-check` (`docs/qa/phase-1/engineer-hand-checks.md`); the patterned picture is generated on first use (`files/debug-media/photo-scenes/background-pattern.jpg`).

### Done when
1. **Behind a Fit clip (video):** `canvas-image-check`: the picture shows in the bars above and below the 16:9 clip and the 4:3 photo, and nowhere on the 9:16 clip (same shape, no bars). Canvas panel → Fill: the picture is gone behind every clip and the hint line appears. Back to Fit.
2. **Preview = export (video):** export `canvas-image-check` at 1080p30 and again after changing the ratio to 1:1; pull three frames (one per clip) and compare with the preview screenshots cropped to the frame and scaled to the export width, and with `expected-video-*` in the references folder: the same crop of the picture within 1% of the width, the same brightness (no dimming), black in any gap. Look at the bar edge next to the clip: no hairline of black or of the picture inside the clip's edge (the bars are cut to a rounded pixel count and drawn at the exact thickness).
3. **Behind the layers (photo):** `photo-background-image`: the picture covers the canvas; Canvas panel shows Image first in the row (no Blur) with its thumbnail ringed. `renderScene` at 1080: compare with `photo-background-image-1080.png` (positions within 1% of the width, same cover crop).
4. **Pick, Replace, Remove, Undo (both editors):** Canvas panel → Image swatch with no picture opens the system picker at once (images only, one item); cancelling changes nothing. A pick lands as the background (the sheet "Adding your photo" only when the copy is slow); one Undo ("Undo: Background") takes it off. Replace gives a new picture (a new file in `files/media/<project id>/`; the old one stays for Undo). Remove goes back to black (video) or white (photo). A color, or Blur in the video editor, over the picture drops it; Undo brings it back. `Not enough space` and `Couldn't add your photo` read as in P1-02. Taps while the picker opens do nothing extra.
5. **Missing picture:** `photo-background-missing` (and, for video, delete the picture's file with `adb shell run-as app.memix rm files/media/<project id>/<picture>` after setting one): the canvas draws black, the Image swatch shows the icon in `text-muted` with the ring, the line "The image is missing. Pick another one or remove it." shows under the swatches, one toast per open names the ratio; export draws black bars.
6. **Old drafts:** open a draft saved at schema 2 and one at schema 3 (P2-01's photo draft) after this build: both open with their backgrounds unchanged (the JVM harness checked the JSON; this is the real database). A video draft with Blur still shows Blur.
7. **Media rules:** set a picture, close, kill, start again: it is still there (the cleaner runs at start). Duplicate the draft, delete the original: the copy still shows its picture. Open `files/media/` for the duplicate: it has its own copy of the picture.
8. **Orientation:** pick a portrait phone photo whose EXIF turn is 90 degrees (a camera photo): it covers the canvas upright in the swatch, the photo canvas and the video bars (the engineer assumes `ImageDecoder`'s size is the size as shown, as in P2-01: report if it is turned).
9. **4K export memory:** export at 2160 x 3840 with the picture set; report `dumpsys meminfo` peak (expected: about two frames of bitmap, 66 MB, on top of the usual, for each distinct clip shape: clips of one shape share their bars, so also try five clips of five shapes and report the peak: that is the case that could need a cap) and the export time against the same export with a black background.
10. **Responsiveness:** after a ratio change or any Canvas edit with the picture set, the video preview shows the new frame within 100 ms of the edit plus the usual rebuild (the picture is decoded at each rebuild: `adb logcat -s MemixPreview`; report the rebuild time with and without the picture, and tell the engineer if the picture adds more than 100 ms).
11. **Leaks:** LeakCanary, 5 cycles in each editor: open, set a picture, Replace, Remove, change the ratio, close. No leak; `dumpsys meminfo` Graphics after cycle 1 and cycle 5.
12. **Analytics:** logcat shows `tool_use` `canvas_background` with `value` `image` on a pick or Replace, `color` on Remove and on a color, `blur` on Blur; nothing else carries the picture.
13. **Accessibility:** the Image swatch reads "Image" (and "Pick an image" as its click label when empty, "Image, file missing" when missing); Replace and Remove read "Replace image" and "Remove image"; "Background image set" and "Background image removed" are announced politely. Focus return after the picker closes is not built (known limit, shared with the P4 gap in the P2-01 review).
14. **Fonts and 360 dp:** Hindi and Spanish at 200% on 360 x 640: the image row wraps under its label, nothing is cut (`docs/ux/reviews/P2-12/10-bg-hindi-200-canvas-panel.png`).

### Re-run because P2-12 moved the import flow and the swatch
- **Create → Video meme and Photo meme:** "Adding your media" with "1 of N" and "Adding your photo" with no count; Cancel, "Not enough space", "None went in", no picker app.
- **Video editor Add media and Replace (P1-16):** unchanged: the picks land, the toasts and the focus return work. The editor's `EditorMediaImport` now delegates to the shared `ProjectPickImport`.
- **Canvas panel colors in both editors:** the ten swatches, the ring, the black swatch's hairline ring; the timeline's spoken clip kinds ("video", "photo") after the `import_item_*` strings moved to `:core:ui`.

## P2-13 · add image layers in the photo editor

Spec: `docs/ux/specs/P2-13-image-layers.md` (its "QA compares" and "Measured" lists are the checks). Screenshots of the states (desktop renders): `docs/ux/reviews/P2-13/`. No new debug hook: use a photo start (Home → Photo meme, a 4:3 photo), then add a 9:16 photo and a second 4:3 photo from the gallery (the debug pictures `shape-9x16.jpg` and `shape-4x3.jpg` are written by `--es memix.openEditor photo-layers-10`; see the P2-01 hooks to copy them to the gallery).

1. **Two taps:** nothing selected: Add photo (main bar, before Layers), a photo in the picker. The photo lands as the top layer, fitted inside the frame, centered, scale 1, selected with frame and handle, and the bar switches to the selected-layer bar. Cancelling the picker, "Not enough space", "None went in" and "No picker app" change nothing and fire no `tool_use`.
2. **A 9:16 photo on a 4:3 canvas:** fitted inside, the canvas unchanged and its background beside the photo. **A same-shape photo:** covers the frame (1% rule); frame and handle still show it is there.
3. **Undo and redo:** one Undo ("Undo: Add photo") removes the layer; Redo brings it back at the same place. `adb logcat` shows one `tool_use` `add_photo` per landing and none for a cancelled pick.
4. **From the Layers panel:** Layers → the Add photo button is the first item of the list (after Move up and down and the Opacity row, never a drop target when dragging rows); after a landing the new row is selected at the top with the button above it; on a short panel (360 x 640, or 200% font) the Opacity row is the list's first item and the Add photo button follows it.
5. **On an empty canvas** (delete the only layer, or start blank with `photo-empty`): Add photo works and the empty-canvas card goes.
6. **Like any layer:** move, pinch, turn, fade, hide, lock, reorder, duplicate and delete the new layer; Position panel steps work.
7. **Reopen:** kill after landing (`adb shell am force-stop app.memix`), reopen from Drafts: the layer, its place and its picture are there; kill while the picker is open: the result still lands once (the root keeps the open request across a restart behind the picker).
8. **Adding the same gallery photo twice** makes a second file in `files/media/<project id>/`; Duplicate shares one file; close, kill, restart: both layers still draw (the cleaner keeps the shared files).
9. **Memory and speed:** add a 12 MP photo to the `photo-layers-10` scene: it decodes at the drawn size (`dumpsys meminfo` Graphics stays near the P2-01 numbers, not 480 MB) and the next drag stays under 5% janky frames (benchmark build, three runs).
10. **Leaks:** LeakCanary, 5 cycles of add, drag and undo.
11. **Accessibility:** "Photo added in front" is announced politely; Add photo reads its visible label in the bar and the panel; TalkBack focus after the picker closes (from the main bar the layer lands selected and the bar switches: report where focus lands; focus return is not built, the P4 gap of the P2-01 review). Hindi and Spanish at 200% on 360 x 640: `docs/ux/reviews/P2-13/30-hindi-200-*`.
