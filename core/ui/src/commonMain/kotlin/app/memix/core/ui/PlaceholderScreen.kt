package app.memix.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.component.CloseButton

/** A screen whose feature isn't built yet: it says so and offers the way back, nothing that pretends to work. */
@Composable
fun PlaceholderScreen(title: String, body: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MemixColors.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
        CloseButton(onClose, Modifier.padding(start = MemixSpacing.space2, top = MemixSpacing.space2))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            EmptyState(title, body)
        }
    }
}
