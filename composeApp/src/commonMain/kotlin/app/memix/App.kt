package app.memix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.memix.feature.home.HomeScreen
import app.memix.feature.home.HomeViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object HomeRoute

@Composable
fun App() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val viewModel = koinViewModel<HomeViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            HomeScreen(state, viewModel::onIntent)
        }
    }
}
