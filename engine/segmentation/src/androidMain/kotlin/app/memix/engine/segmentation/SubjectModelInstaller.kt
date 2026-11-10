package app.memix.engine.segmentation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.memix.core.domain.segmentation.ModelPreparation
import app.memix.core.domain.segmentation.ModelState
import app.memix.core.domain.segmentation.SegmentationFailure
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusCodes
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

/**
 * The ML Kit subject-segmentation model is an optional Google Play services module, not part of the APK. This checks
 * that it is on the phone and downloads it on first use, reporting progress where Play services does
 * (developers.google.com/ml-kit/guides/play-services-optional-modules; the classes are checked against play-services-base 18.11.1).
 */
internal class SubjectModelInstaller(private val context: Context) {
    private val moduleInstall = ModuleInstall.getClient(context)

    suspend fun state(segmenter: SubjectSegmenter): ModelState {
        val playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (playServices != ConnectionResult.SUCCESS) return ModelState.Unavailable(SegmentationFailure.NO_PLAY_SERVICES)
        return try {
            if (moduleInstall.areModulesAvailable(segmenter).await().areModulesAvailable()) ModelState.Ready else ModelState.NotInstalled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelState.Unavailable(failureOf(e))
        }
    }

    suspend fun install(segmenter: SubjectSegmenter, onProgress: (Float?) -> Unit): ModelPreparation {
        // Without this, Play services would pause the download and wait for a network that may never come.
        if (!hasConnection()) return ModelPreparation.Failed(SegmentationFailure.OFFLINE)
        return suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            lateinit var listener: InstallStatusListener
            fun finish(result: ModelPreparation) {
                if (!finished.compareAndSet(false, true)) return
                moduleInstall.unregisterListener(listener)
                continuation.resume(result)
            }
            listener = InstallStatusListener { update ->
                when (update.installState) {
                    InstallState.STATE_COMPLETED -> finish(ModelPreparation.Ready)
                    InstallState.STATE_CANCELED -> finish(ModelPreparation.Cancelled)
                    InstallState.STATE_FAILED -> finish(ModelPreparation.Failed(failureOfStatus(update.errorCode)))
                    InstallState.STATE_DOWNLOADING -> onProgress(update.progressInfo?.let { it.bytesDownloaded fractionOf it.totalBytesToDownload })
                    else -> Unit // pending, installing, paused (waiting for the network) and unknown: keep waiting
                }
            }
            continuation.invokeOnCancellation { moduleInstall.unregisterListener(listener) }
            val request = ModuleInstallRequest.newBuilder().addApi(segmenter).setListener(listener).build()
            moduleInstall.installModules(request)
                .addOnSuccessListener { response -> if (response.areModulesAlreadyInstalled()) finish(ModelPreparation.Ready) }
                .addOnFailureListener { error -> finish(ModelPreparation.Failed(failureOf(error))) }
        }
    }

    private fun hasConnection(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private infix fun Long.fractionOf(total: Long): Float? = if (total > 0) (toFloat() / total).coerceIn(0f, 1f) else null

    private fun failureOf(error: Exception): SegmentationFailure =
        if (error is ApiException) failureOfStatus(error.statusCode) else SegmentationFailure.UNEXPECTED

    private fun failureOfStatus(statusCode: Int): SegmentationFailure = when (statusCode) {
        CommonStatusCodes.NETWORK_ERROR -> SegmentationFailure.OFFLINE
        ModuleInstallStatusCodes.INSUFFICIENT_STORAGE -> SegmentationFailure.STORAGE_FULL
        CommonStatusCodes.API_NOT_CONNECTED,
        CommonStatusCodes.SERVICE_DISABLED,
        CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED,
        -> SegmentationFailure.NO_PLAY_SERVICES
        else -> SegmentationFailure.MODEL_UNAVAILABLE
    }
}
