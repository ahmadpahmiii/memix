# Add a photo as a layer in the photo editor

**Date:** 27 Oct 2026 · **Author:** PM (Phase 2 kickoff, mapping the PRD's photo editor rows to tickets) · **Status:** **Approved: B** (owner, 27 Oct 2026, relayed by the main session). P2-13 added to TICKETS after P2-12; `decision-log.md`, 27 Oct

**Problem:** the PRD's photo editor lists layers as "Text, stickers, images, cut-outs, drawings; reorder, lock, hide, opacity". Every kind has a ticket except **images**: P2-03 builds text and stickers, P2-05 drawings, P2-07 cut-outs, P2-02 the format cells. Nothing lets a user add a second photo from the gallery on top of the first, which is how most "reaction face on a meme" and "this vs that" memes are made. The model already has `ImageLayer`, and the photo you start from is one. Without a ticket, this promise quietly drops out of v1, as the background image almost did (decision log, 21 Oct).

**What it is, in plain words:** a tool in the photo editor that opens the gallery picker (the same picker and file copy as P1-02, never uploaded), and puts the picked photo on the canvas as a new top layer, fitted inside the frame, as one undo step. After that it moves, scales, rotates, reorders, hides, locks and fades like any layer (P2-01).

**Considerations and suggested fixes:**
- **A. Put it inside P2-01.**
  - Time: about half a day to a day more in P2-01, which is already L (the editor shell, the layer model, gestures, the layers panel, the canvas panel, the start flow, drafts routing, one draft format change).
  - Money: none.
  - Risk: P2-01 is at the top of its size already. I would need to watch for a second build session and may have to ask you to split it.
  - User impact: none yet; nobody outside the team has a build before the closed test (about 25 Jan 2027).
- **B. One new small ticket, P2-13 "Add image layers in the photo editor" (recommended).** Size S, right after P2-12, Needs P2-01 and P2-12. P2-12 builds the "pick one photo for an existing project" path for the background image, so this reuses it with a different destination.
  - Time: about 1 day in Phase 2. Phase 2 starts 20 days before its plan, which covers it.
  - Money: none.
  - Risk: low. P2-01 is checked with a debug-built 10-layer scene, so it doesn't wait for this ticket.
  - User impact: none yet; it lands in November, before any outside tester.
- **C. Leave it out of v1.** Photos would join a scene only through formats (Panels cells), cut-outs and stickers. The PRD's Layers row loses "images".
  - Time: saves about 1 day.
  - Money: none.
  - Risk: none to build. A visible gap against InShot and CapCut, which both let you add a photo over a photo.
  - User impact: a user can't put a second picture on a meme except inside Panels.

**Recommendation: B.** It keeps P2-01 at a size one builder can finish, it gives the promise an owner, and the cost is about a day inside a phase that starts 20 days early.

**Impact:** no launch date moves (Android ≈ 7 Mar 2027, iOS ≈ 2 May 2027); Phase 2 stays planned for 16 Nov–6 Dec. The 5,000 DAU goal is touched only slightly: layering photos is a common way to make a meme, so it helps the first export, not the install. Quality is unchanged.

**If approved (B):** the PM adds the P2-13 row after P2-12 in `docs/TICKETS.md` (the PRD stays as it is, because the promise is kept), adds the row to the board, and logs it in `decision-log.md`. The designer specs it with the P2-12 pick flow. **If A:** the PM adds a line to P2-01's ticket text and the designer includes the tool in the P2-01 spec. **If C:** the PM removes "images" from the PRD's photo Layers row.

**Needed by:** before P2-12 starts, because P2-12 and P2-13 share the picker path (about 2 to 4 days from now, after P2-01).
