# Proposal: render the Blur background beneath masked and keyed main clips

**Date:** 17 Nov 2026 · **Author:** PM (from the designer's P4-03b review, flag 1) · **Status:** **Proposed** (not scheduled; nothing is built or blocked on it)

## Problem
With a Blur background, a main clip that is masked (P4-03b) or keyed (P4-04) shows black around the cut-out instead of the blurred copy. A circle window over a blurred copy of the same clip is a likely meme use. Today the behavior is consistent between mask and key, and accepted for now (decision log, 17 Nov).

## Options
- **A. Later list, no date (recommended):** add as a Later item. Cost nothing now; the behavior is documented and the Canvas panel already offers a color or an image behind the clip.
- **B. A new Phase 4 ticket (S to M):** the engine draws the Blur copy of the clip (with crop and turn, no key or mask) beneath the keyed or masked clip, in preview and export, with a reference render. Needs a device check of the Blur pass cost with a mask.
- **C. Never:** keep black and say so in the Canvas panel note.

## Recommendation
A. Revisit if the QA pass or the closed test shows people asking for it; B is the fix if so.

## Impact
Goal (5,000 DAU by month 3): none direct. Dates: A none; B about 1 to 2 days of the Phase 4 build. Quality: A leaves a visible black edge that users can fix by choosing a color background.

Owner answer (short form): "A", "B" or "C".
