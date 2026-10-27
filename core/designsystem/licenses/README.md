# Bundled fonts

Every font in `../src/commonMain/composeResources/font/` is SIL Open Font License 1.1. Each license file here is the
font project's own `OFL.txt`, copied unchanged.

| File | Font | Source | License |
| --- | --- | --- | --- |
| `anton_regular.ttf` | Anton Regular | [googlefonts/AntonFont](https://github.com/googlefonts/AntonFont) (P0-04; the commit wasn't recorded) | `Anton-OFL.txt` |
| `space_grotesk_regular.ttf`, `space_grotesk_medium.ttf`, `space_grotesk_bold.ttf` | Space Grotesk | [floriankarsten/space-grotesk](https://github.com/floriankarsten/space-grotesk) (P0-04; the commit wasn't recorded) | `SpaceGrotesk-OFL.txt` |
| `space_mono_regular.ttf` | Space Mono Regular | [googlefonts/spacemono](https://github.com/googlefonts/spacemono) (P0-04; the commit wasn't recorded) | `SpaceMono-OFL.txt` |
| `teko_bold.ttf` | Teko Bold 2.000 (Indian Type Foundry), Latin and Devanagari, no Reserved Font Name | `fonts/ttf/Teko-Bold.ttf` from [googlefonts/teko](https://github.com/googlefonts/teko) at commit `2bf909d46b0061a5e3e16e8acc4fef670e36a8f2` (the repository has no tags), fetched 9 Oct 2026 for P1-17. 257,136 bytes, SHA-256 `d4a64ecf1e27b570845a4c8024d9ac2bcb24421b85bf83858a934dbd5adc778d`. | `Teko-OFL.txt` (`OFL.txt` at the same commit) |
| `poppins_bold.ttf` | Poppins Bold (Indian Type Foundry), Latin and Devanagari, no Reserved Font Name; caption font `poppins` (P1-10) | `ofl/poppins/Poppins-Bold.ttf` from [google/fonts](https://github.com/google/fonts/tree/main/ofl/poppins), `main` at `47d54ca1ebb14fd864695f1d39dc734f39017c33`, fetched 20 Oct 2026. 155,996 bytes, SHA-256 `983676516167748b74de6f4771fb384c664fd913acb8b471122ecacf5da5ea6c`. | `Poppins-OFL.txt` (`ofl/poppins/OFL.txt`, same commit) |
| `baloo2_extrabold.ttf` | Baloo 2 ExtraBold (Ek Type), Latin and Devanagari, no Reserved Font Name; caption font `baloo2` (P1-10) | `fonts/ttf/Baloo2/Baloo2-ExtraBold.ttf` from [yanone/Baloo2-Variable](https://github.com/yanone/Baloo2-Variable/tree/master/fonts/ttf/Baloo2) (google/fonts' upstream), `master` at `29ddd81f64ccf958ab3c33104a66de8978e25d4c`, fetched 20 Oct 2026. 356,520 bytes, SHA-256 `9f7598515faaccbbde0331c0c51369ff204b780eac8c22561d5304ede60de61c`. | `Baloo2-OFL.txt` (google/fonts `ofl/baloo2/OFL.txt`, `main` at `47d54ca`) |
| `kalam_bold.ttf` | Kalam Bold (Indian Type Foundry), Latin and Devanagari, no Reserved Font Name; caption font `kalam` (P1-10) | `ofl/kalam/Kalam-Bold.ttf` from [google/fonts](https://github.com/google/fonts/tree/main/ofl/kalam), `main` at `47d54ca1ebb14fd864695f1d39dc734f39017c33`, fetched 20 Oct 2026. 460,896 bytes, SHA-256 `2f6576601db015d4f6c08678120277fc8510b98c06e932ce7a6a9cbff4cbdded`. | `Kalam-OFL.txt` (`ofl/kalam/OFL.txt`, same commit) |
| `rozha_one_regular.ttf` | Rozha One Regular (Indian Type Foundry), Latin and Devanagari, no Reserved Font Name; caption font `rozha` (P1-10) | `ofl/rozhaone/RozhaOne-Regular.ttf` from [google/fonts](https://github.com/google/fonts/tree/main/ofl/rozhaone), `main` at `47d54ca1ebb14fd864695f1d39dc734f39017c33`, fetched 20 Oct 2026. 322,828 bytes, SHA-256 `8491b838c31f30272aa6da67e4749afabab8297db82a055fd2d0aa689f500ee3`. | `RozhaOne-OFL.txt` (`ofl/rozhaone/OFL.txt`, same commit) |

To add a font: take it from the project's own repository, add its `OFL.txt` here as `<Family>-OFL.txt`, and add a row
with the commit and SHA-256.
