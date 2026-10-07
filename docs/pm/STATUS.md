# Status board

Owned by the product manager. Keep it one screen long. Statuses: Not started · Ready · Building · Built · QA · Accepted · Needs work · Rejected · Moved.

**Current phase:** P0 · Foundation (planned 12–18 Oct 2026; started 7 Oct, 5 days early) · Android launch target ≈ 7 Mar 2027 · iOS ≈ 2 May 2027

| ID | Ticket | Status | Owner | Links |
| --- | --- | --- | --- | --- |
| P0-01 | Create the KMP project: Android and iOS targets, Compose Multiplatform, version catalog, module skeleton from the technical design | Ready (7 Oct) | principal-mobile-engineer | Placeholder screen only, no spec needed |
| P0-02 | CI on GitHub Actions | Not ready: needs P0-01 built and a GitHub repo (owner) | principal-mobile-engineer | |
| P0-03 | Architecture base: Koin, navigation host, base ViewModel (UiState and Intent), result and error types, logging | Not ready: needs P0-01 built | principal-mobile-engineer | |
| P0-04 | Design system in Compose: tokens, bundled fonts, core components (buttons, chips, sound chip, cards, sheets, tabs, sliders, toggles) | Not ready: needs P0-01 built; spec requested from ux-designer (toggle has no component spec yet; icon set changed to a Memix-drawn set) | principal-mobile-engineer | |
| P0-05 | Bottom navigation and empty screens: Home, Templates, Create sheet, Sounds, Drafts | Not ready: needs P0-03, P0-04; spec requested from ux-designer | principal-mobile-engineer | |
| P0-06 | Supabase project: schema SQL, access rules, seed data (10 sounds, 3 templates) | Not ready: needs the owner's Supabase project; seed rows need licensed sounds | backend-engineer | |
| P0-07 | Cloudflare R2 bucket, custom media domain, upload script | Not ready: needs the owner's Cloudflare account and the domain (PRD open item) | backend-engineer | |
| P0-08 | Firebase on Android: Crashlytics, Analytics, Remote Config | Not ready: needs P0-01 built and the owner's Firebase project | principal-mobile-engineer | |
| P0-09 | String resources for English, Indonesian, Spanish, Portuguese, Hindi | Not ready: needs P0-01 built | principal-mobile-engineer | |

**Top risks**
1. Full v1 scope for one developer: watch the cut list (speed curves, blend modes, transition count).
2. Sound licensing: no row without license fields. P0-06 seed sounds must be real licensed items, not dummies.
3. P0-06, P0-07, P0-08 depend on owner accounts and the domain; they can slip without blocking the app tickets.
