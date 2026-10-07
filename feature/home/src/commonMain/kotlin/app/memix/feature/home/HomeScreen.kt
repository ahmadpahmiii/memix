package app.memix.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.memix.core.model.Region

@Composable
fun HomeScreen(state: HomeUiState, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val regionLabel = when (val region = state.region) {
        Region.Global -> "Global" // TODO(P0-09): string resource
        is Region.Country -> region.displayName
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(regionLabel, style = TextStyle(color = Color.White)) // TODO(P0-04): theme token
    }
}
