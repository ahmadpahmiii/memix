package app.memix.platform.services

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.annotation.RequiresApi
import app.memix.core.domain.region.AppLanguageSwitch
import app.memix.core.model.AppLanguage
import java.lang.ref.WeakReference
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-app language on every Android version Memix supports (spec P3-11, owner's delegation 12 Nov 2026).
 *
 * - **Android 13+ (API 33):** the framework's [LocaleManager]. It is the same setting as System settings → App languages
 *   (`android:localeConfig` lists the five); the system recreates the activity.
 * - **Android 10 to 12 (API 29 to 32):** the chosen tag is stored by [LanguageOverride], applied to the base context of the
 *   application and the activity, and the resumed activity is recreated. AndroidX's `AppCompatDelegate` backport would need an
 *   `AppCompatActivity`, which Memix does not use.
 *
 * Either way Compose resources, `LocalLocale`, the Hindi display face and the export notification's strings follow the new
 * configuration; whether they do is checked on a phone (TECHNICAL_DESIGN → Region sheet).
 */
internal class AndroidAppLanguageSwitch(private val context: Context) : AppLanguageSwitch {
    private val language = MutableStateFlow(readCurrent())
    private var resumedActivity = WeakReference<Activity>(null)

    override val current: StateFlow<AppLanguage> = language.asStateFlow()

    override val canChange: Boolean = true

    init {
        // The language can also change in System settings while the process lives; the callback keeps [current] true.
        context.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                language.value = readCurrent()
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(ResumedActivityTracker())
    }

    override fun phoneLanguageCode(): String? = AppLanguage.languageSubtag(systemLocale(context).toLanguageTag())

    override fun languageName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.forLanguageTag(language.value.code))

    override fun apply(language: AppLanguage) {
        val showing = readCurrent()
        // Set first so names and strings read from [current] are right before the new configuration arrives.
        this.language.value = language
        // Already showing it (the first confirm usually keeps the phone's language): writing it as an override could
        // recreate every screen for nothing, e.g. [hi] over [hi-IN, en-IN]. The first-confirm pin only matters when they differ.
        if (showing == language) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) applyWithSystem(language) else applyWithOverride(language)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyWithSystem(language: AppLanguage) {
        try {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.code)
        } catch (failure: RuntimeException) {
            // The system refused (or has no locale service): keep showing what the app really shows.
            this.language.value = readCurrent()
        }
    }

    private fun applyWithOverride(language: AppLanguage) {
        LanguageOverride.store(context, language.code)
        Locale.setDefault(LanguageOverride.localeFor(language.code))
        // No resumed activity (the app went to the background during the sheet's close): onActivityResumed catches up.
        resumedActivity.get()?.recreateUnlessShowing(language)
    }

    private fun Activity.recreateUnlessShowing(language: AppLanguage) {
        if (resolvedLanguage(resources.configuration) != language) recreate()
    }

    // The first of the phone's languages that is one of ours; the app shows English when none is.
    private fun readCurrent(): AppLanguage = resolvedLanguage(context.resources.configuration)

    private fun resolvedLanguage(configuration: Configuration): AppLanguage {
        val shown = configuration.locales.getFirstMatch(SUPPORTED_TAGS) ?: Locale.getDefault()
        return AppLanguage.fromTag(shown.toLanguageTag()) ?: AppLanguage.Default
    }

    private inner class ResumedActivityTracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            resumedActivity = WeakReference(activity)
            // Below Android 13 the activity can come back showing an older language than the one chosen while it was away.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) activity.recreateUnlessShowing(language.value)
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumedActivity.get() === activity) resumedActivity = WeakReference(null)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        val SUPPORTED_TAGS = AppLanguage.entries.map { it.code }.toTypedArray()
    }
}

/** The phone's own first locale, whatever language the app shows (the region and phone language come from here). */
internal fun systemLocale(context: Context): Locale {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // Below Android 13 the app's own locale replaces Locale.getDefault(), so ask the system's resources.
        return android.content.res.Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
    }
    val systemLocales = context.getSystemService(LocaleManager::class.java).systemLocales
    return if (systemLocales.isEmpty) Locale.getDefault() else systemLocales[0]
}
