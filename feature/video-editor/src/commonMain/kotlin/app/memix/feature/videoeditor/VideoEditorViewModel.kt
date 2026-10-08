package app.memix.feature.videoeditor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import app.memix.core.ui.MemixViewModel
import app.memix.core.ui.formatTimecode
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

// TODO(P1-04): becomes the editor's ViewModel, editing through a ProjectEditSession (StartEditSessionUseCase).
/** The editor placeholder's state: the clips of the main video track, read back from the saved project. */
@Immutable
data class VideoEditorUiState(val clips: ImmutableList<EditorClipRow>)

@Immutable
data class EditorClipRow(
    val id: String,
    val isVideo: Boolean,
    /** "00:12.40", in the timecode format every editor screen uses. */
    val durationText: String,
    /** For the screen reader: whole seconds, at least 1. */
    val durationSeconds: Int,
)

/** The placeholder has nothing to tap yet. */
sealed interface VideoEditorIntent

class VideoEditorViewModel(projectId: String, getProject: GetProjectUseCase) :
    MemixViewModel<VideoEditorUiState, VideoEditorIntent>(VideoEditorUiState(persistentListOf())) {

    init {
        viewModelScope.launch {
            // A draft that can't be read keeps the list empty; P1-04 specs what the editor says then.
            val project = (getProject(projectId) as? Outcome.Success)?.value ?: return@launch
            updateState { VideoEditorUiState(mainTrackRows(project)) }
        }
    }

    override fun onIntent(intent: VideoEditorIntent) = Unit

    private fun mainTrackRows(project: Project): ImmutableList<EditorClipRow> {
        val mainTrack = project.video?.tracks?.firstOrNull { it.kind == TrackKind.MAIN_VIDEO } ?: return persistentListOf()
        return mainTrack.items
            .filterIsInstance<MediaClip>()
            .sortedBy { it.startUs }
            .map { clip ->
                EditorClipRow(
                    id = clip.id,
                    isVideo = clip.source.kind == MediaKind.VIDEO,
                    durationText = formatTimecode(clip.durationUs),
                    durationSeconds = (clip.durationUs / MICROS_PER_SECOND).roundToInt().coerceAtLeast(1),
                )
            }
            .toImmutableList()
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000.0
    }
}
