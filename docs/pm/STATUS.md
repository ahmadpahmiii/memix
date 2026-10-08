# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved. Code that is CI-green but not yet run on an emulator or phone stays **Building (hand check pending)**. The repo files are the source of truth (owner, 8 Oct 2026); the Claude Docs are the original plan, kept for reference only.

**Current phase:** P1 · Core and video editor (planned 19 Oct–15 Nov 2026; kicked off 7 Oct, 12 days early) · Android launch ≈ 7 Mar 2027 · iOS ≈ 2 May 2027. Phase 0 merged to `main` as b9582b5 ([PR #7](https://github.com/ahmadpahmiii/memix/pull/7)); report `phase-reports/P0.md`, retro `retros/P0.md` (proposals 1–7 applied, 8 not approved).

**Branch:** `claude/phase-1-kickoff-cenfgz` (from `main` at b9582b5) serves as the Phase 1 branch: this cloud session can push only there, not to `phase-1` (session constraint, not an owner decision). CI runs on it via the `claude/phase-*` push filter (3bb047e). Latest CI: green on 0d6ea30 ([run 37792214766](https://github.com/ahmadpahmiii/memix/actions/runs/37792214766)), the first real compile of the Compose UI. One PR for the phase, after QA and the phase gate. **One builder at a time**; helpers may run alongside.

| ID | Ticket | Status | Spec first | Owner | Links |
| --- | --- | --- | --- | --- | --- |
| P1-01 | Project model, serialization, SQLDelight storage, migrations | **Building (hand check pending)**: provisionally accepted; F5.1 closed (cd5c75f); the adb round trip moves to end-of-phase QA (owner, 8 Oct) | — | principal-mobile-engineer | a453967, cd5c75f, acceptance-log |
| P1-07 | Undo and redo (100), auto-save 500 ms | **Building (hand check pending)**, half done: history, auto-save, debug hook provisionally accepted; kill checks move to end-of-phase QA; buttons, toast, save banner in a second commit after P1-04 | done (in P1-04) | principal-mobile-engineer | 53a8eb4, acceptance-log |
| P1-02 | Gallery picker with cached copies | **Building (hand check pending)**: code provisionally accepted; **owner runs device steps 1–11 soon**; design review provisional from code, final at QA; N1 accepted until P1-04; N2 state: designer adding it to the spec, built in P1-04 | done (+N2 state) | principal-mobile-engineer | 0d6ea30, spec ca3ce51, acceptance-log |
| P1-03 | `VideoEngine` interface, Android composition builder | **Building (hand check pending)**: code provisionally accepted; handoff fixed (f9403e6, commands in `docs/qa/phase-1/engineer-hand-checks.md`); **owner runs the export check soon**; preview half in P1-04's session | — | principal-mobile-engineer | 83ad6ff, acceptance-log |
| P1-04 | Preview: player, play, pause, seek, timecode | **Ready**; the run also builds: delete a no-clip draft on leaving the editor, P1-02 N2 (after the designer's spec update) and N1. Top of L | done | principal-mobile-engineer | spec a30ae8e, acceptance-log |
| P1-05 | Timeline UI | Not ready: waits for P1-04 (all else passes) | done | principal-mobile-engineer | spec a30ae8e |
| P1-06 | Clip edits: split, trim, delete, duplicate, reorder | Not ready: waits for P1-05 (all else passes) | done | principal-mobile-engineer | spec a30ae8e |
| P1-16 | Add media and replace a clip (approved 8 Oct) | Not ready: waits for P1-06, P1-07 | Replace drafted in the P1-06 spec; designer finalizes it when P1-16 comes up | principal-mobile-engineer | proposal 2026-10-07-p1-02-followups |
| P1-08 | Meme sounds from the bundled starter pack | Not started; waits for P1-05. Sound files: after the owner opens `freesound.org` + `cdn.freesound.org`, one session downloads the 10 approved sounds, saves the license pages, normalizes and bundles them | done (in P1-05/06) | principal-mobile-engineer | 2de5ecf |
| P1-09 | Original audio: volume, mute, detach | Not started | done (in P1-05/06) | principal-mobile-engineer | |
| P1-10 | Text, basic | Not started | yes | principal-mobile-engineer | |
| P1-11 | Canvas: ratios, fit/fill, background | Not started | yes | principal-mobile-engineer | |
| P1-12 | Export | Not started | yes | principal-mobile-engineer | |
| P1-13 | Watermark | Not started; top-left, 3% margin (8 Oct) | — | principal-mobile-engineer | |
| P1-14 | Drafts screen | Not started | yes | principal-mobile-engineer | |
| P1-15 | Release plumbing: signing, CI upload to internal testing | **Waits on the owner (1B, approved 8 Oct):** built in the first session after the upload key + service account exist, latest 11 Jan 2027 (P5-08 needs it). Phases close without it; phase-end internal-testing builds are skipped until then | — | principal-mobile-engineer | proposal 2026-10-08-play-release-path (1) |
| P1-17 | Hindi display face (added 8 Oct) | Not started; designer picks the face first | yes (pick + document) | ux-designer, principal-mobile-engineer | decision-log 8 Oct |
| P5-08 | Closed test for production access (added 8 Oct) | Not started; starts ≈ 25 Jan 2027; owner recruits 15–20 testers in early January; needs P1-15 | — | product-manager, owner | proposal 2026-10-08-play-release-path (2) |

**Top risks**
1. **Play release path (new).** The personal account needs a closed test before production: at least 12 testers opted in for 14 days in a row, then Google's review. Planned start ≈ 25 Jan 2027 → access ≈ 15 Feb, with room for one rerun before 7 Mar. **Latest date for the upload key and service account: 11 Jan 2027** (25 Jan is the last date that keeps 7 Mar with no slack). Until P1-15 lands, no internal-testing builds; QA uses debug builds. The privacy policy needs a web address by mid-January (domain or temporary page).
2. **Device checks fall to the owner.** P1-02 and P1-03 now; P1-01 and P1-07 at end-of-phase QA (Claude Code on the owner's Mac, Android Studio emulator, which also covers P0's unverified items). Speed numbers and LeakCanary runs are on the owner's phone. Cloud sessions compile Android once the owner allows `dl.google.com` and `maven.google.com`; there's still no emulator (no KVM).
3. **Starter sound files gate P1-08** (sound first): waits for the owner to open the Freesound domains, then a fetch, license-page and normalize session before P1-08's sync check.

**Watching:** sounds keep their times when earlier clips are cut (owner tries the P1-06 build, then decides); P1-04 and P1-06 at the top of L; version-1 drafts and silent clips in P1-04; Supabase Storage Free limits until R2 (P6-07); public repo: secret scan, redistributable bundled files only.

**Owner answered all 15 questions on 8 Oct** (decision-log). **Decided:** play-release path 1B (P1-15 when the owner's credentials arrive, latest 11 Jan 2027); closed test from ≈ 25 Jan 2027. **Owner actions:** allow `freesound.org`, `cdn.freesound.org`, `dl.google.com`, `maven.google.com` · P1-02 steps 1–11 and P1-03's export check · Firebase: confirm the test crash, create `media_base_url` · GitHub Secret Protection + Push protection · upload key + service account by 11 Jan 2027.
