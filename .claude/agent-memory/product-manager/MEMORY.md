# product-manager memory

Lessons for this project only, newest first. Keep under ~150 lines; prune stale items. Focus: Estimates vs actuals, recurring Reject causes, owner preferences on trade-offs (as stated), risks being watched.

- 2026-10-07 · P1 ready checks (P1-07, P1-03, P1-02) · PRD scope can lack a ticket: "add more at any time, replace a clip" had none, and the designer found the gap, not me. At each phase kickoff, map the PRD rows for that phase to tickets. Pattern with no emulator: a Done when item that needs a later screen gets checked in that later ticket's hand session (the ticket check moves, the criteria don't, so no proposal). Evidence comes from a debug hook, a throwaway JVM harness and the owner's adb/ffprobe run. Ready checks with evidence bars go in acceptance-log, because the board must stay one screen. Watching: P1-03 (L) risks slipping on CI-only Media3 compiles; the P1-03 and P1-07 ticket checks can't close before P1-04.
- 2026-10-07 · P1 kickoff · Cloud sessions may have no Android SDK/emulator (dl.google.com blocked): CI-green code stays "Building (hand check pending)" until the owner or a later session runs it. Phase branch may be `claude/phase-*` (session constraint, goes on the board's Branch line, not the decision log). Watching: licensed starter sounds for P1-08 (none exist yet; P0-06 seeds moved), watermark position for P1-13, P1-15 credentials, reference-phone perf checks need the owner's device.
- 2026-10-08 · P0 · Planned 1 week, built in 2 days except P0-07 (moved out by the owner). Owner prefers one PR per phase and decides fast when given 2–3 options with a recommendation; values token and time efficiency.

<!-- - 2026-10-xx · P0-01 · lesson … -->
