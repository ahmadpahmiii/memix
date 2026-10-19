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
   after about half a second the banner "Your phone is full, so your latest changes aren't saved." with × beside the
   message and Free up space on its own row below, on the right; the whole message shows (also in pt and hi, and at 200%
   font). Pressing × shows a lighter gray square behind it. With TalkBack on and the banner up, Undo speaks "Undo: Trim"
   but no toast appears. × hides the banner (another edit doesn't bring it back). Close -> the sheet "Your latest changes aren't saved" (Free up space,
   Leave anyway); back or the scrim keep you in the editor. Free up space opens Android's storage screen;
   `adb shell rm /sdcard/Download/fill.bin`, come back: the banner and sheet go by themselves (TalkBack says "Changes saved").
6. **Preview error:** `--es memix.openEditor preview-error` -> dark gray frame with "The preview stopped. Your edits are safe."
   and a lighter gray Try again button (it fails again: the project is damaged on purpose). Play is greyed, and a tap on the
   stage does nothing. Close works.
7. **Missing media:** `--es memix.openEditor missing-media` -> once, the toast "A clip's file is missing, so it plays black.
   …" (about 4 s); playing shows A, 3 s of black and silence, then B.
8. **No clips, deleted on leaving (owner, 8 Oct):** `--es memix.openEditor empty` -> "This draft has no clips" on the stage,
   Play greyed. Close. Then `adb exec-out run-as app.memix cat databases/memix.db > memix.db` (pull `memix.db-wal` too if it exists) and
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

**D. Added 9 Oct (PM gaps): edit timing, P1-02 N2, `project_create` once, open-editor time.**

1. **Edit timing, P1-04 review R1 (benchmark build; measured at phase-end QA).** Media3 1.11.1 rebuilds every
   player on each edit, and P1-06's bar is "each edit shows in the preview within 100 ms". Since 9 Oct the preview logs
   one line per edit under `MemixPreview`: `Edit shown in N ms (rebuild R ms, first frame F ms)`, from the edit to the
   first picture of the edited project (rebuild = planning, file checks, building and the player's setup; first frame =
   from there to the picture).
   ```sh
   ./gradlew :androidApp:installBenchmark
   adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor preview-check
   #    wait for the first picture, then
   adb logcat -c
   #    long-press the timecode 10 times, about 2 s apart (each is one test edit: 1 s off the last clip; 30 s -> 20 s)
   adb logcat -d -s MemixPreview:* | grep "Edit shown"
   ```
   - Pass: 10 lines, N at most 100 ms in the median and the worst (P1-06's bar). Report the median and worst N with
     their R and F, the phone model and Android version. Repeat with `export-check` (3 clips: 5 presses) and note it too.
   - Undo and redo are edits as well: each logs its own line.
   - A line `Edit shown: no first frame reported N ms after the composition was set (rebuild R ms)` means the player
     didn't report a picture within 5 s: note it, and whether the picture on the stage changed.
   - Owner's rule (9 Oct): P1-06 is built without this number (the timeline shows each edit at once and the preview
     catches up). R1 is measured here at phase-end QA; if N is over 100 ms, note it in the QA report and a fix ticket
     follows.
2. **P1-02 N2: the first save fails after copying (debug build).** A real phone can't be filled in the milliseconds
   between the end of the copy and the save, so a hand-check switch makes that one save fail as if the phone were full.
   ```sh
   ./gradlew :androidApp:installDebug
   adb shell am start -S -n app.memix/.android.MainActivity --ez memix.failImportSave true
   adb logcat -c
   ```
   Create -> Video meme -> pick P1-02's `clip-12s.mp4` -> the copy runs, then the sheet says "Not enough space" ("Your
   media needs 100 MB, and your phone has … free": the save's headroom against what's really free, because the failure
   is simulated). Then:
   - `adb shell run-as app.memix ls -laR files/media` -> one new folder holding the clip's copy, no `.part`: the copies
     are kept.
   - `adb logcat -d -s MemixAnalytics:*` -> no `project_create` yet.
   - Free up space -> Android's storage screen -> back to Memix -> the editor opens by itself with the clip
     (`00:00.00 / 00:12.40`), and the log now has exactly one `project_create {editor=video, source=gallery}`.
   - Run it again and press Close on the sheet instead: back on Home, and `ls -laR files/media` no longer has that
     pick's folder (the copies go with the closed sheet).
   - Without the extra, imports save normally (the switch lasts for one save, in that app process only).
3. **`project_create` fires once per new project (debug build).** `adb logcat -c`, import one clip normally, then in
   the editor make 3 test edits (long-press the timecode), Undo, Redo, Close; open `--es memix.openEditor export-check`
   and close it. `adb logcat -d -s MemixAnalytics:*` -> exactly one `project_create`, plus one `tool_use` for the undo
   and one for the redo. Opening a saved draft never logs it (reopening by tapping a draft arrives with P1-14's list;
   the adb extra stands in for it).
4. **Open-editor time (benchmark build; PRD: under 1 s from tap to the first picture).** Tapping a draft arrives with
   P1-14, so this times the editor from the moment it's asked to open. The preview also logs
   `Preview opened in N ms (build B ms, first frame F ms)`: from creating the preview to its first picture.
   ```sh
   adb shell am start -S -W -n app.memix/.android.MainActivity --es memix.openEditor preview-check | grep TotalTime
   adb logcat -d -v time -s MemixEditorCheck:* MemixPreview:*
   ```
   - The editor's open time is the clock time from `openEditor: saved preview-check, opening it` to
     `Preview opened in …` (navigation, the slide-in, reading the draft and the preview); N is the preview's share.
     `TotalTime` is only the app's own start and first frame, without the video.
   - Run it 5 times (`-S` restarts the app each time; clear the log in between). Report the median and worst open time,
     and N, B and F of the median run.

### States to screenshot for the design review (`docs/ux/reviews/P1-04/`)
At 360 dp wide if possible (an emulator with a 360 × 800 dp screen), English unless noted:
`default-paused` (export-check just opened: frame 0, 00:00.00 / 00:08.00, Undo and Redo grey), `opening` (gray frame; catch
it right after the push, or skip), `playing` (Pause icon, running time), `at-end` (00:08.00 / 00:08.00, Play),
`undo-toast` (after a test edit and Undo: "Undo: Trim", Redo white), `redo-toast`, `save-banner-storage-full`,
`unsaved-sheet`, `preview-error`, `missing-media-toast`, `empty-draft`, `timecode-hi-200` (Hindi at 200% font: the length
under the time with no "/", nothing cut off), `transport-hi-200`, and `catalog-toast` / `catalog-iconbutton` from the
component catalog (long-press the Home wordmark).
After the design-review fixes (9 Oct), these must show the fixed build: `default-paused` ("00:00.00 / 00:08.00" on one
line at 360 dp), `timecode-hi-200`, `save-banner-storage-full` plus `save-banner-storage-full-pt-200` and
`save-banner-storage-full-hi` (whole message, Free up space under it on the right), `preview-error` (gray button on a
darker frame, Play greyed), `empty-draft` (Play greyed) and `catalog-toast`.
Free up space hiding when no storage screen opens can't be forced on a normal phone (Android's storage manager or
Settings always opens); it is checked in code and on iOS in P7.

## P1-05 · the timeline scrolls at 60 fps, no visible lag, no leaks

Spec `docs/ux/specs/P1-05-timeline.md`. Media: P1-04's files in `files/debug-media/` (`clip-a.mp4`, `clip-b.mp4`, `photo.png`,
`sound.m4a`, `clip-1080p.mp4`; P1-04 step 0 and 1). New hand-check projects (debug and benchmark builds):
`timeline-check` (20 main clips, 80 s, plus caption, meme-sound and "Original audio" lanes; the last sound runs 2 s past
the end), `timeline-tracks` (8 lanes) and `hour-long` (120 × 30 s of the 1080p clip). Not in P1-05, so not on screen:
the original-audio toggle (P1-06/P1-09), the "Add a meme sound" row (P1-08), Add media (P1-16); the white handles of a
selected item are drawn but drag with P1-06.

```sh
./gradlew :androidApp:installDebug
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor timeline-check
adb logcat -s MemixThumbnails:* MemixPreview:* MemixEditorCheck:*      # second terminal
```

**A. Hand checks (debug build).**
1. **Open:** the white playhead (a line with a round head) is in the middle at `00:00`, the left half is empty. Ruler labels
   every 2 s (`00:00`, `00:02`, `00:04`) with small ticks between. Main row: gray tiles that fill with frames one by one,
   each fading in; no spinner, no shimmer. Under it: orange captions ("Caption 1"), blue meme sounds (no titles until
   P1-08), green "Original audio". Lanes have a slightly lighter band from 00:00 to the end of the video. Nothing selected.
2. **Scroll:** drag left: time moves under the playhead, the preview shows frames while you drag and the exact frame when
   you stop, the timecode follows. Fling: it slows down by itself and stops exactly at the end (the end under the playhead,
   no bands on the right half), with no stretch or bounce; fling back: stops exactly at `00:00`. A label whose clip
   starts off screen stays at the left edge ("Original audio" while you scroll through it).
3. **Touch pauses:** Play, then touch the timeline anywhere: playback stops at once. While playing untouched, the content
   scrolls under the fixed playhead.
4. **Pinch:** zooms around the playhead; the timecode doesn't change while you pinch. All the way in: labels every 0.25 s
   (`00:03`, `.25`, `.50`, `.75`). All the way out: the whole 80 s in the right half of the timeline. Thumbnails
   stretch, then the exact frames fade in; no gray flash for frames already seen.
5. **Ruler:** tap it: that time slides under the playhead in about 0.2 s, the preview shows it, a selected item stays
   selected. Long-press it: one short buzz as a small menu (Zoom in, Zoom out, Show whole video) opens above your finger
   (review fix P4; the buzz follows the phone's touch-vibration setting, so keep it on for this check). Zoom steps keep it
   open; the step at its limit is gray and does nothing; Show whole video closes it; a tap outside or Back closes it.
6. **Selection:** tap a main clip: white outline with a thin dark gap, a white handle at each end, and its length
   (`00:06.00`) on a dark badge in its top-left corner. Tap it again: nothing changes. Tap a caption: the selection moves
   there (no badge). Tap an empty part of a lane, or below the lanes: cleared. Selecting never moves the playhead; scroll,
   zoom and Play keep the selection; a test edit (long-press the timecode) and Undo keep it while the item exists.
   Badge while scrolling (review fix P6): zoom in until one selected clip is wider than the screen, then drag so its start
   goes off the left edge: the badge stays at the left edge of the part you can see, and goes only when that part is
   narrower than the badge.
7. **Past the end:** scroll to the end: the last blue sound continues past the end of the video, that part is darkened,
   and the bands stop at the end.
8. **Many lanes:** `--es memix.openEditor timeline-tracks`: eight lanes in this order: video, overlay (gray), text,
   sticker (yellow), meme sound, meme sound, audio (green), effect (violet). The lanes scroll up and down under the
   ruler; the playhead reaches the bottom of the visible lanes; a diagonal drag moves one way only (the first that
   moves far enough).
9. **Missing file:** `missing-media`: the middle clip is gray with "File missing" and no thumbnails. With TalkBack on
   (review fix M1), that clip says "File missing" too: "Video, clip 2 of 3, 3.0 seconds long, starts at 3.0 seconds" plus the
   state "File missing" (before any other state). Clips 1 and 3 don't say it.
10. **Empty:** `empty`: the ruler and an empty main row, no bands.
11. **An hour:** `hour-long`: open, long-press the ruler, Show whole video: the hour fits in the right half, ruler labels
    are minutes or hours and never overlap, thumbnails load only around the playhead, nothing stutters while you fling.
12. **TalkBack:** swipe right from the top of the timeline: "Timeline" (actions: Zoom in, Zoom out, Show whole video);
    "Playhead, 0.0 seconds of 1 minute 20.0 seconds" (swipe up or down steps one frame; actions Forward 1 second, Back 1
    second, Go to start, Go to end); then the items in each lane, e.g. "Video, clip 1 of 20, 6.0 seconds long, starts at
    0.0 seconds, Sound detached", "Text, Caption 1, 3.0 seconds long, starts at 0.0 seconds", "Meme sound, 1.0 seconds
    long, starts at 2.0 seconds" (no title until P1-08, and the track name is said once; review polish P3). Past the
    last item on screen, swiping on scrolls the lane and reaches the next one; double-tap selects the item and brings its
    start under the playhead. All 20 clips can be reached this way. A sound past the end says "Partly after the end of
    the video". (TalkBack may say the states before the description; that depends on its element-description-order
    setting, not on Memix.)
    - **Lane order (review polish P5, the known risk):** on the Playhead, use its action Go to start, then swipe right
      through all 20 clips and keep going into the caption lane. Note which comes next: "Text, Caption 1, 3.0 seconds long,
      starts at 0.0 seconds" (pass), or a later caption with "Caption 1" reachable only by swiping back (the known risk:
      report it; the fix is one screen-reader scroll container around all lanes).
    - **Zoom menu title (review polish P2):** if double-tap-and-hold on the focused Playhead opens the zoom menu,
      TalkBack says "Zoom" as it opens. If it doesn't open that way, note it; the zoom actions on "Timeline" are the
      TalkBack route.
13. **Hindi at 200% font** (Settings → Display → Font size largest, app language Hindi): taller rows, bigger ruler with
    longer steps, "मूल ऑडियो" not cut off; the main row keeps its height and the badge still fits.
14. `MemixThumbnails` shows no "No thumbnail" line for the good files (one per tile of `missing.mp4` is expected: it isn't
    there).

**B. Leaks (debug build, LeakCanary 2.14).** Open and close the editor 5 times with `timeline-check`, each time scrolling,
flinging, pinching, selecting and opening the zoom menu; wait 10 s on Home. Pass: no LeakCanary notification and
`adb logcat -d -s LeakCanary:*` reports no leak.

**C. Done when: 60 fps scroll, under 5% janky frames (benchmark build, the owner's phone).**
```sh
./gradlew :androidApp:installBenchmark
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor timeline-check
#    find the timeline on screen (its bounds), e.g. bounds="[0,1290][1080,2010]" -> y = 1650
adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb exec-out cat /sdcard/ui.xml | tr '>' '\n' | grep 'content-desc="Timeline"'
#    wait about 5 s (thumbnails near the start), then 10 flings, 120 ms each, across the middle of the timeline
adb shell dumpsys gfxinfo app.memix reset
for i in 1 2 3 4 5; do adb shell input swipe 900 1650 150 1650 120; sleep 1.5; adb shell input swipe 150 1650 900 1650 120; sleep 1.5; done
adb shell dumpsys gfxinfo app.memix | grep -E "Total frames|Janky frames|percentile"
```
- Pass: janky frames under 5% in each run and nothing that looks like a stutter. Run it 3 times; report the median and
  worst janky %, the 90th and 99th percentile frame times, the phone model and Android version.
- Repeat once right after opening (no wait, thumbnails still loading) and once on `hour-long` after Show whole video;
  note both results.
- Also pinch in and out for 10 s with `dumpsys gfxinfo` reset before: note the janky %.
- Scrub response (P1-04's 100 ms): by eye while dragging slowly; there is no log line for it yet.

### States to screenshot for the design review (`docs/ux/reviews/P1-05/`)
At 360 dp wide if possible, English unless noted: `open-one-clip` (export-check just opened: playhead over 00:00, ruler
every 2 s, thumbnails loaded), `open-timeline-check`, `loading` (timeline-check right after opening, tiles filling in),
`many-tracks` (timeline-tracks scrolled down a little, with the bottom lane cut by the edge; if a lane boundary lines
up with the edge instead, say so in the QA notes), `past-end` (timeline-check at the end, the washed sound),
`selected-sound` (outline, gap, handles) and `selected-clip-badge`, `selected-clip-badge-scrolled` (the same clip with
its start scrolled off the left edge, the badge still showing at the left edge; review fix P6), `zoom-max` (0.25 s
labels), `zoom-whole` (Show whole video), `zoom-menu` (above the finger, over the preview), `hour-long-whole`
(00:00 · 30:00 · 1:00:00), `missing-file`, `empty`, `hi-200` (Hindi at 200% font, a clip selected).
Not screenshots, but in the QA notes: the TalkBack lane order (A12), "File missing" spoken (A9) and the buzz when the
zoom menu opens (A5).

## P1-06 · clip edits: each edit reaches the preview, never a black frame, no leaks

Spec `docs/ux/specs/P1-06-clip-edits-and-audio.md`. Media and hand-check projects as in P1-04 and P1-05 (`timeline-check`,
`timeline-tracks`, `export-check`, `missing-media`); their files' lengths are measured when the editor opens them. For a
one-clip project, import one clip from the gallery. Not in P1-06, so not on screen: the Meme sounds tool, the sounds sheet
and Move (P1-08), Volume, Detach audio and the original-audio toggle (P1-09), the text tools (P1-10), Replace (P1-16).

```sh
./gradlew :androidApp:installDebug
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor timeline-check
adb logcat -s MemixPreview:* MemixAnalytics:*      # second terminal
```

**A. Hand checks (debug build).**
1. **Tool bars:** nothing selected: one tool, Edit (scissors). Edit with the playhead inside a clip selects that clip, and
   the bar fades (about 0.1 s) to ← | Split · Delete (red) · Duplicate · Reorder; at the very end Edit picks the last clip.
   A caption: Delete · Split · Duplicate. A meme sound or "Original audio": Split · Delete · Duplicate. ←, a tap on empty
   timeline or system back returns to Edit; with nothing selected, back closes the editor.
2. **Split:** playhead inside a clip, at least 0.1 s from its edges: Split gives two pieces meeting at the playhead, the
   right one selected; the preview doesn't change. Undo says "Undo: Split". At a clip's edge Split says "To split, move the
   playhead inside this clip." Split a caption and a sound too.
3. **Trim:** select a clip and drag its end handle left: only that edge moves, a bubble above shows the new length, a gap
   opens before the next clip, and the preview shows the new last frame while you drag. The bubble has no shadow, sits
   whole inside the timeline (never cut at the top, never over the transport row) and goes at once on release (review
   P1, P2). Release: the clips close up, the preview goes back to the playhead's frame, "Undo: Trim" works. Then:
   - Clip 2 (`clip-b.mp4` from 1 s): drag its start handle left: it stops after 1 s with one buzz (no more footage).
   - Drag an edge near the playhead, a clip boundary or a caption's edge: it snaps with one buzz and a thin gray line
     across the lanes (no line at the playhead, which is already one).
   - Keep the finger at the screen's edge: time scrolls that way, faster nearer the edge.
   - A caption's end stops at the next caption. A photo can be made longer without limit.
   - A drag that starts on a handle never scrolls the timeline. System back during a drag cancels it (nothing changes).
4. **Delete:** later clips close up, the selection clears, Edit returns. In a one-clip project: "Your video needs at least
   one clip." Deleting "Original audio" (its lane's only item) removes the lane.
5. **Duplicate:** a main clip's copy lands right after it, later clips move right, the copy is selected. A caption's copy
   goes right after it on the same lane, or on a new lane when that time is taken.
6. **Reorder:**
   - Long-press a main clip: a buzz, the clips become square tiles showing their first frames, everything else is
     darkened, the held tile follows the finger and neighbors slide out of its way. Drop: the new order, the playhead at
     the moved clip, "Undo: Reorder".
   - Reorder tool: the tiles with the selected clip under the playhead; Move earlier and Move later move it one place;
     at either end they say "This clip is already first." / "…last."; a tap on another tile moves it there; Done applies.
   - The playhead fades out with the wash and comes back when the mode ends (review P4); the reorder bar has a thin
     gray line along its top edge (P3).
   - **Only the strip takes touches (review M1):** in reorder mode, tap and long-press the washed ruler, a washed lane
     (a caption, a sound) and the empty area below the lanes, including straight above or below the moving tile and
     above another tile: nothing happens. The clip doesn't move, nothing is grabbed, the timeline doesn't scroll and the
     mode stays open.
   - **The moving tile stays in view (review M2):** on `timeline-check` (20 clips), select clip 10, tap Reorder and tap
     Move later six times: the strip slides along with the tile in one movement, and the moving tile stays on screen at
     least a finger's width from each side. Keep going to the end ("This clip is already last."), then Move earlier all
     the way to the start: the same.
   - Back cancels: the order stays as it was and there is nothing new to undo. A drop where it started records nothing.
   - In a one-clip project the tool says "Reorder needs at least two clips."
7. **Preview catching up:** after every edit the stage keeps the old picture until the new one shows: never black, never
   gray. If an edit takes over a second, "Updating the preview" shows at the top of the stage and fades when the picture
   updates; TalkBack doesn't read it.
8. **TalkBack:**
   - Double-tap a clip to select it; its actions (TalkBack menu → Actions): Split, Delete, Duplicate, Move earlier, Move
     later. A caption or sound: Split, Delete, Duplicate (plus Move to playhead since P1-08). After each edit TalkBack says what happened, e.g. "Delete: clip 2",
     "Split: clip 4", "Trim: Caption 1". Explanations are spoken.
   - Right after the selected item come its handles: "Start of clip 3", "9.0 seconds". Swipe up or down: one frame each
     (one Trim step each). Action: Trim to playhead.
   - Reorder mode: the tiles read as a list, "Video, clip 2 of 20", the moving one "selected"; double-tap another tile
     moves the clip there and TalkBack says "Clip 1 of 20" (after each Move earlier or Move later too); Move earlier,
     Move later and Done are buttons.
   - **Focus (review P6):** select a clip, focus the Reorder tool and double-tap it: TalkBack's focus lands on Move
     earlier, not nowhere. Done or back: focus comes back to the Reorder tool. A long-press lift leaves focus alone.
9. **`tool_use`:** `MemixAnalytics` shows one `tool_use` per edit, tool `split`, `trim`, `delete`, `duplicate` or
   `reorder`, and none for a trim released where it started, a reorder dropped in place or a tool that only explains.
10. **Hindi at 200% font, 360 dp wide:** the clip tool bar's labels are whole (never cut: two lines is the copy budget,
    and at 200% a label may take a third; a word that can't break widens its tool, at least 64 dp), and the bar grows
    taller rather than cutting anything. Split and Delete are in view without scrolling. Delete reads "हटाएं"; in
    Indonesian, Reorder reads "Atur urutan" (review P7).
11. **Haptics** (keep the phone's touch vibration on): a SegmentTick when a trim snaps and when it stops at the footage
    limit; a LongPress buzz when a long-press lifts a clip into reorder mode.
12. **Remove animations** (Settings → Accessibility → Remove animations, or `adb shell settings put global
    animator_duration_scale 0`; back with 1): every edit changes at once, the tool bars switch at once, nothing slides
    (the ripple, the reorder morph, the tiles and the strip following the tile all jump).

**B. Edit timing with real edits, R1 (benchmark build; measured here at phase-end QA).** As P1-04 block D, step 1, but
with real edits on `timeline-check`: a trim release, a delete, a duplicate, a reorder drop and an undo, each 3 times, 2 s
apart, then `adb logcat -d -s MemixPreview:* | grep "Edit shown"`. Report the median and worst N with R and F per kind of
edit. Split changes only the timeline, so it logs nothing. Over 100 ms: note it in the QA report; a fix ticket follows.

**C. 60 fps recording: no black frame, frames to the edit (benchmark build, the owner's phone).** Record the screen at
60 fps (the phone's own screen recorder at 60 fps, or `scrcpy --record p106.mp4` from a computer). Make each edit once:
split, trim release, delete, duplicate, reorder drop, undo. In the recording, step frame by frame:
- From the frame where the finger lifts (or the tool is tapped) to the first frame where the preview changes: pass is 6
  frames (100 ms) or fewer. Split shows no preview change by design; check that the timeline changed at once.
- Any black or gray frame in the preview between the edit and the new picture is a fail.
- Note how often "Updating the preview" appeared.

**D. Leaks (debug build, LeakCanary 2.14).** Open and close the editor 5 times on `timeline-check`, each time making every
edit (split, trim on both ends, delete, duplicate, reorder by drag and by the tool, undo, redo); wait 10 s on Home. Pass:
no LeakCanary notification and `adb logcat -d -s LeakCanary:*` reports no leak.

**E. Sound drift trial (owner decision, 8 Oct).** On `timeline-check` the first meme sound starts at 0:02. Trim clip 1's
start by about 1 s: the video after it moves 1 s earlier while the sound stays at 0:02, so it now plays over a different
moment. Note whether that drift would bother you; it decides whether "keep sounds with their clip" is worth proposing.

### States to screenshot for the design review (`docs/ux/reviews/P1-06/`)
Take these after the review fixes, at 360 dp wide if possible, in English unless noted (the list from
`docs/ux/reviews/P1-06.md` → Device screenshots):
1. `main-tools`: nothing selected; Edit only.
2. `clip-tools-video`: a video clip selected: outline and handles on the clip; Close, divider, Split · Delete (red) ·
   Duplicate · Reorder.
3. `clip-tools-photo`
4. `clip-tools-text`: Delete · Split · Duplicate.
5. `clip-tools-sound`: Split · Delete · Duplicate.
6. `clip-tools-hi-200`: Hindi at 200% font, a video clip selected.
7. `explain-split`: the toast after Split with the playhead outside the clip.
8. `explain-last-clip`: a one-clip project, Delete tapped.
9. `trim-end-mid`: a main clip's end handle dragged inward: the gap on the near side, the bubble whole at the top of the
   timeline with no shadow.
10. `trim-start-overlap`: the start handle dragged outward over the previous clip.
11. `trim-snap-guide`: snapped to a caption's edge, with the guide line.
12. `trim-footage-limit`: the handle stopped at the source's end.
13. `trim-released`: the closed-up result.
14. `split-result`: two pieces meeting at the playhead, the right one selected.
15. `delete-after`: later clips closed up, the main tool bar back.
16. `duplicate-landed`: the copy right after the original, with the outline on the copy.
17. `reorder-tool`: entered from the Reorder tool: tiles, wash, the playhead faded out, the reorder bar with its hairline.
18. `reorder-moved-far`: `timeline-check` after six taps of Move later; the moving tile still on screen.
19. `reorder-drag`: a long-press lift, mid-drag.
20. `reorder-hi-200`: Hindi at 200% font.
21. `updating-preview`: only if the status appeared during the R1 run (block B).

**For QA to confirm on screen (end of phase):**
- `trim-end-mid`: the bubble doesn't overlap the transport row and isn't cut.
- `clip-tools-video`: Delete's red reads clearly next to the gray tools, and the pressed state is still legible.
- `clip-tools-hi-200`: Split and Delete in view without scrolling; no label cut.
- `reorder-moved-far`: the strip moves along with the tile, and the slide and the scroll read as one movement.
- In the QA notes, not screenshots: A6 (M1, washed areas do nothing), A8 (TalkBack, focus on Move earlier), A11
  (haptics), A12 (Remove animations).

## P1-08 · meme sounds: in sync within one frame, in the preview and the export

Spec `docs/ux/specs/P1-06-clip-edits-and-audio.md` → Meme sounds sheet, Meme sounds, Move, Volume panel; `P1-05-timeline.md`
→ "Add a meme sound" row. Projects: `timeline-check` (P1-05) and `sound-sync` (new): 12 s of photos, so only sounds are
heard, with P1-03's test tone (`sound.m4a`, whose 2 kHz tone starts 0.5 s into the file) starting at 1.0, 6.0, 8.0 (start
trimmed) and 11.8 s (runs past the end). Both need P1-03's test files in `files/debug-media/` (P1-03 step 0). Not in P1-08,
so not on screen: the original-audio toggle and clip volume (P1-09), Add media and Replace (P1-16).

```sh
./gradlew :androidApp:installDebug
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor timeline-check
adb logcat -s MemixPreview:* MemixAnalytics:* MemixSounds:* MemixExportCheck:*      # second terminal
```

**A. Hand checks (debug build).**
1. **Listen to all 10 sounds** (owner, 14 Oct): Meme sounds tool → play each row in turn. Only one plays at a time, the
   row shows Pause and a lighter band (`surface-raised`) while it plays and goes back to Play at its end. Press Home while one plays: it stops, and it
   doesn't play again when you come back. Note any sound that's too quiet (Drum roll was
   normalized to −23.7 LUFS because of its rimshot) or that sounds like a famous recording; either goes to the PM.
2. **Two taps:** with nothing selected, Meme sounds (blue, place 2) → the sheet opens at about half the screen with the
   top of the preview above it; a drag up on its header grows it. Add on a row: the sheet closes and the sound lands. With a
   clip selected, the "Add a meme sound" pill (blue, centered on the playhead line and drawn over it, in its row under the
   sound lanes) → the selection clears and the sheet opens → Add. The pill stays at the playhead while time scrolls and
   moves with the lanes' vertical scroll. Pressed, the pill turns gray (`surface-raised`); a tap just above or below the
   drawn pill, inside its row, still opens the sheet (the whole 48 dp row is its target). Close the sheet without adding:
   TalkBack's focus goes back to the tool or the pill that opened it.
3. **Sheet rows:** the title (two lines at most), "0:05 · CC0" (length rounded up, the credit never cut), no heart, a
   blue add button. In id, es, pt and hi the titles are translated (`adb shell cmd locale set-app-locales app.memix
   --locales hi`). ×, a scrim tap, a drag down, back and Escape close it and add nothing.
4. **Landing:** at the playhead, the whole sound at 100%; it bonks, the phone gives one Confirm vibration, it's selected
   with its tools (Volume · Split · Delete · Move here · Duplicate), TalkBack says "Boing added at 3.2 seconds" and its
   focus lands on the Volume tool. Where a sound already plays (`timeline-check` at 0:30), it goes on a new lane under the
   last sound lane and the lanes below slide down while the pill disappears, then fades in under the new lane (never over
   the new sound); if that lane is out of view, the lanes scroll to it. At the very end (scroll to the end), it's placed so it
   ends at the end. Undo says "Undo: Add sound". Adding the same sound twice works.
5. **Move by drag:** long-press a sound: a buzz, a white outline, and it follows the finger with a bubble showing its new
   start. Either end snaps (one tick and a thin line) to the playhead, 0:00, the end, clip boundaries and other items'
   edges. Over another sound lane it changes lane; over the "Add a meme sound" row it shows on a new lane there, with a
   lane band from 0:00 to the end under it; dropped
   where it would overlap, it goes to the next free lane or a new one. It can't start before 0:00, and the part past the end
   is washed. Near a side of the screen, time scrolls. Back during the drag cancels. Undo says "Undo: Move". A caption
   moves the same way (text lanes only); a long-press on a main clip still starts reorder.
6. **Move here:** the playhead inside the video: the sound starts there. At the very end: it ends at the end.
7. **Trim a sound:** its handles stop at the sound's own start and end, with one tick. With TalkBack, a selected sound's
   or caption's actions add "Move to playhead" (Move here's single-pointer route). The Move here tool reads "Move here,
   button, double-tap to move to playhead" (its name is its label), and Voice Access "Tap Move here" works.
8. **Volume:** Volume tool → the panel covers the timeline and tool bar; the preview and transport row stay. Press Play and
   drag the slider: the change is heard while dragging. It snaps to 100% with one tick; a double-tap on the thumb goes
   back to 100%; 0% is silent, 200% louder (clipped, not distorted into noise). Each release: "Undo: Volume". Done, back
   and Escape close it, the sound stays selected and focus goes back to the Volume tool. With TalkBack the slider steps
   10% on the grid: drag to 147%, then a step up gives 150% and steps down reach 100% exactly (arrow keys the same). With
   the panel open, swiping back from its title "Volume" reaches Redo, never a sound tool or a timeline item, and Shift+Tab
   on a keyboard doesn't reach them either. Undo past the landing closes it.
9. **Analytics** (`MemixAnalytics`): `sound_add {sound_id, tab=starter, editor=video}` once per landing, never for a
   duplicate; `sound_preview {sound_id, tab=starter}` per play; `tool_use` `move` and `volume` once per step; nothing for
   opening the sheet or the panel, a drop where it was, or a release at the same volume.
10. **Hindi at 200% font, 360 dp:** the sheet rows, the pill and the sound tool labels are whole; the pill row grows.
    In es and pt at 200% the pill's label wraps to two centered lines, the pill keeps `space-4` from each side of the
    timeline and its row grows (renders 100 and 101 show it).
11. **Drift trial** (owner, 8 Oct; P1-06 block E): on `timeline-check`, land a sound on a clip boundary, then trim clip 1's
    start by about 1 s. The video after it moves 1 s earlier and the sound keeps its time, so it now plays 1 s off that
    boundary. Note whether that would bother you.

**B. Preview sync (debug build; the onset log).**
1. `adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor sound-sync`, then `adb logcat -c`.
2. Play from 0:00 to the end. Then tap the ruler at 0:05 and play again (a seek into a sound logs nothing, so always
   start at least half a second before a sound).
3. `adb logcat -d -s MemixPreview:* | grep "Sound onset"`: one line per sound, for example `Sound onset: heard at 6.000 s,
   placed at 6.000 s, off by +0.0 ms (a frame is 33.3 ms)`. Pass: every line within one frame (expect under 1 ms). The
   sound at 11.8 s is heard for 0.2 s and stops with the video.
4. Add a starter sound with a sharp start (Wrong buzzer, Boing or Deep boom) from the sheet, play over it, and check its line
   too.
5. **Ear check** on the phone's own speaker (Bluetooth adds the headset's delay, which isn't Memix's): on `timeline-check`,
   move Wrong buzzer until it snaps to a clip boundary and play across it. The buzz starts as the picture cuts, with no
   gap you can see or hear.

**C. Export sync (debug build).**
1. On `sound-sync`, add starter sounds: one near the start, one in the middle, one with Move here, one with its start
   trimmed, and one that runs past the end. Close the editor (it saves).
2. `adb shell am start -S -n app.memix/.android.MainActivity --ez memix.exportLatestDraft true`, then
   `adb logcat -d -s MemixExportCheck:*`: one `sound:` line per sound (where it starts, from where in its file, its length
   and volume), then `export: done … path=…/cache/exports/<file>.mp4`.
3. `adb exec-out run-as app.memix cat cache/exports/<file>.mp4 > p108.mp4`, then
   `ffmpeg -i p108.mp4 -af silencedetect=noise=-40dB:d=0.05 -f null - 2>&1 | grep silence_end` and
   `ffprobe -v error -show_entries stream=codec_type,start_time -of csv p108.mp4`.
4. Pass: each `silence_end` is within 0.033 s of a logged start, both streams start at 0, the file is 12.0 s long and
   the sound past the end stops at 12 s.

**D. Leaks (debug build, LeakCanary 2.14).** Open and close the editor 5 times on `timeline-check`, each time: open the
sheet, play two sounds, add one, move it, open the Volume panel and drag the slider; wait 10 s on Home. Pass: no
LeakCanary notification and `adb logcat -d -s LeakCanary:*` reports no leak (the sheet's player closes with the editor).

**E. APK size:** the starter pack adds 453,633 bytes (443 KB) to the APK, measured from the debug APK's
`assets/composeResources/memix.platform.services.generated.resources/files/starter-sounds/` (the m4a files are stored
uncompressed). The license proofs aren't in the APK.

### States to screenshot for the design review (`docs/ux/reviews/P1-08/`)
At 360 dp wide if possible, English unless noted: `main-tools` (Edit, Meme sounds in blue), `sounds-sheet` (half height,
one row playing), `sounds-sheet-expanded` (after a drag up), `sound-landed` (selected, sound tools), `sound-new-lane` (a
sound landed on a new lane), `add-row-pill` (a clip selected, the pill at the playhead), `move-drag` (a carried sound with
its bubble and snap line), `move-to-add-row` (over the add row, with the new-lane band), `volume-panel` (100%),
`volume-panel-200`, `sounds-sheet-hi-200`, `sound-tools-hi-200` and `add-row-hi-200` (Hindi at 200% font), `add-row-es-200`
and `add-row-pt-200` (the label on two lines), plus a short screen recording of a sound landing on a new lane (the pill
sits out the slide). Take them after the P1-08 review fixes.

## P1-16 · Add media lands after the clip under the playhead as one step; Replace keeps the clip's start and length

Spec `docs/ux/specs/P1-06-clip-edits-and-audio.md` → Main tool bar, Add media, Replace, States, Copy, Accessibility,
Analytics; `P1-05-timeline.md` → Trailing area; `P1-04` → missing-file toast and empty state. Projects: `timeline-check`
(20 clips of 6, 3, 5 and 2 s: clip 3 runs 9–14 s, so 9.0 s is the boundary between clips 2 and 3; clip 1's sound is
detached), `export-check` (clip B at 5.0–8.0 s is detached, its audio on the audio lane at 5.0 s), `missing-media` and
`empty`. Keep a few videos of different lengths (one of 2 s, one of 60 s) and some photos in the phone's gallery.

```sh
./gradlew :androidApp:installDebug
adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor timeline-check
adb logcat -s MemixAnalytics:* MemixEditorCheck:* MediaImport:*      # second terminal
```

**A. Add media (debug build).**
1. **Main tool bar:** with nothing selected, Edit · Meme sounds (blue) · Add media (plus icon, place 3 until P1-10's Text
   takes place 3). The same system picker as Create → Video meme opens, with no permission prompt; it allows 35.
2. **Inside a clip:** playhead at about 11 s (inside clip 3), Add media, pick a photo and a video. They land right after
   clip 3, in pick order; clip 3 isn't cut; clip 4 and everything after it move right by the added length (they slide);
   the meme sounds, captions and "Original audio" don't move. The playhead jumps to the first new clip (14.0 s) and the
   new clips bonk together: they grow a little taller and only a few dp wider, never over their neighbors. The selection
   and tool bar don't change. TalkBack: "2 clips added". Undo: "Undo: Add media"
   removes both; Redo brings both back.
3. **On a boundary:** with TalkBack on, double-tap clip 3 (the playhead goes to exactly 9.0 s), TalkBack off, Add media,
   one photo: it lands at 9.0 s, between clips 2 and 3 (before clip 3, not after it).
4. **At the end:** scroll to the very end, Add media: appended after the last clip.
5. **The tile:** at the end of the video, the plus tile sits just right of the last clip (a dark square, pressed: lighter).
   With the playhead a little before the end (inside the last clip), tap the tile, pick one: appended at the end, not after
   the clip under the playhead. The tile hides in reorder mode, while a trim handle or a carried sound is held, and while a
   selected clip ends close to the end (the last clip always; pinch out with the next-to-last clip selected and it hides
   once that clip's end handle would reach it). Pressed, it turns lighter in a short fade. TalkBack reads it as "Add
   videos or photos", after the "Add a meme sound" pill.
6. **Empty project:** `--es memix.openEditor empty`: "This draft has no clips · Add a video or photo to start.", the tool
   bar shows only Add media, and the tile sits just right of the playhead at 0:00. Add from either: the clips start at
   0:00, the preview comes up, Play works, and Edit and Meme sounds appear.
7. **Nothing happens when nothing is picked:** open the picker and press back, or close it, or confirm with nothing
   selected: no change, no "Undo", no `tool_use` line. Focus (TalkBack) goes back to the tool or tile that opened it.
   With a keyboard: Add media, pick a long video, Tab to Cancel and let the copy finish: the focus ring lands on Add
   media (the same after Continue on "1 of 2 added", and after the Meme sounds sheet's ×).
8. **Import sheet:** pick a long video (over 300 ms to copy): P1-02's "Adding your media" sheet over the editor, with its
   own scrim, "1 of 1" and the bar; Cancel or back cancels (nothing added, no copy left: `adb shell run-as app.memix ls
   files/media/<project id>/`). Pick a damaged file with a good one: "1 of 2 added" with the row; Continue returns to the
   editor and then the clip bonks. "Not enough space" (fill the phone, or a big pick on a nearly full phone) and Free up
   space behave as in P1-02.
9. **Drift trial** (PM, with P1-06 block E): land a meme sound on a clip boundary, then Add media before it: later clips
   move right, the sound keeps its time and now plays off that boundary. Note whether that would bother you.

**B. Replace (debug build).**
1. **Where:** select a main clip: Split · Delete · Duplicate · Reorder · Replace (last; new icon: a block between two curved
   arrows). Sounds, captions and other items have no Replace.
2. **A longer video:** select clip 3 (5.0 s), Replace, pick the 60 s video. Same start and length (the badge still says
   00:05.00), new thumbnails, it bonks once, stays selected, the playhead doesn't move. TalkBack: "Replace: clip 3".
   Undo: "Undo: Replace" brings the old picture back. The logcat `MemixEditorCheck` lines of the next save list only
   `video.tracks[...].items[timeline-clip-2].source.*` (clip 3 already ran from 0 to 5 s of its file, so no trim line; a
   clip with a trimmed start also shows `trimInUs` -> 0 and its `trimOutUs`): no other clip, track, sound or caption, no
   `startUs`, `volume` or `muted`.
3. **A photo:** Replace clip 3 with a photo: it shows for the clip's 5.0 s.
4. **A shorter video:** Replace clip 3 with the 2 s video: the toast "That video is shorter than this clip (00:05.00). Pick
   a longer one, or trim this clip first.", nothing changes, no "Undo", and its copy is gone (`ls files/media/<id>/`: no new
   file).
5. **Detached clip:** `--es memix.openEditor export-check`, select clip B (5.0–8.0 s, its audio already on the audio lane),
   Replace with a video that has sound: the toast "The detached sound stays, so this clip's own sound starts at 0%.";
   playing, you hear the detached audio once, not doubled. The save's `MemixEditorCheck` lines show `audioDetached` true ->
   false and `volume` -> 0.0 for that clip besides its source and trim; the audio lane's clip has no line. A photo or a
   silent video gets no toast.
6. **Missing file:** `--es memix.openEditor missing-media`: the toast now says "Replace or delete that clip to fix it.".
   Select the middle clip ("File missing"): Replace comes first. Replace it with a 3 s or longer video: "File missing"
   goes, thumbnails appear, the preview plays it.
7. **Copies go at the next start** (PM bar): after step B2, note the old file in `adb shell run-as app.memix ls
   files/media/<id>/`, close the editor, `adb shell am force-stop app.memix`, start the app again: the old file is gone and
   the draft opens with the new picture. With the editor still open (before closing), Undo brings the old picture back.
8. **Analytics** (`MemixAnalytics`): `tool_use {editor=video, tool=add_media}` once per pick that lands (2 items = one
   line), `tool_use {tool=replace_clip}` once per Replace; nothing for a cancelled pick, a refused Replace or an empty
   pick, and never `project_create` from the editor.

**C. Process death (debug build).** In Developer options set "Don't keep activities" on. Add media → the picker opens,
the editor is destroyed behind it → pick 2 photos: back in the editor, they land after the clip that was under the
playhead when you tapped (the draft reopens first). Turn the option off again.

**D. Leaks (debug build, LeakCanary 2.14).** Open and close the editor 5 times on `timeline-check`, each time: Add media
(2 photos), Replace one clip, cancel one pick, then wait 10 s on Home. Pass: no LeakCanary notification and `adb logcat
-d -s LeakCanary:*` reports no leak.

**E. Hindi and large text:** Hindi at 200% font, 360 dp: the main tool bar (Add media), a main clip's tools (Replace),
the empty state, and the import sheet are whole; labels wrap, nothing is cut.

### States to screenshot for the design review (`docs/ux/reviews/P1-16/`)
At 360 dp wide if possible, English unless noted: `main-tools-add-media`, `tile-at-end`, `tile-pressed`, `empty-project`
(tile and Add media only), `media-added` (after the bonk, playhead on the first new clip), `import-sheet-over-editor`,
`clip-tools-replace`, `replaced-clip` (same start and length, new thumbnails), `replace-too-short-toast`,
`replace-detached-toast`, `missing-clip-replace-first`, `main-tools-hi-200`, `clip-tools-replace-hi-200`,
`empty-project-hi-200`, `main-tools-es-200` (the longest Add media label).

## P1-17 · Hindi display face: hero and entry cards in Teko, nothing clipped, other languages unchanged

```sh
./gradlew :androidApp:installDebug
adb shell cmd locale set-app-locales app.memix --locales hi    # Android 13+; back to the phone's language: --locales ""
adb shell settings put system font_scale 2.0                   # 200%; back with 1.0
adb shell wm size 1080x2400 && adb shell wm density 480        # 360 dp wide; back with "wm size reset" and "wm density reset"
```
1. **Hindi, 100% and 200%, 360 dp:** Home shows "मीम बनाएं" and the two entry-card titles in Teko Bold (tall, condensed,
   square), not the phone's regular Devanagari. The "Memix" wordmark top-left is still Anton. At 200% the titles wrap;
   no mark above the headline bar or below the baseline is cut, including inside the cards' rounded corners.
2. **Create sheet hero** in Hindi at 200%, and the **Drafts** empty headline (`display-xl`) in Hindi at 200%: same checks.
3. **Other languages unchanged:** `--locales en` at 360 dp, compare Home with `docs/ux/reviews/P0-05/android-home-360.png`
   (pixel-identical apart from content); spot-check id, es, pt.
4. **Catalog in Hindi** (long-press the wordmark): Type shows "मीम बनाएं (display-xl)" and "वीडियो मीम (display)" in Teko,
   and "Memix (wordmark)" in Anton.
5. **Vertical position:** compare the Hindi hero's size and centering with the English hero; note in the QA report if Teko
   sits low in its line (the designer adjusts the token).
6. **APK size:** compare `androidApp/build/outputs/apk/benchmark/*.apk` before and after this change (or `apkanalyzer
   files list`): the font adds 257,136 bytes uncompressed, about 106 KB in the APK if the build deflates it.

### States to screenshot for the design review (`docs/ux/reviews/P1-17/`)
`home-hi-100`, `home-hi-200` (360 dp), `create-sheet-hi-200`, `drafts-empty-hi-200`, `home-en-360`, `catalog-type-hi`.

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
