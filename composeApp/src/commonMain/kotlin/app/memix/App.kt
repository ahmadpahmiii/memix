package app.memix

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.component.BottomNav
import app.memix.core.designsystem.component.NavDestination
import app.memix.core.designsystem.component.Scrim
import app.memix.debug.DebugComponentCatalog
import app.memix.feature.drafts.DraftsScreen
import app.memix.feature.home.HomeScreen
import app.memix.feature.home.HomeViewModel
import app.memix.feature.photoeditor.PhotoEditorPlaceholder
import app.memix.feature.sounds.SoundsScreen
import app.memix.feature.templates.TemplatesScreen
import app.memix.feature.videoeditor.VideoEditorPlaceholder
import app.memix.feature.videoeditor.VideoEditorViewModel
import memix.composeapp.generated.resources.Res
import memix.composeapp.generated.resources.nav_create
import memix.composeapp.generated.resources.nav_drafts
import memix.composeapp.generated.resources.nav_home
import memix.composeapp.generated.resources.nav_sounds
import memix.composeapp.generated.resources.nav_templates
import org.jetbrains.compose.resources.stringResource
import app.memix.core.domain.RefreshAppConfigUseCase
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val tabRoutes = listOf(HomeRoute, TemplatesRoute, SoundsRoute, DraftsRoute)

/** [isDebugBuild] unlocks the component catalog (long-press on the Home wordmark). */
@Composable
fun App(isDebugBuild: Boolean) {
    val refreshAppConfig = koinInject<RefreshAppConfigUseCase>()
    LaunchedEffect(Unit) { refreshAppConfig() }
    MemixTheme {
        val navController = rememberNavController()
        var createSheetOpen by rememberSaveable { mutableStateOf(false) }
        val videoMemeImport = rememberVideoMemeImport(
            closeCreateSheet = { createSheetOpen = false },
            openEditor = { projectId -> navController.navigate(VideoEditorRoute(projectId)) },
        )
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentTab = tabRoutes.firstOrNull { route -> backStackEntry?.destination?.hasRoute(route::class) == true }

        Box(Modifier.fillMaxSize().background(MemixColors.canvas)) {
            Column(Modifier.fillMaxSize()) {
                MemixNavHost(
                    navController,
                    isDebugBuild,
                    onOpenCreate = { createSheetOpen = true },
                    onStartVideoMeme = { videoMemeImport.start(fromCreateSheet = false) },
                    Modifier.weight(1f),
                )
                if (currentTab != null) {
                    MemixBottomNav(currentTab, onSelect = { navController.navigateToTab(it) }, onCreate = { createSheetOpen = true })
                }
            }
            // One scrim for both sheets, so it stays up when the Create sheet hands over to the import (spec P1-02).
            Scrim(
                visible = createSheetOpen || videoMemeImport.needsScrim,
                onDismiss = if (createSheetOpen) ({ createSheetOpen = false }) else videoMemeImport.scrimTap,
            )
            CreateSheet(
                visible = createSheetOpen,
                onDismiss = { createSheetOpen = false },
                onVideoMeme = { videoMemeImport.start(fromCreateSheet = true) },
                onPhotoMeme = { createSheetOpen = false; navController.navigate(PhotoEditorRoute) },
                onTemplates = { createSheetOpen = false; navController.navigateToTab(TemplatesRoute) },
            )
            VideoMemeImportSheet(videoMemeImport)
        }
    }
}

@Composable
private fun MemixNavHost(
    navController: NavHostController,
    isDebugBuild: Boolean,
    onOpenCreate: () -> Unit,
    onStartVideoMeme: () -> Unit,
    modifier: Modifier,
) {
    val reduceMotion = MemixTheme.reduceMotion
    NavHost(
        navController,
        startDestination = HomeRoute,
        modifier = modifier,
        // Tabs switch in place; only full screens (editors, catalog) slide.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<HomeRoute> {
            val viewModel = koinViewModel<HomeViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onStartVideoMeme = onStartVideoMeme,
                onOpenPhotoEditor = { navController.navigate(PhotoEditorRoute) },
                onOpenCatalog = if (isDebugBuild) ({ navController.navigate(CatalogRoute) }) else null,
            )
        }
        composable<TemplatesRoute> { TemplatesScreen() }
        composable<SoundsRoute> { SoundsScreen() }
        composable<DraftsRoute> { DraftsScreen(onMakeMeme = onOpenCreate) }
        fullScreen<VideoEditorRoute>(reduceMotion) { entry ->
            val route = entry.toRoute<VideoEditorRoute>()
            val viewModel = koinViewModel<VideoEditorViewModel> { parametersOf(route.projectId) }
            val state by viewModel.state.collectAsStateWithLifecycle()
            VideoEditorPlaceholder(state, onClose = { navController.popBackStack() })
        }
        fullScreen<PhotoEditorRoute>(reduceMotion) { PhotoEditorPlaceholder(onClose = { navController.popBackStack() }) }
        if (isDebugBuild) {
            fullScreen<CatalogRoute>(reduceMotion) { DebugComponentCatalog(onClose = { navController.popBackStack() }) }
        }
    }
}

private inline fun <reified T : Any> androidx.navigation.NavGraphBuilder.fullScreen(
    reduceMotion: Boolean,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    val duration = tween<androidx.compose.ui.unit.IntOffset>(MemixMotion.durationScreen)
    composable<T>(
        enterTransition = { if (reduceMotion) fadeIn(tween(MemixMotion.durationPress)) else slideIntoContainer(SlideDirection.Start, duration) },
        popExitTransition = { if (reduceMotion) fadeOut(tween(MemixMotion.durationPress)) else slideOutOfContainer(SlideDirection.End, duration) },
    ) { entry -> content(entry) }
}

@Composable
private fun MemixBottomNav(currentTab: Any, onSelect: (Any) -> Unit, onCreate: () -> Unit) {
    BottomNav(
        leading = listOf(
            NavDestination(HomeRoute, stringResource(Res.string.nav_home), MemixIcons.Home),
            NavDestination(TemplatesRoute, stringResource(Res.string.nav_templates), MemixIcons.Templates),
        ),
        trailing = listOf(
            NavDestination(SoundsRoute, stringResource(Res.string.nav_sounds), MemixIcons.Sounds),
            NavDestination(DraftsRoute, stringResource(Res.string.nav_drafts), MemixIcons.Drafts),
        ),
        selectedKey = currentTab,
        onSelect = { onSelect(it.key) },
        createLabel = stringResource(Res.string.nav_create),
        onCreate = onCreate,
    )
}

/** Standard bottom-nav behavior: one copy of each tab, each keeps its state, back from a tab goes to Home. */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
