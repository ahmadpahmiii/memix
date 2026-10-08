# Gallery picker and media import · P1-02

## Job
Get the user's videos and photos into a new video project fast, with no permission prompt. Every video job in the PRD starts here (first one: "React to a clip with a meme sound"). It's step one of the fast path, a basic meme in under a minute.

**Done when** (TICKETS): picked media appear in the project without a storage permission prompt.

**The one blue action:** the Video meme entry that opens the picker (the Home card and the Create sheet row, both already blue). Inside Memix's import sheet, blue is the progress fill while copying, and the one button of each result state. Nothing in this flow is selected (white).

**Tap count:** Home → Video meme (1) → tap a clip (1) → the picker's Add (1) → editor. That's 3 taps, every time. CapCut uses its own gallery, so its first run adds an access prompt (Assumption: from product knowledge; its help center tells users to check photo permissions when imports fail, see Evidence).

## Entry and exit

```
Home "Video meme" card ──────┐
Create sheet "Video meme" row ─┴─> system photo picker ── nothing picked / back ──> where you were (Create sheet still open)
                                         │ 1–35 picked
                                         v
                              Memix copies each file ── all done < 300 ms ──> video editor
                                         │ still copying at 300 ms
                                         v
                         import sheet (progress, Cancel) ──> video editor, or a result state
```

- **Kept:** the project, and Memix's own copy of every file that went in. Nothing is kept from a pick that was backed out of, cancelled, or where nothing went in. A project only exists once at least one item has landed, so Drafts never fills with empty projects.
- **Closing the editor** (close button or system back) → the screen the flow started from (Home or a tab), not the Create sheet. The draft stays.
- **Photo meme** (Home card and Create row) stays on its placeholder until P2. P2-01 specs the photo picker.

### Before the picker
- A tap opens the picker straight away. Memix shows no screen, explainer or permission rationale before it, the first time or any time: the system photo picker needs no permission (Guidance).
- The screen behind it stays as it was: the Create sheet stays open, or Home stays. The picker opens at half height over it (Guidance).
- Taps on the entry are ignored while the picker is launching, so a double tap can't open two pickers.

### During the picker (system UI: Memix doesn't style it)
What Memix asks for. Engineer: check the names against the current androidx.activity docs when the ticket starts.

| Setting | Value | Why |
| --- | --- | --- |
| Media type | Images and videos | PRD: "videos and photos, multi-select" |
| Most items | 35, or the system's limit (`MediaStore.getPickImagesMaxLimit()`) if that's lower | See "Order, limit and what each item becomes" |
| Ordered selection | On | Where it's supported, results come back in the order the user tapped them |
| Accent color | Not set | The picker follows the phone's light or dark theme, which we don't control, so a sky-blue accent can't be contrast-checked on both. The accent API also only works from Android 15 (API 35). A system picker that looks like the system tells people it's the private one that needs no permission. |
| Default tab | Not set (Photos) | Recent items first is what a quick meme needs |
| HDR transcoding | The principal engineer decides | The picker's transcoding needs Android 13+ and stops at 1-minute videos; the engine may tone-map instead |

**Don't promise what the picker doesn't guarantee.** No Memix copy, help text or test script may rely on: numbered selection badges, the wording of the picker's limit message, tap order on devices without ordered selection, the label on its Add button, or cloud albums. All of these vary by device and picker version.

**Phones without the system picker** (no Google system updates and no Play services backport) get the system file chooser instead (`ACTION_OPEN_DOCUMENT`). It also needs no permission. It ignores the item limit, so Memix applies the limit itself.

### After the picker
- **Nothing picked** (back, swipe down, or no items chosen): the picker returns an empty list and Memix does nothing. The Create sheet is still open, or Home is still there. No message, no project, no event.
- **One or more picked:**
  1. The Create sheet slides away but its scrim stays, so the scrim doesn't blink off and back on if the import sheet follows. From the Home card there's no scrim yet.
  2. Memix copies the files in the order they were picked, then checks each copy (see States: "Won't play").
  3. All done within 300 ms → the editor opens. No import UI shows. This should be the usual case for short phone clips (Assumption: QA measures it).
  4. Still copying at 300 ms → the import sheet slides up. Once it's shown, it stays at least 500 ms so it never flashes, the same idea as Android's `ContentLoadingProgressBar` (Guidance).

## Order, limit and what each item becomes
- **Order.** Items go on the main video track back to back from 0 µs, in the order the picker returns them. With ordered selection, that's the order the user tapped (TikTok also builds slideshows in tap order, Evidence). Where the picker doesn't support ordered selection, Memix keeps the order it gets back and doesn't re-sort by date. Users reorder clips in P1-06.
- **Videos** go in at full length (trim in 0, trim out = duration) and keep their own sound.
- **Photos** become still clips of **3.0 s (3,000,000 µs)**. The PRD sets no default, so this is a proposal for the PM (below). Until it's decided, build with 3.0 s as a single constant. Animated GIF and WebP files go in as a still of their first frame in P1 (moving stickers are P4-12).
- **Limit: 35 per pick, no limit per project.**
  - Why 35: it's the most TikTok takes in one photo-mode post; Instagram carousels take 20 (Evidence). That covers every "photo dump" format, and it stops a "select all" in the file chooser from copying hundreds of files.
  - System picker: it stops at 35 itself and shows its own message.
  - File-chooser fallback: Memix keeps the first 35 in the order returned, and the import sheet says so (see States: "Over the limit").
- **New project:**
  - Video type, with P1-01's default canvas (9:16, 1080 × 1920) and clips set to fit. P1-11 may change that rule.
  - Its name stays **empty**, not a stored English default. Screens show a translated stand-in (P1-14 spec), so the name follows the user's language.

## Layout
- **Create sheet**, board `design/screens/Create.dc.html`, with the P0-05 differences plus one more: the Video meme row's body becomes **"Pick videos and photos"**. Photos are allowed too, and "clips" reads as video only.
- **Home**, board `design/screens/Main.dc.html`: the Video meme card now opens the picker instead of the editor placeholder.
- **Import sheet:** no board covers it. Components: Sheet (`design/system/components/Sheet/README.md`, new blocking variant) and ProgressBar (`design/system/components/ProgressBar/README.md`, new).

### Import sheet, copying
`surface`, `radius-lg` top corners, `shadow-sheet`, over `scrim`. Padding `space-4`, bottom `space-6` plus the system navigation inset. While copying it uses the **blocking variant**: no grabber, no close button. Top to bottom:
1. Title in `title`, `text`: "Adding your media".
2. `space-4`, then one row: count in `label` `text-secondary` on the left ("3 of 5"), percent in `timecode` `text-secondary` on the right ("40%").
3. `space-2`, then the ProgressBar at full width: a `rail-height` rail in `hairline`, fill in `primary`.
4. `space-2`, then one optional line in `label`, `text-muted`:
   - Time left, only when the estimate is over 10 s.
   - Or, while a file of unknown size copies, the amount copied so far.
5. The over-the-limit note, only in that case, in `label` `text-secondary`.
6. `space-6`, then **Cancel**: secondary Button, full width, at least `touch-target` tall. It's in thumb reach at the bottom edge.

How the numbers work:
- **Count:** the item being copied now, out of the total. It never passes the total.
- **Percent:** rounded down, so it never shows 100% before the copy is done.
- **Progress:** bytes copied divided by total bytes, when every size is known. Otherwise each item counts as an equal share. While a file of unknown size copies, the line under the bar shows the amount copied, so the screen keeps changing.
- **No looping indeterminate bar.** Nothing in Memix moves on its own (MOTION 2).
- **Time left:** remaining bytes divided by the average speed over the last 2 s.
  - Shown only after 2 s of copying, and only when over 10 s.
  - Rounded up to 5 s steps under a minute, whole minutes above.
  - A percent-done indicator with an estimate is the guidance for waits over 10 s (Guidance).

### Import sheet, results
Results use the same sheet surface:
- The title in `title`.
- A body line in `body` `text-secondary`.
- A list of the items that didn't go in.
- One full-width button.

**Row for an item that didn't go in:**
- Leading type icon: `MemixIcons.Video` or `MemixIcons.Photo`, 24, `text-secondary`.
- Then a column:
  - File name in `body-strong` `text`, one line, with the ellipsis in the middle so the extension stays visible. With no file name, the row says "Video" or "Photo".
  - The reason in `label` `text-secondary`. It wraps and is never cut off.
- `space-3` between rows. No dividers, no cards, no thumbnails (a file that won't open usually can't make one).
- **Long lists** scroll inside the sheet. The sheet's top stays at least `space-10` below the status bar, and the title and button stay in place.

### Video editor (for P1-02 only)
The editor is still the P0-05 placeholder; P1-04 and P1-05 replace it. So the "Done when" can be seen, the placeholder lists the project's media, **read back from the saved project**, not from the picker result:
- **Title** "Video editor" as now. The body changes to "Your media is in. The preview and timeline come next."
- **List:** one row per clip, in timeline order. Each row has:
  - the position in `timecode` `text-muted`
  - the type icon (24, `text-secondary`)
  - "Video" or "Photo" in `body-strong`
  - the duration in `timecode` `text-secondary`, right-aligned (for example 00:12.40; photos 00:03.00)
- **Row size:** at least `touch-target` tall, `space-4` side gutters. Rows can't be tapped.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Picker open | System picker at half height over the current screen | (system) |
| Nothing picked | Current screen unchanged (Create sheet still open, or Home) | none |
| Quick copy (all done < 300 ms) | Straight to the editor; the import sheet never shows | none |
| Copying (> 300 ms) | Import sheet, copying: title, count, percent, bar, Cancel | `import_title`, `import_count`, `import_percent`, `import_cancel` |
| Copying, long | As above, plus time left when over 10 s | `import_time_left_seconds` / `_minutes` |
| Copying, size unknown | Amount copied instead of time left | `import_copied` |
| Over the limit (file chooser only) | The sheet always shows in this case, even for a quick copy. The note sits under the bar. When the copy ends, the sheet goes to "Some didn't go in" with the note as its body, not straight to the editor. | `import_cap_note` |
| Cancelled (Cancel, system back or Escape while copying) | Everything from this pick is dropped and its copies deleted. The sheet slides away and you're where you started: the screen under the Create sheet (sheet closed), or for "Add media" the editor, unchanged. No project. No confirmation, because picking again is cheap. | none |
| Some didn't go in (at least one did) | Blocking result: title "3 of 5 added", body, rows (plus the limit note if it applies), **Continue** (primary) → editor. System back and Escape also continue: every way out leads to the editor with what went in. | `import_partial_title`, `import_partial_body`, `import_continue` |
| None went in | Standard result (grabber and close button): title, body, rows, **Pick again** (primary). It closes the sheet (200 ms), then opens the picker. Close, scrim, drag or back → where you started, no project. | `import_none_title`, `import_none_body`, `import_pick_again` |
| Can't be read (per item) | Row reason. The file couldn't be opened or read: it was deleted, access was lost, or a cloud-only item couldn't download. | `import_reason_unreadable` |
| Won't play (per item) | Row reason. The copy has no playable video track, the image won't decode, or the metadata check fails. | `import_reason_unsupported` |
| Not enough space (before copying) | Checked before the first byte is copied: total known size plus 100 MB headroom, against the space Memix can use. Standard sheet: title, body with both sizes, **Free up space** (primary) opens Android's storage manager and asks for the total needed, headroom included (`EXTRA_REQUESTED_BYTES` is the total the app will allocate; the system works out what's missing). When you come back to Memix it checks again by itself. With enough space, copying starts and the sheet switches to Copying; without, the numbers update. Close → nothing added. | `import_storage_title`, `import_storage_body`, `import_free_up_space` |
| Ran out of space while copying | Same sheet; the first size is what's left to copy. Items already copied are kept for the retry and dropped on Close. | same |
| Ran out of space saving the new project (added 8 Oct, owner decision) | **When:** every file is copied and checked and at least one went in, but the new project's first save fails because the phone is full (`StorageFull` at the database write). Not "Couldn't add your media": the files are fine, the phone is full. **Shows:** the same "Not enough space" sheet and **Free up space** button. Nothing is left to copy, so the first size is the 100 MB headroom (what Memix waits for before it saves again) and the second is what's free now. If the copying sheet was up, its content cross-fades to this one (the bar showed 100% first); after a quick copy with no sheet, the sheet slides up already in this state. **Copies:** kept while the sheet is open. **Back in Memix** (it checks by itself, as above): with 100 MB free, it saves again, without copying anything again and without going back to Copying, then goes where a good save goes: the editor, or "Some didn't go in" when items failed or the limit applied. `project_create` fires then, once. Still too full: the numbers update, and the sheet stays. **Close** (button, scrim, drag, back): the copies are deleted, no project, no event; you're where you started. If Android stops the app while the sheet is up, the next launch deletes the copies (no saved project uses them). **Other save failures** (not space; rare) keep today's build: "Couldn't add your media" with no rows, copies deleted. **Not here:** saves inside the editor (autosave, later "Add media") use P1-07's save banner, not this sheet. | Reused, no new strings: `import_storage_title`, `import_storage_body` (%1$s = 100 MB), `import_free_up_space`; Close as in "Not enough space" |
| Offline | Files on the phone copy as usual. A cloud-only item may fail with "Can't be read", because the picker fetches cloud items when Memix opens them (Guidance). | none |
| No picker app on the phone (launch fails) | Standard sheet: title, body, **Close** (secondary, full width, in thumb reach) | `import_no_picker_title`, `import_no_picker_body`, `close` |
| App in the background while copying | Copying goes on while the app is running, and the sheet shows the current state on return. If Android stops the app, the pick is lost: the picker's access ends when the app stops (Guidance). Unfinished files are deleted at the next launch, and a first import leaves no project. | none |
| Picker result after Android stopped the app (picker open on a low-memory phone) | The result still arrives once the app restarts, and the copy starts as usual | none |
| Permission denied or limited | Doesn't apply: Memix declares no media permission, so there's nothing to deny | none |
| First run | Same as every run: no explainer, no permission prompt, no tips | none |
| Long content | 35 items: the count reads "35 of 35". Long file names are shortened in the middle. Titles wrap to two lines, and button labels wrap instead of being cut off. Five languages and 200% font scale: see Accessibility. | none |

## Interactions
| Where | Tap | System back / Escape | Scrim tap / drag down |
| --- | --- | --- | --- |
| Entry (card or row) | Opens the picker; press colors over 120 ms | (normal for that screen) | n/a |
| Copying | Cancel → dropped, back where you started | Same as Cancel | Nothing |
| Some didn't go in | Continue → editor | Same as Continue | Nothing |
| None went in, Not enough space, No picker | Their button | Closes; nothing added | Closes; nothing added |

No haptics: the haptic tick belongs to sounds landing on the timeline.

### "Add media" from inside the editor (later ticket; P1-05 places the button)
- **Entry:** an "Add media" button at the end of the main video track; the P1-05 spec gives its exact position. One tap opens the same picker with the same settings and the same limit (35 per pick, no project limit).
- **While copying:** the same import sheet, over the editor.
- **Where items land:**
  - After the clip under the playhead. If the playhead sits exactly between two clips, they land there. If it's at the end, they're added at the end.
  - Never splits a clip.
  - Later clips move right by the added length (placed by time, not by index).
- **After landing:** the playhead moves to the start of the first new clip, the new clips bonk once (`duration-bonk`), and the selection doesn't change.
- **Undo** is one step that removes the whole pick. Cancelled, or nothing went in: the editor is unchanged.
- **Gap:** no ticket covers this, or the PRD's "replace a clip", yet (proposal below).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Picker | The system's own | The system's own |
| Create sheet leaves after a pick | 200 ms slide down; scrim stays | 120 ms fade |
| Import sheet arrives | 200 ms slide up (scrim fades in when coming from Home) | 120 ms fade |
| Progress fill | Moves to each new value over 120 ms, linear; at most 10 updates a second | Jumps |
| Copying → result | Content cross-fades over 120 ms; height changes over 200 ms | 120 ms fade; height snaps |
| To the editor | 320 ms push that covers the sheet | 120 ms fade |
| "Add media" (later): clips land | Bonk, 240 ms | 120 ms fade-in |

## Copy
English source. Translators: the engineer machine-drafts id, es, pt and hi (allowed until P5); final translations come in P5-06. Budgets are characters including filled-in placeholders.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `create_video_body` (changed) | Pick videos and photos | 34 | Replaces "Pick clips from your gallery". Verb first. |
| `import_title` | Adding your media | 28 | "Media" means videos and photos. If the word sounds stiff, say "videos and photos" within budget. |
| `import_count` | %1$d of %2$d | 16 | %1$d = the item being added now, %2$d = the total |
| `import_percent` | %1$d%% | 6 | Use the language's own percent format |
| `import_time_left_seconds` | one: About 1 second left · other: About %d seconds left | 30 | Plural resource |
| `import_time_left_minutes` | one: About 1 minute left · other: About %d minutes left | 30 | Plural resource |
| `import_copied` | %1$s copied | 24 | %1$s = a short size in the user's language, e.g. "48 MB" |
| `import_cap_note` | You can add up to %1$d at a time, so these are the first %1$d you picked. | 100 | %1$d = 35 |
| `import_cancel` | Cancel | 16 | |
| `import_partial_title` | %1$d of %2$d added | 28 | %1$d added, %2$d picked |
| `import_partial_body` | These couldn't be added. Everything else is in your project. | 90 | When only the limit applies (no failures), the body is `import_cap_note` instead |
| `import_continue` | Continue | 18 | |
| `import_none_title` | Couldn't add your media | 32 | |
| `import_none_body` | None of these could be added. Try other videos or photos. | 80 | |
| `import_pick_again` | Pick again | 18 | |
| `import_item_video` | Video | 16 | Row name when the file has no name; also the editor list and the icon's spoken label |
| `import_item_photo` | Photo | 16 | Same uses as `import_item_video` |
| `import_reason_unsupported` | Damaged, or a format this phone can't play | 60 | |
| `import_reason_unreadable` | Couldn't be read. If it's in the cloud, check your connection. | 80 | |
| `import_storage_title` | Not enough space | 28 | |
| `import_storage_body` | Your media needs %1$s, and your phone has %2$s free. Free up space, then come back to carry on. | 130 | Sizes in the user's language. "Carry on" = continue. %1$s includes the 100 MB headroom; after a failed first save it is the headroom alone. |
| `import_free_up_space` | Free up space | 22 | |
| `import_no_picker_title` | Can't open your gallery | 32 | |
| `import_no_picker_body` | This phone has no app for picking videos and photos. | 80 | |
| `editor_video_body` (changed) | Your media is in. The preview and timeline come next. | 70 | Internal builds only; P1-04 replaces it |
| `editor_media_row_a11y` | %1$s %2$d of %3$d, %4$s | 50 | Screen reader only: "Video 1 of 3, 12 seconds"; %4$s = `duration_seconds` |
| `duration_seconds` | one: 1 second · other: %d seconds | 20 | Plural resource, whole seconds, minimum 1 |

Translator notes:
- **Tone:** plain and calm. Errors never blame the user and always say what to do.
- **Case:** sentence case everywhere; no uppercase styles in this flow.
- **"Memix"** never translates.
- **Placeholders** are numbered, so word order can change (Hindi puts the total first: "5 में से 3").
- **Length:** Portuguese and Spanish run longest; keep `import_cancel`, `import_continue` and `import_pick_again` short enough for one line at 360 dp.
- **Hindi** falls back to the system Devanagari face, which needs more line height. No text row in this flow has a fixed height.

## Accessibility
- **Sheet opens:** focus moves to the sheet and TalkBack reads its title. Use the title as the pane title, so the change to a result state is announced too.
- **Count line:** a polite live region. It speaks when a new item starts ("4 of 5"), not on every percent. That meets WCAG 2.2 SC 4.1.3 Status Messages (Guidance).
- **Progress bar:** a progress range from 0 to 1 with a state description ("40 percent"). It's read when focused, not live.
- **Time-left line:** not live.
- **Buttons:** the visible label is the accessible name ("Cancel", "Continue", "Pick again", "Free up space", "Close"), per SC 2.5.3. All are at least `touch-target` tall and sit at the bottom of the sheet, in thumb reach.
- **Rows that didn't go in:** each is one screen-reader node: "{file name}, {Video or Photo}. {reason}". Failure is never shown by color alone; the reason is text. `danger` isn't used, because nothing is being deleted.
- **Editor list rows** read `editor_media_row_a11y`, not the timecode digits.
- **Focus and keys:**
  - Focus order is title, status, list, button.
  - Tab reaches every button. Escape follows the system-back rules in Interactions.
  - The sheet holds focus while open, and focus returns to the entry card or row when the sheet closes without a pick.
- **Contrast:**
  - All text is `text`, `text-secondary` or `text-muted` on `surface` (4.6:1 or more).
  - The progress fill is 7.3:1 against `surface` and 4.9:1 against its `hairline` rail; both are over the 3:1 needed for UI parts.
- **200% font scale:**
  - The sheet grows; the list scrolls inside it.
  - Title and button labels wrap to two lines rather than being cut off.
  - The count and percent row wraps the percent under the count if needed.
- **Reduce motion:** see Motion.
- **The picker** handles its own accessibility (system UI).

## Analytics
- **`project_create`** `{editor: "video", source: "gallery"}` fires once, when the new project is first saved with at least one item, after a full or partial import. It doesn't fire on back-out, cancel, when nothing went in, or on a first save that failed for space; it fires when the save after freeing space succeeds. `gallery` was approved by the owner on 8 Oct (decision log; PRD → Metrics and analytics): `blank` now means only the photo editor's blank layout.
- **Later, "Add media":** `tool_use` `{editor: "video", tool: "add_media"}` when the items land.
- **No new events.** For import failure rates, the engineer may log Crashlytics non-fatals with only the file type, a size bucket and the error class: no file names, no URIs.

## QA compares
Only Memix's own states are compared. The picker is system UI.
1. **Copying a large video:** count, percent, part-filled bar, time left, Cancel. In English, and in Hindi at 200% font scale.
2. **Some didn't go in:** two good files and one damaged one (for example a truncated MP4). Expect "2 of 3 added", the row with file name and reason, and Continue.
3. **None went in:** one damaged video. Expect "Couldn't add your media", its row, and Pick again.
4. **Not enough space**, with real sizes filled in.
5. **Editor after a mixed pick** (video, photo, video): rows in the order the picker returned, with the photo at 00:03.00.
6. **Create sheet** with "Pick videos and photos", in English and in Hindi at 200%.

Checks without screenshots (note the results in the QA report):
- **No permission prompt.**
  - On a fresh install, go Create → Video meme → pick → editor lists the media. No permission dialog appears at any point.
  - App info → Permissions lists no Photos and videos or Files permission.
  - The merged manifest has no `READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` or `READ_MEDIA_VISUAL_USER_SELECTED`.
  - Repeat on Android 10 (API 29): the backport picker or the file chooser shows, still with no prompt.
- **Back out of the picker:** the Create sheet is still open and no draft exists.
- **Cancel while copying:** you're back where you started, with no draft and no files left in the app's media folder.
- **Order:** the editor rows match the order the picker returned. Note whether the picker showed numbers on that device.
- **Limit:**
  - The system picker stops at 35.
  - The file chooser with more than 35 picked shows the limit note and adds 35.
- **Quick path:** one short phone clip goes straight to the editor and the sheet never flashes. On the owner's phone, note how long a 1 GB video takes to copy.
- **Failed first save for space** (hard to hit by hand: the disk must fill between the last copy and the save). Checked off-device by the engineer with a save that returns `StorageFull`: the "Not enough space" sheet shows 100 MB needed; Close deletes the pick's folder and leaves no draft; a resume with space saves without copying again, opens the editor (or "Some didn't go in"), and fires `project_create` once.
- **Phase end, once Drafts (P1-14) exists:** delete the original from the gallery, reopen the draft, and check it still plays from Memix's copy.

## Requests to the principal mobile engineer
I don't edit code, so these are requests, not changes.
1. **Picker:** `PickMultipleVisualMedia` set up as in the table under "During the picker". Register the launcher so a result that arrives after Android stopped the app still starts the copy.
2. **Manifest:**
   - No media or storage permissions in the merged manifest. Check that no library adds them through manifest merging.
   - Add the Play services backport entry (`com.google.android.gms.metadata.ModuleDependencies` with `photopicker_activity:0:required`) so Android 10 phones get the picker.
3. **Copies:**
   - Keep them in the app's files directory, as `MediaRef.cachedCopyPath` already says, not the cache directory: Android can delete cache files when storage runs low, which would break drafts.
   - Write to a temporary name and rename once complete. Delete leftovers on cancel and at launch.
   - Delete a project's copies with the draft (P1-14). Delete a dropped clip's copy only when no undo state still points to it.
4. **Space:**
   - Check with `StorageManager.getAllocatableBytes` before copying, needing the total plus 100 MB. Running out mid-copy goes to the same state.
   - "Free up space" opens `StorageManager.ACTION_MANAGE_STORAGE` with `EXTRA_REQUESTED_BYTES`. If that doesn't open, use the internal storage settings screen. If neither opens, hide the button and the body still tells the user what to do.
   - Check again when the app comes back to the foreground.
   - **Failed first save (8 Oct):** when `SaveGalleryProjectUseCase` returns `StorageFull`, don't discard the batch. Keep it as the pick waiting for space and show `NotEnoughSpace` (`checkSpace` on a fully copied batch already gives the 100 MB headroom). On resume with enough space, run only the save and the result step again, not `copy`. Today's `finish()` sends every save failure to `NoneAdded` with no rows (`MediaImportViewModel.kt:122-126`); keep that only for failures other than `StorageFull`.
5. **Check each copy:** a video needs a duration and a playable video track; a photo must decode. Record each source's full duration and pixel size at import, because P1-06 trim limits and P1-11 fit need them. Where to store them is the engineer's call.
6. **Names and timing:**
   - Leave `Project.name` empty at creation; no schema change.
   - Use the 3.0 s still duration as one constant until the PM decides.
   - Copy off the main thread. Progress updates at most 10 times a second.
7. **Tokens and components:**
   - Add `MemixSize.railHeight = 4.dp` and use it in Slider in place of its private 4 dp.
   - Add the ProgressBar component and the Sheet blocking variant (specs linked under Layout) to `:core:designsystem` and to the debug component catalog.
8. **Persistable URI permission:** Memix doesn't need lasting access to the original, because the copy is what the project uses. Whether to take one is your call.

## Proposals and questions for the PM
1. **Still duration (PRD is silent).** I propose **3.0 s** for photos on the video timeline, and the same value for P2-10 "Make it a video meme".
   - Desktop editors default to 4–5 s for slideshow pacing (Evidence).
   - Memes move faster, and meme sounds are mostly a few seconds long (Assumption).
   - The user can trim it in P1-06.
2. **No ticket for "add more at any time" and "replace a clip"** (PRD → Video editor → Media). This spec defines how "Add media" behaves. Either fold both into P1-06 or add an S-size ticket after P1-05.
3. ~~**`project_create.source`** has only `blank` and `template` in the PRD.~~ **Decided 8 Oct (owner):** `source` gains `gallery`; video gallery starts send it, and `blank` means only the photo editor's blank layout (decision log; PRD → Metrics and analytics). Analytics above is updated.
4. **Decided 8 Oct (owner): a failed first save for space** reuses the "Not enough space" sheet with Free up space, not "Couldn't add your media". See States: "Ran out of space saving the new project".

Nothing here needs the owner's decision.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| The photo picker needs no permission. It runs on Android 11+ through Google system updates, and on Android 4.4–10 through a Play services backport. Otherwise it falls back to `ACTION_OPEN_DOCUMENT`. It opens at half height. `getPickImagesMaxLimit()` caps the count, and the cap is ignored in the fallback. Access lasts until restart or until the app stops. Cloud items are included. HDR transcoding needs Android 13+ and works up to 1-minute videos. | Guidance | [Android photo picker](https://developer.android.com/training/data-storage/shared/photo-picker) |
| Ordered selection, accent color and default tab were added in androidx.activity 1.10.0, and "might not be supported by the underlying photo picker implementation". The accent color needs API 35. The `EXTRA_PICK_IMAGES_IN_ORDER` extra arrived in API 35. | Guidance | [PickVisualMediaRequest.Builder](https://developer.android.com/reference/androidx/activity/result/PickVisualMediaRequest.Builder), [activity releases](https://developer.android.com/jetpack/androidx/releases/activity), [API 35 MediaStore diff](https://developer.android.com/sdk/api_diff/35/changes/android.provider.MediaStore) |
| Cloud items are served to the app through FUSE when it opens them, so copying can be slow or fail offline | Guidance | [Cloud media provider](https://developer.android.com/guide/topics/providers/cloud-media-provider) |
| Google Play asks apps with one-time or occasional photo use to use the system picker instead of media permissions | Guidance | [Play Photo and Video Permissions policy](https://support.google.com/googleplay/android-developer/answer/14115180) |
| CapCut's import help points users to photo permissions, album loading and unsupported formats | Evidence (vendor help center, read through search results; the site is blocked here) | [Album goes black while importing](https://www.capcut.com/help/album-importing-not-work), [Can't import MP4 and JPG](https://www.capcut.com/help/can-not-import-mp4-and-jpg-files) |
| TikTok photo mode takes up to 35 photos, in tap order. Instagram carousels went up to 20 in 2024. | Evidence (third-party guides, press) | [Kapwing](https://east.kapwing.com/resources/how-to-post-photos-and-carousels-on-tiktok-with-photo-mode/), [Metricool](https://metricool.com/instagram-increases-carousel-content-limit/) |
| Editors default a still to 4–5 s | Evidence (help pages) | [Clipchamp](https://support.microsoft.com/en-us/Clipchamp/how-to-change-the-duration-of-an-image-on-the-timeline), [Movavi](https://img.movavi.com/online-help/videosuite/16/image_duration.htm) |
| Waits over about 10 s need a percent-done indicator with an estimate | Guidance | [NN/g response-time limits](https://www.nngroup.com/articles/response-times-3-important-limits/), [NN/g progress indicators](https://www.nngroup.com/articles/progress-indicators/) |
| Delay showing progress and keep it up for a minimum time so it doesn't flash | Guidance | [ContentLoadingProgressBar](https://developer.android.com/reference/androidx/core/widget/ContentLoadingProgressBar) |
| Android may delete cache files when storage is low | Guidance | [App-specific storage](https://developer.android.com/training/data-storage/app-specific) |
| `ACTION_MANAGE_STORAGE` (API 25) opens the system's free-up-space screen and takes `EXTRA_REQUESTED_BYTES`. Use `getAllocatableBytes` (API 26) for space decisions. | Guidance | [StorageManager](https://developer.android.com/reference/android/os/storage/StorageManager) |
| Status messages must reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
