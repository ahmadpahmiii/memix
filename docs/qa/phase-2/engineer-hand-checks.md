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
