# Bundled fonts

Every font in `../src/commonMain/composeResources/font/` is SIL Open Font License 1.1. Each license file here is the
font project's own `OFL.txt`, copied unchanged.

| File | Font | Source | License |
| --- | --- | --- | --- |
| `anton_regular.ttf` | Anton Regular | [googlefonts/AntonFont](https://github.com/googlefonts/AntonFont) (P0-04; the commit wasn't recorded) | `Anton-OFL.txt` |
| `space_grotesk_regular.ttf`, `space_grotesk_medium.ttf`, `space_grotesk_bold.ttf` | Space Grotesk | [floriankarsten/space-grotesk](https://github.com/floriankarsten/space-grotesk) (P0-04; the commit wasn't recorded) | `SpaceGrotesk-OFL.txt` |
| `space_mono_regular.ttf` | Space Mono Regular | [googlefonts/spacemono](https://github.com/googlefonts/spacemono) (P0-04; the commit wasn't recorded) | `SpaceMono-OFL.txt` |
| `teko_bold.ttf` | Teko Bold 2.000 (Indian Type Foundry), Latin and Devanagari, no Reserved Font Name | `fonts/ttf/Teko-Bold.ttf` from [googlefonts/teko](https://github.com/googlefonts/teko) at commit `2bf909d46b0061a5e3e16e8acc4fef670e36a8f2` (the repository has no tags), fetched 9 Oct 2026 for P1-17. 257,136 bytes, SHA-256 `d4a64ecf1e27b570845a4c8024d9ac2bcb24421b85bf83858a934dbd5adc778d`. | `Teko-OFL.txt` (`OFL.txt` at the same commit) |

To add a font: take it from the project's own repository, add its `OFL.txt` here as `<Family>-OFL.txt`, and add a row
with the commit and SHA-256.
