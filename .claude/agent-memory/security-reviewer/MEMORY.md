# security-reviewer memory

Lessons for this project only, newest first. Keep under ~150 lines; prune stale items. Focus: Confirmed false alarms and why, secret formats this project uses, near-misses.

- 2026-10-08 · setup · The repo went public on 8 Oct 2026. A full-history scan (22 commits) found no credentials and no credential files. `androidApp/google-services.ci.json` is a committed placeholder with fake values; the real `google-services.json` stays local. The Supabase project ref and URL appear in docs and `.mcp.json` by design (not secrets).

<!-- - 2026-10-xx · P1-xx · lesson … -->
