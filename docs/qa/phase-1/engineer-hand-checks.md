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

1. **Edit timing, P1-04 review R1 (benchmark build; P1-06 starts from this number).** Media3 1.11.1 rebuilds every
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
   - If N is over 100 ms, tell the PM before P1-06 starts (it's P1-06's main risk).
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
   selected. Long-press it: a small menu (Zoom in, Zoom out, Show whole video) above your finger. Zoom steps keep it open;
   the step at its limit is gray and does nothing; Show whole video closes it; a tap outside or Back closes it.
6. **Selection:** tap a main clip: white outline with a thin dark gap, a white handle at each end, and its length
   (`00:06.00`) on a dark badge in its top-left corner. Tap it again: nothing changes. Tap a caption: the selection moves
   there (no badge). Tap an empty part of a lane, or below the lanes: cleared. Selecting never moves the playhead; scroll,
   zoom and Play keep the selection; a test edit (long-press the timecode) and Undo keep it while the item exists.
7. **Past the end:** scroll to the end: the last blue sound continues past the end of the video, that part is darkened,
   and the bands stop at the end.
8. **Many lanes:** `--es memix.openEditor timeline-tracks`: eight lanes in this order: video, overlay (gray), text,
   sticker (yellow), meme sound, meme sound, audio (green), effect (violet). The lanes scroll up and down under the
   ruler; the playhead reaches the bottom of the visible lanes; a diagonal drag moves one way only (the first that
   moves far enough).
9. **Missing file:** `missing-media`: the middle clip is gray with "File missing" and no thumbnails.
10. **Empty:** `empty`: the ruler and an empty main row, no bands.
11. **An hour:** `hour-long`: open, long-press the ruler, Show whole video: the hour fits in the right half, ruler labels
    are minutes or hours and never overlap, thumbnails load only around the playhead, nothing stutters while you fling.
12. **TalkBack:** swipe right from the top of the timeline: "Timeline" (actions: Zoom in, Zoom out, Show whole video);
    "Playhead, 0 seconds of 1 minute 20 seconds" (swipe up or down steps one frame; actions Forward 1 second, Back 1
    second, Go to start, Go to end); then the items in each lane, e.g. "Video, clip 1 of 20, 6 seconds long, starts at 0
    seconds, Sound detached", "Text, Caption 1, 3 seconds long, starts at 0 seconds". Past the last item on screen,
    swiping on scrolls the lane and reaches the next one; double-tap selects the item and brings its start under the
    playhead. All 20 clips can be reached this way. A sound past the end says "Partly after the end of the video".
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
`many-tracks` (timeline-tracks scrolled down a little), `past-end` (timeline-check at the end, the washed sound),
`selected-sound` (outline, gap, handles) and `selected-clip-badge`, `zoom-max` (0.25 s labels), `zoom-whole`
(Show whole video), `zoom-menu`, `hour-long-whole`, `missing-file`, `empty`, `hi-200` (Hindi at 200% font, a clip
selected).

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
