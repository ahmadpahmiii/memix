# Phase 4 kickoff: ready checks, splits, order, content, owner items

**Date:** 17 Nov 2026 · **Author:** PM · **Status:** **Approved by delegation, recommendation applied** (owner's standing delegation; owner reviews at the end-of-build approval). **TICKETS and PRD are not edited until the main session confirms "apply".**

Context: owner, 17 Nov: Phase 3's open work moves to the end, Phase 4 starts now; then "ship it all" (Phases 4 to 7 code, one QA and approval list at the end). See `decision-log.md`.

## Ready check (17 Nov)

Rule: Ready = "Done when" is checkable by hand, every Need is built (Phases 1 to 3 are all built, provisional), size is L or smaller, a UI spec exists or is requested, no open question. "Needs spec" means the designer specs before that half is built; engine-only halves need none.

| Ticket | Verdict | Spec? | Note |
| --- | --- | --- | --- |
| P4-01 Overlays (L) | Ready, **split 01a/01b** | 01b yes | Needs P1-05 built. 01a: model, schema bump, multi-layer composition, opacity, z-order, transform. 01b: on-frame gestures, overlay lane, picker. "Five overlays at 30 fps" is a phone number (end-of-build list) |
| P4-02 Keyframes (L) | Blocked on P4-01; **split 02a/02b** | 02b yes | 02a: model, easing, interpolation in preview and export. 02b: timeline markers and panel |
| P4-03 Blend modes, masks (L) | Blocked on P4-01; **split 03a/03b** | 03b yes | Each is a cut-list item. References for 10 modes and 4 masks in `docs/qa/references/` |
| P4-04 Green screen (M) | Blocked on P4-01 | yes | "Standard green screen clip": the engineer makes a synthetic clip as fixture; a real clip joins the owner list |
| P4-05 Templates (L) | Blocked on P4-04; **split 05a/05b** | yes (both) | P0-06's 3 seed templates never existed (moved with P0-07): build 3 fixture templates. 05a: repository, Templates screen. 05b: slot-fill, open in editor, `template_open` and `project_create source: template` |
| P4-06 About 30 effects (L) | Ready after the effect list is fixed; **split 06a/06b** | yes | The designer's spec freezes the 30 names; **zoom punch must be in it** (P4-14 needs it). Each gets a reference render |
| P4-07 About 20 video LUT filters (M) | Ready (Needs P2-04 built) | small addendum to the P2-04 spec | Same ids and LUT data as the photo filters; list the 20 ids in the handoff |
| P4-08 About 20 transitions (M) | Ready (Needs P1-06) | yes | Audio continuous across each; cut list: the count |
| P4-09 Speed (L) | Ready; **split 09a/09b** | yes | 09a: constant 0.1x to 10x, audio follows or mutes. 09b: curves and presets (cut-list item). Interacts with P3-09's audio speed (0.5x to 2x) |
| P4-10 Reverse, freeze (M) | Ready (Needs P1-06) | small | Smooth reverse preview needs a decode strategy; engineer notes the memory cost |
| P4-16 Crop, rotate, flip, per-clip Fit or Fill (M) | Blocked on P4-01 | **yes, and it does not exist** | The 21 Oct proposal said "spec before Phase 4". There is no `P4-16` file in `docs/ux/specs/`; only notes in `P1-11-canvas.md` (line 329) and `P2-04-image-tools.md` (lines 67, 290) say the P2-04 Crop screen is reusable. Designer specs it first |
| P4-11 Text animations, presets (M) | Ready (Needs P1-10) | yes | |
| P4-12 Stickers in video, photo GIF export (M) | Ready (Needs P3-01, P2-08) | yes | Watch M (animated decode, three sources, GIF encoder). Catalog packs use fixtures until P4-15b. Sticker rule 35% size, 10 to 500% |
| P4-13 Video background removal (L) | Blocked on P4-01; **split 13a/13b** | yes | 13a: MediaPipe video mode, mask cache, low-res preview. 13b: full-res export, refine, progress. People only (settled 8 Oct) |
| P4-14 Meme shortcuts (M) | Blocked on P4-06 (zoom) | yes | |
| P4-15 Load 40 + 60 + 20 (L) | Blocked on P4-05; **split 15a/15b** | none | 15a: loader and fixture packs. 15b: the real load, **owner-gated** |

## Splits

- **A (recommended):** the eight splits above, all wording only: L becomes two M. Cost: about half a day of extra handoffs per split; benefit: each half fits one or two sessions, and 03b, 09b and 06b can be cut alone.
- B: keep Ls and split only when a session runs out. Cheaper on paper; in Phase 3 the unplanned splits (P3-09, P3-12) came late and delayed the board.
- C: split only the three most at risk (P4-01, P4-09, P4-13).

**Recommendation: A.**

## Content (P4-05 seeds, P4-12 packs, P4-15)

- **A (recommended): build with fixtures, load later.** Fixtures are original, script- or hand-made items (solid-color and generated clips, simple original layouts, shape-based sticker packs) kept in the repo's fixtures and loaded in debug builds only, never into the live Supabase catalog. They exercise slots, presets, packs and the license-field checks. The real content load (P4-15b) goes on the end-of-build owner list.
- B: wait for real content. Blocks P4-05b, P4-12 and P4-14's demo; no.
- C: fixtures go to live Supabase. They would ship; no.

**Legal note:** photo "famous formats" must be original illustrated versions (PRD). Which formats, and the lawyer read, are the owner's.

## Build order (one builder; designer specs run ahead)

1. P4-01a, 2. P4-01b, 3. P4-02a, 4. P4-02b, 5. P4-04, 6. P4-03a, 7. P4-03b, 8. P4-07, 9. P4-06a, 10. P4-06b, 11. P4-08, 12. P4-09a, 13. P4-10, 14. P4-09b, 15. P4-16, 16. P4-11, 17. P4-12, 18. P4-13a, 19. P4-13b, 20. P4-05a, 21. P4-05b, 22. P4-14, 23. P4-15a. P4-15b waits for the owner.

Why: overlays and keyframes are the base for keying, masks, segmentation, crop and templates; engine-heavy tickets come early while the model is fresh; P4-05 comes late so templates can use effects, overlays and keying; P4-14 needs P4-06's zoom. P4-16 follows P4-10 as the owner placed it. The designer's spec order: **P4-01b, P4-16, P4-04, P4-02b, P4-03b, P4-06, P4-07 addendum, then the rest in build order.**

Schema: each persisted-shape ticket bumps `schemaVersion` (13 now) with an identity migration and the old-draft check, as in Phase 3. Recommendation: the engineer adds the whole model of a ticket pair in the first half (as P3-09a did), so each pair bumps once.

## Dates

Planned 28 Dec to 7 Feb (6 weeks). Phase 3 (16 rows) took about 6 builder days against 3 weeks planned. At that pace the 23 build rows of Phase 4 take roughly two to three weeks (an estimate from one phase, not a promise). **No re-plan needed.** Launch dates are not at risk from the code; they are at risk from the QA debt and the owner-gated items below. Cut list if needed (decided by the owner): speed curves (09b), blend modes (03a), transition count (08).

## Owner-only items (recommendations)

1. **Approve the fixture approach (A above).** Needed before P4-05b.
2. **Real content for P4-15b:** the 40 video templates, 60 photo templates, 20 sticker packs, with license fields. Recommendation: start with 10 + 15 + 5 (enough for a closed test), grow monthly from the CMS; this needs a decision on scope at the end-of-build list, not now.
3. **A real green screen clip** and a few real overlay clips for the phone check of P4-04. Recommendation: owner supplies one at end-of-build QA.
4. **Which famous photo formats** to illustrate originally, and the lawyer read. Recommendation: before P4-15b.
5. **One branch, one PR or stacked PRs** when it all lands (the owner said "ship all"). Recommendation: keep building on this branch; at the end, QA, then open stacked PRs cut at each phase's last commit (P3 and P4 first), each with the security review on its exact commit; or one PR if the owner prefers. Non-blocking.
6. **A short smoke pass before the 25 Jan 2027 closed test** (R8 build, cold start, one export, one sound). Recommendation: yes; testers should not get a build no device has run.

## Impact

5,000 DAU goal: pro tools are the "as capable as the common editors" half of the bet; the speed of building them does not move the launch. Quality risk: QA debt (32 + 15 + Phase 4) and schema churn; a device bug in the engine can rework many tickets. Merge `main` into this branch whenever the owner's Phase 1 and 2 fixes land.
