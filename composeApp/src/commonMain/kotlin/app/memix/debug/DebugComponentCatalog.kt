package app.memix.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.catalog.ComponentCatalog
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.Scrim
import app.memix.core.domain.media.ImportFailure
import app.memix.core.domain.media.MAX_ITEMS_PER_PICK
import app.memix.feature.videoeditor.importmedia.CopyProgressUi
import app.memix.feature.videoeditor.importmedia.ImportIntent
import app.memix.feature.videoeditor.importmedia.ImportSheet
import app.memix.feature.videoeditor.importmedia.ImportUiState
import app.memix.feature.videoeditor.importmedia.NotAddedItemUi
import app.memix.feature.videoeditor.importmedia.TimeLeft
import app.memix.feature.videoeditor.importmedia.isBlockingSheet
import app.memix.feature.videoeditor.importmedia.showsSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/**
 * The component catalog plus every import sheet state with sample numbers (P1-02), so the designer can review them
 * on a phone without real damaged files or a full disk. Debug builds only; labels are developer English.
 */
@Composable
internal fun DebugComponentCatalog(onClose: () -> Unit) {
    var sample by remember { mutableStateOf<ImportUiState>(ImportUiState.Idle) }
    Box(Modifier.fillMaxSize()) {
        ComponentCatalog(onClose, appSections = { ImportSheetSamples(onShow = { sample = it }) })
        Scrim(
            visible = sample.showsSheet,
            onDismiss = if (sample.isBlockingSheet) null else ({ sample = ImportUiState.Idle }),
        )
        ImportSheet(
            state = sample,
            // Every button closes the sample; coming back to the app (AppResumed) leaves it up.
            onIntent = { intent -> if (intent != ImportIntent.AppResumed) sample = ImportUiState.Idle },
            onPickAgain = { sample = ImportUiState.Idle },
            onFreeUpSpace = { sample = ImportUiState.Idle },
        )
    }
}

@Composable
private fun ImportSheetSamples(onShow: (ImportUiState) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
        Text("Import sheet (P1-02)", MemixTheme.type.title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
            importSheetSamples.forEach { (label, state) -> Button(label, { onShow(state) }) }
        }
    }
}

private fun copying(progress: CopyProgressUi, limit: Int? = null) = ImportUiState.Copying(sheetShown = true, progress, limit)

private val damagedVideo = NotAddedItemUi("VID_20261007_181530_cut_short_on_upload.mp4", isVideo = true, ImportFailure.UNSUPPORTED)
private val cloudPhoto = NotAddedItemUi("IMG_4471.HEIC", isVideo = false, ImportFailure.UNREADABLE)
private val unnamedVideo = NotAddedItemUi(null, isVideo = true, ImportFailure.UNREADABLE)

private val importSheetSamples: List<Pair<String, ImportUiState>> = listOf(
    "Copying" to copying(CopyProgressUi(3, 5, 0.4f, 40, timeLeft = null, currentFileCopiedBytes = null)),
    "Copying, long" to copying(CopyProgressUi(1, 2, 0.12f, 12, TimeLeft.Seconds(45), currentFileCopiedBytes = null)),
    "Copying, minutes" to copying(CopyProgressUi(2, 4, 0.31f, 31, TimeLeft.Minutes(3), currentFileCopiedBytes = null)),
    "Copying, size unknown" to copying(CopyProgressUi(2, 3, 0.33f, 33, timeLeft = null, currentFileCopiedBytes = 48_200_000)),
    "Copying, over limit" to copying(CopyProgressUi(35, 35, 0.97f, 97, timeLeft = null, currentFileCopiedBytes = null), MAX_ITEMS_PER_PICK),
    "Some didn't go in" to ImportUiState.SomeNotAdded("sample", 2, 3, persistentListOf(damagedVideo), limit = null),
    "Some, limit only" to ImportUiState.SomeNotAdded("sample", 35, 41, persistentListOf(), MAX_ITEMS_PER_PICK),
    "Some, limit and failures" to ImportUiState.SomeNotAdded("sample", 33, 41, persistentListOf(damagedVideo, unnamedVideo), MAX_ITEMS_PER_PICK),
    "None went in" to ImportUiState.NoneAdded(persistentListOf(damagedVideo)),
    "None, long list" to ImportUiState.NoneAdded(List(12) { if (it % 2 == 0) damagedVideo else cloudPhoto }.toPersistentList()),
    "Not enough space" to ImportUiState.NotEnoughSpace(neededBytes = 1_340_000_000, freeBytes = 412_000_000, canFreeUpSpace = true),
    "Not enough space, no button" to ImportUiState.NotEnoughSpace(neededBytes = 1_340_000_000, freeBytes = 412_000_000, canFreeUpSpace = false),
    "No picker" to ImportUiState.NoPicker,
)
