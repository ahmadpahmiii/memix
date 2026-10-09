# Starter sounds (staged for P1-08)

These are the 10 CC0 meme sounds the owner approved on 8 Oct 2026 (`docs/content/P1-08-starter-sound-candidates.md`, `docs/pm/decision-log.md`). They are staged here for **P1-08**, and that ticket decides where the bundled resources live in the app (proposal: `core/data/src/commonMain/composeResources/files/starter-sounds/`). Copy `<slug>.m4a` and `manifest.json` from here into that location; the `licenses/` folder stays in the repo and is not bundled. The app never calls Freesound.

| File | What |
| --- | --- |
| `<slug>.m4a` | 10 sounds, AAC-LC 128 kbps, 48 kHz, 7–87 KB each, about 450 KB total |
| `manifest.json` | One entry per sound: `id`, `file`, `title` (en/id/es/pt/hi), `durationMs`, `author`, license fields, `licenseProof`, both SHA-256s, `edits`, measured loudness |
| `licenses/<slug>.md` | Text snapshot of each Freesound page (URL, title, author, license line as shown, file details, description, UTC read time, HTML hash). The owner chose this over a web.archive.org capture. |

## Rules

- **`id` is permanent.** It is `uuid5(NAMESPACE_URL, "https://freesound.org/s/<freesound id>/")` and becomes the `sounds.id` row when the catalog loads these sounds (P3-13), so projects that use a starter sound keep working.
- **Source files are the pages' public HQ previews** (MP3 128 kbps, `fetchedFrom: "freesound-hq-preview"`) because originals need a Freesound login. That means one lossy generation before the AAC encode, which is fine for short stings. If someone re-encodes later from an original (with the owner's login), keep the `id` and update `sha256Source`, `sha256Output` and `fetchedFrom`. Files on R2 or Storage get a new key.
- **Reproduce** from the preview URLs in `manifest.json` with `scripts/audio/normalize.sh <preview.mp3> <slug>.m4a [start] [duration] [fade_in] [fade_out]`. Only two sounds need extra args: `deep-boom` is `0 3 0 0.5` and `awkward-crickets` is `129.75 3 0.2 0.5`. All others take no extra args. Check the result with `scripts/audio/measure.sh`.
- **Loudness:** each sound is normalized to −16 LUFS with no limiter, and the gain is capped so true peak stays at or below −1 dBTP. Three sounds end up quieter because of that cap: `sitcom-laugh` and `whoosh` at −18.5 LUFS, and `drum-roll` at −23.7 LUFS (its rimshot limits the gain).
- **Titles** in id, es, pt and hi are machine drafts until P5.
- **No credit is required** (CC0). `courtesyCredit` is for the Credits screen (P5-07). qubodup's page asks for credit with the URL, and that request is honoured.
- **Still open:** nobody has listened to these files yet (checklist column 4). P1-08's hand check covers it.
