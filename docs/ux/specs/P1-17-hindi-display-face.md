# Hindi display face · P1-17

## Job
In Hindi, the loud moments (Home hero, entry cards, sheet heroes, empty-state headlines) keep the condensed, heavy meme voice that Anton gives the other four languages. Today they fall back to the system's regular Devanagari face (`docs/ux/reviews/P0-05/android-home-hi-200.png`). There is no blue action here; this is type only.

**Done when** (TICKETS): in Hindi, the Home hero and entry-card titles render in the new face instead of the system font, nothing clips at 200% font scale and 360 dp, and the other four languages look unchanged.

## Decision
**Teko Bold** (Indian Type Foundry, SIL OFL 1.1, no Reserved Font Name; Latin, Latin Extended and Devanagari). The comparison with Khand, Rajdhani and Anek Devanagari, the rules and the sources are in `docs/DESIGN_SYSTEM.md` → Tokens → Type → Hindi display face. The values are in `design/tokens.json` → type → "Display (Hindi)".

## What changes
| Where | English, id, es, pt | Hindi |
| --- | --- | --- |
| `display-xl` (Home hero, empty-state headlines) | Anton 40/44, 400 | Teko 40/52, 700, letter spacing 0 |
| `display` (entry cards, Create sheet hero) | Anton 28/30, 400 | Teko 28/36, 700, letter spacing 0 |
| `wordmark` (new token: "Memix" on Home) | Anton 28/30 | Anton 28/30 (unchanged) |
| Interface roles | Space Grotesk | Space Grotesk; Devanagari from the system face (unchanged) |

- **The switch follows the app language** (the per-app language or the system language Memix resolves to), not the characters in the string. A Hindi user's Latin words in a headline are set in Teko too, so one headline never mixes two display faces.
- **Uppercase:** `DisplayText` still uppercases per locale. Devanagari has no case, so only Latin letters change.

## States
| State | What shows |
| --- | --- |
| Hindi, 100% font | Hero "मीम बनाएं" and entry-card titles in Teko Bold |
| Hindi, 200% font, 360 dp | Titles wrap. Nothing clips: no marks above the headline bar or below the baseline are cut, including inside the entry cards' rounded clip. |
| Other four languages | Pixel-identical to before (Anton) |
| Language switched while the app runs | The display roles follow on the next composition; no restart needed |

## Accessibility
- Contrast is unchanged (same colors).
- At 200% font, the headings still wrap, never truncate.
- The hero stays a heading for screen readers.

## Requests to the principal mobile engineer
I don't edit code, so these are requests.
1. **Font file:** bundle `teko_bold.ttf` in `:core:designsystem` `composeResources/font/`. It is `fonts/ttf/Teko-Bold.ttf` from https://github.com/googlefonts/teko (251 KB). Add its `OFL.txt` wherever the other font licenses live.
   - Use the variable `Teko[wght].ttf` from google/fonts (285 KB) instead only if Compose Multiplatform resources can set the weight axis on Android and iOS. Check the current CMP docs; don't trust memory.
2. **MemixType:** when the app language is `hi`, build `displayXl` and `display` from Teko at weight 700 with the Hindi sizes and line heights above, and letter spacing 0. Every other language keeps today's values. Keep the property names, so no screen changes.
   - **Line height:** compare 52/36 with Teko's built-in line spacing. If Teko's is taller, use it, rounded up to 4 dp, and tell me the value so I can update the tokens.
3. **New `wordmark` style** (Anton 28/30, 400, the same as today's `display`), never localized. Point `HomeScreen.kt:104` at `MemixTheme.type.wordmark`, because it uses `type.display` today and would switch to Teko in Hindi.
4. **Catalog:** in the Type section, show `display-xl` and `display` with a Hindi sample ("मीम बनाएं", "वीडियो मीम") whenever the catalog runs in Hindi.
5. **P1-10 shares this file:** in captions, Teko Bold draws the Devanagari runs that Anton can't (spec P1-10 → Fonts). Bundle it once, in `:core:designsystem`, where the text engine can reach it as well.

## QA compares
1. Home in Hindi at 100% and 200% font, 360 dp: hero and entry cards in Teko, nothing clipped, wordmark still Anton.
2. The Create sheet hero in Hindi at 200%.
3. An empty state with a `display-xl` headline (Drafts) in Hindi at 200%.
4. Home in English at 360 dp, compared with `docs/ux/reviews/P0-05/android-home-360.png`: unchanged.
5. On the Hindi Home hero, check visual size and vertical centering against the English Anton hero. Teko may sit low in its line box; if it does, note it in the QA report and I'll adjust the line-height token.
