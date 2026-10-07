# Regression smoke list

Run at the end of every phase (and before every release). Add a step for every fixed bug. Steps that need features not built yet are skipped until their phase.

1. Cold start reaches Home; no crash in logcat.
2. Bottom nav: Home, Templates, Create, Sounds, Drafts all open; back behaves.
3. Video meme: pick 2 clips → timeline shows both → play/pause/seek work.
4. Add a meme sound at the playhead → it plays in sync on preview.
5. Add top/bottom caption text → visible in preview.
6. Undo/redo 5 steps; kill the app; reopen → draft restored identical.
7. Export 1080p30 → file in the Memix album; ffprobe duration/resolution/fps correct; watermark top-left.
8. Photo meme: classic caption → save PNG.
9. Remove background on a photo → cut-out shown; Apply keeps it.
10. Airplane mode: app opens, cached sounds play, catalog shows cached data with a clear offline message.
11. Switch app language to id, es, pt, hi: no untranslated or clipped strings on Home and in the export sheet.
