# Video filters and adjustments · P4-07 (addendum to P2-04)

**Read `P2-04-image-tools.md` first. This file is only what differs for video.** Everything not mentioned here is P2-04's, unchanged: the 20 filter **ids, names, recipes and LUT data** (33³, trilinear, `photo.imageTools`), the six adjustments with their ranges, defaults and formulas, the strip's tiles and ring, the Intensity slider, the Adjust sliders and their snaps, steps of 10 for keyboard and TalkBack, one undo step per tile tap or slider release, Reset per tab, the copy keys and budgets (`filter_<id>`, `adjust_*`, `filters_intensity`, `filters_none`, `edit_photo_reset`, the tab labels), the five-language drafts, the contrast figures, and the "filter" analytics parameter. Also reused: P4-16's clip geometry (`MediaClip.edit: ImageEdit?`, which this ticket gives the color fields), P4-04's pipeline order and P4-01b's panel rules.

## Job
"Put a look on my meme video": a filter on the whole video in two taps, or on one clip, or on an overlay. **Done when** (TICKETS): same ids and looks as the photo filters. Made checkable in Measured. **One main action: none**; Export is the one blue action; Done is Ghost (ToolPanel).

**Fast path, with tap counts:**
- **A filter on the whole video:** Filters (1, on the main bar), a tile (2), Done (3). Every main clip gets it.
- **One clip only:** the clip (1), Filters (2, one swipe of the bar), a tile (3).
- **Brighten:** Filters (1), Adjust (2), drag Brightness (3).

## What differs

### 1. The tool and its scope
- **Where:** a **Filters** tool in the video editor's **main bar** (final order: Edit · Meme sounds · Text · Add media · Overlay · Voiceover · Stickers · Effects · **Filters** · Remove BG · Speed; not shown in an empty project) and in the **clip bars** (the consolidated order set in P4-04: main clips after Position, overlays after Mask). Icon `adjust` (P2-04's: three sliders), label "Filters" (`tool_filters`), the normal tool look. Name = visible label; click label "Change the look of your video" on the main bar, "Change the look of this clip" in a clip bar.
- **Scope follows where it was opened** (Question 1):
  - **Main bar, nothing selected: every main clip at once**, one undo step. The title reads "Filters, all clips".
  - **A clip's bar: that clip only** (a main clip or an overlay). The title reads "Filters".
- **A look that differs between clips** (the "mixed" state): no tile is selected, the Intensity row is hidden, the Adjust sliders show the value of the clip under the playhead, and one hint line sits under the tabs (`filters_mixed_hint`: "Clips have different looks. A change applies to all."). A tap or a slider release then writes that **one** setting to every main clip; the settings it doesn't touch stay per clip.
- **New clips** (Add media, Duplicate is a copy): a new main clip takes the filter and adjustments **every existing main clip shares**, and none when they are mixed (P1-11's rule for Fit, applied to the look).
- **The tool's target is never "the photo under the playhead":** no selection inference as in P2-04 (that rule was for a single photo layer); with several clips the scope above is explicit in the title.
- **Not offered:** captions, stickers, sounds. **Deep-fry** has no tab in video (it is one of the effects, P4-06).

### 2. The panel
- **Tabs: Filters · Adjust** (SegmentedTabs, two tabs); it opens on Filters. P2-04's third tab, Deep-fry, is absent.
- **Reset** (Quiet, header) resets the open tab for the **panel's scope** (all main clips from the main bar, or the one clip).
- **Playback keeps running** if it was running; the look is live on the playing video, and a slider drag is live (Volume precedent, P1-06). If the phone can't hold 30 fps with the look on, the preview drops to a lower resolution rather than frames (the Blur precedent, P1-11 build note); export is always full quality; no message.
- **Playhead rule** (P4-01b): from a clip bar, if the playhead is outside the clip, it jumps to the clip's start when the panel opens. From the main bar it stays.
- **Strip thumbnails** are the user's video: the **frame at the playhead** (the clip under it, with its crop and turn but not its color edits; the first frame of that clip if the playhead is outside it, as P4-16's crop frame), made on demand at the strip's size and cached for the panel's visit. They are **not refreshed as the playhead moves** while the panel is open: a strip that re-renders 21 tiles on every scrub would cost more than it tells (a refresh is a close and a reopen). A tile not yet made is a `surface-raised` square, static, as P2-04.
- **Budget** (the panel is the editor's **fixed region**, not P2-04's content-sized panel: 229 / 302 / 348 dp at 360 × 640 / 360 × 800 / 412 × 915; content = region − 96 = **133 / 206 / 252 dp**; rows measured from the component code and the P2-04 sizes: the tabs 48, a filter tile 72 plus `space-1` plus a one-line `caption` name 14 = **90 dp**, a Slider row 68 as the safe figure (a 16 dp label over the 48 dp rail), gaps 16):
  - **Pinned rule:** the title row (48) and the tabs (48) may take 40% of the panel at most (`pinned-bar-max-fraction`, P3-09): 91.6 dp at 360 × 640, so **there the tabs scroll with the content**; at 360 × 800 (121 dp) and 412 × 915 (139 dp) they are pinned.
  - **Filters tab:** the strip 90, then Intensity 16 + 68 (with a filter on) = **174 dp**. At 360 × 640: the tabs and the top 72 dp of the tiles show (48 + 16 + 72 = 136 of 133: the tile's last 3 dp and the names are under the fold), so the first view is the tabs and the pictures, and one swipe reaches the names and Intensity. At 360 × 800: pinned tabs; 158 dp of content: the strip and Intensity show, the slider's last 16 dp of target cut (rail and thumb in view). At 412 × 915: whole, 30 dp spare. The mixed hint adds 32 dp (two lines) under the tabs: it scrolls at the two smaller sizes.
  - **Adjust tab:** six rows of 68 and five gaps = **488 dp**: at 360 × 800 two rows show (152 of 158), at 412 × 915 two and a half, at 640 the tabs and the first row's label; the panel scrolls (as P2-04's does).
  - Accepted: 360 × 640 is the floor and the ToolPanel's scroll rule covers it; the photo editor's panel can grow to 40% of the window, the video editor's can't because the timeline's region is fixed (P1-04).
  - At 200% font every row grows, tab labels size to their text and scroll sideways (SegmentedTabs rule), tile names wrap to two or three lines and the strip grows taller; the panel scrolls with the title in place.

### 3. How it draws
- **Pipeline position** (P4-04 fixed it for the whole clip): turn, mirror, crop → key → **P2-04's steps 2 to 6 in their order** (brightness, contrast, saturation, warmth → filter → sharpen → vignette; there is no deep-fry) → Fit or Fill → mask → framing → opacity → blend. So a filter never moves a green screen's key, and a Fit clip's black bars and the canvas background are not filtered.
- **Vignette and the sharpen radius are measured on the clip's own (cropped) picture,** not the canvas: *r* is the distance from the picture's center as a share of its half-diagonal; the sharpen radius is 0.0015 of the picture's shorter side. A Fit clip's vignette darkens the clip's edges, not the bars; a clip at another resolution or ratio gets the same look scaled.
- **Same data, one look:** the LUT files, the sampling rule and the formulas are the ones in `:core:model` from P2-04 (no video copy); checked by reading (Measured). The Media3 path feeds them as the engine's LUT effect plus a color-matrix and a small shader for sharpen and vignette; P4-07 reports back if the current API can't take a 33³ table (P2-04 request 2 already told the engineer to verify `SingleColorLut`).
- **Preview = export,** as P2-04 says for the photo editor: the same spec, the same data.
- **Video-specific:** color is processed in 8-bit sRGB; the decoded frame's color range and matrix follow the clip (limited-range sources are expanded first, so a filter on a phone clip and on its still frame match). The engineer notes this in the handoff.

### 4. The look on the timeline and in other edits
- **Thumbnails** (timeline strip, Reorder tiles, drafts cover) show geometry only, not filters or adjustments (P4-16 D8; P2-04's Layers row rule). No "Edited" label exists in the video timeline: the clip's **spoken description** gains `a11y_state_filtered` ("Filter: Vivid" or "Adjusted" when only sliders are set), appended after P4-04's "Green screen" and before the mask (the order is set in P4-03b: Cropped, Rotated, Flipped, Moved or resized, Green screen, Filter or Adjusted, Mask, Blend, Keyframes).
- **Split, Duplicate:** both pieces keep it. **Replace:** keeps it (the user's look for the slot, like Fit or Fill). **Crop, trims, moves:** untouched. **Ratio change:** untouched (the look is about the picture).
- **Undo:** a tile tap, a slider release, Reset: one step each ("Filter", "Adjust", "Reset"); from the main bar the step covers every clip ("Filter, all clips" is `edit_filter_all`).

### 5. Analytics
`tool_use`, `editor: "video"`, once per committed undo step, with the **same ids as the photo editor** (P2-04): `filter` with `filter: <id>` (`none` on removal) and `adjust_brightness`, `adjust_contrast`, `adjust_saturation`, `adjust_warmth`, `adjust_sharpen`, `adjust_vignette`. Reset fires the ids that changed. No new id; no scope parameter (Question 2 in the PM list below).

## Reference renders and Measured (CLAUDE.md rule 5)
- **Parity with the photo references (the Done when):** for each of the 20 filters at 100%, and a sample at 50%, take the three test photos of P2-04 (`docs/qa/references/P2-04-image-tools/`) as **main photo clips** (3 s) in the video editor with the filter on, export 1080p30, pull the frame at 1.5 s, and compare with the P2-04 reference for the same photo and filter at 1080: **the mean absolute difference at most 2/255 per channel and 99% of pixels within 6/255** (the allowance is the encoder's: H.264 at the export bitrate; the photo references are lossless). Also the six adjustments at −100, 0, +100 on the gray ramp. Same ids in the same order, checked by reading.
- **Video-specific references** in `docs/qa/references/P4-07-video-filters/`: `filter-on-moving-clip` (a debug clip with a moving test pattern: the look on frames at 0.5 s and 3 s, so a per-frame error shows); `adjust-on-fit-clip` (a landscape clip Fit in 9:16: the black bars unfiltered, the vignette on the clip only); `filter-on-overlay` (an overlay with a filter over an unfiltered main clip); `filter-all-clips` (three clips, the look on all); `filter-then-key` (a green overlay, filter Cinema: the key unchanged, the subject graded); `limited-range` (the same clip as a limited-range video and a full-range still: the same look).
- **Editor equals export:** a preview screenshot of the video editor (cropped to the frame, scaled to the export width) against the export frame: 99% of pixels within 3/255 per channel.
- **Each edit is one undo step:** a tile, an Intensity release, an Adjust release, Reset (a tab), from the main bar (all clips, one step) and from a clip bar: Undo once after each.
- **Speed (the Done when's cousin):** a filter plus Adjust on a 1080p main clip with one 1080p overlay: preview at 30 fps or more on the owner's phone (Media3 logs); an Adjust drag for 3 s three times: janky frames under 5%; opening the Filters tab shows the first five tiles within 300 ms (P2-04's bar).
- **Non-destructive and old drafts:** the media file's size and hash don't change; a draft saved before P4-07 opens with no look.
- **API 29:** the filter works on an API 29 emulator and a current phone (P2-04's check) in the video path too.
- **Leaks:** LeakCanary: open Filters from the main bar and from a clip, change tiles and sliders, Reset, close the editor; repeat 5 times.

## States (only the new or changed rows; P2-04's States table covers the rest)
| State | What shows | Copy |
| --- | --- | --- |
| Main bar, tool | `adjust` icon, "Filters"; absent in an empty project | `tool_filters` |
| Panel from the main bar | Title "Filters, all clips"; Filters tab; the strip of the playhead's clip | `filters_title_all` |
| Panel from a clip bar | Title "Filters" | `tool_filters` |
| Mixed looks | No tile selected; no Intensity row; the hint | `filters_mixed_hint` |
| A filter on, all clips | Its tile ringed; Intensity row | `filters_intensity` |
| Preview catching up | Last frame stays; "Updating the preview" after 1 s (P1-06) | `editor_updating_preview` |
| Clip file missing (clip bar) | The tool is absent (P4-16 order); from the main bar it applies to the other clips | none |
| Photo clip | The same panel; the strip uses the photo | none |
| Reorder mode | Main bar replaced; unreachable | P1-06 |
| Offline, permission | Not applicable | none |

## Copy (new keys only; the 20 `filter_<id>`, `adjust_*`, tab labels and `filters_*` are P2-04's, reused)
English source; drafts for id, es, pt and hi; the engineer machine-drafts the rest until P5. Budgets are characters.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_filters` | Filters | 14 | Filter · Filtros · Filtros · फ़िल्टर | Tool label and the panel title for one clip; the same word as `edit_photo_tab_filters` (reuse its value) |
| `tool_filters_click_all` | Change the look of your video | 40 | Ubah tampilan videomu · Cambia el aspecto de tu video · Mude o visual do seu vídeo · अपने वीडियो का लुक बदलें | Click label on the main bar; 29 en |
| `tool_filters_click_clip` | Change the look of this clip | 40 | Ubah tampilan klip ini · Cambia el aspecto de este clip · Mude o visual deste clipe · इस क्लिप का लुक बदलें | Click label in a clip bar; 28 en |
| `filters_title_all` | Filters, all clips | 28 | Filter, semua klip · Filtros, todos los clips · Filtros, todos os clipes · फ़िल्टर, सभी क्लिप | Panel title from the main bar; es 24, pt 25 |
| `filters_mixed_hint` | Clips have different looks. A change applies to all. | 60 | Tampilan klip berbeda-beda. Perubahan berlaku untuk semua. · Los clips tienen aspectos distintos. Un cambio se aplica a todos. · Os clipes têm visuais diferentes. Uma mudança vale para todos. · क्लिप के लुक अलग-अलग हैं। बदलाव सभी पर लागू होगा। | Line under the tabs in the mixed state; 52 en, es 62 (at the limit: trim a word when drafting) |
| `a11y_state_filtered` | Filter: %1$s | 24 | Filter: %1$s · Filtro: %1$s · Filtro: %1$s · फ़िल्टर: %1$s | Appended to the clip's spoken description; %1$s is a filter name, or the word "Adjusted" (`a11y_state_adjusted`) when only sliders are set |
| `a11y_state_adjusted` | Adjusted | 16 | Disesuaikan · Ajustado · Ajustado · समायोजित | Same place |
| `edit_filter_all` | Filter, all clips | 24 | Filter, semua klip · Filtro, todos los clips · Filtro, todos os clipes · फ़िल्टर, सभी क्लिप | Edit name after "Undo: " (P2-04's `edit_filter`, `edit_adjust` for a clip) |

Translator notes: **Filters** are ready-made looks for a whole video; the 20 names are the photo editor's, so they match (see P2-04's notes). **"All clips"** means every clip in the main video, not the overlays.

## Accessibility (only what differs)
- The panel title says the scope, so a screen-reader user hears "Filters, all clips" on open. Focus lands on the title; closing returns focus to the Filters tool. The tool's name is its label; the click label carries the scope.
- The strip is P2-04's single-choice group ("Filters", "Vivid, 2 of 21"); in the mixed state no tile is "selected", and the hint is read after the tabs.
- All gestures are taps and sliders (no drag-only control; the sliders' steps are P2-04's).
- **200% font:** as above; the title wraps Reset and Done under it; the strip's tile names wrap and the strip grows.
- **Reduce motion:** P2-04's (the tile ring and tab switch are 120 ms fades).

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| The look | `MediaClip.edit: ImageEdit?` (P4-16) gains **`adjust`** (six floats) and **`filter: FilterRef(id, intensity)?`**, P2-04's exact types; `deepFry` stays 0 and has no control in video. The fields P2-04 defined whole in `:core:model` (the P2-04 request 9 said "define `ImageEdit` whole in 04a") are reused, not copied |
| Scope | The main bar's tool writes the same fields to every main clip in one `commit`; a clip bar's to one clip |
| New clips | `addMedia` copies the shared look when every main clip has the same `adjust` and `filter` (the P1-11 Fit rule) |
| Version | One bump with P4-16's if the engineer adds the geometry and color fields together; the new fields default to null |

## Requests to the principal mobile engineer
1. **One look in both editors:** the video engine's filter and adjustment pass reads the same `:core:model` specs and LUT resources as the photo editor; verify the current Media3 `SingleColorLut` and effect APIs at the start (P2-04 request 2), and report if a 33³ table or the combined pass needs a different form. The `iosMain` implementation is a marked stub that reports unsupported.
2. **The panel as one component:** lift P2-04's Edit photo panel (tabs, strip, Intensity, Adjust rows, Reset) into `:core:ui` with `tabs = [Filters, Adjust, (Deep-fry)]`, a thumbnail source (a bitmap or a video frame) and a scope title; both editors host it. Video hosts it in its fixed-region ToolPanel.
3. **The tool** in the main bar and the clip bars; the mixed state and the shared-look rule for new clips.
4. **Thumbnails:** one frame per open (the playhead's clip frame via P4-16's crop-frame use case), the 21 tiles on demand, cached for the visit.
5. **Strings:** the new keys in the five files; reuse P2-04's values for the tabs and filter names (rename to neutral keys when lifting). **Analytics:** none new.
6. **Reference scenes** for the video-specific renders; frames saved to `docs/qa/references/P4-07-video-filters/`.

## Tokens and icons
- **No new color, size, radius, motion token or icon.** The tool uses `adjust` (P2-04); the tiles use `filter-tile-size`; the pinned rule uses `pinned-bar-max-fraction`.
- **New content group** `videoLook` in `design/tokens.json` and `design/system/tokens.json`: the scope rules, the pipeline position and the parity tolerance (references `photo.imageTools`, so the data lives once). Kotlin: no new object; the P2-04 `ImageToolCatalog` is reused.
- **Pointer edits made with this spec:** ToolBar README (Filters in the main bar and the clip bars), DESIGN_SYSTEM (a video-look note), P2-04's "Same ids and data for video" line stays true.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. What does Filters do from the main bar: the whole video, or one clip?**
- *Problem:* a video has several clips; "put a look on my meme" usually means all of it, but one clip is sometimes wanted.
- *Considerations:* **A. Main bar: every main clip; a clip's own bar: that clip** (recommended; applied): the common job is two taps; the one-clip job is two taps too (select, Filters); the title says which; cost: the mixed state and the shared-look rule for new clips are extra logic (about half a day). **B. Always the selected clip (select a clip first):** no mixed state, but the whole-video look costs a tap per clip. **C. A "Apply to all clips" switch in the panel:** one more row (48 dp of the 133 dp budget) on every use.
- *Reverse:* a rule change; no migration (the model is per clip either way).

**For the PM (not asked of the owner):** the PRD's video filter row lists **tint and grain** beside the six adjustments; the photo editor (P2-04) has neither, and TICKETS P4-07 says "same ids and looks as the photo filters". This addendum ships the **same six** so both editors match. **Proposal:** add tint and grain to **both** editors in one small later ticket (a Later item, one Adjust row each, with reference renders), or drop them from the PRD row. Needs a PM decision; nothing here depends on it.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| A 3D LUT of size 33 sampled trilinearly is a standard compact carrier for color looks and Media3 applies one as a GL effect | Guidance (API reference) | [Media3 `SingleColorLut`](https://developer.android.com/reference/androidx/media3/effect/SingleColorLut) |
| A look applied to a whole video in two taps (a Filters tool on the main bar) and per clip from a selected clip is how mobile editors split the scope | Assumption (product knowledge of CapCut and VN; their help pages weren't reachable here) | none |
| Fixed regions at the floor size (360 × 640) scroll by the ToolPanel rule; the pinned part may take 40% of the panel | Evidence (Memix specs P3-09, P4-01b; arithmetic from the P1-04 region figures) | `design/system/components/ToolPanel/README.md` |
| Memix row heights (tile 72 + name, slider 64 to 68, tabs 48) | Evidence (read from the component code and the P2-04 sizes) | `core/designsystem/.../Slider.kt`, `OptionTile.kt` |
| A control's accessible name includes its visible label; non-text marks need 3:1 | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html), [SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
