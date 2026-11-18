# Video background removal for people: tool, progress, preview, refine, export · P4-13 (13a and 13b)

Eleventh Phase 4 spec (written 18 Nov 2026). It reuses, and does not repeat:
- `P2-07-background-removal.md`: the photo cut-out (the checkerboard cells, the Erase and Restore brush, the five brush sizes, edge softness 0 to 100% default 30%, stroke quantizing and the 5,000-point cap, the failure cards and their copy, "Remove cut-out", the privacy line, `bg_remove` analytics). The board `design/screens/RemoveBackground.dc.html` is the photo screen; **the video version is a panel, not a screen** (Decision D1).
- `P4-10-reverse-and-freeze.md`: the "prepared copy with a percent status in the stage's one slot after 1 s" precedent, Undo cancels the work, the full-phone toast. `P4-04-green-screen.md`: the clip pipeline and the alpha-producing step it fixed; the "Blur background shows black under a keyed main clip" limit. `P4-09-speed.md`: the ToolPanel with Tabs (scroll at 640, pinned from 800), the panel budget method.
- `P4-01b-overlay-layers.md`: video overlays (the best place for a cut-out person). `P1-12-export.md`: the Exporting screen and the Export sheet notices. Component READMEs: ToolPanel, Tabs, SegmentedChoice, Slider, ProgressBar, Button, Toast, ToolBar.

## Job
"Cut my friend out of the video": one tap, the background goes, the person stays and can stand on another clip. PRD → Background removal: free, on the phone, **people only in v1** (owner, 8 Oct 2026), a progress bar, a reduced-resolution preview, a full-resolution export, refine. **One main action: none.** Export stays the one blue action (P1-12); the panel's Done is Ghost.

**Done when** (TICKETS): **13a** a masked clip previews with the background removed; **13b** progress shows while processing, and the export matches the preview. Both made checkable in Measured.

**Split (decided here, for the PM).**

| Part | Contains | Why |
| --- | --- | --- |
| **P4-13a** | The tool in the bars and the main bar, the model and edits, MediaPipe person segmentation in source time, the per-frame mask cache, the low-res preview, the cue on the clip, the percent status in the stage's slot, Undo cancels, the failure states, the panel with the View choice and **Remove cut-out** | The 13a Done when; usable without any slider |
| **P4-13b** | The Edges tab (Edge softness, Edge size, Steady edges), the Brush tab, the determinate progress bar with **Cancel** and resume, the full-resolution mask in the export, the export notice and the busy rule, the cache-lost rule | The 13b Done when; refine is the cut-list item (the Brush tab is the first to drop, Question 1) |

**Fast path, with tap counts.**
- **Cut a person out of a clip:** the clip (1), **Remove BG (2)**. The panel opens, the percent runs, the person appears on the checkerboard as frames finish. Two taps, no setting.
- **From the main bar:** with nothing selected, **Remove BG (1)** acts on the main clip under the playhead (as Speed).
- **Put the person on another video:** the cut-out clip is an **overlay** (Overlay tool, pick, Remove BG): the base video shows through.
- **Fix a rough edge (13b):** Edges tab, Edge size or Steady edges (3).
- **Undo:** one step; Undo while processing cancels it.

**Principles check.** Content first: the stage shows the user's clip; the checkerboard is only an inspection view. Sound first: the pinned "Add a meme sound" row and the clip's sound are untouched. Fast path: two taps and no setting. One-handed: tool, panel and Cancel are in the lower two thirds. Forgiving: Undo cancels or removes; the cut is non-destructive (the file is untouched); Remove cut-out gives the picture back. Accessible: every control is a tap, a slider or a choice; the brush is the one essential drag (WCAG 2.5.7 exception, as P2-07). Five languages: panel labels measured at 200%.

**Color roles:** no blue except the slider fills and the progress fill (`primary`). White is selection (the selected tab, segment, brush size). The checkerboard (cells 12 dp in `canvas` and `surface-raised`) and the cut-out result are content on the stage. No new color.

## Decisions
- **D1. A panel, not a screen.** The photo cut-out is a full-screen mode because it is a still. A video needs the timeline, the playhead and playback to judge a cut, so Remove background is a **ToolPanel** over the editor (the stage and transport stay live; the timeline is covered as for every panel). The Brush tab lives in the same panel; it paints on the stage above, which can be zoomed with two fingers (1× to 5×) exactly as P2-07.
- **D2. People only.** The model finds people. The panel says so once, quietly (`cutout_video_people_only`), and the "nothing found" card repeats it with the way out ("Photos can cut out pets and objects").
- **D3. The cut is computed once per source frame and kept in a cache** (in source time, in the app's cache folder, never in the project), so speed, reverse, crop, split, trim inside the processed range and Replace-with-the-same-file do not recompute. The project stores only the settings (softness, edge size, steadiness, strokes). A cleared cache is rebuilt (cache-lost rule).
- **D4. Where it sits in the clip pipeline** (P4-04's order, extended): turn → mirror → crop → **key and cut-out (their alphas multiply)** → color edits → Fit or Fill → mask → framing → opacity → blend. So a filter never re-cuts, a crop never recomputes, and a keyed and cut-out clip works.
- **D5. Where the cut-out is useful.** On an **overlay** the base video shows through. On a **main clip** the canvas background shows (black under Blur, P4-04's known limit; or the Canvas panel's color): the first time per clip a toast says so (`cutout_video_main_hint`).

## Entry and exit
| Surface | Opens when | Closes when |
| --- | --- | --- |
| **Remove background panel** (ToolPanel, Tabs "Edges · Brush" in 13b) | The **Remove BG** tool of a selected video clip or video overlay (this starts the cut-out if the clip has none); or the **Remove BG** tool of the main bar with nothing selected, which selects the main video clip under the playhead (the last clip at the very end) and opens | Done, system back, Escape. **Processing continues** after the panel closes; the clip stays selected |

- **Main bar:** Remove BG after Filters, before Speed (final order from P4-07: … Filters · Remove BG · Speed). Not shown in an empty project or one with no main video clip. If the clip under the playhead is a photo, the nearest video clip to the left, else to the right; with only photos around it, the tool is not shown.
- **Clip bars (proposal to the PM, TICKETS consolidated bars):** **Remove BG** right after **Green screen** (both produce alpha) in the **main video clip** bar and the **video overlay** bar. Absent from photo clips, captions, stickers, sounds and from a clip whose file is missing (P4-16 order). A **reversed** or **speed-changed** clip is allowed (the cache is in source time).
- **Playhead:** playback keeps running if it was running. If the playhead is outside an overlay's span it jumps to its start when the panel opens (P4-01b). **Kept:** the cut-out settings and strokes in the project (autosave, undo). **Not kept:** the open tab, the View choice beyond the visit, the mask cache across a cache clear.
- **Back order:** a stroke in progress is cancelled, a panel closes, the clip bar closes, then the editor.
- **Leaving the editor:** processing stops; the frames already cut stay in the cache and the next open continues from there.

## Layout
Board: none for video (`RemoveBackground.dc.html` is the photo screen). Everything below is new.

### The tool and the clip
- **Icon `cutout`** (exists, P2-07), label "Remove BG" (the shipped `tool_remove_bg`), normal look; click label "Remove the background from this clip". When the clip already has a cut-out the tool takes the **active look** (`primary` on `primary-subtle`, spoken "on") and opens the panel to edit it.
- **The cue on the clip** (every cut-out clip, selected or not): a `scrim` chip (`radius-sm`, 16 dp tall, `space-1` padding) at the clip's **bottom-right**, `space-1` inside, holding the `cutout` icon at `icon-small` in `text`; **while processing** it reads the percent instead ("40%" in `caption` `text`, polite live region on the panel and banner only). Drawn only when the clip is at least 56 dp wide (the retime chip sits bottom-left, the duration badge top-left, the keyframe badge top-right). A cue, not a control. Contrast `text` on `scrim` over a light thumbnail 7.6:1 or more.
- **Status in the stage's one slot** (after 1 s, only while the panel is closed): "Removing background 40%" (`cutout_video_working`), Toast look, no spinner (MOTION 2); a polite live region announced at most every 25%; gone with a 120 ms fade when done.

### The Remove background panel (ToolPanel)
Title row: **Remove background** (`title`, heading), **Remove cut-out** (Quiet; absent until a cut-out exists and during Preparing), **Done** (Ghost); the two buttons wrap under the title at large font. Remove cut-out applies at once as **one step "Remove cut-out"**, closes the panel, and the clip is whole again.

**While it works (13a: the text status only; 13b: this block):** at the top of the content, the status line "Removing the background… 40%" (`cutout_video_working_panel`, `body` `text`), a **determinate ProgressBar** under it (`primary`, `rail-height`), and **Cancel** (Secondary). **Cancel** stops the work and undoes the "Remove background" step (the clip is as before; nothing is kept in the project; the finished frames stay in the cache). The tabs and sliders are not shown until the cut is ready (nothing to adjust yet); the stage plays the **frames already cut and the original after them** (an honest progress: a playhead past the finished point shows the clip as it was), and the status line says so once: "You can keep editing. Frames not done yet show the original." (`cutout_video_working_hint`, `label` `text-secondary`).

**Ready (13a: the View choice and the hint only; 13b adds the tabs):**
1. **View**, a **SegmentedChoice** "Checkerboard · In video" (label "View"; default **Checkerboard**, remembered for the visit): Checkerboard shows **only this clip** over the checkerboard (the rest of the composition and the captions are hidden in the view), with a quiet pill at the stage's top "Showing this clip only" (`cutout_view_note`, `surface-raised`, `radius-full`, `label`); In video shows the composition as it will export. View is a preview aid: never exported, never in the project.
2. **13b, Tabs "Edges · Brush"** (the Tabs component, as P4-09: scroll with the content at 640, pinned from 800):
   - **Edges** tab, `space-4` apart: **Edge softness** (Slider 0 to 100%, default **30%**, readout `30%`, snaps to 0 within ±3% with one SegmentTick; the photo's control and default; the ramp is 0.2% to 1.5% of the shorter side); **Edge size** (Slider −5 to +5, step 1, default 0, readout "+2" or "−3"; shrinks or grows the cut by 0.2% of the shorter side a step, up to ±1%; for halos and cut-off hair); **Steady edges** (Slider 0 to 100%, default **50%**; averages the cut over 0 to ±4 neighboring frames, less shimmer on the edge but a little lag when the person moves fast). One undo step on each release ("Cut-out edges"); double-tap the thumb resets that slider.
   - **Brush** tab: **Erase · Restore** (SegmentedChoice, default Erase), the five brush sizes **Thin · Fine · Medium · Thick · Heavy** (the Draw tiles, shares 0.015, 0.03, 0.06, 0.10, 0.16 of the picture's longer side, default Medium), and one hint: "Strokes apply to the whole clip. They work best when the camera stays still." (`cutout_video_brush_hint`). One finger paints on the stage (Erase hides, Restore brings the picture back); two fingers pinch-zoom (1× to 5×) and pan and never paint; a small **Fit** button (Quiet) shows at the stage's corner while zoomed. A stroke is committed on release as one step "Cut-out brush" and is a vector in the picture's coordinates, quantized, simplified and capped at **2,000 points per clip** (the P2-07 math; the toast `cutout_full` at the cap, reused).
3. **A quiet line at the bottom:** "Works on people. Runs on your phone; your video is never uploaded." (`cutout_video_privacy`, `caption` `text-secondary`).

- **Budget** (content 133 / 206 / 252 dp, P4-09's method; rows: View choice 56, Slider row 68, tile row 48, SegmentedChoice 56, hint 32, gaps 16): **Working:** status 24 + 16 + bar 16 + 16 + Cancel 48 = **120 dp**, hint below: fits at 133 with the hint scrolling at 640. **Ready, 13a:** View 56 + 16 + hint 32 + 16 + line 32 = **152**: scrolls at 640 (the View choice shows whole), fits from 800. **Ready, 13b Edges:** (tabs 48 + 16 +) View 56 + 16 + Softness 68 + 16 + Edge size 68 + 16 + Steady edges 68 + 16 + line 32 = **about 420**: scrolls on every phone. At 360 × 640 the first view is tabs and the View choice (120 of 133); at 800 and 915 (tabs pinned, content 142 / 188) View and Edge softness show whole (140 of 142), Edge size and Steady edges one swipe down. Accepted: refine is a pro tool; the one-tap result needs no scroll. At 200% font every row grows and the panel scrolls on every phone.

## Rules behind the layer
### How it is cut (preview and export agree)
Part of every draft's look, so fixed; also in `design/tokens.json` → `cutoutVideo`. Changing a rule after the closed test (about 25 Jan 2027) needs a schema bump.
| Rule | Value |
| --- | --- |
| What is found | **People** (the person segmentation model, MediaPipe Image Segmenter in video mode; verified at the start). Several people in a frame are all kept. Pets and objects are not found (D2) |
| Source time | The cut is made on the **source file's frames in their own time**, at most **30 frames a second** (a 60 fps clip gets a cut for every second frame, held); the preview and the export read the same cache entry for a source frame |
| The range cut | The clip's **trimmed source range** at the time of the tap. **At most 60 s** of source (`cutoutVideo.maxSourceSeconds`); a longer range explains `explain_cutout_video_long` and does nothing. Trimming outward later cuts the new frames automatically (the same status); trimming inward reuses the cache |
| Frames with no person | A gap shorter than **0.3 s** holds the last cut (no flicker from a missed frame); a longer gap removes everything in those frames (the person left the shot). A clip where fewer than **10%** of the cut frames contain a person is "nothing found" |
| Preview | The cut at the model's own size, smoothed up to the preview size; the editor never drops below 30 fps for it (it drops preview size first, as P4-07) |
| Export | **Refined at the output size**: the cut is upsampled with an edge-aware filter against the full-resolution frame (a guided upsample), so edges are as sharp as the picture at 1080p or 4K; the same settings (softness, edge size, steadiness, strokes) |
| What matches | Preview and export agree **everywhere except a band of 6 px around the edge** (at 1080 p; scaled with the output), where the export is sharper. Elsewhere pixels are within 3/255 |
| Edge softness | Alpha ramp across the edge: 0.2% (0%) to 1.5% (100%) of the shorter side, default 30% |
| Edge size | ±5 steps of 0.2% of the shorter side (erode or dilate the alpha) |
| Steady edges | Triangular average of the alpha over ±*r* frames, *r* = round(4 × value), default 2 |
| Strokes | Vectors in the picture's coordinates, applied to every frame of the clip after the cut and edge settings (Erase forces alpha 0 inside the stroke, Restore forces the cut off, i.e. alpha 1) |
| Limits | Source range ≤ 60 s; strokes ≤ 2,000 points per clip; one cut-out per clip |

### Working, cancelling, resuming
- **Start:** the tap commits **one undo step "Remove background"** (the setting exists at once, so Undo, Cancel and Export see it) and starts the work. The work is off the main thread and never blocks editing or playback of other clips.
- **Cancel, Undo, Delete of the clip:** stop the work at once (within 1 s) and, for Cancel and Undo, take the step back. The cache keeps the finished frames (the next try continues from there).
- **Export while working:** the Export button explains instead of opening the sheet: "Wait for the background removal to finish, or cancel it." (`explain_export_cutout_busy`), as the photo editor does for a busy cut-out (P2-08). While it works, the export is never silently missing the cut.
- **Cache lost** (the system cleared it, a draft restored on another phone): when the project opens or the clip comes into the timeline, the work starts again with the same status and the same Export rule; one toast "Redoing the cut-out for this clip." (`cutout_video_redo`).
- **Heat and battery:** the screen is kept on while working in the foreground; if the phone is hot (thermal status severe or worse) the work pauses and the status reads "Paused while the phone cools down." (`cutout_video_cool`), then continues by itself.
- **Space:** the cache is bounded (a 60 s clip at most about 40 MB); a full phone makes the P1-02 "Not enough space" toast with Free up space (`explain_cutout_video_space`), and the step is undone.

### Export
- The Export sheet (P1-12) gains one informational line when any clip has a cut-out: "Clips with a removed background take longer to export." (`export_cutout_note`, `label` `text-secondary`); the estimate shown in the facts line includes it (the engineer measures on the owner's phone; rule: at most 3 times the same export without cut-outs, Measured).
- The Exporting screen is unchanged: one determinate bar over the whole export (P1-12); no second phase is shown.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Tool in the bars | `cutout` icon and label; active look when the clip has a cut-out | `tool_remove_bg`, `tool_remove_bg_video_click` |
| Panel, working (13a text; 13b with bar and Cancel) | Status with percent, hint; the clip's chip shows the percent | `cutout_video_working_panel`, `cutout_video_working_hint` |
| Panel closed, working over 1 s | Stage banner with percent; chip on the clip | `cutout_video_working` |
| Preparing (the model is not on the phone, first use) | The photo's Preparing card text in the panel; a determinate bar when the download reports progress; Cancel | `cutout_preparing_title`, `cutout_preparing_body` (P2-07) |
| Ready | View choice (and in 13b the tabs); the cut on the checkerboard; the clip's cue icon | `cutout_video_view_*`, `cutout_ready` |
| View: In video | The composition, other layers and captions shown | `cutout_video_view_video` |
| A main clip with nothing behind it | The canvas background shows; one toast the first time per clip | `cutout_video_main_hint` |
| Nothing found (under 10% of frames have a person) | The cut-out step is undone; a card in the panel: "We couldn't find a person in this clip", "Background removal in video works on people. Photos can cut out pets and objects." | `cutout_video_none_title`, `cutout_video_none_body` |
| Can't remove right now (model unavailable, unknown error) | Card with Try again (Primary, the one blue action in a failure state) and the step undone | `cutout_failed_title`, `cutout_failed_body`, `cutout_retry` (P2-07) |
| Offline, first use | The P2-07 card; Try again | `cutout_offline_title`, `cutout_offline_body` |
| Not enough memory | "This clip is too big to cut out right now", "Close other apps, or trim the clip." Try again | `cutout_video_memory_title`, `cutout_video_memory_body` |
| Range over 60 s | Toast; nothing changes | `explain_cutout_video_long` |
| Phone full | Toast with Free up space; step undone | `explain_cutout_video_space` |
| Phone hot | The status line reads paused; resumes alone | `cutout_video_cool` |
| Cancelled | Step undone; panel closes; the chip goes | none |
| Export while working | Export explains; no sheet | `explain_export_cutout_busy` |
| Cache lost | Work restarts with a toast; chip shows the percent | `cutout_video_redo` |
| Brush cap reached (13b) | Toast | `cutout_full` (P2-07) |
| Remove cut-out | One step; the clip whole; panel closes | `edit_remove_cutout` (P2-07) |
| A clip with the file missing | Tool absent | none |
| A photo clip | Tool absent | none |
| An old draft | No cut-outs; opens unchanged | none |
| Long content (5 languages, 200% font) | Title-row buttons wrap; the choice stacks; tab names scroll; cards scroll inside the panel; the progress label wraps | none |
| First run | No coach mark; the first successful cut shows `cutout_video_main_hint` only on a main clip | none |

**Every failure card has a way out** (Try again, or the message says what to do) and none is a dead end; every failure undoes the step so the clip is never left half-cut.

## Interactions
- **Remove BG tap:** one step, work starts, panel opens. **Done:** closes; work continues. **Cancel:** stops and undoes. **Remove cut-out:** one step.
- **View choice:** immediate, no step. **Sliders:** drag shows the stage live at the preview resolution and applies on release (one step); keyboard and TalkBack steps land on the grid (softness and steady 10%, edge size 1); double-tap resets. **Brush:** one stroke, one step; two fingers zoom; Undo works as everywhere.
- **Stage taps** play or pause as everywhere (a tap in the Brush tab paints only when it is a drag; a tap is play or pause). **Keyboard (basic, P1):** Tab reaches the tool, tabs, controls; Enter activates; painting needs a pointer (the essential exception); Escape is back.
- **Haptics:** SegmentTick at the softness 0 snap; nothing else.
- **Undo steps:** "Remove background", "Cut-out edges", "Cut-out brush", "Remove cut-out".

## Motion
| What | Motion | Reduce motion |
| --- | --- | --- |
| Panel opens, closes | 200 ms slide | 120 ms fade (instant on Android) |
| Tab change | 200 ms cross-fade | 120 ms fade |
| Progress bar fill | Follows the real percent (no easing, no looping bar) | same |
| Checkerboard on and off | 120 ms cross-fade | same |
| The pill, banner, chip text, toasts | 120 ms fade | same |
| The clip itself | Content: plays with the video | still plays |

Nothing in the chrome loops (MOTION 2): no indeterminate bar; Preparing without progress shows words only.

## Copy
English source; drafts for id, es, pt and hi (the engineer machine-drafts the rest until P5; a native read before the closed test). Budgets are characters. Percent signs in resources are a single `%`. Reused unchanged: `tool_remove_bg`, `cutout_ready`, `cutout_preparing_*`, `cutout_failed_*`, `cutout_offline_*`, `cutout_retry`, `cutout_full`, `cutout_erase`, `cutout_restore`, `cutout_softness`, `cutout_fit`, `draw_size_*`, `edit_remove_cutout`, `panel_done`, `fx_reset`, `editor_updating_preview`, the P1-02 Free up space action.

| Key | English | Budget | id · es · pt · hi (draft) | Note |
| --- | --- | --- | --- | --- |
| `tool_remove_bg_video_click` | Remove the background from this clip | 45 | Hapus latar belakang klip ini · Quitar el fondo de este clip · Remover o fundo deste clipe · इस क्लिप का बैकग्राउंड हटाएं | Click label; 35 en |
| `cutout_video_title` | Remove background | 24 | as `cutout_title` | Panel title |
| `cutout_video_working` | Removing background %1$d% | 30 | Menghapus latar %1$d% · Quitando el fondo %1$d% · Removendo o fundo %1$d% · बैकग्राउंड हटा रहे हैं %1$d% | Stage banner and chip; polite live region |
| `cutout_video_working_panel` | Removing the background… %1$d% | 40 | Menghapus latar belakang… %1$d% · Quitando el fondo… %1$d% · Removendo o fundo… %1$d% · बैकग्राउंड हटा रहे हैं… %1$d% | Panel status; same as `cutout_working` plus the percent |
| `cutout_video_working_hint` | You can keep editing. Frames not done yet show the original. | 80 | Kamu bisa terus mengedit. Frame yang belum selesai menampilkan aslinya. · Puedes seguir editando. Los fotogramas pendientes muestran el original. · Você pode continuar editando. Os quadros pendentes mostram o original. · आप एडिट करते रह सकते हैं। जो फ़्रेम बाकी हैं वे अपने असली रूप में दिखेंगे। | Hint; 59 en, es 71 |
| `cutout_video_cancel` | Cancel | 12 | Batal · Cancelar · Cancelar · रद्द करें | Button; use the shipped Cancel word |
| `cutout_video_view` | View | 12 | Tampilan · Vista · Visualização · दृश्य | Choice label |
| `cutout_video_view_checkerboard` | Checkerboard | 14 | Papan catur · Tablero · Xadrez · चेकरबोर्ड | Segment; the transparency pattern. Use the word editors use |
| `cutout_video_view_video` | In video | 12 | Di video · En el vídeo · No vídeo · वीडियो में | Segment |
| `cutout_view_note` | Showing this clip only | 30 | Hanya menampilkan klip ini · Solo se muestra este clip · Mostrando só este clipe · सिर्फ़ यह क्लिप दिख रही है | Pill on the stage; 22 en |
| `cutout_video_edges` | Edges | 10 | Tepi · Bordes · Bordas · किनारे | Tab |
| `cutout_video_brush` | Brush | 10 | Kuas · Pincel · Pincel · ब्रश | Tab |
| `cutout_video_edge_size` | Edge size | 20 | Ukuran tepi · Tamaño del borde · Tamanho da borda · किनारे का आकार | Slider label: shrinks (minus) or grows (plus) the cut |
| `cutout_video_steady` | Steady edges | 20 | Tepi stabil · Bordes estables · Bordas estáveis · स्थिर किनारे | Slider label: less shimmer between frames |
| `cutout_video_brush_hint` | Strokes apply to the whole clip. They work best when the camera stays still. | 90 | Goresan berlaku untuk seluruh klip. Paling cocok jika kamera diam. · Los trazos afectan a todo el clip. Funcionan mejor con la cámara quieta. · Os traços valem para o clipe todo. Funcionam melhor com a câmera parada. · ब्रश पूरी क्लिप पर लागू होते हैं। कैमरा स्थिर हो तो सबसे अच्छा चलता है। | Hint; 76 en, es 70 |
| `cutout_video_people_only` | Works on people. | 30 | Berfungsi untuk orang. · Funciona con personas. · Funciona com pessoas. · लोगों पर काम करता है। | Quiet line |
| `cutout_video_privacy` | Works on people. Runs on your phone; your video is never uploaded. | 80 | Berfungsi untuk orang. Berjalan di ponsel; videomu tidak pernah diunggah. · Funciona con personas. Se ejecuta en tu teléfono; tu vídeo nunca se sube. · Funciona com pessoas. Roda no seu celular; seu vídeo nunca é enviado. · लोगों पर काम करता है। आपके फ़ोन पर चलता है; वीडियो कभी अपलोड नहीं होता। | The panel's bottom line; 66 en, es 78 |
| `cutout_video_main_hint` | The background is gone. Put a video behind it, or pick a color in Canvas. | 90 | Latar sudah hilang. Taruh video di belakangnya, atau pilih warna di Kanvas. · El fondo ya no está. Pon un vídeo detrás o elige un color en Lienzo. · O fundo sumiu. Coloque um vídeo atrás ou escolha uma cor em Tela. · बैकग्राउंड हट गया। पीछे वीडियो रखें या कैनवास में रंग चुनें। | Toast, first cut of a main clip; 71 en; use the shipped `Canvas` word per language |
| `cutout_video_none_title` | We couldn't find a person in this clip | 50 | Kami tidak menemukan orang di klip ini · No encontramos a ninguna persona en este clip · Não encontramos nenhuma pessoa neste clipe · इस क्लिप में कोई व्यक्ति नहीं मिला | Card; 38 en, id 42 |
| `cutout_video_none_body` | Background removal in video works on people. Photos can cut out pets and objects. | 90 | Penghapus latar di video hanya untuk orang. Foto bisa memotong hewan dan benda. · En vídeo solo funciona con personas. Las fotos pueden recortar mascotas y objetos. · No vídeo só funciona com pessoas. Fotos podem recortar pets e objetos. · वीडियो में यह सिर्फ़ लोगों पर काम करता है। फ़ोटो में पालतू जानवर और चीज़ें भी कट सकती हैं। | Card; 79 en |
| `cutout_video_memory_title` | This clip is too big to cut out right now | 50 | Klip ini terlalu besar untuk dipotong sekarang · Este clip es demasiado grande para recortarlo ahora · Este clipe é grande demais para recortar agora · यह क्लिप अभी कटने के लिए बहुत बड़ी है | Card; 41 en |
| `cutout_video_memory_body` | Close other apps, or trim the clip. | 60 | Tutup aplikasi lain, atau pangkas klipnya. · Cierra otras apps o recorta el clip. · Feche outros apps ou apare o clipe. · दूसरे ऐप बंद करें या क्लिप छोटी करें। | Card; 35 en |
| `explain_cutout_video_long` | Background removal works on up to %1$d seconds. Trim the clip first. | 80 | Penghapus latar maksimal %1$d detik. Pangkas klip dulu. · Quitar el fondo funciona hasta %1$d segundos. Recorta el clip antes. · Remover o fundo funciona até %1$d segundos. Apare o clipe antes. · बैकग्राउंड हटाना %1$d सेकंड तक चलता है। पहले क्लिप छोटी करें। | Toast; %1$d = 60; 66 en |
| `explain_cutout_video_space` | Not enough space to remove the background. Free up space and try again. | 80 | Ruang tidak cukup untuk menghapus latar. Kosongkan ruang lalu coba lagi. · No hay espacio para quitar el fondo. Libera espacio e inténtalo de nuevo. · Sem espaço para remover o fundo. Libere espaço e tente de novo. · बैकग्राउंड हटाने की जगह नहीं है। जगह खाली करके फिर कोशिश करें। | Toast with Free up space; 70 en |
| `explain_export_cutout_busy` | Wait for the background removal to finish, or cancel it. | 70 | Tunggu penghapusan latar selesai, atau batalkan. · Espera a que termine de quitarse el fondo, o cancélalo. · Espere a remoção do fundo terminar, ou cancele. · बैकग्राउंड हटने तक रुकें, या उसे रद्द करें। | Toast on Export; 56 en |
| `export_cutout_note` | Clips with a removed background take longer to export. | 70 | Klip dengan latar yang dihapus butuh waktu ekspor lebih lama. · Los clips sin fondo tardan más en exportarse. · Clipes sem fundo demoram mais para exportar. · जिन क्लिप का बैकग्राउंड हटा है उन्हें एक्सपोर्ट में ज़्यादा समय लगता है। | Export sheet line; 54 en |
| `cutout_video_redo` | Redoing the cut-out for this clip. | 50 | Mengulang pemotongan untuk klip ini. · Rehaciendo el recorte de este clip. · Refazendo o recorte deste clipe. · इस क्लिप का कट-आउट फिर से बना रहे हैं। | Toast after a cache loss; 34 en |
| `cutout_video_cool` | Paused while the phone cools down. | 50 | Dijeda sampai ponsel dingin. · En pausa mientras el teléfono se enfría. · Em pausa até o celular esfriar. · फ़ोन ठंडा होने तक रुका है। | Status; 34 en |
| `a11y_cutout_video_ready` | Background removed from the clip | 40 | Latar klip dihapus · Fondo del clip quitado · Fundo do clipe removido · क्लिप का बैकग्राउंड हट गया | Polite, when the work ends |
| `a11y_state_cutout` | Background removed | 24 | Latar dihapus · Sin fondo · Sem fundo · बैकग्राउंड हटा | Appended to the clip's spoken description, with "Cut-out 40 percent" while working (`a11y_state_cutout_working` "Removing background, %1$d percent") |
| `edit_remove_bg_video` | Remove background | 24 | as `edit_remove_bg` | Edit names after "Undo: " |
| `edit_cutout_edges` | Cut-out edges | 24 | Tepi potongan · Bordes del recorte · Bordas do recorte · कट-आउट के किनारे | |
| `edit_cutout_brush` | Cut-out brush | 24 | Kuas potongan · Pincel del recorte · Pincel do recorte · कट-आउट ब्रश | |

Translator notes:
- **Cut-out** is the person kept with the background gone. **Checkerboard** is the gray-and-dark square pattern that shows transparent areas. **Edges** are the border between the person and the removed background; **steady** means the edge does not shimmer from frame to frame.
- **Calm tone** in every failure: nothing is the user's fault; say what to do next.
- **Hindi:** the interface uses the system Devanagari face; no row has a fixed text height; seconds in full ("सेकंड").

## Accessibility
- **Focus and TalkBack order:** the panel's title takes focus; then Remove cut-out, Done, the status (a live region), Cancel (while working), the View choice, the tabs, the controls; closing returns focus to the Remove BG tool. A failure card takes focus when it appears and Try again is its action; Close stays reachable.
- **Progress:** the status is a polite live region announced at most every 25%; ready is announced once (`a11y_cutout_video_ready`). The chip is output only.
- **The clip's spoken description** gains `a11y_state_cutout` (or the working form) after the speed and reverse parts; never in the state description.
- **Single-pointer routes (WCAG 2.5.1, 2.5.7):** the one-tap cut, the View choice, the three sliders (keyboard and TalkBack steps), Cancel and Remove cut-out need no drag. **The brush is an essential drag with no alternative** (the exception, as P2-07); the sliders and the one-tap result are the whole accessible route, and the Brush tab says in its hint when it will help.
- **The covered region:** `coveredByModal` on the timeline and tool bar while the panel is open.
- **Touch targets:** every control 48 dp or more; slider rows 48; brush tiles as Draw; the chip is output only.
- **Contrast:** status `text` on `surface` 15.6:1; the hint `text-secondary` 7.1:1; slider and progress fill `primary` 7.3:1 on `surface`; the chip and banner `text` on `scrim` 7.6:1 or more; the white selected tab and segment with `on-primary` 18.9:1. **State is never color alone:** a word or a shape accompanies each.
- **200% font:** see States. **Reduce motion:** see Motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | The tap that starts a cut-out (the committed step "Remove background") | `editor: "video"`, `tool: "remove_bg"` |
| `bg_remove` | The work ends (success, failure or cancel) | `editor: "video"`, `success`, `duration_ms`; on failure `reason`: `cancelled`, `no_person`, `unavailable`, `offline`, `memory`, `storage`, `thermal`; `clip_s` (the source range length, whole seconds, no clip content) |
| `tool_use` | A slider release (13b) | `tool: "cutout_adjust"`, `value: "softness"`, `"edge_size"` or `"steady"` |
| `tool_use` | A brush stroke committed (13b) | `tool: "cutout_brush"` |
| `tool_use` | Remove cut-out | `tool: "clear_cutout"` (the P2-07 id) |

- No event for the View choice, a replayed preview, opening the panel or a Try again except the new `bg_remove`. `reason` is a new parameter on an existing event: for the PM to confirm. Analytics stay off until consent (P5-01).

## QA compares
Use the debug draft `cutout-video-check` (Requests 6) and a 360 × 640 and a 360 × 800 phone. Screenshots in `docs/ux/reviews/P4-13a/` and `P4-13b/`.
1. **The tool:** Remove BG in the main bar, in a main clip bar and an overlay bar (the PM's order), absent on a photo clip; its active look; Spanish at 200%.
2. **Working:** the panel with the bar and Cancel (13b) and the 13a text status; the stage banner with the panel closed; the chip with a percent; a playhead past the finished point shows the original.
3. **Ready:** the cut on the checkerboard with the pill; In video; a main clip with the hint toast; an overlay person over a base clip.
4. **Edges and Brush (13b)** at 640, 800 and 915; softness 0 / 30 / 100 on a hair edge; Edge size −5 and +5; Steady edges 0 and 100; a brush stroke zoomed to 5×.
5. **Each failure card:** nothing found, can't remove, offline, memory; the long-range toast; the full-phone toast; the export-busy toast; Hindi at 200%.
6. **Export:** the Export sheet's note; one export next to its preview.

**Measured: the Done when** (QA report; PM method in `docs/pm/acceptance-log.md`):
- **13a, a masked clip previews with the background removed.** On `cutout-video-check` (a person in front of a flat green wall, a moving person, two people, no person, 70 s, 60 fps, a person who leaves the shot): at five times per clip the editor preview on the checkerboard shows the wall **removed (at least 98% of wall pixels transparent) and the person kept (at least 98% of person pixels opaque)**; the preview holds 30 fps or more with a cut-out clip and one overlay on the owner's phone (release-type build); the time to cut a 10 s 1080p30 clip on that phone is recorded (not gating; the PM judges, as P4-10's numbers).
- **13b, progress and export parity.** The percent rises steadily to 100; Cancel stops within 1 s and takes the step back; a closed panel still shows the banner. **The export matches the preview:** at ten times per clip, the export frame and the preview frame (scaled to 1080 and with the same View) agree within 3/255 on 99% of pixels outside a 6 px edge band, and the person's mask has an overlap (intersection over union) of at least 0.97; the export's edge is no softer than the preview's. Export time with a 10 s cut-out clip is at most 3 times the same export without it (recorded on the owner's phone).
- **Edges:** Edge softness 0 → 100 widens the alpha ramp from about 0.2% to 1.5% of the shorter side (measured on a straight edge); Edge size ±5 moves the edge by 1% of the shorter side; Steady edges 0 → 100 lowers the frame-to-frame change of the alpha along the edge of the steady clip by at least 40%.
- **Gaps:** a missed frame of under 0.3 s keeps the picture; the person leaving the shot empties the frame after 0.3 s.
- **Rules:** a 70 s range refuses with the toast; a trim outward cuts only the new frames; a speed change, a reverse and a crop do not start new work; Undo at 30% cancels and leaves no cut; clearing the app cache and reopening redoes the cut with the toast and blocks Export until done.
- **Each edit is one undo step;** old drafts open; **leaks and memory:** LeakCanary: tap Remove BG, cancel, tap again, finish, adjust, Done, close; repeat 5 times; memory stays flat across five cuts.

## Model needs (for the PM and the engineer; I don't edit code)
| Need | Expected model |
| --- | --- |
| The setting | `MediaClip.cutOut: VideoCutOut? = null` with `softness` (0 to 1), `edgeSize` (-5 to 5), `steady` (0 to 1), `strokes: List<CutOutStroke>` (the P2-05 vectors with an erase or restore flag), and a `modelVersion` so a newer model rebuilds the cache. One `schemaVersion` bump with an identity migration (null); old-draft check. Applies to main clips and video overlays; photo clips never |
| The cache | Per source file and source frame in the app cache (like `ReversedCopies`): not in the project, not in `mediaCopyPaths()`; keyed by the file, `modelVersion` and frame time; a bounded size; resumable |
| Edits | `startClipCutOut`, `setClipCutOutEdges`, `addClipCutOutStroke`, `removeClipCutOut`; pure, one `commit` each; the Export busy rule reads a `cutOutReady` flag from the session |
| Declarative | `CutOutVideoSpec` constants (limits, ramps, defaults) in `:core:model` and a reference render per setting (rule 5) |
| Pipeline | The alpha multiplies with the key's alpha at the key step (D4) in both effect chains |

## Requests to the principal mobile engineer
1. **Start-of-ticket checks:** the current MediaPipe Tasks Image Segmenter in video mode (running mode, the person model and its size, license, whether it is bundled or downloaded, `segmentForVideo` timestamps), per-frame speed on the owner's phone and the thermal behavior. Report anything that changes the 60 s cap or the 30 fps cap. Verify the model's license is redistributable (no GPL/LGPL; Apache-2.0 expected).
2. **The `Segmenter` interface** (`background-removal` skill) gets a video entry (a stream of source frames in, masks out, progress as a Flow, cancel, resume from a frame); features call use cases only. iOS gets a marked stub that reports unsupported (never a silent no-op).
3. **Cache:** masks stored compactly (an 8-bit mono sequence or small PNG per frame), read by the preview and the export; the export refines against the full-resolution frame (guided upsample); the same settings code for both.
4. **The panel** from ToolPanel, Tabs, SegmentedChoice, Slider, ProgressBar; the chip and banner; the Export notice and busy rule; the cache-lost start; the thermal pause; keep the screen on while working.
5. **Bars:** Remove BG in the main bar, the main clip bar and the overlay bar (the PM confirms the consolidated order).
6. **Debug draft `cutout-video-check`** with the seven clips and a reference harness; frames saved to `docs/qa/references/P4-13-cutout-video/`.
7. **Strings** in the five files; **analytics:** `EditorTool` values `cutout_adjust`, `cutout_brush`, the `reason` and `clip_s` parameters on `bg_remove`; **no new icon.**

## Tokens and icons
- **No new color, size, radius or motion token.** The checkerboard reuses `canvas` and `surface-raised` (cells 12 dp, P2-07); the chip reuses `scrim`, `radius-sm`, `icon-small`, `caption`.
- **New content group** `cutoutVideo` in `design/tokens.json` and `design/system/tokens.json` (limits, ramps, defaults, gap hold, no-person threshold). Kotlin: `CutOutVideoCatalog` next to `OverlayCatalog`, never `MemixTheme`.
- **No new icon:** `cutout` (head over shoulders) is the tool and the chip.
- **Pointer edits made with this spec:** DESIGN_SYSTEM (a video background removal paragraph and the extended pipeline), the Timeline README (the cut-out chip).

## Questions for the owner
Each is decided in the spec as recommended, so the build can go on. Answer in short form ("1A, 2A").

**1. What does "refine" mean for video?**
- *Problem:* the photo brush paints one picture; a video has hundreds of frames and a moving person.
- *Considerations:* **A. Three sliders (Edge softness, Edge size, Steady edges) plus a brush whose strokes cover the whole clip** (recommended; applied): the sliders handle the common flaws on any clip, the brush rescues a steady-camera clip, one honest limit in one line. **B. Sliders only; the brush is Later:** a smaller 13b and less QA; a leftover chair behind a steady-camera subject can't be fixed. **C. A per-frame brush with keyframes:** the full pro answer; very large and a poor fit for a one-minute meme.
- *Reverse:* the Brush tab is a cut-list item; no migration (strokes just stay empty).

**2. How long a clip can be cut?**
- *Problem:* the work runs in real time on a mid phone: a 60 s clip may take a minute or two and warms the phone.
- *Considerations:* **A. 60 s of source and 30 fps** (recommended; applied; constants): covers meme clips; a longer range explains and the user trims. **B. 30 s:** faster and cooler, refuses more real clips. **C. No cap:** a 10-minute clip can run for ten minutes and fill the cache.
- *Reverse:* constants.

**3. Can the user keep editing while it works?**
- *Problem:* a modal wait is simple; a free editor is friendlier but needs the "export is blocked until done" rule.
- *Considerations:* **A. Yes: progress in the panel, the banner and the chip; Export explains until done** (recommended; applied). **B. A blocking dialog with a bar:** simplest, but it stops a user with other edits to make.
- *Reverse:* a flow switch.

**4. Where does the tool live?**
- *Problem:* the PM's consolidated bars hold no Remove BG; the main bar has it.
- *Considerations:* **A. Main bar plus the main-clip and overlay bars, after Green screen** (recommended; applied as a proposal to the PM): a person on an overlay needs the tool in the overlay's bar, and the two alpha tools sit together. **B. Main bar only:** an overlay can't be cut without a main-bar tap and the selection rule gets confusing.
- *Reverse:* a bar order edit.

**5. Export waits or refuses while work is unfinished?**
- *Problem:* a user may tap Export at 60%.
- *Considerations:* **A. Refuse with a toast** (recommended; applied): no surprise long export; mirrors the photo editor. **B. Wait and continue automatically:** friendlier, but a hidden long wait with the screen on.
- *Reverse:* an edit rule.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| MediaPipe Image Segmenter has an image, a video and a live-stream running mode; video mode takes frame timestamps | Guidance (docs; verify at the start) | [MediaPipe Image Segmenter, Android](https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android) |
| Dragging needs a single-pointer alternative; painting is an essential exception | Guidance | [WCAG 2.2 SC 2.5.7](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements) |
| Status messages need a polite live region | Guidance | [WCAG 2.2 SC 4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) |
| Temporal smoothing reduces edge shimmer in per-frame segmentation | Assumption (product knowledge; the Steady edges default is tuned by eye at QA) | none |
| Photo cut-out UX (checkerboard, Erase and Restore, softness 30%) | Evidence (this project's shipped spec) | `P2-07-background-removal.md` |
| The 60 s and 30 fps caps | Assumption (numbers to be confirmed by the start-of-ticket check on the owner's phone) | none |
