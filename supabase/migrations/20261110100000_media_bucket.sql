-- P3-13: public bucket for catalog media (Supabase Storage until P6-07 moves the files to Cloudflare R2 under the same keys).
-- Keys are versioned and never overwritten: sounds/<yyyy>/<mm>/<uuid>.m4a, waveforms/..., previews/..., templates/..., stickers/...
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'media', 'media', true,
  10485760, -- 10 MB per file; the largest planned file is a template preview
  array['audio/mp4', 'audio/aac', 'audio/mpeg', 'application/json', 'image/webp', 'image/png', 'image/jpeg', 'video/mp4']
)
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Access rules. Reads: a public bucket serves /storage/v1/object/public/media/<key> to anyone without touching
-- storage.objects policies, so the app's publishable key needs no policy to download. Writes: storage.objects has row
-- level security on, and with no policy for anon or authenticated, upload, update, delete and listing through the API
-- are all refused (listing would reveal unpublished files). Only the secret key, which bypasses RLS, writes: it lives
-- in .env on the owner's Mac and is used by scripts/media/upload_media.py. Do NOT add a policy on storage.objects for
-- the 'media' bucket; if one is ever needed it goes through a new migration and the anon checks in
-- docs/qa/phase-3/engineer-hand-checks.md (P3-13) are repeated.
