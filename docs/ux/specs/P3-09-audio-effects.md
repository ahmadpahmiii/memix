# Audio effects and timeline waveforms · P3-09

Part of the Phase 3 sound specs. Read first, and not repeated here:
- `P1-06-clip-edits-and-audio.md`: the clip tool bar, the Volume ToolPanel (this panel follows its shape), landing and Move rules, the lane rule, undo naming, "Preview catching up".
- `P1-05-timeline.md`: lanes, clip look and labels, the past-the-end wash, zoom.
- `P3-02`, `P3-03`: the sound row and the details waveform (bars, `waveform-bar-width`, `waveform-bar-gap`). `P3-08-voiceover.md`: where voiceover clips come from. `P3-07-my-sounds.md`: imported audio.
- `P2-04-image-tools.md`: the panel pattern with tabs and a header Reset that this spec copies.

No board shows it. Components: ToolPanel, Tabs, Slider (with `origin` and `snapValue`), SegmentedChoice, OptionTile (new label layout, defined in `P3-11-region-sheet.md`), Toast, ToolBar, Timeline.

## Job
"Make this sound mine": fade it, shift its pitch or speed, add echo, bass or distortion, or turn a voice into a chipmunk, and **see where it is** on the timeline. **Done when** (TICKETS): preview and export sound the same. **The PM's bar** (proposal 10 Nov, clarification 6): the preview chain and the export chain are the same effect spec (rule 5); an offline render of each preset through both paths differs by less than −40 dB (null test), plus an ear check on the phone for each preset.

**The one blue action:** none. Effects on a meme sound or an audio clip are sound UI; the panel's Reset is a Quiet button (as P2-04) and the tab dots are `primary`. Done is Ghost (ToolPanel README). Selection is white (the selected tab's underline).

**Tap count:** a sound or audio clip (1), Audio effects (2), a tab and a slider or tile (3, 4). Fade and pitch are two taps and a drag from the clip.

## Split line (for the PM: this ticket is L)
Natural halves, as the PM proposed (clarification 6). **Build the model once:** 09a adds **all** effect fields with their defaults in one schema bump, so 09b needs no second bump or migration (P3-08 adds none).

| Part | What it contains |
| --- | --- |
| **P3-09a** | Waveforms on the timeline (all of "Waveforms on the timeline" below); the tool, the panel shell (title, Reset, Done, Tabs, tab dots, undo names, the "Preparing audio" status, scroll and pinned rules); the **Tone** tab (Pitch, Speed, with the length change and lane rule) and the **Fades** tab; the model fields for everything; the parity harness (null test) for Fades, Pitch and Speed |
| **P3-09b** | The **Bass** tab (Bass boost, Distortion), the **Echo** tab (Echo, Length, the tail), the **Voice** tab (six presets, using the OptionTile label layout that P3-11 already built), the peak limiter, the per-preset ear check |

Before 09b ships the panel shows only the tabs that exist (Tone, Fades), never a disabled placeholder; the tab order below is the final one and a tab sits one place to the left until the tab before it is built (the ToolBar rule).

## Entry and exit
- **Entry:** the **Audio effects** tool in the clip tool bar of a **meme sound** or an **audio clip** (a voiceover, an imported sound, detached original audio). Sound clip tools become **Volume · Split · Delete · Audio effects · Move here · Duplicate** (ToolBar README updated). Audio effects is fourth so it shows without scrolling at 360 dp (the bar shows about four and a half tools after the Close button); Move here and Duplicate each move one place right and stay reachable by scrolling and by the item's accessibility actions.
- **Not on a video clip's attached sound.** To change it, tap **Detach audio** first, then Audio effects on the new green clip (Questions 2). A video clip's tool bar is unchanged.
- **Opens** the ToolPanel over the timeline and tool bar (as Volume): the preview and transport stay visible and live; the playhead keeps its place; playback keeps running if it was running, so the user hears each change (Volume precedent). The selection stays.
- **Closes:** Done, system back, Escape (the selection stays); the item is deleted or removed by undo (the panel closes, ToolPanel rule). Focus returns to the Audio effects tool.
- **Tab remembered** for the rest of the visit, else the first tab.

## Layout
Panel region at 360 × 640 is about 229 dp (P1-04: timeline 157 plus tool bar 72). Title row 48 plus tabs 48 leave about 133 dp of content, and every tab below is built to fit that at 100% font: two sliders (108 dp), a tile row and a hint, or one slider and one segmented row (128 dp).
- **Title row:** "Audio effects" (`title`, heading), **Reset** (Quiet) and **Done** (Ghost). Reset and Done wrap under the title at large font (P2-04 pattern).
- **Tabs** (the Tabs component, `Tabs` not SegmentedTabs because there are five, and because an editor panel that grows by ticket takes Tabs from its first ticket): **Voice · Tone · Bass · Echo · Fades**, selected `text` with the white 2 px underline. The row scrolls sideways and the selected tab scrolls into view. **A dot** (8 dp, `space-2`, `primary`) after the label marks a tab that has anything set, so effects set on another tab are never forgotten; it is a second signal with the spoken state "on" (not color alone).
- **Pinned part:** title row plus tabs. If they would take more than `pinned-bar-max-fraction` (40%) of the panel at large font, the tabs scroll with the content and only the title row stays (the same token, a third use). The content scrolls under what is pinned.

### Voice (09b)
- A scrolling row of **OptionTiles, label layout**, edge to edge: **None · Chipmunk · Giant · Robot · Megaphone · Cave**. One choice, selected tile white (`on-primary` label). Selecting one is one undo step ("Voice").
- A hint under it in `label` `text-secondary`: `fx_voice_hint` "Voices stack with pitch, bass and echo."
- **Presets are a starting character, not a different mode:** a preset plays first in the chain and does **not** change any slider. Sliders show only what the user set; a preset plus pitch +3 is "more chipmunk". No preset id is hidden behind slider values.
- What each is (target character, numbers are the engineer's to tune by ear and the owner listens at QA): **Chipmunk** raises the pitch and brightens; **Giant** lowers it and adds weight; **Robot** a metallic, buzzing voice; **Megaphone** a thin, loud voice with a little grit; **Cave** a long echo with a dull top.

### Tone (09a)
- **Pitch:** a bipolar Slider (`origin` 0), **−12 to +12** semitones, step 1, default 0, snaps to 0. Readout `+3`, `−5`, `0` in `timecode`. It changes the pitch **without** changing speed.
- **Speed:** a Slider **0.5× to 2×**, step 0.1, default 1×, snaps to 1×. Readout `1.5×`. It changes speed **without** changing pitch, and **changes the clip's length on the timeline** (below).

### Bass (09b)
- **Bass boost:** Slider 0 to 100%, default 0 (off), step 10%, readout `40%`. Maps to up to about +12 dB below a few hundred hertz.
- **Distortion:** Slider 0 to 100%, default 0, step 10%. At 100% it is meant to be harsh (the meme "deep-fried" sound); the sound is never sanitized, only kept from clipping the file (limiter, Requests).

### Echo (09b)
- **Echo:** Slider 0 to 100%, default 0 (off), step 10%: how loud the repeats are.
- **Echo length:** SegmentedChoice **Short · Medium · Long** (about 150, 300 and 500 ms between repeats), default Medium. It has no effect while Echo is 0 and is stored anyway; nothing is dimmed (DESIGN_SYSTEM: controls stay enabled).
- **The tail** rings out after the clip ends, up to about 1.5 s, and is cut at the end of the video. It is not drawn on the timeline (the clip keeps its length), and preview and export do the same.

### Fades (09a)
- **Fade in** and **Fade out:** Sliders 0 to 5 s, step 0.1 s, default 0 (off), readout `1.2 s`. The two can never add up to more than the clip: dragging one stops at what the other leaves (SegmentTick at the stop, no toast), so a fade never pushes the other.
- A clip under 0.3 s is too short to fade: both sliders stay at 0 and a tap on one explains (`fx_fade_too_short`).

## Waveforms on the timeline (09a)
- **Where:** meme sound clips (blue) and audio clips (green): voiceover, imported, detached original audio. **Not** on main video clips (their thumbnails fill them; Questions 3), not on text, sticker or effect clips.
- **Look:** the P3-03 bars (`waveform-bar-width` 2 dp, `waveform-bar-gap` 2 dp, `radius-full` ends), centered on the clip's midline, in `on-track` at `waveform-clip-opacity` (60%). Maximum bar height: the clip's height (`track-height`, 28 dp) minus `space-1` above and below, 20 dp. A bar is never shorter than its width, so silence is a dotted line. About 3.3:1 on `track-audio` and 3.6:1 on `track-meme-sound` (computed by hand; QA measures a screenshot; the graphic needs 3:1, WCAG 2.2 SC 1.4.11). The clip's label (sticky, P1-05) sits on a **solid patch of the clip's color** (`space-1` side padding, `radius-sm`), so no bar runs behind a letter and the label stays `on-track` 6.8:1 and 7.9:1 as before.
- **What a bar is:** the sound's peaks over the clip's trimmed range, resampled to one bar per 4 dp at the current zoom (the maximum in each bucket), times the clip's **volume** (capped at the clip height) and its **fades** (a fade shortens the bars toward the edge, so a fade is visible as the shape). Pitch, echo, bass and distortion do not change the bars; speed changes how many bars the clip has. Drawn in the draw phase only: dragging a Volume or fade slider redraws bars with no recomposition (mobile-performance skill).
- **Peaks:** catalog sounds use `waveform_path` (P3-03; about 32 to 64 values, so a long zoom-in shows stepped bars, accepted); voiceover, imported and detached audio need peaks at about 100 a second, computed once when the clip is created and cached with the project's media (the engineer's call; Requests).
- **While peaks are missing or loading:** no bars and no placeholder; the label alone, as today. Bars fade in over 120 ms when they arrive. A file that can't be decoded shows nothing (the clip's existing missing-file look still applies).
- **Past the end of the video:** the wash covers bars as it covers everything (P1-05). **Selected:** the white outline and handles as ever; bars unchanged.
- **Screen readers:** the waveform is decorative (hidden); nothing is added to the clip's spoken name except "Audio effects on" when any effect is set.
- **Cost:** only bars inside the viewport are drawn; a 10-minute clip at the widest zoom draws a few hundred bars.

## The rules behind the panel
1. **Stacking.** Every group can be on at once. One fixed order, the same in preview and export (the spec in `:core:model`, rule 5): **Speed and pitch, then Voice, then Bass and Distortion, then Echo, then Fades, then Volume.** The user never orders them. A tab's dot says it is on; **Reset** (below) clears all. A hidden effect on another tab is the one confusion stacking can cause, which is what the dots are for.
2. **Reset** (the header's Quiet button, spoken "Reset all audio effects"): sets every effect of the selected clip back to its default in **one undo step** ("Reset effects"). Clip-wide, not per tab, because effects stack across tabs and one "why does it sound odd" has one answer. With nothing set, a tap explains with `fx_nothing_to_reset` and records nothing.
3. **The source is never changed.** Effects live in the project's clip. A catalog sound's cached file, a My sound and a recording stay as they were, so the same sound used twice can differ and Undo is exact.
4. **Speed changes the clip's length.** Its start stays; its end moves. If the longer clip would overlap the next item on its lane, the clip moves to the first lane of its kind that is free for its whole length, else a new lane (the P1-06 lane rule), the lanes slide as usual, and a 4 s toast says `fx_moved_lane_toast`. It never overwrites a neighbor and never shortens one. A clip that now runs past the end of the video shows the wash; no toast (it is visible). One undo step undoes length and lane together.
5. **Split, Duplicate, Trim.** **Split** copies every effect to both pieces, **except** that Fade in stays on the left piece and Fade out on the right one (otherwise a split would put a dip in the middle). **Duplicate** copies everything. **Trim** and Split keep fades inside the clip: a fade longer than the new clip is shortened to fit, in the same undo step as the trim. **Move here** and dragging change nothing about effects. **Replace** does not exist for sounds (P1-06).
6. **Meme sound or audio clip: the same panel, the same ranges.** What differs is only the material: a meme sound is short and already loudness-normalized (−16 LUFS, PRD), so its fade ranges are small and bass, distortion and pitch change it a lot; a voiceover or imported sound is long and a voice, so Voice and Fades matter most. No tab is hidden or reordered by kind: a lowered "bruh" is a classic meme, and a deeper voice is the headline of the feature. A **catalog sound used with effects keeps its title** on the clip; nothing renames it.
7. **No clipping.** The chain ends in a peak limiter at about −1 dBFS before Volume, so Bass and Distortion cannot make the exported file clip digitally (the engineer's call; same stage in preview and export). Volume above 100% keeps the P1-06 rule.
8. **Preview equals export.** The user hears the same chain in the editor and in the saved file. The preview never plays a lighter, different chain to save work.

### Preview on a slow phone
- **A fast phone** runs the chain live: sliders are heard while dragging, like Volume.
- **A phone that can't keep up** (the engine sees dropouts, or the chain is too heavy; its call) never plays a glitching preview: it plays a prepared copy of the clip with the chain applied (made in the background, the same chain as export) and **keeps playing the last prepared version until the new one is ready**, as the preview keeps its last frame (P1-06 → Preview catching up). On such a phone a slider is heard **after release**, not while dragging.
- **Up to 1 s: nothing shown.** After 1 s of waiting, a quiet status in the stage slot, `fx_preparing` ("Preparing audio"), in the Toast look with no spinner (MOTION 2); it fades out when the new sound is playing. One slot, banner first (P1-04).
- **Export is unaffected** by any of this: it always renders the chain at full quality. Say so only if the owner's test shows people worry; the status says "Preparing", not "slow".
- **QA:** the owner's phone is the yardstick (no reference phone); QA forces the prepared path with a debug switch (Requests) and runs the null test on it too.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Panel open, nothing set | Title, Reset, Done, tabs with no dots, the open tab at its defaults | `fx_title` |
| A slider being dragged (fast phone) | Heard live; readout moves; one undo step on release | none |
| A slider being dragged (slow phone) | Readout moves; heard after release; status after 1 s | `fx_preparing` |
| Effect set | The tab has a dot; the clip's spoken description says effects are on | `a11y_state_effects_on` |
| Fade at its limit | The slider stops; one SegmentTick; no toast | none |
| Clip too short to fade (under 0.3 s) | Sliders at 0; a tap explains | `fx_fade_too_short` |
| Speed makes the clip overlap its neighbor | The clip moves to another lane; lanes slide; toast 4 s | `fx_moved_lane_toast` |
| Speed pushes the clip past the end of the video | Wash on the overhang, nothing said | none |
| Reset with effects | All defaults, one step, dots gone | `edit_fx_reset` |
| Reset with nothing set | Toast 2 s, no step | `fx_nothing_to_reset` |
| A voice preset chosen | Tile white, the Voice tab's dot; sliders unchanged | `fx_voice_*` |
| A clip whose file is missing | The tool explains on tap and does not open | `fx_missing_explain` |
| Waveform, peaks loading or absent | Label only; bars fade in later | none |
| Waveform, silent sound | A dotted line of minimum bars | none |
| Preparing audio (slow phone) | Last prepared sound keeps playing; status after 1 s | `fx_preparing` |
| Undo removes the clip | The panel closes | none |
| Offline, permission | Not applicable (all on the phone, nothing to ask) | none |
| Long content, 5 languages, 200% font | Tabs scroll or scroll with the content (pinned rule); slider labels wrap to two lines beside their readouts; tiles widen, labels never cut; SegmentedChoice stacks at 200% (its rule); the panel content scrolls under the title row | none |
| First run | No coach mark; the first tab opens | none |

## Interactions
- **Sliders:** drag, or tap the rail; double-tap the thumb resets that slider; snap to the default with one SegmentTick (Pitch 0, Speed 1×, others at 0); keyboard and TalkBack steps land on the grid (Pitch 1, Speed 0.1×, Fades 0.1 s, others 10%). One undo step on release ("Pitch", "Speed", "Fade in", "Fade out", "Bass boost", "Distortion", "Echo").
- **Tiles and segments:** a tap applies at once, one step ("Voice", "Echo length"); tapping the selected one does nothing and records nothing.
- **Reset:** as above. **Done** closes.
- **The panel takes every touch in its region** (ToolPanel). **Tapping the stage** still toggles play, so the user can start and stop to listen.
- **Haptics:** SegmentTick for snaps and limits only.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Panel opens, closes | 200 ms slide (`duration-sheet`) | 120 ms fade (instant on Android) |
| Tab change | 200 ms cross-fade | 120 ms fade |
| Tab dot appears, tile or segment selects | 120 ms (`duration-press`) | same |
| Waveform bars arrive | 120 ms fade-in | same |
| A clip moving lane after Speed; clip end moving | 200 ms lane slide (P1-05) | 120 ms cross-fade |
| Status and toasts | 120 ms fade | same |
Nothing loops (MOTION 2): "Preparing audio" is text, not a spinner.

## Copy
English source; id, es, pt and hi are machine drafts until P5-06. **Budget** = longest in any of the five languages, in characters. Tone: verb first where it is an action, sentence case, no emoji, no exclamation marks. Audio terms follow the editor's: id "suara", es "sonido", pt "som", hi "साउंड".

| Key | English | Budget | id | es | pt | hi |
| --- | --- | --- | --- | --- | --- | --- |
| `tool_audio_effects` | Audio effects | 20 | Efek audio | Efectos de audio | Efeitos de áudio | ऑडियो इफ़ेक्ट |
| `fx_title` | Audio effects | 24 | Efek audio | Efectos de audio | Efeitos de áudio | ऑडियो इफ़ेक्ट |
| `fx_reset` | Reset | 14 | Reset | Restablecer | Redefinir | रीसेट |
| `fx_reset_a11y` | Reset all audio effects | 44 | Reset semua efek audio | Restablecer todos los efectos de audio | Redefinir todos os efeitos de áudio | सभी ऑडियो इफ़ेक्ट रीसेट करें |
| `fx_tab_voice` | Voice | 12 | Suara | Voz | Voz | आवाज़ |
| `fx_tab_tone` | Tone | 12 | Nada | Tono | Tom | टोन |
| `fx_tab_bass` | Bass | 12 | Bass | Graves | Graves | बास |
| `fx_tab_echo` | Echo | 12 | Gema | Eco | Eco | इको |
| `fx_tab_fades` | Fades | 12 | Fade | Fundidos | Fades | फ़ेड |
| `fx_fade_in` | Fade in | 26 | Fade in | Entrada gradual | Fade in | फ़ेड इन |
| `fx_fade_out` | Fade out | 26 | Fade out | Salida gradual | Fade out | फ़ेड आउट |
| `fx_pitch` | Pitch | 16 | Nada suara | Tono | Tom | पिच |
| `fx_speed` | Speed | 16 | Kecepatan | Velocidad | Velocidade | स्पीड |
| `fx_bass` | Bass boost | 20 | Penguat bass | Refuerzo de graves | Reforço de graves | बास बूस्ट |
| `fx_distortion` | Distortion | 20 | Distorsi | Distorsión | Distorção | डिस्टॉर्शन |
| `fx_echo` | Echo | 16 | Gema | Eco | Eco | इको |
| `fx_echo_length` | Echo length | 20 | Panjang gema | Duración del eco | Duração do eco | इको की लंबाई |
| `fx_length_short` | Short | 10 | Pendek | Corto | Curto | छोटा |
| `fx_length_medium` | Medium | 10 | Sedang | Medio | Médio | मध्यम |
| `fx_length_long` | Long | 10 | Panjang | Largo | Longo | लंबा |
| `fx_voice_none` | None | 10 | Tidak ada | Ninguna | Nenhuma | कोई नहीं |
| `fx_voice_chipmunk` | Chipmunk | 12 | Tupai | Ardilla | Esquilo | चिपमंक |
| `fx_voice_giant` | Giant | 12 | Raksasa | Gigante | Gigante | दानव |
| `fx_voice_robot` | Robot | 12 | Robot | Robot | Robô | रोबोट |
| `fx_voice_megaphone` | Megaphone | 12 | Megafon | Megáfono | Megafone | मेगाफ़ोन |
| `fx_voice_cave` | Cave | 12 | Gua | Cueva | Caverna | गुफ़ा |
| `fx_voice_hint` | Voices stack with pitch, bass and echo. | 56 | Efek suara digabung dengan nada, bass, dan gema. | Las voces se suman al tono, los graves y el eco. | As vozes se somam ao tom, aos graves e ao eco. | आवाज़ के इफ़ेक्ट पिच, बास और इको के साथ जुड़ते हैं। |
| `fx_seconds` | {n} s | 8 | {n} dtk | {n} s | {n} s | {n} से |
| `fx_speed_value` | {n}× | 8 | {n}× | {n}× | {n}× | {n}× |
| `fx_nothing_to_reset` | No effects to reset. | 36 | Belum ada efek untuk direset. | No hay efectos que restablecer. | Não há efeitos para redefinir. | रीसेट करने के लिए कोई इफ़ेक्ट नहीं है। |
| `fx_fade_too_short` | This sound is too short to fade. | 44 | Suara ini terlalu pendek untuk di-fade. | Este sonido es demasiado corto para un fundido. | Este som é curto demais para um fade. | यह साउंड फ़ेड के लिए बहुत छोटा है। |
| `fx_moved_lane_toast` | There wasn't room here, so the sound moved to another track. | 70 | Tidak ada ruang di sini, jadi suara dipindah ke trek lain. | No había espacio aquí, así que el sonido pasó a otra pista. | Não havia espaço aqui, então o som foi para outra faixa. | यहाँ जगह नहीं थी, इसलिए साउंड दूसरे ट्रैक पर चला गया। |
| `fx_missing_explain` | This sound's file is missing, so effects can't play. Delete it and add it again. | 90 | File suara ini hilang, jadi efek tidak bisa diputar. Hapus lalu tambahkan lagi. | Falta el archivo de este sonido, así que los efectos no se pueden oír. Bórralo y añádelo de nuevo. | O arquivo deste som sumiu, então os efeitos não tocam. Exclua e adicione de novo. | इस साउंड की फ़ाइल नहीं मिली, इसलिए इफ़ेक्ट नहीं चल सकते। इसे हटाकर फिर जोड़ें। |
| `fx_preparing` | Preparing audio | 24 | Menyiapkan audio | Preparando el audio | Preparando o áudio | ऑडियो तैयार हो रहा है |
| `a11y_state_effects_on` | Audio effects on | 24 | Efek audio aktif | Efectos de audio activados | Efeitos de áudio ativos | ऑडियो इफ़ेक्ट चालू |
| `a11y_fx_tab_on` | {tab}, on | 24 | {tab}, aktif | {tab}, activado | {tab}, ativo | {tab}, चालू |
| `edit_fx_voice` | Voice | 20 | Suara | Voz | Voz | आवाज़ |
| `edit_fx_echo_length` | Echo length | 20 | Panjang gema | Duración del eco | Duração do eco | इको की लंबाई |
| `edit_fx_reset` | Reset effects | 20 | Reset efek | Restablecer efectos | Redefinir efeitos | इफ़ेक्ट रीसेट |

Undo names for the sliders reuse the label strings (`fx_fade_in`, `fx_fade_out`, `fx_pitch`, `fx_speed`, `fx_bass`, `fx_distortion`, `fx_echo`): "Undo: Pitch".

Notes for translators: "Fade" is the gradual rise or fall of loudness at the start or end of a sound; keep the word your language's editing apps use (id and pt often keep "fade"). "Pitch" is how high or low the voice sounds; "Speed" is how fast it plays; "Bass boost" makes the low sounds stronger; "Echo" repeats the sound fading away. The voice names are playful: use the meme-speak word, not a literal one ("Chipmunk" is the squeaky voice, "Giant" the deep one). `{n}` is a formatted number in the language's own format (comma or point). `fx_voice_none` is "no voice effect". `track` in `fx_moved_lane_toast` is the timeline row, as in `track_audio`. Hindi: check the two-line wrap of the toasts and the tab row at 200%.

## Accessibility
- **Names (WCAG 2.5.3):** the tool "Audio effects"; each tab is a tab node: "Echo, tab, 4 of 5, selected"; with a dot, the tab's **name** becomes `a11y_fx_tab_on` ("Echo, on": the visible label first, so SC 2.5.3 holds). Never put "on" in the state description, which would replace the spoken "selected" (P1-11 review); each slider "Pitch" with its readout as the state ("+3"), steps of its grid; each tile and segment reads its label, "selected" and its position ("2 of 6"); Reset "Reset all audio effects" (contains the visible "Reset"); Done as ToolPanel.
- **Order:** title, Reset, Done, tabs, the open tab's controls. Opening the panel moves focus to the title; closing returns it to the Audio effects tool (ToolPanel).
- **What the panel covers** (the timeline and tool bar) leaves the accessibility tree and keyboard focus (`Modifier.coveredByModal`).
- **Slider results spoken:** every step announces its new value through the state description; a Reset announces "Audio effects reset".
- **Waveform:** decorative, hidden. The clip says "Audio effects on" while any effect is set, so a blind user knows a sound is not plain.
- **Status messages** ("Preparing audio", toasts) are polite and never take focus (SC 4.1.3).
- **Contrast:** `primary` dot on `surface` 7.3:1; tab `text` on `surface` 12.4:1, unselected `text-secondary` 5.7:1; Reset `primary` on `surface` 7.3:1; slider thumb white on `surface` 17.2:1; selected tile `on-primary` on white 18.9:1; clip bars as computed above.
- **Targets:** every control 48 dp or more (tabs 48 tall, tiles at least `touch-target`, sliders 48 tall rows, Reset and Done 48).
- **200% font:** see States. **Reduce motion:** see Motion; nothing is conveyed only by motion. **Keyboard:** Tab reaches tabs and controls; arrow keys step sliders by their grid; Escape closes.

## Analytics
Events from the PRD plus proposed `tool_use` ids for the PM (they live in `EditorTool`; same pattern as the text and photo tools):
- `tool_use {editor: "video", tool}` once per recorded step: `audio_fade_in`, `audio_fade_out`, `audio_pitch`, `audio_speed`, `audio_bass`, `audio_distortion`, `audio_echo`, `audio_echo_length`, `audio_voice` with `value` = the preset id (`chipmunk`, `giant`, `robot`, `megaphone`, `cave`, `none`, never anything about the file), `audio_reset` (one event for a Reset, no per-part events; the PM may prefer P2-04's per-part pattern). **PM extends the `value` parameter** (today for canvas tools) to `audio_voice`.
- Not fired: opening the panel, switching tabs, a drag in progress, a tap on the already-selected tile, an explanation. No `sound_add`. No names, titles or file details.
- The tool-use mix of voice presets answers whether the six presets earn their place.

## QA compares
Screenshots at 360 dp wide, English unless noted, in `docs/ux/reviews/P3-09/`:
1. `tool-bar-sound`: the clip bar of a meme sound with Audio effects fourth. `panel-fades`, `panel-tone`, `panel-bass`, `panel-echo`, `panel-voice` at 360 × 640 (content fits without scrolling at 100%).
2. `tab-dots`: two tabs with dots, others without; `panel-hi-200` Hindi at 200% font; `panel-es-200` (Spanish tabs).
3. `waveform-meme-sound`, `waveform-audio`, `waveform-voiceover`: bars in a blue clip and a green clip with the label on its patch; one clip with a fade-in and fade-out (bars shaped); one selected with trim handles; one past the video's end under the wash. Measure the bar-to-clip contrast on one frame (3:1 or more).
4. `speed-moved-lane`: Speed lowered until the clip overlaps its neighbor, then the toast and the new lane.
5. `reset`: before and after; `reset-empty` toast.
6. `preparing`: the slow-path status (debug switch).
**By recording or note, the Done when:**
- **Null test:** each preset and each slider extreme rendered through the preview path and the export path differ by less than −40 dB (the P1-08 audio harness); then **an ear check on the phone** for each of the six presets, preview then exported file (the owner listens at QA).
- Split with effects (fades go to the right sides), Duplicate, Trim with a long fade, Undo of a Speed change that moved a lane, Undo of Reset.
- TalkBack through the panel and a slider; Switch Access to the Move here tool still reachable.

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on.
1. **Does Reset clear the open tab or everything?**
   - **A. Recommended: everything on the selected clip.** Effects stack, so one clear answer to "why does it sound odd". One step, Undo brings it back.
   - B. The open tab only (P2-04's rule for photos). Finer, but up to five taps to get plain sound.
2. **Effects on a video clip's own sound.**
   - **A. Recommended: Detach audio first, then Audio effects.** No new model. Voice changing a person's talking clip is one more tap than it could be.
   - B. Audio effects on video clips too (a new effects field on video clips, a second engine path, a schema bump). **Scope change; a proposal for the PM** (size S to M) if the first test shows people want it.
3. **A waveform on video clips.**
   - **A. Recommended: not in P3-09.** The thumbnails fill the clip; a waveform over them is noise. Meme sounds and audio clips get it.
   - B. A thin strip along the bottom of video clips that have sound. Helps lining up a sound with a beat in the original audio, but needs peaks for every imported video (import time, storage). A proposal for the PM, after P4-14's beat markers are decided.
4. **Speed range for sounds.**
   - **A. Recommended: 0.5× to 2×.** Where meme sounds stay recognizable; clip speed (P4-09) goes 0.1× to 10×.
   - B. 0.25× to 4×. More room, but most results are noise.
5. **When a slower Speed no longer fits its lane,** the clip moves to a free lane and says so (**recommended**). Alternative: refuse the change with a toast. Refusing leaves the user to make room first, which is the work Speed should have done.

## Requests to the principal mobile engineer
- **New icon:** `audio_effects` (DESIGN_SYSTEM → Iconography; Kotlin `MemixIcons.AudioEffects`). New token: `MemixSize.waveformClipOpacity` (Float, 0.6). No other token.
- **Model (09a, one schema bump + migration, old-draft check):** an effects block on `AudioClip` with every field and its default: `fadeInUs`, `fadeOutUs`, `pitchSemitones`, `speed`, `voice` (id or none), `bassBoost`, `distortion`, `echoAmount`, `echoLength`. `durationUs` for a clip with speed is `(trimOut − trimIn) / speed`. Fade, pitch, speed, echo and voice are declarative specs; the order in rule 1 is the single source for preview and export (rule 5).
- **Panel:** ToolPanel, Tabs, Slider with `origin` and `snapValue`, SegmentedChoice, OptionTile label layout (built by P3-11); `Modifier.coveredByModal` on the covered region; the title row plus tabs pinned unless over `MemixSize.pinnedBarMaxFraction`.
- **Sound clip tool bar** order: Volume · Split · Delete · Audio effects · Move here · Duplicate; Split and Trim and Duplicate carry effects per rule 5.
- **Timeline:** bars in the draw phase from the peak cache; the label patch; bars follow volume, fades and speed; hidden from semantics.
- **Peaks:** cached per file at about 100 a second for recorded, imported and detached audio; catalog sounds use `waveform_path`.
- **Parity:** one `AudioProcessor` spec for both paths, a debug switch to force the prepared path, and the −40 dB null test harness for the five effect groups and six presets.
- **Limiter** at the end of the chain, identical in both paths. **Echo tail** up to about 1.5 s, cut at the video's end.
- **Strings:** the keys above in the five `strings.xml` files.

## Tokens
New: `waveform-clip-opacity` (`MemixSize.waveformClipOpacity`, Float, 0.6; usage and contrast in `design/tokens.json`, `design/system/tokens.json`, DESIGN_SYSTEM → Size). `pinned-bar-max-fraction` gets a third use (its usage line says so). New icon: `audio_effects`.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Pitch and speed can be changed independently with Media3's `SonicAudioProcessor`; custom `AudioProcessor`s can chain echo and EQ in both the player and Transformer | Guidance (check the current class names when the ticket starts; the skill says to verify APIs) | [Media3 audio processing](https://developer.android.com/media/media3/transformer/transformations) |
| A graphic that carries information needs 3:1 against its background | Guidance | [WCAG 2.2 SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) |
| Status messages reach screen readers without taking focus | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| A tab or panel that grows by ticket should not change its component | Assumption (our consistency rule) | none |
| People forget an effect set on another tab, so a mark on the tab helps | Assumption (test it) | none |
| Competitor editors put voice effects and sound effects behind a selected audio clip | Assumption (product knowledge; check CapCut and VN) | none |

## Test script for the owner (5 people, a phase build)
Tasks: (1) "Make this boom sound deeper." (2) "Make the voice sound like a chipmunk." (3) "Make the sound fade out at the end." (4) "Put it all back to normal." (5) "Find where the sound starts and ends without playing it." Observe: whether people find Audio effects on a selected sound; whether the tab dots help or confuse; whether Reset is found; whether the waveform is used for task 5. Success: tasks 1 and 3 in under 20 s, no help; task 4 in one tap. Notes template: task, time, taps, what they said, where they hesitated.
