# WhatsApp sticker pack · P2-09

> **Draft for build, 5 Nov 2026.** Ready check: `docs/pm/acceptance-log.md` (5 Nov, P2-09). Delegated: no watermark on stickers (1A).
> **WhatsApp's spec re-read on 5 Nov** from the [official Android README](https://github.com/WhatsApp/stickers/blob/main/Android/README.md); it matches the PM's reading, with three additions used below (the "is added" check, WhatsApp Business, and the accessibility text). The engineer re-verifies at the start of the build.

Builds on `P2-14-sticker-picker.md` (the sticker store and the Stickers sheet where this starts), `P2-07-background-removal.md` (where stickers are made) and `P1-14-drafts.md` (the name field, the row Menu and the Undo toast).

## Job
"Turn my cut-outs into a WhatsApp sticker pack": choose 3 to 30 stickers, name the pack, tap one button, confirm in WhatsApp. Free, on the phone, nothing uploaded; WhatsApp copies the files itself when the user confirms.

**Done when** (TICKETS): a pack installs into WhatsApp on a test phone.

**The one blue action:** *Add to WhatsApp* on the builder; *Done* on the result; *New pack* on the packs list.

**Fast path, with tap counts:** Stickers (1), WhatsApp packs (2), pick the stickers (3 taps for a 3-sticker pack), Add to WhatsApp, then WhatsApp's own confirm. Name and emojis have defaults, so nothing else is required.

## Entry and exit
- **Entry:** the Stickers sheet (P2-14) gets one row at the top, shown whenever the store has a sticker: **WhatsApp packs** (see Layout). It opens the **Packs** screen, or the **builder** straight away when no pack exists yet. P2-14's empty state is unchanged (no stickers, no row).
- **Exit:** × on any screen and system back go one step back; **Done** on the result goes back to the editor (the sheet is closed).
- **A pack is only built from stickers already in My stickers** (made by Save as sticker, P2-07). The pack keeps its own prepared files, so deleting a sticker from My stickers never breaks a pack (P2-14's copy rule).
- **Proposal, not built here:** an entry from Home or the Create sheet, so a pack doesn't need an open photo (Proposal 1).

## Screens

### Stickers sheet row (an edit to P2-14)
Above the grid, full width, `space-4` side padding: a `surface-raised` row, `radius-md`, 56 dp tall, the whole row one target. Start: the `sticker` icon (24, `text`). Body: "WhatsApp packs" in `body-strong` `text`, and under it in `label` `text-secondary`: "Make a pack from your stickers" (no pack yet), or "2 packs" (packs exist), or "Add 2 more stickers to make a pack" (fewer than 3 stickers and no pack). End: the `chevron_right` icon. Pressed: `hairline`. The row stays when there are fewer than 3 stickers; it explains (below).

### Packs screen
Full screen on `canvas`; only shown when at least one pack exists.
- **Top:** title "WHATSAPP PACKS" in `display`, × at the start.
- **List:** one row per pack, 72 dp tall, `surface` rows with `hairline` dividers (the Drafts list look, P1-14): the tray sticker 48 dp (`radius-sm`, on `surface-raised`), the name in `body-strong` `text` (two lines at most), and under it in `label` `text-secondary` "12 stickers" plus, when WhatsApp reports it, "In WhatsApp". Newest first.
- **Delete:** long-press a row opens a Menu with **Delete**; a toast with Undo for 8 s says "Pack deleted. It stays in WhatsApp until you remove it there." (P1-14's pattern; the pack's files go when the toast ends). TalkBack gets the same as an action on every row.
- **New pack:** the one Primary button, full width at the bottom (thumb reach). At 10 packs it explains on tap ("You have 10 packs. Delete one to make another.").
- A tap on a row opens the builder with that pack's stickers, name and emojis (editing). **A sticker that was deleted from My stickers stays in the pack, selected, shown from the pack's own prepared copy** (deleting from My stickers never breaks a pack); the user can unselect it. It is never dropped silently.
- **When the last pack is deleted on this screen (review):** the screen stays, with an empty state, until the user leaves or taps New pack: "No packs yet" (`packs_empty_title`, budget 30) and "Make a pack from your stickers." (`packs_empty_body`, 60), with the New pack button; Undo within 8 s brings the pack back. The screen is skipped on entry only when no pack exists (it opens the builder).
- **Back and Done (review):** × and system back from the first screen (Packs, or the builder when there is no pack) and Done on the result return to the editor with the Stickers sheet closed (accepted: one tap to reopen it).

### Builder screen
Full screen on `canvas`.
- **Top:** title "NEW PACK" (or "EDIT PACK") in `display`, × at the start.
- **Name:** heading "Pack name" (`label` `text-secondary`), then the single-line field in P1-10's look (`surface-raised`, `radius-md`, `space-3` padding, `touch-target` tall, `body` `text`), prefilled "My pack", cap 30 characters, counter in `caption` `text-muted` from 25 on. Helper under it in `caption` `text-secondary`: "Use pictures you have the right to share." (WhatsApp requires legal, authorized stickers.) An empty name falls back to "My pack" when the pack is added, so the name is never an error.
- **Grid:** the user's stickers, the P2-14 grid and tile (3 columns at 360 dp, `space-2` gaps, `surface-raised` tiles, fitted inside), newest first. **Tap toggles.** Selected: the tile gets the `stroke-selection` white ring (2 dp, outside the tile, like the swatch) and a white `check` disc in the corner with `on-primary` mark (24 dp visual; the whole tile is the 96 dp target). No tint over the picture. The first selected sticker in grid order is the pack's tray icon; the Emojis sheet and the Packs row show it, and no label is added to the grid.
- **Counter line**, above the bottom bar, `body` `text-secondary`: "Pick at least 3 stickers" (under 3), "5 selected" (3 to 30), "30 selected. That's the most a pack holds." (at 30). A polite live region. A 31st tap changes nothing and says "A pack holds up to 30 stickers." in a toast.
- **Bottom bar** (pinned, `surface`, `space-4` padding): **Emojis** (Secondary, one third) and **Add to WhatsApp** (Primary, two thirds), `space-3` apart; stacked at 200% font, Emojis above Add. Both stay enabled-looking; under 3 selected, Add explains on tap ("Pick at least 3 stickers.").
- **Large font (review P2-09):** the **notice** (too detailed, not added, no WhatsApp) is not part of the pinned bottom bar: it sits **in the scrolling content, directly above the counter**, so the pinned part is only the counter and the two buttons (stacked at 200%). If the pinned part would take more than 40% of the window, the counter scrolls with the content too and only the buttons stay pinned. The grid always keeps at least a row and a half in view.
- **Fewer than 3 stickers in the store** (the Packs entry): in place of the grid, centered: the `sticker` icon (32, `text-secondary`), "You need 3 stickers for a pack", "You have 1. Cut out more people, pets or objects from your photos and save them as stickers." and the P2-14 action (Remove background as Primary when the canvas has an image layer, else Add photo as Secondary). The name field and bottom bar are hidden.
- **A sticker that doesn't fit WhatsApp's size** (after Add is tapped, see Rules): the tile shows a small `danger` disc with "!" (24 dp, `on-primary`; spoken "Too detailed for WhatsApp"), and a notice above the counter: "1 sticker is too detailed for WhatsApp's size limit. Unselect it or pick another." The notice clears when the selection changes.
- **While preparing** (after Add): the bottom bar becomes text "Getting your pack ready · 12 of 30" in `body` `text-secondary` over a determinate ProgressBar; the grid goes inert; × cancels with no confirmation (nothing was changed in WhatsApp). With few stickers it is over in a second; no looping bar (MOTION 2).

### Emojis sheet
The **Emojis** button opens a Sheet with two panes in the one sheet.
- **List pane:** title "Emojis" (`title`), a row per selected sticker (64 dp): its thumbnail (48), its emojis side by side as text (up to three, 24 dp glyphs), a `chevron_right`. Tap a row for the picker. "Done" (Primary) at the bottom closes the sheet.
- **Picker pane:** title "Emojis for this sticker" with a back arrow at the start (`arrow_back`, 48). A grid of **48 emojis** (`design/tokens.json` → `photo.stickerPack.emojiChoices`, 6 columns of 48 dp targets, no search), the sticker's current ones selected (`selection` fill with the emoji on it). Tap toggles; **1 to 3** (a fourth: toast "Up to 3 emojis per sticker."; unselecting the last: "Pick at least 1 emoji."). Changes apply at once; back returns to the list.
- **Default:** every sticker starts with 🙂, so the fast path needs no emoji step. WhatsApp uses emojis to suggest stickers, so a pack that others will use is better with its own (said once in the sheet: "Emojis help people find your stickers in WhatsApp." in `caption` `text-secondary` under the title).
- TalkBack speaks emojis in the phone's language by itself, so the tiles carry no strings (device check).

### Result screen ("Added")
Shown when WhatsApp reports the pack added (below). Title "ADDED" in `display`; the tray sticker (96 dp on `surface-raised`, `radius-lg`); a `success` pill with `check`: "Added to WhatsApp"; one body paragraph in `text-secondary`: "Open a chat in WhatsApp, tap the sticker icon, and look for “%1$s”." (the pack name); **Done** (Primary, bottom). Reduce-motion: no motion beyond the push.

## Rules

### What WhatsApp needs (from the README)
- **Stickers:** static WebP, **exactly 512 × 512**, transparent, **100 KB or less**; 3 to 30 per pack; a pack is all static or all animated (animated packs, 500 KB, are not in v1). **Tray icon:** static 96 × 96, 50 KB or less: the first sticker of the pack, scaled. 1 to 10 packs per app. 1 to 3 emojis per sticker.
- **Fitting:** a sticker is scaled to fit inside 512 × 512 and centered on transparent padding; WebP quality is searched down until the file is 100 KB or less. If it can't reach that at a quality the picture still looks right at (the engineer sets the floor, about 40; Assumption), the sticker is reported (the "!" disc above) and the pack isn't added until the user fixes the selection.
- **Pack data:** the name (≤ 30 in the UI), the publisher "Memix" (fixed in v1; no author field; Proposal 2), a never-changing identifier, `image_data_version` bumped on every change (stickers, emojis, name, tray), each sticker's emojis, optionally accessibility text.
- **Adding:** the intent `com.whatsapp.intent.action.ENABLE_STICKER_PACK` with `sticker_pack_id`, `sticker_pack_authority`, `sticker_pack_name`. One pack per button, never "add all". WhatsApp shows its own confirm dialog; the user must accept there.
- **Both apps:** if WhatsApp and WhatsApp Business are installed, Android's own chooser appears for the intent; no Memix UI for it. Afterwards the "is added" check runs against both.
- **Knowing it worked:** WhatsApp documents no result code, so after returning Memix asks WhatsApp's own check (`is_whitelisted`: 1 added, 0 not, none = too old or not installed) for this pack, in both apps. Memix can only check its own packs.
- **Names:** "WhatsApp" is in the button and the help text (fine inside the app); the app's name and Play title never contain it, and the Play description includes "WAStickerApps" (flag to P6-03).

### Updating a pack already in WhatsApp
Edits bump `image_data_version`; the user re-adds with the same button, which reads **Update in WhatsApp** for a pack that was added before. WhatsApp refreshes it on the version change.

### Privacy
The pack is served by a provider only WhatsApp may read. Nothing goes to any server from Memix; once the user accepts, WhatsApp keeps its own copy and friends receive stickers as WhatsApp sends them. A one-line note on the builder is not needed; the Settings privacy page (P6) names it.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Row, no pack | "Make a pack from your stickers" | `packs_row_title`, `packs_row_make` |
| Row, packs | "2 packs" | `packs_row_count` |
| Row, under 3 stickers | "Add 2 more stickers to make a pack"; it opens the explanation state | `packs_row_need` |
| Packs screen | List, New pack | `packs_title`, `packs_new` |
| Builder, under 3 selected | Counter "Pick at least 3 stickers"; Add explains | `pack_count_need`, `explain_pack_min` |
| Builder, 3 to 30 | "5 selected" | `pack_count` |
| At 30 | Counter note; a 31st tap toasts | `pack_count_max`, `explain_pack_max` |
| Emojis | Two panes | `pack_emojis_*` |
| Preparing | Progress text and bar; inert | `pack_preparing` |
| Too detailed | "!" disc on the tile, notice | `pack_too_big`, `a11y_pack_too_big` |
| WhatsApp opens | WhatsApp's confirm over Memix | system |
| Added | Result screen | `pack_added_*` |
| Not added (declined or backed out) | Back on the builder, a calm notice | `pack_not_added` |
| WhatsApp isn't installed | Notice on tap of Add; the pack is saved and can be added later | `pack_no_whatsapp` |
| WhatsApp too old | Notice | `pack_whatsapp_old` |
| Deleted pack | Row leaves; toast with Undo | `packs_deleted`, `packs_undo` |
| 10 packs | New pack explains | `explain_packs_max` |
| Can't write (no space) | Toast, nothing changes | `pack_save_failed` |
| A sticker's source file is missing | The tile shows the `photo` icon in `text-muted`; it can't be selected; the notice says why on tap | `stickers_file_missing` (P2-14) |
| Long content (5 languages, 200%) | Name and notices wrap; the bottom bar stacks; the grid scrolls | none |
| Offline | No difference | none |

**"Not added" is never an error:** the user may have tapped Cancel in WhatsApp. The notice reads "It's not in WhatsApp yet. Tap Add to WhatsApp when you're ready." and Add stays the primary.

## Interactions
- **Tile:** tap toggles (Selection haptic is not used; nothing buzzes). **Name:** keyboard, Done/IME action closes it. **Add to WhatsApp:** prepares, then opens WhatsApp. **Return from WhatsApp:** the check runs at once and the screen shows Added or the "not added" notice.
- **Back** at any step goes one step back and never discards selections made on the builder in this visit (kept in the ViewModel until the screen closes); a closed builder keeps nothing but saved packs. A pack is saved the moment Add succeeds in preparing (so "Update" works later), not before.

## Motion
Screen push 320 ms; sheet 200 ms; tile selection ring and check fade 120 ms; a deleted row leaves in 200 ms. Reduce motion: push becomes a 120 ms fade; everything else instant.

## Copy
English source; machine drafts until P5. Budgets in characters.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `packs_row_title` | WhatsApp packs | 20 | "WhatsApp" never translates |
| `packs_row_make` | Make a pack from your stickers | 40 | |
| `packs_row_count` | %1$d packs | 14 | Plural-aware (one, other) |
| `packs_row_need` | Add %1$d more stickers to make a pack | 50 | Plural-aware |
| `packs_title` | WhatsApp packs | 20 | Uppercase in Anton (Teko in Hindi) |
| `packs_new` | New pack | 14 | Primary |
| `packs_sticker_count` | %1$d stickers | 14 | Plural-aware; row meta |
| `packs_in_whatsapp` | In WhatsApp | 16 | Row meta when WhatsApp reports it |
| `packs_deleted` | Pack deleted. It stays in WhatsApp until you remove it there. | 80 | Toast |
| `packs_undo` | Undo | 10 | Reuse the common label |
| `explain_packs_max` | You have 10 packs. Delete one to make another. | 60 | |
| `pack_builder_new` | New pack | 14 | Uppercase in Anton |
| `pack_builder_edit` | Edit pack | 14 | Uppercase in Anton |
| `pack_name_label` | Pack name | 20 | |
| `pack_name_default` | My pack | 20 | The prefilled name |
| `pack_name_helper` | Use pictures you have the right to share. | 50 | |
| `pack_count_need` | Pick at least 3 stickers | 40 | |
| `pack_count` | %1$d selected | 20 | Plural-aware |
| `pack_count_max` | %1$d selected. That's the most a pack holds. | 50 | |
| `explain_pack_min` | Pick at least 3 stickers. | 40 | Toast |
| `explain_pack_max` | A pack holds up to 30 stickers. | 50 | Toast |
| `pack_need_title` | You need 3 stickers for a pack | 40 | |
| `pack_need_body` | You have %1$d. Cut out more people, pets or objects from your photos and save them as stickers. | 120 | Plural-aware |
| `pack_emojis` | Emojis | 14 | The Secondary button and the sheet title |
| `pack_emojis_help` | Emojis help people find your stickers in WhatsApp. | 60 | |
| `pack_emojis_pick_title` | Emojis for this sticker | 30 | |
| `pack_emojis_done` | Done | 12 | Reuse `export_done` |
| `explain_pack_emoji_max` | Up to 3 emojis per sticker. | 40 | Toast |
| `explain_pack_emoji_min` | Pick at least 1 emoji. | 40 | Toast |
| `pack_add` | Add to WhatsApp | 20 | Primary. "WhatsApp" never translates |
| `pack_update` | Update in WhatsApp | 22 | Primary for a pack added before |
| `pack_preparing` | Getting your pack ready · %1$d of %2$d | 50 | |
| `pack_too_big` | %1$d sticker is too detailed for WhatsApp's size limit. Unselect it or pick another. | 100 | Plural-aware |
| `a11y_pack_too_big` | Too detailed for WhatsApp | 30 | Spoken name of the "!" disc |
| `pack_added_title` | Added | 12 | Uppercase in Anton |
| `pack_added_pill` | Added to WhatsApp | 24 | |
| `pack_added_body` | Open a chat in WhatsApp, tap the sticker icon, and look for “%1$s”. | 100 | %1$s = the pack's name |
| `pack_not_added` | It's not in WhatsApp yet. Tap Add to WhatsApp when you're ready. | 80 | |
| `pack_no_whatsapp` | WhatsApp isn't on this phone. Your pack is saved: add it once WhatsApp is installed. | 100 | |
| `pack_whatsapp_old` | Your WhatsApp is too old for stickers. Update it, then try again. | 90 | |
| `pack_save_failed` | Couldn't make the pack. Free up space and try again. | 70 | Toast |
| `a11y_pack_tile_selected` | %1$s, selected | 30 | Tile state; %1$s = "Sticker 3" |

Translator notes: "Sticker" is the word WhatsApp uses in your language. "Pack" is a set of stickers (WhatsApp's word for it). "WhatsApp" never translates. Emojis are not translated and need no strings. Avoid any wording that suggests Memix is made by WhatsApp.

## Accessibility
- **Packs and builder:** each screen's title is the pane title; focus lands on it. The tile grid is a set of toggle buttons ("Sticker 3, selected"); the counter is a polite live region ("5 selected"); the "!" disc is part of the tile's spoken state. Order: ×, name, grid, notice, counter, Emojis, Add.
- **Emojis sheet:** rows read "Sticker 3, emojis 🙂, double-tap to change"; the picker's tiles are toggles read by TalkBack's own emoji names.
- **Preparing:** the progress is a range with "12 of 30"; milestones announced politely at most every 25%.
- **Result:** focus on the title; the pill is a merged live node.
- **Touch targets:** all 48 dp or more; tiles 96 dp.
- **Contrast** (reused pairs): `text` on `surface-raised` 12.4:1; `text-secondary` on `canvas` 7.1:1 or better; `on-primary` on `primary` 7.9:1; the check disc `on-primary` on white 18.9:1; the "!" disc `on-primary` on `danger` 5.7:1.
- **Drag:** none; everything is a tap (SC 2.5.7).
- **200% font:** name and counter wrap, the bottom bar stacks (Emojis above Add), rows grow to three lines, the result text wraps and scrolls.
- **Reduce motion:** see Motion.

## Analytics
Per the PM's ready check; `tool_use` carries `editor: "photo"`.
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | The builder opens | `tool: "make_pack"` |
| `tool_use` | Add or Update in WhatsApp is tapped and the pack is ready | `tool: "add_to_whatsapp"` |
| `share_target` | The check says the pack is in WhatsApp | `target: "whatsapp"` |

No event for pack count, naming or emoji choices (not in the PRD). Analytics stay off until consent (P5-01).

## QA compares
1. **The Stickers sheet** at 360 × 640 with 0, 2 and 5 stickers: the WhatsApp packs row (absent, "Add 1 more…", "Make a pack…").
2. **Builder:** empty selection, 3 selected, 30 selected, a 31st tap; the name field with 30 characters; the tray note.
3. **Emojis sheet:** list and picker, the fourth emoji, the last unselected.
4. **Preparing** with 30 stickers; **Too detailed** (a debug sticker of noise).
5. **WhatsApp on the owner's phone (the Done when):** Add to WhatsApp, WhatsApp's confirm, the Added screen, then the pack in WhatsApp's sticker tray with the tray icon and the emojis; send one sticker. Then the same on WhatsApp Business, and with both installed (the chooser).
6. **Not added:** Cancel in WhatsApp; then re-add. **Not installed** (the emulator has no WhatsApp). **Update:** change a sticker, Update in WhatsApp, the tray refreshes.
7. **Hindi at 200% font:** the builder with the stacked bar, the notices and the result.
8. **Packs list** with 3 packs; delete and Undo; 10 packs and New pack.

**Measured:** for a 3-sticker and a 30-sticker pack, unzip the prepared files from app storage (`adb run-as` on the benchmark build): every sticker is WebP, exactly 512 × 512, with alpha, 100 KB or less (`webpinfo`/`identify`); the tray is 96 × 96, 50 KB or less; `contents.json` has the identifier, a version that went up after an edit, 1 to 3 emojis each; a pack can't hold fewer than 3 or more than 30. Time to prepare a 30-sticker pack on the owner's phone (log; target under 5 s, Assumption). LeakCanary after five builds. The provider is exported with the read permission and nothing else can read it (`adb shell content query` from the shell fails).

## Requests to the principal mobile engineer
1. **Provider:** the ContentProvider as WhatsApp requires (`android:exported="true"`, `android:enabled="true"`, `readPermission="com.whatsapp.sticker.READ"`, the four paths), in `:platform:services` and the manifest; files from app storage; nothing uploaded. Test package visibility on Android 11 and up (the README names no `<queries>`; add `com.whatsapp` and `com.whatsapp.w4b` if the check needs it).
2. **Encoder** in `:engine:photo`: fit to 512 × 512, WebP with alpha (verify `WEBP_LOSSY` is API 30+; below it, `WEBP` and quality semantics differ: check on the lowest API we ship), search quality down to the floor, report a sticker that can't fit. Tray at 96 × 96.
3. **Pack store** (SQLDelight table or files; a `.sqm` migration and `verifySqlDelightMigration` if SQL): id, name, tray, stickers in order with emojis and prepared file names, `imageDataVersion`, `addedToWhatsAppAt`; at most 10 packs; delete with the Undo window.
4. **Constants** from `design/tokens.json` → `photo.stickerPack` in one commonMain place with a comment pointing here; the emoji list is data, not strings.
5. **Add flow:** the intent with `startActivityForResult`; on return call the `is_whitelisted` check for both authorities (`com.whatsapp.provider.sticker_whitelist_check`, `com.whatsapp.w4b.provider.sticker_whitelist_check`); handle `ActivityNotFoundException`.
6. **Screens, strings (en, id, es, pt, hi), analytics** as above; plural-aware counts. Optional: set the sticker's accessibility text (125 characters) to the pack name plus the sticker's number.
7. **The Stickers sheet row** (P2-14 edit) and the entry from the empty case.
8. **R8:** the provider and its permission survive minification; check with `bundleRelease` and on the benchmark build. **iOS:** differs, P7-09; a marked stub.
9. **Flags:** the Play listing text for P6-03 must avoid "WhatsApp" in the app name and include "WAStickerApps".

## Proposals and questions for the PM
1. **An entry outside the editor** (Home or the Create sheet: "Make a sticker pack") so the PRD job "Make a cut-out sticker" doesn't need an open photo project. Today it takes: open a photo, Stickers, WhatsApp packs. *Recommendation applied: build only the Stickers sheet row now; the PM weighs a Home entry for Phase 3 (P0-05 change, one row).*
2. **Publisher name:** fixed "Memix" in v1, no field (one less step, and the name travels with every pack as a quiet credit). *Recommendation applied; reversible with one field later.*
3. **Pack order = My stickers order, no manual reorder, no tray choice** in v1 (Later, no ticket). Cost of adding both: a drag-reorder screen with no non-drag alternative to design.

**Delegated owner decisions (applied, for review after Phase 2):**
- **Publisher is "Memix", not user-entered.**
- **A one-line legal helper under the pack name** ("Use pictures you have the right to share."), because WhatsApp requires legal, authorized stickers.
- **Default emoji 🙂 for every sticker** with an optional Emojis sheet, instead of a mandatory emoji step.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| 512 × 512 static WebP, 100 KB; animated 500 KB; tray 96 × 96, 50 KB; 3 to 30 stickers; 1 to 10 packs; 1 to 3 emojis; contents.json fields and limits; provider paths and permission | Official platform docs (read 5 Nov 2026) | [WhatsApp Stickers, Android README](https://github.com/WhatsApp/stickers/blob/main/Android/README.md) |
| The add intent, one pack per button, never "add all", and the `is_whitelisted` check (1 added, 0 not, null = too old); WhatsApp Business has its own authority | Official platform docs | same |
| App and Play listing names must not contain "WhatsApp"; description contains "WAStickerApps" | Official platform docs | same |
| No result code is documented for the intent, so Memix asks the check | Official platform docs (absence of one; the engineer confirms on a phone) | same |
| TalkBack reads emojis in the phone's language | Assumption (device check) | none |
| Most users want a pack without a naming and emoji ritual: defaults first | Assumption | none |
