# Text shadow and background box, both editors · P2-11

Adds the PRD's "shadow" and "background box" to captions (PRD → Text; owner, 9 Oct, option C): two new rows in the **Style panel**, built once in the shared text engine so the video editor and the photo editor get them together. It reuses, and does not repeat:
- `P1-10-text.md`: the Style panel (Font, Color, Outline), the caption rendering rules (size, wrap, outline, Devanagari, one renderer), the selection frame, Position, analytics ids.
- `P2-03-photo-text-and-stickers.md`: the Style panel in the photo editor, text layers, drawing a caption as one group at the layer's opacity, the ratio-change rule.
- `P2-02-meme-formats.md`: bubbles, whose words are a caption (below).

## Job
Make a caption readable and loud over any picture: a soft shadow lifts white text off a busy photo, and a box behind the words is TikTok's most familiar caption look. **Done when** (TICKETS): a caption with shadow and box looks the same in preview and export in both editors. **Method** (as P2-03's, PM 2 Nov): the same caption on the photo editor's screen, the render hook and the video export frame, side by side, size and position within 1% of the canvas width (below), plus a reference render.

**The one blue action:** none. Blue appears nowhere new. White is selection (the selected swatch ring, the selected segment).

**Fast path, with tap counts:** a selected caption: Style (1), scroll to Shadow or Box, a color (2): it is on. Soft by default; **Softness** (3) or **Corners** (3) only if the user wants another step. Switching it off: the "No shadow" or "No box" swatch (1).

## Entry and exit
No new surface. The rows live in the existing **Style panel** (ToolPanel) of both editors, reached as P1-10 and P2-03 say (Style on the selected-text bar). Done, back and Escape close it; the text stays selected.

## Layout
The Style panel (P1-10 → Style panel) is lifted into `:core:ui` since P2-03, so **one change serves both editors**. Rows, top to bottom, each a `label` `text-secondary` heading above its controls, `space-4` apart:

1. **Font** (five FontTiles) · 2. **Color** (ten Swatches) · 3. **Outline** ("No outline" and ten Swatches): unchanged.
4. **Shadow** (new): a Swatch row with **"No shadow" first**, then the ten caption-palette colors: the same pattern as Outline. A tap on a color turns the shadow on in that color; "No shadow" turns it off. Selected: the white ring; "No shadow" gets the `stroke-hairline` `text-muted` ring and its slashed circle like "No outline" (5.7:1).
   - **Softness** (new, only while a shadow is on): heading "Softness", a **SegmentedChoice** (`design/system/components/SegmentedChoice/README.md`) of three: **Sharp · Soft · Blurry**. A new shadow starts at **Soft**.
5. **Box** (new): a Swatch row with **"No box" first**, then the ten colors. A tap on a color turns the box on in that color.
   - **Corners** (new, only while a box is on): heading "Corners", a SegmentedChoice of three: **Square · Rounded · Pill**. A new box starts at **Rounded**.

- **Why swatches with a "none" first, not switches:** it is one tap to turn on in the color wanted (no separate Switch, then color), the same grammar as Outline, and no new component. **Why steps, not sliders:** a slider is a drag, and three named steps cover what a caption needs (P1-10's rule: no sliders in this panel).
- **Every tap is applied at once and is one undo step** ("Shadow", "Box"); tapping the option that is already selected does nothing and records nothing. The preview stays visible above the panel (ToolPanel rule), so each choice is seen on the real frame.
- **The softness and corners rows** appear and go with a 120 ms fade; the rows below move at once (as P2-12's image row).
- **Panel height:** the panel gains about 180 dp (Shadow) and about 180 dp (Box) with their sub-rows on, so with the P2-03 content of about 340 dp it reaches roughly 700 dp with both on. It **scrolls inside at every phone height** (the photo editor's 40% cap, the video editor's timeline region): a Style panel that scrolls is already the case since P2-03. The new rows come after Outline, so the first screen of the panel is unchanged. (Assumption: a user who wants shadow or box scrolls; QA checks it is found at 360 × 640. Fix if not: a "Shadow and box" tab or sub-panel, a new ticket, not built here.)
- **For a bubble's words** (P2-02): the **Box row is hidden** (the bubble body is already the box) and the **Shadow row stays**: the same shadow draws under the words inside the bubble. A bubble's Style panel is Shape, Font, Color, Fill, **Shadow** (and Softness).

## How a shadow and a box draw (the rules saved with every draft)
Fixed rules in one commonMain place (`CaptionCatalog`, `design/tokens.json` → `caption.shadow`, `caption.box`). Sizes are in **em, the caption's font size** (8% of the canvas's shorter side at scale 1, times the layer's scale), so they scale with the text, with the canvas and with the export size, and **a shadow or box never re-flows the text**. Changing one after the closed test (about 25 Jan 2027) needs a schema bump.

| Rule | Value |
| --- | --- |
| **Draw order** (bottom to top) | **Box → the shadow of the text (its outline and fill together) → the outline → the fill**. The whole caption is one group (also at the layer's opacity in the photo editor), then clipped at the canvas edge |
| **Shadow shape** | The silhouette of the text **with its outline** (so an outlined caption casts one shadow of its outer edge, not two), in the shadow color at **60% opacity**, offset down and to the right in the caption's own space: **0.05 em right, 0.07 em down**. The shadow **turns with the caption** (drawn in the caption's own space, so preview and export agree). Assumption: acceptable; a fixed-light-direction shadow is Later |
| **Softness steps** | **Sharp**: blur 0. **Soft**: Gaussian blur radius **0.06 em**. **Blurry**: **0.14 em** |
| **Box shape** | **One rectangle around the whole laid-out block**, not one per line. Padding **0.30 em** left and right, **0.18 em** above and below the block (Devanagari marks included: the block is the measured one). The box color is opaque (a palette color) |
| **Corner steps** | **Square**: 0. **Rounded**: **0.25 em**. **Pill**: **0.60 em**, never more than half of the box's shorter side |
| **Wrap and size** | Unchanged: wrap 90% of the shorter side applies to the **text**, so a box can be a little wider than the text (at most 0.6 em). The box never changes the font size, the wrap or the line breaks |
| **Emoji** | Drawn as today (without an outline). Whether the shadow is cast under a color emoji is the platform renderer's; preview and export use the same renderer, so they match on a phone. Checked at QA (the same build note as P1-10's emoji outline) |
| **Colors** | The ten caption-palette colors. Shadow and box colors are user content, never chrome |

**Why one box, not per-line boxes:** the lines are centered and ragged, so per-line boxes need merged, stepped outlines with rounded inner corners, which differ by script and font metrics (Devanagari). One block is robust, is what most "caption on a black bar" memes look like, and cannot disagree between the two renderers. Per-line hugging boxes are Later.

## The selection frame, the hit area and the layout box
- **The selection frame and the hit area include the box** (the block plus its padding) and **do not include the shadow**: the frame hugs what is solid, the shadow is decoration (D4).
- **The measurer** (`CaptionMeasurer`, the one the ratio-change rule and hit tests use) returns the box's size when a box is on: the ratio-change rule keeps the **box** inside the frame, with its 2% margin; the Position panel's Center centers the box.
- **The shadow may leave the canvas** and is clipped there like any caption part; it never counts for "fully inside".
- **The video overlay** is a bitmap: its bounds include a margin for the shadow's blur and offset (engine request below) so the shadow never clips at the block's edge.
- **Timeline and Layers rows** are unchanged (the first line of the text).

## States
| State | What shows | Copy |
| --- | --- | --- |
| Style panel, no shadow, no box (every existing caption) | Font, Color, Outline, Shadow (None selected), Box (None selected); no Softness or Corners rows | `style_shadow`, `shadow_none`, `style_box`, `box_none` |
| Shadow on | The chosen color ringed; Softness row (Soft selected); the shadow on the preview | `style_shadow_softness`, `shadow_soft`, … |
| Box on | The chosen color ringed; Corners row (Rounded selected); the box behind the words on the preview | `style_box_corners`, `box_rounded`, … |
| Shadow and box on | Both; the shadow falls on the box | none |
| A bubble's words | No Box row; Shadow row works | none |
| A locked text (photo) | Style explains (P2-03): the toast; nothing changes | `explain_layer_locked` |
| Hindi, emoji, rotated, wrapped, at the canvas edge | As the rules above: box and shadow follow the block; the box is clipped at the canvas edge like the text | none |
| Draft saved before this ticket | No shadow, no box, looks exactly as before | none |
| Long content (5 languages, 200% font) | Headings wrap; the SegmentedChoice wraps into two rows (its rule); the panel scrolls | none |
| Offline, permission | Don't apply | none |
| First run | Same as every run | none |

## Interactions
- **A color tap** turns the effect on in that color (shadow: Soft; box: Rounded). **"No shadow" / "No box"** turns it off; the sub-row goes; **re-enabling starts from the defaults again** (the previous softness or corners are not remembered: one more state to keep in the model for no gain).
- **A new text takes the last style used in the visit**, **including** its shadow and box (P1-10 and P2-03's rule): a user who made one boxed caption gets the next in the same look. **Duplicate and Split** copy the whole style. A format slot's default style (P2-02) has **no shadow and no box** (the Caption bar slot is black on white; the Demotivational slots are white on black), and in the formats where a slot's style overrides the last-style rule that stays.
- **Undo names:** "Undo: Shadow", "Undo: Box". Every tap, including softness and corners, is one step.
- **Gestures, Position, Opacity, Lock, Hide** are P1-10's and P2-01's, unchanged.
- **Haptics:** none. **Playback (video):** unchanged.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Shadow or box appears on the preview | At once (like every Style change) | Same |
| Softness or Corners row appears or goes | 120 ms fade, rows below move at once | Same |
| Panel opens or closes | 200 ms slide | 120 ms fade |

## Copy
English source; the engineer machine-drafts id, es, pt and hi (until P5). Budgets are characters. **Shared keys live in `:core:ui`** with the Style panel.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `style_shadow` | Shadow | 20 | Row heading: a soft dark copy of the letters behind them |
| `shadow_none` | No shadow | 20 | Spoken name of the first swatch (pairs with `outline_none`) |
| `style_shadow_softness` | Softness | 16 | Row heading: how blurred the shadow is |
| `shadow_sharp` | Sharp | 12 | Segment: a shadow with a hard edge |
| `shadow_soft` | Soft | 12 | Segment: a lightly blurred shadow |
| `shadow_blurry` | Blurry | 12 | Segment: a very blurred shadow |
| `style_box` | Box | 20 | Row heading: a colored rectangle behind the words |
| `box_none` | No box | 20 | Spoken name of the first swatch |
| `style_box_corners` | Corners | 16 | Row heading: how round the box's corners are |
| `box_square` | Square | 12 | Segment: sharp corners |
| `box_rounded` | Rounded | 14 | Segment: slightly round corners |
| `box_pill` | Pill | 12 | Segment: fully round ends, like a pill |
| `edit_text_shadow` | Shadow | 24 | Edit name after "Undo: " |
| `edit_text_box` | Box | 24 | Edit name after "Undo: " |

Reused: `color_*` (spoken swatch names), `outline_none`'s pattern, `explain_layer_locked`, `panel_done`.

Translator notes:
- **Shadow** here is the dark, soft copy of the letters, not a "shade". **Box** is a colored rectangle behind the words, the TikTok caption look, not a "container" or a "frame".
- **Sharp / Soft / Blurry** describe the shadow's edge; **Square / Rounded / Pill** the box's corners. Keep each set short: they sit on small segments (three in a row, so about 8 characters each at 100% font).
- **"No shadow", "No box"** are spoken names of swatches, like "No outline".

## Accessibility
- **TalkBack and focus:** the P1-10 and P2-03 order, with Shadow and Box after Outline. Each Swatch row is a single-choice group ("Shadow", "Box"): each swatch reads its color name or "No shadow" / "No box", "selected", and its place ("3 of 11"). The SegmentedChoice rows are groups named by their heading ("Softness", "Corners"): each segment reads its label, "selected" and its place ("2 of 3"). After a tap, the new state is announced politely.
- **Touch targets:** swatches are 32 dp drawn in 48 dp targets; segments are at least 48 dp tall.
- **Contrast:** swatch rings `selection` on `surface` 17.2:1; the "No shadow" and "No box" rings `text-muted` 5.7:1 (the P1-10 numbers); SegmentedChoice as its README (`text` on `canvas` 16.9:1, selected `on-primary` on white 18.9:1). **A box on the preview is user content**: nothing in the chrome depends on its color. A shadow never has a contrast requirement (decoration); the text keeps its own readability from color and outline.
- **200% font:** the segments wrap into two rows, the headings wrap, the panel scrolls. **Captions on the canvas don't follow the system font size** (P1-10), so neither does the box nor the shadow.
- **Single-pointer:** nothing here needs a drag.
- **Reduce motion:** see Motion.

## Analytics
`tool_use` with `editor: "video"` or `"photo"`, once per committed undo step (never when a tap changes nothing). **No parameter** (the PM confirmed 2 Nov; `font` stays on `add_text` and `text_font` only).

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A tap in the Shadow rows: a color, "No shadow", or a softness step | `tool: "text_shadow"` |
| `tool_use` | A tap in the Box rows: a color, "No box", or a corners step | `tool: "text_box"` |

Analytics stay off until consent (P5-01).

## QA compares
Use the debug drafts `photo-text-shadow-box` and the video project `text-shadow-check` (request 5), and a gallery start.
1. **The Style panel** at 360 × 800 and 360 × 640, scrolled to Shadow and Box: "No shadow" and "No box" selected; then a shadow on (Softness row, Soft selected), a box on (Corners row, Rounded selected). In both editors.
2. **A white caption with a black outline over a bright photo,** with each of: no effect, Soft shadow, Blurry shadow, Black box Rounded, White box with black text Pill, shadow plus box.
3. **A bubble's Style panel:** no Box row, Shadow works.
4. **A wrapped three-line caption with a box,** its selection frame around the box (not the shadow), then rotated 30° and scaled 250%.
5. **Hindi and emoji** ("जब BUILD PASS हो 😂") with box and with shadow, in all five fonts: the box covers the Devanagari marks above and below; the emoji's shadow as the renderer casts it.
6. **Hindi at 200% font,** 360 × 640: the Style panel scrolled to the new rows, the Softness segments wrapped into two rows.

**Measured: the Done when** (PM, 2 Nov; the designer adds the numbers):
- **Same caption, three places:** the photo editor's screen, the P2-01 render hook and the video export frame (1080p), for `text-shadow-check` / `photo-text-shadow-box` with six captions: all five fonts, Hindi, emoji, rotated 30°, a three-line wrap, one against the canvas edge, each with shadow only, box only, both. **Pass:** the same line breaks and glyphs; the box rectangle and corners, the shadow offset and blur, and the text within **1% of the canvas width** in position and size; the same colors sampled inside the box and inside the text; the shadow and box clipped at the same canvas edge; 720p and 1080p give the same layout.
- **Reference renders** in `docs/qa/references/P2-11-text-shadow-box/` (CLAUDE.md rule 5), on a white canvas, a black canvas and a photo, at **1080 and 2160** (the 2160 frame equals the 1080 frame scaled by 2: box, shadow offset and blur scale with the em).
- **Old drafts:** a video draft and a photo draft saved before this ticket open with no shadow and no box, unchanged; Phase 1's P1-10 steps are re-run (the shared renderer changed): typing, style, position, the preview-equals-export comparison.
- **The opacity group** (photo): a boxed, shadowed caption at 40% shows no overlap darkening between box, shadow and text.
- **Performance:** typing 100 characters with a box and a blurry shadow shows no visible lag; a drag of a boxed caption stays under 5% janky frames on the owner's phone (release-type build). The shadow's blur is computed once per commit or per typed character, not per frame.
- **Leaks:** LeakCanary, 5 cycles of open, style a caption with shadow and box, edit it, close.

## Model
Changes needed from the design (the engineer owns the exact code; the PM's must-knows carry the version bump):

| Need | Model |
| --- | --- |
| The style | `CaptionStyle` gains `shadow: CaptionShadow? = null` (`color: ArgbColor`, `softness`: 0 sharp, 1 soft, 2 blurry) and `box: CaptionBox? = null` (`color: ArgbColor`, `corners`: 0 square, 1 rounded, 2 pill). Null means off. Steps, not numbers: the numbers live in the constants |
| Both editors | `CaptionStyle` is shared by the video `TextItem` and the photo `TextLayer`, so **video drafts bump too**: one `CURRENT_SCHEMA_VERSION` bump (read the constant) with a version-only step (the new fields default to null), and the old-draft check for **a video draft and a photo draft**. Never rename a `@SerialName` |
| Constants | Offset 0.05 em right and 0.07 em down; opacity 60%; softness blur 0, 0.06, 0.14 em; box padding 0.30 em by 0.18 em; corners 0, 0.25, 0.60 em; in `CaptionCatalog` with a comment pointing to this spec and `design/tokens.json` → `caption` |

## Requests to the principal mobile engineer
I don't edit code, so these are requests; the PM's must-knows (2 Nov) carry the rest.
1. **One renderer** (`AndroidCaptionRenderer`) draws both editors' captions: box, then the shadow, the outline, the fill, laid out once in canvas pixels and scaled to every output. The scene's `SceneCaptions` interface (P2-03) gets the same style; the caption layer is one group at the layer's opacity, **including box and shadow**.
2. **Bitmap bounds** (the video overlay): add a margin of at least offset plus blur (0.07 em + 3 × the blur radius, then rounded up) around the block so the shadow's blur never clips. Check that the margin does not change the caption's position.
3. **The measurer** returns the box's size when a box is on (block plus 0.30 em and 0.18 em), so the ratio-change rule, hit tests, the selection frame and Center use it; the shadow is not part of it.
4. **The Style panel** in `:core:ui`: the two Swatch rows with a "none" first (reuse the Outline row's composable) and the two SegmentedChoice rows; the bubble variant hides the Box row. Hide the Softness and Corners rows when their effect is off.
5. **Debug scenes:** `photo-text-shadow-box` (a photo canvas with the six captions above) and the video project `text-shadow-check`, opened with `memix.openEditor`.
6. **Analytics:** two new `EditorTool` values, `text_shadow` and `text_box`, one per committed step.
7. **Re-run list** (the shared renderer changed): P1-10's typing, style, position and the preview-equals-export comparison; P2-03's text and the ratio-change step.
8. **Emoji and shadows:** if Android does not cast a shadow under color emoji, leave it (preview and export match); if the outline pass doubled the emoji, the same QA rule as P1-10 applies.

## Proposals and questions
**For the PM**
1. **Confirm the two ids** `text_shadow` and `text_box` (done, 2 Nov) and that the row-per-effect order (Shadow, then Box) is fine.
2. **Video drafts bump** because `CaptionStyle` is shared: one version-only step covers both editors; the old-draft check needs a video draft too.
3. **Bubble words:** Box hidden, Shadow kept (your D3). Say if you would rather hide both.
4. **P2-02's slot styles** stay without shadow and box. The Done-when comparison for P2-03 (no shadow, no box) is unaffected.
5. **Size watch:** M; the work is a renderer pass, two rows, a measurer change and a bump. If it runs past two sessions, the natural split is shadow first, box second, with one schema bump for both.

**For the owner** (delegated 2 Nov: the recommendation is applied; the PM logs it for your review after Phase 2)
- **1. One box around all the lines, or a box per line (hugging each line)?** *Problem:* TikTok-style captions often hug each line. *Considerations:* **A. One block** (applied, recommended): the same shape in both renderers on every script and font, simple, and the "caption on a black bar" look people expect from memes; the cost is empty corners on ragged centered lines. **B. A box per line:** the look of some caption tools, but merged stepped outlines with rounded inner corners that need care per font and script (Devanagari marks), about a day more and more risk of the two editors disagreeing. *Reverse:* switching to B later changes the look of saved drafts (the box rule is saved), so it needs a bump and a migration; do it before the closed test (about 25 Jan 2027) or never. **Applied: A.**
- **2. Does the shadow turn with the text?** *Problem:* a turned caption's shadow can either stay down-right on the canvas or turn with the caption. *Considerations:* **A. Turns with the caption** (applied, recommended): one bitmap per caption, the same in preview and export, no extra state; a caption turned 90° has its shadow to its own lower right. **B. Fixed light direction:** looks more natural when turned, but the shadow must be drawn separately from the caption's bitmap, in both engines, and kept equal. *Reverse:* also a look change to saved drafts. **Applied: A.**

**Later, no ticket:** per-line hugging boxes; a shadow offset and a box padding the user can set; a color picker beyond the ten palette colors; a stroke on the box; box opacity; a fixed shadow direction.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| A black box behind white captions is the most familiar short-video caption look | Assumption-level (third-party guides; the PRD and the 9 Oct owner decision already rest on it) | `docs/ux/specs/P1-10-text.md` → Decision for the owner |
| A stroked, colored shadow needs a bitmap margin or its blur clips at the edge | Reasoned from how the overlay bitmap is built (a draw-once bitmap with fixed bounds) | `docs/ux/specs/P1-10-text.md` → Requests; the PM's must-know 3 |
| Three named steps give enough control without a drag | Assumption (P1-10's "no sliders" reasoning; the P2 test decides) | none |
| Non-text indicators need 3:1 against adjacent colors; the swatch rings meet it | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| Dragging needs a no-drag alternative: nothing in this ticket drags | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
