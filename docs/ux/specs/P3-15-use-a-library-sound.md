# Use a library sound: start a new video meme with it · P3-15

Part of the Phase 3 sound specs. Read first, and not repeated here:
- `P3-02-sounds-library.md`: the row, the blue Use block, the Sounds tab (its "Use block" decision, Question 1 A, approved 11 Nov).
- `P3-03-sound-preview-and-details.md`: the details sheet and its Primary, "Use in a video meme".
- `P3-04-download-states.md`: the row's Downloading, Failed, Offline and Unavailable looks.
- `P1-02-gallery-picker.md`: the system picker, the import sheet and every result of "pick videos and photos". This flow **is** that flow, carrying one pending sound.
- `P1-06-clip-edits-and-audio.md` → Meme sounds → Landing, and `P3-05-sound-picker-sheet.md` → Adding (the overhang rule).

No board shows it. No new screen: it adds a step before an existing picker and a state to an existing arrival.

## Job
"I heard a sound I like; put it on a new meme." From a browse-only list to a meme in three taps, so the library has a way into the editor (Sound first). **Done when** (TICKETS): from the Sounds tab, a sound is in the editor at 0:00 on the meme sound track after Use and picking one video; offline, a sound that is not on the phone shows no Use block; `sound_add` fires with `editor: video` and the row's `tab`, and `project_create` with `source: gallery`.

**The one blue action:** the Use block (meme-sound UI, blue by the system's rule) and the details sheet's Primary. Nothing new is blue.

**Tap count:** Sounds tab → **Use** (1) → a clip in the system picker (2) → the picker's Add (3) → the editor, with the sound already on the timeline. The same 3 taps as P1-02's Home → Video meme path, with the sound for free. (From the details sheet it is the sheet's Primary instead of the block: one more tap to open the sheet.)

## Entry points
1. **The Use block** on a Sounds-tab row, in every tab and in search results (P3-02).
2. **Use in a video meme**, the Primary of the details sheet (P3-03), which closes the sheet and starts the same flow.
3. **The Home "Trending sounds" row's add action** (P3-14). That row's own spec says what it looks like; its action is exactly this flow, and it needs the same one block. P3-15 is built first (TICKETS).

All three end in the same code path: a pending sound id, then P1-02's picker. The picker, its settings (images and videos, up to 35, ordered selection), the copy, the import sheet and every result are P1-02's and are not changed.

## The flow
```
Use tapped ── sound on the phone ────────────────────────> system picker
           └─ not on the phone, online ─ download ─ ok ──> system picker
                                              └─ fail ──> stays on the row, Failed look
picker: nothing picked / back ──> where you were (the Sounds tab or the details sheet's list), nothing changed
picker: 1 to 35 picked ──> Memix copies them (P1-02) ──> editor, sound at 0:00
```
- **Before the picker:** if the sound is already on the phone (it was played, it is in the starter pack, or it is a My sound), the picker opens at once: no screen, no explainer, no permission rationale (P1-02). A tap on any Use control is ignored while a picker is launching.
- **Sound not on the phone, online:** the tap starts the download (P3-04: "Use block while not saved"). Under 300 ms nothing shows and the picker opens. Over 300 ms the row shows **Downloading** (disc becomes Cancel, meta "Downloading…", the rail); the Use block is in its disabled look, still focusable. In the details sheet the Primary's label becomes "Downloading…" and ignores taps (the P3-10 "Sending…" pattern); the sheet closes when the picker opens. **Cancel** on the disc, or leaving the screen, stops the download; nothing is kept.
- **Download fails:** the user stays where they are. The row shows the Failed look and a Retry disc ("Couldn't download. Tap to try again."); a tap on the Use block retries and, on success, carries on to the picker (P3-04). Phone storage full: the "Not enough space" look and strip or toast of P3-04.
- **Offline and not on the phone:** **no Use block is drawn** (TICKETS), its 48 dp slot stays empty so names do not jump when the connection returns, and the meta says "Not saved for offline". The details sheet hides its Primary and says why (P3-03). **P3-04's table is updated:** its "Disabled" Add/Use block cells for *offline, not saved* and *removed from the catalog* now read "not drawn". The Downloading and Failed looks keep the disabled or active block they had.
- **A My sound** (P3-07) is always on the phone, so Use goes straight to the picker.
- **Picker cancelled** (back, swipe down, nothing picked): the Sounds tab is exactly as it was (tab, scroll, search, the row idle). No toast, no event, no project. The downloaded file stays cached; it is the user's saved copy now.
- **Picker confirmed:** P1-02 takes over. Copying under 300 ms goes straight to the editor; longer shows the import sheet ("Adding your media"); results are P1-02's ("Some didn't go in": Continue goes to the editor **with the sound**; "None went in": Pick again keeps the pending sound, Close drops it and leaves no project). Cancel while copying drops the pick, the files and the pending sound. A pick never creates a project with zero items.
- **The user presses back or closes the details sheet while the picker is up:** not possible; the picker is on top. If Android stops Memix while the picker is open on a low-memory phone, the result still arrives on restart (P1-02) and the **pending sound id comes back with it** (it is kept in the saved state). If it was lost, see "Sound missing" below.

## Arriving in the editor
The editor opens as in P1-02 (a 320 ms push; with reduce motion, a 120 ms fade), with the picked clips back to back from 0:00.
- **The sound:** the whole sound (trim in 0, trim out = its length), at **0:00**, on the first meme-sound lane, at 100% volume. The P1-06 landing rule for "the end of the video" does not apply (the playhead is at 0:00).
- **The playhead** is at 0:00 and **nothing is selected**: the main tool bar shows (Edit, Meme sounds, Text, Add media). The sound is not selected, because selecting it would swap the bar for the sound's clip tools and a first-time user would lose the main tools; the next likely move is Play. (This differs from landing in an open editor, where the new sound is selected: there the user is mid-edit and wants its tools.)
- **Feedback once the push has finished:** the sound's clip does the bonk (`duration-bonk`, 240 ms; with reduce motion a 120 ms fade-in), the phone gives one Confirm haptic ("a sound landed"), and TalkBack hears `a11y_sound_added` ("Bruh added at 0 seconds") after the screen's own announcement. No toast on the normal path.
- **Undo:** the editor's Undo is empty on arrival, as after P1-02: the sound is part of the project's first state, not an edit. Deleting it takes Select, Delete (then Undo works).
- **The sound plays over the clips' own sound.** Nothing is muted for the user (their volume tools are one tap away; changing a user's mix unasked is worse than a loud first play).
- **Sound longer than the picked media** (a 5 s sound on a single 3 s photo, or a 2 s clip): the part past the video's end is not heard and shows the past-the-end wash. If the unheard part is over 0.5 s, the 4 s toast `picker_overhang_toast` says to trim it or move it earlier, in the stage slot, after the push (Questions 1). The project is not lengthened.
- **Sound missing** (the pending id was lost with the process, or the cached file is gone): the editor opens with the picked media and no sound, and a 4 s toast says `use_sound_missing_toast`. The engineer keeps the cached file pinned from the tap to the landing so the second case should never happen (Requests).
- **Closing the editor** goes back to the Sounds tab (or Home), not to a Create sheet (P1-02). The draft stays.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Use, sound on the phone | The picker opens at once | none |
| Use, not saved, online, quick (under 300 ms) | No visible wait; the picker opens | none |
| Use, not saved, online, slow (over 300 ms) | Row Downloading look (or the Primary reads "Downloading…"); Use block disabled look | `sound_dl_downloading` |
| Download failed | Row Failed look, Retry disc; the Use block retries and continues | `sound_dl_failed` |
| Phone storage full | P3-04's look and its Free up space action | `sound_dl_failed_storage`, `sound_dl_storage_toast`, `sound_dl_free_up_space` |
| Offline, not saved | No Use block; meta "Not saved for offline"; details Primary hidden with its line | `sound_not_saved_offline`, `sound_detail_offline_note` |
| Removed from the catalog (a favorite or recent) | No Use block; meta "No longer available" | `sound_unavailable` |
| Picker open, cancelled | The Sounds tab unchanged; no message | none |
| Copying, results | P1-02's states, unchanged | `import_*` |
| Arrived, normal | Editor, sound clip bonks once at 0:00, nothing selected | `a11y_sound_added` |
| Arrived, sound runs past the end | As above plus wash on the overhang, toast 4 s | `picker_overhang_toast` |
| Arrived, sound missing | Editor without the sound; toast 4 s | `use_sound_missing_toast` |
| Permission | None needed (the picker has none; a sound download needs none) | none |
| Long content, 5 languages, 200% font | The row and sheet rules of P3-02 and P3-03. The Primary label wraps to two lines; names two lines then an ellipsis | none |
| First run | No coach marks; the Use block is the same blue block as everywhere | none |

## Interactions
- **Tap the Use block, or the sheet's Primary:** the flow above. No haptic on the tap (the Confirm haptic belongs to the landing).
- **Stopping a preview:** a playing preview stops when Use is tapped (the picker takes the screen; leaving the app stops previews anyway).
- **Escape / back:** nothing to do on the block. Back inside the picker returns to the tab.
- The block is an icon only (`create` in `on-primary`, as P3-02 decided to keep the row narrow). Its meaning is carried by the spoken name and by the details sheet's labelled button. If the first user test (P3-02's script, task 4) shows people read it as "favorite" or "add to a list", the fix is a one-word label ("Use") on the block at the cost of about 40 dp of the text column: a one-component change.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Row states (Downloading, Failed) | 120 ms (`duration-press`) | same |
| Details Primary label to "Downloading…" | 120 ms cross-fade | same |
| Editor arrival | 320 ms push (P0-05) | 120 ms fade |
| Sound clip lands | the bonk, 240 ms, after the push | 120 ms fade-in |
| Toast | 120 ms fade | same |

## Copy
English source; id, es, pt and hi are machine drafts (P5-06 has the final say). This flow reuses P3-02, P3-03, P3-04 and P1-02 strings; one new string, plus the two shared with P3-05 that this spec needs.

| Key | English | Budget | id | es | pt | hi |
| --- | --- | --- | --- | --- | --- | --- |
| `sound_a11y_use` | Use {name} in a new video meme | 56 + name | Pakai {name} di meme video baru | Usar {name} en un meme de video nuevo | Usar {name} em um novo meme de vídeo | {name} को नए वीडियो मीम में इस्तेमाल करें |
| `sound_detail_use` | Use in a video meme | 28 | Pakai di meme video | Usar en un meme de video | Usar em um meme de vídeo | वीडियो मीम में इस्तेमाल करें |
| `use_sound_missing_toast` | The sound didn't come along. Add it from Meme sounds. | 70 | Suaranya tidak ikut. Tambahkan dari Suara meme. | El sonido no se incluyó. Añádelo desde Sonidos de memes. | O som não veio junto. Adicione-o em Sons de memes. | आवाज़ साथ नहीं आई। इसे मीम साउंड से जोड़ें। |
| `a11y_sound_added_unnamed` | Sound added at {time} (spoken only, when the sound's name isn't known) | 40 | Suara ditambahkan di {time} | Sonido añadido en {time} | Som adicionado em {time} | आवाज़ {time} पर जोड़ी गई |
| `picker_overhang_toast` | (defined in `P3-05-sound-picker-sheet.md`; reused on arrival) | 110 | | | | |

`sound_a11y_use` and `sound_detail_use` already exist in English (P3-02, P3-03); the translations are new here. Notes for translators: "meme" stays as people say it in that language; "Meme sounds" must match the label of `tool_meme_sounds` in the editor (the toast points at that tool), so the draft above copies the current `tool_meme_sounds` strings (checked in the five `strings.xml` files: "Suara meme", "Sonidos de memes", "Sons de memes", "मीम साउंड"). If P5-06 changes that label, this toast changes with it. Hindi: check the Primary's two-line wrap at 200%.

## Accessibility
- **The Use block:** "Use {name} in a new video meme" (P3-02). The disabled-while-downloading block stays focusable and still says its name; the row's meta "Downloading…" is read with the name block.
- **Offline rows:** there is no block to focus, and the name block's meta ("Not saved for offline") says why; this is the "explain why" the screen-reader user needs, since the block is gone.
- **The picker is system UI**; focus returns to the Use block or the details Primary that started the flow when the user cancels. After a successful pick, focus follows the editor's rule (arrival, P1-04).
- **Announcements (polite, SC 4.1.3):** "Downloading {name}" and "Couldn't download {name}" (P3-04), the editor's "{name} added at 0 seconds" and the toast, if any. Nothing takes focus.
- **Contrast:** Use block `on-primary` on `primary` 7.9:1; Primary button label as P3-03; toast `text` on `surface-raised` 12.4:1.
- **Targets:** the Use block 48 dp (36 dp drawn), the sheet's Primary at least `touch-target`.
- **200% font and reduce motion:** see above; no gesture-only action.

## Analytics
Events from the PRD; no names, no query text.
- `sound_add {sound_id, tab, editor: "video"}` fires **once, when the editor opens with the sound on the timeline** (not when Use is tapped, not when the picker is cancelled, and not when the download fails). `tab` is the row's tab (`trending`, `local`, `global`, `favorites`, `recent`, `my_sounds`, `search`); from the details sheet, the tab of the screen that opened it. For a My sound `sound_id` is `my_sound` (P3-07).
- `project_create {editor: "video", source: "gallery"}` once, when the new project is first saved with at least one item (P1-02 rules). Order: `project_create`, then `sound_add`.
- **Home row (P3-14):** `tab` needs a value for a sound added from Home. `home`, a value of the existing `tab` parameter (in the PRD since 11 Nov).
- No `sound_add` if the sound is missing on arrival. `sound_preview` is unchanged: a preview before Use already fired it.

## QA compares
Screenshots at 360 dp wide, English unless noted, in `docs/ux/reviews/P3-15/`:
1. `use-row-saved`: the row, then the picker (system UI, one screenshot for the record) and the editor on arrival with the blue sound clip at 0:00 and the main tool bar.
2. `use-downloading` (throttled network) and `use-failed`; `details-use-downloading` (the Primary reads "Downloading…").
3. `offline-row-no-use`: airplane mode, a sound that was never played: no block, names aligned with other rows, meta "Not saved for offline"; `details-offline` (no Primary, the line).
4. `arrive-overhang`: a 5 s sound on one 3 s photo: wash, toast.
5. `arrive-hi-200`: Hindi at 200% font, the details Primary wrapped.
By recording or note: the three taps counted on the owner's phone (Use, a clip, Add), the picker cancelled (tab unchanged, no toast), "Some didn't go in" with the sound kept, back out of the editor to the Sounds tab, the bonk and Confirm haptic after the push, TalkBack through the row and the arrival announcement.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on.
1. **A sound longer than the picked media** (a 5 s sound, one 3 s photo).
   - **A. Recommended: keep the media as picked; the overhang is shown and a toast says to trim or move it.** Same rule as the editor's picker (P3-05 Question 2). Predictable.
   - B. Lengthen the last photo to cover the sound. The sound always plays, but Memix changes the user's timeline unasked and breaks the 3 s photo rule (P1-02).
2. **Nothing selected on arrival** (decided, not a question): the first thing a new user needs is the main tool bar and Play. Say so if the owner prefers the sound selected.

## Requests to the principal mobile engineer
- **Pending sound:** the Create video flow carries one `pendingSoundId` from Use to the project's creation; keep it in saved state so a process restart during the picker still lands the sound. When the first save succeeds, write the sound onto the project (as P1-06 landing at 0:00 does, first free meme lane), in the same save as the clips, so `project_create` and the sound are one commit.
- **Pin the cached file** from the tap (or the end of the download) to the landing so the 500 MB cleanup cannot evict it (P3-04).
- **Arrival feedback:** the bonk and Confirm haptic run after the push finishes; selection stays empty; Undo stack empty.
- **SoundRow:** the Use block is not drawn (slot kept) for offline-not-saved and removed rows. P3-04's table is updated; P3-05's Add block follows the same rule.
- **Reuse** the overhang check from P3-05 (`playhead + length − end`, 0.5 s threshold) with the playhead at 0:00.
- **Strings:** `use_sound_missing_toast` and the translations above in the five `strings.xml` files, shared with the picker where noted.
- **No new token and no new icon.**

## Tokens
None. No new color, size, radius or icon.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| The system photo picker needs no permission and returns its result even after Android stopped the app | Guidance | [Android photo picker](https://developer.android.com/training/data-storage/shared/photo-picker), P1-02 Evidence |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Hiding an action that cannot work, and saying why in text next to it, is clearer than a dead button | Guidance | [Material 3: disabled states](https://m3.material.io/foundations/interaction/states/applying-states), DESIGN_SYSTEM → States ("prefer keeping controls enabled and explaining on tap" for tools; a row action with no meaning offline is omitted, as P3-04's meta explains) |
| An icon-only plus can read as "favorite" or "add to a list" | Assumption (test it, P3-02's script task 4) | none |

## Test script for the owner
Use P3-02's script (task 4, "Put a sound on a video"): here, observe whether people tap the blue block or open the details first, whether they expect the picker next, and whether they notice the sound on the timeline when the editor opens. Success: task 4 in under 45 s, no help.
