# Templates: the Templates tab (05a) and the Template screen with slot fill (05b) · P4-05

Twelfth Phase 4 spec (written 18 Nov 2026). It reuses, and does not repeat:
- `design/screens/Templates.dc.html` (the board: Anton heading, Video / Photo switch, category chips, a two-column card grid with a type badge and a duration chip), `design/system/components/TemplateCard`, `Chip`, `SegmentedTabs`, `BottomNav`.
- `P1-02-gallery-picker.md`: the system photo picker (no permission), the import sheet after 300 ms, "no empty drafts: a project exists once one item lands", the import failure copy. `P3-02-sounds-library.md`, `P3-04-download-states.md`: the catalog list pattern (placeholders after 300 ms, offline from the cache, "Try again", the 500 MB media cache). `P3-14-home-trending-sounds.md`: the trending rule.
- `P1-10-text.md`: caption styles and the Classic all-caps keyboard; `P4-04-green-screen.md`, `P4-01b-overlay-layers.md`, `P4-06-video-effects.md`, `P2-02-meme-formats.md`: what a template builds. `P1-12-export.md`: the busy Button look in `P2-08`.
- PRD → Templates (five types, "pick a template, fill its slots, preview, then open the result in the matching editor with everything still editable"), TECHNICAL_DESIGN → catalog (`templates` table, `categories`, `trending`).

## Job
"Make a meme from a ready structure in under a minute": open Templates, tap one, drop in my clip and my words, open it in the editor and export. **The one main action** on the Template screen: **Open in editor** (blue). The tab itself has no blue action (a card tap is the action).

**Done when** (TICKETS): **05a** the Templates list and a template's preview work from the cache, offline too; **05b** a user fills a 2-slot preset and exports it. Both made checkable in Measured.

**Split (decided here, for the PM).**

| Part | Contains | Why |
| --- | --- | --- |
| **P4-05a** | The repository and use cases (list by kind and category, trending, detail), the cache, the Templates tab (Video / Photo switch, chips, grid, loading, empty, offline and error states), the **Template screen without slots**: the looping preview, name, type, credit, summary line (the Use area absent) | The 05a Done when; lists and previews |
| **P4-05b** | The slot-fill part of the Template screen, asset download, picking media, captions, **Open in editor** (builds a video or photo project from the template), the 3 fixture templates (debug builds only), analytics | The 05b Done when |

The fixture templates (P4-15a's loader names them) are needed by 05a's QA too: until 05b the engineer seeds the same three catalog rows (names, posters, previews) in debug builds only; real catalog content is P4-15b.

**Fast path, with tap counts.**
- **A 2-slot preset:** Templates tab (1), a card (2), pick clip 1 (3), pick clip 2 (4), type a caption (5), **Open in editor (6)**, Export (7). The editor opens on the finished meme, paused at 0:00; everything is editable.
- **A green screen overlay:** a card, pick one clip, Open in editor.
- **A photo format or photo template:** a card, pick the photo (or only type the captions), Open in editor.
- **No dragging at all:** every control is a tap, a field or a picker.

**Principles check.** Content first: posters and the preview are the loudest things on screen; chips and the switch are quiet. Sound first: a template's sound cues land on the meme sound track, exactly as hand-placed ones; the editor's pinned sound row is untouched. Fast path: seven taps to an exported 2-slot meme; every slot has a default. One-handed: the card grid, the slot rows and the blue button are in the lower two thirds (the button is pinned at the bottom). Forgiving: nothing is created until Open in editor; leaving keeps the fill for the visit; everything is editable afterwards. Accessible: the preview has a pause control (SC 2.2.2) and starts muted. Five languages: template names, categories and slot labels come from the catalog in five languages; app strings measured at 200%.

**Color roles:** the one blue action is **Open in editor**; the **Try again** of a failed list is Primary (the one action of that state). White is selection (the selected chip, the switch's selected label keeps SegmentedTabs). Posters are the user's and the catalog's content; the type badge and duration chip are `text` on `scrim`. No new color.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| **Templates tab** (a bottom-nav tab, `BottomNav`) | The Templates item; Home's "See all" for templates, if the PM adds a Home row (proposal) | Another tab, a card tap |
| **Template screen** (full screen, 320 ms push over the tab; the bottom nav hidden, like Create flows) | A card tap (also later from Home) | Back (top-left and system), Escape → the Templates tab with scroll, switch and chip as they were; **Open in editor** → the editor (the Template screen is removed from the back stack: Back in the editor goes Home, as every new project) |

- **Opens on:** the **Video** side and the **Trending** chip (or **All** when there are no trending rows); the last Video or Photo choice and chip are remembered for the visit. **Not kept:** the scroll position of the grid beyond the visit; a half-filled Template screen beyond the visit (kept while the app lives, below).
- **Back order:** keyboard closes, then the screen.
- **Kept:** the catalog rows (SQLDelight, 6 h list and 24 h detail), posters and previews (the media cache, 500 MB least-recently-used), downloaded template assets (same cache). A project exists only when **Open in editor** succeeds: P1-02's "no empty drafts".

## Layout
Board: `Templates.dc.html`. Differences from the board, and why:
1. **Thumbnails are the poster's own shape**: video cards 9:16, photo cards 4:5 (a photo poster of another ratio is fitted inside it on `surface-raised`). The board's 230 dp-tall 3:4-ish tiles would crop a caption that sits at the top or bottom of a poster, and a meme template's caption is the point. At 360 dp wide a video card is 158 × 281 dp; the grid shows about 1.3 rows on a 640 dp phone.
2. **The title scrolls away; the switch and the chips stay pinned** (the board's header would eat 28% of a 640 dp phone). Pinned height: switch row 48 + `space-2` + chips 48 = about 104 dp.
3. The board's cards link straight to the editor; here a card opens the **Template screen** (the PRD's "fill its slots, preview").
4. **Type badge** on the poster's bottom-left is `scrim`, as the TemplateCard README says (the board's opaque `canvas` chip becomes the README's 70% wash); the duration chip at the bottom-right is `timecode` `text` on `scrim`. Chip text 7.6:1 or more over any poster.

### The Templates tab (05a)
Top to bottom on `canvas`:
1. **Heading** "TEMPLATES" in `display-xl` (Anton uppercase; Teko for Devanagari, P1-17), `space-4` side padding; scrolls away with the grid.
2. **Switch:** SegmentedTabs **Video · Photo** (`templates_tab_video`, `templates_tab_photo`), pinned.
3. **Chips** (the Chip component, single-select, a row that scrolls sideways, `space-3` gaps, a 48 dp target around the 36 dp chip), pinned: **Trending · All · the catalog's categories for that side** (catalog `categories`, `kind = template`, names in the user's language; the CMS decides the list; the board's "Green screen, Reactions, Zoom, Captions" are examples). Selected = `selection` fill with `on-primary` text. **Trending** lists this month's `trending` rows for the user's region first, then global, no repeats (`ov.{region, global}` as P3-14); when there are none the **Trending chip is not shown** and **All** opens first. A chip with no templates for the current side is not shown.
4. **The grid:** two columns, `space-3` gaps, `space-4` side padding, **newest first** (All and categories) or **rank order** (Trending); a lazy grid that loads **30 at a time** and the next page when the last row is two rows away. A card = the TemplateCard: `radius-lg`, `surface`, the poster (9:16 or 4:5) with the type badge and (video) the duration chip, the **name under it** (`label` `text`, `space-2` padding, up to two lines). No use count (TemplateCard README). Pressed: the card's `surface` steps to `surface-raised`, 120 ms. The whole card is the target.
5. **Type badge words:** video **Green screen** (the template video plays on top and your clip shows through its green), **Character** (a keyed character over your clip), **Preset** (clips, captions, sounds and effects timed for you); photo **Format** (a layout to fill) and **Photo** (a ready picture to caption). Never colour alone: a word.
6. **Bottom nav** as every tab (`BottomNav`, Templates current).

**Posters and the cache.** The catalog gives a **poster** (a small still, 9:16 or 4:5, WebP, at most 60 KB) and a **preview** (a short muted video, 3 to 6 s, at most 2 MB) per template (schema need below). The grid reads posters only; a poster not yet loaded is a plain `surface-raised` rectangle with the name under it (no spinner). The cache is the media cache (500 MB LRU).

### The Template screen (05a: the top; 05b adds the slot area and the button)
Full screen on `canvas`; the bottom nav is hidden. Top to bottom:
1. **Top bar** (56 dp): Back (`arrow_back`, "Back"), the template's name in `title` (one line, ellipsis; the full name is in the heading below), nothing else.
2. **The preview** (scrolls with the page): the template's preview video fitted inside a `stage` (black) band, 9:16 or 4:5, up to **45% of the window height** (at 360 × 640: 288 dp tall, 162 dp wide; the preview is a reference, not an editor). Two small controls on `scrim` discs (48 dp targets, `icon` 24 dp in `text`) at its lower corners: **Play or Pause** (`play`/`pause`) at the start corner, **Sound** (`volume_off` when muted, `sounds` when on) at the end corner. It **starts muted and plays, looping three times, then pauses on its first frame**; a tap on the picture also toggles play. If the system's animations are off it opens paused. **Offline with no cached preview:** the poster with the quiet line "Preview needs the internet." (`templates_preview_offline`) and no controls. The poster shows first (120 ms cross-fade to the video once its first frame is ready).
3. **The facts**, `space-4` side padding, `space-2` apart: the name in `title-l`; one line in `label` `text-secondary`: the type word, the duration (video, in `timecode`: `0:06`) and the summary ("2 clips · 1 caption", plural-aware; a caption-only template reads "1 caption"); the credit line **"By <credit>"** (`caption` `text-muted`) when the catalog row has a `credit` (CC BY items; the credits screen, P5-07, lists them all).
4. **05b: the slot area** (below).
5. **05b: the pinned bottom bar** (`canvas`, `hairline` top border, `space-4` padding, the blue **Open in editor** button, full width, 56 dp tall, riding above the keyboard).

In 05a the screen ends after the facts; there is no button (the tab and the preview work from the cache).

### The slot area (05b)
Under the facts, a `label` `text-secondary` heading **"Your clips"** (or "Your photo" when the only media slot is a photo) over the media slots, then **"Your text"** over the caption slots, `space-4` apart. A template with no media slots (a ready photo to caption) shows only the text; a template with no captions shows only the clips.
- **A media slot** is one row, `surface`, `radius-lg`, 72 dp tall, the whole row the target: at the start a 56 dp thumbnail square (`list-thumb-size`, `radius-sm`, `surface-raised`); in the middle the **slot's label from the template** (`body-strong` `text`, up to two lines: "Your reaction") and under it `label` `text-secondary` the kind and length ("Video or photo · 3 s", `templates_slot_meta`); at the end `chevron_right` in `text-secondary`. **Empty:** the thumbnail holds `create` in `text-secondary`. **Filled:** the picked media's first frame (cropped to the square) with a `badge-size` duration chip for a video, and the second line reads "Tap to change" (`templates_slot_change`). Tap: the system photo picker (P1-02: images, videos or both as the slot allows, one item, no permission); a pick returns here and the row fills. **Row notes** (one `label` line in `text-secondary` under the row, only when needed): the picked clip is shorter than the slot ("This clip is shorter than the slot. It holds its last frame."), longer ("Only the first 3 s are used. You can trim it in the editor."), or unreadable ("That file didn't work. Pick another one.", the row stays empty).
- **A caption slot** is a text field: a `label` `text-secondary` heading with the slot's label ("Top text"), then a field (`surface-raised`, `radius-md`, `space-3` padding, at least `touch-target` tall, `body` `text`, grows to 3 lines then scrolls), with **the template's sample words as the placeholder** in `text-muted` (the field starts **empty**; a slot left empty adds no caption). Maximum **100 characters** (the template's boxes are fixed; from 80 a counter "93/100" shows under the field, `caption` `text-muted`). The keyboard asks for all caps when the slot's font is Classic (P1-10); Enter on a one-line slot moves to the next field and on the last closes the keyboard. The slot's style (font, color, outline, box, animation) comes from the template and is not shown here.
- **Budget** (window 640 / 800 / 915; top bar 56, bottom bar 88, content viewport 496 / 656 / 771 dp): preview 288 / 360 / 412 dp then the facts about 88 dp: the first slot row starts at 376 / 448 / 500 dp, so at 640 the **slots start in the lower third and the page scrolls** (two media rows and a caption need a swipe); at 800 and 915 the first two rows show. Accepted: the preview is a reference and the slots are one swipe at the floor size. With the keyboard open the viewport is about 216 dp at 640: the focused field scrolls into view above the pinned button.
- **Leaving with something filled keeps it** for this template while the app lives (back and re-open restores the picks and words, nothing is asked: Forgiving; nothing is lost that was in the project, because no project exists yet). A process death loses it.

### Open in editor (05b)
- **The button** reads "Open in editor" (Primary). Tapping with an **empty media slot** does not open anything: a toast "Fill every clip slot first." (`templates_fill_missing`) and the page scrolls to the first empty slot (a blocked control explains on tap, as everywhere). Empty captions are fine.
- **Assets.** When the screen opens (05b), the template's assets (the template video, sound cues, any images) start downloading into the media cache (the P3-04 rules, the same cache). **While they are not all on the phone** and every slot is filled, the button takes the **busy look** (`surface-raised` fill, label `text-secondary`, as P2-08's) and reads "Getting the template… 40%" with a determinate ProgressBar under it; it is inert; when ready it turns blue by itself and TalkBack hears "Template ready". **Offline** and not cached: the button explains on tap (`templates_offline_assets`); a failed download: the line "Couldn't get this template. Check your connection and try again." (`templates_assets_failed`, `label` `text-secondary`) above the button and the button's tap retries.
- **Importing the user's media** (P1-02): after 300 ms the blocking import sheet "Adding your clips…" (the P1-02 sheet and its failure copy) shows while the files are copied into the project; failures use P1-02's copy; nothing is created if a copy fails.
- **Then:** the project is built from the template's preset (below), `project_create` fires (`source: template`), the editor opens **paused at 0:00** and a one-time toast shows "Press play to see your meme. Everything is editable." (`templates_editor_hint`, 4 s). The editor is the preview (Question 3).

## Rules behind the layer
### What a template builds (preview and export agree; everything is editable afterwards)
A template is a set of files plus a JSON `preset` (`templates.preset`): slots, timings, sound cues, effects, text styles (PRD). Opening it builds an ordinary `Project` with ordinary items, so every editor tool works on it. The map:

| Template kind | Slots | Builds |
| --- | --- | --- |
| `video_preset` | 1 to 3 media, 1 or 2 captions | The user's clips on the **main track** in slot order, each cut to the slot's length (shorter holds its last frame; photos are stills of the slot's length), the template's captions in the template's styles at its positions and times, **sound cues as meme sounds** at their exact times, **effects** (for example a `zoom_punch`) at their times |
| `video_overlay` (green screen) | 1 media | The user's media on the **main track**; the template video as a **video overlay** with **green screen keying** (P4-04) at the template's key settings, full frame, over it |
| `video_cutout` (character) | 1 media | The user's media on the main track; the template video as an **overlay** keyed (P4-04) at its position and size, as the template says |
| `photo_format` | 1 or more photo slots, captions | A **photo project** (`PhotoScene`) with the format's layout (P2-02) holding the user's photos and the captions in the template's styles |
| `photo_template` | captions only (optionally a photo) | A photo project with the template's picture as the main layer and the captions on it |

- **All the template's own files** (the template video, a character, sounds, a picture) are copied into the project's folder like any media (P1-02's rule), so the project survives a cache clear.
- **Slot length:** a media slot may declare a length; the user's clip is cut at it from the clip's start. Trimming and moving are the editor's.
- **The template's language:** captions' sample words and the template's names and labels are catalog text per language; a language the template lacks falls back to English (catalog rule: English is required).
- **Versioning:** the preset carries a `version`; a template whose version the app can't read **is not listed** (the list query asks only for versions the app supports), so there is no state to design.

### Photos and videos by kind
The Video side lists `video_*` kinds and the Photo side `photo_*` kinds. A video template's media slots say `video`, `photo` or `any` (the picker shows what fits). A photo template's media slots are photos only.

### Licensing
Every template has `source_url`, `license_type`, `license_proof` and `credit` (PRD, content rule); the screen shows the credit line when present; templates and their assets are never user media and are never uploaded from the phone. **P4-15 reminder:** every animated or video template asset is checked at intake for the flash limit (no more than 3 flashes in any second, SC 2.3.1).

## States
| State | What shows | Copy |
| --- | --- | --- |
| Templates tab, list | Switch, chips, the grid; Trending first | `templates_title`, `templates_tab_*`, `templates_chip_*` |
| Loading (over 300 ms, no cache) | Six static `surface-raised` card placeholders (the poster shape) | none |
| Poster not loaded | Plain `surface-raised` rectangle with the name | none |
| Loading more (next page) | The last row's placeholders; no spinner | none |
| Empty catalog (no templates at all) | Centered: `templates` icon 32 dp `text-secondary`, the title, the body; no button | `templates_empty_title`, `templates_empty_body` |
| Empty category | A quiet line under the chips | `templates_category_empty` |
| Offline, with cache | The cached list; a chip not loaded before is empty with the offline line | `templates_offline_line` |
| Offline, no cache | Centered title and body | `templates_offline_title`, `templates_offline_body` |
| Failed, no cache | Centered title, body and **Try again** (Primary) | `templates_error_title`, `templates_error_body`, `templates_retry` |
| Template screen, with preview | Preview playing muted 3 times, then paused | `a11y_template_preview_*` |
| Preview offline, uncached | Poster and the quiet line | `templates_preview_offline` |
| Template screen, slots empty | Rows with `create`; the button blue | `templates_slots_*` |
| A slot filled | Thumbnail, "Tap to change", notes if short or long | `templates_slot_*` |
| A slot's pick unreadable | Row stays empty; the note | `templates_slot_failed` |
| Open with an empty media slot | Toast; scroll to the slot | `templates_fill_missing` |
| Assets downloading | Button busy with percent and bar | `templates_getting` |
| Assets offline, not cached | Button explains on tap | `templates_offline_assets` |
| Assets failed | Line above the button; the tap retries | `templates_assets_failed` |
| Importing the media (over 300 ms) | The P1-02 blocking import sheet | P1-02 strings |
| Import fails | P1-02's failure copy; nothing created | P1-02 |
| Opened | The editor, paused at 0:00, with the toast | `templates_editor_hint` |
| A template removed from the catalog after it was cached | Not in the list after the next refresh; an open Template screen keeps working from the cache | none |
| Language missing in the template | English text | none |
| Long content (5 languages, 200% font) | Chip row and switch scroll sideways (no truncation); card names wrap to three lines (cards grow); slot labels and notes wrap; the page scrolls; the button stays pinned and its label wraps to two lines | none |
| First run | No coach mark | none |

## Interactions
- **Card tap:** opens the Template screen (`template_open`). **Chip, switch:** immediate, 120 ms; the grid scrolls to the top; one chip active.
- **Preview:** tap the picture or the Play control to pause and resume; the Sound control toggles; the preview stops when the screen closes or the app backgrounds.
- **Slot row tap:** the picker; **caption field:** typing as P1-10 (no entry bar: it is a form).
- **Open in editor:** as above. **Back:** to the tab.
- **Keyboard (basic, P1):** Tab reaches the switch, chips, cards, the preview controls, rows, fields and the button; Enter activates; Escape is back.
- **Haptics:** none.
- **Pull to refresh:** none; the catalog refreshes on app start and every 6 h.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Screen push and pop | 320 ms (`duration-screen`) | 120 ms fade |
| Chip and switch change | 120 ms; the grid cross-fades 200 ms | 120 ms fade |
| Poster arrives | 120 ms fade | same |
| Card pressed | 120 ms | same |
| Poster to preview cross-fade | 120 ms | same |
| **The preview video** | Content: plays muted, three loops, then pauses; a control pauses it | Starts **paused on the poster** when the system removes animations; Play starts it |
| Busy button percent | Follows the real percent, no looping bar | same |
| Toasts | 120 ms fade | same |

Nothing in the chrome loops (MOTION 2); the grid shows posters only.

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5; a native read before the closed test). Budgets are characters. **Template names, categories, slot labels and sample captions are catalog content** in five languages, written by whoever loads the catalog (P4-15), not app strings. Reused: `templates_title`, `templates_empty_title` (kept), `panel_done`, the P1-02 import strings, `text_entry_count`.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `templates_tab_video` | Video | 10 | Video · Vídeo · Vídeo · वीडियो | Switch; same as the Create sheet's word |
| `templates_tab_photo` | Photo | 10 | Foto · Foto · Foto · फ़ोटो | Switch |
| `templates_chip_trending` | Trending | 14 | Populer · Tendencias · Em alta · ट्रेंडिंग | Chip; the word used by the Sounds tab |
| `templates_chip_all` | All | 10 | Semua · Todas · Todos · सभी | Chip |
| `template_type_green_screen` | Green screen | 16 | Layar hijau · Pantalla verde · Tela verde · ग्रीन स्क्रीन | Badge |
| `template_type_character` | Character | 16 | Karakter · Personaje · Personagem · किरदार | Badge: a character placed over your clip |
| `template_type_preset` | Preset | 16 | Preset · Preajuste · Predefinição · प्रीसेट | Badge: clips, text, sounds and effects set up for you |
| `template_type_format` | Format | 16 | Format · Formato · Formato · फ़ॉर्मेट | Badge: a photo layout to fill |
| `template_type_photo` | Photo | 16 | Foto · Foto · Foto · फ़ोटो | Badge: a ready picture to caption |
| `templates_empty_body` | New templates arrive every month. Check back soon. | 60 | Template baru hadir tiap bulan. Cek lagi nanti. · Cada mes llegan plantillas nuevas. Vuelve pronto. · Todo mês chegam modelos novos. Volte logo. · हर महीने नए टेम्पलेट आते हैं। जल्द फिर देखें। | Replaces the P0 "comes in a later build"; 51 en |
| `templates_category_empty` | Nothing here yet. Try another category. | 50 | Belum ada. Coba kategori lain. · Aún no hay nada. Prueba otra categoría. · Nada aqui ainda. Tente outra categoria. · यहाँ अभी कुछ नहीं है। दूसरी श्रेणी चुनें। | 39 en |
| `templates_offline_line` | You're offline. Showing what's saved. | 50 | Kamu offline. Menampilkan yang tersimpan. · Sin conexión. Se muestra lo guardado. · Você está offline. Mostrando o que está salvo. · आप ऑफ़लाइन हैं। सहेजे हुए दिखा रहे हैं। | Quiet line under the chips; 37 en |
| `templates_offline_title` | You're offline | 30 | Kamu offline · Sin conexión · Você está offline · आप ऑफ़लाइन हैं | Empty state |
| `templates_offline_body` | Connect to see templates. The ones you opened before still work. | 80 | Sambungkan internet untuk melihat template. Yang pernah dibuka tetap bisa dipakai. · Conéctate para ver plantillas. Las que abriste antes siguen funcionando. · Conecte-se para ver modelos. Os que você abriu antes continuam funcionando. · टेम्पलेट देखने के लिए कनेक्ट करें। पहले खोले हुए चलते रहेंगे। | 62 en, es 76 |
| `templates_error_title` | Couldn't load templates | 30 | Template gagal dimuat · No se pudieron cargar las plantillas · Não foi possível carregar os modelos · टेम्पलेट लोड नहीं हुए | |
| `templates_error_body` | Check your connection and try again. | 50 | Periksa koneksi lalu coba lagi. · Revisa tu conexión e inténtalo de nuevo. · Verifique sua conexão e tente de novo. · कनेक्शन जाँचें और फिर कोशिश करें। | Same as `stickers_pack_failed_body` |
| `templates_retry` | Try again | 14 | Coba lagi · Reintentar · Tentar de novo · फिर कोशिश करें | Primary button of the failure state |
| `templates_preview_offline` | Preview needs the internet. | 40 | Pratinjau butuh internet. · La vista previa necesita internet. · A prévia precisa de internet. · प्रीव्यू के लिए इंटरनेट चाहिए। | Under the poster; 27 en |
| `templates_summary` | %1$s · %2$s · %3$s | 50 | same | Type · duration · slots (the parts are below) |
| `templates_summary_clips` | one: "%1$d clip" · other: "%1$d clips" | 20 | id (one form): "%1$d klip" · es: "%1$d clip" / "%1$d clips" · pt: "%1$d clipe" / "%1$d clipes" · hi: "%1$d क्लिप" / "%1$d क्लिप" | A plurals resource (id has one form) |
| `templates_summary_captions` | one: "%1$d caption" · other: "%1$d captions" | 20 | id: "%1$d teks" · es: "%1$d texto" / "%1$d textos" · pt: "%1$d legenda" / "%1$d legendas" · hi: "%1$d कैप्शन" / "%1$d कैप्शन" | A plurals resource |
| `templates_credit` | By %1$s | 40 | Oleh %1$s · De %1$s · Por %1$s · %1$s द्वारा | %1$s = the catalog's credit (a name) |
| `templates_slots_clips` | Your clips | 20 | Klipmu · Tus clips · Seus clipes · आपकी क्लिप | Heading |
| `templates_slots_photo` | Your photo | 20 | Fotomu · Tu foto · Sua foto · आपकी फ़ोटो | Heading when the only media slot is a photo |
| `templates_slots_text` | Your text | 20 | Teksmu · Tu texto · Seu texto · आपका टेक्स्ट | Heading |
| `templates_slot_kind_video` | Video | 14 | Video · Vídeo · Vídeo · वीडियो | Slot meta |
| `templates_slot_kind_photo` | Photo | 14 | Foto · Foto · Foto · फ़ोटो | Slot meta |
| `templates_slot_kind_any` | Video or photo | 20 | Video atau foto · Vídeo o foto · Vídeo ou foto · वीडियो या फ़ोटो | Slot meta |
| `templates_slot_meta` | %1$s · %2$s | 40 | same | Kind · length ("Video · 3 s"; the length from the `timecode` format; a slot without a length shows the kind only) |
| `templates_slot_change` | Tap to change | 24 | Ketuk untuk ganti · Toca para cambiar · Toque para trocar · बदलने के लिए टैप करें | A filled slot's second line |
| `templates_slot_short` | This clip is shorter than the slot. It holds its last frame. | 70 | Klip ini lebih pendek dari slot. Frame terakhir ditahan. · Este clip es más corto que el hueco. Se queda en el último fotograma. · Este clipe é mais curto que o espaço. Fica no último quadro. · यह क्लिप स्लॉट से छोटी है। आख़िरी फ़्रेम रुका रहेगा। | Note under the row; 60 en |
| `templates_slot_long` | Only the first %1$s is used. You can trim it in the editor. | 80 | Hanya %1$s pertama yang dipakai. Bisa dipangkas di editor. · Solo se usan los primeros %1$s. Puedes recortarlo en el editor. · Só os primeiros %1$s são usados. Você pode aparar no editor. · सिर्फ़ पहले %1$s इस्तेमाल होंगे। एडिटर में छोटा कर सकते हैं। | %1$s = a length such as "3 s"; 58 en |
| `templates_slot_failed` | That file didn't work. Pick another one. | 50 | File itu tidak bisa dipakai. Pilih yang lain. · Ese archivo no funcionó. Elige otro. · Esse arquivo não funcionou. Escolha outro. · वह फ़ाइल नहीं चली। कोई और चुनें। | 41 en |
| `templates_fill_missing` | Fill every clip slot first. | 40 | Isi semua slot klip dulu. · Rellena todos los huecos de clips primero. · Preencha todos os espaços de clipes primeiro. · पहले सभी क्लिप स्लॉट भरें। | Toast; es 41 (pt 46: "Preencha todos os espaços primeiro." 36) |
| `templates_open_editor` | Open in editor | 24 | Buka di editor · Abrir en el editor · Abrir no editor · एडिटर में खोलें | The one blue button |
| `templates_getting` | Getting the template… %1$d% | 30 | Mengambil template… %1$d% · Obteniendo la plantilla… %1$d% · Baixando o modelo… %1$d% · टेम्पलेट ला रहे हैं… %1$d% | Busy button; a single `%` after the number |
| `templates_offline_assets` | Connect to the internet once to download this template. | 70 | Sambungkan internet sekali untuk mengunduh template ini. · Conéctate a internet una vez para descargar esta plantilla. · Conecte-se à internet uma vez para baixar este modelo. · यह टेम्पलेट डाउनलोड करने के लिए एक बार इंटरनेट से जुड़ें। | Toast; 55 en, es 59 |
| `templates_assets_failed` | Couldn't get this template. Check your connection and try again. | 80 | Template gagal diambil. Periksa koneksi lalu coba lagi. · No se pudo obtener esta plantilla. Revisa tu conexión e inténtalo de nuevo. · Não foi possível baixar este modelo. Verifique sua conexão e tente de novo. · यह टेम्पलेट नहीं आ सका। कनेक्शन जाँचकर फिर कोशिश करें। | Line above the button; 64 en, es 74 |
| `templates_editor_hint` | Press play to see your meme. Everything is editable. | 70 | Tekan putar untuk melihat memenya. Semua bisa diedit. · Dale a reproducir para ver tu meme. Todo se puede editar. · Toque em reproduzir para ver seu meme. Tudo é editável. · अपना मीम देखने के लिए प्ले दबाएं। सब कुछ एडिट हो सकता है। | Toast, 4 s; 52 en |
| `a11y_template_card` | %1$s, %2$s, %3$s | 80 | same | Name, type, length spoken ("TV reaction, Green screen, 6 seconds"; photos: name, type) |
| `a11y_template_preview_play` | Play preview | 24 | Putar pratinjau · Reproducir vista previa · Reproduzir prévia · प्रीव्यू चलाएं | Control name |
| `a11y_template_preview_pause` | Pause preview | 24 | Jeda pratinjau · Pausar vista previa · Pausar prévia · प्रीव्यू रोकें | Control name |
| `a11y_template_sound_on` | Turn preview sound on | 30 | Nyalakan suara pratinjau · Activar sonido de la vista previa · Ativar som da prévia · प्रीव्यू की आवाज़ चालू करें | Control name |
| `a11y_template_sound_off` | Turn preview sound off | 30 | Matikan suara pratinjau · Silenciar la vista previa · Desativar som da prévia · प्रीव्यू की आवाज़ बंद करें | Control name |
| `a11y_template_ready` | Template ready | 24 | Template siap · Plantilla lista · Modelo pronto · टेम्पलेट तैयार | Polite, when assets finish |
| `a11y_template_slot` | %1$s, slot %2$d of %3$d, %4$s | 70 | %1$s, slot %2$d dari %3$d, %4$s · %1$s, hueco %2$d de %3$d, %4$s · %1$s, espaço %2$d de %3$d, %4$s · %1$s, स्लॉट %2$d / %3$d, %4$s | %4$s = "empty" or "filled" (`a11y_slot_empty` "Empty" · Kosong · Vacío · Vazio · खाली; `a11y_slot_filled` "Filled" · Terisi · Lleno · Preenchido · भरा) |

Translator notes:
- A **template** is a ready structure for a meme: the user adds their own clips and words. A **slot** is one place the user fills. **Preset** here is a template whose clips, captions, sounds and effects are timed for you.
- **Green screen**: the template video plays on top and shows the user's clip through its green. **Character**: a cut-out character placed over the user's clip. **Format**: a photo layout to fill. **Photo**: a ready picture to caption.
- "Trending" is what is popular this month in the user's country. Keep the same word as the Sounds tab.
- **Hindi:** the interface uses the system Devanagari face; the heading uses Teko; nothing has a fixed text height.

## Accessibility
- **Focus and TalkBack order, the tab:** the heading, the switch (a tab group), the chips (a single-choice group: "Trending, selected, 1 of 6"), then the cards in reading order, each a button `a11y_template_card` (the poster is decorative). Changing a chip announces the result count politely ("12 templates"). **The Template screen:** the back button, the name as the screen's title, the preview (a group with Play or Pause and Sound as buttons), the facts, the slots in order (`a11y_template_slot`; fields read their label), the button last. Back returns focus to the card that opened it.
- **Moving content (SC 2.2.2):** the preview is muted, plays three loops, then pauses, and has a labeled Play or Pause button; it starts paused when the system removes animations.
- **Sound:** off by default; the Sound control states its action ("Turn preview sound on").
- **Single-pointer, no-drag:** every control is a tap, a field or a picker (WCAG 2.5.1, 2.5.7).
- **Touch targets:** cards, chips (36 dp drawn in 48 dp), the switch, rows (72), fields, the controls on the preview (48) and the button (56) are 48 dp or more.
- **Contrast:** card names `text` on `surface` 15.6:1; the type badge and duration `text` on `scrim` 7.6:1 or more over any poster; the selected chip `on-primary` on white 18.9:1, unselected `text` on `surface-raised` 12.4:1; the facts `text-secondary` on `canvas` 7.1:1; the credit `text-muted` 4.6:1; the placeholder `text-muted` on `surface-raised` 4.6:1 or more; Open in editor `on-primary` on `primary` 7.9:1, the busy look `text-secondary` on `surface-raised` 5.7:1. **State is never color alone:** the selected chip is also spoken; the filled slot says "Tap to change".
- **200% font:** see States. **Reduce motion:** see Motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `template_open` | A card tap opens the Template screen (the PRD event) | `template_id` (the catalog id), `type` (the kind: `video_preset`, `video_overlay`, `video_cutout`, `photo_format`, `photo_template`) |
| `project_create` | Open in editor builds the project (the PRD event) | `editor` (`video` or `photo`), `source: "template"`, and a new `template_id` (the catalog id; for the PM to confirm) |
| `tool_use` | A preview sound toggle | none: not tracked |

- No event for chips, the switch, picks or typing. The catalog id is a fixed catalog value, never user content; no file names, captions or paths ever. Analytics stay off until consent (P5-01).

## QA compares
Use the three debug fixture templates (a 2-slot preset, a green screen overlay, a photo format; names in P4-15a), a 360 × 640 and a 360 × 800 phone, and airplane mode. Screenshots in `docs/ux/reviews/P4-05a/` and `P4-05b/`.
1. **The tab:** Video and Photo, the chips (Trending first, or All when empty), the card grid with badges and duration; the loading placeholders (slow network); the empty catalog; the failed state with Try again; airplane mode with a warm cache.
2. **The Template screen top:** the preview playing and paused, the controls, the facts and credit; the preview offline and uncached; Spanish and Hindi at 200%.
3. **The slot area (05b)** at 640 (slots under the fold) and 800: empty rows, a filled row with a short-clip note, a long-clip note, a caption with a placeholder and a counter; the keyboard open with the focused field above the button.
4. **Open in editor:** the busy button with a percent bar, the explain toast for an empty slot, the import sheet, the editor opening paused with the toast.
5. **The built projects:** the preset (clips on the main track with the sound cue and the effect in place), the green screen overlay (an overlay keyed over the clip), the photo format (a photo project).
6. **A cleared cache offline:** the list is empty with the offline state; a previously used template's Use explains.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **05a, the list and a template's preview work from the cache, offline too.** Open the Video and Photo sides online (cards and posters load, a preview plays), turn on airplane mode and kill the app: the Templates tab shows the same list and posters within 1 s, a card opens, the preview plays from the cache (if it had been opened online; otherwise the offline line). The Trending chip shows region rows then global with no repeats. A 60-template list scrolls with no visible lag (janky frames under 5%, `dumpsys gfxinfo`). LeakCanary: switch sides, chips, open and close five templates.
- **05b, a user fills a 2-slot preset and exports it.** On the 2-slot fixture: pick two clips and type a caption, Open in editor: the project has the two clips on the main track at the template's lengths, the caption in the template's style at its time, the sound cue at its exact time (within 33 ms) and the zoom punch effect; the editor opens paused at 0:00; **Export at 1080p30 completes and the file matches the editor's preview** (frames at three times within 3/255 on 99% of pixels, the sound cue at its time within 33 ms in the file). All of it is editable (move the caption, delete the effect). Time from the card tap to the editor with two prepared clips: recorded (the tap path above is 6 taps).
- **The other kinds:** the green screen fixture opens with the keyed overlay over the user's clip; the photo format opens in the photo editor with the photo and captions. An empty caption slot adds no caption; an empty media slot blocks with the toast and scrolls to it.
- **Assets:** online first use shows the busy button with a percent and then goes blue; airplane mode with the assets uncached explains; with them cached, Open in editor works offline.
- **Copy of media and no leak of the user's:** the project holds copies of the template's assets and the user's media (delete the cache folder: the project still opens); nothing is uploaded (a network capture while filling shows no request carrying the user's files or words).
- **Old drafts open;** no schema bump is needed for the tab; the project built from a template uses existing schema.

## Model needs (for the PM, the backend and the engineer; I don't edit code or the schema)
| Need | Expected |
| --- | --- |
| Catalog row | The `templates` table has `preview_path` only. This spec needs **a poster and a preview video per row**: a new column **`poster_path`** (WebP still, at most 60 KB, 9:16 or 4:5) with `preview_path` meaning the muted preview mp4 (at most 2 MB, 3 to 6 s) and `asset_paths` the build files. A migration (backend engineer), the content script's validation (poster present, sizes), and `kind` stays the five values |
| Names and labels | `title` (jsonb, five languages, English required) already; the **slot labels, sample captions and kind words live in `preset` as localized objects** (`{"en": "...", "id": "..."}`) so a template can name its own slots |
| `preset` | A versioned JSON: `version`, `kind`, `durationMs`, `slots: [{id, kind: media|caption, media: video|photo|any, labelI18n, lengthMs?, sampleI18n?, maxChars?}]`, the **timeline items** to build (clips by slot id, captions with style and times, sound cues by catalog sound id and time, effects by id and time, overlays with key settings) and a **photo scene** for photo kinds. `TemplateSpec` in `:core:model`; a builder `buildProjectFromTemplate(spec, fills)` that returns a normal `Project` (rule 3: features call use cases only) |
| Query | The list query asks for `kind in (…)` for the chosen side, a category or trending, and only versions the app supports; 30 per page |
| Trending | `trending` rows with `kind = template` per month, region and global (exists) |
| Analytics | `template_open` and `project_create` as above |
| Not modeled | No favorites, no search, no per-template usage count |

## Requests to the principal mobile engineer
1. **Repository and use cases** in `:core:domain` over `:core:data` (catalog fetch with the 6 h and 24 h refresh, the SQLDelight cache, a `.sqm` migration if a table is added, `verifySqlDelightMigration`): list by side and category, trending, detail. **Features call use cases only.**
2. **The Templates tab** in `:feature:templates` from SegmentedTabs, Chip, TemplateCard (poster shapes, badges), the lazy grid with paging, placeholders after 300 ms, offline and error states; the pinned switch and chips; Coil for posters through the media cache.
3. **The Template screen** (push, no bottom nav): the preview player (a small separate player instance, muted, three loops, Play and Sound controls, released on exit; **not** the editor's `CompositionPlayer`), the facts; **05b** the slot rows, the picker per slot (kinds), caption fields with the template's style capitalization, the fill kept in memory per template, the pinned button with the busy look, asset prefetch through the P3-04 download rules, the import sheet (P1-02), `buildProjectFromTemplate`, the editor opening paused with the toast.
4. **Fixtures (debug builds only; P4-15a names them):** three original templates (a 2-slot preset with a caption, a sound cue at 1.2 s from the starter sounds and a `zoom_punch` at the cut, 6 s; a green screen overlay whose template video has a flat green area; a photo format with one photo slot and two captions), each with all five languages and every license field (`license_type: own`), a poster and a preview. 05a seeds the same rows without assets. Never in a release build.
5. **Debug hooks** `memix.openTemplate <id>` and `memix.templatesOffline` (force the offline state) for QA.
6. **Strings** in the five files (including the two plurals); **analytics:** `template_open`, `project_create` with `template_id`.
7. **No new icon:** `create`, `chevron_right`, `arrow_back`, `play`, `pause`, `sounds`, `volume_off`, `templates` all exist.

## Tokens and icons
- **No new color, size, radius or motion token.** Reused: `radius-lg`, `list-thumb-size`, `badge-size`, `space-*`, `duration-screen`, `scrim`.
- **New content group** `templates` in `design/tokens.json` and `design/system/tokens.json` (page size, cache lifetimes, poster shapes and size limits, preview limits and loop count, caption limit, the five kinds and their badge words). Kotlin: a `TemplateCatalog` in commonMain next to `OverlayCatalog`, never `MemixTheme`.
- **Pointer edits made with this spec:** DESIGN_SYSTEM (a templates paragraph), the TemplateCard README (poster shapes, the Template screen as the destination), and the `Templates` board stays as the reference for the tab.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Does a card open a Template screen first, or go straight to the editor?**
- *Problem:* the PRD says "pick a template, fill its slots, preview, then open"; the board's cards link straight to the editor.
- *Considerations:* **A. A card opens the Template screen: preview, slots, then Open in editor** (recommended; applied): the user sees what they will get and fills the slots in one place; costs one screen. **B. Straight to the editor with empty slots:** one fewer screen but slot filling would need new editor UI and a blank first frame.
- *Reverse:* a flow change before the build.

**2. Poster and preview files.**
- *Problem:* a grid of 30 looping videos is heavy and breaks "nothing loops in the chrome"; the schema has one preview path.
- *Considerations:* **A. A poster still for the grid and a short muted preview video for the Template screen** (recommended; applied): light, accessible, fast offline; costs a `poster_path` column and one more file per template for the content team. **B. One looping preview in the grid:** heavy on memory, battery and data, and moving content on every card.
- *Reverse:* the column is data only.

**3. Is the editor the "preview" step?**
- *Problem:* a separate preview of the filled result needs the engine in the Template screen.
- *Considerations:* **A. No: the editor opens paused on the result and is the preview** (recommended; applied): one player, nothing to build twice; the user presses play. **B. A preview player on the Template screen:** matches the PRD wording, a second engine host to keep in sync with the editor.
- *Reverse:* an added preview later; no migration.

**4. Empty caption slots.**
- *Problem:* a template shows sample words; a user may skip a caption.
- *Considerations:* **A. Fields start empty; an empty one adds no caption** (recommended; applied): no sample words left in a meme by mistake. **B. Prefill the sample words:** shows the meme at once, but samples such as "ME WATCHING THE NEWS" can be exported unchanged.
- *Reverse:* a default.

**5. Does Home get a trending templates row?** (a proposal for the PM, not decided here; PRD → Home lists it, no ticket does) *Considerations:* **A. Yes, a small ticket after 05a** (recommended): five cards in a horizontal row (the TemplateCard README already says "a horizontal row on Home"), the P3-14 pattern; **B. Not in v1.**

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Auto-playing moving content over 5 seconds needs a pause control | Guidance | [WCAG 2.2 SC 2.2.2](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html) |
| Content flashing more than three times a second can trigger seizures (intake check) | Guidance | [WCAG 2.2 SC 2.3.1](https://www.w3.org/WAI/WCAG22/Understanding/three-flashes-or-below-threshold.html) |
| A document or photo picker needs no storage permission | Guidance | [Android photo picker](https://developer.android.com/training/data-storage/shared/photopicker) |
| Mobile meme and short-video apps open a template to a fill step with a preview, then an editor | Assumption (product knowledge; help pages were not reachable here) | none |
| Empty, offline and failed states each need a way forward | Guidance (expert) | [NN/g, Empty states](https://www.nngroup.com/articles/empty-state-interface-design/) |
| The board and README for cards, chips and the switch | Evidence (this project) | `design/screens/Templates.dc.html`, `design/system/components/*/README.md` |
| Template catalog schema (kind values, `preset`, trending) | Evidence | `docs/TECHNICAL_DESIGN.md` → Backend schema |
