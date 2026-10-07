-- P0-06: catalog schema, access rules and search. Media files live on R2; rows hold R2 keys.
create extension if not exists pg_trgm with schema extensions;

create function public.set_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at = now();
  return new;
end $$;

create table public.categories (
  id text primary key,
  kind text not null check (kind in ('sound', 'sticker', 'template')),
  name jsonb not null check (name ? 'en'),
  sort int not null default 0,
  is_active boolean not null default true
);

-- Every catalog table carries the same license columns; a CC-BY item can't exist without its credit.
create table public.sounds (
  id uuid primary key default gen_random_uuid(),
  title jsonb not null check (title ? 'en'),
  category_id text not null references public.categories (id),
  regions text[] not null default '{global}',
  tags text[] not null default '{}',
  file_path text not null,
  duration_ms int not null check (duration_ms > 0),
  waveform_path text,
  source_url text not null,
  license_type text not null check (license_type in ('CC0', 'CC-BY', 'royalty-free', 'own', 'licensed')),
  license_proof text not null,
  credit text,
  is_active boolean not null default true,
  added_by text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint sounds_cc_by_has_credit check (license_type <> 'CC-BY' or credit is not null)
);

create table public.templates (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('video_overlay', 'video_cutout', 'video_preset', 'photo_format', 'photo_template')),
  title jsonb not null check (title ? 'en'),
  category_id text not null references public.categories (id),
  regions text[] not null default '{global}',
  tags text[] not null default '{}',
  preview_path text not null,
  asset_paths text[] not null,
  preset jsonb not null,
  source_url text not null,
  license_type text not null check (license_type in ('CC0', 'CC-BY', 'royalty-free', 'own', 'licensed')),
  license_proof text not null,
  credit text,
  is_active boolean not null default true,
  added_by text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint templates_cc_by_has_credit check (license_type <> 'CC-BY' or credit is not null)
);

create table public.sticker_packs (
  id uuid primary key default gen_random_uuid(),
  title jsonb not null check (title ? 'en'),
  category_id text references public.categories (id),
  regions text[] not null default '{global}',
  tags text[] not null default '{}',
  cover_path text not null,
  source_url text not null,
  license_type text not null check (license_type in ('CC0', 'CC-BY', 'royalty-free', 'own', 'licensed')),
  license_proof text not null,
  credit text,
  is_active boolean not null default true,
  added_by text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint sticker_packs_cc_by_has_credit check (license_type <> 'CC-BY' or credit is not null)
);

create table public.stickers (
  id uuid primary key default gen_random_uuid(),
  pack_id uuid not null references public.sticker_packs (id) on delete cascade,
  file_path text not null,
  animated boolean not null default false,
  sort int not null default 0
);

create table public.trending (
  month date not null,
  region text not null,
  kind text not null check (kind in ('sound', 'template')),
  item_id uuid not null,
  rank int not null check (rank > 0),
  primary key (month, region, kind, rank)
);

create table public.reports (
  id uuid primary key default gen_random_uuid(),
  item_kind text not null check (item_kind in ('sound', 'template', 'sticker_pack')),
  item_id uuid not null,
  reason text check (char_length(reason) <= 500),
  status text not null default 'open' check (status in ('open', 'actioned', 'dismissed')),
  created_at timestamptz not null default now()
);

create trigger sounds_updated_at before update on public.sounds for each row execute function public.set_updated_at();
create trigger templates_updated_at before update on public.templates for each row execute function public.set_updated_at();
create trigger sticker_packs_updated_at before update on public.sticker_packs for each row execute function public.set_updated_at();

-- One index per filter and sort the app uses (TECHNICAL_DESIGN → app-call table).
create index sounds_category_recent on public.sounds (category_id, created_at desc) where is_active;
create index sounds_regions on public.sounds using gin (regions);
create index sounds_tags on public.sounds using gin (tags);
create index sounds_title_trgm on public.sounds using gin ((title::text) extensions.gin_trgm_ops);
create index templates_kind_recent on public.templates (kind, created_at desc) where is_active;
create index sticker_packs_recent on public.sticker_packs (created_at desc) where is_active;
create index stickers_pack_order on public.stickers (pack_id, sort);
create index reports_item on public.reports (item_kind, item_id);

-- Access rules: the app's publishable key reads active rows and files reports. Nothing else.
alter table public.categories enable row level security;
alter table public.sounds enable row level security;
alter table public.templates enable row level security;
alter table public.sticker_packs enable row level security;
alter table public.stickers enable row level security;
alter table public.trending enable row level security;
alter table public.reports enable row level security;

create policy "Active categories are readable" on public.categories for select to anon, authenticated using (is_active);
create policy "Active sounds are readable" on public.sounds for select to anon, authenticated using (is_active);
create policy "Active templates are readable" on public.templates for select to anon, authenticated using (is_active);
create policy "Active sticker packs are readable" on public.sticker_packs for select to anon, authenticated using (is_active);
create policy "Stickers of active packs are readable" on public.stickers for select to anon, authenticated
  using (exists (select 1 from public.sticker_packs p where p.id = pack_id and p.is_active));
-- Trending rows only hold ranks and ids; the items themselves are filtered by their own policies.
create policy "Trending ranks are readable" on public.trending for select to anon, authenticated using (true);
create policy "Anyone can file a report" on public.reports for insert to anon, authenticated with check (status = 'open');

-- Typo-tolerant search ("bruhh" finds "bruh"): trigram match on title and tags, active rows in the region or global.
create function public.search_sounds(q text, region text default 'global', lim int default 30, off int default 0)
returns setof public.sounds
language sql stable security invoker set search_path = '' as $$
  select s.*
  from public.sounds s
  where s.is_active
    and s.regions && array[region, 'global']
    and (extensions.word_similarity(q, s.title::text) >= 0.4
         or exists (select 1 from unnest(s.tags) t where extensions.similarity(q, t) >= 0.4))
  order by greatest(
             extensions.word_similarity(q, s.title::text),
             coalesce((select max(extensions.similarity(q, t)) from unnest(s.tags) t), 0)) desc,
           s.created_at desc
  limit least(greatest(lim, 1), 100) offset greatest(off, 0);
$$;
