# Export · P1-12

> **Final, 21 Oct 2026.** Draft of 20 Oct finished with the owner's answers (21 Oct: one Share button, notification permission at the first Export video, `export_cancel`; `docs/pm/decision-log.md`), the PM's clarifications (20 Oct: Make another, `export_start` timing) and the engineer's encoder facts (`docs/research/P1-12-encoder-facts.md`, 21 Oct). The watermark row is specified in `P1-13-watermark.md` and only placed here.

> **Corrected after the design review, 22 Oct 2026** (`docs/ux/reviews/P1-12.md`): the Top bar at large font rule, the 200% rules for the Exporting, Exported and failure screens, the Free up space request, the frame rate "can't" toast, the failure heading, spoken facts line and progress, and `export_res_*` as constants. Each change is marked "(review)".

Builds on `P1-04-editor-and-preview.md` (top bar, the reserved Export slot), `P1-02-gallery-picker.md` ("Not enough space", Free up space) and `P1-11-canvas.md` (canvas sizes: the shorter side is about 1080 px; custom shapes up to 2.39:1). Boards: `design/screens/Export.dc.html` (the sheet) and `design/screens/ExportDone.dc.html` (the result; its share grid is P5-05's).

## Job
Turn the draft into a video file the user can post, fast and without surprises: pick quality, see size and time before starting, keep using the phone while it encodes, find the file in the gallery, and share it. Every PRD job ends here.

**Done when** (TICKETS): 60 s at 1080p30 exports in 60 s or less on the owner's phone and plays in the gallery; LeakCanary shows no leaks after an export and a cancel.

**The one blue action:** **Export** in the editor's top bar (it arrives with this ticket), then **Export video** in the sheet, then **Done** on the result. One per surface.

**Fast path:** Export (1), Export video (2). The settings remember the last choice, so most exports are those two taps. Posting is one more: Share (3), then the app in the system share sheet.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| Export sheet (standard Sheet) | Export in the editor's top bar | Export video (→ Exporting); close, scrim, drag down, back or Escape (→ editor, nothing changes) |
| Exporting screen (full screen, pushed over the editor) | Export video | Finished (→ the same screen's Exported state); Cancel (→ editor, toast); failure (→ its error state) |
| Exported screen | The export finished and the file is in the gallery | × or back (→ the editor, draft unchanged); Done (→ Home); Make another (→ Home with the Create sheet open; PM, 20 Oct); Share opens the system share sheet over it and returns to it |

- **Export from other editor states:** typing commits first; an open panel or a selection is left as it is behind the sheet; reorder mode explains like the ratio pill ("Tap Done to finish reordering first."). Playback pauses.
- **Kept:** the draft is untouched by exporting. The settings (resolution, frame rate) are remembered on the phone for the next export, across projects. Watermark-free is never remembered (P1-13).

## Layout

### Export button (editor top bar, right)
- Primary Button, label "Export", `touch-target` tall, `radius-md`, `space-2` from the screen edge. P1-04's open question (top-right is a stretch for one thumb) is settled: every competitor puts the final action there, it's used once per meme, and a bottom placement would take tool bar room used many times per meme (Assumption).
- **The board's "1080p" quality pill is dropped** (PM, 20 Oct). It opened the same settings as Export and crowded the bar; quality is chosen right before exporting, in the sheet. The ratio pill (P1-11) stays alone in the center.
- **At large font (review):** the pill stays at the true center while it clears both neighbors by `space-2`; when it doesn't (the Export label is long: es "Exportar" at 200% is about 170 dp wide and would cover the pill by about 40 dp), the pill moves left by the overlap and nothing else moves. Close and Export never move or shrink, and a label never gets a line cap. Build as a custom `Layout` in the top bar, not a `Box` with overlapping aligns.

### Export sheet
Standard Sheet (grabber, close button), as on the board, from the top:
1. **Title** "EXPORT" in `display` (Anton, uppercase; Teko in Hindi): a sheet hero, like Create.
2. **Resolution:** heading in `label` `text-secondary`, then a SegmentedChoice (`design/system/components/SegmentedChoice/README.md`) with **480p · 720p · 1080p · 2K · 4K**. The name means the canvas's **shorter side** (480, 720, 1080, 1440, 2160 px), so a 16:9 "1080p" is 1920 × 1080, a 9:16 one 1080 × 1920, and the widest custom 1080p about 2604 × 1092.
3. **Frame rate:** **24 · 30 · 60 fps**.
4. **Watermark row** (P1-13 spec → Export sheet row). P5-02 adds its rewarded-ad Remove.
5. **Facts line** in `timecode` `text-secondary`: the format on the left ("MP4 · H.264 · AAC"), the estimate on the right ("Up to 18 MB · about 20 s"). It wraps to two lines when it doesn't fit (Hindi, 200%).
6. **Notices**, when they apply (`body` `text-secondary`, above the button, in this order):
   - Last setting lowered (Settings rules → What the phone can do).
   - Not enough space (Settings rules → Free space).
   - A clip's file is missing: "A clip's file is missing, so it exports black." (P1-04's state.)
7. **Export video:** the primary Button, full width, at the bottom.

- **Defaults:** 1080p, 30 fps the first time; afterwards the last choice. If the last choice isn't possible for this canvas and phone, the nearest possible one below it, with the "lowered" notice.
- The sheet doesn't scroll at 100% font on 360 × 640 (with the watermark row and one notice); at 200% it grows to the long-content height and the middle scrolls (Sheet rule).
- **Spacing (review):** the facts line and the notices sit in one Column with no `spacedBy`; each notice carries `space-4` of top padding inside its fade, so a hidden notice takes no space (a hidden `AnimatedVisibility` still takes its `spacedBy` gap: three hidden notices added 48 dp of blank under the facts line).
- **While it closes (review):** the sheet keeps showing its last content during the 200 ms slide-out (Export video, close), not an empty panel.

### Exporting screen
Full screen on `canvas`, pushed over the editor (320 ms), status bar as usual.
- Title "EXPORTING" in `display` at the top start, no close button.
- The meme's frame: the canvas shape, the largest that fits the middle of the screen, showing the draft's first frame (as the Drafts thumbnail, P1-14) at the canvas ratio. Static. Its fill is the canvas color the user chose (`stage` black for Blur, which isn't drawn here), as built (review: accepted; it matches the file better than a fixed black).
- Under it: the ProgressBar (determinate), then, `space-2` below it, "42%" in `title` `text` with "About 12 s left" in `body` `text-secondary` beside it (review: `space-2`, not `space-4`, so the bar and its numbers read as one group). The time appears after 2 s and only when it's over 10 s (P1-02's rule).
- "You can leave Memix. Your video keeps exporting and saves to your gallery." in `body` `text-secondary`. True with or without notification permission, so it needs no variant.
- **Cancel:** a secondary Button, full width at the bottom, in thumb reach.
- **Large font (review):** the frame shrinks first, down to 30% of the screen height; below that the whole screen scrolls, so Cancel is always reachable. The same rule for the failure state below. Never a clipped button (estimate from the type sizes: Hindi at 200% needs about 570 dp where a 360 × 640 phone leaves about 548 dp; the failure state needs about 700 dp; confirm on the owner's screenshots).

### Failure state (the Exporting screen after an error)
Same frame and rules as above. Title "NOT EXPORTED" (`export_fail_heading`, `display`), the frame, the error message in `body` `text-secondary` (the message names the problem, so no second headline; review: the title no longer says "EXPORTING" over a failure), then the two buttons stacked, primary above secondary (States table). The message is a polite live region with its text merged into one node.

### Exported screen (the ExportDone board)
From the top:
- Title "EXPORTED" in `display`, × (IconButton, "Back to editor") at the top end.
- **The result:** the exported file in the canvas shape, `radius-lg`, with a centered play disc (PlayDisc: `play-disc-size` 56, `primary`, `on-primary` icon; new token, review). Tap plays it once with sound and stops at the end; tap again pauses. No autoplay, no loop (MOTION 2). The disc fades out while it plays and back when it stops (120 ms). The watermark shows in it because it's in the file (P1-13). The whole picture is the target, named "Play your video", or "Pause your video" while playing (`export_pause_a11y`, added in the build and approved).
- **Status:** a `success` pill with the new `check` icon and `on-track` text: "Saved to your gallery · 0:08 · 1080p". The board said "Memix album"; "gallery" is the word users know on Android, and the album name appears in the gallery itself.
- **Share** (owner, 21 Oct): one secondary Button, full width, in the place of the board's share grid, `space-4` below the pill: the new `share` icon plus "Share". It opens the Android share sheet with the video attached. P5-05 replaces this button with the share row (the board's grid); the place and order stay, so the screen doesn't jump when it lands.
  - Why secondary and not blue: Done stays the one primary, as on the board and after P5-05, whose six targets are neutral tiles too. A blue Share now would move the screen's primary and then take it away again (Assumption; QA notes whether testers find Share at once).
- **Footer:** Make another (secondary) and Done (primary), side by side, `space-3` apart; they stack at 200% font, Make another above Done, so Done stays last in reading order and lowest, nearest the thumb.
- **Large font (review):** the result keeps at least 40% of the screen height; the title row, result, status, Share and footer then scroll together as one screen when they don't fit. The result never shrinks to nothing and the footer is never cut.

## Settings rules

### What the phone can do
The engine checks each resolution × frame rate for **this canvas's real size**, not the landscape name (request 1):
- **Output size:** the canvas size scaled so its shorter side is the resolution's number, each side rounded to the chosen encoder's alignment (even at least). The shape changes by under 1%, which nobody can see.
- **Checked with the long side as width,** because Transformer encodes portrait frames turned and writes the rotation into the file (encoder facts → 1). So 9:16 1080p is checked as 1920 × 1080, and the widest custom 4K as 5162 × 2160.
- **Codec:** H.264 if its encoder takes that size and rate (it plays everywhere people share memes), else HEVC if the phone has it, else not possible. The facts line names the codec, so it's never hidden. No codec choice in the interface (PRD: "H.264, or HEVC when the device supports it").
- **No silent shrinking:** the export runs with format fallback off (encoder facts → 1), so a size the encoder refuses fails instead of exporting smaller than the user chose. The sheet's check should prevent that; if it happens anyway, the encoder failure state offers the next lower resolution.

How the sheet shows it. Options keep their normal look (SegmentedChoice: never dimmed, never hidden) and act as follows:

| The tapped option | What happens | Toast |
| --- | --- | --- |
| Possible with the other setting | Selected | none |
| Possible, but not with the other setting (4K at 60 fps on a phone that does 4K only up to 30) | **The tapped option wins** and the other setting steps down to the nearest value that works; both changes are visible in the sheet | "This phone exports 4K at up to 30 fps, so it's set to 30 fps." / "This phone exports 60 fps at up to 2K, so it's set to 2K." |
| Not possible at any frame rate, and the **phone** is the limit (the resolution fails at the standard 16:9 size too) | Selection unchanged | "This phone can't export 4K." |
| A **frame rate** the phone can't do at any resolution (review: the build handles it; the draft table had no row) | Selection unchanged | "This phone can't export 60 fps." (same string, %1$s = "60 fps") |
| Not possible at any frame rate, and the **shape** is the limit (the same resolution works at 16:9, but not at this canvas's size) | Selection unchanged | "This phone can't export 4K in this shape. Try 2K, or change the shape in Canvas." (%2 = the highest resolution that works for this canvas) |

- **Why the tapped option wins:** the tap is the user's newest intent. Refusing it with "pick 30 fps first" turns one tap into a read-and-two-taps dance; changing the other setting with a toast that says so is visible and undone with one tap (Forgiving; Assumption). The draft's "explain and keep" is replaced.
- **Why name the shape:** a custom 2.39:1 canvas at 4K needs a codec level almost no phone has (encoder facts → 1), while the same phone exports 9:16 4K fine. "This phone can't export 4K" would be false and would send users looking for a better phone instead of a lower resolution or a narrower shape. "Canvas" is the panel's exact title (P1-11 `canvas_title`).
- **Remembered setting not possible here** (a 4K user opens a custom-shape project, or a new phone): the sheet opens on the nearest possible setting below it and shows the notice "Your last setting doesn't work here, so it's set to 2K · 30 fps." The notice goes once the user picks anything. Nothing changes silently.
- **No resolution possible at all** (defensive; 480p works on every phone we know of): Export video explains on tap, "This phone can't export this shape. Change the shape in Canvas, then try again."
- **Not ready yet:** the capability check runs once per app start, off the main thread, warmed when the editor opens (request 1). If the sheet opens before it finishes, the options act as possible; the lowered notice and toasts apply as soon as it's ready. If Export video is tapped first, the export waits for the check at 0% on the Exporting screen.

### Upscaling
Clips smaller than the chosen resolution are allowed without a warning: some platforms rank higher resolutions better, and captions are drawn sharp at any size (P1-10).

### Estimate
- **Size, an upper bound:** (video bitrate + 128 kbps audio) × length × 1.05. Video bitrates follow YouTube's recommended upload rates for SDR (Guidance), set explicitly in VBR mode (request 2): 480p 2.5 / 4 Mbps (30 / 60 fps), 720p 5 / 7.5, 1080p 8 / 12, 2K 16 / 24, 4K 40 / 60; 24 fps uses the 30 fps rate. Where the encoder's bitrate range clamps a rate, the estimate uses the clamped value. The 5% covers the MP4 container and short encoder overshoots (Assumption, measured at QA).
- **Shown as "Up to 18 MB"** (decided, encoder facts question P1). Hardware VBR lands near the target on moving footage but undershoots on stills, text and held frames, which are most memes, so the file is often smaller and never meant to be larger. "Up to" is true for every file; "about" would be wrong on the low side for most memes; "up to about" hedges twice. Rounded: under 10 MB to 0.1 MB, then whole MB, then GB with one decimal.
- **Time:** until this phone has exported once, the video's length × a resolution factor (480p–1080p: 1×, 2K: 2×, 4K: 4×; Assumption, replaced by measurements). After that, from this phone's measured speed per pixel per second of video. Shown "about 20 s" (nearest 5 s under a minute), then "about 2 min".

### Free space
- **Needed:** the size estimate plus P1-02's 100 MB headroom, when the encoder writes straight into the gallery item (encoder facts → 4, the recommended build). If the device check of that direct write fails and the build falls back to a temporary file plus a copy, twice the estimate plus 100 MB. The check follows whichever is built (request 4).
- **When it isn't there:**
  - The notice "Not enough space. Free up about 240 MB to export at 1080p." with **Free up space** (Quiet button, P1-02's intent; hidden when the phone has no storage screen). The amount **shown** is needed minus free, rounded up to the next 10 MB.
  - **What the button asks the storage screen for (review):** the **total** the export needs free (the estimate plus the 100 MB headroom, or twice the estimate plus it), never the missing amount. Android's `EXTRA_REQUESTED_BYTES` is "the total size that an application is interested in allocating", so asking for 240 MB when 300 MB is already free tells Android there is nothing to do (P1-02 passes its total the same way). The same on the failure state: the needed total for the failed choice, not the flat headroom.
  - Export video stays enabled and explains on tap ("Free up space first, or pick a lower resolution."), as every blocked control does.
  - The check runs again when the sheet opens, when a setting changes and when the app returns to the foreground.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Sheet, default | Resolution and frame rate (last used), watermark row, facts line, Export video | `export_title`, `export_resolution`, `export_frame_rate`, `export_res_*`, `export_fps`, `export_facts_format`, `export_estimate`, `export_start`; watermark row: P1-13 |
| Option not possible with the other setting | The tapped option selected, the other stepped down; toast | `toast_export_fps_lowered`, `toast_export_res_lowered` |
| Option the phone can't do | Selection unchanged; toast | `explain_export_no_res` |
| Option this shape can't do | Selection unchanged; toast | `explain_export_shape` |
| Remembered setting not possible here | The nearest lower setting selected; notice until the user picks | `export_lowered_notice` |
| No resolution possible (defensive) | Export video explains on tap | `explain_export_no_shape` |
| Not enough space | Notice with Free up space | `export_no_space`, `editor_free_up_space`, `explain_export_no_space` |
| A clip's file is missing | Notice; the export runs, the clip exports black | `export_missing_media` |
| Empty project (defensive) | Export explains on tap; no sheet | `explain_export_empty` |
| First Export video on Android 13+ | The Exporting screen pushes in and the export starts; Android's notification dialog shows over it, once (Interactions) | system dialog |
| Exporting | Frame, progress, time left, Cancel | `export_progress_title`, `export_percent`, `export_time_left`, `export_leave_ok`, `export_cancel` |
| Back pressed while exporting | Sheet "Stop exporting?": Keep exporting (secondary), Stop (danger). Close, scrim and back keep exporting. | `export_stop_title`, `export_keep`, `export_stop` |
| Cancelled | Back in the editor; toast | `export_stopped_toast` |
| Exported | Result, status pill, Share, footer | `export_done_title`, `export_saved`, `export_share`, `export_make_another`, `export_done`, `export_back_to_editor`, `export_play_a11y` |
| Failed: out of space mid-export | Error state on the Exporting screen: heading, message, Free up space (primary), Back to editor (secondary). The partial file is deleted. | `export_fail_heading`, `export_fail_space`, `export_free_up_space` |
| Failed: encoder (incl. a size the encoder refused) | Message, "Export at 720p" (primary; the next lower resolution that the check allows), Back to editor. At 480p: Try again instead. | `export_fail_heading`, `export_fail_encoder`, `export_try_lower`, `export_try_again` |
| Failed: a clip couldn't be read (`missing_media`, `decoder`) | Message, Back to editor (primary), Try again (secondary) | `export_fail_heading`, `export_fail_media` |
| Stopped by Android (time limit or a refused service start) | Message, Try again (primary), Back to editor | `export_fail_heading`, `export_fail_system` |
| Failed: unknown | Message, Try again (primary), Back to editor | `export_fail_heading`, `export_fail_other` |
| App in the background | Progress notification when allowed (below); the screen is current on return, including Exported or a failure that happened meanwhile | notification copy |
| Notifications not allowed | Nothing in the shade; the export shows only in Android's Task Manager (Active apps). The Exporting screen is unchanged; on return it shows the current state. No in-app nag. | none |
| Memix stopped mid-export (killed, crashed, or Stop in the Task Manager) | Next start: nothing in the gallery, the partial deleted; a persistent toast on the first screen with Open draft | `export_last_stopped`, `export_open_draft` |
| Offline | No difference | none |

## Notification (Android)
Channel "Exports" (low importance: no sound). Shown only when notifications are allowed (Android 13+) or on Android 10–12.
| When | Title | Text | Actions | Tap |
| --- | --- | --- | --- | --- |
| Exporting | Exporting your meme | 42% · about 12 s left | Cancel | Opens Memix on the Exporting screen |
| Done | Your meme is saved | In your gallery · 0:08 · 1080p | none | Opens the Exported screen |
| Failed | Export didn't finish | Open Memix to try again. | none | Opens the error state |
| Stopped by Android | Export stopped | Open Memix to export again. | none | Opens the error state |

- The progress is determinate and updates at most once a second.
- The Cancel action also makes Android 12+ show the notification at once instead of holding it up to 10 s (encoder facts → 5).
- Android 14+ lets users swipe the ongoing notification away; the export keeps running, and nothing in the app depends on the notification.
- There's no notification for "Memix stopped": no process is left to post it. The in-app toast covers it.

## Interactions
- **Export video:** the sheet closes and the Exporting screen pushes in; the export starts at once. `export_start` fires (PM, 20 Oct: here, not on the top bar's Export).
- **Notification permission** (owner, 21 Oct): on Android 13+, at the **first** Export video ever, Memix asks with Android's own dialog, no pre-screen, right after the Exporting screen appears. The export is already running behind it and doesn't wait for the answer. Memix records that it asked and never asks again, whatever the answer (Android stops showing it after two refusals anyway). Focus returns to the Exporting title when the dialog closes. Android 10–12: no dialog.
- **Cancel** (button or notification): stops at once, deletes the partial file, pops back to the editor, toast "Export stopped." No confirmation: the tap is deliberate, and nothing but time is lost. `export_cancel` fires.
- **Back while exporting:** the "Stop exporting?" sheet, because a back gesture can be accidental and a long 4K export is worth one question. Stop works as Cancel. This is the only confirmation outside the unsaved-changes sheet (P1-04).
- **Leaving the app:** the export continues in a foreground service; the notification carries progress when allowed. The editor can't be used during an export (the Exporting screen covers it), so the file always matches what the user saw.
- **Finished:** the file is saved to the gallery ("Memix" album, request 4) before the Exported state shows; `export_complete` fires.
- **Share:** opens the Android share sheet with the video (`video/mp4`, the gallery item's content URI, read permission granted) and the title "Share your meme". `share_target` fires with `target: "more"` when the share sheet opens (owner, 21 Oct; the same value P5-05's More will use). Coming back from the share sheet or the chosen app lands on the Exported screen as it was.
- **Make another:** Home with the Create sheet open (PM, 20 Oct). **Done:** Home. **×** or back: the editor, draft unchanged.
- **After an export** (P5-03): the interstitial, when its rules allow, appears between Exporting and Exported, never before the first export. Nothing to build here; keep the transition a single place where P5-03 can hook in.
- **Next start after Memix was stopped mid-export:** the persistent toast "Your last export didn't finish. Nothing was saved." with **Open draft** (opens that draft in the editor) and ×. Shown once.
- **Haptics:** one Confirm when the export finishes, if the screen is showing (content landed).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Export sheet | 200 ms slide over the scrim | 120 ms fade |
| Exporting screen | 320 ms push | 120 ms fade |
| Progress fill | Moves to each value over 120 ms, at most 10 updates a second (ProgressBar) | jumps |
| Exporting → Exported | 200 ms cross-fade of the content; the frame stays in place | 120 ms fade |
| Segment selection (incl. a stepped-down setting) | 120 ms fill change | same |
| Notices appearing or going | 120 ms fade; the button below moves without animation | same |

## Copy
English source; machine drafts until P5. Budgets in characters.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `editor_export` | Export | 14 | Top-bar button |
| `export_title` | Export | 14 | Sheet hero, shown uppercase in Anton |
| `export_resolution` | Resolution | 20 | |
| `export_frame_rate` | Frame rate | 20 | |
| ~~`export_res_480` … `export_res_4k`~~ | 480p, 720p, 1080p, 2K, 4K | 6 | **Constants, not strings** (review, confirmed): `ExportResolution.label`, one place in `:core:domain`, never translated, like "MP4" and "H.264". Any new resolution name is added there. |
| `export_fps_a11y` | %1$d frames per second | 30 | Spoken name of a frame-rate segment (added in the build) |
| `export_facts_a11y` | MP4, %1$s, AAC. Up to %2$s, about %3$s. | 60 | Spoken facts line (review); %1$s = "H.264"/"HEVC", %2$s = size, %3$s = time. No "·" in speech. Units are written out ("18 megabytes", "20 seconds") if TalkBack reads "MB" or "s" badly on the phone (device check); until then the visible size and time strings may be reused |
| `export_fail_heading` | Not exported | 20 | Failure screen title, uppercase in Anton (review); replaces `export_fail_title` on the screen |
| `export_percent_a11y` | %1$d percent | 16 | Progress bar and milestone announcement (added in the build) |
| `export_pause_a11y` | Pause your video | 24 | The result's name while it plays (added in the build) |
| `export_free_up_space` | Free up space | 18 | The failure state's primary; same words as the sheet's `import_free_up_space` |
| `export_dismiss` | Dismiss | 14 | The next-start toast's × label |
| `export_fps` | %1$d fps | 8 | "30 fps"; "fps" stays as is unless your language's video apps translate it |
| `export_facts_format` | MP4 · %1$s · AAC | 24 | %1$s = "H.264" or "HEVC"; not translated |
| `export_estimate` | Up to %1$s · about %2$s | 32 | %1$s = size ("18 MB"), %2$s = time ("20 s", "2 min"). "Up to": the file is this size or smaller. |
| `export_start` | Export video | 20 | Primary button |
| `toast_export_fps_lowered` | This phone exports %1$s at up to %2$s, so it's set to %2$s. | 80 | %1$s = "4K", %2$s = "30 fps" |
| `toast_export_res_lowered` | This phone exports %1$s at up to %2$s, so it's set to %2$s. | 80 | %1$s = "60 fps", %2$s = "2K". Same English as above; separate keys because word order differs in some languages. |
| `explain_export_no_res` | This phone can't export %1$s. | 50 | %1$s = "4K" |
| `explain_export_shape` | This phone can't export %1$s in this shape. Try %2$s, or change the shape in Canvas. | 100 | %1$s = "4K", %2$s = "2K". "Canvas" must match `canvas_title`. |
| `export_lowered_notice` | Your last setting doesn't work here, so it's set to %1$s · %2$s. | 80 | %1$s = "2K", %2$s = "30 fps" |
| `explain_export_no_shape` | This phone can't export this shape. Change the shape in Canvas, then try again. | 90 | Defensive |
| `export_no_space` | Not enough space. Free up about %1$s to export at %2$s. | 80 | %1$s = "240 MB", %2$s = "1080p" |
| `explain_export_no_space` | Free up space first, or pick a lower resolution. | 60 | |
| `export_missing_media` | A clip's file is missing, so it exports black. | 60 | |
| `explain_export_empty` | Add a video or photo first. | 40 | |
| `export_progress_title` | Exporting | 14 | Uppercase in Anton |
| `export_percent` | %1$d%% | 5 | "42%" |
| `export_time_left` | About %1$s left | 24 | "About 12 s left" |
| `export_leave_ok` | You can leave Memix. Your video keeps exporting and saves to your gallery. | 90 | |
| `export_cancel` | Cancel | 16 | |
| `export_stop_title` | Stop exporting? | 30 | |
| `export_keep` | Keep exporting | 20 | |
| `export_stop` | Stop | 14 | Danger button |
| `export_stopped_toast` | Export stopped. | 30 | |
| `export_done_title` | Exported | 14 | Uppercase in Anton |
| `export_saved` | Saved to your gallery · %1$s · %2$s | 50 | %1$s = length "0:08", %2$s = "1080p" |
| `export_share` | Share | 16 | Button; P5-05 replaces it |
| `export_share_sheet_title` | Share your meme | 40 | Title passed to Android's share sheet |
| `export_play_a11y` | Play your video | 24 | |
| `export_back_to_editor` | Back to editor | 20 | × label and error-state button |
| `export_make_another` | Make another | 18 | |
| `export_done` | Done | 12 | |
| `export_fail_title` | The export didn't finish | 40 | Replaced on the screen by `export_fail_heading` (review); kept for the notification title family only if the engineer wants one source |
| `export_fail_space` | Your phone ran out of space. Free up space and try again. | 80 | |
| `export_fail_encoder` | This phone couldn't encode the video at this setting. Try a lower resolution. | 90 | |
| `export_try_lower` | Export at %1$s | 20 | %1$s = "720p" |
| `export_fail_media` | One of your clips couldn't be read. Replace it in the editor, then export again. | 90 | |
| `export_fail_system` | Android stopped the export. Your draft is safe. Try again. | 80 | |
| `export_fail_other` | Something went wrong. Your draft is safe. Try again. | 70 | |
| `export_try_again` | Try again | 18 | Reuse `editor_preview_retry` |
| `export_last_stopped` | Your last export didn't finish. Nothing was saved. | 70 | Persistent toast at the next start |
| `export_open_draft` | Open draft | 18 | The toast's Quiet button |
| `notif_channel_exports` | Exports | 20 | Android notification channel name |
| `notif_exporting_title` | Exporting your meme | 40 | |
| `notif_exporting_text` | %1$s · about %2$s left | 40 | |
| `notif_done_title` | Your meme is saved | 40 | |
| `notif_done_text` | In your gallery · %1$s · %2$s | 40 | |
| `notif_failed_title` | Export didn't finish | 40 | |
| `notif_failed_text` | Open Memix to try again. | 40 | |
| `notif_stopped_title` | Export stopped | 40 | Android's time limit or a refused start |
| `notif_stopped_text` | Open Memix to export again. | 40 | |

Removed from the draft: `explain_export_4k_30` and `explain_export_no_60` (replaced by the two "so it's set to" toasts), `notif_export_stopped_*` (renamed `notif_stopped_*`; no notification after a process death).

Translator notes: "Export" means making the final video file (CapCut's word in your language is a good guide). Resolutions, "fps", "MP4", "H.264", "HEVC" and "AAC" are never translated. "Memix" never translates. "Shape" means the frame's proportions (tall, square, wide), the same word as the Canvas panel's ratio tiles.

## Accessibility
- **Sheet:** the title is the pane title; Resolution and Frame rate are single-choice groups ("1080p, selected, 3 of 5"). Toasts are announced politely; a stepped-down setting is announced through its toast, and the changed segment reads "selected" when focused. The facts line is one node ("MP4, H.264, AAC. Up to 18 megabytes, about 20 seconds": spoken strings, not the symbols). Notices are read in order before Export video.
- **Exporting:** focus lands on the title (and returns there after the notification dialog); the progress is a range with "42 percent"; milestones (25, 50, 75%) and the finish are announced politely; never every percent. **Review:** the milestone announcement is its own live-region node that is read-only for TalkBack's swipe order (not a second "25 percent" next to the bar's "42 percent"), and "About 12 s left" is a separate, ordinary text node so it can still be read by swiping (it was hidden by the percent row's `clearAndSetSemantics`). The live node isn't focusable.
- **Exported:** focus on the title; the status pill is announced (one merged node: `semantics(mergeDescendants = true)` plus the live region, review: a live region on an unmerged Row has no text of its own); the player is a button named "Play your video" (one name, set once: the `contentDescription` only, not also `onClickLabel`, which made TalkBack say it twice); Share reads "Share". Reading order: title, ×, player, status, Share, Make another, Done.
- **Failure state:** focus lands on the heading when it appears; the message is announced politely (merged node).
- **Touch targets:** every button, segment and the × are 48 dp or more.
- **Contrast:** segment labels `text` on `canvas` 16.9:1, selected `on-primary` on white 18.9:1; facts and notices `text-secondary` on `surface` 7.1:1; status pill `on-track` on `success` 9.3:1; play disc `on-primary` on `primary` 7.9:1; Share `text` on `surface-raised` 12.4:1.
- **200% font:** segments wrap into two rows (SegmentedChoice rule), the facts line wraps, notices wrap, footer buttons stack (Make another above Done, so Done stays last and lowest); nothing cut. On the Exported screen the result shrinks before anything else, down to 40% of the screen height, then the content scrolls; on Exporting and the failure state the frame shrinks to 30% and the screen scrolls (Layout). The top bar's Export never covers the ratio pill (Layout → Export button).
- **Reduce motion:** see Motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `export_start` | Export video is tapped and the export starts (PM, 20 Oct) | `editor: "video"`, `format: "mp4"`, `resolution` ("1080p"), `fps` (30), `length_s` |
| `export_complete` | The file is in the gallery | `editor`, `format`, `time_ms` (encode start to saved), `size_mb` |
| `export_cancel` | Cancel (button or notification), Stop in "Stop exporting?", or Stop in Android's Task Manager (owner, 21 Oct) | `editor`, `progress` (whole percent done, 0–100). A Task Manager stop kills Memix with no callback, so it fires at the next start (`ApplicationExitInfo` `REASON_USER_REQUESTED`) with the last saved percent (request 3). |
| `export_fail` | The export errors | `editor`, `error_code` (below) |
| `share_target` | Share opens the system share sheet | `target: "more"` (owner, 21 Oct) |

**`error_code` values** (a PM clarification, settled here; the PRD lists none):
- `storage_full`: out of space mid-export.
- `encoder`: an encoder or muxer error, including a size the encoder refused (Media3 `ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED`, 4003, and the other 4xxx and 7xxx codes).
- `missing_media`: **only** when an input file that existed when the export started can't be opened or read during it (Media3 IO errors on an input: file not found, no permission, read failure). A clip already missing at the start doesn't fail: it exports black by design, with the sheet's notice, and fires no `export_fail`. In practice this is rare (copies live in Memix's own storage); it's kept because a source vanishing mid-export is the one way a file can go missing, and its fix (Replace) differs from an encoder error's.
- `decoder`: a clip's video or audio couldn't be decoded (Media3 3xxx). Same message as `missing_media` for the user; a separate code because the cause differs (a file Memix accepted but the phone can't decode).
- `system_stopped`: Android stopped the service (Android 15+ `onTimeout` for the media-processing time limit, or a refused foreground-service start).
- `process_death`: Memix was killed for any reason other than a user stop (memory, crash), detected and fired at the next start.
- `unknown`: anything else.

No event for the notification permission answer (not in the PRD). Analytics stay off until consent (P5-01).

## QA compares
1. **Export sheet** at 360 × 640: five resolutions, three frame rates, the watermark row, facts line ("Up to … · about …"), Export video; then the not-enough-space notice (emulator storage filled).
2. **What the phone can do** (emulator without a 4K encoder, or a custom 31:13 canvas): the phone toast on 4K; the shape toast on a custom canvas where 16:9 works; the "so it's set to 30 fps" toast and both segments changed; the lowered notice after reopening a custom project with 4K remembered.
3. **Exporting** at 40%: progress, time left, Cancel; the notification in the shade; on a fresh Android 13+ install, the permission dialog over the Exporting screen with the progress already moving behind it.
4. **Stop exporting?** after a back gesture.
5. **Exported:** the result with the play disc, the status pill, Share, the footer; then the system share sheet open with the video; then the file in the gallery's Memix album, playing.
6. **Hindi at 200% font:** the sheet (segments in two rows), the Exported screen with Share and the footer stacked, the Exporting screen with Cancel reachable, and the failure state with both buttons reachable (review). Also Portuguese or Spanish at 200%: the editor's top bar with Export clear of the ratio pill.
7. **A failure state** (review): the heading "NOT EXPORTED", the message and the two buttons, with one blue primary (the out-of-space one is easiest to reach by filling the emulator's storage).

**Measured (the Done when):** 60 s 1080p30 `preview-check`-style draft, release-type build on the owner's phone: Export video to `export_complete` ≤ 60 s; ffprobe shows 1080 × 1920 (or 1920 × 1080 with rotation ±90; the canvas's shape at 1080 short side), 30 fps, H.264, AAC, the expected length within one frame; the file plays in the gallery. Estimate vs actual: the file is **never larger than the shown size**; on live footage it's within 25% under it (log `ExportResult.averageVideoBitrate`, the file size and the estimate for every export; note stills drafts separately). Time within 30% after the first export. LeakCanary after one export and one cancel: no leak.

**Also checked here** (PM, 20 Oct): P1-10's 720p half of the preview = export comparison and P1-11's blur softness at 720p and 1080p, now through the real sheet.

**Edge checks:** deny the notification permission, leave the app during a 4K export: nothing in the shade, the Task Manager shows Memix, and on return the screen shows the current state. Stop Memix from the Task Manager mid-export: on the next start, the toast, no file in the gallery, `export_cancel` with the last percent. Swipe the ongoing notification (Android 14+): the export continues.

## Requests to the principal mobile engineer
1. **Capabilities:** `VideoEngine.capabilities(canvas)` (encoder facts → 1): for each resolution × frame rate, the codec (H.264 hardware, else HEVC, else none) at the aligned output size with the long side as width, via `EncoderUtil.isSizeSupported` and `VideoCapabilities.areSizeAndRateSupported`, plus the bitrate clamp from `getSupportedBitrateRange`. Also whether the same resolution works at the standard 16:9 size, to tell "phone" from "shape". Scan once per process off the main thread, warm it when the editor opens, cache it; the sheet reads it per option. Export with `setEnableFormatFallback(false)`; plan sizes already aligned to the encoder's width and height alignment.
2. **Bitrates:** VBR (`VideoEncoderSettings.setBitrate`, `BITRATE_MODE_VBR`), the table in Settings rules → Estimate, clamped to the encoder's range; the estimate uses the clamped value × 1.05. Log the estimate, `averageVideoBitrate` and the file size per export.
3. **Foreground service** (encoder facts → 3): `mediaProcessing` on Android 15+ through `Service.startForeground` directly (ServiceCompat drops it), `dataSync` on 10–14; start it from the Export video tap while the screen is visible. The notification table, Cancel from the notification, `onTimeout` → cancel, delete the pending item, the "Stopped by Android" state and notification, `stopSelf()`. Save the whole percent (at most once a second, with the notification update) so a Task Manager stop can report it at the next start (`ApplicationExitInfo` `REASON_USER_REQUESTED` → `export_cancel`; other reasons → `export_fail` `process_death`), then show the next-start toast and delete the pending item.
4. **Gallery:** write straight into a MediaStore pending item in `Movies/Memix` (`IS_PENDING = 1`) through a custom `Muxer.Factory` over the item's file descriptor, copying `InAppMp4Muxer`'s orientation and metadata handling (encoder facts → 4); file name `Memix_<yyyyMMdd_HHmmss>.mp4`; size from `pfd.statSize`; delete the item on failure or cancel. Hand-check the seek on Android 10 and 11+. If it fails, fall back to a temp file plus copy and switch the free-space rule to 2 × estimate + 100 MB; say which in the handoff.
5. **Remembered settings** in multiplatform-settings, app-wide; "permission asked" flag the same way.
6. **Learned speed:** after each export, store seconds per (megapixels × seconds of video) per resolution group, for the time estimate.
7. **Share:** `Intent.createChooser(ACTION_SEND)` with `video/mp4`, the gallery item's content URI, `FLAG_GRANT_READ_URI_PERMISSION` and `export_share_sheet_title`. Behind a `:core:domain` interface (a share use case), implemented in `:platform:services`, so P5-05 extends it.
8. **Notification permission:** `POST_NOTIFICATIONS` in the manifest; ask once on Android 13+ at the first Export video, after the service has started (the export never waits).
9. **Components and icons:** SegmentedChoice in `:core:designsystem` and the catalog. Icons drawn to the grammar (DESIGN_SYSTEM → Iconography): `share` and `check`.
10. **Analytics:** as in the table, with the `error_code` mapping from Media3's `ExportException` codes.

## Proposals and questions for the PM
All answered; kept for the record.
1. ~~Quality pill dropped~~ **Answered** (PM, 20 Oct): dropped.
2. ~~Share in P1?~~ **Answered** (owner, 21 Oct, A): one Share button now, system share sheet, `share_target` `target: "more"`; P5-05 replaces it.
3. ~~Notification permission~~ **Answered** (owner, 21 Oct, A): asked once at the first Export video on Android 13+; the export starts whatever the answer.
4. ~~Cancel in analytics~~ **Answered** (owner, 21 Oct, A): new `export_cancel {editor, progress}`.
5. ~~Make another~~ **Answered** (PM, 20 Oct): Home with the Create sheet open.
6. ~~`export_start` timing~~ **Answered** (PM, 20 Oct): on Export video in the sheet.
7. ~~PM item (c), the watermark row~~ **Answered:** `P1-13-watermark.md` → Export sheet row.
8. ~~PM item (d), when `missing_media` fires~~ **Answered:** Analytics → `error_code` (only a file that vanishes or becomes unreadable mid-export).
9. ~~Encoder facts U1 and P1~~ **Answered:** the shape toast (What the phone can do) and "Up to X MB" (Estimate).

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Recommended SDR upload bitrates: 480p 2.5 / 4 Mbps, 720p 5 / 7.5, 1080p 8 / 12, 1440p 16 / 24, 2160p 35–45 / 53–68 (standard / high frame rate) | Guidance | [YouTube recommended upload encoding settings](https://support.google.com/youtube/answer/1722171) |
| Transformer encodes portrait turned (long side as width) and, with format fallback on, silently exports smaller; with it off, an unsupported size fails with 4003 | Evidence (Media3 1.11.1 source) | `docs/research/P1-12-encoder-facts.md` → 1 |
| A custom 2.39:1 canvas at 4K (5162 × 2160) needs codec Level 6, beyond nearly every phone; 9:16 4K needs Level 5.1 | Guidance (ITU level tables, unverified this session) | encoder facts → 1 |
| Hardware VBR is defined only as an average; it undershoots on static content | Guidance (Android docs) + Assumption (to measure) | [MediaFormat KEY_BIT_RATE](https://developer.android.com/reference/android/media/MediaFormat); encoder facts → 2 |
| Undo or a deliberate tap beats a confirmation for reversible actions; confirm only where loss can't be undone | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| Apps can add their own media to MediaStore collections on Android 10+ without a storage permission; pending items with IS_PENDING | Guidance | [Android: access media files from shared storage](https://developer.android.com/training/data-storage/shared/media) |
| Android 13+ requires POST_NOTIFICATIONS; without it, foreground-service notices show only in the Task Manager | Guidance | [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) |
| Ask for a permission in context, when the user starts the feature that needs it | Guidance | [Android: request runtime permissions](https://developer.android.com/training/permissions/requesting) |
| `mediaProcessing` foreground service type and its 6 h limit on Android 15+; Task Manager Stop kills the app with no callback | Guidance | [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types), [FGS timeout](https://developer.android.com/develop/background-work/services/fgs/timeout), [user stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping) |
| Mobile editors put Export at the top right and the resolution beside it | Assumption (product knowledge of CapCut, VN, InShot) | — |
