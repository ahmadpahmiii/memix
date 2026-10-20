# Crop, rotate and flip a video clip, and Fit or Fill per clip

**Date:** 20 Oct 2026 · **Author:** PM (from the designer's P1-11 spec, 90c8b07 → Proposals and questions for the PM, 2) · **Status:** Proposed

**Problem:** the PRD's video editor promises "crop, rotate, flip" under Basic edits. No ticket builds them for video. P2-04 builds them for photos only, and P4-10 covers reverse and freeze frame only. Also, P1-11 sets Fit or Fill for every clip at once. Drafts already store Fit or Fill per clip, but nothing lets the user set it for one clip, for example a landscape clip filling the frame while a portrait photo next to it fits. Without a ticket, none of this is in v1.

Why people use it in memes: crop zooms in on a reaction face, rotate fixes a sideways clip, flip mirrors a shot, and per-clip Fit or Fill handles a mix of landscape and portrait clips.

**Considerations and suggested fixes:**
- **A. A new Phase 4 ticket (recommended).** "P4-16 Clip crop, rotate, flip and per-clip Fit or Fill", size M (2 to 3 days), Needs P4-01 (it builds the on-frame transform gestures) and P2-04 (its crop screen can be reused). Placed right after P4-10.
  - Time: 2 to 3 days in Phase 4, which is already the fullest phase (6 weeks, with the holidays).
  - Money: none.
  - Risk: Phase 4 could run late. If it does, this ticket joins the cut list talk (speed curves, blend modes, transition count), and you decide then. Each new look needs a reference render for the iOS version.
  - User impact: users get the basic edits every mobile editor has.
- **B. Add it to P4-10 (reverse and freeze frame), making one L ticket.**
  - Time: the same 2 to 3 days.
  - Money: none.
  - Risk: one big ticket that mixes unrelated tools is harder to check and harder to cut in part. P4-10 would then also wait for P4-01.
  - User impact: the same as A.
- **C. Drop it from v1** and move it to Later · Improvements (L-03).
  - Time: saves 2 to 3 days of Phase 4.
  - Money: none.
  - Risk: the PRD's Basic edits row loses "crop, rotate, flip". A sideways clip can't be fixed in Memix.
  - User impact: a visible gap against CapCut and InShot. The PRD's bet is "an editor as capable as the common mobile editors".

**Recommendation: A.** It keeps a PRD promise at a known small cost, in the phase that already builds the gestures it needs. It doesn't block P1-11.

**Impact:** no launch date moves if Phase 4 holds. If Phase 4 slips, this is one more candidate to cut, decided by you then. The 5,000 DAU goal: basic edits are expected, so missing ones show up in store reviews more than adding them shows up in installs. Quality is unchanged.

**If approved (A):** the PM adds the P4-16 row after P4-10 in `docs/TICKETS.md` (the PRD stays as it is) and logs it in `decision-log.md`. The designer specs it before Phase 4.
