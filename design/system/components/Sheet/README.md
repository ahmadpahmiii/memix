# Sheet

A bottom sheet for choices that sit above the current screen: Create, Export, the editor's Meme sounds sheet, sound details, tool settings.

- **Provide:** a title in `title`, rows of settings, and at most one `primary` action at the bottom.
- **Layout:** `surface` with `radius-lg` top corners, `shadow-sheet`, a grabber, over `scrim`; padding `space-4`.
- **Export sheet:** resolution, frame rate, format, an estimate line in `timecode`, the watermark row with **Remove** (watches one rewarded ad), then **Export**.
- **Blocking variant:** for work that must finish or be cancelled (media import), and for a result the user must acknowledge. No grabber and no close button; scrim taps and drags do nothing; system back and Escape run the screen's cancel or continue action. Everything else uses the standard sheet.
- **Long content:** the sheet grows with its content until its top is `space-10` below the status bar; then the middle scrolls while the title and the bottom button stay in place.
- **Half-height opening** (`opensAtHalfHeight`, first used by the editor's Meme sounds sheet, P1-08): for a long list picked while the screen behind still matters.
  - It opens no taller than half the window, so the top of the screen behind stays visible above the scrim (in the editor, the top of the preview). A shorter content (an empty state) just takes its own height.
  - The list scrolls inside the half-height sheet, so every row is reachable with no extra step, also with TalkBack.
  - A drag up on the grabber or the title row grows it to the full long-content height (200 ms, at once with reduce motion); a drag down closes it as usual. A drag on the list scrolls the list.
  - The last visible row is cut by the sheet's edge, which shows there's more.
- **Focus:** opening moves focus to the sheet; its title is the pane title. Closing returns focus to the control that opened it, unless the sheet's action moves it somewhere more useful (the editor's sheet after adding a sound: the new sound's first tool).
  - Keyboard focus is trapped inside the sheet only while it's shown, not during its 200 ms exit. Otherwise a return requested as it starts to leave is cancelled when focus sat on one of its buttons (Cancel, Continue), and focus ends nowhere (P1-16 review).
- **Don't:** stack sheets; open a second one only after the first closes.
