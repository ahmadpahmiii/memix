package app.memix.feature.videoeditor.transitions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Slider
import app.memix.core.designsystem.component.ToolPanel
import app.memix.core.model.project.TransitionCatalog
import app.memix.feature.videoeditor.TransitionPanelUi
import app.memix.feature.videoeditor.VideoEditorIntent
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.panel_done
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.tool_transition
import memix.feature.video_editor.generated.resources.transition_dur_value
import memix.feature.video_editor.generated.resources.transition_duration
import memix.feature.video_editor.generated.resources.transition_none
import org.jetbrains.compose.resources.stringResource

/**
 * The Transition panel of the cut after the selected main clip (spec docs/ux/specs/P4-08-transitions.md → The Transition panel): a strip of the
 * 21 tiles (None, then the 20 in their groups) made from the cut's two real frames, and the Duration slider (hidden while None is chosen). A
 * tile is one undo step and plays the cut once; so does a Duration release. A ToolPanel over the timeline and tool bar, shown while [panel]
 * isn't null; the tiles come from [PanelFrames].
 */
@Composable
internal fun TransitionPanel(panel: TransitionPanelUi?, tiles: TransitionTiles, onIntent: (VideoEditorIntent) -> Unit, modifier: Modifier = Modifier) {
    // The last values stay on screen while the panel slides away.
    val held = remember { arrayOfNulls<TransitionPanelUi>(1) }
    if (panel != null) held[0] = panel
    val shown = panel ?: held[0]
    val frames = panel?.frames
    LaunchedEffect(frames?.key) { if (frames != null) tiles.show(frames.key, frames.outgoing, frames.incoming) }
    LaunchedEffect(panel == null) { if (panel == null) tiles.release() }
    ToolPanel(
        visible = panel != null,
        title = stringResource(Res.string.tool_transition),
        doneLabel = stringResource(UiRes.string.panel_done),
        onDone = { onIntent(VideoEditorIntent.CloseTransition) },
        modifier = modifier,
    ) {
        if (shown == null) return@ToolPanel
        TransitionStrip(shown.transitionId, tiles, onIntent)
        if (shown.transitionId != null) DurationRow(shown, onIntent)
    }
}

@Composable
private fun TransitionStrip(selectedId: String?, tiles: TransitionTiles, onIntent: (VideoEditorIntent) -> Unit) {
    val ids = remember { listOf<String?>(null) + TransitionCatalog.all.map { it.id } }
    val none = stringResource(Res.string.transition_none)
    val listState = rememberLazyListState()
    val selectedIndex = ids.indexOf(selectedId).coerceAtLeast(0)
    // The selected tile scrolls into view when the panel opens (and after an undo brings another one back).
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }
    LazyRow(Modifier.fillMaxWidth().selectableGroup(), state = listState, horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        itemsIndexed(ids, key = { _, id -> id ?: "none" }) { index, id ->
            val name = if (id == null) none else transitionNameOf(id)?.let { stringResource(it) }.orEmpty()
            TransitionTile(name, if (id == null) tiles.noneTile() else tiles.tileOf(id), selected = id == selectedId, position = "${index + 1} / ${ids.size}") {
                onIntent(VideoEditorIntent.TransitionPicked(id))
            }
        }
    }
}

// 72 dp square with the transition at 50% between the user's two frames, the name under it; a white ring when selected (spec → The strip).
@Composable
private fun TransitionTile(name: String, tile: ImageBitmap?, selected: Boolean, position: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(MemixSize.filterTileSize)
            // A tap on the chosen tile replays the cut (no step), so it stays a button.
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$name, $position" },
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(MemixSize.filterTileSize)
                .clip(MemixShapes.radiusMd)
                .background(MemixColors.surfaceRaised)
                .then(if (selected) Modifier.border(MemixStroke.strokeSelection, MemixColors.selection, MemixShapes.radiusMd) else Modifier),
        ) {
            if (tile != null) Image(BitmapPainter(tile), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Centered under the picture, one line or several, the same in the Filters and Transition strips (design review P4-08).
        Text(name, MemixTheme.type.label, Modifier.fillMaxWidth(), color = MemixColors.text, textAlign = TextAlign.Center)
    }
}

// Duration, 0.1 s to 2 s in 0.1 s steps (the keyboard's too); the thumb stops at the longest the two clips allow. A release applies and plays the cut.
@Composable
private fun DurationRow(panel: TransitionPanelUi, onIntent: (VideoEditorIntent) -> Unit) {
    val seconds = panel.durationUs / MICROS_PER_SECOND
    Slider(
        label = stringResource(Res.string.transition_duration),
        value = seconds,
        onValueChange = { onIntent(VideoEditorIntent.TransitionDurationChanged((it * MICROS_PER_SECOND).roundToInt().toLong())) },
        valueText = stringResource(Res.string.transition_dur_value, secondsText(panel.durationUs)),
        valueRange = TransitionCatalog.MIN_DURATION_US / MICROS_PER_SECOND..TransitionCatalog.MAX_DURATION_US / MICROS_PER_SECOND,
        defaultValue = TransitionCatalog.DEFAULT_DURATION_US / MICROS_PER_SECOND,
        step = TransitionCatalog.DURATION_STEP_US / MICROS_PER_SECOND,
        onValueChangeFinished = { onIntent(VideoEditorIntent.TransitionDurationReleased) },
    )
}

// "0.5": whole tenths of a second, with a point whatever the language (a timecode-like readout in `timecode`).
private fun secondsText(durationUs: Long): String {
    val tenths = (durationUs + TransitionCatalog.DURATION_STEP_US / 2) / TransitionCatalog.DURATION_STEP_US
    return "${tenths / TENTHS}.${tenths % TENTHS}"
}

private const val TENTHS = 10L
private const val MICROS_PER_SECOND = 1_000_000f
