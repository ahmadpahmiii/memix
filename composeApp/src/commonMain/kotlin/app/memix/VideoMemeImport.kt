package app.memix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memix.core.designsystem.MemixMotion
import app.memix.core.domain.media.MAX_ITEMS_PER_PICK
import app.memix.feature.videoeditor.importmedia.ImportIntent
import app.memix.feature.videoeditor.importmedia.ImportSheet
import app.memix.feature.videoeditor.importmedia.ImportUiState
import app.memix.feature.videoeditor.importmedia.MediaImportViewModel
import app.memix.feature.videoeditor.importmedia.isBlockingSheet
import app.memix.feature.videoeditor.importmedia.showsSheet
import app.memix.platform.services.FreeUpSpaceLauncher
import app.memix.platform.services.MediaPickerLauncher
import app.memix.platform.services.rememberFreeUpSpaceLauncher
import app.memix.platform.services.rememberMediaPickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Create → Video meme (P1-02) at the app root: opens the system picker, hands what was picked to
 * [MediaImportViewModel], and keeps the Create sheet's scrim up while that pick copies. The picker and the storage
 * screen are platform launchers, so the app root owns them; the feature only sees results, as intents.
 */
@Stable
internal class VideoMemeImport(
    private val viewModel: MediaImportViewModel,
    private val picker: MediaPickerLauncher,
    private val freeUpSpace: FreeUpSpaceLauncher,
    private val importState: State<ImportUiState>,
    // Where the open picker was started from; saved, so it survives Android restarting the app behind the picker.
    private val pickStartedOnCreateSheet: MutableState<Boolean>,
    private val scrimHeld: MutableState<Boolean>,
    // Closes the Create sheet and keeps its scrim up for the import that follows.
    private val handOverFromCreateSheet: () -> Unit,
    private val scope: CoroutineScope,
) {
    val state: ImportUiState get() = importState.value

    /** The import sheet is up, or a pick that started on the Create sheet is still copying (its scrim stays). */
    val needsScrim: Boolean by derivedStateOf { state.showsSheet || (scrimHeld.value && state != ImportUiState.Idle) }

    /** What a scrim tap does: closes a standard import sheet, and nothing while a blocking one is up. */
    val scrimTap: (() -> Unit)? by derivedStateOf {
        if (state.showsSheet && !state.isBlockingSheet) ({ viewModel.onIntent(ImportIntent.Close) }) else null
    }

    /**
     * Opens the picker, unless an import is running. From the Create sheet, the sheet stays open behind the picker
     * and closes, keeping its scrim, once something was picked.
     */
    fun start(fromCreateSheet: Boolean) {
        if (state != ImportUiState.Idle) return
        pickStartedOnCreateSheet.value = fromCreateSheet
        if (picker.launch()) return
        if (fromCreateSheet) handOverFromCreateSheet()
        viewModel.onIntent(ImportIntent.PickerUnavailable)
    }

    fun onIntent(intent: ImportIntent) = viewModel.onIntent(intent)

    /** "Pick again": the sheet closes first, then the picker opens (spec P1-02 → States). */
    fun pickAgain() {
        viewModel.onIntent(ImportIntent.Close)
        scope.launch {
            delay(MemixMotion.durationSheet.toLong())
            start(fromCreateSheet = false)
        }
    }

    fun openFreeUpSpace(requestedBytes: Long) {
        if (!freeUpSpace.launch(requestedBytes)) viewModel.onIntent(ImportIntent.FreeUpSpaceUnavailable)
    }
}

/**
 * Sets up the import. Call it unconditionally at the app root: a picker result that arrives after Android restarted
 * the app reaches only a picker registered at the same place in the UI. [openEditor] runs once the media is in.
 */
@Composable
internal fun rememberVideoMemeImport(closeCreateSheet: () -> Unit, openEditor: (projectId: String) -> Unit): VideoMemeImport {
    val viewModel = koinViewModel<MediaImportViewModel>()
    val importState = viewModel.state.collectAsStateWithLifecycle()
    val pickStartedOnCreateSheet = rememberSaveable { mutableStateOf(false) }
    val scrimHeld = rememberSaveable { mutableStateOf(false) }
    val currentCloseCreateSheet by rememberUpdatedState(closeCreateSheet)
    val scope = rememberCoroutineScope()
    val freeUpSpace = rememberFreeUpSpaceLauncher()
    val handOverFromCreateSheet = remember {
        {
            currentCloseCreateSheet()
            scrimHeld.value = true
        }
    }
    val picker = rememberMediaPickerLauncher(MAX_ITEMS_PER_PICK) { picked ->
        if (picked.isNotEmpty() && pickStartedOnCreateSheet.value) handOverFromCreateSheet()
        pickStartedOnCreateSheet.value = false
        viewModel.onIntent(ImportIntent.Picked(picked))
    }
    val videoMemeImport = remember(viewModel, picker, freeUpSpace) {
        VideoMemeImport(viewModel, picker, freeUpSpace, importState, pickStartedOnCreateSheet, scrimHeld, handOverFromCreateSheet, scope)
    }

    val editorToOpen by remember { derivedStateOf { (importState.value as? ImportUiState.OpenEditor)?.projectId } }
    val currentOpenEditor by rememberUpdatedState(openEditor)
    LaunchedEffect(editorToOpen) {
        val projectId = editorToOpen ?: return@LaunchedEffect
        currentOpenEditor(projectId)
        viewModel.onIntent(ImportIntent.EditorOpened)
    }
    val idle by remember { derivedStateOf { importState.value == ImportUiState.Idle } }
    LaunchedEffect(idle) { if (idle) scrimHeld.value = false }
    return videoMemeImport
}

/** The import sheet, drawn where the app root draws its sheets. It reads the import's progress, so only it redraws. */
@Composable
internal fun VideoMemeImportSheet(videoMemeImport: VideoMemeImport) {
    ImportSheet(
        state = videoMemeImport.state,
        onIntent = videoMemeImport::onIntent,
        onPickAgain = videoMemeImport::pickAgain,
        onFreeUpSpace = videoMemeImport::openFreeUpSpace,
    )
}
