# My sounds: import and extract · P3-07

Part of the Phase 3 sound specs. Read first, and not repeated here:
- `P3-02-sounds-library.md`: the Sounds tab, the My sounds tab's place in it, the row, search and the empty-state look.
- `P3-03-sound-preview-and-details.md`: the details sheet, including its "My sound" state.
- `P3-05-sound-picker-sheet.md`: the editor's picker (My sounds is one of its tabs, and panes swap inside its one sheet).
- `P1-02-gallery-picker.md`: the import sheet, its progress and result states, the storage and "no picker" states, the no-permission rule. This spec reuses them with audio wording.
- `P3-15-use-a-library-sound.md`: the Use block (it works on My sounds too).

No board shows it. Components: Sheet (standard and blocking variants), ProgressBar, SoundRow, Toast, a navigation row (`nav-row-height`, first used by P2-09's WhatsApp packs row), Button.

## Job
"Use a sound from my own phone": a voice note, a song snippet, the audio of a clip I filmed. **Done when** (TICKETS): imported sounds work in the editor and are never uploaded.

**The one blue action:** none on the tab. The two actions are navigation-row buttons on `surface-raised`; the Create button in the bottom nav stays the screen's blue (P0-05). The blue on a My sounds row is the usual meme-sound disc and Use block. Failure and storage sheets have one Primary each, as P1-02.

**Tap count:** My sounds tab, Import audio (1), a file in the system picker (2): the sound is in the list. In the editor: Meme sounds (1), My sounds tab (2), Import audio (3), a file (4), then Add (5). Importing is a setup step, not the fast path: afterwards, a My sound is 2 taps from the timeline like any other (P3-05).

## What "never uploaded" means (the rule behind every state)
- The file is copied into the app's own storage and never read by any network code. It has **no catalog row, no upload, no Report** (nothing to report: it is the user's own file), and **no file name, path or length in analytics or Crashlytics** (the engineer logs the error class only).
- A sound the user adds to a project is copied into that project like a gallery clip (P1-02 request 3), so **deleting it from My sounds never changes a draft** that used it, and deleting a draft never removes it from My sounds.
- The My sounds folder is the user's own data on their phone. Whether Android's backup service may include it is the engineer's call (Requests); the app's claim is that **Memix** never uploads it.
- Imported sounds are **not** added to Favorites or Recently used and have **no heart** (they already have their own tab, and a deleted one would leave dead rows there). This overrides P3-02's "same row": on My sounds the row is `[disc] [name + meta] [Use]`, so the name block gets 48 dp more.

## Entry and exit
- **Arrives from:** the My sounds tab, in the Sounds screen (P3-02) and in the editor's picker (P3-05). The two actions are the first two list items there.
- **Imports go to the system, then back:** each action opens a system picker (no Memix screen before it, no explainer, no permission rationale, as P1-02). Back or nothing picked returns to the tab exactly as it was: no message, no event.
- **After a successful import:** the user stays on the My sounds tab; the new sound is the first row; a 2 s toast says it was added; the list scrolls to the top; focus goes to the new row's name block. The sound is **not** played and **not** placed on a timeline (Questions 4).
- **Closing a result sheet** (Close, scrim, drag, back): the tab as it was.
- **Kept:** My sounds persist across restarts and app updates, newest first, until the user deletes them. They are not part of any draft's schema.

## Layout
### The My sounds tab (Sounds screen and picker)
Inside the P3-02 frame (title, search, tabs; the picker's header per P3-05). Top of the list, scrolling with it:
1. **Two navigation rows**, `space-4` side gutters, `space-2` between: each `nav-row-height` (56) at least, `surface-raised`, `radius-md`, a 24 dp icon in `text-secondary` at the start, the title in `body-strong` `text`, a subtitle in `label` `text-secondary`, no chevron (they open a picker, they do not go deeper). Both are Secondary-weight: nothing here is blue. Pressed: `hairline`. They grow with text and wrap at 200% font.
   - **Import audio** (icon `folder`, new, see Tokens), subtitle "Pick a file from your phone". Opens the system document picker, audio files only, **one file**.
   - **Use a video's sound** (icon `video`, existing), subtitle "Take the audio from a video". Opens the system photo picker, **videos only, one video** (not photos: a photo has no sound).
2. **Note** (only when the list has sounds): `space-3` below the rows, two short sentences in `label` `text-secondary`, wrapping: "Your sounds stay on this phone and are never uploaded." and "Only use sounds you have the right to use." (Questions 5). When the list is empty the empty block's own body carries the privacy line, so the note is not drawn twice.
3. **The list:** SoundRows, newest first, on `canvas` (or `surface` in the picker). Meta "{duration} · Imported" or "{duration} · From a video" (`sound_detail_mine_*`, in the credit slot of `sound_meta`). No category chips. The empty block (P3-02's "No imported sounds yet", title in `display` uppercase) sits centered under the rows, with **no button of its own**, because the two rows above are the actions.
- **While a search has text** the rows and note are hidden (as chips are) and the field filters the list by contains-match (P3-02).

### The row
`[disc] [name + meta] [Use]` (heart removed, above). In the picker the block is **Add** (P3-05). The name block opens the details sheet (pane in the picker). Name in `body-strong`, up to two lines; meta wraps, never cut. The playing row shows the waveform if peaks exist, else the rail (P3-03).

### The details sheet for a My sound (P3-03's "My sound" state, with two additions)
- Title row: the name in `title` (no heart). Player row as P3-03. Facts: **Credit** "Imported" or "From a video" only (no category, license, source or Report).
- **Use in a video meme** (Primary, P3-15). Below it two Quiet text buttons side by side, each 48 dp tall: **Rename** (`edit` icon 20 dp) and **Delete** (`delete` icon 20 dp, words and icon in `danger`, 5.2:1 on `surface`). These sit where Report sits for catalog sounds.
- **In the editor's picker the pane shows Add to video only.** Rename and Delete are managed on the Sounds tab, not mid-edit: a deletion's Undo toast would sit under the picker's scrim where TalkBack and keyboard cannot reach it, and the editor's own Undo does not cover the library.
- **Rename** swaps the sheet's content (a pane, not a second sheet; back arrow at the start) to a P1-14-style field: title "Rename sound", a single-line field labelled "Name" (`surface-raised`, `radius-md`, `space-3` padding, `touch-target` tall, `body`), prefilled with the whole name selected, 60 characters at most (grapheme clusters; the counter "48/60" in `caption` `text-muted` shows from 48), no line breaks, and **Save** as the pane's one Primary. An empty name does not save: Save stays enabled and a tap shows "Give your sound a name." under the field and moves focus to it. Unchanged name: Save just goes back. Back, close or scrim discards. A rename writes the name only; it is not an edit to any draft.
- **Delete** removes the row at once, closes the sheet and shows a toast `Deleted {name}` with a Quiet **Undo**, 8 s (never shorter than the user's "Time to take action", as P1-14). No confirmation: two deliberate taps plus Undo protect better than a dialog (P1-14, Raskin). The audio file stays on disk until the toast ends; Undo puts the row back in place; if the app dies first, the next launch cleans the file. While the Undo window is open no other toast may take the Undo away (P1-14 rule): another toast shows for its own time and the Undo toast returns for the rest of the window.

## Flow A: Import an audio file
1. Tap **Import audio** → the system document picker (`ACTION_OPEN_DOCUMENT`, `audio/*`, one file, no permission). The screen behind stays as it was.
2. A file is picked → Memix copies it, **checks it** (it must have a playable audio track and a known length) and **converts it to the catalog's format** (AAC 128 kbps `.m4a`, the engineer's call, Requests) so every imported sound decodes the same on Android and iOS and a 10-minute file stays small.
3. **Within 300 ms:** no UI; the sound appears (Success below). **Longer:** the **import sheet** (P1-02's blocking variant: no grabber, no close; Cancel is the only exit), title "Adding your sound", percent in `timecode` `text-secondary`, ProgressBar, Cancel (Secondary, full width). It stays at least 500 ms once shown. In the picker it is a pane of the picker's sheet with the same content and no back arrow while it runs.
4. **Success:** the sheet goes; the My sounds tab shows the new sound first (see Entry and exit).

## Flow B: Use a video's sound
1. Tap **Use a video's sound** → the system photo picker, videos only, one item (`PickVisualMedia`, `VideoOnly`; no permission; the file chooser fallback applies the same filter and count, P1-02).
2. A video is picked → Memix **extracts its audio track** to the same format and runs the same checks.
3. **Within 300 ms:** no UI. **Longer** (a long video): the same import sheet, same title, percent from the extraction's progress.
4. **Success:** as Flow A. The sound's name is the video's file name without its extension, or "Sound from video" when it has none. **The video itself is not kept**: only the audio.

**Names:** the file name without its extension, trimmed, at most 60 characters (longer is cut at 60), else "My sound" for an audio file with no name. No two names are forced to differ: importing the same file twice makes two rows (Assumption; no dedupe in v1, delete is cheap).

**Limits:** no cap on how many sounds. **Length: at most 10 minutes** (Questions 1). A sound over a minute is allowed and plays; the editor's trim handles cut it down (P1-06 → Trim), and landing it past the video's end is covered by P3-05's overhang rule.

## States
| State | What shows | Copy |
| --- | --- | --- |
| My sounds, with sounds | Two rows, note, list newest first | `mysounds_*` |
| My sounds, empty | Two rows, then the centered empty block (no button) | `sounds_empty_mine_title`, `sounds_empty_mine_body` |
| System picker open | System UI at half height over the tab | (system) |
| Nothing picked | The tab unchanged; no message | none |
| Quick copy (under 300 ms) | No sheet: the sound appears | `mysound_added_toast` |
| Copying (over 300 ms) | Import sheet: title, percent, bar, Cancel | `mysound_import_title`, `import_percent`, `import_cancel` |
| Cancelled (Cancel, back or Escape while copying) | Everything from this pick dropped and its files deleted; the tab as it was; no toast | none |
| **Unsupported file** (not audio, DRM-protected, or a codec this phone can't decode) | Result sheet (standard variant) "Couldn't add your sound": the row (the `folder` or `video` icon, name in `body-strong` shortened in the middle, reason in `label` `text-secondary`), Primary **Pick again** (closes the sheet, then reopens the same picker). Close, scrim, drag, back: the tab as it was. Nothing is stored. | `mysound_none_title`, `mysound_reason_unsupported`, `import_pick_again`, `mysound_none_body` |
| **A video with no audio track** | The same result sheet, reason "This video has no sound." A video whose audio is only silence is not detected (Assumption; it imports and shows a flat waveform) | `mysound_reason_no_audio` |
| **A very long file** (over 10 minutes, audio file or video) | The same result sheet; the reason says the limit and what to do. Checked from the file's metadata before copying, so no wait | `mysound_reason_too_long` |
| **Permission denied** | Doesn't apply to the pickers: the document picker and the photo picker need no permission, and Memix declares no audio, media or storage permission (P1-02, Play photo and video policy). The case that does exist is a **provider refusing access** (a revoked grant, a file app that blocks the read): the same result sheet with its own reason that names the next step. Memix never asks for a permission to fix it | `mysound_reason_denied` |
| Can't be read (a cloud file with no connection, a file deleted since picking) | The same result sheet, P1-02's reason | `import_reason_unreadable` |
| Not enough space (before or during the copy) | P1-02's storage sheet: title, body with both sizes, Primary **Free up space**; hides the button if the phone has no storage screen; checks again on return; Close drops the pick | `import_storage_title`, `mysound_storage_body`, `import_free_up_space` |
| No picker app on the phone | Standard sheet: title, body, **Close** | `mysound_no_picker_title`, `mysound_no_picker_body`, `close` |
| App in the background while copying | Copying goes on; the sheet shows the current state on return. If Android stops the app, the pick is lost and unfinished files are deleted at the next launch (P1-02) | none |
| Success | The row at the top, 2 s toast, list at the top, focus on the new row | `mysound_added_toast` |
| Rename | Pane with the field; Save | `mysound_rename_*` |
| Rename or delete fails (a write to the phone's own storage; rare) | Toast 4 s; nothing changes | `mysound_save_failed` |
| Delete | Row gone, toast with Undo 8 s | `mysound_deleted_toast`, `mysound_undo` |
| In the editor, a My sound is added | Lands on the meme sound track like any sound (P1-06 landing, P3-05 overhang); the clip's label is the sound's name | `a11y_sound_added` |
| Offline | Everything works: imports are local. Only a cloud-stored file can fail, with "Couldn't be read" | none |
| Long content, 5 languages, 200% font | Names two lines then an ellipsis (full name spoken), meta wraps, the rows wrap their subtitles, the note wraps, sheets scroll inside with Cancel/Primary pinned | none |
| First run | The same as every run: the rows and the note, no coach marks, no permission prompt | none |

## Interactions
- **Tap a navigation row:** opens the picker at once (press color over 120 ms). Taps are ignored while a picker is launching, so a double tap can't open two.
- **Tap the disc:** plays the sound (P3-04 rules do not apply: it is on the phone). One sound at a time across the whole app.
- **Tap the name block or long-press:** the details sheet. **Tap Use:** the P3-15 flow, with this sound.
- **Details Rename / Delete:** above. **Escape or back** closes the sheet or pane; during copying it is Cancel.
- No swipe-to-delete (a swipe fights with scrolling, and it has no keyboard or TalkBack route). **Haptics:** none, except what the row already has (LongPress).
- **Keyboard:** Tab reaches the two rows, then each sound's controls; Enter activates.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Import sheet arrives, leaves | 200 ms slide (picker: 200 ms pane cross-fade) | 120 ms fade |
| Progress fill | to each value over 120 ms, at most 10 updates a second | jumps |
| Copying to result | content cross-fades 120 ms, height 200 ms | 120 ms fade, height snaps |
| New row appears at the top | the list scrolls to the top (200 ms); the row fades in over 120 ms. No bonk (nothing landed on a timeline) | at once |
| Deleted row leaves, Undo restores | rows close up or part over 200 ms | at once |
| Toasts | 120 ms fade | same |
Nothing loops (MOTION 2).

## Copy
English source; id, es, pt and hi are machine drafts (P5-06 has the final say). **Budget** is the longest allowed in any language, in characters including placeholders. Shared strings are reused from P1-02 (`import_percent`, `import_cancel`, `import_pick_again`, `import_continue`, `import_storage_title`, `import_free_up_space`, `import_reason_unreadable`, `close`) and P3-03 (`sound_detail_mine_imported`, `sound_detail_mine_video`). Tone: calm, verb first, "you", sentence case, no emoji, no exclamation marks. Errors never blame the user and say what to do next. "Memix" never translates.

| Key | English | Budget | id | es | pt | hi |
| --- | --- | --- | --- | --- | --- | --- |
| `mysounds_import_title` | Import audio | 24 | Impor audio | Importar audio | Importar áudio | ऑडियो इंपोर्ट करें |
| `mysounds_import_body` | Pick a file from your phone | 36 | Pilih file dari ponselmu | Elige un archivo de tu teléfono | Escolha um arquivo do seu celular | अपने फ़ोन से फ़ाइल चुनें |
| `mysounds_video_title` | Use a video's sound | 28 | Pakai suara dari video | Usar el sonido de un video | Usar o som de um vídeo | वीडियो की आवाज़ इस्तेमाल करें |
| `mysounds_video_body` | Take the audio from a video | 36 | Ambil audio dari sebuah video | Saca el audio de un video | Extraia o áudio de um vídeo | किसी वीडियो से ऑडियो निकालें |
| `mysounds_privacy_note` | Your sounds stay on this phone and are never uploaded. | 70 | Suaramu tetap di ponsel ini dan tidak pernah diunggah. | Tus sonidos se quedan en este teléfono y nunca se suben. | Seus sons ficam neste celular e nunca são enviados. | आपकी आवाज़ें इसी फ़ोन में रहती हैं और कभी अपलोड नहीं होतीं। |
| `mysounds_rights_note` | Only use sounds you have the right to use. | 70 | Gunakan hanya suara yang kamu berhak pakai. | Usa solo sonidos que tengas derecho a usar. | Use apenas sons que você tem o direito de usar. | सिर्फ़ वही आवाज़ें इस्तेमाल करें जिनका उपयोग करने का आपको अधिकार है। |
| `mysound_import_title` | Adding your sound | 28 | Menambahkan suaramu | Añadiendo tu sonido | Adicionando seu som | आपकी आवाज़ जोड़ी जा रही है |
| `mysound_none_title` | Couldn't add your sound | 36 | Suara tidak bisa ditambahkan | No se pudo añadir tu sonido | Não foi possível adicionar seu som | आपकी आवाज़ जोड़ी नहीं जा सकी |
| `mysound_none_body` | Try a different file or video. | 50 | Coba file atau video lain. | Prueba con otro archivo o video. | Tente outro arquivo ou vídeo. | कोई दूसरी फ़ाइल या वीडियो आज़माएँ। |
| `mysound_reason_unsupported` | Memix can't play this file. Try an MP3, M4A or WAV. | 80 | Memix tidak bisa memutar file ini. Coba MP3, M4A, atau WAV. | Memix no puede reproducir este archivo. Prueba con un MP3, M4A o WAV. | O Memix não consegue tocar este arquivo. Tente um MP3, M4A ou WAV. | Memix यह फ़ाइल नहीं चला सकता। MP3, M4A या WAV आज़माएँ। |
| `mysound_reason_no_audio` | This video has no sound. | 40 | Video ini tidak punya suara. | Este video no tiene sonido. | Este vídeo não tem som. | इस वीडियो में आवाज़ नहीं है। |
| `mysound_reason_too_long` | It's longer than %1$d minutes. Trim it first, then try again. | 90 | Durasinya lebih dari %1$d menit. Pangkas dulu, lalu coba lagi. | Dura más de %1$d minutos. Recórtalo primero y vuelve a intentarlo. | Dura mais de %1$d minutos. Corte-o primeiro e tente de novo. | यह %1$d मिनट से लंबी है। पहले ट्रिम करें, फिर दोबारा कोशिश करें। |
| `mysound_reason_denied` | Memix wasn't allowed to open this file. Pick it from another place. | 90 | Memix tidak diizinkan membuka file ini. Pilih dari lokasi lain. | Memix no tiene permiso para abrir este archivo. Elígelo desde otra ubicación. | O Memix não tem permissão para abrir este arquivo. Escolha-o de outro local. | Memix को यह फ़ाइल खोलने की अनुमति नहीं मिली। इसे किसी और जगह से चुनें। |
| `mysound_storage_body` | Your sound needs %1$s, and your phone has %2$s free. Free up space, then try again. | 130 | Suaramu butuh %1$s, sedangkan ponselmu punya %2$s kosong. Kosongkan ruang, lalu coba lagi. | Tu sonido necesita %1$s y tu teléfono tiene %2$s libres. Libera espacio y vuelve a intentarlo. | Seu som precisa de %1$s e seu celular tem %2$s livres. Libere espaço e tente de novo. | आपकी आवाज़ को %1$s चाहिए और आपके फ़ोन में %2$s खाली हैं। जगह खाली करें, फिर दोबारा कोशिश करें। |
| `mysound_no_picker_title` | Can't open your files | 32 | Tidak bisa membuka file-mu | No se pueden abrir tus archivos | Não foi possível abrir seus arquivos | आपकी फ़ाइलें नहीं खुल सकतीं |
| `mysound_no_picker_body` | This phone has no app for picking audio files. | 80 | Ponsel ini tidak punya aplikasi untuk memilih file audio. | Este teléfono no tiene una app para elegir archivos de audio. | Este celular não tem um app para escolher arquivos de áudio. | इस फ़ोन में ऑडियो फ़ाइलें चुनने वाला कोई ऐप नहीं है। |
| `mysound_added_toast` | Added to My sounds | 32 | Ditambahkan ke Suaraku | Añadido a Mis sonidos | Adicionado a Meus sons | मेरी आवाज़ें में जोड़ी गई |
| `mysound_name_video` | Sound from video | 24 | Suara dari video | Sonido de un video | Som de um vídeo | वीडियो की आवाज़ |
| `mysound_name_file` | My sound | 16 | Suaraku | Mi sonido | Meu som | मेरी आवाज़ |
| `mysound_item_audio` | Audio file | 16 | File audio | Archivo de audio | Arquivo de áudio | ऑडियो फ़ाइल |
| `mysound_rename` | Rename | 16 | Ganti nama | Cambiar nombre | Renomear | नाम बदलें |
| `mysound_delete` | Delete | 16 | Hapus | Eliminar | Excluir | हटाएँ |
| `mysound_rename_title` | Rename sound | 28 | Ganti nama suara | Cambiar nombre del sonido | Renomear som | आवाज़ का नाम बदलें |
| `mysound_rename_label` | Name | 14 | Nama | Nombre | Nome | नाम |
| `mysound_rename_save` | Save | 12 | Simpan | Guardar | Salvar | सहेजें |
| `mysound_rename_empty` | Give your sound a name. | 40 | Beri nama untuk suaramu. | Ponle un nombre a tu sonido. | Dê um nome ao seu som. | अपनी आवाज़ को कोई नाम दें। |
| `mysound_deleted_toast` | Deleted {name} | 20 + name | {name} dihapus | {name} eliminado | {name} excluído | {name} हटाई गई |
| `mysound_undo` | Undo | 12 | Urungkan | Deshacer | Desfazer | पहले जैसा करें |
| `mysound_save_failed` | Couldn't save that. Try again. | 50 | Tidak bisa menyimpan. Coba lagi. | No se pudo guardar. Inténtalo de nuevo. | Não foi possível salvar. Tente de novo. | सहेजा नहीं जा सका। फिर कोशिश करें। |
| `mysound_a11y_added` | Added to My sounds: {name} | 30 + name | Ditambahkan ke Suaraku: {name} | Añadido a Mis sonidos: {name} | Adicionado a Meus sons: {name} | मेरी आवाज़ें में जोड़ी गई: {name} |

Translator notes: "Import" and "audio" are the words each language's editing apps use. `%1$d` in `mysound_reason_too_long` is always 10 (use the plural form for "other"). "Trim" is the editor's word for cutting a clip's ends. `mysounds_rights_note` is legal-adjacent and gets the lawyer's read with the license sentences before the closed test (PRD risks). Hindi: recheck the two-line subtitles and `label` meta at 200% (Devanagari line height, P1-08 review).

## Accessibility
- **Navigation rows:** each is one button. Its name is "Import audio, Pick a file from your phone" (title then subtitle, so the visible text is in the name, WCAG 2.5.3). They are the first two stops in the list after the tabs.
- **Row (My sound):** the disc "Play {name}" / "Pause {name}"; the name block "{name}, {duration}, Imported" with the click label "Show details"; the Use block "Use {name} in a new video meme". TalkBack actions: Details, Use in a new video meme, Rename, Delete.
- **Sheets:** the title is the pane title and focus moves to it; the screen behind leaves TalkBack and keyboard focus (`coveredByModal`). Progress is a progress range, read when focused, not live; the percent is not announced on every step. Buttons are named by their visible label.
- **Result rows** are one node: "{file name}, {Audio file or Video}. {reason}". Failure is never colour alone: the reason is text, and `danger` is not used here (nothing is being deleted by the failure).
- **Announcements (polite, SC 4.1.3):** on success "Added to My sounds: {name}" (the toast), after a delete "Deleted {name}" (the toast; its Undo is focusable), on Cancel nothing.
- **Focus:** after success, the new row's name block; after Delete, the next row (or the previous at the end, or the first navigation row when the list empties); after Undo, the restored row; after a result sheet closes, the navigation row that opened it; after Rename, the row.
- **Contrast:** `text` on `surface-raised` 12.4:1; `text-secondary` 5.7:1; Delete `danger` on `surface` 5.2:1 (never on `surface-raised`); Undo `primary` on `surface-raised` 5.9:1; the progress fill `primary` 7.3:1 on `surface`, 4.9:1 on its rail.
- **Targets:** every control 48 dp or more (the rows are 56 dp and grow).
- **200% font:** rows grow and wrap; names keep two lines; the note and subtitles wrap; sheets scroll with the Primary or Cancel pinned (P3-03's 40% rule for a pinned bar); the rename field grows and the pane scrolls above the keyboard.
- **Reduce motion:** see Motion. Everything is reachable without a swipe or a long-press.

## Analytics
- **Existing PRD events:** `sound_preview {sound_id, tab}` with `sound_id` the constant `my_sound` and `tab` `my_sounds` (a local id means nothing to the catalog, and nothing identifying leaves the phone); `sound_add {sound_id, tab, editor: "video"}` the same way when a My sound lands on a timeline; `sound_search {results, offline}` as P3-02. **No PRD event fires for importing.**
- **Proposal for the product manager (do not fire until the PRD has it):** `sound_import {source, success, reason}` where `source` is `file` or `video`, `success` is true or false, and `reason` (failure only) is one of `unsupported`, `no_audio`, `too_long`, `unreadable`, `denied`, `storage`. It tells the owner whether the 10-minute limit and the format message are the right ones. **Never** a file name, path, length or size.
- Crashlytics: the engineer logs non-fatals with the error class only.

## QA compares
Screenshots at 360 dp, English unless noted, in `docs/ux/reviews/P3-07/`. Test files needed: a good MP3, a renamed `.txt` as `.mp3` (unsupported), a video with the sound muted at recording (no audio track), a 12-minute file (too long), a cloud-only file with airplane mode on (can't be read).
1. `mine-empty` and `mine-with-sounds` (rows, note, list, newest first).
2. `import-progress` (a large file, throttled) and `import-progress-hi-200`.
3. `fail-unsupported`, `fail-noaudio`, `fail-toolong`, `fail-unreadable`.
4. `fail-denied`: **hard to hit by hand** (a provider that revokes access); the engineer checks it off-device by returning a `SecurityException` from the read, and notes the result.
5. `storage-full` (a nearly full emulator image).
6. `details-mine`, `rename-pane` (keyboard up), `delete-undo-toast`.
7. `mine-hi-200`: Hindi at 200%, rows and a long name wrapped.
8. `picker-mine`: the same tab inside the editor's picker, and a My sound added to the timeline.
By recording or note: the file really is not sent anywhere (the engineer's network log shows no request carrying it while importing, playing and exporting); deleting a My sound does not change a draft that used it; airplane mode: import still works; no permission prompt at any point and no audio, media or storage permission in the merged manifest.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on.
1. **How long may an imported file be?**
   - **A. Recommended: up to 10 minutes.** Covers a full song to cut a part from, and a long voice note; at AAC 128 kbps that is about 10 MB. Over 10 minutes is refused with a message that says to trim it first.
   - B. 60 seconds, with a trim step at import for anything longer. Meme-sized files, but a whole new screen (a range selector) for an M ticket.
   - C. No limit. Risks a very long copy and a full phone.
2. **One file at a time, or several?**
   - **A. Recommended: one.** Small result states (one reason, one button), and repeating costs a tap.
   - B. Several at once (up to 10): P1-02's "some didn't go in" result, more build. Good for a folder of voice notes.
3. **Which track does an imported sound land on in the editor?**
   - **A. Recommended: the meme sound track** (blue, like every sound from the picker). One idea, one place; Add works the same.
   - B. The green Audio track (like detached original audio), because it is "your own audio". Splits what the user sees as the same job.
4. **After importing from the editor's picker, should the sound land on the timeline at once?**
   - **A. Recommended: no.** It becomes the first row; the user taps Add. One more tap but no surprise (a long song landing at the playhead is a surprise).
   - B. Yes, place it at the playhead. Fewer taps; a wrong file lands on the timeline.
5. **A rights reminder on My sounds.**
   - **A. Recommended: one calm line** under the two rows ("Only use sounds you have the right to use."). No dialog, no click-through. It gets the lawyer's read with the license sentences.
   - B. Nothing. Cleanest, but a user who imports a copyrighted song to post gets a muted post (PRD risks) with no warning from us.
   - C. A one-time dialog on the first import. More friction than the risk justifies; users click through.

## Requests to the principal mobile engineer
- **Pickers:** `ACTION_OPEN_DOCUMENT` with `audio/*` for audio (single file); the photo picker `PickVisualMedia` `VideoOnly` for video (single). Both declare no permission. A phone without a document picker gets the "no picker" sheet. Verify names against current androidx.activity docs when the ticket starts.
- **Copy and check:** copy to the app's own files directory (not cache), temporary name then rename, delete leftovers on cancel and at launch (P1-02 request 3). Check a playable audio track and a known duration; reject over 10 minutes from metadata before copying; check space with `StorageManager.getAllocatableBytes` using P1-02's headroom unless you advise a smaller one for audio.
- **Format:** convert to AAC 128 kbps `.m4a` on import (Media3 Transformer, audio only; the same call extracts a video's track) so playback, trim and iOS behave the same. Say if you prefer to keep the source codec. Imported audio is **not loudness-normalized** (the catalog is, −16 LUFS), so it may be louder or quieter than library sounds: the Volume tool is the fix and no UI says so.
- **Waveform:** compute peaks at import if the engine already has the routine for P3-09; if not, the rail fallback (P3-03) is acceptable for v1.
- **Data:** a local `my_sounds` table (id, name, duration, source `file` or `video`, path, created at), never synced. A project that uses one copies the file into its own folder (`mediaCopyPaths` / `withMediaCopyPaths`, STATUS watch list). Delete with Undo keeps the file until the toast ends.
- **Backup:** say whether the My sounds folder is in Android's backup rules. If it is, "never uploaded" means by Memix and the in-app line stays accurate; if you exclude it, say so in the handoff.
- **Never log** file names, paths, URIs, lengths or sizes (analytics or Crashlytics).
- **Strings:** the keys above in the five `strings.xml` files; shared strings in the shared module (P3-02 rule).
- **Icon to draw:** `folder` (Tokens).

## Tokens
No new token (`nav-row-height` 56 exists, `MemixSize.navRowHeight`). **One new icon: `folder`** (`MemixIcons.Folder`, drawn to the grammar in DESIGN_SYSTEM → Iconography: a stroked folder outline with the tab drawn solid as the core). It was chosen over an upload arrow because an upload glyph would promise the opposite of "never uploaded".

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| The document picker and the photo picker need no storage or media permission; one-time access to a user-chosen file should use them, not a broad permission | Guidance | [Android: open files using the Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider), [Photo picker](https://developer.android.com/training/data-storage/shared/photo-picker) |
| Android decodes MP3, AAC (M4A), WAV (PCM), FLAC, Vorbis and Opus; support for exotic codecs varies by phone, so the "unsupported" state is needed and the copy names the three safe formats | Guidance | [Android supported media formats](https://developer.android.com/media/platform/supported-formats) |
| Progress for waits over about 10 s should show percent done; the minimum display time stops a flash | Guidance | [NN/g: progress indicators](https://www.nngroup.com/articles/progress-indicators/), [ContentLoadingProgressBar](https://developer.android.com/reference/androidx/core/widget/ContentLoadingProgressBar) |
| Undo beats confirmation for reversible actions | Guidance (expert) | [Aza Raskin, "Never use a warning when you mean undo"](https://alistapart.com/article/neveruseawarning/) |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| A 10-minute file is about 10 MB at AAC 128 kbps (128 kbit/s × 600 s ÷ 8) | Computed | arithmetic |
| People will want to cut a part of a long file rather than import short clips | Assumption (product reasoning; the test below checks it) | none |

## Test script for the owner (5 people, a phase build)
Tasks: (1) "Get the sound from this video of your dog onto a new meme." (2) "Import a voice note from your files and use it in the editor." (3) "Remove a sound you imported by mistake, then bring it back." (4) "Try a picture file as a sound." Observe: which of the two rows people pick first, whether the 10-minute message is understood, whether people look for the sound in the library after importing. Success: task 1 in under 45 s without help; 3 finds Undo. Notes template: task, time, taps, what they said, where they hesitated.
