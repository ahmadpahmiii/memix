# Sticker picker: the Stickers tool and "My stickers" · P2-14

Eighth spec for the photo editor. It reuses, and does not repeat:
- `P2-03-photo-text-and-stickers.md`: the **sticker layer** (35% of the shorter side at scale 1, scale 10% to 500%, row "Sticker", gestures, lock, hide, ratio rule), which is where a picked sticker lands. P2-03 built the layer and no way to add one (owner, 28 Oct, option C); this ticket is that way.
- `P2-07-background-removal.md`: **Save as sticker** writes to the sticker store this picker lists.
- `P2-01-photo-editor-shell.md`: the layer rules, the toasts, the Sheet and Menu patterns.
- `P1-14-drafts.md`: delete with Undo and the long-press Menu (the same patterns).
- `P4-15` (later): catalog packs join this picker as tabs.

## Job
"Put my friend's cut-out on the meme": open Stickers, tap one, it is on the canvas. **Done when** (TICKETS): a cut-out saved as a sticker in P2-07 appears in My stickers and lands on the canvas as a sticker layer in one tap, as one undo step, and is still there after a restart.

**The one blue action:** none on the sheet's list. In the **empty state** the one blue action is **Remove background**. White is selection.

**Fast path, with tap counts:** Stickers (1), a sticker (2): it is on the canvas, selected, and can be pinched at once.

## Entry and exit
- **The Stickers tool** (icon `sticker`, label "Stickers") is in the photo editor's main bar right after Text (final order in P2-05). Always shown (it is not tied to a selection). **Tap:** opens the **Stickers sheet** (Sheet component: rises over the editor with a scrim; the canvas stays visible above it). Taps are ignored while it opens.
- **Closes:** a sticker tap (it lands), the close button, a scrim tap, drag down, system back or Escape. Nothing else changes.
- **Landing a sticker (D4):** a tap on a tile **closes the sheet and puts the sticker on the canvas** as one `StickerLayer`: **centered (0.5, 0.5), scale 1** (35% of the shorter side, P2-03's rule), rotation 0, unlocked, shown, 100%, **at the front of the stack, selected** (frame, handle, the selected-layer bar), as **one undo step "Add sticker"**. No bonk, no haptic; the layer's picture fades in over 120 ms (P2-01). A second sticker lands in the same place (the user moves it; no cascading).
- **Kept:** the layer and **its own copy of the file** (below). Not kept: the sheet.

## Layout
Board: none; Sheet component, `design/system/components/Sheet/README.md`.
- **Sheet:** title "Stickers" (`title`), a close button, height up to **70% of the window** (the grid scrolls inside), `surface`, the sheet's soft shadow (sheets are the only shadow).
- **Tabs row, built for packs:** a horizontally scrolling **Tabs** row (`design/system/components/Tabs/README.md`) under the title lists the sticker sources, **today only "My stickers"**. **With one source the tab row is not shown** (the sheet just reads "Stickers" over the grid); when P4-15 adds catalog packs, each pack is one more tab (its name, a "My stickers" first), with the same grid and tiles, and its own loading and offline states specified by that ticket. Nothing here reserves space for them.
- **The grid:** tiles in a lazy grid, **3 columns at 360 dp** (tiles at least 96 dp wide, equal, `space-2` gaps, `space-4` side padding, so 4 columns on a tablet or wide phone), **newest first**. Each tile is a `surface-raised` square (`radius-md`) showing the sticker's picture **fitted inside with `space-2` padding; transparent parts show the tile (no checkerboard)**. Pressed: `surface-raised` to `hairline`, 120 ms. The tile is the whole touch target (96 dp or more).
- **Thumbnails** come from the scene decoder at about 128 px, one per file, cached while the sheet is open, never the original; a tile not made yet shows the plain tile (no spinner).
- **Empty state** (no stickers yet; the tool is there from the start, so the list must not be a dead end): centered in the sheet, the `sticker` icon (32 dp, `text-secondary`), the title **"No stickers yet"**, the body **"Cut out a person, pet or object from a photo and save it as a sticker. It will show up here."**, and one button:
  - the canvas has an image layer: **"Remove background"** (Primary, the blue): closes the sheet and starts P2-07 on the main photo (as its tool does);
  - no image layer: **"Add photo"** (Secondary): closes the sheet and starts P2-13's pick; no blue.

## Delete (D3)
- **Long-press a tile** (LongPress haptic) opens a **Menu** (`design/system/components/Menu/README.md`) anchored at the finger with one item, **Delete** (label in `text`, as the Drafts menu). TalkBack and Switch Access get the same as an action on every tile.
- **Consequence, no confirmation, Undo instead** (P1-06's rule): the tile leaves the grid at once (200 ms, instant with reduce motion) and a **toast with Undo** shows for 8 s or the user's "time to take action" (as P1-14): **"Sticker deleted"** and **Undo**. Undo puts it back in place. The file is removed from the store when the toast's time ends (or the sheet closes and the toast with it); a second delete replaces the toast as P1-14 says.
- **Stickers already placed keep working:** every placed sticker has **its own copy of the file in the project's folder**, so deleting from My stickers never changes a meme or a draft (said once in the toast's TalkBack text: "Sticker deleted. Memes that use it are not affected.").
- Deleting the last sticker shows the empty state.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Sheet, stickers | Title, the grid, newest first | `stickers_title` |
| Sheet, none yet | The empty state with its one action | `stickers_empty_title`, `stickers_empty_body`, `stickers_empty_action` |
| Landing | The sheet closes; the sticker on the canvas, selected | `a11y_sticker_added` |
| Menu open | Delete | `edit_delete` |
| Deleted, Undo shown | The tile gone; the toast | `stickers_deleted`, `stickers_undo` |
| A tile whose file is missing from the store | The tile shows the `photo` icon in `text-muted` on `surface-raised`; a tap says so; Delete works | `stickers_file_missing` |
| Copy into the project fails (no space) | The sheet stays open; a toast | `stickers_add_failed` |
| An unreadable sticker file | As a missing file | `stickers_file_missing` |
| A draft with a placed sticker after the store sticker is deleted | The sticker still shows (its own copy) | none |
| Many stickers (hundreds) | The grid is lazy; scrolling stays smooth | none |
| Long content (5 languages, 200% font) | The empty state's text and button wrap and the sheet scrolls; the grid has no text | none |
| Offline, permission | Don't apply: everything is on the phone | none |
| First run | The empty state | none |

## Interactions and motion
- **Tap a tile:** lands it (above). **Long-press:** the Menu. **Scroll:** the grid. **Taps while a sticker is being copied** (a few ms to a second for a large file) are ignored; after 300 ms the tile shows a pressed look and nothing else (no sheet, no spinner).
- **Motion:** the sheet rises over 200 ms (reduce motion: 120 ms fade); the layer fades in 120 ms; a deleted tile leaves in 200 ms (instant with reduce motion).
- **Haptics:** LongPress for the Menu; nothing else.
- **Keyboard:** Tab reaches the tiles, Enter lands one, the Menu key or Shift+F10 opens the Delete menu, Escape closes.

## Copy
English source; machine drafts for id, es, pt and hi (until P5). Budgets are characters. Reused: `edit_delete`, `tool_delete`, `layer_name_sticker`, `tool_add_photo`, `tool_remove_bg`.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `tool_stickers` | Stickers | 20 | Tool and sheet title. Small transparent pictures you place on a meme |
| `stickers_title` | Stickers | 20 | Sheet title |
| `stickers_tab_mine` | My stickers | 20 | The one tab today (shown only when packs exist) |
| `stickers_empty_title` | No stickers yet | 30 | |
| `stickers_empty_body` | Cut out a person, pet or object from a photo and save it as a sticker. It will show up here. | 110 | |
| `stickers_empty_action` | Remove background | 24 | Button, same words as the tool `tool_remove_bg` where it fits |
| `stickers_deleted` | Sticker deleted | 24 | Toast |
| `stickers_undo` | Undo | 12 | Toast action (same word as `editor_undo`) |
| `stickers_deleted_a11y` | Sticker deleted. Memes that use it are not affected. | 70 | Spoken with the toast |
| `stickers_file_missing` | This sticker's file is missing. Delete it from the list. | 70 | Toast |
| `stickers_add_failed` | Couldn't add the sticker. Free up space and try again. | 70 | Toast |
| `a11y_sticker_tile` | Sticker %1$d of %2$d | 30 | Spoken name of a tile |
| `a11y_sticker_added` | Sticker added in front | 30 | Announced politely after landing |
| `edit_add_sticker` | Add sticker | 24 | Edit name after "Undo: " |

Translator notes: **"Sticker"** is the same word as the layer name `layer_name_sticker` (a small see-through picture placed on another). **"Cut out"** is the person or thing kept with the background removed (P2-07).

## Accessibility
- **TalkBack:** the sheet's title is the pane title (focus lands there); each tile is a button "Sticker 3 of 12" (the picture is decorative), actions Delete; the tab row, when shown, is a tab group. After landing, "Sticker added in front" is announced and focus goes to the first tool of the selected-layer bar (the P2-13 pattern; a device check). The Undo in the toast is focusable and the toast is a live region (P1-14).
- **Touch targets:** tiles 96 dp or more; the menu item and Undo 48 dp or more; the close button 48 dp.
- **Contrast:** the empty text `text` and `text-secondary` on `surface` (16.9:1, 7.1:1); the empty-state icon `text-secondary` 7.1:1; the Primary button `on-primary` on `primary` 7.9:1. Sticker pictures are content.
- **200% font:** as the States table. **Reduce motion:** see Motion.

## Analytics
`add_sticker` and `delete_sticker` are the PM's ids (5 Nov); they are `tool_use` ids with `editor: "photo"`, once per committed action, no parameter.

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A sticker lands on the canvas (one undo step) | `tool: "add_sticker"` |
| `tool_use` | A sticker is deleted from My stickers (when the toast's time ends and the deletion is final; an Undo fires nothing) | `tool: "delete_sticker"` |

Opening the sheet, a failed copy and a tap on a missing sticker fire nothing. Analytics stay off until consent (P5-01).

## QA compares
Use a store with 2 cut-outs (from P2-07), one with 30, and an empty store.
1. **The sheet** at 360 × 800 and 360 × 640: the title and a 3-column grid of white, black and colored cut-outs on their tiles; the empty state with Remove background, and with Add photo on a text-only canvas.
2. **Landing:** a tap; the sticker centered at 35% of the shorter side, selected, one undo step "Undo: Add sticker"; a second sticker on top.
3. **Delete:** the Menu, the toast with Undo, Undo; a placed sticker still shows after its store entry is deleted.
4. **A missing file** tile; **no space** toast.
5. **Hindi at 200% font**, 360 × 640: the empty state and the sheet.

**Measured: the Done when** (PM, 5 Nov; the designer adds):
- Save two cut-outs in P2-07, open Stickers: both are there, newest first; tap one: one sticker layer, at scale 1, centered, front, selected; **one Undo** removes it; kill the process and reopen the draft: it is there; **duplicate the draft and delete the sticker from My stickers: both drafts still show it** (own copy; `mediaCopyPaths()` covers it).
- The placed sticker carries its measured `pixelSize` (an unknown size would draw as a square): a 3:1 sticker lands 3:1.
- **Speed and memory:** a store of 200 stickers scrolls smoothly (no visible lag; thumbnails at 128 px, never the originals); LeakCanary, 5 cycles of open the sheet, scroll, land, delete, close.
- **Old drafts and the store:** a fresh install has an empty store and an empty state.

## Model
| Need | Model |
| --- | --- |
| The store | **Device-wide**, not per project: the sticker files plus an index (id, file, `pixelSize`, added time), shared by P2-07 (writes), P2-14 (lists, deletes) and P2-09 (WhatsApp packs). If a SQLDelight table holds the index it needs a `.sqm` migration and `verifySqlDelightMigration` run locally |
| A placed sticker | A `StickerLayer` whose `source` is **a copy of the file in the project's folder** (media copies are never changed in place), with its `pixelSize`; `mediaCopyPaths()` already covers sticker layers. No schema bump: the layer kind exists (P2-03) |
| Delete | Removes the index entry and the store file only; never a project's copy |

## Requests to the principal mobile engineer
I don't edit code, so these are requests; the PM's must-knows (5 Nov) carry the rest.
1. **Sticker store** use cases in `:core:domain` (list, add from P2-07, delete with an undo window, copy into a project) behind a repository interface; features call use cases only.
2. **Placing copies the file into the project's folder with its `pixelSize`** and commits `addStickerLayer` as one undo step; use P2-03's layer rule and P2-13's landing pattern.
3. **The sheet and its Menu and toast** from the Sheet, Menu and Toast components; thumbnails from the scene decoder at about 128 px, cached for the sheet's life; a lazy grid.
4. **The tab row** is built as a list of sources with one entry today, hidden when there is one source, so P4-15 adds a source without a layout change.
5. **New icon `sticker`** (DESIGN_SYSTEM); the main-bar tool in the final order; the EmptyState button actions call P2-07's and P2-13's entries.
6. **Analytics:** `EditorTool` values `add_sticker` and `delete_sticker`.
7. **Hand checks:** the PM's list (save two cut-outs, place one, delete the other, restart, duplicate the draft, LeakCanary).

## Proposals and questions
**For the PM**
1. **Confirm** the ids and that `delete_sticker` fires when the deletion becomes final (an Undo fires nothing).
2. **The tab row is hidden with one source** (so the sheet is not a one-tab tab bar today).
3. **Stickers sit in the main bar after Text** whether or not any exist (the empty state is the way to the first one).

**For the owner** (delegated; recommendation applied; the PM logs it for review)
- **1. Delete with Undo, or ask first?** *Considerations:* **A. Delete at once with Undo** (applied, recommended: the app's rule; a sticker is easy to remake and placed copies are safe); **B. Ask "Delete this sticker?":** one more tap every time. **Applied: A.**
- **2. A sticker lands in the center, one tap, no resizing step?** *Considerations:* **A. Center at 35% of the shorter side** (applied, recommended: one tap, a pinch away from final); **B. A placing step (drag first):** slower. **Applied: A.**

**Later, no ticket:** rename or reorder stickers; sharing a sticker; a sticker's own look settings; catalog packs (P4-15).

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Undo beats confirmation for reversible actions | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| A list that is empty at first must offer the way to fill it, not a dead end | Guidance (expert) | [NN/g, Empty states](https://www.nngroup.com/articles/empty-state-interface-design/) |
| A sticker copy inside the project keeps drafts safe when the store changes | Reasoned from the media rules (copies are never changed in place) | `docs/TECHNICAL_DESIGN.md` → media copies |
