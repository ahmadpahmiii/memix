# Design review · P4-01b overlay layers

Reviewer: ux-designer, 17 Nov 2026. Against `docs/ux/specs/P4-01b-overlay-layers.md`. Inputs: 18 renders (harness renders, not emulator captures; the phone checks stay on the end-of-build list), the strings in five languages, `ic_overlay.xml`, the handoff.

**Verdict: CHANGES requested (copy only, three strings). Everything else approved; the deferrals below are accepted.**

## Must-fix
1. `overlay_skipped_toast` (es, pt) breaks the spec's translator note "same word everywhere" (tool is "Superposición" / "Sobreposição", the toast says "superpuestos" / "sobrepostos"). Replace:
   - es: `Algunas superposiciones de video no se mostrarán: este teléfono no puede reproducir tantas a la vez.`
   - pt: `Algumas sobreposições de vídeo não aparecerão: este celular não consegue reproduzir tantas ao mesmo tempo.`
   - Keep en `Some overlay videos won't show: this phone can't play that many at once.`, id `Beberapa video overlay tidak tampil: ponsel ini tidak bisa memutar sebanyak itu sekaligus.`, hi `कुछ ओवरले वीडियो नहीं दिखेंगे: यह फ़ोन इतने वीडियो एक साथ नहीं चला सकता।` (id/hi unchanged from the build). The claim is true for preview and export, as the builder's code does.
2. `a11y_overlay_opacity` hi says "प्रतिशत पारदर्शिता" = percent transparency, the inverse of what the slider sets (10% opacity would be spoken as nearly opaque). Replace hi with `%1$d प्रतिशत अपारदर्शिता`. en, id, es, pt are right and stay (`%1$d percent opacity`, `%1$d persen opasitas`, `%1$d por ciento de opacidad`, `%1$d por cento de opacidade`; note pt-BR/pt-PT: "por cento" is fine in both).
   - Logged for the P5 translator pass: the shared `layers_opacity` / `tool_layer_opacity` hi label "पारदर्शिता" (P2-13) has the same inversion; "अपारदर्शिता" is the exact term. Not in this ticket's diff.

## Answers to the builder
1. **Two new strings:** `a11y_overlay_opacity` en/id/es/pt/hi as above. It is spoken only (not a visible toast): correct per spec. `overlay_skipped_toast`: confirmed with the replacements above. This also replaces the spec's "more than five: softer preview, no message"; spec States row amended (P4-01a's admission leaves overlays out, and saying so is the cheaper surprise than a missing overlay with no reason).
2. **Duplicate offset:** amended. The copy starts at the original's end, so it can never overlap it; the offset rule is dropped from the spec and from `overlay.duplicateOffsetOfShortSide` in both `tokens.json` files. Engineer: delete the unreachable branch and `OverlayCatalog.DUPLICATE_OFFSET_OF_SHORT_SIDE` (optional cleanup, no behavior change); update `engineer-hand-checks.md` line 115.
3. **Bubble on pure move:** OK. It is P1-10's shared behavior; a bubble that shows "50% · 0°" while only moving is noise. Spec States row clarified.
4. **Not built:**
   - Pinned-row lane slide: **defer.** Polish only (reduce motion already means "at once"); the pill still pins correctly. Note it on the end-of-build list; QA looks for a jump at the moment the lanes first overflow.
   - Scroll-into-view after reorder: **defer, but must be fixed before the P4 phase gate.** With 1 / 3 / 4 whole lanes, the selected clip can end up off-screen after Bring forward / Send back with 5 lanes, with no cue. Rare in normal projects (two or three overlays); the TalkBack announcement ("Layer 2 of 3") still tells the user what happened.
   - Dropping a meme sound on a new lane while the row is pinned: **defer.** An edge of an edge (five or more overlay lanes plus a sound drag); no dead end, the sound lands on the nearest valid lane. Add to the end-of-build QA list.

## Checks that passed
- Icon: stroked frame and solid core match the spec geometry (frame outer 2–22 × 4–20, core 10–18 × 10–16, 2 px clear); reads as picture-in-picture, not `duplicate`, `fit` or `formats`.
- Main bar: Overlay fifth, tool look `text-secondary`; es at 200% wraps and the bar grows, nothing cut (the existing "Sonidos de me…" truncation is P3's, not new).
- Overlay bar: Position, Opacity, Delete (`danger`, with its word), Split, Volume in order; missing-file bar has Delete first.
- Stage: white frame, near-black edge, handle whole on the stage; timeline lane: 28 dp thumbnails on `track-video`, white outline and handle when selected, no label; pinned pill clear of the lanes.
- Layer order: buttons first, hint below; pt wraps the hint to two lines, buttons stay in view. Position panel hi at 200%: rows wrap under their labels, scrolls.
- Tokens only; no blue except the meme-sound pill; v1 palette absent.

## Optional notes
- The render captures are harness output: confirm on the owner's phone that the Hindi Position title "जगह" and Overlay tool label stay on one line at 200%.
- Spec edits made: Duplicate rule, two States rows. Tokens: `overlay.duplicateOffsetOfShortSide` removed (both files). Log: `docs/ux/CHANGELOG.md` entry to be added with the next token pass, "sync to design canvas" (nothing visual changed).
