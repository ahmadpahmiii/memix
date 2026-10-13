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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.ToolBar
import app.memix.core.designsystem.component.ToolBarClose
import app.memix.core.designsystem.component.ToolBarItem
import app.memix.core.designsystem.component.ToolTone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.clip_tools_close
import memix.feature.video_editor.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.reorder_move_earlier
import memix.feature.video_editor.generated.resources.reorder_move_later
import memix.feature.video_editor.generated.resources.tool_delete
import memix.feature.video_editor.generated.resources.tool_duplicate
import memix.feature.video_editor.generated.resources.tool_edit
import memix.feature.video_editor.generated.resources.tool_reorder
import memix.feature.video_editor.generated.resources.tool_split
import org.jetbrains.compose.resources.stringResource

/**
 * The tool bar over the navigation bar's space (spec P1-06 → Layout): the main tools with nothing selected, the
 * selected item's tools otherwise, or reorder mode's buttons; each switch cross-fades in 120 ms (the same with reduce
 * motion: it's a fade).
 */
@Composable
internal fun ToolBarRegion(clipTools: ClipTools?, reordering: Boolean, onIntent: (VideoEditorIntent) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MemixColors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        val mode = if (reordering) ToolBarMode.Reorder else clipTools?.let(ToolBarMode::Clip) ?: ToolBarMode.Main
        Crossfade(mode, animationSpec = tween(MemixMotion.durationPress), label = "tool bar") { shown ->
            when (shown) {
                ToolBarMode.Main -> ToolBar(rememberMainTools(onIntent))
                is ToolBarMode.Clip -> {
                    val closeLabel = stringResource(Res.string.clip_tools_close)
                    ToolBar(rememberClipTools(shown.tools, onIntent), close = ToolBarClose(closeLabel) { onIntent(VideoEditorIntent.ClearSelection) })
                }
                ToolBarMode.Reorder -> ReorderBar(onIntent)
            }
        }
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
private fun ReorderBar(onIntent: (VideoEditorIntent) -> Unit) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MemixSize.toolbarHeight)
            .padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2, Alignment.CenterVertically),
    ) {
        Button(stringResource(Res.string.reorder_move_earlier), { onIntent(VideoEditorIntent.ReorderStep(steps = -1)) })
        Button(stringResource(Res.string.reorder_move_later), { onIntent(VideoEditorIntent.ReorderStep(steps = 1)) })
        // Quiet: blue for the mode's one main action, and narrow enough that all three fit one line at 360 dp in English.
        Button(stringResource(Res.string.panel_done), { onIntent(VideoEditorIntent.ReorderDone) }, variant = ButtonVariant.Quiet)
    }
}

/** Nothing selected: Edit (P1-08 adds Meme sounds, P1-10 Text; each tool keeps its place as more arrive). */
@Composable
private fun rememberMainTools(onIntent: (VideoEditorIntent) -> Unit): ImmutableList<ToolBarItem> {
    val edit = stringResource(Res.string.tool_edit)
    return remember(edit, onIntent) { persistentListOf(ToolBarItem(edit, MemixIcons.Edit, { onIntent(VideoEditorIntent.EditTool) })) }
}

/**
 * The selected item's tools in the spec's order. Split comes first (the most used cut) and Delete third at most, so
 * "Split, then Delete" works without scrolling at 360 dp.
 */
@Composable
private fun rememberClipTools(clipTools: ClipTools, onIntent: (VideoEditorIntent) -> Unit): ImmutableList<ToolBarItem> {
    val split = stringResource(Res.string.tool_split)
    val delete = stringResource(Res.string.tool_delete)
    val duplicate = stringResource(Res.string.tool_duplicate)
    val reorder = stringResource(Res.string.tool_reorder)
    return remember(clipTools, split, delete, duplicate, reorder, onIntent) {
        val splitTool = ToolBarItem(split, MemixIcons.Split, { onIntent(VideoEditorIntent.SplitSelected) })
        val deleteTool = ToolBarItem(delete, MemixIcons.Delete, { onIntent(VideoEditorIntent.DeleteSelected) }, ToolTone.Danger)
        val duplicateTool = ToolBarItem(duplicate, MemixIcons.Duplicate, { onIntent(VideoEditorIntent.DuplicateSelected) })
        when (clipTools) {
            ClipTools.MAIN_CLIP -> persistentListOf(
                splitTool,
                deleteTool,
                duplicateTool,
                ToolBarItem(reorder, MemixIcons.Reorder, { onIntent(VideoEditorIntent.StartReorderSelected) }),
            )
            ClipTools.OTHER -> persistentListOf(splitTool, deleteTool, duplicateTool)
            ClipTools.TEXT -> persistentListOf(deleteTool, splitTool, duplicateTool)
        }
    }
}
