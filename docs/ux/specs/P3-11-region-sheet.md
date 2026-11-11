# Region sheet: region and language · P3-11

Part of the Phase 3 specs. Read first, and not repeated here:
- `P3-02-sounds-library.md`: the Local tab and its region line (this spec turns the line into the sheet's opener and updates two of that spec's states), Trending's regional part.
- `P0-05-navigation-and-browse-screens.md`: Home's region label (the board's pill, left as a label "until P3-11"), the Sheet and its blocking variant, `region_*` strings.
- `P1-02-gallery-picker.md`, `P1-12-export.md`: how a blocking sheet and a one-time first-run step are written.
- The Sheet, OptionTile and Button READMEs in `design/system/components/`.

No board shows it. There is **no onboarding flow and no Settings screen** in v1 (proposal 10 Nov, Q1 A; a Settings screen is raised at P5): this one sheet is both the first-launch step and the way to change the answers later.

## Job
"Memix should know where I am and what language I read, with no sign-up." One look, usually one tap. **Done when** (TICKETS): changing the region in the Region sheet, opened from the Local tab header, changes the Local tab. The first confirm fires `onboarding_complete` (PRD, owner 11 Nov).

**The one blue action:** the sheet's one Primary Button, **Continue** the first time and **Save** after. Selected tiles are white (selection). On Home the sheet is the only blue on screen besides the Video meme card, which is behind the scrim.

**Tap count:** first launch, nothing to change: **1** (Continue). Changing the region later: Local tab header (1), a region (2), Save (3).

## Entry and exit
- **First launch** (after the ad-consent form for the EEA, UK and Switzerland, which comes first and is not ours): Home loads and the sheet rises over it at once, in its **blocking variant** (no grabber, no close button; a scrim tap or drag does nothing). It shows again if the app is updated from a build that never showed it, and after "Clear data", never otherwise. Why blocking: the PRD calls this one step in the first run, the answers drive three screens, and a dismissible sheet that returns on every start is a nag. Why one tap: both lists arrive filled in.
- **Continue** confirms, closes the sheet over 200 ms and fires `onboarding_complete`. **System back and Escape do the same as Continue** (the blocking-sheet rule: back runs the sheet's continue action, here accepting the visible answers; Questions 5).
- **Later:** opened from the **Local tab header** (the region row at the top of its list) and from **Home's region button** (Questions 3), as the standard sheet: grabber, close button, scrim, drag down, back and Escape all close it and **discard** pending changes. **Save** applies and closes.
- **Opens with** the current values selected. Nothing else changes behind it; a playing preview keeps playing (it is a sheet over a browse screen).
- **Kept:** the two answers, on the phone only (multiplatform-settings), across restarts and updates. **Not kept:** the phone's own country when it is outside the list (we don't store what the user didn't choose; Questions 2).
- **Focus** returns to the control that opened the sheet; after the first Continue, focus goes to Home's hero heading.

## Layout
Sheet component: `surface`, `radius-lg` top corners, `shadow-sheet`, over `scrim`, padding `space-4`; the long-content rule (the middle scrolls, the title and the Primary stay). `Modifier.coveredByModal` behind it. Title "Region and language" in `title`.

Top to bottom:
1. **First-run line** (first run only): `region_first_hint` in `body` `text-secondary`, `space-3` below the title.
2. **Region** group: heading "Region" in `label` `text-secondary`; below it a **two-column grid of OptionTiles, label layout** (new, OptionTile README updated): equal widths, `space-2` gaps, each at least `touch-target` tall, label in `body-strong`, `radius-md`. Unselected `surface-raised` with `text` (12.4:1); selected `selection` white with `on-primary` (18.9:1). The tiles: **the five launch regions** in the catalog's order (Indonesia, United States, India, Brazil, Mexico) and, **first**, the phone's own country when it is not one of them (Questions 2). Names are the country name in the app's current language. One choice.
3. **A note** under the grid when one applies (`label` `text-secondary`, wraps, never cut): country outside the list, or region not readable (States).
4. **Language** group (`space-6` above): heading "Language" and a **horizontally scrolling row of label-layout tiles**, edge to edge, the selected one scrolled into view: **English · Bahasa Indonesia · Español · Português · हिन्दी**, each in its own language and script, never translated. About 60% of them show at 360 dp, so the cut-off tile tells the user to scroll. One choice.
5. **A note** under the row when the phone's language is not one of the five.
6. **Primary** pinned at the bottom: **Continue** or **Save**, full width, `touch-target` tall. Save with nothing changed just closes (the P3-07 rename pattern); it never says "nothing changed".

The sheet measures about 56 (title) + 28 + 3 × 52 (grid) + 24 + 28 + 52 (language) + 72 (Primary) + gaps ≈ 440 dp with the first-run line and no notes: it fits a 360 × 640 phone without scrolling at 100% font (the long-content cap leaves about 576 dp). With a 4th grid row (phone country outside the list) or two notes it scrolls under the pinned title and Primary.

### The Local tab's header (replaces P3-02's "region line, a label not a button")
- The first item of the Local list, a `surface-raised` row, `radius-md`, `space-4` side gutters, `touch-target` tall at least: `globe` (24, `text-secondary`), "Sounds for {region}" in `label` `text` (`sounds_region_line`), and at the end the word **Change** in `primary` (`label`; a link, 7.3:1 on `surface`; on `surface-raised` 5.9:1). **The whole row is one button.** Pressed: `hairline`.
- If a region is missing (cannot happen after the first-run step; a defensive state) the row reads `sounds_region_choose` ("Choose your region") in `text` and has no "Change" word.
- P3-02's states update: **Empty, Local (region known)** keeps its "See global sounds" Secondary; the header row above it is the way to switch region. **Empty, Local (region unknown)** drops "no button until P3-11 gives one": the header row is the button; the body reads `sounds_local_unknown_body` (updated, below).

### Home's region button (replaces P0-05's label)
The top-right of Home's top row: `globe` (16), the region name in `label` `text-secondary` (one line), and `chevron_right` (16, `text-secondary`) after it, inside a 48 dp target (`radius-md` press fill `surface-raised`). The chevron says it is a control (P0-05's reason for a label no longer holds: the control is live). It is not a SettingPill (that component is for editors' top bars).

## States
| State | What shows | Copy |
| --- | --- | --- |
| First run, phone region and language both among the five | Both selected; first-run line; Continue | `region_first_hint`, `region_continue` |
| First run, **region outside the five** (for example Philippines, Spain, Germany) | A sixth tile first: the phone's country, selected; the note says it has no local sounds yet and to pick another to see its Local tab | `region_note_outside` |
| First run, **region unreadable** (no region on the phone) | The region of the closest launch region for the **selected language** is pre-selected (id Indonesia, en United States, es Mexico, pt Brazil, hi India), with a note saying so. The user can change it | `region_note_unknown` |
| First run, **language not supported** (for example French) | **English** selected; a note names the phone's language and says English is selected. The five languages stay choosable | `region_note_language` |
| Later open, current values | Both selected; Save | `region_save` |
| **Changing the region later** | Tap a tile (white), Save: the sheet closes; the Local list reloads for the region (200 ms cross-fade, scrolled to the top, chip reset to All); Trending's regional part and Home's trending sounds update the same way; the header reads the new name; a polite announcement; no toast | `region_changed_a11y` |
| **Changing the language later** | Tap a language, Save: the app's strings change at once (the sheet closes first, then every screen redraws in the new language; Hindi gets its display face); the Local header and Home button show the region name in the new language | none |
| Save with nothing changed | Closes | none |
| Dismissed later (close, scrim, drag, back) | Closes; nothing applies | none |
| Phone offline | Works fully: the five regions and five languages ship in the app. Regions the catalog adds later appear in the list the next time it is online and stay cached | none |
| Loading, error | None: no screen waits on the network; the list is local | none |
| Permission | None: the region comes from the phone's regional setting, not location, and no permission is asked | none |
| Region with no local sounds (a catalog region that is empty) | Selectable; the Local tab shows P3-02's "No local sounds yet" with the region's name | `sounds_empty_local_*` |
| Long content, 5 languages, 200% font | The grid becomes **one column** (a tile never breaks a word); the language row scrolls with its selected tile in view; the notes and first-run line wrap; the sheet content scrolls under the pinned title and Primary; the Primary wraps to two lines | none |
| First run | The only run with the first-run line, the blocking variant, Continue and the event | `region_first_hint` |

## Interactions
- **Tap a tile:** selects it at once (white, 120 ms); tapping the selected one does nothing. Nothing applies until Continue or Save.
- **Continue, Save:** apply both answers, close, and (the first time only) fire the event. No haptic.
- **Local header and Home button:** open the sheet. No long-press, no gesture-only path.
- **Back, Escape:** first run, Continue; later, close without applying.
- **Keyboard:** Tab reaches the tiles (arrow keys move inside a group), the Primary and, later, the close button; Enter selects or activates.
- **Selection is white, primary is blue:** the tiles are white, the Primary and the "Change" word are blue.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Sheet opens, closes | 200 ms slide over a fading scrim (`duration-sheet`) | 120 ms fade (instant on Android) |
| A tile selects, a press | 120 ms (`duration-press`) | same |
| Local list reloads after a change | 200 ms cross-fade | 120 ms fade |
| Language change | The new strings appear when the sheet has closed; no animation | same |
Nothing loops (MOTION 2).

## Copy
English source; id, es, pt and hi are machine drafts until P5-06. **Budget** = longest allowed in any of the five languages, in characters. Tone: sentence case, "you", no emoji, no exclamation marks. Country names come from the platform in the app's language; language names are fixed, in their own script. `{country}` and `{region}` are country names; `{language}` is a language name as the app's current language writes it.

| Key | English | Budget | id | es | pt | hi |
| --- | --- | --- | --- | --- | --- | --- |
| `region_sheet_title` | Region and language | 28 | Wilayah dan bahasa | Región e idioma | Região e idioma | क्षेत्र और भाषा |
| `region_heading_region` | Region | 14 | Wilayah | Región | Região | क्षेत्र |
| `region_heading_language` | Language | 14 | Bahasa | Idioma | Idioma | भाषा |
| `region_first_hint` | We picked these from your phone's settings. You can change them later. | 90 | Dipilih dari pengaturan ponselmu. Kamu bisa mengubahnya nanti. | Elegidos según los ajustes de tu teléfono. Puedes cambiarlos después. | Escolhidos pelas configurações do celular. Você pode mudar depois. | ये आपके फ़ोन की सेटिंग से चुने गए हैं। आप इन्हें बाद में बदल सकते हैं। |
| `region_note_outside` | We don't have local sounds for {country} yet. Pick another region to see its Local tab. | 110 | Belum ada suara lokal untuk {country}. Pilih wilayah lain untuk melihat tab Lokal-nya. | Aún no hay sonidos locales para {country}. Elige otra región para ver su pestaña Local. | Ainda não há sons locais para {country}. Escolha outra região para ver a aba Local dela. | {country} के लिए अभी लोकल साउंड नहीं हैं। उसका स्थानीय टैब देखने के लिए कोई और क्षेत्र चुनें। |
| `region_note_unknown` | We couldn't read your region, so we picked one for your language. Change it if it's wrong. | 110 | Wilayahmu tidak terbaca, jadi kami memilih satu sesuai bahasamu. Ubah jika salah. | No pudimos leer tu región, así que elegimos una según tu idioma. Cámbiala si no es la tuya. | Não conseguimos ler sua região, então escolhemos uma pelo seu idioma. Mude se estiver errada. | आपका क्षेत्र नहीं पता चला, इसलिए हमने आपकी भाषा के हिसाब से एक चुना है। गलत हो तो बदल लें। |
| `region_note_language` | Memix isn't in {language} yet, so English is selected. | 80 | Memix belum tersedia dalam {language}, jadi bahasa Inggris dipilih. | Memix aún no está en {language}, así que se eligió inglés. | O Memix ainda não está em {language}, então o inglês foi selecionado. | Memix अभी {language} में नहीं है, इसलिए अंग्रेज़ी चुनी गई है। |
| `region_continue` | Continue | 16 | Lanjut | Continuar | Continuar | जारी रखें |
| `region_save` | Save | 14 | Simpan | Guardar | Salvar | सेव करें |
| `sounds_region_line` | Sounds for {region} | 36 | (exists, P3-02) | | | |
| `sounds_region_change` | Change | 14 | Ubah | Cambiar | Mudar | बदलें |
| `sounds_region_change_a11y` | Change region | 24 | Ubah wilayah | Cambiar región | Mudar região | क्षेत्र बदलें |
| `sounds_region_choose` | Choose your region | 30 | Pilih wilayahmu | Elige tu región | Escolha sua região | अपना क्षेत्र चुनें |
| `sounds_local_unknown_body` | Pick your region to see local sounds. | 56 | Pilih wilayahmu untuk melihat suara lokal. | Elige tu región para ver sonidos locales. | Escolha sua região para ver sons locais. | लोकल साउंड देखने के लिए अपना क्षेत्र चुनें। |
| `home_region_a11y` | Region: {region} | 28 | Wilayah: {region} | Región: {region} | Região: {region} | क्षेत्र: {region} |
| `region_changed_a11y` | Region set to {region} | 30 | Wilayah diatur ke {region} | Región cambiada a {region} | Região definida como {region} | क्षेत्र {region} पर सेट हुआ |
| `region_global` | Global | 16 | (exists, P0-05; stays for the defensive case) | | | |

`sounds_local_unknown_body` **replaces** the P3-02 text ("Local sounds show up once Memix knows your region."), and `sounds_local_unknown_title` stays. Language names (never translated): English, Bahasa Indonesia, Español, Português, हिन्दी. Notes for translators: "region" means the country whose local sounds the user wants, not a place inside a country. "Local tab" is the tab the app calls `sounds_tab_local` (id Lokal, es Local, pt Local, hi स्थानीय): the notes must use that exact word. `region_note_unknown` and `region_note_language` explain a default the app chose, so they are calm, not apologetic. Hindi: check the sheet's two-line Primary and the 200% line height of the notes.

## Accessibility
- **Sheet:** the title is the pane title and takes focus on open; the screen behind leaves TalkBack and keyboard focus (`coveredByModal`). The blocking variant has no scrim "Close".
- **Groups:** "Region" and "Language" are single-choice groups named by their headings; each tile reads its name, "selected" when it is, and its position ("Indonesia, selected, 1 of 6"). The state description carries only "selected" (P1-11 review). A tile's name is its label.
- **Order:** title, first-run line, Region heading, tiles, note, Language heading, tiles, note, Primary; later also the close button after the title.
- **Opener names (SC 2.5.3):** the Local row reads "Sounds for Indonesia, Change" with the click label "Change region"; Home's button reads `home_region_a11y` ("Region: Indonesia"), which contains its visible text, click label "Change region".
- **Announcements (polite, SC 4.1.3):** after Save, `region_changed_a11y`; the notes are part of the reading order, not live regions. After a language change, TalkBack reads the new strings from the screen it returns to; focus is on the opener.
- **Contrast:** `text` on `surface-raised` 12.4:1; `on-primary` on white 18.9:1; `text-secondary` on `surface` 5.7:1; the "Change" word `primary` on `surface-raised` 5.9:1; the chevron `text-secondary`. A selected white tile on `surface` is 17.2:1 (UI part).
- **Targets:** every tile, the row, the button, the Primary 48 dp or more.
- **200% font and reduce motion:** see States and Motion. No gesture-only action: everything is a tap.

## Analytics
- `onboarding_complete {language, region}` (PRD, owner 11 Nov): fires **once, at the first confirm** (Continue, or back). `language` is `en`, `id`, `es`, `pt` or `hi` (the chosen one, not the phone's); `region` is the chosen country code (`ID`, `US`, `IN`, `BR`, `MX`, or the code of a catalog region added later). The phone's own country is not sent when the user picked another. Like every event it follows the user's consent choice: if analytics are off, nothing is sent but the first run still counts as done on the phone.
- No event for a later Save. **For the PM, optional:** `region_change {language, region}` if the owner wants to see how often people move region (not added, since the PRD has none). No event for opening the sheet.

## QA compares
Screenshots at 360 dp wide, English unless noted, in `docs/ux/reviews/P3-11/`:
1. `first-run-id` (phone in Indonesian, Indonesia): the blocking sheet over Home with the first-run line, both selected; `first-run-hi-200` Hindi at 200% font (one column, Primary reachable).
2. `first-run-outside` (phone region Philippines): the extra tile and its note; `first-run-fr` (phone language French): English selected and the note; `first-run-unknown` (region removed).
3. `later-from-local`: opened from the Local header, the standard sheet; `local-after-change`: the Local tab after changing to Mexico; `home-button`, Home's top row with the globe, name and chevron.
4. `local-unknown-state` (defensive): the "Choose your region" row.
5. `language-change`: before and after switching to Español (Home and Local), and Hindi's display face on the Home hero.
By recording or note: the one-tap first run (1 tap counted), back at first run (same as Continue, event once), the airplane-mode first run, a Philippines phone ending on a Local empty state, TalkBack through the groups.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on.
1. **Does the language list change the app's language, or only confirm the phone's?**
   - **A. Recommended: it changes it** (per-app language). Many Indian and Indonesian phones run in English while the person reads Hindi or Indonesian; the PRD says the screen "confirms language and region". Costs: the engineer must verify that Compose Multiplatform resources and the Hindi display face switch at runtime on Android and iOS; if they can't, the Language group becomes a read-only line ("Memix follows your phone's language") in the same layout, and nothing else changes.
   - B. Read-only from the start. Simplest and most robust, but a person with an English phone gets English.
2. **A phone whose country has no local sounds** (Philippines, Spain, Germany).
   - **A. Recommended: keep their country as the region** (a tile marked selected, a note), so Local says "No local sounds yet" and lights up by itself when that country is added to the catalog (PRD: no app change). The user can pick another region in one tap.
   - B. Pick the nearest launch region silently. A Local tab with content now, but it is another country's, and it never corrects itself.
3. **Home's region button also opens the sheet.** The ticket names only the Local tab header.
   - **A. Recommended: yes.** P0-05 left Home's region as a label "until P3-11", and the Home trending row (P3-14) is region-based, so people look there to change it. Tiny: one button, no new screen.
   - B. Home stays a label. Then two screens show the region and only one changes it.
4. **First-launch sheet: blocking with one Continue (recommended)** versus dismissible. A dismissible sheet that never fires `onboarding_complete` and returns each start is a nag; blocking costs one tap, with answers already filled in.
5. **Back at first launch confirms the visible answers (recommended).** The alternative, exiting the app, loses a first-time user over a back press.
6. **Decided, not a question:** the region also drives Trending's regional part (P3-02) and Home's trending row (P3-14), because they read the same value; no extra ticket.

## Requests to the principal mobile engineer
- **Setting:** a persisted `region` (country code) and `language` (one of five) and a `firstRunDone` flag; `GetPhoneRegionUseCase` stays the detector, a new use case holds the chosen region; `Region.Global` stays only as the defensive state. The region list = catalog regions (cached) with the five bundled as the offline fallback.
- **Detection:** language from the phone's locale among the five, else English; region from `DeviceRegion` (no location permission); the unknown-region default by language (rule in States).
- **Language switch:** per-app language (`AppCompatDelegate.setApplicationLocales` or `LocaleManager` on Android, the app language on iOS). **Verify first** that Compose Multiplatform resources, `LocalLocale` and `usesHindiDisplayFace()` follow it without a restart; report to the designer if they don't (Questions 1).
- **Components:** the Sheet's blocking variant (first run) and standard variant (later); **OptionTile label layout** (below); Home's button; the Local header row. Region names from the platform in the app language.
- **Strings** in the five `strings.xml` files; `sounds_local_unknown_body` changes text in `:feature:sounds`.
- **Event:** `onboarding_complete` once.
- **No new token. No new icon** (`globe`, `chevron_right` exist).

## Component change: OptionTile, label layout
`design/system/components/OptionTile/README.md` gains a third layout, first used here and again by P3-09's voice presets: **label layout**, no picture, the label in `body-strong`, at least `touch-target` tall, `space-4` side padding, one line (the longest word never breaks); in a **two-column grid** (equal widths, one column at large font) or a **scrolling row** (edge to edge, selected tile scrolled into view). Look, selection, pressed and accessibility are the OptionTile's.

## Tokens
None new. Uses `surface-raised`, `selection`, `on-primary`, `primary`, `radius-md`, `radius-lg`, `touch-target`, `space-*`, `shadow-sheet`, `duration-sheet`, `duration-press`.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Android per-app language preferences let a user pick an app's language apart from the system's | Guidance | [Android per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages) |
| The region can be read from the locale without the location permission | Guidance | [Android `Locale`](https://developer.android.com/reference/java/util/Locale), `DeviceRegion` already does it |
| Pre-filled defaults on a first-run step cut the effort to one tap | Guidance | [NN/g: defaults in forms](https://www.nngroup.com/articles/form-design-white-space/) (general form guidance) |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| A visible label must be in the accessible name | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| People in India and Indonesia often keep an English phone but prefer a local-language app | Assumption (common observation, no source checked; the first test asks) | none |
| A two-column grid of six tiles fits the sheet without scrolling at 360 × 640 | Assumption (computed by hand; QA measures) | none |

## Test script for the owner (5 people, a phase build)
Tasks: (1) "Open Memix for the first time and get to the home screen." (2) "Make the app speak Hindi (or another language you read)." (3) "Show me sounds from Mexico." Observe: whether people read the first-run line or just tap Continue; whether they find the Local header's Change; whether the language change surprises them. Success: task 1 in one tap; task 3 in under 15 s, no help. Notes template: task, time, taps, what they said, where they hesitated.
