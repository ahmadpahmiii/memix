package app.memix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.focusRing

/** A 9:16 template frame. Without a [thumbnail] the frame stays an empty `surface-raised` block. */
@Composable
fun TemplateCard(
    name: String,
    typeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: (@Composable BoxScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier.clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)) {
        Box(
            Modifier
                .focusRing(interactionSource, MemixShapes.radiusLg)
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(MemixShapes.radiusLg)
                .background(MemixColors.surfaceRaised),
        ) {
            thumbnail?.invoke(this)
            Text(
                typeLabel,
                MemixTheme.type.caption,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(MemixSpacing.space2)
                    .clip(MemixShapes.radiusSm)
                    .background(MemixColors.scrim)
                    .padding(horizontal = MemixSpacing.space2, vertical = MemixSpacing.space1),
                maxLines = 1,
            )
        }
        Text(name, MemixTheme.type.label, Modifier.padding(top = MemixSpacing.space2), maxLines = 2)
    }
}
