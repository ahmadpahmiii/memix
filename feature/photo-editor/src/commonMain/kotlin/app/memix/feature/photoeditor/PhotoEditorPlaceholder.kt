package app.memix.feature.photoeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memix.core.ui.PlaceholderScreen
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.editor_photo_body
import memix.feature.photo_editor.generated.resources.editor_photo_title
import org.jetbrains.compose.resources.stringResource

// TODO(P2-01): replace with the editor screen.
/** Stands in for the editor until it's built, so navigation and back can be checked end to end. */
@Composable
fun PhotoEditorPlaceholder(onClose: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(stringResource(Res.string.editor_photo_title), stringResource(Res.string.editor_photo_body), onClose, modifier)
}
