# Remove background: the cut-out screen, refine, save as sticker (and P2-06's states) · P2-07

Seventh spec for the photo editor. P2-06 (the `Segmenter` and the ML Kit model) has **no screen of its own**: its "Preparing…" and failure states are specified here. It reuses, and does not repeat:
- `P2-04-image-tools.md`: the **Crop screen pattern** (a mode over the editor: Close, Reset, a blue Done, back discards), the way a tool selects the main photo, the pipeline and `ImageLayer.edit`, the Slider and tile patterns.
- `P2-05-draw-and-censor.md`: freehand strokes (quantize, simplify, cap), the five size words.
- `P2-01-photo-editor-shell.md`: layers, the Layers row, locked and missing layers, `drawPhotoScene`.
- `P2-03-photo-text-and-stickers.md`: the sticker layer (the destination of a saved sticker, via P2-14).
- Board: `design/screens/RemoveBackground.dc.html` (checkerboard cut-out, "Background removed", Erase and Restore, brush size, edge softness, "Save as sticker" and "Apply").

## Job
"Cut my friend out of the photo": one tap, a clean cut-out on a checkerboard, a few brush strokes to fix the edges, then Apply (the layer now has no background) or **Save as sticker** (it goes to My stickers, P2-14). Free, on the phone, nothing uploaded. **Done when** (TICKETS): refine strokes survive save and reopen. **PM reading (5 Nov):** also, the edited cut-out in the editor equals the render at 1080 and 2160.

**The one blue action:** **Apply** on the cut-out screen (one per screen, as the Crop screen's Done). In a failure state the one blue action is **Try again**. White is selection (the selected segment, tile ring).

**Fast path, with tap counts:** Remove BG (1: it selects the main photo and opens the screen), wait under 1.5 s, Apply (2). To keep it: Save as sticker (2) instead of or before Apply. First use ever adds one wait for the model download.

## Decisions
- **D1. A mode screen like Crop**, full screen over the editor (200 ms slide up; reduce motion 120 ms fade), with the picture's whole upright **original picture** shown (no crop, rotation or color edits: the cut-out belongs to the picture; the layer's geometry and color edits apply to the picture and its cut-out together afterwards, P2-04).
- **D2. The tool** (**Remove BG**, icon `cutout`): in the main bar (final order in P2-05) **only while an image layer exists**; a tap with nothing selected **selects the main photo (the lowest image layer) and opens the screen on it**, as P2-04's tools; and in the selected image layer's bar after Crop (**Position · Opacity · Delete · Edit photo · Crop · Remove BG · Duplicate · Lock · Hide · Layers**). **Text and sticker layers show no Remove BG** (no dead controls); a locked layer or a missing file explains (the usual toasts). On a layer that **already has a cut-out** it opens on that cut-out for editing.
- **D3. All detected subjects are kept** (people, pets and objects together: the segmenter's full mask). Choosing one subject out of several is Later; the refine brush covers it.
- **D4. Save as sticker** is a button on the screen (board), independent of Apply.
- **D5. No fallback model** (owner, 5 Nov by delegation): when the model is unavailable the user sees a calm "can't remove the background right now" with a retry.

## The cut-out screen

### Layout, top to bottom
1. **Top bar** (56 dp, `canvas`): **Close** (`close`, spoken "Cancel"), the title "Remove background", then **Undo** and **Redo** (screen-local: they step through the refine strokes and the softness changes made on this screen; `text`, disabled `text-muted`).
2. **Stage** (`stage` black): the picture, **cut out over a checkerboard** (cells 12 dp in `canvas` and `surface-raised`; content-on-picture, not chrome), fitted inside the stage and zoomable (below). A quiet status pill at the top of the stage while ready: **"Background removed"** (`surface-raised`, `radius-full`, `label`; the board's "1.2 s" is dropped: a duration means nothing to a user).
3. **Bottom area** (`surface`, `hairline` top border, `space-4` padding):
   - **Mode row:** a SegmentedChoice **Erase · Restore** (default Erase). Erase hides what the brush paints over; Restore brings the photo back.
   - **Brush size:** five tiles **Thin · Fine · Medium · Thick · Heavy** (the Draw tiles, a dot at the relative size), default Medium. Sizes are shares of the picture's longer side: **0.015, 0.03, 0.06, 0.10, 0.16**.
   - **Edge softness:** a **Slider**, label "Edge softness", readout in percent, **0 to 100%, default 30%** (the board), one step on release (the feather is computed on release, not per frame); snap to 0 within ±3% with one SegmentTick.
   - **Actions:** **Save as sticker** (Secondary) and **Apply** (Primary, the blue), side by side; a quiet line under them: **"Runs on your phone. Your photo is never uploaded."** (`caption`, `text-secondary`, the board's line).
   - When the layer already has a cut-out, a **Quiet "Remove cut-out"** button sits above the actions (it applies "Remove cut-out" at once and closes: the layer gets its whole picture back).

### Gestures
- **One finger paints** with the brush (Erase or Restore), 1:1 with the finger, round, in the chosen size; **two fingers pinch-zoom (1× to 5×) and pan** the picture and never paint (extra fingers during a stroke are ignored). A small **Fit** button (`label`, Quiet) appears at the stage's corner while zoomed and returns to fit.
- A stroke is **committed on release** and is one screen-local undo step. Strokes are vectors in picture coordinates (fractions of the picture's longer side), quantized and simplified and capped at **5,000 points** as P2-05 (a full cut-out shows the toast "This cut-out has a lot of edits. Apply it and edit again if you need more.").
- **No selection and no layer gestures** on this screen. Single-finger painting is an essential drag (WCAG 2.5.7's exception); everything that makes the cut-out (the automatic result, softness, Apply, Save, Remove cut-out) needs no drag.

### Leaving
- **Apply** commits everything (the cut-out, the strokes, the softness) as **one undo step "Remove background"**, closes the screen and returns to the editor with the layer selected. **Close, system back and Escape discard** the screen's changes (no question: nothing was applied; the screen is a mode, as Crop; Assumption, QA watches for lost work); a cut-out already on the layer is unchanged.
- **Save as sticker** does **not** close the screen or apply anything.

## States: P2-06's "Preparing…", the wait, and the failures
All status messages are **calm and static** (MOTION 2 bans looping bars): a card centered on the stage over the dimmed picture, `surface-raised`, `radius-lg`, `space-4` padding, a title in `title` and a body in `body` (12.4:1), and the one action.

| State | What shows | Copy |
| --- | --- | --- |
| **Preparing** (the model is not installed yet; first use) | The picture dimmed; the card "Getting the cut-out tool ready", the body "This happens once and needs the internet. Your photo stays on your phone." A **determinate progress bar** (`ProgressBar`, primary) when the download reports its progress, otherwise the words only. **Close** works (a cancel; nothing is lost) | `cutout_preparing_title`, `cutout_preparing_body` |
| **Working** (the model is ready; segmenting, usually under 1.5 s) | Nothing for the first 300 ms (the screen holds the photo); after that the static line "Removing the background…" at the stage's top (no spinner). The tools below are present and inert | `cutout_working` |
| **Ready** | The cut-out on the checkerboard, "Background removed", the tools live | `cutout_ready` |
| **Nothing found** (no subject) | The picture stays whole (nothing is cut out yet); a card: "We couldn't find a person, pet or object", "Try another photo, or erase the background by hand." The brush tools stay live (Erase works on the whole picture); **Try again** re-runs the segmenter | `cutout_none_title`, `cutout_none_body` |
| **Can't remove right now** (the model is unavailable: no Play services, the install failed, an unknown segmenter error) | The card: "Can't remove the background right now", "Please try again in a moment." **Try again** (Primary) re-runs the check and the install | `cutout_failed_title`, `cutout_failed_body`, `cutout_retry` |
| **Offline, first use** (the model needs downloading and there is no network) | "Connect to the internet once", "Memix needs to download the cut-out tool the first time. Connect and try again. Your photo stays on your phone." **Try again** | `cutout_offline_title`, `cutout_offline_body` |
| **Not enough memory** | "This photo is too big to cut out right now", "Close other apps, or try a smaller photo." **Try again** | `cutout_memory_title`, `cutout_memory_body` |
| **A model that never finishes** | After 30 s the Preparing card adds "This is taking longer than usual." and **Try again** (it restarts the check) | `cutout_slow` |
| **Save as sticker**, saved | A toast: "Saved to My stickers" | `cutout_saved` |
| **Save as sticker**, nothing to save | A toast: "Nothing is left to save: restore some of the photo first." | `cutout_save_empty` |
| **Save as sticker**, already saved (nothing changed since the last save) | A toast: "This sticker is already in My stickers." | `cutout_save_again` |
| **Save as sticker**, can't write | A toast: "Couldn't save the sticker. Free up space and try again." | `cutout_save_failed` |
| **Locked layer, text or sticker layer, missing file** | The tool explains with the usual toast and never opens | `explain_layer_locked`, `explain_layer_missing` |
| **Hidden layer** | Allowed; the screen shows the picture (the stage is the screen, not the canvas) | none |
| **Undo and Redo on the screen** | Step through strokes and softness | `editor_undo`, `editor_redo` |
| **A draft saved before this ticket** | No cut-outs; opens unchanged | none |
| **Long content (5 languages, 200% font)** | Cards wrap; the bottom area scrolls vertically if taller than 45% of the window; the stage never shrinks below 30% of the window | none |

- **No model fallback** (owner, 5 Nov, by delegation): only the states above; no second library.
- **Every failure has Try again** and a Close; none ends in a dead end.

## What Apply does to the layer
- **Non-destructive** (Model): the cut-out is the segmenter's **mask saved as a new media file** in the picture's own coordinate space (long side capped), the **refine strokes** are a vector list on the layer, **softness** a parameter. The photo file is never changed; the final alpha is computed from these, so **crop, rotate, flip, straighten, move and scale keep working** (P2-04's geometry applies to the picture and its mask together), and reopening the screen shows the saved strokes.
- **Pipeline position:** the cut-out alpha applies first, with the picture; adjustments, filter, deep-fry, sharpen and vignette then act on the visible pixels and keep the alpha (P2-04 order).
- **The layer's box is unchanged:** the transparent parts still count as the layer's box for the frame and hit testing (P2-01). A tap on the empty area selects the layer; the Layers panel reaches the layers below.
- **The Layers row:** thumbnail **fitted inside with the cut-out applied** on `surface` (transparent shows on `surface`); the meta line gains **"Cut out"**, ordered after "File missing" and before "Edited": Locked · Hidden · File missing · **Cut out** · Edited · 40% opacity.

## Save as sticker
- **What is saved:** the picture **with the cut-out, strokes and softness applied, trimmed to the cut-out's bounds**, as a transparent PNG or WebP, long side capped at **1024 px** (P2-09 converts to WhatsApp's 512 × 512), written to the **sticker store** on the phone (device-wide, shared with P2-14 and P2-09), never uploaded. The layer's own crop and color edits are **not** included (the sticker is the cut-out; Later: with the look).
- **It is separate from Apply:** both, either or neither. It does not close the screen. Tapping it again with nothing changed since the last save says it is already saved.
- **Where it ends up:** **My stickers** (P2-14), at the top of the list.

## Interactions
- **Back order:** a stroke in progress is cancelled → a status card closes the screen (Close) → the screen closes, discarding.
- **Haptics:** SegmentTick at the softness slider's snap; nothing else.
- **Keyboard:** Tab reaches the controls; Escape discards; painting needs a pointer (the essential exception).
- **Motion:** the screen slides up 200 ms (reduce: 120 ms fade); the status pill and cards fade 120 ms; strokes follow the finger with no easing.

## Copy
English source; machine drafts for id, es, pt and hi (until P5). Budgets are characters. Reused: `panel_done`, `explain_layer_locked`, `explain_layer_missing`, `draw_size_*`, `editor_undo`, `editor_redo`.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `tool_remove_bg` | Remove BG | 20 | Tool. "BG" = background; use your language's short form, or "Cut out" |
| `cutout_title` | Remove background | 24 | Screen title |
| `cutout_cancel_a11y` | Cancel | 12 | Spoken name of the close button |
| `cutout_ready` | Background removed | 24 | Status pill |
| `cutout_working` | Removing the background… | 32 | Static line while working |
| `cutout_erase` | Erase | 12 | Segment: hides more of the photo |
| `cutout_restore` | Restore | 12 | Segment: brings the photo back |
| `cutout_softness` | Edge softness | 20 | Slider label: how soft the cut edge is |
| `cutout_save_sticker` | Save as sticker | 20 | Button: keeps the cut-out in My stickers |
| `cutout_apply` | Apply | 12 | Button: the one blue action |
| `cutout_remove` | Remove cut-out | 20 | Quiet button: gives the whole photo back |
| `cutout_fit` | Fit | 8 | Returns the zoomed picture to fit |
| `cutout_privacy` | Runs on your phone. Your photo is never uploaded. | 60 | The board's line |
| `cutout_full` | This cut-out has a lot of edits. Apply it and edit again if you need more. | 90 | Toast at the 5,000-point cap |
| `cutout_preparing_title` | Getting the cut-out tool ready | 40 | |
| `cutout_preparing_body` | This happens once and needs the internet. Your photo stays on your phone. | 90 | |
| `cutout_slow` | This is taking longer than usual. | 50 | |
| `cutout_none_title` | We couldn't find a person, pet or object | 50 | |
| `cutout_none_body` | Try another photo, or erase the background by hand. | 70 | |
| `cutout_failed_title` | Can't remove the background right now | 50 | |
| `cutout_failed_body` | Please try again in a moment. | 50 | |
| `cutout_offline_title` | Connect to the internet once | 40 | |
| `cutout_offline_body` | Memix needs to download the cut-out tool the first time. Connect and try again. Your photo stays on your phone. | 120 | |
| `cutout_memory_title` | This photo is too big to cut out right now | 50 | |
| `cutout_memory_body` | Close other apps, or try a smaller photo. | 60 | |
| `cutout_retry` | Try again | 14 | The one blue action in a failure state |
| `cutout_saved` | Saved to My stickers | 30 | Toast |
| `cutout_save_empty` | Nothing is left to save: restore some of the photo first. | 70 | Toast |
| `cutout_save_again` | This sticker is already in My stickers. | 50 | Toast |
| `cutout_save_failed` | Couldn't save the sticker. Free up space and try again. | 70 | Toast |
| `a11y_cutout_done` | Background removed | 24 | Announced when the result is ready |
| `layer_meta_cutout` | Cut out | 14 | Row meta |
| `edit_remove_bg` | Remove background | 24 | Edit name after "Undo: " |
| `edit_remove_cutout` | Remove cut-out | 24 | |

Translator notes:
- **"Cut-out"** is the person or thing kept with the background gone; **"sticker"** is a small transparent picture to place on a meme (the same word as `layer_name_sticker`).
- **Erase / Restore** are brush modes: hide more of the photo, bring it back.
- **Calm tone** in every failure: nothing is the user's fault; say what to do next.

## Accessibility
- **TalkBack:** one-tap result is the whole accessible route: the tool, the automatic cut-out ("Background removed" announced politely), softness (a Slider with 10% steps), Apply, Save as sticker and Remove cut-out are all single-pointer. **Brush painting is an essential drag with no alternative in v1** (WCAG 2.5.7's exception); the automatic result must be good enough without it, and the layer can be redone by another photo. Later: tap-to-include subjects.
- **Focus order:** Close, Undo, Redo, the stage (one node "Photo, background removed"), Erase, Restore, the size tiles, softness, Remove cut-out (if shown), Save as sticker, Apply. A status card takes focus when it appears and Try again is its action; Close stays reachable.
- **Touch targets:** every control 48 dp or more; the size tiles and segments as Draw.
- **Contrast:** card text `text` on `surface-raised` 12.4:1; the privacy line `text-secondary` on `surface` 7.1:1; Apply `on-primary` on `primary` 7.9:1; the checkerboard is decoration.
- **200% font:** as the States table; the buttons stack when they don't fit side by side.
- **Reduce motion:** see Interactions.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `bg_remove` | The segmenter finishes (a success or a failure) | `editor: "photo"`, `success` (true or false), `duration_ms` (PM, 5 Nov: from the PRD) |
| `tool_use` | **Apply** commits | `editor: "photo"`, `tool: "remove_bg"` (once per committed undo step) |
| `tool_use` | **Remove cut-out** commits | `tool: "clear_cutout"` (new id for the PM to confirm) |
| `save_sticker` | Save as sticker finishes successfully | `editor: "photo"` (PM, 5 Nov) |

No event for strokes, softness, opening the screen or retries. Analytics stay off until consent (P5-01).

## QA compares
Use the debug scenes `cutout-check` (a person, a pet and an object photo, a busy background, a hair-edge photo), the failure hooks `bg-remove-fail:<kind>` (`unavailable`, `offline`, `memory`, `none`, `slow`) and a gallery start.
1. **The bars:** a photo layer selected (Remove BG after Crop, whole or cut by the scroll); the main bar with Remove BG; a text layer without it.
2. **The screen** at 360 × 800 and 360 × 640 after a person photo: the checkerboard, "Background removed", Erase and Restore, the size tiles, softness at 30%, Save as sticker and Apply, the privacy line.
3. **Refine:** an Erase stroke across the background left behind, a Restore stroke over a lost ear, softness at 0, 30% and 100% on a hair edge; zoomed to 5× and Fit.
4. **Each state card:** Preparing (with a progress bar and without), Working (after 300 ms), Nothing found, Can't remove right now, Offline, Not enough memory, Slow.
5. **Apply and reopen:** the layer cut out on the canvas over another layer; the row's thumbnail and "Cut out"; the screen reopened with the strokes; a crop and a filter after the cut-out; Remove cut-out; Undo.
6. **Save as sticker:** the toast, then My stickers (P2-14); twice in a row ("already").
7. **Hindi at 200% font**, 360 × 640: the screen, a failure card, the bottom area.

**Measured: the Done when** (PM method in `docs/pm/acceptance-log.md`; the designer adds):
- **Refine survives save and reopen:** three strokes (erase, restore, erase), softness 60%, Apply, kill the process, reopen from Drafts: the cut-out is the same; the screen reopens with the same strokes.
- **Editor equals render:** on `cutout-check`, the canvas screenshot against the render hook at 1080 and 2160: edges and the feather within 1% of the width, the same alpha at sampled points (within 3 of 255), and **the 2160 render equals the 1080 render scaled**. References in `docs/qa/references/P2-07-cutout/` (CLAUDE.md rule 5), at both sizes.
- **Non-destructive:** the photo file's size and hash do not change; a crop, a move and a filter after the cut-out keep it; Undo restores exactly.
- **A person, a pet and an object** each cut out; the first run (download) reported separately from the median of five warm runs (P2-06's bar: under 1.5 s on the owner's phone).
- **Draft size:** report the saved JSON size of a long refine session (at the 5,000-point cap) and that autosave still commits within 100 ms; the mask is a file, not JSON.
- **API 29:** the screen works on an API 29 emulator with Play services; the feather uses the software path on Android 10 to 12.
- **Leaks:** LeakCanary, 5 cycles of open, cut out, refine, Apply, close.

## Model
Changes needed from the design (the engineer owns the exact code; the PM's must-knows carry the version bump):

| Need | Model |
| --- | --- |
| The cut-out | `ImageEdit.cutout: Cutout? = null` (P2-04's `ImageLayer.edit`): `mask: MediaRef` (an 8-bit alpha WebP or PNG in the **picture's own coordinate space**, long side capped at 2048 px), `strokes` (a vector list of mode erase or restore, brush width as a share of the picture's longer side, points quantized to 0.001 and simplified, at most 5,000 points), `softness` (0 to 1) |
| Never destructive | The mask is a **new media file**; the photo and the mask are never rewritten in place; the mask goes into `mediaCopyPaths()` and `withMediaCopyPaths()` or the cleaner deletes it and a duplicate shares it |
| Final alpha | Computed from the mask, the strokes and the softness (a Gaussian feather on the alpha, radius up to 0.01 of the longer side, **on slider release, not per frame**); applied with the picture at step 1 of P2-04's pipeline; geometry applies to picture and mask together |
| Version | One bump (read the constant), version-only (the new field defaults to null); old-draft check |
| Constants | Mask cap 2048, brush sizes 0.015 to 0.16, softness default 0.3 and feather 0.01, the point cap, the sticker long side 1024, in one commonMain place with a comment pointing here and `design/tokens.json` → `photo.cutout` |
| The sticker store | Device-wide (files plus an index), written by Save as sticker, listed by P2-14, used by P2-09; a placed sticker copies its file into the project (P2-14) |

## Requests to the principal mobile engineer
I don't edit code, so these are requests; the PM's must-knows (5 Nov) carry the rest.
1. **The screen as a mode over the editor** (the Crop pattern), reusing the Slider, SegmentedChoice, size tiles and ProgressBar; the screen is built on the segmenter's `SegmentationResult` through use cases only (features never import ML Kit).
2. **States as the table says,** driven by distinct segmenter results (not installed, no Play services, no network, out of memory, no subject, unknown) so no failure is a crash or a silent empty screen; the debug hooks `bg-remove-fail:<kind>` let QA reach each. The 300 ms rule, the 30 s slow note, no spinner.
3. **Mask handling:** keep the mask as a temporary file until Apply; Apply writes the media copy; Save as sticker renders from the same data and trims to the alpha bounds; refine strokes quantized and capped as P2-05; the feather on release; Android 10 to 12 software path.
4. **Zoom and pan on the screen** without re-decoding (draw-phase transform of the decoded bitmap).
5. **The layer bar and main bar** entries and the "Cut out" meta part; the row thumbnail applies the mask.
6. **Analytics:** `bg_remove` (`success`, `duration_ms`), `tool_use remove_bg`, `clear_cutout`, `save_sticker`.
7. **Update** TECHNICAL_DESIGN and the `background-removal` skill (no fallback in v1, P4-13 reconsiders; P2-06's commit).
8. **Size watch** (the PM's): past two build sessions, split the **cut-out and refine** from **Save as sticker**.

## Proposals and questions
**For the PM**
1. **Confirm the new id** `clear_cutout` (Remove cut-out).
2. **One tool entry in two places** (main bar after Censor in the final order, and the layer bar after Crop). The P2-04 spec's layer-bar line gains Remove BG.
3. **All subjects are kept** (D3); picking one subject is Later.
4. **The saved sticker excludes the layer's crop and color edits** (it is the cut-out); say if you want them baked in.
5. **No event** for refine strokes or softness.

**For the owner** (delegated, recommendation applied; the PM logs it for review)
- **1. Does Apply discard on back, as Crop?** *Problem:* a user could lose refine work with one back gesture. *Considerations:* **A. Back discards, Apply applies** (applied, recommended: the screen's contract, matches Crop); **B. Back applies:** a half-made cut-out lands on the layer. **Applied: A.** QA watches for lost work.
- **2. Keep all subjects, or let the user pick one?** *Considerations:* **A. All subjects** (applied, recommended: one tap, the smallest build; Erase fixes an extra subject); **B. Tap a subject to include or exclude it:** nicer for group photos but needs a mask per subject saved and more UI. *Reverse:* adding it later is a layer-data addition, no change to saved cut-outs. **Applied: A.**

**Later, no ticket:** choosing subjects by tap; hold-to-compare with the original; a sticker saved with the layer's look; an outline or shadow around a sticker; a fallback model.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| ML Kit Subject Segmentation (beta) works on still images, on device, with the model delivered through Google Play services | Guidance (vendor docs; PRD) | [ML Kit subject segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android) |
| Freehand brush painting is an essential drag, so WCAG's dragging criterion has an exception | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| Calm, specific messages that say what to do next beat generic errors | Guidance (expert) | [NN/g, Error-message guidelines](https://www.nngroup.com/articles/error-message-guidelines/) |
| A feather computed on release avoids per-frame blur cost | Reasoned from the P2-01 performance rules | `docs/ux/specs/P2-01-photo-editor-shell.md` → Requests |
