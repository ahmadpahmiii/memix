# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved.

**Current phase:** P0 · Foundation (12–18 Oct 2026) · Android launch target ≈ 7 Mar 2027 · iOS ≈ 2 May 2027

| ID | Ticket | Status | Owner | Links |
| --- | --- | --- | --- | --- |
| P0-01 | Create the KMP project: Android and iOS targets, Compose Multiplatform, version catalog, module skeleton from the technical design | Not started | principal-mobile-engineer | |
| P0-02 | CI on GitHub Actions | Not started | principal-mobile-engineer | |
| P0-03 | Architecture base: Koin, navigation host, base ViewModel (UiState and Intent), result and error types, logging | Not started | principal-mobile-engineer | |
| P0-04 | Design system in Compose: tokens, bundled fonts, core components (buttons, chips, sound chip, cards, sheets, tabs, sliders, toggles) | Not started | principal-mobile-engineer | |
| P0-05 | Bottom navigation and empty screens: Home, Templates, Create sheet, Sounds, Drafts | Not started | principal-mobile-engineer | |
| P0-06 | Supabase project: schema SQL, access rules, seed data (10 sounds, 3 templates) | Not started | backend-engineer | |
| P0-07 | Cloudflare R2 bucket, custom media domain, upload script | Not started | backend-engineer | |
| P0-08 | Firebase on Android: Crashlytics, Analytics, Remote Config | Not started | principal-mobile-engineer | |
| P0-09 | String resources for English, Indonesian, Spanish, Portuguese, Hindi | Not started | principal-mobile-engineer | |

**Top risks**
1. Full v1 scope for one developer: watch the cut list (speed curves, blend modes, transition count).
2. Sound licensing: no row without license fields.
3. Two native video engines: Android first, reference renders for iOS parity.
