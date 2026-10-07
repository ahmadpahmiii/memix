---
name: code-standards
description: Memix Kotlin and Compose code standards — readable naming, small functions, error handling, coroutines and Flow rules, Compose structure — plus the review checklist every PR passes. Use when writing or reviewing any Kotlin in Memix.
---

# Code standards

Goal: code a new developer understands on first read, with no surprises in production.

## Readability

- Names say what a thing is or does: `trimClipToPlayhead()`, not `handle()`. No abbreviations except well-known ones (`id`, `url`, `fps`).
- One job per function; aim for under ~30 lines and at most 2 levels of nesting. Return early instead of nesting `if`s.
- No magic numbers: tokens for UI, named constants for domain values (`MAX_UNDO_STEPS = 100`).
- Comments explain **why**, never what. KDoc on public interfaces in `:core:domain` describing the contract (units, threading, errors).
- Delete dead code; don't comment it out. No TODO without a ticket ID (`TODO(P4-06)`).
- Keep files focused: one main class per file; a screen file holds the screen and its private sub-composables.

## Kotlin

- `val` and immutable data; `data class` for state; `List` not `MutableList` in APIs (`ImmutableList` from kotlinx.collections.immutable for Compose state).
- Never `!!`. Model absence with nullable types and handle it; model failures with a sealed `Result`/error type the UI can show ("That video won't open. Try another one.").
- `internal` by default inside modules; `public` only for the module's API.
- `when` over sealed types is exhaustive; no `else` branch that hides new cases.
- Extension functions only when they read naturally at the call site.

## Coroutines and Flow

- Structured concurrency only: `viewModelScope`, lifecycle scopes, or a scope owned by the engine. Never `GlobalScope`.
- Inject dispatchers (`DispatcherProvider`); blocking I/O in `withContext(io)`, CPU work on `default`, never on main.
- State = `StateFlow<UiState>` (`stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)`); one-off events (navigate, toast) = `Channel`/`SharedFlow`.
- Cancellation must work: check `isActive`/`ensureActive()` in long loops (export, mask generation); release resources in `finally`.

## Compose

- Screens are stateless: `ThingScreen(state, onIntent)`; the ViewModel lives one level up.
- `Modifier` is the first optional parameter and is applied to the root.
- No business logic or I/O in composables; `remember`/`derivedStateOf` for derived values; `key` and `contentType` in lazy lists.
- Previews for every component state the spec lists (they double as the designer's review material).
- Tokens only (`design-tokens` skill).

## Review checklist (every PR)

1. **Does it do the ticket?** Each "Done when" item, plus obvious edge cases: empty, huge, offline, denied permission, process death.
2. **Readable?** Names, function size, nesting, comments explain why.
3. **Architecture?** Layer rules, interfaces, no platform leaks into common code, time in µs, immutable Project, migration if persisted.
4. **Smooth?** Main thread clean, no heavy work in composition, stable state, fast-changing reads deferred to layout/draw, resources released (`mobile-performance`).
5. **Safe?** No secrets, no GPL/FFmpeg, no unlicensed content, ad rules respected, permissions requested in context.
6. **Strings and tokens?** Five languages, no hardcoded UI values, no v1 colors (`#FFE14D`, `#FF4FA3`).
7. **Checked by hand?** The PR says what was run on the emulator/phone, with screenshots for UI changes.

Write findings as: file:line, problem, why it matters, fix. Approve only when items 1–7 hold.
