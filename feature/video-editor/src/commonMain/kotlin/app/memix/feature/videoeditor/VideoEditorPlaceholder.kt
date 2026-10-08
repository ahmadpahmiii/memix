package app.memix.feature.videoeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.CloseButton
import app.memix.core.ui.EmptyState
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.duration_seconds
import memix.feature.video_editor.generated.resources.editor_media_row_a11y
import memix.feature.video_editor.generated.resources.editor_video_body
import memix.feature.video_editor.generated.resources.editor_video_title
import memix.feature.video_editor.generated.resources.import_item_photo
import memix.feature.video_editor.generated.resources.import_item_video
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// TODO(P1-04): replace with the editor screen.
/**
 * Stands in for the editor until it's built. It lists the project's clips as saved, so a P1-02 import can be checked
 * end to end (spec P1-02 → Video editor). Rows can't be tapped.
 */
@Composable
fun VideoEditorPlaceholder(state: VideoEditorUiState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MemixColors.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
        CloseButton(onClose, Modifier.padding(start = MemixSpacing.space2, top = MemixSpacing.space2))
        LazyColumn(Modifier.weight(1f)) {
            item(key = "intro", contentType = "intro") {
                EmptyState(stringResource(Res.string.editor_video_title), stringResource(Res.string.editor_video_body))
            }
            itemsIndexed(state.clips, key = { _, clip -> clip.id }, contentType = { _, _ -> "clip" }) { index, clip ->
                ClipRow(position = index + 1, count = state.clips.size, clip = clip)
            }
        }
    }
}

@Composable
private fun ClipRow(position: Int, count: Int, clip: EditorClipRow) {
    val kindLabel = stringResource(if (clip.isVideo) Res.string.import_item_video else Res.string.import_item_photo)
    val spokenDuration = pluralStringResource(Res.plurals.duration_seconds, clip.durationSeconds, clip.durationSeconds)
    val spokenRow = stringResource(Res.string.editor_media_row_a11y, kindLabel, position, count, spokenDuration)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MemixSize.touchTarget)
            .padding(horizontal = MemixSpacing.space4)
            // Read as "Video 1 of 3, 12 seconds", not as timecode digits.
            .clearAndSetSemantics { contentDescription = spokenRow },
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(position.toString(), MemixTheme.type.timecode, color = MemixColors.textMuted)
        Icon(if (clip.isVideo) MemixIcons.Video else MemixIcons.Photo, contentDescription = null, tint = MemixColors.textSecondary)
        Text(kindLabel, MemixTheme.type.bodyStrong, Modifier.weight(1f))
        Text(clip.durationText, MemixTheme.type.timecode, color = MemixColors.textSecondary)
    }
}
