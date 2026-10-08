# Phase 1 · hand checks for the owner

The engineers' device steps for each ticket's "Done when", copied from their handoffs. This cloud session has no Android SDK or emulator, so these are the checks that move a ticket from "Building (hand check pending)" to Built. QA folds them into `device-checklist.md` at the phase-end pass. All hooks run in debug builds only (`FLAG_DEBUGGABLE`), except P1-04's `memix.openEditor` and the editor's debug edit, which also run in the `benchmark` build used for speed numbers.

Run from the repo root with a phone or emulator attached: `./gradlew :androidApp:installDebug` first.

## P1-01 · project saved, force-closed and reopened comes back identical

```bash
./gradlew :androidApp:installDebug
adb logcat -c
adb shell am start -S -n app.memix/.android.MainActivity --es memix.projectCheck save
adb logcat -d -s MemixProjectCheck:* ProjectRepository:*     # expect: save: saved sample-every-track…
adb shell am force-stop app.memix
adb shell am start -S -n app.memix/.android.MainActivity --es memix.projectCheck verify
adb logcat -d -s MemixProjectCheck:* ProjectRepository:*     # expect: verify: identical
```
- Pass: `D/MemixProjectCheck: verify: identical`.
- Fail: `E/MemixProjectCheck: verify: different. First difference at character N: …`, or `verify: load failed with <error>` (with the reason under `ProjectRepository`).
- Optional, a cross-check from outside the app: `adb shell run-as app.memix sqlite3 databases/memix.db "select id,name,type,updated_at_epoch_us,length(project_json) from project"` (if the device has sqlite3).
- In a release build the extra does nothing: the gate is `FLAG_DEBUGGABLE`.

## P1-02 · picked media appear in the project without a storage permission prompt

Test files on the desktop (ffmpeg):
```
ffmpeg -f lavfi -i testsrc2=size=1080x1920:rate=30 -f lavfi -i sine=frequency=440 -t 12.4 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest clip-12s.mp4
ffmpeg -f lavfi -i testsrc2=size=1080x1920:rate=30 -t 5 -c:v libx264 -pix_fmt yuv420p -an clip-5s-silent.mp4
ffmpeg -f lavfi -i testsrc2=size=3024x4032 -frames:v 1 photo.jpg
head -c 300000 clip-12s.mp4 > damaged.mp4
ffmpeg -f lavfi -i testsrc2=size=3840x2160:rate=30 -t 300 -c:v libx264 -b:v 25M -pix_fmt yuv420p big-1gb.mp4
adb push clip-12s.mp4 clip-5s-silent.mp4 photo.jpg damaged.mp4 big-1gb.mp4 /sdcard/DCIM/MemixTest/
for f in clip-12s.mp4 clip-5s-silent.mp4 photo.jpg damaged.mp4 big-1gb.mp4; do adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/MemixTest/$f; done
```
1. `adb uninstall app.memix; ./gradlew :androidApp:installDebug` (fresh install).
2. **No permission:** `adb shell dumpsys package app.memix | grep -i permission` -> no `READ_MEDIA_*`, `READ_EXTERNAL_STORAGE`,
   `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_VISUAL_USER_SELECTED` (strict: `adb shell dumpsys package app.memix | grep -iE "READ_MEDIA|EXTERNAL_STORAGE"`
   prints nothing). Merged manifest: `./gradlew :androidApp:processDebugMainManifest` then
   `grep -iE "READ_MEDIA|EXTERNAL_STORAGE" $(find androidApp/build/intermediates -path "*merged_manifest*debug*" -name AndroidManifest.xml)` prints nothing.
   App info -> Permissions lists no "Photos and videos" or "Files".
3. **Done when:** `adb logcat -c`; Create -> Video meme -> picker opens at half height, no dialog -> tap clip-12s, photo,
   clip-5s-silent (in that order) -> confirm the selection -> the editor opens reading 00:00.00 / 00:20.40 (12.40 + 3.00 + 5.00;
   since P1-04 the editor has no clip list, the timeline comes with P1-05), no sheet flash.
   `adb logcat -d -s MemixAnalytics:* MediaImport:*` -> one `project_create {editor=video, source=gallery}`.
   `adb shell run-as app.memix ls -laR files/media` -> one folder, three files ending .mp4/.jpg/.mp4, no `.part`.
4. **Back out:** Create -> Video meme -> back -> the Create sheet is still open; `run-as ... ls files/media` unchanged.
5. **Cancel:** pick big-1gb.mp4 -> import sheet at ~0.3 s with count, percent, bar, time left after ~2 s -> note the copy
   rate -> Cancel -> back where you started, no new folder under files/media. Repeat and let it finish: note seconds per GB.
6. **Partial / none:** clip-12s + damaged + photo -> "2 of 3 added", row "damaged.mp4 / Damaged, or a format this phone
   can't play" -> Continue -> editor. damaged alone -> "Couldn't add your media" -> Pick again reopens the picker.
7. **Not enough space:** `adb shell df -h /data`; fill: `adb shell dd if=/dev/zero of=/sdcard/Download/fill.bin bs=1048576 count=<free MB - 600>`;
   pick big-1gb.mp4 -> "Not enough space" with both sizes -> Free up space opens the storage screen ->
   `adb shell rm /sdcard/Download/fill.bin` -> back to Memix -> copying starts by itself.
8. **Process death:** open the picker from Create, then `adb shell am kill app.memix` (if the process survives, set
   Developer options -> Background process limit -> No background processes), pick a clip -> Memix restarts and the
   editor opens with it. Mid-copy kill: start big-1gb.mp4, `adb shell am force-stop app.memix`, relaunch, wait 2 s ->
   `run-as app.memix ls -laR files/media` shows no `.part` and no folder for that pick.
9. **Rotation and background:** rotate and press Home during a big copy -> copying continues; the sheet shows the current state on return.
10. **Android 10 (API 29) emulator or phone:** same as step 3 -> backport picker or the file chooser, no prompt. In the
    file chooser, select 36+ files -> the sheet shows the 35 note, result "35 of N added".
11. **TalkBack** spot check: sheet title announced on open and on result; "4 of 5" spoken per item, not per percent.
Pass = all of the above; report the 1 GB copy time and anything off.

### States to screenshot for the design review (`docs/ux/reviews/P1-02/`)

Debug catalog (long-press the Home wordmark -> Catalog -> "Import sheet (P1-02)" buttons; sample data):
`copying`, `copying-long` (About 45 seconds left), `copying-minutes`, `copying-size-unknown` (48 MB copied),
`copying-over-limit`, `some-not-added`, `some-limit-only`, `some-limit-and-failures`, `none-added`, `none-long-list`
(scrolls, title/button fixed), `not-enough-space`, `not-enough-space-no-button`, `no-picker`; plus `catalog-progressbar`
and `catalog-blocking-sheet`. Hindi at 200% font: `copying-hi-200`, `some-not-added-hi-200`.
Real flow: `create-sheet` (en, and `create-sheet-hi-200`), `copying-real-1gb`, `some-not-added-real`, `none-added-real`,
`editor-after-mixed-pick` (video, photo, video; since P1-04 it shows the editor frame and 00:20.40, not a list). The system picker is not compared.

## P1-03 · export of a project with every supported track type

Ticket scope (PM): main video and audio tracks; overlay, text, sticker and effect tracks are skipped with a log line. The preview half of the Done when is checked in P1-04.

```sh
# 0. Make the test media on the desktop (ffmpeg on the desktop is allowed; nothing here goes into the app or the repo)
mkdir -p ~/memix-p1-03 && cd ~/memix-p1-03
ffmpeg -v error -y -f lavfi -i "testsrc2=size=1280x720:rate=30:duration=6" \
  -f lavfi -i "aevalsrc='0.5*sin(2*PI*440*t)*lt(mod(t,1),0.1)|0.5*sin(2*PI*440*t)*lt(mod(t,1),0.1)':s=48000:d=6" \
  -vf "drawtext=text='A %{pts\:hms}':fontsize=44:fontcolor=white:box=1:boxcolor=black:x=(w-text_w)/2:y=(h-text_h)/2" \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 128k -shortest clip-a.mp4
ffmpeg -v error -y -f lavfi -i "testsrc2=size=720x1280:rate=30:duration=6" \
  -f lavfi -i "aevalsrc='0.5*sin(2*PI*880*t)*lt(mod(t,1),0.1)':s=44100:d=6" \
  -vf "drawtext=text='B %{pts\:hms}':fontsize=64:fontcolor=white:box=1:boxcolor=black:x=(w-text_w)/2:y=(h-text_h)/2" \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 128k -shortest clip-b.mp4
ffmpeg -v error -y -f lavfi -i "color=c=gray:s=1080x1080" \
  -vf "drawtext=text='PHOTO':fontsize=200:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2" -frames:v 1 photo.png
ffmpeg -v error -y -f lavfi -i "aevalsrc='0.5*sin(2*PI*2000*t)*gte(t,0.5)':s=48000:d=1" -c:a aac -b:a 128k sound.m4a

# 1. Install, then copy the files into the app's files dir (debug builds allow run-as)
./gradlew :androidApp:installDebug          # from the repo root
adb push clip-a.mp4 clip-b.mp4 photo.png sound.m4a /data/local/tmp/
adb shell run-as app.memix mkdir -p files/debug-media
for f in clip-a.mp4 clip-b.mp4 photo.png sound.m4a; do adb shell run-as app.memix cp /data/local/tmp/$f files/debug-media/$f; done
adb shell run-as app.memix ls -l files/debug-media       # 4 files, same sizes as on the desktop
#    If cp is refused: adb shell "cat /data/local/tmp/$f | run-as app.memix sh -c 'cat > files/debug-media/$f'"

# 2. Export (keep the app open and the phone still until "done"; the hook runs in the activity's scope)
adb logcat -c
adb shell am start -S -n app.memix/.android.MainActivity --ez memix.exportCheck true
adb logcat -s MemixExportCheck:* MemixVideoEngine:*      # Ctrl-C after "export: done"

# 3. Pull the file (use the path from the "done" line)
adb exec-out run-as app.memix cat /data/user/0/app.memix/cache/exports/export-XXXX.mp4 > memix-p1-03.mp4

# 4. Check
ffprobe -v error -show_entries stream=index,codec_type,codec_name,width,height,r_frame_rate:stream_side_data=rotation -of compact memix-p1-03.mp4
ffprobe -v error -select_streams v:0 -show_entries stream=duration -of default=nw=1 memix-p1-03.mp4
for t in 0 1 2.95 3 4.95 5 6 7.95; do ffmpeg -v error -y -ss $t -i memix-p1-03.mp4 -frames:v 1 frame-$t.png; done
ffmpeg -v info -i memix-p1-03.mp4 -vn -af silencedetect=noise=-40dB:d=0.05 -f null - 2>&1 | grep -o "silence_[a-z]*: [0-9.]*"
ffmpeg -v info -ss 2 -t 3 -i clip-a.mp4      -vn -af volumedetect -f null - 2>&1 | grep max_volume
ffmpeg -v info -ss 0 -t 3 -i memix-p1-03.mp4 -vn -af volumedetect -f null - 2>&1 | grep max_volume
ffmpeg -v info -ss 1 -t 3 -i clip-b.mp4      -vn -af volumedetect -f null - 2>&1 | grep max_volume
ffmpeg -v info -ss 5 -t 3 -i memix-p1-03.mp4 -vn -af volumedetect -f null - 2>&1 | grep max_volume
```

**What a pass looks like**
- **Logcat:**
  - `export: starting export-check, expected length 8.000 s`
  - four `Not rendered yet: … track …, arrives with P4-01 / P1-10 / P4-12 / P4-06` lines (tag `MemixVideoEngine`)
  - progress lines
  - `export: done in … ms. path=/data/user/0/app.memix/cache/exports/export-….mp4 duration=8.0xx s size=… bytes tracks=2 (video, audio)`
  - `failed with NotFound` means a test file is missing from `files/debug-media`.
- **Streams:** exactly two, h264 video and aac audio. Video `r_frame_rate=30/1`. Size `1080x1920`, or `1920x1080` with `rotation=90` or `-90`; the second is Media3's default: portrait is encoded landscape with a rotation flag.
- **Length:** the video stream lasts 7.967–8.033 s (8.000 ± 1 frame).
- **Frames** (upright 1080×1920 PNGs). A wrong timecode means a trim bug; wrong order or wrong timing means a placement bug.
  - `frame-0` shows `A 00:00:02.000`: the center of the landscape clip, its sides cropped (fill).
  - `frame-1` shows `A 00:00:03.000`.
  - `frame-2.95` shows `A 00:00:04.967`.
  - `frame-3` and `frame-4.95` show the gray PHOTO square with black bars above and below (fit). The photo holds for 2 s.
  - `frame-5` shows `B 00:00:01.000`.
  - `frame-6` shows `B 00:00:02.000`.
  - `frame-7.95` shows `B 00:00:03.967`.
- **Sound events** (each within ±0.033 s, that is 1 frame):
  - `silence_end` at 1.0, 2.0, 3.5, 5.0, 6.0, 7.0; `silence_start` at 0.1, 1.1, 2.1, 4.0, 5.1, 6.1, 7.1.
  - The 3.5 s event is the meme sound: it starts within 1 frame of its timeline start and was trimmed (the source's first 0.5 s of silence is gone).
  - Nothing between 4.0 and 5.0: the muted track's beep at 4.2 stays silent.
  - **A/V sync:** each beep comes from the same source second the frame shows at that moment. At 1.0 s you hear source A's 3 s beep and see `A 00:00:03.000`; at 6.0 s, source B's 2 s beep and `B 00:00:02.000`.
- **Loudness** (`max_volume`):
  - Export 0–3 s ≈ clip A 2–5 s minus 6 dB (± 1.5 dB): clip volume 0.5 applied.
  - Export 5–8 s ≈ clip B 1–4 s (± 1.5 dB): the detached audio plays once. About +6 dB means it played twice; silence means it didn't play.
  - Rehearsal values: −5.9 → −11.9 dB and −5.8 → −5.8 dB.

## P1-04 · 1080p preview at 30 fps or more, no visible lag, no leaks (plus P1-07's controls and P1-02 N1/N2)

Two builds: **debug** for the hand checks and LeakCanary, **benchmark** (release code, debug key) for every number. Both
are signed with the debug key, so `installBenchmark` goes over `installDebug` and keeps the pushed files and drafts.

```sh
# 0. Test media on the desktop (P1-03's four files, plus a 1080p one and a silent clip)
cd ~/memix-p1-03        # the folder from P1-03 step 0, with clip-a.mp4, clip-b.mp4, photo.png, sound.m4a
ffmpeg -v error -y -f lavfi -i "testsrc2=size=1080x1920:rate=30:duration=32" -f lavfi -i "sine=frequency=330:sample_rate=48000:duration=32" \
  -vf "drawtext=text='%{pts\:hms} f%{n}':fontsize=96:fontcolor=white:box=1:boxcolor=black:x=(w-text_w)/2:y=(h-text_h)/2" \
  -c:v libx264 -profile:v high -pix_fmt yuv420p -b:v 8M -maxrate 10M -bufsize 16M -c:a aac -b:a 128k -shortest clip-1080p.mp4
ffprobe -v error -show_entries stream=codec_name,width,height,r_frame_rate,duration -of compact clip-1080p.mp4
#    expect h264 1080x1920 30/1 32.0 s and aac 32.0 s (about 33 MB)
ffmpeg -v error -y -f lavfi -i "testsrc2=size=1080x1920:rate=30:duration=5" -c:v libx264 -pix_fmt yuv420p -an clip-5s-silent.mp4

# 1. Debug build, then the files into the app (run-as works on debug builds only)
./gradlew :androidApp:installDebug
adb push clip-a.mp4 clip-b.mp4 photo.png sound.m4a clip-1080p.mp4 /data/local/tmp/
adb shell run-as app.memix mkdir -p files/debug-media
for f in clip-a.mp4 clip-b.mp4 photo.png sound.m4a clip-1080p.mp4; do adb shell run-as app.memix cp /data/local/tmp/$f files/debug-media/$f; done
adb push clip-5s-silent.mp4 /sdcard/DCIM/MemixTest/ && adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/MemixTest/clip-5s-silent.mp4

# Open a hand-check project in the editor (debug and benchmark builds); logcat tag MemixEditorCheck
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor <export-check|preview-check|preview-error|missing-media|empty>
```

**A. Hand checks (debug build).** `adb logcat -s MemixPreview:* MemixEditorCheck:* MemixAnalytics:* CompositionPlayer:*` in a second terminal.
1. **Open:** `--es memix.openEditor export-check`. The editor slides in: top bar with Close, black stage with a gray 9:16 frame
   until the first picture, then frame 0 of the project; `00:00.00 / 00:08.00`; Play; Undo and Redo greyed. The project has no
   saved lengths (like a version-1 draft), so opening it also proves the editor measures the files itself.
2. **Play to the end:** Play (button or a tap anywhere on the stage). The icon turns to Pause at once, the time runs, A then
   PHOTO then B as in P1-03, the beep at 3.5 s, clip A quieter, clip B's sound once. At the end it stops on the last frame
   with `00:08.00 / 00:08.00` and Play; Play again starts from 00:00.00. No "preview stopped" message, no error in logcat.
   **P1-03's preview frames:** record it (`adb shell screenrecord --bit-rate 20000000 /sdcard/p104.mp4`, Ctrl-C after the
   end, `adb pull /sdcard/p104.mp4`) and step through on the desktop: where the editor's timecode reads 00:00.00 / 00:02.95 /
   00:03.00 / 00:05.00 / 00:07.95, the picture shows `A 00:00:02.000` / `A 00:00:04.967` / PHOTO / `B 00:00:01.000` / `B 00:00:03.967`
   (± 1 frame). Seeking by hand arrives with the timeline (P1-05).
3. **Pauses by itself:** while playing, press Home (paused on return, the editor unchanged); start a call or play music in
   another app (pauses and stays paused after); unplug wired headphones or switch off a Bluetooth headset (pauses).
4. **Undo and redo (P1-07):** long-press the timecode = one test edit (1 s off the last clip, named "Trim"; debug and benchmark
   builds only). The length drops by 1 s, Undo turns white. Undo -> toast "Undo: Trim" for about 2 s at the top of the stage,
   the length comes back, Redo white, Undo grey. Redo -> "Redo: Trim". Each undo and redo logs one
   `tool_use {editor=video, tool=undo|redo}` (MemixAnalytics); a greyed button logs nothing.
5. **Save failing (P1-07):** fill the phone (`adb shell df -h /data`, then
   `adb shell dd if=/dev/zero of=/sdcard/Download/fill.bin bs=1048576 count=<free MB - 20>`), long-press the timecode ->
   after about half a second the banner "Your phone is full, so your latest changes aren't saved." with Free up space and ×.
   × hides it (another edit doesn't bring it back). Close -> the sheet "Your latest changes aren't saved" (Free up space,
   Leave anyway); back or the scrim keep you in the editor. Free up space opens Android's storage screen;
   `adb shell rm /sdcard/Download/fill.bin`, come back: the banner and sheet go by themselves (TalkBack says "Changes saved").
6. **Preview error:** `--es memix.openEditor preview-error` -> gray frame with "The preview stopped. Your edits are safe." and
   Try again (it fails again: the project is damaged on purpose). Close works.
7. **Missing media:** `--es memix.openEditor missing-media` -> once, the toast "A clip's file is missing, so it plays black.
   …" (about 4 s); playing shows A, 3 s of black and silence, then B.
8. **No clips, deleted on leaving (owner, 8 Oct):** `--es memix.openEditor empty` -> "This draft has no clips" on the stage.
   Close. Then `adb exec-out run-as app.memix cat databases/memix.db > memix.db` (pull `memix.db-wal` too if it exists) and
   `sqlite3 memix.db "select id from project"` on the desktop: no `empty` row. Also: open `export-check`, long-press the
   timecode until the frame says "This draft has no clips" (8 presses), Close -> no `export-check` row; open it again
   with the extra to get it back. A draft that still has clips is kept.
9. **Silent clip (P1-02 N5):** Create -> Video meme -> pick `clip-5s-silent.mp4` -> the editor plays 5 s with no error.
10. **The editor covers the import sheet (P1-02 N1):** Create -> Video meme -> pick P1-02's `big-1gb.mp4` -> when the copy
    ends, the editor slides in over the sheet; the sheet does not slide down first. Back on Home, no sheet.
11. **Portrait:** rotate the phone in the editor: it stays portrait; Home and the tabs still rotate.
12. **Keyboard (optional):** Tab moves Close -> preview -> play/pause -> undo -> redo; Enter presses; Escape closes the editor.

**B. Leaks (debug build, LeakCanary 2.14).** Open and close the editor 5 times (`export-check` and a real import), each time
playing, pausing, one test edit and an undo; wait 10 s on Home. Pass: no "LeakCanary" notification and
`adb logcat -d -s LeakCanary:*` reports no leak (a "Dumping heap" line means something was retained after 5 s; open the
LeakCanary app for the trace). LeakCanary's launcher icon is "Leaks".

**C. Done when: 1080p preview at 30 fps or more (benchmark build, the owner's phone).**
```sh
./gradlew :androidApp:installBenchmark        # over the debug install; the files and drafts stay
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor preview-check
adb shell dumpsys gfxinfo app.memix reset
#    tap Play, wait for the end (30 s), then:
adb logcat -d -s MemixPreview:*               # "Played 30000 ms of video in N ms: F frames shown (X fps), D dropped"
adb shell dumpsys gfxinfo app.memix | grep -E "Total frames|Janky frames|percentile"
adb logcat -d | grep droppedFrames            # Media3's own counts, if any
```
- Pass: X is 29.5 fps or more (a 30 fps source shown in real time: about 900 frames in about 30 s), D under 1% (9
  frames), N within 2% of 30000; gfxinfo janky frames under 5% (the timecode redraws every frame); nothing visibly stutters.
  Run it 3 times; report the median and worst X, D and janky %, plus the phone model and Android version.
- `preview-check` has six tracks; in P1 only the main video and the two sound tracks render (text, sticker and effect
  wait for their tickets), so the PRD's 6-track budget is measured again with P1-10 and P4.
- Cold start with P1-02's launch cleanup (N6): `adb shell am force-stop app.memix`, then
  `adb shell am start -W -n app.memix/.android.MainActivity | grep TotalTime`, 5 times; report the median (PRD: under 1.5 s).

### States to screenshot for the design review (`docs/ux/reviews/P1-04/`)
At 360 dp wide if possible (an emulator with a 360 × 800 dp screen), English unless noted:
`default-paused` (export-check just opened: frame 0, 00:00.00 / 00:08.00, Undo and Redo grey), `opening` (gray frame; catch
it right after the push, or skip), `playing` (Pause icon, running time), `at-end` (00:08.00 / 00:08.00, Play),
`undo-toast` (after a test edit and Undo: "Undo: Trim", Redo white), `redo-toast`, `save-banner-storage-full`,
`unsaved-sheet`, `preview-error`, `missing-media-toast`, `empty-draft`, `timecode-hi-200` (Hindi at 200% font: the length
wraps under the time, nothing cut off), `transport-hi-200`, and `catalog-toast` / `catalog-iconbutton` from the
component catalog (long-press the Home wordmark).

## P1-07 · killing the app mid-edit restores the draft

Pass bar (PM, reworded 8 Oct): the save starts 500 ms after the last edit and needs a few ms to write, so the device check kills at 700 ms (last edit reopens) and at 300 ms (previous save reopens). Neither may ever reopen a damaged draft.

```bash
./gradlew :androidApp:installDebug

# 1. Kill 700 ms after the last edit -> the last edit reopens
adb logcat -c
adb shell am start -S -n app.memix/.android.MainActivity --es memix.projectCheck autosave --ei memix.killAfterMs 700
#    wait about 10 s, until logcat shows "kill -9 now"
adb shell am start -S -n app.memix/.android.MainActivity --es memix.projectCheck autosave-verify
adb logcat -d -s MemixProjectCheck:* ProjectRepository:*
#    pass: "undo x100 reached 'edit 10'; one more undo stayed at 'edit 10'", "redo x3 reached 'edit 13'",
#          "drag of 60 frames is one step (... true)", "last change is 'edit 13, dragged'", "kill -9 now, 700 ms ...",
#          then "autosave-verify: reopened 'edit 13, dragged', identical to the scripted version"

# 2. Inside the window: the same with --ei memix.killAfterMs 300
#    pass: verify reopens the version in the last "saved '...'" line before "kill -9 now" (normally 'edit 13'), identical

# 3. App to the background (Home), then killed: the same with --ez memix.background true (no killAfterMs)
#    pass: "app stopped N ms after the last change; saved at once" with N under 500, "kill -9 now",
#          then verify -> 'edit 13, dragged', identical. If N is 500 or more, run it again (inconclusive).

# 4. PM's force-stop variant: run autosave with no extras, wait for "done", then
adb shell am force-stop app.memix
#    then autosave-verify -> 'edit 13, dragged', identical
```

Any line starting with `autosave-verify: DAMAGED` or `load failed` is a fail.
