package app.memix

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.component.BottomNav
import app.memix.core.designsystem.component.NavDestination
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.Scrim
import app.memix.core.designsystem.component.coveredByModal
import app.memix.core.domain.RefreshAppConfigUseCase
import app.memix.core.domain.text.CaptionMeasurer
import app.memix.core.domain.video.ExportJobState
import app.memix.core.domain.video.ExportVideoUseCase
import app.memix.core.domain.video.RecoverUnfinishedExportUseCase
import app.memix.core.domain.video.EXPORT_HEADROOM_BYTES
import app.memix.debug.DebugComponentCatalog
import app.memix.debug.openDebugEditorProject
import app.memix.engine.video.CaptionStandIn
import app.memix.engine.video.VideoPreviewSurface
import app.memix.engine.video.VideoResultSurface
import app.memix.feature.export.ExportExit
import app.memix.feature.export.ExportScreen
import app.memix.feature.export.ExportViewModel
import app.memix.feature.export.LastExportStoppedToast
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
import app.memix.platform.services.rememberNotificationPermissionRequest
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
        Box(Modifier.fillMaxSize()) {
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
                    VideoEditorDestination(
                        entry.toRoute<VideoEditorRoute>().projectId,
                        onClose = { navController.popBackStack() },
                        onOpenExport = { navController.navigate(ExportRoute) },
                    )
                }
                fullScreen<ExportRoute>(reduceMotion) {
                    ExportDestination(
                        onExit = { exit ->
                            navController.leaveExport(exit)
                            // Make another: Home with the Create sheet open (spec P1-12 → Exported screen).
                            if (exit == ExportExit.MAKE_ANOTHER) createSheetOpen = true
                        },
                    )
                }
                fullScreen<PhotoEditorRoute>(reduceMotion) { PhotoEditorPlaceholder(onClose = { navController.popBackStack() }) }
                if (isDebugBuild) {
                    fullScreen<CatalogRoute>(reduceMotion) { DebugComponentCatalog(onClose = { navController.popBackStack() }) }
                }
            }
            ReopenRunningExport(navController)
            LastExportStoppedNotice(onOpenDraft = { projectId -> navController.navigate(VideoEditorRoute(projectId)) })
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
    // While a sheet or its scrim is up, the tabs and nav behind it are hidden from TalkBack and the keyboard (P1-08
    // review, F1). The scrim's own rule decides, so the hand-over from Create to the import stays covered throughout.
    val sheetUp = createSheetOpen || videoMemeImport.needsScrim
    Box(Modifier.fillMaxSize().background(MemixColors.canvas)) {
        Column(Modifier.fillMaxSize().coveredByModal(sheetUp)) {
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
            visible = sheetUp,
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

/**
 * The video editor (P1-04) with the engine's preview surface, the phone's storage screen and the pickers for Add media
 * and Replace (P1-16) plugged in.
 */
@Composable
private fun VideoEditorDestination(projectId: String, onClose: () -> Unit, onOpenExport: () -> Unit) {
    val viewModel = koinViewModel<VideoEditorViewModel> { parametersOf(projectId) }
    // The engine's caption renderer, which its stand-in draws with (P1-10).
    val captionMeasurer = koinInject<CaptionMeasurer>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val freeUpSpace = rememberFreeUpSpaceLauncher()
    LockPortraitOnPhones()
    EditorMediaPickers(state.mediaPicker, viewModel::onIntent)
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(state.isClosing) { if (state.isClosing) currentOnClose() }
    // Export video started an export: the Exporting screen covers the editor (spec P1-12 → Entry and exit).
    val currentOnOpenExport by rememberUpdatedState(onOpenExport)
    LaunchedEffect(state.openExportScreen) {
        if (!state.openExportScreen) return@LaunchedEffect
        currentOnOpenExport()
        viewModel.onIntent(VideoEditorIntent.ExportScreenOpened)
    }
    VideoEditorScreen(
        state = state,
        onIntent = viewModel::onIntent,
        previewSurface = { session, modifier -> VideoPreviewSurface(session, modifier) },
        captionStandIn = { text, style, transform, canvasWidthPx, canvasHeightPx, modifier ->
            CaptionStandIn(captionMeasurer, text, style, transform, canvasWidthPx, canvasHeightPx, modifier)
        },
        // When no storage screen opens, the editor hides the button (spec P1-04 → Saving).
        onFreeUpSpace = { requestedBytes ->
            if (!freeUpSpace.launch(requestedBytes)) viewModel.onIntent(VideoEditorIntent.FreeUpSpaceUnavailable)
        },
    )
}

/**
 * The Exporting and Exported screens (P1-12) with the engine's result surface, the phone's notification dialog and its
 * storage screen plugged in. [onExit] leaves the screen.
 */
@Composable
private fun ExportDestination(onExit: (ExportExit) -> Unit) {
    val viewModel = koinViewModel<ExportViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notificationPermission = rememberNotificationPermissionRequest()
    val freeUpSpace = rememberFreeUpSpaceLauncher()
    // After a full disk the storage screen opens; when none exists the button goes away.
    var canFreeUpSpace by remember { mutableStateOf(true) }
    ExportScreen(
        state = state,
        onIntent = viewModel::onIntent,
        resultSurface = { player, modifier -> VideoResultSurface(player, modifier) },
        requestNotificationPermission = notificationPermission::request,
        onFreeUpSpace = if (canFreeUpSpace) ({ if (!freeUpSpace.launch(EXPORT_HEADROOM_BYTES)) canFreeUpSpace = false }) else null,
        onExit = onExit,
    )
}

/**
 * Brings the Exporting screen back when Memix is opened (from the notification or the recents screen) while an export is
 * running, or ended while the app was away: the screen is current on return (spec P1-12 → App in the background).
 * Android keeps the export alive without the activity, so the editor and its draft go behind it again.
 */
@Composable
private fun ReopenRunningExport(navController: NavHostController) {
    val export = koinInject<ExportVideoUseCase>()
    LaunchedEffect(Unit) {
        val job = when (val state = export.state.value) {
            is ExportJobState.Running -> state.job
            is ExportJobState.Done -> state.job
            is ExportJobState.Failed -> state.job
            ExportJobState.Idle, is ExportJobState.Cancelled -> return@LaunchedEffect
        }
        // Nothing to do when the screen is already there (Android recreated the activity and restored the screens).
        if (navController.currentDestination?.hasRoute(ExportRoute::class) == true) return@LaunchedEffect
        navController.navigate(VideoEditorRoute(job.project.id))
        navController.navigate(ExportRoute)
    }
}

/**
 * After Memix was stopped mid-export: once, at the first screen, a toast with Open draft (spec P1-12 → Next start). The
 * use case also deletes the unfinished file and counts the event.
 */
@Composable
private fun BoxScope.LastExportStoppedNotice(onOpenDraft: (projectId: String) -> Unit) {
    val recover = koinInject<RecoverUnfinishedExportUseCase>()
    // Saved, so a rotation right after the start doesn't lose it (the use case answers once per run).
    var draftId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { recover()?.let { draftId = it.projectId } }
    val placement = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(MemixSpacing.space4)
    FadingToast(draftId, placement) { projectId ->
        LastExportStoppedToast(
            onOpenDraft = {
                draftId = null
                onOpenDraft(projectId)
            },
            onDismiss = { draftId = null },
        )
    }
}

private fun NavHostController.leaveExport(exit: ExportExit) {
    when (exit) {
        ExportExit.EDITOR -> popBackStack()
        ExportExit.HOME, ExportExit.MAKE_ANOTHER -> popBackStack(MainRoute, inclusive = false)
    }
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
