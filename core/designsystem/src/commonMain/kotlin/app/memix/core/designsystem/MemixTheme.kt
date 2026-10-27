package app.memix.core.designsystem

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

object MemixTheme {
    val colors = MemixColors
    val spacing = MemixSpacing
    val shapes = MemixShapes
    val stroke = MemixStroke
    val size = MemixSize
    val elevation = MemixElevation
    val motion = MemixMotion

    val type: MemixType
        @Composable @ReadOnlyComposable get() = LocalMemixType.current

    /** True when the user asked the system to reduce motion; movement then becomes a short fade. */
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

@Composable
fun MemixTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMemixType provides memixType(),
        LocalReduceMotion provides isReduceMotionEnabled(),
        // Components draw their own pressed colors, so the default gray press overlay is off.
        LocalIndication provides NoIndication,
        LocalTextSelectionColors provides TextSelectionColors(MemixColors.primary, MemixColors.primarySubtle),
        content = content,
    )
}

private val LocalMemixType = staticCompositionLocalOf<MemixType> { error("Wrap the UI in MemixTheme { }") }
private val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
internal expect fun isReduceMotionEnabled(): Boolean

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0
}
