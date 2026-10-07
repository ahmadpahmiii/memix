---
name: content-intake
description: Add meme sounds, templates or sticker packs to the Memix catalog with complete licensing — find, verify license, normalize audio (−16 LUFS, AAC 128 kbps), waveform, upload to R2, insert the Supabase row, monthly trending, takedowns. Use when the user wants to add catalog content or build/run the intake script (P3-12).
---

# Content intake

Only content with a recorded license enters the catalog. Accepted: CC0/public domain, CC-BY (with in-app credit), royalty-free licenses that allow in-app distribution, own recordings/recreations, packs licensed in writing. Rejected: clips ripped from songs, films, TV, games; YouTube downloads; unknown licenses. When unsure, reject and say why. (Not legal advice; the PRD recommends an IP-lawyer review before launch.)

## Per sound

1. Source: Freesound (CC0 filter), Pixabay, or record a recreation. Save the license page URL and a screenshot as `license_proof`.
2. Run the intake script (desktop; ffmpeg is fine here because it never ships in the app): trim silence → loudness-normalize to **−16 LUFS**, true peak **−1 dBTP** → encode **AAC 128 kbps `.m4a`** → write a waveform JSON (~64 buckets) → read `duration_ms`.
3. Upload to R2: `sounds/<yyyy>/<mm>/<uuid>.m4a` (+ `waveforms/…json`). Keys are immutable; a changed file gets a new key.
4. Insert the `sounds` row: localized `title`, `category_id`, `regions`, `tags`, `file_path`, `duration_ms`, `waveform_path`, `source_url`, `license_type`, `license_proof`, `credit` (required for CC-BY), `added_by`.
5. Listen to it in the app after the next catalog refresh.

Templates (`templates/`, `previews/`) and sticker packs (`stickers/`) follow the same steps with their own folders and a JSON `preset` for templates (slots, timings, sound cues, effects, text styles).

## Monthly trending (1st of each month)

`trending` rows per launch region and `global`: 10–20 sounds and 5–10 templates each, ranked.

## Takedowns

Report or email → set `is_active = false` → record the outcome on the `reports` row → reply within 72 h.

Never commit R2 or Supabase write keys; the script reads them from `.env`.
