# P2-06: no MediaPipe fallback for photo cut-outs yet

**Date:** 5 Nov 2026 · **Author:** PM (P2-06 ready check) · **Status:** **Approved by delegation (owner, 2 Nov): recommendation applied, owner reviews after Phase 2** (A)

**Problem:** the technical design and the background-removal skill say that if ML Kit's subject segmentation is not available on a phone, photo cut-outs fall back to the MediaPipe person model "and say so". The P2-06 ticket text does not list a fallback. A fallback needs a second library and a model file, and covers only people.

**Considerations and options:**
- **A. Leave the fallback out of P2-06 (recommended).** If ML Kit's model is unavailable, show a calm "can't remove the background right now" state with a retry. P4-13 brings MediaPipe for video anyway, and the fallback is reconsidered there. Cost: none now; a phone without the model (no Play services, or the download failed) can't cut out until it succeeds.
- **B. Build the fallback in P2-06.** About 1 to 2 more days, a bigger download or an on-demand model, more R8 and licensing review; P2-06 would grow beyond M.
- **C. Drop the fallback from the design for good.** Simplest, but removes a safety net the owner may want once real phones are tested.

**Recommendation: A.** The app ships through Google Play, so Play services is the normal case. Reverse: add the fallback to a later ticket; no saved-draft change.

**Impact:** no launch date moves. Quality risk is limited to phones where the model cannot install; QA reports whether any test phone hits it.
