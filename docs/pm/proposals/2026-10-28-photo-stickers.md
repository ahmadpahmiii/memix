# Stickers in the photo editor: where do the first ones come from?

**Date:** 28 Oct 2026 · **Author:** PM (P2-03 ready check) · **Status:** **Approved: C** (owner, 28 Oct 2026, relayed by the main session). P2-14 added to TICKETS after P2-07; `decision-log.md`, 28 Oct

**Problem:** P2-03's ticket says "Text and stickers in the photo editor". Text is ready to build, because the five fonts and the caption engine exist. Stickers are not, because **no sticker exists anywhere in the app yet**. The catalog (P3-01, P3-13) comes in Phase 3, the 20 sticker packs (P4-15) in Phase 4, and the video editor's stickers (P4-12) are not built either: the video engine skips sticker tracks today. The first stickers the app can have in Phase 2 are the ones a user makes: P2-07's "save as sticker" (a cut-out saved on the phone), and P2-09's WhatsApp packs are made from those. A Stickers button in P2-03 would open an empty list, which the design system calls a dead control. Also, every sticker that ships in the app needs a recorded license and the owner's approval, as the 10 starter sounds did.

What P2-03 can check by hand for stickers without any content: the sticker layer itself (drawn in the scene, selected, moved, scaled, turned, faded, locked, hidden, reordered, saved and reopened), with a debug scene of generated sticker pictures, as P2-01 did with generated photos. Text carries P2-03's Done when ("the same style looks identical in both editors"); stickers have no style to compare.

**Considerations and suggested fixes:**
- **A. Ship a small starter set of stickers inside P2-03 (about 12).**
  - Time: about 1 more day in P2-03 plus the content work: finding or drawing 12 pictures, saving each license proof, your approval of each (the P1-08 pattern). P2-03 goes from the middle of M to the top, and a hold-up on approvals would hold the ticket.
  - Money: none, if the stickers are CC0 or drawn by us.
  - Risk: licensing is the project's one rule that can't be fixed later (a takedown after launch costs more). More files in a public repo, each needing proof.
  - User impact: a working Stickers tool in the first internal build; the starter set may be replaced when the catalog packs arrive.
- **B. Build the sticker layer in P2-03, and put the picker with P2-07.** P2-07 already saves cut-outs as stickers, so it builds the "My stickers" list; the picker that lists them and places one on the canvas is added to P2-07, which grows from M toward L.
  - Time: none now; P2-07 grows by about a day.
  - Money: none.
  - Risk: P2-07 (background removal UX, refine brush) is already the phase's riskiest M. Adding the picker there makes its size watch likelier to trigger.
  - User impact: stickers appear when the first real ones (the user's own) exist.
- **C. Build the sticker layer in P2-03, and add one small ticket, P2-14 "Stickers in the photo editor: picker and My stickers", right after P2-07 (recommended).** Size S, Needs P2-03 and P2-07. P2-07 keeps its scope (it writes the cut-out to a sticker store); P2-14 adds the Stickers tool and a picker with a "My stickers" list that places one as a sticker layer. Catalog packs join the same picker when P4-15 loads them.
  - Time: about 1 day for P2-14 inside Phase 2, covered by the 20-day early start. P2-03 gets lighter by the picker it no longer carries.
  - Money: none.
  - Risk: low. Every control that appears has something behind it. The sticker layer is built and checked early (P2-03), so P2-07, P2-09 and P2-10 can rely on it.
  - User impact: none until the first internal build; then Stickers appear with the first thing to put in them.

**Recommendation: C.** It adds no licensing work, keeps P2-07 at its size, and gives the one missing piece an owner.

**Impact:** no launch date moves (Android ≈ 7 Mar 2027, iOS ≈ 2 May 2027). The 5,000 DAU goal is touched only slightly: stickers matter to the "cut-out sticker" job, which arrives with P2-07 and P2-09 either way. Quality is unchanged.

**If approved (C):** the PM edits the P2-03 row (the title keeps "stickers" for the sticker layer and adds that the picker is P2-14), adds the P2-14 row after P2-07 (Needs P2-03, P2-07), updates the board and logs it in `decision-log.md`. The designer specs P2-03's sticker layer now and P2-14's picker before P2-07 ends. **If A:** the PM adds the starter-set work to P2-03 and the backend engineer runs the content intake; you approve each sticker's license. **If B:** the PM adds the picker to P2-07's row.

**Needed by:** before the P2-03 spec is final and the build starts (after P2-13, about a week from now). The text half of P2-03 doesn't wait for it.
