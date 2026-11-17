# Reverse and freeze frame · P4-10

Ninth Phase 4 spec (rewritten 17 Nov after a lost session). It reuses, and does not repeat:
- `P4-09-speed.md`: the retime chip, the Sound choice (Keep or Mute), the Detach audio rules, the main clip bar's final order, how keyframes and transitions follow a length change.
- `P1-06-clip-edits-and-audio.md`, `P1-05-timeline.md`: Split (selects the right piece), ripple on the main row, "Preview catching up", the clip look, undo naming. `P1-16` (Replace). `P4-16-clip-crop-rotate-flip-fit.md` and `P4-04-green-screen.md`: the clip pipeline and the main photo clip's tools.
- `P4-08-transitions.md`: a cut keeps its transition rules. `P4-02b-keyframes.md`: keyframes.
- Component READMEs: ToolBar, Timeline, Toast.

## Job
"Play it backwards" (the fall that becomes a jump) and "stop on the face" (freeze on the reaction, then the caption lands). PRD → Video editor → Basic edits: freeze frame, reverse. **One main action: none.** Export stays the one blue action (P1-12).

**Done when** (TICKETS): reversed clips play smoothly in preview. Made checkable in Measured (frame order, 30 fps, no stutter). Freeze frame has no Done-when of its own; its checks are in Measured too.

**Fast path, with tap counts.**
- **Reverse a clip:** the clip (1), Reverse (2, the sixth tool, in view after one short swipe). One tap, no panel; it shows "Reversing" first if the phone has to prepare it (below).
- **Freeze the face:** scrub to it, the clip (1), **Freeze frame** (2). A 2 s still lands at the playhead, selected; then the caption (Text, 2 taps) lands on it.
- **Longer freeze:** drag its end handle (or Trim to playhead).
- **Undo either:** one step.

**Principles check.** Content first: no panel, no chrome; the picture does the work. Sound first: reversing flips the clip's sound too; the pinned pill is untouched. Fast path: two taps each. One-handed: the tools sit in the bar's first six places. Forgiving: each is one undo step and both undo exactly; reverse is a switch (tap again), the source is never changed. Accessible: tools are buttons with spoken state. Five languages: labels measured at 200%.

**Color roles:** no blue except the active look on a reversed clip's tool (`primary` on `primary-subtle`, as Keyframes' Remove keyframe); the chip is `text` on `scrim`. No new color.

## Entry and exit
There are no panels or sheets. Both tools act at once.

| Tool | Where | Acts on |
| --- | --- | --- |
| **Reverse** | Main video clip bar; video overlay bar (final orders in P4-09) | The selected clip: toggles reversed |
| **Freeze frame** | Main video clip bar only (Question 2) | The selected clip at the playhead |

- **Not offered on photos** (a still has no direction and is already frozen): the tools are **absent** from photo clip bars, and on a clip whose file is missing (P4-16 order). Not on captions, stickers, sounds, effects.
- Back order and selection unchanged (the clip stays selected after Reverse; **the new freeze clip becomes selected** after Freeze frame).
- **Kept:** the reversed flag, the freeze clip, in the project. Not kept: the reversed copy's cache (rebuilt on demand).

## Layout
Board: none. The tools are P4-09's bars; the look is below.

### Reverse
- **Icon `reverse`** (new, below), label "Reverse" (`tool_reverse`), the normal tool look (`text-secondary`). It is a **toggle**: when the clip is reversed the tool takes the **active look** (`primary` on `primary-subtle`, `radius-md`), as Keyframes' Remove keyframe does for "the playhead is on one"; the label stays "Reverse" (WCAG 2.5.3), the spoken state is "on" or "off" (a switch role), and the click label names the tap: "Play this clip backwards" or "Play this clip forwards again".
- **What reverse does:** the clip plays from its last frame to its first, **its sound too** (backward), at the clip's speed (P4-09). Its length, start, trims, crop, filters, key, mask, framing, Fit or Fill and transitions are unchanged; keyframes stay on the clip's timeline time (the framing animates as before, the picture inside plays backward). The Speed panel's **Sound: Mute** silences it, as for any clip.
- **The clip on the timeline:** thumbnails show the frames in the order they now play; the retime chip (P4-09) shows the `reverse` icon first.
- **Detach audio** on a reversed clip explains (`explain_detach_reversed`): a sound clip has no direction. (P4-09's rule list gains this row.)
- **Split** (P1-06) cuts a reversed clip at the playhead: both pieces stay reversed; the piece on the left of the cut shows the *later* part of the source. **Duplicate, Replace** (keeps the flag), **Crop, Filters:** nothing special.
- **Limit:** reverse works on a clip whose **trimmed source is at most 60 s** (`reverse.maxSourceSeconds`, Question 1). A longer clip explains (`explain_reverse_long`) and nothing changes. Trimming a reversed clip down is always allowed.

### Preparing the reversed picture (no spinner, no blocking)
A video can't be played backward directly (frames depend on earlier frames), so the engine prepares a reversed copy of the clip's trimmed range on the phone. The UI contract:
- **The tap is instant:** the tool takes its active look and the chip appears at once (one undo step "Reverse"). The edit never waits.
- **Up to 1 s: nothing shown.** After 1 s, if the copy isn't ready, a status in the stage's one slot, Toast look, no spinner (MOTION 2): **"Reversing 40%"** (`reverse_preparing`, the percent updates a few times a second, a polite live region, not a progress bar). Gone, with a 120 ms fade, when ready. Banner first, one slot (P1-04).
- **While it isn't ready,** the rest of the project plays normally; when playback reaches the clip, the stage holds the clip's **first frame** (the one it will start on) and the clip is silent, until ready: no glitching, no frames out of order. A stage tap still plays and pauses.
- **Changing the clip's source range** (trimming out, extending, Replace) prepares again for the new range, with the same status; trimming **inside** the range reuses the copy.
- **Export:** an unfinished copy is finished first; the Export screen's own progress covers it (no new state; the engineer notes the time cost in the handoff, as the kickoff asked).
- **Memory and storage:** the copy lives in the app cache, is removed when the clip is un-reversed, deleted, or the project closes after a week unused; a full phone makes the P1-02 "Not enough space" toast with Free up space (`explain_reverse_space`, shared wording with P1-02).
- **Cancel:** un-reversing (a tap on the active tool, or Undo) cancels the preparation at once.

### Freeze frame
- **Icon `freeze`** (new, below), label "Freeze frame" (`tool_freeze_frame`, two lines), the normal look; click label "Hold the picture at the playhead".
- **What a tap does:** the clip is **split at the playhead** and a **frozen still of that frame**, **2 s** long (`freeze.defaultMs` 2000), is **inserted between the two pieces**. The pieces and every later main clip move later by the freeze's length (main-track ripple; sounds, captions, overlays and effects stay where they are, the P1-06 rule). One undo step "Freeze frame" covers the split and the insert.
- **The freeze clip:** a main-track **still clip like a photo clip**: it is selected after the tap, has the P4-16 picture tools, takes a transition, and its length is trimmed with its handles (0.1 s to 10 s, `freeze.maxMs` 10000; **Trim to playhead** and the end handle). Its picture is the clip's frame at the playhead **as the viewer sees it**: the clip's own crop, turns, mirrors, Fit or Fill, framing and color edits (evaluated at that time if keyframed) are copied into the freeze clip and its keyframes start empty; speed and reverse are not copied (a still has none). Its sound is **silent**; the source clip's sound stops at the cut and resumes after the freeze (Question 3).
- **The freeze clip's bar:** the photo clip bar without Replace: **Split · Delete · Crop · Fill or Fit · Position · Filters · Green screen · Mask · Keyframes · Transition · Duplicate · Reorder** (no Volume, Speed, Freeze frame, Reverse, Detach audio, Replace: absent).
- **Where the playhead is:** inside the clip and at least 0.1 s from each end: the split above. Within 0.1 s of the clip's **start**: no split; the freeze lands **before** the clip using its first frame. Within 0.1 s of the **end**: after the clip using its last frame (no sliver pieces under 0.1 s). Outside the clip, or on a gap: the tool explains (`explain_freeze_outside`, "Move the playhead onto the clip first.").
- **The cut with a transition:** a transition on the clip (P4-08) moves to the **right piece** (it still leads into the next clip); the two new cuts are plain, as Split. The freeze clip has none until the user adds one.
- **The clip on the timeline:** the freeze clip shows its still as a **tiled thumbnail strip** and the retime chip with the `freeze` icon (P4-09 chip, no text), and its spoken description says "Frozen frame". It looks like a photo clip; the chip is what tells it apart.
- **Source gone:** the freeze keeps a reference to the source clip's frame; if the file goes missing it is the same "File missing" state as any clip, and Replace is not offered (use Delete).

## States
| State | What shows | Copy |
| --- | --- | --- |
| Reverse tool, off | Normal look | `tool_reverse` |
| Reverse tool, on | Active look; the chip with `reverse` | `tool_reverse` (switch on) |
| Reverse tapped | Instant; one step | `edit_reverse` |
| Preparing, up to 1 s | Nothing | none |
| Preparing, over 1 s | Status with percent; the clip holds its first frame, silent, when played | `reverse_preparing` |
| Ready | Status fades; the clip plays backward | none |
| Clip over 60 s | Toast on tap; nothing changes | `explain_reverse_long` |
| Phone full | Toast with Free up space | `explain_reverse_space` |
| Preparing fails (decoder error) | The flag is switched back off, one toast; the edit is undone as one step | `reverse_failed` |
| Detach on a reversed clip | Toast | `explain_detach_reversed` |
| Freeze tapped, inside the clip | The freeze lands selected after a bonk; ripple; one step | `edit_freeze_frame` |
| Freeze near an end | Lands before or after, no sliver | none |
| Freeze, playhead off the clip | Toast | `explain_freeze_outside` |
| Freeze clip selected | The freeze bar; the chip | `a11y_state_frozen` |
| Freeze trimmed to its limits | The handle stops at 0.1 s and 10 s | none |
| Photo clip, missing file | Both tools absent | none |
| Preview catching up | Last frame stays; "Updating the preview" after 1 s | `editor_updating_preview` |
| Reorder mode | Main bar replaced; unreachable | P1-06 |
| Undo | Reverse: flag off; Freeze: pieces joined, still gone | `editor_undo_toast` |
| Offline, permission | Not applicable | none |
| Long content (5 languages, 200% font) | Tool labels wrap (es "Congelar fotograma" three lines), the bar grows; the status wraps | none |
| First run | No coach mark | none |
| An old draft | No reversed clips, no freezes | none |

## Interactions
- **Reverse:** one tap toggles; one undo step each way ("Reverse"); playback is not paused. **Freeze frame:** one tap; playback **pauses** (the playhead sits on the new freeze's start).
- **Haptics:** none (Confirm belongs to sounds). The freeze's landing bonk (`duration-bonk`) is the feedback; with reduce motion, none.
- **Keyboard (basic, P1):** Tab reaches the tools; Enter activates; Escape is back.
- **Undo steps:** "Reverse" (both directions), "Freeze frame" (split and insert together).

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Tool takes or loses its active look | 120 ms | same |
| The freeze clip lands, later clips ripple | `duration-bonk` on the new clip; 200 ms ripple | 120 ms cross-fade, no bonk |
| Retime chip appears | 120 ms fade | same |
| Status ("Reversing") | 120 ms fade; the percent is text | same |

Nothing loops (MOTION 2).

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5). Budgets are characters. Percent signs in resources are written as a single `%`.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_reverse` | Reverse | 12 | Balik · Invertir · Inverter · उल्टा | Tool label. Means "play backwards", not "undo"; es and pt are 8 characters: one word |
| `tool_reverse_click_on` | Play this clip forwards again | 40 | Putar klip ini maju lagi · Reproducir este clip hacia delante · Reproduzir este clipe para a frente · इस क्लिप को फिर आगे चलाएं | Click label when on; 29 en |
| `tool_reverse_click_off` | Play this clip backwards | 40 | Putar klip ini mundur · Reproducir este clip al revés · Reproduzir este clipe de trás para a frente · इस क्लिप को उल्टा चलाएं | Click label when off; 24 en, pt 44 (trim to "Reproduzir este clipe ao contrário" 34) |
| `tool_freeze_frame` | Freeze frame | 20 | Bekukan frame · Congelar fotograma · Congelar quadro · फ़्रेम रोकें | Tool label; two lines "Freeze / frame" |
| `tool_freeze_frame_click` | Hold the picture at the playhead | 40 | Tahan gambar di playhead · Mantener la imagen en el cabezal · Manter a imagem na posição atual · प्लेहेड पर तस्वीर रोकें | Click label; the playhead word as the shipped `tool_move_here_click`; 32 en |
| `reverse_preparing` | Reversing %1$d% | 24 | Membalik %1$d% · Invirtiendo %1$d% · Invertendo %1$d% · उल्टा कर रहे हैं %1$d% | Status; polite live region |
| `reverse_failed` | Couldn't reverse this clip. Try again. | 50 | Tidak bisa membalik klip ini. Coba lagi. · No se pudo invertir este clip. Inténtalo de nuevo. · Não foi possível inverter este clipe. Tente de novo. · यह क्लिप उल्टी नहीं हो सकी। फिर कोशिश करें। | Toast; 39 en, es 55: trim "Inténtalo otra vez." when drafting |
| `explain_reverse_long` | Reverse works on clips up to 60 seconds. Split this one first. | 70 | Balik hanya untuk klip sampai 60 detik. Potong dulu klip ini. · Invertir funciona con clips de hasta 60 segundos. Divide este primero. · Inverter funciona com clipes de até 60 segundos. Divida este primeiro. · उल्टा करना 60 सेकंड तक की क्लिप पर चलता है। पहले इसे बाँटें। | Toast; 62 en, es 66 |
| `explain_reverse_space` | Not enough space to reverse. Free up space and try again. | 70 | Ruang tidak cukup untuk membalik. Kosongkan ruang lalu coba lagi. · No hay espacio para invertir. Libera espacio e inténtalo de nuevo. · Sem espaço para inverter. Libere espaço e tente de novo. · उल्टा करने की जगह नहीं है। जगह खाली करके फिर कोशिश करें। | Toast with the P1-02 Free up space action; 57 en |
| `explain_freeze_outside` | Move the playhead onto the clip first. | 50 | Pindahkan playhead ke klip dulu. · Mueve primero el cabezal sobre el clip. · Mova antes a posição atual para o clipe. · पहले प्लेहेड को क्लिप पर ले जाएं। | Toast; 38 en |
| `explain_detach_reversed` | Reversed clips can't detach their sound. Turn Reverse off first. | 70 | Klip terbalik tidak bisa dipisah suaranya. Matikan Balik dulu. · Los clips invertidos no pueden separar su sonido. Desactiva Invertir primero. · Clipes invertidos não podem separar o som. Desative Inverter primeiro. · उल्टी क्लिप की आवाज़ अलग नहीं हो सकती। पहले उल्टा बंद करें। | Toast; 63 en, es 73 (trim "Quita Invertir primero.") |
| `a11y_state_reversed` | Reversed | 16 | Terbalik · Invertido · Invertido · उल्टा | Appended to the clip's spoken description (after Speed) |
| `a11y_state_frozen` | Frozen frame | 16 | Frame beku · Fotograma congelado · Quadro congelado · रुका हुआ फ़्रेम | Spoken description of a freeze clip |
| `a11y_freeze_added` | Frozen frame added | 24 | Frame beku ditambahkan · Fotograma congelado añadido · Quadro congelado adicionado · रुका फ़्रेम जुड़ा | Polite |
| `edit_reverse` | Reverse | 24 | as `tool_reverse` | Edit name |
| `edit_freeze_frame` | Freeze frame | 24 | as `tool_freeze_frame` | Edit name |

Reused unchanged: `editor_updating_preview`, `editor_undo_toast`, `editor_missing_media`, the P1-02 Free up space action.

Translator notes:
- **Reverse** plays the clip backwards, sound included. **Freeze frame** stops the picture on one frame for a moment and keeps the video going afterwards. Use the words video editors use for these two.
- "Reversing 40%" is a live progress line, not a button.
- **Hindi:** the interface uses the system Devanagari face; no row has a fixed text height.

## Accessibility
- **Reverse** is a switch: "Reverse, switch, on" (state description "On" or "Off", the click label names the tap). The status is a polite live region (`reverse_preparing`, announced at most every 25% to avoid chatter); done is announced "Reverse ready" (`a11y_reverse_ready`: "Reversed clip ready" · Klip terbalik siap · Clip invertido listo · Clipe invertido pronto · उल्टी क्लिप तैयार).
- **Freeze frame:** after the tap focus moves to the new clip's node, "Frozen frame, 2 seconds" (`a11y_freeze_added` is announced). The clip actions menu of a main video clip lists Reverse and Freeze frame (no drag needed anywhere here).
- **The clip's spoken description** appends "Speed 2×" (P4-09), then "Reversed", then the transition (P4-08), after Keyframes. Never in the state description.
- **Touch targets:** bar tools 48 dp or more; the chip is output only. **Contrast:** the active look `primary` on `primary-subtle` over `surface` 6.2:1; the chip `text` on `scrim` 7.6:1 or more; the status `text` on `surface-raised` 12.4:1. **State is never color alone:** the switch's spoken state and the chip's `reverse` icon.
- **200% font:** see States. **Reduce motion:** see Motion.

## Analytics
`tool_use`, `editor: "video"`, once per committed undo step (never when a tool only explains):

| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | Reverse switched on or off | `tool: "reverse"`, `value: "on"` or `"off"` |
| `tool_use` | Freeze frame | `tool: "freeze_frame"` |

Analytics stay off until consent (P5-01).

## QA compares
Use the debug clip `reverse-check` (Requests 4) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-10/`.
1. **The bars:** a main video clip's bar with Freeze frame and Reverse in view after a short swipe; a photo clip (neither); a video overlay (Reverse); Spanish at 200% ("Congelar fotograma").
2. **Reverse on, off, preparing:** the active look, the chip, the "Reversing 40%" status (on a 60 s clip so it shows), the held first frame while preparing.
3. **Freeze:** the freeze clip landing mid-clip (three pieces on the main row), the chip, its bar; near the start (lands before); outside the clip (the toast).
4. **Reversed clip's thumbnails** in playing order; a split reversed clip (the left piece shows the later part).
5. **Detach audio** on a reversed clip (toast).

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **Reversed clips play smoothly in preview:** the debug clip `reverse-check`: 10 s, 1080p30, a burned-in source time to the centisecond, a falling-pitch tone sweep. Reverse it and play it in the editor preview on the owner's phone (release-type build, Media3 logs): (a) **30 fps or more**, janky frames under 5%; (b) a screen recording stepped frame by frame shows the burned-in time **falling by one source frame per output frame** (33 ms), no repeated or skipped frames over 10 s; (c) scrubbing across the clip shows the right frame within the P1-04 100 ms rule once the copy is ready; (d) audio is the sweep played backward (a rising pitch), no clicks (the export's spectrogram, checked by eye).
- **Export equals preview:** the exported frames at five times show the same source time as the preview (within one source frame).
- **Preparing:** a 60 s 1080p clip reaches ready in the time the engineer reports (recorded; the PM judges), the percent rises steadily, playback of the other clips is never blocked, and un-reversing during preparation stops it at once.
- **Limits:** a 61 s clip refuses with the toast; trimming it to 60 s then allows it.
- **Freeze:** the still's picture equals the clip's frame at the playhead within 3/255 (exported frame vs the same time on the source with the clip's look); the pieces around it continue the source time without a gap or repeat (left piece ends at the playhead's source time, right starts there); the project grows by exactly the freeze length (2.0 s) and later main clips move by it, sounds don't; its sound is silent; one Undo removes it and rejoins the clip.
- **Each edit is one undo step:** Reverse on, off, Freeze: Undo once each.
- **Reference renders** (CLAUDE.md rule 5) in `docs/qa/references/P4-10-reverse-freeze/`: `reverse-t25`, `reverse-t50`, `reverse-t75` (frames with the burned-in time), `freeze-mid`, `freeze-near-start`.
- **Old drafts open;** **leaks:** LeakCanary: reverse, wait, un-reverse, freeze, undo, close the editor; repeat 5 times (and the cache is cleaned).

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| Reverse | `MediaClip.reversed: Boolean = false`; the trimmed source range maps from its end; split keeps the flag; sound reversed with the picture |
| Freeze | A main-track `MediaClip` with `freeze: FreezeSource(sourceMediaRef, sourceTimeUs)?` behaving like a photo clip for length and tools; the look copied at creation; no new file stored |
| Derived | The reversed copy is cache, never in the project; rebuilt when the source range changes |
| Edits | `setClipReversed`, `freezeFrame(clipId, timeUs)`, pure, one `commit` each (the freeze is split plus insert, with the transition moved to the right piece) |
| Version | One `schemaVersion` bump with P4-09's (fields default to false and null) |

## Requests to the principal mobile engineer
1. **Decode strategy for smooth reverse** (the kickoff's note): prepare an all-intra (every frame a sync frame) reversed temp copy of the trimmed range on the phone, in the background, with progress; verify the current Media3 route at the start (a Transformer pass with reversed frame order, or the engine's own copy) and **record peak memory, time and cache size for a 60 s 1080p clip** in the handoff; no FFmpeg. If 60 s is wrong for the phone, lower `reverse.maxSourceSeconds` and tell the PM (Question 1).
2. **Preview and export** use the same reversed copy; audio reversed from a decoded buffer (a clip is at most 60 s); Mute respected.
3. **Freeze:** the still comes from the engine's frame decode at the mapped source time with the clip's look applied; the clip behaves as a photo clip (length trimming 0.1 to 10 s).
4. **Debug clip `reverse-check`** and the reference scenes; frames saved to `docs/qa/references/P4-10-reverse-freeze/`.
5. **The status** through the existing one-slot banner with a polite live region; **strings** in the five files; two `EditorTool` values; **icons** `reverse` and `freeze` (below).
6. **Constants:** one commonMain `FreezeReverseCatalog` with a comment pointing to this spec and `design/tokens.json` → `freezeReverse`.

## Tokens and icons
- **No new color, size, radius or motion token.**
- **New content group** `freezeReverse` in `design/tokens.json` and `design/system/tokens.json` (limits and the freeze defaults). Kotlin: `FreezeReverseCatalog`, not in `MemixTheme`.
- **Two new icons** (the engineer draws, I approve in the review):
  - `reverse` (Kotlin `MemixIcons.Reverse`, `ic_reverse.xml`): **two solid left-pointing triangles** (9 px wide, 12 px tall, 1 px corners; all core, like `play`) side by side with 1.4 px or more of air between them. It must not read as `flip` (one solid and one stroked triangle across an axis) or `undo` (a hooked arrow).
  - `freeze` (Kotlin `MemixIcons.Freeze`, `ic_freeze.xml`): a stroked 20 × 16 px frame (2 px stroke, 1 px corners) holding **two solid bars** (3 × 8 px, the core), 3 px apart, centered, 1.4 px or more of air to the frame. It must not read as `pause` (two bars with no frame) or `mask` (a frame holding a circle).
- **Pointer edits made with this spec:** ToolBar README (Reverse, Freeze frame in the main video clip bar; Reverse in the overlay bar), Timeline README (retime chip icons), DESIGN_SYSTEM (iconography, a paragraph).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. How long a clip can be reversed?**
- *Problem:* reversing needs a prepared copy on the phone; a long 1080p clip takes time and storage.
- *Considerations:* **A. Up to 60 seconds** (recommended; applied): meme clips are short, a 60 s clip prepares in seconds to under a minute on a mid phone (Assumption; the engineer measures); cost: a refusal toast on long clips (Split first). **B. Up to 30 s:** safer for old phones, more refusals. **C. No limit:** never refuses, but a long clip can fill the phone or be killed.
- *Reverse:* a constant.

**2. Freeze frame on overlays too?**
- *Problem:* a freeze inserts time; on an overlay lane that means pushing the rest of the lane and fighting neighbors.
- *Considerations:* **A. Main video clips only** (recommended; applied): the meme job is "freeze the main video and add a caption"; no lane-packing cases. **B. Overlays too:** more complete; cost: the P3-09 lane rule applied to an insert, more states and QA.
- *Reverse:* adding overlays later is an extension, no migration.

**3. Does the clip's sound keep playing under a freeze?**
- *Problem:* a frozen picture can be silent, or let the original sound run on (a record-scratch gag).
- *Considerations:* **A. Silent freeze** (recommended; applied): predictable, the sound resumes where it stopped, and the user can drop a meme sound on the freeze (the usual meme move). **B. Sound runs under the freeze:** the clip would play on while its picture is stopped, so the sound and picture drift apart after it; needs a "hold the sound" model.
- *Reverse:* a behavior change; no migration.

**4. Default freeze length 2 s?**
- *Problem:* the freeze needs a length that works for a caption and a sound without an edit.
- *Considerations:* **A. 2 s** (recommended; applied; a constant): long enough to read a caption, short enough to stay snappy. **B. 3 s (a photo's length):** consistent with stills, slower.
- *Reverse:* a constant.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| Video frames depend on earlier frames, so backward playback needs a prepared all-intra copy or heavy decoding; engines reverse by transcoding | Assumption (video-codec knowledge; the engineer measures on the phone) | none |
| Media3 offers Transformer for re-encoding a clip and GL effects per frame | Guidance (API docs; verify at the start) | [Transformer](https://developer.android.com/media/media3/transformer/transformations) |
| A switch's state is spoken and its action named by the click label; a control's name includes its visible label | Guidance | [WCAG 2.2 SC 2.5.3](https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html), [SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Non-text marks need 3:1; color can't be the only cue | Guidance | [SC 1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html), [SC 1.4.1](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html) |
| Mobile editors offer freeze as "insert a still at the playhead" and reverse as a one-tap per-clip tool | Assumption (product knowledge; help pages were not reachable here) | none |
