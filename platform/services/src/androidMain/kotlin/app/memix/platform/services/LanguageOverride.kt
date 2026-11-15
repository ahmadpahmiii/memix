package app.memix.platform.services

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.os.StrictMode
import java.util.Locale

/**
 * The app language chosen in the Region sheet on Android 12 and below (API 29 to 32), where the system has no per-app
 * language. It is one tag in its own SharedPreferences file, read synchronously because it must be applied in
 * `attachBaseContext`, before anything is drawn: [wrap] in `MemixApplication` and `MainActivity`. On Android 13+ the
 * system keeps the language ([AndroidAppLanguageSwitch] uses `LocaleManager`), so [wrap] returns its context unchanged and a
 * tag left from before the phone updated is handed to the system once and deleted.
 */
object LanguageOverride {
    private const val FILE = "memix_language"
    private const val KEY_TAG = "tag"

    /** [base] with the stored language as its locale, or [base] itself when none is stored or the system does it. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base) ?: return base
        val locale = localeFor(tag)
        Locale.setDefault(locale)
        // Only the locale is overridden; everything else stays what the base context has.
        val delta = Configuration().apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(delta)
    }

    /** Android 13+: a tag stored on Android 12 goes to the system once, then is deleted. Call from `Application.onCreate`, so no screen is recreated after the first frame. */
    fun handOverToSystem(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val tag = storedTag(context) ?: return
        try {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } catch (failure: RuntimeException) {
            // The system refused: the tag is dropped anyway, the app follows the phone.
        }
        clear(context)
    }

    // The chosen language with the phone's own country kept, so number and date formats do not lose it ("id-ID" on an Indonesian phone).
    internal fun localeFor(tag: String): Locale {
        val country = Resources.getSystem().configuration.locales.get(0)?.country.orEmpty()
        return Locale.Builder().setLanguageTag(tag).setRegion(country.ifEmpty { null }).build()
    }

    internal fun storedTag(context: Context): String? = allowingDiskAccess {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TAG, null)
    }

    internal fun store(context: Context, tag: String) = allowingDiskAccess {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_TAG, tag).commit()
    }

    internal fun clear(context: Context) = allowingDiskAccess {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY_TAG).commit()
    }

    // The one tag has to be there before the first frame, so this small read (and write) on the main thread is on purpose.
    private fun <T> allowingDiskAccess(block: () -> T): T {
        val policy = StrictMode.allowThreadDiskReads()
        try {
            val writes = StrictMode.allowThreadDiskWrites()
            try {
                return block()
            } finally {
                StrictMode.setThreadPolicy(writes)
            }
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }
}
