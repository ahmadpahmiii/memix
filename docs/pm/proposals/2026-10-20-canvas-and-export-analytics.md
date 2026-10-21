# Analytics: which canvas choices people make, and export cancels

**Date:** 20 Oct 2026 · **Author:** PM (from the designer's specs, 90c8b07: P1-11 → Proposals 3, P1-12 → Proposals 4) · **Status:** **Approved: Q1 A, Q2 A** (Owner's words, 21 Oct 2026: "1 - 6 do with your recomendation, i have approve it"). PRD analytics table updated (`tool_use` `value`, `export_cancel`); `decision-log.md`, 21 Oct

Both change the PRD's analytics table (`docs/PRD.md` → Metrics and analytics), so they're yours. Analytics stays off until the user consents (P5-01), and neither carries personal data.

## Question 1: which canvas choices people make

**Problem:** P1-11 fires `tool_use` with `canvas_ratio`, `canvas_fit` or `canvas_background` when someone changes the canvas. That tells us someone changed it, not to what. We can't learn which shapes people make (9:16 for TikTok, 1:1 for a feed post, 16:9 for YouTube). That should decide the default shape and the shapes of the templates in P4-05.

**Considerations and suggested fixes:**
- **A. Add an optional `value` parameter (recommended),** for the three canvas tools only:
  - `canvas_ratio`: `9:16`, `1:1`, `4:5`, `3:4`, `16:9` or `custom` (never the custom numbers)
  - `canvas_fit`: `fit` or `fill`
  - `canvas_background`: `blur` or `color` (never the color itself)
  - Time: under an hour inside P1-11, plus one line in the PRD.
  - Money: none. At P5 you register `value` as one custom dimension in Firebase (a few minutes; the free plan allows 50).
  - Risk: none. Using `value` for any other tool later comes back to you as a proposal.
  - User impact: none directly; better defaults and templates later.
- **B. Leave the event as it is.**
  - Costs nothing now. The default shape and template shapes stay guesses.

**Recommendation: A.** It's the same kind of change you approved for fonts on 9 Oct, at almost no cost.

## Question 2: counting export cancels

**Problem:** the PRD has `export_start`, `export_complete` and `export_fail`, but no event for a cancel. When someone starts an export and stops it, it vanishes from the numbers. Exports per daily user (target 1.5 or more) is one of our goals. If exports are too slow and people give up, we need to see it.

**Considerations and suggested fixes:**
- **A. A new event `export_cancel` (recommended),** with `editor` and `progress` (the percent done when cancelled, 0 to 100).
  - Time: under an hour inside P1-12, plus one PRD line.
  - Money: none.
  - Risk: none.
  - User impact: none directly. It shows how far people get before giving up, which tells us whether export speed is a problem.
- **B. Count a cancel as `export_fail` with `error_code: "cancelled"`.**
  - Time and money: the same. No PRD change, because the PRD lists no `error_code` values.
  - Risk: the failure rate looks worse than it is, and every report must filter cancels out to see real errors.
- **C. Don't count cancels.**
  - Costs nothing now. Cancels can only be guessed from starts minus completes minus fails, mixed with phones that killed the app.

**Recommendation: A.**

## Impact (both)

No date moves. P1-11 stays M and P1-12 stays L. Both help the supporting goal of 1.5 exports per daily user, and through it the 5,000 DAU goal, by showing where people drop out.

**If approved:** the PM adds the `value` parameter and/or the `export_cancel` row to the PRD analytics table and logs it in `decision-log.md`. The designer updates the P1-11 and P1-12 analytics tables. The ticket checks verify that the events fire with the right values.
