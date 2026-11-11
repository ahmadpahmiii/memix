# Sound preview, waveform and details sheet · P3-03

Part of the Sounds library set; read `P3-02-sounds-library.md` first (screen, row, tabs, search). Download states are in `P3-04-download-states.md`; Report is in `P3-10-report-a-sound.md` (it is a pane of this sheet).

## Job
"Hear it before I use it, and know where it's from." **Done when** (TICKETS): a cached sound starts playing within 300 ms. Details exist so the license and credit are always one tap from a sound (PRD: "Sound details show the source credit"; a CC-BY credit is a license condition).

**The one blue action:** on the sheet, **Use in a video meme** (Primary, the only Button). The play disc is blue because it is a meme sound.

## Entry and exit
- **Opens from:** a tap or a long-press on the name block of a row of the Sounds tab (P3-02); the long-press is on the name block only, not the whole row, so it doesn't fight the disc and heart (review P3-03, 11 Nov 2026). The editor's picker (P3-05) opens the same sheet from its rows; there the Primary is **Add to video** and lands at the playhead (P3-05 spec decides the rest; this sheet only takes the label and action as parameters).
- **Closes:** close button, scrim, drag down, system back, Escape. Back from the Report pane returns to the details pane first (P3-10). Nothing changes on close. Focus returns to the row's name block (Sheet README).
- **Playback continues** if the sound was playing when the sheet opened, and keeps playing when it closes; the row shows the state. One sound plays at a time across rows and the sheet (one player, P1-08). Leaving the Sounds tab or the app stops it.
- **Kept:** nothing beyond what the actions inside do (favorite, report flag).

## Layout
No board; the Sheet component (`design/system/components/Sheet/README.md`): `surface`, `radius-lg` top corners, `shadow-sheet`, grabber, close button. Standard height with the long-content rule (the middle scrolls, the title row and the Primary stay). `Modifier.coveredByModal` behind it. It is a **content sheet**, not half-height (a short sheet).

Top to bottom, `space-4` padding:
1. **Title row:** the sound's name in `title`, up to two lines (the full name is spoken). At the end, the **heart** (same toggle as the row, 48 dp). The sheet's close button sits in the sheet frame as for every sheet.
2. **Player row** (`space-3` below): the play disc (40 dp visual, 48 dp target, same states as the row), then the **large waveform** (`waveform-height-large`, 56 dp) filling the width, then the time "0:01 / 0:02" in `timecode` `text-secondary` at the end. The waveform is drawn as in the row (below) but taller; it is not a scrub control (tapping it does nothing; the disc plays and pauses). The time updates with playback.
3. **Facts** (`space-6` below): a list of label-and-value pairs, label in `label` `text-secondary`, value in `body` `text`, `space-3` between pairs, each pair stacked label over value so long values wrap:
   - **Category:** the catalog category name (hidden for My sounds).
   - **Credit:** the sound's `credit` text exactly as stored ("Sad trombone by Name, Freesound"). Wraps, never cut, not translated. Hidden when empty (CC0 without a credit still shows License).
   - **License:** the license name and one plain sentence (copy below). Hidden for My sounds.
   - **Source:** the host of `source_url` ("freesound.org") as a link in `primary` (7.3:1 on `surface`), opening the page in the browser. Catalog sounds only. If no browser can open it: toast `sound_detail_source_failed`.
   - **On this phone:** the saved state, with a `check` in `success` for "Saved on this phone" and "Included with Memix", or `text-secondary` words alone for "Saves when you play or add it" (P3-04).
4. **Use in a video meme** (`space-6` above): the Primary Button, full width, at least `touch-target` tall. Disabled look, not hidden, is not used: when a sound can't be used (removed from the catalog, or not on the phone while offline) the button is hidden and a line explains (`sound_detail_unavailable_note`, `sound_detail_offline_note`).
5. **Report this sound:** a Quiet text button below the Primary, centered, `flag` icon (20 dp) and label in `text-secondary`, 48 dp tall. After a report, it is replaced by the plain line "Reported. Thank you." Hidden for My sounds.

The Primary is pinned at the bottom with the Report row under it; the facts scroll. At 200% font the Primary and Report remain reachable: if both together exceed 40% of the window height (`pinned-bar-max-fraction`), Report scrolls with the content and only the Primary stays pinned (the pack builder rule, P2-09).

## The waveform (P3-03)
- **Data:** the sound's peaks from `waveform_path` (P3-01 loads it with the row; about 32 to 64 values 0 to 1, normalized per sound so every sound shows its own shape, not its loudness).
- **Drawing:** bars `waveform-bar-width` wide with `waveform-bar-gap` between, `radius-full` ends, centered on a midline. The number of bars is however many fit the width, resampling the peaks (max in each bucket). Bar height is the peak times `waveform-height` (row) or `waveform-height-large` (sheet), never below `waveform-bar-width`, so silence is a dotted line, not a gap.
- **Colors:** played bars `primary`; the rest `text-muted`. Both are at least 3:1 on `canvas`, `surface` and `surface-raised` (WCAG 2.2 SC 1.4.11: `primary` 5.9:1 or more, `text-muted` 4.6:1 or more); the played/unplayed edge is also position, so it is not color alone.
- **In the row:** only the playing row shows it, under the name, in place of the P1-08 thin rail, at the text block's width. Idle rows show no waveform (500 sounds of 32 bars would be noise and cost draw time).
- **No peaks** (the starter pack has none until P3-09, or the file failed to load, or still loading): a 4 dp (`rail-height`) rail in `hairline` with a `primary` fill, as the P1-08 row had. Nothing is said: the rail is a normal look, not an error.
- **Progress:** drawn in the draw phase from the player's position, up to 30 updates a second; no recomposition per frame (mobile-performance skill). With reduced motion: 4 updates a second.
- **Not an `Image`:** the waveform is decorative for screen readers (hidden); the playback state is in the disc and in the time.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Default, sound saved | Title, player row, facts, Use, Report | strings below |
| Playing | Disc filled with pause; waveform filling with `primary`; time updating | `sound_a11y_pause` |
| Not saved, online | "Saves when you play or add it"; play downloads first (P3-04 states appear in the player row) | `sound_detail_not_saved` |
| Downloading (> 300 ms) | The rail under the player row replaces the waveform's played part; the disc is Cancel; the time reads "Downloading…" | `sound_dl_downloading` |
| Download failed | The time slot reads the failure; the disc is retry | `sound_dl_failed` |
| Not saved, offline | Disc disabled; the line explains; Use hidden | `sound_detail_offline_note` |
| Removed from the catalog | Title, facts as last known, disc disabled, Use hidden, the line explains; heart still works; Report hidden (nothing left to report) | `sound_detail_unavailable_note` |
| My sound | No category, license, source or Report. Credit shows "Imported" or "From a video" | `sound_detail_mine_imported`, `sound_detail_mine_video` |
| Facts missing (no credit, no category) | The pair is not drawn; nothing says "none" | none |
| Reported already | Report row replaced by the line | `sound_detail_reported` |
| Loading, error | None: the sheet opens with data the row already has; only the waveform may still be loading (rail meanwhile) | none |
| Long content | Names and credits wrap; facts scroll; Primary and Report pinned (rule above) | none |
| First run | Same | none |

## Interactions
- **Disc:** play, pause, resume, as the row. At the end it returns to idle; the next tap plays from the start.
- **Heart:** as the row; the row behind updates.
- **Source link:** opens the browser (Android `ACTION_VIEW`); the app stays open behind it.
- **Use in a video meme:** closes the sheet and starts the flow of P3-02 (photo picker, then the editor).
- **Report this sound:** swaps the sheet's content to the Report pane (P3-10), 200 ms cross-fade.
- **Drag up** on the grabber or title row: the sheet grows within the long-content rule; **drag on the facts** scrolls them.
- No haptics except the heart's SegmentTick.

## Motion
Sheet slides in and out over `duration-sheet` (200 ms); reduce motion, a 120 ms fade. Pane swap: 200 ms cross-fade (120 ms with reduce motion). The disc and heart state changes 120 ms. Progress as above.

## Copy
Budget is the longest allowed in any of the five languages. Notes: licence sentences are shown to users as a statement of rights, so they get a legal read before launch (PRD risk table "Sound licensing"; the lawyer review) and the machine drafts must not be shipped unreviewed after P5-06. Credits and source hosts are never translated.

| Key | English | Budget | Notes |
| --- | --- | --- | --- |
| `sound_detail_category` | Category | 16 | Label |
| `sound_detail_credit` | Credit | 14 | Label |
| `sound_detail_license` | License | 14 | Label |
| `sound_detail_source` | Source | 14 | Label. The value is a host, e.g. freesound.org |
| `sound_detail_device` | On this phone | 20 | Label |
| `sound_license_cc0` | CC0. Free to use, no credit needed. | 56 | "CC0" stays as written |
| `sound_license_cc_by` | CC BY. Free to use with credit to the author. | 64 | "CC BY" stays as written |
| `sound_license_royalty_free` | Royalty-free. Free to use in your videos. | 60 | |
| `sound_license_own` | Recorded by Memix. Free to use. | 48 | The `own` license type |
| `sound_license_licensed` | Licensed to Memix for use in your videos. | 60 | The `licensed` type |
| `sound_detail_saved` | Saved on this phone | 32 | `check` in `success` |
| `sound_detail_included` | Included with Memix | 32 | The starter pack |
| `sound_detail_not_saved` | Saves when you play or add it | 40 | |
| `sound_detail_use` | Use in a video meme | 28 | Primary. In the picker: `sound_detail_add_to_video` "Add to video" |
| `sound_detail_report` | Report this sound | 28 | |
| `sound_detail_reported` | Reported. Thank you. | 32 | |
| `sound_detail_source_failed` | Couldn't open the page. | 40 | Toast |
| `sound_detail_unavailable_note` | This sound was removed from the library. | 60 | |
| `sound_detail_offline_note` | You're offline. Connect to play or use this sound. | 72 | |
| `sound_detail_mine_imported` | Imported | 16 | |
| `sound_detail_mine_video` | From a video | 20 | |
| `sound_detail_close_a11y` | Close | 12 | Exists in Sheet |
| `sound_detail_source_a11y` | Open {host} in your browser | 48 + host | Spoken name of the link |

## Accessibility
- **Sheet:** title is the pane title; focus moves to the sheet on open; the screen behind is out of reach (`coveredByModal`); closing returns focus to the opener.
- **Order:** title, heart, disc, time, then facts top to bottom (label and value read as one: "Credit, Sad trombone by …"), Source link, Use, Report. The waveform is hidden from TalkBack.
- **Names:** disc "Play/Pause {name}"; heart "Favorite {name}"; link "Open {host} in your browser" (the visible text is the host, so the name contains it, SC 2.5.3); Use "Use in a video meme" (equal to its label).
- **Contrast:** `primary` link on `surface` 7.3:1; `success` `check` on `surface` is an icon beside words (the words carry the meaning); value text `text` 12.4:1; labels `text-secondary` 5.7:1; Report `text-secondary` on `surface` 5.7:1.
- **200% font:** values wrap, the time may wrap under the waveform's row (the row becomes disc and waveform on one line, time under it) before anything is cut; Use and Report follow the pinned rule.
- **Reduce motion:** progress in steps; fades as in Motion.

## Analytics
- `sound_preview {sound_id, tab}`: a play started from the sheet fires with the `tab` of the screen that opened it (`search` when opened from search results; `recent`/`favorites` by section). Resume and replay don't fire (same rule as the row).
- `sound_report` is P3-10's.
- Opening the sheet logs nothing.

## QA compares
Screenshots in `docs/ux/reviews/P3-03/`:
1. `row-playing`: waveform in the row, played/unplayed split, the band.
2. `row-playing-norails`: a sound without peaks (rail fallback).
3. `details-ccby`: a CC-BY sound with credit, license sentence, source link, saved state; and `details-cc0` (no credit).
4. `details-notsaved-online` and `details-offline-notsaved`.
5. `details-mine`.
6. `details-hi-200`: Hindi at 200% font; Use and Report reachable.
7. `details-removed`.
Timing: a cached sound starts within 300 ms (the Done when), measured by the engineer on the owner's phone and noted in the ticket's commit message.

## Questions for the owner
1. **Opening the sheet from the name block (recommended)** vs. a visible "more" button on every row. A button costs about 48 dp of the 170 dp that names and credits have, so a CC-BY credit wraps to three lines at 100%. If the first user test shows people tap the name expecting play, the fix is that the name block plays and a chevron appears at the row's end; it is a one-component change.
2. **Source link on every catalog sound (recommended)**: CC-BY expects a link to the work, and a visible source keeps the library honest. Costs nothing to build. If the owner prefers the host hidden, the credit text alone remains.
3. **License sentences are drafted by the designer, not by a lawyer.** Recommended: ship them as is for internal testing and get the lawyer review (PRD risks) before the closed test.
