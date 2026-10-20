# A picture behind the clips: canvas background image

**Date:** 20 Oct 2026 · **Author:** PM (from the designer's P1-11 spec, 90c8b07 → Proposals and questions for the PM, 1) · **Status:** Proposed

**Problem:** the PRD promises a background image in both editors: the video canvas has "background color, blur **or image**" and the photo canvas "background color **or image**". No ticket builds it. P1-11 builds color and blur only, as its ticket says, and P2-01 says only "canvas". Without a ticket, the promise quietly drops out of v1.

"Image" here means a photo the user picks from their own gallery, shown behind clips that don't fill the frame. It stays on the phone like every other picked file (no upload) and needs no licensed content.

**Considerations and suggested fixes:**
- **A. Add it to P1-11 now.**
  - Time: about 1 more day in Phase 1. P1-11 goes from a middle M to the top of M, and it already has the hardest unknown of the canvas work (the blur in Media3).
  - Money: none.
  - Risk: one saved-draft format change (a new background type plus a migration) in P1, and the photo editor needs the same feature again in P2.
  - User impact: none yet. Nobody outside the team gets a build before the closed test (about 25 Jan 2027), so building it early gains users nothing.
- **B. One new ticket in Phase 2 for both editors (recommended).** "P2-12 Canvas background image (video and photo editors)", size S, Needs P1-11 and P2-01, placed right after P2-01.
  - Time: about 1 day in Phase 2. Phase 1 is about 12 days ahead of plan, which covers it.
  - Money: none.
  - Risk: low. It's built once, with one draft format change, using the gallery picker and file copy that already exist (P1-02).
  - User impact: it lands in December, well before the closed test and the launch, so no real user waits.
- **C. Drop it from v1.**
  - Time: saves about 1 day.
  - Money: none.
  - Risk: none to build. The PRD's canvas rows lose "or image".
  - User impact: a small gap. InShot offers a picture behind the clip; Memix users would have color or blur only.

**Recommendation: B.** It's the same feature in both editors, so it's built once, and Phase 1 has the hardest work still to come (text, canvas, export).

**Impact:** no launch date moves (Android ≈ 7 Mar 2027, iOS ≈ 2 May 2027). The 5,000 DAU goal is touched only slightly: this is a look option, not a reason to install. Quality is unchanged.

**If approved (B):** the PM adds the P2-12 row after P2-01 in `docs/TICKETS.md` (the PRD stays as it is, because the promise is kept) and logs it in `decision-log.md`. The designer specs it before Phase 2, next to the P2-01 spec. P2-01's "canvas" then means ratio, size and color in the photo editor, and the image comes with P2-12.
