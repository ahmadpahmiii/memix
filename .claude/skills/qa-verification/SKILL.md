---
name: qa-verification
description: The Memix QA engineer's manual testing method for the end of each phase — phase test plan, driving an Android emulator with adb, design checks against specs and tokens, result checks against "Done when", the edge-case catalog, export-file verification with ffprobe, manual performance numbers, the owner's device checklist, bug reports and the phase verdict. No automated tests. Use when a phase's tickets are built or fixes need re-testing.
---

# QA verification (manual, end of phase)

## 1. Plan

`docs/qa/phase-<n>/test-plan.md` from the phase's tickets: each "Done when" item → manual steps + expected result; which spec states to screenshot ("QA compares" section of each spec); which edge cases apply; which regression steps to run. Reuse and extend the previous phase's plan.

## 2. Set up the emulator

```bash
emulator -list-avds                                   # pick a recent Pixel image (API 34+)
./gradlew :androidApp:installDebug   # or: adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb push qa-media/. /sdcard/DCIM/MemixQA/             # sample media (see below)
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/MemixQA
```
Sample media to keep outside the repo or in a git-ignored `qa-media/`: short 1080p30 H.264, 4K60 HEVC, portrait with rotation metadata, variable frame rate phone clip, a 10-minute clip, a video with no audio, a 12 MP photo, a PNG with transparency.

## 3. Drive and observe

```bash
adb shell am start -n <pkg>/<activity>
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml   # find element bounds/labels
adb shell input tap X Y ; adb shell input swipe X1 Y1 X2 Y2 300 ; adb shell input text 'hello'
adb exec-out screencap -p > docs/qa/phase-<n>/screens/<ID>-<state>.png
adb logcat -d -v time | grep -iE 'memix|AndroidRuntime|FATAL' | tail -100
```
Screenshot only the states you check; look at each one before moving on.
uiautomator lags the screen: after a tap or launch, wait about 1.5 s and dump twice before judging. Selected and checked states sit on the clickable parent node, not the text node. When a dump and the screen disagree, the screenshot wins.

## 4. Design check (every UI ticket)

Against the spec, the board and the tokens: layout and spacing; v2 colors only (`grep -rniE 'FFE14D|FF4FA3' --include='*.kt' .` must be empty); type styles; every listed state; copy in all five languages (`adb shell cmd locale set-app-locales <pkg> --locales id` and es, pt, hi; watch truncation); accessibility: targets ≥ 48 dp (from `uiautomator` bounds), content descriptions present, font scale 200% (`adb shell settings put system font_scale 2.0`), TalkBack order where feasible; keyboard: `adb shell input keyevent KEYCODE_TAB` through each new screen (screenshot the focus ring), `KEYCODE_ENTER` activates, `KEYCODE_ESCAPE` and `KEYCODE_BACK` close sheets and dialogs.

## 5. Result check

Every "Done when" item by hand, then the edge-case catalog where relevant:
- Offline / flaky network: `adb shell svc wifi disable; adb shell svc data disable` (cached sounds still play; catalog shows cached data; clear messages)
- Low storage: fill the emulator, start an export → blocked with a clear message
- Permissions: deny and partial photo access (`adb shell pm revoke <pkg> android.permission.READ_MEDIA_VIDEO`)
- Process death mid-edit: `adb shell am kill <pkg>` in background → draft restored
- App backgrounded during export → continues with notification; cancel works
- Huge/odd media from the sample set
- Ads rules: no ads in editors or during export; interstitial at most 1 per 3 exports, never the first; rewarded removes the watermark for one export
- Analytics events (Firebase DebugView: `adb shell setprop debug.firebase.analytics.app <pkg>`)

## 6. Export files

```bash
adb pull /sdcard/Movies/Memix/<file>.mp4 qa-out/
ffprobe -v error -show_entries format=duration:stream=codec_type,codec_name,width,height,avg_frame_rate,start_time -of json qa-out/<file>.mp4
```
Check duration (± 1 frame), resolution, fps, codecs, audio stream present, audio/video start times aligned; open a frame to confirm the watermark is present or absent as expected. Compare effect frames with `docs/qa/references/`.

## 7. Performance (phases that touch hot paths)

Use the methods in the `mobile-performance` skill (`am start -W`, `dumpsys gfxinfo`, export timing). Emulator numbers are indicative only. Real budgets go on the owner's device checklist.

## 8. Owner device checklist

`docs/qa/phase-<n>/device-checklist.md`: ≤ 15 short steps needing a real phone (reference-phone performance, haptics, sharing into installed apps, audio sync by ear, ML Kit model download, consent in an EEA region), each with a result column. Fold the owner's results into the report.

## 9. Bugs

Append to `docs/qa/bugs.md`:
`BUG-### · <ticket> · S1–S4 · title` then steps, expected, actual, evidence (screenshot path, logcat excerpt), device/emulator + build, status (Open/Fixed/Verified/Won't fix).
Severity: **S1** crash, data loss, legal/licensing or privacy issue · **S2** main flow broken, no workaround · **S3** workaround exists or visible spec deviation · **S4** cosmetic.
Add a regression step to `docs/qa/regression.md` when a bug is fixed.

## 10. Phase report

`docs/qa/phase-<n>/report.md`: per ticket (design ✓/✗, result ✓/✗, bugs), regression result, performance numbers, Not verified items (waiting on the owner), and the verdict: **Pass** = no open S1/S2 and every criterion verified; otherwise **Fail**.
