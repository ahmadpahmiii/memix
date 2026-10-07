# Sheet

A bottom sheet for choices that sit above the current screen: Create, Export, sound details, tool settings.

- **Provide:** a title in `title`, rows of settings, and at most one `primary` action at the bottom.
- **Layout:** `surface` with `radius-lg` top corners, `shadow-sheet`, a grabber, over `scrim`; padding `space-4`.
- **Export sheet:** resolution, frame rate, format, an estimate line in `timecode`, the watermark row with **Remove** (watches one rewarded ad), then **Export**.
- **Blocking variant:** for work that must finish or be cancelled (media import), and for a result the user must acknowledge. No grabber and no close button; scrim taps and drags do nothing; system back and Escape run the screen's cancel or continue action. Everything else uses the standard sheet.
- **Long content:** the sheet grows with its content until its top is `space-10` below the status bar; then the middle scrolls while the title and the bottom button stay in place.
- **Don't:** stack sheets; open a second one only after the first closes.
