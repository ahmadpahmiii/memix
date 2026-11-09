# P2-10 renders (design review material)

Desktop render of the real composables (`ImageComposeScene`, 3x density, the repo's tokens, strings and fonts); the pictures are generated stand-ins and the caption renderer is a Skia stand-in (not Android's `StaticLayout`). **Not a phone**: no touch feel, no push, the video editor's preview stage is empty (it needs a player).

| Frame | What it shows |
| --- | --- |
| `01-default-4x3-360x640`, `-360x800`, `01-default-9x16-*` | The Make it a video meme row under the stage, the stage refitted (a 4:3 and a 9:16 canvas) |
| `02-layer-selected-360x640` | A text selected: the row stays |
| `03-layers-panel-row-collapsed-360x640`, `03-style-panel-row-collapsed-360x640` | A panel open: the row is collapsed |
| `04-working-360x640` | "Making video..." (busy button; the editor is inert, not visible in a still) |
| `05-explain-empty-*`, `05-explain-busy-*` | The two toasts on tap (P2-08's words) |
| `06-fail-*`, `06-fail-space-*` | The two failure toasts |
| `07-*` | Hindi and Spanish and Portuguese at 200% font, Indonesian and Hindi at 100%, and Hindi 200% while working |
| `08-empty-canvas-360x640` | An empty canvas: the row keeps its look (a tap explains) |
| `video-arrival-sounds-sheet-360x640` | The video editor on arrival with the Meme sounds sheet open |
| `video-arrival-note-360x640`, `video-note-*` | After the sheet closes, the first time: the persistent note with Got it (en, es 200%, hi 200%, pt, id) |
| `video-arrival-after-sheet-no-note-360x640` | The second time: no note |

Not drawn: the video editor's first frame (see `docs/qa/references/P2-10-make-video-meme/` for the photo render against the baked picture plus the lifted texts), the push and the sheet's rise.
