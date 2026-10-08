# Decision log

Decisions the owner made or approved. Newest first. Earlier product decisions are in `docs/PRD.md` → Decision log.

| Date | Decision | Why | Approved by |
| --- | --- | --- | --- |
| 2026-10-08 | CLAUDE.md rule 3 also allows platform code in the composition root (`:androidApp`, `:composeApp`) for app entry and wiring, such as creating the SQLDelight driver and debug hand-check hooks; no feature logic there. | P1-01 needed a place to create the database driver: `:core:data` must stay shared code, and a domain interface would leak SQLDelight types. The composition root already held the iOS entry point and Koin start-up. | Owner |
| 2026-10-08 | `project_create.source` gains `gallery` (values `blank`, `template`, `gallery`). Video gallery starts send `gallery`; `blank` means only the photo editor's blank layout. PRD → Metrics and analytics updated. ([proposal](proposals/2026-10-07-p1-02-followups.md), B) | With `blank` covering both, the start-to-export funnel couldn't tell gallery starts from blank photo layouts. No live data affected: analytics stays off until consent (P5-01). | Owner |
| 2026-10-08 | New ticket P1-16 (S, Needs P1-02, P1-06, P1-07): add media and replace a clip from the video editor. Replace covers video and photo clips only; a meme sound is deleted and re-added in P1. ([proposal](proposals/2026-10-07-p1-02-followups.md), A) | The PRD promises "add more at any time, replace a clip" and no ticket built it. Up to 1 day in P1, which is 12 days ahead, so launch dates don't move. | Owner |
| 2026-10-08 | P0-07 (Cloudflare R2) moves out of Phase 0 while another media-hosting approach is found; P0-06's seed sounds and templates move with it. Phase 0 closes without them. | R2 needs a billing checkout and a domain; nothing in P1 or P2 needs hosted media. | Owner |
| 2026-10-08 | Memix gets its own Supabase project (`memix`, ref `drnhpnixnewqjfmrchrr`, Singapore, free plan) instead of sharing the owner's existing project. | The existing project holds another app's data. | Owner |
| 2026-10-07 | One branch and one PR per phase (`phase-<n>`), opened when the phase is done; tickets are commits on the phase branch. CI also runs on pushes to `phase-*`. P0's remaining work moves to `phase-0`; the per-ticket PR #6 is closed in its favor. | Per-ticket PRs were redundant for a solo owner. | Owner |
| 2026-10-07 | Icons: a Memix-drawn icon set replaces the proposed Lucide set, drawn as each ticket needs icons. | Lucide is the default AI-generated icon look (antislop R-04); Memix-drawn icons carry the brand. | Owner |
| 2026-10-07 | Kickoff scope for this session: Phase 0 app code first, then the design in Compose (P0-04, P0-05). P0-06 to P0-08 wait for the owner's accounts. | Owner's choice at kickoff. | Owner |
| 2026-10-07 | No automated tests; QA is a manual pass at the end of each phase (emulator via adb + owner's device checklist). | Save tokens and time for a solo build. | Owner |
| 2026-10-07 | Team of 5 role agents (principal mobile, UX designer, PM, backend, QA) replaces the 6 specialists; PM proposes and the owner decides; retro at every phase gate. | Clear ownership of code quality, design, product and QA. | Owner |
| 2026-10-07 | Design v2 "neutral chrome, one blue" (neutral grays, sky blue #2BB3F3, white selection); v1 yellow/pink rejected. | Research-based palette for photo/video editing. | Owner |
