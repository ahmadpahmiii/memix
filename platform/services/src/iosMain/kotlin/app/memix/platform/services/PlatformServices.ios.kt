package app.memix.platform.services

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.AppConfig
import app.memix.core.domain.DeviceRegion
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.drafts.LocalCalendar
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaInspector
import app.memix.core.domain.share.Sharer
import app.memix.core.domain.sound.SoundLibrary
import app.memix.core.domain.video.AppExitReasons
import app.memix.core.domain.video.ExportControls
import app.memix.core.domain.video.ExportForeground
import app.memix.core.domain.video.ExportNotice
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

actual val platformServicesModule = module {
    single<DeviceRegion> { IosDeviceRegion() }
    single<Logger> { IosLogger() }
    single<AppConfig> { DefaultAppConfig() }
    single<Analytics> { NoAnalytics() }
    single<MediaFiles> { NoMediaFiles() }
    single<MediaInspector> { NoMediaInspector() }
    single<SoundLibrary> { BundledSoundLibrary(get(), get(), Dispatchers.Default) }
    single<ExportForeground> { NoExportForeground() }
    single<Sharer> { NoSharer() }
    single<AppExitReasons> { NoAppExitReasons() }
    single<LocalCalendar> { IosLocalCalendar() }
}

// iOS keeps the system region in currentLocale even when the app has its own language.
private class IosDeviceRegion : DeviceRegion {
    override fun regionCode(): String? = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String

    override fun displayName(regionCode: String): String =
        NSLocale.currentLocale.displayNameForKey(NSLocaleCountryCode, regionCode) ?: regionCode
}

// TODO(P7-08): Crashlytics breadcrumbs and non-fatals on iOS.
private class IosLogger : Logger {
    override fun debug(tag: String, message: String) {
        println("D/$tag: $message")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        println("E/$tag: $message${throwable?.let { "\n" + it.stackTraceToString() }.orEmpty()}")
    }
}

// TODO(P7-08): Firebase Remote Config on iOS; until then iOS uses the defaults.
private class DefaultAppConfig : AppConfig {
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
    override val mediaBaseUrl: String = ""
}

// TODO(P7-08): Firebase Analytics on iOS; until then nothing is logged.
private class NoAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit
}

// TODO(P7-05): iOS exports run as a background task (beginBackgroundTask). Until then nothing exports on iOS.
private class NoExportForeground : ExportForeground {
    override fun start(controls: ExportControls): Boolean = false

    override fun showProgress(percent: Int, secondsLeft: Long?) = Unit

    override fun finish(notice: ExportNotice?) = Unit
}

// TODO(P7-09): UIActivityViewController.
private class NoSharer : Sharer {
    override fun shareVideo(location: String, chooserTitle: String): Boolean = false
}

private class NoAppExitReasons : AppExitReasons {
    override suspend fun lastRunStoppedByUser(): Boolean? = null
}
