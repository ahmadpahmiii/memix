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

## P2-03 · text and stickers in the photo editor

Spec: `docs/ux/specs/P2-03-photo-text-and-stickers.md` (its "QA compares" and "Measured" lists are the checks). Desktop renders of the states: `docs/ux/reviews/P2-03/` (the text in them is a stand-in, see its README: judge the real text on the phone). Reference frames: `docs/qa/references/P2-03-text/`. Nothing here has run on a device yet; the logic (landing slots, edits, ratio rule, sticker sizes) ran in a JVM harness (186 checks, 0 failures).

### Hooks

```bash
./gradlew :androidApp:installDebug
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-text-check   # 9:16, plain photo + the six captions of the video's text-check
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-stickers     # photo, one text, five stickers (square, locked wide, hidden tall, tiny, missing file)
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-layers-10    # now with three text layers on top
adb shell am start -S -n app.memix/.android.MainActivity --es memix.renderScene photo-text-check --ei memix.renderWidth 1080   # after opening it once
adb exec-out run-as app.memix cat cache/scene-renders/photo-text-check-1080.png > text-check.png
```

### Done when 1 · the same style looks identical in both editors

Method (spec → Measured): (a) a screenshot of `photo-text-check` with nothing selected, cropped to the frame and scaled to 1080 wide; (b) the render hook's PNG above; (c) the video editor's `text-check` exported at 1080p30, a frame where all six captions show. Compare side by side and as a 50% overlay. Pass: the same line breaks and glyphs in the same fonts (Teko for the Devanagari under Classic), glyph size and outline width within 1% of the canvas width (10.8 px), position within 1% of the width, rotation within 1 degree, the same colors inside a letter, the off-frame Bubbly clipped at the same place, the same overlap order (OVERLAP over THE END). Put the three 1080 frames in `docs/qa/references/P2-03-text/` next to the desktop stand-in renders (they replace them as the iOS parity target).

### The rest of the spec's list

1. **Typing a new caption** (4:3 photo): Text on the main bar: the keyboard and the entry bar come up at once, the empty landing square at the top slot, the live caption in Classic as you type (no lag at 100 characters), Undo, Redo and the ratio pill disabled (grey, taps do nothing), the frame refits above the keyboard (never bigger than normal) and returns after Done. Done, Back, Escape on a keyboard, Close and swiping the keyboard down each commit; nothing typed (or only spaces) adds nothing and no Undo.
2. **After Done:** the caption is selected (two-tone frame and handle) and the bar reads Edit text, Style, Position, Delete (visible at 360 dp), then Opacity, Duplicate, Lock, Hide, Layers; "Text added" is announced; "Undo: Add text". Tap the selected caption: typing opens (a locked one explains instead). Clear the words in Edit text: the layer is deleted as one step ("Undo: Delete" brings it back).
3. **Landing:** first text at the top, second at the bottom, third in the middle, a fourth in the middle again; hide the top one and the next goes to the top. A second caption takes the last style you picked.
4. **Style and Position panels** (the Style panel is the lifted one): five font tiles in their own fonts, ten colors, No outline plus ten outline colors; each tap shows at once and is one undo step ("Font", "Color", "Outline"); tapping the selected one records nothing; the canvas refits above a 360 x 640 panel. Position: Size 25 to 500% (100% reachable), Rotation 15 degree steps, Move arrows (held: repeat, one undo step on release), Center. TalkBack announces each step.
5. **Gestures on the stage:** drag (1:1, snap to the middle lines with a tick), two-finger size, turn and move (bubble "120% · 15 degrees"), the handle; release is one step ("Position" or "Size and rotation"), `tool_use` `transform_text`. A touch off any layer clears the selection.
6. **Blank start:** Create sheet shows four rows (Video, Photo, Blank with the `create` icon, Template). Blank opens a white 1:1 canvas with the new empty card ("Tap Text to add words, or Add photo to add a picture."), and with no draft yet. Text, "Hello", Done: Drafts shows a draft; `adb logcat -s MemixAnalytics:*` shows `project_create` (`photo`, `blank`) once at that commit and nothing for a ratio or color change alone. Change only the ratio and the color: the card keeps "Tap Text to add words..." (the Undo body needs a layer that was there: `hadLayers && canUndo`). Delete the only text: the card's other body ("Tap Undo to bring your layer back, ..."); close: no draft. **Kill:** after the text, send the app to the background and `adb shell am kill app.memix`, reopen from the recents: the text is back; on an untouched blank canvas the same kill returns a fresh blank canvas and no draft.
7. **Ratio sequence** on `photo-text-check` (and `photo-stickers`): 9:16 to 16:9 to 1:1 to custom 9:21 to back. Every text and sticker that started fully inside stays fully inside (2% margin), a big text shrinks first (not under 25%; stickers not under 10%), a text hung off the edge keeps its center and scale; the layers-moved toast; one Undo restores the previous ratio and every layer exactly.
8. **Locked text:** lock a caption, then drag, tap, pinch it and use Style, Position, Delete: it never changes and the toast explains; the lock badge replaces the handle; Unlock is first in the bar.
9. **Stickers** (`photo-stickers`): landing size is 35% of the shorter side at scale 1 (the square one); pinch to 500% and down to 10%; the locked wide one will not move; the hidden tall one is in the Layers panel only; the tiny 96 px one is sharp enough at scale 1 (blurry when enlarged: expected); the missing one shows "File missing" and the photo icon in its row, a toast once per open, and is not drawn. Layers panel: sticker rows show the picture (never a bitmap bigger than about 128 px), text rows show the `text` icon on a dark tile with the first line as the name, hidden and faded rows dimmed. Opacity of a sticker and of a text: a translucent caption shows no outline through its fill (`photo-text-faded-opacity-group-1080.png`).
10. **Mixed scripts:** "जब BUILD PASS हो" and an emoji in all five fonts, outline on and off: Devanagari under Classic is Teko, nothing clipped above or below, the emoji without a ring.
11. **Reopen:** kill mid-edit and reopen from Drafts: every text and sticker, including locked, hidden and faded states, is the same.
12. **Speed** (benchmark build, owner's phone; report model, Android version): `photo-layers-10` with its three text layers: three 3-second drags (`adb shell input swipe 200 900 900 1300 3000`, with `dumpsys gfxinfo` reset before each) under 5% janky frames; typing 100 characters shows no visible lag; `dumpsys meminfo` Graphics and Native Heap after a drag, against the P2-01 numbers.
13. **Leaks** (debug build, LeakCanary): five cycles of Blank, add text, type, Style, Position, Layers, ratio change, Undo, close; and of `photo-stickers` open, select, pinch, close.
14. **Accessibility:** TalkBack reads each text on the stage with all its words and the actions Edit text and Style (a long text reads whole); the entry field is focused on opening; "Text added" is polite. Focus return after the keyboard and panels is not built (the P4 gap noted in the P2-01 review). Hindi and Spanish at 200% font on 360 x 640: typing, the selected-text bar, the Style panel, the Layers panel (a Devanagari row name), the empty card: `docs/ux/reviews/P2-03/40-hindi-200-*`. Captions on the canvas must not grow with the system font size.

### Re-run because of the lift (video editor, P1-10 and P1-11)

The typing bar, `TextEntryUi`, the Style panel and 24 caption strings moved to `:core:ui`, and the video editor now uses them. Re-run on `text-check` and `canvas-check` (steps in `docs/qa/phase-1/engineer-hand-checks.md`):
- **P1-10 steps 2 to 5 and 11:** typing (every way out commits, nothing typed adds nothing, clearing an edit deletes as one step), landing at the playhead on the video unchanged, the Style panel (tiles in each font, colors, outline, one undo step per tap, the preview staying visible), and Hindi at 200% for both panels and the entry bar. The strings read the same in all five languages (they only moved).
- **P1-11 canvas steps with the pill:** Undo, Redo and the ratio pill while typing. **What changed:** before P2-03, tapping the pill while typing committed the words and then opened the Canvas panel (the P1-11 spec's "Opening from other states"); now the pill is inert and dimmed while typing in the video editor too (the P2-03 spec's request, one behavior in both editors). Undo and Redo sat in the transport row, which is hidden under the keyboard, so they were already unreachable. Check that the pill is dimmed and does nothing while typing and works again after Done.
- **P0-05 (Create sheet):** the sheet now has four rows; the order is Video meme, Photo meme, Start with a blank canvas (the `create` icon, directly under Photo meme), Start from a template; the Blank row opens the photo editor on a blank canvas.

## P2-02 · meme formats (Plain, Classic, Caption bar, Demotivational, Panels, Bubbles)

Spec: `docs/ux/specs/P2-02-meme-formats.md` (QA compares, Measured). Renders: `docs/ux/reviews/P2-02/`. References (10 frames, desktop, stand-in text): `docs/qa/references/P2-02-formats/` (README has the device commands). Logic (picture areas, slots, placement, 16 panel layouts, bubble geometry, switching, schema-4 draft opens plain, round trips): JVM harness, 360 checks, 0 failures. Nothing has run on a device.

1. **Plain to Classic and back with Undo:** gallery 4:3 photo (no hints, no bar: plain), Formats, Classic: two "Tap to add text" hints, the photo untouched; Undo ("Undo: Format") returns to plain exactly. Plain tile selected at the start; a tap on Plain with no format does nothing and logs nothing (`adb logcat -s MemixAnalytics:*`: `format_classic` once, `format_plain` only when going back).
2. **Caption bar / Demotivational:** the photo is placed in the picture area (white bar 0.25 S tall; black poster, white frame, title and subtitle hints below); the hint line appears when the photo doesn't reach the area's sides; a moved photo stays; a typed caption in each slot (Caption bar: black Modern on the bar; Demotivational: serif title, small subtitle); the Canvas panel's background row explains the poster hides it.
3. **Switching never deletes or moves text or extra photos;** one Undo reverses each switch exactly. Add photo under Caption bar lands inside the picture area (P2-13 rule).
4. **Ratio sequence** on each format (4:3, 9:16, 16:9, 1:1): the parts re-lay inside the new canvas, the as-placed photo follows the picture area.
5. **Old drafts:** install the P2-03 build (0f9ef3e), make a photo draft with text, install this build over it: the draft opens plain (Plain tile white, no hints, nothing moved). A Phase 1 video draft opens unchanged.
6. **References:** render `photo-format-*` at both sizes with the hook and compare with `docs/qa/references/P2-02-formats/` (1% position, 2% size, 1 degree; the 2160 frame equals the 1080 frame scaled by 2).
7. **Empty draft:** Blank, choose Classic, close: no draft saved. Hindi and Spanish at 200%: `docs/ux/reviews/P2-02/50-hindi-200-*`.
8. **Panels:** Formats, Panels: the photo is in cell 1 of two side by side, an empty cell shows the pick tile (Add photo); tap it: the picker for that cell, the photo lands in it, the cell is selected (ring) with the bar Replace, Position, Remove. Drag in a filled cell pans its photo 1:1 (never a gap), two fingers zoom 100 to 500%, one "Panel photo" step each (`transform_layer`); Position panel: Size ladder, Move arrows (held repeats), Center, limits explain. Remove empties the cell (`remove_cell_photo`); Undo brings it back. Layout tool: counts 2 to 6, Grid / Side by side / Stacked, Make panels even (`panels_layout`); a lower count drops the last photos with the toast and Undo returns them. Dividers: drag a handle (stops 15% from the neighbours, snaps to even within 3% with one tick, commits once on release: `panels_divider`); tap a handle: the Divider panel (arrows 2%, held repeats, Even). A grid's rows have their own vertical dividers. Kill and reopen: cells, views and dividers are as saved; the cell photos survive the cleaner (restart the app twice).
9. **Panels speed bar (benchmark build, owner's phone; report model and Android):** `--es memix.openEditor photo-panels-6` (six 12 MP cells): three 3 s divider drags (`adb shell input swipe` along a gutter, `dumpsys gfxinfo app.memix reset` before each) under 5% janky frames; `dumpsys meminfo app.memix` Graphics and Native Heap after a drag: each cell decodes at its drawn size (never six 48 MB bitmaps; the P2-01 numbers are the yardstick). Pan and pinch in a cell also smooth.
10. **Bubbles:** Formats, Bubbles: one slot hint ("Tap to add a bubble") and the Add bubble tool; tap either: typing opens at once with the live bubble (the body grows around the words, the tail points toward the canvas center); Done adds it (`add_bubble`, no font), selected with the frame around body and tail, the scale-and-rotate handle and the tail handle (a drag moves the tip 1:1, one "Tail" step on release; both handles stay 48 dp at a 25% bubble; the tail handle wins where they overlap). Style: Shape (Speech, Round, Thought: `bubble_shape`), Font and Color (`text_font`, `text_color`), Fill (`bubble_fill`), no Outline row; Position: Size, Rotation, Move, Tail arrows (1% of the canvas, held repeats), Reset tail, Center. A thought bubble's dots shrink with a short tail (two dots, then one); its default tail is long. Locked: Unlock first, the tail handle is gone and Style, Position and Tail explain. Bubbles scale 25 to 500% and may leave the canvas at 500% (clipped). Ratio change keeps a bubble with its tail inside.
11. **Leaks (debug build, LeakCanary):** five cycles of open, Panels, fill a cell, drag a divider, change the layout, switch to Bubbles, add a bubble, drag its tail, close.
12. **TalkBack:** cells read "Panel 2 of 4" (selected) with the pick tile reading "Empty. Double-tap to add a photo."; each divider is an adjustable "Divider 1 of 3, 50 percent" (swipe up and down move it 2%); a bubble reads "Speech bubble: <words>"; the handles are hidden (the Divider panel, the cell Position panel and the Tail row are the routes).

## P2-11 · text shadow and box (both editors)

Spec: `docs/ux/specs/P2-11-text-shadow-and-box.md`. References (desktop, stand-in text): `docs/qa/references/P2-11-text-shadow-box/`. Nothing here has run on a phone: no emulator in the build session.

```
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-text-shadow-box
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor text-shadow-check
```

1. **Style panel, both editors:** select a caption, Style, scroll past Outline: Shadow (No shadow selected) and Box (No box selected), no Softness or Corners rows. At 360 x 640 are the new rows found by scrolling? Tap a shadow color: Softness row fades in (120 ms) with Soft selected; tap Sharp, Blurry, then "No shadow": the row goes; tap a color again: Soft again. Same for Box (Rounded default, Square, Pill).
2. **Undo names:** each tap is one step, "Undo: Shadow" / "Undo: Box"; tapping the selected option records nothing.
3. **Bubble:** Formats, Bubbles, a bubble's Style: Shape, Font, Color, Shadow (Softness), Fill; no Outline, no Box.
4. **Three places, same caption:** `photo-text-shadow-box` on screen, its render (`--es memix.renderScene photo-text-shadow-box --ei memix.renderWidth 1080` and 2160), and the `text-shadow-check` export frame at 1.0 s (1080p and 720p): same line breaks and glyphs; box rectangle and corners, shadow offset and blur and the text within 1% of the canvas width; same colors inside box and text; clipped at the same edge (the EDGE caption). Compare the 2160 render with the 1080 one scaled by 2.
5. **Selection frame and hit area** hug the box, not the shadow; rotated 30 degrees (Bubbly) and at 250%. Position's Center centers the box; the ratio change keeps the box inside with its 2% margin.
6. **PRE-AGREED MUST-FIX IF IT FAILS (code review): blur on hardware:** is the Soft and Blurry shadow blurred (not hard-edged or missing) on the photo editor's screen, on API 29 and a current phone? If it is missing or hard, the Android docs' "setMaskFilter unsupported" table applies: tell the engineer (fallback: a software shadow bitmap).
7. **Opacity group (photo):** a boxed, shadowed caption at 40%: no overlap darkening between box, shadow and text (`opacity-group-40` reference).
8. **Emoji and Hindi** ("जब BUILD PASS हो 😂") with box and shadow in all five fonts: the box covers the marks above and below; where the shadow falls under the emoji is the platform's.
9. **Old drafts:** install the P2-02b build, make one video draft and one photo draft with captions, install this build over them: both open with no shadow, no box, unchanged; add a shadow, close, reopen: kept.
10. **New text keeps the last style** including shadow and box; Duplicate and Split copy it; a Caption bar slot is still black on white with no box.
11. **PRE-AGREED MUST-FIX IF IT FAILS (code review; fallback = a cached software shadow Bitmap per `CaptionLayout`, not built yet): speed (benchmark build, owner's phone; report model and Android):** type 100 characters with a box and a Blurry shadow: no visible lag; drag a boxed caption in `photo-text-shadow-box` for 3 s three times (`adb shell dumpsys gfxinfo app.memix reset`, then read Janky frames): under 5%, with a Blurry shadow plus a box on the dragged caption. The blur is expected per draw on the photo screen (not cached): this is the number that tells whether a cached bitmap is needed.
12. **Leaks (debug build, LeakCanary):** five cycles of open, style a caption with shadow and box, edit it, close.
13. **Re-run because the renderer changed:** P1-10 steps 2 to 5 and 11 (typing, style, position, preview equals export) on `text-check`; P2-03 text steps (add, type, style, Position, opacity group) and the ratio-change step on `photo-text-check`.
14. **Spanish and Portuguese at 200%** (design review M1): Softness and Corners segments stack one per row at full width and no word is broken (the frame `docs/ux/reviews/P2-11/70-spanish-200-style-panel-shadow-box.png` shows es).

## P2-04a · image tools, part a: Crop screen (crop, rotate, flip, straighten)

Spec: `docs/ux/specs/P2-04-image-tools.md`. Renders: `docs/ux/reviews/P2-04/` (desktop, stand-in picture); references: `docs/qa/references/P2-04-image-tools/`. Part b (adjustments, 20 filters, LUT script, deep-fry, Edit photo panel) is not built. Nothing here has run on a phone.

```
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-image-tools
```

1. **Bars:** an image layer selected: Position, Opacity, Delete, Crop (cut by the scroll at 360 dp), Duplicate, ...; a text layer has no Crop; the main bar shows Crop after Add photo only while an image layer exists; tapping it with nothing selected selects the main photo and opens the screen. Locked layer: Crop explains (the toast). Missing file: explains.
2. **Crop screen:** opens with the whole picture, the frame the whole picture, Free selected. Drag a corner, an edge, inside; the grid shows only while dragging; the frame stops at 10% and at the picture's edges. Aspect tiles: 1:1 replaces the frame by the largest centered one and locks it (corners keep the shape, edges do nothing); Free unlocks. Rotate left/right and flip: the frame turns/mirrors with the picture, a locked shape inverts on a turn. Straighten -10 deg: no empty corners, the frame does not move; snap to 0 with a tick.
3. **Edges:** the four rows trim 1% a tap, hold repeats (the held steps apply on release in this build), the readouts, the limit line at the minimum; Done returns to the crop screen. With a locked aspect the other side follows.
4. **Done is one step "Crop"**: Undo restores exactly; the layer's row says "Edited"; reopening Crop shows the saved crop, turn, flip and straighten. Close, system back and Escape discard (no question): QA watches for lost work. Edges open: back closes only Edges.
5. **After a crop** (4:3 photo cropped to 1:1 in a 4:3 canvas): the canvas keeps its shape, the photo is fitted inside, its transform kept; selection frame and hit area follow the cropped shape; pinch and turn still work; the ratio-change rule keeps it inside.
6. **P2-02 as-placed re-run:** Caption bar with the main photo as placed: crop it: it is placed again in the picture area; a photo you moved first stays put.
7. **Picture sharpness:** crop a 12 MP photo to 10%: still sharp on the canvas (decoded larger by the crop's zoom), no jank while dragging the frame (release build).
8. **Old drafts:** a photo draft saved before this build opens unchanged (schema 7, "edit" absent); a draft with an edit, killed and reopened, keeps it.
9. **Analytics:** a Done that only rotated logs `rotate_image` alone; crop + flip logs `crop` and `flip_image`; one undo step for all.
10. **200% font, es and hi:** top bar wraps (Reset and Done on a second line), "Edges" and the aspect tiles are whole, the bottom area scrolls.
11. (04b is below.)
12. **Leaks (debug build, LeakCanary):** 5 cycles of open Crop, rotate, drag, Done, close.

## P2-04b · image tools, part b: Edit photo (Filters, Adjust, Deep-fry)

Spec: `docs/ux/specs/P2-04-image-tools.md`. Frames: `docs/ux/reviews/P2-04/90-*` (desktop, stand-in picture). References: `docs/qa/references/P2-04-image-tools/` (contact sheets, one tile per id). Nothing here has run on a phone. **The color pass is a CPU pass on every Android version** (a decision, TECHNICAL_DESIGN → Decisions); the GL path of the spec is not built.

1. **Bars:** an image layer selected: Position, Opacity, Delete, **Edit photo**, Crop (cut by the scroll at 360 dp); the main bar has Edit photo and Crop after Add photo only while an image layer exists; a tap with nothing selected selects the main photo. Locked: explains; missing file: explains; hidden layer: the panel opens with its hint line.
2. **Filters tab:** 21 tiles (None first), each the photo with that filter; tiles fill in from the left; the selected one is ringed and scrolled into view; a tap applies at 100% (one step "Filter"), the Intensity row appears (snap to 100% with a tick); None removes it. First five tiles within 300 ms of opening (measure on the phone).
3. **Adjust tab:** six sliders (the four bipolar ones with the origin tick), snap to 0, readouts "+20" / "-30"; the picture follows the finger; one step "Adjust" on release; Reset sets all six to 0 in one step "Reset". Deep-fry: Amount 0 to 100%, hint, one step "Deep-fry".
4. **Look equals the reference:** open `photo-image-tools`, put each filter on the portrait photo and compare with `filters-100-*` sheets (same colors; the phone's picture is a different one); render `--es memix.renderScene photo-image-tools --ei memix.renderWidth 1080` and 2160 and compare with the screen (99% of pixels within 3/255 is the spec bar; grain and sharpen are now resolution-independent: expect about 100% on smooth areas at deep-fry 30%, misses only at hard edges and above about 60% deep-fry; report both).
5. **Speed (benchmark build, the owner's phone; report model and Android):** a 12 MP layer (`photo-layers-10` base): three 3 s drags of an Adjust slider and of Intensity (`adb shell input swipe` along the rail; `dumpsys gfxinfo app.memix reset` first): janky frames under 5%. Time from release to the full-size picture. **PRE-AGREED MUST-FIX (principal, 7 Nov):** more than 5% janky frames, or the picture updating under the finger fewer than about 10 times a second, or over 500 ms from release to the full-size picture: build the GL pass on the same LUT data before P2-08 closes (first try: split the rows over cores). Also the time of a 2160 render with every tool on (the render hook) and peak memory.
6. **API 29 emulator (spec Done when):** the whole tool works on an API 29 emulator and on a current phone with the same look (the CPU pass is the same code; watch memory and speed on the emulator).
7. **Reset per tab:** Filters Reset removes the filter (event `filter` with `none`), Adjust Reset logs one event per slider that was not 0, Deep-fry Reset logs `deep_fry`; a tab already at normal records nothing.
8. **Undo/redo:** each step restores exactly, the open panel shows the restored values; the layer's row says "Edited" for a color edit too; a crop after a filter keeps the filter; Duplicate copies the edit.
9. **Non-destructive:** the media file's size and hash are unchanged after every tool; reopen a draft: same picture; an old draft opens unchanged.
10. **Every id:** all 20 filters at 100% and 50% on a real photo and on the gray ramp; none looks broken (clipped, banded); `none` leaves the picture as it was.
11. **200% font, es and hi:** tab labels and tile names wrap and grow the tile, the Adjust panel scrolls.
12. **Leaks (debug build, LeakCanary):** 5 cycles of open Edit photo, change a filter and an adjustment, open Crop, rotate, Done, close.
13. **Make it a video meme (P2-10 flag):** the video overlay has no LUT in Phase 2: P2-10 bakes the edited picture.

## P2-05a · Draw (the drawing half; the censor half is still to build)

Spec: `docs/ux/specs/P2-05-draw-and-censor.md`. Frames: `docs/ux/reviews/P2-05/95-*` (desktop render, stand-in text). References: `docs/qa/references/P2-05-draw-censor/` (strokes at five sizes, an erased stroke, a dot, a 40% group; on a photo and on white; 1080 and 2160). Nothing here has run on a device.

1. **Main bar** (360 x 800 and 360 x 640): Formats, Text, Add photo, Draw, Edit photo whole, Crop cut, Layers last; Remove BG, Stickers and Censor are not there yet.
2. **Draw mode:** tap Draw: the Draw bar replaces the tool bar, no frame or handle shows, the ratio pill is dimmed, Undo and Redo work. Entering and leaving with no stroke leaves no layer and no Undo step.
3. **A stroke:** red and Medium are the defaults; it follows the finger with no lag (60 fps screen recording, release-type build); a tap makes a dot; a second finger or palm never ends or moves a stroke. Back during a stroke cancels it; the next back leaves the mode.
4. **One layer:** three strokes make one "Drawing" layer (Layers panel, mini render on the gray tile); Done leaves it selected with Position, Opacity, Delete, Draw, Duplicate, Lock, Hide, Layers. Draw there adds to the same layer (one layer, not two). Undo of the first stroke removes the layer and the mode stays open.
5. **Eraser:** clears only the drawing's own strokes, never the photo; with nothing drawn the first eraser stroke shows "Draw something first, then you can erase it." and records nothing.
6. **Layer actions on a drawing:** move, scale to 300%, turn, fade to 40% (no overlap darkening where strokes cross), hide, reorder under a caption, duplicate, delete, undo, lock (Draw and Position explain), kill the app and reopen from Drafts: the same drawing each time. A touch outside the strokes' box (grown to 48 dp) goes to the layer below.
7. **Ratio change:** a drawing fully inside stays inside (2% margin, shrinks first, not below 10%) and is not stretched; one undo step and the toast.
8. **Cap:** a scribble that passes 5,000 points stops at the cap and says "This drawing is full..."; a new drawing is started by deselecting and tapping Draw.
9. **Draft size:** JVM harness number (this machine, not a phone): 3 minutes of scribble (about 10,800 raw touch points) stores as 2,851 points and a 27.5 KB project JSON; a full layer (5,000 points) is 48.0 KB. Check on the phone that autosave still commits within 100 ms (`adb logcat`, 500 ms debounce plus write) with a full drawing.
10. **Editor equals render:** `photo-draw-censor` on screen against `--ei memix.renderShortSide 1080|2160` (strokes within 1% of the width, eraser gap the same); desktop check: the 2160 render against the 1080 render scaled differs by more than 64/255 in 0.64% of sampled pixels (edges only).
11. **Speed:** drag, scale and fade a full drawing in `photo-layers-10` (release-type build, owner's phone): three 3-second drags under 5% janky frames. Desktop CPU raster of a full 5,000-point drawing is 48 ms a frame, which says nothing about a phone GPU: measure it.
12. **200% font, es and hi:** the Draw bar scrolls inside 40% of the window, size words wrap, nothing is cut.
13. **TalkBack:** opening the mode says "Drawing. Draw with one finger."; Done says "Drawing saved" (only after a stroke); a drawing's row has the Draw action.
14. **Old drafts:** a draft saved before this build (schema 7) opens unchanged. **Analytics:** one `draw_stroke` or `erase_stroke` per stroke, none for color or size.
15. **Leaks (debug build, LeakCanary):** 5 cycles of enter Draw mode, draw, erase, Done, close.
16. **API 29:** the drawing half uses only Canvas layers and paths (no RenderEffect or AGSL): check it on an API 29 emulator with the rest of the tool.

## P2-05b · Censor (minimums raised after the design review: pixelate a x 1/5 to 1/2, blur sigma a x 0.22 to 0.5, floor 0.016 S, blur cell 2 sigma)

Spec: `docs/ux/specs/P2-05-draw-and-censor.md`. Frames: `docs/ux/reviews/P2-05/96-*` (desktop render, stand-in text). References: `docs/qa/references/P2-05-draw-censor/censor-*` (modes over a photo, strengths 0/50/100 with an ellipse and a turned one, the legibility card, an order check; 1080 and 2160). Debug scene `photo-draw-censor` now also has digits under a Pixelate, a Blur ellipse and a Black bar, and a sharp caption above them. Nothing here has run on a device.

1. **Add:** the Censor tool (main bar, after Crop) puts a Pixelate rectangle (0.40 S by 0.25 S, 50%) in the middle, selected, with its panel open. One undo step "Add censor"; `tool_use` `censor_pixelate`.
2. **Panel:** Mode (Blur, Pixelate, Black bar), Shape, Strength (not for Black bar); each tap one step; a mode change fires its own id (`censor_blur`, `censor_pixelate`, `censor_bar`), shape, strength and size fire nothing. Strength drags live and records on release.
3. **Handles:** each edge target is capped at a third of the box's side on screen (24 dp at least), so the middle of a small box still moves it; corner handle scales and turns; the four edge bars resize along the layer's own axis with the opposite side fixed, stopping at 0.06 S and 3 S with no toast; Position panel Width and Height rows do the same (2% of S a tap, hold repeats, a limit explains). Check a turned censor's edge drag.
4. **Backdrop:** it hides only what is under it: a caption over it stays sharp, one under it is hidden; move the censor above and below a layer in the Layers panel; a hidden layer below is not drawn through it; a locked one is.
5. **Legibility (spec Done when, two people):** the card in `censor-legibility-card-s1080.png` and the same on the phone: ten digits 0.03 S tall under Pixelate and Blur at strength 0: no digit identified; Black bar covers fully; the same at 2160. After the design review the card also has text 0.03, 0.06 and 0.10 S tall, each in a snug (1.3x) and a loose (3x) box, Blur and Pixelate at strength 0 (`censor-legibility-text003|006|010-*`); on the desktop render no digit can be made out in any. If anyone reads a digit on the phone, raise `CensorCatalog` (a look change for saved censors, so before the closed test).
6. **Editor equals render:** `photo-draw-censor` on screen against the render hook at 1080 and 2160; desktop check: the 2160 render against the 1080 render scaled differs by more than 64/255 in 0.64% of sampled pixels (edges). The pixelate grid is in units of S, anchored at the canvas's top-left.
7. **Rows and bars:** "Censor" row with the `censor` icon; the censor bar is Censor, Position, Delete, Duplicate, Lock, Hide, Layers with no Opacity; no Opacity row in the Layers panel and no Opacity TalkBack action. Locked: badge, Unlock first, Censor, Position, Width, Height explain.
8. **Ratio change, duplicate, delete, undo, reopen from Drafts:** the same censor each time.
9. **API 29 emulator (the PM's must-know):** blur and pixelate must look the same as on a current phone. This build uses only Canvas drawing and bitmaps (no RenderEffect, no AGSL), so the look should be identical; check it, and watch memory and drag smoothness there.
10. **Speed (release-type build, owner's phone):** in `photo-layers-10` add a drawing and a censor: three 3-second drags under 5% janky frames; a censor over a 12 MP photo drags smoothly. Desktop CPU raster numbers (not a phone): one Pixelate 1.4 ms, one Blur 3.4 ms, the whole `photo-draw-censor` scene 11.4 ms a frame at 1440 x 1080. Each frame draws what is under the censor again into a small bitmap, so cost grows with the scene under it: if the drag bars fail, cache the backdrop while the layers below don't change.
11. **200% font, es and hi:** the Censor panel scrolls, tiles wrap.
12. **Leaks (debug build, LeakCanary):** 5 cycles of enter Draw mode, draw, erase, Done, add a censor, change its mode, close.

## P2-06 · `Segmenter` and ML Kit subject segmentation (no screen: P2-07 builds the cards)

Nothing here is visible in the app yet; P2-06 is the engine, the model download and the states' data. Everything runs through the debug hook. **The emulator needs a Google Play image** (an AVD marked "Google Play"; a plain "Google APIs" or AOSP image has no Play services, which is check 4). Sign in to Play on it so the module can download. Builds: debug for the checks, `installBenchmark` (R8-minified) for the numbers.

Set up: put a 12 MP photo and a few others on the phone's app files: `adb shell "run-as app.memix mkdir -p files/debug-media/segment" && adb shell "run-as app.memix sh -c 'cat > files/debug-media/segment/person.jpg'" < person.jpg` (same for `pet.jpg`, `object.jpg`, `empty.jpg` = a plain wall or sky, `person-12mp.jpg`). Read the log with `adb logcat -s MemixSegmentCheck:D MemixSegmenter:D`.

1. **First run, model download:** on a phone or Play emulator that has never used ML Kit subject segmentation (on an emulator, `adb shell pm clear com.google.android.gms` resets it; never on a real phone): `adb shell am start -n app.memix/.android.MainActivity --es memix.segmentCheck debug-media/segment/person-12mp.jpg --ei memix.segmentPasses 6` (the path is relative to `files/`). Expect `pass 1 (first: may include the model download)` with a few `downloading the model` lines (a fraction from 0 to 1, or `null` where Play services gives no size), a total of several seconds; passes 2 to 6 show the warm numbers; the last line is `warm median of 5: ... ms`.
2. **The bar (owner's phone, benchmark build):** the warm median of 5 for the 12 MP photo is under 1500 ms. Write down pass 1 apart, the median, the worst, and the decode / model / mask split. The mask is 1024 px on its long side; the PNG write is included in the total.
3. **Person, pet, object:** run `person.jpg`, `pet.jpg` and `object.jpg` with `--ei memix.segmentPasses 2 --ez memix.segmentKeepMasks true`: each pass ends `... mask 1024x...`. Pull the masks (`adb shell run-as app.memix ls files/media/segment-check`, then `adb exec-out run-as app.memix cat files/media/segment-check/<name>.png > mask.png`) and look: white is the subject, black the rest, soft grey edges, the picture's proportions, upright for a portrait photo (EXIF). Delete the folder afterwards (`run-as app.memix rm -r files/media/segment-check`).
4. **No Play services:** on an AOSP (non-Play) emulator image: `failed (NO_PLAY_SERVICES)` after a few ms, no crash.
5. **Offline first use:** airplane mode on, model not installed (check 1's setup): `failed (OFFLINE)` at once, no download started. Turn the network on and rerun: it downloads.
6. **Nothing found:** `empty.jpg`: `nothing found after ... ms`.
7. **Missing file:** a path that does not exist: `failed (SOURCE_UNREADABLE)`.
8. **Out of memory and slow:** not reproducible on purpose; the code paths are `OUT_OF_MEMORY` (an `OutOfMemoryError` while decoding or masking) and `SLOW` (30 s). Check only that nothing else crashes on a huge image: a 100 MP panorama (`pano.jpg`) either finishes or fails calmly, and the app stays alive.
9. **Leaving during the download:** press Home during pass 1's download, return: the download finished on its own in Play services, and a new run finds the model installed (pass 1 is then warm-ish: only the model load).
10. **Memory:** after the run, `adb shell dumpsys meminfo app.memix`: Native Heap falls back after the app is sent to the background (the ML Kit client is closed on UI-hidden); no growth over 5 repeated runs of the hook.
11. **R8:** the same runs on the `benchmark` build give the same results as debug (no `ClassNotFoundException` or "component registrar" errors in logcat). `bundleRelease` ran green on 7 Nov and the mapping keeps `SubjectSegmentationRegistrar`.
12. **Data safety (P6-05):** `adb shell dumpsys netstats` / a proxy while the hook runs warm shows no upload of the photo or the mask; the model download is by Play services (`com.google.android.gms`).

## P2-07 · Remove background: cut-out screen, refine, softness, Save as sticker

Spec: `docs/ux/specs/P2-07-background-removal.md`. Needs a Google Play emulator or a phone (checks 1 to 6 use ML Kit through Play services). Not run anywhere yet; no renders exist (`docs/ux/reviews/P2-07/` and `docs/qa/references/P2-07-*` are still to be made on a device with `memix.renderScene`).

1. **One tap:** open a photo project with a person photo; Remove BG on the main bar (only while an image layer exists; it selects the main photo): the screen slides up, the photo stays, after under 1.5 s "Background removed" shows on the checkerboard. Pet and object photos the same. Layer bar: Position · Opacity · Delete · Edit photo · Crop · Remove BG · ... (Text and sticker layers show no Remove BG).
2. **Refine:** Erase a bit of the cut-out and Restore a bit back: the stroke shows live (erase clears, restore shows the photo), commits on release; two fingers pinch (1x to 5x) and pan and never paint; Fit returns. Undo and Redo step through strokes and softness. 5,000 points: "This cut-out has a lot of edits...".
3. **Softness:** the slider (0 to 100%, 30% default, snaps to 0 within 3% with a tick) changes the edge on release, not per frame.
4. **Apply, kill, reopen (Done when):** Apply, then force-stop and reopen the draft: the cut-out and the refine strokes are the same; reopen Remove BG: the strokes are there. Layers row says "Cut out"; Undo says "Undo: Remove background"; Remove cut-out gives the whole photo back ("Undo: Remove cut-out"). Back/Close/Escape discard.
5. **Crop, move and color after a cut-out:** crop, turn, flip, move, scale, a filter and deep-fry keep the cut-out and its alpha.
6. **Editor equals render:** `--es memix.renderScene <project> --ei memix.renderWidth 1080` and `--ei memix.renderShortSide 2160`: the cut-out matches the screen; the 2160 edge is as soft as the 1080 one (the feather scales with the picture).
7. **Failure cards** (debug/benchmark builds): `adb shell am start -n app.memix/.android.MainActivity --es memix.bgRemoveFail unavailable|offline|memory|none|slow|off` then Remove BG: each shows its card with Try again (`slow`: Preparing with a progress bar, after 30 s "This is taking longer than usual." and Try again); Close always works; `off` returns to the real segmenter. Real first use: model download shows Preparing with progress; airplane mode before the first download shows the offline card.
8. **Save as sticker:** the button saves `files/stickers/<uuid>.png` (`adb shell run-as app.memix ls files/stickers`): transparent, trimmed to the cut-out, long side 1024 at most, without the crop or color edits; a second tap with nothing changed says "already in My stickers"; with everything erased "Nothing is left to save"; the screen stays open. Nothing is uploaded.
9. **Speed and leaks:** release-type build: stroke drawing follows the finger without lag on a 12 MP photo; LeakCanary (debug), 5 cycles of open, paint, Apply, reopen; the drag bars in `photo-layers-10` with a cut-out layer.
10. **Known gaps to judge:** Nothing found has no live brush (Try again only); the Layers panel thumbnail does not show the cut-out yet; the cut-out screen has not been design-reviewed; machine-translated strings (id, es, pt, hi).
