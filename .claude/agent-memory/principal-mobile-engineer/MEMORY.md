# principal-mobile-engineer memory

Lessons for this project only, newest first. Keep under ~150 lines; prune stale items. Focus: API gotchas (with versions), performance baselines per device, recurring review findings, patterns that worked.

- 2026-10-08 · P0 · AGP 9 keeps com.android.application out of KMP modules (the app is :androidApp). Compose 1.12: Modifier.dropShadow(shape, Shadow) exists; BackHandler is deprecated, use NavigationBackHandler (navigationevent 1.1.0). A sheet that holds focus must handle Key.Back itself. The phone's region comes from LocaleManager.systemLocales, not Locale.getDefault().

<!-- - 2026-10-xx · P0-01 · lesson … -->
