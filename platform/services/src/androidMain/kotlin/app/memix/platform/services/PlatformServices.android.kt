package app.memix.platform.services

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.util.Log
import app.memix.core.domain.AppConfig
import app.memix.core.domain.AppError
import app.memix.core.domain.DeviceRegion
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import com.google.android.gms.tasks.Task
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformServicesModule = module {
    single<DeviceRegion> { AndroidDeviceRegion(androidContext()) }
    single<Logger> { AndroidLogger() }
    single<AppConfig> { FirebaseAppConfig() }
}

private class AndroidDeviceRegion(private val context: Context) : DeviceRegion {
    override fun regionCode(): String? = systemLocale().country.ifEmpty { null }

    override fun displayName(regionCode: String): String =
        Locale.Builder().setRegion(regionCode).build().getDisplayCountry(Locale.getDefault())

    // A per-app language (Android 13+) replaces Locale.getDefault(), so the phone's region comes from the system list.
    private fun systemLocale(): Locale {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return Locale.getDefault()
        val systemLocales = context.getSystemService(LocaleManager::class.java).systemLocales
        return if (systemLocales.isEmpty) Locale.getDefault() else systemLocales[0]
    }
}

/** Logcat plus Crashlytics: debug lines become breadcrumbs on the next crash report, errors become non-fatal reports. */
private class AndroidLogger : Logger {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
        crashlytics.log("$tag: $message")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
        crashlytics.log("$tag: $message")
        crashlytics.recordException(throwable ?: IllegalStateException("$tag: $message"))
    }
}

private class FirebaseAppConfig : AppConfig {
    private val remoteConfig = FirebaseRemoteConfig.getInstance().apply {
        setDefaultsAsync(mapOf(MEDIA_BASE_URL to ""))
    }

    override suspend fun refresh(): Outcome<Unit> = try {
        remoteConfig.fetchAndActivate().await()
        Outcome.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Outcome.Failure(AppError.Offline)
    } catch (e: Exception) {
        Outcome.Failure(AppError.Unexpected(e))
    }

    override val mediaBaseUrl: String get() = remoteConfig.getString(MEDIA_BASE_URL)

    private companion object {
        const val MEDIA_BASE_URL = "media_base_url"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error == null) continuation.resume(task.result) else continuation.resumeWithException(error)
    }
}
