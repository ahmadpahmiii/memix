# P2-03 text and sticker references

Renders of the shared `drawPhotoScene` at 1080 px wide, with a desktop **stand-in** for the caption renderer (Skia and the repo's font files, the caption geometry rules; not Android's `StaticLayout`), generated pictures and stickers. They show the layout the scene must produce; the exact glyph shapes, line breaks and fallback fonts (Devanagari, emoji) are Android's and are not in these images. iOS parity (P7-04) compares against the device captures QA makes, listed below.

- `photo-text-check-1080.png`: the six captions of the video editor's `text-check` on a 9:16 photo canvas (the `photo-text-check` scene: Classic, Modern at 50% and -15 degrees, Serif under the red OVERLAP, Handwritten, Bubbly at 250% and 30 degrees partly off the right edge; Latin words only here). On a phone the scene has Hindi and emoji too.
- `photo-text-faded-opacity-group-1080.png`: a text layer at 40% opacity; its outline must not show through its fill (the opacity group).
- `photo-stickers-1080.png`: the `photo-stickers` scene: a square sticker, a locked 3:1, a hidden 1:3 (not drawn), a tiny one scaled up, one at 150% and 50% opacity, and one whose file is gone (not drawn).

QA on a device: open `photo-text-check`, render it (`--es memix.renderScene photo-text-check --ei memix.renderWidth 1080`) and compare with these frames and with the video editor's `text-check` export frame: the same words, size, outline and place.
