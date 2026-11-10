# P2-07 renders (design review material)

Desktop render of the real composables (`ImageComposeScene`, 3x density, the repo's tokens, strings and fonts) with a stand-in `Segmenter` mask (a head and a body ellipse with a soft edge, 1024 x 768) over the generated 4:3 test picture. **Not a phone**: text is the real UI font, but nothing here shows ML Kit's real masks, Android's blur or touch feel. The preview picture is the stand-in "4000 x 3000" gradient, so the white lettering you see is part of the picture, not of the screen.

| Frame | What it shows |
| --- | --- |
| `97-cutout-ready.png`, `-360x640` | The cut-out screen, Ready: checkerboard, "Background removed", Erase / Restore, five sizes, Edge softness 30%, Save as sticker, Apply, privacy line |
| `97-cutout-erase-strokes.png` | A saved cut-out reopened with two refine strokes (one erase, one restore); the "Remove cut-out" Quiet button shows |
| `97-cutout-restore-selected.png` | Restore selected (a tap on the segment) |
| `97-cutout-live-erase.png` | A finger mid-stroke in Erase: the erased part shows at once |
| `97-cutout-softness-0.png`, `-softness-100.png` | Edge softness 0% and 100% |
| `97-cutout-saved.png` | Save as sticker done: "Saved to My stickers" on the stage |
| `97-card-preparing.png`, `97-card-preparing-taking-longer.png` | Preparing with progress (40%), and after 30 s |
| `97-card-working.png` | Working: the line "Removing the background..." after 300 ms |
| `97-card-nothing-found.png`, `97-card-cant-remove.png`, `97-card-offline.png`, `97-card-memory.png` | The other status cards, each with Try again |
| `97-*-spanish-200.png`, `97-*-hindi-200.png` | Ready, Offline and Preparing-taking-longer in es and hi at 200% font, 360 x 640 |
| `97-editor-cutout-selected.png`, `-360x640` | The editor with an applied cut-out layer selected (bar: Position, Opacity, Delete, Edit photo, Crop, ...) |
| `97-layers-panel-cutout-row.png` | The Layers row: "Photo / Cut out" |
| `97-main-bar-remove-bg.png` | Main bar with Remove BG (scroll to see it) |

## After the design review (7 Nov, re-rendered)
- M1: plain apostrophes in all five languages (`We couldn't find...`).
- M2: a status card scrolls inside the stage and dims the picture behind it with `scrim` (`97-card-*`, `97-*-spanish-200`, `97-*-hindi-200`; at 200% the card is taller than the stage and scrolls to Try again).
- M3: Nothing found keeps the brush live. A cut-out with no mask (`maskPath` null) means everything is kept and only the strokes cut; the card is a small one at the top and the picture stays whole under it (`97-card-nothing-found.png`, `-erased`, `-spanish-200`). Apply and Save as sticker work once one stroke exists.
- P2: the tools scroll; Save as sticker, Apply and the privacy line are pinned at the bottom (`97-cutout-erase-strokes.png`).
- P3: `ic_cutout` has a solid head and the shoulders at y 15 (`97-icon-sheet.png`, at 24, 48 and 96 dp next to Crop, Censor and Draw).
- Apply and Save as sticker while inert (Preparing, Working, Nothing found with no stroke) look the same as when live: the `Button` component has no disabled look, so they simply do nothing. Left as is; a disabled variant would be a design-system change for the designer.
- The Layers row thumbnail now shows the cut-out (code review fix; `97-layers-panel-cutout-row.png`).
- The notice pill announces politely and "Background removed" returns when it clears (3 s); not visible in a still.
