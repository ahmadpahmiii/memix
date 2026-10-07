# Decision log

Decisions the owner made or approved. Newest first. Earlier product decisions are in `docs/PRD.md` → Decision log.

| Date | Decision | Why | Approved by |
| --- | --- | --- | --- |
| 2026-10-08 | P0-07 (Cloudflare R2) moves out of Phase 0 while another media-hosting approach is found; P0-06's seed sounds and templates move with it. Phase 0 closes without them. | R2 needs a billing checkout and a domain; nothing in P1 or P2 needs hosted media. | Owner |
| 2026-10-08 | Memix gets its own Supabase project (`memix`, ref `drnhpnixnewqjfmrchrr`, Singapore, free plan) instead of sharing the owner's existing project. | The existing project holds another app's data. | Owner |
| 2026-10-07 | One branch and one PR per phase (`phase-<n>`), opened when the phase is done; tickets are commits on the phase branch. CI also runs on pushes to `phase-*`. P0's remaining work moves to `phase-0`; the per-ticket PR #6 is closed in its favor. | Per-ticket PRs were redundant for a solo owner. | Owner |
| 2026-10-07 | Icons: a Memix-drawn icon set replaces the proposed Lucide set, drawn as each ticket needs icons. | Lucide is the default AI-generated icon look (antislop R-04); Memix-drawn icons carry the brand. | Owner |
| 2026-10-07 | Kickoff scope for this session: Phase 0 app code first, then the design in Compose (P0-04, P0-05). P0-06 to P0-08 wait for the owner's accounts. | Owner's choice at kickoff. | Owner |
| 2026-10-07 | No automated tests; QA is a manual pass at the end of each phase (emulator via adb + owner's device checklist). | Save tokens and time for a solo build. | Owner |
| 2026-10-07 | Team of 5 role agents (principal mobile, UX designer, PM, backend, QA) replaces the 6 specialists; PM proposes and the owner decides; retro at every phase gate. | Clear ownership of code quality, design, product and QA. | Owner |
| 2026-10-07 | Design v2 "neutral chrome, one blue" (neutral grays, sky blue #2BB3F3, white selection); v1 yellow/pink rejected. | Research-based palette for photo/video editing. | Owner |
