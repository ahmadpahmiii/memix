# P4-07 design review: video filters and adjustments

Verdict: **Approved with polish** (PASS; no must-fix). Reviewed against `P4-07-video-filters-addendum.md` and `P2-04-image-tools.md`: the renders in this folder, the references in `docs/qa/references/P4-07-video-filters/`, and the strings in `core/ui` and `feature/video-editor` (en, id, es, pt, hi). Desktop renders only; nothing here was seen on a device or GPU.

## What matches the spec
- Panel: the title says the scope ("Filters, all clips" / "Filters" from a clip bar); two tabs, Filters and Adjust, with no Deep-fry; Reset is Quiet (blue text) and Done is Ghost, so Export stays the one blue action.
- White ring on the selected tile; the mixed state shows no selected tile and no Intensity row, with the hint under the tabs.
- Tokens only: no outlines, shadows, gradients or v1 colors.
- Pinned rule: tabs scroll with the content at 360x640 and are pinned at 360x800 and 412x915, as the budget says.
- Strings: every new key exists in all 5 languages and matches the spec drafts. The photo-editor strings moved to `core/ui` with their ids and text unchanged.
- References: the six video-specific sets, the parity contact sheets and the harness numbers (mean 0.22/255, 100% within 6) are present. They are CPU-twin renders, not GPU frames, and the README says so.

## Builder flags
1. **Filters off-screen at 360 dp: accepted.** The tool's position in the bar is not what makes it a swipe. The final order puts it 9th (after Stickers and Effects), so it is off-screen at 360 dp whatever we do. `bar-main.png` shows the next tool clipped at the right edge, which is the scroll cue. Don't reorder. Filters is a look (a "finish" step), and Sound first is protected by Meme sounds being 2nd. The fast-path tap counts need a note (amendment A1).
2. **Hi and es at 200% on 360x640: not must-fix.** The spec already accepts the 360x640 floor with scrolling (budget section). In `filters-vivid-hi-200` and `filters-vivid-es-200` the title wraps and Reset and Done drop under it. The tiles show only their top ~40 px, with the ring cut. The panel scrolls, so nothing is unreachable. Polish P1 below, and QA checks it on a device.
3. **Provisional bar positions: accepted.** The final order is the spec's; on each later tool ticket, whoever lands it re-slots Filters (Stickers, Effects, P4-16).
4. **One-off hitch on the first drag or on sharpen from 0: accepted for now.** The cause is a composition rebuild. Deferred to QA (D2). If it is over about 300 ms on the owner's phone, the fix is to build the look effect at identity when a clip is added, so no rebuild is needed later. This is an engineering call.
5. **30 fps lower-resolution fallback not built: deferred.** The spec says "if the phone can't hold 30 fps". D1 measures it first. If the owner's phone holds 30 fps, the spec line stays as a contingency. If not, it becomes a ticket.
6. **Shared names and labels: approved.** One source of truth is better. Check that no `edit_photo_*` key is left orphaned.

## Polish (not blocking; engineer may batch into a later ticket)
- **P1.** At 200% font on a 640 dp-high window, show a clearer cue that the panel scrolls. A cheap option is to cap the wrapped title at 2 lines (it already wraps to 2). Don't shrink the title; Hindi and es titles are legible.
- **P2. Copy over budget (translators and engineer, no layout break since the hint wraps to 2 lines):**
  - `filters_mixed_hint` es is 65 characters against a budget of 60. Fix: "Los clips tienen aspectos distintos. El cambio afecta a todos." (62; still over) or better "Aspectos distintos por clip. Un cambio se aplica a todos." (57).
  - pt is 62. Fix: "Os clipes têm visuais diferentes. A mudança vale para todos." (60).
  - Hi, id and en are within budget.
- **P3.** The mixed-state render at 360x640 (`filters-mixed-360x640`) pushes the tile row fully under the fold because of the 32 dp hint. This is as the spec predicted; only QA needs to confirm the swipe reaches the names.
- **P4.** `filters-vivid-hi` and `-es-200` tiles are cropped by the render at the region's bottom edge, not by a bug. Re-render after the device pass for the final reviews.

## Spec amendments (applied to the addendum)
- **A1.** The fast-path tap counts are changed to "plus a swipe of the bar on phones under about 400 dp wide". The "Filters (1)" step is not guaranteed on one screen.
- **A2.** The "Preview at 30 fps" fallback is marked **contingent on D1**.

## Deferrals for the QA list
- **D1.** On the owner's phone: with a filter and Adjust on a 1080p clip plus one 1080p overlay, the preview holds 30 fps or more (Media3 logs). The shaders have never run on a GPU, so this is the first real check. Export frame at 1.5 s against the references: mean at most 2/255, 99% within 6/255.
- **D2.** The first slider drag on an unfiltered clip and sharpen from 0 do not stall more than about 300 ms or drop frames visibly.
- **D3.** At 360x640 and 200% in hi and es, the panel scrolls to reach the tile names and Intensity, and Done and Reset stay reachable.
- **D4.** TalkBack: the panel title is read as the scope; the clip description reads "Filter: Vivid" or "Adjusted" in the order Cropped, Rotated, Flipped, Moved or resized, Green screen, Filter or Adjusted, Mask, Blend, Keyframes. In the mixed state no tile is "selected" and the hint is read after the tabs.
- **D5.** Old draft opens with no look; undo is one step per tile, release and Reset, and from the main bar one step covers every clip.
- **D6.** Check the 5 translated strings in the app with Hindi Devanagari line height, in the mixed hint at 200%.
