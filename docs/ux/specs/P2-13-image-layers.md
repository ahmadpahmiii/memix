# Image layers: add a photo on top · P2-13

Owner-approved 27 Oct (proposal `docs/pm/proposals/2026-10-27-photo-editor-image-layers.md`, option B). A new S ticket right after P2-12 (Needs P2-01 and P2-12). It is short on purpose: it adds one entry, one landing rule and one edit, and **reuses**:
- `P2-12-canvas-background-image.md` → Entry and exit: the one-photo pick flow (picker, import, copy, space check, failure sheets). Only the destination differs.
- `P2-01-photo-editor-shell.md`: everything a layer does after it lands (select, move, scale, turn, fade, lock, hide, reorder, delete, duplicate), the Layers panel, hit testing, the size of an image layer at scale 1.

## Job
The "reaction face on a meme" and "this vs that" jobs: put a second photo on the canvas and place it. **Done when** (the ticket, as the owner approved it): the picked photo lands on the canvas as a new top layer, fitted inside the frame, as one undo step; after that it behaves like any layer.

**The one blue action:** none. Add photo is a tool.

**Fast path, with tap counts:** Add photo (1), a photo in the picker (2); the new layer is already selected, so a pinch or a drag places it (3).

## Entry and exit
Two entries, the same flow, both live only once this ticket is built (nothing in P2-01 reserves them):

| Entry | Where | Notes |
| --- | --- | --- |
| **Add photo** tool | The photo editor's main tool bar, in its final place just before Layers (P2-01 → Tool bars). Icon `create` (the plus; the video editor's Add media uses it too) | Shown when nothing is selected |
| **Add photo** button | The first item of the Layers panel's list, above the front layer: where the new layer will land. A full-width Secondary Button with the `create` icon, `touch-target` tall, scrolling with the list | Shown whether or not a layer is selected |

- A layer being selected hides the main bar (the selected-layer bar has no Add photo): from there, Close layer tools, then Add photo, or Layers, then the panel's button. A shortcut in the selected-layer bar would crowd Delete's place; two taps from a selection to Add photo is fine because adding follows deselecting (Assumption).
- **The flow after a tap:** P2-12's pick flow, unchanged: the system picker (**images only, one item**, no permission), the 300 ms rule, the "Adding your photo" sheet, "Not enough space", "None went in", "No picker app" (photo wording). Nothing picked, cancelled or failed: the editor is unchanged, nothing is recorded, nothing fires.
- **Kept:** the layer and its media copy (`files/media/<project id>/`, never uploaded). Undoing the add leaves the file in the folder while Undo can bring it back (copies are never changed or removed in place).

## What lands
- **A new `ImageLayer` at the top of the stack** (the front), as one committed edit: one undo step, "Add photo".
- **Size: fitted inside the frame** (the owner's words): P2-01's "size at scale 1", the picture scaled to the largest size that lies wholly inside the canvas, centered, scale 1, no turn. A picture whose shape is within 1% of the canvas's covers it (the same 1% rule as every image layer). **Place:** the center of the canvas (0.5, 0.5).
- **Unlocked, shown, 100% opacity.**
- **The canvas doesn't change.** Its shape belongs to the project; the photo adapts to it. (A user whose second photo has another shape sees it letterboxed in the frame and can change the ratio with the pill.)
- **It becomes the selection:** its frame and handle show, and the bar switches to the selected-layer bar. The photo is fully visible, which is the point: the user sees what landed and can pinch or drag it at once. If it covers the layers beneath it (a same-shape photo does), the handle and frame still show it's there, and Undo is one tap.
- **If the Layers panel is open** (the entry was its button): the new row appears at the top of the list, selected, scrolled into view; the canvas above shows it.
- **If the canvas has no layers** (a blank canvas, or the last layer deleted): the same landing, and the empty-canvas card goes.
- **No layer limit** and no cap per pick: one photo per pick. Several at once is Later (below).
- **Adding the same gallery photo twice** makes a second copy (a storage cost only). **Duplicate** (P2-01) shares one file instead.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Main bar | Add photo before Layers | `tool_add_photo` |
| Layers panel | The Add photo button first in the list | `tool_add_photo` |
| Picker open, nothing picked, copying, no space, failed | P2-12 / P1-02's sheets with photo wording; the editor unchanged under them | `import_*`, `import_title_photo`, `import_none_*_photo` |
| Landed | The photo on the canvas, fitted, selected with frame and handle; the selected-layer bar; the announcement | `a11y_layer_added`, `edit_add_photo` |
| Landed on an empty canvas | The same; the empty card is gone | none |
| Landed, same shape as the canvas | The photo covers the frame (1% rule); the layers beneath are hidden by it; frame and handle show | none |
| Undone | The layer is gone; the previous selection logic of P2-01 | `editor_undo_toast` ("Undo: Add photo") |
| Missing file later (a draft restored without it) | P2-01's missing-photo state | `photo_missing_media` |
| Long content (5 languages, 200% font) | The button's label wraps inside its full width; the tool's label wraps (ToolBar rule) | none |
| Offline, permission | Don't apply | none |
| First run | Same as every run; no coach marks | none |

## Interactions and motion
- **Taps while the picker launches are ignored** (P1-02).
- **After landing:** no bonk (a bonk is for timeline items; the canvas change is the layer appearing: a 120 ms fade-in of its picture and frame, as every layer appears in P2-01). Reduce motion: instant.
- **Haptics:** none.
- **Undo and redo:** one step removes or restores the layer, with the usual selection rules (P2-01 → Interactions).

## Copy
English source; machine drafts for id, es, pt and hi until P5. Reused: `import_*` and the photo-wording keys (P2-01), `panel_done`, `editor_undo_toast`.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `tool_add_photo` | Add photo | 20 | Tool label and the panel button. Verb first. "Photo" matches the Photo meme row |
| `a11y_layer_added` | Photo added in front | 30 | Announced politely after the layer lands. "In front" = on top of the others |
| `edit_add_photo` | Add photo | 24 | Edit name after "Undo: " |

Translator note: **"Add photo"** adds a second picture on top of the first; it is not "attach a photo". The same word for "photo" as everywhere else.

## Accessibility
- **Tool bar and panel:** Add photo is a tool in the bar's order (before Layers) and the first stop in the Layers panel's list (after Move layer up and down and the Opacity row, before the rows). Its name is its visible label.
- **Focus after the picker:** closing the picker or the import sheet returns focus to the control that opened it: the panel's button stays, so focus returns to it. From the main bar the layer lands selected and the bar switches, so focus goes to the first tool of the selected-layer bar (Position), as P1-08 does after a sound lands. Where TalkBack's focus actually lands is a phone check (the same as P1-16 and P1-08).
- **Announcement:** "Photo added in front" (`a11y_layer_added`), then P2-01's layer reading when the user swipes to the canvas ("Photo, layer 1 of 3, selected").
- **Touch targets:** the tool and the button are 48 dp or more. **Contrast:** the button `text` on `surface-raised` 12.4:1; the tool `text-secondary` on `surface` 5.7:1.
- **Reduce motion:** see Interactions and motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | The photo lands (once per pick) | `editor: "photo"`, `tool: "add_photo"` |

- **`add_photo` is a new `tool_use` id** (the video editor's equivalent is `add_media`), for the PM to confirm. A cancelled pick, a failed import and nothing picked fire nothing. `value` is not used (canvas tools only).
- Analytics stay off until consent (P5-01).

## QA compares
Use a 4:3 photo start, a second 9:16 photo and a second 4:3 photo (same shape as the canvas).
1. **The main bar** with Add photo before Layers; **the Layers panel** with the Add photo button above the front row.
2. **After landing a 9:16 photo on a 4:3 canvas:** the photo fitted inside (the 4:3 canvas shows it with the background beside it), selected with frame and handle on top, the selected-layer bar; the new row at the top of the open panel.
3. **After landing a same-shape photo:** it covers the frame; frame and handle show.
4. **Undo:** the layer gone; redo brings it back at the same place.
5. **Hindi at 200% font**, 360 × 640: the bar with Add photo, and the panel's button with its label wrapped.

**Measured:**
- **Done when:** the landed layer is at scale 1, centered, rotation 0, unlocked, shown, fully opaque; its box lies wholly inside the frame (within 1% for the cover case); it is the top-most layer; one undo removes it.
- **Reopen:** kill after landing and reopen from Drafts: the layer, its place and its picture are there.
- **Memory and speed:** adding a 12 MP photo to the `photo-layers-10` scene decodes at the drawn size (P2-01 request 6) and the next drag stays under 5% janky frames.
- **Leaks:** LeakCanary, 5 cycles of add, drag and undo.

## Model
**No new fields and no schema bump.** `ImageLayer` already exists (P1-01); `locked`, `hidden` and `opacity` come with P2-01 (version 3). The media rules P2-01 (and the PM's must-know 5) already put in `Project.mediaCopyPaths()` cover the new layer's file.

## Requests to the principal mobile engineer
I don't edit code, so these are requests.
1. **Reuse P2-12's pick path** (P2-12 request 1), with the destination "new top layer". The edit is one `addImageLayer(media)` in `:core:domain`: appends at the front, centered, scale 1, selects it, one undo step.
2. **Reuse the P2-01 scene function** for the fitted size and the 1% cover rule; don't compute it twice.
3. **The tool and the panel button:** the ToolBar entry (before Layers) and a Secondary Button as the list's first item; the icon is `create` (no new icon).
4. **Analytics:** a new `EditorTool` value `add_photo`.
5. **Hand checks to list:** the QA compares above and the tap count (Add photo, pick: two taps).

## Proposals and questions for the PM
1. **`tool_use` id `add_photo`** (new): please confirm.
2. **Replace photo** on image layers: a layer whose file is gone can only be deleted in P2-01, and a user who picked the wrong base photo has Delete plus Add photo (two steps, and the canvas keeps the old photo's shape). A **Replace** tool on the selected-layer bar (the same pick path, keeps the transform, one step "Replace") would fix both, and the missing-photo toast could then say "Replace or delete that layer", as P1-16 did for clips. It is a small addition to this S ticket, not in the owner's approved text. **My recommendation: not now.** Delete plus Add photo covers the swap; if the missing-file state shows up in the P2 test, add Replace then. (It would be a scope change, so it comes back to you and the owner, not to the build.)
3. **One photo per pick.** Adding several at once (the picker allows it) would land each as its own layer, stacked fitted-inside on top of each other, which is rarely what anyone wants without a layout. Panels (P2-02) is where several photos belong. Later, no ticket.
4. **Landing smaller for a second photo** (a reaction face is usually an inset) would be friendlier than fitted inside, but the owner's text says fitted inside the frame, and a pinch is one gesture away. Kept as approved; a Later idea.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| `PickVisualMedia` with `ImageOnly` picks one image without a permission | Guidance (API reference) | [PickVisualMedia](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickVisualMedia), [Android photo picker](https://developer.android.com/training/data-storage/shared/photopicker) |
| Mobile photo and video editors add an overlay photo from a tool and then place it with a pinch and a drag | Assumption (product knowledge of InShot and CapCut; not confirmed from their help pages) | none |
