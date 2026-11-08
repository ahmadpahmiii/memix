# Photo export · P2-08

> **Draft for build, 5 Nov 2026.** Ready check: `docs/pm/acceptance-log.md` (5 Nov, P2-08). Delegated decisions applied: GIF moved to P4-12 (2A); the watermark is on PNG, JPG and clipboard copies, not on stickers (1A).

Builds on `P1-12-export.md` (the sheet, the Exported screen, the failure copy) and `P1-13-watermark.md` (the mark, drawn last). Every photo layer kind draws through `drawPhotoScene`, so the file equals the editor and the render hook (P2-01).

## Job
Get the finished picture out in under 10 seconds from the tap: into the gallery, into a chat, or onto the clipboard. The user sees the size before saving and nothing is surprising (no ads, no upload).

**Done when** (TICKETS): each format opens correctly in the gallery and in chat apps; LeakCanary shows no leaks.

**The one blue action:** *Save to gallery* in the sheet; *Done* on the Exported screen (as P1-12).

## Entry and exit
- **Entry:** the Export button in the photo editor's top bar. **Exit:** Save → the Exported screen → Done (Home) or × (back to the editor). Copy → back to the editor with a confirmation. Close, scrim or back → the editor.
- **Nothing is lost:** drafts autosave; the sheet changes no layer.

## Layout

### Export button (photo editor top bar)
- Same Button as the video editor (P1-12 → Export button): primary, label "Export" (`editor_export`, reused), `touch-target` tall, `radius-md`, `space-2` from the edge. P2-01 left the end slot for it.
- **Bar rule at large font (P2-01's request to re-check; this settles it).** The bar is one custom `Layout`. Row 1 always holds Close at the start and Export at the end; neither moves or shrinks. The ratio pill, Undo and Redo sit between them when they fit with `space-2` between neighbours, the pill clamped left of Undo as P1-12 does. **When they don't fit, all three move together to a second row under the bar** (48 dp tall, pill at the start, Undo and Redo at the end), and the stage gives up 48 dp. All-or-nothing, so the bar never reshuffles piece by piece. Estimate: at 100% on 360 dp the three fit (Close 48 + Export about 88 + Undo and Redo 96 + gaps leaves about 100 dp for the pill, enough for "16:9" and tight for "31:13"); at 200% in es and pt ("Exportar" about 170 dp) and in Hindi they don't, so they take the second row. The engineer measures with the real fonts; QA checks both (QA compares 7).
- The button explains instead of opening a sheet in two cases, as every blocked control does (toast on tap, `explain_*`):
  - No visible layer yet → `explain_photo_export_empty`.
  - A cut-out is still being made (P2-07's busy state) → `explain_photo_export_busy`, so the file never silently lacks it.
- Tapping Export while a text box is being edited commits the text first, then opens the sheet. **In Draw mode (review):** Export ends Draw mode (as Done does, nothing is lost: every stroke is already committed) and opens the sheet; it is never silently ignored. Any other open panel closes first the same way. The Crop and cut-out screens have no Export (their own top bar).

### Export sheet
Standard Sheet (grabber, close), `surface`. From the top:
1. **Title** "EXPORT" in `display` (`export_title`, reused).
2. **Format:** heading "Format" in `label` `text-secondary`, then a SegmentedChoice with **JPG · PNG**. The names are constants (never translated).
3. **Quality slot** (fixed height, so the sheet doesn't jump when the format changes):
   - **JPG:** the Slider (`design/system/components/Slider/README.md`): label "Quality", readout "90", range 50 to 100, step 5, default 90, double-tap resets. The edit commits on release; the facts line updates live while dragging.
   - **PNG:** the same slot shows one line in `label` `text-secondary`: "Sharpest text and edges. Bigger file." No slider, nothing disabled.
4. **Size:** heading "Size" (`label` `text-secondary`), SegmentedChoice **1080 px · 2160 px**: the canvas's shorter side. The long side follows the canvas shape.
5. **Watermark row** (P1-13 → Export sheet row) with the photo line "Small text in the top-left corner of your picture". Information only until P5-02.
6. **Facts line** in `timecode` `text-secondary`: left "JPG · 1080 × 1350", right "About 1.2 MB". It wraps to two lines when it doesn't fit.
7. **Notices**, when they apply (`body` `text-secondary`, above the buttons, P1-12's rules for spacing: one Column, no `spacedBy`, each notice carries its own top padding):
   - Not enough space (below), with a Quiet **Free up space** button.
   - A picture's file is missing.
   - The last failure (States).
8. **Buttons**, one row: **Copy** (secondary, one third) and **Save to gallery** (primary, two thirds), `space-3` apart. At 200% font they stack, Copy above Save, so the primary is lowest and nearest the thumb.

- **Defaults:** JPG, quality 90, 1080 px the first time; afterwards the last choice, kept app-wide and separate from video's (never in a draft). JPG is the default because chats recompress anyway and its files are a fraction of PNG's (Assumption; the facts line lets the user see the difference). PNG is one tap away.
- **No settings the picture can't do:** both sizes are always offered. A size that fails on a weak phone fails with a calm message (States), not a hidden option.
- **Height (review, corrected):** at 100% font on 360 × 640 the sheet fits without scrolling **with no notice** (the renders show it using about 88% of the window already; my earlier estimate counted the watermark row and one notice and was about 75 dp short). **With a notice, or at 200% font, the middle scrolls and the title and the Copy and Save buttons stay pinned**; when a notice appears the middle scrolls to it. Don't shrink the controls to fit.
- While it closes, the sheet keeps its last content (P1-12 review).

### Saving (inside the sheet)
No Exporting screen: a photo takes a second or two (target 2 s at 1080, 5 s at 2160; Assumption, measured at QA).
- The tapped button's label becomes "Saving…" (or "Copying…") and both buttons and the controls go inert. **Look (review): the two buttons use the Button's new `busy` look** (`surface-raised` fill, label `text-secondary`, 5.7:1; the working button reads "Saving…"); Format, Size and Quality are not dimmed (no token; the wait is a few seconds), but they are marked disabled for TalkBack and ignore taps. Nothing moves: MOTION 2 allows no looping bar, and a determinate bar has no true progress to show.
- **Close, scrim and back cancel**, with no confirmation (nothing is lost; the draft is untouched). The partial file is deleted and a toast says "Export stopped." If the user leaves Memix, the work continues in the app's process and the result is waiting on return; if Android kills Memix first, nothing is saved and nothing is left behind.

### Exported screen (photo)
P1-12's Exported screen with these differences:
- **The result** is the saved file, the canvas shape, `radius-lg`, decoded at screen size. It has **no play disc and no press state**: the picture is not a control. The mark is visible because it is in the file.
- **Status pill** (`success`, `check` icon, `on-track` text): "Saved to your gallery · JPG · 1080 × 1350".
- **Share:** secondary, full width, `space-4` below the pill: the system share sheet with the picture attached (`image/jpeg` or `image/png`). P5-05 replaces it with the share row.
- **Footer:** Make another (secondary) and Done (primary), stacked at 200%. Title "EXPORTED", × "Back to editor".
- At large font the result keeps at least 40% of the screen height, then the screen scrolls (P1-12).

### Copy (clipboard)
- Copy renders exactly what Save would (the chosen format, quality and size, with the mark), puts it on the clipboard as an image, and closes the sheet. The user is back in the editor, ready to switch to a chat and paste.
- **Android 12 and lower:** a toast "Copied. Paste it into a chat." **Android 13 and up:** Android shows its own clipboard preview, so Memix shows no toast (a second one would be a duplicate, per Android's guidance), and TalkBack still hears the same words announced politely.
- Copy writes nothing to the gallery.

## Rules

### Estimate
- "About 1.2 MB" = megapixels of the output × the table in `design/tokens.json` → `photo.export.estimateMbPerMegapixel` (JPG by quality, PNG one rate), until this phone has exported once; afterwards from the measured bytes per megapixel of the last export in the same format and quality band. Rounded: under 10 MB to 0.1 MB, then whole MB.
- **"About", not "Up to":** unlike a video encoder, a JPG or PNG of a meme can land well under or a little over a formula, and no honest upper bound exists. The engineer logs estimate and actual for every export so the table can be tuned.

### Free space
- **Needed:** twice the estimate plus 100 MB (P1-02's headroom): the file is written through a temporary copy for the clipboard and for the gallery item's fallback.
- **When it isn't there:** the notice "Not enough space. Free up about 240 MB to save this picture." with Free up space (P1-12's intent and rule: ask the storage screen for the total, not the missing amount). Save stays enabled and explains on tap ("Free up space first, or pick a smaller size."). Copy needs only the cache for one file (the estimate plus the headroom) and is checked on its own.
- The check runs when the sheet opens, when a setting changes and when the app returns to the foreground.

### What the file is
- **Pixels:** the canvas scaled so the shorter side is exactly 1080 or 2160 px (by the one scene function, P2-01 request 5), the longer side by the canvas ratio, rounded. Image layers decode at up to 4096 px per side, software-decoded, as the render hook does.
- **Mark:** P1-13's rules, drawn last at 4% of the shorter side; on Save and Copy; never on sticker files (P2-07) or WhatsApp packs (P2-09).
- **JPG:** sRGB, no EXIF at all (the source photo's location and camera data never travel). The canvas is opaque, so nothing is lost; any transparent pixel is flattened onto the canvas background.
- **PNG:** sRGB, lossless, no metadata.
- **Album:** `Pictures/Memix` through MediaStore, no permission; name `Memix_<yyyyMMdd_HHmmss>.jpg` or `.png`. In the gallery the folder shows as "Memix".
- **Sticker layers and formats:** whatever draws in the editor draws in the file; there is no export-only drawing except the mark.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Sheet, default | Format, quality slot, size, watermark row, facts line, Copy and Save to gallery | `photo_export_*`, `export_title`, `export_watermark_title`, `photo_export_watermark_body` |
| PNG selected | The quality slot shows the one-line hint; the facts line changes | `photo_export_png_hint` |
| Not enough space | Notice with Free up space; Save explains on tap | `photo_export_no_space`, `export_free_up_space`, `explain_photo_export_no_space` |
| A picture's file is missing | Notice; the picture is left out of the file | `photo_export_missing_media` |
| Empty canvas | Export explains on tap; no sheet | `explain_photo_export_empty` |
| Cut-out still being made | Export explains on tap; no sheet | `explain_photo_export_busy` |
| Saving or copying | The tapped label reads "Saving…" or "Copying…"; everything inert; close cancels | `photo_export_saving`, `photo_export_copying` |
| Cancelled | Back in the editor; toast | `export_stopped_toast` |
| Saved | Exported screen | `export_done_title`, `photo_export_saved`, `export_share`, `export_make_another`, `export_done`, `export_back_to_editor` |
| Copied | Sheet closed; toast on Android 12 and lower | `photo_export_copied` |
| Failed: storage full | Notice in the sheet, nothing saved, partial deleted; Free up space | `export_fail_space`, `export_free_up_space` |
| Failed: too big for this phone (memory or encoder) | Notice in the sheet; the size segment stays as chosen; nothing else changes | `photo_export_fail_size` |
| Failed: a picture couldn't be read (`missing_media`) | Notice in the sheet | `photo_export_fail_media` |
| Failed: unknown | Notice in the sheet | `export_fail_other` |
| Offline | No difference | none |

A failure keeps the user in the sheet with the settings intact; the primary stays "Save to gallery", so pressing it again is the retry. The notice is a polite live region. No separate failure screen and no dead end.

## Interactions
- **Export:** opens the sheet (or explains). **Format, Size:** select; the facts line updates at once. **Quality:** drag, tap the rail, or step with TalkBack; one `SegmentTick` haptic each step.
- **Save to gallery:** starts the save; on success the Exported screen pushes in (320 ms, as P1-12). **Copy:** starts the copy.
- **Share:** the system share sheet with the saved file. **Make another:** Home with the Create sheet open. **Done:** Home. **×:** back to the editor.
- **Back** on the Exported screen: back to the editor (the file is already saved).

## Motion
Sheet slide and the push to Exported as P1-12. The label change to "Saving…" is instant. Toasts fade 120 ms. Reduce motion: the push becomes a 120 ms fade (MOTION 2).

## Copy
English source; machine drafts until P5. Budgets in characters.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `editor_export` | Export | 14 | Reused from video; both editors use one word |
| `export_title` | Export | 14 | Reused. Sheet hero, uppercase in Anton |
| `photo_export_format` | Format | 20 | Heading. "JPG" and "PNG" are constants, never translated |
| `photo_export_quality` | Quality | 20 | Slider label; the readout is a plain number |
| `photo_export_png_hint` | Sharpest text and edges. Bigger file. | 50 | Shown in the quality slot when PNG is selected |
| `photo_export_size` | Size | 20 | Heading. "1080 px" and "2160 px" are constants |
| `photo_export_size_a11y` | %1$d pixels on the short side | 40 | Spoken name of a size segment |
| `photo_export_watermark_body` | Small text in the top-left corner of your picture | 60 | Photo version of `export_watermark_body`; "Memix" never translates |
| `photo_export_facts` | %1$s · %2$s | 24 | %1$s = "JPG" or "PNG", %2$s = "1080 × 1350". Not translated |
| `photo_export_estimate` | About %1$s | 20 | %1$s = "1.2 MB" |
| `photo_export_facts_a11y` | %1$s, %2$d by %3$d pixels. About %4$s. | 70 | Spoken facts line. %4$s = "1.2 megabytes" if TalkBack reads "MB" badly |
| `photo_export_save` | Save to gallery | 20 | Primary. Same word "gallery" as `export_saved` |
| `photo_export_copy` | Copy | 12 | Secondary. Copies the picture to paste in a chat |
| `photo_export_saving` | Saving… | 14 | Label while working |
| `photo_export_copying` | Copying… | 14 | Label while working |
| `photo_export_copied` | Copied. Paste it into a chat. | 40 | Toast on Android 12 and lower; spoken on all versions |
| `photo_export_saved` | Saved to your gallery · %1$s · %2$s | 50 | %1$s = "JPG", %2$s = "1080 × 1350" |
| `photo_export_result_a11y` | Your exported picture | 30 | Result image description (not a control) |
| `photo_export_no_space` | Not enough space. Free up about %1$s to save this picture. | 80 | %1$s = "240 MB" |
| `explain_photo_export_no_space` | Free up space first, or pick a smaller size. | 60 | Toast on tap |
| `photo_export_missing_media` | A picture's file is missing, so it's left out. | 60 | |
| `photo_export_fail_size` | This phone couldn't make the picture at this size. Try a smaller size. | 90 | |
| `photo_export_fail_media` | One of your pictures couldn't be read. Replace it in the editor, then try again. | 90 | |
| `explain_photo_export_empty` | Add a photo, text or sticker first. | 50 | |
| `explain_photo_export_busy` | Wait a moment. Your cut-out is still being made. | 60 | |
| `export_fail_space`, `export_fail_other`, `export_free_up_space`, `export_stopped_toast`, `export_done_title`, `export_share`, `export_share_sheet_title`, `export_make_another`, `export_done`, `export_back_to_editor`, `export_watermark_title` | (as P1-12 and P1-13) | | Reused as they are |

Translator notes: "Export" is the word for making the final file (CapCut's word in your language is a good guide). "Gallery" is the app that shows photos on the phone. "Paste" is what the user does in a chat after copying. "JPG", "PNG", "px" and "Memix" never translate.

## Accessibility
- **Sheet:** the title is the pane title. Format and Size are single-choice groups ("JPG, selected, 1 of 2"; "1080 pixels on the short side, selected, 1 of 2"). The Quality slider reads "Quality, 90", steps of 5 with swipe up and down, and its readout is the state description; it is hidden from the order when PNG is selected, where the hint line is read instead. The facts line is one node (spoken string). Notices are read in order before the buttons. Copy and Save are plain buttons named by their labels.
- **Saving:** focus stays on the tapped button; "Saving" is announced politely, and the finish is announced by the focus moving to the Exported title or, for Copy, by "Copied. Paste it into a chat." read politely on every Android version.
- **Exported:** focus on the title; the status pill is one merged live node; the picture has a description and is not focusable as a control; order: title, ×, picture, status, Share, Make another, Done.
- **Touch targets:** every control 48 dp or more; the slider row is `touch-target` tall.
- **Contrast** (reused): `text` on `surface` 16.9:1; `text-secondary` on `surface` 7.1:1; `on-primary` on `primary` 7.9:1; selected segment `on-primary` on white 18.9:1; status pill 9.3:1. The slider's rail and thumb meet 3:1 against `surface` (Slider README).
- **200% font:** segments wrap into two rows, the buttons stack, notices and the facts line wrap, the middle scrolls. The top bar's second row (above) keeps the ratio pill and Undo and Redo reachable.
- **Reduce motion:** see Motion.

## Analytics
Photo-shaped; the video parameters (`fps`, `length_s`) don't apply.
| Event | When | Parameters |
| --- | --- | --- |
| `export_start` | Save to gallery or Copy is tapped and work begins | `editor: "photo"`, `format` ("jpg" or "png"), `resolution` ("1080" or "2160": the shorter side in px, written without "p"), `action` ("save" or "copy"), `quality` (JPG only) |
| `export_complete` | The file is in the gallery, or on the clipboard | `editor`, `format`, `time_ms`, `size_mb` (the file's size; for Copy the rendered file's size) |
| `export_cancel` | Close, scrim or back while saving | `editor`, `progress: 0` (a photo has no percent) |
| `export_fail` | The work errors | `editor`, `error_code`: `storage_full`, `encoder` (includes out of memory), `missing_media`, `unknown` |
| `share_target` | Share opens the system share sheet | `target: "more"` |

`action` and `quality` are additions to the PRD's list: without `action`, a Copy can't be told from a Save in the numbers (Proposal 1). `watermark_removed` arrives with P5-02. The interstitial hook for P5-03 stays empty here: Save and Copy each count as one export, photo and video together, and no ad ever shows in the sheet or while saving.

## QA compares
1. **The sheet** at 360 × 640: JPG with its slider, then PNG with the hint (same height), the size segments, the watermark row, the facts line, Copy and Save to gallery; then the not-enough-space notice.
2. **Saving** (a 2160 export on the slowest phone available): the "Saving…" label; close cancels, no file in the gallery, toast.
3. **Exported:** the picture with the mark, the pill, Share, the footer; the system share sheet with the picture; the file in the gallery's Memix album.
4. **Copy:** paste into WhatsApp and Telegram; the toast on Android 12 or lower, the system preview on 13 and up.
5. **Hindi at 200% font:** the sheet (segments in two rows, buttons stacked) and the Exported screen.
6. **Failure notice:** a failure in the sheet (storage filled).
7. **The top bar** at 100% with "31:13" and at 200% in es and pt and Hindi: Export clear of the ratio pill; the second row when needed.

**Measured (the Done when):**
- **Each format opens:** a JPG and a PNG from a scene with a format, an image, a sticker, a drawing, a censor and text, each opened in the gallery, WhatsApp and Telegram; a clipboard copy pasted into a chat.
- **Size:** `identify` (or ffprobe) shows the shorter side exactly 1080 or 2160 px; the long side by the canvas ratio. JPG quality reads back as chosen. No EXIF.
- **Equality** (this is where the deferred photo checks of P2-01, P2-02, P2-03, P2-04, P2-05, P2-07, P2-11 and P2-12 close): the PNG equals the render hook's output at the same size, apart from the mark; the mark's ink box starts 3% of the shorter side from the left and top (32 px at 1080, 65 px at 2160, ± 2 px).
- **Time:** Save to saved ≤ 2 s at 1080 and ≤ 5 s at 2160 on the owner's phone (Assumption). Log the estimate and the actual size for each export.
- **Memory:** peak memory of a 2160 export of a 9:16 canvas (about 33 MB as a bitmap) reported; LeakCanary after five cycles of Save, Copy and cancel: no leaks.
- The reference frames go in `docs/qa/references/P2-08-photo-export/` (PNG, 1080, plain and with a format) for iOS parity in P7-09.

## Requests to the principal mobile engineer
1. **`PhotoRenderer`** in `:core:domain` (PM item 1); the Android implementation draws `drawPhotoScene` offscreen at the output size and encodes PNG or JPG in `:engine:photo`; the watermark is the last draw, with P1-13's constants and flag. iOS stub marked for P7-09.
2. **Constants** from `design/tokens.json` → `photo.export` in one commonMain place next to the other photo constants. The remembered choices (format, quality, size) in multiplatform-settings, separate from video's.
3. **Save:** MediaStore pending item in `Pictures/Memix`, name pattern above; no EXIF; sRGB; delete on failure or cancel.
4. **Copy:** a content URI (FileProvider from the cache) with a read grant in `ClipData`, an image MIME type; delete the cache file on the next start. Toast only on Android 12 and lower; the spoken announcement on all.
5. **Work scope:** off the main thread, in a scope that outlives the sheet and survives rotation and leaving; closing the sheet while working cancels it. Not a foreground service (short work).
6. **Top bar:** the two-row rule above in P2-01's custom Layout.
7. **Estimate and free space:** the table, learning from actuals, the log line, the free-space rule above (Free up space intent as P1-12).
8. **Analytics** and the empty interstitial hook as above. **Strings** in five languages, tokens only, the Exported screen reusing P1-12's components.
9. **Debug:** the export hook from P1-12 also drives a photo draft (format, quality, size, action, watermark-free) so QA can run the measurements.

## Proposals and questions for the PM
1. **Add `action` and `quality` to photo `export_start`** (small; PRD lists only editor, format, resolution). Without `action` the numbers can't separate Copy from Save. *Recommendation applied: add both; the PM edits the PRD list.*
2. **Does a Copy count toward the interstitial's "1 per 3 exports"?** *Recommendation applied: yes, every Save and Copy counts, because both give the user a finished meme; P5-03 may decide otherwise and the hook takes either.*
3. **GIF** is gone from P2-08 (PM, 2A); when animated stickers land in P4-12 the Format group gets a third segment, GIF, with a frame-count estimate. No work now.

**Delegated owner decisions (applied, for the owner's review after Phase 2):**
- **Default format JPG 90 at 1080 px** (not PNG). Chats recompress, JPG is a fraction of the size, PNG is one tap away. Reversible: one constant.
- **Copy and Save are both in the sheet**, Save primary, Copy secondary. One blue action; the clipboard path is still two taps from the editor (Export, Copy).

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Android 13 and up shows its own clipboard confirmation; in-app confirmation is for Android 12L and lower, otherwise it duplicates | Guidance | [Android: Copy and paste](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste) |
| Photo export must be a short, in-sheet action (no progress screen) | Assumption (checked by the time target at QA) | none |
| JPG at 85 to 90 suits memes shared in chats; PNG for lossless text | Assumption (the facts line shows both sizes) | none |
| Estimate table (MB per megapixel) | Assumption, replaced by measured values | `design/tokens.json` → `photo.export` |
