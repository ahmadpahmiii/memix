package app.memix.feature.photoeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.component.Button
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.make_video_title
import memix.feature.photo_editor.generated.resources.make_video_working
import org.jetbrains.compose.resources.stringResource

/**
 * The Make it a video meme row between the stage and the tool bar (spec docs/ux/specs/P2-10-make-it-a-video-meme.md → Layout): a
 * Secondary button centered on `canvas`, 56 dp tall (the button's touch target and `space-1` above and below). It collapses with the
 * stage's refit (200 ms, instant with reduce motion) whenever the state takes the room ([PhotoEditorUiState.showsMakeVideoRow]).
 * While the video is made the label reads "Making video…" and the button is busy.
 */
@Composable
internal fun MakeVideoRow(state: PhotoEditorUiState, onIntent: (PhotoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = MemixTheme.reduceMotion
    AnimatedVisibility(
        visible = state.showsMakeVideoRow,
        modifier = modifier,
        enter = expandVertically(if (reduceMotion) snap<IntSize>() else tween(MemixMotion.durationSheet)),
        exit = shrinkVertically(if (reduceMotion) snap<IntSize>() else tween(MemixMotion.durationSheet)),
    ) {
        Box(
            Modifier.fillMaxWidth().background(MemixColors.canvas).padding(vertical = MemixSpacing.space1),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                text = stringResource(if (state.makingVideo) Res.string.make_video_working else Res.string.make_video_title),
                onClick = { onIntent(PhotoEditorIntent.MakeVideo) },
                // The new label is announced politely while the video is made, and focus stays on the button.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                leadingIcon = MemixIcons.Video,
                busy = state.makingVideo,
            )
        }
    }
}

/** Over the whole editor while the video is made: swallows every touch, so nothing moves and a second tap does nothing (spec → While it works). */
@Composable
internal fun MakeVideoInputBlock(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        },
    )
}
