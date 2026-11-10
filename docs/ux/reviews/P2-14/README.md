# P2-14 renders (design review material)

Desktop render of the real composables (`ImageComposeScene`, 3x density, the repo's tokens, strings and fonts); the sticker pictures are the generated stand-ins (a square, a 3:1 wide, a 1:3 tall and a tiny one). **Not a phone**: no touch feel, no real thumbnails decode.

| Frame | What it shows |
| --- | --- |
| `98-stickers-sheet.png`, `-360x640` | The Stickers sheet: title, three columns at 360 dp, newest first |
| `98-stickers-empty-with-photo.png` | Empty state with a photo on the canvas: Remove background (the blue one) |
| `98-stickers-empty-no-photo.png` | Empty state on a text-only canvas: Add photo (Secondary) |
| `98-stickers-menu.png` | Long press on a tile: the Delete menu |
| `98-stickers-deleted-undo.png` | A tile deleted: "Sticker deleted" with Undo |
| `98-stickers-missing-tile.png` | A tile whose file is missing (the photo icon) and the "file is missing" toast |
| `98-stickers-add-failed.png` | The no-space toast; the sheet stays open |
| `98-stickers-sheet-spanish-200.png`, `98-stickers-sheet-hindi-200.png`, `98-stickers-empty-*-200.png` | es and hi at 200% font, 360 x 640 |
| `98-sticker-landed.png` | A landed sticker (3:1), selected, at 35% of the shorter side |
| `98-main-bar-stickers.png` | The main bar: Formats, Text, **Stickers**, Add photo, Draw, ... |

Notes for the review: the tab row is not built into a frame because it is hidden while there is one source (a list with one entry, `StickersSheet.kt`); the tiles' pressed look and the 300 ms busy look are not visible in stills.
