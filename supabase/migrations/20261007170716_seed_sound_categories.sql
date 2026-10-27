-- P0-06 seed: the sound categories from the PRD (Sound library → Categories). Non-English names are machine drafts until P5-06.
-- Template and sticker categories come with P4-05; the PRD doesn't define them yet.
insert into public.categories (id, kind, name, sort) values
  ('sound_trending', 'sound', '{"en":"Trending","id":"Trending","es":"Tendencias","pt":"Em alta","hi":"ट्रेंडिंग"}', 10),
  ('sound_reactions', 'sound', '{"en":"Reactions","id":"Reaksi","es":"Reacciones","pt":"Reações","hi":"रिएक्शन"}', 20),
  ('sound_impacts', 'sound', '{"en":"Impacts and hits","id":"Dentuman","es":"Golpes e impactos","pt":"Impactos e batidas","hi":"धमाके"}', 30),
  ('sound_fails', 'sound', '{"en":"Fails","id":"Gagal","es":"Fallos","pt":"Falhas","hi":"फेल"}', 40),
  ('sound_suspense', 'sound', '{"en":"Suspense","id":"Tegang","es":"Suspenso","pt":"Suspense","hi":"सस्पेंस"}', 50),
  ('sound_cartoon', 'sound', '{"en":"Cartoon","id":"Kartun","es":"Dibujos animados","pt":"Desenho animado","hi":"कार्टून"}', 60),
  ('sound_voice_lines', 'sound', '{"en":"Voice lines","id":"Kutipan suara","es":"Frases","pt":"Falas","hi":"डायलॉग"}', 70),
  ('sound_animals', 'sound', '{"en":"Animals","id":"Hewan","es":"Animales","pt":"Animais","hi":"जानवर"}', 80),
  ('sound_gaming', 'sound', '{"en":"Gaming","id":"Game","es":"Videojuegos","pt":"Games","hi":"गेमिंग"}', 90),
  ('sound_local', 'sound', '{"en":"Local","id":"Lokal","es":"Locales","pt":"Locais","hi":"लोकल"}', 100);
