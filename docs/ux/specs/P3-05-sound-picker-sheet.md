# Sound picker sheet in the video editor · P3-05

Part of the Phase 3 sound specs. Read first, and not repeated here:
- `P3-02-sounds-library.md`: the row, tabs, search, empty and error blocks. The picker reuses them.
- `P3-03-sound-preview-and-details.md`: the playing row, the waveform and the details sheet (this spec decides how it shows inside the picker).
- `P3-04-download-states.md`: Downloading, Failed, Offline and Unavailable row looks.
- `P3-10-report-a-sound.md`: the Report pane.
- `P3-07-my-sounds.md`: the My sounds tab and its import actions.
- `P1-06-clip-edits-and-audio.md` → Meme sounds sheet and Meme sounds (the P1-08 sheet this replaces, and the landing rules this keeps), `P1-05-timeline.md` → "Add a meme sound" row.

No board shows it. Components: Sheet (`design/system/components/Sheet/README.md`, half-height opening), SoundRow, Tabs, Toast, ProgressBar.

## Job
"React to a clip with a meme sound" (PRD jobs table), from inside the editor, with the whole library behind it. **Sound first**: any sound is at most 2 taps from the timeline and lands at the playhead. **Done when** (TICKETS): two taps from the editor to a placed sound.

**The one blue action:** the blue **Add** block on each row (meme-sound UI, as on P1-08), and **Add to video** (Primary) on the details pane. Selection stays white (the selected tab underline). Every button in an empty or error state is Secondary.

**Tap count (the Done when, with the state it holds in):**
| Starting point | Tap 1 | Tap 2 |
| --- | --- | --- |
| Nothing selected | Meme sounds tool (main tool bar) | A row's Add block |
| A clip or sound selected (clip tool bar showing) | "Add a meme sound" pill on the timeline (clears the selection) | A row's Add block |
| Photo editor's "Make it a video meme" (P2-10) | The editor arrives with this sheet already open | A row's Add block (1 tap) |

The sheet opens with real rows and Add blocks on screen, so tap 2 needs no scroll, search or tab change when the sound is in the opening tab. A sound in another tab costs one more tap (the tab); a search costs typing. Those are not the Done when's path.

## Entry and exit
- **Opens from:** the Meme sounds tool and the "Add a meme sound" pill (unchanged, P1-05, P1-06), and P2-10's arrival. It replaces the P1-08 sheet's content; the entry points, the title "Meme sounds" and the half-height opening do not change.
- **On open:** the editor pauses (P1-08). The **playhead is read when the sheet opens**; nothing can move it behind the sheet.
- **Opening tab:** the tab last used in the picker during this app run, else **Trending** (Questions 1). The scroll and the search text are not kept; the tab is. Favorites and recents are stored on the phone (P3-06) and are always current.
- **Closes:**
  - **A sound lands:** the sheet closes over 200 ms and focus goes to the new sound's first tool (Sheet README).
  - **Close button, scrim, drag down, system back (when no pane or keyboard is open), Escape:** nothing is added. A playing preview stops.
  - **System back order:** keyboard first, then the open pane (Details, Report, Import) back to the list, then the sheet. This differs from P3-02 (where back clears search first) on purpose: a sheet is transient, so the search text goes with it.
- **Panes, not stacked sheets** (Sheet README: never stack): the list, the **details pane** (P3-03's content), the **Report pane** (P3-10) and the **import pane** (P3-07) swap inside this one sheet over 200 ms with a back arrow `arrow_back` at the start of the title row. The list's scroll and tab come back as they were. A playing preview keeps playing across panes.
- **Kept after a landing:** the sound is one undo step in the editor ("Add sound"), the sound is recorded in Recently used (P3-06), and the sheet's tab is remembered.

## Layout
Sheet component: `surface`, `radius-lg` top corners, `shadow-sheet`, grabber, close button, over `scrim`; `Modifier.coveredByModal` on the editor behind it.

### Height
- Opens at **half the window** (`opensAtHalfHeight`) so the top of the preview stays in view, **but never lower than the header plus three rows** (**about 436 dp**; the first draft said 364 dp and counted 48 dp rows, but a row measures about 70 dp: 48 dp target, 16 dp row padding, 8 dp gap; corrected in the P3-05 review): at half height the header (frame, search, tabs) leaves room for only about 1.9 rows on a 640 dp-tall phone, and the Done when is a tap on a row. In the Sheet README. The sheet also keeps at least this height while open, so a short list or few search results do not move the tabs.
- A drag up on the grabber or the title row grows it to the long-content height (`space-10` under the status bar). **Focusing the search field grows it at once** so the keyboard and the results both fit (200 ms; at once with reduce motion). It stays grown after the keyboard closes until the user drags it down.
- **Large font:** from a font scale of **1.5** the search field scrolls with the list (it becomes the first list item, below the tabs) and only the title row and the tabs stay pinned. (First draft: when the header takes over 40% of the sheet. Accepted in the P3-05 review as a fixed rule: simpler, and the header would not reach 40% before 2.0 anyway, so it hides search a little early on purpose. `pinned-bar-max-fraction` is not used.)

### Content, top to bottom
1. **Title row** (the sheet frame): "Meme sounds" in `title`, the close button at the end. In a pane, `arrow_back` (48 dp) at the start and the pane's title.
2. **Search field** (pinned): as P3-02 (`surface-raised`, `radius-md`, `touch-target` tall, `search` icon 20 dp, Clear when there is text, IME action Search, name "Search sounds"). Placeholder per tab (`sounds_search_hint`, `_favorites`, `_mine`).
3. **Tabs** (pinned): **Trending · Local · Global · Favorites · My sounds**, the Tabs component exactly as P3-02 (white 2 px underline on the selected one, scrolling row, 48 dp tall).
4. **The list:** status strips first, then SoundRows. Rows are on `surface`, so the playing band is `surface-raised` (`onSurface = true`, P1-08 review P1). Lazy list, 30 per page, "Loading more" and "Couldn't load more" as P3-02.

**Differences from P3-02 (the Sounds tab):**
- **No category chips.** They would add a third pinned row (48 dp) and cost about one visible row at half height. The picker's job is a quick pick; categories are searchable by name (P3-02 → Search covers categories). Chips stay on the Sounds tab.
- **No scrolling title** (the title is the sheet's).
- **The blue block is "Add"** (lands at the playhead), not "Use" (starts a new meme). Same 36 dp `primary` block with `create` in `on-primary`, 48 dp target. Spoken "Add {name} to video".
- **The heart is shown** (P3-06 exists now; P1-08 hid it). It is the same toggle with the same rules.
- **Local's region line, status strips, empty blocks, placeholder rows and the error block** are the P3-02 ones, centered in the sheet's list area.
- **A strip that carries an action** (storage full, below) is a `surface-raised` strip with the text and one Quiet button on its own row, as the persistent Toast lays out its action.

### The row
Exactly P3-02's row: `[disc] [name + meta] [heart] [Add]`. The name block opens the details pane; a long-press anywhere on the row does the same (LongPress haptic). Download, offline and unavailable looks are P3-04's, with one change this spec makes for both screens: in an **offline, not-saved** row and a **removed from the catalog** row the Add (and Use) block is **not drawn** and its 48 dp slot stays empty, so names do not jump when the connection returns. This follows the P3-15 ticket ("offline, a sound that is not on the phone shows no Use block"). P3-04's table is updated to match.

### Details pane
P3-03's sheet content (title with the sound's name and heart, player row with the large waveform, facts, Report), inside this sheet. The Primary is **Add to video** (`sound_detail_add_to_video`), full width and pinned. It lands the sound at the playhead and closes the sheet exactly as the row's Add does. Where a sound can't be added (offline and not saved, removed) the button is hidden and P3-03's line explains. Back, the back arrow and Escape return to the list.

## Adding: what happens
The landing rules are P1-06 → Meme sounds → Landing and are not changed: at the playhead, on the first meme-sound lane free for the whole length (else a new lane), the whole sound at 100%, the bonk, one Confirm haptic, the new sound selected so its tools are ready, TalkBack "{name} added at {time}", one undo step "Add sound". This spec adds what the P1-08 sheet never had to say.

### Where it lands (the playhead and the end of the video)
Let `end` be the video's length and `length` the sound's.
| Case | Start | What the user sees |
| --- | --- | --- |
| Playhead is more than one frame before `end`, and the sound fits (`playhead + length ≤ end`) | The playhead | Normal landing |
| **Playhead at the end** (within one frame of `end`) | `max(0, end − length)`: the sound **ends** at the end of the video (P1-06 rule, kept) | Normal landing; nothing is said, because the sound is heard |
| **Sound longer than the video left** (`playhead + length > end`) and the unheard part is **0.5 s or less** | The playhead | Normal landing; no toast (a tail of half a second is not worth a message) |
| Unheard part **over 0.5 s** | The playhead (the user chose that moment) | The part past `end` shows the past-the-end wash (P1-05), the sound is selected, and a 4 s toast says what to do: `picker_overhang_toast` |
| Sound longer than the **whole** video | **The playhead**, as every other row (P3-05 review: the first draft said 0:00 on the false ground that the playhead would put the start before 0:00; it never does, and a jump at 10.0 s of sound length is a surprise) | Overhang over 0.5 s: wash and toast, as the row above |
- **Why the playhead wins over "shift it earlier"** (Questions 2): a hit placed at 3.2 s is meant to sound at 3.2 s. Shifting it silently would put it where the user did not ask. The toast names both fixes: trim the tail (drag its end handle, or the tool bar) or move it earlier (Move here).
- The video never grows to fit a sound (P1-05: items may reach past the end).
- The toast sits in the stage's message slot, which is above the scrim, so it is visible as the sheet closes. TalkBack hears "{name} added at {time}" and then the toast.

### Sound is on the phone
Lands at once. No progress, no wait.

### Sound is not on the phone (online)
The first tap on Add starts the download; the sheet stays open until the sound lands.
- **Under 300 ms:** nothing shows; the sound lands and the sheet closes. The tap count stays 2.
- **Over 300 ms:** the row shows **Downloading** (P3-04: disc becomes Cancel, meta "Downloading…", the determinate rail). The Add block is in its disabled look (still focusable). The sheet's close button stays active.
- **Success:** the sound lands, the sheet closes, the row never shows a "done" state.
- **The user closes the sheet or taps Cancel on the disc while it downloads:** the download stops, nothing lands, nothing is kept, no toast. (A late landing after the sheet is gone would put a sound on the timeline the user already walked away from.)
- **The user taps another row's disc or Add while one downloads:** the first is cancelled and the new one starts (one sound at a time, as P3-04).

### Download fails mid-add
- After the engine's two retries (P3-04) the row shows **Failed**: disc becomes Retry (`rotate_right`), meta "Couldn't download. Tap to try again." in `danger` (on `surface`, 5.2:1). **Nothing lands and the sheet stays open**, because closing would hide the reason and the next step. The Add block is active: a tap retries and, on success, lands the sound (P3-04). A polite announcement "Couldn't download {name}" fires once.
- The sound's partial file is deleted. A second failure shows the same look; no counter.
- **Phone storage full:** the row shows "Not enough space on your phone." (P3-04) and the Add block is disabled. A **strip** at the top of the list says `sound_dl_storage_toast` with a Quiet **Free up space** button (P3-04's action, hidden when the phone has no storage screen). The strip is used instead of P3-04's persistent toast because a toast under a scrim cannot be reached by TalkBack or keyboard while the sheet covers the editor. It goes when a download succeeds or the sheet closes. Coming back from the system's storage screen retries nothing by itself.
- **The file arrives but won't decode:** nothing lands; the toast `explain_sound_unreadable` ("That sound won't play. Try another one.") shows in the stage slot (P1-06), and the row goes to idle. The bad file is deleted so the next tap downloads it again (P3-04). **If copying into the project fails because the phone is full**, the toast is `sound_dl_storage_toast` instead (existing string, no action, no new key); a missing file keeps `explain_sound_unreadable`.

### Offline
- **Strip** `sounds_offline_strip` at the top of the list when the connection drops (polite). Rows with audio on the phone are normal and add as usual (no connection needed); the rest are dimmed with "Not saved for offline" and show no Add block.
- **Nothing cached at all** (first launch offline, P3-02's load-error case): the picker does **not** show the error block. The starter pack ships inside the app (PRD: 50 sounds, 10 until P3-04), so the Trending, Local and Global tabs show the pack's rows under the offline strip, with "Included with Memix" in the details pane. Two taps still work. Favorites and My sounds are as normal. *(P3-04 says the pack plays "from the Favorites and My sounds tabs and from the editor's picker". I read that as "the saved rows"; the engineer confirms where the pack's rows are listed on the Sounds tab. In the picker this rule holds regardless.)*
- **Search offline:** P3-02's rule (contains-match over saved sounds, strip "Offline: showing matches from saved sounds").
- **The connection drops mid-download:** the row goes to Failed as above.
- **Coming back online:** the strip goes; no toast; rows that were dimmed become normal.

### Other states
- **A favorite or recent removed from the catalog:** the row stays with "No longer available", no Add block; the heart still works.
- **Add tapped twice quickly:** the second tap is ignored (one landing, one undo step). A sound already on the phone lands in a frame or two, so the block is **not drawn disabled** (it would only flash); it is marked disabled for TalkBack while the landing runs. A landing that takes over 300 ms (a long imported file) shows the Downloading look's disabled Add block.
- **Volume is 0 when a preview starts:** P3-02's toast once per app session. The sound still plays.
- **A sound already used in the project:** nothing special: it can be added again.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Default (online, data) | Title, search, tabs, rows with disc, name, heart, Add; at least 3 rows visible | tab strings, `sound_meta` |
| First load (> 300 ms, nothing cached) | Six static placeholder rows (P3-02) | `sounds_loading_status` (spoken) |
| Load error, nothing cached, online | P3-02's error block, Secondary "Try again". (Offline: the starter pack, see Offline.) | `sounds_load_error_*`, `sounds_try_again` |
| Offline, cached | Strip, saved rows normal, others dimmed with no Add block | `sounds_offline_strip`, `sound_not_saved_offline` |
| Offline, nothing cached | Strip and the starter pack's rows in Trending, Local, Global | `sounds_offline_strip` |
| Searching, results, none, offline | P3-02's states, centered in the list area | `sounds_*` as P3-02 |
| Empty tabs (Trending, Local, Global, Favorites, My sounds) | P3-02's and P3-07's empty blocks | as P3-02, P3-07 |
| Previewing | Playing row with waveform and `surface-raised` band | `sound_a11y_pause` |
| Downloading (> 300 ms) | Row in the Downloading look; Add disabled look | `sound_dl_downloading` |
| Download failed | Row Failed look with Retry; sheet stays open | `sound_dl_failed` |
| Phone storage full | Row look plus a strip with Free up space | `sound_dl_failed_storage`, `sound_dl_storage_toast`, `sound_dl_free_up_space` |
| Sound landed | Sheet closes; bonk, Confirm haptic, sound selected | `a11y_sound_added` |
| Landed at the end of the video | Ends at the end of the video; nothing said | none |
| Landed past the end (over 0.5 s unheard) | Wash on the overhang; toast 4 s | `picker_overhang_toast` |
| Sound won't decode | Toast; nothing lands; row idle | `explain_sound_unreadable` |
| Details pane (saved, not saved, offline, removed) | P3-03's states; Primary is Add to video | `sound_detail_add_to_video` and P3-03 |
| Report pane | P3-10 | P3-10 |
| Import pane | P3-07 | P3-07 |
| Permission | None needed | none |
| Long content, 5 languages, 200% font | Names two lines then ellipsis, meta wraps (never cut), tabs scroll, header rule above, rows grow, the sheet scrolls | none |
| First run | As every run: no coach marks, keyboard closed | none |

## Interactions
- **Tap the disc:** plays that sound alone; any other stops; the editor stays paused (P1-08). Tap again pauses. Fires `sound_preview`.
- **Tap Add:** the Adding section. **Tap the name block or long-press:** the details pane.
- **Tap the heart:** toggles at once, SegmentTick haptic when on, no toast (P3-02).
- **Tap a tab:** 200 ms cross-fade of the list. **No swipe between tabs** (P3-02), and **no drag conflict:** a drag on the list scrolls the list, a drag on the grabber or title row moves the sheet.
- **Search:** P3-02's rules (300 ms, two characters, count spoken, typo-tolerant online).
- **Keyboard:** Tab reaches the close button, search, tabs and each row's four controls; Enter activates; Space on a disc plays; Escape closes the keyboard, then goes back a pane, then closes the sheet.
- **Haptics:** Confirm when a sound lands (P1-06), LongPress for the long-press, SegmentTick for the heart. Nothing else.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Sheet opens, closes | 200 ms slide over a fading scrim | 120 ms fade |
| Grows to full height (drag, or search focus) | 200 ms | at once |
| Pane swap (list, details, report, import) | 200 ms cross-fade | 120 ms fade |
| Tab change | 200 ms cross-fade | 120 ms fade |
| Row states (disc, band, heart, downloading) | 120 ms | same |
| Download rail | to each value over 120 ms, at most 10 updates a second | jumps |
| Waveform progress | follows the audio, up to 30 updates a second | 4 updates a second |
| Sound lands | the bonk, 240 ms | 120 ms fade-in |
| Toasts | 120 ms fade | same |
On Android, "reduce motion" ends every animation on the next frame (DESIGN_SYSTEM → Motion), so each cell shows as an instant change there. Nothing loops (MOTION 2).

## Copy
English source; id, es, pt and hi are machine drafts (allowed until P5-06; the final say is P5-06). **Budget** is the longest the string may be in any language, in characters including placeholders. Strings P3-02, P3-03 and P3-04 already define (`sounds_*`, `sound_*`, tab labels) are reused, one copy shared with the Sounds screen, not duplicated. Tone: verb first, "you", sentence case, no emoji, no exclamation marks.

| Key | English | Budget | id | es | pt | hi |
| --- | --- | --- | --- | --- | --- | --- |
| `sound_a11y_add` | Add {name} to video | 40 + name | Tambahkan {name} ke video | Añadir {name} al video | Adicionar {name} ao vídeo | {name} को वीडियो में जोड़ें |
| `sound_detail_add_to_video` | Add to video | 28 | Tambahkan ke video | Añadir al video | Adicionar ao vídeo | वीडियो में जोड़ें |
| `picker_overhang_toast` | The end of this sound is past your video, so it won't play. Trim it or move it earlier. | 110 | Ujung suara ini melewati akhir videomu, jadi tidak akan terdengar. Pangkas atau geser lebih awal. | El final de este sonido pasa del final de tu video, así que no se oirá. Recórtalo o muévelo antes. | O final deste som passa do fim do seu vídeo, então não será ouvido. Corte-o ou mova-o para antes. | इस आवाज़ का आखिरी हिस्सा आपके वीडियो के बाद है, इसलिए वह नहीं बजेगा। उसे ट्रिम करें या पहले खिसकाएँ। |
| `picker_back_a11y` | Back to sounds | 30 | Kembali ke daftar suara | Volver a los sonidos | Voltar aos sons | आवाज़ों पर वापस जाएँ |

Translator notes: "sound" is the meme-sound word each language's editing apps use (id "suara", es "sonido", pt "som", hi "आवाज़", matching the tab labels in P3-02). `{name}` is a sound title and may be long: the budget counts it separately. "Trim" is the editor's own word for the handles (P1-06 → Trim). "Earlier" means a smaller time on the timeline. Hindi: check Devanagari line height at 200% in the toast's first render.

## Accessibility
- **Sheet:** the title is the pane title; focus moves to the sheet on open, and the editor behind it leaves TalkBack and keyboard focus (`coveredByModal`, P1-08 F1). The scrim stays reachable as "Close".
- **Order:** title, close, search, Clear, tabs, strip, rows (disc, name block, heart, Add). In a pane: back, title, content, Primary.
- **Names (WCAG 2.5.3):** the disc "Play {name}" and its other states (P3-04); the name block "{name}, {meta}" with the click label "Show details"; the heart "Favorite {name}", checkable; the Add block "Add {name} to video"; the details Primary "Add to video" (equal to its label).
- **Row actions for TalkBack** (long-press is never the only route): Details, Favorite or Remove from favorites, Add to video, Report (in the pane).
- **Announcements (polite, never taking focus, SC 4.1.3):** "Searching…", the result count, the offline strip, "Downloading {name}" and "Couldn't download {name}" (P3-04), the landing "{name} added at {time}", the overhang toast.
- **After a landing,** focus goes to the new sound's first tool (Sheet README), as P1-08.
- **Focus on Retry:** after a failure, focus stays on the disc so a screen-reader user hears the new name "Try again: download {name}".
- **Contrast:** `text` on `surface` 12.4:1; `text-secondary` 5.7:1; `text-muted` on `surface-raised` 4.6:1 or more; `danger` meta words only on `surface` or `canvas` (5.2:1; never on the playing band); `primary` on `surface-raised` 5.9:1; Add block `on-primary` on `primary` 7.9:1.
- **Targets:** every control 48 dp or more (search, Clear, tabs, disc, name block, heart, Add, back, close, the strip's Quiet button).
- **200% font:** see Layout (header rule). Names take two lines and meta as many as it needs; the waveform keeps its dp size; toasts and strips wrap and are never cut; the Primary stays pinned in the details pane (P3-03's 40% rule).
- **Reduce motion:** see Motion. Every action is reachable without a swipe or a long-press.

## Analytics
Events from the PRD; no query text, names or file details.
- `sound_preview {sound_id, tab}` once per tap that starts playback (not resume, not replay). `tab` is `trending`, `local`, `global`, `favorites`, `recent`, `my_sounds` or `search` (P3-02).
- `sound_add {sound_id, tab, editor: "video"}` when the sound lands on the timeline (not when Add is tapped, not on a failed download). For a My sound, `sound_id` is the constant `my_sound` (a local id means nothing to the catalog) and `tab` is `my_sounds`.
- `sound_search {results, offline}` as P3-02, one per settled query (PRD, 11 Nov). The P3-02 text that calls it "a proposal, do not fire" is out of date and is corrected there.
- `sound_report` is P3-10's. Opening the sheet, a pane or a tab logs nothing. A failed download goes to Crashlytics non-fatal logging by the engineer (error class only), not Analytics (P3-04).
- No `tool_use`: the picker is not a tool.

## QA compares
Screenshots at 360 dp wide, English unless noted, in `docs/ux/reviews/P3-05/`:
1. `picker-open`: the sheet over the editor, at least three rows with their Add blocks, header and tabs; at 360 × 800 and at 360 × 640.
2. `picker-playing`: a row playing with waveform and `surface-raised` band.
3. `picker-search`: the sheet grown with the keyboard up and results.
4. `landing-end`, `landing-overhang`: playhead at the end (sound ends at the end); a 4 s sound with 1.5 s left (wash and toast).
5. `row-downloading`, `row-failed`, `strip-storage-full` inside the picker (throttled network, then airplane mode mid-download).
6. `picker-offline-cached` and `picker-offline-starter` (first launch offline: the starter pack's rows).
7. `details-pane`: Add to video, back arrow; `picker-hi-200`: Hindi at 200% with the header scrolling away.
By recording or note: the two-tap path counted on the owner's phone (with a clip selected and with nothing selected), TalkBack through a row, the sheet closing on a landing, back order (keyboard, pane, sheet).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on.
1. **Which tab opens first?**
   - **A. Recommended: the tab last used in the picker, else Trending.** Predictable; a user who lives in Favorites stays there.
   - B. Favorites first when it has anything, else Trending. Fewer taps for repeat users, but the screen changes shape between visits.
   - C. Always Trending. Simplest, ignores habits.
2. **A sound that runs past the end of the video.**
   - **A. Recommended: it lands at the playhead; the overhang is shown and a toast says to trim or move it** (over 0.5 s only). The user's chosen moment is kept.
   - B. Shift it earlier so it ends with the video. The sound is always heard, but it lands where nobody put it.
   - C. Ask in a sheet. Another step, against sound first.
3. **Category chips are dropped in the picker** (decided, not a question): they would cost about one visible row at half height. Say so if the owner wants them back; it is a one-component change in the header.

## Requests to the principal mobile engineer
- **Sheet:** `opensAtHalfHeight` gets the minimum "header plus three rows" rule (added to the Sheet README), and a way to grow when the search field gets focus. Panes swap inside one Sheet; no second sheet.
- **SoundRow:** the Add variant of the Use block (`sound_a11y_add`), the empty Add/Use slot for the offline-not-saved and unavailable looks, and a strip variant with a Quiet action. One component for the Sounds tab and the picker, one set of strings.
- **Landing:** the overhang rule above is a calculation on the existing landing code (`playhead + length − end`); the toast uses the existing stage slot. Read the playhead when the sheet opens.
- **Download then land:** cancel on close, on Cancel and on another row's tap; a failure leaves the sheet open.
- **Strings:** the four new keys in the five `strings.xml` files, shared strings in the shared module (P3-02 rule). The `sound_*` keys keep one copy.
- **Pinned header at large font:** reuse `MemixSize.pinnedBarMaxFraction`; no new token.

## Tokens
No new token and no new use of an existing one (the picker's large-font rule is a fixed font scale of 1.5; see Height). No new icon.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| A bottom sheet suits a pick that keeps the screen behind it relevant; it should not stack | Guidance | [Material 3: bottom sheets](https://m3.material.io/components/bottom-sheets/guidelines) |
| Status messages (searching, download failed, landed) reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| A pinned header that eats the viewport hurts at large text, so it must scroll away | Guidance | [WCAG 2.2 SC 1.4.4](https://www.w3.org/WAI/WCAG22/Understanding/resize-text.html), [SC 1.4.10 Reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html) |
| Competitor editors need about three taps (open Audio, open Sounds, tap +) to place a library sound | Assumption (product knowledge, not checked here; verify when the owner can open CapCut and VN) | none |
| At half height the header leaves about 1.9 rows on a 640 dp-tall phone, so a minimum is needed | Assumption (computed by hand from the row and header heights; QA measures it) | none |

## Test script for the owner (5 people, a phase build)
Tasks: (1) "Put a boom sound where the clip gets funny." (2) "Now make that same sound louder." (3) "With airplane mode on, put a sound on the video." (4) "Put a long song's sound at the very end of the video." Observe: whether people tap the pill or the tool; whether they find Add without scrolling; what they do when the toast says the end is past the video. Success: task 1 in under 10 s and 2 taps; 3 works with no help. Notes template: task, time, taps, what they said, where they hesitated.
