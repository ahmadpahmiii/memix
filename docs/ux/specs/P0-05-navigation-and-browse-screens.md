# Bottom navigation and browse screens · P0-05

## Job
Let people move between Home, Templates, Sounds and Drafts and open Create from anywhere, with back behaving the way Android and iOS users expect. Content arrives in later phases, so every screen here tells the truth about what exists now: nothing is shown that isn't real (antislop R-26, R-38).

The one blue action: the Create block in the bottom navigation on every browse screen; on Home, also the Video meme entry card (the screen's main action).

## Entry and exit
- App start lands on **Home**. Tabs switch without animation beyond the 120 ms color change; each tab keeps its own scroll position.
- **Create** (nav block) opens the Create sheet over the current tab; closing it returns to that tab unchanged.
- **System back:** on a tab other than Home → Home; on Home → leaves the app; with the sheet open → closes the sheet; on an editor placeholder → the screen it came from.
- **Editor placeholders** (Video editor, Photo editor) are full screens without bottom navigation, with a close button top-left. They exist so navigation and back can be checked; the editors arrive in P1 and P2.

## Layout

### Home · board `design/screens/Main.dc.html`
- Top row: "Memix" wordmark (Anton `display`, 28/30), region on the right.
- Hero: "MAKE A MEME" in `display-xl`.
- Entry cards: Video meme (`primary`) and Photo meme (`surface-raised`), two columns, `space-3` gap. Both open the matching editor placeholder.
- **Differences from the board:**
  - The region is a **label, not a pill button**: `globe` icon + region name in `label`, `text-secondary`, no fill. Changing region comes with P3-11; until then a pill that looks tappable would be a dead control.
  - **Trending templates and Trending sounds are left out** until the catalog is live (P3-01, P4-05). Showing the board's sample items would present invented content as real.

### Templates · board `design/screens/Templates.dc.html`
- Title "Templates" (`title-l`), then the empty state. The Video/Photo switch and category chips arrive with the template library (P4-05); with no templates to filter they would be controls that change nothing.

### Sounds · board `design/screens/Sounds.dc.html`
- Title "Meme sounds" (`title-l`), then the empty state. Search, tabs and chips arrive with the library (P3-02), for the same reason.

### Drafts
- Title "Your drafts" (`title-l`), then the empty state. Drafts can't be saved until P1-01, so the empty state is the real one.

### Create sheet · board `design/screens/Create.dc.html`
- Sheet title "CREATE" (`display`), close button, Video meme row (`primary`), Photo meme row (`surface-raised`, `primary` icon), "Start from a template" row (`surface-raised`) that switches to the Templates tab and closes the sheet.
- **Difference from the board:** the line "Memix asks for gallery access the first time you pick media" is dropped. The Android photo picker (P1-02) needs no permission, so the line would be false.

### Bottom navigation · component `BottomNav`
Home, Templates, Create, Sounds, Drafts. Current item `text`; others `text-secondary`; Create is the `primary` block. Height `nav-height` plus the system navigation inset; content scrolls above it, never under it.

## States
| Screen | State | What shows | Copy |
| --- | --- | --- | --- |
| Home | Default | Wordmark, region, hero, two entry cards | see Copy |
| Home | Region unknown (phone has no region) | Region label reads "Global" | "Global" |
| Templates | Empty (P0) | Headline + body, centered in the space above the nav | "NO TEMPLATES YET" / "The template library comes in a later build." |
| Sounds | Empty (P0) | Same layout | "NO SOUNDS YET" / "The meme sound library comes in a later build." |
| Drafts | Empty | Headline + body + secondary button "Make a meme" that opens the Create sheet | "NO DRAFTS YET" / "Go make something unhinged." |
| Editor placeholder | Default | Close button, title, one line | "Video editor" / "The video editor arrives in the next phase."; "Photo editor" / "The photo editor arrives in phase 2." |
| Any | Long content | Hindi and Portuguese strings wrap; headlines may take two lines; nothing truncates | |

Loading and error states don't apply in P0: no screen fetches data yet. They are specced with the tickets that bring data (P1-14 Drafts, P3-02 Sounds, P4-05 Templates).

Empty-state headlines use `display-xl` (Anton, uppercase via the style, not typed in caps; the token table assigns `display-xl` to empty-state headlines), body in `body` `text-secondary`. Each empty state sits in the visual center of the space between the title and the nav; it's the screen's focal point.

## Interactions
- Tabs, Create block, entry cards, sheet rows and the close buttons respond on press with the token pressed colors (no movement).
- The Create sheet closes on: close button, scrim tap, drag down, system back.
- No haptics in P0 (the haptic tick is for sounds landing on the timeline).

## Motion
Screen push to an editor placeholder: 320 ms slide; tab switch: no slide, 120 ms color change on the nav item; sheet: 200 ms slide over a fading scrim. Reduce motion: 120 ms fades.

## Copy (English source, budgets for translators)
| String | Text | Budget |
| --- | --- | --- |
| nav_home | Home | 10 |
| nav_templates | Templates | 10 |
| nav_create (content description) | Create | 20 |
| nav_sounds | Sounds | 10 |
| nav_drafts | Drafts | 10 |
| home_hero | Make a meme | 16 (displayed uppercase) |
| entry_video_title | Video meme | 12 (uppercase) |
| entry_video_body | Clips, sounds, effects | 28 |
| entry_photo_title | Photo meme | 12 (uppercase) |
| entry_photo_body | Captions, panels, cut-outs | 28 |
| region_global | Global | 16 |
| templates_title | Templates | 20 |
| templates_empty_title | No templates yet | 22 (uppercase) |
| templates_empty_body | The template library comes in a later build. | 60 |
| sounds_title | Meme sounds | 20 |
| sounds_empty_title | No sounds yet | 22 (uppercase) |
| sounds_empty_body | The meme sound library comes in a later build. | 60 |
| drafts_title | Your drafts | 20 |
| drafts_empty_title | No drafts yet | 22 (uppercase) |
| drafts_empty_body | Go make something unhinged. | 40 |
| drafts_empty_action | Make a meme | 18 |
| create_title | Create | 12 (uppercase) |
| create_video_body | Pick clips from your gallery | 34 |
| create_photo_body | Pick a photo from your gallery | 34 |
| create_template | Start from a template | 28 |
| close (content description) | Close | 16 |
| editor_video_title | Video editor | 20 |
| editor_video_body | The video editor arrives in the next phase. | 60 |
| editor_photo_title | Photo editor | 20 |
| editor_photo_body | The photo editor arrives in phase 2. | 60 |

Translator notes: "unhinged" is playful, not insulting; pick the local meme-speak equivalent. "Memix" never translates. Uppercase comes from the text style, so translations are written in sentence case.

## Accessibility
- Bottom nav items are tabs with selected state announced; Create announces "Create, button".
- Entry cards and sheet rows are single buttons whose label reads title then body.
- Focus order follows reading order; the sheet traps focus while open and returns it to the Create block when closed.
- 200% font scale: the hero and empty-state headlines wrap; the nav labels shrink to fit before wrapping; nothing is clipped.

## Analytics
None in P0 (`project_create` fires when a project actually starts, in P1).

## QA compares
1. Home, default and with the phone region removed ("Global").
2. Each tab's empty state.
3. Create sheet open over Sounds, then closed with system back (still on Sounds).
4. Video meme card → editor placeholder → back to Home.
5. Drafts "Make a meme" → Create sheet.
6. Home in Hindi at 200% font scale.
