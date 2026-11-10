package app.memix.feature.mediaimport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

/**
 * An editor's import of a pick for its open project ([ProjectPickImport]): its sheet's state, read only by the sheet so the
 * screen doesn't recompose with each progress step. Compared by identity: one per editor.
 */
@Stable
class ProjectImportUi(internal val state: StateFlow<ImportUiState>)

/** The import sheet over an editor (P1-02's sheet with its own scrim). It reads the import's progress itself. */
@Composable
fun ProjectImportSheet(
    importUi: ProjectImportUi,
    wording: ImportWording,
    onIntent: (ImportIntent) -> Unit,
    onPickAgain: () -> Unit,
    onFreeUpSpace: (requestedBytes: Long) -> Unit,
) {
    val importState by importUi.state.collectAsState()
    ImportSheet(importState, onIntent, onPickAgain, onFreeUpSpace, drawScrim = true, wording = wording)
}

/** Whether the import sheet is up. Only that answer is read, so the caller doesn't recompose with each progress step. */
@Composable
fun rememberImportSheetShown(importUi: ProjectImportUi?): Boolean {
    val importState = importUi?.state?.collectAsState() ?: return false
    val shown by remember(importState) { derivedStateOf { importState.value.showsSheet } }
    return shown
}
