# Voiceover recording · P3-08

Part of the Phase 3 sound specs. Read first, and not repeated here:
- `P1-06-clip-edits-and-audio.md`: the tool bars (main bar and clip bar), the Volume ToolPanel, the landing rules, the bonk and haptics, undo naming, toasts in the stage slot.
- `P1-05-timeline.md`: the Audio lane (`track-audio` green, label "Voiceover" is named there for P3), lanes, the past-the-end wash.
- `P1-04-editor-and-preview.md`: the transport row, play/pause rules, the one-slot toast and the save banner.
- `P3-09-audio-effects.md`: what the user does with the take next (waveform, Audio effects, voice-changer presets).

No board shows it. Components: ToolPanel (video-editor variant, covers the timeline and tool bar), Button (`danger` variant, `secondary`), ProgressBar-style rail, Toast, ToolBar. Voiceover is new in the main tool bar.

## Job
"Say something over my meme": record my voice onto the timeline while the video plays, then hear it back in the right place. **Done when** (TICKETS): the recording stays in sync with the preview. **The PM's bar** (proposal 10 Nov, clarification 5): a clap test; the clap's onset in the exported file is within 2 frames (67 ms) of the clap in the preview, with the phone's own or a wired microphone. **Permission** (clarification 7): the microphone is asked once, at the first tap on Record, with Android's own dialog.

**The one blue action:** none. A recording is not a meme sound, so nothing here is blue; the Record and Stop buttons use the `danger` variant, because DESIGN_SYSTEM gives `danger` to "recording states, always with a word or icon". The screen's one blue action stays Export (top bar). Done is Ghost, as every ToolPanel (ToolPanel README).

**Tap count:** Voiceover tool (1), Record (2), speak, Stop (3), Done (4). The first time, Android's permission dialog adds one tap after Record. Recording starts 3 seconds after the last tap.

## Entry and exit
- **Entry:** the **Voiceover** tool, the fifth in the video editor's main tool bar (Edit, Meme sounds, Text, Add media, **Voiceover**; ToolBar README updated). It is not shown in an empty project (no clips, so no length to record against): Add media stays the only tool there. Opening the panel does **not** ask for the microphone; only a Record tap does.
- **The panel** is the ToolPanel over the timeline and tool bar region, as Volume is: the preview and the transport row stay visible and live, the covered region leaves TalkBack and keyboard focus (`coveredByModal`). Pausing playback first: opening the panel pauses the editor, as every edit (P1-04).
- **Exit:**
  - **Done**, system back and Escape close the panel when nothing is recording. While recording or counting down they do what Stop or Cancel does (below), so no take is lost to a back press.
  - After a take has landed, **Done** closes the panel and **selects the newest take**, so the clip bar (Volume, Split, Delete, Audio effects, Move here, Duplicate) is under the thumb and a voice-changer is one tap away (P3-09).
  - With no take made, Done closes the panel and nothing is selected.
- **Kept:** every landed take is a normal Audio-lane clip in the project (autosave, drafts). **Not kept:** a take shorter than 0.5 s, or one with no sound at all (below).
- **Closing the editor (X) during a recording** stops it, keeps the take (when it is long enough) and then closes: the draft saves as always.

## Layout
Panel region at 360 × 640 is about 229 dp tall (the timeline's 157 plus the tool bar's 72; P1-04), so after the 48 dp title row about 181 dp remain. Every state below fits that at 100% font without scrolling; at 200% font the content scrolls under the fixed title row (ToolPanel README).

Title row: **Voiceover** (`title`, a heading), **Done** (Ghost) at the end. Content `space-4` padding, `space-3` between rows.

### Ready
1. **Hint:** `voiceover_hint` in `body` `text-secondary`, up to 2 lines.
2. **Record:** a `danger` Button, full width, `touch-target` tall, the `mic` icon (20 dp, `on-primary`) and "Record" (`on-primary` on `danger` 5.7:1). Pressed: `danger-pressed`.
3. **Route note** (only when it applies, `label` `text-secondary`, wraps, never cut): no headphones connected: `voiceover_muted_note`; Bluetooth headset connected: `voiceover_bluetooth_note`. Never both (a Bluetooth headset counts as headphones, so the video's sound plays in it).

### Counting down (3 s)
- The Record button's place shows the number **3, 2, 1** in `display-xl` (`text`, centered, changes once a second with no scale or fade) and a **Secondary Cancel** button under it, full width. The video and the playhead do not move during the countdown; the microphone is not open yet.
- Why a countdown: the user's finger has just left the screen, and on the first take Android's dialog has just closed; a take that starts on the tap records the tap and a breath.
- Tapping Cancel, Done, back or Escape cancels: nothing recorded, back to Ready.

### Recording
1. **Status row** (`touch-target` tall): a 12 dp `danger` dot (static, not pulsing: nothing loops, MOTION 2), the word "Recording" in `label` `text`, and at the end the take's length in `timecode` `text` (`00:04.20`, counting up).
2. **Level meter:** a `rail-height` rail (`hairline`) with a `text` fill that follows the microphone level, at most 20 updates a second, full width. It is a live reading, not an animation, so reduce motion does not change it. It is hidden from screen readers.
3. **Stop:** the `danger` Button, `stop` icon and "Stop", full width, under the meter.
4. **Quiet hint** (appears after 3 s of near-silence, `label` `text`, replaces the route note's place): `voiceover_quiet_hint`; it goes when sound returns.
The transport row above shows the video playing and the project timecode; the timeline under the panel scrolls with the playhead as in any playback, unseen.

### After Stop
Back to Ready with the hint line replaced by `voiceover_saved` ("Voiceover added at 0:03"), so the user knows a take landed behind the panel. Record again starts the next take **at the playhead, which sits at the end of the last take**, so narration goes line after line; to redo a line, Undo, then Record.

## What recording does
- **Where it starts:** the playhead when the countdown ends. The video plays from there, in step with the recording.
- **Where it lands:** an Audio-lane clip (`track-audio`, label "Voiceover") starting at that playhead, on the first Audio lane free for its whole length, else a new lane (the P1-06 lane rule). Volume 100%, trim in 0, trim out = the take's length.
- **What the user hears while recording:**
  - **Headphones (wired, USB or Bluetooth):** the project plays as in a normal preview (original audio, meme sounds, audio), so the user can time a line to a hit.
  - **No headphones:** the project plays **silent**, the picture still runs, and `voiceover_muted_note` says why. Otherwise the speaker's sound goes back into the microphone (Questions 2). This changes nothing in the project (no mute is saved, no undo step).
- **Which microphone:** the phone's own, or a wired or USB headset's. **A Bluetooth headset's microphone is not used** (Questions 5): it would put the take on a low-quality phone-call route and add delay that no compensation can promise, outside the 2-frame bar; the note says so. No Bluetooth permission is asked.
- **Sync (the bar):** the engineer measures the phone's input latency and places the take so that what was said at picture time T sits at T on the timeline. The clap test is the proof (QA compares).
- **Stops on its own:** at **the end of the video** (the clip ends exactly there; toast `voiceover_end_toast`, a Confirm haptic) because the project never lengthens to fit a sound (P1-05; Questions 3); when the phone runs low on space; and on any interruption (below).
- **Stop:** a tap on Stop (or the Play/Pause button, a tap on the stage, Done, back, Escape) stops the video and the take together. A take of **0.5 s or more** lands: one Confirm haptic, `a11y_voiceover_added`, one undo step "Voiceover". The bonk is not played (the panel covers the clip); the Confirm haptic is the "it landed" signal.
- **While recording** the controls that would change the project (Undo, Redo, Export, the ratio pill, the original-audio toggle under the panel) explain on tap with `voiceover_stop_first_toast` instead of acting: they keep their look, as every blocked control (DESIGN_SYSTEM → States). The editor keeps the screen awake.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Ready, headphones | Hint, Record, no note | `voiceover_hint` |
| Ready, no headphones | Hint, Record, silent-video note | `voiceover_muted_note` |
| Ready, Bluetooth headset | Hint, Record, phone-microphone note | `voiceover_bluetooth_note` |
| First Record tap, no permission yet | Android's own dialog over the editor; the panel keeps Ready behind it. Grant: the countdown starts at once | none (system text) |
| **Permission denied** (the user said No to that dialog) | The Record button and hint are replaced by the explanation and a **Secondary Open settings** button (opens Memix's page in system settings); no second system dialog (asked once). Focus goes to the explanation | `voiceover_denied`, `voiceover_open_settings` |
| **Permanently denied** (denied earlier and the system no longer asks; or turned off later in Settings; or the system refuses the dialog) | The same explanation and button. Coming back from Settings with the permission on returns to Ready, no auto-start | same |
| Playhead at the end of the video (within 0.5 s of it) | Record explains, nothing starts; the playhead does not jump to 0:00 (unlike Play) | `voiceover_at_end_explain` |
| Counting down | 3-2-1, Cancel | `voiceover_countdown_a11y` |
| Recording | Status row, meter, Stop; video plays | `voiceover_recording`, `voiceover_stop` |
| Quiet for 3 s | The quiet hint | `voiceover_quiet_hint` |
| Reached the end of the video | Take lands, Ready with "Voiceover added", toast 4 s | `voiceover_end_toast` |
| Stopped, 0.5 s or longer | Take lands; Ready with the saved line | `voiceover_saved`, `a11y_voiceover_added` |
| **Nothing recorded** (under 0.5 s, or no sound above the noise floor in the whole take) | Nothing lands; toast 4 s; Ready | `voiceover_too_short_toast`, `voiceover_silent_toast` |
| **Interrupted** (call, alarm, another app takes the microphone, headset unplugged or switched, screen off, Home or app switch) | The take stops at that moment and is kept if it is 0.5 s or longer, with the video paused. In the foreground: toast 4 s. If the user left Memix, the toast shows on return. Memix has no foreground service, so it never records in the background | `voiceover_interrupted_toast` |
| Microphone busy at the start (another app holds it) | Nothing starts; toast 4 s; Ready | `voiceover_busy_toast` |
| Phone storage low | Before the countdown (under 100 MB free, the P1-02 headroom): toast and nothing starts. During a take: it stops and keeps what was written | `voiceover_no_space_toast`, `voiceover_interrupted_toast` |
| Blocked controls while recording | Toast, nothing changes | `voiceover_stop_first_toast` |
| Offline | Not applicable: recording needs no connection | none |
| Long content, 5 languages, 200% font | Hint and notes wrap, never cut; Record and Stop stay full width and may take two lines; the countdown number stays one line; panel content scrolls under the title row | none |
| First run | As any run; no coach mark. The permission dialog is the only first-time step | none |

**The take is not saved on a crash** (Assumption, not scope): the file is written as it records and the clip joins the project when the take ends; if the process dies during a take, the half file is cleaned at the next start (`LeftoverMediaCleaner`). Say so to the owner if long voiceovers matter (Questions 6).

## Interactions
- **Record:** permission check, then countdown, then recording. Tapping Record twice quickly is one tap (the button becomes Cancel).
- **Stop:** one tap, no confirmation (a take is undoable).
- **Haptics:** Confirm when a take lands or the end of the video stops it (the one "it landed" signal, as meme sounds); nothing at the countdown, the start or a cancel.
- **Keyboard:** Tab reaches Record or Stop (or Cancel) and Done; Enter activates; Escape does what back does.
- **Selection is white, primary is blue:** neither is used in the panel; `danger` carries the recording state with a word and an icon (never color alone).

## Motion
- Panel opens and closes: 200 ms slide (`duration-sheet`); reduce motion 120 ms fade (instant on Android, as DESIGN_SYSTEM → Motion).
- Countdown digits and Record to Cancel/Stop label swaps: an instant change; button press 120 ms (`duration-press`).
- Meter fill follows the level (a reading, not motion); the status dot is static; nothing loops (MOTION 2).
- Toasts: 120 ms fade.

## Copy
English source; id, es, pt and hi are machine drafts (allowed until P5-06). **Budget** = the longest allowed in any of the five languages, in characters. Tone: verb first, "you", sentence case, no emoji, no exclamation marks. Terms follow the existing editor strings: id "playhead", es "cabezal", pt "cursor", hi "प्लेहेड"; sound words "suara", "sonido", "som", "साउंड".

| Key | English | Budget | id | es | pt | hi |
| --- | --- | --- | --- | --- | --- | --- |
| `tool_voiceover` | Voiceover | 20 | Rekam suara | Voz en off | Narração | वॉइसओवर |
| `timeline_voiceover` | Voiceover | 24 | Rekaman suara | Voz en off | Narração | वॉइसओवर |
| `voiceover_hint` | Records at the playhead while your video plays. | 56 | Merekam dari playhead sambil videomu diputar. | Graba desde el cabezal mientras se reproduce tu video. | Grava a partir do cursor enquanto o vídeo toca. | वीडियो चलते समय प्लेहेड से रिकॉर्ड करता है। |
| `voiceover_record` | Record | 16 | Rekam | Grabar | Gravar | रिकॉर्ड करें |
| `voiceover_stop` | Stop | 16 | Berhenti | Detener | Parar | रोकें |
| `voiceover_recording` | Recording | 16 | Merekam | Grabando | Gravando | रिकॉर्डिंग जारी है |
| `voiceover_muted_note` | Your video plays silent so the microphone only hears you. Use headphones to hear it. | 110 | Videomu diputar tanpa suara agar mikrofon hanya mendengar kamu. Pakai headphone untuk mendengarnya. | Tu video se reproduce sin sonido para que el micrófono solo te oiga a ti. Usa auriculares para oírlo. | O vídeo toca sem som para o microfone ouvir só você. Use fones de ouvido para ouvi-lo. | रिकॉर्ड करते समय वीडियो बिना आवाज़ के चलता है, ताकि माइक सिर्फ़ आपको सुने। उसे सुनने के लिए हेडफ़ोन लगाएं। |
| `voiceover_bluetooth_note` | Recording uses your phone's microphone, not the Bluetooth headset. | 80 | Merekam memakai mikrofon ponsel, bukan headset Bluetooth. | Se graba con el micrófono del teléfono, no con el auricular Bluetooth. | A gravação usa o microfone do celular, não o fone Bluetooth. | रिकॉर्डिंग फ़ोन के माइक से होती है, ब्लूटूथ हेडसेट से नहीं। |
| `voiceover_quiet_hint` | We can't hear anything. Check that nothing covers the microphone. | 80 | Tidak ada suara terdengar. Pastikan tidak ada yang menutupi mikrofon. | No se oye nada. Comprueba que nada tape el micrófono. | Não ouvimos nada. Confira se nada cobre o microfone. | कुछ सुनाई नहीं दे रहा। देखें कि माइक ढका तो नहीं है। |
| `voiceover_saved` | Voiceover added at {time}. Record again to keep going. | 64 | Rekaman suara ditambahkan di {time}. Rekam lagi untuk lanjut. | Voz en off añadida en {time}. Graba otra vez para seguir. | Narração adicionada em {time}. Grave de novo para continuar. | {time} पर वॉइसओवर जुड़ गया। आगे बढ़ने के लिए फिर रिकॉर्ड करें। |
| `voiceover_denied` | Memix can't record without the microphone. Turn it on in Settings, then come back. | 100 | Memix tidak bisa merekam tanpa mikrofon. Aktifkan di Pengaturan, lalu kembali. | Memix no puede grabar sin el micrófono. Actívalo en Ajustes y vuelve. | O Memix não pode gravar sem o microfone. Ative nas Configurações e volte. | माइक के बिना Memix रिकॉर्ड नहीं कर सकता। सेटिंग में उसे चालू करें, फिर वापस आएं। |
| `voiceover_open_settings` | Open settings | 20 | Buka Pengaturan | Abrir ajustes | Abrir configurações | सेटिंग खोलें |
| `voiceover_at_end_explain` | Move the playhead before the end of your video to record. | 70 | Geser playhead ke sebelum akhir video untuk merekam. | Mueve el cabezal antes del final del video para grabar. | Mova o cursor para antes do fim do vídeo para gravar. | रिकॉर्ड करने के लिए प्लेहेड को वीडियो के अंत से पहले ले जाएं। |
| `voiceover_end_toast` | Reached the end of your video. Your recording is saved. | 70 | Sampai di akhir videomu. Rekamanmu tersimpan. | Llegaste al final de tu video. Tu grabación está guardada. | Chegou ao fim do vídeo. Sua gravação foi salva. | वीडियो खत्म हो गया। आपकी रिकॉर्डिंग सेव हो गई है। |
| `voiceover_interrupted_toast` | Recording stopped. Your take so far is saved. | 70 | Rekaman berhenti. Hasil sejauh ini tersimpan. | La grabación se detuvo. Lo grabado hasta ahora está guardado. | A gravação parou. O que foi gravado até aqui foi salvo. | रिकॉर्डिंग रुक गई। अब तक का हिस्सा सेव है। |
| `voiceover_too_short_toast` | That was too short to keep. Try again. | 56 | Terlalu singkat untuk disimpan. Coba lagi. | Fue demasiado corto para guardarlo. Inténtalo de nuevo. | Ficou curto demais para guardar. Tente de novo. | यह रिकॉर्डिंग बहुत छोटी थी। फिर से आज़माएं। |
| `voiceover_silent_toast` | Nothing was recorded. Check that your microphone is on, then try again. | 90 | Tidak ada yang terekam. Pastikan mikrofon menyala, lalu coba lagi. | No se grabó nada. Comprueba que el micrófono esté activado e inténtalo de nuevo. | Nada foi gravado. Confira se o microfone está ligado e tente de novo. | कुछ रिकॉर्ड नहीं हुआ। देखें कि माइक चालू है, फिर दोबारा कोशिश करें। |
| `voiceover_busy_toast` | The microphone is in use by another app. Close it and try again. | 90 | Mikrofon sedang dipakai aplikasi lain. Tutup aplikasi itu lalu coba lagi. | Otra aplicación está usando el micrófono. Ciérrala e inténtalo de nuevo. | Outro app está usando o microfone. Feche-o e tente de novo. | माइक किसी दूसरे ऐप में चल रहा है। उसे बंद करें और फिर कोशिश करें। |
| `voiceover_no_space_toast` | Not enough space to record. Free up some space, then try again. | 90 | Ruang tidak cukup untuk merekam. Kosongkan ruang, lalu coba lagi. | No hay espacio para grabar. Libera espacio e inténtalo de nuevo. | Sem espaço para gravar. Libere espaço e tente de novo. | रिकॉर्ड करने की जगह नहीं है। जगह खाली करें, फिर कोशिश करें। |
| `voiceover_stop_first_toast` | Stop recording first. | 36 | Hentikan rekaman dulu. | Detén la grabación primero. | Pare a gravação primeiro. | पहले रिकॉर्डिंग रोकें। |
| `voiceover_countdown_a11y` | Recording starts in 3 seconds | 40 | Merekam dimulai dalam 3 detik | La grabación empieza en 3 segundos | A gravação começa em 3 segundos | रिकॉर्डिंग 3 सेकंड में शुरू होगी |
| `voiceover_stop_a11y` | Stop recording | 24 | Hentikan rekaman | Detener grabación | Parar gravação | रिकॉर्डिंग रोकें |
| `a11y_voiceover_added` | Voiceover added at {time}, {length} long | 60 | Rekaman suara ditambahkan di {time}, panjang {length} | Voz en off añadida en {time}, de {length} | Narração adicionada em {time}, com {length} | {time} पर वॉइसओवर जुड़ा, {length} लंबा |
| `edit_voiceover` | Voiceover | 20 | Rekaman suara | Voz en off | Narração | वॉइसओवर |

Notes for translators: "Voiceover" is the voice laid over a video (not "dubbing"); `tool_voiceover` is a tool label (two lines of about 10 characters). "Take" in the English toasts means one recording, so write "what you recorded". `{time}` and `{length}` are spoken times ("3.2 seconds"). Hindi: check the two-line wrap of `voiceover_muted_note` and the 200% line height of the Record button.

Also reused (no change): Cancel and Done (`panel_done`), `editor_save_*` for a failed save.

## Accessibility
- **Names (WCAG 2.5.3):** the tool "Voiceover"; Record "Record"; Stop: visible "Stop", name `voiceover_stop_a11y` "Stop recording" (contains the label); Cancel "Cancel"; Open settings as visible; the panel title is the pane title and takes focus on open (ToolPanel README); focus returns to the Voiceover tool when the panel closes.
- **Announcements (polite, never taking focus, SC 4.1.3):** the countdown is **one** announcement ("Recording starts in 3 seconds"), not three digits (TalkBack would talk over them); then "Recording" when it starts (assertive is not used); every toast; "Voiceover added at 3.2 seconds, 4 seconds long" when a take lands. The live timecode is not announced each tick; a screen-reader user hears it when they focus it. The meter and the dot are hidden from the tree.
- **A blind user can record:** Record and Stop are plain buttons in a fixed place, the take ends with an announcement, and the mute-while-recording rule keeps TalkBack's own voice out of the take only if the user wears headphones; the muted-note says so in text.
- **Focus:** after Record is tapped, focus stays on the same button, which now reads Cancel then Stop. After the permission dialog returns, focus is on Record or on the explanation.
- **Contrast:** `on-primary` on `danger` 5.7:1, on `danger-pressed` 4.8:1; `text` on `surface` 12.4:1; `text-secondary` on `surface` 5.7:1; the meter fill `text` on a `hairline` rail 10:1 or more (UI part 3:1); the dot `danger` on `surface` 5.2:1 and it comes with the word "Recording".
- **Targets:** every control 48 dp or more; Record and Stop full width.
- **200% font:** see States. **Reduce motion:** nothing in this panel moves except the slide. **Switch Access:** no gesture-only action (no hold-to-record on purpose: a held press is hard for some users and fails WCAG 2.5.1's single-pointer spirit).

## Analytics
- `tool_use {editor: "video", tool: "voiceover"}` once per take that lands in the project (not per tap, not for a discarded or empty take). **PM adds the id `voiceover`** to `EditorTool` (new, like `add_media`).
- No `sound_add`: a voiceover is not a library or imported sound. No event for the permission result, the countdown, or an interruption (no extra data collection; Crashlytics non-fatal logging of the error class for a failed start, by the engineer).
- **Play Data safety (P6-05):** microphone audio is recorded on the phone and never leaves it. The privacy policy line is the PM's.

## QA compares
Screenshots at 360 dp wide, English unless noted, in `docs/ux/reviews/P3-08/`:
1. `ready-headphones`, `ready-no-headphones`, `ready-bluetooth`: the panel over the editor with the three notes.
2. `countdown`, `recording` (dot, "Recording", timecode, meter), `after-stop` (saved line).
3. `denied` and `permanently-denied` (Open settings), `at-end-explain`.
4. `recording-hi-200`: Hindi at 200% font while recording (Stop reachable).
5. `take-on-timeline`: after Done, the green "Voiceover" clip selected with its clip bar.
By recording or note:
- **The clap test (the bar).** Record a clap with the video playing; export; compare the clap's onset in the exported file with the preview's playback of the same take: within 67 ms (2 frames at 30 fps). Do it with the built-in microphone (video silent) and with a wired headset. QA chooses the measuring method (waveform editor on both files); Bluetooth is a note, not a pass or fail.
- Permission flow on a fresh install: no dialog at the tool tap, one dialog at Record; then deny, grant in Settings and return.
- Interruptions: an incoming call, Home during a take, unplugging a wired headset, the take's end at the video's end, 0.5 s taps, and a silent take (microphone covered).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on.
1. **A countdown before recording?**
   - **A. Recommended: 3 seconds, with Cancel.** The user just lifted a finger (and, the first time, closed a permission dialog); a take that begins on the tap records the tap. Costs 3 s per take.
   - B. No countdown. One second faster, but takes start with a thud and the first one starts the moment the dialog closes.
   - C. A setting for it. There is no Settings screen in v1.
2. **What plays during a take when no headphones are connected?**
   - **A. Recommended: the picture plays, the project's sound is silent**, and a note says so. The microphone only hears the user. Costs: the user can't time a line to a sound without headphones.
   - B. Play everything through the speaker. Timing is easy, but the speaker's sound goes into the take (echo, a doubled meme sound that then sits under the user's effects) and cannot be removed afterward.
3. **Recording past the end of the video.**
   - **A. Recommended: it stops at the end and keeps the take.** The video never grows to fit a sound (P1-05), and a voice past the end would not be heard.
   - B. It keeps recording and the clip hangs past the end (the wash shows). Gives a long take to trim, but records something that will never play.
4. **Asking again after the user says No.**
   - **A. Recommended: ask once (the PM's rule); after a No the panel explains and offers Open settings.** One extra trip to Settings for someone who tapped No by mistake.
   - B. Ask once more at the next Record tap (Android allows two requests). Friendlier, but breaks "asked once".
5. **A Bluetooth headset's microphone.**
   - **A. Recommended: never use it; the phone's own microphone records, and a note says so.** Keeps takes at normal quality, inside the 2-frame bar, and needs no Bluetooth permission.
   - B. Use the headset's microphone. Convenient for wireless-earbud users, but the audio is phone-call quality with a delay of 100 ms or more, outside the bar, and needs the Bluetooth permission on Android 12 and up.
6. **A take lost when the process dies mid-recording** (low memory while the user is away). **Recommended: accept it in v1** (the take lands when it ends). Recovery of a half file would be a follow-up ticket for the PM to size if the first tests show long voiceovers.

## Requests to the principal mobile engineer
- **Tool:** `tool_voiceover` is the fifth main tool (after Add media), not shown in an empty project; `EditorTool.VOICEOVER("voiceover")`; the undo step name `edit_voiceover`.
- **New icons to draw:** `mic`, `stop` (DESIGN_SYSTEM → Iconography). The recording dot is drawn, not an icon file. No new token.
- **Permission:** request `RECORD_AUDIO` only from the Record tap; keep one "asked and denied" flag; Open settings uses the app-details settings intent; re-check the permission when the app resumes.
- **Capture:** input latency measured and compensated (the 67 ms bar); take written to the project's media folder as it records; keep the screen on; stop on audio-focus loss, route change, `onStop`, low space; no foreground service.
- **Playback during a take:** engine-only mute of project audio when no headphones (no project change, no undo step); the preview and the take start from the same clock.
- **Strings:** the keys above in the five `strings.xml` files.
- **Landing:** the take is one commit (clip on the lane, one undo step "Voiceover"); selection set to it when Done closes the panel.

## Tokens
None new. Uses `danger`, `danger-pressed`, `on-primary`, `rail-height`, `display-xl`, `touch-target`. New icons: `mic`, `stop` (Kotlin names `MemixIcons.Mic`, `MemixIcons.Stop`).

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Android's runtime permission dialog should come at the moment of need, with no pre-explainer when the action is the request | Guidance | [Android: request runtime permissions](https://developer.android.com/training/permissions/requesting) |
| Apps can't record the microphone in the background without a foreground service, and from Android 11 the system can silence a background recording | Guidance | [Android: background microphone access](https://developer.android.com/training/permissions/requesting#background-access) and [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) |
| Bluetooth headsets switch to a low-quality, delayed voice route (SCO) when used as a microphone | Guidance | [Android: Bluetooth audio](https://developer.android.com/develop/connectivity/bluetooth/profiles) |
| Status messages (countdown, saved, interrupted) must reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Press-and-hold as the only way to record is a pointer gesture some users can't do | Guidance | [WCAG 2.2 SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures.html) |
| A countdown before recording is common in mobile editors | Assumption (product knowledge; check CapCut and VN when the owner can open them) | none |
| A speaker's sound re-enters the microphone of the same phone | Assumption (general acoustics; QA hears it with the project silent and unsilenced) | none |

## Test script for the owner (5 people, a phase build)
Tasks: (1) "Add your voice saying something over the clip, starting at the funny part." (2) "Do it again, better." (3) "Make your voice sound deeper." (Leads into P3-09.) Observe: whether people find Voiceover without help; what they do at the permission dialog; whether they notice the video is silent without headphones and understand why; whether they stop with the button or the stage. Success: task 1 in under 30 s, no help. Notes template: task, time, taps, what they said, where they hesitated.
