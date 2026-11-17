# Phase 4 · hand checks for the owner

The engineers' checks for each ticket's "Done when", copied from their handoffs. Nothing here has run on a device yet (cloud checkout, no emulator): QA folds these into the phase-end pass.

## P4-01a · overlay layers, engine and model

Spec of the editor half: `docs/ux/specs/P4-01b-overlay-layers.md`. Design notes: `docs/TECHNICAL_DESIGN.md` → Overlays. Reference render: `docs/qa/references/P4-01-overlays/`. There is no overlay UI yet: the draft is opened by a debug hook, so everything below needs a debug build (`installDebug`) or `installBenchmark` (the `memix.openEditor` hook works on both; the export hooks need debug).

### 0. Test media

P1-03 step 0 and P1-04 step 0 (`clip-a.mp4` 1280x720 6 s, `clip-b.mp4` 720x1280 6 s, `photo.png` 1080x1080, `clip-1080p.mp4` 1080x1920 32 s) plus `photo-4x3.png` (P1-10, 1440x1080), pushed into `files/debug-media/`. The overlay drafts carry the measured sizes and lengths of exactly these files, so use files of these sizes.

### 1. What was checked without a device

A JVM harness (not in the repo; it compiled the repo's `:core:model`, `:core:domain`, the project codec and the engine planner): 122 checks, all pass. Covered: schema 14 and the 13 to 14 migration (an old JSON without `transform` and `opacity` opens at 14 with default values), the overlay edits and that each is one undo step, the planner output (layers bottom to top, gaps, cuts to the video, photos, skips, missing media), the decoder budget rule, and the placement math checked against a hand copy of Media3 1.11.1's overlay matrix chain (rotation direction, aspect without shear, resolution independence). `:androidApp:assembleDebug`, `lint`, `compileCommonMainKotlinMetadata`, `:core:data:verifySqlDelightMigration` and `:androidApp:bundleRelease` ran green. Nothing ran on a phone or emulator.

### 2. Overlay check draft (does it look right?)

```sh
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor overlay-check
adb logcat -s MemixPreview:* MemixVideoEngine:*
```

The editor opens a 10 s draft of the 1080p clip with five overlays (no overlay tools exist yet; you only watch and scrub). Compare with `docs/qa/references/P4-01-overlays/overlay-check-sheet.png`:

1. Nothing but the main video until 0:01. At 0:01 the red 16:9 video (upper left) and the green square photo (60%, turned counterclockwise 10 degrees) appear; at 0:02 the blue tall video, turned 12 degrees clockwise at 80%; at 0:03 the lower-left 4:3 photo; at 0:04 the purple corner video at lower right (90%). They leave at 0:07, 0:08, 0:09, 0:09 and the end.
2. Stacking: the photo is in front of the blue video, the blue video in front of the red one (later layer is in front). Captions, when there are any, are in front of every overlay.
3. Opacity: the photo (60%) and the blue video (80%) show the main video through them.
4. Seek to 0:05, then 0:09.5, then back to 0:00; scrub across 0:01 to 0:09. Overlays appear and vanish on the second, with no black rectangle and no frozen last frame of an overlay after it ended (the Media3 compositor keeps a finished sequence's last frame unless it is hidden: the thing most likely to go wrong).
5. Play to the end twice: no stutter you can see, no error in logcat.
6. Export it, then compare a frame:

```sh
adb shell am start -S -n app.memix/.android.MainActivity --ez memix.exportLatestDraft true --ez memix.exportWatermarkFree true
adb logcat -s MemixExportCheck:* MemixVideoEngine:*      # "export: done", pull the path it prints
ffmpeg -ss 5 -i export.mp4 -frames:v 1 frame5.png      # same frame as the preview at 0:05; compare with overlay-check-5s.png by eye
ffprobe -v error -show_entries stream=codec_name,width,height,r_frame_rate,duration -of compact export.mp4
```

Pass: the same overlays at the same places, sizes, turns, opacities and stacking as the preview. Note that with overlays the frame rate is 30 whatever the sheet asks (expected, see Limits). Record the export time.

### 3. Five overlays at 1080p, 30 fps (the Done when of P4-01)

Release code, not debug: `./gradlew :androidApp:installBenchmark`, test files pushed, then:

```sh
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor overlay-perf
adb logcat -s MemixVideoEngine:* MemixPreview:*          # the first lines: "Video decoder budget N (hardware decoders report [...])"
```

`overlay-perf` is the 1080p clip with five more 1080p videos over it at once (six decoders), each a quarter-size tile. Wait for the first frame, press Play and let it play 10 s. Measure the frames that reach the screen (the `MemixPreview` play-span line counts one decoder only, so don't use its fps):

```sh
adb shell dumpsys SurfaceFlinger --list | grep -i -E "SurfaceView.*app.memix"      # the preview's layer name
adb shell dumpsys SurfaceFlinger --latency "<that layer name>" > before.txt
# play 10 s
adb shell dumpsys SurfaceFlinger --latency "<that layer name>" > after.txt
```

Count the distinct frame timestamps (column 2) in 10 s of playing, fps = count / 10. Write down: phone model, Android version, build type (`benchmark`), the budget line from logcat, median and worst of 3 runs, and whether the picture stutters by eye. Pass: 30 fps or more. If the budget line says less than 6 (say 4), expect the lowest overlay videos to be missing (`Overlays left out of the preview: [...]` in logcat): that is the rule working; write the number down and tell the principal engineer, because the "five at once" target then depends on a proxy decision. Also note memory (`adb shell dumpsys meminfo app.memix`, "TOTAL PSS" before and while playing) and heat after three runs.

### 4. Things to look at because they could not be verified

- **FIRST, high priority: which input is primary.** Media3 takes the first *registered* input as primary (it paces the output and is drawn on top), and the design needs the stage sequence to register first. Nothing here proves it. Failure sign: overlays never show, or the main video covers them. Logcat tag `MemixVideoEngine` also prints "The primary compositor input is W x H, not the stage's 16 x 16" once. If it fails, stop and tell the principal engineer before any other check.
- **Gap-only primary sequence.** The stage is a video sequence that holds only a gap. The code reads as supported (Media3 builds a gap sequence from blank frames) but nobody ran it here. Failure signs: the preview never shows a frame (stays on the spinner), `fail` lines in logcat tag `MemixPreview`, or the export failing with `Unexpected`. Tell the principal engineer, with the logcat.
- **Player swap.** Open a draft with no overlays, then (when the 01b UI exists) add the first overlay: the picture blinks for a few frames while the player is replaced. A crash or a stuck preview there is a bug.
- **Trim drag with overlays.** Dragging a main clip's trim handle shows a one-clip composition on the same player (which then has the multi-input graph). Check the drag still shows frames while `overlay-check` is open.
- **HDR overlay.** An HDR phone clip as an overlay is untested; Media3's compositor rejects HDR textures. Try one if the owner's phone shoots HDR.
- **Frame pace.** With overlays the output paces at 30 fps (the stage's blank frames): a 60 fps export is a 30 fps picture; accepted for now. Experiment worth trying later: replace the stage gap with a 1 x 1 transparent image item at the export frame rate, and see if the output follows it.
- **Edit latency (with `overlay-perf`).** Time to first frame after opening the draft, and after an edit (use `memix.openEditor` then any edit once the 01b UI exists; the `ShowTimingLog` lines in `MemixPreview` give both). Also scrub latency: drag the playhead across the timeline and note the delay per update (target under 100 ms to a new frame). P4-01b keeps drags on the stand-in and commits on release, so these are the cost of the commit and the scrub.
- **Left-out overlays.** The preview state carries `skippedOverlayIds` (`PreviewPlayback`) for P4-01b to show; for now only the logcat line "Overlays left out of the preview" says so.
- **Sound.** Done in P4-01b (see the P4-01b section below).
- **Missing file.** Delete `files/debug-media/photo.png` and reopen: the photo overlay turns into nothing (a gap) and the preview says a file is missing; the rest still draws.
- **Old drafts.** Open a draft saved before this build (any video draft from Phase 3): it opens and looks the same. A draft saved from here opens in the previous build? No: schema 14 says "Needs a newer version of Memix" there, as designed.

## P4-01b · overlay layers in the video editor

Nothing here was run on a device or emulator (this machine has neither). The Gradle checks, a JVM harness for the edits, the plan and the editor logic (203 checks) and Compose renders of the panels, bars, lanes and stage (`docs/ux/reviews/P4-01b/`) were done. Everything below needs the owner's phone, on a `benchmark` build, with the debug draft `overlay-check` (open with `memix.openEditor`) unless said otherwise.

### 1. First, does it work at all
- Tap **Overlay** (fifth tool): the photo picker opens, one item. Pick a video: it lands at the playhead at 50%, centered, selected, with its frame and handle; the bar switches to **Position, Opacity, Delete, Split, Volume, Layer order, Move here, Duplicate, Detach audio**. A second pick lands lower right. **Failure signs:** the picker doesn't open (the `Overlay` request in `EditorMediaPickers`), the overlay is on the timeline but never on the stage (P4-01a's "primary input" log line `The primary compositor input`), or it lands behind the main video.
- Undo after each of: add, drag, Position tap, Opacity release, Bring forward. Each must undo exactly that step (the toast names it).

### 2. The stand-in (no double image)
- Drag an overlay slowly over a bright main video, filmed or screen-recorded, stepped frame by frame around the touch-down and the release. **Pass:** no second copy of the overlay while the finger is down; no jump or flash at the release. **Failure signs:** a faint copy at the old place during the drag (the compositor's hide didn't take: `OverlayVisibility` and the paused re-render), a one-frame flash at the release (the stand-in cleared before the preview caught up, `OverlayControl.onPreviewCatchingUp`), the overlay vanishing at the touch for a moment (its picture wasn't ready; it should then stay drawn by the preview).
- The stand-in's frame of a video overlay is the one at the paused playhead: look for a visible difference between it and the live frame when the video moves a lot (known and accepted: it is a still).
- Pinch with two fingers anywhere on the stage, twist, and drag the handle: bubble "% - deg" shows for size and turn, snap guides and a tick at the canvas center, at the canvas edges (new: the overlay's side flush with an edge) and at 0/90/180/270 degrees.

### 3. Sound (new in this ticket)
- An overlay video with speech: hear it with the main video's sound (preview and the exported file). Volume tool: the slider is heard live. **Failure signs:** nothing from the overlay (the layer sequence's audio track types), the first overlay silent, a click or a gap at its start, a crash on adding the second overlay video with sound (the codec count).
- **Five overlay videos with sound at once** (`overlay-check`, if it has sound; else five clips): does the preview still play at 30 fps and does audio stay in sync? Audio decoders are not in the budget; if this fails, count them.
- Detach audio on an overlay: its sound moves to an Audio lane and the overlay goes silent; undo puts it back.
- Mute the layer? (There is no per-layer mute in the UI; only the original-audio toggle of the main track, which does not mute overlays.) Say whether it should.

### 4. Opacity
- Opacity panel while the video plays: the blend changes live, with no stutter; release = one step. At 10% over a bright main clip it is faint, not gone. **Failure sign:** the overlay snaps to its old opacity for a moment after the release (the committed composition's first frame; look for a flash).

### 5. Position and Layer order panels
- Position at 360 x 640 and 360 x 800 scrolls (the spec's accepted floor); held arrows repeat and move the overlay (stand-in) and commit once on release.
- Two overlays overlapping: Bring forward and Send back swap their stacking; the lanes follow (a lane may appear or go); at the front or back the toast says so. **Not built:** the lane slide and the scroll that keeps the moved clip in view.

### 6. Timeline
- The overlay lane: 28 dp thumbnails (videos show different frames along the clip), `track-video`, the white outline and handles when selected. With five overlay lanes plus a caption lane the lanes scroll and the **Add a meme sound row is pinned at the bottom**; the pill stays centered on the playhead. **Failure signs:** the pill covers the last lane, the pill missing, a sound can't be dropped on a new lane (not supported while pinned).
- Delete the overlay's file (`files/media/<project>/...`) and reopen: the lane says "File missing", nothing on the stage, one toast, and the bar starts with Delete.

### 7. The ratio change
- 9:16 to 16:9 with an overlay inside the frame and one hung off an edge: the first moves in to touch the edge, the second keeps its place; the toast "Layers moved to stay inside the frame." (The string is P1-11's `canvas_layers_moved`, whose wording the designer is changing to "Layers".)

### 8. Left-out overlays
- Add more overlay videos than the phone decodes (`overlay-perf` has ten): a toast says some are left out; they are also missing from an export. The wording is the engineer's proposal (`overlay_skipped_toast`): designer to confirm.

### 9. Things that could not be verified
- The overlay audio on a real phone (decoder count, sync), and that Media3's compositor honors a changed `OverlaySettings` alpha mid-clip while paused (the opacity slider and the stand-in's hiding depend on it; the re-render is a seek to the current position, as the caption switch does).
- Haptics, TalkBack order and the focus after the picker closes; reduce motion.
