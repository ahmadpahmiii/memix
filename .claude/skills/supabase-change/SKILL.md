---
name: supabase-change
description: Make a change to Memix's Supabase backend safely — SQL migration, RLS policies, RPCs, indexes, seed data, the matching supabase-kt repository/DTO changes, cache rules, docs and a manual access check. Use for any schema, policy or catalog-query change.
---

# Supabase change

1. **Migration file.** `supabase/migrations/<yyyymmddhhmm>_<what>.sql` (folder created in P0-06). Never edit an applied migration; add a new one.
2. **Shared catalog columns** stay on every catalog table: `title jsonb`, `category_id`, `regions text[] default '{global}'`, `tags text[]`, `source_url not null`, `license_type not null check (in 'CC0','CC-BY','royalty-free','own','licensed')`, `license_proof not null`, `credit`, `is_active default true`, `added_by`, `created_at`, `updated_at`.
3. **RLS and grants.** Enable RLS on every new table. Anon: `select` only `where is_active = true`; `insert` only on `reports` (with length checks on `reason`). No update/delete for anon. Policy SQL lives in the same migration. **Say the grant too:** Supabase no longer gives the API roles new tables by default (changelog 45329), and a policy without a table privilege answers `42501 permission denied`. A new table gets `grant select on ... to anon, authenticated` (catalog tables) or `grant insert on ... to anon, authenticated` (reports, which also must never be read back: the app sends `Prefer: return=minimal`), and a new RPC gets `grant execute on function ... to anon, authenticated`; add them to a new idempotent grants migration (the first is `20261111120000_api_role_grants.sql`). Check it on a local Postgres with the API roles created and no default privileges.
4. **Indexes.** One for every filter/sort the app uses; check `EXPLAIN ANALYZE` on realistic data (≥ 5,000 sounds) and put the timing in the PR.
5. **RPC.** Search via `search_sounds(q, region, lim, off)` (P3-01: word similarity `q <% sound_search_text(title, tags)`, the values of the five titles plus the tags, never `title::text`, which holds the jsonb keys; the function sets `pg_trgm.word_similarity_threshold` to 0.4 for its own call; active only). A function used in an index expression and in a query needs a standard SQL body (`begin atomic`) so the planner can inline it; with a `set search_path` clause it runs once per row and skips the index. New RPCs: `security invoker`, active rows only.
6. **Manual access check** with the anon key (curl or the Supabase client), pasted in the PR: active rows readable, inactive hidden, writes refused except a `reports` insert. QA repeats it at phase end. When the app writes (reports), also read the request the real client sends (columns, `Prefer`, body) and run exactly that body as the `anon` role, because the app's insert and the policy have to agree.
7. **App.** DTOs (`@Serializable`), repository mapping to `:core:model`, SQLDelight cache tables (in `:core:data`'s `MemixDatabase`; a new or changed table adds a `migrations/<n>.sqm`, checked by `:core:data:verifySqlDelightMigration`) and refresh windows (categories 24 h, sounds/trending/templates 6 h, search never cached).
8. **Seed.** Update seed data if needed (license fields filled).
9. **Docs.** `docs/TECHNICAL_DESIGN.md` → Backend (SQL sketch, app-call table).
10. **Secrets.** Service key and DB password only in `.env` / CI secrets. Never in code, logs or PR text.
11. **Cost.** Free plan: 500 MB DB, 5 GB egress, 1 GB storage, pauses after 1 idle week. Media is in the Supabase Storage bucket `media` until P6-07 (watch the 1 GB and the 5 GB egress), then on R2.
