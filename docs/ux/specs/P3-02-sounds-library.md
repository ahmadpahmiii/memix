# Sounds library: the Sounds tab · P3-02, with P3-06 (Favorites and recently used)

Phase 3 spec set for the sound library. Four linked files; this one is the screen. Read the others for their parts:
- `P3-03-sound-preview-and-details.md`: the playing row's waveform and the details sheet (credit, license).
- `P3-04-download-states.md`: the row's download states and the offline look (mechanism only).
- `P3-10-report-a-sound.md`: the Report pane inside the details sheet.

Builds on, and does not repeat: `P0-05-navigation-and-browse-screens.md` (frame, bottom nav, empty-state look, Home region label), `P1-06-clip-edits-and-audio.md` → Meme sounds sheet (the SoundRow, one sound plays at a time, the playing band), `P1-14-drafts.md` (toast with Undo, sheets, no spinner), and the component READMEs in `design/system/components/` (SoundRow, Tabs, Chip, Sheet, Toast, ProgressBar).

Decided since (owner, 11 Nov): P3-11's Region sheet (`P3-11-region-sheet.md`) and the Home trending row (`P3-14-home-trending-sounds.md`) now have specs. Where this screen touches them it says so.

Board: `design/screens/Sounds.dc.html`. Differences are listed under Layout.

## Job
"React to a clip with a meme sound" (PRD jobs table) and "find a sound by name even when I misspell it". The Sounds tab is where a user browses, hears and keeps sounds; the editor's picker (P3-05) is where one lands on a timeline.

**The one blue action:** none of its own. The blue on this screen is the Create button in the bottom nav (P0-05) and meme-sound UI (play discs, the Use block), which the system already colors blue. Every button in an empty or error state is **Secondary**, so no second main action competes.

**Fast path (Sound first):** Sounds tab (1), disc (2): you hear it. Hear, then Use (a row's blue block, 3), pick media (4): the editor opens with the sound at 0:00. From inside the editor the rule stays "any sound in 2 taps" through P3-05's picker, not this screen.

## Entry and exit
- **Arrives from:** the Sounds item in the bottom nav (P0-05).
- **Tab, chip and scroll are kept** while the app is open and the user moves between bottom-nav items (state survives in the ViewModel). **Not kept:** the search text (clears when you leave the tab), a playing preview (stops, as P1-08: leaving stops it).
- **System back:** closes the keyboard if open (system). Then, if the search field has text, clears it (stay on the screen). Otherwise follows P0-05 (back to Home).
- **A row's name block or long-press:** opens the details sheet (P3-03). **The Use block:** the system photo picker, then the editor (below). Closing the details sheet returns to the same scroll and focus (focus goes back to the row that opened it).
- **Use block (decision, see Questions 1):** tapping the blue block on a row starts a **new video meme** with that sound. Photo picker for videos and photos (P1-02); when the user confirms, the editor opens as in P1-02 and the sound lands at 0:00 on the first meme sound lane with the bonk (P1-08 landing rules). Cancelling the picker changes nothing and leaves the user on this screen. The sound is not copied or downloaded twice (it is already on the phone once it played or the block downloaded it, see P3-04). This is a new flow that needs a small ticket (see Questions 1); if the owner says no, the block is not drawn on this screen and the rows have disc, name and heart.

## Layout
Board: `Sounds.dc.html`. Components: Tabs, Chip, SoundRow, Sheet, Toast, ProgressBar (tokens: `canvas`, `surface`, `surface-raised`, `radius-md`, `space-*`, `touch-target`).

Top to bottom, inside the P0-05 frame (status bar inset on top, bottom nav under it; content scrolls above the nav, never under it):

1. **Title** "Meme sounds" in `title-l` (P0-05; the board's 40 px Anton heading predates P0-05 and is not used). It scrolls away with the list.
2. **Search field** (pinned once the title has scrolled off): `surface-raised`, `radius-md`, `touch-target` tall, `space-4` side gutters. Leading `search` icon at 20 dp in `text-secondary` (new icon, DESIGN_SYSTEM → Iconography), the text in `body`, the placeholder in `text-muted`. A **Clear** IconButton (`close`, 48 dp) at the end appears only when there is text. Differences from the board: the board's visible "Search sounds" label above the field is dropped. A magnifier plus a visible field is the recognised search pattern, and the label cost 20 dp of a screen that already carries four stacked rows. The accessible name is "Search sounds". IME action: Search (it hides the keyboard; results already show as you type).
3. **Tabs** (pinned under the search field): the Tabs component. **Trending · Local · Global · Favorites · My sounds**, in that order, Trending selected on first run. The row scrolls sideways; the selected tab scrolls into view. Tabs are `touch-target` tall (the board's 44 dp is raised to 48). Selected `text` with the white 2 px underline; others `text-secondary`. At 360 dp about three and a half tabs show, so the cut-off fourth tells the user to scroll.
4. **Category chips** (scroll with the list as its first item): "All" plus the categories from the catalog in the catalog's `sort` order, as the Chip component (selected white fill with `on-primary`, others `surface-raised`). A chip is drawn 36 dp tall inside a `touch-target`-tall hit area. **Shown on Trending, Local and Global only.** Hidden on Favorites, My sounds and while a search has text. Selecting a chip filters the current tab's list; **changing tab resets it to All** (Assumption: a kept filter leads to empty tabs the user has to diagnose). Category names come from the catalog (`name` per language), not from string resources.
5. **Status strips** (the first list item, above the rows, scroll away): `surface-raised`, `radius-md`, `label` in `text`, `space-3` padding, `space-4` side margins, never cut. Used for the offline note and the offline search note. No icon.
6. **The list.** Rows are SoundRows on `canvas` (the playing row steps up to `surface`, P1-08 rule). Row height at least `touch-target` plus `space-2` above and below (64 dp), growing with text. No dividers (the board's hairlines between rows are dropped: depth is by value and spacing, as on Drafts, P1-14). Lazy list, 30 per page.

### The row
`[play disc] [name + meta] [heart] [Use]`, from the start edge, `space-1` between the four.
- **Play disc:** 40 dp `primary-subtle` disc with `primary` `play` in a 48 dp square; playing: `primary` fill with `on-primary` `pause` (SoundRow README).
- **Name and meta block:** one button at least `touch-target` tall that opens the details sheet. Name in `body-strong`, up to two lines; meta in `label` `text-secondary`: "{duration} · {credit}" (`sound_meta`, P1-08), wrapping and never cut (a CC-BY credit is a license condition). In search results the same.
- **Heart:** the SoundRow favorite toggle. Off: `heart` in `text-secondary`; on: `heart_filled` in `text`. (The board shows the on heart in blue; the SoundRow README, which the P1-08 review settled, says `text`, because a heart is not a meme sound. The README wins.) 48 dp target.
- **Use:** the 36 dp `primary` block with `create` in `on-primary`, 48 dp target; pressed `primary-pressed`. Spoken "Use {name} in a new video meme". In the editor's picker (P3-05) the same block is "Add to video" and lands at the playhead; the two share the component and differ by name and action.
- **Width at 360 dp:** 32 gutters + 3 × 48 + 4 × 4 leaves about 170 dp for the text block, enough for "0:02 · Royalty-free" on one line; longer credits wrap.
- **The playing row** adds the waveform under the name (P3-03). **Download, offline, unavailable** looks are in P3-04.

### Tab contents
| Tab | What it lists | Order | Chips |
| --- | --- | --- | --- |
| Trending | This month's trending for the user's region, then global (TECHNICAL_DESIGN → Trending), de-duplicated | Rank | Yes |
| Local | Sounds tagged for the user's region, with a region line (below) | Newest first | Yes |
| Global | Sounds tagged `global` | Newest first | Yes |
| Favorites | Two sections: **Recently used** (up to 5), then **Favorites** (all) | Recent: last used first. Favorites: last favorited first | No |
| My sounds | The user's imported and extracted sounds (P3-07 builds the import) | Newest first | No |

- **Local's region line:** the first list item, a `globe` icon (24 dp, `text-secondary`) and "Sounds for {region}" in `label` `text-secondary`, where {region} is the same country name Home shows (P0-05 `region_*`). **Updated 11 Nov (P3-11):** the line is now the **opener of the Region sheet**: a `surface-raised` row, `touch-target` tall, with the word "Change" in `primary` at its end; the whole row is one button. If the region is missing it reads "Choose your region". The row's exact look, copy and behavior are in `P3-11-region-sheet.md` → The Local tab's header. (It was a label while the owner had not decided the sheet; a tappable-looking control would have been a dead one.)
- **Favorites tab:** "Recently used" is every sound that landed on a timeline in the editor or the Use flow, last 20 stored, 5 shown (Assumption: five rows fit one screen above the second section; no "see all" in v1). A sound can be in both sections. Sections have a `label` `text-secondary` heading, `space-6` above (not on the first), `space-2` below. When a section is empty it is not drawn, except Favorites, which shows its hint when Recently used is present.
- **Un-favoriting inside the Favorites tab** does not remove the row at once: the heart turns off and the row stays until the user leaves the tab or refreshes, so a mis-tap can be undone with the same tap and the list never shifts under a finger. TalkBack hears "Favorite {name}, off".
- **A favorite or recent whose sound left the catalog:** the row stays, with the disc and Use block disabled and the meta "No longer available" (P3-04); the heart still works so the user can remove it.
- **Favorites and recents are stored on the phone only** (PRD), as the sound id. They survive restarts (P3-06 Done when) and updates.

## Search
- **Scope:** on Trending, Local and Global, search covers the whole catalog (name, tags, category, in any language), weighted toward the user's region. On Favorites and My sounds, search filters that list only, locally, by contains-match, and the placeholder says so ("Search your favorites", "Search your sounds"). Assumption: one rule per tab family, announced by the placeholder.
- **Online:** results come from the typo-tolerant search (`search_sounds`, P3-01), so "bruhh" finds "bruh". It starts 300 ms after the last keystroke and for two characters or more; one character shows no results change. Results replace the tabs' list; the tabs and chips stay where they are but chips are hidden. Clearing the field returns to the tab exactly as it was (scroll kept).
- **Offline:** the same box searches **sounds whose audio is on the phone** (the starter pack, downloaded sounds and My sounds) by contains-match, not typo-tolerant, and a status strip says "Offline: showing matches from saved sounds" (the PM's offline rule, 10 Nov). If nothing matches, the no-results text names the reason (below).
- **While waiting (> 300 ms):** the previous results stay on screen and a status line "Searching…" sits in a strip at the top of the list. No spinner (MOTION 2). Spoken politely.
- **Result count** is spoken, not drawn ("12 results"), after the list updates (WCAG 2.2 SC 4.1.3 status messages).
- **Hindi and other scripts:** queries match titles in every language plus tags, so a Hindi user can type "bruh". **Request to the backend engineer (check, not an assumption):** confirm that `search_sounds` finds a Devanagari title from a Devanagari query, including vowel signs, because pg_trgm splits words by the database's character classes. If it doesn't, Hindi search needs a `unaccent`/`simple` prefix fallback.
- **Results rows** are the same SoundRow; Use, heart and details work as elsewhere.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Default (online, data) | Title, search, tabs, chips, rows | `sounds_title`, tab and chip strings |
| First load (> 300 ms, nothing cached) | Six static placeholder rows: a 40 dp `surface-raised` disc and two `surface-raised` bars (name 60%, meta 35% of the width), `radius-full`. No shimmer, no spinner. A polite status for screen readers | `sounds_loading_status` |
| Refreshing (cached rows shown) | Nothing extra. The cached list updates in place; rows already on screen don't move. | none |
| Load error, nothing cached | Centered `EmptyState`-style block: title, body, Secondary "Try again". Tabs and search stay | `sounds_load_error_title`, `sounds_load_error_body`, `sounds_try_again` |
| Refresh error, cached rows shown | The cached list stays. One toast per session, 4 s | `sounds_refresh_failed` |
| Next page loading (> 300 ms) | A last list item, `label` `text-muted`: "Loading more" | `sounds_more_loading` |
| Next page failed | The last item reads the message and holds a Secondary "Try again" | `sounds_more_failed`, `sounds_try_again` |
| Offline (cached catalog) | Offline strip first; rows with audio on the phone as normal, others dimmed with "Not saved for offline" (P3-04) | `sounds_offline_strip`, `sound_not_saved_offline` |
| Offline, nothing cached ever | The load-error block with the offline wording of the body | `sounds_load_error_title`, `sounds_load_error_body` |
| Empty, Trending | Title, body, Secondary "Browse global" which opens Global | `sounds_empty_trending_title`, `sounds_empty_trending_body`, `sounds_browse_global` |
| Empty, Local (region known) | Title, body with the region, Secondary "See global sounds" which opens Global | `sounds_empty_local_title`, `sounds_empty_local_body`, `sounds_browse_global` |
| Empty, Local (region unknown; defensive after P3-11, since the first-run sheet always sets one) | The region row at the top reads "Choose your region" and is the button; title and body below it, no button of their own | `sounds_region_choose`, `sounds_local_unknown_title`, `sounds_local_unknown_body` |
| Empty, Global (catalog empty) | The P0-05 empty state with new body | `sounds_empty_title`, `sounds_empty_body` |
| Empty, category | Title, body, Secondary "Show all" which selects All | `sounds_empty_category_title`, `sounds_empty_category_body`, `sounds_show_all` |
| Empty, Favorites (and no recents) | Heart icon (32 dp, `text-secondary`), title, body, Secondary "Browse trending" | `sounds_empty_favorites_title`, `sounds_empty_favorites_body`, `sounds_browse_trending` |
| Favorites hint (recents present, no favorites) | Under "Favorites" the body text in `body` `text-secondary` | `sounds_empty_favorites_body` |
| Empty, My sounds | Title and body. P3-07 puts its two navigation rows (Import audio, Use a video's sound) at the top of the My sounds list, empty or not, above this block (`P3-07-my-sounds.md`; updated 11 Nov, was "two Secondary buttons here"); until it ships the tab shows no button. My sounds rows have no heart (P3-07) | `sounds_empty_mine_title`, `sounds_empty_mine_body` |
| Searching, results | List of results, count spoken | `sounds_results_a11y` |
| Search, no results (online) | Title with the query, body, Secondary "Clear search" | `sounds_search_none_title`, `sounds_search_none_body`, `sounds_clear_search` |
| Search, offline | Strip, then results or the offline no-results | `sounds_search_offline_strip`, `sounds_search_none_offline_title`, `sounds_search_none_offline_body` |
| Search waiting (> 300 ms) | "Searching…" strip, old results stay | `sounds_searching` |
| Sound won't play (file unreadable) | Toast 4 s; the row returns to idle | `sound_play_failed` |
| Sound fails to download | Row failed look with retry (P3-04) | `sound_dl_failed` |
| Phone media volume is 0 when a sound starts | Toast once per app session, 2 s; the sound plays anyway | `sound_volume_low` |
| Long content (long names, 5 languages, 200% font) | Names wrap to two lines then ellipsis (full name spoken); meta wraps, never cut; tabs scroll; chips scroll; rows grow; empty-state text wraps and the screen scrolls | none |
| Permissions | None needed (previews and downloads need no permission) | none |
| First run | Trending selected, no coach marks, keyboard closed | none |

The empty-state blocks use the P0-05 look: centered, `space-10` padding, title in `display` uppercase (Anton; Teko in Hindi, P1-17), body in `body` `text-secondary`, the Secondary button below it. The query in the no-results title is the user's text, wraps to two lines then an ellipsis, and is spoken whole.

## Interactions
- **Tap the disc:** plays that sound; any other sound stops (one at a time, P1-08). Tap again pauses, and the next tap resumes where it paused. At its end the row returns to idle. If the sound isn't on the phone it downloads first (P3-04).
- **Tap the name block, or long-press anywhere on the row:** opens the details sheet (LongPress haptic for the long-press). The sheet's primary action is Use in a video meme.
- **Tap the heart:** toggles at once (the write is local). One SegmentTick haptic when it turns on. No toast: the heart is the feedback.
- **Tap Use:** the flow above.
- **Tap a tab:** switches (200 ms cross-fade of the list). Swipe sideways on the list does not switch tabs (it would fight with horizontal scrolling of chips and with rows' back gesture): tabs are tapped. Assumption; test with the owner's phone.
- **Tap a chip:** filters; "All" clears.
- **Pull down** at the top of a list: refresh (the same catalog refresh as opening). Offline: nothing happens and the strip already says why.
- **Scrolling** hides the keyboard.
- **Keyboard:** Tab reaches search, the tabs, chips and each row's four controls; Enter activates; Space on a disc plays; Escape clears a non-empty search, else does nothing.
- **Selection is white, primary is blue:** the selected tab underline and the selected chip are white; the playing disc and Use block are blue.

## Motion
- Tab content change: 200 ms cross-fade (`duration-sheet`). Reduce motion: 120 ms fade, which on Android reads as an instant swap (DESIGN_SYSTEM → Motion).
- Heart toggle, disc state, row band: 120 ms (`duration-press`).
- A row appearing from a chip or tab change: no movement, no stagger.
- Waveform progress follows the audio; with reduced motion it advances in 4 steps per second (see P3-03).
- Placeholder rows are static. Nothing loops (MOTION 2).

## Copy
English source. **Budget** is the longest the string may be in any of the five languages, counted in characters, because Indonesian, Spanish and Portuguese run about 25 to 35% longer than English and Hindi needs more width per character. Strings are in `:feature:sounds` unless noted; `sound_*` strings (the row's and sheet's) go in `:core:designsystem` or a shared module the picker (P3-05) can also use: engineer's call, but one copy, not two. Tone: short, verb first, "you" and "your", sentence case, no emoji, no exclamation marks.

| Key | English | Budget | Notes |
| --- | --- | --- | --- |
| `sounds_title` | Meme sounds | 20 | exists |
| `sounds_search_hint` | Try bruh, boom or sad violin | 32 | The examples are well-known sounds; translate by swapping for sounds people in that language know. Never a literal translation |
| `sounds_search_hint_favorites` | Search your favorites | 28 | |
| `sounds_search_hint_mine` | Search your sounds | 28 | |
| `sounds_search_a11y` | Search sounds | 20 | Name of the field |
| `sounds_search_clear_a11y` | Clear search | 20 | |
| `sounds_tab_trending` | Trending | 14 | See tab table below |
| `sounds_tab_local` | Local | 12 | |
| `sounds_tab_global` | Global | 12 | |
| `sounds_tab_favorites` | Favorites | 14 | |
| `sounds_tab_mine` | My sounds | 16 | |
| `sounds_chip_all` | All | 10 | |
| `sounds_region_line` | Sounds for {region} | 36 | {region} is a country name. Put it last or first as the language needs |
| `sounds_section_recent` | Recently used | 24 | |
| `sounds_section_favorites` | Favorites | 20 | |
| `sounds_offline_strip` | You're offline. Saved sounds still play. | 56 | |
| `sounds_searching` | Searching… | 20 | |
| `sounds_loading_status` | Loading sounds | 24 | Spoken only |
| `sounds_results_a11y` | {n} results (plural) | 24 | Spoken only. Plural forms for es and pt, one form for id and hi |
| `sounds_load_error_title` | Couldn't load sounds | 28 | Uppercase Anton in the block |
| `sounds_load_error_body` | Check your connection, then try again. | 60 | |
| `sounds_try_again` | Try again | 20 | Button |
| `sounds_refresh_failed` | Couldn't refresh. Showing saved sounds. | 56 | Toast |
| `sounds_more_loading` | Loading more | 24 | |
| `sounds_more_failed` | Couldn't load more. | 32 | |
| `sounds_empty_title` | No sounds yet | 22 | exists; uppercase |
| `sounds_empty_body` | New sounds arrive every month. Check back soon. | 60 | **Replaces** the P0 body "The meme sound library comes in a later build.", which is false once the library is live. Same key |
| `sounds_empty_trending_title` | No trending sounds yet | 28 | Uppercase |
| `sounds_empty_trending_body` | New picks arrive every month. | 50 | |
| `sounds_browse_global` | Browse global | 24 | Button |
| `sounds_empty_local_title` | No local sounds yet | 28 | |
| `sounds_empty_local_body` | We're adding sounds for {region}. Try global in the meantime. | 80 | |
| `sounds_local_unknown_title` | We don't know your region | 32 | |
| `sounds_local_unknown_body` | Pick your region to see local sounds. | 56 | Changed 11 Nov (P3-11): the region row above is the way to set it |
| `sounds_region_change`, `sounds_region_change_a11y`, `sounds_region_choose` | Change / Change region / Choose your region | 14 / 24 / 30 | P3-11 (`P3-11-region-sheet.md` has all five languages) |
| `sounds_empty_category_title` | Nothing in this category | 36 | |
| `sounds_empty_category_body` | Try another category, or see all sounds. | 60 | |
| `sounds_show_all` | Show all | 16 | Button |
| `sounds_empty_favorites_title` | No favorites yet | 28 | |
| `sounds_empty_favorites_body` | Tap the heart on a sound to keep it here. | 60 | Also the hint under "Favorites" |
| `sounds_browse_trending` | Browse trending | 24 | Button |
| `sounds_empty_mine_title` | No imported sounds yet | 28 | |
| `sounds_empty_mine_body` | Sounds you import from your files or videos show up here. They stay on your phone. | 100 | Privacy line from the PRD: never uploaded |
| `sounds_search_none_title` | No sounds for "{query}" | 40 + query | The query is the user's text, wraps, never cut |
| `sounds_search_none_body` | Check the spelling or try a shorter word. | 60 | |
| `sounds_clear_search` | Clear search | 20 | Button |
| `sounds_search_offline_strip` | Offline: showing matches from saved sounds | 56 | The PM's wording (10 Nov) |
| `sounds_search_none_offline_title` | No saved sounds match "{query}" | 48 + query | |
| `sounds_search_none_offline_body` | You're offline, so only sounds saved on this phone are searched. | 80 | |
| `sound_play_failed` | That sound won't play. Try another one. | 56 | Same words as the editor's `explain_sound_unreadable` (P1-08, Hindi fixed in its review P7) |
| `sound_volume_low` | Turn up your volume to hear it. | 40 | Toast |
| `sound_a11y_play` / `_pause` | Play {name} / Pause {name} | 40 + name | exist in SoundRow |
| `sound_a11y_favorite` | Favorite {name} | 40 + name | A toggle; the spoken state is "on" or "off" |
| `sound_a11y_use` | Use {name} in a new video meme | 56 + name | Spoken name of the Use block on this screen |
| `sound_a11y_details_hint` | Show details | 20 | Click label of the name block |

**Tab labels in the five languages** (machine drafts; P5-06 has the final say; the tightest strings on the screen):

| English | id | es | pt | hi |
| --- | --- | --- | --- | --- |
| Trending | Tren | Tendencias | Em alta | ट्रेंडिंग |
| Local | Lokal | Local | Local | स्थानीय |
| Global | Global | Global | Global | ग्लोबल |
| Favorites | Favorit | Favoritos | Favoritos | पसंदीदा |
| My sounds | Suaraku | Mis sonidos | Meus sons | मेरी आवाज़ें |

Hindi: sentences use the Devanagari line height the P1-08 review already checked at 200% (`label` 13/16 and `caption` 11/14 rendered whole); recheck the chips, meta and the strips in the first Hindi render.

## Accessibility
- **Contrast:** `text` on `canvas` 16.9:1; `text-secondary` on `canvas` or `surface` 5.7:1 or more; `text-muted` on `surface-raised` (placeholder, "Loading more") 4.6:1 or more; the playing band: `text` 12.4:1 and `primary` 5.9:1 on `surface-raised` (a sheet) and 7.3:1 on `surface`. `danger` words are used only on `canvas` or `surface` (5.2:1 or more; on `surface-raised` it is 4.3:1, so never there).
- **Targets:** every control 48 dp or more: the search field, Clear, tabs (raised from the board's 44), chips (hit area), disc, name block, heart, Use block.
- **Names (WCAG 2.5.3 Label in Name):** tabs are tab nodes: "Trending, tab, 1 of 5, selected"; a chip is "{category}, toggle button, selected". The disc "Play {name}" / "Pause {name}". The name block: its text "{name}, {meta}", with click label "Show details". The heart "Favorite {name}", checkable (the spoken state "on/off" is the toggle's own: nothing else goes in the state description, P1-11 review). The Use block "Use {name} in a new video meme".
- **Row actions for TalkBack** (the rule: long-press is never the only route): Details, Favorite or Remove from favorites, Use in a new video meme, Report (in the sheet). The row's reading order is disc, name block, heart, Use.
- **Order:** title, search field, Clear, tabs, chips, strip, rows. A status strip is announced politely when it appears; "Searching…" and the result count are polite status messages (SC 4.1.3); none takes focus.
- **Focus:** closing the details sheet returns focus to the row's name block. After a chip or tab change focus stays on the control. After Clear, focus goes to the search field. After "Show all" and "Clear search", focus goes to the first row (or the field).
- **200% font:** tabs and chips scroll; names take two lines and meta takes as many as it needs; the playing row's waveform keeps its dp size; the search placeholder may cut with an ellipsis (it is a hint; the field's name is spoken in full); empty-state text wraps and the screen scrolls, never clipping its button (P1-12 rule: give the scrolling column `heightIn(min = viewport)`).
- **Reduce motion:** see Motion. **Switch Access / keyboard:** every control above is reachable; no swipe-only or long-press-only action.
- **Sheets:** the screen behind the details sheet leaves TalkBack and keyboard focus with `Modifier.coveredByModal` (P1-08 F1).

## Analytics
Events from the PRD; no query text, no names, no personal data.
- `sound_preview {sound_id, tab}` once per tap that starts playback (not for resume after pause, not for a replay at the end). `tab` is `trending`, `local`, `global`, `favorites` (the Favorites section), `recent` (the Recently used section), `my_sounds`, or `search` (any row in search results).
- `sound_add {sound_id, tab, editor: "video"}` when the Use flow lands the sound at 0:00, with the same `tab` values (PM, 10 Nov: `recent` and `search` are new values of the existing parameter).
- `project_create {editor: video, source: gallery}` when the Use flow's editor opens (P1-02 rules).
- `sound_report` is in P3-10.
- `sound_search {results, offline}` (count and a flag, never the text) once per settled query: in the PRD since 11 Nov (PM). Updated 11 Nov: this line used to call it a proposal.
- **Not an event:** `sound_favorite {sound_id, on}` (decided 11 Nov: not added).

## QA compares
Screenshots at 360 dp wide, English unless noted, in `docs/ux/reviews/P3-02/`:
1. `trending`: default with chips and one row playing (waveform, band).
2. `trending-hi-200`: Hindi at 200% font, a long meta wrapped, tabs scrolled to the selected one.
3. `local`, `local-empty`, `local-no-region`: the region line and the two empty states.
4. `search-typo`: "bruhh" with "Bruh" in the results (online); `search-none`; `search-offline` and `search-none-offline`.
5. `favorites`: both sections; `favorites-empty`; `favorites-unfavorited-row` (heart off, row still there).
6. `offline`: strip, saved rows normal, unsaved rows dimmed (P3-04).
7. `loading` (placeholder rows), `load-error`.
8. `my-sounds-empty`.
Also by recording or note: tab cross-fade, TalkBack on a row (disc, name block, heart, Use), Back clearing the search first.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on.
1. **What does the blue block on a Sounds-tab row do?** The PRD's "tap + to place the sound at the playhead" is about the editor's picker (P3-05); the tab itself has no playhead.
   - **A. Recommended: start a new video meme with the sound** (photo picker, then the editor with the sound at 0:00). Keeps "sound first" and removes the dead end of a browse-only screen. Costs a small new ticket (the Create video flow carrying a pending sound; an S to M, for the product manager to size). Until it exists, the block is not drawn and the details sheet has no Use button.
   - B. No block on the tab; the library is for listening, favoriting and reporting, and adding happens only in the editor. Cheapest. The tab then has no action that leads to a meme.
   - C. Ask which draft to add it to. More choices, a longer path; not recommended.
2. **Category chips "Local" and "Trending".** The PRD's category list includes both, but they are tabs. **Recommended: the catalog doesn't offer them as chips** (no duplicate control; the chip row shows All plus the other eight). Alternative: keep them as chips that jump to the tab.
3. **Recently used shows 5 rows inside the Favorites tab** (**recommended**, no extra screen). Alternative: a "Recent" chip-like switch at the top of Favorites, or a "see all" (more build, more taps).
4. **Search scope on Favorites and My sounds filters that list only**, while the other tabs search everything (**recommended**; the placeholder says which). Alternative: always search everything, which a Favorites user wouldn't expect.
5. **Swipe between tabs is off** (recommended, to keep the chip row and scrolling predictable). It costs one tap per tab change. Alternative: allow it and drop sideways scrolling of chips into a wrapped grid.
6. **Support email for copyright owners** is in P3-10's questions.

## Requests to the principal mobile engineer
- New tokens (above and in DESIGN_SYSTEM → Size): `MemixSize.waveformBarWidth`, `waveformBarGap`, `waveformHeight`, `waveformHeightLarge`. No other new token.
- New icons to draw: `search`, `flag` (DESIGN_SYSTEM → Iconography). Retry reuses `rotate_right`.
- `SoundRow` gains the name-block button, the optional Use block, the waveform slot, the failed, downloading, offline and unavailable looks and `onSurface` (already built). Draw the waveform in the draw phase (no recomposition per frame, mobile-performance skill).
- The shared strings above in the five `strings.xml` files; `sounds_empty_body` changes text.
- The README of SoundRow was updated with the details entry, the waveform and the download states.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| A visible search field with a magnifier icon is recognised without a label | Guidance | [NN/g: Search, visible and simple](https://www.nngroup.com/articles/search-visible-and-simple/) |
| Status messages (searching, result count, offline note) must be announced without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Visible label must be in the accessible name | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Text must resize to 200% without loss | Guidance | [WCAG 2.2 SC 1.4.4](https://www.w3.org/WAI/WCAG22/Understanding/resize-text.html) |
| Graphics needed to understand content (the waveform) need 3:1 | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| Typo tolerance through trigram similarity: "bruhh" and "bruh" share 4 of 7 trigrams (0.57, above pg_trgm's default 0.3) | Assumption (computed by hand; the backend engineer confirms with the RPC) | PostgreSQL `pg_trgm` docs |
| Users tap a sound's name expecting details, a disc expecting play; a five-row Recently section fits one screen | Assumption (test it) | none; add to the first user test below |

## Test script for the owner (5 people, a phase build)
Tasks: (1) "Find the sound that goes bruh and hear it." (2) "Keep it so you can find it fast later." (3) "Find a sad trombone, misspelled however you like." (4) "Put a sound on a video." (5) "With airplane mode on, find a sound you heard before." Observe: whether people tap the name expecting play; whether the Favorites tab is found; whether Use is understood as "make a new meme"; what they do on the offline strip. Success: tasks 1 and 2 under 15 s each; 4 under 45 s. Notes template: task, time, taps, what they said, where they hesitated.
