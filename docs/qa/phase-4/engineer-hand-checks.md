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

## P4-04 · green screen keying

Spec: `docs/ux/specs/P4-04-green-screen.md`. Notes: `docs/TECHNICAL_DESIGN.md` → Green screen. Screenshots (desktop renders, not a phone): `docs/ux/reviews/P4-04/`. Reference renders: `docs/qa/references/P4-04-green-screen/`. Scenes: `--es memix.openEditor greenscreen-check` (the 10 s 1080p clip with three `chroma-check` pictures over it: green, blue already keyed with Blue, off-green `#6BB536`) and `greenscreen-main` (the green picture as the main clip over the canvas Image background). The pictures are drawn on first use into `files/debug-media/` (PNG, `ChromaCheckFixture`). For a video fixture: `ffmpeg -loop 1 -i chroma-check.png -t 4 -r 30 -vf noise=alls=4:allf=t -c:v libx264 -pix_fmt yuv420p chroma-check.mp4` (desktop only), push it to `files/debug-media/` and swap the media in `greenScreenCheck()`.

### 1. What was checked without a device
JVM harnesses (not in the repo): 37 checks on the key math, edits, JSON round trip and the 15 to 16 migration (an old draft's JSON without `chromaKey` opens unkeyed), the pick mapping (overlay turn and scale, main Fit), and the fixture's Done when; 37 checks on the real `GreenScreenControl` with a fake edit host (opening keys at the defaults as one step, swatch, slider release, Turn off, Undo closing the panel, pick mode, miss banner, "nothing found"). All pass. On the CPU mirror at the defaults: backdrop cleared 100% (need 99.5), subject kept 100% (99), strand centers kept 100% (99), band G - max(R, B) 0/255 (6). The Android shader (`ChromaKeyEffect`, `BackdropEffect`) and everything on screen were compiled, not run.

### 2. The Done when, on a phone
1. Open `greenscreen-check`, tap the green picture, Green screen (fourth tool): keyed at once, backdrop gone, the main video shows through, no green rim. Undo once: unkeyed and the panel closes.
2. Strength and Edge softness: the matte follows the thumb live (playing and paused); one step per release. Blue swatch on the green picture: nothing left to remove, the banner "Nothing to remove here" after 600 ms or after the color change.
3. Eyedropper on the off-green picture: banner, playback pauses, press shows the loupe above the finger, release picks (one step, Picked swatch). Release outside the picture: the miss banner for 2 s. Eyedropper again, Done or back ends the mode.
4. `greenscreen-main`: Green screen on the main clip: the canvas background picture shows through. With Color background: the color. With Blur: black (delegated, below).
5. Turn off: one step, panel closes. Opacity at 50% on a keyed overlay: the keyed alpha is multiplied.
6. Export 1080p30 and compare frame 1.0 s with the preview and `docs/qa/references/P4-04-green-screen/default-key.png` (alpha within 3/255 for 99% of pixels, per the spec).
7. Drag a keyed overlay: its picture stays where it was until release (no stand-in, below).

### 3. Numbers to take (release or benchmark, the owner's phone)
- Preview of a keyed 1080p overlay over the 1080p main video at 30 fps or more; janky frames under 5% for three 3 s Strength drags.
- LeakCanary: open the panel, pick, drag both sliders, Turn off, close the editor, five times.
- The owner's real green clip, defaults only or Strength or Softness alone.

### 4. Things that could not be verified
- Everything on a phone: the GL pass (orientation, `Presentation` alpha through the compositor, the picture backdrop's flip), the pick's loupe, haptics-free gestures, TalkBack, reduce motion.
- Delegated: Blur background under a keyed main clip is black; a keyed overlay has no drag stand-in; the pick reads a 960 px frame from the thumbnail reader rather than the decoder's own frame; "nothing found" uses the whole source frame.

## P4-03b · shape masks

Spec: `docs/ux/specs/P4-03b-shape-masks-and-blend.md` (mask half; blend is P4-03a, deferred). Notes: `docs/TECHNICAL_DESIGN.md` → Shape masks. Screenshots (desktop renders, not a phone): `docs/ux/reviews/P4-03b/`. Reference renders: `docs/qa/references/P4-03-masks/`. Scenes: `--es memix.openEditor mask-check` (the 10 s 1080p clip with four masked `mask-grid` overlays at the corners and an unmasked one in the middle) and `mask-main` (the grid as the main clip, a Circle, over the canvas Image background). The grid is drawn on first use into `files/debug-media/`.

### 1. What was checked without a device
JVM harnesses (not in the repo): 64 checks on `MaskMath` (each shape's edge, softness, Invert, rounded corners, portrait boxes), the edits, Position steps and limits, box geometry, the JSON round trip and the 16 to 17 migration (a draft's JSON without `mask` opens unmasked); 60 checks on `MaskControl` (one undo step each, value ids, held steps, explanations, panel sync), the gesture arithmetic (move, handle, edges, band, pinch, snaps) and the stage's hit testing. The reference renders were drawn from the same arithmetic; the Done when's edge numbers were measured on them (edge within 1% of the box width, soft edge band within 2% of the shorter side).

### 2. The Done when, on a phone
1. Open `mask-check`: each corner overlay shows its mask as in the references (rounded rectangle, soft circle, inverted turned Linear, soft turned Mirror). Export 1080p30, frame at 1.0 s: edge within 1% of the box width on three samples, inside 253/255 or more, outside 2/255 or less away from the edge band, preview and export within 3/255 on 99% of pixels. The top risks: the shader's y flip (the Linear arrow's side), the turn direction, and the main clip's box on Fit.
2. Tap the middle overlay, Mask (after Green screen): the panel opens on Shape with None selected. Pick each shape: it appears at its defaults with its frame and handles; one undo step each. Edge softness, Invert, Rounded corners: live while dragging, one step on release.
3. Stage: drag inside moves 1:1; the corner handle sizes and turns; the edge handles size one side; two fingers pinch, twist and pan; SegmentTick at the centre, the flush side and 0/90/180/270 degrees; a tap outside the clip's box plays or pauses; no stand-in flash; Undo is ignored while a finger is down.
4. Position tab: Width, Height, Rotation, Move, Center; the limit toasts; a hold repeats and is one step.
5. Done: the frame goes, the mask stays. Undo after each edit takes exactly that edit away. An old draft opens unmasked.
6. `mask-main`: the Circle on the main clip shows the canvas picture around it; with a Color background the color; with Blur: black (delegated). A keyed clip plus a Circle: the mask cuts the keyed subject.
7. TalkBack: tiles read "Circle, 3 of 5, selected"; the Position tab is the whole no-drag route; the spoken result after a step; the clip's description gains "Mask: Circle".

### 3. Numbers to take (release or benchmark, the owner's phone)
- A masked 1080p overlay over the 1080p main video at 30 fps or more; janky frames under 5% for three 3 s mask drags.
- LeakCanary: open the panel, pick shapes, drag, Invert, switch tabs, Done, close the editor, five times.

### 4. Things that could not be verified
- Everything on a phone: the GL pass (orientation, the main clip's box through `Presentation` and `BackdropEffect`), the gestures and haptics, TalkBack, reduce motion, the panel's pinned tabs at 360 x 640.
- Delegated: Blur background under a masked main clip is black; the flush snap needs the shape's axes aligned with the box's; the guides draw the centre lines only; mask parameters are not keyframed.

## P4-07 · video filters and adjustments

Spec: `docs/ux/specs/P4-07-video-filters-addendum.md` (on `P2-04-image-tools.md`). Notes: `docs/TECHNICAL_DESIGN.md` → Video filters and adjustments. Screenshots (desktop renders, not a phone): `docs/ux/reviews/P4-07/`. Reference renders, photo path and video twin side by side: `docs/qa/references/P4-07-video-filters/`. Scenes: `--es memix.openEditor filters-check` (three main clips, the 1080p video, the grid picture and the patterned 3:2 picture, and a 5 s overlay video, no looks) and `filters-mixed` (the clips already differ: Cinema 80% with a vignette on the video, Mono on the patterned picture, nothing on the grid, Vivid on the overlay).

### 1. What was checked without a device
A throwaway JVM harness (not in the repo; compiles `:core:model` and `:core:domain`): 245 checks, all pass.
- **Parity (the Done when).** The video twin (`VideoLookMath`, line by line what the two shaders compute) against the photo path (`ImagePipeline`) on three procedural 1080 x 1440 pictures (a chart with a gray ramp, skin and color patches and a hue sweep; a hard-edged picture; a soft one): all 20 filters at 100% and 50%, the six adjustments at -100, 0, +100 and 50, and all six plus a filter together, 198 cases: worst mean difference 0.221/255 per channel, 100% of pixels within 6/255 (and within 3/255), worst single pixel 2/255. The same look at 2160 halved against 1080: 100% within 3/255 on the soft picture, 99.2% on the chart. The sharpen kernel's merged taps equal the full kernel, and the tap count stays within the shader's 9 for every size from 240 to 4320 px short side.
- Edits and model: the all-clips edit, the mixed-state write (one setting to every clip, the rest per clip), a clip and an overlay, a sound refused, new clips taking the shared look (none when mixed or empty), Split, Duplicate and Replace keeping it, geometry kept when the look changes, Reset per tab, the analytics ids each step logs, a neutral look saved as none.
- Filter then key: the matte is identical with and without the look. Limited range: a studio-swing source expanded to full range gives the full-range still's look within 2/255.
- Builds: `assembleDebug`, `lint`, `compileCommonMainKotlinMetadata`, `verifySqlDelightMigration`, `bundleRelease` green.

### 2. The Done when, on a phone
1. **Same looks as the photo editor.** Open `photo-image-tools` and `filters-check`; for each of the 20 filters at 100%, and a sample at 50%, put the same still on a photo layer and as a main photo clip and compare (the main bar's Filters, a tile): same ids, same order, the same look. Export 1080p30 and pull the frame at 1.5 s: against the photo render at 1080, mean difference at most 2/255 per channel and 99% of pixels within 6/255 (the encoder's allowance); `docs/qa/references/P4-07-video-filters/filters-100-*.png` show what to expect. The six adjustments at -100, 0, +100 on the gray ramp.
2. **Main bar, every main clip.** `filters-check`, nothing selected: Filters (main bar, after Voiceover): the title reads "Filters, all clips", Filters tab, the strip shows the video's frame. Tap Noir: every main clip is Noir, the overlay is not; one undo step ("Filter, all clips"); Undo takes it from all three. Intensity: live while dragging, one step on release.
3. **A clip's own bar.** Tap the grid clip, swipe the bar to Filters (after Mask): the title reads "Filters"; Vivid on that clip only. The playhead jumps to the clip when it was outside it. The overlay's bar has Filters too and the look draws on the overlay only.
4. **Mixed.** `filters-mixed`, Filters from the main bar: no tile selected, no Intensity row, the hint under the tabs; Adjust shows the clip under the playhead. Tap Warm: all three clips become Warm, their sliders stay as they were. Drag Contrast: all three clips get that value, the filters stay.
5. **Adjust.** Brightness, Contrast, Saturation, Warmth, Sharpen, Vignette: live on the playing video (30 fps, no stutter), one step on release (steps of 10 with a keyboard or TalkBack). The first drag on a clip that has no look, or Sharpen from 0 on a clip with only color looks, builds one new composition (a one-off hitch, delegated); later drags are smooth.
6. **Reset** (header, per tab, for the panel's scope): Filters removes the filter, Adjust zeroes the six; one step; "Reset" in Undo.
7. **Other edits.** Add media after a look every main clip shares: the new clip has it; with different looks it has none. Split and Duplicate keep it, Replace keeps it, Ratio change leaves it. The timeline thumbnails show no color (geometry only). The clip's spoken description gains "Filter: Vivid" or "Adjusted".
8. **Pipeline.** A Fit clip (the 1080p video in 9:16 is a full-frame fit; the 3:2 picture is Fit with bars): the bars stay black and the vignette darkens the clip's edges, not the bars. A keyed overlay with a filter: the key is unchanged and the subject is graded. Blur background: the blurred copy has the look; under a keyed or masked clip Blur is black (P4-04, P4-03b).
9. **Old draft.** A draft saved before this build opens with no look; the media file's size and hash don't change.
10. **API 29.** The same on an API 29 emulator (the shaders are GLES 2).
11. **Analytics (debug log).** A tile: `tool_use` `filter` with the filter id (`none` on removal); each slider release: its own `adjust_*`; Reset: the ids that changed.

### 3. Numbers to take (release or benchmark, the owner's phone)
- A filter plus Adjust on the 1080p main clip with one 1080p overlay: preview at 30 fps or more (Media3 logs); an Adjust drag for 3 s three times: janky frames under 5% (`dumpsys gfxinfo`); the first five tiles within 300 ms of opening the Filters tab. Sharpen is the costly one (25 texture fetches per pixel at 1080 short side; 81 at 2160): **measure the 1080 and 4K sharpen cost on the benchmark build (`installBenchmark`), never debug**. If the preview can't hold 30 fps, the spec's lower-resolution fallback is not built; report the device.
- Editor equals export: a preview screenshot against the export frame, 99% of pixels within 3/255.
- LeakCanary: open Filters from the main bar and from a clip, change tiles and sliders, Reset, close the editor, five times.

### 4. Things that could not be verified
- Everything on a GPU: both shaders (pass 1's table lookup orientation, pass 2's generated kernel and the vignette centre), the live holder, the rebuild on the first drag, the effect order with a key, a mask and Fit, 8-bit rounding between the passes, API 29. The parity numbers are for the CPU twin; the twin is the shaders' math written out, not the shaders.
- The frame at the playhead for the tiles (read through the thumbnail reader), TalkBack, reduce motion, the pinned tabs at 360 x 640.
- Delegated: on an overlay the look runs after the scaling pass; the sharpen radius is capped at 3.99 px; the lower-resolution fallback is not built; Filters is after Mask on the clip bars (P4-16's tools are not built).

## P4-06a · video effects: the framework, Motion, Light and Color

Spec: `docs/ux/specs/P4-06-video-effects.md`. Notes: `docs/TECHNICAL_DESIGN.md` → Video effects. Screenshots (desktop renders, not a phone): `docs/ux/reviews/P4-06a/`. Reference renders (the CPU twin, 540 x 960): `docs/qa/references/P4-06-effects/`. Scenes: `--es memix.openEditor effects-check` (the 4 s scene clip, a caption "CHECK", a sticker item, no effects), `stack-two` (a zoom punch on lane 1 and a Deep-fry on lane 2 over the same second) and `stack-two-reverse` (the other way round).

### 0. Test media
Make the clip on a desktop (ffmpeg, not in the app) with `docs/qa/references/P4-06-effects/make-effects-check.sh 1 effects-check.mp4` and push it to `files/debug-media/effects-check.mp4`: 1080 x 1920, 30 fps, 4 s, no sound: a 12 x 12 grid of 90 x 120 px cells with a letter in each (row r starts at letter r), 7 colour bars under it, a white 120 px square that moves across and up and down. The same script with scale 0.5 and `png-seq` made the frames the references were drawn from. The draft carries 4 000 000 us and 1080 x 1920 as the measured facts.

### 1. What was checked without a device
A throwaway JVM harness (not in the repo; compiles `:core:model`, `:core:domain`, the project codec and `EffectSpans`): 140 checks, all pass.
- Envelopes: zoom punch at 80 ms is 1.35 at the default 70% (within 1%) and 1.0 at 600 ms; Steady ramps over min(80 ms, L/4); Decay about 2% at the end; Ramp full at the end then 0; intensity 0 is 0.
- Edits: landing at the playhead and ending at the end (under 0.5 s left, or past the end), the first free lane, Change effect keeping place, length and intensity, Intensity clamped, split, trim (minimum 0.1 s), move, duplicate and delete on effect items, a main-clip delete does not move effects, an unbuilt or unknown id adds nothing.
- The three-at-once rule: three overlapping are allowed, a fourth is not, back-to-back items don't count, a move onto three others exceeds.
- Persistence: effect items round trip through the project codec; a version-18 draft (no effect items) opens as version 19.
- The CPU twin (`EffectMath`): strength 0 changes nothing, deterministic, every one of the 15 changes the frame, opaque, the cover property (no canvas edge shows for Shake, Wobble, Bounce, Zoom punch, Slow push at three strengths over 400 time steps), size independence (a detailed frame at 540 x 960 against its half-size twin: mean difference under 6/255).
- Stacking: zoom then deep-fry differs from deep-fry then zoom (the references `stack-two-*.png`).
- **Photosensitivity** (WCAG 2.2 SC 2.3.1; frame-by-frame mean relative luminance of the scene with the effect, 30 fps, flashes counted as pairs of opposing changes of at least 10% of the range): Flash 100%: luminance 0.223 to 0.854, 1 flash; Flash default 80%: 1; two Flash items 0.34 s apart: 2; three: 3 (the most any second can hold). Light pulse 100% and default: the swell is under 0.10 on this scene (0.225 to 0.285), 0 flashes. The Glitch and Static plots belong to 06b.
- Builds: `assembleDebug`, `lint`, `compileCommonMainKotlinMetadata`, `verifySqlDelightMigration`, `bundleRelease` green.

Not checked: **everything on a GPU** (all the shaders, the ping-pong targets, the grain texture, the glow's quarter-size passes, the framebuffer query), the live preview path on a player, the export, API 29, the tile frame read, TalkBack, reduce motion, LeakCanary, the timeline lane drawing and drag. The GLSL was not run through a validator.

### 2. The Done when, on a phone
1. **The shaders compile.** Open `effects-check`, add every one of the 15 effects once (Effects, a tile). Logcat tag `MemixPreview`: no shader or GL error; the preview shows each effect. A blank or frozen picture means a shader failed: note which.
2. **Each effect matches its reference.** For each of the 30 reference files (`<id>-100-<ms>.png`, `<id>-50-<ms>.png`) place the effect on a 2 s item at 1.0 s with the intensity (Intensity panel, steps of 10%: 100 and 50), pause at 1.0 s + the file's ms in the preview, and compare: the picture should match the reference up to the caption's font. Export 1080p30 and pull the frame at the same instant (`ffmpeg -ss <1.0 + ms> -i export.mp4 -frames:v 1 f.png`): scaled to 540 wide it matches the reference within 3/255 per channel for 99% of pixels (the encoder's allowance; the caption is the one difference). Preview and export frames equal within the same allowance.
3. **Zoom punch, the headline.** Scrub to 1.5 s, Effects, Zoom punch: the sheet closes, the item lands selected on a new violet lane (bonk, the lanes scroll to it, TalkBack says "Effect added"), 0.6 s long at 70%. At 80 ms into the item the picture is 1.35x (measure the grid cells: 90 px become 121.5 px at 1080 wide) and back to 1.0x at 0.6 s.
4. **Intensity.** Select the item, Intensity (3rd tool after Delete): the panel opens with the effect's name, the slider at 70%, one line of description; drag while the video plays: the picture follows live, one undo step "Intensity" on release (steps of 10 from a keyboard). 0% shows the video untouched (compare the exported frame with the unaffected one: identical).
5. **Change effect.** Change effect (bar): the sheet titled "Change effect", the current tile ringed white, a tap on it does nothing, another tile swaps it in place with the same length and intensity; one undo step.
6. **Stacking.** `stack-two` and `stack-two-reverse`: the two frames at 1.08 s differ, in lane order (references `stack-two-zoom-then-fry.png`, `stack-two-fry-then-zoom.png`). The caption "CHECK" is fried and zoomed with the rest. With a free export (the watermark on) the "Memix" mark is not fried or moved. Add a third overlapping effect: it lands. A fourth: nothing lands and the toast reads "Three effects at once is the most. Move one first." Drag an item onto three others, trim one to overlap three, duplicate one onto three: each is refused with the same toast and the item stays where it was.
7. **Ripple.** Delete the first main clip: the effect items keep their times.
8. **The sheet.** Tabs Motion, Light, Color only (no Glitch, Blur or Retro). Opens on Motion, then on the last tab for the visit. Tiles are the frame at the playhead with each effect at its peak; a tile not made yet is a plain square and still works. 3 columns at 360 dp; at 360 x 640 the grid scrolls. Spanish and Hindi at 200%: names wrap, the grid scrolls (desktop renders in `docs/ux/reviews/P4-06a/`).
9. **The lane.** The violet Effects lane, the effect's name on the item; trim handles and drag as every item; selected is white; an item past the end of the video has the wash.
10. **Old draft.** A draft saved before this build opens with no effects and no effect lane.
11. **Landing near the end.** Playhead 0.3 s before the end, Flash: it lands ending at the end, 0.25 s long. Playhead at the end: the same. A 0.4 s video, Slow push: 0.4 s long from 0:00.
12. **Analytics (debug log).** A tile: `tool_use` `effect_add` with `value` the effect id; Change effect: `effect_change` with `value`; an Intensity release: `effect_intensity`; Delete: `effect_delete` (not `delete`); moves, trims, splits, duplicates: the shared ids.
13. **LeakCanary.** Open the sheet, switch tabs, land, change, Intensity, delete, close the editor; five times: no leak (the tile frame reader and the tiles are released with the sheet).
14. **Flash rate.** Place three Flash items within a second: they land; a fourth within that second is refused with "Flashes can't come faster than three a second. Move one first." (also by move, trim, duplicate).
15. **Old debug scenes.** `ExportCheckProject` and `SampleProject` carry a 400 ms zoom punch that now draws: their frames around 3.5 to 4 s differ from older pictures; don't compare them with old references.
16. **Export draws what the preview draws.** Export `stack-two` with and without the watermark; frames at 1.08 s equal the preview's.

### 3. Numbers to take (release or benchmark, the owner's phone)
- Perf items to watch: shader programs compile on first use of each effect (a hitch the first time), and the passthrough blit runs when an effects pass is idle between items.
- Each of the 15 effects alone at 100% over the 1080p main clip with one overlay: preview at 30 fps or more (Media3 `MemixPreview` play-span lines); then three of the heaviest stacked (Glow, Deep-fry, Spotlight): 30 fps or more; janky frames under 5% (`dumpsys gfxinfo`) during a 3 s Intensity drag. **Glow (four passes, 16 reads per low-res pixel plus two 2 x reach + 1 tap blurs on a quarter-size copy) and Deep-fry (a 3 x 3 grain read per pixel and the 25-tap sharpen) are the costly ones.** If a phone can't hold 30 fps the spec's lower-resolution preview is not built: report the device and the effect.
- Export 60 s at 1080p30 with a Deep-fry and a Glow item over the whole video: time against the same export without effects.
- Memory: the three full-size targets and the grain texture (about 8 MB each at 1080 x 1920, plus 0.5 MB) are made on first use and released with the player.

## P4-06b · video effects: Glitch, Blur and Retro

Spec: `docs/ux/specs/P4-06-video-effects.md`. Notes: `docs/TECHNICAL_DESIGN.md` → Video effects, second batch. Screenshots (desktop renders, not a phone): `docs/ux/reviews/P4-06b/`. References (CPU twin, 540 x 960) and the luminance table and plots: `docs/qa/references/P4-06-effects/` (README there). Scene: `--es memix.openEditor effects-check` as in P4-06a (make the clip as in P4-06a → 0).

### 1. What was checked without a device
The same throwaway JVM harness as P4-06a (extended): 300 checks pass, including the luminance runs.
- The twins: strength 0 changes nothing, deterministic, every one of the 30 changes the frame, opaque; 540 x 960 against 270 x 480 agree for the 06b effects except Old screen (its stripes are whole pixels).
- Randomness: the frame index rounds the time to the nearest 1/30 s (0, 16 666 us, 16 667 us, 33 333 us, 1 s); Film grain's field changes 24 times a second; the same frame time gives the same Static frame whatever the time into the item; the next frame differs; Static's noise is zero-mean (mean luma within 3%).
- Glitch: bursts are 3 frames, never more than 3 in any 30 frames (3000 frames checked), about 3 slots in 4 hold one; 6 to 10 slices 2 to 9% tall, shift up to 8% of the width; outside a burst the frame is untouched; burst frames differ from each other; 800 ms is the middle of a burst.
- Shapes: RGB split moves red right of green and leaves green alone; Tear's band is 12% tall, centered at 600 ms and nearer the top at 300 ms; Pixelate's block is one colour and a tiny strength changes less than a big one; Blur, Radial blur, Smear, Soft focus remove detail; Tilt blur keeps the middle row sharp and blurs the top; Blur at strength 0.01 is nearly the sharp frame; Blur's numbers at 1080p (sigma 27 px, 6.75 in the copy, reach 21); Mirror at 100% is the left half mirrored, at 50% folds at three quarters; Fisheye keeps the corners.
- **Shader safety**, with no validator: the generated sources of all 13 programs were written out and read by a script (every declared uniform is used, no bare integer literal in a float context, no int variable outside a loop header or `float()`, no reserved word as a name): clean; loop bounds are constants (the Gaussian's reach is written into the source).
- **Photosensitivity:** every effect of the three tabs at 100% and with three stacked, and Glitch + Static + VHS: at most 1 flash in any second (table and plots in the references README).
- Builds: `assembleDebug`, `lint`, `compileCommonMainKotlinMetadata`, `verifySqlDelightMigration`, `bundleRelease` green (see the commit message).

Not checked: **everything on a GPU** (all 13 new programs, the noise texture's `GL_REPEAT` read, the four-pass quarter-size chain, the shift uniforms), the preview's frame rate, API 29, the export.

### 2. The Done when, on a phone
1. **The shaders compile.** `effects-check`, add each of the 15 effects once (Effects: Glitch, Blur, Retro tabs). Logcat tag `MemixPreview`: no shader or GL error; each shows. A blank or frozen picture means a failed shader: note which.
2. **Each effect matches its reference.** For the 30 new reference files (`<id>-100-<ms>.png`, `<id>-50-<ms>.png`): add the effect as an item, set Intensity 100 or 50, **start the item at 0:00** for Glitch, Static, Film grain and VHS (so the hash sees the same time as the references), pause at the file's ms and compare; export 1080p30 and compare the frame scaled to 540 wide (99% of pixels within 3/255 for the effects with no noise; for Static, Film grain, VHS, Glitch the look and the amount of noise, not the grain; Old screen and VHS stripes can sit at another phase).
3. **Glitch.** A 1 s item at 70%: short bursts (about 3 a second at most) of sideways slices with red and blue split, the picture clean between them; the same frames on a second play-through and in the export.
4. **Tear.** A band 12% tall sweeps down every 1.2 s and shears sideways.
5. **Blur family.** Blur, Tilt blur (the middle band stays sharp), Soft focus (brighter, dreamy), Radial blur (streaks out from the center), Smear (horizontal): drag Intensity through 0 to 100% while playing; the picture follows with no jump at the low end.
6. **Retro.** VHS (scanlines, a band drifting up, noise, color fringes), Old screen (bent edges with a dark rim, scanlines, a vignette), Film grain (fine flicker 24 times a second), Fisheye (bulge, no edge shows), Mirror (the fold from the right edge to the middle with Intensity).
7. **Stacking.** Three at once with the heaviest (Blur, Radial blur, VHS): lands; a fourth is refused as in 06a.
8. **The sheet.** Six tabs: Motion, Glitch, Light, Color, Blur, Retro, each with five tiles; tiles are the frame at the playhead with the effect at its peak (Glitch at a burst's middle, Tear with the band at half height); at 360 x 640 the grid scrolls; Spanish and Hindi at 200%: names wrap (Desenfoque inclinado, Granulado de filme, Desfoque radial), tabs scroll sideways (desktop renders in `docs/ux/reviews/P4-06b/`).
9. **Old draft with a 06b id** (none exist yet): n/a. A draft from this build opened on 06a code would keep the items and skip them (not checkable here).
10. **LeakCanary:** open the sheet on each of the three new tabs, land, close the editor, five times: no leak. Also leave the editor with a Static or VHS item selected: the noise texture is released with the player (no GL error on re-entry).

### 3. Numbers to take (release or benchmark, the owner's phone)
- Each of the 15 new effects alone at 100% over the 1080p main clip with one overlay: preview at 30 fps or more (Media3 `MemixPreview` play-span lines); then three of the heaviest stacked (Blur x3: 12 passes; Radial blur + VHS + Blur): 30 fps or more; janky frames under 5% (`dumpsys gfxinfo`) during a 3 s Intensity drag. **Costs to expect** (per pixel of the full-size frame): Radial blur 16 reads, VHS 7, the Gaussian three 4 + 1 + the copy's 2 x 43 taps on a 270 x 480 copy, the rest 1 to 3. If a phone can't hold 30 fps, report the device and the effect (the lower-resolution preview is not built).
- Export 60 s at 1080p30 with Blur over the whole video against the same without effects.
- Memory: Blur, Tilt blur and Soft focus use the same two quarter-size targets as Glow; the noise texture is 64 KB; nothing else is new.

## P4-08 · transitions between clips

Spec `docs/ux/specs/P4-08-transitions.md`. Notes: `docs/TECHNICAL_DESIGN.md` → Transitions. Desktop renders of the UI (not a phone): `docs/ux/reviews/P4-08/`. Reference renders (the CPU twin, 270 x 480, one strip per transition at progress 25, 50 and 75% at its default length, plus the two scene pictures): `docs/qa/references/P4-08-transitions/`. **Nothing here has run on a GPU, a phone or an emulator: the shaders, the compositor input order (stage, overlays, main, partner) and the partner's timing are unverified on a device.**

Clips: `docs/qa/references/P4-08-transitions/make-transition-clips.sh <dir>` makes `transition-a.mp4` (lettered grid, moving white square, 440 Hz) and `transition-b.mp4` (colour bars, moving black square, 880 Hz), 6 s each; push both to `files/debug-media/`. Scenes (`--es memix.openEditor <name>`): `transitions-check` (both trimmed from 1.5 s: real footage each side of the cut at 3.0 s; a meme sound at 3.5 s), `transitions-hold` (3 s files: the edge frames are held), `transitions-photos` (two stills), `transitions-set` (Dissolve 0.5 s and Push left 1 s already set, a 1.2 s middle clip).

1. **Shaders compile.** Open `transitions-check`, select clip 1, Transition (swipe the bar), tap each of the 21 tiles in turn; logcat (`MemixPreview`, GL errors) must show no shader error for any of the 20. Top risk.
2. **Each of the 20 against its reference** at 25, 50 and 75% (export 1080p30, extract frames with ffmpeg at 2.75, 3.0, 3.25 s for 0.5 s; scale to 270 wide; 99% of pixels within 3/255). At the window start and end the frame equals plain A and plain B. Also `transitions-hold` (held edge, no judder at the cut), `transitions-check` (real footage both sides: the squares keep moving) and `transitions-photos`.
3. **Audio proof (the Done when).** On `transitions-check` export with each of the 20 at 0.5 s and 2.0 s and with none; decode each (`ffmpeg -i x.mp4 -f s16le -ac 1 -ar 48000 x.raw`): the none export minus each export is within -60 dB over the whole file; audio length within one frame of the video; no 1 ms stretch of digital silence within 1 s of the cut; 20 ms RMS within 0.5 dB of the tone on both sides. Repeat with a muted clip, a sped clip (after P4-09) and with the meme sound. The harness already proves the plan's audio fields identical (40 plans, difference 0.0); this checks the real graph.
4. **Length and sync.** Exported length with and without a transition is equal; the meme sound at 3.5 s is at 3.5 s in both.
5. **Preview.** Tap a tile: the cut plays once from 0.8 s before to 0.4 s after; a stage tap stops it; a second tap on the same tile replays with no undo step; a Duration release plays it. 30 fps or more through each window (play-span log, release or benchmark build); janky frames under 5% over a Duration drag. Blur through is the heaviest (49 taps).
6. **Rebuild feel.** Time from a tile tap to the first transition frame; Preview catching up shows after 1 s if slower.
7. **The cut's cue.** `transitions-set`: the chip sits on both cuts; select clip 1 or 2 and the adjoining chips go; reorder mode, a trim and a move hide it; zoom out until a neighbour is under 48 dp and it goes.
8. **Fit rules.** `transitions-set`: trim clip 2 to 0.9 s: the Dissolve shortens or Push left shortens with the 3 s toast, one Undo restores both; trim to 0.3 s: removed with the other toast. Split a clip with a transition: it stays on the right piece. Delete a neighbour: it goes. Duplicate: both cuts keep it. The last clip's Transition tool toasts; so does a cut with 0.04 s clips.
9. **Undo steps.** A tile, a release, None: one step each, named Transition, Transition length, Remove transition.
10. **Old draft.** Open a schema-19 draft: plain cuts, no tool error; save, reopen.
11. **Missing neighbour file.** The cut plays that clip as black without a crash. A held frame that can't be read leaves its side fading toward black (no crash).
12. **Flashing.** Dip to white shows one swell; Glitch cut at 0.4 s shows three short bursts.
13. **LeakCanary.** Open the panel, ten tiles, drag Duration, close; five times: no leak (the reader and the tiles are released with the panel). Export twice in a row: no leaked GL programs or decoders; the held-frame JPEGs are in `cache/transition-holds/`.
14. **TalkBack.** The clip says "Transition to next clip: Dissolve" last; its actions include Transition; the strip reads "Dissolve, 2 / 21".
15. **Decoder budget.** `overlay-perf` with a transition on a cut: the partner takes one decoder, so one overlay fewer may be skipped (toast).
16. **Flash-rate rule.** Two Glitch cuts within 1 s: the second tile tap is refused with the Flash toast and no step; a Dip to white beside Flash items counts with them (three events in any 1 s is the most). A trim that shortens a transition is never refused for that.
17. **Held-frame cache.** `cache/transition-holds/` names are stable across launches (no new files for the same cut after a restart) and files older than 7 days are gone after the first export or preview of a later run.
18. **Later refinements, not built.** The decoder budget takes one for the whole video while a partner exists; counting it only inside windows would free an overlay slot elsewhere.

## P4-09a · speed, constant 0.1x to 10x

Spec `docs/ux/specs/P4-09-speed.md` (09a part). Notes: `docs/TECHNICAL_DESIGN.md` → Speed. Desktop renders of the UI (not a phone): `docs/ux/reviews/P4-09a/`. Reference numbers and frames: `docs/qa/references/P4-09-speed/`. **What ran:** a desktop JVM harness (173 checks, scratchpad, not in the repo) over the model, the edits, the migration, the plan and Media3's own `SpeedChangingAudioProcessor` (pitch kept at 0.1x to 10x on a 1 kHz tone; length and A/V end drift; a negative control). **What did not run:** anything on a phone, an emulator or a GPU: that CompositionPlayer and Transformer play and export a speed item (it compiles against Media3 1.11.1), real A/V sync, the sound by ear at the extremes, the frame rate at 2x.

Clip: `docs/qa/references/P4-09-speed/make-speed-check.sh <dir>` makes `speed-check.mp4` (10 s, 1080 x 1920, source time burned in to the centisecond, a 1 kHz tone that steps down 3 dB each second and starts over every 5 s); push it to `files/debug-media/`. Scenes (`--es memix.openEditor <name>`): `speed-check` (three clips of the file: 0 to 2 s at 0.25x = 8 s, 2 to 6 s at 2x = 2 s, 6 to 10 s at 4x with Mute = 1 s; a meme sound at 8.5 s), `speed-plain` (the whole file at 1x: set the speed by hand), `speed-overlay` (the file at 1x and a half-size overlay of it at 2x, then a second overlay at 3.2 s).

1. **Speed in the export (the Done when).** Export `speed-check`: clip 1 is 8.00 s, clip 2 2.00 s, clip 3 1.00 s (11.00 s in all, within one frame each). Extract frames with ffmpeg: the burned-in number equals output time x speed within 33 ms (table in the references README). Cut each clip's audio: Keep clips keep 1 kHz within 1% and the level within 1 dB of the source; the Mute clip is under -80 dBFS RMS; the audio length equals the video's within 23 ms (ffprobe, then `ffmpeg -i x.mp4 -f s16le -ac 1 -ar 48000`). Repeat each speed alone on `speed-plain` (tiles 0.25x, 0.5x, 2x, 4x, 8x; slider 0.1x and 10x): length is source / speed.
2. **Preview.** Play each clip: the picture and the tone stay together (watch the burned-in number against the tone's level steps: the step lands when the number crosses a whole second). Seek into the middle of a 0.25x and a 4x clip: sound resumes in step. 2x and 0.5x of a 1080p clip at 30 fps or more (`MemixPreview` play-span lines, release or benchmark build). 10x: frames skip, the sound stays clean.
3. **Pitch by ear.** A voice clip at 2x and 0.5x: faster or slower, not a chipmunk or a monster. At 0.1x and 10x it is rough and the hint shows with Keep; Mute removes it.
4. **Mute.** Sound Mute on a clip: silent in preview and export; the Volume panel shows "Sound is off in Speed." at the top and its slider still moves; Keep again restores the sound; "Mute original audio" (the timeline toggle) and Volume 0 still silence it too.
5. **Lengths and ripple.** On `speed-plain` add a second clip: set the first to 2x, the second moves earlier; Undo once restores both. The meme sound in `speed-check` does not move when clip 1 changes speed (P1-06 rule).
6. **Overlay lane rule.** `speed-overlay`: select the first overlay, Speed, 0.5x: it now runs into the second, so it moves to another layer with the 4 s toast; the second does not move; one Undo reverses both. Speed on a clip whose end is past the video's: the past-end wash stays.
7. **Keyframes.** Add keyframes at 20% and 60% of a clip, set 2x: they stay at 20% and 60% of the new length (the diamonds move); one Undo restores the times and the speed together.
8. **Transitions.** Put a 2 s Dissolve between two clips of 4 s each, speed the first up to 8x: the transition shortens to what fits with the 3 s toast, in the same step. A transition on a 2x clip with spare footage plays its partner at 2x (the picture inside the window keeps moving at the clip's pace).
9. **Detach audio.** A 2x clip: Detach audio makes a sound clip whose Speed (Audio effects) reads 2x and plays in step with the picture within 33 ms. A 4x clip, a 0.25x clip and a Mute clip: the toast "Detach works at 0.5x to 2x with sound kept..." and nothing detaches.
10. **Trim and split.** Trim a 2x clip's end handle: the picture frame under the handle matches (the handle moves in what you see). Split a 2x clip at the playhead: both pieces keep 2x; the right piece starts where the left one stops in the source (the burned-in number is continuous across the cut).
11. **Slider.** Drag over the whole range: the readout moves with the speed and the resulting length; it snaps to 1x with one tick; double-tap the thumb: 1x; arrow keys step 0.1x up to 2x, then 0.5x; TalkBack reads "2x, 1.5 seconds". Dragging only moves the readout: the picture and sound follow after the release (the composition rebuilds); time the rebuild (Preview catching up shows after 1 s if slower). Janky frames under 5% over a 3 s drag (`dumpsys gfxinfo`).
12. **10-minute limit.** A 100 s clip: the slider's left end stops at 0.17x with no toast.
13. **Tiles and bars.** Tiles 0.25x 0.5x 1x 2x 4x 8x: the one equal to the speed is white, with another value none; tapping the selected tile does nothing (no step). The main bar's Speed (last tool) opens the clip under the playhead, or the nearest video clip when the playhead is on a photo; absent with only photos; absent on a photo clip's bar and on a clip whose file is missing. Back closes the panel, then the clip bar.
14. **Retime chip.** "2x" at the bottom-left of every sped clip, selected or not; hidden under 56 dp wide; the duration badge and the keyframe badge are not covered; TalkBack says "Speed 2x" after the keyframes.
15. **Reset.** Reset after changing speed and Sound: 1x, Keep, one step "Reset speed"; Reset with nothing set toasts "Speed is already normal." and records no step.
16. **Old draft.** Open a schema-20 draft: every clip at 1x, Keep; save, reopen. The new debug scenes save at schema 21.
17. **Leaks.** LeakCanary: open the panel, tiles, drag, Sound, close, five times; export twice in a row.
18. **Frame rate and slow-mo.** At 0.1x to 0.25x distinct frames are 3 to 7 a second and a caption or an overlay over the clip is redrawn only when a frame arrives: see whether the caption animation looks stepped; if so it becomes a ticket (a held-frame repeat in the composition).
19. **Not built here.** The curve tab, the six presets and the graph (P4-09b); reverse and freeze frame (P4-10: their fields are saved and ignored); the P4-16 pointer to Crop and Fill; a live preview while the slider moves.
