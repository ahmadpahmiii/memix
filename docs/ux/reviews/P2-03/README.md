# P2-03 screenshots

Desktop renders (Compose `ImageComposeScene`, 3x density, generated pictures; no emulator was available), not phone frames. 360 x 800 dp unless the name says 640. **The text in these frames is drawn by a stand-in** (Skia with the repo's real caption font files, laid out with the caption rules: 8% of the shorter side, wrap at 90%, outline under fill), not by Android's `StaticLayout` renderer. Line breaks can differ by a word, and Devanagari and emoji have no fallback font there (so no Hindi sample words are used). The chrome is real: bars, panels, frames, handles, rows, copy in all five languages. Judge the text itself, and the keyboard refit, on a phone (hand checks, P2-03).

- 40-main-bar-text: the main bar, Text first, then Add photo, then Layers
- 40-blank-start-empty-card / 40-after-delete-empty-card / 40-ratio-only-empty-card: the two bodies of the empty-canvas card (Blank start; after a layer was deleted; a blank canvas with only a ratio change, which keeps the Blank body). The tool names in the bodies use no-break spaces. The card is `surface-raised` (designer decision, 2 Nov), drawn on the stage, so it reads over both the white canvas and the black stage; 40-blank-start-empty-card-black-canvas shows a black canvas
- 40-text-selected-bar (and 360x640): selected text, the bar Edit text, Style, Position, Delete, Opacity, ...
- 40-text-selected-second-on-top, 40-text-locked-selected, 40-text-hidden-selected
- 40-typing-new / 40-typing-edit (and 360x640): the entry bar in place of the tool bar; Undo, Redo and the ratio pill dimmed. The desktop has no keyboard, so the stage does not shrink here
- 40-style-panel-360x800 / 360x640, 40-position-panel-text, 40-opacity-panel-text
- 40-layers-panel-text-and-stickers (+ 360x640, -scrolled), 40-layers-panel-sticker-rows-top (+ 360x640): text rows (first line as name, the Text icon on a surface tile), sticker rows (the picture; File missing; Locked), the faded and hidden rows
- 40-stickers-scene, 40-sticker-selected-bar, 40-sticker-locked-selected, 40-sticker-tiny-selected, 40-sticker-missing-toast
- 40-text-added-toast
- 40-hindi-200-* and 40-spanish-200-*: main bar, selected-text bar, typing, Style panel, Layers panel, empty card at 200% font on 360 x 640

Not rendered (needs a device): the real caption rendering, typing with the keyboard and the stage refit, the haptic and gestures, TalkBack, the Create sheet's four rows (`:composeApp`, not in the render harness), the video editor after the lift.
