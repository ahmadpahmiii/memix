# Phase 3 kickoff: gaps found, split of P3-12, and clarifications

Date: 10 Nov 2026 · Author: product manager · Status: **Approved by delegation (10 Nov): recommendation applied, owner reviews after Phase 3.** The delegation is the main session's relay of the Phase 2 working mode to Phase 3 (the owner's own 10 Nov words cover starting Phase 3 in parallel; they do not mention Phase 3 decisions). I applied the recommendations only where they do not hold the builder up. **TICKETS.md and PRD.md are not edited yet**: Q1, Q2 and Q3 change ticket text or add a ticket, so the main session should confirm "apply" (or the owner overrules) before I write them in. Nothing in Q1 to Q3 blocks P3-13, P3-12a, P3-01 or P3-02 to P3-10.

How I found these: at each phase kickoff I map the PRD rows to tickets (7 Oct lesson). Two PRD promises in Phase 3's area have no ticket, one ticket cannot be done as written, and the content half of two tickets cannot be finished by an agent.

## Q1. P3-11 names two screens that no ticket builds

**Problem.** P3-11 says "confirm in onboarding" and its Done when says "changing region in settings changes the Local tab". The tickets contain no onboarding flow and no Settings screen. The PRD only has the event `onboarding_complete` (language and region confirmed). As written, P3-11 would either build two screens it is sized S for, or ship a region choice with nowhere to change it.

**Considerations.**
- A: P3-11 builds one **Region sheet** (language and region lists) shown once on first launch and opened again from the Local tab header. `onboarding_complete` fires on the first confirm. Done when reads "changing region in the Region sheet (from the Local tab) changes the Local tab". Stays S to M, no new screen outside the sound area. Cost: a Settings screen is still missing later.
- B: Add a Settings screen ticket now (language, region, and later privacy options and credits). Size M, one more designer spec, about 1.5 days. Gives P5-01 (UMP "privacy options" must be reachable again) and P5-07 (credits screen needs an entry) their home early.
- C: Build neither; Local tab shows the phone's region with no way to change it. Fails the Done when.

**Recommendation: A for Phase 3.** Smallest thing that keeps the PRD promise. The Settings screen gap is real for P5-01 and P5-07: I will raise it as its own proposal at the Phase 5 ready check, not now.

**Impact.** Goal: Local tab is the reason a new region's user finds familiar sounds (day-1 retention). Dates: none. Reverse: replace the sheet with a Settings row later (UI move, no data change).

## Q2. The PRD's Home row "trending sounds for the user's region" has no ticket

**Problem.** PRD → Home lists trending templates and trending sounds. P0-05 left both out until the catalog exists ("P3-01, P4-05"), and P4-05 covers templates. Nothing covers the sounds row. Home is the first screen every day; an empty sounds row hurts "sound first".

**Considerations.**
- A: New ticket **P3-14**, size S, Needs P3-01 and P3-03: a "Trending sounds" row on Home (the user's region plus global, tap plays a preview, add goes through the sound flow). Needs a small designer addition. About 1 day.
- B: Fold it into P3-02. P3-02 is already L; it would go past two sessions.
- C: Leave to Phase 5 polish. Risks that nobody builds it.

**Recommendation: A**, placed after P3-06 in order. **Impact.** About 1 day in a phase that is 27 days early. No date moves.

## Q3. P3-12 split: script first, the 500-sound load is content work

**Problem.** P3-12's Done when ("500 sounds are live, each with complete license fields") depends on sourcing 500 licensed sounds, the owner's license approval (PRD: "the owner approves each license", 8 Oct) and a secret key an agent in a cloud session does not have. The ticket cannot be "built and checked" in one go, and the code half would sit open behind the content half.

**Considerations.**
- A: Split. **P3-12a** the intake script (trim, normalize, encode, waveform, upload, row template), checked with fixture files and `--dry-run`, then run once on the 10 starters. **P3-12b** the load of 500 sounds, run in batches on the owner's Mac, finishing when 500 are live; it runs alongside the later tickets and closes before the Phase 3 gate or, at the latest, before the closed test (about 25 Jan 2027).
- B: Keep one L ticket and keep Phase 3 open until 500 are live. Blocks the gate on content speed.
- C: Cut the first load to 200 sounds. A scope cut, not needed yet; revisit at 150 sounds if sourcing is slow.

**Recommendation: A.** **Impact.** No scope change; the Done when moves to 12b unchanged. Gate waits on 12b only if it is not done by then (I will say so in the phase report).

## PM clarifications (nothing added or cut; owner can veto)

1. **P3-13, "immutable cache headers".** Supabase Storage may not accept the `immutable` token. Keys are content-addressed or versioned (a changed file gets a new key), so `Cache-Control: public, max-age=31536000` is accepted if the engineer shows `immutable` cannot be set. The engineer checks first and records the header actually returned (with `curl -I`).
2. **P3-13 `media_base_url`.** Remote Config first; if the key is empty (the owner's console step is still owed since P0-08), fall back to a build-config value from `local.properties` (`memix.media.baseUrl`, same pattern as the Supabase URL; engineer adds the row to `docs/CREDENTIALS.md`). No URL in source code.
3. **P3-01 search offline.** TECHNICAL_DESIGN says search is "never cached". Offline behavior is not specified. Clarification: online search uses the `search_sounds` RPC (trigram, so "bruhh" finds "bruh"); offline, the search box searches the cached sounds by simple contains-match and says so ("Offline: showing matches from saved sounds"). The designer words the state.
4. **P3-04 starter pack.** The pack is the 10 approved sounds in `content/starter-sounds/` plus 40 more from P3-12b's first approved batch, bundled in the app (about 46 KB each, 50 sounds about 2.3 MB). The mechanism is built and provisionally accepted on the 10; the "50" is content and closes when the owner approves the other 40.
5. **P3-08 "stays in sync".** Bar: a clap test; the clap's onset in the exported file is within 2 frames (67 ms) of the clap in the preview, with the built-in or a wired microphone. Bluetooth headsets are outside the bar and get a one-line note. The engineer compensates the measured input latency.
6. **P3-09 "sound the same".** Bar: the preview chain and the export chain are the same `AudioProcessor` spec (rule 5); an offline render of each preset through both paths differs by less than -40 dB (null test, as the P1-08 audio harness), plus an ear check on the phone for each preset. Size watch: past two build sessions, split 09a (fades, pitch, speed, waveforms) and 09b (echo, bass boost and distortion, voice-changer presets).
7. **P3-08 microphone permission.** Asked once, at the first tap on Record, with Android's own dialog, as the notification permission at the first export (21 Oct). A denied state explains and opens the app's settings. The Play Data safety answer for the microphone (audio stays on the phone) goes into P6-05's list.
8. **Needs, real versus listed.** P3-05 also needs P3-03 (preview in the picker) and P3-04 (download state); P3-10 needs P3-03; P3-02's Favorites tab is empty until P3-06 and My sounds until P3-07, so the order puts P3-06 straight after P3-02 and the designer states which tabs show before. P3-07's imported files are media copies: every new media-holding field must go into `mediaCopyPaths` and `withMediaCopyPaths` (else the cleaner deletes it) and needs a schema bump with an old-draft check.
9. **Analytics.** Existing: `sound_preview`, `sound_add`, `sound_report`. `sound_add.tab` takes `trending`, `local`, `global`, `favorites`, `my_sounds`, `recent`, `search` (the last two are new values of an existing parameter). New `tool_use` ids for P3-08 and P3-09 are confirmed at each spec check (same-id rule, 8 Oct).

## Owner-only items and how to keep building

| Item | Who can do it | Recommendation | Log as |
| --- | --- | --- | --- |
| Remote Config key `media_base_url` (Firebase console; owed since P0-08) | Owner only (console login) | Fallback from `local.properties` (clarification 2) so nothing waits; owner adds the key with P1-15 check D | Owner-only, non-blocking |
| Supabase: bucket, applying the sound seed, uploads | Bucket and seed SQL: the Supabase MCP (OAuth held by Claude Code on the owner's machine), if it is signed in; **file uploads need `SUPABASE_SECRET_KEY` in `.env` on the owner's Mac** (never in repo, CI or chat; `docs/CREDENTIALS.md`) | Build the migration, script and app now with fixtures and `--dry-run`; the owner (or QA's Mac session) runs the upload once and the live checks (`curl -I`, anon upload refused) | Owner-only (key); rest delegated |
| P3-12b license approval and sourcing | Owner approves licenses; sourcing needs `FREESOUND_API_KEY` in `.env` (owner's Mac) | The owner approves the source rule once (CC0, CC-BY with an in-app credit, own or recreated) and then reviews a table per batch of about 50 (title, source URL, license, proof, credit); the script refuses any row with a missing field. Run sourcing in Claude Code on the Mac in batches while cloud sessions build the app. Free-to-use-in-videos licences that bar standalone redistribution do not qualify | Owner-only |
| P3-04 starter pack, 40 new sounds | Owner approves | Same table review, first batch; 10 already approved (8 Oct); the listen for those 10 is still owed | Owner-only (approval); build delegated |
| "Voice lines (recreated)" and Local content for 5 regions | Needs a person to record or an owner decision | Risk only. If sourcing is short at 150 sounds, I bring a scope-cut proposal (fewer in those two categories) | Risk, not a decision yet |

## Dates

No re-plan needed. Phase 3 is planned 7 to 27 Dec (3 weeks, weeks 9 to 11) and starts 10 Nov, 27 days early. Phase 2 built 15 tickets in 14 days against 21 planned. Phase 3's 14 rows (13 plus P3-14) are about 4 L, 5 M, 5 S by size; at Phase 2's pace that is about 3 weeks of builder time, so a build end near 1 to 4 Dec is realistic, with content (12b) and the owner's QA as the long poles. Planned dates, P4 from 28 Dec and both launch dates stay: Android about 7 Mar 2027, iOS about 2 May 2027. The real constraint is the QA backlog: 17 + 15 + Phase 3's tickets, all provisional, no device run yet.
