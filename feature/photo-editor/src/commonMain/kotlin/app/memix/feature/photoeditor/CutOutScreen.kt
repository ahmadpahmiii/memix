package app.memix.feature.photoeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.OptionTileLayout
import app.memix.core.designsystem.component.ProgressBar
import app.memix.core.designsystem.component.Segment
import app.memix.core.designsystem.component.SegmentedChoice
import app.memix.core.designsystem.component.Slider
import app.memix.core.model.project.CutOutCatalog
import app.memix.core.ui.scene.SceneBitmap
import kotlin.math.max
import kotlin.math.roundToInt
import app.memix.core.ui.scene.StickerPng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.editor_redo
import memix.core.ui.generated.resources.editor_undo
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.a11y_cutout_done
import memix.feature.photo_editor.generated.resources.cutout_apply
import memix.feature.photo_editor.generated.resources.cutout_cancel_a11y
import memix.feature.photo_editor.generated.resources.cutout_erase
import memix.feature.photo_editor.generated.resources.cutout_failed_body
import memix.feature.photo_editor.generated.resources.cutout_failed_title
import memix.feature.photo_editor.generated.resources.cutout_fit
import memix.feature.photo_editor.generated.resources.cutout_full
import memix.feature.photo_editor.generated.resources.cutout_memory_body
import memix.feature.photo_editor.generated.resources.cutout_memory_title
import memix.feature.photo_editor.generated.resources.cutout_none_body
import memix.feature.photo_editor.generated.resources.cutout_none_title
import memix.feature.photo_editor.generated.resources.cutout_offline_body
import memix.feature.photo_editor.generated.resources.cutout_offline_title
import memix.feature.photo_editor.generated.resources.cutout_preparing_body
import memix.feature.photo_editor.generated.resources.cutout_preparing_title
import memix.feature.photo_editor.generated.resources.cutout_privacy
import memix.feature.photo_editor.generated.resources.cutout_ready
import memix.feature.photo_editor.generated.resources.cutout_remove
import memix.feature.photo_editor.generated.resources.cutout_restore
import memix.feature.photo_editor.generated.resources.cutout_save_again
import memix.feature.photo_editor.generated.resources.cutout_save_empty
import memix.feature.photo_editor.generated.resources.cutout_save_failed
import memix.feature.photo_editor.generated.resources.cutout_save_sticker
import memix.feature.photo_editor.generated.resources.cutout_saved
import memix.feature.photo_editor.generated.resources.cutout_retry
import memix.feature.photo_editor.generated.resources.cutout_slow
import memix.feature.photo_editor.generated.resources.cutout_softness
import memix.feature.photo_editor.generated.resources.cutout_title
import memix.feature.photo_editor.generated.resources.cutout_working
import memix.feature.photo_editor.generated.resources.draw_size_fine
import memix.feature.photo_editor.generated.resources.explain_cutout_not_ready
import memix.feature.photo_editor.generated.resources.explain_cutout_nothing
import memix.feature.photo_editor.generated.resources.draw_size_heavy
import memix.feature.photo_editor.generated.resources.draw_size_medium
import memix.feature.photo_editor.generated.resources.draw_size_thick
import memix.feature.photo_editor.generated.resources.draw_size_thin
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The cut-out screen over the editor (spec docs/ux/specs/P2-07-background-removal.md): a mode like Crop, 200 ms slide up or a 120 ms fade
// with reduce motion. Back, Close and Escape discard; Apply commits.
@Composable
internal fun CutOutOverlay(state: PhotoEditorUiState, bitmaps: SceneBitmaps, onIntent: (PhotoEditorIntent) -> Unit) {
    val reduceMotion = MemixTheme.reduceMotion
    // Kept for the slide-out, once the session is gone.
    val last = remember { arrayOfNulls<CutOutSessionUi>(1) }
    state.cutOut?.let { last[0] = it }
    AnimatedVisibility(
        state.cutOut != null,
        enter = if (reduceMotion) fadeIn(tween(MemixMotion.durationPress)) else slideInVertically(tween(MemixMotion.durationSheet)) { it },
        exit = if (reduceMotion) fadeOut(tween(MemixMotion.durationPress)) else slideOutVertically(tween(MemixMotion.durationSheet)) { it },
    ) {
        val session = last[0] ?: return@AnimatedVisibility
        val path = state.layers.firstOrNull { it.id == session.layerId }?.path ?: return@AnimatedVisibility
        CutOutScreen(session, path, bitmaps, onIntent)
    }
}

@Composable
private fun CutOutScreen(session: CutOutSessionUi, path: String, bitmaps: SceneBitmaps, onIntent: (PhotoEditorIntent) -> Unit) {
    // A fresh mask (a retry that succeeded) starts the edits again; a saved cut-out opens on its strokes and softness.
    val edits = remember(session.layerId, session.cutOut?.maskPath, session.cutOut != null) {
        CutOutEdits(session.cutOut?.strokes.orEmpty(), session.cutOut?.softness ?: CutOutCatalog.DEFAULT_SOFTNESS)
    }
    val picture by produceState<SceneBitmap?>(null, path) { value = bitmaps.cutOutSource(path, PREVIEW_LONG_SIDE_PX) }
    val base = session.cutOut
    val cutPicture by produceState<ImageBitmap?>(null, picture, base?.maskPath, edits.strokes, edits.softness) {
        val source = picture
        value = if (source != null && base != null) bitmaps.cutOutPreview(source, base.copy(strokes = edits.strokes, softness = edits.softness)) else null
    }
    val scope = rememberCoroutineScope()
    var erasing by remember { mutableStateOf(true) }
    var sizeIndex by remember { mutableIntStateOf(CutOutCatalog.DEFAULT_BRUSH_INDEX) }
    // Follows Undo and Redo of a softness step as well as a new set of edits.
    var shownSoftness by remember(edits, edits.softness) { mutableFloatStateOf(edits.softness) }
    var notice by remember { mutableStateOf(false) }
    var savingSticker by remember { mutableStateOf(false) }
    LaunchedEffect(notice) {
        if (notice) {
            delay(NOTICE_MILLIS)
            notice = false
        }
    }
    val ready = session.phase == CutOutPhase.Ready
    // Nothing found keeps the brush live: the whole picture counts as kept and the user erases by hand (spec → States).
    val brushLive = (ready || session.phase == CutOutPhase.NothingFound) && base != null
    val canFinish = base != null && (ready || (session.phase == CutOutPhase.NothingFound && edits.strokes.isNotEmpty()))
    Column(Modifier.fillMaxSize().background(MemixColors.canvas).safeDrawingPadding()) {
        TopBar(edits, onIntent)
        Stage(
            session = session,
            original = picture,
            cut = cutPicture,
            erasing = erasing,
            sizeIndex = sizeIndex,
            paintingEnabled = brushLive && cutPicture != null,
            edits = edits,
            notice = notice,
            onFull = { notice = true },
            onIntent = onIntent,
            modifier = Modifier.weight(1f),
        )
        BottomArea(
            session = session,
            erasing = erasing,
            onErasing = { erasing = it },
            sizeIndex = sizeIndex,
            onSize = { sizeIndex = it },
            softness = shownSoftness,
            onSoftness = { shownSoftness = it },
            onSoftnessFinished = { edits.changeSoftness(shownSoftness) },
            ready = canFinish,
            onApply = { onIntent(PhotoEditorIntent.CutOutApply(edits.strokes, edits.softness)) },
            onSaveSticker = {
                // What the sticker is made from is fixed at the tap, so strokes painted while it is made don't change it or its key.
                val strokes = edits.strokes
                val softness = edits.softness
                val key = "${base?.maskPath}|$softness|${strokes.hashCode()}"
                if (!savingSticker) scope.launch {
                    savingSticker = true
                    // The sticker is the cut-out alone, without the layer's crop and color edits. It is cut again from a bigger decode than the
                    // screen's, so a small subject still reaches a sharp sticker (a trimmed 1280 px picture would leave a few hundred pixels).
                    val source = bitmaps.cutOutSource(path, STICKER_SOURCE_LONG_SIDE_PX)
                    val cutOut = base
                    val cut = if (source != null && cutOut != null) bitmaps.cutOutPreview(source, cutOut.copy(strokes = strokes, softness = softness)) else null
                    val result = if (cut != null) bitmaps.stickerPng(cut) else StickerPng.Failed
                    onIntent(
                        when (result) {
                            is StickerPng.Ready -> PhotoEditorIntent.CutOutSaveSticker(result.bytes, key)
                            StickerPng.Empty -> PhotoEditorIntent.CutOutSaveSticker(null, key)
                            StickerPng.Failed -> PhotoEditorIntent.CutOutSaveSticker(null, key, couldNotEncode = true)
                        },
                    )
                    savingSticker = false
                }
            },
            onInert = { onIntent(PhotoEditorIntent.CutOutInertTapped) },
            onRemove = { onIntent(PhotoEditorIntent.CutOutRemove) },
        )
    }
}

@Composable
private fun TopBar(edits: CutOutEdits, onIntent: (PhotoEditorIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(MemixSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(MemixIcons.Close, stringResource(Res.string.cutout_cancel_a11y), { onIntent(PhotoEditorIntent.CutOutClosed) })
        Text(
            stringResource(Res.string.cutout_title),
            MemixTheme.type.title,
            Modifier.weight(1f).padding(horizontal = MemixSpacing.space2).semantics { heading() },
        )
        IconButton(MemixIcons.Undo, stringResource(UiRes.string.editor_undo), { edits.undo() }, enabled = edits.canUndo)
        IconButton(MemixIcons.Redo, stringResource(UiRes.string.editor_redo), { edits.redo() }, enabled = edits.canRedo)
    }
}

@Composable
private fun Stage(
    session: CutOutSessionUi,
    original: SceneBitmap?,
    cut: ImageBitmap?,
    erasing: Boolean,
    sizeIndex: Int,
    paintingEnabled: Boolean,
    edits: CutOutEdits,
    notice: Boolean,
    onFull: () -> Unit,
    onIntent: (PhotoEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = remember { CutOutView() }
    val currentEdits by rememberUpdatedState(edits)
    val currentErasing by rememberUpdatedState(erasing)
    val currentSize by rememberUpdatedState(sizeIndex)
    val currentFull by rememberUpdatedState(onFull)
    val picture = cut ?: original?.image
    val pictureWidth = picture?.width ?: 0
    val pictureHeight = picture?.height ?: 0
    LaunchedEffect(cut) { view.settle() }
    Box(
        modifier
            .fillMaxWidth()
            .background(MemixColors.stage)
            .clip(MemixShapes.radiusMd)
            .onSizeChanged { view.stage = it }
            // Keyed on the picture's size, not the picture: a cut picture arriving mid-stroke must not restart the gesture and strand the stroke.
            .pointerInput(paintingEnabled, pictureWidth, pictureHeight) {
                if (!paintingEnabled || pictureWidth == 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    view.begin(down.position, pictureWidth, pictureHeight, currentErasing, currentSize)
                    var transforming = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            // Two fingers pinch and pan and never paint; the stroke so far is dropped (spec → Gestures).
                            if (!transforming) view.cancelStroke()
                            transforming = true
                            view.transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(), pictureWidth, pictureHeight)
                            event.changes.forEach { it.consume() }
                        } else if (!transforming && pressed == 1) {
                            event.changes.firstOrNull { it.pressed }?.let { change ->
                                view.extend(change.position, pictureWidth, pictureHeight)
                                if (change.positionChange() != Offset.Zero) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (!transforming) {
                        view.finish()?.let { done ->
                            val added = currentEdits.addPath(done.erasing, done.sizeIndex, done.xs, done.ys, done.count)
                            if (added != PathAdd.NOTHING) view.hold(done)
                            if (added != PathAdd.WHOLE) currentFull()
                        }
                    }
                }
            }
            .drawBehind { drawCheckerboard() },
    ) {
        if (picture != null) {
            Canvas(Modifier.fillMaxSize()) {
                check(view.version >= 0) // read here: the stroke grows in place, so the version says when to redraw
                drawCutOut(picture, original?.image, cut != null, view)
            }
        }
        StatusLayer(session, edits, notice, onIntent, view)
    }
}

@Composable
private fun StatusLayer(session: CutOutSessionUi, edits: CutOutEdits, notice: Boolean, onIntent: (PhotoEditorIntent) -> Unit, view: CutOutView) {
    val phase = session.phase
    // A card over the whole stage dims the picture and scrolls inside the stage, so Try again stays reachable at 200% fonts (spec → States).
    // Nothing found is a small card at the top instead: the picture stays whole and the brush stays live under it.
    if (phase is CutOutPhase.Preparing || phase is CutOutPhase.Failed) Box(Modifier.fillMaxSize().background(MemixColors.scrim))
    Box(Modifier.fillMaxSize().padding(MemixSpacing.space4), contentAlignment = if (phase == CutOutPhase.NothingFound) Alignment.TopCenter else Alignment.Center) {
        // The card is as tall as its words, up to the stage; past that it scrolls (the box around it takes no touches).
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        when (phase) {
            CutOutPhase.Ready -> Unit
            CutOutPhase.Working -> WorkingLine()
            is CutOutPhase.Preparing -> StatusCard(
                title = stringResource(Res.string.cutout_preparing_title),
                body = stringResource(Res.string.cutout_preparing_body),
                progress = phase.fraction,
                extra = if (phase.slow) stringResource(Res.string.cutout_slow) else null,
                retry = phase.slow,
                onRetry = { onIntent(PhotoEditorIntent.CutOutRetry) },
            )
            CutOutPhase.NothingFound -> StatusCard(stringResource(Res.string.cutout_none_title), stringResource(Res.string.cutout_none_body), retry = true) { onIntent(PhotoEditorIntent.CutOutRetry) }
            is CutOutPhase.Failed -> {
                val (title, body) = when (phase.problem) {
                    CutOutProblem.OFFLINE -> Res.string.cutout_offline_title to Res.string.cutout_offline_body
                    CutOutProblem.MEMORY -> Res.string.cutout_memory_title to Res.string.cutout_memory_body
                    CutOutProblem.SLOW, CutOutProblem.UNAVAILABLE -> Res.string.cutout_failed_title to Res.string.cutout_failed_body
                }
                StatusCard(stringResource(title), stringResource(body), extra = if (phase.problem == CutOutProblem.SLOW) stringResource(Res.string.cutout_slow) else null, retry = true) {
                    onIntent(PhotoEditorIntent.CutOutRetry)
                }
            }
        }
        }
    }
    if (session.phase == CutOutPhase.Ready) {
        Box(Modifier.fillMaxSize().padding(MemixSpacing.space3), contentAlignment = Alignment.TopCenter) {
            val line = when {
                notice -> stringResource(Res.string.cutout_full)
                session.notice == CutOutNotice.SAVED -> stringResource(Res.string.cutout_saved)
                session.notice == CutOutNotice.EMPTY -> stringResource(Res.string.cutout_save_empty)
                session.notice == CutOutNotice.ALREADY_SAVED -> stringResource(Res.string.cutout_save_again)
                session.notice == CutOutNotice.FAILED -> stringResource(Res.string.cutout_save_failed)
                session.notice == CutOutNotice.NOT_READY -> stringResource(Res.string.explain_cutout_not_ready)
                session.notice == CutOutNotice.NOTHING_TO_APPLY -> stringResource(Res.string.explain_cutout_nothing)
                else -> null
            }
            // A save answer is announced politely, then the quiet "Background removed" comes back when it clears (3 s).
            if (line != null) Pill(line, announce = line) else Pill(stringResource(Res.string.cutout_ready), announce = stringResource(Res.string.a11y_cutout_done))
        }
        if (view.zoom > 1.001f) {
            Box(Modifier.fillMaxSize().padding(MemixSpacing.space3), contentAlignment = Alignment.BottomEnd) {
                Button(stringResource(Res.string.cutout_fit), { view.reset() }, variant = ButtonVariant.Quiet)
            }
        }
    }
}

// Nothing for the first 300 ms (the screen holds the photo), then one static line: no spinner (spec → States).
@Composable
private fun WorkingLine() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(WORKING_LINE_DELAY_MILLIS)
        visible = true
    }
    if (visible) Pill(stringResource(Res.string.cutout_working))
}

@Composable
private fun Pill(text: String, announce: String? = null) {
    Text(
        text,
        MemixTheme.type.label,
        Modifier
            .clip(MemixShapes.radiusFull)
            .background(MemixColors.surfaceRaised)
            .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2)
            .semantics {
                if (announce != null) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = announce
                }
            },
    )
}

@Composable
private fun StatusCard(title: String, body: String, extra: String? = null, progress: Float? = null, retry: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.clip(MemixShapes.radiusLg).background(MemixColors.surfaceRaised).padding(MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, MemixTheme.type.title, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(body, MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
        if (progress != null) ProgressBar(progress, "${(progress * PERCENT).roundToInt()}%")
        if (extra != null) Text(extra, MemixTheme.type.body, textAlign = TextAlign.Center)
        if (retry) Button(stringResource(Res.string.cutout_retry), onRetry, variant = ButtonVariant.Primary)
    }
}

@Composable
private fun BottomArea(
    session: CutOutSessionUi,
    erasing: Boolean,
    onErasing: (Boolean) -> Unit,
    sizeIndex: Int,
    onSize: (Int) -> Unit,
    softness: Float,
    onSoftness: (Float) -> Unit,
    onSoftnessFinished: () -> Unit,
    ready: Boolean,
    onApply: () -> Unit,
    onSaveSticker: () -> Unit,
    onInert: () -> Unit,
    onRemove: () -> Unit,
) {
    val hairline = MemixColors.hairline
    val hairlineWidth = MemixStroke.strokeHairline
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = MemixSize.touchTarget * BOTTOM_AREA_MAX_ROWS)
            .background(MemixColors.surface)
            .drawBehind { drawLine(hairline, Offset.Zero, Offset(size.width, 0f), hairlineWidth.toPx()) },
    ) {
        // Only the tools scroll; the actions and the privacy line stay pinned below them (spec → The cut-out screen).
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = MemixSpacing.space4, top = MemixSpacing.space4, end = MemixSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        ) {
            SegmentedChoice(
                listOf(Segment(stringResource(Res.string.cutout_erase)), Segment(stringResource(Res.string.cutout_restore))),
                selectedIndex = if (erasing) 0 else 1,
                onSelect = { onErasing(it == 0) },
                groupName = stringResource(Res.string.cutout_title),
            )
            BrushSizes(sizeIndex, onSize)
            Slider(
                label = stringResource(Res.string.cutout_softness),
                value = softness,
                onValueChange = onSoftness,
                valueText = "${(softness * PERCENT).roundToInt()}%",
                defaultValue = CutOutCatalog.DEFAULT_SOFTNESS,
                step = SOFTNESS_STEP,
                onValueChangeFinished = onSoftnessFinished,
                snapValue = 0f,
                snapDistance = SOFTNESS_SNAP,
            )
            if (session.hadCutOut) Button(stringResource(Res.string.cutout_remove), onRemove, variant = ButtonVariant.Quiet)
        }
        Column(Modifier.padding(MemixSpacing.space4), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
                Button(stringResource(Res.string.cutout_save_sticker), { if (ready) onSaveSticker() else onInert() }, Modifier.weight(1f), variant = ButtonVariant.Secondary)
                Button(stringResource(Res.string.cutout_apply), { if (ready) onApply() else onInert() }, Modifier.weight(1f), variant = ButtonVariant.Primary)
            }
            Text(stringResource(Res.string.cutout_privacy), MemixTheme.type.caption, color = MemixColors.textSecondary)
        }
    }
}

@Composable
private fun BrushSizes(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val names: List<StringResource> = listOf(Res.string.draw_size_thin, Res.string.draw_size_fine, Res.string.draw_size_medium, Res.string.draw_size_thick, Res.string.draw_size_heavy)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
    ) {
        names.forEachIndexed { index, name ->
            OptionTile(stringResource(name), selected = index == selectedIndex, onClick = { onSelect(index) }, layout = OptionTileLayout.STACKED) { tint ->
                SizeDot(index, tint)
            }
        }
    }
}

// The checkerboard: cells in `canvas` and `surface-raised` (content on the picture, not chrome; spec → The cut-out screen).
private fun DrawScope.drawCheckerboard() {
    val cell = CHECKER_CELL_DP.dp.toPx()
    var row = 0
    var y = 0f
    while (y < size.height) {
        var column = 0
        var x = 0f
        while (x < size.width) {
            drawRect(if ((row + column) % 2 == 0) MemixColors.canvas else MemixColors.surfaceRaised, Offset(x, y), Size(cell, cell))
            x += cell
            column++
        }
        y += cell
        row++
    }
}

// The cut picture (or the whole one while there is none), under the zoom and pan, with the strokes still being made shown at once: an
// erase clears what it covers in the picture's own layer, a restore shows the whole picture through the stroke.
private fun DrawScope.drawCutOut(picture: ImageBitmap, original: ImageBitmap?, isCut: Boolean, view: CutOutView) {
    val rect = view.pictureRect(picture.width, picture.height) ?: return
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawImage(picture, srcSize = IntSize(picture.width, picture.height), dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()), dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()), filterQuality = FilterQuality.Medium)
        if (isCut) {
            view.held.forEach { drawPendingStroke(canvas, it, rect, original) }
            view.live?.let { drawPendingStroke(canvas, it, rect, original) }
        }
        canvas.restore()
    }
}

private fun DrawScope.drawPendingStroke(canvas: GraphicsCanvas, stroke: CutOutView.Live, rect: Rect, original: ImageBitmap?) {
    val diameter = CutOutCatalog.BRUSH_SIZES[stroke.sizeIndex] * max(rect.width, rect.height)
    if (stroke.erasing) {
        drawLive(stroke, rect, diameter, Color.Black, BlendMode.Clear)
    } else if (original != null) {
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawLive(stroke, rect, diameter, Color.White, BlendMode.SrcOver)
        drawImage(original, srcSize = IntSize(original.width, original.height), dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()), dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()), blendMode = BlendMode.SrcIn)
        canvas.restore()
    }
}

private fun DrawScope.drawLive(live: CutOutView.Live, rect: Rect, diameter: Float, color: Color, blendMode: BlendMode) {
    if (live.count == 0) return
    val first = Offset(rect.center.x + live.xs[0] * max(rect.width, rect.height), rect.center.y + live.ys[0] * max(rect.width, rect.height))
    if (live.count == 1) {
        drawCircle(color, diameter / 2f, first, blendMode = blendMode)
        return
    }
    val path = Path().apply {
        moveTo(first.x, first.y)
        for (i in 1 until live.count) lineTo(rect.center.x + live.xs[i] * max(rect.width, rect.height), rect.center.y + live.ys[i] * max(rect.width, rect.height))
    }
    drawPath(path, color, style = DrawStroke(diameter, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blendMode)
}

private const val PREVIEW_LONG_SIDE_PX = 1280
private const val STICKER_SOURCE_LONG_SIDE_PX = 2048
private const val NOTICE_MILLIS = 3000L
private const val WORKING_LINE_DELAY_MILLIS = 300L
private const val PERCENT = 100f
private const val SOFTNESS_STEP = 0.1f
private const val SOFTNESS_SNAP = 0.03f
private const val CHECKER_CELL_DP = 12
private const val BOTTOM_AREA_MAX_ROWS = 7
