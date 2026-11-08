# P2-08 photo export references

Made by `PhotoSceneRenderer` (`:engine:photo` commonMain), the code the phone's exporter runs, in the desktop render harness. **Stand-ins, not phone files:** the harness decodes generated pictures, draws text with Skia and the repo's font files (not Android's StaticLayout), draws the "Memix" mark with a Skia stand-in that places its ink box by the same `WatermarkCatalog` rule, and encodes with Skia (Android's `Bitmap.compress` writes the real files). Replace them with phone files at QA (spec P2-08 → QA compares, "The reference frames").

- `export-everything-s1080.png`, `export-everything-s2160.png`: the scene `everything` (a Classic meme format, a 4:3 photo, a sticker, a drawing, a censor and two texts) with the mark, PNG, shorter side exactly 1080 and 2160 px (canvas 4:3, so 1440 x 1080 and 2880 x 2160).
- `export-everything-s1080-q90.jpg`, `export-everything-s2160-q90.jpg`: the same as JPG at the sheet's default quality 90. `-q50.jpg` is the lowest quality the slider offers.
- `mark-only-portrait-s1080.png`, `mark-only-portrait-s2160.png`: the mark alone on a black 9:16 canvas, for the 3% margin check below.

## Checked in the harness (desktop JVM, 7 Nov 2026)

- **One render path:** for five scenes (`everything`, `filtered-edit` with crop, turn, straighten, adjustments, a filter and deep-fry, `cutout` with softness, `panels`, `tall-9x16`) at both sizes, the exporter's pixels equal the old render-hook path's pixels: **0 differing pixels in all 10 comparisons**. This is the desktop half of the deferred photo checks of P2-01, P2-02, P2-03, P2-04, P2-05, P2-07 (the phone half is the same comparison with a real file).
- **Size:** 1440 x 1080 and 2880 x 2160 (shorter side exactly 1080 and 2160), 1080 x 1920 and 2160 x 3840 for 9:16 (`identify`).
- **No metadata:** the JPGs read back with no EXIF (Pillow `getexif()` is empty).
- **The mark:** the ink box starts at 33 px from the left and 32 px from the top at 1080 (3% = 32.4 px) and 65 and 65 at 2160 (64.8 px), in portrait, landscape and square canvases: within the spec's plus or minus 2 px.
- **File size:** this scene gives 0.12 MB per megapixel at JPG 90 and 0.17 at PNG, far under the estimate table's 0.65 and 2.2 for photos: a meme with flat areas compresses better than a camera photo. The estimate says "About".
