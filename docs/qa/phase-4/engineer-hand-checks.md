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
- **Sound.** Overlays are silent in this ticket. The P4-01b spec expects their sound (default volume 100%): needs a decision (see the commit message).
- **Missing file.** Delete `files/debug-media/photo.png` and reopen: the photo overlay turns into nothing (a gap) and the preview says a file is missing; the rest still draws.
- **Old drafts.** Open a draft saved before this build (any video draft from Phase 3): it opens and looks the same. A draft saved from here opens in the previous build? No: schema 14 says "Needs a newer version of Memix" there, as designed.
