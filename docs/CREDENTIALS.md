# Credentials and local config

Every key, password, token and private config file Memix uses from P0 to the iOS launch (P7), where it lives, and how CI gets it. **None of these values are committed to git**, including the ones that are public by design (they ship inside the app). The one identifier in the repo is the Supabase project ref `drnhpnixnewqjfmrchrr` (in `.mcp.json` and this file): it names the project but grants no access without a key or a sign-in.

Guards:
- `.gitignore` covers every file below.
- `scripts/check-secrets.sh` blocks a commit that contains a key, token, private key or one of these files. It runs as the pre-commit hook (`.githooks/pre-commit`) and in CI. Turn the hook on once per clone: `git config core.hooksPath .githooks`.
- Never `git add -f` an ignored file. If a secret ever reaches GitHub, rotate it first, then clean history.
- Back up every **Secret** in a password manager; local files can be lost.

Columns: **Secret** = gives write or admin access, keep private. **Public** = ships inside the app or is visible anyway, kept out of git by the rule above.

## Local files (never committed)

| File | Holds | Used by |
| --- | --- | --- |
| `local.properties` | `sdk.dir`; the app config values marked `local.properties` below | Gradle on this machine |
| `.env` | Every value marked `.env` below; template in `.env.example` | Scripts, Supabase and Cloudflare tooling |
| `androidApp/google-services.json` | Firebase Android config | Gradle google-services plugin |
| `iosApp/iosApp/GoogleService-Info.plist` | Firebase iOS config (P7) | Xcode build |
| `keystore.properties` + the upload keystore (`.jks`) | Android upload key and its passwords (P1-15) | Release signing |

Keep the keystore outside the repo folder (for example `~/keys/memix-upload.jks`); `keystore.properties` points to it.

## Inventory

| Credential | Kind | Ticket | Local | CI (GitHub Actions secret) | In the app? |
| --- | --- | --- | --- | --- | --- |
| Supabase project URL (`https://<project-ref>.supabase.co`) | Public | P0-06, P3-01 | `local.properties` → `memix.supabase.url` | `SUPABASE_URL` | Yes |
| Supabase publishable key (`sb_publishable_…`, formerly "anon") | Public; RLS lets it only read active rows and insert reports | P0-06, P3-01 | `local.properties` → `memix.supabase.publishableKey` | `SUPABASE_PUBLISHABLE_KEY` | Yes |
| Supabase secret key (`sb_secret_…`, formerly "service_role") | **Secret**; bypasses RLS | P3-12 intake script | `.env` → `SUPABASE_SECRET_KEY` | Not in CI | **Never** |
| Supabase database password | **Secret** | Migrations via CLI, if used | `.env` → `SUPABASE_DB_PASSWORD` | Not in CI | **Never** |
| Supabase personal access token | **Secret** | Supabase CLI or MCP without OAuth | `.env` → `SUPABASE_ACCESS_TOKEN` | Not in CI | **Never** |
| Cloudflare account ID | Identifier | P0-07 | `.env` → `R2_ACCOUNT_ID` | Not in CI | No |
| R2 access key ID and secret access key (S3 API token, Object Read & Write on the media bucket only) | **Secret** | P0-07, P3-12 | `.env` → `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` | Not in CI | **Never**: the app downloads public URLs |
| Cloudflare API token (Wrangler, Pages) | **Secret** | P6-04 website | `.env` → `CLOUDFLARE_API_TOKEN` | `CLOUDFLARE_API_TOKEN` only if CI deploys the site | **Never** |
| Media base URL (`https://media.<domain>`, or the r2.dev URL during development) | Public | P0-07 | Firebase Remote Config → `media_base_url` | Not needed | Read at runtime |
| Firebase Android config (`google-services.json`) | Public; its API key is restricted to `app.memix` | P0-08 | `androidApp/google-services.json` | `GOOGLE_SERVICES_JSON` (base64) | Yes, compiled in |
| Firebase iOS config (`GoogleService-Info.plist`) | Public; restricted to the bundle ID | P7-08 | `iosApp/iosApp/GoogleService-Info.plist` | `GOOGLE_SERVICE_INFO_PLIST` (base64) | Yes, compiled in |
| Android upload keystore, store password, key alias, key password | **Secret** | P1-15 | Keystore outside the repo + `keystore.properties` | `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | No (only the signature) |
| Google Play service account JSON (upload to internal testing) | **Secret** | P1-15 | Not stored locally | `PLAY_SERVICE_ACCOUNT_JSON` | **Never** |
| AdMob app ID and ad unit IDs | Public; debug builds use Google's test IDs | P5-01 to P5-03 | `local.properties` → `memix.admob.appId`, `memix.admob.rewardedId`, `memix.admob.interstitialId` | `ADMOB_APP_ID`, `ADMOB_REWARDED_ID`, `ADMOB_INTERSTITIAL_ID` | Yes |
| Apple distribution certificate (`.p12`) and its password | **Secret** | P7-01 | Keychain on the Mac | `IOS_CERT_P12_BASE64`, `IOS_CERT_PASSWORD` | No |
| Apple provisioning profile | **Secret** | P7-01 | `~/Library/MobileDevice/Provisioning Profiles` | `IOS_PROVISIONING_PROFILE_BASE64` | No |
| App Store Connect API key (`AuthKey_<id>.p8`, key ID, issuer ID) | **Secret** | P7-01, P7-10 | Outside the repo | `ASC_KEY_P8_BASE64`, `ASC_KEY_ID`, `ASC_ISSUER_ID` | **Never** |
| Freesound API key (finding CC0 sounds) | **Secret** | P3-12 | `.env` → `FREESOUND_API_KEY` | Not in CI | **Never** |

## Not stored anywhere in the project

- **MCP sign-ins (Supabase, Cloudflare):** OAuth tokens held by Claude Code. `.mcp.json` only holds the server URL and project ref.
- **GitHub:** the `gh` CLI keeps its token in the macOS keychain.
- **Account logins** (Supabase, Cloudflare, Firebase, Google Play Console, AdMob, Apple Developer, domain registrar): password manager only, with two-factor sign-in on.

## Adding a new one

1. Add a row above (kind, ticket, local place, CI secret name, in the app or not).
2. Add its file or variable to `.gitignore` or `.env.example`.
3. If it has a recognizable format, add the pattern to `scripts/check-secrets.sh`.
