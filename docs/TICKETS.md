# Build tickets

Source of truth: the "Memix — Product Requirements" Claude Doc (https://claude.ai/code/artifact/c81028c0-e543-4488-ace3-100bdcbb9e10). This file is an export from Oct 7, 2026; if the two disagree, ask the owner which one wins.

## How to work the tickets

Work top to bottom within each phase. One ticket is one branch and one pull request, and every phase ends with a build in Play internal testing and a manual QA pass of the whole phase.

- **IDs:** `P<phase>-<number>`, for example `P1-04`. The Needs column lists tickets that must merge first.
- **Sizes** (one developer working with Claude): S is up to 1 day, M is 2 to 3 days, L is 4 to 5 days.
- **Definition of done** for every ticket:
  - The Android app builds, the iOS framework compiles, and CI is green.
  - The engineer ran the change on an emulator or phone and listed what was checked in the PR. The project has no automated tests; QA tests manually at the end of each phase.
  - UI uses design-system tokens only (no raw colors or sizes).
  - Every user-facing string is a resource, with all five languages present (machine-drafted is fine until phase 5).
  - Analytics events named in the PRD fire.
  - The PRD or technical design is updated if the behavior changed.

## Phase 0 · Foundation (week 1, from 12 Oct)

| ID | Ticket | Done when | Size | Needs |
| --- | --- | --- | --- | --- |
| P0-01 | Create the KMP project: Android and iOS targets, Compose Multiplatform, version catalog, module skeleton from the technical design | The app launches on an Android emulator and an iOS simulator with a placeholder screen | M | — |
| P0-02 | CI on GitHub Actions | Each PR runs the Android build, lint and the iOS framework compile; merging requires green | M | P0-01 |
| P0-03 | Architecture base: Koin, navigation host, base ViewModel (UiState and Intent), result and error types, logging | A sample screen shows state from a use case | M | P0-01 |
| P0-04 | Design system in Compose: tokens, bundled fonts, core components (buttons, chips, sound chip, cards, sheets, tabs, sliders, toggles) | A catalog screen shows every component and the designer approves it against the design system | L | P0-01 |
| P0-05 | Bottom navigation and empty screens: Home, Templates, Create sheet, Sounds, Drafts | Navigation and back behavior work in the dark theme | S | P0-03, P0-04 |
| P0-06 | Supabase project: schema SQL, access rules, seed data (10 sounds, 3 templates) | The public key reads active rows only; writes are refused | M | — |
| P0-07 | Cloudflare R2 bucket, custom media domain, upload script | A test file loads from the media domain with immutable cache headers | S | — |
| P0-08 | Firebase on Android: Crashlytics, Analytics, Remote Config | A test crash shows in the console; a Remote Config value is read | S | P0-01 |
| P0-09 | String resources for English, Indonesian, Spanish, Portuguese, Hindi | Switching language changes the UI strings | S | P0-01 |

## Phase 1 · Core and video editor (weeks 2 to 5, from 19 Oct)

| ID | Ticket | Done when | Size | Needs |
| --- | --- | --- | --- | --- |
| P1-01 | Project model, serialization, SQLDelight storage, migrations | A project created, saved, force-closed and reopened comes back identical | M | P0-03 |
| P1-02 | Gallery picker (Android photo picker, multi-select videos and photos) with a cached copy of each file | Picked media appear in the project without a storage permission prompt | M | P1-01 |
| P1-03 | `VideoEngine` interface and the Android composition builder (main video sequence, audio sequences) | A sample project with every track type previews and exports correctly | L | P1-01 |
| P1-04 | Preview: player in Compose, play, pause, seek, timecode | 1080p preview plays at 30 fps or more on the reference phone | L | P1-03 |
| P1-05 | Timeline UI: tracks, pinch zoom, scroll, playhead, thumbnail strip, selection, track colors | Timeline scrolls at 60 fps on the reference phone | L | P1-04 |
| P1-06 | Clip edits: split, trim handles, delete, duplicate, reorder | Each edit shows in the preview within 100 ms | L | P1-05 |
| P1-07 | Undo and redo (100 steps), auto-save 500 ms after the last change | Killing the app mid-edit restores the draft | M | P1-01 |
| P1-08 | Meme sounds on the timeline from the bundled starter pack: add at playhead, move, trim, volume | Sound stays in sync within 1 frame in preview and export | M | P1-05 |
| P1-09 | Original audio: volume, mute, detach | Detached audio becomes its own audio-track clip | S | P1-04 |
| P1-10 | Text, basic: add, edit, font (Anton plus 4 more), color, outline, move / scale / rotate gestures, timing on the timeline | Text looks the same in preview and export | L | P1-04 |
| P1-11 | Canvas: ratios 9:16, 1:1, 4:5, 3:4, 16:9 and custom; fit or fill; background color or blur | Changing ratio keeps every layer inside the frame | M | P1-04 |
| P1-12 | Export: resolution and fps choices, device capability check, size and time estimate, free-space check, foreground service with notification, cancel | 60 s at 1080p30 exports in 60 s or less on the reference phone and plays in the gallery | L | P1-03 |
| P1-13 | Watermark: small "Memix" text, top-left, on free exports | Watermark shows by default and is absent when the export is marked watermark-free | S | P1-12 |
| P1-14 | Drafts screen: list, open, rename, duplicate, delete | Drafts sort by last edited | S | P1-01 |
| P1-15 | Release plumbing: signing and CI upload to the Play internal testing track | A build installs from internal testing | S | P0-02 |

## Phase 2 · Photo editor (weeks 6 to 8, from 16 Nov)

| ID | Ticket | Done when | Size | Needs |
| --- | --- | --- | --- | --- |
| P2-01 | Photo editor shell: canvas, layer model, move / scale / rotate gestures, layers panel (reorder, lock, hide, opacity) | A 10-layer scene stays smooth while dragging | L | P1-01, P0-04 |
| P2-02 | Meme formats: Classic, Caption bar, Panels (2 to 6 cells, draggable dividers), Demotivational, Speech bubbles | Each format exports correctly at 1080 px and 2160 px | L | P2-01 |
| P2-03 | Text and stickers in the photo editor, sharing the video editor's text engine | The same style looks identical in both editors | M | P2-01, P1-10 |
| P2-04 | Image tools: crop, rotate, flip, straighten; adjustments; 20 LUT filters; deep-fry | Filter ids and looks match the video filters | L | P2-01 |
| P2-05 | Draw (brush sizes and colors, eraser) and censor (blur, pixelate, black bar) | Strokes and censor areas stay editable as layers | M | P2-01 |
| P2-06 | `Segmenter` interface and ML Kit subject segmentation, with model availability check and "Preparing…" state | A person, pet or object cut-out takes under 1.5 s for a 12 MP photo on the reference phone | M | P0-03 |
| P2-07 | Photo background removal UX: one tap, checkerboard result, refine brush, edge softness, save as sticker | Refine strokes survive save and reopen | M | P2-06, P2-01 |
| P2-08 | Photo export: PNG, JPG with quality, GIF for animated layers, copy to clipboard, save to the "Memix" album | Each format opens correctly in the gallery and in chat apps | M | P2-01 |
| P2-09 | WhatsApp sticker pack export (check WhatsApp's current sticker spec first) | A pack installs into WhatsApp on a test phone | M | P2-08 |
| P2-10 | Make it a video meme: convert the photo scene to a video timeline, then open the sound picker | Every layer keeps its position, size and style | M | P2-01, P1-03 |

## Phase 3 · Sounds and audio (weeks 9 to 11, from 7 Dec)

| ID | Ticket | Done when | Size | Needs |
| --- | --- | --- | --- | --- |
| P3-01 | Catalog repositories: categories, sound pages, trending, search, with SQLDelight caching and refresh rules | Launching offline shows the cached catalog | L | P0-06, P1-01 |
| P3-02 | Sounds screen: Trending, Local, Global, Favorites and My sounds tabs; categories; typo-tolerant search | Search finds "bruh" when typed "bruhh" | L | P3-01 |
| P3-03 | Sound preview with waveform and details (credit, license) | A cached sound starts playing within 300 ms | M | P3-01 |
| P3-04 | Download manager: 500 MB cache with least-recently-used cleanup, progress, retries; 50-sound offline starter pack | Airplane mode still plays the starter pack and every cached sound | M | P3-01 |
| P3-05 | Sound picker sheet inside the video editor, adding to the meme sound track at the playhead | Two taps from editor to placed sound | M | P3-02, P1-08 |
| P3-06 | Favorites and recently used, stored on the phone | Both lists survive an app restart | S | P3-02 |
| P3-07 | My sounds: import an audio file; extract audio from a gallery video | Imported sounds work in the editor and are never uploaded | M | P3-02 |
| P3-08 | Voiceover recording from the microphone onto an audio track | Recording stays in sync with the preview | M | P1-08 |
| P3-09 | Audio effects: fades, pitch, speed, echo, bass boost / distortion, voice-changer presets; waveforms on the timeline | Preview and export sound the same | L | P1-08 |
| P3-10 | Report a sound | The report appears as a row in Supabase | S | P3-03 |
| P3-11 | Region and language: detect from the phone, confirm in onboarding, filter the Local tab | Changing region in settings changes the Local tab | S | P3-01 |
| P3-12 | Content intake script (trim, normalize, encode, waveform, upload, row template), then load the first 500 sounds | 500 sounds are live, each with complete license fields | L | P0-07 |

## Phase 4 · Pro video tools (weeks 12 to 17, from 28 Dec)

| ID | Ticket | Done when | Size | Needs |
| --- | --- | --- | --- | --- |
| P4-01 | Overlay layers (picture-in-picture video and photo): opacity, stacking order, transform | Five overlays at 1080p preview at 30 fps or more | L | P1-05 |
| P4-02 | Keyframes for position, scale, rotation, opacity and volume, with easing and keyframe markers on the timeline | Motion matches between preview and export | L | P4-01 |
| P4-03 | 10 blend modes and shape masks (rectangle, circle, linear, mirror) | Each mode and mask matches its reference render | L | P4-01 |
| P4-04 | Green screen keying on clips and overlays: pick color, strength, edge softness | A standard green screen clip keys cleanly at default settings | M | P4-01 |
| P4-05 | Templates: repository, Templates screen (Video / Photo switch, categories, previews), slot-fill flow, open in editor | A user fills a 2-slot preset and exports it | L | P3-01, P4-04 |
| P4-06 | About 30 video effects as timeline items with intensity | Each effect matches its reference render and preview stays at 30 fps or more | L | P1-05 |
| P4-07 | About 20 LUT filters and adjustments for video | Same ids and looks as the photo filters | M | P2-04 |
| P4-08 | About 20 transitions between clips | Transitions keep audio continuous | M | P1-06 |
| P4-09 | Speed: constant 0.1× to 10× and speed curves with presets | Audio follows the speed or mutes, per the user's choice | L | P1-06 |
| P4-10 | Reverse and freeze frame | Reversed clips play smoothly in preview | M | P1-06 |
| P4-11 | Text animations (in, out, loop) and text presets | Animations look the same in preview and export | M | P1-10 |
| P4-12 | Stickers in video: catalog packs, animated GIF and WebP, the user's own files | Animated stickers loop in sync with the timeline | M | P3-01 |
| P4-13 | Video background removal for people: MediaPipe person segmentation, per-frame mask cache, low-res preview, full-res export, refine | Progress shows while processing, and the export matches the preview | L | P2-06, P4-01 |
| P4-14 | Meme shortcuts: Sound + zoom combo, quick top/bottom caption, manual beat markers | Sound + zoom places both items in one tap | M | P4-06, P3-05 |
| P4-15 | Load 40 video templates, 60 photo templates and 20 sticker packs | Every item has complete license fields | L | P4-05 |

## Phase 5 · Ads, analytics, sharing, languages (weeks 18 to 19, from 8 Feb)

| ID | Ticket | Done when | Size | Needs |
| --- | --- | --- | --- | --- |
| P5-01 | AdMob with the UMP consent flow for EEA, UK and Switzerland; test ads in debug builds | The consent form appears on a device set to an EEA region | M | P0-08 |
| P5-02 | Rewarded ad on the export sheet removes the watermark | A completed ad produces one watermark-free export | M | P5-01, P1-13 |
| P5-03 | Interstitial after export, capped by Remote Config (1 per 3 exports, never the first export) | A manual run of 10 exports follows every cap rule | S | P5-01 |
| P5-04 | Every analytics event from the PRD, respecting consent | All events show in Firebase DebugView | M | P0-08 |
| P5-05 | Share row: TikTok, Instagram, WhatsApp, YouTube Shorts, Save, More | Each installed target opens with the file attached | M | P1-12 |
| P5-06 | Final translations for all five languages, including store listing text | No untranslated strings; the longest strings still fit | M | P0-09 |
| P5-07 | Credits and licenses screen (open-source, fonts, sound credits) | Every CC-BY item's credit is listed | S | P3-01 |

## Phase 6 · Android QA and release (weeks 20 to 21, from 22 Feb)

| ID | Ticket | Done when | Size | Needs |
| --- | --- | --- | --- | --- |
| P6-01 | Full QA pass on the device matrix and the fix list | No critical or major bugs open | L | All earlier |
| P6-02 | Performance: baseline profiles, profiling against the PRD targets | Every target in the PRD's quality table is met | M | — |
| P6-03 | Store listing: "Memix: Meme Video & Photo", short description, screenshots, feature graphic, in five languages | Listing passes Play review | M | P5-06 |
| P6-04 | Privacy policy, support page and app-ads.txt on the app's domain | All three URLs are live | S | — |
| P6-05 | Play Console forms: Data safety, content rating, target audience 13+, ads declaration | All forms are accepted | S | P6-04 |
| P6-06 | Production release with staged rollout (10%, 50%, 100%) and crash monitoring | Crash-free users stay at 99.5% or more at each step | S | P6-01 |

## Phase 7 · iOS (weeks 22 to 29, from 8 Mar)

Start the Apple Developer enrollment during phase 5 so it's ready when this phase begins.

| ID | Ticket | Done when | Size | Needs |
| --- | --- | --- | --- | --- |
| P7-01 | Apple Developer enrollment, bundle id, signing in CI | CI produces a signed iOS build | S | — |
| P7-02 | iOS app running the shared UI; fix iOS-only layout and gesture issues | Every screen works on a current iPhone | M | P7-01 |
| P7-03 | iOS `VideoEngine`, part 1: `AVMutableComposition` tracks and `AVPlayerLayer` preview | A basic project previews like Android | L | P7-02 |
| P7-04 | iOS `VideoEngine`, part 2: custom compositor on Metal / Core Image for effects, filters, transitions, masks, keying, overlays | Reference renders match Android side by side | L | P7-03 |
| P7-05 | iOS export with `AVAssetWriter`, background task and progress | 60 s at 1080p30 exports in about real time | M | P7-04 |
| P7-06 | iOS audio: mixing, effects, voiceover | Preview and export sound the same as Android | M | P7-03 |
| P7-07 | iOS `Segmenter`: Vision foreground mask for photos, person segmentation with a sequence handler for video | Cut-outs match the Android behavior | M | P7-02 |
| P7-08 | iOS platform services: photo picker, share sheet and direct targets, AdMob with UMP and the tracking prompt (ATT), Firebase | All services work on a TestFlight build | M | P7-02 |
| P7-09 | iOS photo encoders and WhatsApp sticker export | Photo exports match Android | M | P7-02 |
| P7-10 | TestFlight beta, App Store listing, privacy labels, App Review submission | The app is approved | M | All of phase 7 |
