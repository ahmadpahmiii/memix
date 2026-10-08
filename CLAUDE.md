# Memix — project brief for Claude Code

Memix ("Memix: Meme Video & Photo" on the stores) is a meme maker with **two separate editors** (video and photo), a large **licensed, regional meme-sound library**, and meme templates. Kotlin Multiplatform + Compose Multiplatform, **Android first** (launch ≈ 7 Mar 2027), iOS later from the same code (≈ 2 May 2027). Solo developer working with Claude. Ads-only monetization, no accounts, everything renders on the phone.

## Read before you write code

| Need | File |
| --- | --- |
| What we're building and why, scope, rules, metrics | `docs/PRD.md` |
| Modules, data model, engine interfaces, backend schema, CI | `docs/TECHNICAL_DESIGN.md` |
| What to build next, acceptance criteria, sizes, dependencies | `docs/TICKETS.md` |
| Colors, type, spacing, components, Compose mapping | `docs/DESIGN_SYSTEM.md` + `design/tokens.json` |
| What each screen looks like | `design/screens/*.dc.html` (open in a browser) |
| Where things stand, ticket statuses, decisions | `docs/pm/STATUS.md`, `docs/pm/decision-log.md` |
| Screen spec for a UI ticket | `docs/ux/specs/` |
| Bugs and QA results | `docs/qa/` |

**The repo files are the source of truth** (owner, 8 Oct 2026). The Claude Docs and design artifacts linked at the top of some docs are the original plan, kept for reference only; don't sync changes back to them. If code and docs disagree, stop and ask — don't silently pick one. When behavior, a module or a command changes, update the docs in the same ticket commit, including any skill or agent file that names it (grep `.claude/` for the old name).

## Stack (pin latest stable in `gradle/libs.versions.toml` on project creation)

- Kotlin Multiplatform, Compose Multiplatform (shared UI), Gradle version catalog.
- Clean architecture + MVVM: one `StateFlow<UiState>` per ViewModel, `Intent`/event in, one-way data flow. Coroutines + Flow.
- Koin (DI), Multiplatform Navigation Compose, lifecycle ViewModel, kotlinx.serialization, Ktor + supabase-kt (postgrest-kt), SQLDelight, multiplatform-settings, Coil 3.
- Video: **Media3** (Transformer, Composition, CompositionPlayer, GL effects) on Android; **AVFoundation** (AVMutableComposition, custom Metal/Core Image compositor, AVAssetWriter) on iOS — both behind `VideoEngine`.
- Background removal behind `Segmenter`: ML Kit Subject Segmentation (photo, beta) + MediaPipe Image Segmenter VIDEO mode (people in video) on Android; Vision `VNGenerateForegroundInstanceMaskRequest` (iOS 17+) and `VNGeneratePersonSegmentationRequest` + `VNSequenceRequestHandler` on iOS.
- Backend: Supabase (Postgres catalog + RLS, dashboard as CMS) + Cloudflare R2 (all media files, zero egress). Firebase Crashlytics, Analytics, Remote Config. AdMob + UMP consent.

## Architecture rules (enforced in review)

1. `:core:model` and `:core:domain` are pure `commonMain` — no Android/iOS imports, no Ktor/SQLDelight types.
2. Features (`:feature:*`) call **use cases only**. Never a repository, engine or SDK directly.
3. Platform code lives only in `androidMain` / `iosMain` of `:engine:*` and `:platform:services`, behind interfaces declared in `:core:domain`, plus the composition root (`:androidApp`, `:composeApp`) for app entry and wiring (for example creating the SQLDelight driver and debug hand-check hooks). No feature logic in the composition root.
4. Time is **microseconds (`Long`)** everywhere in the project model. Clips are positioned by time, never by index.
5. Effects, filters and transitions are **declarative specs** (id + params) in `:core:model`; each engine renders the same spec. Every new spec gets a reference render in `docs/qa/references/`.
6. `Project` is immutable; every edit returns a new `Project` (undo keeps 100). Autosave 500 ms after the last change. Bump `schemaVersion` + add a migration for any persisted shape change, and check by opening an old draft.
7. The iOS framework must compile on every push to a phase branch and every PR, even before iOS work starts (keeps Android-only APIs out of shared code).

## Design rules (v2 — "neutral chrome, one blue")

- Dark only. Neutral, untinted grays: canvas `#111111`, surface `#1B1B1B`, surface-raised `#2C2C2C`, hairline `#393939`; preview stage `#000000`.
- **One accent:** primary sky blue `#2BB3F3` (text on it `#111111`) for the one main action per screen, the global Create button, the active tool, links, progress, and anything that is a meme sound.
- **Selection is white** `#FFFFFF`: selected clip/layer outline, trim handles, playhead, selected chip.
- Track colors: video `#3D3D3D`, text `#E78E40`, sticker `#D8AE31`, meme sound `#2BB3F3`, audio `#3DAE79`, effect `#AA84EB`; labels on them `#111111`.
- No outlines around shapes, no hard/offset shadows, no gradients. Depth by value steps. Only sheets/menus get a soft shadow.
- **Never hardcode** a color, size, radius or font in a screen: use `MemixTheme` tokens. Missing token → add it to `design/tokens.json` + `docs/DESIGN_SYSTEM.md` + the Kotlin token object first.
- The v1 palette (yellow `#FFE14D`, pink `#FF4FA3`, black outlines, hard shadows) is rejected. Don't reintroduce it.
- Fonts: Anton (wordmark, Home hero, entry cards, meme captions), Space Grotesk (UI), Space Mono (timecodes). All OFL, bundled.
- Copy: sentence case, verb first, "you/your", no emoji in chrome. Every user-facing string is a resource in **en, id, es, pt, hi**.

## Hard "don'ts"

- No FFmpeg / FFmpegKit or any GPL/LGPL library inside the app (desktop content scripts may use ffmpeg).
- No secrets or account config in the repo. Every credential, where it lives and how CI gets it: `docs/CREDENTIALS.md`. Secrets live in `.env` (template `.env.example`), `local.properties` and CI secrets. The app ships only the Supabase publishable key (RLS: read active rows, insert `reports`). Turn on the secret check once per clone: `git config core.hooksPath .githooks`.
- No pull request without a security review. `.claude/hooks/pr_security_gate.py` (registered in `.claude/settings.json`) blocks every pull request until `scripts/check-secrets.sh` is clean for the files and every commit since `main`, the branch is pushed, and the `security-reviewer` agent passed that exact commit. The repo is public, so a key deleted in a later commit is still exposed: rotate it first.
- No catalog content without a recorded license (`source_url`, `license_type`, `license_proof`, `credit`). No YouTube rips.
- No ads inside the editors or during export; interstitial max 1 per 3 exports and never on the first; rewarded ad removes the watermark for one export; no banners.
- No accounts, no uploads of user media, no cloud rendering in v1.

## Team

Six agents in `.claude/agents/`: five role agents plus the `security-reviewer`. The main session orchestrates them with the `ticket-workflow` skill.

| Agent | Owns | May edit |
| --- | --- | --- |
| `product-manager` | Goals, PRD, tickets, status board, ready checks, ticket and phase verdicts, proposals, `/retro` | `docs/PRD.md`, `docs/TICKETS.md`, `docs/pm/` (hook-enforced) |
| `ux-designer` | UX research, screen specs, design system and tokens, copy, design reviews | `design/`, `docs/DESIGN_SYSTEM.md`, `docs/ux/` (hook-enforced) |
| `principal-mobile-engineer` | All app code, architecture, performance, code review of every Kotlin change | app modules, Gradle, CI |
| `backend-engineer` | Supabase, R2, content scripts, remote data layer, cost and security | `supabase/`, `scripts/`, `:core:data` remote |
| `qa-engineer` | Manual QA at the end of every phase: design + result, bugs, phase verdict | `docs/qa/` (hook-enforced) |
| `security-reviewer` | Scans every commit a pull request would add for credentials, keys and files that must stay local; PASS or BLOCK per pushed commit | Its verdict in `.git/` and its memory (hook-enforced); never code |

- **Authority:** the PM proposes scope, priority and date changes; **the owner decides**. Nobody changes scope silently.
- **Asking the owner to decide** (owner, 8 Oct 2026): for each decision give the **problem** (what's wrong or missing, in plain words), the **considerations** (what each option costs and gives: time, money, risk, user impact), and the **suggested fixes** as lettered options with one recommendation. Keep the language simple; the owner answers in short form ("1A, 2B").
- **Branches and PRs:** one branch per phase (`phase-<n>`), one PR per phase, opened only when the phase is done. Tickets are commits on the phase branch.
- **Per ticket:** PM ready check → designer spec (UI tickets) → engineer builds and checks by hand → principal code review → designer review (UI) → PM ticket check → commit on the phase branch (`<ID>: <ticket name>`, handoff in the commit message) and push.
- **One builder at a time** (owner, 8 Oct 2026, to stay inside usage limits): only one agent builds code at once; small helpers (ready checks, reviews, research) may run alongside. Builders write their handoff file early and update it as they go.
- **End of phase:** QA manual pass (emulator via adb + a short device checklist for the owner; cloud sessions have no emulator, so QA runs in Claude Code on the owner's Mac with the Android Studio emulator) → fixes → QA re-test → PM phase gate → `/retro` → internal-testing build → `security-reviewer` PASS on the pushed head → one PR from `phase-<n>` to `main`. The owner merges.
- **Improving the team:** at every phase gate the PM runs `/retro` and proposes exact edits to agents, skills or rules. The owner approves; applied changes are logged in `.claude/agents/CHANGELOG.md`. Each agent keeps lessons in `.claude/agent-memory/<agent>/MEMORY.md`.

## Quality without automated tests

- The project has **no automated tests**: no unit, UI, screenshot or golden-frame tests. Don't write them or ask for them.
- Every push to a phase branch and every PR builds Android, runs lint and compiles the iOS framework in CI.
- Every ticket is run by hand on an emulator or phone. The ticket's commit message lists what was checked and measured; screenshots of UI states go in `docs/ux/reviews/<ID>/`.
- New effects get a reference render in `docs/qa/references/` for visual comparison and iOS parity.
- **Definition of done:**
  - CI green
  - "Done when" checked by hand
  - tokens only
  - strings in en, id, es, pt, hi (machine draft OK until P5)
  - PRD analytics events fire
  - code review and design review passed
  - docs updated
- Verify current library APIs against official docs when a ticket starts (Media3, ML Kit, MediaPipe and Compose MP move fast). Don't trust memory for class names or versions.

## Commands

Run from the repo root. The Gradle daemon runs on JDK 21 (`gradle/gradle-daemon-jvm.properties`; Gradle finds or downloads it, and CI sets up Temurin 21); app bytecode still targets JVM 17. `local.properties` (not committed) holds `sdk.dir`.

```bash
./gradlew :androidApp:assembleDebug                       # Android debug build
./gradlew lint                                            # Android lint; covers the KMP library modules through checkDependencies
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 # iOS framework compiles (needs macOS)
./gradlew :androidApp:installDebug                        # install on the running emulator/phone for hand checks
./gradlew :androidApp:installBenchmark                    # release-like build signed with the debug key, for fps and cold-start checks (never uploaded)
xcodebuild -project iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT="$PWD/iosApp/build" build  # iOS app for the simulator
```

Since AGP 9, the Android app is its own module (`:androidApp`); `:composeApp` is the shared KMP module that builds the `ComposeApp` framework for `iosApp/`. Shared Gradle setup lives in `build-logic/` (`memix.kmp.library`, `memix.kmp.compose`).

The first iOS link after adding a library takes up to 15 minutes while Kotlin/Native builds its caches; later links take under a minute. After the JDK changes, or on a "spawn helper" error, run `./gradlew --stop`.

## Current phase

P1 · Core and video editor (started 7 Oct 2026). Phase 0 merged to `main` (PR #7; P0-07 moved). The Phase 1 branch is `claude/phase-1-kickoff-cenfgz`, branched from `main`, because cloud sessions can't push to `phase-1`. Keep this line updated as phases advance.

## Open items that affect code

Settled by the owner on 8 Oct 2026 (decision log):
- Video background removal is **people only** in v1.
- Watermark: small "Memix" text, **top-left**, 3% margin.
- Media files live in **Supabase Storage for now** and move to Cloudflare R2 before launch. Read the media base URL from Remote Config (`media_base_url`), never hardcode it, so the move needs no app change.
- Performance bar: **no visible lag and no memory leaks, on Android and iOS**. There is no named reference phone; the numbers in the tickets (30 fps preview, 60 fps timeline, 100 ms edits, export time) are measured on the owner's own phone.
- Hindi uses a Devanagari display font (the designer picks an OFL face, e.g. Teko) where Anton has no glyphs.

Still open:
- WhatsApp sticker-pack spec must be checked at P2-09. Launch countries for trending lists are not final.

## Skills in this repo (`.claude/skills/`)

- **Workflow:** `ticket-workflow`, `product-acceptance`, `retro`, `play-release` (manual: `/play-release`)
- **Engineering:** `memix-architecture`, `code-standards`, `mobile-performance`, `new-feature`, `android-media-engine`, `media-effect`, `background-removal`, `ios-port`
- **Design:** `ux-research`, `screen-spec`, `design-tokens`
- **Backend:** `supabase-change`, `content-intake`
- **QA:** `qa-verification`
