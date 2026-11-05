# Make it a video meme · P2-10

> **Draft for build, 5 Nov 2026.** Ready check: `docs/pm/acceptance-log.md` (5 Nov, P2-10). Delegated decision applied (proposal 3B): the scene is baked into one picture; the top run of opaque text layers stays editable as captions.

Builds on `P2-01-photo-editor-shell.md` (the editor this button lives in), `P1-04-editor-and-preview.md` (where it arrives), `P1-06-clip-edits-and-audio.md` → Meme sounds sheet (what it opens), `P1-10-text.md` and `P2-03-photo-text-and-stickers.md` (the shared caption renderer) and `P2-08-photo-export.md` (the "busy" and "empty" rules). Board: `design/screens/PhotoEditor.dc.html` shows a "Make it a video meme" pill; this spec changes its shape and place (Layout).

## Job
"I made a good picture meme; now give it a sound": one tap turns the picture into a video meme with the same look, the text still editable, and the meme-sounds sheet open so the first sound is one more tap away (Sound first).

**Done when** (TICKETS): every layer keeps its position, size and style. **Clarified reading (PM, 5 Nov):** the first frame of the video export equals the photo render within 1% of the canvas width; text equal by the shared renderer. Layers other than the top text are merged into the picture (the baked scene), so they keep their look but are no longer separate layers.

**The one blue action:** none of its own. The button is Secondary (Layout), so the photo editor keeps Export as its one primary. In the video editor the sounds sheet's blue add buttons are the blue.

**Fast path, with tap counts:** Make it a video meme (1) → the video editor opens with the sounds sheet → a sound's add button (2). Export is two more taps.

## Entry and exit
- **Entry:** the **Make it a video meme** button under the photo editor's stage.
- **Exit:** the video editor opens as a new video project (Rules). The photo editor closes with its draft saved, so **Close in the video editor goes Home** (editors are separate; each draft is found on Drafts, the photo one with its photo badge). Nothing is lost and the photo stays as it was (D3).
- **A new video project each time.** Tapping the button again later from the same photo makes another video draft; the Drafts screen shows both. No link is kept between the two drafts.

## Layout

### The button (photo editor)
- **Place (board difference).** The board draws a small pill inside the stage. That covers the picture or crowds it, so the button gets its own **56 dp row between the stage and what is under it** (the format bar, or the tool bar), centered, `canvas` behind it, and the stage refits above it. Price: the stage gives up 56 dp (at 360 × 640 the stage goes from 440 to about 384 dp, a 9:16 frame from 229 × 408 to about 216 × 384; QA compares 8). The row is shown in the editor's default and layer-selected states and **collapses with the stage refit** whenever a panel, the Layers panel, a keyboard or a tool screen is open, as the format bar does.
- **Look.** A **Secondary Button** (`surface-raised`, `text`, `radius-md`, `touch-target` tall), hugging its content, with the `video` icon (20, `text`, not blue) and the label **"Make it a video meme"** in `label`. Not the board's blue-iconed pill: blue is for Export, the one main action here, and a blue icon on a second control would make two (P1-12's reasoning for Share). Pressed: `surface-raised` to `hairline`.
- At 200% font the label wraps to two lines and the button grows; the row grows with it (the stage refits). Never cut.

### When it can't run
It explains with a toast on tap, as every blocked control does, and the button keeps its look:
- No visible layer: "Add a photo, text or sticker first." (the same words as Export's, `explain_photo_export_empty`).
- A cut-out is still being made: "Wait a moment. Your cut-out is still being made." (`explain_photo_export_busy`).

### While it works
The tap commits any text being edited, then the button's label becomes **"Making video…"** and the editor goes inert (nothing moves, no bar: MOTION 2). Under a second at 1080 on a normal phone; the target is 2 s on the owner's phone (Assumption, QA measures). Back is ignored while it works and a second tap does nothing.

### Arriving in the video editor
- The video editor opens by P1-04's rule for every arrival: playhead 0:00, paused, nothing selected, default zoom, an empty undo history. The preview shows the first frame: the baked picture with the captions on it.
- The canvas is the photo's canvas (ratio, size, background). The timeline has one main clip (the baked picture, 3.0 s, Fill) and, on the text track (orange), one text item per lifted caption spanning the clip. The picture is a normal image clip: trim, speed and the rest work.
- **The sounds sheet opens once the editor has finished arriving** (after the 320 ms push and the first frame; with reduce motion, after the 120 ms fade), exactly as if the user had tapped Meme sounds. A sound's add button lands it at the playhead (0:00), per P1-08. Close, scrim, drag down or back leaves the editor as it is.
- **The one-time note** (D2): the first time ever, once the sounds sheet has closed (a sound added or closed without one), a persistent toast with a **Got it** button says "Your layers are now one picture, except text on top, which stays editable." It stays until dismissed, or 8 seconds, or the user's accessibility time to take action (P1-14's rule), whichever is longer. It is never a blocker and is shown once per install. Reason for the timing: the sheet covers the bottom of the screen on arrival, and a note under a scrim can't be read (Assumption; QA notes whether testers find the text still editable).

## Rules

### What happens to the scene
1. **Bake.** All visible layers **below the lifted text** are drawn with the one scene function (`drawPhotoScene`, the same as export), offscreen at the canvas size scaled so the shorter side is 1080 px (the 1x size of P2-08; a 2160-wide canvas is not used here, to keep the video editor light), software decoding. Formats, panels, bubbles, stickers, images, drawings, censors, filters, crops, cut-outs and the background are all in the picture. **No watermark** is drawn (the watermark belongs to the video export, P1-13). Hidden layers are skipped.
2. **Lift.** The **contiguous top run of visible, fully opaque text layers** becomes video text items, each with the same text, style, transform and order, from 0 to the clip's length. Text with any opacity below 100%, or under any other layer (including a sticker or drawing above it), is baked instead, so what the user sees on the first frame is exactly the first frame of the photo. Bubbles are always baked.
3. **The picture** is saved as a new media file inside the new project's folder (never changing a file in place); the photo draft is untouched.
4. **Equality.** The first frame of the video export equals the photo render within 1% of the canvas width everywhere; text is equal because both use the shared renderer (P1-10, P2-03, P2-11). Shadows and boxes carry over (they are text style).
5. **Not carried:** the photo's undo history, layer locks, and anything the video editor has no equivalent for. Image and sticker layers becoming timeline tracks is a later change (flagged to the Phase 4 gate, PM item 9).

### Why this reading
The video editor has no filter, cut-out, backdrop or drawing equivalent in Phase 2, so keeping those as layers would change the picture. Baking keeps the look exactly; lifting the text keeps the part users change most often (timing and wording). The note and the busy rule keep it from being a surprise.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Photo editor, default | The button under the stage | `make_video_title` |
| No visible layer | Toast on tap | `explain_photo_export_empty` |
| Cut-out being made | Toast on tap | `explain_photo_export_busy` |
| A panel, keyboard or tool screen open | The row is collapsed | none |
| Working | Label "Making video…"; inert | `make_video_working` |
| Arrived, sounds sheet | The video editor with the Meme sounds sheet | P1-08 |
| After the sheet, first time | The persistent toast | `make_video_note`, `make_video_note_ok` |
| A picture's file is missing | The missing image is left out (as in export); no extra message | none |
| Failed: no space | Back in the photo editor, label restored; toast | `make_video_fail_space` |
| Failed: other (memory, decode) | Back in the photo editor; toast | `make_video_fail` |
| Interrupted (the app is backgrounded or killed) | Nothing was created; the photo draft is unchanged | none |
| Long content (5 languages, 200%) | The label wraps; the row grows; the stage refits | none |
| Offline | No difference | none |

A failure leaves no half-made video draft: the new project and its file are created together or not at all.

## Interactions
- **Tap:** as above. **Back during work:** ignored. **Rotation:** none (portrait). **Home during work:** the work finishes if it can; if the process is killed nothing is left.
- **In the video editor:** the captions behave like any text item (select, edit, restyle, move, trim); their style is the same as in the photo editor, so nothing jumps when edited (P2-03's styles are the video's).

## Motion
The push into the video editor is 320 ms (P0-05 forward push); the sounds sheet rises 200 ms after it. The row collapses with the stage refit (200 ms, instant with reduce motion). The toast fades 120 ms. Reduce motion: the push becomes a 120 ms fade.

## Copy
English source; machine drafts until P5. Budgets in characters.

| Key | English | Budget | Note |
| --- | --- | --- | --- |
| `make_video_title` | Make it a video meme | 26 | The button label (wraps). Sentence case, verb first |
| `make_video_working` | Making video… | 20 | Label while working |
| `make_video_note` | Your layers are now one picture, except text on top, which stays editable. | 90 | Persistent toast, once |
| `make_video_note_ok` | Got it | 12 | The toast's button |
| `make_video_fail` | Couldn't make the video meme. Try again. | 50 | Toast |
| `make_video_fail_space` | Couldn't make the video meme. Free up space and try again. | 70 | Toast |
| `explain_photo_export_empty`, `explain_photo_export_busy` | (as P2-08) | | Reused |

Translator notes: "Video meme" is the phrase the app uses for a meme made as a video (as in "Video meme" on the Create sheet). "Layers" are the things stacked on the picture (text, stickers, images); "one picture" means they are merged. "Text on top" means text above everything else.

## Accessibility
- **The button:** a button with its visible label as its name, "Make it a video meme". The toast on tap is read politely when it explains.
- **While working:** "Making video" is announced politely; focus stays on the button.
- **Arrival:** focus lands in the video editor as P1-04 says for every arrival; the sounds sheet then takes focus as it opens (P1-08's rule; where TalkBack lands is a phone check). The one-time note is a polite live region with a **Got it** button.
- **Touch targets:** the button 48 dp or more; the toast button 48.
- **Contrast** (reused): `text` on `surface-raised` 12.4:1; the toast text per the Toast component.
- **200% font:** see Layout.
- **Reduce motion:** see Motion.

## Analytics
| Event | When | Parameters |
| --- | --- | --- |
| `tool_use` | The new video project exists and the editor opens | `tool: "make_video"`, `editor: "photo"` |

No `project_create` (PM: the video project comes from a photo project, not the gallery). The sounds sheet's `sound_add` and the rest fire as in P1-08.

## QA compares
1. **The photo editor** at 360 × 640 and 360 × 800: the row with the button under the stage, the stage refitted; then with a panel open (row collapsed).
2. **A rich scene** (a format, an image, a sticker, a drawing, a censor and two texts, one covered): the photo render (the render hook), then the video editor's first frame: equal within 1% of the canvas width; the two texts: the top one is on the text track, the covered one is in the picture.
3. **Arrival:** the push, the sounds sheet opening after it, adding a sound lands at 0:00, then the one-time note after the sheet closes; a second make-video shows no note.
4. **The video export** of that draft: its first frame equals the photo render apart from the watermark.
5. **Failures:** a photo draft with an empty canvas, a cut-out in progress, and the storage-full toast.
6. **Hindi at 200% font** at 360 × 640: the button wrapped, the stage refitted, nothing cut.
7. **Undo in the video editor** after arriving: nothing to undo; after editing a caption, one step.
8. **Reopen both drafts** from Drafts: the photo draft as it was, the video draft with its captions and sound.
9. **Memory:** LeakCanary after five conversions.

**Measured:** the first-frame difference (ImageMagick `compare -metric RMSE` with a 1%-of-width tolerance on edges); conversion time on the owner's phone (target 2 s); the baked picture's file size and dimensions (shorter side 1080).

## Requests to the principal mobile engineer
1. **The button and row** in the photo editor shell (P2-01): Secondary Button with the `video` icon (already in `MemixIcons`), the 56 dp row, collapsing with the stage refit; the empty and busy toasts reuse P2-08's keys.
2. **A use case** `MakeVideoMeme(photoProject)` in `:core:domain` doing the PM's steps (new project, bake via `drawPhotoScene`, lift, `PHOTO_CLIP_DURATION_US`, Fill), all-or-nothing on failure; the Android bake in `:engine:photo`; no Android types in the use case.
3. **Navigation:** replace the photo editor with the video editor (Close goes Home); open the Meme sounds sheet after the arrival finishes; no `project_create`; `tool_use` `make_video` with `editor: photo`.
4. **The one-time note:** a persisted flag in multiplatform-settings, a persistent Toast with a button, shown after the sheet's first close.
5. **Strings** in five languages; tokens only.
6. **Hand checks** per the ready check (8): the rich scene, the comparison, Undo in the video editor, reopening both drafts, LeakCanary.

## Proposals and questions for the PM
1. **The stage cost.** The button's own row takes 56 dp of the photo stage. If that proves too costly on small phones at QA, the fallback is to move the button into the Export sheet's top and the Exported screen (a second chance after saving). *Recommendation applied: the row now; QA looks at 360 × 640.*
2. **Editors replace each other** (the photo editor closes; Close goes Home). The alternative keeps the photo editor underneath so Back returns to it, at the cost of two editors in memory. *Recommendation applied: replace; both drafts are one tap away on Drafts.*
3. **The note's timing** (after the sounds sheet closes, not on arrival). *Recommendation applied.*

**Delegated owner decisions (applied, for review after Phase 2):**
- **The button is Secondary and gets its own row**, not the board's blue-iconed pill inside the stage: one blue action per screen, and the picture stays uncovered.
- **The photo editor is replaced by the video editor** rather than kept underneath.

## Evidence
| Claim | Strength | Source |
| --- | --- | --- |
| The ticket's clarified reading, the bake-and-lift rule and the engineer steps | PM ready check | `docs/pm/acceptance-log.md` (5 Nov, P2-10) |
| Opening the sounds sheet on arrival keeps sound within 2 taps of the timeline | Product principle | `docs/PRD.md` (Sound first) |
| A control that sits over the picture hides content; the board's pill in the stage does | Judgement from the board and P2-01's stage sizes | `design/screens/PhotoEditor.dc.html` |
| A note under a modal scrim can't be read | Assumption (QA notes whether testers find the editable text) | none |
