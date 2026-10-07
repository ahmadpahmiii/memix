---
name: retro
description: Run the Memix retrospective that improves the agent team — gather evidence from bugs, verdicts, reviews and agent memories, find what an agent, skill, hook or rule should have prevented, and propose exact edits for the owner to approve. The product manager runs it at the end of every phase; the owner can also type /retro.
---

# Retro

Nothing changes without the owner's approval. Keep agents lean: removing an instruction that didn't help is as valuable as adding one.

## Gather (this phase only)

- `docs/qa/bugs.md` and `docs/qa/phase-<n>/report.md`: bugs, severity, where they slipped through.
- `docs/pm/acceptance-log.md` and the phase report: Needs-work/Reject reasons, estimate vs actual.
- `docs/ux/reviews/`: Changes-requested items.
- PR review notes (`gh pr list --state merged`, `gh pr view <n> --comments` if `gh` is available).
- Each agent's `.claude/agent-memory/<agent>/MEMORY.md`.

## Analyze

- Group problems by root cause: unclear ticket, missing spec state, skipped standard, wrong assumption about an API, missing edge case, tooling gap, token cost.
- For each recurring cause (≥ 2 occurrences or any S1), name the cheapest fix: which agent prompt, skill, template, hook or CLAUDE.md rule should change.
- Note what worked and should stay.

## Output

`docs/pm/retros/P<n>.md`:
1. What went well (keep doing)
2. What went wrong — each with evidence links
3. Proposed changes — for each: file, exact before → after text (or a short diff), expected effect, cost; a checkbox `[ ] Approved by owner`
4. Changes to drop (instructions that added cost without value)

Then stop and ask the owner to approve. After approval the main session applies the checked changes and adds a row per change to `.claude/agents/CHANGELOG.md`.
