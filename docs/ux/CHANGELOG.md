# Design changelog

Design-system and token changes made in the repo. Items marked **sync to design canvas** still need copying back to the claude.ai artifacts (design system + app screens).

| Date | Change | Contrast / notes | Sync to design canvas |
| --- | --- | --- | --- |
| 2026-10-07 | v2 palette "neutral chrome, one blue" | All text ≥ 4.5:1 | Done |
| 2026-10-07 | Icons: Memix-drawn set replaces the proposed Lucide (owner's decision). Grammar: 24px grid, 2px flat-cap strokes, mitred joins, one solid core. | Icons always come with a visible or screen-reader label. | Sync to design canvas |
| 2026-10-07 | Dials recorded: ENERGY 2 / RHYTHM 2 / MOTION 2. | | Sync to design canvas |
| 2026-10-07 | New tokens: motion durations (`duration-press`, `duration-sheet`, `duration-screen`, `duration-bonk`), values taken from the existing Motion rules. | | Sync to design canvas |
| 2026-10-07 | New component spec: Toggle. | Off thumb 4.7:1 on its track; on thumb 7.9:1 on primary. | Sync to design canvas |
| 2026-10-07 | TemplateCard drops the use count (no real usage data exists; antislop R-17). Create sheet drops "Memix asks for gallery access…" (the Android photo picker needs no permission). Home's region becomes a label until P3-11, and the trending rows wait for the catalog. | | Sync to design canvas |
| 2026-10-07 | New token `danger-pressed` `#E04E43` (the danger fill had no pressed state). | on-primary text 4.80:1 | Sync to design canvas |
| 2026-10-07 | Home (P0 only): hero and entry cards centered vertically, entry cards at the 4:5 canvas ratio until the trending rows exist. | | Sync to design canvas |
| 2026-10-07 | New token `rail-height` (4px): the thin rail under a fill, shared by Slider (its 4px was a private constant) and the new ProgressBar. New component spec: ProgressBar (determinate only). Sheet: blocking variant and long-content rule. DESIGN_SYSTEM States: progress rule. Kotlin `MemixSize.railHeight` handed to the principal engineer in the P1-02 spec. | Fill `primary` 7.3:1 on surface, 5.9:1 on surface-raised, 4.9:1 against the `hairline` rail (UI parts need 3:1 or more). | Sync to design canvas |
| 2026-10-07 | Create sheet (P1-02): Video meme row body "Pick clips from your gallery" → "Pick videos and photos". | | Sync to design canvas |
