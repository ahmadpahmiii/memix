# P4-06b desktop renders for the design review

Desktop Compose renders (3x, not a phone) of the real `EffectsSheet` and `EffectPanel`, tiles made by the real `EffectTiles` pass (the CPU twins) from a frame of the effects-check scene. Same method as `docs/ux/reviews/P4-06a/` (whose renders were redone with this build).

- `sheet-glitch|blur|retro-360x800`: the three new tabs, five tiles each; `sheet-glitch-360x640`, `sheet-retro-412x915`: other window sizes; `sheet-change-retro-360x800`: replace mode with VHS as the current effect; `sheet-not-made-glitch-360x800`: plain squares before the tiles arrive.
- `sheet-glitch-es`, `sheet-blur-pt`, `sheet-retro-id`, `sheet-blur-hi`: languages at 100%; `sheet-blur-es-200`, `sheet-retro-es-200`, `sheet-glitch-hi-200`, `sheet-retro-hi-200`: Spanish and Hindi at 200% font (names wrap to three lines, the grid scrolls).
- `panel-vhs`, `panel-tilt-blur-es-200`, `panel-old-screen-hi-200-360x640`: the Intensity panel with the new names and descriptions (the panel is title, slider, one line; the panel scrolls at 200% at 360x640).
- Tile rules: Glitch's tile is a burst's middle (800 ms), Tear's band is at half height (600 ms), the others at their reference time; the Sun leak tile is the top crop (06a).
- The six-tab row scrolls sideways (Retro is partly off-screen at 360 dp); the selected tab keeps its white underline.
- Flags for the designer: see the engineer's report (Pixelate fade-in, Tear sine period, Old screen vignette strength, flash-guard scope for Static and Old screen).
