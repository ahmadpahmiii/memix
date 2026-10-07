---
name: supabase-change
description: Make a change to Memix's Supabase backend safely — SQL migration, RLS policies, RPCs, indexes, seed data, the matching supabase-kt repository/DTO changes, cache rules, docs and a manual access check. Use for any schema, policy or catalog-query change.
---

# Supabase change

1. **Migration file.** `supabase/migrations/<yyyymmddhhmm>_<what>.sql` (folder created in P0-06). Never edit an applied migration; add a new one.
2. **Shared catalog columns** stay on every catalog table: `title jsonb`, `category_id`, `regions text[] default '{global}'`, `tags text[]`, `source_url not null`, `license_type not null check (in 'CC0','CC-BY','royalty-free','own','licensed')`, `license_proof not null`, `credit`, `is_active default true`, `added_by`, `created_at`, `updated_at`.
3. **RLS.** Enable on every new table. Anon: `select` only `where is_active = true`; `insert` only on `reports` (with length checks on `reason`). No update/delete for anon. Policy SQL lives in the same migration.
4. **Indexes.** One for every filter/sort the app uses; check `EXPLAIN ANALYZE` on realistic data (≥ 5,000 sounds) and put the timing in the PR.
5. **RPC.** Search via `search_sounds(q, region, lim, off)` (pg_trgm on title + tags, active only). New RPCs: `security invoker`, active rows only.
6. **Manual access check** with the anon key (curl or the Supabase client), pasted in the PR: active rows readable, inactive hidden, writes refused except a `reports` insert. QA repeats it at phase end.
7. **App.** DTOs (`@Serializable`), repository mapping to `:core:model`, SQLDelight cache tables and refresh windows (categories 24 h, sounds/trending/templates 6 h, search never cached).
8. **Seed.** Update seed data if needed (license fields filled).
9. **Docs.** `docs/TECHNICAL_DESIGN.md` → Backend (SQL sketch, app-call table).
10. **Secrets.** Service key and DB password only in `.env` / CI secrets. Never in code, logs or PR text.
11. **Cost.** Free plan: 500 MB DB, 5 GB egress, 1 GB storage, pauses after 1 idle week. Media stays on R2.
