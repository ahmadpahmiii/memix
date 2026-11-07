# P2-02 format references

Twelve frames: Classic, Caption bar, Demotivational, Panels (a 2 x 2 grid, three cells filled, one empty: it draws the background), Panels in 3 columns at even dividers (`photo-format-panels-cols3-even`: three equal cells) and Bubbles (one of each shape, the thought bubble with its dot chain), each at **1080 and 2160 px on the canvas's shorter side** (`-s1080`: 1440 x 1080, `-s2160`: 2880 x 2160), drawn by the shared `drawPhotoScene` with the format's parts. The 2160 frame must equal the 1080 frame scaled by 2 (every part is a share of S).

**Desktop renders with a text stand-in** (Skia with the repo's caption fonts, not Android's renderer: line breaks and glyphs on a phone can differ; Devanagari and emoji aren't drawn) and generated pictures. They show the format geometry. On a device, render the same scenes with the debug hook and compare:

```bash
for s in classic caption-bar demotivational panels bubbles; do
  adb shell am start -S -n app.memix/.android.MainActivity --es memix.openEditor photo-format-$s
  adb shell am start -S -n app.memix/.android.MainActivity --es memix.renderScene photo-format-$s --ei memix.renderShortSide 1080
  adb shell am start -S -n app.memix/.android.MainActivity --es memix.renderScene photo-format-$s --ei memix.renderShortSide 2160
done
adb exec-out run-as app.memix cat cache/scene-renders/photo-format-classic-s1080.png > classic-1080.png   # and the rest
```
Final check with P2-08's exporter at QA: the files open, are exactly 1080 and 2160 on the shorter side, and match these frames.
