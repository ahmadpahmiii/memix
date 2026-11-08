package app.memix.feature.photoeditor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.Segment
import app.memix.core.designsystem.component.SegmentedChoice
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.Slider
import app.memix.core.domain.photo.PhotoExportAction
import app.memix.core.domain.video.ExportError
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PhotoExportFormat
import app.memix.core.ui.SizeRounding
import app.memix.core.ui.export.ExportNotice
import app.memix.core.ui.export.ExportNoticeText
import app.memix.core.ui.export.ExportSheetHeading
import app.memix.core.ui.export.ExportWatermarkRow
import app.memix.core.ui.fileSizeText
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.export_title
import memix.core.ui.generated.resources.import_free_up_space
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.photo_export_copy
import memix.feature.photo_editor.generated.resources.photo_export_copying
import memix.feature.photo_editor.generated.resources.photo_export_estimate
import memix.feature.photo_editor.generated.resources.photo_export_fail_media
import memix.feature.photo_editor.generated.resources.photo_export_fail_other
import memix.feature.photo_editor.generated.resources.photo_export_fail_size
import memix.feature.photo_editor.generated.resources.photo_export_fail_space
import memix.feature.photo_editor.generated.resources.photo_export_facts
import memix.feature.photo_editor.generated.resources.photo_export_facts_a11y
import memix.feature.photo_editor.generated.resources.photo_export_format
import memix.feature.photo_editor.generated.resources.photo_export_missing_media
import memix.feature.photo_editor.generated.resources.photo_export_no_space
import memix.feature.photo_editor.generated.resources.photo_export_png_hint
import memix.feature.photo_editor.generated.resources.photo_export_quality
import memix.feature.photo_editor.generated.resources.photo_export_save
import memix.feature.photo_editor.generated.resources.photo_export_saving
import memix.feature.photo_editor.generated.resources.photo_export_size
import memix.feature.photo_editor.generated.resources.photo_export_size_a11y
import memix.feature.photo_editor.generated.resources.photo_export_watermark_body
import org.jetbrains.compose.resources.stringResource

/**
 * The photo Export sheet (spec docs/ux/specs/P2-08-photo-export.md → Export sheet): the "EXPORT" hero, Format, the quality slot,
 * Size, the watermark row, the facts line, the notices that apply, and Copy with Save to gallery. [sheet] null keeps it closed.
 * [onFreeUpSpace] opens the phone's storage screen asking for the given bytes; null hides Free up space.
 */
@Composable
internal fun PhotoExportSheet(
    sheet: PhotoExportSheetUi?,
    onIntent: (PhotoExportIntent) -> Unit,
    onFreeUpSpace: ((Long) -> Unit)?,
) {
    // The last content stays while the sheet slides away, not an empty panel (spec → While it closes).
    var lastSheet by remember { mutableStateOf(sheet) }
    if (sheet != null) lastSheet = sheet
    Sheet(visible = sheet != null, onDismiss = { onIntent(PhotoExportIntent.Close) }, title = stringResource(UiRes.string.export_title), heroTitle = true) {
        val shown = lastSheet ?: return@Sheet
        // The middle scrolls at 200% font; the hero and the buttons stay put (Sheet rule).
        val scroll = rememberScrollState()
        ScrollToNotice(scroll, shown)
        Column(Modifier.weight(1f, fill = false).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4)) {
            FormatAndQuality(shown, onIntent)
            SizeChoice(shown, onIntent)
            ExportWatermarkRow(stringResource(Res.string.photo_export_watermark_body))
            // One Column without spacing: a hidden notice takes no room, a shown one brings its own gap (spec → Spacing).
            Column {
                FactsLine(shown)
                Notices(shown, onFreeUpSpace)
            }
        }
        Buttons(shown, onIntent)
    }
}

// While the picture is made the controls keep their look (no dimming token for a two-second state) but TalkBack says why nothing changes.
private fun Modifier.disabledWhile(working: Boolean): Modifier = if (working) semantics { disabled() } else this

// A notice that appears below the fold (a short phone, a large font) scrolls into view, so "not enough space" is never hidden.
@Composable
private fun ScrollToNotice(scroll: ScrollState, sheet: PhotoExportSheetUi) {
    val reduceMotion = MemixTheme.reduceMotion
    LaunchedEffect(sheet.saveShortBytes != null, sheet.failure) {
        if (sheet.saveShortBytes == null && sheet.failure == null) return@LaunchedEffect
        // The notice fades in over 120 ms and the scroll range grows with it: wait for it by frame time.
        val start = withFrameMillis { it }
        while (withFrameMillis { it } - start < NOTICE_SETTLE_MILLIS) Unit
        if (reduceMotion) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue)
    }
}

@Composable
private fun FormatAndQuality(sheet: PhotoExportSheetUi, onIntent: (PhotoExportIntent) -> Unit) {
    val formatHeading = stringResource(Res.string.photo_export_format)
    ExportSheetHeading(formatHeading)
    SegmentedChoice(
        PhotoExportFormat.entries.map { Segment(it.label) },
        PhotoExportFormat.entries.indexOf(sheet.settings.format),
        { onIntent(PhotoExportIntent.Format(it)) },
        formatHeading,
        Modifier.fillMaxWidth().disabledWhile(sheet.working != null),
    )
    // Same height for both, so the sheet doesn't jump when the format changes (spec → Quality slot).
    FixedHeightSlot(showFirst = sheet.settings.format == PhotoExportFormat.JPG, first = { QualitySlider(sheet, onIntent) }, second = { PngHint() })
}

@Composable
private fun QualitySlider(sheet: PhotoExportSheetUi, onIntent: (PhotoExportIntent) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val quality = sheet.settings.quality
    Slider(
        stringResource(Res.string.photo_export_quality),
        quality.toFloat(),
        { value ->
            val stepped = PhotoExportCatalog.snappedQuality(value.roundToInt())
            // One tick for each step the thumb passes, not for each pixel of the drag.
            if (stepped != quality) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onIntent(PhotoExportIntent.Quality(stepped))
            }
        },
        quality.toString(),
        Modifier.fillMaxWidth().disabledWhile(sheet.working != null),
        valueRange = PhotoExportCatalog.QUALITY_MIN.toFloat()..PhotoExportCatalog.QUALITY_MAX.toFloat(),
        defaultValue = PhotoExportCatalog.DEFAULT_QUALITY.toFloat(),
        step = PhotoExportCatalog.QUALITY_STEP.toFloat(),
        onValueChangeFinished = { onIntent(PhotoExportIntent.QualityReleased) },
    )
}

@Composable
private fun PngHint() {
    Text(stringResource(Res.string.photo_export_png_hint), MemixTheme.type.label, color = MemixColors.textSecondary)
}

// Both children are measured, only the chosen one is composed in the layout and placed: the slot is as tall as the taller.
@Composable
private fun FixedHeightSlot(showFirst: Boolean, first: @Composable () -> Unit, second: @Composable () -> Unit, modifier: Modifier = Modifier) {
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val firstHeight = subcompose(SlotId.FIRST_MEASURE, first).maxOfOrNull { it.measure(loose).height } ?: 0
        val secondHeight = subcompose(SlotId.SECOND_MEASURE, second).maxOfOrNull { it.measure(loose).height } ?: 0
        val height = maxOf(firstHeight, secondHeight)
        val shown = subcompose(if (showFirst) SlotId.FIRST else SlotId.SECOND, if (showFirst) first else second)
        val placeables = shown.map { it.measure(Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)) }
        layout(constraints.maxWidth, height) {
            placeables.forEach { it.place(0, (height - it.height) / 2) }
        }
    }
}

private enum class SlotId { FIRST_MEASURE, SECOND_MEASURE, FIRST, SECOND }

@Composable
private fun SizeChoice(sheet: PhotoExportSheetUi, onIntent: (PhotoExportIntent) -> Unit) {
    val sizeHeading = stringResource(Res.string.photo_export_size)
    ExportSheetHeading(sizeHeading)
    SegmentedChoice(
        PhotoExportCatalog.SHORT_SIDES_PX.map { Segment("$it $PIXEL_UNIT", stringResource(Res.string.photo_export_size_a11y, it)) },
        PhotoExportCatalog.SHORT_SIDES_PX.indexOf(sheet.settings.shortSidePx),
        { onIntent(PhotoExportIntent.Size(it)) },
        sizeHeading,
        Modifier.fillMaxWidth().disabledWhile(sheet.working != null),
    )
}

// "JPG · 1080 × 1350" and "About 1.2 MB", side by side, wrapping to two lines when they don't fit. One node.
@Composable
private fun FactsLine(sheet: PhotoExportSheetUi) {
    val format = sheet.settings.format.label
    val output = sheet.output
    val size = fileSizeText(sheet.estimateBytes)
    val facts = stringResource(Res.string.photo_export_facts, format, "${output.widthPx} $TIMES_SIGN ${output.heightPx}")
    val estimate = stringResource(Res.string.photo_export_estimate, size)
    val spoken = stringResource(Res.string.photo_export_facts_a11y, format, output.widthPx, output.heightPx, size)
    FlowRow(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(facts, MemixTheme.type.timecode, color = MemixColors.textSecondary)
        Text(estimate, MemixTheme.type.timecode, color = MemixColors.textSecondary)
    }
}

// In the spec's order, above the buttons: not enough space, a missing picture, the last failure.
@Composable
private fun Notices(sheet: PhotoExportSheetUi, onFreeUpSpace: ((Long) -> Unit)?) {
    ExportNotice(sheet.saveShortBytes != null) {
        Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
            ExportNoticeText(stringResource(Res.string.photo_export_no_space, fileSizeText(sheet.saveShortBytes ?: 0L, SizeRounding.UP)))
            FreeUpSpaceButton(sheet, onFreeUpSpace)
        }
    }
    ExportNotice(sheet.missingPicture) { ExportNoticeText(stringResource(Res.string.photo_export_missing_media)) }
    ExportNotice(sheet.failure != null) {
        val failure = sheet.failure
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
        ) {
            if (failure != null) ExportNoticeText(stringResource(failure.message()))
            if (failure == ExportError.STORAGE_FULL) FreeUpSpaceButton(sheet, onFreeUpSpace)
        }
    }
}

// The storage screen is asked for the total the picture needs, not the amount missing (spec → Free space).
@Composable
private fun FreeUpSpaceButton(sheet: PhotoExportSheetUi, onFreeUpSpace: ((Long) -> Unit)?) {
    if (onFreeUpSpace == null) return
    Button(stringResource(UiRes.string.import_free_up_space), { onFreeUpSpace(sheet.saveNeededBytes) }, variant = ButtonVariant.Quiet)
}

private fun ExportError.message() = when (this) {
    ExportError.STORAGE_FULL -> Res.string.photo_export_fail_space
    ExportError.ENCODER, ExportError.DECODER -> Res.string.photo_export_fail_size
    ExportError.MISSING_MEDIA -> Res.string.photo_export_fail_media
    ExportError.SYSTEM_STOPPED, ExportError.PROCESS_DEATH, ExportError.UNKNOWN -> Res.string.photo_export_fail_other
}

/**
 * Copy (one third) and Save to gallery (two thirds), `space-3` apart; stacked, Copy above Save, when either label doesn't fit its
 * share (200% font), so the primary is lowest and nearest the thumb. While working, the tapped one reads "Saving…" or
 * "Copying…" and both are inert.
 */
@Composable
private fun Buttons(sheet: PhotoExportSheetUi, onIntent: (PhotoExportIntent) -> Unit) {
    val working = sheet.working
    val gap = MemixSpacing.space3
    val idle = working == null
    Layout(
        content = {
            Button(
                stringResource(if (working == PhotoExportAction.COPY) Res.string.photo_export_copying else Res.string.photo_export_copy),
                { onIntent(PhotoExportIntent.Copy) },
                busy = !idle,
            )
            Button(
                stringResource(if (working == PhotoExportAction.SAVE) Res.string.photo_export_saving else Res.string.photo_export_save),
                { onIntent(PhotoExportIntent.Save) },
                variant = ButtonVariant.Primary,
                busy = !idle,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val third = (width - gapPx) / 3
        val shares = listOf(third, width - gapPx - third)
        val sideBySide = measurables.indices.all { measurables[it].maxIntrinsicWidth(Constraints.Infinity) <= shares[it] }
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixedWidth(if (sideBySide) shares[index] else width))
        }
        val height = if (sideBySide) placeables.maxOf { it.height } else placeables.sumOf { it.height } + gapPx
        layout(width, height) {
            if (sideBySide) {
                placeables[0].place(0, 0)
                placeables[1].place(shares[0] + gapPx, 0)
            } else {
                placeables[0].place(0, 0)
                placeables[1].place(0, placeables[0].height + gapPx)
            }
        }
    }
}

private const val NOTICE_SETTLE_MILLIS = 160L

// Constants of the picture, never translated (spec → Copy).
private const val PIXEL_UNIT = "px"
private const val TIMES_SIGN = "×"
