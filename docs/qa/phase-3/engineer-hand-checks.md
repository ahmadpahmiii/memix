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

The P3-01 search fix (migration `20261111100000_search_sounds_values_only.sql`, section P3-01 below) changes the function and its index; the Hindi results below were checked again on it. On local Postgres 16 with the P0-06 migration, in both a `C.UTF-8` and an `en_US.UTF-8` database: a Devanagari query finds a Devanagari title (full word, prefix of 2 or 3 letters, a word inside a longer title, a typo with a different vowel sign, a mixed Latin and Devanagari query), and an unrelated Devanagari word finds nothing. pg_trgm keeps vowel signs and virama inside the word, so there is no split at vowel signs. Repeat on the live project with `select title->>'en' from search_sounds('उदास', 'global');` after the starters are seeded (expect "Sad trombone").

## P3-01 · catalog repositories

Built without a device or the live project: the owner asked for features first (10 Nov 2026). Nothing is applied to the live Supabase project. No screen uses the catalog yet (P3-02 does), so the app-side checks run through a debug build once P3-02 is in; until then QA relies on A and B.

### A. Owner steps (once, on the Mac)

1. Read `supabase/migrations/20261111100000_search_sounds_values_only.sql`, then apply it in the SQL editor (it runs as one transaction, so a failure leaves the old index and function). Then run `select title->>'en' from search_sounds('id', 'global');` (expect 0 rows; before the fix every sound came back) and `select title->>'en' from search_sounds('trombon', 'global');` (expect "Sad trombone" once the starters are seeded; a typo such as "trombonne" finds it too). The Supabase security advisor may list `sound_search_text` as having a mutable search path; it names no table and uses only built-in operators, so that is expected.
2. Put `memix.supabase.url=https://<ref>.supabase.co` and `memix.supabase.publishableKey=<sb_publishable_...>` in `local.properties` (names in `docs/CREDENTIALS.md`; the publishable key only, never the secret key), next to `memix.media.baseUrl`, and rebuild: `./gradlew :androidApp:installDebug`. Without them a debug build runs but the catalog never refreshes (every refresh reports Offline; one debug log line says so), and a release build (`bundleRelease`, `installBenchmark`) fails with "The release build has no catalog settings" unless `-Pmemix.catalogConfig=none` is passed (CI does; the owner's Play bundle must not). Every build also refuses a key that is not `sb_publishable_...` (or an anon JWT): a `sb_secret_...` or `service_role` key fails with "not a publishable key". Checked here with temporary values in `local.properties` (restored): no settings, secret key and service_role JWT fail; publishable key and anon JWT pass.

### B. What was checked here (repeat to confirm)

- **Search SQL, local Postgres 16** (catalog migrations, the starter seed and fixtures; a throwaway database): the old function matched all rows for "hi", "id" and "en"; the new one finds none for "id", "es", "pt" and "en", and "hi" only finds words that start with "hi". "bruhh" finds "Bruh"; Devanagari "उदास", "उदा", "ट्रॉम्", "हँसी" and "ढोल" find their rows. The 0.4 threshold applies inside the function only (a 0.5 match is found by `search_sounds` and not by a plain `<%` in the session, which keeps 0.6).
- **5,000-row speed check (for the phase-end pass).** On the live project, once at least a few thousand sounds are loaded (P3-12b), or on local Postgres with the generator below: run `explain (analyze, buffers) select s.* from sounds s where s.is_active and 'bruhh' operator(extensions.<%) public.sound_search_text(s.title, s.tags);` and then `select count(*) from search_sounds('bruhh', 'ID', 30, 0);` three times with `\timing on`. Pass: the plan shows a Bitmap Index Scan on `sounds_search_trgm` (or at worst a sequential scan under 15 ms) and the call takes under 20 ms. Here (local PG16, 5,000 synthetic rows, planner defaults) the old function took 50 to 75 ms and the new one 1.6 to 5 ms. A query that matches nearly every row (a word in all titles) sorts all the rows and takes longer (about 150 ms here); that is not a regression. Generator: insert 5,000 rows with `md5`-based titles in the five languages; the script used is not kept in the repo.
- **Desktop JVM script, 114 checks, all pass** (fakes for the network, a real SQLite file; not kept in the repo): categories refresh at 24 h and not before, a forced refresh, a clock that moved back; feed paging of 30 with a short last page; a page inside 6 h not fetched and one past 6 h fetched; refreshing page 0 restarts the feed; page N before N-1 refused; **launching offline**: a second repository on the same file with no network shows the cached categories and 35 cached sounds over 2 pages, a refresh returns Offline and leaves the cache whole; trending order (region by rank, then global, no repeats, a switched-off sound dropped, this month's `2026-12-01` requested, chips filter without a request); the offline search (case-insensitive, tags, Hindi, only saved ids, `%` and `_` literal, more than 999 ids in chunks, best title match first, flagged offline) and another failure not mistaken for offline; the waveform decoder (any peak count, unknown fields, version 2, bad JSON, quoted numbers, empty, clamped) and the waveform cache (fetched once, then served offline, a changed path refetched, no base URL or no network gives none); an empty category answer keeps the saved rows and leaves no stamp; more than 1024 waveform peaks refused; a version 1 database with a saved draft migrates to version 2 with the draft intact.
- `:core:data:verifySqlDelightMigration` passes (version 1 plus `1.sqm` equals `Catalog.sq` and `Project.sq`).

### C. On the phone (phase-end pass; needs P3-02's screen)

Run this on `./gradlew :androidApp:installBenchmark` (the R8-minified release code) with the two properties set in `local.properties`, because R8 keeps for Ktor, OkHttp and supabase-kt could only be checked by reading the merged rules here (Ktor's engine rule and the serialization rules cover the catalog; nothing was added), not by running them. A failure that shows only in this build, such as a refresh that always reports Offline or an empty list that a debug build fills, points at R8.

1. Online, first launch: the Sounds screen loads categories and the first 30 sounds; scrolling loads the next page. Pull to refresh reloads from page 0.
2. Close the app. Turn on airplane mode. Launch: the categories and the sounds seen before still show (the ticket's Done when) and the offline strip shows.
3. Search "bruhh" online: "Bruh" is found. Turn on airplane mode and search "bruh": the strip says the matches come from saved sounds, and only sounds whose audio is on the phone appear.
4. A sound with a waveform shows its bars on play; one with no waveform shows the rail (no crash). Check one with the file removed in the dashboard.
5. Upgrade check: install the previous build (Phase 2, database version 1) with a draft, then install this build over it: the draft opens.

## P3-02 · Sounds screen

Needs P3-01's migration applied and `memix.supabase.*` in `local.properties`. Use `installBenchmark` for the scroll check.

1. Open Sounds: title, search, five tabs, chips (All plus 8; no "Trending" or "Local" chip). The list shows at once on a second launch.
2. Type "bruhh" slowly, then fast: results appear about 300 ms after the last key; "Bruh" is in them. Clear (x): back to the tab at the same scroll. Back button with text: clears first, then leaves.
3. One character does nothing. A nonsense word shows "No sounds for ...". Hindi: type a Devanagari title word.
4. Airplane mode, then pull down: offline strip. Search "bruh": strip says saved sounds, only starter sounds match; "bruhh" finds nothing offline (expected: contains-match).
5. Play a starter sound (disc), tap again: stops. Play another: first stops. Tap a non-starter sound: "That sound won't play" toast (downloads arrive with P3-04). Leave the tab and return: preview stopped, search cleared, tab and chip kept.
6. My sounds shows its empty state; no Use block, name is not a button yet (P3-15, P3-03, P3-07). Favorites and the heart: see P3-06 below.
7. Scroll the list with 100+ sounds (after P3-12b): `adb shell dumpsys gfxinfo app.memix framestats` janky frames under 5 percent; page 2 loads near the end.
8. TalkBack: tabs read as tabs, discs "Play {name}", result count is spoken after a search (live region on a zero-size node: not verified, report if silent). 200% font and Hindi: nothing cut.
9. Rotate the phone with text in the search and a sound playing: both stay. Switch to Home and back: search cleared, preview stopped. (Review fixes.)
10. Tab fade: switch tabs and watch for one frame of the new list at full brightness; pull-to-refresh feel; scroll kept across a bottom-nav trip (only if navigation saves state).
11. Focus: "Clear search" button puts focus in the search box; "Show all" puts it on the "All" chip.
12. Not built from the spec: pause/resume (the player only stops), "turn up your volume" toast, long-press and TalkBack row actions (details sheet is P3-03), focus return after Show all and Clear search.

Desktop proof of the search path (not a phone): the ViewModel, `SearchSoundsUseCase`, the SQLDelight repository and the repo's real `search_sounds` SQL on a local Postgres 16: 34 checks, 0 failed (types "bruhh" at 50 ms per key: one server call 302 ms after the last key, result exactly "Bruh"). The HTTP layer (PostgREST), the live project and the phone are not covered.

## P3-06 · Favorites and recently used

Needs P3-01's catalog rows on the phone (open Trending once online). Use `installBenchmark` for the restart checks so the database file is the real one.

1. Heart: on Trending tap a heart. It fills at once (white heart), one short tick; tap again: it empties, no tick, no toast. The same sound shows the same heart on Local, Global and in search results.
2. Favorites tab, empty: the heart, "No favorites yet", "Browse trending" (goes to Trending). No Recently used heading.
3. Heart three sounds in order A, B, C. Favorites tab: "Favorites" heading, then C, B, A (last favorited first). No "Recently used" heading yet.
4. Restart (the Done when): swipe the app away, open it again, Sounds, Favorites: the same three, same order. Do the same after the app is force-stopped from settings.
5. Un-heart B inside the Favorites tab: the heart empties and the row stays where it is. Tap it again: it fills, the row has not moved. Un-heart B, change tab and come back: B is gone. Un-heart B, pull down on the tab: B goes. Un-heart B, go to Home and back to Sounds: B is gone.
6. Recently used: open a video project, add sounds from the Meme sounds sheet one by one (use five different ones, then a sixth). Favorites tab: "Recently used" first with the last five added, newest first; add the oldest of them again: it moves to the top. Previewing a sound in the sheet or on the Sounds tab adds nothing. Restart the app: the same five.
7. A sound in both lists shows in both sections with its heart filled. Heart a Recently used row: it also appears at the top of Favorites. Only recents and no favorites: under "Favorites" the hint "Tap the heart on a sound to keep it here." shows.
8. Search on Favorites: the placeholder says "Search your favorites"; typing filters both sections by title (any language); no match shows "No sounds for ..."; clearing returns the lists. Back clears the search first.
9. Play from both sections: the disc plays; tapping a Recently used row's disc while the same sound is in Favorites highlights both rows (same sound). Analytics debug log: `sound_preview` tab is `recent` for the first section and `favorites` for the second; no `sound_favorite` event exists.
10. Upgrade check (database v2 to v3): install the P3-02 build with a draft and some sounds browsed, then install this build over it: the draft opens and Trending still shows at once offline. Then favorite a sound, update again by reinstalling: it is still a favorite.
11. TalkBack: the heart reads "Favorite {name}, switch, off" (and "on" after a tap), not "checkbox, checked". On a row, the name and meta read as one stop "{name}, {meta}" and its actions list has "Add to favorites" or "Remove from favorites"; running it flips the heart. Tab, chips and the other rows are unchanged. The two section headings read as headings.
12. Heart speed: the fill must be visible within 100 ms of the tap (the write is local and the heart follows the database; if it ever lags, say so and the ViewModel gets an optimistic copy).
13. Hindi and 200% font: the headings, the hint and the row wrap and nothing is cut.
14. Storage failure (optional, if you can fill the phone): a heart tap or an added sound that can't be written is logged and dropped; the app doesn't crash.
15. Known limit to confirm: a starter sound used in the editor on a phone that never loaded the catalog is a recent that is not drawn until Trending has loaded once.

Desktop proof (not a phone): a JVM script on a real SQLite file, 29 checks, 0 failed: favorites and recents written, the connection closed, a new repository opened on the same file and both lists are there in order; the cap of 20 stored recents; a repeat moves to the top; a version 2 file (draft, saved sound, feed entry) migrates to 3 with all intact and the new tables take rows that survive another restart. `:core:data:verifySqlDelightMigration` passes.

