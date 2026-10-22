package app.memix.feature.videoeditor.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.Segment
import app.memix.core.designsystem.component.SegmentedChoice
import app.memix.core.designsystem.component.Sheet
import app.memix.core.domain.video.EXPORT_FRAME_RATES
import app.memix.core.domain.video.ExportResolution
import app.memix.core.ui.SizeRounding
import app.memix.core.ui.fileSizeText
import app.memix.core.ui.shortDurationText
import app.memix.feature.videoeditor.ExportSheetUi
import app.memix.feature.videoeditor.VideoEditorIntent
import memix.feature.video_editor.generated.resources.Res
import memix.feature.video_editor.generated.resources.export_estimate
import memix.feature.video_editor.generated.resources.export_facts_format
import memix.feature.video_editor.generated.resources.export_fps
import memix.feature.video_editor.generated.resources.export_fps_a11y
import memix.feature.video_editor.generated.resources.export_frame_rate
import memix.feature.video_editor.generated.resources.export_lowered_notice
import memix.feature.video_editor.generated.resources.export_missing_media
import memix.feature.video_editor.generated.resources.export_no_space
import memix.feature.video_editor.generated.resources.export_resolution
import memix.feature.video_editor.generated.resources.export_start
import memix.feature.video_editor.generated.resources.export_title
import memix.feature.video_editor.generated.resources.import_free_up_space
import org.jetbrains.compose.resources.stringResource

/**
 * The Export sheet (spec docs/ux/specs/P1-12-export.md → Export sheet): the "EXPORT" hero, Resolution and Frame rate,
 * the facts line, the notices that apply, and Export video. [sheet] null keeps it closed. [onFreeUpSpace] opens the
 * phone's storage screen asking for the given bytes; null hides Free up space (no such screen on this phone).
 */
@Composable
internal fun ExportSheet(
    sheet: ExportSheetUi?,
    missingMedia: Boolean,
    onIntent: (VideoEditorIntent) -> Unit,
    onFreeUpSpace: ((Long) -> Unit)?,
) {
    Sheet(
        visible = sheet != null,
        onDismiss = { onIntent(VideoEditorIntent.CloseExportSheet) },
        title = stringResource(Res.string.export_title),
        heroTitle = true,
    ) {
        if (sheet == null) return@Sheet
        // The middle scrolls at 200% font; the hero and Export video stay put (Sheet rule).
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4),
        ) {
            Settings(sheet, onIntent)
            // P1-13 places the watermark row here.
            FactsLine(sheet)
            Notices(sheet, missingMedia, onFreeUpSpace)
        }
        Button(
            stringResource(Res.string.export_start),
            { onIntent(VideoEditorIntent.ExportVideo) },
            Modifier.fillMaxWidth(),
            ButtonVariant.Primary,
        )
    }
}

@Composable
private fun Settings(sheet: ExportSheetUi, onIntent: (VideoEditorIntent) -> Unit) {
    val resolutionHeading = stringResource(Res.string.export_resolution)
    Heading(resolutionHeading)
    SegmentedChoice(
        ExportResolution.entries.map { Segment(it.label) },
        ExportResolution.entries.indexOf(sheet.choice.resolution),
        { onIntent(VideoEditorIntent.ExportResolutionTapped(it)) },
        resolutionHeading,
        Modifier.fillMaxWidth(),
    )
    val frameRateHeading = stringResource(Res.string.export_frame_rate)
    Heading(frameRateHeading)
    SegmentedChoice(
        EXPORT_FRAME_RATES.map { Segment(stringResource(Res.string.export_fps, it), stringResource(Res.string.export_fps_a11y, it)) },
        EXPORT_FRAME_RATES.indexOf(sheet.choice.frameRate),
        { onIntent(VideoEditorIntent.ExportFrameRateTapped(it)) },
        frameRateHeading,
        Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, MemixTheme.type.label, Modifier.semantics { heading() }, color = MemixColors.textSecondary)
}

// "MP4 · H.264 · AAC" and "Up to 18 MB · about 20 s", side by side, wrapping to two lines when they don't fit. One node.
@Composable
private fun FactsLine(sheet: ExportSheetUi) {
    val format = stringResource(Res.string.export_facts_format, sheet.codec.label)
    val estimate = sheet.estimate?.let {
        stringResource(Res.string.export_estimate, fileSizeText(it.maxSizeBytes, SizeRounding.UP), shortDurationText(it.seconds))
    }
    FlowRow(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = listOfNotNull(format, estimate).joinToString(". ") },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(format, MemixTheme.type.timecode, color = MemixColors.textSecondary)
        if (estimate != null) Text(estimate, MemixTheme.type.timecode, color = MemixColors.textSecondary)
    }
}

// In the spec's order, above Export video; each fades in and out in 120 ms (spec → Motion).
@Composable
private fun Notices(sheet: ExportSheetUi, missingMedia: Boolean, onFreeUpSpace: ((Long) -> Unit)?) {
    Notice(sheet.loweredTo != null) {
        val lowered = sheet.loweredTo ?: sheet.choice
        NoticeText(stringResource(Res.string.export_lowered_notice, lowered.resolution.label, stringResource(Res.string.export_fps, lowered.frameRate)))
    }
    Notice(sheet.spaceShortBytes != null) {
        val shortBytes = sheet.spaceShortBytes ?: 0L
        Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
            NoticeText(stringResource(Res.string.export_no_space, fileSizeText(shortBytes, SizeRounding.UP), sheet.choice.resolution.label))
            if (onFreeUpSpace != null) {
                Button(stringResource(Res.string.import_free_up_space), { onFreeUpSpace(shortBytes) }, variant = ButtonVariant.Quiet)
            }
        }
    }
    Notice(missingMedia) { NoticeText(stringResource(Res.string.export_missing_media)) }
}

@Composable
private fun Notice(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(MemixMotion.durationPress)), exit = fadeOut(tween(MemixMotion.durationPress))) { content() }
}

@Composable
private fun NoticeText(text: String) {
    Text(text, MemixTheme.type.body, color = MemixColors.textSecondary)
}
