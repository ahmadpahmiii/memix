# ux-designer memory

Lessons for this project only, newest first. Keep under ~150 lines; prune stale items. Focus: Design decisions and their reasons, patterns that worked or failed, recurring review issues, competitor insights.

- 2026-10-08 · P1-04/05/06 (+P1-07/08/09) · Video editor core specs:
  - **Layout:** fixed 60/40 preview/timeline split, so the preview never jumps. Portrait lock on phones only (Android 16 ignores locks at sw600+).
  - **Timeline:** fixed center playhead under the play button. No track-label column (it cost 20% of 360 dp); track type = order + color + item labels + spoken names. Clip tools replace the tool bar; the board's separate selected bar had 36 dp chips.
  - **Sound first:** an "Add a meme sound" pill fixed at the playhead keeps sounds 2 taps away even with a clip selected. The original-audio toggle sits in the empty space left of 0:00.
  - **Time and selection:** timecode `MM:SS.cc` (hundredths, since fps is picked at export). Split selects the right piece. The last main clip can't be deleted. Sounds don't follow ripple edits (open question for the P1 test).
  - **WCAG 2.5.1/2.5.7 alternatives:** ruler long-press zoom Menu, ruler tap seek, Reorder tap mode, "Move here", "Trim to playhead".
  - **Contrast catches:** white selection on blue sound clips is only 2.4:1, so the outline is drawn outside with a 1 dp gap. `on-track` on a scrim-washed clip is 1.5:1, so past-end labels switch to `text`.
  - **Tokens added:** `playhead-head-size`, `trim-handle-width`, `icon-small`. New component specs: ToolPanel, Toast, Menu.
  - **Research limits:** this session can't fetch CapCut help, apple.com, w3.org or lilys.ai pages, so CapCut specifics stay labeled Assumption; iMovie and Clipchamp help (via search) are the Guidance.
- 2026-10-07 · P1-02 · System photo picker: no permission, and we can't style it reliably (the accent color needs API 35, and the picker follows the system's light or dark theme), so leave it at the default. Copy must never promise picker behavior (numbered badges, tap order, limit wording); ordered selection is "might not be supported". Import UI shows only after 300 ms and stays at least 500 ms; blocking Sheet variant; determinate progress only (MOTION 2 bans looping bars). No empty drafts: a project exists only once one item lands. Names are stored empty so they follow the language. Limit 35 per pick (TikTok photo mode). Gaps found: "add more" and "replace a clip" have no ticket; the PRD sets no still duration (proposed 3 s).
- 2026-10-08 · P0 · Boards carry sample content; every spec says what replaces it until real data exists. Anton has no Devanagari (proposal pending). Home centers its two doors while it has one job; trending rows put it back at the top.

<!-- - 2026-10-xx · P0-01 · lesson … -->
