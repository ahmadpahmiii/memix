# P4-07 desktop renders for the design review

Desktop Compose renders (3x, not a phone) of the real `LookPanel` and `ToolBarRegion` with the real tile pass on a generated picture. Panel scenes sit in the editor's fixed region (229 / 302 / 348 dp at 360x640 / 360x800 / 412x915).

- `bar-main*`, `bar-clip-*`, `bar-overlay-video`: Filters in the main bar and the clip bars (es at 200% too).
- `filters-none|vivid|mixed|clip-*`: Filters tab: no filter, a filter with Intensity, the mixed state (hint, no tile selected, no Intensity), a clip's own panel ("Filters").
- `adjust-*`: Adjust tab; `*-es|id|pt|hi`, `*-200`: languages and 200% font.

Flags for the designer: (1) Filters sits after Mask on clip bars and after Voiceover on the main bar until Stickers, Effects, P4-16 tools exist; (2) at 360x640 the tabs scroll with the content as the spec's pinned rule says; (3) tile pictures are a generated picture here, in the app they are a frame of the clip.
