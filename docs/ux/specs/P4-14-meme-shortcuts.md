# Meme shortcuts: Sound + zoom, Top and bottom text, Beat markers · P4-14

Thirteenth Phase 4 spec (written 18 Nov 2026). It reuses, and does not repeat:
- `P3-05-sound-picker-sheet.md`: the Meme sounds sheet, its rows and Add block, the title row, the landing rules (at the playhead, the end of the video, downloads, overhang, one undo step, one Confirm haptic, TalkBack), the pinned "Add a meme sound" pill (`P1-05-timeline.md`).
- `P4-06-video-effects.md`: `zoom_punch` (600 ms, intensity 70%, the Punch envelope; 1.35 at 80 ms), the effect landing rules, the at-most-3-overlap rule and its toast, the effects lane.
- `P1-10-text.md`: the Typing entry bar, caption landing slots (top 0.25, bottom 0.75), "last style used", the one renderer, commit rules. `P4-02b-keyframes.md`: the **mode bar that replaces the tool bar** (so the timeline stays live) and its Previous and Next pattern.
- `P1-05-timeline.md`: the ruler, snapping (one SegmentTick), the value bubble, TalkBack nodes. `P1-06-clip-edits-and-audio.md`: the bars, undo naming, ripple rule ("sounds, captions and the rest do not follow ripple edits").
- Component READMEs: Sheet, ToolBar, Toast, Chip.

## Job
Three one-minute meme moves the PRD calls "meme shortcuts": **Sound + zoom** (a meme sound with a zoom punch on its first hit), **top and bottom text** (the classic two-line caption), and **beat markers** (tap along to the music and line things up). **One main action: none** here; Export stays the one blue action (P1-12).

**Done when** (TICKETS P4-14): Sound + zoom places both items in one tap. The other two are checked by their own bars (Measured).

**Fast path, with tap counts.**
- **Sound + zoom:** Meme sounds (1), the **+ Zoom** switch once (2), a sound's Add (3). From then on, for this editor visit, every Add places the sound **and** the zoom: **Meme sounds (1), Add (2)**, the same two taps as a plain sound, and both land in one tap of Add and one undo step.
- **Top and bottom text:** Top & bottom (1), type, Next (2), type, Done (3). Both captions land on the clip, in the classic style. Fewer taps than two separate Text rounds (Text, type, Done twice).
- **Beat markers:** Beats (1), Play, then tap **Tap beat** on every beat. Markers show on the ruler; items snap to them; Previous and Next jump between them.
- **No dragging at all:** every shortcut is a tap, a switch or a typed field (Accessibility).

**Principles check.** Content first: nothing covers the picture (the markers live in the ruler; typing keeps the stage). Sound first: Sound + zoom rides the same two taps as any sound, and the marker row of Previous and Next gives exact landing times for sounds. Fast path: each shortcut saves at least a round of taps. One-handed: the Tap beat pad is the widest tool in the bar, at the bottom edge. Forgiving: each shortcut is one undo step; markers have Remove and Clear all. Accessible: no rhythm-only route (a paused Add marker exists), markers are nodes and ruler actions. Five languages: labels measured at 200%.

**Color roles:** no new color. The **+ Zoom** switch is a selected-chip look (white when on, as Chip: `selection` fill with `on-primary` text), not blue; the picker's Add blocks stay blue (meme sounds). Beat markers are `text-secondary` triangles, `selection` white when selected. The effect item the combo adds is the usual violet `track-effect`.

## Decisions
- **D1. Sound + zoom is a switch in the picker's title row, not a second button on every row.** A row has no room for a second block (about 170 dp of name at 360), and a header row would cost a visible list row. The title row already holds a title and a close button, so a switch there costs nothing in height. With it on, the unchanged Add block does both.
- **D2. The switch is remembered for the editor visit, not forever.** Off every time the editor opens: a user who tried it once never gets zooms on sounds they did not ask for. While on, it shows in the open sheet's title row and the landing announces it.
- **D3. The zoom is synced to the sound's own start** (its first hit; meme sounds put the hit at the start), placed with the effect's own defaults (600 ms, 70%), and editable like any effect item (Intensity, Change effect, Move).
- **D4. Top and bottom text spans the clip under the playhead**, not 3 s from the playhead: a top-and-bottom caption is a title for a shot (Question 2).
- **D5. Beat markers are project-wide marks on the timeline, fixed in time, tapped in or placed at the playhead; no automatic beat detection in v1** (PRD: "Beat-sync and auto-captions are not in v1"). They are snap targets, jump targets and nothing else.

## 1 · Sound + zoom

### Entry and layout
- **Where:** the Meme sounds sheet (P3-05), opened by the Meme sounds tool or the "Add a meme sound" pill. The **title row** becomes: title "Meme sounds", then at the end, before the close button, the **+ Zoom switch** (a Chip-look toggle: `radius-full`, 36 dp drawn in a 48 dp target; off `surface-raised` with `text`, on `selection` white with `on-primary`; the `effects` icon at `icon-small` then the label "+ Zoom"). At 360 dp the row holds title about 105 dp, the switch about 84 dp, the close button 48: fits. At large font (fontScale 1.5 and up) the switch **wraps to a second line under the title** (the row grows 48 dp; the search field already scrolls into the list from 1.5, P3-05).
- **The Add block** and the details pane's **Add to video** are unchanged in look; their spoken names add "and zoom" while the switch is on (`a11y_add_sound_zoom`).
- **Only in the video editor's picker.** Not in the Sounds tab (no timeline there), not in the photo editor, not on P3-15's Use flow.
- **Hidden** when the project has no video or photo clip (a sound can't be synced to a picture that isn't there; the picker still works).

### What a tap on Add does with the switch on
1. The sound lands **exactly as P3-05 says** (download first if needed; nothing lands if it fails; the same overhang rule).
2. **In the same commit**, a `zoom_punch` effect item lands on the Effects lane: **start = the sound's start**, length 600 ms, intensity 70% (P4-06's defaults), on the first lane free for its span (P4-06 lane rule). The effect landing rules of P4-06 apply: with less than 0.5 s of video left it lands ending at the end with the smaller of 600 ms and the video.
3. **One undo step "Add sound and zoom".** Undo removes both.
4. **After:** the sound is selected (P3-05), the sheet closes, both items bonk (`duration-bonk`), the lanes scroll to show the sound and the effect lane slides in if it is new; one Confirm haptic (the sound's, P1-06). TalkBack hears "{name} and zoom added at {time}" (`a11y_sound_zoom_added`).
- **The zoom is refused, the sound still lands** when three effects already overlap the zoom's span (P4-06's limit): the sound lands as normal (one step "Add sound") and the stage shows `explain_zoom_skipped` ("Sound added. Three effects already overlap here, so there is no zoom."). The switch stays on.
- **A photo-only project** works: effects act on every frame (P4-06).
- **If the playhead lands the sound earlier than the playhead** (the sound ends at the end of the video, P3-05) the zoom starts at that moved start, still synced.

### States
| State | What shows | Copy |
| --- | --- | --- |
| Sheet, switch off | A grey chip "+ Zoom" in the title row | `picker_zoom_toggle` |
| Switch on | The chip white with `on-primary` text; remembered for the visit | spoken "on" |
| Landing with zoom | Both items bonk; sound selected; announcement | `a11y_sound_zoom_added` |
| Zoom refused (3 effects) | The sound alone; message in the stage slot | `explain_zoom_skipped` |
| Download in progress, then success | As P3-05; the zoom lands with the sound, never before | none |
| Download fails | Nothing lands (no zoom alone) | P3-04 strings |
| No clip in the project | Switch not shown | none |
| Large font | The chip drops under the title | none |

### Rules
- The zoom's length is **not** the sound's length (a 5 s sound still gets a 600 ms punch). A sound that is under 600 ms still gets a full punch.
- One Add = at most one zoom. Adding the same sound twice adds two zooms; the third overlapping effect would be refused.
- The effect item is a normal effect: Intensity, Change effect, Delete, Move and the 3-overlap rule all apply later.
- Sounds and effects do not follow ripple edits (P1-06): unchanged.

## 2 · Top and bottom text

### Entry and layout
- **Tool:** **Top & bottom** (new icon `caption_bars`, label "Top & bottom", click label "Add top and bottom text to this clip"), in the video main bar **right after Text**: Edit · Meme sounds · Text · **Top & bottom** · Add media · Overlay · Voiceover · **Beats** · Stickers · Effects · Filters · Remove BG · Speed. Not shown in an empty project. Add media moves one place right (still reachable; it also has its tile at the end of the main row, P1-16). **Proposal to the PM:** update the consolidated main bar in TICKETS and the ToolBar README.
- **Typing layout** is **P1-10's, unchanged** (the stage above, the entry bar on the keyboard, the live caption on the stage, the 250-character limit and counter, every exit commits). Two steps, one field at a time, so the entry bar keeps its height and the stage keeps its size:
  1. **Step 1, "Top text":** the field's placeholder reads "Top text". The Done button reads **Next** when the field has text, **Skip** when it is empty. The keyboard's action key is Next. The preview shows the caption live at the top slot.
  2. **Step 2, "Bottom text":** the same field (cleared), the placeholder "Bottom text", the button **Done**, the action key Done. The top caption stays on the stage as a stand-in; the bottom one shows live at the bottom slot.
- **Classic font asks the keyboard for all caps** (P1-10); other fonts do not. The style is **the last style used in the visit, else the default** (P1-10): both captions share it.
- **Back, Escape, the keyboard going away, Close** at either step **commit** what has been typed (P1-10): at step 1 only the top; at step 2 both. Blank steps add nothing. If both are blank nothing is added and nothing is recorded.

### What lands
- **Time:** both captions **span the main clip under the playhead** (its start to its end on the timeline; the last clip when the playhead is at or past the end). In a project with several clips, a top-and-bottom caption is a title for one shot.
- **Place:** the top caption at the top slot (center y 0.25), the bottom at the bottom slot (0.75), horizontally centered, scale 100%, rotation 0° (P1-10's slots). Each goes on **the first text lane free for the whole span**, so the second lands on a second text lane (P1-06 lane rule).
- **One undo step "Add top and bottom text".** The **bottom** caption (the last one typed) is selected after, with its text bar; a top-only commit selects the top. TalkBack hears "Top and bottom text added" (`a11y_quick_caption_added`). No haptic.
- **Everything is a normal caption:** Style, Position, Edit text, Looks (P4-11), Keyframes and the rest work on each.

### States
| State | What shows | Copy |
| --- | --- | --- |
| Main bar | `caption_bars` icon after Text; absent in an empty project | `tool_quick_caption` |
| Step 1, empty | Entry bar with "Top text" and **Skip** | `quick_caption_top_hint`, `quick_caption_skip` |
| Step 1, with text | **Next**; the live top caption | `quick_caption_next` |
| Step 2 | "Bottom text", **Done**; the top caption stands in | `quick_caption_bottom_hint`, `panel_done` |
| Committed | Both selected-style captions on two text lanes, the bottom selected | `a11y_quick_caption_added` |
| Both blank | Nothing added; the main bar | none |
| Over 250 characters | The P1-10 limit | P1-10 |
| Long content (5 languages, 200% font) | The button label wraps to two lines (Spanish "Siguiente"); the field grows; the stage shrinks as in P1-10 | none |

## 3 · Beat markers

### What a beat marker is
A **project-wide mark at a time** on the ruler, put there by tapping along to the music or at the playhead. Markers are **snap targets** (an item's edges, a trim handle, the playhead while scrubbing and the Move here target all snap to them, one SegmentTick per engagement, within `space-2` on screen, exactly like snapping to the playhead and neighbors, P1-05) and **jump targets** (Previous, Next). Nothing else: no automatic cuts, no "place sounds on every marker" (Later, no ticket).
- **Markers do not move with edits** (P1-06's rule for sounds: nothing follows a main-track ripple): they stay at their times. If the user moves the music, they Clear and tap again.
- **Limits:** at most **500** markers (`beatMarkers.max`: about four minutes at 120 BPM); two markers can't be closer than **100 ms** (a tap that close to an existing marker is ignored with no message: it is a double tap). At 500, a tap explains (`explain_beat_max`).

### The Beats tool and the beat bar
- **Tool:** **Beats** (new icon `beats`, label "Beats", click label "Mark the beat on the timeline"), in the main bar right after Voiceover. Not shown in an empty project. **Active look** while the beat bar is open is not needed (the bar replaces the tool bar).
- **The beat bar** replaces the tool bar like the keyframe bar (P4-02b D3): the timeline, stage and transport stay live. `surface`, `toolbar-height`, the `hairline` top border; left to right:

| # | Tool | Icon | What it does |
| --- | --- | --- | --- |
| 0 | **Close** (a leading 48 dp button, then a `hairline` divider) | `arrow_back` | Back to the main bar. Spoken "Close beat markers" |
| 1 | **Tap beat** (the wide pad, 120 dp × 56 dp, `surface-raised`, `radius-md`, the `beats` icon over the label; pressed `hairline`) | `beats` | A marker at the playhead **at touch-down**. While the video plays this is the tap-along; while paused it adds one at the playhead. A marker within 100 ms of this time is not added again |
| 2 | **Previous** | `arrow_left` | Playhead to the previous marker (scrolled into view), which becomes the **selected** marker. None before: explains |
| 3 | **Next** | `arrow` | The mirror |
| 4 | **Remove** | `minus` | Removes the **selected** marker (or the one within half a frame of the playhead); none: explains (`explain_beat_remove_none`) |
| 5 | **Clear all** | `delete` in `text-secondary` | Removes every marker, one step; not `danger` (undoable; the word is "Clear") |

- **What shows at 360 dp:** Close 48 + divider + Tap beat 120 + Previous 64 + Next 64 = about 300; Remove is half visible, Clear all one swipe away. The pad sits at the bottom edge so the thumb can tap steadily.
- **Tap along (the main use):** the user plays (the transport's play button, as ever) and taps the pad on each beat. **The marker time is the playhead time at touch-down**, not at release (a rhythm tap must not drift by the finger's hold). Each tap gives a `SegmentTick` haptic (a drum pad's light feedback) and the pad brightens for 120 ms. Hundreds of taps are fine; the session is one undo step (below). **Tap offset** (a constant, `beatMarkers.tapOffsetMs`, default 0): a fixed shift to earlier markers for display and touch latency; QA measures it on the owner's phone and tunes it (Measured). Assumption: people tap slightly early or late; the constant keeps the choice out of the interface.
- **Undo:** a **tap-along session** (taps while playing, ending when playback pauses, the bar closes, or 3 s pass without a tap) is **one undo step "Beat markers"**; paused adds are one step each; Remove and Clear all one each.
- **First time per editor visit**, the bar shows a 4 s hint in the stage's slot: "Play the video and tap on every beat." (`beat_hint_first`).

### On the timeline
- **The ruler:** each marker is a **solid triangle pointing down**, `beat-marker-size` (8 dp wide, 6 dp tall, new token) in `text-secondary` (5.7:1 on `canvas`), standing on the ruler's baseline; the **selected** marker (after Previous, Next, or a tap on it) is `beat-marker-size-selected` (12 dp, 9 dp tall) in `selection` white (17.2:1). Size also changes, so selection is never color alone. Markers outside the viewport are not drawn.
- **While the beat bar is open**, a 1 dp `hairline` line runs from each marker down through the lanes behind the items (decorative; the triangle carries the information) so items can be lined up by eye. Outside the bar the lines are not drawn (less clutter).
- **Tap a marker's triangle** (a 48 dp target around it, on the ruler row: the documented non-video exception; Previous and Next are the equal routes): selects it and moves the playhead to it, no undo step.
- **Marker triangles and the zoomed-out view:** markers closer than 6 dp on screen are drawn as one wider run of triangles (the nearest stays tappable); zooming in separates them.
- **Snapping:** as above; the value bubble reads the snapped time as for any snap.

### States
| State | What shows | Copy |
| --- | --- | --- |
| Main bar | `beats` icon after Voiceover; absent in an empty project | `tool_beats` |
| Beat bar, no markers | The bar; Previous and Next explain on tap; the first-time hint | `beat_hint_first` |
| Tapping while playing | Triangles appear on the ruler as taps land; the pad brightens | none |
| Tapping while paused | One marker at the playhead | none |
| A marker selected | White larger triangle; Remove acts on it | none |
| No marker before or after | Toast | `explain_beat_none_prev`, `explain_beat_none_next` |
| Remove with nothing selected | Toast | `explain_beat_remove_none` |
| 500 markers | Toast on the next tap | `explain_beat_max` |
| Clear all | Markers gone; one step | `edit_beat_clear` |
| Item dragged near a marker | Snaps with one SegmentTick; the bubble shows the time | none |
| Past the end of the video | Markers may exist past the end; they sit under the `scrim` wash | none |
| An old draft | No markers | none |
| Long content (5 languages, 200% font) | Tool labels wrap and widen the tools (the pad stays at least 120 dp); the bar grows | none |

## Interactions
- **Sound + zoom switch:** a tap toggles (120 ms); no undo step (it is a setting of the sheet); it is kept for the visit.
- **Quick caption:** as P1-10 Typing; the button label and placeholder change per step.
- **Beats:** as above. **Keyboard (basic, P1):** Tab reaches the switch, the beat bar's tools; Enter on Tap beat adds a marker at the playhead; Escape is back.
- **Haptics:** Sound + zoom landing has the sound's Confirm (P1-06); Tap beat a SegmentTick per tap; snaps a SegmentTick; nothing else.
- **Undo steps:** "Add sound and zoom", "Add top and bottom text", "Beat markers", "Remove beat marker", "Clear beat markers".

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Switch on and off | 120 ms fill change | same |
| Both items land | `duration-bonk`; lane slide 200 ms | no bonk, lane at once |
| Beat bar replaces the tool bar | 120 ms cross-fade | same |
| Marker appears | 120 ms fade | same |
| Pad pressed | 120 ms | same |
| Toasts | 120 ms fade | same |

Nothing in the chrome loops (MOTION 2).

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5; a native read before the closed test). Budgets are characters. Tool labels sit on two lines of about 10 characters (P1-06). Reused: `panel_done`, `tool_delete` word per language for Clear all where it fits, `editor_updating_preview`.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `picker_zoom_toggle` | + Zoom | 10 | + Zoom · + Zoom · + Zoom · + ज़ूम | Switch label in the Meme sounds title row. Visible text; "zoom" is the loanword editors use |
| `a11y_picker_zoom_toggle` | Add a zoom punch with each sound | 40 | Tambah zoom kilat di setiap suara · Añadir un zoom de impacto con cada sonido · Adicionar um zoom de impacto a cada som · हर साउंड के साथ ज़ूम पंच जोड़ें | Spoken name (contains the visible "Zoom"); state spoken "on" or "off"; 32 en |
| `a11y_add_sound_zoom` | Add %1$s and zoom to video | 60 | Tambah %1$s dan zoom ke video · Añadir %1$s y zoom al vídeo · Adicionar %1$s e zoom ao vídeo · %1$s और ज़ूम वीडियो में जोड़ें | The Add block's spoken name while the switch is on |
| `a11y_sound_zoom_added` | %1$s and zoom added at %2$s | 60 | %1$s dan zoom ditambahkan di %2$s · %1$s y zoom añadidos en %2$s · %1$s e zoom adicionados em %2$s · %1$s और ज़ूम %2$s पर जुड़े | Polite; times spoken as P1-04 |
| `explain_zoom_skipped` | Sound added. Three effects already overlap here, so there is no zoom. | 80 | Suara ditambahkan. Sudah ada tiga efek di sini, jadi tanpa zoom. · Sonido añadido. Ya hay tres efectos aquí, así que no hay zoom. · Som adicionado. Já há três efeitos aqui, então sem zoom. · साउंड जुड़ गया। यहाँ पहले से तीन इफ़ेक्ट हैं, इसलिए ज़ूम नहीं जुड़ा। | Toast; 69 en, es 70, pt 66 |
| `edit_sound_zoom` | Add sound and zoom | 24 | Tambah suara dan zoom · Añadir sonido y zoom · Adicionar som e zoom · साउंड और ज़ूम जोड़ें | Edit name after "Undo: " |
| `tool_quick_caption` | Top & bottom | 14 | Atas & bawah · Arriba y abajo · Topo e base · ऊपर और नीचे | Tool label; two lines in es, pt |
| `tool_quick_caption_click` | Add top and bottom text to this clip | 45 | Tambah teks atas dan bawah ke klip ini · Añadir texto arriba y abajo a este clip · Adicionar texto em cima e embaixo deste clipe · इस क्लिप में ऊपर और नीचे का टेक्स्ट जोड़ें | Click label; 36 en |
| `quick_caption_top_hint` | Top text | 24 | Teks atas · Texto superior · Texto de cima · ऊपर का टेक्स्ट | Field placeholder and spoken name, step 1 |
| `quick_caption_bottom_hint` | Bottom text | 24 | Teks bawah · Texto inferior · Texto de baixo · नीचे का टेक्स्ट | Field placeholder and spoken name, step 2 |
| `quick_caption_next` | Next | 12 | Lanjut · Siguiente · Avançar · आगे | Entry bar button, step 1 with text |
| `quick_caption_skip` | Skip | 12 | Lewati · Omitir · Pular · छोड़ें | Entry bar button, step 1 empty |
| `a11y_quick_caption_added` | Top and bottom text added | 40 | Teks atas dan bawah ditambahkan · Texto arriba y abajo añadido · Texto em cima e embaixo adicionado · ऊपर और नीचे का टेक्स्ट जुड़ा | Polite |
| `edit_quick_caption` | Add top and bottom text | 24 | Tambah teks atas dan bawah · Añadir texto arriba y abajo · Adicionar texto em cima e embaixo · ऊपर-नीचे का टेक्स्ट जोड़ें | Edit name |
| `tool_beats` | Beats | 10 | Beat · Ritmo · Batidas · बीट | Tool label; the music's beat |
| `tool_beats_click` | Mark the beat on the timeline | 40 | Tandai beat di timeline · Marca el ritmo en la línea de tiempo · Marque a batida na linha do tempo · टाइमलाइन पर बीट चिह्नित करें | Click label; 29 en |
| `beat_tap` | Tap beat | 12 | Ketuk beat · Marcar · Marcar · बीट टैप करें | The wide pad's label. A tap puts a marker |
| `beat_previous` | Previous | 12 | Sebelumnya · Anterior · Anterior · पिछला | Beat bar |
| `beat_next` | Next | 12 | Berikutnya · Siguiente · Próxima · अगला | Beat bar |
| `beat_remove` | Remove | 12 | Hapus · Quitar · Remover · हटाएं | Beat bar |
| `beat_clear` | Clear all | 12 | Hapus semua · Quitar todo · Limpar tudo · सब हटाएं | Beat bar |
| `beat_hint_first` | Play the video and tap on every beat. | 50 | Putar videonya dan ketuk di setiap beat. · Reproduce el vídeo y toca en cada tiempo. · Reproduza o vídeo e toque em cada batida. · वीडियो चलाएं और हर बीट पर टैप करें। | Hint, 4 s; 36 en |
| `explain_beat_none_prev` | No beat marker before this. | 40 | Tidak ada penanda beat sebelum ini. · No hay marcas de ritmo antes. · Não há marcas de batida antes. · इससे पहले कोई बीट मार्कर नहीं है। | Toast |
| `explain_beat_none_next` | No beat marker after this. | 40 | Tidak ada penanda beat setelah ini. · No hay marcas de ritmo después. · Não há marcas de batida depois. · इसके बाद कोई बीट मार्कर नहीं है। | Toast |
| `explain_beat_remove_none` | Select a marker first, or move the playhead onto one. | 70 | Pilih penanda dulu, atau geser playhead ke penanda. · Elige una marca antes, o pon el cabezal sobre una. · Selecione uma marca antes, ou leve a posição atual até uma. · पहले मार्कर चुनें या प्लेहेड को उस पर ले जाएं। | Toast; 52 en |
| `explain_beat_max` | That's the most beat markers (%1$d). Remove some first. | 70 | Itulah batas penanda beat (%1$d). Hapus beberapa dulu. · Es el máximo de marcas de ritmo (%1$d). Quita algunas antes. · Esse é o máximo de marcas de batida (%1$d). Remova algumas antes. · बीट मार्कर की सीमा (%1$d) पूरी हो गई। पहले कुछ हटाएं। | Toast; %1$d = 500 |
| `a11y_beat_marker` | Beat marker %1$d of %2$d at %3$s | 50 | Penanda beat %1$d dari %2$d di %3$s · Marca de ritmo %1$d de %2$d en %3$s · Marca de batida %1$d de %2$d em %3$s · बीट मार्कर %1$d / %2$d, %3$s पर | Spoken name of a marker node |
| `a11y_beats_added` | %1$d beat markers added | 40 | %1$d penanda beat ditambahkan · %1$d marcas de ritmo añadidas · %1$d marcas de batida adicionadas · %1$d बीट मार्कर जुड़े | Polite, at the end of a tap-along session |
| `a11y_beat_add_here` | Add beat marker here | 40 | Tambah penanda beat di sini · Añadir marca de ritmo aquí · Adicionar marca de batida aqui · यहाँ बीट मार्कर जोड़ें | A custom action on the playhead (the no-rhythm route) |
| `edit_beat_markers` | Beat markers | 24 | Penanda beat · Marcas de ritmo · Marcas de batida · बीट मार्कर | Edit name |
| `edit_beat_remove` | Remove beat marker | 24 | Hapus penanda beat · Quitar marca de ritmo · Remover marca de batida · बीट मार्कर हटाएं | Edit name |
| `edit_beat_clear` | Clear beat markers | 24 | Hapus semua penanda beat · Quitar marcas de ritmo · Limpar marcas de batida · बीट मार्कर साफ़ करें | Edit name |

Translator notes:
- **Zoom** here is the quick zoom-in effect (a "zoom punch"). **Beat** is the pulse of the music; a **beat marker** is a mark on the timeline at that moment. **Tap beat** puts a marker where the video is now; people tap it along to the music.
- **Top & bottom** is the classic meme layout: one line of text at the top, one at the bottom.
- **Hindi:** the interface uses the system Devanagari face; no row has a fixed text height.

## Accessibility
- **Sound + zoom:** the switch is "Add a zoom punch with each sound, switch, on" (`Role.Switch`, the state spoken); its name contains the visible "Zoom" (SC 2.5.3). Landing announces `a11y_sound_zoom_added`; focus goes to the new sound (P3-05). The Add block's name says "and zoom" while it is on.
- **Top and bottom:** the field's accessible name is "Top text" or "Bottom text"; the button's name is its visible label (Next, Skip, Done); after each step TalkBack announces the field name; commit focuses the new caption (P1-10's rule).
- **Beats:** **no rhythm-only route.** A user who cannot tap in time scrubs to a moment (the ruler's seek or the playhead's actions) and uses **Tap beat** while paused, or the playhead's custom action `a11y_beat_add_here`, plus **Next** and **Previous**. Markers are timeline nodes `a11y_beat_marker` (lazy: on-screen ones plus the ruler's scroll action, P1-05) with actions Select and Remove. A tap-along session ends with a polite "N beat markers added". Dragging is never required (WCAG 2.5.1, 2.5.7).
- **Covered region:** the picker is a Sheet (`coveredByModal`); the beat bar leaves the timeline live.
- **Touch targets:** the switch 48 dp, the pad 120 × 56 dp, bar tools 48 dp or more, a marker's target 48 dp wide on the ruler row (documented exception with Previous, Next and the actions as equal routes).
- **Contrast:** the switch on `selection` with `on-primary` 18.9:1, off `text` on `surface-raised` 12.4:1; the pad label `text` on `surface-raised` 12.4:1; markers `text-secondary` on `canvas` 5.7:1, selected `selection` 17.2:1; the marker lines `hairline` are decoration only. **State is never color alone:** the switch is spoken, the selected marker is also larger.
- **200% font:** see States. **Reduce motion:** see Motion.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging, never for opening the sheet or the bar, never for a switch toggle). New ids for the PM to confirm:

| Event | When | Parameters |
| --- | --- | --- |
| `sound_add` | A sound lands (the PRD event, unchanged) | `sound_id`, `tab`, `editor` as P3-05 |
| `tool_use` | An Add with the switch on lands the sound and the zoom | `tool: "sound_zoom"` (plus P4-06's `effect_add` with `value: "zoom_punch"`, as that spec says) |
| `tool_use` | Top and bottom committed | `tool: "quick_caption"`, `value: "both"`, `"top"` or `"bottom"`; `font` (the font id) as `add_text` |
| `tool_use` | A tap-along session or a paused add | `tool: "beat_marker"`, `value: "tap"` or `"paused"`, `count` (the markers in the step) |
| `tool_use` | Remove, Clear all | `tool: "beat_remove"`, `tool: "beat_clear"` |

- Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `shortcuts-check` (Requests 6): a 12 s clip with a 120 BPM click track (a beat every 0.5 s with a burned-in beat number), a 3 s clip, a photo, and effects pre-placed for the refusal. 360 × 640 and 360 × 800. Screenshots in `docs/ux/reviews/P4-14/`.
1. **The picker:** the title row with the switch off and on, Spanish at 200% (the switch under the title), the Add block's spoken name; the landed sound and zoom with both items bonking; the lane view after.
2. **The refusal:** three effects overlapping, the sound alone with the message.
3. **Top and bottom:** step 1 empty (Skip), step 1 with text (Next), step 2, the result on two text lanes with the bottom selected; a Classic font caps keyboard; Hindi at 200% in the entry bar.
4. **The beat bar:** at 360 dp (Close, the pad, Previous, Next, half of Remove); markers on the ruler with a selected one; the lines through the lanes; a sound dragged near a marker snapping.
5. **Markers at scale:** 60 markers at the default zoom and zoomed out; the 500 limit toast; Clear all.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Sound + zoom places both items in one tap.** With the switch on, one tap on a row's Add leaves **a meme sound at the playhead and a `zoom_punch` item (600 ms, 70%) whose start equals the sound's start**, in **one undo step** (one Undo removes both, one Redo puts both back). In the 1080p30 export of `shortcuts-check`, the zoom's scale at 80 ms into the item is 1.35 within 1% (the P4-06 reference) and the sound's first audible sample is within 33 ms of the zoom's first frame. With three overlapping effects the sound lands alone and the message shows. A sound from the network (not on the phone) lands both after the download, never the zoom alone; a failed download lands neither. The switch is off after the editor is closed and reopened.
- **Top and bottom text:** the two captions span the clip under the playhead (start and length within one frame), sit at the 0.25 and 0.75 slots, share one style, are on separate text lanes, and equal in the export (P1-10's bar: position within 1% of the canvas width, size within 2%). One Undo removes both. Skipping the top adds only the bottom. Blank adds nothing.
- **Beat markers:** with `shortcuts-check` playing, a marker lands at the playhead time of the touch within one frame (screen recording, stepped); 20 taps along the click track give 20 markers with none lost; a tap within 100 ms of a marker adds nothing; the 500 limit explains; a tap-along session is one undo step; markers persist across kill and reopen and are in the saved draft; dragging a sound's end within `space-2` of a marker snaps with one SegmentTick; Previous and Next jump to the neighbours in order and select them; Clear all and Remove are one step each. **Tap offset:** the median difference between markers and the click track's known beats from a tapped run is recorded (the owner taps 40 beats); the engineer sets `tapOffsetMs` only if the median error is over 40 ms.
- **Performance:** the timeline scrolls with no visible lag (janky frames under 5%) with 500 markers; the tap pad adds no visible lag during playback at 30 fps.
- **Old drafts open** with no markers; **leaks:** LeakCanary: open the picker, toggle the switch, land, undo; tap along 30 beats, Clear all, close; Top & bottom five times; repeat the whole 5 times.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| Beat markers | `Project.beatMarkersUs: List<Long> = emptyList()` (sorted, microseconds, at most 500, gaps of at least 100 ms). One `schemaVersion` bump with an identity migration and an old-draft check. Photo projects keep it empty |
| Sound + zoom | No model change: a composite edit `addSoundWithZoom` = P3-05's add plus `addEffect(zoom_punch)` inside **one** `commit` (the effects overlap veto is inside `commit`; if it refuses, the sound alone is committed and the result carries the "zoom skipped" flag) |
| Top and bottom | A composite edit `addTopBottomText(top?, bottom?, clipSpan)` of two `TextItem`s in one `commit`, using the same landing code as `addText` |
| Snapping | The timeline's snap target list gains the markers; one function for items, handles and the playhead scrub |
| Session undo | A tap-along session coalesces into one undo step (a time window of 3 s idle or playback pause) |
| Constants | `MemeShortcutCatalog` in commonMain (zoom length 600 ms and intensity 70% read from `EffectCatalog`, markers max 500, min gap 100 ms, tap offset 0, snap radius, session idle 3 s) with a comment to this spec and `design/tokens.json` → `memeShortcuts` |

## Requests to the principal mobile engineer
1. **The picker's switch:** a Chip-look toggle in the Sheet's title row, state held in the editor's session (reset on open), hidden with no clip; the Add block and `Add to video` call the combined edit when on. P3-05's landing code is reused as is.
2. **Top & bottom:** a mode of the existing Typing flow (two steps, the button label and placeholder per step, one commit); no new entry bar. The span is the clip under the playhead from the project model (the last clip at or past the end).
3. **The beat bar** as a mode like the keyframe bar; the wide Tap pad takes the playhead time at **touch-down** (from the player's current position, not from the gesture's release), with a SegmentTick; session coalescing; the markers in the Timeline (ruler triangles, the lines in beat mode, the 48 dp targets, culling), as snap targets and in the scrub; `Project.beatMarkersUs`.
4. **Start-of-ticket check:** how late the Media3 playhead reads at touch-down on the owner's phone, so `tapOffsetMs` is known before QA; report the number.
5. **TalkBack:** marker nodes (lazy), the ruler and playhead actions (Add beat marker here, Next and Previous marker).
6. **Debug draft `shortcuts-check`** with the 120 BPM click track and the effects for the refusal.
7. **Strings** in the five files; **analytics:** `EditorTool` values `sound_zoom`, `quick_caption`, `beat_marker`, `beat_remove`, `beat_clear`; **icons:** `caption_bars`, `beats`; **tokens:** `beat-marker-size`, `beat-marker-size-selected` (below).

## Tokens and icons
- **Two new size tokens** (added to `design/tokens.json`, `design/system/tokens.json` and DESIGN_SYSTEM with this spec; the Kotlin change is a request to the principal mobile engineer): `beat-marker-size` 8 px (the width of a beat marker's triangle on the ruler; height 6 px; `text-secondary` on `canvas` 5.7:1) and `beat-marker-size-selected` 12 px (height 9 px; `selection` white on `canvas` 17.2:1). The change is logged in `docs/ux/CHANGELOG.md` for the design canvas.
- **New content group** `memeShortcuts` in both token files (the combo, quick caption slots, markers). Kotlin: `MemeShortcutCatalog`, never `MemixTheme`.
- **Two new icons** (DESIGN_SYSTEM → Iconography; the engineer draws, I approve in the review):
  - `caption_bars`: a stroked 20 × 18 px frame (2 px stroke, 1 px corners) holding **two solid 12 × 3.5 px bars**, one near the top and one near the bottom, centered, 1.4 px or more of air to the frame and 5 px between them and the frame's middle: a meme's two lines. Must not read as `formats` (one solid bar flush with the top of a frame), `panels` or `overlay`. The Top & bottom tool.
  - `beats`: a 2 px baseline (20 px) with **three upward 2 × 6 px ticks** at uneven spacing and a **solid 8 × 6 px downward triangle** (the core) above the middle tick, 1.4 px clear of it: a marker on a ruler. Must not read as `keyframe` (a diamond), `playhead` or `volume`. The Beats tool and the Tap beat pad.
- **Reused:** `effects` (the switch), `arrow_back`, `arrow_left`, `arrow`, `minus`, `delete`.
- **Pointer edits made with this spec:** DESIGN_SYSTEM (size tokens, iconography, a shortcuts paragraph), the ToolBar README (main bar order, the beat bar), the Timeline README (beat markers), P3-05 (the title row's switch).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. How does Sound + zoom work?**
- *Problem:* "one tap" has to fit the picker's rows, which have no room for a second block.
- *Considerations:* **A. A "+ Zoom" switch in the picker's title row; with it on, Add places both** (recommended; applied): no row changes, no lost rows, the same two taps for every later sound; the first use costs one extra tap. **B. A separate "Sound + zoom" tool in the main bar:** a literal one-tap path after the picker opens, but a 13th main-bar tool and a second route to the same sheet. **C. Long-press on Add:** hidden, nobody finds it.
- *Reverse:* a UI swap; the edit is the same.

**2. How long are the top and bottom captions?**
- *Problem:* P1-10's text lasts 3 s from the playhead; a title for a shot usually spans the shot.
- *Considerations:* **A. The whole clip under the playhead** (recommended; applied): matches how memes use top and bottom text; costs a longer item a user may trim. **B. 3 s from the playhead:** consistent with normal text; the user trims longer.
- *Reverse:* a landing rule.

**3. Should the Sound + zoom switch be remembered across editor visits?**
- *Problem:* a remembered "on" can add zooms the user forgot about.
- *Considerations:* **A. Remembered for the visit only** (recommended; applied): predictable; the user turns it on again next time. **B. Remembered for good:** one tap fewer next time, surprises.
- *Reverse:* a setting.

**4. Tap offset.**
- *Problem:* a finger lands slightly after the beat the user heard; the screen lags the sound.
- *Considerations:* **A. Ship 0 and let QA measure on the owner's phone** (recommended; applied): one constant to set from data. **B. Guess 60 ms now:** probably wrong on some phones.
- *Reverse:* a constant.

**5. Are the two extra main-bar tools acceptable?**
- *Problem:* Top & bottom (after Text) and Beats (after Voiceover) make the bar 13 tools and push Add media one place right.
- *Considerations:* **A. Yes** (recommended; applied): each shortcut is one tap from the bar; Add media keeps its end-of-row tile. **B. Put Top & bottom inside the Text tool and Beats on the ruler's long-press menu:** fewer tools, hidden shortcuts.
- *Reverse:* a bar order edit.

## Proposals for the PM (not decided here)
1. **Update the consolidated main bar** in TICKETS and the ToolBar README: Edit · Meme sounds · Text · **Top & bottom** · Add media · Overlay · Voiceover · **Beats** · Stickers · Effects · Filters · Remove BG · Speed.
2. **Later, no ticket:** "place a sound on every marker", beat-synced cuts and auto beat detection (PRD: not in v1), a "Zoom" action on an already placed sound's bar.
3. **Analytics ids** above (`sound_zoom`, `quick_caption`, `beat_marker`, `beat_remove`, `beat_clear`).

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| People tap to a beat slightly ahead of or behind it, and display plus touch latency add tens of milliseconds | Assumption (rhythm-tapping literature and product knowledge; the offset is measured at QA, not assumed) | none |
| Time-critical taps should take the time at touch-down, not release | Guidance (rhythm-game practice) | none |
| Rhythm input needs a non-rhythm alternative; dragging needs a single-pointer route | Guidance | [WCAG 2.2 SC 2.5.1](https://www.w3.org/WAI/WCAG22/Understanding/pointer-gestures), [SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| A control's accessible name must contain its visible label | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html) |
| The zoom's numbers (600 ms, 70%, 1.35 at 80 ms) | Evidence | `P4-06-video-effects.md` |
| Top and bottom text is the classic meme layout | Assumption (product knowledge) | none |
| Mobile editors offer manual beat markers placed with a button while a track plays | Assumption (product knowledge; help pages were not reachable here) | none |
