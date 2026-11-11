# Download manager: what the user sees · P3-04 (UI states only)

Part of the Sounds library set; see `P3-02-sounds-library.md` (row, tabs) and `P3-03-sound-preview-and-details.md` (details sheet). This ticket's mechanism (500 MB LRU cache, progress, retries, the 50-sound starter pack) is the engineer's; this file says only what each state looks and sounds like. **There is no Downloads screen and no setting in v1** (no Settings screen exists; PM proposal of 10 Nov, Q1).

## Job
"Every sound just works, online or off, and I'm told only when something needs me." **Done when** (TICKETS): airplane mode still plays the starter pack and every cached sound.

## The rule behind the states
- **Sounds download when first needed:** on play, or on Use. Nothing is downloaded by browsing. A sound is about 46 KB, so on a normal connection the download finishes before the 300 ms the UI waits to show anything (Assumption; the engineer measures on the owner's phone and tells me the p95).
- **No confirmation on mobile data:** the files are tiny (about 46 KB each); a "Use mobile data?" question would cost more than the download. (Assumption; if the files grow past 1 MB, revisit.)
- **The 500 MB cache cleans itself** by least-recently-used order. The user is **never told** about it and never asked to clean it: at about 46 KB, 500 MB is roughly ten thousand sounds. A sound that was evicted just downloads again when played, and looks like any not-yet-saved sound.
- **"Cache full"** therefore means the *phone's* storage is full, not the 500 MB limit. That is the one state with a message (below).
- **Starter pack and My sounds are never evicted** and show as saved.

## Row states
A row's disc and meta line change; the name, heart and layout stay. All fit the P3-02 row.

| State | Disc | Meta line | Waveform area | Add / Use block |
| --- | --- | --- | --- | --- |
| Saved (default) | Play | "0:02 · CC0" | none (rail or bars while playing) | Active |
| Not saved, online | Play (same as saved: no hint, because every sound downloads on demand) | Same as saved | none | Active; it downloads, then runs the flow |
| Downloading (> 300 ms) | **Cancel**: `close` icon in `primary` on `primary-subtle` | "Downloading…" in `text-secondary` | Determinate rail (ProgressBar look: `hairline` rail, `primary` fill) | Disabled look, still focusable |
| Failed (network, server, stalled 15 s) | **Retry**: `rotate_right` in `primary` on `primary-subtle` | "Couldn't download. Tap to try again." in `danger` (5.2:1 or more on `canvas` and `surface`; never on `surface-raised`) | none | Active (tap retries and continues) |
| Failed, phone storage full | **Retry**, enabled | "Not enough space on your phone." in `danger` | none | Disabled look |
| Offline, not saved | Disabled `play` in `text-muted` | "Not saved for offline" in `text-muted` | none | **Not drawn**; its 48 dp slot stays empty (TICKETS P3-15; updated 11 Nov, was "Disabled") |
| Offline, saved | Play | As saved | none | Active (needs no connection once it has its media) |
| Removed from the catalog (a favorite or recent) | Disabled `play` | "No longer available" in `text-muted` | none | **Not drawn** (slot kept empty, as above); the heart works |
| Unreadable file on the phone | Play | After a tap: toast, row idle; the file is deleted and the next play downloads it again | none | Active |

Disabled looks: the icon and text in `text-muted` (4.6:1 or more), no fill change. They stay in the focus order with a disabled state so a screen-reader user hears why (see below).

- **Under 300 ms:** no Downloading look. The disc goes straight to Pause and the sound plays (pressed look in between; no spinner).
- **Cancel:** a tap on the disc while downloading stops it and returns the row to idle; nothing is kept, no toast.
- **Tapping another row's disc** while one downloads cancels the first and starts the second (one sound at a time).
- **Retry:** the engine already retried twice with backoff before showing Failed; the user's tap is a fresh attempt. A second failure shows the same look (no counter, no escalating message).
- **Stalled:** no bytes for 15 s counts as failed (Assumption; the engineer tunes it).
- **Use block while not saved:** the first tap downloads the sound, shows the Downloading look on the row, and when done carries on to the photo picker. A failure leaves the user here with the Failed look.

## The one toast and the one action: phone storage full
- **When:** a download or an import can't be written because the phone is out of space.
- **What:** the row shows the "Not enough space" look; a persistent Toast (Toast README, persistent variant) says `sound_dl_storage_toast` with one Quiet action **Free up space** that opens the system's storage settings. It stays until dismissed (×) or until the user returns with room.
- **Coming back from the storage screen:** nothing is retried by itself (a sound is one tap away). The row keeps its Retry.
- Reuses P1-02's "Not enough space" intent for Free up space (the same code path); this spec adds no sheet. (The P1-08 review's follow-up F2, which routed a full phone to the "That sound won't play" message, is closed by this state.)
- **Hide the action** when the phone has no storage screen, as P1-02 does. Never show a button that does nothing.

## Offline
- **Strip** at the top of the list: `sounds_offline_strip` ("You're offline. Saved sounds still play."). It appears when the connection drops while the screen is open (polite announcement) and goes when it returns. No toast for the return.
- **Browsing** shows the cached catalog. Rows whose audio is on the phone are normal; the rest are dimmed as above. They are not hidden, so the user sees the library is bigger than their phone's copy.
- **Details sheet** for a sound that isn't saved while offline: disc disabled, Use hidden, a line explains (P3-03).
- **Search** offline: see P3-02.
- **Starting the app offline for the first time** (nothing cached): corrected 11 Nov after the P3-04 review. The earlier text said the starter pack plays from the Favorites and My sounds tabs; that was wrong, because both tabs are empty on a first launch. Instead: the offline strip, then the bundled starter sounds as normal rows under a `label` heading "Starter sounds" (`sounds_starter_heading`, 20), and no load-error block. The load-error block stays for an online failure. Needs the review's follow-up ticket (see `docs/ux/reviews/P3-04.md`).

## Motion
Row state changes use 120 ms (`duration-press`). The rail's fill moves to each value over 120 ms, at most 10 updates a second, linear; reduce motion: it jumps (ProgressBar README). Nothing loops.

## Copy
| Key | English | Budget | Notes |
| --- | --- | --- | --- |
| `sound_dl_downloading` | Downloading… | 20 | Meta slot |
| `sound_dl_failed` | Couldn't download. Tap to try again. | 52 | Meta slot, wraps. Names the next step |
| `sound_dl_failed_storage` | Not enough space on your phone. | 52 | Meta slot |
| `sound_dl_storage_toast` | Your phone is full, so this sound can't be saved. | 72 | Persistent toast; says what happened |
| `sound_dl_free_up_space` | Free up space | 20 | Toast action. Reuse P1-02's string if it exists |
| `sound_not_saved_offline` | Not saved for offline | 32 | Meta slot |
| `sound_unavailable` | No longer available | 32 | Meta slot |
| `sound_a11y_cancel_download` | Cancel download of {name} | 40 + name | Disc while downloading |
| `sound_a11y_retry` | Try again: download {name} | 48 + name | Disc after a failure |
| ~~`sound_a11y_downloading`~~, ~~`sound_a11y_download_failed`~~, ~~`sound_a11y_unavailable_hint`~~ | Dropped at the P3-04 review | | The meta line is a polite live region inside the name block, so the announcement is "{name}, {meta}"; the disabled disc's state description is the meta line |

## Accessibility
- The disc's name changes with the state ("Play", "Cancel download of", "Try again: download"), the same pattern as Play and Pause. The visible icon changes with it, so the name still matches what is seen.
- The meta line is read as part of the name block, so "Couldn't download. Tap to try again." is heard whether or not the user reaches the disc. A change into Downloading or Failed is a polite announcement (SC 4.1.3); the Downloading look appears only after 300 ms, so quick downloads announce nothing.
- Color is never the only signal: Failed is words plus a different icon; offline is words plus a flat look.
- Targets and 200% font as the row (P3-02).

## Analytics
None new. `sound_preview` fires only once the sound starts playing, not on a download that fails. A failed download's detail goes to Crashlytics non-fatal logging by the engineer (error class only), not Analytics.

## QA compares
Screenshots in `docs/ux/reviews/P3-04/`:
1. `row-downloading` (throttled network to hold the state), `row-failed`, `row-failed-storage`.
2. `list-offline`: strip, saved rows, dimmed rows.
3. `toast-storage-full` (a nearly full emulator image).
4. `airplane-starter`: airplane mode, the starter pack plays from the offline first-launch list (Trending, Local and Global show a "Starter sounds" section) and from the editor's picker.
5. `row-failed-hi-200`: Hindi at 200%, the meta wrapped whole.

## Questions for the owner
1. **No setting to clear downloads and no Downloads screen in v1 (recommended)**: the cache cleans itself and a sound is 46 KB. The cost is that a user whose phone is full has only the system's Free up space. A "Clear downloaded sounds" row belongs on the Settings screen the PM proposed at Phase 5 (kickoff Q1).
2. **No mobile-data warning (recommended)**, as above.
