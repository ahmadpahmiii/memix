# Acceptance log

One entry per ticket check (provisional) and per phase gate (final). Newest first.

| Date | Ticket | Verdict | Evidence | Notes / follow-ups |
| --- | --- | --- | --- | --- |
| 2026-10-07 | P0-01 | Provisional accept | Engineer ran the placeholder on the Pixel_9 emulator (API 35) and the iPhone 17 simulator (iOS 26.5): "Memix" shows on both; `assembleDebug`, `lint` (0 errors) and the iOS framework link pass | CI lands in P0-02. Module layout changed for AGP 9 (`:androidApp`), recorded in TECHNICAL_DESIGN → Decisions. Placeholder text color is hardcoded until P0-04 (`TODO(P0-04)`). |
| 2026-10-07 | P0-02 | Provisional accept, one item open | PR #2 ran both jobs green (Android build and lint 6m3s, iOS framework 4m6s) | "Merging requires green" can't be enforced: GitHub refused branch protection on a private repo without GitHub Pro. Owner decides: GitHub Pro, a public repo, or the rule as a convention. |
| 2026-10-07 | P0-03 | Provisional accept | Home shows the region from `GetPhoneRegionUseCase` on the emulator ("United States") and the simulator ("Indonesia") | Logger is bound but unused until P0-08. |
| 2026-10-07 | P0-09 | Provisional accept | Per-app language switched through hi, id, es, pt, en on the emulator: the Compose string `region_global` showed in Hindi, and the country name follows the app language while the region stays the phone's (also checked on iOS in Hindi) | Indonesian resource resolution (`id` vs Android's legacy `in`) gets checked with P0-05's strings, which differ between languages. |
| 2026-10-07 | P0-04 | Provisional accept | Catalog screen shows every component; click-through on the emulator: sheet opens and closes 7 ways, toggle, chip, tab, segment, play/pause, favorite, slider tap, catalog close and back all change state; iOS catalog and sheet checked on the simulator; token audit clean; designer verdict Approved with polish | Focus rings and 200% font scale not verified on screen yet (QA phase pass). |
