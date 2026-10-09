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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
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
import app.memix.core.domain.RefreshAppConfigUseCase
import app.memix.core.domain.media.ImportMediaUseCase
import app.memix.debug.DebugComponentCatalog
import app.memix.debug.openDebugEditorProject
import app.memix.engine.video.VideoPreviewSurface
import app.memix.feature.drafts.DraftsScreen
import app.memix.feature.home.HomeScreen
import app.memix.feature.home.HomeViewModel
import app.memix.feature.photoeditor.PhotoEditorPlaceholder
import app.memix.feature.sounds.SoundsScreen
import app.memix.feature.templates.TemplatesScreen
import app.memix.feature.videoeditor.VideoEditorIntent
import app.memix.feature.videoeditor.VideoEditorScreen
import app.memix.feature.videoeditor.VideoEditorViewModel
import app.memix.platform.services.LockPortraitOnPhones
import app.memix.platform.services.rememberFreeUpSpaceLauncher
import memix.composeapp.generated.resources.Res
import memix.composeapp.generated.resources.nav_create
import memix.composeapp.generated.resources.nav_drafts
import memix.composeapp.generated.resources.nav_home
import memix.composeapp.generated.resources.nav_sounds
import memix.composeapp.generated.resources.nav_templates
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val tabRoutes = listOf(HomeRoute, TemplatesRoute, SoundsRoute, DraftsRoute)

/**
 * The app's root. [isDebugBuild] unlocks the component catalog (long-press on the Home wordmark).
 * [debugEditorProject] (debug and benchmark builds, from an adb extra) opens one of the hand-check projects in the
 * video editor once, at start.
 */
@Composable
fun App(isDebugBuild: Boolean, debugEditorProject: String? = null) {
    val refreshAppConfig = koinInject<RefreshAppConfigUseCase>()
    LaunchedEffect(Unit) { refreshAppConfig() }
    MemixTheme {
        val navController = rememberNavController()
        var createSheetOpen by rememberSaveable { mutableStateOf(false) }
        // Registered here, outside the navigation, so a picker result after Android restarted the app still lands.
        val videoMemeImport = rememberVideoMemeImport(
            closeCreateSheet = { createSheetOpen = false },
            openEditor = { projectId -> navController.navigate(VideoEditorRoute(projectId)) },
        )
        if (debugEditorProject != null) {
            LaunchedEffect(debugEditorProject) {
                val projectId = openDebugEditorProject(debugEditorProject) ?: return@LaunchedEffect
                navController.navigate(VideoEditorRoute(projectId))
            }
        }
        val reduceMotion = MemixTheme.reduceMotion
        NavHost(
            navController,
            startDestination = MainRoute,
            modifier = Modifier.fillMaxSize().background(MemixColors.canvas),
            // Only full screens (editors, catalog) slide; see fullScreen().
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<MainRoute> {
                MainShell(
                    isDebugBuild = isDebugBuild,
                    createSheetOpen = createSheetOpen,
                    onCreateSheetOpenChange = { createSheetOpen = it },
                    videoMemeImport = videoMemeImport,
                    onOpenPhotoEditor = { navController.navigate(PhotoEditorRoute) },
                    onOpenCatalog = { navController.navigate(CatalogRoute) },
                )
            }
            fullScreen<VideoEditorRoute>(reduceMotion) { entry ->
                VideoEditorDestination(entry.toRoute<VideoEditorRoute>().projectId, onClose = { navController.popBackStack() })
            }
            fullScreen<PhotoEditorRoute>(reduceMotion) { PhotoEditorPlaceholder(onClose = { navController.popBackStack() }) }
            if (isDebugBuild) {
                fullScreen<CatalogRoute>(reduceMotion) { DebugComponentCatalog(onClose = { navController.popBackStack() }) }
            }
        }
    }
}

/**
 * The tabs with the bottom nav, plus the sheets that open over them (Create, import). They live inside this one
 * destination, so a full screen pushed from here (the editor after an import) slides in over the sheets instead of
 * the sheets sliding away first (spec P1-02 → Motion).
 */
@Composable
private fun MainShell(
    isDebugBuild: Boolean,
    createSheetOpen: Boolean,
    onCreateSheetOpenChange: (Boolean) -> Unit,
    videoMemeImport: VideoMemeImport,
    onOpenPhotoEditor: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val tabsController = rememberNavController()
    val backStackEntry by tabsController.currentBackStackEntryAsState()
    val currentTab = tabRoutes.firstOrNull { route -> backStackEntry?.destination?.hasRoute(route::class) == true } ?: HomeRoute
    Box(Modifier.fillMaxSize().background(MemixColors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            TabsNavHost(
                tabsController,
                onOpenCreate = { onCreateSheetOpenChange(true) },
                onStartVideoMeme = { videoMemeImport.start(fromCreateSheet = false) },
                onOpenPhotoEditor = onOpenPhotoEditor,
                onOpenCatalog = if (isDebugBuild) onOpenCatalog else null,
                modifier = Modifier.weight(1f),
            )
            MemixBottomNav(currentTab, onSelect = { tabsController.navigateToTab(it) }, onCreate = { onCreateSheetOpenChange(true) })
        }
        // One scrim for both sheets, so it stays up when the Create sheet hands over to the import (spec P1-02).
        Scrim(
            visible = createSheetOpen || videoMemeImport.needsScrim,
            onDismiss = if (createSheetOpen) ({ onCreateSheetOpenChange(false) }) else videoMemeImport.scrimTap,
        )
        CreateSheet(
            visible = createSheetOpen,
            onDismiss = { onCreateSheetOpenChange(false) },
            onVideoMeme = { videoMemeImport.start(fromCreateSheet = true) },
            onPhotoMeme = {
                onCreateSheetOpenChange(false)
                onOpenPhotoEditor()
            },
            onTemplates = {
                onCreateSheetOpenChange(false)
                tabsController.navigateToTab(TemplatesRoute)
            },
        )
        VideoMemeImportSheet(videoMemeImport)
    }
}

@Composable
private fun TabsNavHost(
    navController: NavHostController,
    onOpenCreate: () -> Unit,
    onStartVideoMeme: () -> Unit,
    onOpenPhotoEditor: () -> Unit,
    onOpenCatalog: (() -> Unit)?,
    modifier: Modifier,
) {
    NavHost(
        navController,
        startDestination = HomeRoute,
        modifier = modifier,
        // Tabs switch in place.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<HomeRoute> {
            val viewModel = koinViewModel<HomeViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            HomeScreen(state = state, onStartVideoMeme = onStartVideoMeme, onOpenPhotoEditor = onOpenPhotoEditor, onOpenCatalog = onOpenCatalog)
        }
        composable<TemplatesRoute> { TemplatesScreen() }
        composable<SoundsRoute> { SoundsScreen() }
        composable<DraftsRoute> { DraftsScreen(onMakeMeme = onOpenCreate) }
    }
}

/** The video editor (P1-04) with the engine's preview surface and the phone's storage screen plugged in. */
@Composable
private fun VideoEditorDestination(projectId: String, onClose: () -> Unit) {
    val viewModel = koinViewModel<VideoEditorViewModel> { parametersOf(projectId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val freeUpSpace = rememberFreeUpSpaceLauncher()
    LockPortraitOnPhones()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(state.isClosing) { if (state.isClosing) currentOnClose() }
    VideoEditorScreen(
        state = state,
        onIntent = viewModel::onIntent,
        previewSurface = { session, modifier -> VideoPreviewSurface(session, modifier) },
        // A save needs little room; ask for the import's headroom, so the next import fits too. When no storage screen
        // opens, the editor hides the button (spec P1-04 → Saving).
        onFreeUpSpace = {
            if (!freeUpSpace.launch(ImportMediaUseCase.HEADROOM_BYTES)) viewModel.onIntent(VideoEditorIntent.FreeUpSpaceUnavailable)
        },
    )
}

private inline fun <reified T : Any> NavGraphBuilder.fullScreen(
    reduceMotion: Boolean,
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    val duration = tween<IntOffset>(MemixMotion.durationScreen)
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
