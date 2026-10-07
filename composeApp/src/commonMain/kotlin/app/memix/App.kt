package app.memix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.catalog.ComponentCatalog
import app.memix.feature.home.HomeScreen
import app.memix.feature.home.HomeViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object HomeRoute

@Serializable
data object CatalogRoute

/** [isDebugBuild] unlocks the component catalog (long-press on Home). */
@Composable
fun App(isDebugBuild: Boolean) {
    MemixTheme {
        val navController = rememberNavController()
        NavHost(navController, startDestination = HomeRoute, modifier = Modifier.fillMaxSize().background(MemixColors.canvas)) {
            composable<HomeRoute> {
                val viewModel = koinViewModel<HomeViewModel>()
                val state by viewModel.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onIntent = viewModel::onIntent,
                    onOpenCatalog = if (isDebugBuild) ({ navController.navigate(CatalogRoute) }) else null,
                )
            }
            if (isDebugBuild) {
                composable<CatalogRoute> { ComponentCatalog(onClose = { navController.popBackStack() }) }
            }
        }
    }
}
