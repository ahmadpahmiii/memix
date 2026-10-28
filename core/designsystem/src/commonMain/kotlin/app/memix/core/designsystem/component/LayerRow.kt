package app.memix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor

/** A faded or hidden layer's thumbnail never goes below this, so it stays recognizable (spec P2-01 → Layers panel). */
private const val MIN_THUMBNAIL_ALPHA = 0.4f

/**
 * One layer in the photo editor's Layers panel (`design/system/components/LayerRow/README.md`): its picture at
 * `layer-thumb-size`, its [name], a [meta] line only when something is worth saying, and the lock and visibility buttons.
 * `surface-raised`, at least 56 dp tall; a [selected] or [lifted] row has the white `stroke-selection` ring inside its
 * edge (`surface-raised` can't mark selection: 1.4:1). A [hidden] row's thumbnail and name are dimmed; a faded layer's
 * thumbnail is drawn at its [opacity] but never below 40%; a [missing] layer shows a `photo` icon on `surface` instead of
 * [thumbnail].
 *
 * A tap on the row is [onClick]; the two buttons toggle only their own state. The caller says what a screen reader hears
 * in [semantics]: one node per row, with the row's actions, so the buttons are no extra stops.
 */
@Composable
fun LayerRow(
    name: String,
    meta: String?,
    selected: Boolean,
    lifted: Boolean,
    locked: Boolean,
    hidden: Boolean,
    opacity: Float,
    missing: Boolean,
    lockDescription: String,
    visibilityDescription: String,
    onClick: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleVisibility: () -> Unit,
    semantics: SemanticsPropertyReceiver.() -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val container = pressedColor(interactionSource, MemixColors.surfaceRaised, MemixColors.hairline)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MemixSize.layerThumbSize + MemixSpacing.space2 * 2)
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .clip(MemixShapes.radiusMd)
            .drawBehind {
                drawRect(container.value)
                if (selected || lifted) {
                    // Half of a double-width stroke on the edge is clipped away: the ring sits inside the row's edge.
                    val ring = MemixStroke.strokeSelection.toPx()
                    drawOutline(MemixShapes.radiusMd.createOutline(size, layoutDirection, this), MemixColors.selection, style = Stroke(ring * 2))
                }
            }
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics(semantics)
            .padding(start = MemixSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        Thumbnail(hidden, opacity, missing, thumbnail)
        Column(Modifier.weight(1f).padding(vertical = MemixSpacing.space2)) {
            Text(
                name,
                MemixTheme.type.bodyStrong,
                color = if (hidden) MemixColors.textSecondary else MemixColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) Text(meta, MemixTheme.type.label, color = MemixColors.textSecondary)
        }
        IconButton(
            if (locked) MemixIcons.Lock else MemixIcons.Unlock,
            lockDescription,
            onToggleLock,
            onSurfaceRaised = true,
            tint = if (locked) MemixColors.text else MemixColors.textMuted,
        )
        IconButton(
            if (hidden) MemixIcons.EyeOff else MemixIcons.Eye,
            visibilityDescription,
            onToggleVisibility,
            onSurfaceRaised = true,
            tint = if (hidden) MemixColors.text else MemixColors.textSecondary,
        )
    }
}

@Composable
private fun Thumbnail(hidden: Boolean, opacity: Float, missing: Boolean, content: @Composable () -> Unit) {
    val alpha = when {
        hidden -> MIN_THUMBNAIL_ALPHA
        opacity < 1f -> opacity.coerceAtLeast(MIN_THUMBNAIL_ALPHA)
        else -> 1f
    }
    Box(
        Modifier.size(MemixSize.layerThumbSize).clip(MemixShapes.radiusSm).alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        if (missing) {
            Box(Modifier.size(MemixSize.layerThumbSize).background(MemixColors.surface), contentAlignment = Alignment.Center) {
                Icon(MemixIcons.Photo, contentDescription = null, tint = MemixColors.textMuted)
            }
        } else {
            content()
        }
    }
}
