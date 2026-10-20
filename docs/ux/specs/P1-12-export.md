# Export · P1-12

> **Draft, started 20 Oct 2026.** The decisions below are made unless marked open. To finish before the build: the PM's answers to "Proposals and questions", the engineer's capability and bitrate facts from the current Media3 docs (requests 1 and 2), and the P1-13 watermark row, which its own spec adds to the sheet.

Builds on `P1-04-editor-and-preview.md` (top bar, the reserved Export slot), `P1-02-gallery-picker.md` ("Not enough space", Free up space) and `P1-11-canvas.md` (canvas sizes: the shorter side is about 1080 px). Boards: `design/screens/Export.dc.html` (the sheet) and `design/screens/ExportDone.dc.html` (the result).

## Job
Turn the draft into a video file the user can post, fast and without surprises: pick quality, see size and time before starting, keep using the phone while it encodes, and find the file in the gallery. Every PRD job ends here.

**Done when** (TICKETS): 60 s at 1080p30 exports in 60 s or less on the owner's phone and plays in the gallery; LeakCanary shows no leaks after an export and a cancel.

**The one blue action:** **Export** in the editor's top bar (it arrives with this ticket), then **Export video** in the sheet, then **Done** on the result. One per surface.

**Fast path:** Export (1), Export video (2). The settings remember the last choice, so most exports are those two taps.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Export sheet (standard Sheet) | Export in the editor's top bar | Export video (→ Exporting); close, scrim, drag down, back or Escape (→ editor, nothing changes) |
| Exporting screen (full screen, pushed over the editor) | Export video | Finished (→ the same screen's Exported state); Cancel (→ editor, toast); failure (→ its error state) |
| Exported screen | The export finished and the file is in the gallery | × or back (→ the editor, draft unchanged); Done (→ Home); Make another (→ Home with the Create sheet open) |

- **Export from other editor states:** typing commits first; an open panel or a selection is left as it is behind the sheet; reorder mode explains like the ratio pill ("Tap Done to finish reordering first."). Playback pauses.
- **Kept:** the draft is untouched by exporting. The settings (resolution, frame rate) are remembered on the phone for the next export, across projects.

## Layout

### Export button (editor top bar, right)
- Primary Button, label "Export", `touch-target` tall, `radius-md`, `space-2` from the screen edge. P1-04's open question (top-right is a stretch for one thumb) is settled: every competitor puts the final action there, it's used once per meme, and a bottom placement would take tool bar room used many times per meme (Assumption).
- **The board's "1080p" quality pill is dropped.** It opened the same settings as Export, so it duplicated a control in a crowded bar (at 360 dp and 200% font the bar holds Close, the ratio pill and Export at most), and quality is chosen right before exporting, in the sheet. The ratio pill (P1-11) stays alone in the center.

### Export sheet
Standard Sheet (grabber, close button), as on the board:
1. **Title** "EXPORT" in `display` (Anton, uppercase; Teko in Hindi): a sheet hero, like Create.
2. **Resolution:** heading in `label` `text-secondary`, then a SegmentedChoice (`design/system/components/SegmentedChoice/README.md`, new) with **480p · 720p · 1080p · 2K · 4K**. The PRD lists five; the board showed four. The name means the canvas's **shorter side** (480, 720, 1080, 1440, 2160 px), so a 16:9 "1080p" is 1920 × 1080 and a 9:16 one 1080 × 1920.
3. **Frame rate:** **24 · 30 · 60 fps**.
4. *(P1-13)* the watermark row, then (P5-02) its rewarded-ad Remove.
5. **Facts line** in `timecode` `text-secondary`: the format on the left ("MP4 · H.264 · AAC"), the estimate on the right ("Est. 18 MB · about 20 s"). It wraps to two lines when it doesn't fit (Hindi, 200%).
6. **Notices**, when they apply (`body` `text-secondary`, above the button):
   - Not enough space (below).
   - A clip's file is missing: "A clip's file is missing, so it exports black." (P1-04's state.)
7. **Export video:** the primary Button, full width, at the bottom.

- **Defaults:** 1080p, 30 fps the first time; afterwards the last choice. If the last choice isn't possible for this project or phone (below), the nearest lower one.
- The sheet doesn't scroll at 100% font on 360 × 640; at 200% it grows to the long-content height and the middle scrolls (Sheet rule).

### Exporting screen
Full screen on `canvas`, pushed over the editor (320 ms), status bar as usual.
- Title "EXPORTING" in `display` at the top start, no close button.
- The meme's frame: the canvas shape on `stage`-black, the largest that fits the middle of the screen, showing the draft's first frame (as the Drafts thumbnail, P1-14) at the canvas ratio. Static.
- Under it: the ProgressBar (determinate), with "42%" in `title` `text` and "About 12 s left" in `body` `text-secondary` beside it. The time appears after 2 s and only when it's over 10 s (P1-02's rule).
- "You can leave Memix. Your video keeps exporting." in `body` `text-secondary`.
- **Cancel:** a secondary Button, full width at the bottom, in thumb reach.

### Exported screen (the ExportDone board)
- Title "EXPORTED" in `display`, × (IconButton, "Back to editor") at the top end.
- **The result:** the exported file in the canvas shape, `radius-lg`, with a centered play disc (`primary`, `on-primary` icon). Tap plays it once with sound and stops at the end; tap again pauses. No autoplay, no loop (MOTION 2). The watermark shows in it because it's in the file (P1-13).
- **Status:** a `success` pill with a check icon and `on-track` text: "Saved to your gallery · 0:08 · 1080p". The board said "Memix album"; "gallery" is the word users know on Android, and the album name appears in the gallery itself.
- **Share:** P5-05 adds the share row. In P1: open question (Proposals 2).
- **Footer:** Make another (secondary) and Done (primary), side by side, `space-3` apart; they stack at 200% font.

## Settings rules
- **What the phone can do** (request 1): resolutions and frame rates the encoder can't do for this canvas shape keep their normal look and **explain on tap** (design system rule), without changing the selection:
  - "This phone can't export 4K." / "This phone exports 4K at up to 30 fps." / "This phone can't export 60 fps at this resolution."
  - Picking 60 fps while 4K is selected on a phone that can't do both explains and keeps 30 fps; it never silently changes the other setting.
- **Codec:** H.264 for everything the phone's H.264 encoder can do (it plays everywhere people share memes); HEVC only where the size needs it and the phone has it (often 4K). The facts line names the codec, so it's never hidden. No codec choice in the interface (PRD: "H.264, or HEVC when the device supports it").
- **Upscaling** (clips smaller than the chosen resolution) is allowed without a warning: some platforms rank higher resolutions better, and the meme's captions are drawn sharp at any size (P1-10).
- **Estimate:**
  - **Size** = (video bitrate + 128 kbps audio) × length. Video bitrates follow YouTube's recommended upload rates for SDR (Guidance), which the engine sets explicitly so the estimate holds (request 2): 480p 2.5 / 4 Mbps (30 / 60 fps), 720p 5 / 7.5, 1080p 8 / 12, 2K 16 / 24, 4K 40 / 60; 24 fps uses the 30 fps rate. Shown rounded: under 10 MB to 0.1 MB, then whole MB, then GB with one decimal.
  - **Time:** until this phone has exported once, the video's length × a resolution factor (480p–1080p: 1×, 2K: 2×, 4K: 4×; Assumption, replaced by measurements). After that, from this phone's measured speed per pixel per second of video. Shown "about 20 s" (nearest 5 s under a minute), then "about 2 min".
- **Free space:** export needs the size estimate plus P1-02's 100 MB headroom (twice the estimate if the file is copied into the gallery after encoding; request 4). When it isn't there:
  - The notice "Not enough space. Free up about 240 MB to export at 1080p." with **Free up space** (Quiet button, P1-02's intent; hidden when the phone has no storage screen).
  - Export video stays enabled and explains on tap ("Free up space first, or pick a lower resolution."), as every blocked control does.
  - The check runs again when the sheet opens, when a setting changes and when the app returns to the foreground.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Sheet, default | Resolution and frame rate (last used), facts line, Export video | `export_title`, `export_resolution`, `export_frame_rate`, `export_res_*`, `export_fps_*`, `export_facts_format`, `export_estimate`, `export_start` |
| Option the phone can't do | Normal look; toast on tap | `explain_export_*` |
| Not enough space | Notice with Free up space | `export_no_space`, `editor_free_up_space`, `explain_export_no_space` |
| A clip's file is missing | Notice | `export_missing_media` |
| Empty project (defensive) | Export explains on tap; no sheet | `explain_export_empty` |
| Exporting | Frame, progress, time left, Cancel | `export_progress_title`, `export_percent`, `export_time_left`, `export_leave_ok`, `export_cancel` |
| Back pressed while exporting | Sheet "Stop exporting?": Keep exporting (secondary), Stop (danger). Close, scrim and back keep exporting. | `export_stop_title`, `export_keep`, `export_stop` |
| Cancelled | Back in the editor; toast | `export_stopped_toast` |
| Exported | Result, status pill, footer | `export_done_title`, `export_saved`, `export_make_another`, `export_done`, `export_back_to_editor`, `export_play_a11y` |
| Failed: out of space mid-export | Error state on the Exporting screen: message, Free up space (primary), Back to editor (secondary). The partial file is deleted. | `export_fail_title`, `export_fail_space` |
| Failed: encoder or engine error | Message, Try again (primary; at the next lower resolution if the error was the encoder's), Back to editor | `export_fail_title`, `export_fail_encoder`, `export_try_again` |
| Failed: unknown | Message, Try again, Back to editor | `export_fail_title`, `export_fail_other` |
| App in the background | Progress notification (below); the screen is current on return | notification copy |
| Memix stopped mid-export (process death) | Next start: nothing in the gallery, partial deleted; the notification says it stopped | `notif_export_stopped` |
| Offline | No difference | none |
| Permission | Notifications (Android 13+): open (Proposals 3). The gallery needs none (own files, Android 10+). | |

## Notification (Android)
Channel "Exports" (low importance: no sound).
| When | Title | Text | Actions | Tap |
| --- | --- | --- | --- | --- |
| Exporting | Exporting your meme | 42% · about 12 s left | Cancel | Opens Memix on the Exporting screen |
| Done | Your meme is saved | In your gallery · 0:08 · 1080p | none | Opens the Exported screen |
| Failed | Export didn't finish | Open Memix to try again. | none | Opens the error state |
| Stopped (process death) | Export stopped | Open Memix to export again. | none | Opens the draft |

The notification's progress is determinate and updates at most once a second.

## Interactions
- **Export video:** the sheet closes and the Exporting screen pushes in; the export starts at once. `export_start` fires.
- **Cancel** (button or notification): stops at once, deletes the partial file, pops back to the editor, toast "Export stopped." No confirmation: the tap is deliberate, and nothing but time is lost.
- **Back while exporting:** the "Stop exporting?" sheet, because a back gesture can be accidental and a long 4K export is worth one question. This is the only confirmation outside the unsaved-changes sheet (P1-04).
- **Leaving the app:** the export continues in a foreground service; the notification carries progress. The editor can't be used during an export (the Exporting screen covers it), so the file always matches what the user saw.
- **Finished:** the file is saved to the gallery ("Memix" album, request 4) before the Exported state shows; `export_complete` fires.
- **After an export** (P5-03): the interstitial, when its rules allow, appears between Exporting and Exported, never before the first export. Nothing to build here; keep the transition a single place where P5-03 can hook in.
- **Haptics:** one Confirm when the export finishes, if the screen is showing (content landed).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Export sheet | 200 ms slide over the scrim | 120 ms fade |
| Exporting screen | 320 ms push | 120 ms fade |
| Progress fill | Moves to each value over 120 ms, at most 10 updates a second (ProgressBar) | jumps |
| Exporting → Exported | 200 ms cross-fade of the content; the frame stays in place | 120 ms fade |
| Segment selection | 120 ms fill change | same |

## Copy
English source; machine drafts until P5. Budgets in characters.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `editor_export` | Export | 14 | Top-bar button |
| `export_title` | Export | 14 | Sheet hero, shown uppercase in Anton |
| `export_resolution` | Resolution | 20 | |
| `export_frame_rate` | Frame rate | 20 | |
| `export_res_480` … `export_res_4k` | 480p, 720p, 1080p, 2K, 4K | 6 | Not translated |
| `export_fps` | %1$d fps | 8 | "30 fps"; "fps" stays as is unless your language's video apps translate it |
| `export_facts_format` | MP4 · %1$s · AAC | 24 | %1$s = "H.264" or "HEVC"; not translated |
| `export_estimate` | Est. %1$s · about %2$s | 32 | %1$s = size ("18 MB"), %2$s = time ("20 s", "2 min") |
| `export_start` | Export video | 20 | Primary button |
| `explain_export_no_4k` | This phone can't export 4K. | 50 | |
| `explain_export_4k_30` | This phone exports 4K at up to 30 fps. | 50 | |
| `explain_export_no_60` | This phone can't export 60 fps at this resolution. | 60 | |
| `export_no_space` | Not enough space. Free up about %1$s to export at %2$s. | 80 | %1$s = "240 MB", %2$s = "1080p" |
| `explain_export_no_space` | Free up space first, or pick a lower resolution. | 60 | |
| `export_missing_media` | A clip's file is missing, so it exports black. | 60 | |
| `explain_export_empty` | Add a video or photo first. | 40 | |
| `export_progress_title` | Exporting | 14 | Uppercase in Anton |
| `export_percent` | %1$d%% | 5 | "42%" |
| `export_time_left` | About %1$s left | 24 | "About 12 s left" |
| `export_leave_ok` | You can leave Memix. Your video keeps exporting. | 70 | |
| `export_cancel` | Cancel | 16 | |
| `export_stop_title` | Stop exporting? | 30 | |
| `export_keep` | Keep exporting | 20 | |
| `export_stop` | Stop | 14 | Danger button |
| `export_stopped_toast` | Export stopped. | 30 | |
| `export_done_title` | Exported | 14 | Uppercase in Anton |
| `export_saved` | Saved to your gallery · %1$s · %2$s | 50 | %1$s = length "0:08", %2$s = "1080p" |
| `export_play_a11y` | Play your video | 24 | |
| `export_back_to_editor` | Back to editor | 20 | × label and error-state button |
| `export_make_another` | Make another | 18 | |
| `export_done` | Done | 12 | |
| `export_fail_title` | The export didn't finish | 40 | |
| `export_fail_space` | Your phone ran out of space. Free up space and try again. | 80 | |
| `export_fail_encoder` | This phone couldn't encode the video. Try again at a lower resolution. | 90 | |
| `export_fail_other` | Something went wrong. Your draft is safe. Try again. | 70 | |
| `export_try_again` | Try again | 18 | Reuse `editor_preview_retry` |
| `notif_channel_exports` | Exports | 20 | Android notification channel name |
| `notif_exporting_title` | Exporting your meme | 40 | |
| `notif_exporting_text` | %1$s · about %2$s left | 40 | |
| `notif_done_title` | Your meme is saved | 40 | |
| `notif_done_text` | In your gallery · %1$s · %2$s | 40 | |
| `notif_failed_title` | Export didn't finish | 40 | |
| `notif_failed_text` | Open Memix to try again. | 40 | |
| `notif_export_stopped_title` | Export stopped | 40 | |
| `notif_export_stopped_text` | Open Memix to export again. | 40 | |

Translator notes: "Export" means making the final video file (CapCut's word in your language is a good guide). Resolutions, "fps", "MP4", "H.264" and "AAC" are never translated. "Memix" never translates.

## Accessibility
- **Sheet:** the title is the pane title; Resolution and Frame rate are single-choice groups ("1080p, selected, 3 of 5"); options the phone can't do are announced with their explanation when chosen. The facts line is one node ("MP4, H.264, AAC. Estimated 18 megabytes, about 20 seconds": spoken strings, not the symbols).
- **Exporting:** focus lands on the title; the progress is a range with "42 percent"; milestones (25, 50, 75%) and the finish are announced politely; never every percent.
- **Exported:** focus on the title; the status pill is announced; the player is a button "Play your video".
- **Touch targets:** every button, segment and the × are 48 dp or more.
- **Contrast:** segment labels `text` on `canvas` 16.9:1, selected `on-primary` on white 18.9:1; facts `text-secondary` on `surface` 7.1:1; status pill `on-track` on `success` 9.3:1; play disc `on-primary` on `primary` 7.9:1.
- **200% font:** segments wrap into two rows (SegmentedChoice rule), the facts line wraps, footer buttons stack; nothing cut.
- **Reduce motion:** see Motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `export_start` | Export video is tapped | `editor: "video"`, `format: "mp4"`, `resolution` ("1080p"), `fps` (30), `length_s` |
| `export_complete` | The file is in the gallery | `editor`, `format`, `time_ms` (encode start to saved), `size_mb` |
| `export_fail` | The export errors | `editor`, `error_code` (`storage_full`, `encoder`, `missing_media`, `process_death`, `unknown`) |

A cancel is open (Proposals 4).

## QA compares
1. **Export sheet** at 360 × 640: five resolutions, three frame rates, facts line, Export video; then the not-enough-space notice (emulator storage filled).
2. **An option the phone can't do** (emulator without a 4K encoder): the toast.
3. **Exporting** at 40%: progress, time left, Cancel; then the notification in the shade.
4. **Stop exporting?** after a back gesture.
5. **Exported:** the result with the play disc, the status pill, the footer; then the file in the gallery's Memix album, playing.
6. **Hindi at 200% font:** the sheet (segments in two rows) and the Exported footer stacked.

**Measured (the Done when):** 60 s 1080p30 `preview-check`-style draft, release-type build on the owner's phone: Export video to `export_complete` ≤ 60 s; ffprobe shows 1080 × 1920 (or the canvas's shape at 1080 short side), 30 fps, H.264, AAC, the expected length within one frame; the file plays in the gallery. Estimate vs actual: size within 15%, time within 30% after the first export. LeakCanary after one export and one cancel: no leak.

## Requests to the principal mobile engineer
1. **Capabilities:** `VideoEngine.capabilities()` (planned in TECHNICAL_DESIGN): per codec, the largest size and frame rate the encoder supports for a given canvas shape (Media3 `EncoderUtil` / `MediaCodecInfo.VideoCapabilities`; check the current docs). The sheet asks it per option.
2. **Bitrates:** set the video bitrate explicitly per resolution and frame rate (Settings rules → Estimate) so estimates hold, and log actual size vs estimate.
3. **Foreground service** with the notification table, `dataSync` or the type the current Android docs require for media processing (check the Android 14+ foreground-service types), Cancel from the notification, and resume of the Exporting screen when the app reopens.
4. **Gallery:** save into `Movies/Memix` through MediaStore (no permission on Android 10+), file name `Memix_<yyyyMMdd_HHmmss>.mp4`. If Transformer can write straight to a MediaStore pending item, free space needs only the estimate; otherwise copying doubles it (the space check follows whichever is built).
5. **Remembered settings** in multiplatform-settings, app-wide.
6. **Learned speed:** after each export, store seconds per (megapixels × seconds of video) per resolution group, for the time estimate.
7. **Components:** SegmentedChoice (new README) in `:core:designsystem` and the catalog.
8. **Analytics:** as in the table.

## Proposals and questions for the PM
1. **Quality pill dropped** (design decision, FYI): the board's "1080p" pill duplicated the sheet. P1-04 and the SettingPill README already say so.
2. **Share in P1?** P1-12 saves to the gallery; the share row is P5-05. The first internal builds then have no way to post except through the gallery app. Option: a single "Share" (system share sheet, about half a day) on the Exported screen now, which P5-05 turns into the full row. Recommend yes; it's small and it's how testers will judge the app.
3. **Notification permission (Android 13+):** without it, the export still runs, but its progress and "saved" notification don't show in the shade. Options: A ask in context the first time the user taps Export video (system dialog, no pre-screen); B never ask in v1. Recommend A: leaving the app during a long export is a core case, and the ask comes exactly when its value is obvious. This is the first permission Memix asks for, so it's the owner's call.
4. **Cancel in analytics:** no PRD event. Options: `export_fail` with `error_code: "cancelled"` (no PRD change, but it inflates the failure rate) or a new `export_cancel {editor, progress}` (PRD change). Recommend the new event.
5. **Make another** goes to Home with the Create sheet open (the board). Confirm, or should it open the gallery picker directly?

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Recommended SDR upload bitrates: 480p 2.5 / 4 Mbps, 720p 5 / 7.5, 1080p 8 / 12, 1440p 16 / 24, 2160p 35–45 / 53–68 (standard / high frame rate) | Guidance | [YouTube recommended upload encoding settings](https://support.google.com/youtube/answer/1722171) |
| Undo or a deliberate tap beats a confirmation for reversible actions; confirm only where loss can't be undone | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| Apps can add their own media to MediaStore collections on Android 10+ without a storage permission | Guidance | [Android: access media files from shared storage](https://developer.android.com/training/data-storage/shared/media) |
| Android 13+ requires the POST_NOTIFICATIONS runtime permission; foreground-service notifications still appear in the Task Manager without it | Guidance | [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) |
| Mobile editors put Export at the top right and the resolution beside it | Assumption (product knowledge of CapCut, VN, InShot) | — |
