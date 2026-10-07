# ux-designer memory

Lessons for this project only, newest first. Keep under ~150 lines; prune stale items. Focus: Design decisions and their reasons, patterns that worked or failed, recurring review issues, competitor insights.

- 2026-10-07 · P1-02 · System photo picker: no permission, and we can't style it reliably (the accent color needs API 35, and the picker follows the system's light or dark theme), so leave it at the default. Copy must never promise picker behavior (numbered badges, tap order, limit wording); ordered selection is "might not be supported". Import UI shows only after 300 ms and stays at least 500 ms; blocking Sheet variant; determinate progress only (MOTION 2 bans looping bars). No empty drafts: a project exists only once one item lands. Names are stored empty so they follow the language. Limit 35 per pick (TikTok photo mode). Gaps found: "add more" and "replace a clip" have no ticket; the PRD sets no still duration (proposed 3 s).
- 2026-10-08 · P0 · Boards carry sample content; every spec says what replaces it until real data exists. Anton has no Devanagari (proposal pending). Home centers its two doors while it has one job; trending rows put it back at the top.

<!-- - 2026-10-xx · P0-01 · lesson … -->
