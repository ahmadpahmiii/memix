# QA

Memix has no automated tests. The QA engineer tests manually at the end of every phase (method: `qa-verification` skill).

- `phase-<n>/test-plan.md`: manual steps per "Done when" item, states to compare, edge cases
- `phase-<n>/screens/`: screenshots taken during the pass
- `phase-<n>/device-checklist.md`: short list of real-phone checks for the owner
- `phase-<n>/report.md`: per-ticket results, regression, performance, verdict
- `bugs.md`: every bug with severity S1–S4
- `regression.md`: smoke list re-run every phase
- `references/`: reference renders for effects (iOS parity)
