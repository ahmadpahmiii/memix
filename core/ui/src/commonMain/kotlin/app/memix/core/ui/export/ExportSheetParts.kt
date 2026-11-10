package app.memix.core.ui.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.export_watermark_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Memix watermark" with where it goes ([body]), in one raised container, in both editors' Export sheets (spec P1-13 → Export
 * sheet row). Information, not a control: no press state, one merged, non-clickable node that TalkBack reads title then line
 * with each language's own pauses. P5-02 adds its rewarded-ad Remove here.
 */
@Composable
fun ExportWatermarkRow(body: String, modifier: Modifier = Modifier) {
    val title = stringResource(Res.string.export_watermark_title)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .padding(MemixSpacing.space3)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
    ) {
        Text(title, MemixTheme.type.bodyStrong)
        Text(body, MemixTheme.type.label, color = MemixColors.textSecondary)
    }
}

/** A setting's heading in an Export sheet: `label`, `text-secondary`, announced as a heading. */
@Composable
fun ExportSheetHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, MemixTheme.type.label, modifier.semantics { heading() }, color = MemixColors.textSecondary)
}

/**
 * A notice above an Export sheet's buttons: fades in and out in 120 ms and carries its own `space-4` gap above, so a hidden notice
 * takes no room (put them in one Column without spacing, spec P1-12 → Spacing).
 */
@Composable
fun ExportNotice(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(MemixMotion.durationPress)), exit = fadeOut(tween(MemixMotion.durationPress))) {
        Box(Modifier.padding(top = MemixSpacing.space4)) { content() }
    }
}

/** The words of an [ExportNotice]. */
@Composable
fun ExportNoticeText(text: String) {
    Text(text, MemixTheme.type.body, color = MemixColors.textSecondary)
}
