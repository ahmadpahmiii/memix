# Kickoff prompt for the coding session

Copy everything inside the fence into a new Claude Code session opened in the repo folder that contains this kit (`CLAUDE.md`, `docs/`, `design/`, `.claude/`).

```text
You're starting the build of Memix, a Kotlin Multiplatform meme video + photo editor. Android ships first (launch ≈ 7 Mar 2027); iOS comes later from the same code. I'm the owner, a solo developer with a Mac. Planning is finished. You now run a team of five agents and build the app one ticket at a time.

Read these first, in this order, before doing anything:
1. CLAUDE.md: rules, stack, architecture, design rules, the team, quality without automated tests, definition of done
2. docs/PRD.md: what we're building (two separate editors, meme-sound library, templates, ads-only, no accounts)
3. docs/TECHNICAL_DESIGN.md: modules, Project data model, VideoEngine/Segmenter interfaces, Supabase schema + RLS, R2, CI
4. docs/TICKETS.md and docs/pm/STATUS.md: the phased backlog with "Done when" criteria, and where we are
5. docs/DESIGN_SYSTEM.md + design/tokens.json + design/screens/*.dc.html: the v2 look ("neutral chrome, one blue": neutral grays, sky blue #2BB3F3 as the only accent, white selection). The old yellow/pink look is rejected; never use it.

The team (.claude/agents) and who owns what:
- product-manager: goals, tickets, status, ready checks, ticket and phase verdicts, proposals, /retro. Proposes scope, priority and date changes; I decide.
- ux-designer: UX research, a screen spec before every UI ticket, the design system, design reviews. Never edits code.
- principal-mobile-engineer: all app code; clean, readable, performance-first; reviews every Kotlin change.
- backend-engineer: Supabase, R2, content scripts, remote data layer, cost and security.
- qa-engineer: manual QA at the end of each phase on an Android emulator via adb, plus a short real-phone checklist for me. Files bugs, gives the phase verdict. Never edits code.
Hooks keep the PM, designer and QA inside their own files. Follow the ticket-workflow skill for the order of steps.

Rules that matter most:
- No automated tests in this project: no unit, UI, screenshot or golden-frame tests. Check every change by hand on the emulator and list what you checked in the PR.
- Stack: KMP + Compose Multiplatform; clean architecture + MVVM (StateFlow UiState + Intent); Koin; Ktor + supabase-kt; SQLDelight; kotlinx.serialization; Coil 3. Pin the latest stable versions on creation day, checked on official pages, not from memory.
- Video: Media3 on Android, AVFoundation on iOS, behind VideoEngine. Background removal: ML Kit (photos) + MediaPipe (people in video), Apple Vision later, behind Segmenter. No FFmpeg, no GPL.
- Time is microseconds everywhere; Project is immutable; effects are declarative specs with a reference render in docs/qa/references/.
- Tokens only in UI; strings in en, id, es, pt, hi.
- If docs and reality disagree (an API changed, a version doesn't exist, a requirement conflicts), stop and tell me. Report honestly what you ran and what you couldn't.

Start Phase 0 · Foundation:
1. Ask product-manager for the P0 ready check (update docs/pm/STATUS.md).
2. Then P0-01 with principal-mobile-engineer: create the KMP project (Android + iOS targets, Compose Multiplatform, version catalog, the module skeleton exactly as in docs/TECHNICAL_DESIGN.md, a placeholder screen that runs on an Android emulator and an iOS simulator). Before generating files, show me:
   (a) the module list with each module's source sets,
   (b) the libs.versions.toml entries with the versions you found and where you checked them,
   (c) the Gradle task names for: Android debug build, lint, iOS simulator framework link, install on the emulator.
3. Build it. Update CLAUDE.md → Commands with the real task names, then continue with P0-02 (CI: build + lint + iOS compile, no tests) unless I say otherwise.
At the end of P0, run the end-of-phase steps: QA manual pass, PM phase gate, /retro.
```

## Official docs to check at the start of the matching tickets

| Topic | Link | Tickets |
| --- | --- | --- |
| Compose Multiplatform versions and compatibility | https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html | P0-01 |
| Media3 Transformer: Composition | https://developer.android.com/media/media3/transformer/composition | P1-03, P1-04, P1-12 |
| Media3 multi-asset editing | https://developer.android.com/media/media3/transformer/multi-asset | P1-03, P4-01 |
| ML Kit Subject Segmentation (Android) | https://developers.google.com/ml-kit/vision/subject-segmentation/android | P2-06 |
| MediaPipe Image Segmenter | https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter | P4-13 |
| Apple Vision: matte effects for people | https://developer.apple.com/documentation/vision/applying-matte-effects-to-people-in-images-and-video | P7-07 |
| Apple Vision: foreground instance mask | https://developer.apple.com/documentation/vision/vngenerateforegroundinstancemaskrequest | P7-07 |
| supabase-kt | https://github.com/supabase-community/supabase-kt | P0-06, P3-01 |
| Supabase pricing and billing FAQ | https://supabase.com/pricing · https://supabase.com/docs/guides/platform/billing-faq | P0-06 |
| AdMob UMP (GDPR) for Android | https://developers.google.com/admob/android/privacy/gdpr | P5-01 |
| AdMob consent requirements | https://support.google.com/admob/answer/13554116 | P5-01 |
| Play testing requirements for new personal accounts | https://support.google.com/googleplay/android-developer/answer/14151465 | P6 |
| Cloudflare R2 pricing | https://developers.cloudflare.com/r2/pricing/ | P0-07 |
| Claude Code subagents (agent files, memory, hooks) | https://code.claude.com/docs/en/sub-agents | when changing the team |

## Design references

- Design system (tokens, components, brand book): https://claude.ai/artifact/7HzM44Ra1z4juDDr4m5kpy
- App screens canvas (9 boards): https://claude.ai/artifact/9sJhkuQFurxbC7wRdpFfpB
- Product doc (PRD, technical design, tickets): https://claude.ai/code/artifact/c81028c0-e543-4488-ace3-100bdcbb9e10

These links are private to you until you share them. The coding session works from the exported files in this kit.
