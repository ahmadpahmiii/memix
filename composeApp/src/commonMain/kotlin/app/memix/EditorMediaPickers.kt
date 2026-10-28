package app.memix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import app.memix.core.domain.media.MAX_ITEMS_PER_PICK
import app.memix.core.domain.project.MediaPlace
import app.memix.core.model.project.MediaOrigin
import app.memix.feature.videoeditor.MediaPickRequest
import app.memix.feature.videoeditor.VideoEditorIntent
import app.memix.platform.services.PickerMedia
import app.memix.platform.services.rememberMediaPickerLauncher

/**
 * The video editor's two system pickers (P1-16): up to 35 items for Add media, one for Replace. Opens the one
 * [request] asks for and keeps that request while the picker is open, saved with the screen, so the result goes back
 * to the editor with what it was for ([VideoEditorIntent.MediaPicked]) even after Android restarted the app behind the
 * picker. Call it unconditionally in the editor's destination: a result after a restart reaches only a picker
 * registered at the same place.
 */
@Composable
internal fun EditorMediaPickers(request: MediaPickRequest?, onIntent: (VideoEditorIntent) -> Unit) {
    val currentOnIntent by rememberUpdatedState(onIntent)
    val openRequest = rememberSaveable(stateSaver = MediaPickRequestSaver) { mutableStateOf<MediaPickRequest?>(null) }
    val onPicked: (List<MediaOrigin>) -> Unit = { picked ->
        val opened = openRequest.value
        openRequest.value = null
        if (opened != null) currentOnIntent(VideoEditorIntent.MediaPicked(opened, picked))
    }
    val addPicker = rememberMediaPickerLauncher(MAX_ITEMS_PER_PICK, PickerMedia.IMAGES_AND_VIDEOS, onPicked)
    val replacePicker = rememberMediaPickerLauncher(maxItems = 1, PickerMedia.IMAGES_AND_VIDEOS, onPicked)
    LaunchedEffect(request) {
        val wanted = request ?: return@LaunchedEffect
        openRequest.value = wanted
        val picker = if (wanted is MediaPickRequest.Replace) replacePicker else addPicker
        if (picker.launch()) {
            currentOnIntent(VideoEditorIntent.PickerOpened)
        } else {
            openRequest.value = null
            currentOnIntent(VideoEditorIntent.PickerUnavailable)
        }
    }
}

/** Saves a request as a short list of plain values: its kind, then the time or the clip id. */
private val MediaPickRequestSaver = Saver<MediaPickRequest?, List<Any>>(
    save = { request ->
        when (request) {
            null -> emptyList()
            is MediaPickRequest.Replace -> listOf(KIND_REPLACE, request.itemId)
            is MediaPickRequest.Add -> when (val place = request.place) {
                is MediaPlace.AfterClipAt -> listOf(KIND_AFTER_CLIP, place.atUs)
                MediaPlace.End -> listOf(KIND_END)
            }
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            KIND_REPLACE -> MediaPickRequest.Replace(saved[1] as String)
            KIND_AFTER_CLIP -> MediaPickRequest.Add(MediaPlace.AfterClipAt(saved[1] as Long))
            KIND_END -> MediaPickRequest.Add(MediaPlace.End)
            else -> null
        }
    },
)

private const val KIND_REPLACE = "replace"
private const val KIND_AFTER_CLIP = "after"
private const val KIND_END = "end"
