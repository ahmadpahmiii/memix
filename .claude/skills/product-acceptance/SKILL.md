---
name: product-acceptance
description: The Memix product manager's checklists and templates — ticket ready check, ticket check after build, phase gate report with final verdicts, scope proposals for the owner, the status board and the decision log. Use before a ticket starts, after it's built, when a phase ends, or when scope/priority/dates might change.
---

# Product acceptance

Files live in `docs/pm/`: `STATUS.md`, `acceptance-log.md`, `phase-reports/`, `proposals/`, `decision-log.md`, `retros/`.

## 1. Ready check (before build)

Ready only if all hold, else Not ready with what's missing:
- "Done when" is concrete and checkable by hand on an emulator/phone (no "works well").
- Every ticket in `Needs` is built.
- Size ≤ L; otherwise propose a split.
- UI ticket → designer spec exists or is requested now.
- No open question blocks it (check PRD open items).
Write the verdict on the ticket's row in `STATUS.md` (Not started / Ready / Building / Built / QA / Accepted / Rejected / Moved).

## 2. Ticket check (after build)

Go through "Done when" one by one against the engineer's handoff (what was checked by hand, screenshots, numbers), the principal's review and the designer's review. Also check PRD rules: ads never in editors or during export; licensed content only; no user uploads; analytics events from the PRD fire; strings in five languages.
Verdict in `acceptance-log.md`: **Provisional accept** / **Needs work** (list) / **Reject** (reasons). Final verdicts come at the phase gate.

## 3. Phase gate (after QA's phase report)

`docs/pm/phase-reports/P<n>.md`:
1. Phase goal (from PRD release plan) — met / partly / not met
2. Tickets: final verdict each (Accept / Accept with follow-ups / Reject / Moved), linked to QA results
3. Open bugs by severity — **gate fails with any open S1 or S2**
4. Performance budgets touched this phase — numbers vs targets
5. Schedule: planned vs actual dates, slip, effect on launch dates
6. Risks and the cut list (speed curves, blend modes, transition count) — recommend cuts only as proposals
7. Next phase: ticket order, readiness, what the designer must spec first
8. Decisions needed from the owner
Then run `/retro`.

## 4. Proposals (scope, priority, dates)

`docs/pm/proposals/<yyyy-mm-dd>-<topic>.md`: problem · options (2–3) · recommendation · impact on goal (5,000 DAU by month 3), dates and quality · status **Proposed**. Only after the owner writes Approved: update PRD/TICKETS and add a row to `decision-log.md` (date, decision, why, who approved).

## 5. Status board

`STATUS.md`: current phase, dates vs plan, one row per ticket (ID, status, owner agent, links), top 3 risks. Update at every step; keep it one screen long.
