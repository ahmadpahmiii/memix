# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved. Code that is CI-green but not yet run on an emulator or phone stays **Building (hand check pending)**.

**Current phase:** P1 · Core and video editor (planned 19 Oct–15 Nov 2026; kicked off 7 Oct, 12 days early) · Android launch ≈ 7 Mar 2027 · iOS ≈ 2 May 2027. Phase 0 merged to `main` as b9582b5 ([PR #7](https://github.com/ahmadpahmiii/memix/pull/7)); report `phase-reports/P0.md`, retro `retros/P0.md` (proposals 1–7 applied, 8 not approved).

**Branch:** `claude/phase-1-kickoff-cenfgz` (from `main` at b9582b5) serves as the Phase 1 branch: this cloud session can push only there, not to `phase-1` (session constraint, not an owner decision). CI runs on it via the `claude/phase-*` push filter (3bb047e). Latest CI: green on 0d6ea30 ([run 37792214766](https://github.com/ahmadpahmiii/memix/actions/runs/37792214766)), the first real compile of the Compose UI. One PR for the phase, after QA and the phase gate.

| ID | Ticket | Status | Spec first | Owner | Links |
| --- | --- | --- | --- | --- | --- |
| P1-01 | Project model, serialization, SQLDelight storage, migrations | **Building (hand check pending)**: provisionally accepted; F5.1 closed (cd5c75f); open until the owner's adb round trip prints "verify: identical" | — | principal-mobile-engineer | a453967, cd5c75f, acceptance-log |
| P1-07 | Undo and redo (100), auto-save 500 ms | **Building (hand check pending)**, half done: history, auto-save, debug hook provisionally accepted (8 Oct); buttons, toast, save banner in a second commit after P1-04 | done (in P1-04) | principal-mobile-engineer | 53a8eb4, acceptance-log |
| P1-02 | Gallery picker with cached copies | **Building (hand check pending)**: code provisionally accepted (8 Oct), pending the design review; open until the owner's device steps 1–11 pass; owner questions (a)–(c) below | done | principal-mobile-engineer | 0d6ea30, spec ca3ce51, acceptance-log |
| P1-03 | `VideoEngine` interface, Android composition builder | **Building (hand check pending)**: code provisionally accepted (8 Oct); handoff needs the ffmpeg commands before the owner's export check; preview half checked in P1-04's session | — | principal-mobile-engineer | 83ad6ff, acceptance-log |
| P1-04 | Preview: player, play, pause, seek, timecode | **Ready** (rechecked 8 Oct after P1-02: duration, pixel size and has-audio now stored at import; ticket check adds silent clips, version-1 drafts and cold start) | done | principal-mobile-engineer | spec a30ae8e, acceptance-log |
| P1-05 | Timeline UI | Not ready: waits for P1-04 (all else passes) | done | principal-mobile-engineer | spec a30ae8e |
| P1-06 | Clip edits: split, trim, delete, duplicate, reorder | Not ready: waits for P1-05 (all else passes) | done | principal-mobile-engineer | spec a30ae8e |
| P1-16 | Add media and replace a clip (approved 8 Oct) | Not ready: waits for P1-06, P1-07 (P1-02 committed; all else passes) | Add media in P1-02 spec; tile in P1-05; Replace drafted in P1-06 spec: designer finalizes it (drop "pending", sounds out of scope) when P1-16 comes up | principal-mobile-engineer | proposal 2026-10-07-p1-02-followups |
| P1-08 | Meme sounds from the bundled starter pack | Not started; waits for P1-05; owner approved 10 of 12 CC0 starter sounds (8 Oct, 2de5ecf); files not fetched or normalized yet (Freesound unreachable here) | done (in P1-05/06) | principal-mobile-engineer | |
| P1-09 | Original audio: volume, mute, detach | Not started | done (in P1-05/06) | principal-mobile-engineer | |
| P1-10 | Text, basic | Not started | yes | principal-mobile-engineer | |
| P1-11 | Canvas: ratios, fit/fill, background | Not started | yes | principal-mobile-engineer | |
| P1-12 | Export | Not started | yes | principal-mobile-engineer | |
| P1-13 | Watermark | Not started; position confirmed top-left, 3% margin (8 Oct) | — | principal-mobile-engineer | |
| P1-14 | Drafts screen | Not started | yes | principal-mobile-engineer | |
| P1-15 | Release plumbing: signing, CI upload to internal testing | Not started; blocked on credentials | — | principal-mobile-engineer | |
| P1-17 | Hindi display face (added 8 Oct) | Not started; designer picks the face first | yes (pick + document) | ux-designer, principal-mobile-engineer | decision-log 8 Oct |

**Top risks**
1. **No device in this session.** Every hand check and perf number falls to the owner: now P1-01's round trip, P1-03's export and P1-07's kill checks; later 30 fps (P1-04), 60 fps (P1-05), 100 ms edits (P1-06), export time (P1-12) and LeakCanary runs, all on the owner's phone with the release-type build P1-04 adds.
2. **Phase 0 had no QA pass.** The P1 test plan also covers P0-01…P0-09, incl. unverified focus rings and the S4 nav-bar flicker on pop.
3. **Starter sound files gate P1-08** (sound first): 10 licenses approved, but no audio is in the repo yet; fetching, checking and normalizing them needs a session that reaches Freesound, before P1-08's sync check.

**Watching:** sounds keep their times when earlier clips are cut (tested in the P1 build); P1-06 at the top of L; version-1 drafts and silent clips in P1-04 (P1-02 N5); Supabase Storage Free limits until the R2 move (P3-13 now, P6-07 before launch); public repo: secret scan and redistributable bundled files only.

**Decided 8 Oct** (decision-log): (a) merge gate: convention, no branch protection; P0-02's follow-up closed · (b) Hindi face → P1-17 · (c) Supabase Storage now (P3-13), R2 before launch (P6-07) · (g) no named phone; owner's phone, no lag, no leaks · (h) shortcuts and lock UI → Later (L-01, L-02). **Open:** (d) Firebase: confirm the test crash, add `media_base_url` · P1-02 (asked by the main session): (a) delete an empty draft when the editor closes after undoing to nothing; (b) the failed-first-save state (N2); (c) the import sheet's exit animation (N1). **P1 inputs:** starter sounds: team search plus owner approval of each license · watermark: done · upload keystore and Play service account for P1-15 (`docs/CREDENTIALS.md`): open.
