# Memix — Product Requirements

Source of truth: the "Memix — Product Requirements" Claude Doc (https://claude.ai/code/artifact/c81028c0-e543-4488-ace3-100bdcbb9e10). This file is an export from Oct 7, 2026; if the two disagree, ask the owner which one wins.

## Summary

Memix is the fastest way to make a meme, photo or video, with the perfect sound. The goal is **5,000 daily active users (DAU) by month 3 after the Android launch**, planned for early March 2027.

- **What it is:** one app with two separate editors (a video meme editor and a photo meme editor), a large regional meme-sound library, and ready-made templates.
- **The gap:** general editors such as CapCut are built for every kind of video, so finding and timing meme sounds is slow. Most meme makers are photo-only and template-only. Creators end up using two or three apps for one meme.
- **The bet:** a big, legal, local meme-sound library, one-tap sound placement and meme-native templates, inside an editor as capable as the common mobile editors.
- **How it's built:** Kotlin Multiplatform with Compose Multiplatform for shared UI and logic. Native video engines: Media3 on Android, AVFoundation on iOS. Android ships first, iOS follows from the same codebase.
- **Money:** ads only. Every editing feature is free, including background removal.

## Target users and use cases

The primary user is a TikTok or Reels creator, 13 or older, anywhere in the world. The secondary user makes quick memes for group chats.

| Job to be done | Example | Editor |
| --- | --- | --- |
| React to a clip with a meme sound | Drop a boom hit on the exact frame of a fail | Video |
| Make a classic photo meme | Top and bottom caption on a photo | Photo |
| Turn a photo into a video meme | Still photo + zoom punch + sound, exported as MP4 | Photo, then Video |
| Use a green screen meme | A meme template layered over the user's own clip | Video |
| Make a cut-out sticker | Remove a friend's background, export as a WhatsApp sticker | Photo |
| Make a comparison meme | A 2 to 4 panel layout with captions | Photo |

Users bring their own photos and videos from the gallery. Recording inside the app is not part of v1.

## Product principles

These six rules settle trade-offs when a decision isn't covered elsewhere in this doc.

1. **Sound first.** Any sound is at most two taps from the timeline, and lands at the playhead.
2. **Fast path, deep tools.** A basic meme takes under a minute. Pro tools sit one level down, never in the way.
3. **Neutral chrome, one blue.** Every screen uses untinted dark grays, as Adobe's editors do, so the user's colors aren't shifted. One sky-blue accent marks the main action and meme sounds; white marks selection, as in CapCut. The user's meme is always the most colorful thing on screen.
4. **Free core.** Every editing feature is free, including background removal. Ads only remove the watermark or appear after an export.
5. **Legal by default.** Every catalog sound, template, sticker and font has a recorded license.
6. **On the phone.** Editing, background removal and export run on the device. No cloud rendering, and no account needed.

## App structure

Every path ends in one of two separate editors: the Video editor or the Photo editor. A photo project can move to the Video editor in one tap with **Make it a video meme**, keeping its layers.

Bottom navigation has five items: **Home · Templates · Create (+) · Sounds · Drafts**.

| Screen | Purpose | What's on it |
| --- | --- | --- |
| Home | Start fast | Two big entry cards (Video meme, Photo meme), trending templates, trending sounds for the user's region |
| Create (+) | Pick an editor | Sheet with Video meme (gallery picker, videos and photos, multi-select) and Photo meme (gallery picker or a blank layout) |
| Templates | Browse formats | Video / Photo switch, category chips, preview grid |
| Sounds | Find sounds | Search; Trending, Local, Global, Favorites and My sounds tabs; categories; tap to preview |
| Drafts | Resume work | Projects saved on this phone, type badge, last edited |
| Video editor | Edit video memes | Preview, multi-track timeline, tool bar |
| Photo editor | Edit photo memes | Canvas with layers, format bar, tool bar |
| Export | Save and share | Format, quality, watermark option, share targets |

First launch: ad consent (EEA, UK and Switzerland only), then one screen that confirms language and region (prefilled from the phone), then Home. No sign-up.

## Video editor

The video editor ships the core toolset of common mobile editors in v1, plus meme shortcuts. Layout from top to bottom: preview canvas, play controls with timecode, multi-track timeline, tool bar.

| Area | v1 features |
| --- | --- |
| Media | Import videos and photos from the gallery (multi-select), add more at any time, replace a clip |
| Basic edits | Split, trim, delete, duplicate, reorder, crop, rotate, flip, freeze frame, reverse |
| Speed | Constant 0.1× to 10×; speed curves with presets (ramp, bullet, flash) and a custom curve |
| Canvas | Ratios 9:16, 1:1, 4:5, 3:4, 16:9 and custom; fit or fill; background color, blur or image |
| Layers | Unlimited video and photo overlays (picture-in-picture), opacity, blend modes, shape masks (rectangle, circle, linear, mirror), green screen keying (pick color, strength, edge softness) |
| Keyframes | Position, scale, rotation, opacity and volume, with easing |
| Text | Meme fonts including Anton, outline, shadow, background box, in / out / loop animations, text presets |
| Stickers | Static and animated (GIF, WebP) from catalog packs or the user's own files |
| Effects | About 30, placed on the timeline with intensity: zoom punch, shake, glitch, flash, RGB split, VHS, blur, deep-fry and more |
| Filters | About 20 filters, plus brightness, contrast, saturation, warmth, tint, sharpen, vignette and grain |
| Transitions | About 20 between clips |
| Audio | Multi-track; original audio volume, mute or detach; meme sounds; imported audio; voiceover from the microphone; volume, fade in/out, pitch, speed, echo, bass boost / distortion, voice-changer presets; waveforms on the timeline |
| Meme shortcuts | **Sound + zoom** (drop a sound with a synced zoom punch), quick top/bottom caption, manual beat markers |
| Background removal | People in video, free (see Background removal) |
| Project | Undo / redo (100 steps), auto-save on every change, drafts |

Each track type has its own color on the timeline: video gray, text orange, sticker and image gold, meme sound Memix blue, audio green, effect violet. Beat-sync and auto-captions are not in v1.

## Photo editor

The photo editor is a separate editor tuned for still images. It shares the text, sticker and filter engine with the video editor, so styles look the same in both. Layout from top to bottom: top bar, canvas with layers, format bar, tool bar.

| Area | v1 features |
| --- | --- |
| Meme formats | Classic (top and bottom text), Caption bar (white bar above the image), Panels (2 to 6 cells; grid, side by side or stacked; draggable dividers), Demotivational frame, Speech bubbles |
| Image tools | Crop, rotate, flip, straighten; brightness, contrast, saturation, warmth, sharpen, vignette; about 20 filters; deep-fry |
| Layers | Text, stickers, images, cut-outs, drawings; reorder, lock, hide, opacity |
| Draw | Brush sizes and colors, eraser |
| Censor | Blur, pixelate, black bar |
| Background removal | People, pets and objects, free; refine brush; save the cut-out as a sticker |
| Canvas | Ratios and custom size, background color or image |
| Project | Undo / redo, auto-save, drafts |
| Make it a video meme | Opens the photo in the Video editor as a clip with its layers, then opens the sound picker |

Exports are PNG, JPG (quality slider), GIF (when a layer is animated), copy to clipboard, and WhatsApp sticker packs. Sticker packs follow WhatsApp's published sticker spec (size, file type and count limits), checked during implementation.

## Background removal

Background removal is free and runs on the phone in both editors. Photos support any prominent subject (people, pets, objects). Videos support **people only in v1**, because the on-device subject model for Android handles still images only.

| Editor | Removes | Android (v1) | iOS (later) |
| --- | --- | --- | --- |
| Photo | People, pets, objects | [ML Kit Subject Segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android) (beta, still images only, model downloaded through Google Play services) | [Vision foreground instance mask](https://developer.apple.com/documentation/vision/vngenerateforegroundinstancemaskrequest) (iOS 17+) |
| Video | People | [MediaPipe Image Segmenter](https://developers.google.cn/mediapipe/solutions/vision/image_segmenter) with a person model in video mode | [Vision person segmentation](https://developer.apple.com/documentation/vision/applying-matte-effects-to-people-in-images-and-video) (iOS 15+), one sequence handler per clip for steady masks |

How it works for the user:

- One tap on **Remove background** on a selected photo, clip or layer.
- The result shows over a checkerboard, with a refine brush (erase, restore) and an edge-softness slider.
- Video shows a progress bar, previews at reduced resolution, and renders at full resolution on export.
- The first use on Android may show "Preparing…" while Google Play services downloads the model.

The ML Kit API is in beta and may change, so it sits behind our own interface with MediaPipe as the fallback.

## Sound library

The sound library is the main reason to choose Memix: **500 licensed sounds at launch, about 100 added each month**, served from the backend so the list changes without an app update.

- **Tabs:** Trending (monthly, per region plus global), Local (the phone's region), Global, Favorites, My sounds.
- **Regions:** the phone's region picks the Local tab. Launch with trending lists for 5 to 10 countries (see open items).
- **Categories:** Reactions, Impacts and hits, Fails, Suspense, Cartoon, Voice lines (recreated), Animals, Gaming, Local, Trending.
- **Search:** by name, tag and category, tolerant of typos.
- **Preview:** tap to play, with waveform and duration. Sound details show the source credit.
- **Add:** tap + to place the sound at the playhead on the meme sound track; then drag, trim, set volume and apply audio effects.
- **Favorites and recently used:** stored on the phone.
- **My sounds:** import an audio file or extract audio from a gallery video. These stay on the phone and are never uploaded.
- **Offline:** a starter pack of 50 sounds ships inside the app; every other sound is cached after its first download.
- **Audio spec:** AAC (.m4a), 128 kbps, loudness-normalized to −16 LUFS with peaks at −1 dBTP, so sounds don't jump in volume.
- **Report:** every sound has a Report option that sends a takedown request to the team.

Prefer CC0 sounds. A CC-BY sound is accepted only when an in-app credit satisfies its license.

## Templates

Templates give users a finished meme structure to fill. **v1 launches with 40 video templates and 60 photo templates**, with new trending templates added monthly from the CMS.

| Type | What the user does | Contents |
| --- | --- | --- |
| Green screen, overlay | The template plays on top; its green area shows the user's photo or video | Template video, slot position |
| Green screen, cut-out | A meme character keyed out of green is placed over the user's clip | Template video, key settings |
| Video preset | Fill 1 to 3 media slots and 1 or 2 captions | Sound cues at exact times, effects, text styles |
| Photo format | Fill photo and caption slots in a layout | Layout, text styles |
| Photo template | Caption a ready image | Licensed or self-made image, or an original illustrated version of a famous format |

The flow is: pick a template, fill its slots, preview, then open the result in the matching editor with everything still editable. Each template is a set of media files plus a JSON preset (slots, timings, sound cues, effects, text styles). Users can also import their own green screen videos and key them in the Video editor. The licensing policy below applies to every template asset.

## Export and sharing

Exports have **no length limit**: video is encoded straight to the output file, so length is limited only by free storage and time.

| Output | Options |
| --- | --- |
| Video | MP4 (H.264, or HEVC when the device supports it); 480p, 720p, 1080p, 2K, 4K; 24, 30 or 60 fps; any ratio. 4K60 only where the phone's encoder supports it |
| Photo | PNG, JPG (quality slider), GIF, copy to clipboard |
| Stickers | WhatsApp sticker pack |

- **Before export:** show the estimated file size and time, and check free space.
- **During export:** it keeps running in the background with a progress notification, and can be cancelled.
- **Watermark:** a small "Memix" text mark (no logo), top-left with a 3% margin, on every free export. Top-left avoids the buttons TikTok and Reels place on the right and bottom.
- **Remove watermark:** one rewarded ad removes it from that one export.
- **After export:** a share row with TikTok, Instagram (Reels and Stories), WhatsApp, YouTube Shorts, Save to gallery, and More (system share sheet). Files also save to a "Memix" album.

Direct sharing uses Android share intents aimed at each app. TikTok's own share SDK can come later if intents prove too limited.

## Monetization and ad rules

v1 earns from AdMob ads only. There are no subscriptions or in-app purchases, and no ad ever interrupts editing.

| Ad | Where | Rule |
| --- | --- | --- |
| Rewarded | Export sheet, "Remove watermark" | User-initiated; one completed ad removes the watermark from that one export |
| Interstitial | After an export finishes | At most 1 per 3 exports (photo and video counted together); never before the user's first export; never inside an editor or during export |
| Banner / native | None | Not used in v1 |

- The interstitial frequency is set in Firebase Remote Config, so it can change without an app update.
- Users in the EEA, UK and Switzerland see a consent form first through Google's UMP SDK. Google requires a certified consent platform there; without one, only limited ads serve ([AdMob consent requirements](https://support.google.com/admob/answer/13554116?hl=en)).
- The app is not child-directed (target audience 13+).
- A one-time "Remove ads" purchase is reconsidered once the app reaches 5,000 DAU.

## Content and licensing policy

Only content with a recorded license enters the catalog. Platforms fingerprint audio in uploads and can mute or remove a match ([how Instagram's system works](https://www.theippress.com/?p=8138)). Apple can also reject an app under guideline 5.2.1 until the developer shows proof of the right to use third-party content ([example rejection](https://developer.apple.com/forums/thread/734904)).

| Accepted | Not accepted |
| --- | --- |
| CC0 / public domain | Clips ripped from songs, films, TV shows or games |
| CC-BY, when an in-app credit meets the license | Downloads from YouTube ([YouTube terms](https://grandavehousing.calpoly.edu/news/youtube-tos-can-you-download) generally forbid downloading without permission) |
| Royalty-free licenses that allow distribution inside an app | Anything with an unknown license |
| Our own recordings and recreations |  |
| Packs licensed in writing |  |

- **Every catalog item records:** source URL, license type, license proof (link or screenshot), credit text, who added it, and when.
- **Takedowns:** a Report button in the app plus a support email. Setting `is_active = false` in the CMS hides the item on the next catalog refresh. Each request is logged, with a 72-hour response target.
- **User imports:** stay on the user's phone, are never uploaded, and need no moderation.
- **No community feed in v1**, so there is no user-generated content to moderate.
- **Fonts and stickers:** fonts must be OFL or licensed for app embedding; stickers follow the same rule as sounds.

This isn't legal advice. A one-hour review with an IP lawyer before launch is recommended.

## Platform, performance and quality

Android 10 and up ships first. iOS 17 and up follows from the same codebase; iOS 17 is the floor because photo background removal needs it.

| Target | Value |
| --- | --- |
| Cold start to Home | under 1.5 s on the reference phone |
| Open an editor | under 1 s |
| Preview playback, 1080p, 6 tracks | 30 fps minimum; 60 fps where the phone allows |
| Scrub response | under 100 ms per frame update |
| Export, 60 s at 1080p30 | 60 s or less on the reference phone (about real time) |
| Crash-free users | 99.5% or more |
| Download size | under 50 MB (ML models downloaded on demand) |

- **Reference phone:** a 2023 or newer upper-mid or flagship Android. Older phones still run every feature, just slower.
- **Stack:** Kotlin Multiplatform; Compose Multiplatform for shared UI; native video engines (Media3 on Android, AVFoundation on iOS); no FFmpeg.
- **Offline:** everything except catalog browsing and downloads works offline.
- **Accessibility:** touch targets of 48 dp or more, WCAG AA contrast, screen-reader labels on every tool.
- **Languages at launch:** English, Indonesian, Spanish, Portuguese, Hindi. Dark theme only.

## Metrics and analytics

The north-star metric is **DAU, with a target of 5,000 by month 3 after the Android launch**, measured in Firebase Analytics. Supporting targets: exports per DAU of 1.5 or more, day-1 retention of 35%, day-7 retention of 15%, and 99.5% crash-free users. These targets are starting assumptions to revisit after the first month of data.

| Event | Fires when | Parameters |
| --- | --- | --- |
| `onboarding_complete` | Language and region confirmed | language, region |
| `project_create` | A project starts | editor (video, photo), source (blank, template) |
| `template_open` | A template opens | template\_id, type |
| `sound_preview` | A sound plays in the library | sound\_id, tab |
| `sound_add` | A sound lands on a timeline | sound\_id, tab, editor |
| `tool_use` | A tool is applied | editor, tool |
| `bg_remove` | Background removal finishes | editor, success, duration\_ms |
| `export_start` | The user taps Export | editor, format, resolution, fps, length\_s |
| `export_complete` | The file is saved | editor, format, time\_ms, size\_mb |
| `export_fail` | Export errors | editor, error\_code |
| `watermark_removed` | A rewarded ad completes | editor |
| `share_target` | A share target is chosen | target |
| `sound_report` | A sound is reported | sound\_id |

Events carry no personal data, and analytics follow the user's consent choice.

## Legal, store compliance and listing

The store title becomes **"Memix: Meme Video & Photo" (25 characters)**. "Memix - Meme Video & Photo Editor" is 33 characters, and both stores cap titles at 30; Apple also cuts titles at about 26 in search results ([AppTweak](https://apptweak.com/en/aso-blog/app-name-guidelines-and-best-practices)).

| Item | Decision |
| --- | --- |
| Store title | Memix: Meme Video & Photo |
| Short description / iOS subtitle | Meme editor with meme sounds (28 characters) |
| Name check | "Memix" was used by an earlier meme app from IRL. Search WIPO Global Brand Database and the local trademark office before announcing; buy the domain |
| Privacy policy and support | Hosted on the app's domain, linked from both stores and the app |
| AdMob | Publish app-ads.txt on the same domain |
| Play Console | Target audience 13+, "contains ads", Data safety form (advertising ID, analytics, crash data), content rating questionnaire |
| Play testing gate | The account was created before 13 Nov 2023, so the 12-tester closed-test rule for new personal accounts doesn't apply ([Google Play help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-GB)). Internal testing is still used |
| Consent | Google UMP for EEA, UK and Switzerland; Apple's tracking prompt (ATT) on iOS later |
| Credits screen | Open-source licenses, font licenses, sound credits |

Store listings are localized in all five launch languages; the brand name stays untranslated.

## Release plan

Android launches around **7 March 2027** and iOS around **2 May 2027**, if the full v1 scope holds.

| Phase | Weeks | Dates | Scope |
| --- | --- | --- | --- |
| P0 | 1 | Oct 12 – Oct 18, 2026 | Foundation |
| P1 | 2–5 | Oct 19 – Nov 15 | Core + video editor (first internal build) |
| P2 | 6–8 | Nov 16 – Dec 6 | Photo editor + photo background removal |
| P3 | 9–11 | Dec 7 – Dec 27 | Sounds and audio |
| P4 | 12–17 | Dec 28 – Feb 7, 2027 | Pro video tools + video background removal |
| P5 | 18–19 | Feb 8 – Feb 21 | Ads, analytics, sharing, languages |
| P6 | 20–21 | Feb 22 – Mar 7 | Android QA and release → **Android launch ≈ Mar 7, 2027** |
| P7 | 22–29 | Mar 8 – May 2 | iOS → **iOS launch ≈ May 2, 2027** |

Each phase ends with a build in internal testing and a manual QA pass, so problems surface early. What each phase delivers:

- **0 · Foundation (1 week):** KMP project, CI, design system in Compose, Supabase and R2 set up.
- **1 · Core + video editor (4 weeks):** project model, timeline, Android engine preview and export, meme sounds on the timeline, text, watermark. First internal test build.
- **2 · Photo editor (3 weeks):** meme formats, image tools, layers, photo background removal, PNG / JPG / GIF / WhatsApp stickers, Make it a video meme.
- **3 · Sounds and audio (3 weeks):** full library with search, tabs, favorites, offline pack and imports; voiceover; audio effects.
- **4 · Pro video tools (6 weeks):** overlays, keyframes, masks, blend modes, green screen and templates, transitions, effects, filters, text animation, speed curves, video background removal. The late-December holidays fall here.
- **5 · Ads, analytics, sharing, languages (2 weeks).**
- **6 · Android QA and release (2 weeks):** device testing, store listing, production rollout.
- **7 · iOS (8 weeks):** AVFoundation engine, iOS platform pieces, TestFlight, App Review.

## Not in v1, risks and decisions

**Not in v1:** accounts and login, community feed, camera recording, auto-captions, text-to-speech, beat-sync, cloud rendering, subscriptions and purchases, AI generation, desktop and web, tablet-optimized layouts, and background removal of non-people subjects in video.

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Full scope for one developer | Dates slip | Every phase ships to internal testing; a cut list is ready (speed curves, blend modes, transition count) |
| Sound licensing | Muted posts, takedowns, store removal | License rule and CMS fields, takedown flow, lawyer review |
| Two native video engines | iOS later, engine bugs | One shared engine spec, reference renders compared during QA, Android first |
| Video background removal quality | Rough edges | People only in v1, refine brush, low-res preview |
| ML Kit subject segmentation is beta | API changes | Behind our own interface, MediaPipe fallback |
| Name conflict | Costly rename later | Trademark search before launch |
| File download costs | Bill grows with users | Cloudflare R2 from day one (free egress), on-device caching |
| Supabase Free limits | Backend restricted when over quota | Monitor usage, move to Pro at about 1,500 DAU |

**Open items**

- [ ] Pick the 5 to 10 launch countries for trending lists (proposed: Indonesia, United States, India, Brazil, Mexico, Philippines, Spain)
- [ ] Trademark search for "Memix" and buy the domain
- [ ] Confirm watermark position (proposed top-left)
- [ ] Verify WhatsApp sticker spec during the photo export ticket
- [ ] Book the IP lawyer review before launch

**Decision log (all decided 7 Oct 2026)**

| Decision | Choice |
| --- | --- |
| App stack | Kotlin Multiplatform, Compose Multiplatform shared UI, native video engines, no FFmpeg |
| Platform order | Android first, iOS after the Android launch |
| Editors | Two separate editors: video and photo |
| Background removal | Free in both editors |
| Accounts | None in v1 |
| Backend | Supabase (Free, then Pro at about 1,500 DAU) + Cloudflare R2 for files; Supabase dashboard as the first CMS |
| Crash and analytics | Firebase Crashlytics and Analytics |
| Money | Ads only; rewarded ad removes the watermark; capped interstitial after export |
| Watermark | Small "Memix" text, no logo |
| Look | Dark only; neutral Adobe Spectrum grays in CapCut's layout, sky-blue accent #2BB3F3, white selection (revised 7 Oct after the first draft's yellow and pink) |
| Name | Memix |
| Goal | 5,000 DAU by month 3 after launch |
| Languages | English, Indonesian, Spanish, Portuguese, Hindi |
| Testing | No automated tests; manual QA at the end of each phase by the QA agent, with device-only checks done by the owner |

How it's built: [TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md) · What to build, phase by phase: [TICKETS.md](TICKETS.md) · Look and feel: [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)
