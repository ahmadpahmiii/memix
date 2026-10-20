package app.memix.feature.videoeditor

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.ToolBar
import app.memix.core.designsystem.component.ToolBarClose
import app.memix.core.designsystem.component.ToolBarItem
import app.memix.core.designsystem.component.ToolTone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.clip_tools_close
import memix.feature.video_editor.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.reorder_move_earlier
import memix.feature.video_editor.generated.resources.reorder_move_later
import memix.feature.video_editor.generated.resources.tool_add_media
import memix.feature.video_editor.generated.resources.tool_delete
import memix.feature.video_editor.generated.resources.tool_detach_audio
import memix.feature.video_editor.generated.resources.tool_duplicate
import memix.feature.video_editor.generated.resources.tool_edit
import memix.feature.video_editor.generated.resources.tool_meme_sounds
import memix.feature.video_editor.generated.resources.tool_move_here
import memix.feature.video_editor.generated.resources.tool_move_here_a11y
import memix.feature.video_editor.generated.resources.tool_reorder
import memix.feature.video_editor.generated.resources.tool_replace
import memix.feature.video_editor.generated.resources.tool_split
import memix.feature.video_editor.generated.resources.tool_volume
import org.jetbrains.compose.resources.stringResource

/**
 * The tool bar over the navigation bar's space (spec P1-06 → Layout): the main tools with nothing selected, the
 * selected item's tools otherwise, or reorder mode's buttons; each switch cross-fades in 120 ms (the same with reduce
 * motion: it's a fade).
 *
 * The focused Reorder tool disappears in the cross-fade, so when reorder mode opens from it, focus moves to Move
 * earlier, and when the mode ends it comes back to the Reorder tool (spec P1-06 → Accessibility → Reorder focus). A
 * long-press lift leaves focus alone. [focusTargets] holds the requesters of the tools focus goes back to after a pick,
 * the sounds sheet or the Volume panel. [selectedIsMissing]: the selected main clip's file is gone, so Replace comes first.
 */
@Composable
internal fun ToolBarRegion(
    clipTools: ClipTools?,
    reorder: ReorderUi?,
    isEmpty: Boolean,
    selectedIsMissing: Boolean,
    focusTargets: EditorFocusTargets,
    onIntent: (VideoEditorIntent) -> Unit,
) {
    val moveEarlierFocus = remember { FocusRequester() }
    val reorderToolFocus = remember { FocusRequester() }
    var focusAfterSwap by remember { mutableStateOf(FocusAfterSwap.NONE) }
    var openedFromTool by remember { mutableStateOf(false) }
    val reordering = reorder != null
    val fromTool = reorder?.byDrag == false
    LaunchedEffect(reordering) {
        when {
            reordering -> {
                openedFromTool = fromTool
                if (fromTool) focusAfterSwap = FocusAfterSwap.MOVE_EARLIER
            }
            openedFromTool -> {
                openedFromTool = false
                focusAfterSwap = FocusAfterSwap.REORDER_TOOL
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(MemixColors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        val mode = if (reordering) ToolBarMode.Reorder else clipTools?.let(ToolBarMode::Clip) ?: ToolBarMode.Main
        Crossfade(mode, animationSpec = tween(MemixMotion.durationPress), label = "tool bar") { shown ->
            when (shown) {
                ToolBarMode.Main -> ToolBar(rememberMainTools(isEmpty, focusTargets, onIntent))
                is ToolBarMode.Clip -> {
                    val closeLabel = stringResource(Res.string.clip_tools_close)
                    ToolBar(
                        rememberClipTools(shown.tools, selectedIsMissing, onIntent, reorderToolFocus, focusTargets),
                        close = ToolBarClose(closeLabel) { onIntent(VideoEditorIntent.ClearSelection) },
                    )
                    FocusOnce(focusAfterSwap == FocusAfterSwap.REORDER_TOOL, reorderToolFocus) { focusAfterSwap = FocusAfterSwap.NONE }
                }
                ToolBarMode.Reorder -> {
                    ReorderBar(onIntent, moveEarlierFocus)
                    FocusOnce(focusAfterSwap == FocusAfterSwap.MOVE_EARLIER, moveEarlierFocus) { focusAfterSwap = FocusAfterSwap.NONE }
                }
            }
        }
    }
}

private enum class FocusAfterSwap { NONE, MOVE_EARLIER, REORDER_TOOL }

/** Moves focus to [requester] once, after the bar that holds it is laid out, when [wanted]. */
@Composable
private fun FocusOnce(wanted: Boolean, requester: FocusRequester, onDone: () -> Unit) {
    if (!wanted) return
    LaunchedEffect(requester) {
        requester.requestFocus()
        onDone()
    }
}

private sealed interface ToolBarMode {
    data object Main : ToolBarMode

    data class Clip(val tools: ClipTools) : ToolBarMode

    data object Reorder : ToolBarMode
}

/**
 * Reorder mode's bar (spec P1-06 → Reorder → Moving by taps): Move earlier and Move later move the clip one place, Done
 * applies the order. Buttons rather than icon tools, so the words carry the meaning and nothing new is drawn. At large
 * font sizes they wrap onto a second line and the bar grows, rather than cutting a label.
 */
@Composable
private fun ReorderBar(onIntent: (VideoEditorIntent) -> Unit, moveEarlierFocus: FocusRequester) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MemixSize.toolbarHeight)
            // The tool bar's top edge, so the bar doesn't lose it for the length of the mode.
            .drawBehind { drawRect(MemixColors.hairline, Offset.Zero, Size(size.width, MemixStroke.strokeHairline.toPx())) }
            .padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2, Alignment.CenterVertically),
    ) {
        Button(stringResource(Res.string.reorder_move_earlier), { onIntent(VideoEditorIntent.ReorderStep(steps = -1)) }, Modifier.focusRequester(moveEarlierFocus))
        Button(stringResource(Res.string.reorder_move_later), { onIntent(VideoEditorIntent.ReorderStep(steps = 1)) })
        // Quiet: blue for the mode's one main action, and narrow enough that all three fit one line at 360 dp in English.
        Button(stringResource(Res.string.panel_done), { onIntent(VideoEditorIntent.ReorderDone) }, variant = ButtonVariant.Quiet)
    }
}

/**
 * Nothing selected: Edit, Meme sounds in blue (it's meme-sound UI), then Add media. Add media sits in place 3 until
 * P1-10's Text takes that place and moves it to 4; each tool keeps its place as more arrive (spec P1-06 → Main tool
 * bar). An empty project has only Add media: nothing to select, nothing to play a sound over.
 */
@Composable
private fun rememberMainTools(isEmpty: Boolean, focus: EditorFocusTargets, onIntent: (VideoEditorIntent) -> Unit): ImmutableList<ToolBarItem> {
    val edit = stringResource(Res.string.tool_edit)
    val memeSounds = stringResource(Res.string.tool_meme_sounds)
    val addMedia = stringResource(Res.string.tool_add_media)
    return remember(isEmpty, edit, memeSounds, addMedia, focus, onIntent) {
        val addMediaTool = ToolBarItem(addMedia, MemixIcons.Create, { onIntent(VideoEditorIntent.AddMediaTool) }, focusRequester = focus.addMediaTool)
        if (isEmpty) return@remember persistentListOf(addMediaTool)
        persistentListOf(
            ToolBarItem(edit, MemixIcons.Edit, { onIntent(VideoEditorIntent.EditTool) }),
            ToolBarItem(memeSounds, MemixIcons.Sounds, { onIntent(VideoEditorIntent.MemeSoundsTool) }, ToolTone.MemeSound, focusRequester = focus.memeSoundsTool),
            addMediaTool,
        )
    }
}

/**
 * The selected item's tools in the spec's order. Split comes first on clips (the most used cut), Volume first on
 * sounds, and Delete third at most, so "Split, then Delete" works without scrolling at 360 dp. Move here moves every
 * item but main clips, which reorder instead. Replace is a main clip's last tool, and its first when the clip's file
 * is missing ([isMissing]): it's the one tool that fixes it, and the missing-file toast names it.
 */
@Composable
private fun rememberClipTools(
    clipTools: ClipTools,
    isMissing: Boolean,
    onIntent: (VideoEditorIntent) -> Unit,
    reorderFocus: FocusRequester,
    focus: EditorFocusTargets,
): ImmutableList<ToolBarItem> {
    val split = stringResource(Res.string.tool_split)
    val delete = stringResource(Res.string.tool_delete)
    val duplicate = stringResource(Res.string.tool_duplicate)
    val reorder = stringResource(Res.string.tool_reorder)
    val volume = stringResource(Res.string.tool_volume)
    val moveHere = stringResource(Res.string.tool_move_here)
    val moveHereClickLabel = stringResource(Res.string.tool_move_here_a11y)
    val replace = stringResource(Res.string.tool_replace)
    val detach = stringResource(Res.string.tool_detach_audio)
    return remember(clipTools, isMissing, split, delete, duplicate, reorder, volume, moveHere, moveHereClickLabel, replace, detach, onIntent, reorderFocus, focus) {
        val splitTool = ToolBarItem(split, MemixIcons.Split, { onIntent(VideoEditorIntent.SplitSelected) })
        val deleteTool = ToolBarItem(delete, MemixIcons.Delete, { onIntent(VideoEditorIntent.DeleteSelected) }, ToolTone.Danger)
        val duplicateTool = ToolBarItem(duplicate, MemixIcons.Duplicate, { onIntent(VideoEditorIntent.DuplicateSelected) })
        // Its name is its label; "move to playhead" follows as the click label, since "here" means the playhead (spec
        // P1-06 → Accessibility).
        val moveHereTool = ToolBarItem(moveHere, MemixIcons.MoveHere, { onIntent(VideoEditorIntent.MoveHereSelected) }, clickLabel = moveHereClickLabel)
        val volumeTool = ToolBarItem(volume, MemixIcons.Volume, { onIntent(VideoEditorIntent.VolumeTool) }, focusRequester = focus.volumeTool)
        val replaceTool = ToolBarItem(replace, MemixIcons.Replace, { onIntent(VideoEditorIntent.ReplaceSelected) }, focusRequester = focus.replaceTool)
        val reorderTool = ToolBarItem(reorder, MemixIcons.Reorder, { onIntent(VideoEditorIntent.StartReorderSelected) }, focusRequester = reorderFocus)
        when (clipTools) {
            // Volume second, because lowering the original under a meme sound is the next most common job; Detach audio
            // with the rare tools (spec P1-06 → Clip tool bar). A clip with no sound keeps both; they explain on tap.
            ClipTools.VIDEO_CLIP -> {
                val detachTool = ToolBarItem(detach, MemixIcons.DetachAudio, { onIntent(VideoEditorIntent.DetachAudioSelected) })
                val usual = listOf(splitTool, volumeTool, deleteTool, duplicateTool, detachTool, reorderTool)
                (if (isMissing) listOf(replaceTool) + usual else usual + replaceTool).toImmutableList()
            }
            ClipTools.PHOTO_CLIP -> {
                val usual = listOf(splitTool, deleteTool, duplicateTool, reorderTool)
                (if (isMissing) listOf(replaceTool) + usual else usual + replaceTool).toImmutableList()
            }
            ClipTools.SOUND -> persistentListOf(
                volumeTool,
                splitTool,
                deleteTool,
                moveHereTool,
                duplicateTool,
            )
            ClipTools.TEXT -> persistentListOf(deleteTool, splitTool, moveHereTool, duplicateTool)
            ClipTools.OTHER -> persistentListOf(splitTool, deleteTool, moveHereTool, duplicateTool)
        }
    }
}

/**
 * The controls focus goes back to after a surface closes (spec P1-06 → Accessibility): the opener of a pick, the sounds
 * sheet or the Volume panel, or the Volume tool of a sound that just landed. Remembered once per editor screen.
 */
@Stable
internal class EditorFocusTargets {
    val addMediaTool = FocusRequester()
    val addMediaTile = FocusRequester()
    val replaceTool = FocusRequester()
    val memeSoundsTool = FocusRequester()
    val addSoundPill = FocusRequester()
    val volumeTool = FocusRequester()

    /** Moves focus to [target]'s control; nothing happens when it isn't on screen. */
    fun moveTo(target: FocusTarget) {
        when (target) {
            FocusTarget.ADD_MEDIA_TOOL -> addMediaTool
            FocusTarget.ADD_MEDIA_TILE -> addMediaTile
            FocusTarget.REPLACE_TOOL -> replaceTool
            FocusTarget.MEME_SOUNDS_TOOL -> memeSoundsTool
            FocusTarget.ADD_SOUND_PILL -> addSoundPill
            FocusTarget.VOLUME_TOOL -> volumeTool
        }.requestFocus()
    }
}
