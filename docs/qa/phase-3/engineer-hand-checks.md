# Phase 3 · hand checks for the owner

The engineers' checks for each ticket's "Done when", copied from their handoffs. The owner asked for features first and testing later (10 Nov 2026), so nothing below has run on a device or against the live project yet. QA folds these into the phase-end pass.

## P3-13 · media storage on Supabase Storage

Nothing was applied or uploaded from the cloud checkout: it has no secret key. Built and dry-run only (`python3 scripts/media/upload_media.py --dry-run --starters` lists 10 keys, 10 sizes, the header and the public URLs). Replace `<ref>` with the project ref and `<publishable>` with the publishable key from `local.properties`; never paste the secret key anywhere but `.env`.

### A. Owner live run (once, on the Mac)

1. Put `SUPABASE_URL` and `SUPABASE_SECRET_KEY` in `.env` (names in `.env.example`, sources in `docs/CREDENTIALS.md`).
2. Confirm the project is Memix's (ref `drnhpnixnewqjfmrchrr`) in the Supabase dashboard, then apply the bucket migration `supabase/migrations/20261110100000_media_bucket.sql` (SQL editor). Dashboard: Storage shows a public bucket `media`. Apply the seed `20261110100100_seed_starter_sounds.sql` only after step 3 has uploaded the files (its rows are active at once); then the Table editor shows 10 rows in `sounds`. Don't use `supabase db push` here, because it applies both together.
3. `python3 scripts/media/upload_media.py --dry-run --starters`, then without `--dry-run`. Pass: `10 of 10 done`, each file prints `served: HTTP 200, Cache-Control: public, max-age=31536000`. Running it again prints `already exists` for each and still exits 0 (keys are never overwritten).
4. Header check, by hand: `curl -I https://<ref>.supabase.co/storage/v1/object/public/media/sounds/2026/11/f152c936-e835-5dc6-845e-072223b720b0.m4a`. Expect `200`, `content-type: audio/mp4`, `cache-control: public, max-age=31536000` (second request: `cf-cache-status: HIT`). Write the exact `cache-control` line into the ticket's commit message.
5. Optional, to learn whether Storage passes `immutable`: upload a throwaway key with `--cache-control "public, max-age=31536000, immutable"` (for example `previews/test-immutable.png` with any small PNG), `curl -I` it, and delete it in the dashboard. If the header comes back without `immutable`, the fallback stands; record that in `docs/TECHNICAL_DESIGN.md` → Backend.
6. Remote Config (Firebase console, with P1-15 check D): add `media_base_url` = `https://<ref>.supabase.co/storage/v1/object/public/media`. Or skip it and put `memix.media.baseUrl=<same value>` in `local.properties`.

### B. Anon access (repeat at phase end)

The publishable key can read and cannot write. Each command must be refused (4xx) except the first two.

```bash
BASE=https://<ref>.supabase.co
KEY=<publishable>          # from local.properties; the publishable key only
F=sounds/2026/11/f152c936-e835-5dc6-845e-072223b720b0.m4a

# 1. Download works: 200
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/storage/v1/object/public/media/$F"
# 2. Catalog rows are readable: 10 starter sounds with file_path
curl -s "$BASE/rest/v1/sounds?select=id,file_path&limit=20" -H "apikey: $KEY" -H "Authorization: Bearer $KEY"
# 3. Upload refused (expect 400/403, "row-level security policy")
curl -s -w "\n%{http_code}\n" -X POST "$BASE/storage/v1/object/media/sounds/2026/11/anon-test.m4a" -H "apikey: $KEY" -H "Authorization: Bearer $KEY" -H "Content-Type: audio/mp4" --data-binary @content/starter-sounds/boing.m4a
# 4. Overwrite refused
curl -s -w "\n%{http_code}\n" -X PUT "$BASE/storage/v1/object/media/$F" -H "apikey: $KEY" -H "Authorization: Bearer $KEY" -H "Content-Type: audio/mp4" --data-binary @content/starter-sounds/boing.m4a
# 5. Delete refused (a refusal or an empty list; the file must still download afterwards)
curl -s -w "\n%{http_code}\n" -X DELETE "$BASE/storage/v1/object/media/$F" -H "apikey: $KEY" -H "Authorization: Bearer $KEY"
# 6. Listing refused or empty
curl -s -w "\n%{http_code}\n" -X POST "$BASE/storage/v1/object/list/media" -H "apikey: $KEY" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" -d '{"prefix":"sounds/2026/11","limit":5}'
# 7. Re-run check 1: still 200
# 8. Takedown: set is_active=false on one sound row; check 2 no longer lists it after the next refresh
```

### C. App (phase-end pass, debug build)

1. With neither Remote Config nor `local.properties` set: `adb logcat -d -s AppConfig` shows `media_base_url=''` and the app behaves as before (starter sounds still play from the bundle).
2. With `memix.media.baseUrl` in `local.properties` and a rebuilt app (`./gradlew :androidApp:installDebug`): the log shows the Storage URL. `mediaUrl(base, "sounds/2026/11/<id>.m4a")` joins with exactly one `/` whether or not the base ends with one (read the P3-01 code that first calls it).
3. After the console key is added and fetched, the log shows the console value even when `local.properties` differs (Remote Config wins).
4. Open a copy of `local.properties` without the key: `./gradlew :androidApp:processDebugMainManifest` still succeeds and the merged manifest has `app.memix.media_base_url` with an empty value (`androidApp/build/intermediates/merged_manifests/debug/.../AndroidManifest.xml`).

## P3-12a · content intake script

Built and run in `--dry-run` and locally only (reviewer must-fixes applied 11 Nov) (no `.env`, no live project touched, nothing uploaded). Needs Python 3.9+, `ffmpeg` and `ffprobe` on the Mac (`brew install ffmpeg`). Script: `scripts/media/intake_sounds.py` (commands, rules and formats in its header and in `docs/TECHNICAL_DESIGN.md` → CMS workflow).

### A. Owner how-to (P3-12b: one batch of about 50 sounds)

1. **Keys, once:** `.env` gets `SUPABASE_URL` and `SUPABASE_SECRET_KEY` (P3-13 step 1) and `FREESOUND_API_KEY` (free API key from freesound.org/apiv2/apply; Secret, owner only). Nothing is ever printed or pasted into chat.
2. **Make the batch:** `python3 scripts/media/intake_sounds.py init content/intake/batch-01` (the folder must be inside the repo and named with letters, digits, `_` or `-`). It prints an example row.
3. **Find sounds:** on Freesound, filter by license (Creative Commons 0, or Attribution) and copy the sound page URLs, then `python3 scripts/media/intake_sounds.py fetch content/intake/batch-01 <url> <url> ... --category sound_reactions` (one category per call). A sound with any other license is refused on the spot. Sounds from elsewhere or your own recordings: put the file in `source/` and add a row by hand (`license_type: own`, `source_url: own:<where recorded>`); save the license page and a screenshot in `proofs/` and point `license_proof` at it (an https URL, or a path from the repo root under `content/`, for example `content/intake/batch-01/proofs/x.md`). Pixabay and "free to use in videos" licenses are not accepted (they bar standalone redistribution).
4. **Fill in `batch.json`:** the translated titles (`id`, `es`, `pt`, `hi`; machine drafts are fine), a better `title.en` if the Freesound name is rough, `tags`, `regions` (use `["ID"]` and so on for the Local tab), and `trim` for long recordings (`start` and `duration` in seconds, `fade_in`, `fade_out`).
5. **Dry run, then real:** `prepare content/intake/batch-01 --dry-run` prints the plan and every refused row with its reason. Fix those, then run `prepare` without `--dry-run`. It writes `out/` and `review.md`.
6. **Review:** open `review.md` (title, source URL, license, proof, credit, length, loudness), open each source URL and proof, and listen to the files in `out/`. A row marked "quiet: peak-capped" is below −20 LUFS; re-trim it or reject it.
7. **Approve:** `approve content/intake/batch-01 --all`, or `--ids <first 8 characters>,...` for some, and `--reject <ids>` for the rest. Pass: each prints `approved`. Changing any field of a row afterwards voids its approval (`load` says so).
8. **Load:** `load content/intake/batch-01 --dry-run` (lists every upload and prints the SQL), then `load content/intake/batch-01`. Pass: each file prints `served: HTTP 200, Cache-Control: public, max-age=31536000`, and `load.sql` is written. Re-running is safe (existing keys are skipped).
9. **Apply the SQL:** confirm the project ref is `drnhpnixnewqjfmrchrr`, then paste `load.sql` in the SQL editor (or copy it to `supabase/migrations/<yyyymmddhhmm>_sounds_batch-01.sql`). Pass: the rows count matches the approved sounds.
10. **Starter waveforms (once, also P3-12b):** `starters content/intake/starters-waveforms`, then `prepare`, `approve --all`, `load --dry-run`, `load`. It uploads 10 waveform files (the live audio is not touched) and writes `load.sql` with 10 `update public.sounds set waveform_path = … where … and waveform_path is null` statements; paste it in the SQL editor. Pass: each sound's `waveform_path` is set.
11. **Check in the app** after the next refresh (6 h, or clear the app data). Commit `batch.json`, `proofs/`, `review.md` and `load.sql`; never `source/` or `out/` (gitignored).

### B. What was checked here (repeat to confirm on the Mac, no keys needed)

On a scratch folder with generated tones (`ffmpeg -f lavfi -i "sine=frequency=440:duration=1.2" beep.wav`):
1. `prepare --dry-run`: rows with a missing `license_proof`, a missing `source_url`, `CC-BY` without `credit`, `CC-BY-NC`, `royalty-free`, a `license_url` of `by-nc` on a CC-BY row, an unknown category and a proof file that does not exist were each refused with a reason; the valid rows were planned; nothing was written.
2. `prepare`: the valid rows became `out/<id>.m4a` at −16.0 LUFS (−18.8 for a peaky noise burst, capped by −1 dBTP), true peak at or below −1 dBTP, plus `out/<id>.waveform.json` (`{"v":1,"peaks":[64 values]}`); a 50 ms tone was refused as too short; `review.md` listed 3 rows and the refusals.
3. `approve --ids` / `--reject`, then `load --dry-run`: only the approved rows were planned, 2 uploads each (audio, then waveform) with the same keys and headers as P3-13, and SQL printed. Editing a credit after approval made `load` refuse the row.
4. The printed SQL was applied twice in a rolled-back transaction on a local Postgres 16 with the catalog migration: 3 rows the first time, 0 the second (`on conflict do nothing`), all constraints met.
5. `starters <dir>`, `prepare`, `approve --all`, `load --dry-run` (waveform-only mode): all 10 starter sounds pass the gate, get a waveform from the live file (hash-checked against the manifest) and 10 `update … where waveform_path is null` statements. Separately, a normal full run of the starters through the encoder passed 10 of 10 (Drum roll needed the headroom re-encode, ending at −23.8 LUFS, −1.2 dBTP).
6. Not run (needs keys): `fetch` against the live Freesound API and the live `load`. Both were run against stubs only (the header carries the token, the CDN download carries none, a `by-nc` sound is refused, a CC-BY sound gets the credit "<name> by <user>, Freesound"). **First live run: do `fetch` with 2 or 3 URLs and read `batch.json` and `proofs/` before doing 50.**

### B2. Review fixes, proven on fixtures (repeat on the Mac)

- **M1:** a row edited after approval is refused at `load` and is absent from the SQL (the dry-run printed 2 rows of 3; the stubbed live run wrote `load.sql` with 2 rows and uploaded 4 files).
- **M2:** an `id` of `../../evil` or in upper case is refused; `processed.file` as `../../../../etc/hostname`, `processed.waveform` as `/etc/hostname` and a `durationMs` holding SQL text are each refused at `load`.
- **S1:** a `license_proof` outside `content/`, with `..`, or an `http://` URL is refused; editing the proof file after approval voids the approval (3 rows refused).
- **S3:** a corrupt source (`junk.wav`) is refused with the ffmpeg reason and the batch continues; the stubbed `fetch` survived a sound with no `previews` (KeyError) and one with an `http://` preview, and saved each added row as it went.
- **S4/N:** a CC-BY credit equal to the title is refused; a `fr` title key, a `trim.start` of `"1; rm"` and a batch outside the repo are refused.

### C. Search in Hindi (P3-01's search check)

`search_sounds` needs no change. On local Postgres 16 with the P0-06 migration, in both a `C.UTF-8` and an `en_US.UTF-8` database: a Devanagari query finds a Devanagari title (full word, prefix of 2 or 3 letters, a word inside a longer title, a typo with a different vowel sign, a mixed Latin and Devanagari query), and an unrelated Devanagari word finds nothing. pg_trgm keeps vowel signs and virama inside the word, so there is no split at vowel signs. Repeat on the live project with `select title->>'en' from search_sounds('उदास', 'global');` after the starters are seeded (expect "Sad trombone").
