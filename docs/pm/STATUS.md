# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved. Code that is CI-green but not yet run on an emulator or phone stays **Building (hand check pending)**. The repo files are the source of truth (owner, 8 Oct 2026). Evidence and details: `acceptance-log.md`.

**Current phase:** P1 · Core and video editor (planned 19 Oct–15 Nov 2026; kicked off 7 Oct, 12 days early) · Android launch ≈ 7 Mar 2027 · iOS ≈ 2 May 2027. Phase 0 merged to `main` as b9582b5 ([PR #7](https://github.com/ahmadpahmiii/memix/pull/7)).

**Branch:** `claude/phase-1-kickoff-cenfgz` (from `main` at b9582b5) serves as the Phase 1 branch (session constraint: cloud sessions can't push to `phase-1`). Latest CI: **green on 5d24f16** (P1-06 design fixes, [run 38375001507](https://github.com/ahmadpahmiii/memix/actions/runs/38375001507)): secret scan, Android build and lint on every module (3m15s), and the iOS framework link (3m9s). One PR for the phase, after QA and the phase gate. **One builder at a time:** nobody is building now. Next: **P1-16** (Ready, table order), then P1-08.

| ID | Ticket | Status | Spec | Owner | Links |
| --- | --- | --- | --- | --- | --- |
| P1-01 | Project model, storage, migrations | **Building (hand check pending)**: provisionally accepted; adb round trip at end-of-phase QA | — | principal-mobile-engineer | a453967, cd5c75f |
| P1-07 | Undo and redo (100), auto-save 500 ms | **Building (hand check pending)**: complete; provisionally accepted 9 Oct; kill checks and P1-04 A4/A5/A8 on a device | in P1-04 | principal-mobile-engineer | 53a8eb4, 961cc37, ab13bca |
| P1-02 | Gallery picker with cached copies | **Building (hand check pending)**: code provisionally accepted; N1/N2 built in 961cc37; owner runs steps 1–11 | done | principal-mobile-engineer | 0d6ea30 |
| P1-03 | `VideoEngine`, Android composition builder | **Building (hand check pending)**: code provisionally accepted; owner runs the export check; preview half = P1-04 step A2 | — | principal-mobile-engineer | 83ad6ff, f9403e6 |
| P1-04 | Preview: player, play, pause, seek, timecode | **Building (hand check pending)**: code provisionally accepted 9 Oct; design review approved with polish, fixes in ab13bca (R9 later). Handoff steps added in 8ad5570 (block D) | done | principal-mobile-engineer | 961cc37, acc9126, ab13bca, 8ad5570, review P1-04 |
| P1-05 | Timeline UI | **Building (hand check pending)**: code provisionally accepted 13 Oct. Design review pass with fixes: M1, P2–P4, P6–P8 done; P5 (TalkBack lane order) is a device check. Steps A–C and 15 screenshots at phase-end QA | done | principal-mobile-engineer | 3784b9e, 6fa5926, c6831c6, review P1-05 |
| P1-06 | Clip edits: split, trim, delete, duplicate, reorder | **Building (hand check pending)**: code provisionally accepted 13 Oct. Design review M1, M2 and P1–P7 fixed in 5d24f16 (CI green); Media3 gap closed. P6 TalkBack focus is step A8. P8 in a follow-up commit by the QA fix pass; P9 → P7-02. Blocks A–E and 21 screenshots at phase-end QA | done | principal-mobile-engineer | ea38fc6, 5d24f16, review P1-06 |
| P1-16 | Add media and replace a clip | **Ready** (13 Oct, second check): spec final, Done when worded for the tool and the end tile (PM, owner can veto). Next builder ticket. Builder must decide when unused media copies are deleted (acceptance log) | final (fb8b1b6, in P1-06 spec) | principal-mobile-engineer | proposal 2026-10-07 |
| P1-08 | Meme sounds, bundled starter pack | **Ready** (13 Oct). The ear check doesn't block the start (option A gates only the commit). After P1-16 (table order). Owns Move and sound volume | in P1-05/06 | principal-mobile-engineer | 2de5ecf, 35ca7f1 |
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
1. **Edit speed (R1).** Media3 1.11.1 rebuilds the whole player on every edit, and P1-06's trim also rebuilds at the drag's start and at release. R1 is measured at phase-end QA from the 60 fps recording (the log can read a frame low), and a fix ticket follows if edits take over 100 ms. Whether the preview flashes black stays unseen until then too.
2. **Every device check waits for phase-end QA** (owner, 9 Oct): P1-01 to P1-07 and P1-17 so far. A problem found there (30 fps, leaks, R1) can mean rework under the timeline and the edits. The phase is 12 days ahead, which is the buffer for that.
3. **Play release path:** credentials by 11 Jan 2027; closed test ≈ 25 Jan; privacy-policy web address by mid-January.

**Watching:** sounds keep their times when earlier clips are cut or media is added (the owner tries it once P1-06, P1-08 and P1-16 are built); no black frame relies on Media3 internals, so any Media3 bump re-runs P1-06 block C; TalkBack lane order (P1-05 P5, fix ready) and reorder focus (P1-06 P6); unused media copies stay on the phone until the draft is deleted (P1-16 decides the rule); Supabase Free limits until R2 (P6-07); public repo, redistributable files only; CI's `ubuntu-latest` moves to Ubuntu 26 from 19 Oct (GitHub notice), so a red run after that date checks the runner image first.

**Owner decisions open:** (1) Who listens to the 10 starter sounds before they ship (licensed content: the check that catches a famous recording). A: the owner plays them before the P1-08 commit, about 1 minute (recommended) / B: at phase-end QA, in the app / C: ship on the measured checks alone (`docs/content/P1-08-starter-sound-candidates.md`). None of these blocks P1-08's start. (2) If P1-16's spec isn't ready when the builder is free: A wait / B take P1-08 first (recommended) / C P1-08 first regardless (`proposals/2026-10-13-p1-08-before-p1-16.md`). **Moot for now (13 Oct):** P1-16's spec landed in time and P1-16 is Ready, so it's built first in table order. The proposal stays Proposed; an answer only matters if a later ticket's spec is late.
**Owner actions:** every Phase 1 device step at phase-end QA, including R1 (P1-04 block D) · Firebase test crash + `media_base_url` · upload key + service account by 11 Jan 2027.
