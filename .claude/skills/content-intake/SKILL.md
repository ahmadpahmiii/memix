---
name: content-intake
description: Add meme sounds, templates or sticker packs to the Memix catalog with complete licensing — find, verify license, normalize audio (−16 LUFS, AAC 128 kbps), waveform, upload to R2, insert the Supabase row, monthly trending, takedowns. Use when the user wants to add catalog content or build/run the intake script (P3-12).
---

# Content intake

Only content with a recorded license enters the catalog. **Sound intake accepts three sources only** (owner, 11 Nov 2026; PRD → Content and licensing policy): CC0 or public domain, CC-BY with an in-app credit, and our own or recreated sounds. A license that bars standalone redistribution (Pixabay's content license, "free to use in videos", NC, SA, ND) does not qualify, because the app redistributes each sound as a file. Royalty-free-for-apps licenses and packs licensed in writing need the owner's separate approval before a sound of that kind is loaded; the script refuses them. Rejected: clips ripped from songs, films, TV, games; YouTube downloads; unknown licenses. When unsure, reject and say why. (Not legal advice; the PRD recommends an IP-lawyer review before launch.)

## Per batch of about 50 sounds (`scripts/media/intake_sounds.py`, P3-12a; the owner runs the live steps, P3-12b)

Run `python3 scripts/media/intake_sounds.py <command> content/intake/<batch>`. The commands, rules and file formats are in the script's docstring and `docs/TECHNICAL_DESIGN.md` → CMS workflow. Cloud sessions build, dry-run and fix; **only the owner's Mac holds `FREESOUND_API_KEY` and `SUPABASE_SECRET_KEY`**.

1. `init <batch>`: creates `batch.json`, `source/`, `proofs/`.
2. Source: `fetch <batch> <freesound urls or ids> --category sound_<x>` (Freesound API; refuses anything not CC0 or CC-BY; saves the HQ preview and a text license snapshot as `license_proof`), or add rows by hand for own recordings and recreations (`license_type: own`, `source_url: own:<where>`). Fill the translated titles (machine drafts are fine until P5-06; Devanagari titles are NFC-normalized by the script) and check category, regions and tags.
3. `prepare <batch> --dry-run`, then `prepare <batch>`: the license gate (refuses a missing `source_url`, `license_type`, `license_proof`, `title.en`, `credit` for CC-BY, an unknown category or a license outside CC0, CC-BY, own), trim silence → **−16 LUFS** with true peak **−1 dBTP** → **AAC 128 kbps `.m4a`** (via `scripts/audio/normalize.sh`) → 64-value waveform JSON → `duration_ms` → `review.md`, the table for the owner (title, source URL, license, proof, credit, length, loudness).
4. The owner opens each source and proof, listens to `out/`, then `approve <batch> --all` (or `--ids`, `--reject`). Editing a row afterwards voids its approval.
5. `load <batch> --dry-run`, then `load <batch>`: uploads the `.m4a` then the waveform with `upload_media.py` (`sounds/<yyyy>/<mm>/<id>.m4a`, `waveforms/<yyyy>/<mm>/<id>.json`; public Supabase Storage bucket `media` until P6-07 moves it to R2 under the same keys; keys are immutable; `Cache-Control: public, max-age=31536000`) and writes `load.sql`. Apply it in the SQL editor of project `drnhpnixnewqjfmrchrr` (or copy it to a migration). It inserts the `sounds` rows (`on conflict do nothing`).
6. Listen to the batch in the app after the next catalog refresh. Commit `batch.json`, `proofs/`, `review.md` and `load.sql` (audio in `source/` and `out/` is gitignored).

A changed file for a loaded sound gets `"revision": 1` on its row (new id, new key). The 10 starter sounds are already live; `intake_sounds.py starters <dir>` makes a waveform-only batch for them (waveform files plus `update … set waveform_path` SQL), so no row keeps a null waveform. Batches must live inside the repo (`content/intake/<name>`); `license_proof` is an https URL or a file under `content/`.

Templates (`templates/`, `previews/`) and sticker packs (`stickers/`) follow the same upload and license steps by hand until a later ticket extends the script (it handles sounds only), with their own folders and a JSON `preset` for templates (slots, timings, sound cues, effects, text styles).

## Monthly trending (1st of each month)

`trending` rows per launch region and `global`: 10–20 sounds and 5–10 templates each, ranked.

## Takedowns

Report or email → set `is_active = false` → record the outcome on the `reports` row → reply within 72 h.

Never commit R2 or Supabase write keys, or the Freesound key; the scripts read them from `.env` (`SUPABASE_URL`, `SUPABASE_SECRET_KEY`, `FREESOUND_API_KEY`) and never print them. The keys are on the owner's Mac only: cloud sessions build and dry-run, the owner runs the live fetch and upload.
