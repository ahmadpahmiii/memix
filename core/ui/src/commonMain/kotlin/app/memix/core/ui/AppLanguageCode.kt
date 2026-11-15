package app.memix.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import app.memix.core.model.AppLanguage

/**
 * The code of the language the app shows now, one of the five ("en", "id", "es", "pt", "hi"), for looking up text the
 * catalog carries in each language. Use this, never `LocalLocale.current.language`: Java can report Indonesian as "in".
 * English when the locale is not one of the five. Changes with the composition when the app language changes.
 */
@Composable
fun appLanguageCode(): String =
    (AppLanguage.fromTag(LocalLocale.current.toLanguageTag()) ?: AppLanguage.Default).code
