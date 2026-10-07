---
name: play-release
description: Build and ship a signed Memix Android release to Google Play (internal testing or staged production rollout) with the pre-release checklist. Manual only — run with /play-release.
disable-model-invocation: true
---

# Play release

Ask which track (internal / production) and version name if not given.

1. **Preflight** (stop on any failure and report):
   - on `main`, clean tree, CI green for the commit;
   - for production: QA's latest phase report is **Pass** and the PM's phase gate is accepted;
   - `versionCode` incremented, `versionName` set;
   - Crashlytics mapping upload enabled; R8 rules checked on a release build;
   - AdMob uses real ad unit IDs in release, test IDs in debug; UMP consent shows on an EEA-region device;
   - strings complete in en, id, es, pt, hi; no `TODO(P7…)` reachable on Android.
2. **Build:** `./gradlew :composeApp:bundleRelease` (signing from CI secrets / local keystore outside the repo). Never print keystore passwords.
3. **Smoke check** on the reference phone: cold start < 1.5 s, open both editors, add a meme sound, export 1080p30, share to one app, watermark present, rewarded ad removes it.
4. **Upload** via the CI release workflow (or Play Console). Internal testing for phase builds; production as a staged rollout 10% → 50% → 100%, holding each step until crash-free users ≥ 99.5%.
5. **Store listing** (production): title "Memix: Meme Video & Photo", short description "Meme editor with meme sounds", screenshots, feature graphic, five languages; Data safety, content rating, target audience 13+, contains ads; privacy policy and app-ads.txt live on the app domain.
6. **Report:** version, track, rollout %, what was smoke-checked, anything skipped.

The account predates 13 Nov 2023, so the 12-tester closed-test rule doesn't apply; internal testing is still used every phase.
