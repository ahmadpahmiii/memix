# Drafts · P1-14

Replaces the P0-05 Drafts tab (title plus empty state) with the real list. Consistent with `P0-05-navigation-and-browse-screens.md` (frame, empty state, bottom nav), `P1-02-gallery-picker.md` (names stored empty, the import sheet, "Not enough space") and `P1-04-editor-and-preview.md` (opening a draft, missing media).

## Job
"Resume work" (PRD → App structure): find the meme you were making and get back into it in one tap, plus keep the list tidy (rename, duplicate, delete).

**Done when** (TICKETS): drafts sort by last edited.

**The one blue action:** none on the screen itself. Create in the bottom navigation stays the blue (P0-05). The rename sheet's Save is its own one primary.

**Fast path:** Drafts tab (1), the draft (2): the editor opens where the PRD target says, in under 1 s.

## Entry and exit
- **Arrives from:** the Drafts tab in the bottom navigation, and back from an editor opened here.
- **A row** → the editor for that draft (P1-04 entry: playhead at 0:00, paused, nothing selected). Photo drafts open the photo editor once P2 exists.
- **Back from the editor:**
  - If the draft changed, it's now first (it was edited last): the list scrolls to the top and focus goes to its row.
  - If it didn't change, the list keeps its scroll and focus returns to its row.
  - A draft the editor deleted (no clips left, P1-04) is simply gone.
- **System back** on Drafts follows P0-05 (tabs go back to Home).
- **Kept:** everything is on the phone. Not kept: the scroll position across app restarts.

## Layout
No board: the P0-05 frame plus this spec. Title "Your drafts" (`title-l`, P0-05), then the list, scrolling above the bottom navigation.

### Draft row
`canvas` background, no dividers, no card. From the start:
1. **Thumbnail:** a `list-thumb-size` (56, new token) square, `radius-sm`, `space-4` from the screen edge.
   - Image: the first frame of the video (the first main clip at its trim-in), center-cropped square, as the timeline tiles are (P1-05).
   - **Type badge** in its bottom-start corner, `space-1` inside: `scrim` fill, `radius-sm`, the `video` icon (later `photo`) at `icon-small` in `text` (7.5:1 or more over any image). The PRD's "type badge"; it matters once photo drafts arrive in P2, and is shown now so the row doesn't change later.
   - No image yet, or it can't be read: `surface-raised` with the type icon at 24 in `text-secondary`. No spinner (MOTION 2); the image fades in over 120 ms when ready.
2. **Text**, `space-3` after the thumbnail, filling the width:
   - Name in `body-strong` `text`, up to two lines, then an ellipsis at the end (the full name is spoken).
   - Below it, the edit time in `label` `text-secondary` ("Edited 2 hours ago").
3. **More options:** IconButton, new icon `more` (three solid dots in a row; all core, like play) in `text-secondary`, 48 dp target, `space-1` from the end edge. It opens the row's Menu.

- **Row height:** at least the thumbnail plus `space-2` above and below (72 dp); it grows with the text.
- **Pressed:** a `surface` fill (one value step up from `canvas`) behind the row, `radius-md`, inset `space-2` from both screen edges, over `duration-press`. Nothing moves.
- **Unnamed drafts** (names are stored empty, P1-02) show "Untitled video meme" in the user's language, so the name follows a language change.

### Row menu
Component Menu (`design/system/components/Menu/README.md`), anchored to the More options button (or to the press point after a long-press):

**Rename · Duplicate · Delete**

- Labels in `body` `text`. Delete stays `text`: `danger` on the menu's `surface-raised` is 4.3:1, under the 4.5:1 text needs, and the word says enough. The undo toast is the safety net, not color.
- The Menu rule "never the only route" holds: TalkBack and Switch Access get the same three as row actions; long-press is a shortcut.

### Rename sheet
Standard Sheet (`design/system/components/Sheet/README.md`), title "Rename draft", close button.
- A single-line text field (P1-10's field look: `surface-raised`, `radius-md`, `space-3` padding, at least `touch-target` tall, `body` `text`), labelled "Name". Prefilled with the current name with **all of it selected**, so typing replaces it and a tap places the cursor (the file-rename convention). An unnamed draft opens empty, with the stand-in "Untitled video meme" as the placeholder in `text-muted`.
- **Save:** the primary Button, full width at the bottom, riding above the keyboard. The keyboard's Done key saves too.
- Limit 60 characters (grapheme clusters); from 50 on, "52/60" in `caption` `text-muted` under the field, end-aligned.
- The keyboard opens with the sheet.

## Order (the Done when)
- Newest edit first: sorted by `updatedAtEpochUs`, which every save sets (TECHNICAL_DESIGN → Storage).
- **Opening a draft without changing it doesn't move it:** the editor saves nothing unless something changed (P1-07).
- **Renaming doesn't move it either.** A name isn't an edit to the meme, and a row that jumps away right after the user renamed it breaks their place (Assumption). The rename is written without touching `updatedAtEpochUs` (request 2; Proposals 1).
- **A duplicate sits directly above its original** (it takes the original's time plus 1 µs), so it appears where the user is looking. It moves to the top the first time it's edited (Proposals 2). Its edit-time line shows the original's time, which is true of its content.
- **Ties:** when two drafts have the same edit time (a second copy of the same original ties with the first), the newer one goes first: sorted by `createdAtEpochUs`, newest first. Both copies stay above the original (PM, 20 Oct).

## States
| State | What shows | Copy |
| --- | --- | --- |
| Empty | P0-05's empty state, unchanged: headline, body, secondary "Make a meme" (opens Create) | `drafts_empty_*` (P0-05) |
| Default | Rows, newest edit first | `drafts_untitled_video`, `drafts_edited_*`, `drafts_more_a11y` |
| Loading (> 300 ms) | Only the title; rows appear when read. No spinner or skeleton (MOTION 2). The database is on the phone, so this is rare. | none |
| Load error (the database can't be read) | Centered message in `body` `text-secondary` and a secondary "Try again" | `drafts_load_error`, `drafts_try_again` |
| Menu open | Menu by the button or press point | `drafts_rename`, `drafts_duplicate`, `drafts_delete` |
| Renaming | Rename sheet with keyboard | `drafts_rename_title`, `drafts_rename_label`, `drafts_rename_save`, `drafts_name_count` |
| Deleted | Row gone; toast with Undo, above the bottom nav | `drafts_deleted`, `drafts_undo` |
| Duplicated | New row above the original; toast | `drafts_duplicated` |
| Duplicating takes over 300 ms | P1-02's import sheet (blocking), title "Copying your draft", determinate progress, Cancel | `drafts_duplicating_title`, P1-02 progress strings |
| Not enough space to duplicate | P1-02's "Not enough space" sheet with Free up space | P1-02 strings |
| Draft from a newer Memix (`schema_version` above this app's) | Row as usual, but the meta line says so; a tap, Rename or Duplicate explain with a toast; Delete works | `drafts_needs_update`, `drafts_cant_open` |
| Damaged draft (found only when opened) | Toast on tap; nothing else changes | `drafts_cant_open` |
| Draft whose media is missing (restored from a backup, TECHNICAL_DESIGN → Backups) | Placeholder thumbnail; it opens in P1-04's missing-media state | P1-04 |
| Long content | Names wrap to two lines then ellipsis; 200% font: rows grow, the meta wraps, the button stays at the end | none |
| Offline, permissions | Don't apply | none |
| First run | The empty state | P0-05 |

## Interactions
- **Tap a row:** opens the draft (320 ms push, P1-04).
- **Long-press a row:** the Menu at the press point, with a LongPress haptic.
- **More options:** the Menu, anchored to the button.
- **Rename:** the sheet. Save writes the trimmed name; an empty name makes the draft unnamed again (the stand-in shows). Unchanged: the sheet just closes. Close, scrim, drag down, back or Escape: nothing changes. Line breaks can't be typed.
- **Duplicate:** a copy with everything in the draft and its own media copies (request 3), named "{name} (copy)", then "(copy 2)", "(copy 3)" if that name exists. A copy of an unnamed draft stays unnamed. Toast "Draft duplicated."
- **Delete:** the row goes at once and a toast offers Undo. No confirmation: two deliberate taps (More options, Delete) plus undo protect the work better than a dialog people click through by habit (Raskin; Guidance).
  - **Undo** puts the draft back exactly, in its place.
  - The toast stays 8 s, or longer if the user's "Time to take action" setting asks for more (it contains a control).
  - The files are removed when the toast ends, or at the next app start if Memix is closed before that (`LeftoverMediaCleaner` already does this for drafts that aren't saved).
  - A second delete while the toast shows finalizes the first; the new toast replaces it.
- **Swipe to delete:** no. It's undiscoverable and easy to trigger while scrolling.
- **Relative times** update when the screen comes back to the foreground and once a minute while it's shown.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Row press | 120 ms fill fade | same |
| Deleted row | Collapses over 200 ms; rows below slide up | instant |
| Undo / new duplicate row | Rows part over 200 ms, the row fades in | instant |
| Menu | 120 ms fade (Menu) | same |
| Rename sheet | 200 ms slide over a fading scrim | 120 ms fade |
| Thumbnail arrives | 120 ms fade-in | same |
| Opening a draft | 320 ms push | 120 ms fade |

## Copy
English source; machine drafts in id, es, pt and hi until P5. Budgets in characters, placeholders filled.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `drafts_untitled_video` | Untitled video meme | 30 | Shown for a draft with no name. Never saved, so it follows the language |
| `drafts_untitled_photo` | Untitled photo meme | 30 | For P2 |
| `drafts_type_video` | Video meme | 20 | Spoken name of the type badge |
| `drafts_type_photo` | Photo meme | 20 | For P2 |
| `drafts_edited_now` | Edited just now | 30 | Under a minute ago |
| `drafts_edited_minutes` | one: Edited 1 minute ago · other: Edited %d minutes ago | 30 | Plural resource |
| `drafts_edited_hours` | one: Edited 1 hour ago · other: Edited %d hours ago | 30 | Plural; under 24 hours |
| `drafts_edited_yesterday` | Edited yesterday | 30 | The previous calendar day |
| `drafts_edited_days` | other: Edited %d days ago | 30 | Plural; 2 to 6 days |
| `drafts_edited_date` | Edited %1$s | 30 | %1$s = a short date in the phone's locale ("12 Oct", with the year if it isn't this year) |
| `drafts_needs_update` | Needs a newer version of Memix | 40 | Meta line for a draft saved by a newer app. "Memix" never translates |
| `drafts_more_a11y` | More options | 24 | Spoken label of the ⋮ button |
| `drafts_rename` | Rename | 20 | Menu item and row action |
| `drafts_duplicate` | Duplicate | 20 | Same word as the editor's Duplicate tool in your language |
| `drafts_delete` | Delete | 20 | Same word as the editor's Delete tool |
| `drafts_rename_title` | Rename draft | 30 | Sheet title |
| `drafts_rename_label` | Name | 20 | Field label |
| `drafts_rename_save` | Save | 16 | Primary button |
| `drafts_name_count` | %1$d/%2$d | 9 | "52/60" |
| `drafts_copy_name` | %1$s (copy) | 60 | %1$s = the original's name, shortened to fit the 60-character limit |
| `drafts_copy_name_n` | %1$s (copy %2$d) | 60 | "(copy 2)" |
| `drafts_deleted` | Draft deleted. | 30 | Toast |
| `drafts_undo` | Undo | 12 | The toast's Quiet button |
| `drafts_duplicated` | Draft duplicated. | 30 | Toast |
| `drafts_duplicating_title` | Copying your draft | 30 | Import-sheet title while a big duplicate copies |
| `drafts_cant_open` | This draft can't be opened. Update Memix, or delete it. | 70 | Toast |
| `drafts_load_error` | Your drafts couldn't be loaded. | 50 | |
| `drafts_try_again` | Try again | 18 | Same text as `editor_preview_retry`: reuse it |
| `a11y_draft_row` | %1$s, %2$s, %3$s | 120 | Spoken row: name, type, edit time ("Cat fail, Video meme, Edited 2 hours ago") |

Translator notes:
- **"Draft"** is an unfinished meme saved on this phone, not an email draft. Use the word your language's video apps use.
- **Edit times** are complete sentences so you can order the words naturally; translate each plural form.

## Accessibility
- **TalkBack order:** title → rows in order → bottom navigation. Each row is **one node** ("Cat fail, Video meme, Edited 2 hours ago"); double-tap opens the draft. Its actions: Rename, Duplicate, Delete. The More options button is a separate focus stop for keyboards and Switch Access; TalkBack users can use it or the actions.
- **The list** is a lazy list; off-screen rows come with scrolling (P1-05's lesson: Compose leaves off-screen nodes out).
- **Menu and sheet:** Menu and Sheet rules (focus in on open, back to the button or row on close). After Rename, focus returns to the row (which hasn't moved). After Delete, focus goes to the next row (or the previous one at the end; the empty state's button when the list empties) and the toast is announced politely; its Undo is focusable. After Undo, focus returns to the restored row. After Duplicate, focus stays on the original's button and the toast is announced.
- **Touch targets:** rows, the More options button, menu items, Save and the toast's Undo are 48 dp or more.
- **Contrast:** name `text` on `canvas` 16.9:1; meta `text-secondary` 7.8:1; More icon `text-secondary` 7.8:1; pressed row `surface`: name 15.4:1, meta 7.1:1. Badge icon `text` on `scrim` 7.5:1 or more over any image. Toast and menu `text` on `surface-raised` 12.4:1; Undo `primary` on `surface-raised` 5.9:1.
- **200% font:** the row grows; the name keeps its two lines and the meta wraps; the thumbnail stays 56 and aligns to the top of the text. The rename field grows with the text; the sheet scrolls above the keyboard if needed. Nothing is cut except a name past two lines, which is spoken in full.
- **Keyboard:** Tab moves row → More options → next row; Enter opens; the Menu key or Shift+F10 opens the row menu; Escape closes menus and the sheet. Shortcuts: Later (L-01).
- **Reduce motion:** see Motion.

## Analytics
- **No PRD event fires here.** Opening, renaming and deleting a draft aren't PRD events.
- **Duplicate doesn't fire `project_create`:** its `source` values (blank, template, gallery) describe how a new meme starts, and a copy isn't one (Proposals 3).

## QA compares
1. **Three drafts** at 360 dp, sorted by last edit: thumbnails with the badge, one unnamed ("Untitled video meme"), the edit times.
2. **After editing the oldest draft** and coming back: it's first and focused. After renaming the middle one: it hasn't moved.
3. **Menu open** from More options, then the rename sheet with the keyboard and the name selected.
4. **Delete:** the row gone and the toast with Undo; then Undo restores it in place.
5. **Duplicate:** "{name} (copy)" directly above the original.
6. **Hindi at 200% font**, 360 dp: rows, menu and the rename sheet; plus the `needs-update` row (debug draft with a higher `schema_version`, request 7).

**Measured:** the Done when on 10 debug drafts: edit three in a known order and check the list order; reopen without editing and check nothing moved; kill the app during a delete's toast and check the draft is gone and its folder is deleted at the next start. LeakCanary: open and close 5 drafts from the list; no leak.

## Requests to the principal mobile engineer
I don't edit code, so these are requests.
1. **List:** `ProjectSummary` rows ordered by `updated_at_epoch_us` descending, then `created_at_epoch_us` descending for ties (the columns already exist, so the list never decodes projects), observed as a Flow so returning from the editor updates it. Read `schema_version` too, for the newer-version state.
2. **Rename** without touching `updated_at_epoch_us`: write the new name into the project JSON and the `name` column, keeping the time. This is a repository operation, not an editor commit.
3. **Duplicate:** a new id, `createdAtEpochUs` now, `updatedAtEpochUs` = the original's + 1, the name rule, and its own media folder. Please use **hard links** for the media copies if both platforms allow it in the app's files directory (Android `Os.link`, iOS `FileManager.linkItem`): the copies are written once and never changed, so a link is instant and takes no space, and deleting either draft leaves the other whole. If links aren't possible, copy with P1-02's flow (space check, the import sheet after 300 ms, Cancel deletes the partial copy).
4. **Delete with undo:** remove the row at once, keep the project in memory and its files on disk until the toast ends, then delete the folder. Undo writes the same project back (same id and times). If the app dies first, the next start's `LeftoverMediaCleaner` removes the folder.
5. **Thumbnail:** write `thumbnail_path` as a square JPEG of the first main clip's first frame (192 px, from the existing `ThumbnailReader`) in the draft's folder: at the first save (P1-02 import) and when the editor closes or goes to the background if the first clip or its trim-in changed. The cleaner already keeps it.
6. **Relative time:** the rules in Copy (now, minutes, hours, yesterday, days, then a short localized date), refreshed on resume and every minute. The short date comes from the platform formatter (expect/actual in `:platform:services`).
7. **Debug drafts:** a hand-check hook that saves 10 drafts with spread-out times, one unnamed, one with a long name, one with `schema_version` above the current, one whose media folder is missing.
8. **Token and icon:** `MemixSize.listThumbSize` (56 dp, `design/tokens.json`); icon `more`.

## Proposals and questions for the PM
Questions 1–3 answered (PM, 20 Oct; decision log, TICKETS P1-14 Done when and note); 4 and 5 need no answer. Kept for the record.
1. **Answered: no.** Opening or renaming isn't an edit; a rename writes the name without touching `updatedAtEpochUs`. **"Sorts by last edited": does a rename count as an edit?** I propose no (Order), so a renamed row stays put. Please confirm at the ready check.
2. **Answered: directly above its original** (original's time + 1 µs; ties go to the newer copy, Order). **Where a duplicate goes:** directly above its original rather than at the top. It's in view, and it still sorts by its own edit time from its first edit on.
3. **Answered:** duplicating fires no `project_create` and no other event; no `draft_action` event in v1. **Analytics:** no event for duplicate, rename or delete; duplicate doesn't count as `project_create`. If draft management matters for the north star, a `draft_action {action}` event would be a PRD change (not recommended for v1).
4. **No answer needed** (PM: one list matches the PRD's Drafts row). **One list, no Video / Photo switch.** The SegmentedTabs README named Drafts as a use from P0; the PRD's Drafts is "projects saved on this phone, type badge, last edited". Recency matters more than type when resuming, and the badge tells them apart. I've removed Drafts from the SegmentedTabs README; revisit if photo drafts dominate after P2.
5. **Not raised** (PM, 20 Oct; ideas for later). **Later (no ticket):** a cover that shows the meme as rendered (captions, canvas background) instead of the first video frame, and selecting several drafts to delete at once.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Undo beats confirmation for reversible actions; warnings get clicked through by habit | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| Temporary messages with controls should stay at least as long as the user's "Time to take action" setting; Compose exposes `calculateRecommendedTimeoutMillis` with a `containsControls` flag | Guidance | [Compose AccessibilityManager](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/AccessibilityManager), [WCAG 2.2 SC 2.2.1](https://www.w3.org/WAI/WCAG22/Understanding/timing-adjustable.html) |
| Lost drafts are a recurring complaint about mobile editors | Evidence (anecdotal reviews) | [VN on the App Store](https://apps.apple.com/us/app/vn-video-editor/id1343581380), [InShot draft complaint](https://www.sikayetvar.com/en/inshot-us/inshot-deleted-my-pro-project-draft-after-hours-of-work) |
| Mobile video editors list drafts as rows with a thumbnail, name, date and a "more" menu (rename, duplicate, delete) | Assumption (product knowledge of CapCut and VN; not confirmed from their help pages) | — |
