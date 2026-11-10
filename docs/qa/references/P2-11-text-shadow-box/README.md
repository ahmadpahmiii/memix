# P2-11 text shadow and box references

Renders of the shared `drawPhotoScene` with a desktop **stand-in** for the caption renderer (Skia, the repo's font files and the P2-11 rules from `CaptionCatalog`: box, then the shadow group at 60%, outline, fill). Not Android's `StaticLayout`/`BlurMaskFilter`: glyph shapes, line breaks, Devanagari and emoji are Android's and are judged on a device. Latin-only stand-ins for the seven captions of `photo-text-shadow-box` / `text-shadow-check`.

- `shadow-box-{white,black,photo}-s{1080,2160}.png`: the seven captions on a white canvas, a black canvas (black boxes vanish there, as they should) and a picture; the 2160 frame is the 1080 frame scaled by 2.
- `step-*-s1080.png`: one caption with no effect, each shadow softness, each box corner step, and shadow plus box.
- `opacity-group-40-s1080.png`: a boxed, shadowed caption at 40%: no overlap darkening between box, shadow and text.

QA on a device: `--es memix.openEditor photo-text-shadow-box` and the video `text-shadow-check`, render with `--es memix.renderScene photo-text-shadow-box --ei memix.renderWidth 1080` and compare.
