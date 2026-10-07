---
name: design-tokens
description: Use and govern the Memix v2 design tokens — reading them in Compose through MemixTheme, who may change them (the ux-designer), how a new token is added, and how to audit code for hardcoded values or the rejected v1 colors. Use when building, reviewing or redesigning any Memix screen or component.
---

# Design tokens

Source: `design/tokens.json` (machine-readable) and `docs/DESIGN_SYSTEM.md` (rules + Compose names). Look: **neutral chrome, one blue**.

## Use (engineers)

- Read tokens through `MemixTheme`: `MemixTheme.colors.primary`, `MemixTheme.type.bodyStrong`, `MemixTheme.spacing.space4`, `MemixTheme.shapes.radiusLg`, `MemixTheme.size.touchTarget`.
- Names: kebab-case token → camelCase property (`track-meme-sound` → `trackMemeSound`). px → dp, font px → sp.
- Roles: canvas/surface/surface-raised/hairline for chrome; `stage` behind previews; `primary` (#2BB3F3) for the one main action, Create, the active tool (on `primarySubtle`), links, progress and meme sounds; `selection` (white) for selected clip/layer, handles, playhead, selected chip; track colors only on the timeline and layers panel.
- No borders around shapes, no offset/hard shadows, no gradients (the slider's two-tone fill is the only hard-stop exception). Sheets use `shadowSheet`, menus/toasts `shadowFloat`.

## Change or add a token (designer owns it)

1. The **ux-designer** decides: adds or changes it in `design/tokens.json` with a `usage` sentence; for colors checks contrast (≥ 4.5:1 text, ≥ 3:1 UI parts) and states the ratio.
2. The designer updates `docs/DESIGN_SYSTEM.md` and logs it in `docs/ux/CHANGELOG.md` (marked "sync to design canvas").
3. The **principal mobile engineer** adds the Kotlin property to the token object in `:core:designsystem` and its value in `MemixTheme`.
Engineers never invent a token or hardcode a value instead.

## Audit

```bash
git diff main...HEAD -- '*.kt' | grep -nE 'Color\(0x|\b[0-9]+\.dp\b|[0-9]+\.sp\b|FontFamily\(|RoundedCornerShape\([0-9]'
grep -rniE 'FFE14D|FF4FA3|0E0E11' --include='*.kt' .
```
Any hit outside `:core:designsystem` is a bug unless it's layout math (e.g. a computed timeline offset). The v1 values must never appear.
