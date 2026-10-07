# Memix — coding handoff kit

Put these files at the root of the (empty) Memix repository, commit them, then open Claude Code there and paste the prompt from `KICKOFF_PROMPT.md`.

```
CLAUDE.md                         project brief Claude Code loads automatically (rules, team, quality, commands)
KICKOFF_PROMPT.md                 the first message for the coding session + official doc links
docs/PRD.md                       product requirements (export of the Claude Doc)
docs/TECHNICAL_DESIGN.md          architecture, data model, engines, backend, CI
docs/TICKETS.md                   phased backlog P0–P7 with "Done when" criteria
docs/DESIGN_SYSTEM.md             v2 palette, type, tokens table, Compose mapping, screen index
docs/pm/                          status board, acceptance log, decision log, proposals, phase reports, retros
docs/ux/                          UX research, screen specs, design reviews, design changelog
docs/qa/                          phase test plans and reports, bugs, regression list, reference renders
design/tokens.json                machine-readable design tokens
design/system/                    design-system source (brand book, component specs, reference CSS)
design/screens/*.dc.html          the 9 app screens (open in a browser; missing support.js is expected)
.claude/agents/                   5 role agents + CHANGELOG.md
.claude/skills/*/SKILL.md         18 skills (/play-release and /retro can be typed directly)
.claude/agent-memory/<agent>/     each agent's lessons file (project memory, committed)
.claude/hooks/guard_paths.py      keeps PM, designer and QA inside their own files (needs python3, included with Xcode tools)
.gitignore                        keeps secrets, build output and QA scratch media out of git
```

No automated tests by design: quality comes from CI builds, review on every ticket and a manual QA pass at the end of each phase.

Exports are from 7 Oct 2026. The linked Claude artifacts stay the source of truth; re-export if you change them.
