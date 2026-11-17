# P4-06a desktop renders for the design review

Desktop Compose renders (3x, not a phone) of the real `EffectsSheet`, `EffectPanel` and `ToolBarRegion`, tiles made by the real `EffectTiles` pass (the CPU twin) from a frame of the effects-check scene. Panel scenes sit in the editor's fixed region (229 / 302 / 348 dp at 360x640 / 360x800 / 412x915).

- `bar-main*`, `bar-effect*`: Effects in the main bar; the effect item's bar (Intensity, Delete, Change effect, Split, Move here, Duplicate), es and hi at 200%.
- `sheet-motion|light|color-*`: the three built tabs (no Glitch, Blur, Retro tab); `sheet-change`: replace mode, current tile (Shake) not yet ringed here if the ring is thin; `sheet-not-made`: plain squares before tiles arrive; `*-es-200`, `*-hi-200`, `-id|pt|hi`: languages and 200% font.
- `panel-*`: Intensity at 70%, 0%, 100%, three window sizes, languages, 200%.

Design review fixes applied after these renders: Shake centered, Sun leak out and back (its tile is a top-aligned crop at 1667 ms), Indonesian Glow is "Pendar", icon arcs 3.5 px apart, a Flash-rate guard. **All 26 renders were redone on 17 Nov with P4-06b's build** (after those fixes: the Shake and Sun leak tiles, the icon; the Motion/Light/Color sheets now show all six tabs because 06b built Glitch, Blur and Retro, the tab row scrolls). `sheet-*` here therefore differ from the first set only in the tabs row and the fixed tiles. Flags: (1) none left on Shake; (2) none left on Sun leak; (3) Spotlight's feather is centered on the radius; (4) Deep-fry's name is its own string (same words as the photo editor); (5) the lane item and landing are not rendered here (Timeline needs the controller); (6) the sticker lane in `effects-check` draws from P4-12; (7) references are 540x960 from the CPU twin.
