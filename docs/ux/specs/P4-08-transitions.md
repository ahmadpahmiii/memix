# Transitions between clips: the list of 20, the Transition panel, the cut on the timeline · P4-08

Seventh Phase 4 spec (rewritten 17 Nov after a lost session). It reuses, and does not repeat:
- `P1-05-timeline.md` and `P1-06-clip-edits-and-audio.md`: the main row, clip selection and handles, the main clip bar, Reorder, undo naming, "Preview catching up".
- `P4-07-video-filters-addendum.md`: tiles made from the user's real frames, on demand, cached for the visit; the Filters strip pattern (one scrolling row, Intensity under it).
- `P4-02b-keyframes.md` (the "play the segment once after a tile tap" precedent from Easing) and `P4-04-green-screen.md` (the clip pipeline: a transition works on the finished clip pictures).
- Component READMEs: ToolPanel, Slider, OptionTile, ToolBar, Timeline, Toast.

## Job
"Make the cut feel like something": a dissolve between two scenes, a push, a glitch cut. PRD → Video editor → Transitions: about 20 between clips. **One main action: none.** Export stays the one blue action (P1-12); the panel's Done is Ghost.

**Done when** (TICKETS): transitions keep audio continuous. Made checkable in Measured: the sound is **never touched** by a transition.

**Fast path, with tap counts.**
- **A transition at one cut:** the clip before the cut (1), Transition (2, a swipe of the bar), a tile (3). The panel opens with the playhead on the cut; the tile applies at once and the cut plays once.
- **Longer or shorter:** Duration (4, drag).
- **Off:** the None tile (1), or Undo.
- **No dragging at all:** tiles, the Duration slider's keyboard steps (0.1 s) and the clip's accessibility action cover everything.

**Principles check.** Content first: tile pictures are the user's own two frames; the stage stays live. Sound first: sound is untouched and the pinned "Add a meme sound" row isn't involved. Fast path: three taps. One-handed: the strip and slider sit in the lower half. Forgiving: one undo step per change; None; a transition that no longer fits shortens itself, never blocks an edit. Accessible: every control is a tap or a slider; the cut is spoken. Five languages: names measured at 200%.

**Color roles:** no blue except the Duration slider's `primary` fill; selection white (the selected tile). The cut's cue is `text` on a `scrim` chip. No new color.

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| **Transition panel** (ToolPanel) | The **Transition** tool of a selected main clip: it edits the cut **after** that clip, between it and the next | Done, system back, Escape. The clip stays selected |

- **Playhead:** opening the panel moves the playhead to the cut and pauses playback (the user looks at the cut). The timeline scrolls to keep it under the playhead.
- **Not for** overlays, captions, stickers or sounds, and **not for the last main clip** (nothing follows): the tool is present and **explains on tap** (`explain_transition_last`), because the user may be looking for it on the wrong clip and the reason teaches the rule. A single-clip project says the same.
- **Kept:** the transition and its duration on the clip, in the project. Not kept: the panel, the thumbnails.
- Back order: the panel closes, then the clip bar closes, then the editor.

## Layout
Board: none. Everything is new.

### The tool and the cut
- **Tool:** **Transition** (icon `transition`, label "Transition", `tool_transition`; click label "Change how this clip leads into the next") in the **main video and photo clip bars**, after Keyframes and before Duplicate. Final main-clip bar, consolidated with P4-09 and P4-10 (see those files): **Split · Volume · Delete · Speed · Freeze frame · Reverse · Crop · Fill or Fit · Position · Filters · Green screen · Mask · Keyframes · Transition · Duplicate · Detach audio · Reorder · Replace** (photos have no Volume, Speed, Freeze frame, Reverse or Detach audio). Why late: it is the rarer job for a meme and Delete and Speed must stay in view at 360 dp; the cut's cue (below) and the clip's accessibility action are the other routes.
- **The cut's cue:** where a cut has a transition, a **badge** is drawn centered on the cut in the main row: a `badge-size` (20 dp) `scrim` chip (`radius-sm`) holding the `transition` icon at `icon-small` in `text` (the type-badge look of P1-14, 4.3:1 or more over any thumbnail by the scrim). It is a **cue, not a control**. It is hidden while either neighboring clip is selected (the handle sits on the cut), in Reorder mode, during a trim or move, and when either neighbor is narrower than `touch-target` at the current zoom.
- **Why not a button on every cut** (the common mobile pattern; Question 2): a 48 dp target centered on the cut takes 24 dp from each neighbor; a one-second clip at the default zoom is 40 dp wide, so it would become unselectable. A tool plus a cue costs one tap more and breaks nothing.

### The Transition panel (ToolPanel)
Title row: **Transition** (`title`, heading), **Done** (Ghost). Content, `space-4` apart:
1. **The strip:** a scrolling row, edge to edge, of **21 tiles: None, then the 20** in the order of the list below (groups kept together). A tile (the Filters strip tile, P2-04): a `filter-tile-size` (72 dp) square, `radius-md`, showing **the transition at 50% progress between this cut's two real frames** (the last frame of the clip before, the first of the clip after), made on demand at about 3× the size, cached for the visit; not made yet: a plain `surface-raised` square, static. Under it the name in `caption` (up to two lines). **None** has no picture, only a `surface-raised` square with the clip's first frame at 40% and the name. One choice; the selected tile has the white ring (`stroke-selection`, 2 dp outside, as the filter tiles). The strip scrolls the selected tile into view when the panel opens or an undo changes it.
2. **Duration:** Slider 0.1 s to 2 s, step 0.1 s, default **0.5 s**, readout `0.5 s` in `timecode`; **hidden while None is selected**. The thumb **stops at the longest that fits** (Rules); no toast, no tick (the P3-09 fades rule). Live: dragging doesn't preview; **a release applies** and the cut plays once (below).
- **A tap on a tile** applies it (one undo step "Transition"), moves nothing else, and **plays the cut once**: from 0.8 s before the transition's start to 0.4 s after its end (never before 0:00 or after the end), then pauses; a stage tap stops it, a second tap on the same tile replays without a step. A release of the Duration slider does the same.
- **Budget** (content 133 / 206 / 252 dp; rows from the P4-07 figures: tile 72 + `space-1` + 14 (one-line name) = 90, a Slider row 68, gap 16): strip 90 + 16 + slider 68 = **174 dp**. **360 × 640:** the strip shows whole (90 of 133) and the Duration slider's label and half the rail show (43 dp), the rest scrolls: the pictures and names are the first view and one swipe reaches Duration. **360 × 800 and 412 × 915:** whole. With None selected only the strip shows (90 dp). At 200% names wrap to two or three lines and the strip grows; the panel scrolls with the title in place.

## Rules behind the layer

### What a transition is (the model that keeps sound and timing safe; preview and export match)
- **A transition belongs to the clip before the cut** (`transitionOut`), between two **main-track clips** (video or photo). One per cut, replacing the previous.
- **Nothing moves and nothing is shortened.** The cut stays at the same time, the project keeps its length, and **no other item shifts**: sounds, captions, overlays and effects stay in sync (this is the point of the choice, Question 1). The transition is a picture effect around the cut: the window is **[cut − d/2, cut + d/2]** for a duration *d*.
- **Pictures in the window:** the clip before supplies its picture up to the cut from its own last *d*/2, and **after the cut** from the media it has beyond its trim (the part the user cut off), **else its last frame held**; the clip after supplies its own first *d*/2 after the cut and **before the cut** the media before its trim, else its first frame held. A photo is the same still throughout. So the effect uses real frames where the clip has spare media and holds an edge frame where it hasn't; a hold lasts at most *d*/2 (1 s at the longest) and is covered by the blend.
- **Sound is not touched at all.** Both clips' sound plays exactly as without the transition: no fade, no dip, no overlap, no shift; the audio of the project is **sample-identical** with the transition on or off. A user who wants a sound at the cut adds a meme sound. (This is the whole answer to "keeps audio continuous"; Measured proves it.) A clip with its sound off or detached stays so.
- **What fits:** each clip gives *d*/2 at a cut, and a clip with transitions at both ends gives half of each, so *d*(before) / 2 + *d*(after) / 2 ≤ the clip's length. The **maximum duration** at a cut is the smaller of 2 s and what the two neighbors can give (the clip before, minus the half it gives at its own start; the clip after, minus the half at its end), times 2, **rounded down to 0.1 s**. Under 0.1 s: the tool explains (`explain_transition_short`) and nothing applies.
- **Edits that change what fits** shorten the transition in the **same undo step**, down to 0.1 s, and remove it below that, with a 3 s toast `transition_shortened_toast`: trimming a neighbor, Speed (P4-09), Split. **Delete** of a neighbor removes the transition (the pair is new); **Split** puts the clip's transition on the **right piece** (it still precedes the next clip) and the cut between the pieces is a plain cut; **Duplicate** copies it to the copy (the copy's cut to the original's next clip keeps it; the original's cut to its copy keeps it too); **Reorder** moves a clip with its own transition; after a reorder a transition that no longer fits is shortened or removed as above (one toast for the whole reorder); **Replace** and **Crop** and **Filters** keep it; **Undo** restores everything the step touched.
- **The canvas background and Fit:** the transition is applied to each clip's finished picture (after Fit or Fill, framing and all color edits; before captions, overlays and effects, which draw over it). A Fit clip's bars and background transition with it.
- **Overlays** over a cut are untouched; they play across it.
- **Keyframes** of the two clips are untouched: they evaluate on the clip's own time, including inside the window.

### The list (ids saved forever)
Each is a **declarative spec**: progress *p* (0 to 1 across the window, eased by Bézier (0.42, 0, 0.58, 1) unless "linear"), the picture *A* (before) and *B* (after), UV in 0 to 1. Original recipes; any "soft edge" is a smoothstep of 3% of the frame. Default duration 0.5 s unless noted.

| Group | Id | Name | What it does |
| --- | --- | --- | --- |
| Blend | `dissolve` | Dissolve | mix(A, B, *p*), linear light-free, in sRGB |
| | `dip_black` | Dip to black | A fades to black over the first half, B from black over the second (default 0.8 s) |
| | `dip_white` | Dip to white | the same through white; **one brightness swell per cut** (SC 2.3.1) (default 0.8 s) |
| | `blur_through` | Blur through | A blurs to σ = 3% of short by *p* = 0.5, a hard swap, B unblurs |
| Push and slide | `push_left` | Push left | A moves out to the left, B in from the right, together |
| | `push_right` | Push right | the mirror |
| | `push_up` | Push up | A out the top, B in from the bottom |
| | `push_down` | Push down | the mirror |
| | `cover` | Cover | B slides in from the right over a still A |
| | `uncover` | Uncover | A slides out to the left over a still B |
| Wipes | `wipe_right` | Wipe right | a soft vertical edge sweeps left to right, B behind it |
| | `wipe_down` | Wipe down | a soft horizontal edge sweeps top to bottom |
| | `iris` | Iris | a circle grows from the center showing B, radius 0 to the half-diagonal |
| | `clock` | Clock | a radial sweep from the top, clockwise, soft edge |
| | `blinds` | Blinds | 8 horizontal bands, each opening to B from its top edge |
| Zoom and turn | `zoom_through` | Zoom through | A scales 1 to 1.8 while fading out; B scales 0.8 to 1 while fading in |
| | `twirl` | Twirl | A rotates 0 to 90° and scales to 0.6 as it fades; B rotates −90° to 0 from 0.6 (cover scale 1) |
| | `flip` | Flip | A turns about the vertical center line to edge-on (width to 0) by *p* = 0.5, B turns in from edge-on |
| Graphic | `glitch_cut` | Glitch cut | for the window (default 0.4 s): displaced slices and a 1% red-blue offset in 3 bursts of 90 ms or fewer, A until *p* = 0.5 then B; **at most 3 bursts a second** |
| | `pixel_swap` | Pixel swap | A pixelated from 1% to 12% of short by *p* = 0.5, swap, B back down to 1% |

**Counts:** 4 + 6 + 5 + 3 + 2 = **20**. Random-looking parts (`glitch_cut`) use the same time-hash as P4-06 (no live random numbers).
**Not offered:** transitions that need a third picture, 3D cube or page curl (a 20 is the PRD's number; later additions are new ids).

## States
| State | What shows | Copy |
| --- | --- | --- |
| Tool in the bar | `transition` icon and label; present on every main clip | `tool_transition` |
| Panel, cut with no transition | None selected; the strip; no Duration row | `transition_title` |
| Panel, cut with a transition | Its tile ringed; Duration row | `transition_duration` |
| Tile applied | One step; the cut plays once; the badge appears on the cut | `edit_transition` |
| Duration released | One step; the cut plays once | `edit_transition_duration` |
| Tile picture not made | Plain `surface-raised` square; still tappable | none |
| Last clip, or one clip | Toast on tap | `explain_transition_last` |
| Neighbors too short | Toast on tap, nothing applies | `explain_transition_short` |
| A longer Duration wanted than fits | The thumb stops | none |
| Transition shortened or removed by an edit | Toast 3 s; one step with the edit | `transition_shortened_toast` |
| Cut with a transition, no clip selected | The cue badge on the cut | `a11y_state_transition` |
| Neighbor selected, reorder, trim held, narrow neighbor | The badge hidden | none |
| Preview catching up | Last frame stays; "Updating the preview" after 1 s | `editor_updating_preview` |
| Missing file in a neighbor | The transition still applies: the missing clip plays black (P1-04) | none |
| Offline, permission | Not applicable | none |
| Long content (5 languages, 200% font) | Names wrap to three lines, the strip grows, the panel scrolls; the tool label wraps and the bar grows | none |
| First run | No coach mark; the cue badge appears with the first transition | none |
| An old draft | No transitions | none |

## Interactions
- **Tile tap:** one undo step "Transition"; the panel stays open (the user may try several). **Undo and redo** work with the panel open and move the ring.
- **Stage taps** play or pause as everywhere (a tap during the one-time play stops it).
- **Reduce motion:** the one-time play is the content (the transition itself) and still plays.
- **Keyboard (basic, P1):** Tab reaches the tiles and the slider; Enter applies; arrows step Duration by 0.1 s; Escape is back.
- **Haptics:** none.
- **Undo steps:** "Transition" (a tile), "Transition length" (a release), "Remove transition" (None), and any shortening folds into the edit that caused it.

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Panel opens, closes | 200 ms slide | 120 ms fade (instant on Android) |
| Tile ring, tile picture arriving | 120 ms fade | same |
| The cue badge appearing or going | 120 ms fade | same |
| The transition itself | Content: plays with the video | still plays |
| Toasts | 120 ms fade | same |

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5). Budgets are characters; names sit under 72 dp tiles in `caption` (two lines of about 12 characters at 100%).

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_transition` | Transition | 14 | Transisi · Transición · Transição · ट्रांज़िशन | Tool label and panel title (`transition_title` is the same string). es and pt are 10 and 9 characters: the tool widens by under 10 dp at 100% |
| `tool_transition_click` | Change how this clip leads into the next | 48 | Ubah cara klip ini beralih ke klip berikutnya · Cambia cómo pasa este clip al siguiente · Mude como este clipe passa para o próximo · बदलें कि यह क्लिप अगली में कैसे जाती है | Click label ("double-tap to …"); 40 en |
| `transition_none` | None | 12 | Tanpa · Ninguna · Nenhuma · कोई नहीं | The first tile; same word as the other "none" tiles |
| `transition_duration` | Duration | 14 | Durasi · Duración · Duração · अवधि | Slider label |
| `transition_dur_value` | %1$s s | 8 | %1$s dtk · %1$s s · %1$s s · %1$s सेकंड | Readout, `timecode`: "0.5 s"; hi spells out सेकंड (never "से") |
| `tr_dissolve` | Dissolve | 16 | Lebur · Fundido · Dissolver · डिज़ॉल्व | |
| `tr_dip_black` | Dip to black | 16 | Redup ke hitam · Fundido a negro · Mergulho no preto · काले में फ़ेड | Two lines |
| `tr_dip_white` | Dip to white | 16 | Redup ke putih · Fundido a blanco · Mergulho no branco · सफ़ेद में फ़ेड | Two lines |
| `tr_blur_through` | Blur through | 16 | Lewat buram · Pasar desenfocado · Passar desfocado · ब्लर से गुज़रें | |
| `tr_push_left` | Push left | 16 | Dorong kiri · Empujar a la izquierda · Empurrar à esquerda · बाएं धकेलें | |
| `tr_push_right` | Push right | 16 | Dorong kanan · Empujar a la derecha · Empurrar à direita · दाएं धकेलें | |
| `tr_push_up` | Push up | 16 | Dorong atas · Empujar arriba · Empurrar para cima · ऊपर धकेलें | |
| `tr_push_down` | Push down | 16 | Dorong bawah · Empujar abajo · Empurrar para baixo · नीचे धकेलें | |
| `tr_cover` | Cover | 16 | Tutup · Cubrir · Cobrir · ढकें | The next clip slides over |
| `tr_uncover` | Uncover | 16 | Buka · Descubrir · Descobrir · खोलें | This clip slides away |
| `tr_wipe_right` | Wipe right | 16 | Sapu kanan · Cortinilla a la derecha · Cortina à direita · दाएं वाइप | |
| `tr_wipe_down` | Wipe down | 16 | Sapu bawah · Cortinilla hacia abajo · Cortina para baixo · नीचे वाइप | |
| `tr_iris` | Iris | 16 | Iris · Iris · Íris · आइरिस | A circle opens |
| `tr_clock` | Clock | 16 | Jam · Reloj · Relógio · घड़ी | A clock-hand sweep |
| `tr_blinds` | Blinds | 16 | Tirai · Persiana · Persiana · ब्लाइंड | |
| `tr_zoom_through` | Zoom through | 16 | Zoom tembus · Zoom a través · Zoom através · ज़ूम थ्रू | |
| `tr_twirl` | Twirl | 16 | Putar · Giro · Giro · घुमाव | |
| `tr_flip` | Flip | 16 | Balik · Voltear · Virar · पलटें | |
| `tr_glitch_cut` | Glitch cut | 16 | Potong glitch · Corte glitch · Corte glitch · ग्लिच कट | |
| `tr_pixel_swap` | Pixel swap | 16 | Tukar piksel · Cambio de píxeles · Troca de pixels · पिक्सल अदला-बदली | |
| `explain_transition_last` | Add a clip after this one to use a transition. | 60 | Tambah klip setelah ini untuk memakai transisi. · Añade un clip después de este para usar una transición. · Adicione um clipe depois deste para usar uma transição. · ट्रांज़िशन के लिए इसके बाद एक क्लिप जोड़ें। | Toast; 46 en, es 54, pt 56 |
| `explain_transition_short` | These clips are too short for a transition. | 60 | Klip ini terlalu pendek untuk transisi. · Estos clips son demasiado cortos para una transición. · Estes clipes são curtos demais para uma transição. · ट्रांज़िशन के लिए ये क्लिप बहुत छोटी हैं। | Toast; 43 en |
| `transition_shortened_toast` | Transition shortened to fit. | 40 | Transisi dipersingkat agar muat. · Transición acortada para que quepa. · Transição encurtada para caber. · ट्रांज़िशन छोटा किया गया ताकि फिट हो। | Toast 3 s; when removed, the key `transition_removed_toast` "Transition removed: the clips are too short." (50) is used: id "Transisi dihapus: klip terlalu pendek." · es "Transición quitada: los clips son demasiado cortos." (52) · pt "Transição removida: os clipes são curtos demais." · hi "ट्रांज़िशन हटाया गया: क्लिप बहुत छोटी हैं।" |
| `a11y_state_transition` | Transition to next clip: %1$s | 40 | Transisi ke klip berikutnya: %1$s · Transición al siguiente clip: %1$s · Transição para o próximo clipe: %1$s · अगली क्लिप में ट्रांज़िशन: %1$s | Appended to the clip's spoken description |
| `edit_transition` | Transition | 24 | as `tool_transition` | Edit name |
| `edit_transition_duration` | Transition length | 24 | Panjang transisi · Duración de transición · Duração da transição · ट्रांज़िशन की अवधि | |
| `edit_transition_remove` | Remove transition | 24 | Hapus transisi · Quitar transición · Remover transição · ट्रांज़िशन हटाएं | |

Reused unchanged: `panel_done`, `editor_updating_preview`, `tool_split`, `tool_duplicate`.

Translator notes:
- A **transition** is how one clip changes into the next at the cut (a dissolve, a push). Use the word video editors use in your language; keep the same word on the tool and the panel.
- The 20 names are short labels under pictures; "Cover" and "Uncover" are about which clip slides: the next one over this, or this one away.
- **Hindi:** the interface uses the system Devanagari face; seconds are written in full (सेकंड), never "से".

## Accessibility
- **Focus and TalkBack order:** the panel's title takes focus, then Done, then the strip as a single-choice group named "Transition" ("Dissolve, selected, 2 of 21"), then Duration. Closing returns focus to the Transition tool. The panel covers the timeline and tool bar (`coveredByModal`).
- **The cut is spoken** on the clip before it (`a11y_state_transition`). The clip's spoken description order is: Cropped, Rotated, Flipped, Moved or resized, Green screen, Filter or Adjusted, Mask, Blend, Keyframes, **then Speed, Reversed and Transition last** (P4-09, P4-10 append in that order). Never in the state description.
- **Clip actions** (TalkBack's menu on a main clip) gain "Transition" opening the panel: the no-visible-control route for a screen-reader user.
- **Touch targets:** tiles 72 dp wide and at least 48 tall in a 90 dp column (`filter-tile-size`); the slider row 48; the cue badge is output only.
- **Contrast:** names `text` on `surface` 15.6:1; the ring `selection` on `surface` 17.2:1; the cue `text` on `scrim` chip over thumbnails 4.3:1 or more; slider as its README. **State is never color alone** (ring, label).
- **Flashing:** Dip to white and Glitch cut obey SC 2.3.1 (one swell, at most three bursts a second).
- **Reduce motion:** see Motion. **200% font:** see States.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never while a tile preview plays; never for opening the panel). New ids for the PM to confirm:

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | A tile that changes the transition | `tool: "transition"`, `value: "<transition id>"` |
| `tool_use` | None | `tool: "transition_remove"` |
| `tool_use` | A Duration release that changes it | `tool: "transition_duration"` |

`value` is a fixed catalog id. Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `transition-check` (Requests 4) and a 360 × 640 and 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-08/`.
1. **The tool:** the main clip bar scrolled to Transition; on the last clip the toast; Spanish at 200%.
2. **The panel:** None selected; Dissolve selected with Duration, at 640 (Duration half in view), 800 and 915; tiles from the real frames; Hindi at 200%.
3. **The cue badge** on a cut; hidden with a neighbor selected; hidden when a neighbor is narrow (zoomed out).
4. **Each of the 20** against its reference at progress 25%, 50% and 75% (below).
5. **Shortening:** trim a neighbor under the transition's need: the toast, one undo step.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Audio is continuous and untouched.** On **`transition-check`**: clip A with a 440 Hz tone, clip B with an 880 Hz tone, both at the same level, 3 s each, a cut at 3.0 s. Export with each of the 20 transitions at 0.5 s and at 2.0 s and with none, 1080p30 AAC: (a) the decoded audio of every export is **identical to the none export within −60 dB** (null test over the whole file); (b) the audio's length equals the video's within one frame (33 ms); (c) no 1 ms stretch of digital silence within ±1 s of the cut; (d) the 20 ms RMS level across the window stays within ±0.5 dB of the tone level on both sides (a cut has no dip and no double level). Same with a **muted** clip, a **sped** clip and a clip with a **meme sound** across the cut: unchanged.
- **Length and sync:** the exported video's length with and without a transition is equal; a meme sound placed at 3.5 s is at 3.5 s in both.
- **Each of the 20 matches its reference render** (`docs/qa/references/P4-08-transitions/`, 1080 × 1920, from an export, `<id>-p25|p50|p75.png`) on `transition-check`: clip A = a lettered 12 × 12 grid with a moving white square, clip B = colour bars with a moving square; 99% of pixels within 3/255 per channel; the preview at the same instants within 3/255 for 99%; at the window's start and end the frame equals plain A and plain B. Also `hold-edge` (no spare media: A's last frame held, B's first frame held) and `real-edge` (spare media: real frames both sides), and `photo-photo` (two stills).
- **Preview at 30 fps or more** through each transition on the owner's phone (Media3 logs, release-type build); janky frames under 5% across a Duration drag.
- **Each edit is one undo step:** a tile, a release, None, an edit that shortens; Undo once each.
- **Old drafts open** with no transitions; **leaks:** LeakCanary: open the panel, try 10 tiles, drag Duration, close; repeat 5 times.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| The transition | `MediaClip.transitionOut: Transition? = null` with `id: String`, `durationUs: Long`; main-track clips only |
| Declarative | A `TransitionSpec` per id in `:core:model` (rule 5), the recipes above as constants, with the reference renders (the `media-effect` skill) |
| Composition | The engine renders the window as a two-input pass over two clip pictures with spare media or a held edge frame; **the audio graph is built as if there were no transition** |
| Edits | `setTransition(clipId, id?, durationUs)`, the fit check (max duration), and the shortening folded into trim, speed, split, reorder edits; pure, one `commit` each |
| Version | One `schemaVersion` bump (with P4-09 and P4-10's fields if added together); the field defaults to null |

## Requests to the principal mobile engineer
1. **Engine:** verify at the start how Media3 can render a two-input pass at a cut (a `VideoCompositor` or custom compositor over two sequences versus per-clip overlay effects); the contract is fixed here: window around the cut, no length change, **audio sample-identical**. If a Media3 limit forces overlap, stop and tell the PM, who tells the owner (Question 1 B is a different product).
2. **Spare media:** the clip's source frames beyond its trim are used when present; else the edge frame is held; the same rule in preview and export.
3. **The panel:** the strip from the P4-07 thumbnail use case (two real frames at the cut, the transition at 50% on demand), the Duration slider with the fit check, the one-time play, `coveredByModal`.
4. **Debug draft `transition-check`** (tone clips, grid and bars clips, a sound across the cut) and the reference scenes.
5. **The cue badge** in the draw phase; the clip action; the a11y state; strings in the five files; three `EditorTool` values.
6. **Constants:** one commonMain `TransitionCatalog` with a comment pointing to this spec and `design/tokens.json` → `transitions`.
7. **Icon:** `transition` (below).

## Tokens and icons
- **No new color, size, radius or motion token.** The tile is `filter-tile-size`, the cue is `badge-size` with `icon-small`.
- **New content group** `transitions` in `design/tokens.json` and `design/system/tokens.json`. Kotlin: `TransitionCatalog`, not in `MemixTheme`.
- **One new icon** (the engineer draws, I approve in the review): `transition` (Kotlin `MemixIcons.Transition`, `ic_transition.xml`): a **stroked** 9 × 14 px clip (2 px, 1 px corners) at the left and a **solid** 9 × 14 px clip (the core) at the right, 4 px apart, joined across the gap by a 2 px stroke with a solid 4 px arrowhead pointing right, 1.4 px or more of air to the solid clip. It must not read as `split` (two outlined clips around a solid bar) or `replace` (a clip between two curved arrows).
- **Pointer edits made with this spec:** ToolBar README (the main-clip bar, Transition), Timeline README (the cut's cue), DESIGN_SYSTEM (iconography, transitions paragraph).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. Does a transition change the video's length?**
- *Problem:* a transition needs both clips on screen at once; either they overlap (the video gets shorter) or the picture borrows spare frames and holds edge frames.
- *Considerations:* **A. Length stays; nothing moves; sound untouched** (recommended; applied): sounds and captions placed by hand stay in sync, and the audio is provably unchanged (the Done when). Cost: a held edge frame (at most 0.25 to 1 s) where a clip has no spare media; the blend hides it. **B. The clips overlap and the video gets shorter by each transition:** real frames on both sides, simpler engine, but every later item drifts (sounds don't follow ripple edits, P1-06) and the sound needs a crossfade.
- *Reverse:* a different engine contract, not a data change; decide before 08 starts.

**2. Where is the door: a button on every cut, or a tool in the clip bar?**
- *Problem:* a visible control on each cut is the usual pattern, but it competes with selecting short clips.
- *Considerations:* **A. Tool in the clip bar + a cue badge on cuts that have one** (recommended; applied): nothing breaks short clips; the bar's tool is one swipe away. Cost: less discoverable. **B. A 48 dp button on every cut:** discoverable; cost: needs hide rules against clips under about 100 dp and against both trim handles, and short clips become hard to tap.
- *Reverse:* B can be added later as a second route.

**3. Default duration 0.5 s, maximum 2 s?**
- *Problem:* a meme cut is fast; a long dissolve reads as slow.
- *Considerations:* **A. 0.5 s default, 0.1 to 2 s** (recommended; applied). **B. 1 s default:** friendlier for photo slideshows, slow for memes.
- *Reverse:* a constant; saved transitions keep their own duration.
