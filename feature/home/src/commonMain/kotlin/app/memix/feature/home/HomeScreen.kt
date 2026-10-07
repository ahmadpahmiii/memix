package app.memix.feature.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.model.Region
import memix.feature.home.generated.resources.Res
import memix.feature.home.generated.resources.region_global
import org.jetbrains.compose.resources.stringResource

/** [onOpenCatalog] is non-null only in debug builds. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCatalog: (() -> Unit)? = null,
) {
    val regionLabel = when (val region = state.region) {
        Region.Global -> stringResource(Res.string.region_global)
        is Region.Country -> region.displayName
    }
    val debugGesture = if (onOpenCatalog == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { onOpenCatalog() })
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(regionLabel, MemixTheme.type.body, debugGesture)
    }
}
