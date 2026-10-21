# Watermark · P1-13

> **Final, 21 Oct 2026.** Owner decision (8 Oct): a small "Memix" text mark, no logo, top-left, 3% margin, on every free export (PRD → Export and sharing). P5-02 adds the rewarded ad that removes it from one export.

Builds on `P1-12-export.md` (the sheet, the Exported screen) and `P1-10-text.md` (the one caption renderer that draws text over video in preview and export). Board: `design/screens/ExportDone.dc.html` shows the mark on the result. The `Export.dc.html` watermark row shows the P5-02 version, with Remove.

## Job
Every free export carries a quiet "Memix" credit. It spreads the name when memes are shared and never gets in the way of the meme (Content first). The user knows it's there before exporting, so it's no surprise.

**Done when** (TICKETS): the watermark shows by default and is absent when the export is marked watermark-free.

**The one blue action:** none of its own. The mark sits in the file. The sheet row is information until P5-02 adds Remove.

## Entry and exit
No screen of its own. The mark is drawn into the exported file. The row sits in the Export sheet (P1-12 → Layout item 4).

## The mark (in the exported file)
These are content rules, not chrome. They live in `design/tokens.json` → `watermark` next to `caption` and `canvas`, never in `MemixTheme`. They aren't saved in drafts, so tuning them needs no schema bump and changes only new exports.

| Property | Rule |
| --- | --- |
| Text | "Memix", exactly as written, in every language (never translated, never uppercased) |
| Font | Anton (`anton_regular.ttf`, already bundled): the wordmark face (DESIGN_SYSTEM → `wordmark`) |
| Size | Font size **4% of the output's shorter side**: 43 px at 1080p, 19 px at 480p, 86 px at 4K. About half a default caption (8%), so it reads as a credit, not content. |
| Position | **Top-left.** Its ink box (the glyphs' drawn bounds, not the font's line box) starts **3% of the shorter side** from the left edge and from the top edge: 32 px at 1080p on both edges. One margin on both edges looks even on every shape. Measured from the shorter side, the mark sits in the same place relative to captions at every ratio. |
| Fill | White `#FFFFFF` at 80% opacity. |
| Legibility | A soft shadow with **no offset**: black at 50% opacity, blur radius 15% of the font size. It keeps the mark readable on white or bright footage without a hard edge or outline (no v1 outlines or offset shadows). |
| Layer | Above everything in the frame (clips, canvas background, captions, later stickers and overlays), so nothing covers it. |
| Time | The whole video, from the first frame to the last. No animation. |
| Resolution | Sized and placed from the output's shorter side, so it looks the same at 480p, 1080p and 4K, and in every canvas shape. |
| Preview | **Not drawn in the editor preview.** The editor is the user's workspace (Content first). The sheet row says where the mark goes, and the Exported screen plays the real file. |

- **Why 4%:** the board's mark is about 6% of the frame's width. "Small" (owner) and the caption's 8% put a credit at half a caption. On a phone showing a 9:16 video full screen, 4% is about 15 dp: readable, never louder than a caption (Assumption; the owner sees it at phase-end QA, and a change is one constant).
- **Why white with a soft shadow:** a plain white mark disappears on bright footage, and a black outline is the rejected v1 look. Brand names have no WCAG contrast requirement (SC 1.4.3 exception for logotypes), but the mark should still be readable, so the shadow is there to help it read, not to decorate.
- **Same renderer as captions:** drawn by P1-10's one text renderer as the top-most overlay of the export only. iOS (P7) uses the same constants.
- **Photo exports (P2):** the photo editor's export ticket reuses these rules. Sticker exports are decided there.

## Export sheet row
Between Frame rate and the facts line (P1-12 → Layout item 4), as on the Export board, without Remove until P5-02:
- A container in `surface-raised` (one value step above the sheet's `surface`), `radius-md`, `space-3` padding, full width. It isn't a control: it has no press state and no ripple.
- **Title:** "Memix watermark" in `body-strong` `text`.
- **Line under it:** "Small text in the top-left corner of your video" in `label` `text-secondary`.
- Both wrap; nothing is cut.

**P5-02 (for its spec, not built here):** the line becomes "Watch a short ad to remove it from this export", and a Quiet button "Remove" with the `play` icon in `primary` sits at the row's end, as on the board. After a completed ad, the row reads "No watermark on this export" with the `check` icon, for this export only.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Free export (every export in P1) | The row in the sheet; the mark in the file and on the Exported result | `export_watermark_title`, `export_watermark_body` |
| Marked watermark-free (P1: the debug export hook only; P5-02: after a completed ad) | No mark in the file. In P1 the sheet is never in this state. | none in P1 |
| Long content (5 languages, 200%) | Title and line wrap; the row grows | |

**"Marked watermark-free"** is a per-export flag (`ExportSettings.watermark`, default on). It's never remembered between exports, and it's never saved in the draft (P1-12 → Kept).

## Interactions
None. The row isn't tappable in P1. A tap does nothing and gives no feedback, because nothing in the row looks like a control.

## Motion
None.

## Copy
| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `export_watermark_title` | Memix watermark | 24 | "Memix" never translates. "Watermark" is the word users know from other editors (CapCut's word in your language is a good guide). |
| `export_watermark_body` | Small text in the top-left corner of your video | 60 | Describes where the mark goes. In right-to-left languages it would still be top-left (none of our five are RTL). |

The mark's own text "Memix" isn't a string resource: it's a brand constant next to the watermark rules.

## Accessibility
- The row is one text node, read between Frame rate and the facts line: "Memix watermark. Small text in the top-left corner of your video." It's not focusable as a control.
- Contrast: title `text` on `surface-raised` 12.4:1; line `text-secondary` on `surface-raised` 5.7:1.
- 200% font: both lines wrap; the sheet's middle scrolls (P1-12).
- The mark in the video is content. A screen-reader user learns about it from the row.

## Analytics
None in P1-13. `watermark_removed` arrives with P5-02.

## QA compares
1. **The sheet** at 360 × 640: the row between Frame rate and the facts line (P1-12 compare 1).
2. **Exported result:** the mark in the top-left of the playing file.
3. **The mark in the file**, pulled with ffmpeg from a 9:16 1080p export: on a white frame and on a dark frame, readable on both.
4. **Other shapes and sizes:** 16:9 1080p, 1:1 480p and 9:16 4K (where the phone can): the same look relative to the shorter side.
5. **Watermark-free:** an export made through the debug hook marked watermark-free has no mark.
6. **Hindi at 200% font:** the row wrapped, nothing cut.

**Measured:** on the 1080p frames, the mark's ink box starts 3% of the shorter side from the left and top edges (32 px ± 2 px). The font size is 4% (43 px). Check this against Anton drawn at that size, because ink height depends on the glyphs. The mark is present on the first and last frame. The reference frames go in `docs/qa/references/P1-13-watermark/` (9:16 1080p, white and dark frame) for iOS parity in P7.

## Requests to the principal mobile engineer
1. **Watermark constants** (text, font, 4% size, 3% margin to the ink box, 80% white, shadow black 50% with blur 15% of the font size and no offset) in one commonMain place next to the caption and canvas constants. Add a comment pointing to this spec and to `design/tokens.json` → `watermark`.
2. **Drawing:** the top-most overlay in the export only, through P1-10's text renderer. Measure the ink bounds (Android `Paint.getTextBounds`) to place it, and use `setShadowLayer(radius, 0, 0, color)` or the renderer's equivalent. Don't draw it in `CompositionPlayer` previews. The iOS stub follows rule 7.
3. **Flag:** `ExportSettings.watermark: Boolean = true`. TECHNICAL_DESIGN already reserves it ("P1-13 the watermark"). It isn't persisted with the remembered settings.
4. **Debug:** an extra on the export hook (for example `--ez memix.exportWatermarkFree true`) that marks one export watermark-free, for the Done when.
5. **Sheet row:** as above, with the two strings in five languages. Tokens only.

## Question for the owner (through the PM; it doesn't block the build)
**Problem:** TikTok's own interface covers the top of a 9:16 video in the For You feed (status bar, the Following / For You tabs, the LIVE and search icons). Third-party safe-zone tools that measure TikTok's ad template give 130 to 260 px at the top of a 1080 × 1920 frame, most often 240 px. With a 3% margin, the mark covers roughly 32 to 65 px from the top, so on TikTok's feed it may be partly under the top-left LIVE icon. It shows fully in WhatsApp, the gallery, Reels' full-screen player and everywhere else.

**Considerations and options:**
- **A. Keep top-left with a 3% margin (as decided).** No cost. The mark is in the file everywhere, but it's least visible on TikTok, the biggest place memes go, and partly hidden there it can look like a mistake.
- **B. Top-left, 3% from the left and about 13% from the top** (250 px at 1080 × 1920, below TikTok's top band). One constant, no extra time. It shows on TikTok's feed, but it sits closer to where captions start (the first caption slot is at 25% of the height), so it can come close to a top caption.
- **C. Decide at phase-end QA:** export one test meme, post it privately to TikTok and Reels, and look. About 10 minutes of the owner's time, and the decision rests on real screens instead of third-party tools.

**Recommendation: C.** Build to A now (it's the owner's decision). If the test shows the mark hidden, switching to B is one constant and no draft changes.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Watermark: small "Memix" text, top-left, 3% margin; top-left avoids TikTok's and Reels' right and bottom buttons | Owner decision (8 Oct 2026) | `docs/pm/decision-log.md`; PRD → Export and sharing |
| Text that is part of a logo or brand name has no contrast requirement | Guidance | [WCAG 2.2 SC 1.4.3, exceptions](https://www.w3.org/TR/WCAG22/#contrast-minimum) |
| TikTok's top interface covers about 130 to 260 px of a 1080 × 1920 video (240 px is the most cited, from TikTok's ad template) | Guidance (third-party tools citing TikTok's template; not checked against TikTok's file here) | [Socialync TikTok safe zones](https://www.socialync.io/tools/safe-zones/tiktok), [Upload-Post safe zone checker](https://www.upload-post.com/tools/safe-zone-checker/), [Revid TikTok safe zone](https://www.revid.ai/tiktok-safe-zone), [Postiz](https://postiz.com/tools/tiktok-photo-resizer) |
| A credit at about half a caption's size reads as a credit, not content | Assumption (checked by the owner at QA) | — |
