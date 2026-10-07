# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved. Code that is CI-green but not yet run on an emulator or phone stays **Building (hand check pending)**.

**Current phase:** P1 · Core and video editor (planned 19 Oct–15 Nov 2026; kicked off 7 Oct, 12 days early) · Android launch ≈ 7 Mar 2027 · iOS ≈ 2 May 2027. Phase 0 merged to `main` as b9582b5 ([PR #7](https://github.com/ahmadpahmiii/memix/pull/7)); report `phase-reports/P0.md`, retro `retros/P0.md` (proposals 1–7 applied, 8 not approved).

**Branch:** `claude/phase-1-kickoff-cenfgz` (from `main` at b9582b5) serves as the Phase 1 branch: this cloud session can push only there, not to `phase-1` (session constraint, not an owner decision). CI runs on it via the `claude/phase-*` push filter (3bb047e). One PR for the phase, after QA and the phase gate.

| ID | Ticket | Status | Spec first | Owner | Links |
| --- | --- | --- | --- | --- | --- |
| P1-01 | Project model, serialization, SQLDelight storage, migrations | **Ready** (7 Oct) | — | principal-mobile-engineer | |
| P1-07 | Undo and redo (100), auto-save 500 ms | Not started | — | principal-mobile-engineer | |
| P1-02 | Gallery picker with cached copies | Not started | yes | principal-mobile-engineer | |
| P1-03 | `VideoEngine` interface, Android composition builder | Not started | — | principal-mobile-engineer | |
| P1-04 | Preview: player, play, pause, seek, timecode | Not started | yes | principal-mobile-engineer | |
| P1-05 | Timeline UI | Not started | yes | principal-mobile-engineer | |
| P1-06 | Clip edits: split, trim, delete, duplicate, reorder | Not started | yes | principal-mobile-engineer | |
| P1-08 | Meme sounds from the bundled starter pack | Not started; needs licensed starter sounds | in P1-05/06 spec | principal-mobile-engineer | |
| P1-09 | Original audio: volume, mute, detach | Not started | in P1-05/06 spec | principal-mobile-engineer | |
| P1-10 | Text, basic | Not started | yes | principal-mobile-engineer | |
| P1-11 | Canvas: ratios, fit/fill, background | Not started | yes | principal-mobile-engineer | |
| P1-12 | Export | Not started | yes | principal-mobile-engineer | |
| P1-13 | Watermark | Not started; position unconfirmed (PRD open item) | — | principal-mobile-engineer | |
| P1-14 | Drafts screen | Not started | yes | principal-mobile-engineer | |
| P1-15 | Release plumbing: signing, CI upload to internal testing | Not started; blocked on credentials | — | principal-mobile-engineer | |

**Top risks**
1. **No emulator in this session** (dl.google.com blocked: no Android SDK, Google Maven or emulator). Engineers write code and CI builds it; every hand check, and every perf number on the reference phone (P1-04, P1-05, P1-12), falls to the owner or a later session before a ticket counts as Built.
2. **Phase 0 had no QA pass.** The P1 test plan also covers P0-01…P0-09, incl. unverified focus rings and the S4 nav-bar flicker on pop.
3. **Media hosting undecided** (P0-07 moved). Decide before P3 (7 Dec); P3-01, P3-04, P3-12, P4-05, P4-15 wait on it.

**Owner decisions open:** (a) GitHub merge gate: Pro, public repo, or convention · (b) Devanagari display face for Hindi · (c) media hosting, before 7 Dec · (d) Firebase: confirm the test crash, add `media_base_url`. **P1 inputs:** licensed starter sounds for P1-08 · watermark position for P1-13 · upload keystore and Play service account for P1-15 (`docs/CREDENTIALS.md`).
