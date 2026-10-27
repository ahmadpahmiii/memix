# security-reviewer memory

Lessons for this project only, newest first. Keep under ~150 lines; prune stale items. Focus: Confirmed false alarms and why, secret formats this project uses, near-misses.

- 2026-10-08 · setup · The repo went public on 8 Oct 2026. A full-history scan (22 commits) found no credentials and no credential files. `androidApp/google-services.ci.json` is a committed placeholder with fake values; the real `google-services.json` stays local. The Supabase project ref and URL appear in docs and `.mcp.json` by design (not secrets).

<!-- - 2026-10-xx · P1-xx · lesson … -->
- 2026-10-27 · P1 PR · 94 commits / 480 paths, PASS. mcp__github__run_secret_scanning fails ("no GitHub Advanced Security") on this repo: do the manual diff review instead and note it as a warning. Release build is unsigned by design (benchmark uses the debug key); google-services.ci.json is unchanged and not AIza-shaped.
