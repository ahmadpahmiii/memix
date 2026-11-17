package app.memix.feature.videoeditor.keyframes

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.component.ToolBar
import app.memix.core.designsystem.component.ToolBarClose
import app.memix.core.designsystem.component.ToolBarItem
import app.memix.core.designsystem.component.ToolTone
import app.memix.feature.videoeditor.ClipTools
import app.memix.feature.videoeditor.EditorFocusTargets
import app.memix.feature.videoeditor.EditorPreview
import app.memix.feature.videoeditor.KeyframeBarUi
import app.memix.feature.videoeditor.OverlayPanel
import app.memix.feature.videoeditor.TextPanel
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.tool_layer_opacity
import memix.core.ui.generated.resources.tool_position
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.keyframe_add
import memix.feature.video_editor.generated.resources.keyframe_add_click
import memix.feature.video_editor.generated.resources.keyframe_clear
import memix.feature.video_editor.generated.resources.keyframe_clear_click
import memix.feature.video_editor.generated.resources.keyframe_close_a11y
import memix.feature.video_editor.generated.resources.keyframe_easing
import memix.feature.video_editor.generated.resources.keyframe_move_click
import memix.feature.video_editor.generated.resources.keyframe_next
import memix.feature.video_editor.generated.resources.keyframe_next_click
import memix.feature.video_editor.generated.resources.keyframe_prev
import memix.feature.video_editor.generated.resources.keyframe_prev_click
import memix.feature.video_editor.generated.resources.keyframe_remove
import memix.feature.video_editor.generated.resources.keyframe_remove_click
import memix.feature.video_editor.generated.resources.tool_move_here
import memix.feature.video_editor.generated.resources.tool_volume
import org.jetbrains.compose.resources.stringResource

/**
 * The keyframe bar (spec P4-02b → The keyframe bar): it replaces the item's tool bar while it is open, with Close, Previous, Add or
 * Remove keyframe, Next, Easing, Move here, the value panels the item has (Position, Opacity, Volume) and Clear all. A tool that doesn't
 * apply to the item is absent. Whether the playhead is on a keyframe (Add becomes Remove, in the active look) is read here from the
 * preview's position inside a derived state, so only a change of the answer recomposes the bar.
 */
@Composable
internal fun KeyframeBar(
    bar: KeyframeBarUi,
    clipTools: ClipTools?,
    preview: EditorPreview?,
    focus: EditorFocusTargets,
    onIntent: (VideoEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = preview?.positionUs?.collectAsState()
    val onKeyframe by remember(bar.markers, position) { derivedStateOf { bar.markerAt(position?.value ?: 0L) != null } }
    val tools = rememberKeyframeTools(bar, clipTools, onKeyframe, focus, onIntent)
    val closeLabel = stringResource(Res.string.keyframe_close_a11y)
    ToolBar(tools, modifier, close = ToolBarClose(closeLabel) { onIntent(VideoEditorIntent.CloseKeyframeBar) })
}

@Composable
private fun rememberKeyframeTools(
    bar: KeyframeBarUi,
    clipTools: ClipTools?,
    onKeyframe: Boolean,
    focus: EditorFocusTargets,
    onIntent: (VideoEditorIntent) -> Unit,
): ImmutableList<ToolBarItem> {
    val previous = stringResource(Res.string.keyframe_prev)
    val previousClick = stringResource(Res.string.keyframe_prev_click)
    val add = stringResource(Res.string.keyframe_add)
    val addClick = stringResource(Res.string.keyframe_add_click)
    val remove = stringResource(Res.string.keyframe_remove)
    val removeClick = stringResource(Res.string.keyframe_remove_click)
    val next = stringResource(Res.string.keyframe_next)
    val nextClick = stringResource(Res.string.keyframe_next_click)
    val easing = stringResource(Res.string.keyframe_easing)
    val moveHere = stringResource(Res.string.tool_move_here)
    val moveClick = stringResource(Res.string.keyframe_move_click)
    val position = stringResource(UiRes.string.tool_position)
    val opacity = stringResource(UiRes.string.tool_layer_opacity)
    val volume = stringResource(Res.string.tool_volume)
    val clear = stringResource(Res.string.keyframe_clear)
    val clearClick = stringResource(Res.string.keyframe_clear_click)
    val properties = bar.properties
    val isCaption = clipTools == ClipTools.TEXT
    return remember(properties, isCaption, onKeyframe, previous, add, remove, next, easing, moveHere, position, opacity, volume, clear, focus, onIntent) {
        buildList {
            add(ToolBarItem(previous, MemixIcons.ArrowLeft, { onIntent(VideoEditorIntent.KeyframePrevious) }, clickLabel = previousClick))
            add(
                if (onKeyframe) {
                    ToolBarItem(remove, MemixIcons.Keyframe, { onIntent(VideoEditorIntent.KeyframeToggle) }, ToolTone.Active, removeClick, focus.addKeyframeTool)
                } else {
                    ToolBarItem(add, MemixIcons.Keyframe, { onIntent(VideoEditorIntent.KeyframeToggle) }, clickLabel = addClick, focusRequester = focus.addKeyframeTool)
                },
            )
            add(ToolBarItem(next, MemixIcons.Arrow, { onIntent(VideoEditorIntent.KeyframeNext) }, clickLabel = nextClick))
            add(ToolBarItem(easing, MemixIcons.Easing, { onIntent(VideoEditorIntent.KeyframeEasingTool) }, focusRequester = focus.easingTool))
            add(ToolBarItem(moveHere, MemixIcons.MoveHere, { onIntent(VideoEditorIntent.KeyframeMoveHere) }, clickLabel = moveClick))
            if (properties.position) {
                val panel: VideoEditorIntent = if (isCaption) VideoEditorIntent.OpenTextPanel(TextPanel.POSITION) else VideoEditorIntent.OpenOverlayPanel(OverlayPanel.POSITION)
                add(ToolBarItem(position, MemixIcons.Position, { onIntent(panel) }, focusRequester = focus.positionTool))
            }
            if (properties.opacity) add(ToolBarItem(opacity, MemixIcons.Opacity, { onIntent(VideoEditorIntent.OpenOverlayPanel(OverlayPanel.OPACITY)) }, focusRequester = focus.opacityTool))
            if (properties.volume) add(ToolBarItem(volume, MemixIcons.Volume, { onIntent(VideoEditorIntent.VolumeTool) }, focusRequester = focus.volumeTool))
            add(ToolBarItem(clear, MemixIcons.Delete, { onIntent(VideoEditorIntent.KeyframeClear) }, clickLabel = clearClick))
        }.toImmutableList()
    }
}
