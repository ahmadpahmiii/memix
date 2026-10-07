package app.memix.feature.videoeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.ui.PlaceholderScreen
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.editor_video_body
import memix.feature.video_editor.generated.resources.editor_video_title
import org.jetbrains.compose.resources.stringResource

// TODO(P1-04): replace with the editor screen.
/** Stands in for the editor until it's built, so navigation and back can be checked end to end. */
@Composable
fun VideoEditorPlaceholder(onClose: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(stringResource(Res.string.editor_video_title), stringResource(Res.string.editor_video_body), onClose, modifier)
}
