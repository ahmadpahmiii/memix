# Design system in Compose · P0-04

## Job
Give every later screen one set of tokens, fonts, icons and components, so screens are built from parts instead of raw values. The deliverable the owner and designer judge is the **component catalog** (debug builds only).

Design Read: a phone-first creative tool for 13+ TikTok and Reels creators, in the owner's "neutral chrome, one blue" language (Spectrum grays, CapCut structure, Anton for loud moments), dials ENERGY 2 / RHYTHM 2 / MOTION 2 (`docs/DESIGN_SYSTEM.md` → Dials).

## Entry and exit
Debug builds only: a long-press on the Home wordmark opens the catalog; system back or the close button returns to Home. In release builds the long-press does nothing and the route is unreachable.

## Layout
One scrolling `canvas` screen, `space-4` gutters, one section per component with a `title` heading. Each section shows the component in every state listed below, side by side where it fits at 360 dp, stacked when not. No sample content pretends to be catalog data: sound and template names are written as placeholders (`Sound name`, `Template name`) and durations as `0:00`.

## Tokens
Exactly the tables in `docs/DESIGN_SYSTEM.md`, exposed as `MemixTheme.colors / type / spacing / shapes / stroke / size / elevation / motion`. Add one token group the tables imply but don't list: **motion** (`duration-press` 120 ms, `duration-sheet` 200 ms, `duration-screen` 320 ms, `duration-bonk` 240 ms).

## Fonts
Anton (display), Space Grotesk 400/500/700 (UI), Space Mono 400 (timecode), all SIL OFL, bundled as static TTFs. Space Grotesk is kept for a reason, not as a default: it was drawn from Space Mono, so UI text and timecodes share one voice while Anton stays the only loud face.

## Components and states

| Component | States the catalog shows |
| --- | --- |
| Button | primary, secondary, danger, ghost, quiet × default, pressed, focused; one with a leading icon; a 3-word label at 200% font scale |
| Chip | unselected, selected, focused; a row that scrolls past the screen edge |
| SoundRow (the "sound chip") | idle, playing (row on `surface`, play disc filled `primary`, pause icon), favorite on and off; add button |
| EntryCard | Video meme (`primary`), Photo meme (`surface-raised`, `primary` icon) |
| TemplateCard | thumbnail placeholder (`surface-raised` 9:16 frame with the type badge on the 70% black wash), name. **No use count**: there is no real usage data, so the number would be invented (antislop R-17). |
| Sheet | closed and open with title, two rows and one primary action; scrim; grabber; closes on scrim tap, drag down, system back and Escape |
| Tabs | 5 tabs scrolling, selected underline |
| SegmentedTabs | 2-way, each side selected |
| Slider | default, dragging, value readout in `timecode`; double-tap resets |
| Toggle | off, on, pressed, focused (spec: `design/system/components/Toggle/README.md`) |
| Icons | every icon drawn for P0 in `text-secondary`, `text`, `primary`, with its name |
| Focus ring | shown on one control of each kind, keyboard-focused |

Every interactive component works in the catalog: buttons give press feedback, chips and tabs change selection, the sheet opens and closes, the slider moves, the toggle flips. A catalog control that does nothing is a defect.

## Icons for P0
Drawn to the grammar in `docs/DESIGN_SYSTEM.md` → Iconography: `home`, `templates`, `create` (plus), `sounds`, `drafts`, `video`, `photo`, `close`, `chevron_right`, `arrow_back`, `globe`, `play`, `pause`, `heart`, `heart_filled`.

## Launcher icon
No logo exists (antislop R-23), so the launcher icon is a plain placeholder: an "M" from the Anton wordmark in `text` on `canvas`. It is not a logo and gets replaced when the owner supplies one.

## Motion
Press: color change only, 120 ms. Sheet: slide up 200 ms over a fading scrim. Toggle thumb: 120 ms. Under the system's reduce-motion setting, each becomes a 120 ms fade.

## Copy
Component labels in the catalog are English-only developer text (debug build, never translated). Button sample labels follow the content rules: verb first, sentence case, 1–3 words ("Export", "Delete draft", "Cancel").

## Accessibility
- Every text pairing in the tokens passes AA (checked 7 Oct with the antislop contrast script: lowest is `text-muted` on `surface-raised`, 4.60:1).
- Control edges made only by value steps are 1.35–1.49:1, so no control may rely on its edge: buttons and chips carry a text label, icon-only buttons carry a 4.5:1+ icon and a content description, toggles carry state in the thumb.
- Focus: `focus-ring` white, 2 dp, 2 dp outside the control, on every interactive element (18.9:1 on `canvas`).
- Touch targets: 48 dp minimum, including icon buttons and the toggle row.
- 200% font scale: buttons and chips grow in height and wrap, never clip.

## Analytics
None.

## QA compares
1. Catalog top to bottom at 360 dp and 430 dp widths.
2. Catalog at 200% font scale.
3. Sheet open, and the screen after closing it with system back.
4. Keyboard focus moving through one section (emulator keyboard, Tab).
5. Each icon against the grammar (flat caps, one solid core).
