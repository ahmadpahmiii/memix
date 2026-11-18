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
import app.memix.feature.videoeditor.keyframes.KeyframeBar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import memix.feature.video_editor.generated.resources.Res
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.tool_replace
import memix.feature.video_editor.generated.resources.clip_tools_close
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.reorder_move_earlier
import memix.feature.video_editor.generated.resources.reorder_move_later
import memix.feature.video_editor.generated.resources.tool_add_media
import memix.feature.video_editor.generated.resources.tool_voiceover
import memix.core.ui.generated.resources.tool_delete
import memix.feature.video_editor.generated.resources.tool_detach_audio
import memix.core.ui.generated.resources.tool_duplicate
import memix.feature.video_editor.generated.resources.tool_edit
import memix.core.ui.generated.resources.tool_edit_text
import memix.feature.video_editor.generated.resources.tool_meme_sounds
import memix.feature.video_editor.generated.resources.tool_move_here
import memix.feature.video_editor.generated.resources.tool_move_here_a11y
import memix.core.ui.generated.resources.tool_position
import memix.feature.video_editor.generated.resources.tool_reorder
import memix.feature.video_editor.generated.resources.tool_split
import memix.core.ui.generated.resources.tool_style
import memix.core.ui.generated.resources.tool_text
import memix.feature.video_editor.generated.resources.tool_audio_effects
import memix.feature.video_editor.generated.resources.tool_green_screen
import memix.feature.video_editor.generated.resources.tool_green_screen_click
import memix.feature.video_editor.generated.resources.tool_filters
import memix.feature.video_editor.generated.resources.tool_filters_click_all
import memix.feature.video_editor.generated.resources.tool_filters_click_clip
import memix.feature.video_editor.generated.resources.effects_change_title
import memix.feature.video_editor.generated.resources.tool_effect_change_click
import memix.feature.video_editor.generated.resources.tool_effects
import memix.feature.video_editor.generated.resources.tool_effects_click
import memix.feature.video_editor.generated.resources.tool_intensity
import memix.feature.video_editor.generated.resources.tool_freeze_frame
import memix.feature.video_editor.generated.resources.tool_freeze_frame_click
import memix.feature.video_editor.generated.resources.tool_reverse
import memix.feature.video_editor.generated.resources.tool_reverse_click_off
import memix.feature.video_editor.generated.resources.tool_reverse_click_on
import memix.feature.video_editor.generated.resources.tool_speed
import memix.feature.video_editor.generated.resources.tool_speed_click
import memix.feature.video_editor.generated.resources.tool_transition
import memix.feature.video_editor.generated.resources.tool_transition_click
import memix.feature.video_editor.generated.resources.tool_mask
import memix.feature.video_editor.generated.resources.tool_mask_click
import memix.feature.video_editor.generated.resources.tool_keyframes
import memix.feature.video_editor.generated.resources.tool_keyframes_click
import memix.feature.video_editor.generated.resources.tool_layer_order
import memix.feature.video_editor.generated.resources.tool_overlay
import memix.feature.video_editor.generated.resources.tool_overlay_click
import memix.core.ui.generated.resources.tool_layer_opacity
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
    keyframeBar: KeyframeBarUi?,
    preview: EditorPreview?,
    isEmpty: Boolean,
    hasVideoClip: Boolean,
    selectedIsMissing: Boolean,
    selectedIsReversed: Boolean,
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
        val mode = when {
            reordering -> ToolBarMode.Reorder
            keyframeBar != null -> ToolBarMode.Keyframes
            else -> clipTools?.let(ToolBarMode::Clip) ?: ToolBarMode.Main
        }
        Crossfade(mode, animationSpec = tween(MemixMotion.durationPress), label = "tool bar") { shown ->
            when (shown) {
                ToolBarMode.Main -> ToolBar(rememberMainTools(isEmpty, hasVideoClip, focusTargets, onIntent))
                is ToolBarMode.Clip -> {
                    val closeLabel = stringResource(Res.string.clip_tools_close)
                    ToolBar(
                        rememberClipTools(shown.tools, selectedIsMissing, selectedIsReversed, onIntent, reorderToolFocus, focusTargets),
                        close = ToolBarClose(closeLabel) { onIntent(VideoEditorIntent.ClearSelection) },
                    )
                    FocusOnce(focusAfterSwap == FocusAfterSwap.REORDER_TOOL, reorderToolFocus) { focusAfterSwap = FocusAfterSwap.NONE }
                }
                // The bar reads the newest state, so the markers and the playhead answer stay current while it fades.
                ToolBarMode.Keyframes -> keyframeBar?.let { KeyframeBar(it, clipTools, preview, focusTargets, onIntent) }
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

    data object Keyframes : ToolBarMode
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
        Button(stringResource(UiRes.string.panel_done), { onIntent(VideoEditorIntent.ReorderDone) }, variant = ButtonVariant.Quiet)
    }
}

/**
 * Nothing selected: Edit, Meme sounds in blue (it's meme-sound UI), Text, Add media, Overlay (P4-01b, Add media's sibling), then
 * Voiceover (P3-08, not blue: a recording is not a meme sound); each tool keeps its place as more arrive (spec P1-06 → Main tool
 * bar; P1-10 put Text in place 3; P4-01b put Overlay in place 5 and moved Voiceover to 6). An empty project has only Add media: nothing to select, nothing to play a sound over, caption or record against.
 */
@Composable
private fun rememberMainTools(isEmpty: Boolean, hasVideoClip: Boolean, focus: EditorFocusTargets, onIntent: (VideoEditorIntent) -> Unit): ImmutableList<ToolBarItem> {
    val edit = stringResource(Res.string.tool_edit)
    val memeSounds = stringResource(Res.string.tool_meme_sounds)
    val text = stringResource(UiRes.string.tool_text)
    val addMedia = stringResource(Res.string.tool_add_media)
    val voiceover = stringResource(Res.string.tool_voiceover)
    val overlay = stringResource(Res.string.tool_overlay)
    val overlayClickLabel = stringResource(Res.string.tool_overlay_click)
    val filters = stringResource(Res.string.tool_filters)
    val filtersClickLabel = stringResource(Res.string.tool_filters_click_all)
    val effects = stringResource(Res.string.tool_effects)
    val effectsClickLabel = stringResource(Res.string.tool_effects_click)
    val speed = stringResource(Res.string.tool_speed)
    val speedClickLabel = stringResource(Res.string.tool_speed_click)
    return remember(isEmpty, hasVideoClip, edit, memeSounds, text, addMedia, voiceover, overlay, overlayClickLabel, filters, filtersClickLabel, effects, effectsClickLabel, speed, speedClickLabel, focus, onIntent) {
        val addMediaTool = ToolBarItem(addMedia, MemixIcons.Create, { onIntent(VideoEditorIntent.AddMediaTool) }, focusRequester = focus.addMediaTool)
        if (isEmpty) return@remember persistentListOf(addMediaTool)
        listOfNotNull(
            ToolBarItem(edit, MemixIcons.Edit, { onIntent(VideoEditorIntent.EditTool) }),
            ToolBarItem(memeSounds, MemixIcons.Sounds, { onIntent(VideoEditorIntent.MemeSoundsTool) }, ToolTone.MemeSound, focusRequester = focus.memeSoundsTool),
            ToolBarItem(text, MemixIcons.Text, { onIntent(VideoEditorIntent.TextTool) }, focusRequester = focus.textTool),
            addMediaTool,
            ToolBarItem(overlay, MemixIcons.Overlay, { onIntent(VideoEditorIntent.AddOverlayTool) }, clickLabel = overlayClickLabel, focusRequester = focus.overlayTool),
            ToolBarItem(voiceover, MemixIcons.Mic, { onIntent(VideoEditorIntent.VoiceoverTool) }, focusRequester = focus.voiceoverTool),
            // Effects (P4-06) and Filters sit after Voiceover until Stickers (P2-09 follow-ups) takes its place before them (spec P4-06 → The tool and the bars).
            ToolBarItem(effects, MemixIcons.Effects, { onIntent(VideoEditorIntent.EffectsTool) }, clickLabel = effectsClickLabel, focusRequester = focus.effectsTool),
            ToolBarItem(filters, MemixIcons.Adjust, { onIntent(VideoEditorIntent.FiltersAllTool) }, clickLabel = filtersClickLabel, focusRequester = focus.filtersTool),
            // The last tool: it acts on the video clip under the playhead (spec P4-09 → Entry and exit); absent with no video clip to retime.
            ToolBarItem(speed, MemixIcons.Speed, { onIntent(VideoEditorIntent.SpeedMainTool) }, clickLabel = speedClickLabel, focusRequester = focus.speedTool).takeIf { hasVideoClip },
        ).toImmutableList()
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
    isReversed: Boolean,
    onIntent: (VideoEditorIntent) -> Unit,
    reorderFocus: FocusRequester,
    focus: EditorFocusTargets,
): ImmutableList<ToolBarItem> {
    val split = stringResource(Res.string.tool_split)
    val delete = stringResource(UiRes.string.tool_delete)
    val duplicate = stringResource(UiRes.string.tool_duplicate)
    val reorder = stringResource(Res.string.tool_reorder)
    val volume = stringResource(Res.string.tool_volume)
    val audioEffects = stringResource(Res.string.tool_audio_effects)
    val moveHere = stringResource(Res.string.tool_move_here)
    val moveHereClickLabel = stringResource(Res.string.tool_move_here_a11y)
    val replace = stringResource(UiRes.string.tool_replace)
    val detach = stringResource(Res.string.tool_detach_audio)
    val textTools = rememberTextTools(onIntent, focus)
    val position = stringResource(UiRes.string.tool_position)
    val opacity = stringResource(UiRes.string.tool_layer_opacity)
    val layerOrder = stringResource(Res.string.tool_layer_order)
    val keyframesLabel = stringResource(Res.string.tool_keyframes)
    val keyframesClick = stringResource(Res.string.tool_keyframes_click)
    val greenScreenLabel = stringResource(Res.string.tool_green_screen)
    val greenScreenClick = stringResource(Res.string.tool_green_screen_click)
    val maskLabel = stringResource(Res.string.tool_mask)
    val maskClick = stringResource(Res.string.tool_mask_click)
    val filtersLabel = stringResource(Res.string.tool_filters)
    val filtersClick = stringResource(Res.string.tool_filters_click_clip)
    val speedLabel = stringResource(Res.string.tool_speed)
    val speedClick = stringResource(Res.string.tool_speed_click)
    val transitionLabel = stringResource(Res.string.tool_transition)
    val transitionClick = stringResource(Res.string.tool_transition_click)
    val reverseLabel = stringResource(Res.string.tool_reverse)
    val reverseClick = stringResource(if (isReversed) Res.string.tool_reverse_click_on else Res.string.tool_reverse_click_off)
    val freezeLabel = stringResource(Res.string.tool_freeze_frame)
    val freezeClick = stringResource(Res.string.tool_freeze_frame_click)
    val intensityLabel = stringResource(Res.string.tool_intensity)
    val changeEffectLabel = stringResource(Res.string.effects_change_title)
    val changeEffectClick = stringResource(Res.string.tool_effect_change_click)
    return remember(clipTools, isReversed, reverseLabel, reverseClick, freezeLabel, freezeClick, speedLabel, speedClick, transitionLabel, transitionClick, intensityLabel, changeEffectLabel, changeEffectClick, isMissing, greenScreenLabel, greenScreenClick, maskLabel, maskClick, filtersLabel, filtersClick, split, delete, duplicate, reorder, volume, audioEffects, moveHere, moveHereClickLabel, replace, detach, textTools, position, opacity, layerOrder, keyframesLabel, keyframesClick, onIntent, reorderFocus, focus) {
        val splitTool = ToolBarItem(split, MemixIcons.Split, { onIntent(VideoEditorIntent.SplitSelected) })
        val deleteTool = ToolBarItem(delete, MemixIcons.Delete, { onIntent(VideoEditorIntent.DeleteSelected) }, ToolTone.Danger)
        val duplicateTool = ToolBarItem(duplicate, MemixIcons.Duplicate, { onIntent(VideoEditorIntent.DuplicateSelected) })
        // Its name is its label; "move to playhead" follows as the click label, since "here" means the playhead (spec
        // P1-06 → Accessibility).
        val moveHereTool = ToolBarItem(moveHere, MemixIcons.MoveHere, { onIntent(VideoEditorIntent.MoveHereSelected) }, clickLabel = moveHereClickLabel)
        val volumeTool = ToolBarItem(volume, MemixIcons.Volume, { onIntent(VideoEditorIntent.VolumeTool) }, focusRequester = focus.volumeTool)
        val audioEffectsTool = ToolBarItem(audioEffects, MemixIcons.AudioEffects, { onIntent(VideoEditorIntent.AudioEffectsTool) }, focusRequester = focus.audioEffectsTool)
        val replaceTool = ToolBarItem(replace, MemixIcons.Replace, { onIntent(VideoEditorIntent.ReplaceSelected) }, focusRequester = focus.replaceTool)
        val reorderTool = ToolBarItem(reorder, MemixIcons.Reorder, { onIntent(VideoEditorIntent.StartReorderSelected) }, focusRequester = reorderFocus)
        val positionTool = ToolBarItem(position, MemixIcons.Position, { onIntent(VideoEditorIntent.OpenOverlayPanel(OverlayPanel.POSITION)) }, focusRequester = focus.positionTool)
        val opacityTool = ToolBarItem(opacity, MemixIcons.Opacity, { onIntent(VideoEditorIntent.OpenOverlayPanel(OverlayPanel.OPACITY)) }, focusRequester = focus.opacityTool)
        val layerOrderTool = ToolBarItem(layerOrder, MemixIcons.Layers, { onIntent(VideoEditorIntent.OpenOverlayPanel(OverlayPanel.ORDER)) }, focusRequester = focus.layerOrderTool)
        val keyframesTool = ToolBarItem(keyframesLabel, MemixIcons.Keyframe, { onIntent(VideoEditorIntent.KeyframesTool) }, clickLabel = keyframesClick, focusRequester = focus.keyframesTool)
        val greenScreenTool = ToolBarItem(greenScreenLabel, MemixIcons.GreenScreen, { onIntent(VideoEditorIntent.GreenScreenTool) }, clickLabel = greenScreenClick, focusRequester = focus.greenScreenTool)
        val maskTool = ToolBarItem(maskLabel, MemixIcons.Mask, { onIntent(VideoEditorIntent.MaskTool) }, clickLabel = maskClick, focusRequester = focus.maskTool)
        val filtersTool = ToolBarItem(filtersLabel, MemixIcons.Adjust, { onIntent(VideoEditorIntent.FiltersTool) }, clickLabel = filtersClick, focusRequester = focus.filtersTool)
        // After Keyframes and before Duplicate: the rarer job for a meme, so Delete and Speed stay in view at 360 dp (spec P4-08 → The tool and the cut).
        val transitionTool = ToolBarItem(transitionLabel, MemixIcons.Transition, { onIntent(VideoEditorIntent.TransitionTool) }, clickLabel = transitionClick, focusRequester = focus.transitionTool)
        val speedTool = ToolBarItem(speedLabel, MemixIcons.Speed, { onIntent(VideoEditorIntent.SpeedTool) }, clickLabel = speedClick, focusRequester = focus.speedTool)
        // A switch: on takes the active look and the click label names the tap (spec P4-10 → Reverse). Freeze frame acts at once, with no panel.
        val reverseTool = ToolBarItem(reverseLabel, MemixIcons.Reverse, { onIntent(VideoEditorIntent.ReverseTool) }, if (isReversed) ToolTone.Active else ToolTone.Default, reverseClick, switchOn = isReversed)
        val freezeTool = ToolBarItem(freezeLabel, MemixIcons.Freeze, { onIntent(VideoEditorIntent.FreezeFrameTool) }, clickLabel = freezeClick)
        val intensityTool = ToolBarItem(intensityLabel, MemixIcons.Adjust, { onIntent(VideoEditorIntent.IntensityTool) }, focusRequester = focus.intensityTool)
        val changeEffectTool = ToolBarItem(changeEffectLabel, MemixIcons.Replace, { onIntent(VideoEditorIntent.ChangeEffectTool) }, clickLabel = changeEffectClick, focusRequester = focus.changeEffectTool)
        when (clipTools) {
            // An effect's tools (spec P4-06 → The tool and the bars): Intensity and Delete first so both show whole at 360 dp; no Keyframes, Volume or Position.
            ClipTools.EFFECT -> persistentListOf(intensityTool, deleteTool, changeEffectTool, splitTool, moveHereTool, duplicateTool)
            // The overlay's own tools (spec P4-01b → Overlay tool bar): Position (the no-drag route) and Opacity first, Delete third.
            // A missing file puts Delete first and leaves out what acts on nothing visible.
            ClipTools.VIDEO_OVERLAY -> {
                val detachTool = ToolBarItem(detach, MemixIcons.DetachAudio, { onIntent(VideoEditorIntent.DetachAudioSelected(fileMissing = isMissing)) })
                // Speed after Volume (spec P4-09 → The tool and the bars); a missing file has nothing to retime.
                val rest = listOf(splitTool, volumeTool, speedTool, reverseTool, keyframesTool, layerOrderTool, moveHereTool, duplicateTool, detachTool).filterNot { isMissing && (it === keyframesTool || it === speedTool || it === reverseTool) }
                // Green screen fourth: the common overlay job is a character on green (spec P4-04 → The tool in the bars); a missing file has no picture to key.
                (if (isMissing) listOf(deleteTool) + rest else listOf(positionTool, opacityTool, deleteTool, greenScreenTool, maskTool, filtersTool) + rest).toImmutableList()
            }
            ClipTools.PHOTO_OVERLAY -> {
                val rest = listOf(splitTool, keyframesTool, layerOrderTool, moveHereTool, duplicateTool).filterNot { isMissing && it === keyframesTool }
                (if (isMissing) listOf(deleteTool) + rest else listOf(positionTool, opacityTool, deleteTool, greenScreenTool, maskTool, filtersTool) + rest).toImmutableList()
            }
            // Volume second, because lowering the original under a meme sound is the next most common job; Detach audio
            // with the rare tools (spec P1-06 → Clip tool bar). A clip with no sound keeps both; they explain on tap.
            ClipTools.VIDEO_CLIP -> {
                val detachTool = ToolBarItem(detach, MemixIcons.DetachAudio, { onIntent(VideoEditorIntent.DetachAudioSelected(fileMissing = isMissing)) })
                // Keyframes animate the clip's volume until P4-16 draws its framing; a clip whose file is missing has nothing to animate.
                // Green screen after Delete until P4-16 and P4-07 put Crop, Fit or Fill, Position and Filters before it (spec P4-04 → The tool in the bars).
                val usual = listOfNotNull(splitTool, volumeTool, deleteTool, speedTool.takeUnless { isMissing }, freezeTool.takeUnless { isMissing }, reverseTool.takeUnless { isMissing }, greenScreenTool.takeUnless { isMissing }, maskTool.takeUnless { isMissing }, filtersTool.takeUnless { isMissing }, keyframesTool.takeUnless { isMissing }, transitionTool.takeUnless { isMissing }, duplicateTool, detachTool, reorderTool)
                (if (isMissing) listOf(replaceTool) + usual else usual + replaceTool).toImmutableList()
            }
            ClipTools.PHOTO_CLIP -> {
                val usual = listOfNotNull(splitTool, deleteTool, greenScreenTool.takeUnless { isMissing }, maskTool.takeUnless { isMissing }, filtersTool.takeUnless { isMissing }, transitionTool.takeUnless { isMissing }, duplicateTool, reorderTool)
                (if (isMissing) listOf(replaceTool) + usual else usual + replaceTool).toImmutableList()
            }
            // A frozen frame is a photo clip without Replace (spec P4-10 → The freeze clip's bar): its picture is a copy of a frame, so there is no file to swap.
            ClipTools.FREEZE_CLIP -> {
                listOfNotNull(splitTool, deleteTool, greenScreenTool, maskTool, filtersTool, transitionTool, duplicateTool, reorderTool).toImmutableList()
            }
            // Audio effects fourth, so it shows without scrolling at 360 dp (spec P3-09 → Entry and exit).
            ClipTools.SOUND -> persistentListOf(
                volumeTool,
                splitTool,
                deleteTool,
                audioEffectsTool,
                keyframesTool,
                moveHereTool,
                duplicateTool,
            )
            // What people do with a caption first; Delete before Split, since a caption is deleted more often than cut
            // (spec P1-10 → Text clip tools). The first four fit at 360 dp.
            ClipTools.TEXT -> (textTools + listOf(deleteTool, keyframesTool, splitTool, moveHereTool, duplicateTool)).toImmutableList()
            ClipTools.OTHER -> persistentListOf(splitTool, deleteTool, moveHereTool, duplicateTool)
        }
    }
}

/** Edit text, Style and Position: a caption's own tools, which always act (spec P1-10 → Text clip tools). */
@Composable
private fun rememberTextTools(onIntent: (VideoEditorIntent) -> Unit, focus: EditorFocusTargets): List<ToolBarItem> {
    val editText = stringResource(UiRes.string.tool_edit_text)
    val style = stringResource(UiRes.string.tool_style)
    val position = stringResource(UiRes.string.tool_position)
    return remember(editText, style, position, onIntent, focus) {
        listOf(
            ToolBarItem(editText, MemixIcons.EditText, { onIntent(VideoEditorIntent.EditTextSelected) }),
            ToolBarItem(style, MemixIcons.Style, { onIntent(VideoEditorIntent.OpenTextPanel(TextPanel.STYLE)) }, focusRequester = focus.styleTool),
            ToolBarItem(position, MemixIcons.Position, { onIntent(VideoEditorIntent.OpenTextPanel(TextPanel.POSITION)) }, focusRequester = focus.positionTool),
        )
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
    val audioEffectsTool = FocusRequester()
    val voiceoverTool = FocusRequester()
    val textTool = FocusRequester()
    val styleTool = FocusRequester()
    val positionTool = FocusRequester()
    val selectedCaption = FocusRequester()
    val ratioPill = FocusRequester()
    val overlayTool = FocusRequester()
    val opacityTool = FocusRequester()
    val layerOrderTool = FocusRequester()
    val selectedOverlay = FocusRequester()
    val keyframesTool = FocusRequester()
    val addKeyframeTool = FocusRequester()
    val easingTool = FocusRequester()
    val greenScreenTool = FocusRequester()
    val maskTool = FocusRequester()
    val filtersTool = FocusRequester()
    val effectsTool = FocusRequester()
    val changeEffectTool = FocusRequester()
    val intensityTool = FocusRequester()
    val transitionTool = FocusRequester()
    val speedTool = FocusRequester()

    /** Moves focus to [target]'s control; nothing happens when it isn't on screen. */
    fun moveTo(target: FocusTarget) {
        when (target) {
            FocusTarget.TEXT_TOOL -> textTool
            FocusTarget.STYLE_TOOL -> styleTool
            FocusTarget.POSITION_TOOL -> positionTool
            FocusTarget.SELECTED_CAPTION -> selectedCaption
            FocusTarget.ADD_MEDIA_TOOL -> addMediaTool
            FocusTarget.ADD_MEDIA_TILE -> addMediaTile
            FocusTarget.REPLACE_TOOL -> replaceTool
            FocusTarget.MEME_SOUNDS_TOOL -> memeSoundsTool
            FocusTarget.ADD_SOUND_PILL -> addSoundPill
            FocusTarget.VOLUME_TOOL -> volumeTool
            FocusTarget.AUDIO_EFFECTS_TOOL -> audioEffectsTool
            FocusTarget.VOICEOVER_TOOL -> voiceoverTool
            FocusTarget.RATIO_PILL -> ratioPill
            FocusTarget.OVERLAY_TOOL -> overlayTool
            FocusTarget.OPACITY_TOOL -> opacityTool
            FocusTarget.LAYER_ORDER_TOOL -> layerOrderTool
            FocusTarget.KEYFRAMES_TOOL -> keyframesTool
            FocusTarget.ADD_KEYFRAME_TOOL -> addKeyframeTool
            FocusTarget.EASING_TOOL -> easingTool
            FocusTarget.GREEN_SCREEN_TOOL -> greenScreenTool
            FocusTarget.MASK_TOOL -> maskTool
            FocusTarget.FILTERS_TOOL -> filtersTool
            FocusTarget.EFFECTS_TOOL -> effectsTool
            FocusTarget.CHANGE_EFFECT_TOOL -> changeEffectTool
            FocusTarget.INTENSITY_TOOL -> intensityTool
            FocusTarget.TRANSITION_TOOL -> transitionTool
            FocusTarget.SPEED_TOOL -> speedTool
        }.requestFocus()
    }
}
