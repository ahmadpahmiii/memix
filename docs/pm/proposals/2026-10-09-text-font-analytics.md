# Which caption fonts get used: a `font` parameter on `tool_use`

**Date:** 9 Oct 2026 · **Author:** PM (from the designer's P1-10 spec, 3db2ab3 → Proposals and questions for the PM, 2) · **Status:** Proposed

**Problem:** P1-10 bundles four caption fonts beside Anton (Poppins, Baloo 2, Kalam, Rozha One), which adds about 1.27 MB to the app. The PRD's `tool_use` event has only `editor` and `tool`, so after launch nobody can tell which fonts people pick. Without that, we can't judge whether a font earns its size or which fonts P4-11's text presets should use.

**Considerations and suggested fixes:**
- **A. Add an optional `font` parameter (recommended).** It carries the font id (`anton`, `poppins`, `baloo2`, `kalam`, `rozha`) on `tool_use` when `tool` is `add_text` or `text_font`, in both editors (P2-03 reuses it).
  - Costs: under an hour inside P1-10, plus one line in the PRD analytics table. No personal data. At P5 the owner registers `font` as a custom dimension in Firebase so it shows in reports (a few minutes; the free plan allows 50).
  - Gain: real font usage from the first test build. Data to drop a font nobody uses (a smaller download helps installs) and to pick fonts for P4-11's presets.
- **B. Leave the event as it is.**
  - Costs: nothing now. Font choices later rest on guesses and store reviews.

**Impact:** no date moves; P1-10 stays L. Quality is unchanged. It serves the 5,000 DAU goal only indirectly, through download size and better presets.

**If approved:** the PM adds `font` to the PRD analytics table (`tool_use` parameters: editor, tool; font for text font choices) and logs it in `decision-log.md`; the designer updates the P1-10 spec's analytics table; the P1-10 ticket check verifies the event fires with the right id.
