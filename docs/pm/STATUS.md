# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved.

**Current phase:** P0 · Foundation (planned 12–18 Oct 2026; started 7 Oct, 5 days early) · Android launch target ≈ 7 Mar 2027 · iOS ≈ 2 May 2027

**Branch:** `phase-0`. One PR for the phase, opened after the QA pass and the phase gate. P0-01 to P0-04 and P0-09 were merged as single-ticket PRs before this rule.

| ID | Ticket | Status | Owner | Links |
| --- | --- | --- | --- | --- |
| P0-01 | Create the KMP project: Android and iOS targets, Compose Multiplatform, version catalog, module skeleton from the technical design | Built (7 Oct) | principal-mobile-engineer | [PR #1](https://github.com/ahmadpahmiii/memix/pull/1) |
| P0-02 | CI on GitHub Actions | Built (7 Oct); merge gate needs owner decision | principal-mobile-engineer | [PR #2](https://github.com/ahmadpahmiii/memix/pull/2) |
| P0-03 | Architecture base: Koin, navigation host, base ViewModel (UiState and Intent), result and error types, logging | Built (7 Oct) | principal-mobile-engineer | [PR #3](https://github.com/ahmadpahmiii/memix/pull/3) |
| P0-04 | Design system in Compose: tokens, bundled fonts, core components (buttons, chips, sound chip, cards, sheets, tabs, sliders, toggles) | Built (7 Oct), merged; design review: approved with polish | principal-mobile-engineer | [PR #5](https://github.com/ahmadpahmiii/memix/pull/5) |
| P0-05 | Bottom navigation and empty screens: Home, Templates, Create sheet, Sounds, Drafts | Built (7 Oct); design review: approved with polish | principal-mobile-engineer | on `phase-0` |
| P0-06 | Supabase project: schema SQL, access rules, seed data (10 sounds, 3 templates) | Built (8 Oct); seed sounds and templates moved with P0-07 (they need media hosting) | backend-engineer | |
| P0-07 | Cloudflare R2 bucket, custom media domain, upload script | Moved (8 Oct, owner): out of Phase 0 while another media-hosting approach is found; decide before P3 (7 Dec) | backend-engineer | |
| P0-08 | Firebase on Android: Crashlytics, Analytics, Remote Config | Built (8 Oct); owner confirms the test crash in the Crashlytics console and adds `media_base_url` in Remote Config | principal-mobile-engineer | |
| P0-09 | String resources for English, Indonesian, Spanish, Portuguese, Hindi | Built (7 Oct) | principal-mobile-engineer | [PR #4](https://github.com/ahmadpahmiii/memix/pull/4) |

**Top risks**
1. Full v1 scope for one developer: watch the cut list (speed curves, blend modes, transition count).
2. Sound licensing: no row without license fields. P0-06 seed sounds must be real licensed items, not dummies.
3. Media hosting is open (P0-07 moved). Nothing before P3 needs it (P1-08 uses the bundled starter pack); P3-01, P3-04, P3-12, P4-05 and P4-15 do.
