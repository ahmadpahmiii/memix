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

## P4-02a · keyframes, model and interpolation

Spec of the editor half: `docs/ux/specs/P4-02b-keyframes.md`. Design notes: `docs/TECHNICAL_DESIGN.md` → Keyframes. Reference renders: `docs/qa/references/P4-02-keyframes/`. The keyframe UI is P4-02b (below): the draft `keyframe-check` is opened by a debug hook (`installDebug` or `installBenchmark`; the export hooks need debug). Nothing here has run on a phone or emulator.

### 0. Test media

On top of P4-01's files (`clip-1080p.mp4`) make two more on the desktop and push them into `files/debug-media/` like the others:

```sh
ffmpeg -v error -y -f lavfi -i "color=c=0x2B6EA8:s=1080x1080" \
  -vf "drawgrid=w=90:h=90:t=3:c=white@0.8,drawtext=text='GRID':fontsize=220:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2" -frames:v 1 grid-12.png
ffmpeg -v error -y -f lavfi -i "sine=frequency=1000:duration=10:sample_rate=48000" -af volume=0.5 -c:a aac -b:a 128k tone-1k.m4a
```

### 1. What was checked without a device

A JVM harness (not in the repo; it compiles the repo's `:core:model`, `:core:domain`, the project codec and the engine planner and placement): 151 checks, all pass. Covered: schema 15 and the 14 to 15 migration (old JSON without `keyframes` opens at 15, no keyframes anywhere), the round trip of every easing and an unwrapped 720 degree turn, interpolation (each easing against an independent CSS cubic-bezier within 1e-5, geometric scale, linear rotation, hold, hold-before/after, a center outside the frame), `interpolatedVolume` equal to `interpolatedLook(...).volume`, every edit and that each is one undo step (add, remove, move with its range, easing, clear, the auto-key rule with one or two keyframes), the 50 limit, the 33 ms spacing, the clamps per kind of item, split (continuity across the cut; linear exact, smooth within 6%), trim of the start and end (motion unchanged), duplicate, move, Detach audio, the ratio change, the planner (keyframes, the head cut, sound played by the loudest keyframe, the P4-16 log line, captions), the drawn center, turn, scale and alpha through a copy of Media3's matrix chain, and the volume ramp (-12, -6 and -2.5 dB at 0.5, 1.0 and 1.5 s; the biggest step between two samples is under 2e-5; the gain is the curve at the edges of 64-frame blocks, joined linearly). The reference strips come from the same harness. `:androidApp:assembleDebug`, `lint`, `compileCommonMainKotlinMetadata`, `:core:data:verifySqlDelightMigration` and `:androidApp:bundleRelease` ran green. The Android-only code (`KeyframedGainProcessor`, the per-frame compositor settings, `CaptionOverlay`) is compiled but not run.

### 2. The motion check draft, preview against export

```sh
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor keyframe-check
adb logcat -s MemixPreview:* MemixVideoEngine:*
```

The draft: the 10 s 1080p clip (silent); the grid overlay from 1 s to 7 s whose keyframes sit at 1 s and 4 s on the timeline (center (0.2, 0.2) to (0.8, 0.8), scale 40% to 80%, turn 0 to 180 degrees, opacity 100% to 30%, smooth); a caption "POP" from 5 s to 9 s that grows from 50% to 100% in its first second; a 1 kHz tone (audio lane, 0 to 10 s) whose volume goes 0 to 100% by 2 s, holds to 6 s, then 25% at 8 s, all linear. Open the editor, press Play, watch: the overlay glides, grows, turns and fades between 1 s and 4 s, then holds; the caption pops at 5 to 6 s; the tone fades in over 2 s. Compare the preview frame at 1.5, 2.5, 3.25 s and 5.5 s with the strips in `docs/qa/references/P4-02-keyframes/` (`ease-smooth` is the same curve with other numbers: the center at 2.5 s must be halfway, (0.5, 0.5)).

1. **Frame-stepped preview.** Pause, scrub to 1.0, 2.5 and 4.0 s: the overlay is at its first look, halfway, and at its last look. At 0.5 s it isn't there yet, at 5.0 s it holds the last look.
2. **Export and compare.** Export, pull the file, take frames at 1.0, 1.5, 2.5, 3.25, 4.0, 5.5 s:

```sh
adb shell am start -S -n app.memix/.android.MainActivity --ez memix.exportLatestDraft true --ez memix.exportWatermarkFree true
adb logcat -s MemixExportCheck:* MemixVideoEngine:*      # "export: done", pull the path it prints
for t in 1.0 1.5 2.5 3.25 4.0 5.5; do ffmpeg -v error -y -ss $t -i export.mp4 -frames:v 1 frame-$t.png; done
```

   Pass (spec Measured): the overlay's position within 1% of the width, scale within 1%, turn within 1 degree, opacity within 3/255 of the preview frame at the same time, and the caption's size at 5.5 s the same in both. A visible step between a preview frame and the export frame at the same time is a failure: say which property.
3. **Volume.** Play with headphones: the tone starts silent and rises over 2 s with no clicks, holds, and drops to a quarter by 8 s. Export and measure the levels:

```sh
ffmpeg -v error -y -i export.mp4 -vn -af "astats=metadata=1:reset=24000,ametadata=print:key=lavfi.astats.Overall.RMS_level:file=-" -f null - | grep RMS_level | head -60
```

   Pass: around 0.5, 1.0 and 1.5 s the RMS level is about -12, -6 and -2.5 dB below the level at 3 to 6 s (within 0.5 dB), in the preview (listen) and in the file. Seek to 1.5 s in the preview while paused, then press Play: the level continues from the ramp's value (the stream position after a seek is the thing most likely to go wrong, with a sound that has a tail).
4. **Keyed volume on a clip that isn't first.** Make a draft whose main track has two clips with sound, give the second one a volume ramp (0 to 100% over 2 s) with the keyframe code or a copy of `keyframe-check` edited that way, and do the same on an overlay video that starts at 3 s. In the preview, seek into the ramp (second clip's 1 s) while paused, press Play: the level continues from the ramp's value there, not from 0 and not from the clip's end. Export and measure with the ffmpeg command in step 3: the ramp starts when the clip starts. (The source says the stream position Media3 flushes with is inside the item, so no correction is made; this is the check that proves it.)
5. **Seek and loop.** Seek back and forth across 1 to 4 s several times while playing: the overlay always lands on the curve (no stale look), the tone's level follows the position.

### 3. Things to look at because they could not be verified

- **Per-frame overlay settings.** `getOverlaySettings(inputId, presentationTimeUs)` is read per frame; this ticket relies on the time being the composition's (the layer timeline already relied on it). If a keyed overlay jumps or sticks, tell the principal engineer.
- **Caption cost while it moves.** A keyframed caption redraws the frame-sized canvas overlay each frame (a still caption doesn't). Play 5 to 6 s of `keyframe-check` in a `benchmark` build: `adb shell dumpsys gfxinfo app.memix framestats` and SurfaceFlinger fps as in P4-01a §3; write down the device, the median fps, and whether it stutters. If it does, the next step is a bitmap overlay per keyed caption (TECHNICAL_DESIGN → Keyframes).
- **Allocation.** A keyed overlay builds its `OverlaySettings` per frame on the GL thread. Watch for GC pauses in `adb logcat -s art` while a keyed overlay plays.
- **Volume after a tail.** `KeyframedGainProcessor` takes a sound's echo tail out of the position like `FadeGains` does (Media3 counts from the tail). Keyframe a sound that has Echo on, in the preview and the export, and look for the ramp starting late or early by the tail length.
- **Main clip's framing keys.** A keyed main clip's position, size and turn aren't drawn until P4-16 (logcat line "Not rendered yet: keyframed position, size and turn of main clip"); its volume plays.
- **Sticker keys.** Stickers in a video are drawn from P4-12 on; their keyframes are only stored.
- **Old drafts.** Open any draft saved before this build: it opens and looks the same. A draft saved from here opens in the previous build? No: schema 15 says "Needs a newer version of Memix" there, as designed.

## P4-02b · keyframes, editor

Spec: `docs/ux/specs/P4-02b-keyframes.md`. Notes: `docs/TECHNICAL_DESIGN.md` → Keyframes, editor half. Screenshots (desktop renders, not a phone): `docs/ux/reviews/P4-02b/`. Use the `keyframe-check` draft (P4-02a section) on the owner's phone, release or benchmark build. Nothing here has run on a phone or emulator.

### 1. What was checked without a device
A JVM harness (not in the repo; it compiles the real feature module on the JVM with a fake edit host): 43 checks, all pass. Covered: add, remove, move (Move here and a drag release), Easing and Clear all are one undo step each and Undo takes exactly that; Previous and Next with and without a neighbor; the toasts for Add outside, Move blocked, nothing selected, Easing with none or on the last; the first edit with the bar open makes two keyframes in one step, named after the edit and logged `keyframe_add`; an edit on a keyframe updates it and logs its own id; the static fields (`transform`, `opacity`, `volume`) are never written while keyed (overlay gesture, Position step, Opacity to 0%, sound Volume, caption gesture); a keyed center may leave the frame; which properties each item's bar offers. Desktop renders of the bars, marker row, Easing panel, notes, status lines, es and hi, 200% font.

### 2. Add, move, delete, each one undo step (the Done when)
1. Open `keyframe-check`, tap the overlay, scroll the bar to Keyframes, tap it. The bar replaces the tool bar; the overlay's lane grows a marker row (lanes slide 200 ms); at 360 x 640 the lanes scroll to show it.
2. Scrub to 1 s, tap Add keyframe: a diamond appears within 100 ms, white and large; the tool now reads Remove keyframe in blue. Undo once: it goes.
3. Scrub to 3 s, drag a diamond: the time bubble shows, snaps tick at the playhead and other keyframes, it stops one frame before a neighbor, one buzz at pick-up. Release, Undo once: it goes back.
4. Select a diamond (tap, Previous or Next), scrub elsewhere, Move here: one step. Remove keyframe on a keyframe: one step. Clear all: one step, Undo brings all back.
5. Auto-key: Keyframes, scrub to 1.5 s, drag the overlay on the stage: two diamonds (start and 1.5 s) and "Keyframes on..."; drag at 3 s: a third and "Keyframe added at 0:03.0". Undo takes the edit and its keyframes together. Play: the overlay glides.
6. Opacity panel on a keyed overlay: the note, the slider reaches 0%; Position panel arrows move the overlay at the playhead; a sound clip's Volume at two times ducks it (listen).
7. Easing: put the playhead on the first keyframe, Easing, tap each tile: the segment plays once and stops at the next keyframe (tap the stage to stop it); the tapped tile is one step; a tile already chosen only replays. Easing on the last keyframe or with none explains.
8. Badge: close the bar, select another item: the keyed overlay wears a small diamond at its top-right corner.

### 3. Numbers to take (release or benchmark, the owner's phone)
- Janky frames below 5% for three 3 s drags of a keyed overlay on the stage (`dumpsys gfxinfo`). The stage follows the keyframes through a derived state, so a keyed overlay recomposes the stage per frame while playing: check this is smooth.
- Time from the Add tap to the diamond on screen (under 100 ms).
- LeakCanary: open the bar, add, drag a diamond, change easing, clear, close the editor, five times.

### 4. Things that could not be verified
- Everything on a phone: gestures, haptics, TalkBack (marker group and actions, announcements), reduce motion, 360 x 640 and 800 lanes.
- The lanes' scroll-into-view when the bar opens, and the slide, are only seen in the renders as their end state.
- Playing a segment from the Easing panel (seek, play, stop at the next keyframe).
- Delegated: the diamond fade, the selected diamond's size animation, edge auto-scroll while dragging, the spoken new node after Previous or Next, and `explain_fit_keyed` (needs P4-16's per-clip Fit tool) are not built; see the TECHNICAL_DESIGN limits.
