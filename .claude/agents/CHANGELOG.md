# Agent changelog

Changes to the agents, skills, hooks and CLAUDE.md rules. Retro proposals are applied only after the owner approves them; log each applied change here.

| Date | Change | Why | Source |
| --- | --- | --- | --- |
| 2026-10-07 | Replaced the 6 specialist agents with 5 role agents: principal-mobile-engineer, ux-designer, product-manager, backend-engineer, qa-engineer. Specialist know-how moved into skills. | Owner's request: a team that owns quality, design, product and testing, not just code. | Planning session |
| 2026-10-07 | No automated tests in the project; QA is a manual pass at the end of each phase (adb-driven emulator + owner's device checklist). | Owner's decision to save tokens and time. | Planning session |
| 2026-10-07 | Added `.claude/hooks/guard_paths.py` so PM, designer and QA can only edit their own files. | Keep roles honest: QA reports bugs instead of patching; PM and designer don't touch code. | Planning session |
| 2026-10-07 | `memix-architecture` module table: added `:androidApp`; `:composeApp` is now the shared KMP root. | AGP 9 split (P0-01). | P0-01 |
| 2026-10-07 | Branching: one `phase-<n>` branch and one PR per phase, opened at phase end; ticket handoffs go in commit messages and screenshots in `docs/ux/reviews/<ID>/`. Updated CLAUDE.md, `ticket-workflow`, `code-standards`, `new-feature`, the principal, backend and designer agents, TICKETS.md, TECHNICAL_DESIGN.md and `ci.yml` (push trigger on `phase-*`). | Owner: per-ticket PRs are redundant. | Owner request |
| 2026-10-08 | `qa-verification` and `play-release` use `:androidApp`; principal agent lists `:androidApp`; CLAUDE.md: renamed modules or commands get grepped out of `.claude/` in the same commit. | Skills still named the pre-AGP 9 module (retro P0 #1). | Retro P0 |
| 2026-10-08 | `qa-verification` §3: uiautomator lag, state on the clickable parent, screenshot wins. | False FAILs in P0 click-throughs (retro P0 #2). | Retro P0 |
| 2026-10-08 | `qa-verification` §4: keyboard pass (Tab, Enter, Escape, Back) in the phase QA pass. | Keyboard bugs found late, focus rings unverified (retro P0 #3, lighter form). | Retro P0 |
| 2026-10-08 | CLAUDE.md → Commands: first iOS link time and `./gradlew --stop` after JDK changes. | Lost time on a 15-minute link and a spawn-helper error (retro P0 #4). | Retro P0 |
| 2026-10-08 | `backend-engineer`: confirm the Memix project before any write; test rows in rolled-back transactions. | The first project offered held another app's data (retro P0 #5). | Retro P0 |
| 2026-10-08 | `ticket-workflow`: rule for a skipped QA pass (next phase's plan covers it). | Phase 0 closed without a QA pass (retro P0 #6). | Retro P0 |
| 2026-10-08 | Seeded the five agent memories with Phase 0 lessons. | Memories were empty; agents ran inline at kickoff (retro P0 #7). | Retro P0 |
| 2026-10-08 | `memix-architecture` project model names (`createdAtEpochUs`, `MediaRef(origin, …)`, migration steps, driver in the composition root); `new-feature` and `supabase-change`: a new or changed SQLDelight table adds a `.sqm`, checked by `:core:data:verifySqlDelightMigration`. | Keep skills in step with the P1-01 model and storage. | P1-01 |
| 2026-10-08 | `memix-architecture` and `new-feature`: editors edit through `ProjectEditSession` (`StartEditSessionUseCase`) for undo and auto-save. | Point the P1-04 and P2-01 editor work at the shared session. | P1-07 |
| 2026-10-08 | `memix-architecture`: `VideoEngine` shape as built (`export` returns `Outcome<ExportedVideo>`; other members tagged with the ticket that adds them) and the shared `CompositionPlan`; `android-media-engine` and `ios-port`: mapping rules live in `CompositionPlanner`, both platforms build from the plan. | Keep skills in step with the P1-03 engine. | P1-03 |
