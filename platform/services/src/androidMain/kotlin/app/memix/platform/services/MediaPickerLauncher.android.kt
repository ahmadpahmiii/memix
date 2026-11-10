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
actual fun rememberMediaPickerLauncher(maxItems: Int, media: PickerMedia, onPicked: (List<MediaOrigin>) -> Unit): MediaPickerLauncher {
    // Saved with the activity, so a tap while the picker is still opening is ignored even across a rotation.
    val awaitingResult = rememberSaveable { mutableStateOf(false) }
    // A call site always passes the same maxItems, so this branch never switches for a given picker.
    val launchPicker = if (maxItems == 1) rememberSinglePicker(media, awaitingResult, onPicked) else rememberMultiplePicker(media, maxItems, awaitingResult, onPicked)
    return remember(launchPicker) { AndroidMediaPickerLauncher(awaitingResult, launchPicker) }
}

// PickMultipleVisualMedia requires a maximum above 1 (androidx.activity 1.13), so one item uses PickVisualMedia.
@Composable
private fun rememberSinglePicker(media: PickerMedia, awaitingResult: MutableState<Boolean>, onPicked: (List<MediaOrigin>) -> Unit): () -> Unit {
    // The launcher re-registers whenever the contract instance changes, so it must stay the same object.
    val contract = remember { PickVisualMedia() }
    val activityLauncher = rememberLauncherForActivityResult(contract) { uri ->
        awaitingResult.value = false
        onPicked(listOfNotNull(uri).map { MediaOrigin.GalleryUri(it.toString()) })
    }
    val request = remember(media) { pickRequest(media, ordered = false) }
    return remember(activityLauncher) { { activityLauncher.launch(request) } }
}

@Composable
private fun rememberMultiplePicker(media: PickerMedia, maxItems: Int, awaitingResult: MutableState<Boolean>, onPicked: (List<MediaOrigin>) -> Unit): () -> Unit {
    val contract = remember(maxItems) { PickMultipleVisualMedia(pickerMaxItems(maxItems)) }
    val activityLauncher = rememberLauncherForActivityResult(contract) { uris ->
        awaitingResult.value = false
        onPicked(uris.map { uri -> MediaOrigin.GalleryUri(uri.toString()) })
    }
    val request = remember(media) { pickRequest(media, ordered = true) }
    return remember(activityLauncher) { { activityLauncher.launch(request) } }
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

// Images and videos or images only; several items come back in the order the user tapped them where the picker supports it.
private fun pickRequest(media: PickerMedia, ordered: Boolean): PickVisualMediaRequest = PickVisualMediaRequest.Builder()
    .setMediaType(if (media == PickerMedia.IMAGES_ONLY) PickVisualMedia.ImageOnly else PickVisualMedia.ImageAndVideo)
    .setOrderedSelection(ordered)
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
