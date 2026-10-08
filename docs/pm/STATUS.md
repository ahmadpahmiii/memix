# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved. Code that is CI-green but not yet run on an emulator or phone stays **Building (hand check pending)**.

**Current phase:** P1 · Core and video editor (planned 19 Oct–15 Nov 2026; kicked off 7 Oct, 12 days early) · Android launch ≈ 7 Mar 2027 · iOS ≈ 2 May 2027. Phase 0 merged to `main` as b9582b5 ([PR #7](https://github.com/ahmadpahmiii/memix/pull/7)); report `phase-reports/P0.md`, retro `retros/P0.md` (proposals 1–7 applied, 8 not approved).

**Branch:** `claude/phase-1-kickoff-cenfgz` (from `main` at b9582b5) serves as the Phase 1 branch: this cloud session can push only there, not to `phase-1` (session constraint, not an owner decision). CI runs on it via the `claude/phase-*` push filter (3bb047e). Latest CI: green on a30ae8e ([run 37726228899](https://github.com/ahmadpahmiii/memix/actions/runs/37726228899)). One PR for the phase, after QA and the phase gate.

| ID | Ticket | Status | Spec first | Owner | Links |
| --- | --- | --- | --- | --- | --- |
| P1-01 | Project model, serialization, SQLDelight storage, migrations | **Building (hand check pending)**: provisionally accepted; F5.1 closed (cd5c75f); open until the owner's adb round trip prints "verify: identical" | — | principal-mobile-engineer | a453967, cd5c75f, acceptance-log |
| P1-07 | Undo and redo (100), auto-save 500 ms | **Building (hand check pending)**, half done: history, auto-save, debug hook provisionally accepted (8 Oct); buttons, toast, save banner in a second commit after P1-04 | done (in P1-04) | principal-mobile-engineer | 53a8eb4, acceptance-log |
| P1-02 | Gallery picker with cached copies | **Building** (8 Oct) | done | principal-mobile-engineer | spec ca3ce51, acceptance-log |
| P1-03 | `VideoEngine` interface, Android composition builder | **Building (hand check pending)**: code provisionally accepted (8 Oct); handoff needs the ffmpeg commands before the owner's export check; preview half checked in P1-04's session | — | principal-mobile-engineer | 83ad6ff, acceptance-log |
| P1-04 | Preview: player, play, pause, seek, timecode | **Ready** (8 Oct) | done | principal-mobile-engineer | spec a30ae8e, acceptance-log |
| P1-05 | Timeline UI | Not ready: waits for P1-04 (all else passes) | done | principal-mobile-engineer | spec a30ae8e |
| P1-06 | Clip edits: split, trim, delete, duplicate, reorder | Not ready: waits for P1-05 (all else passes) | done | principal-mobile-engineer | spec a30ae8e |
| P1-08 | Meme sounds from the bundled starter pack | Not started; needs licensed starter sounds | done (in P1-05/06) | principal-mobile-engineer | |
| P1-09 | Original audio: volume, mute, detach | Not started | done (in P1-05/06) | principal-mobile-engineer | |
| P1-10 | Text, basic | Not started | yes | principal-mobile-engineer | |
| P1-11 | Canvas: ratios, fit/fill, background | Not started | yes | principal-mobile-engineer | |
| P1-12 | Export | Not started | yes | principal-mobile-engineer | |
| P1-13 | Watermark | Not started; position unconfirmed (PRD open item) | — | principal-mobile-engineer | |
| P1-14 | Drafts screen | Not started | yes | principal-mobile-engineer | |
| P1-15 | Release plumbing: signing, CI upload to internal testing | Not started; blocked on credentials | — | principal-mobile-engineer | |

**Top risks**
1. **No device in this session.** Every hand check and perf number falls to the owner: now P1-01's round trip, P1-03's export and P1-07's kill checks; later 30 fps (P1-04), 60 fps (P1-05), 100 ms edits (P1-06), export time (P1-12). Perf needs a named reference phone and a release-type build P1-04 adds.
2. **Phase 0 had no QA pass.** The P1 test plan also covers P0-01…P0-09, incl. unverified focus rings and the S4 nav-bar flicker on pop.
3. **Media hosting undecided** (P0-07 moved). Decide before P3 (7 Dec); P3-01, P3-04, P3-12, P4-05, P4-15 wait on it.

**Watching:** sounds keep their times when earlier clips are cut (tested in the P1 build); P1-06 at the top of L; source duration and has-audio stored in P1-02's build, not discovered in P1-04.

**Owner decisions open:** (a) GitHub merge gate: Pro, public repo, or convention · (b) Devanagari display face for Hindi · (c) media hosting, before 7 Dec · (d) Firebase: confirm the test crash, add `media_base_url` · (e) CLAUDE.md rule 3: allow the composition root to create the SQLDelight driver · (f) `proposals/2026-10-07-p1-02-followups.md`: A, new S ticket P1-16 "Add media" and "Replace a clip"; B, add `gallery` to `project_create.source` · (g) **name the reference phone** (2023 or newer, upper-mid or flagship) · (h) keyboard shortcuts and the locked-track UI in the P1 specs go beyond the PRD: not pass/fail unless you want them in P1. **P1 inputs:** licensed starter sounds for P1-08 · watermark position for P1-13 · upload keystore and Play service account for P1-15 (`docs/CREDENTIALS.md`).
