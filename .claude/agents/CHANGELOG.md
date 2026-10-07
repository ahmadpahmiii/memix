# Agent changelog

Changes to the agents, skills, hooks and CLAUDE.md rules. Retro proposals are applied only after the owner approves them; log each applied change here.

| Date | Change | Why | Source |
| --- | --- | --- | --- |
| 2026-10-07 | Replaced the 6 specialist agents with 5 role agents: principal-mobile-engineer, ux-designer, product-manager, backend-engineer, qa-engineer. Specialist know-how moved into skills. | Owner's request: a team that owns quality, design, product and testing, not just code. | Planning session |
| 2026-10-07 | No automated tests in the project; QA is a manual pass at the end of each phase (adb-driven emulator + owner's device checklist). | Owner's decision to save tokens and time. | Planning session |
| 2026-10-07 | Added `.claude/hooks/guard_paths.py` so PM, designer and QA can only edit their own files. | Keep roles honest: QA reports bugs instead of patching; PM and designer don't touch code. | Planning session |
