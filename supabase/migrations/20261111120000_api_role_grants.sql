-- P3-10: table privileges for the API roles (anon = the app's publishable key, authenticated). Row level security says WHICH
-- rows; these grants say whether the role may touch the table at all. Supabase is moving to explicit grants for new tables
-- (changelog 45329), and none of the earlier migrations has one, so a policy alone answers 42501 permission denied.
-- Catalog tables: select only (the "readable" policies keep inactive rows hidden). reports: insert only. The app sends
-- Prefer: return=minimal, so it needs no select on reports; it can read, change or delete no report.
-- Idempotent: applying it twice, or on a project that already grants these, changes nothing.
-- Apply it before the P3-01 and P3-13 live checks.
grant usage on schema public to anon, authenticated;

grant select on public.categories, public.sounds, public.templates, public.sticker_packs, public.stickers, public.trending
  to anon, authenticated;

grant insert on public.reports to anon, authenticated;

-- search_sounds is security invoker, so it runs with the caller's select rights above; sound_search_text is used by it and by the index.
grant execute on function public.search_sounds(text, text, integer, integer) to anon, authenticated;
grant execute on function public.sound_search_text(jsonb, text[]) to anon, authenticated;
