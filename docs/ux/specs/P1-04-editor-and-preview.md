# Video editor frame, preview and transport · P1-04 (with the P1-07 controls)

Part of a set of three. Each one owns one region of the same screen:
- **P1-04 (this file):** the editor frame top to bottom, the top bar, the preview, play/pause/seek, the timecode, undo/redo and the save-failure message (P1-07).
- **P1-05** `P1-05-timeline.md`: the timeline, including how meme sounds (P1-08) and original audio (P1-09) look on it.
- **P1-06** `P1-06-clip-edits-and-audio.md`: the tool bars and every edit, plus the meme sound (P1-08) and original audio (P1-09) controls.

It stays consistent with `P1-02-gallery-picker.md`: the editor opens on a project that already has at least one clip, and P1-04 replaces P1-02's placeholder list (see "Retired from P1-02").

## Job
Show the meme as it will export and let the user find the exact moment to work on. Every video job in the PRD runs through this screen; the first one is "React to a clip with a meme sound": watch the fail, stop on its frame, drop a boom there.

**Done when** (TICKETS): 1080p preview plays at 30 fps or more on the owner's phone; LeakCanary shows no leaks.

**Performance bar (owner, 8 Oct):** there is no named reference phone. The bar is no visible lag and no memory leaks, on Android and iOS. The ticket numbers are measured on the owner's own phone with a release-type build.

**Later, not P1 (owner, 8 Oct; TICKETS → Later · Improvements):** keyboard shortcuts are L-01 and the locked-track UI is L-02. Sections marked **Later (L-01)** or **Later (L-02)** are drafts for those tickets: not built or checked in P1.

**The one blue action:** **Export**, top-right, arrives with P1-12. Until then the editor has no blue button. Meme-sound UI is blue everywhere by the design-system rule (the Meme sounds tool, the "Add a meme sound" row, sound clips), and that's the only other blue. Selection is white.

## Entry and exit
- **Arrives from:** the P1-02 import (new project), Drafts (P1-14), and later P2-10 "Make it a video meme" and templates. Every entry opens the same state: playhead at 0:00, paused, nothing selected, default zoom (P1-05), and the preview showing the first frame. The undo history starts empty.
- **Close** (top-left) or system back, after the back order below → the screen the flow started from (Home or a tab, per P1-02). Any pending save is written first. There's no "Save?" question, because saving is automatic. The one exception: changes that couldn't be saved (see Saving).
- **System back order:** an open menu closes → an open sheet or tool panel closes → a drag in progress is cancelled → reorder mode is cancelled → the selection clears → the editor closes. Predictive back (Android 14+) shows the previous screen only at the last step. In every earlier step, back is handled inside the editor.
- **App goes to the background:** playback pauses and the pending save is flushed (P1-07). On return the editor is unchanged and paused.
- **Kept:** the project (autosaved). Not kept: zoom, scroll, selection, undo history (in memory only, per the PM's P1-07 note).

## Layout
Board: `design/screens/VideoEditor.dc.html` (390 × 844). The regions, top to bottom:

| Region | Height | Specified in |
| --- | --- | --- |
| Status bar inset | system | `canvas` behind it (edge to edge) |
| Top bar | `touch-target` plus `space-1` above and below (56) | this file |
| Preview stage | 60% of the shared space | this file |
| Transport row | `touch-target`; grows at large font scales | this file |
| Timeline | 40% of the shared space, never less than its minimum | P1-05 |
| Tool bar | `toolbar-height`, plus the navigation inset below it in `surface` | P1-06 (main tools and clip tools) |

- **Shared space** = window height − system bars − top bar − transport row − tool bar.
- **Timeline minimum** = ruler + main video row + "Add a meme sound" row (P1-05: 24 + 48 + 48 = 120 at 100% font). When 40% is less than that, the timeline takes its minimum and the preview gets the rest.
- **The split is fixed.** The preview never changes size when tracks are added, an item is selected, or a panel opens. A preview that jumps while you edit breaks your sense of where things are (Assumption; CapCut keeps its preview fixed the same way).

Worked sizes (dp, 100% font):

| Phone | Shared space | Preview stage | 9:16 frame inside it | Timeline |
| --- | --- | --- | --- | --- |
| 360 × 640, 3-button nav | 392 | 235 | 123 × 219 | 157 |
| 360 × 800, gesture nav | 576 | 346 | 186 × 330 | 230 |
| 412 × 915 (Pixel 9 class), gesture nav | 691 | 415 | 224 × 399 | 276 |

- **Width:** designed for 360–430 dp. Everything below works at 360. Nothing gets wider than its content above 430; the timeline simply shows more time.
- **Orientation:** portrait.
  - On phones (smallest width under 600 dp), lock the editor to portrait. In landscape, a phone has about 180 dp left for the preview and timeline together.
  - On large screens, Android 16+ ignores orientation locks for apps targeting API 36, and Android 17 removes the opt-out (Guidance), so the layout must still work in any window.
  - In short windows (split screen, a large screen in landscape), the timeline keeps its minimum and the preview takes what's left.
  - Tablet-optimized layouts are out of v1 (PRD → Not in v1).
- **Tool panels** (P1-06 Volume; later Text and Canvas) cover exactly the timeline and tool bar region. The preview and transport row stay visible and working, so the user can play and hear a change. Sheets (the P1-08 Meme sounds sheet) rise over the whole screen as usual.

### Top bar
`canvas`, no divider.
- **Left:** Close: icon button, `MemixIcons.Close` in `text`, 48 dp target. Spoken "Close editor".
- **Center, reserved for P1-11 and P1-12:** the ratio chip (board "9:16", P1-11 Canvas) and the quality chip (board "1080p", P1-12 Export). Leave them out until their tickets. A chip that does nothing would be a dead control (P0-05 rule). Their specs define them.
- **Right, reserved for P1-12:** Export, the primary Button. P1-12 specs it. Until then the space stays empty. Note for P1-12: top-right is hard to reach one-handed, but every competitor puts the final action there and it's used once per project. P1-12 weighs that trade-off.

### Preview stage
- **Stage:** `stage` black fills the region.
- **Canvas frame:** the largest rectangle with the project's canvas ratio that fits inside the stage, inset by `space-2` on every side and centered. The engine's player surface fills it.
- **Before the first frame:** the frame shows `surface-raised` (the "empty media frame" role), static. No spinner and no shimmer, because nothing in Memix loops (MOTION 2). PRD target: the editor opens in under 1 s.
- **Frame edge:** a black canvas background (the default) blends into the black stage, so a fitted landscape clip doesn't show where the 9:16 frame ends. P1-11 owns showing the frame boundary; nothing is added here.
- **Taps:** a tap anywhere on the stage toggles play/pause. P1-10 will take taps that land on a text layer.
- **Toasts and the save banner** sit at the top of the stage (component `design/system/components/Toast/README.md`).

Board sample content and what replaces it:
- The caption "When the build passes first try", the figure and the skull sticker → the user's own project as the engine renders it.
- "00:03.20 / 00:08.00" → the real playhead time and project length.
- "9:16" and "1080p" → P1-11 and P1-12.

### Transport row
`canvas`, between the stage and the timeline. A `stroke-hairline` `hairline` divider runs along its bottom edge, above the timeline (as on the board). There are three slots, and the play button sits on the screen's vertical center line, directly above the timeline's fixed playhead (P1-05).

| Slot | Content |
| --- | --- |
| Left (`space-4` gutter) | Timecode: current time in `timecode` `text`, then " / " and the project length in `timecode` `text-muted` |
| Center | Play/Pause: icon button, `MemixIcons.Play` / `MemixIcons.Pause` in `text`, 24 icon, 48 dp target, no fill |
| Right (`space-2` gutter) | Undo, then Redo: icon buttons, new icons `undo` and `redo`, 48 dp targets each (P1-07) |

- **Fit at 360 dp:** the left slot is (360 − 48) ÷ 2 = 156 wide. "00:03.20 / 00:08.00" is 19 Space Mono characters, about 137 dp, so it fits with the gutter. The right slot needs 2 × 48 + 8 = 104.
- **Large font or hour-long projects:** when the timecode doesn't fit on one line, the total wraps under the current time (two lines), and the row grows taller. The play button stays centered vertically and horizontally. Nothing is cut off.
  - On one line it is a single text with real spaces, "00:03.20 / 00:08.00" (19 characters, about 139 dp at 12 sp). A separate gap token adds width and can wrap English at 360 dp.
  - Wrapped, the total shows alone under the current time, **without the slash**. The `text` / `text-muted` colors already tell them apart, and a "/" left on its own line at 200% wastes a line of preview (review P1-04, polish 3).
- **Board differences:** buttons go from 44 to 48 dp, to meet `touch-target`. The play icon uses `text`, not pure white.

### Timecode format
- **`MM:SS.cc`**: minutes, seconds, hundredths, always two digits each ("00:03.20"). From one hour up: `H:MM:SS.cc` ("1:02:07.45"). The ruler, duration badges and the trim bubble (P1-05, P1-06) use the same format. P1-02's durations already do ("00:03.00").
- **Hundredths, not frames:** the project is timed in microseconds and the frame rate is only picked at export (24, 30 or 60), so a frame count would change meaning with the export setting. Hundredths read as an ordinary decimal in every launch language (Assumption).
- **Rounding:** always down, so the current time never passes the total. At the end both read the same.
- **Digits and separator:** ASCII digits and "." in every language. It's a timecode, not a number in a sentence. Space Mono keeps every digit the same width, so the text doesn't jitter while it changes.
- **Updates:** with every frame the preview shows. Reading the time must not recompose the rest of the screen (`mobile-performance` skill).

## Playback

| Action | Result |
| --- | --- |
| Play (button, tap on stage, Space) | Plays from the playhead. At the end (within one frame), it starts again from 0:00. The icon turns to Pause at once (optimistic). The timeline scrolls under the fixed playhead (P1-05). |
| Pause (same controls) | Stops on the current frame; the icon turns to Play |
| Reaches the end | Stops. The playhead stays at the end; the icon turns to Play. No loop. |
| Pauses playback automatically | Touching the timeline; starting any edit (tool tap, drag start, undo, redo); opening a menu or sheet; system back; app to background; losing audio focus (call, other media); headphones unplugged |
| Keeps playing | Opening the Volume tool panel (the point is to hear it). Changes in the panel are heard live. |

- **Audio:** every audible item is mixed as it will export, with mute and volume applied (P1-06).
- **Preview quality:** the engine may render at reduced resolution while scrubbing, and renders full resolution at rest (`mobile-performance` skill).

### Seek
- **Main way:** drag the timeline. It scrolls under the fixed playhead, and the preview follows (P1-05).
- **Speed:** each preview update lands in under 100 ms (PRD scrub target). While the user scrolls fast, the preview may show the nearest frame it can decode quickly. Media3's scrubbing mode exists for this (Guidance). Within 100 ms of the scroll stopping, it shows the exact frame.
- **Other ways:** tap the ruler (P1-05); the TalkBack playhead control (Accessibility). Arrow keys: later (L-01).
- **Sound:** seeking is silent. There's no audio scrubbing in v1.

## Undo and redo (P1-07)
- **Placement:** the transport row's right slot, in thumb reach, beside the play button. That is CapCut's position too (Assumption: product knowledge). They're never hidden, so undo is one tap from any state of the editor, including while a tool panel is open.
- **States:**
  - Enabled: icon in `text`.
  - Disabled: icon in `text-muted`. Taps do nothing, and TalkBack says "Undo, disabled".
  - Undo is disabled when the history is empty (a just-opened project). Redo is disabled until something is undone, and again after any new edit.
  - This is the one place Memix uses a plain disabled state instead of "explain on tap". "Nothing to undo" would only be noise, and greyed undo/redo is a convention everyone reads (Guidance: the board already shows Redo greyed).
- **What counts as one step:** each committed edit. That means one gesture end (trim, move, reorder), one tool tap (split, delete, duplicate, detach, mute toggle; unlock later, L-02), one slider release, or one sound landing. Scrolling, zooming, selecting, playing and opening panels are not steps. The history holds 100. The 101st edit drops the oldest without a message.
- **During a drag:** undo and redo are ignored while a finger is on the timeline.
- **After undo or redo:**
  - The change applies instantly, with no animation, so it reads as a jump back.
  - The selection stays if that item still exists; otherwise it clears.
  - The playhead stays, clamped to the new end if the video got shorter.
  - Playback pauses.
  - A toast names what changed: "Undo: Split" / "Redo: Split". The changed item may be scrolled out of view, and the toast tells the user something happened (Assumption: CapCut shows a similar label).
  - Edit names live in P1-06 → Copy.
- **Panels:** if undo removes the item a tool panel is editing, the panel closes. Otherwise the panel's controls show the restored values.

## Saving (P1-07)
- **Saving is silent:** no "Saved" label, no spinner, no dot (PM, P1-07 ready check). It runs 500 ms after the last change, and on close or background.
- **When a save fails:**
  - The edit stays in memory, and the editor keeps working normally.
  - A banner shows at the top of the stage (Toast component, persistent variant):

    | Error | Banner text | Action |
    | --- | --- | --- |
    | `StorageFull` | "Your phone is full, so your latest changes aren't saved." | **Free up space**: Quiet button. It opens Android's storage manager, the same intent as P1-02. |
    | Any other error | "Your latest changes aren't saved yet. Memix tries again with your next change." | none |

  - **Layout:** the message and × share the first row. Free up space sits on its own row below, aligned to the end. The message is never cut off (Toast README → persistent variant).
  - **No storage screen:** if the storage manager can't be opened (some OEM builds; iOS until P7), Free up space is hidden here and in the sheet below. The rule is the same as P1-02's no-button variant: never show a button that does nothing.
  - A **Dismiss** (×) button hides the banner for the rest of the session. It comes back only if a save succeeds and a later one fails again.
  - **One message at a time:** while the banner shows, other toasts (undo, redo, missing media) are not shown and not queued, because a late "Undo: Trim" would be stale. Their text is still announced politely to screen readers (review P1-04, R4).
- **Retries:**
  - with the next change (PM)
  - when the app returns to the foreground
  - when the user comes back from the storage manager
  - When a save succeeds, the banner goes away without a message. TalkBack hears "Changes saved".
- **Closing with unsaved changes** (only while a save is failing): the standard Sheet opens:
  - Title "Your latest changes aren't saved". Body "Your phone is full. Free up space to keep them, or leave and lose them."
  - **Free up space** is primary. **Leave anyway** is a secondary button.
  - Close, scrim or back keep the user in the editor.
  - If a save succeeds while the sheet is open (for example, back from the storage manager), the sheet closes by itself and the user stays in the editor with the work saved. One more tap on Close leaves. Leaving on its own after a trip to Settings would be disorienting.
  - This is the only confirmation in the editor. Losing work is the one thing undo can't fix. Store reviews of VN and InShot show how badly lost drafts land (Evidence, anecdotal).

## States

| State | What shows | Copy |
| --- | --- | --- |
| Opening (first frame not ready) | Layout in place; canvas frame in `surface-raised`; timeline clips in `track-video` until thumbnails arrive (P1-05). Nothing animates. | none |
| Default, paused | First frame; "00:00.00 / {length}"; Play; Undo disabled, Redo disabled | `editor_play` |
| Playing | Pause icon; timecode running; timeline scrolling | `editor_pause` |
| At end | "{length} / {length}"; Play icon; Play restarts from 0:00 | none |
| One clip, many tracks | No difference in this region; see P1-05 | |
| Tool panel open | Preview and transport row stay; panel covers the timeline and tool bar (P1-06) | |
| Preview error (the engine can't build or play the composition) | The frame shows `surface` (not `surface-raised`: a secondary button on `surface-raised` loses its shape) with a centered line in `body` `text-secondary` and a secondary **Try again** button. Play/Pause is disabled. Edits keep working and saving. Try again rebuilds the preview session. | `editor_preview_error_body`, `editor_preview_retry` |
| A clip's file is missing (defensive: the app's copy is gone) | That clip plays as the canvas background. Its timeline clip shows no thumbnails (P1-05). A toast shows once per open. | `editor_missing_media` |
| Save failing | Banner at the top of the stage (Saving) | `editor_save_storage_full` or `editor_save_failed`, `editor_free_up_space`, `editor_dismiss` |
| Leaving while a save is failing | Sheet (Saving) | `editor_unsaved_title`, `editor_unsaved_body`, `editor_free_up_space`, `editor_leave_anyway` |
| Empty project (defensive: P1-02 never creates one, and P1-06 won't delete the last clip) | The frame shows the canvas background with a centered title in `title` and a body in `body` `text-secondary`. The timeline shows the ruler and an empty main row. The tool bar has no tools. Play/Pause is disabled. Close is the way out. Once P1-16 ships, the body changes and the Add media tile (P1-05) is the action. | `editor_empty_title`, `editor_empty_body` (`editor_empty_body_add` with P1-16) |
| Offline | No difference. Everything here is on the phone. | none |
| Permission | None needed | none |
| Long content | Hour-long project: the timecode wraps. 200% font: see Accessibility. | |
| First run | Same as every run. No tips or coach marks. The "Add a meme sound" row (P1-05) is the visible invitation. | none |

## Interactions
- **Press feedback:** the token pressed states, 120 ms. Icon buttons with no fill show `surface-raised` behind them while pressed, at `radius-md`, 48 × 48. Nothing moves.
- **Haptics:** none in this region. Haptics belong to content landing and to timeline gestures (P1-05, P1-06).
- **Keyboard in P1** (basic keyboard access, not shortcuts): Tab and Shift+Tab move focus in the order under Accessibility, Enter activates the focused control (the platform default), and **Escape does the same as system back**. QA's keyboard pass checks these.
- **Keyboard shortcuts: Later (L-01)**, not built or checked in P1. Draft for L-01:

| Key | Action |
| --- | --- |
| Space | Play / pause |
| Left / Right | One frame back or forward (1/30 s) |
| Shift + Left / Right | One second back or forward |
| Home / End | Start / end |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z or Ctrl+Y | Redo |

The L-01 drafts for edit keys are in P1-06 and for zoom keys in P1-05.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Editor opens | 320 ms push (`duration-screen`) | 120 ms fade |
| Play/Pause icon swap | 120 ms cross-fade | same |
| Timeline during playback | Scrolls with playback. It is the position read-out, not decoration. | Same; it still follows playback |
| Toast in / out | 120 ms fade | same |
| Save banner in / out | 120 ms fade | same |
| Undo / redo | The change applies instantly | same |
| Unsaved-changes sheet | 200 ms slide over a fading scrim | 120 ms fade |

## Copy
English source. The engineer machine-drafts id, es, pt and hi (allowed until P5); final translations come in P5-06. Budgets are in characters, including filled-in placeholders.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `editor_close` | Close editor | 24 | Screen reader only |
| `editor_play` | Play | 16 | Screen reader only; also the stage's click label |
| `editor_pause` | Pause | 16 | Same |
| `editor_undo` | Undo | 16 | Screen reader and tooltip |
| `editor_redo` | Redo | 16 | Same |
| `editor_undo_toast` | Undo: %1$s | 32 | %1$s = an edit name from P1-06 (`edit_*`), e.g. "Undo: Split" |
| `editor_redo_toast` | Redo: %1$s | 32 | Same |
| `editor_preview_a11y` | Preview, %1$s | 30 | %1$s = the ratio, e.g. "9:16" |
| `editor_preview_error_body` | The preview stopped. Your edits are safe. | 60 | |
| `editor_preview_retry` | Try again | 18 | |
| `editor_missing_media` | A clip's file is missing, so it plays black. Delete that clip to fix it. | 90 | |
| `editor_save_storage_full` | Your phone is full, so your latest changes aren't saved. | 80 | |
| `editor_save_failed` | Your latest changes aren't saved yet. Memix tries again with your next change. | 100 | "Memix" never translates |
| `editor_free_up_space` | Free up space | 22 | Same text as `import_free_up_space` (P1-02): reuse that resource |
| `editor_dismiss` | Dismiss | 16 | Screen reader label of the banner's ×. Means "hide this message". Never use a word that can mean discard or reject, since it's heard right after "your changes aren't saved" (es "Cerrar aviso", not "Descartar") |
| `editor_saved_a11y` | Changes saved | 24 | Spoken only, when a failing save recovers |
| `editor_unsaved_title` | Your latest changes aren't saved | 40 | |
| `editor_unsaved_body` | Your phone is full. Free up space to keep them, or leave and lose them. | 100 | |
| `editor_leave_anyway` | Leave anyway | 18 | |
| `editor_empty_title` | This draft has no clips | 32 | |
| `editor_empty_body` | Close it and start a new video meme. | 60 | Until P1-16 |
| `editor_empty_body_add` | Add a video or photo to start. | 60 | Once P1-16 ships |
| `a11y_time_seconds` | %1$s seconds | 24 | %1$s = a decimal with one place, in the user's number format ("3.2", "3,2"). Use the noun form your language uses after a decimal number. |
| `a11y_minutes` | one: 1 minute · other: %d minutes | 16 | Plural resource |
| `a11y_time_minutes_seconds` | %1$s %2$s | 40 | %1$s = `a11y_minutes`, %2$s = `a11y_time_seconds`; reorder if your language needs it |
| `a11y_time_of` | %1$s of %2$s | 70 | "3.2 seconds of 8 seconds" |

Translator notes:
- **Tone:** plain and calm. Save messages never blame the user and always say what happens next.
- **Timecodes** ("00:03.20") are never translated or localized. Only the spoken versions are.
- **Hindi** falls back to the system Devanagari face, which needs more line height. No text row here has a fixed height.

### Retired from P1-02
P1-04 replaces the placeholder editor list. Delete:
- `editor_video_title`
- `editor_video_body`
- `editor_media_row_a11y`

Keep:
- `import_item_video` and `import_item_photo` (used by P1-05 labels)
- `duration_seconds`

## Accessibility
- **Focus and TalkBack order:**
  1. Close
  2. (P1-11/P1-12 chips, Export)
  3. Save banner, when shown
  4. Preview
  5. Timecode
  6. Play/Pause
  7. Undo
  8. Redo
  9. Timeline (P1-05)
  10. Tool bar (P1-06)
- **Preview:** one node, "Preview, 9:16". Its click action is labeled "Play" or "Pause".
- **Timecode:** one node that reads `a11y_time_of` ("3.2 seconds of 8 seconds"), not the digits. It is not a live region, because it changes every frame.
- **Play/Pause:** the label follows the state.
- **Undo/Redo:** disabled state is exposed. After undo or redo, the toast text is announced politely. Toasts never take focus (WCAG 2.2 SC 4.1.3, Guidance).
- **Toast timing:** toasts stay at least as long as the user's "Time to take action" setting, via Compose `LocalAccessibilityManager.calculateRecommendedTimeoutMillis` (Guidance; WCAG 2.2 SC 2.2.1). Base durations: undo/redo 2 s; explanations 4 s. The save banner has no timeout.
- **Save banner:** announced politely when it appears. "Free up space" and "Dismiss" are focusable.
- **Unsaved-changes sheet:** takes focus, and TalkBack reads its title as the pane title.
- **Touch targets:** every control here is 48 dp or more.
- **Contrast:**
  - Timecode: `text` and `text-muted` on `canvas`, 4.6:1 or more.
  - Icons: `text` on `canvas`, 12.4:1 or more. Disabled icons in `text-muted` are 4.6:1 or more, though disabled controls don't need it.
  - Toast text: `text` on `surface-raised`, 12.4:1 or more.
- **200% font scale:**
  - The timecode wraps to two lines and the transport row grows.
  - The top bar keeps its height (icons only in P1).
  - The preview's 60% share shrinks only by what the transport row grows.
  - Toast text wraps as far as it needs to and is never cut off (WCAG 2.2 SC 1.4.4). The budgets keep it to about 3 lines at 100%.
- **Reduce motion:** see Motion.

## Analytics
- **None new here.** Playback, seeking and zoom aren't PRD events.
- **Undo and redo** fire `tool_use` `{editor: "video", tool: "undo" | "redo"}`. The undo rate is the best signal of edits going wrong. Confirmed by the PM on 8 Oct (acceptance log): they fire only when they change the project.
- **Save failures:** a Crashlytics non-fatal with the error class only (no project data), so StorageFull rates are visible.

## QA compares
1. **Default, paused** on a 9:16 project at 360 dp: top bar, stage with the frame, "00:00.00 / {length}", Undo and Redo disabled.
2. **Playing** mid-project: Pause icon, running timecode, playhead line under the play button.
3. **After one edit and one undo:** the "Undo: {edit}" toast; Undo enabled or disabled as the history says; Redo enabled.
4. **Save failing** (fill the emulator's storage): banner with Free up space. Then close, and the unsaved-changes sheet.
5. **Hindi at 200% font**, 360 dp: the timecode wraps and nothing is clipped.
6. **Preview error** (debug hook that fails the engine session): message and Try again.

Measured, not screenshotted (note results in the QA report):
- **Done when:** 1080p 30 fps source, a 6-track debug project, 30 s of playback on the owner's phone in a release-type build. Media3 dropped-frame logs plus `dumpsys gfxinfo`. Pass: 30 fps or more, and no visible lag or stutter.
- **Leaks:** a debug build with LeakCanary. Open and close the editor 5 times, play, seek and edit; no leak reported.
- **Scrub:** each preview update under 100 ms (Perfetto, `mobile-performance` skill).
- **Open:** tap on a draft to the first preview frame in under 1 s.
- **Saving:** kill the app 1 s after an edit, reopen, and the edit is there (P1-07). Kill it within 500 ms, and the previous state reopens undamaged.

## Requests to the principal mobile engineer
I don't edit code, so these are requests.
1. **Icons:** draw `undo` and `redo` to the icon grammar (curved arrow with a flat-cut tail, one solid arrowhead as the core).
2. **Layout:**
   - The 60/40 split with the timeline minimum, computed once per window size. It never reacts to content.
   - Portrait lock for the editor on phones (smallest width under 600 dp). Check how the lock is applied per route against the current docs.
3. **Pause triggers:** as listed under Playback, including audio focus loss and `ACTION_AUDIO_BECOMING_NOISY`.
4. **Scrubbing:** check the current Media3 docs when the ticket starts for `CompositionPlayer` scrubbing-mode support. Turn it on while the timeline is being dragged and off at rest, if it helps hit the 100 ms target.
5. **Save failure:** map `AppError.StorageFull` and other failures to the two banner texts. Retry on the next change, on foreground, and on return from `ACTION_MANAGE_STORAGE`.
6. **Toast:** build the Toast component (spec `design/system/components/Toast/README.md`) in `:core:designsystem` and add it to the debug catalog.
7. **Debug hooks for QA:** a project that fails the preview session, and the 6-track sample from P1-03 (P1-05 uses it). The locked track in that sample waits for L-02.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Media3 1.8 added a scrubbing mode for frequent user seeks. `CompositionPlayer` has a matching setter and is marked experimental. | Guidance | [Media3 1.8.0 what's new](https://android-developers.googleblog.com/2025/08/media3-180-whats-new.html), [CompositionPlayer](https://developer.android.com/reference/androidx/media3/transformer/CompositionPlayer) |
| Apps targeting Android 16 (API 36) have orientation, resizability and aspect-ratio restrictions ignored on large screens (smallest width 600 dp or more). Phones are unaffected. Android 17 removes the temporary opt-out. | Guidance | [Orientation and resizability changes in Android 16](https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html), [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16), [Android 17 changes](https://developer.android.com/blog/posts/prepare-your-app-for-the-resizability-and-orientation-changes-in-android-17) |
| Status messages must reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Users can set "Time to take action" (Android 10+); apps should keep temporary messages up at least that long. Compose exposes `calculateRecommendedTimeoutMillis`. | Guidance | [Google: change time to take action](https://support.google.com/accessibility/android/answer/9426889), [Compose AccessibilityManager](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/AccessibilityManager), [WCAG 2.2 SC 2.2.1](https://www.w3.org/WAI/WCAG22/Understanding/timing-adjustable.html) |
| Undo beats confirmation for reversible actions; warnings get clicked through by habit | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| Lost drafts are a recurring complaint about mobile editors | Evidence (anecdotal reviews and complaints) | [VN on the App Store](https://apps.apple.com/us/app/vn-video-editor/id1343581380), [InShot draft complaint](https://www.sikayetvar.com/en/inshot-us/inshot-deleted-my-pro-project-draft-after-hours-of-work) |
| CapCut keeps the preview fixed, the playhead centered, and undo/redo beside play | Assumption (product knowledge; not confirmed from CapCut's help pages, which this session couldn't reach) | — |
