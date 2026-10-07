---
name: ticket-workflow
description: How a Memix ticket moves through the team — PM ready check, designer spec, build by the principal mobile or backend engineer, code and design review, PM ticket check — and how a phase closes with manual QA, the phase gate and /retro. Use when the user says "next ticket", names a ticket ID, asks what to work on, or a phase is finishing.
---

# Ticket and phase workflow

The main session orchestrates. Each step names the agent to delegate to. Memix has **no automated tests**: quality comes from review on every ticket and a **manual QA pass at the end of each phase**.

## Per ticket

| # | Step | Agent | Output |
| --- | --- | --- | --- |
| 1 | **Pick.** The ticket the user named, or the next one in the current phase whose `Needs` are merged (`docs/TICKETS.md`, `docs/pm/STATUS.md`). | main | one line: which ticket and why |
| 2 | **Ready check.** Testable "Done when", dependencies met, size ≤ L, spec needed? | `product-manager` | Ready / Not ready in `docs/pm/STATUS.md` |
| 3 | **Spec** (UI tickets only). All states, copy, motion, accessibility. | `ux-designer` | `docs/ux/specs/<ID>-<screen>.md` |
| 4 | **Build** on branch `<id>-<slug>` (e.g. `p1-04-preview`). App code → `principal-mobile-engineer`; Supabase/R2/scripts/remote data → `backend-engineer`. | engineer | code + PR handoff (what changed, what was checked by hand, emulator screenshots, numbers) |
| 5 | **Build checks:** Android debug build, lint, iOS framework compile. | engineer | green CI |
| 6 | **Code review** with the `code-standards` checklist. The principal reviews all Kotlin, including the backend engineer's. | `principal-mobile-engineer` | review notes in the PR; fixes applied |
| 7 | **Design review** (UI tickets) from the PR screenshots. | `ux-designer` | `docs/ux/reviews/<ID>.md` |
| 8 | **Ticket check.** "Done when" items vs the handoff evidence; provisional verdict. | `product-manager` | `docs/pm/acceptance-log.md` |
| 9 | **PR.** Title `<ID>: <ticket name>`; body = handoff + review + design verdict. The owner merges. | main | PR link |

Rules:
- Don't start step 4 on a Not-ready ticket or a UI ticket without a spec.
- Don't write unit, UI, screenshot or golden-frame tests. Verify by running the app.
- Never call a ticket done with failing build checks or unchecked "Done when" items. Say what's left.

## End of phase

1. **PM** confirms every ticket in the phase is built (or explicitly moved by an approved proposal) and asks QA to start.
2. **QA** (`qa-engineer`): writes the phase test plan, runs the manual pass on the emulator, files bugs, and hands the owner a short device-only checklist.
3. **Fix loop.** Engineers fix S1/S2 bugs (S3/S4 as the PM prioritizes); QA re-tests.
4. **Phase gate.** The PM writes `docs/pm/phase-reports/P<n>.md` with final verdicts per ticket and the decisions the owner needs to make.
5. **Retro.** The PM runs `/retro` and proposes agent, skill and rule changes. The owner approves, the main session applies them, and logs them in `.claude/agents/CHANGELOG.md`.
6. Upload the phase build to Play internal testing (`/play-release`, internal track). Update `CLAUDE.md` → Current phase.
