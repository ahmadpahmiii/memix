# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved. Code that is CI-green but not yet run on an emulator or phone stays **Building (hand check pending)**. The repo files are the source of truth (owner, 8 Oct 2026). Evidence and details: `acceptance-log.md`.

**Current phase:** P1 · Core and video editor (planned 19 Oct–15 Nov 2026; kicked off 7 Oct, 12 days early) · Android launch ≈ 7 Mar 2027 · iOS ≈ 2 May 2027. Phase 0 merged to `main` as b9582b5 ([PR #7](https://github.com/ahmadpahmiii/memix/pull/7)).

**Branch:** `claude/phase-1-kickoff-cenfgz` (from `main` at b9582b5) serves as the Phase 1 branch (session constraint: cloud sessions can't push to `phase-1`). Latest CI: **green on ab13bca** ([run 37877067250](https://github.com/ahmadpahmiii/memix/actions/runs/37877067250)); 961cc37 failed to compile, fixed in acc9126. One PR for the phase, after QA and the phase gate. **One builder at a time:** P1-05 now.

| ID | Ticket | Status | Spec | Owner | Links |
| --- | --- | --- | --- | --- | --- |
| P1-01 | Project model, storage, migrations | **Building (hand check pending)**: provisionally accepted; adb round trip at end-of-phase QA | — | principal-mobile-engineer | a453967, cd5c75f |
| P1-07 | Undo and redo (100), auto-save 500 ms | **Building (hand check pending)**: complete; provisionally accepted 9 Oct; kill checks and P1-04 A4/A5/A8 on a device | in P1-04 | principal-mobile-engineer | 53a8eb4, 961cc37, ab13bca |
| P1-02 | Gallery picker with cached copies | **Building (hand check pending)**: code provisionally accepted; N1/N2 built in 961cc37; owner runs steps 1–11 | done | principal-mobile-engineer | 0d6ea30 |
| P1-03 | `VideoEngine`, Android composition builder | **Building (hand check pending)**: code provisionally accepted; owner runs the export check; preview half = P1-04 step A2 | — | principal-mobile-engineer | 83ad6ff, f9403e6 |
| P1-04 | Preview: player, play, pause, seek, timecode | **Building (hand check pending)**: code provisionally accepted 9 Oct; design review approved with polish, fixes in ab13bca (R9 later). **Handoff Needs work:** add 4 owner steps (N2, `project_create` watch, R1 edit timing, open-editor time) | done | principal-mobile-engineer | 961cc37, acc9126, ab13bca, review P1-04 |
| P1-05 | Timeline UI | **Building** (started 9 Oct; ready check passed, recorded after the start) | done | principal-mobile-engineer | spec P1-05-timeline |
| P1-06 | Clip edits: split, trim, delete, duplicate, reorder | Not ready: waits for P1-05 and the owner's R1 number (edit → preview ms, latest when P1-05 lands ≈ 11 Oct) | done (+ no-black-flash state) | principal-mobile-engineer | spec a30ae8e |
| P1-16 | Add media and replace a clip | Not ready: waits for P1-06, P1-07 | in P1-06 spec | principal-mobile-engineer | proposal 2026-10-07 |
| P1-08 | Meme sounds, bundled starter pack | Not started; waits for P1-05 and the 10 sound files (owner opens the Freesound domains) | in P1-05/06 | principal-mobile-engineer | 2de5ecf |
| P1-09 | Original audio: volume, mute, detach | Not started | in P1-05/06 | principal-mobile-engineer | |
| P1-10 | Text, basic | Not started; spec done (3db2ab3); `tool_use` ids set (`move_text` → `position_text`); ready check waits for the owner's shadow/box answer | done | principal-mobile-engineer | spec P1-10-text |
| P1-11 | Canvas: ratios, fit/fill, background | Not started | yes | principal-mobile-engineer | |
| P1-12 | Export | Not started | yes | principal-mobile-engineer | |
| P1-13 | Watermark (top-left, 3%) | Not started | — | principal-mobile-engineer | |
| P1-14 | Drafts screen | Not started | yes | principal-mobile-engineer | |
| P1-15 | Release plumbing | Waits on the owner's upload key + service account, latest 11 Jan 2027 (1B) | — | principal-mobile-engineer | proposal 2026-10-08 |
| P1-17 | Hindi display face (Teko Bold) | **Building (hand check pending)**: code provisionally accepted 9 Oct; waits for the designer's build review and tokens 52/36 → 60/44 | done | ux-designer, principal-mobile-engineer | ab13bca, spec P1-17 |
| P5-08 | Closed test for production access | Not started; starts ≈ 25 Jan 2027; needs P1-15 | — | product-manager, owner | proposal 2026-10-08 (2) |

**Top risks**
1. **Edit speed (R1, new).** Media3 1.11.1 rebuilds the whole player on every edit. If an edit takes over 100 ms on the owner's phone, P1-06's Done when fails and the fast path feels slow. The owner measures it on the benchmark build before P1-06 starts.
2. **Device checks pile up on the owner:** P1-02, P1-03, P1-04 (30 fps, leaks), P1-17 now; P1-01, P1-07 at end-of-phase QA. P1-05 and P1-06 build on P1-04 before its 30 fps is proven.
3. **Play release path:** credentials by 11 Jan 2027; closed test ≈ 25 Jan; privacy-policy web address by mid-January.

**Watching:** starter sound files gate P1-08; sounds keep their times when earlier clips are cut; P1-06 at the top of L; Supabase Free limits until R2 (P6-07); public repo, redistributable files only.

**Owner decisions open:** (1) text shadow and background box: A in P1-10 now, B in P4-11, C new P2 ticket after P2-03 (recommended; asked 9 Oct). (2) `font` parameter on `tool_use`: A add (recommended) / B no (`proposals/2026-10-09-text-font-analytics.md`).
**Owner actions:** R1 edit timing by ≈ 11 Oct (once the step is pushed) · P1-04 steps A–C, P1-17 steps 1–6, P1-02 steps 1–11, P1-03 export check · allow `freesound.org`, `cdn.freesound.org`, `dl.google.com`, `maven.google.com` · Firebase test crash + `media_base_url` · GitHub push protection · upload key + service account by 11 Jan 2027.
