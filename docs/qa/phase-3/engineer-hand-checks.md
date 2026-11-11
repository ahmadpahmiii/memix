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
