---
name: play-release
description: Prepare a Memix Android release the owner signs and uploads to Google Play by hand (internal testing, closed testing or staged production rollout), with the pre-release checklist. Manual only — run with /play-release.
disable-model-invocation: true
---

# Play release

Ask which track (internal / closed / production) and version name if not given.

**The owner signs and uploads by hand** (owner, 21 Oct 2026). Claude prepares and checks the build and writes the steps; it never handles the upload key. No keystore, password or Play service account goes in the repo, CI or a chat.

1. **Preflight** (stop on any failure and report):
   - clean tree, CI green for the commit: the phase branch's pushed head for a phase-end internal build, `main` for production;
   - for production: QA's latest phase report is **Pass** and the PM's phase gate is accepted;
   - `versionCode` incremented, `versionName` set;
   - Crashlytics mapping upload enabled; R8 rules checked on a release build;
   - AdMob uses real ad unit IDs in release, test IDs in debug; UMP consent shows on an EEA-region device;
   - strings complete in en, id, es, pt, hi; no `TODO(P7…)` reachable on Android.
2. **Build (owner, on his Mac):** Android Studio → Build → Generate Signed App Bundle, release variant, with the upload keystore kept outside the repo (or `./gradlew :androidApp:bundleRelease` with the owner's own local signing config, never committed). Play App Signing holds the app signing key. Never print, paste or commit keystore passwords.
3. **Smoke check** on the owner's own phone: cold start < 1.5 s, open both editors, add a meme sound, export 1080p30, share to one app, watermark present, rewarded ad removes it.
4. **Upload (owner, in Play Console):** Test and release → the track → Create new release → upload the `.aab` → release notes → review → roll out. Internal testing for phase builds; closed testing for P5-08 (at least 12 testers for 14 days before production access); production as a staged rollout 10% → 50% → 100%, holding each step until crash-free users ≥ 99.5%. Every upload needs a `versionCode` higher than the last one Play has seen.
5. **Store listing** (production): title "Memix: Meme Video & Photo", short description "Meme editor with meme sounds", screenshots, feature graphic, five languages; Data safety, content rating, target audience 13+, contains ads; privacy policy and app-ads.txt live on the app domain.
6. **Report:** version, track, rollout %, what was smoke-checked, anything skipped.

The developer account is a personal account created after 13 Nov 2023, so the closed-test rule applies: at least 12 testers opted in for 14 days in a row before applying for production access (P5-08). Internal testing is used at phase ends once the owner's Play Console app exists.
