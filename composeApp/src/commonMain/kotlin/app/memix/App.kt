package app.memix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

// Placeholder root; MemixTheme (P0-04) and the navigation host (P0-03) replace it.
@Composable
fun App() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText("Memix", style = TextStyle(color = Color.White)) // TODO(P0-04): theme token
    }
}
