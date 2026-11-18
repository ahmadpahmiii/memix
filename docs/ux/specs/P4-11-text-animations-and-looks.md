# Text animations (in, out, loop) and text looks · P4-11

Ninth Phase 4 spec (written 18 Nov 2026). It reuses, and does not repeat:
- `P1-10-text.md`: the caption, its one renderer, Style panel, selection frame, Position panel, the text bar, "last style used", analytics ids. `P2-11-text-shadow-and-box.md`: shadow and box (a look includes both).
- `P4-02b-keyframes.md`: keyframes on captions (position, scale, rotation), the keyframe badge, the "plays once" panel precedent (Easing), Béziers `Smooth`, `Ease in`, `Ease out`.
- `P4-06-video-effects.md`: the Sheet with a tile grid, the flash rule (SC 2.3.1), deterministic hashed randomness, "content motion is not interface motion". `P4-09-speed.md`: a ToolPanel with Tabs (scrolling tabs at 640, pinned from 800), OptionTile label tiles, Reset.
- `P1-05-timeline.md`, `P1-06-clip-edits-and-audio.md`: the text lane, trim, split, duplicate, lane rule. Component READMEs: Sheet, ToolPanel, Tabs, OptionTile, SegmentedChoice, ToolBar, Toast.

## Job
"Make the words arrive": a caption that pops in on the punchline, slides out, wobbles while it waits. PRD → Video editor → Text: in, out and loop animations, and text presets. **One main action: none.** Export stays the one blue action (P1-12); a tile tap is the action here.

**Done when** (TICKETS P4-11): animations look the same in preview and export. Made checkable in Measured.

**Words used.** The PRD says "text presets"; the interface says **Looks**: a look is a ready-made caption style (font, color, outline, shadow, box) **plus** an optional animation. "Preset" also names speed curves (P4-09); a word of its own keeps the two apart.

**Fast path, with tap counts.**
- **A caption that pops in:** Text, type, Done (P1-10, 2 taps), then **Looks (1)**, **Pop-up (2)**: font, color, box and the pop-in in two taps.
- **One animation only:** Animate (1), a tile (2). Longer or shorter: Duration slider (3).
- **Off again:** Animate, the **None** tile of that tab; or Undo.
- **No dragging at all:** tiles, the slider's keyboard steps and the choice do everything (Accessibility).

**Principles check.** Content first: tiles show the user's own frame and words; the animation plays on the stage, not in chrome. Sound first: the pinned "Add a meme sound" row is untouched; no lane is added (animations live on the text item). Fast path: Looks is the fifth tool of the text bar, one tap from a selected caption. One-handed: panel and sheet are in the lower two thirds. Forgiving: every change one undo step; Clear. Accessible: nothing flashes more than 2 times a second; every drag has a single-pointer route. Five languages: tile labels measured at 200%.

**Color roles:** no new color, no blue (the slider's `primary` fill is the only one). White is selection (selected tile, selected look). The text lane stays `track-text` orange. The look tiles' caption colors are the user's content (the palette), never chrome.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| **Looks sheet** (Sheet, up to 70% of the window) | The **Looks** tool of a selected caption | A tile tap (applies, then closes), close, scrim, drag down, back, Escape (nothing changes) |
| **Animate panel** (ToolPanel with Tabs In · Out · Loop) | The **Animate** tool of a selected caption | Done, system back, Escape. The caption stays selected |

- **Arrives from:** only the selected caption's bar (P1-10 text bar). Not in the main bar: an animation belongs to one text. Not on photo layers (the photo editor has no time).
- **Playhead:** opening either moves the playhead to the caption's start if it is outside the caption (the P1-10 jump rule). Playback pauses on open.
- **Back order:** a sheet or panel closes, then the text bar's selection as P1-06.
- **Kept:** animation, durations, loop speed and the look in the project (autosave, undo). Not kept: the open tab beyond the visit, the tile pictures.
- **"Last style used"** (P1-10, P2-11) now also carries the three animations: a user who made one popping caption gets the next the same way. **Clear** or a **None** tile removes it from the memory too (Question 2).

## Layout
Board: none (`VideoEditor.dc.html` has the text lane item only). Everything below is new.

### The tools in the bar
Text bar (replaces P4-02b's table row): **Edit text · Style · Position · Delete · Looks · Animate · Keyframes · Split · Move here · Duplicate**. The first four stay whole at 360 dp (Delete in view, ToolBar rule); Looks is the half-visible fifth tool at 360 dp, reachable without a swipe.
- **Looks:** new icon `looks` (below), label "Looks", click label "Pick a ready-made caption look".
- **Animate:** new icon `animate`, label "Animate", click label "Animate how this text appears". **Active look** (`primary` on `primary-subtle`, the Reverse switch pattern) while the caption has any animation, so a quick glance at the bar tells. Spoken: "Animate, on".

### The Looks sheet (Sheet)
Standard Sheet (grabber, close, `surface`, `radius-lg`, `shadow-sheet`, `scrim`). Title "Text looks" (`title`, heading). No tabs (ten looks).
- **The grid:** a lazy grid, **3 columns at 360 dp** (`grid-tile-min`, `space-2` gaps, `space-4` side padding). A tile = a square picture (`radius-md`): **the user's frame at the caption's midpoint with the caption drawn in that look at rest, the user's own words** (first 14 characters, then an ellipsis, so the look is readable in 104 dp; same renderer as the stage, the caption block fitted to 80% of the tile width, centered), over the name in `caption` `text` (`space-1` below, up to two lines). A tile not yet made is a plain `surface-raised` square (no spinner), the tap still works. Pressed: `hairline` fill behind, 120 ms. The tile is the whole touch target (104 dp wide).
- **Looks with an animation** carry a `badge-size` chip at the tile's top-right corner (`scrim`, the `animate` icon at `icon-small` in `text`, `space-1` from the corner, 7.6:1 or more over any picture). It is decoration; the spoken name says "with animation".
- **Selected:** the tile whose look equals the caption's current look (all five style fields and all three animations) gets the white ring (`stroke-selection`); a tap on it does nothing. A caption that matches none has no ring (it was edited by hand).
- **Budget** (window 640 / 800 / 915; sheet 70% = 448 / 560 / 640; frame about 110, bottom padding 24 → **314 / 426 / 506 dp** of grid): a tile row = 104 + `space-1` + 16 (one-line name) to 32 = 124 to 140, plus the `space-2` gap. Ten tiles = 4 rows (3 + 3 + 3 + 1) = about 530 dp, so **the grid scrolls on every phone**; two rows show whole at 640 (288 of 314), three at 800 and 915 (412 of 426 / 506). At 200% font names take up to three lines and two rows fit only at 915.
- **Tapping a tile:** applies the whole look in **one undo step "Look"**, keeps the caption's words, position, size, rotation, timing and keyframes, closes the sheet (200 ms), and **if the look has an in-animation the stage plays the caption once** from its start to 0.5 s after the in-animation ends, then pauses (a tap on the stage stops it). The "last style used" memory takes the look.
- **Looks never touch** the text, transform, timing or keyframes: replacing a look cannot move the caption.

### The Animate panel (ToolPanel)
Title row: **Animate** (`title`, heading), **Clear** (Quiet), **Done** (Ghost); Clear and Done wrap under the title at large font. Clear removes all three animations (one step "Clear animation"); with none set a tap explains (`anim_nothing_to_clear`, a toast, no step).

Under it **Tabs "In · Out · Loop"** (the Tabs component, as P3-09). **Opens on the first tab that has an animation, else In.** A tab with an animation set is spoken "In, Pop, selected". Content per tab, `space-4` apart:
1. **Tiles:** a scrolling row, edge to edge, of **OptionTiles, label layout**, **None first**, then the tab's animations (In 8, Out 8, Loop 6, list below). The current one is white (`on-primary` label); a tap applies at once, **one step "Text animation"**, and **plays the preview once** (Interactions); tapping the selected tile replays it without a step.
2. **In and Out: Duration** (Slider, label "Duration", readout `0.5 s` in `timecode`): 0.1 s to 2 s, step 0.1 s, default **0.5 s** (Typewriter and Erase: 0.04 s per character, between 0.5 s and 2 s, set when picked; the user's value after that). Hidden while the tab's choice is None. One step on release ("Animation length"). **The sum of In and Out never exceeds the caption's length**: the slider's maximum is `min(2 s, length - the other animation)` rounded down to 0.1 s; when that is under 0.1 s a tap on a tile explains `anim_too_short` and changes nothing.
3. **Loop: Speed** (SegmentedChoice "Slow · Normal · Fast", label "Speed"): one cycle takes **2 s · 1 s · 0.5 s**; default Normal. Hidden while None. One step "Animation speed".

- **Budget** (content 133 / 206 / 252 dp, P4-09's method; tile row 48, Slider row 68, SegmentedChoice 56, gaps 16): **At 360 × 640** the tabs scroll with the content (title 48 + tabs 48 are over 40%), first view tabs + tiles = 112 of 133; Duration or Speed is one swipe away (floor size, accepted as P4-09's). **At 360 × 800 and 412 × 915** tabs are pinned, content 142 / 188: tiles 48 + 16 + Slider 68 = **132**, fits whole. At 200% every row grows and the panel scrolls on every phone.
- **Hint** under the controls (`label`, `text-secondary`), the tab's one line: In "How the text arrives." Out "How the text leaves." Loop "A motion that repeats while the text is on screen."

## Rules behind the layer (preview and export match)
Saved with every draft, so fixed; also in `design/tokens.json` → `textAnimation`. Changing a rule after the closed test (about 25 Jan 2027) needs a schema bump.

### How an animation draws
- **One renderer.** The caption is laid out **once** (P1-10 Geometry: canvas pixels, never re-flowed). An animation never re-lays out: it changes only **how the finished block is drawn**: a transform about the block's center (the box's center if a box is on), an alpha, and for Typewriter and Erase a reveal. The block's size, line breaks and font never change.
- **Order of work, per frame:** the item's look at that time (static, or P4-02a's keyframed evaluation) → the loop's transform → the in or out transform → draw the whole group (box, shadow, outline, fill; P2-11's order) **at the combined alpha, once** → clip at the canvas edge → effects over everything (P4-06). So a Shake effect shakes an animated caption, and keyframes and animations stack.
- **Time is the item's own time τ**, in microseconds since its start, taken at the **presentation time of the frame** (a function of time only: no wall clock, no state). Preview, a seek and the export evaluate the same function, so the same frame is identical in all three. Randomness (Shake only) draws from a hash of the frame's time rounded to 1/30 s and the item id, never a live random number.
- **Parameters are shares of the canvas's shorter side `S`**, so every output size agrees (P1-10 rule).
- **Windows.** *L* = the item's length; *dIn*, *dOut* the chosen durations; effective *dIn′*, *dOut′* = *dIn*, *dOut* each cut so that *dIn′ + dOut′ ≤ L* (the larger is cut first, proportionally; stored values are untouched and come back when *L* grows). In progress *p* = clamp(τ / *dIn′*, 0, 1). Out progress *q* = clamp((τ − (L − *dOut′*)) / *dOut′*, 0, 1). The loop runs the whole span: phase φ = frac((τ + loopOffset) / T).
- **Easings** (the P4-02b and P4-06 Béziers): `Out` (0, 0, 0.58, 1), `In` (0.42, 0, 1, 1), `Smooth` (0.42, 0, 0.58, 1); bounce = the standard four-segment ease-out bounce (n = 7.5625, d = 2.75).
- **Selection and hit area ignore the animation.** The white frame, the handle, the guides and the hit area use the item's un-animated look, so dragging stays predictable; during playback the words move under a still frame. Position, Size and the stage gestures edit the un-animated look.
- **Edits.** *Trim:* In and Out are measured from the item's own start and end (shortening the end moves the out-animation with it). *Move, Duplicate:* animations go along. *Split:* the left piece keeps In and Loop, the right keeps Out and Loop, so no animation plays at the cut; the right piece's loop **continues in phase** (`loopOffset` = the left piece's length). *Style, Position, Edit text* never change animations. *Speed, reverse* do not apply to captions.
- **Flashing (SC 2.3.1).** Only Blink changes brightness periodically: it swings the caption between 100% and 30% alpha, at most **2 cycles a second** (Fast, 0.5 s), on one caption: far below three flashes a second and a small area. Nothing else changes luminance; Pop and Drop settle within 0.5 s.

### The list (ids saved forever)
Default length 0.5 s for In and Out; `dir` = the named direction. *p*, *q* as above; *e* = the easing named.

**In (8)**
| Id | Name | What it does |
| --- | --- | --- |
| `in_fade` | Fade | alpha = Out(*p*) |
| `in_pop` | Pop | scale 0 → 1.15 over the first 60% of *p* (Out), then 1.15 → 1 over the rest (Smooth); alpha = min(1, *p* / 0.2) |
| `in_slide_up` | Slide up | offset y = +0.25 *S* × (1 − Out(*p*)): starts below and rises; alpha = Out(*p*) |
| `in_slide_down` | Slide down | the same with y = −0.25 *S* (starts above) |
| `in_slide_left` | Slide left | offset x = +0.25 *S* × (1 − Out(*p*)): starts right, moves left; alpha as above |
| `in_slide_right` | Slide right | x = −0.25 *S* |
| `in_drop` | Drop | offset y = −0.35 *S* × (1 − bounce(*p*)): falls from above and bounces twice; alpha = min(1, *p* / 0.15) |
| `in_typewriter` | Typewriter | the first ⌊*N* × *p*⌋ **user-perceived characters** (grapheme clusters, in reading order, spaces count) are drawn; the box (if any) is drawn whole from the first frame; a Devanagari cluster is never split |

**Out (8)**
| Id | Name | What it does |
| --- | --- | --- |
| `out_fade` | Fade | alpha = 1 − In(*q*) |
| `out_shrink` | Shrink | scale = 1 − In(*q*) (gone at *q* = 1) |
| `out_slide_up` | Slide up | offset y = −0.25 *S* × In(*q*); alpha = 1 − In(*q*) |
| `out_slide_down` | Slide down | y = +0.25 *S* |
| `out_slide_left` | Slide left | x = −0.25 *S* |
| `out_slide_right` | Slide right | x = +0.25 *S* |
| `out_fall` | Fall | offset y = +0.5 *S* × *q*²; alpha = 1 − max(0, (*q* − 0.6) / 0.4) |
| `out_erase` | Erase | the first ⌈*N* × (1 − *q*)⌉ clusters are drawn: the last letters go first |

**Loop (6)** with cycle *T* = 2 s, 1 s or 0.5 s (Slow, Normal, Fast), φ = frac((τ + offset) / *T*); every loop starts and ends at rest (identity at φ = 0), so it never jumps when it begins.
| Id | Name | What it does |
| --- | --- | --- |
| `loop_pulse` | Pulse | scale = 1 + 0.04 × (1 − cos 2πφ) (1 to 1.08) |
| `loop_wobble` | Wobble | rotation = 4° × sin 2πφ |
| `loop_shake` | Shake | offset x, y = 0.012 *S* × a hash in [−1, 1] per 1/30 s slot (jitter, not a sine); the rate is 30 slots a second at every speed, so Shake ignores Speed and the Speed choice is not drawn for it |
| `loop_bounce` | Bounce | offset y = −0.04 *S* × \|sin πφ\| (a hop) |
| `loop_float` | Float | offset y = 0.02 *S* × sin 2πφ |
| `loop_blink` | Blink | alpha = 1 − 0.7 × (0.5 − 0.5 cos 2πφ) (1 to 0.3) |

**Counts:** 8 + 8 + 6 = **22**. Shake has no Speed choice: the row isn't drawn for it (a short hint line says "Shake has a fixed speed.").

### The ten looks (ids saved forever)
A look = font, color, outline, shadow (color, softness), box (color, corners), In, Out, Loop. Colors are the P1-10 palette. A look **replaces** all of them (see Looks sheet); the words, transform, timing and keyframes stay.
| Id | Name | Font | Color · outline | Shadow | Box | In · Out · Loop |
| --- | --- | --- | --- | --- | --- | --- |
| `classic` | Classic | anton | White · Black | none | none | none |
| `shout` | Shout | anton | Yellow · Black | Black, Soft | none | Pop · Fade · none |
| `caption_bar` | Caption bar | poppins | White · none | none | Black, Rounded | Fade · Fade · none |
| `highlight` | Highlight | poppins | Black · none | none | Yellow, Square | Slide up · Fade · none |
| `neon` | Neon | baloo2 | White · none | Light blue, Blurry | none | Fade · Fade · Pulse (Slow) |
| `typed_note` | Typed note | kalam | White · none | none | Black, Square | Typewriter · Fade · none |
| `drama` | Drama | rozha | Red · Black | Black, Soft | none | Fade (1 s) · Fade (1 s) · none |
| `cartoon` | Cartoon | baloo2 | White · Black | none | none | Drop · Shrink · Bounce (Normal) |
| `movie_title` | Movie title | rozha | White · none | Black, Soft | none | Fade (1.5 s) · Fade (1 s) · none |
| `alert` | Alert | anton | Red · White | none | none | Pop · Fade · Blink (Slow) |
- `classic` is the default look: choosing it is how to remove every style and animation at once. Looks are authored for Memix (names are generic words), not copied from another app's list.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Text bar | `looks` and `animate` after Delete; Animate in its active look when any animation is set | `tool_looks`, `tool_animate` |
| Looks sheet, tiles | Ten tiles from the user's frame and words; current look ringed | `looks_title`, `look_name_<id>` |
| Tile picture not made yet | Plain `surface-raised` square, name below, tap works | none |
| Look applied | Sheet closes, caption in the new look; plays once when the look has an In | `a11y_look_applied` |
| Animate panel, nothing set | In tab, None white, no slider | `anim_tab_*`, `anim_none` |
| An animation set | Its tile white, Duration (In, Out) or Speed (Loop) shown | `anim_duration`, `anim_speed` |
| Shake chosen | No Speed row; the fixed-speed hint | `anim_shake_fixed` |
| Caption too short for another animation | The tile explains, nothing changes | `anim_too_short` |
| Clear with nothing set | Toast, no step | `anim_nothing_to_clear` |
| Animation playing on the stage | Words animate; frame, handle and guides stay still | none |
| Item past the end of the video | `scrim` wash on the overhang (P1-05); the part past the end does not play or export | none |
| Item very short (under 0.2 s) | In and Out are cut to fit (stored values kept); the item still draws | none |
| A draft from before this ticket | No animations, no change | none |
| Preview catching up | Last frame stays; "Updating the preview" after 1 s | `editor_updating_preview` |
| Empty project, photo editor | Not applicable (no captions; photo editor has no animation) | none |
| Offline, permission | Not applicable (all on the phone) | none |
| Long content (5 languages, 200% font) | Tile names wrap to three lines and the grid scrolls; tab names scroll sideways; Clear and Done wrap under the title; the hint wraps; tile labels in the Animate row keep one line and widen | none |
| First run | No coach mark | none |

## Interactions
- **Looks tile tap:** one step "Look"; the sheet closes; plays once if the look has an In. **Long-press a tile:** nothing.
- **Animate tile tap:** one step "Text animation"; the panel stays open; the stage plays a preview once: **In** from the caption's start to 0.5 s past the In; **Out** from 0.5 s before the Out starts to the caption's end; **Loop** two cycles from the start (at most 4 s). The playhead stays where the preview stops (the P4-02b Easing precedent); a tap on the stage stops it. Tapping the selected tile replays it, no step.
- **Duration and Speed:** a drag shows the readout live and applies on release (one step); the stage replays the preview on release. Keyboard and TalkBack steps land on the 0.1 s grid; double-tap on the thumb resets to 0.5 s.
- **Stage taps** play or pause as everywhere. **Keyboard (basic, P1):** Tab reaches tool, tabs, tiles, slider; Enter applies; Escape is back.
- **Haptics:** none (no snap points; SegmentTick is not used).
- **Undo steps:** "Look", "Text animation", "Animation length", "Animation speed", "Clear animation".

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Sheet and panel open, close | 200 ms slide | 120 ms fade (instant on Android) |
| Tab change | 200 ms cross-fade | 120 ms fade |
| Tile pressed, ring | 120 ms | same |
| Tile picture arrives | 120 ms fade | same |
| **The animation itself** | Content, not interface motion: plays with the video, in the preview and the export | **Still plays** (it is the user's meme; reduce motion governs the app's chrome) |
| Toasts | 120 ms fade | same |

Nothing in the chrome loops (MOTION 2); the Animate tile pictures are names only, not moving pictures.

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5; a native read before the closed test). Budgets are characters. Tool labels sit on two lines of about 10 characters (P1-06); tile labels sit on one line and widen.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_looks` | Looks | 12 | Gaya · Looks · Looks · लुक | Tool label. A look = ready-made caption style plus animation. Keep the loanword in es and pt |
| `tool_looks_click` | Pick a ready-made caption look | 40 | Pilih gaya teks siap pakai · Elige un look de texto listo · Escolha um look de texto pronto · तैयार टेक्स्ट लुक चुनें | Click label; 30 en |
| `looks_title` | Text looks | 20 | Gaya teks · Looks de texto · Looks de texto · टेक्स्ट लुक | Sheet title |
| `tool_animate` | Animate | 12 | Animasi · Animar · Animar · एनिमेट | Tool label and panel title |
| `tool_animate_click` | Animate how this text appears | 40 | Atur animasi teks ini · Anima cómo aparece este texto · Anime como este texto aparece · इस टेक्स्ट की एनिमेशन चुनें | Click label; 29 en |
| `anim_tab_in` | In | 8 | Masuk · Entrada · Entrada · आना | Tab: how it arrives |
| `anim_tab_out` | Out | 8 | Keluar · Salida · Saída · जाना | Tab: how it leaves |
| `anim_tab_loop` | Loop | 8 | Ulang · Bucle · Loop · दोहराव | Tab: repeats while on screen |
| `anim_none` | None | 12 | Tanpa · Ninguna · Nenhuma · कोई नहीं | First tile of every tab |
| `anim_duration` | Duration | 14 | Durasi · Duración · Duração · अवधि | Slider label (In, Out) |
| `anim_speed` | Speed | 12 | Kecepatan · Velocidad · Velocidade · गति | Choice label; use the word of `tool_speed` in each language |
| `anim_speed_slow` | Slow | 10 | Lambat · Lenta · Lenta · धीमी | Segment |
| `anim_speed_normal` | Normal | 10 | Normal · Normal · Normal · सामान्य | Segment |
| `anim_speed_fast` | Fast | 10 | Cepat · Rápida · Rápida · तेज़ | Segment |
| `anim_hint_in` | How the text arrives. | 40 | Cara teks muncul. · Cómo aparece el texto. · Como o texto aparece. · टेक्स्ट कैसे आता है। | Hint |
| `anim_hint_out` | How the text leaves. | 40 | Cara teks hilang. · Cómo se va el texto. · Como o texto sai. · टेक्स्ट कैसे जाता है। | Hint |
| `anim_hint_loop` | A motion that repeats while the text is on screen. | 60 | Gerakan yang berulang selama teks tampil. · Un movimiento que se repite mientras se ve el texto. · Um movimento que se repete enquanto o texto está na tela. · टेक्स्ट दिखने तक दोहराई जाने वाली गति। | Hint; 50 en, es 52, pt 59, id 41 |
| `anim_shake_fixed` | Shake has a fixed speed. | 40 | Getar punya kecepatan tetap. · El temblor tiene velocidad fija. · O tremor tem velocidade fixa. · कंपन की गति तय है। | Under the loop tiles when Shake is chosen |
| `anim_name_fade` | Fade | 14 | Pudar · Fundido · Esmaecer · फीका | In and Out share it |
| `anim_name_slide_up` | Slide up | 14 | Geser atas · Subir · Subir · ऊपर सरकें | Moves up |
| `anim_name_slide_down` | Slide down | 14 | Geser bawah · Bajar · Descer · नीचे सरकें | Moves down |
| `anim_name_slide_left` | Slide left | 14 | Geser kiri · A la izquierda · Para a esquerda · बाएं सरकें | Moves left |
| `anim_name_slide_right` | Slide right | 14 | Geser kanan · A la derecha · Para a direita · दाएं सरकें | Moves right |
| `anim_name_pop` | Pop | 14 | Muncul · Pop · Pop · पॉप | In only; keep the loanword where editors do |
| `anim_name_drop` | Drop | 14 | Jatuh · Caída · Queda · गिरना | In only; falls and bounces |
| `anim_name_typewriter` | Typewriter | 14 | Mesin ketik · Máquina de escribir · Máquina de escrever · टाइपराइटर | In only; letters appear one by one |
| `anim_name_shrink` | Shrink | 14 | Mengecil · Encoger · Encolher · सिकुड़ना | Out only |
| `anim_name_fall` | Fall | 14 | Jatuh turun · Caer · Cair · गिरकर जाना | Out only; hi 11 |
| `anim_name_erase` | Erase | 14 | Hapus huruf · Borrar · Apagar · मिटना | Out only; letters vanish from the end |
| `anim_name_pulse` | Pulse | 14 | Denyut · Pulso · Pulso · धड़कन | Loop |
| `anim_name_wobble` | Wobble | 14 | Goyang · Vaivén · Balanço · डगमग | Loop; same word as the effect `fx_name_wobble` |
| `anim_name_shake` | Shake | 14 | Getar · Temblor · Tremor · कंपन | Loop; same word as `fx_name_shake` |
| `anim_name_bounce` | Bounce | 14 | Pantul · Rebote · Quique · उछाल | Loop; same as `fx_name_bounce` |
| `anim_name_float` | Float | 14 | Melayang · Flotar · Flutuar · तैरना | Loop |
| `anim_name_blink` | Blink | 14 | Kedip · Parpadeo · Piscar · झपकना | Loop |
| `look_name_classic` | Classic | 16 | Klasik · Clásico · Clássico · क्लासिक | Same word as the Classic font tile |
| `look_name_shout` | Shout | 16 | Teriak · Grito · Grito · चिल्लाहट | |
| `look_name_caption_bar` | Caption bar | 16 | Bilah teks · Barra de texto · Barra de legenda · कैप्शन बार | |
| `look_name_highlight` | Highlight | 16 | Sorot · Resaltado · Destaque · हाइलाइट | |
| `look_name_neon` | Neon | 16 | Neon · Neón · Neon · नियॉन | |
| `look_name_typed_note` | Typed note | 16 | Catatan ketik · Nota escrita · Nota digitada · टाइप नोट | |
| `look_name_drama` | Drama | 16 | Drama · Drama · Drama · ड्रामा | |
| `look_name_cartoon` | Cartoon | 16 | Kartun · Dibujos · Desenho · कार्टून | |
| `look_name_movie_title` | Movie title | 16 | Judul film · Título de película · Título de filme · फ़िल्म टाइटल | |
| `look_name_alert` | Alert | 16 | Peringatan · Alerta · Alerta · अलर्ट | |
| `anim_too_short` | This text is too short for more animation. Make it longer first. | 70 | Teks ini terlalu pendek untuk animasi lagi. Perpanjang dulu. · Este texto es demasiado corto para más animación. Alárgalo primero. · Este texto é curto demais para mais animação. Aumente-o primeiro. · इस टेक्स्ट में और एनिमेशन के लिए जगह नहीं है। पहले इसे लंबा करें। | Toast; 62 en, es 67, pt 64 |
| `anim_nothing_to_clear` | This text has no animation. | 40 | Teks ini tanpa animasi. · Este texto no tiene animación. · Este texto não tem animação. · इस टेक्स्ट में एनिमेशन नहीं है। | Toast |
| `a11y_look_applied` | Look applied | 24 | Gaya diterapkan · Look aplicado · Look aplicado · लुक लगाया गया | Polite, after the sheet closes |
| `a11y_text_anim_state` | Animated: in %1$s, out %2$s, loop %3$s | 70 | Beranimasi: masuk %1$s, keluar %2$s, ulang %3$s · Animado: entrada %1$s, salida %2$s, bucle %3$s · Animado: entrada %1$s, saída %2$s, loop %3$s · एनिमेशन: आना %1$s, जाना %2$s, दोहराव %3$s | Appended to the caption's spoken description; a part with none is left out |
| `a11y_look_tile` | %1$s, with animation, %2$d of %3$d | 50 | %1$s, dengan animasi, %2$d dari %3$d · %1$s, con animación, %2$d de %3$d · %1$s, com animação, %2$d de %3$d · %1$s, एनिमेशन के साथ, %2$d / %3$d | Spoken name of a look tile that has one (no animation: the plain `a11y_look_tile_plain` "%1$s, %2$d of %3$d") |
| `edit_look` | Look | 24 | Gaya · Look · Look · लुक | Edit names after "Undo: " |
| `edit_text_animation` | Text animation | 24 | Animasi teks · Animación de texto · Animação de texto · टेक्स्ट एनिमेशन | |
| `edit_animation_length` | Animation length | 24 | Durasi animasi · Duración de animación · Duração da animação · एनिमेशन की अवधि | |
| `edit_animation_speed` | Animation speed | 24 | Kecepatan animasi · Velocidad de animación · Velocidade da animação · एनिमेशन की गति | |
| `edit_animation_clear` | Clear animation | 24 | Hapus animasi · Quitar animación · Remover animação · एनिमेशन हटाएं | |

Reused unchanged: `panel_done`, the Clear word of P1 (`fx_reset` family: use "Clear" here, new `anim_clear` = Hapus · Quitar · Remover · हटाएं, 12), `editor_updating_preview`, the sheet's close label, `style_*` strings.

Translator notes:
- A **look** is a ready-made way a caption looks and moves (font, colors, box and an animation). Keep it short; "Looks" can stay in English where editors use it.
- **In** is how the text appears, **Out** how it disappears, **Loop** a motion that repeats while it is on screen. Names are one or two words.
- **Typewriter** shows letters one at a time; **Erase** removes them from the end; **Drop** falls from above and bounces.
- **Hindi:** the interface uses the system Devanagari face; no tile has a fixed text height; seconds written in full.

## Accessibility
- **Focus and TalkBack order:** the sheet's title takes focus, then the tiles in reading order (each a single-choice button, `a11y_look_tile`, the current one "selected"; the picture is decorative); closing returns focus to the caption's Looks tool and announces `a11y_look_applied`. The Animate panel: title, Clear, Done, the tabs (a tab group), tiles (a single-choice group "In animation: Pop, selected, 2 of 9"), then Duration or Speed, closing to the Animate tool.
- **The caption's spoken description** gains `a11y_text_anim_state` after the keyframes part (P4-02b) and before "starts at". Never in the state description.
- **Single-pointer routes (WCAG 2.5.1, 2.5.7):** every control is a tap, a slider with keyboard and TalkBack steps, or a choice. Nothing here needs a drag.
- **Covered region:** `coveredByModal` on the timeline and tool bar while the sheet or panel is open.
- **Motion and flashing:** see Photosensitivity; reduce motion does not remove animation (content). A user who needs a still caption chooses None, which is a single tap.
- **Touch targets:** tiles 104 dp (sheet) and 48 dp tall (panel); tabs 48; slider row 48; choice 48; every tool 48 dp or more; the timeline cue is output only.
- **Contrast:** tile names `text` on `surface` 15.6:1; Animate tile labels `text` on `surface-raised` 12.4:1, selected `on-primary` on white 18.9:1; the badge chip `text` on `scrim` 7.6:1 or more; the white ring on `surface` 17.2:1. **State is never color alone:** the ring is a shape and the state is spoken.
- **200% font:** see States.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while dragging, never for opening the sheet or panel, never for a replayed preview). New ids for the PM to confirm:

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A look is applied | `tool: "text_look"`, `value: "<look id>"` (for example `shout`) |
| `tool_use` | An animation tile applied (None included) | `tool: "text_animation"`, `value: "<animation id>"` or `in_none`, `out_none`, `loop_none` |
| `tool_use` | A Duration release | `tool: "text_animation_length"`, `value: "in"` or `"out"` |
| `tool_use` | A loop speed change | `tool: "text_animation_speed"`, `value: "slow"`, `"normal"` or `"fast"` |
| `tool_use` | Clear | `tool: "text_animation_clear"` |

- Values are fixed catalog ids, never user content. Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `text-anim-check` (Requests 5) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-11/`.
1. **The bar:** Looks and Animate after Delete at 360 dp; Animate in its active look with a pop set; Spanish at 200%.
2. **The Looks sheet:** ten tiles at 360 × 640 (two rows whole) and 360 × 800 (three), the ringed current look, an animated tile with its badge, Hindi at 200%.
3. **The Animate panel:** each tab with and without a choice at 640 (Duration under the fold), 800, 915; Loop with Speed; Shake with its hint; the too-short toast.
4. **A caption on the timeline** with an animation (the cue) and with keyframes too (both cues).
5. **Mid-animation frames** on the stage for Pop, Drop, Typewriter, Erase, Slide, Pulse, each next to its export frame (Measured).
6. **Mixed scripts:** "जब BUILD PASS हो" in Typewriter and Erase, in Classic (Teko) and in Modern: no cluster split.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Animations look the same in preview and export.** On `text-anim-check` (a 4 s 1080 × 1920 clip of a lettered grid with six captions: Latin, accented, Devanagari, emoji, a two-line, a boxed one), for each of the 22 ids at default settings (Loop at Normal), frames saved to `docs/qa/references/P4-11-text-animation/` as `<id>-<t>.png` from an **export** at 1080p30: In at *p* = 0.25, 0.5, 0.75, 1; Out at *q* = 0, 0.25, 0.5, 0.75; Loop at φ = 0, 0.25, 0.5, 0.75. The editor preview at the same instants (paused, stepped) scaled to 1080 must match: **99% of pixels within 3/255** (the encoder's allowance); position within 1% of the canvas width, scale within 2%.
- **Determinism:** the same frame exported twice, and the preview after a seek to the same time, are identical (Shake included).
- **Typewriter and Erase:** at *p* = 0.5 exactly ⌊*N* / 2⌋ clusters show; no line re-wraps between the first and the last frame; Devanagari clusters are whole.
- **Stacking:** a caption with keyframes (a slide) plus Pop plus Pulse: preview equals export; with Deep-fry over it, the animated caption is fried.
- **Rest state equals no animation:** at *p* = 1 and at φ = 0 the frame equals the unanimated caption exactly.
- **Photosensitivity:** Blink Fast, luminance plot of the caption area: at most 2 cycles in any second.
- **Preview speed:** four animated captions at once over a 1080p clip hold 30 fps or more on the owner's phone (release-type build); janky frames under 5% during a 3 s Duration drag.
- **Each edit is one undo step:** a look, a tile, a release, a speed, Clear: Undo once each. **Old drafts open** with no animation; **leaks:** LeakCanary: open the sheet, apply, open the panel, tiles, Clear, close; repeat 5 times.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| Animations | On `TextItem`: `animIn: TextAnim?`, `animOut: TextAnim?`, `animLoop: TextAnim?` where `TextAnim(id: String, durationUs: Long)` (for a loop `durationUs` = the cycle *T*), plus `loopOffsetUs: Long = 0` (Split). One `schemaVersion` bump with an identity migration (all null) and an old-draft check. Unknown ids draw no animation and are kept |
| Specs | `TextAnimationSpec` catalog in `:core:model` (22 ids, the formulas above as constants, defaults) and `TextLookCatalog` (10 looks) in commonMain next to `CaptionCatalog`, with a reference render per animation (rule 5) |
| Evaluation | One pure `textAnimationAt(item, timeUs)` returning (transform delta, alpha, revealCount); preview overlay and export call it |
| Edits | `applyTextLook`, `setTextAnimation`, `setTextAnimationLength`, `setTextAnimationSpeed`, `clearTextAnimations`; pure, one `commit` each; the In plus Out cap check inside; Split sets `loopOffsetUs` |
| Last style | The visit memory gains the three animations |
| Not modeled | No stored look id: the current look is found by comparing fields |

## Requests to the principal mobile engineer
1. **Renderer:** extend the one caption renderer with the delta (transform, alpha) and a **reveal count** for Typewriter and Erase. The reveal must **clip the finished layout** at a grapheme-cluster boundary (a per-line clip from the layout's horizontal offsets), never re-lay out a prefix (lines are centered, so a prefix would re-center and re-wrap). Verify at the start how the current text layout exposes cluster offsets on Android (and the iOS plan).
2. **Per frame:** the overlay redraws every frame while an animated caption is in its in, out or loop window (the keyed-caption path of P4-02a); measure 4 animated captions at 30 fps; draw idle captions once.
3. **The sheet and panel** from Sheet, Tabs, ToolPanel, OptionTile, Slider, SegmentedChoice; tile pictures with the caption renderer on the item's midpoint frame (the P4-07 request 4 use case, on demand, cached for the visit); the in-sheet preview play and the panel's preview play call the same "play the window" helper (as P4-02b's easing play).
4. **Bars, cue, spoken state:** the two tools, the active look, the timeline cue (below), the `a11y_text_anim_state` part.
5. **Debug draft `text-anim-check`** and the reference frames in `docs/qa/references/P4-11-text-animation/`.
6. **Constants** in a commonMain `TextAnimationCatalog` with a comment to this spec and `design/tokens.json` → `textAnimation`; never `MemixTheme`. **Strings** in the five files (names, looks, hints); **analytics:** five `EditorTool` values; **icons:** `looks`, `animate`.

## Tokens and icons
- **No new color, size, radius or motion token.** The look tiles reuse `grid-tile-min`, `badge-size`, `icon-small`; the cue uses `icon-small` and `on-track`.
- **New content group** `textAnimation` in `design/tokens.json` and `design/system/tokens.json` (the 22 animations, durations, loop cycles, the ten looks, the photosensitivity ceiling). Kotlin: `TextAnimationCatalog`.
- **The timeline cue:** a text item with any animation shows the `animate` glyph at `icon-small` in `on-track`, vertically centered, `space-1` inside its **right end** (to the left of the keyframe badge when there is one); drawn only when the item is at least 56 dp wide and the label is cut before it. A cue, not a control; the spoken description says it.
- **Two new icons** (DESIGN_SYSTEM → Iconography; the engineer draws, I approve in the review):
  - `animate`: a **hollow** 6 px ring (2 px stroke) at the upper left joined by a 2 px stroked quarter-arc (14 px radius, flat caps) to a **solid** 8 px circle (the core) at the lower right, 2 px clear of the arc's end: "from here to there". Must not read as `effects` (a solid core with three trailing arcs), `easing` (an S-curve with a diamond) or `keyframe` (a diamond).
  - `looks`: three 18 px horizontal bars 6 px apart: the top and bottom as 2 px strokes, the middle one a **solid** 18 × 5 px bar (the core, 1px corners): a list with one chosen. Must not read as `layers`, `formats` (a frame) or `reorder`.
- **Pointer edits made with this spec:** DESIGN_SYSTEM (iconography, a text-animation paragraph), the ToolBar README (text bar), the Timeline README (the cue).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. How many animations, and one of each kind per caption?**
- *Problem:* the PRD says "in, out and loop animations"; the count and whether they combine decide the size of the build and the panel.
- *Considerations:* **A. 22 animations; one In, one Out and one Loop at once** (recommended; applied): the meme set (pop, slide, drop, typewriter, shake, blink) in a panel that fits; cost: no two ins at once. **B. Fewer (about 12):** a faster build, less choice. **C. A free animation timeline:** stacking and ordering, a keyframe-style editor; large and a worse fit for a one-minute meme.
- *Reverse:* adding ids needs no migration; removing one after the closed test does (unknown ids draw nothing).

**2. Does "last style used" carry the animations?**
- *Problem:* a new caption can copy the last look (P1-10) with or without its motion.
- *Considerations:* **A. Carry them** (recommended; applied): the second caption matches the first in one step, which is how a meme is made; cost: a user who tried a pop and wants a still caption taps None (and the memory forgets). **B. Style only:** never surprising, but a "Looks" tap then needs repeating on every caption.
- *Reverse:* one memory field.

**3. Should the Looks also reach the photo editor?**
- *Problem:* a look's style half works in both editors (one renderer); the animations can't.
- *Considerations:* **A. Video editor only now; a photo version (style half only) is a later ticket** (recommended; applied; no scope change); **B. Add it to the photo editor in this ticket:** a second sheet, ten more states in a built editor, QA debt. Proposal for the PM: a "P4-11c photo looks" ticket after P4-12 if the owner wants it.
- *Reverse:* the catalog is shared; a later ticket only adds the entry.

**4. Does a look reset the style I picked by hand?**
- *Problem:* applying a look replaces font, colors, shadow, box and animations.
- *Considerations:* **A. Replace everything, one undo step** (recommended; applied): simple, predictable, Undo is one tap; **B. Merge, keeping what I changed:** unclear which wins, a hidden-state bug source.
- *Reverse:* an edit rule only.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Content flashing more than three times in any one second can trigger seizures; Blink stays at 2 cycles a second at most and on a small area | Guidance | [WCAG 2.2 SC 2.3.1](https://www.w3.org/WAI/WCAG22/Understanding/three-flashes-or-below-threshold.html) |
| Easing Béziers (0, 0, 0.58, 1), (0.42, 0, 1, 1), (0.42, 0, 0.58, 1) | Guidance (standard) | [MDN: `<easing-function>`](https://developer.mozilla.org/en-US/docs/Web/CSS/easing-function) |
| The ease-out-bounce function (n = 7.5625, d = 2.75) is the common standard form | Guidance | [easings.net](https://easings.net/#easeOutBounce) |
| Grapheme clusters are the unit of "user-perceived character" (so a Devanagari conjunct is never split) | Guidance | [Unicode UAX #29](https://unicode.org/reports/tr29/) |
| Mobile editors offer separate in, out and loop animations per text with a duration control | Assumption (product knowledge; help pages were not reachable here) | none |
| Panel row heights (tile 48, Slider 68, choice 56) and the 133 / 206 / 252 dp content budget | Evidence (read from the component code in the earlier specs) | `design/system/components/ToolPanel/README.md`, `P4-09-speed.md` |
| The 22 animations and 10 looks are authored here | Evidence | this file |
