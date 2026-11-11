-- P3-01: fix sound search. The first version trigram-searched `title::text`, which is the jsonb text with its keys
-- (`{"en": "Boing", "hi": ...}`), so a query such as "hi", "id", "es", "pt" or "en" matched every row. Search now runs
-- on the values only (the five titles plus the tags) and the index can serve it.
--
-- Apply by the owner (supabase-change skill): SQL editor or `supabase db push`, after a look at the diff. Nothing in the
-- app needs the old function, and a failed apply leaves the old index and function in place (one transaction).

-- One searchable text per sound: every title in every language, then the tags. Declared immutable so it can be indexed.
-- array_to_string is only stable in Postgres because it accepts any array type; for text[] with a text separator it
-- always returns the same string, so the declaration is true.
-- It has a standard SQL body (begin atomic) and no `set search_path`, on purpose. A function with a settings clause is
-- never inlined; measured on 5,000 rows it then ran once per row (about 55 ms) and the planner skipped the index, against
-- about 2 ms with this form. The planner may still not inline an immutable function that calls the stable
-- array_to_string; the speed-up was measured either way. Do not "fix" the security advisor's search_path warning for
-- this function with `set search_path`: that brings the slow plan back. The body names no table and only uses built-in
-- operators, which are bound when the function is created.
create function public.sound_search_text(title jsonb, tags text[]) returns text
language sql immutable parallel safe
begin atomic
  select coalesce(title ->> 'en', '') || ' ' || coalesce(title ->> 'id', '') || ' ' || coalesce(title ->> 'es', '')
         || ' ' || coalesce(title ->> 'pt', '') || ' ' || coalesce(title ->> 'hi', '')
         || ' ' || coalesce(array_to_string(tags, ' '), '');
end;

drop index if exists public.sounds_title_trgm;
create index sounds_search_trgm on public.sounds
  using gin ((public.sound_search_text(title, tags)) extensions.gin_trgm_ops);

-- "bruhh" finds "bruh": `q <% text` is word_similarity(q, text) >= pg_trgm.word_similarity_threshold, an operator the
-- gin index above can answer (calling word_similarity(...) >= 0.4 as a function cannot use it). The threshold is the
-- function's own setting, 0.4 as before, so it applies to this call only and never to the caller's session.
create or replace function public.search_sounds(q text, region text default 'global', lim int default 30, off int default 0)
returns setof public.sounds
language sql stable security invoker
set search_path = ''
set pg_trgm.word_similarity_threshold = '0.4' as $$
  select s.*
  from public.sounds s
  where s.is_active
    and s.regions && array[region, 'global']
    and q operator(extensions.<%) public.sound_search_text(s.title, s.tags)
  order by extensions.word_similarity(q, public.sound_search_text(s.title, s.tags)) desc, s.created_at desc
  limit least(greatest(lim, 1), 100) offset greatest(off, 0);
$$;
