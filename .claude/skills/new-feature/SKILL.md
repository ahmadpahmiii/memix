---
name: new-feature
description: Scaffold a new Memix feature or screen end-to-end — feature module, screen composable from the designer's spec, ViewModel with UiState/Intent, use case, repository method, Koin wiring, navigation route, strings in five languages and analytics events. Use when a ticket adds a screen, sheet or user flow.
---

# Add a feature

0. **Spec first.** UI work starts from `docs/ux/specs/<ID>-<screen>.md` and the board in `design/screens/`. No spec → ask the ux-designer.
1. **Module.** New screens go in an existing `:feature:*` module when one fits; create `:feature:<name>` only for a new top-level area (`settings.gradle.kts` + version-catalog plugins).
2. **Domain.** Use case in `:core:domain` (`class DoThingUseCase(private val repo: ThingRepository) { suspend operator fun invoke(...): Result<...> }`) and any repository interface method.
3. **Data.** Repository method in `:core:data` (remote via supabase-kt, local via SQLDelight; a new or changed table adds a `migrations/<n>.sqm`). Map DTOs to `:core:model`; no DTO leaks upward. Backend-facing parts are the backend engineer's.
4. **ViewModel.** `StateFlow<ThingUiState>` + `onIntent(ThingIntent)`; immutable UiState with loading/error/content; one-off effects via `Channel`.
5. **Screen.** Stateless `ThingScreen(state, onIntent)`, built from the spec with tokens only; previews for each spec state; 48 dp targets; content descriptions.
6. **Wire.** Koin (`viewModelOf(::ThingViewModel)`, `factoryOf(::DoThingUseCase)`), typed navigation route, back behavior.
7. **Strings.** Every string in en, id, es, pt, hi resources (English from the spec; machine drafts OK until P5).
8. **Analytics.** Fire the PRD events this flow owns through the `Analytics` interface.
9. **Check by hand.** Run it on the emulator through every spec state; save screenshots in `docs/ux/reviews/<ID>/` for the designer. No automated tests.
