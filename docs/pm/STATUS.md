# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved. Code that is CI-green but not yet run on an emulator or phone stays **Building (hand check pending)**. The repo files are the source of truth (owner, 8 Oct 2026). Evidence and details: `acceptance-log.md`.

**Current phase:** P1 · Core and video editor (planned 19 Oct–15 Nov 2026; kicked off 7 Oct, 12 days early) · Android launch ≈ 7 Mar 2027 · iOS ≈ 2 May 2027. Phase 0 merged to `main` as b9582b5 ([PR #7](https://github.com/ahmadpahmiii/memix/pull/7)).

**Branch:** `claude/phase-1-kickoff-cenfgz` (from `main` at b9582b5) serves as the Phase 1 branch (session constraint: cloud sessions can't push to `phase-1`). Latest CI: **green on 3784b9e** (P1-05, [run 37917006534](https://github.com/ahmadpahmiii/memix/actions/runs/37917006534)): secret scan, Android build and lint on every module (5m00s), and the iOS framework link (5m19s, the first link of the timeline code). One PR for the phase, after QA and the phase gate. **One builder at a time:** nobody is building now. P1-06 is next, after the P1-05 design review.

| ID | Ticket | Status | Spec | Owner | Links |
| --- | --- | --- | --- | --- | --- |
| P1-01 | Project model, storage, migrations | **Building (hand check pending)**: provisionally accepted; adb round trip at end-of-phase QA | — | principal-mobile-engineer | a453967, cd5c75f |
| P1-07 | Undo and redo (100), auto-save 500 ms | **Building (hand check pending)**: complete; provisionally accepted 9 Oct; kill checks and P1-04 A4/A5/A8 on a device | in P1-04 | principal-mobile-engineer | 53a8eb4, 961cc37, ab13bca |
| P1-02 | Gallery picker with cached copies | **Building (hand check pending)**: code provisionally accepted; N1/N2 built in 961cc37; owner runs steps 1–11 | done | principal-mobile-engineer | 0d6ea30 |
| P1-03 | `VideoEngine`, Android composition builder | **Building (hand check pending)**: code provisionally accepted; owner runs the export check; preview half = P1-04 step A2 | — | principal-mobile-engineer | 83ad6ff, f9403e6 |
| P1-04 | Preview: player, play, pause, seek, timecode | **Building (hand check pending)**: code provisionally accepted 9 Oct; design review approved with polish, fixes in ab13bca (R9 later). **Handoff Needs work:** add 4 owner steps (N2, `project_create` watch, R1 edit timing, open-editor time) | done | principal-mobile-engineer | 961cc37, acc9126, ab13bca, review P1-04 |
| P1-05 | Timeline UI | **Building (hand check pending)**: built and committed 9 Oct (3784b9e), CI green. Waiting for the design review (running) and the PM ticket check. Device steps A–C run at phase-end QA (owner, 9 Oct) | done | principal-mobile-engineer | 3784b9e, spec P1-05-timeline |
| P1-06 | Clip edits: split, trim, delete, duplicate, reorder | **Ready** (9 Oct; CI on 3784b9e green). Starts once the P1-05 design review is in, so any timeline fixes go first. R1 is not a gate (owner, 9 Oct). Scope boundary and builder gaps: acceptance-log, 9 Oct | done; designer adds the preview catch-up state | principal-mobile-engineer | spec a30ae8e |
| P1-16 | Add media and replace a clip | Not ready: waits for P1-06, P1-07 | in P1-06 spec | principal-mobile-engineer | proposal 2026-10-07 |
| P1-08 | Meme sounds, bundled starter pack | Not started. The 10 CC0 files are staged (35ca7f1); nobody has listened to them yet (owner decision below). Uses P1-06's tool bars; owns Move (drag and Move here) | in P1-05/06 | principal-mobile-engineer | 2de5ecf, 35ca7f1 |
| P1-09 | Original audio: volume, mute, detach | Not started. Owns the original-audio toggle, the Volume panel and Detach audio; uses P1-06's clip tool bar | in P1-05/06 | principal-mobile-engineer | |
| P1-10 | Text, basic | Not started; spec done (3db2ab3); `tool_use` ids set (`move_text` → `position_text`). Owner, 9 Oct: shadow and box → P2-11, `font` parameter added. Ready check due | done | principal-mobile-engineer | spec P1-10-text |
| P1-11 | Canvas: ratios, fit/fill, background | Not started | yes | principal-mobile-engineer | |
| P1-12 | Export | Not started | yes | principal-mobile-engineer | |
| P1-13 | Watermark (top-left, 3%) | Not started | — | principal-mobile-engineer | |
| P1-14 | Drafts screen | Not started | yes | principal-mobile-engineer | |
| P1-15 | Release plumbing | Waits on the owner's upload key + service account, latest 11 Jan 2027 (1B) | — | principal-mobile-engineer | proposal 2026-10-08 |
| P1-17 | Hindi display face (Teko Bold) | **Building (hand check pending)**: code provisionally accepted 9 Oct; waits for the designer's build review and tokens 52/36 → 60/44 | done | ux-designer, principal-mobile-engineer | ab13bca, spec P1-17 |
| P5-08 | Closed test for production access | Not started; starts ≈ 25 Jan 2027; needs P1-15 | — | product-manager, owner | proposal 2026-10-08 (2) |

**Top risks**
1. **Edit speed (R1).** Media3 1.11.1 rebuilds the whole player on every edit. Owner, 9 Oct: P1-06 is built without the number. The timeline shows each edit at once and the preview catches up. R1 is measured at phase-end QA, and a fix ticket follows if edits take over 100 ms. Also unseen until then: whether the preview flashes black after an edit.
2. **Every device check waits for phase-end QA** (owner, 9 Oct): P1-01 to P1-05, P1-07 and P1-17 so far, then P1-06. A problem found there (30 fps, leaks, R1) can mean rework under the timeline and the edits. The phase is 12 days ahead, which is the buffer for that.
3. **Play release path:** credentials by 11 Jan 2027; closed test ≈ 25 Jan; privacy-policy web address by mid-January.

**Watching:** sounds keep their times when earlier clips are cut (the owner tries it once P1-06 and P1-08 are built); P1-06 at the top of L; CI time now that lint checks every module (decision-log, 9 Oct); Supabase Free limits until R2 (P6-07); public repo, redistributable files only.

**Owner decisions open:** (1) Who listens to the 10 starter sounds before they ship (licensed content: the check that catches a famous recording). A: the owner plays them before the P1-08 commit, about 1 minute (recommended) / B: at phase-end QA, in the app / C: ship on the measured checks alone (`docs/content/P1-08-starter-sound-candidates.md`).
**Owner actions:** every Phase 1 device step at phase-end QA, including R1 (P1-04 block D) · Firebase test crash + `media_base_url` · upload key + service account by 11 Jan 2027.
