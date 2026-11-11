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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixTheme
import app.memix.core.domain.SoundTab
import app.memix.core.domain.media.MAX_ITEMS_PER_PICK
import app.memix.core.model.project.MediaOrigin
import app.memix.core.domain.sound.PendingSound
import app.memix.core.domain.sound.PickedSound
import app.memix.core.domain.sound.StartingSound
import app.memix.feature.mediaimport.ImportIntent
import app.memix.feature.mediaimport.ImportSheet
import app.memix.feature.mediaimport.ImportUiState
import app.memix.feature.mediaimport.ImportWording
import app.memix.feature.mediaimport.MediaImportViewModel
import app.memix.feature.mediaimport.MemeImportViewModel
import app.memix.feature.mediaimport.PhotoMemeImportViewModel
import app.memix.feature.mediaimport.SoundMemeImportViewModel
import app.memix.feature.mediaimport.isBlockingSheet
import app.memix.feature.mediaimport.showsSheet
import app.memix.feature.photoeditor.PhotoEditorIntent
import app.memix.feature.photoeditor.PhotoPickRequest
import app.memix.platform.services.FreeUpSpaceLauncher
import app.memix.platform.services.MediaPickerLauncher
import app.memix.platform.services.PickerMedia
import app.memix.platform.services.rememberFreeUpSpaceLauncher
import app.memix.platform.services.rememberMediaPickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Create → Video meme (P1-02) or Photo meme (P2-01) at the app root: opens the system picker, hands what was picked to
 * the import's [MemeImportViewModel], and keeps the Create sheet's scrim up while that pick copies. The picker and the
 * storage screen are platform launchers, so the app root owns them; the feature only sees results, as intents.
 * The Sounds tab's Use (P3-15) is the video meme only: [startWithSound]. The sound itself is the ViewModel's; this class keeps
 * the picker, and the id and tab of the Use whose picker is open, saved with the screen so a restart behind the picker still
 * lands the sound ([ImportIntent.Picked.restoredSound]).
 */
@Stable
internal class MemeImport(
    private val viewModel: MemeImportViewModel,
    val wording: ImportWording,
    private val picker: MediaPickerLauncher,
    private val freeUpSpace: FreeUpSpaceLauncher,
    private val importState: State<ImportUiState>,
    // Where the open picker was started from; saved, so it survives Android restarting the app behind the picker.
    private val pickStartedOnCreateSheet: MutableState<Boolean>,
    private val scrimHeld: MutableState<Boolean>,
    // The video meme's ViewModel when it can start with a library sound (P3-15), and the id and tab of the Use whose picker is open.
    private val soundImport: SoundMemeImportViewModel?,
    private val savedSound: MutableState<PendingSound?>,
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

    /** Each time a Use has run its course; the Sounds tab takes taps again then. Empty for a photo meme. */
    val soundUseEnds: Flow<Unit> get() = soundImport?.soundUseEnds ?: emptyFlow()

    /**
     * Opens the picker, unless an import is running. From the Create sheet, the sheet stays open behind the picker
     * and closes, keeping its scrim, once something was picked.
     */
    fun start(fromCreateSheet: Boolean) {
        if (state != ImportUiState.Idle) return
        savedSound.value = null
        openPicker(fromCreateSheet)
    }

    /** The Sounds tab's Use: the system picker, then a new video meme with [sound] at 0:00. Ignored while an import runs. */
    fun startWithSound(sound: PickedSound) {
        if (soundImport?.beginWithSound(sound) != true) return
        savedSound.value = PendingSound(sound.soundId, sound.tab)
        openPicker(fromCreateSheet = false)
    }

    /** The picker returned: what was picked, or nothing. */
    fun onPicked(picked: List<MediaOrigin>) {
        if (picked.isNotEmpty() && pickStartedOnCreateSheet.value) handOverFromCreateSheet()
        pickStartedOnCreateSheet.value = false
        val restore = savedSound.value
        savedSound.value = null
        viewModel.onIntent(ImportIntent.Picked(picked, restore))
    }

    private fun openPicker(fromCreateSheet: Boolean) {
        pickStartedOnCreateSheet.value = fromCreateSheet
        if (picker.launch()) return
        // A Use that could not open the picker leaves nothing waiting (the ViewModel drops its sound).
        savedSound.value = null
        if (fromCreateSheet) handOverFromCreateSheet()
        viewModel.onIntent(ImportIntent.PickerUnavailable)
    }

    fun onIntent(intent: ImportIntent) = viewModel.onIntent(intent)

    /** "Pick again": the sheet closes first, then the picker opens (spec P1-02 → States). */
    fun pickAgain() {
        viewModel.onIntent(ImportIntent.PickAgain)
        scope.launch {
            delay(MemixMotion.durationSheet.toLong())
            if (state == ImportUiState.Idle) openPicker(fromCreateSheet = false)
        }
    }

    fun openFreeUpSpace(requestedBytes: Long) {
        if (!freeUpSpace.launch(requestedBytes)) viewModel.onIntent(ImportIntent.FreeUpSpaceUnavailable)
    }
}

/** Create → Video meme: up to 35 videos and photos. */
@Composable
internal fun rememberVideoMemeImport(closeCreateSheet: () -> Unit, openEditor: (projectId: String, startingSound: StartingSound) -> Unit): MemeImport =
    rememberMemeImport(koinViewModel<MediaImportViewModel>(), ImportWording.MEDIA, MAX_ITEMS_PER_PICK, PickerMedia.IMAGES_AND_VIDEOS, closeCreateSheet, openEditor)

/** Create → Photo meme: one photo from the gallery. */
@Composable
internal fun rememberPhotoMemeImport(closeCreateSheet: () -> Unit, openEditor: (projectId: String) -> Unit): MemeImport =
    rememberMemeImport(koinViewModel<PhotoMemeImportViewModel>(), ImportWording.PHOTO, maxItems = 1, PickerMedia.IMAGES_ONLY, closeCreateSheet) { projectId, _ -> openEditor(projectId) }

/**
 * Sets up an import. Call it unconditionally at the app root: a picker result that arrives after Android restarted
 * the app reaches only a picker registered at the same place in the UI. [openEditor] runs once the media is in.
 */
@Composable
private fun rememberMemeImport(
    viewModel: MemeImportViewModel,
    wording: ImportWording,
    maxItems: Int,
    media: PickerMedia,
    closeCreateSheet: () -> Unit,
    openEditor: (projectId: String, startingSound: StartingSound) -> Unit,
): MemeImport {
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
    val savedSound = rememberSaveable(stateSaver = PendingSoundSaver) { mutableStateOf<PendingSound?>(null) }
    // The picker's callback runs inside MemeImport, which needs the picker: this cell closes the loop.
    val memeImportCell = remember { arrayOfNulls<MemeImport>(1) }
    val picker = rememberMediaPickerLauncher(maxItems, media) { picked -> memeImportCell[0]?.onPicked(picked) }
    val memeImport = remember(viewModel, picker, freeUpSpace) {
        MemeImport(viewModel, wording, picker, freeUpSpace, importState, pickStartedOnCreateSheet, scrimHeld, viewModel as? SoundMemeImportViewModel, savedSound, handOverFromCreateSheet, scope)
            .also { memeImportCell[0] = it }
    }

    val editorToOpen by remember { derivedStateOf { (importState.value as? ImportUiState.Done)?.projectId } }
    val currentOpenEditor by rememberUpdatedState(openEditor)
    val pushMillis = if (MemixTheme.reduceMotion) MemixMotion.durationPress else MemixMotion.durationScreen
    LaunchedEffect(editorToOpen) {
        val projectId = editorToOpen ?: return@LaunchedEffect
        currentOpenEditor(projectId, viewModel.startingSound)
        // The sheet and scrim hold still while the editor's push covers them, then go (spec P1-02 → Motion).
        delay(pushMillis.toLong())
        viewModel.onIntent(ImportIntent.Finish)
    }
    val idle by remember { derivedStateOf { importState.value == ImportUiState.Idle } }
    LaunchedEffect(idle) { if (idle) scrimHeld.value = false }
    return memeImport
}

/** The import sheet, drawn where the app root draws its sheets. It reads the import's progress, so only it redraws. */
@Composable
internal fun MemeImportSheet(memeImport: MemeImport) {
    ImportSheet(
        state = memeImport.state,
        onIntent = memeImport::onIntent,
        onPickAgain = memeImport::pickAgain,
        onFreeUpSpace = memeImport::openFreeUpSpace,
        wording = memeImport.wording,
    )
}

/**
 * The photo editor's system picker for a photo that goes into the open project (P2-12 canvas background; P2-13 adds Add
 * photo): one image, no video. Opens the one [request] asks for and keeps that request while the picker is open, saved with
 * the screen, so the result goes back to the editor with what it was for ([PhotoEditorIntent.PhotoPicked]) even after Android
 * restarted the app behind the picker. Call it unconditionally in the editor's destination: a result after a restart reaches
 * only a picker registered at the same place.
 */
@Composable
internal fun PhotoEditorPicker(request: PhotoPickRequest?, onIntent: (PhotoEditorIntent) -> Unit) {
    val currentOnIntent by rememberUpdatedState(onIntent)
    val openRequest = rememberSaveable(stateSaver = PhotoPickRequestSaver) { mutableStateOf<PhotoPickRequest?>(null) }
    val picker = rememberMediaPickerLauncher(maxItems = 1, PickerMedia.IMAGES_ONLY) { picked ->
        val opened = openRequest.value
        openRequest.value = null
        if (opened != null) currentOnIntent(PhotoEditorIntent.PhotoPicked(opened, picked))
    }
    LaunchedEffect(request) {
        val wanted = request ?: return@LaunchedEffect
        openRequest.value = wanted
        if (picker.launch()) {
            currentOnIntent(PhotoEditorIntent.PickerOpened)
        } else {
            openRequest.value = null
            currentOnIntent(PhotoEditorIntent.PickerUnavailable)
        }
    }
}

// A waiting sound as its id and its analytics tab.
private val PendingSoundSaver = Saver<PendingSound?, List<String>>(
    save = { sound -> if (sound == null) emptyList() else listOf(sound.soundId, sound.tab.eventValue) },
    restore = { saved ->
        val tab = SoundTab.entries.firstOrNull { it.eventValue == saved.getOrNull(1) }
        saved.firstOrNull()?.let { id -> tab?.let { PendingSound(id, it) } }
    },
)

// A request as a short list of plain values: its kind.
private val PhotoPickRequestSaver = Saver<PhotoPickRequest?, List<Any>>(
    save = { request ->
        when (request) {
            null -> emptyList()
            PhotoPickRequest.Background -> listOf(KIND_BACKGROUND)
            PhotoPickRequest.AddLayer -> listOf(KIND_ADD_LAYER)
            is PhotoPickRequest.Cell -> listOf(KIND_CELL, request.index)
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            KIND_BACKGROUND -> PhotoPickRequest.Background
            KIND_ADD_LAYER -> PhotoPickRequest.AddLayer
            KIND_CELL -> (saved.getOrNull(1) as? Int)?.let { PhotoPickRequest.Cell(it) }
            else -> null
        }
    },
)

private const val KIND_BACKGROUND = "background"
private const val KIND_ADD_LAYER = "add_layer"
private const val KIND_CELL = "cell"
