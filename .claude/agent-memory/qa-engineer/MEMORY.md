# qa-engineer memory

Lessons for this project only, newest first. Keep under ~150 lines; prune stale items. Focus: Fragile areas, bugs that came back, adb/emulator tricks, media that broke things, checks that caught real problems.

- 2026-10-08 · P0 · uiautomator dumps lag about 1 s; selected/checked state is on the clickable parent. Per-app locale: `adb shell cmd locale set-app-locales app.memix --locales <tag>`. 360 dp width: `adb shell wm density 480` (reset after). Test crash: `adb shell am start -S -n app.memix/.android.MainActivity --ez memix.testCrash true`.

<!-- - 2026-10-xx · P0-01 · lesson … -->
