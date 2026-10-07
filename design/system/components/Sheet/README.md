# Sheet

A bottom sheet for choices that sit above the current screen: Create, Export, sound details, tool settings.

- **Provide:** a title in `title`, rows of settings, and at most one `primary` action at the bottom.
- **Layout:** `surface` with `radius-lg` top corners, `shadow-sheet`, a grabber, over `scrim`; padding `space-4`.
- **Export sheet:** resolution, frame rate, format, an estimate line in `timecode`, the watermark row with **Remove** (watches one rewarded ad), then **Export**.
- **Don't:** stack sheets; open a second one only after the first closes.
