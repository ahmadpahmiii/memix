package app.memix.platform.services

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import app.memix.core.model.project.MediaOrigin
import kotlin.math.min

/*
 * androidx.activity 1.13 picks the picker itself: the system photo picker (Android 11+ with Google system updates),
 * else a system-provided fallback picker (the Play services backport on Android 10, installed through the
 * ModuleDependencies entry in androidApp's manifest), else ACTION_OPEN_DOCUMENT. None needs a permission.
 *
 * Not set on purpose (spec P1-02): no accent color (it can't be contrast-checked on the phone's light and dark
 * picker), no default tab (recent items first), and no HDR transcoding (it needs Android 13 and stops at 1-minute
 * videos; the engine tone-maps HDR to SDR instead, P1-03).
 */
@Composable
actual fun rememberMediaPickerLauncher(maxItems: Int, onPicked: (List<MediaOrigin>) -> Unit): MediaPickerLauncher {
    // Saved with the activity, so a tap while the picker is still opening is ignored even across a rotation.
    val awaitingResult = rememberSaveable { mutableStateOf(false) }
    // The launcher re-registers whenever the contract instance changes, so it must stay the same object.
    val contract = remember(maxItems) { PickMultipleVisualMedia(pickerMaxItems(maxItems)) }
    val activityLauncher = rememberLauncherForActivityResult(contract) { uris ->
        awaitingResult.value = false
        onPicked(uris.map { uri -> MediaOrigin.GalleryUri(uri.toString()) })
    }
    return remember(activityLauncher) { AndroidMediaPickerLauncher(awaitingResult) { activityLauncher.launch(PICK_REQUEST) } }
}

private class AndroidMediaPickerLauncher(
    private val awaitingResult: MutableState<Boolean>,
    private val launchPicker: () -> Unit,
) : MediaPickerLauncher {
    override fun launch(): Boolean {
        if (awaitingResult.value) return true
        return try {
            launchPicker()
            awaitingResult.value = true
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

// Images and videos, returned in the order the user tapped them where the picker supports ordered selection.
private val PICK_REQUEST = PickVisualMediaRequest.Builder()
    .setMediaType(PickVisualMedia.ImageAndVideo)
    .setOrderedSelection(true)
    .build()

// The contract refuses a maximum above the system picker's own limit, so ask for the lower of the two.
@SuppressLint("NewApi") // getPickImagesMaxLimit exists where systemPickerAvailable() is true; lint can't follow SDK extension checks.
private fun pickerMaxItems(requested: Int): Int =
    if (systemPickerAvailable()) min(requested, MediaStore.getPickImagesMaxLimit()) else requested

// The same check androidx.activity makes before it opens MediaStore.ACTION_PICK_IMAGES.
private fun systemPickerAvailable(): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> true
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
    else -> false
}
