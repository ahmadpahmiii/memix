# Home: trending sounds for your region · P3-14

Part of the Phase 3 specs. Read first, and not repeated here:
- `P0-05-navigation-and-browse-screens.md` → Home: the top row, hero and entry cards. Its note "when the trending rows arrive, the block moves back to the top and the cards return to the board's size" is carried out here.
- `P3-02-sounds-library.md`: the SoundRow, the placeholder rows, the offline strip, the Trending tab's list (the same list feeds this row).
- `P3-03-sound-preview-and-details.md`: the playing row's waveform and the details sheet (it opens over Home from a row's name).
- `P3-04-download-states.md`: Downloading, Failed, Offline and Unavailable row looks.
- `P3-15-use-a-library-sound.md`: the Use flow this row's add action **is**. P3-15 is built first (TICKETS).
- `P3-11-region-sheet.md`: the region that picks the list and the button that changes it.
- Board: `design/screens/Main.dc.html`, the "Trending sounds" section (heading, "See all", rows). Differences are listed under Layout.

## Job
"Show me what is funny right now" and "put it on a meme in one move". The home screen's second job after picking an editor (PRD → App structure: "trending sounds for the user's region"). **Done when** (TICKETS): the Home row shows the region's trending sounds from the catalog, a tap plays a preview, and the row still shows the cached sounds offline.

**The one blue action:** unchanged, the **Video meme** card. The row adds meme-sound blue only (play discs, the Use blocks), as the system colors sounds. No Secondary or Primary button is added except the error state's Secondary "Try again".

**Tap count (Sound first):** hear a trending sound: **1** (the disc). Put it on a new meme: Use (1), a clip in the system picker (2), the picker's Add (3): the editor opens with the sound at 0:00 (P3-15).

## Entry and exit
- **Arrives from:** the Home tab (P0-05); the section is part of Home, not a screen.
- **Exits:** **Use** starts P3-15's flow (picker, then the editor); closing the editor returns to Home (P3-15). **The name block** opens the details sheet over Home; closing it returns to the same row. **See all** switches to the Sounds tab with **Trending** selected (whatever tab the user left selected there). A row's disc stays on Home.
- **Kept:** Home's scroll position while the app is open and the user moves between bottom-nav items (P0-05). **Not kept:** a playing preview (it stops when the user leaves Home, as everywhere: P1-08).
- **System back** on Home leaves the app, as P0-05; a playing preview stops with the screen.

## Layout
Home becomes a **top-aligned, scrolling column** from this ticket on (P0-05 centered it only while it had one job): top row, hero, entry cards, then the section. Side gutters `space-4`.

1. **Top row, hero, entry cards:** as P0-05, except: the region is the **button** of P3-11 (globe, name, chevron, 48 dp); the hero sits `space-5` under the top row; the entry cards return to **the board's size** (their own minimum height, no 4:5 stretch), side by side, `space-3` gap. Nothing about their color or text changes.
2. **Section heading row** (`space-6` under the cards), `touch-target` tall: "Trending sounds" in `title` (a heading) at the start and **See all** at the end, a Quiet Button (`primary` text, 7.3:1 on `canvas`). At large font **See all** wraps under the heading, never cut. The board's 18 px heading is the `title` token.
3. **Status strip** (only offline with cached rows, below): `sounds_offline_strip`, `surface-raised`, `radius-md`, `label` `text`, `space-3` padding, `space-2` below the heading row.
4. **The rows:** up to **5** SoundRows on `canvas`, no dividers (the board's hairlines are dropped: depth is by value and spacing, as on the Sounds tab), row height at least 64 dp (`touch-target` plus `space-2` above and below), growing with text. Each row is P3-02's row **without the heart**: `[play disc] [name + meta] [Use]` (the SoundRow's optional heart is off). The Use block is the 36 dp `primary` block with `create` in `on-primary`, 48 dp target. Name `body-strong`, up to two lines; meta `label` `text-secondary`, "{duration} · {credit}", wrapping and never cut (a CC-BY credit is a license condition). The playing row steps up to `surface` and shows the waveform (P3-03).
5. **Space under the last row:** `space-6`, then the bottom navigation (content scrolls above it, never under it).

**What the list is:** the same sounds as the Trending tab, in the same order: this month's trending for the user's region (P3-11), then global, de-duplicated (TECHNICAL_DESIGN → Trending), cut to the first five. Five are enough to scan and keep the blue small (Questions 1).

**How it fits:** at 360 × 640 (552 dp between the status bar and the navigation) the top row, hero, cards, heading and **three rows** are on screen (about 536 dp); the other two are a scroll away. At 360 × 800 all five fit. Templates (P4-05) will add "Trending templates" above this section, as the board orders them; P4-05 decides what that does to the row's position.

**Differences from the board:** the board's two sample rows ("Boom hit", "Sad trombone") are replaced by real catalog rows; the board's add button is a plus on a 44 px block, here the Use block (`create`, 36 dp, 48 dp target) because its action is "new meme with this sound", not "add to a timeline" (P3-02 Question 1); the board's hairline between rows goes; "See all" is a Quiet button with a 48 dp target.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Default (online, data, saved or not) | Heading, See all, up to five rows | `home_sounds_title`, `home_sounds_see_all` |
| **Loading** (more than 300 ms, nothing cached) | The heading with **three** static placeholder rows (a 40 dp `surface-raised` disc, two `surface-raised` bars at 60% and 35% of the text width, `radius-full`), no shimmer, no spinner; a polite status for screen readers. Under 300 ms: nothing is drawn (the section is the last thing on the screen, so it appearing moves nothing above it) | `home_sounds_loading` |
| Refreshing (cached rows shown) | Nothing extra: the list updates in place; rows already on screen do not move | none |
| **Offline, cached** | The strip, then the cached rows. A row whose audio is on the phone is normal; one that is not is dimmed with "Not saved for offline", its disc disabled and **no Use block** (its 48 dp slot stays empty, so names don't jump when the connection returns); P3-04's table | `sounds_offline_strip`, `sound_not_saved_offline` |
| **Offline, nothing cached ever** | The heading (no See all) and one `surface-raised` message block, no button | `home_sounds_offline` |
| **Load error, nothing cached, online** | The heading (no See all), the message and a Secondary **Try again** (re-runs the load; on success the rows replace the block) | `home_sounds_error`, `sounds_try_again` |
| Refresh error, cached rows shown | The cached rows stay; no toast (Home would otherwise greet a user with a complaint) | none |
| **Empty** (the catalog has no trending for the region or for global) | **The whole section is not drawn**: no heading, no empty block. Nothing on Home says content is missing (P0-05: nothing is shown that isn't real); the Sounds tab has its own empty states. Home stays top-aligned, with space under the cards | none |
| Row, Downloading, Failed, storage full | P3-04's looks, the same as on the Sounds tab; a Use tap that needs the file starts the download then the picker (P3-15) | `sound_dl_*` |
| Sound won't play | Toast 4 s (P3-02), row returns to idle | `sound_play_failed` |
| Picker cancelled after Use | Home exactly as it was: scroll, rows, preview stopped | none |
| Region changed (P3-11) | The rows cross-fade to the new region's list (200 ms); the playing preview, if any, stops | `region_changed_a11y` |
| Permission | None: previews and downloads need none | none |
| Long content, 5 languages, 200% font | The heading wraps, See all goes under it; rows grow; names two lines then an ellipsis (full name spoken); meta wraps; the page scrolls; entry cards grow as P0-05 | none |
| First run | The section loads behind the Region sheet (P3-11) with the detected region and updates in place after Continue if the user picked another. No coach marks | none |

## Interactions
- **Tap the disc:** plays that sound, any other stops (one at a time); tap again pauses; at its end the row returns to idle. If the sound is not on the phone it downloads first (P3-04). Fires `sound_preview`. **The PM's "a tap plays a preview" is the disc**, as everywhere (P3-02); the first user test checks whether people tap the name expecting play.
- **Tap the name block, or long-press anywhere on the row:** the details sheet (P3-03) over Home; its Primary is **Use in a video meme**; LongPress haptic for the long-press. Report works from there.
- **Tap Use:** P3-15's flow: a preview in progress stops; the sound downloads if needed (the row shows Downloading after 300 ms); the system picker opens; the editor arrives with the sound at 0:00. No haptic on the tap.
- **See all:** a screen switch to the Sounds tab, Trending selected, no animation beyond the nav item's 120 ms color change (P0-05).
- **Pull to refresh:** none on Home (P0-05 has no list to refresh); the catalog refreshes on open, the cache is shown first.
- **Scrolling** is plain vertical scroll of the column; the entry cards scroll away with it.
- **Keyboard:** Tab reaches the region button, the cards, See all and each row's three controls; Enter activates; Space on a disc plays.
- **Selection is white, primary is blue:** nothing on this section is selected; the discs and Use blocks are blue because they are meme sounds.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Rows appear after loading | A 120 ms fade; no stagger, no movement | same |
| Rows change after a region change | 200 ms cross-fade (`duration-sheet`) | 120 ms fade |
| Row states (disc, band, downloading) | 120 ms (`duration-press`) | same |
| Waveform progress | follows the audio, up to 30 updates a second | 4 a second |
| Details sheet | 200 ms slide over a fading scrim | 120 ms fade |
Placeholder rows are static; nothing loops (MOTION 2). On Android "reduce motion" ends every animation on the next frame, so these cells show as instant changes there.

## Copy
English source; id, es, pt and hi are machine drafts until P5-06. **Budget** = longest allowed in any of the five languages, in characters. Tone: short, "you", sentence case, no emoji, no exclamation marks. Row strings (`sound_meta`, `sound_a11y_*`, `sound_dl_*`, `sound_not_saved_offline`, `sounds_offline_strip`, `sounds_try_again`) are P3-02, P3-03 and P3-04's, one shared copy.

| Key | English | Budget | id | es | pt | hi |
| --- | --- | --- | --- | --- | --- | --- |
| `home_sounds_title` | Trending sounds | 26 | Suara tren | Sonidos en tendencia | Sons em alta | ट्रेंडिंग साउंड |
| `home_sounds_see_all` | See all | 14 | Lihat semua | Ver todos | Ver todos | सभी देखें |
| `home_sounds_see_all_a11y` | See all trending sounds | 36 | Lihat semua suara tren | Ver todos los sonidos en tendencia | Ver todos os sons em alta | सभी देखें: ट्रेंडिंग साउंड |
| `home_sounds_loading` | Loading trending sounds | 36 | Memuat suara tren | Cargando sonidos en tendencia | Carregando sons em alta | ट्रेंडिंग साउंड लोड हो रहे हैं |
| `home_sounds_error` | Couldn't load trending sounds. | 48 | Suara tren tidak bisa dimuat. | No se pudieron cargar los sonidos en tendencia. | Não foi possível carregar os sons em alta. | ट्रेंडिंग साउंड लोड नहीं हुए। |
| `home_sounds_offline` | You're offline. Trending sounds show up when you're back online. | 90 | Kamu offline. Suara tren muncul saat kembali online. | Sin conexión. Los sonidos en tendencia aparecen al volver a estar en línea. | Você está offline. Os sons em alta aparecem quando a conexão voltar. | आप ऑफ़लाइन हैं। ऑनलाइन होने पर ट्रेंडिंग साउंड दिखेंगे। |

Notes for translators: "Trending" is what is popular now (the same word as the Sounds tab, `sounds_tab_trending`: id Tren, es Tendencias, pt Em alta, hi ट्रेंडिंग); keep the heading consistent with it. "See all" opens the full list, so "Ver todos" matches the heading's plural. Hindi: check the heading and See all at 200%.

## Accessibility
- **Order:** wordmark, region button, hero heading, Video meme, Photo meme, "Trending sounds" heading, See all, then each row (disc, name block, Use). The heading is a heading node (screen-reader heading navigation).
- **Names (WCAG 2.5.3):** See all, name `home_sounds_see_all_a11y` (contains the visible words; in Hindi the visible text comes first); the disc "Play {name}" / "Pause {name}"; the name block reads "{name}, {meta}" with the click label "Show details"; the Use block "Use {name} in a new video meme" (`sound_a11y_use`). A dimmed offline row reads its meta ("Not saved for offline") with the name, which is the "why" for the missing block.
- **TalkBack row actions** (long-press is never the only route): Details, Use in a new video meme, Report (in the sheet).
- **Announcements (polite, SC 4.1.3):** "Loading trending sounds" when the placeholders show, "Downloading {name}" and "Couldn't download {name}" (P3-04), the offline strip when it appears; none takes focus. After a region change, `region_changed_a11y` (P3-11).
- **Focus:** closing the details sheet returns focus to the row's name block; after the picker is cancelled, focus returns to the Use block that started it; after Try again succeeds, focus goes to the first row.
- **Contrast:** `text` on `canvas` 16.9:1; meta `text-secondary` 5.7:1; the Use block `on-primary` on `primary` 7.9:1; See all `primary` on `canvas` 7.3:1; the strip `text` on `surface-raised` 12.4:1; the playing band as P3-02. Dimmed offline text `text-muted` 4.6:1 or more.
- **Targets:** every control 48 dp or more (region button, See all, disc, name block, Use).
- **200% font and reduce motion:** see States and Motion; no gesture-only action.

## Analytics
Events from the PRD; no names, no query text.
- `sound_preview {sound_id, tab}` once per tap that starts playback (not for resume or replay). **`tab` for a Home row: `home`** (in the PRD since 11 Nov).
- `sound_add {sound_id, tab, editor: "video"}` from P3-15, with the same `tab` value, when the editor opens with the sound on the timeline; `project_create {editor: "video", source: "gallery"}` as in P3-15 / P1-02. Order: `project_create`, then `sound_add`.
- Not events: opening the section, See all, the details sheet, a failed load.

## QA compares
Screenshots at 360 dp wide, English unless noted, in `docs/ux/reviews/P3-14/`:
1. `home-default` at 360 × 640 (three rows visible) and at 360 × 800 (five rows).
2. `home-playing`: a row playing with its waveform and band.
3. `home-loading` (throttled network on a fresh install): the heading with three placeholders; `home-error` (online, server down) and `home-offline-nothing` (airplane mode on a fresh install).
4. `home-offline-cached`: the strip, saved rows normal, unsaved rows dimmed with no Use block and names aligned.
5. `home-hi-200`: Hindi at 200% font; `home-es`: Spanish (the longest heading).
6. `home-empty`: no trending anywhere: no section, cards at the top.
By recording or note: the Use flow from Home (three taps counted: Use, a clip, Add), back from the editor to Home, a region change updating the rows, TalkBack through a row, the details sheet over Home.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on.
1. **How many sounds on Home?**
   - **A. Recommended: five.** Enough to scan; three show on the smallest phone and the rest are a scroll away. One constant.
   - B. Three. Less blue next to the Video meme card and no scroll on a small phone, but a thinner row.
   - C. Ten, or a horizontal strip. More choice, more to scroll past; a strip hides the names and the Use block.
2. **Home becomes top-aligned and scrolling** (decided, a consequence of P0-05's own note). The centered hero was for a Home with one job.
3. **Nothing is drawn when the catalog has no trending sounds** (recommended) rather than a "coming soon" block, so Home never advertises missing content. If the owner prefers to show the section with a message, it is one block (the offline wording).
4. **`tab: home`** for events from this row: accepted by the PM on 11 Nov and in the PRD.

## Requests to the principal mobile engineer
- **Home layout:** a vertical scroll column, top-aligned; remove the vertical centering and the 4:5 card minimum (P0-05); entry cards at the board's size.
- **Section:** a `TrendingSoundsSection` fed by the same P3-01 trending use case as the Trending tab (first five); the SoundRow with no heart; one shared row, strings and states with the Sounds tab (P3-02, P3-04); placeholders after 300 ms; the offline strip; the section absent when empty; details sheet hosted over Home.
- **Use:** the row's Use block and the details Primary call P3-15's pending-sound flow; the `tab` constant (`home` or `trending`).
- **Strings:** the six keys above in the five `strings.xml` files.
- **No new token. No new icon.** (`create` is the Use block's icon, as on the Sounds tab.)

## Tokens
None new. Uses `title`, `label`, `body-strong`, `primary`, `on-primary`, `surface-raised`, `canvas`, `surface`, `radius-md`, `touch-target`, `space-*`, `duration-sheet`, `duration-press`, `waveform-*`.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| A list that updates in place under cached content keeps the screen steady | Guidance | [Material 3: loading and progress](https://m3.material.io/components/progress-indicators/guidelines), and the P3-02 refresh rule |
| Status messages (loading, offline) reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| A visible label must be in the accessible name | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| Hiding an action that cannot work, and saying why in the row's text, beats a dead button | Guidance | [Material 3: disabled states](https://m3.material.io/foundations/interaction/states/applying-states), P3-04's table |
| Three rows fit under the cards on a 360 × 640 phone | Assumption (computed by hand: 68 + 72 + 156 + 48 + 192 = 536 of 552 dp; QA measures) | none |
| Showing trending sounds on the home screen raises sound use | Assumption (product reasoning; the tab-`home` events measure it) | none |

## Test script for the owner (5 people, a phase build)
Use P3-02's script (task 4, "Put a sound on a video") from Home: "From the first screen, put a sound you like on a new video." Observe: whether people tap the disc, the name or Use first; whether they find the row without scrolling; whether the Use block reads as "new meme" or as "favorite". Success: under 45 s, no help.
