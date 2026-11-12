package app.memix.feature.sounds

import androidx.compose.runtime.Immutable
import app.memix.core.domain.Outcome
import app.memix.core.domain.mysound.DeleteMySoundUseCase
import app.memix.core.domain.mysound.ImportMySoundUseCase
import app.memix.core.domain.mysound.MY_SOUND_UNDO_WINDOW
import app.memix.core.domain.mysound.MySound
import app.memix.core.domain.mysound.MySoundFailure
import app.memix.core.domain.mysound.MySoundImport
import app.memix.core.domain.mysound.MySoundSource
import app.memix.core.domain.mysound.ObserveMySoundsUseCase
import app.memix.core.domain.mysound.PrepareMySoundUseCase
import app.memix.core.domain.mysound.RenameMySoundUseCase
import app.memix.core.domain.mysound.RenameOutcome
import app.memix.core.model.project.MediaOrigin
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What My sounds needs from the app, handed over together (a Koin factory) so ViewModel constructors stay readable. */
class MySoundTools(
    val observe: ObserveMySoundsUseCase,
    val importSound: ImportMySoundUseCase,
    val rename: RenameMySoundUseCase,
    val delete: DeleteMySoundUseCase,
    val prepare: PrepareMySoundUseCase,
)

/** The import's sheet (spec P3-07 → States). Absent while nothing is importing or showing a result. */
@Immutable
sealed interface MyImportUi {
    val source: MySoundSource

    /** Converting. The sheet stays hidden for the first 300 ms ([sheetShown] false), so a quick import just adds the sound. */
    data class Copying(override val source: MySoundSource, val sheetShown: Boolean, val percent: Int, val fraction: Float) : MyImportUi

    /** The result sheet "Couldn't add your sound". [displayName] is the picked item's name, shown only on screen. */
    data class Failed(override val source: MySoundSource, val reason: MySoundFailure, val displayName: String?) : MyImportUi

    /** Sizes include the headroom. */
    data class NotEnoughSpace(override val source: MySoundSource, val neededBytes: Long, val freeBytes: Long, val canFreeUpSpace: Boolean) : MyImportUi

    /** The phone has no app that can pick the file. */
    data class NoPicker(override val source: MySoundSource) : MyImportUi
}

/** Whether the import sheet (or the picker's import pane) is up. */
val MyImportUi?.showsSheet: Boolean
    get() = when (this) {
        null -> false
        is MyImportUi.Copying -> sheetShown
        is MyImportUi.Failed, is MyImportUi.NotEnoughSpace, is MyImportUi.NoPicker -> true
    }

/** Copying must finish or be cancelled; the results close like any sheet (spec P3-07 → Import sheet). */
val MyImportUi?.isBlocking: Boolean get() = this is MyImportUi.Copying

/** A deleted sound whose Undo toast is up. */
@Immutable
data class DeletedMineUi(val soundId: String, val name: String, val source: MySoundSource)

/** What the controller reports after each change. */
internal data class MineSnapshot(
    val sounds: List<MySound> = emptyList(),
    val importUi: MyImportUi? = null,
    val deleted: DeletedMineUi? = null,
)

/**
 * Owns My sounds for a [SoundsViewModel]: the list, one import at a time with its sheet, rename, and delete with Undo. Main
 * thread only, except the progress callback, which only reports. [onChanged] gets the snapshot after each change; [onAdded]
 * says a sound was added (the list scrolls to it); [onSaveFailed] says a rename or delete could not be written.
 *
 * No file name, path, length or size is logged anywhere here.
 */
internal class MySoundsController(
    private val scope: CoroutineScope,
    private val tools: MySoundTools,
    private val onChanged: (MineSnapshot) -> Unit,
    private val onAdded: (MySound) -> Unit,
    private val onSaveFailed: () -> Unit,
) {
    var snapshot = MineSnapshot()
        private set

    private var observing: Job? = null
    private var importJob: Job? = null

    // The pick, kept while "Not enough space" waits for the user to free some, so the import can carry on.
    private var waitingPick: Pick? = null
    private var canFreeUpSpace = true
    private var sheetShownAt: TimeMark? = null

    // Progress arrives on the converting thread and is applied on the main thread, where all of this state lives. Each import
    // has a token, so a late tick of a cancelled one is ignored.
    private var importToken = 0
    private var lastProgressAt: TimeMark? = null

    private data class Pick(val origin: MediaOrigin, val source: MySoundSource)

    private fun update(change: (MineSnapshot) -> MineSnapshot) {
        snapshot = change(snapshot)
        onChanged(snapshot)
    }

    /** Starts watching the list. Once. */
    fun start() {
        if (observing != null) return
        observing = scope.launch { tools.observe().collect { sounds -> update { it.copy(sounds = sounds) } } }
        // The Undo toast follows the use case's one window, so the toast and the file's deletion can't disagree.
        scope.launch {
            tools.delete.undoable.collect { removed ->
                update { it.copy(deleted = removed?.let { sound -> DeletedMineUi(sound.id, sound.name, sound.source) }) }
            }
        }
    }

    fun byId(id: String): MySound? = snapshot.sounds.firstOrNull { it.id == id }

    // ---- Import ----

    /** The picker returned [origin]: converts it. Ignored while another import runs. */
    fun import(origin: MediaOrigin, source: MySoundSource) {
        if (snapshot.importUi != null) return
        run(Pick(origin, source), sheetUp = false)
    }

    /** The picker returned nothing: no message, no event. */
    fun nothingPicked() = Unit

    /** The phone has no app for the picker: the sheet says so. */
    fun pickerUnavailable(source: MySoundSource) {
        if (snapshot.importUi == null) update { it.copy(importUi = MyImportUi.NoPicker(source)) }
    }

    private fun run(pick: Pick, sheetUp: Boolean) {
        sheetShownAt = if (sheetUp) TimeSource.Monotonic.markNow() else null
        lastProgressAt = null
        val token = ++importToken
        update { it.copy(importUi = MyImportUi.Copying(pick.source, sheetShown = sheetUp, percent = 0, fraction = 0f)) }
        importJob = scope.launch {
            val showSheetLater = launch {
                delay(SHEET_DELAY)
                showCopyingSheet()
            }
            val result = tools.importSound(pick.origin, pick.source) { fraction -> reportProgress(token, fraction) }
            showSheetLater.cancel()
            // Once up, the sheet stays at least MIN_SHEET_TIME, so it never flashes.
            sheetShownAt?.let { shownAt -> delay(MIN_SHEET_TIME - shownAt.elapsedNow()) }
            finish(pick, result)
        }
    }

    private fun showCopyingSheet() {
        val copying = snapshot.importUi as? MyImportUi.Copying ?: return
        if (copying.sheetShown) return
        sheetShownAt = TimeSource.Monotonic.markNow()
        update { it.copy(importUi = copying.copy(sheetShown = true)) }
    }

    // Called on the converting thread; applied on the main thread, at most 10 sheet updates a second, never 100% before the sound is added.
    private fun reportProgress(token: Int, fraction: Float) {
        scope.launch {
            if (token != importToken) return@launch
            val last = lastProgressAt
            if (last != null && last.elapsedNow() < PROGRESS_INTERVAL) return@launch
            lastProgressAt = TimeSource.Monotonic.markNow()
            val copying = snapshot.importUi as? MyImportUi.Copying ?: return@launch
            val percent = (fraction * PERCENT).toInt().coerceIn(0, MAX_PERCENT_WHILE_COPYING)
            update { it.copy(importUi = copying.copy(percent = percent, fraction = fraction.coerceIn(0f, 1f))) }
        }
    }

    private fun finish(pick: Pick, result: MySoundImport) {
        when (result) {
            is MySoundImport.Added -> {
                waitingPick = null
                sheetShownAt = null
                update { it.copy(importUi = null) }
                onAdded(result.sound)
            }
            is MySoundImport.Failed -> {
                waitingPick = null
                update { it.copy(importUi = MyImportUi.Failed(pick.source, result.failure, result.displayName)) }
            }
            is MySoundImport.NotEnoughSpace -> {
                waitingPick = pick
                update { it.copy(importUi = MyImportUi.NotEnoughSpace(pick.source, result.neededBytes, result.freeBytes, canFreeUpSpace)) }
            }
        }
    }

    /** Cancel, system back or Escape while converting: the pick is dropped, its files are deleted, nothing is said. */
    fun cancelImport() {
        if (snapshot.importUi !is MyImportUi.Copying) return
        importJob?.cancel()
        importJob = null
        importToken++
        sheetShownAt = null
        update { it.copy(importUi = null) }
    }

    /** Close, scrim, drag or back on a result sheet: the tab as it was. A waiting pick is dropped. */
    fun closeResult() {
        if (snapshot.importUi == null || snapshot.importUi is MyImportUi.Copying) return
        waitingPick = null
        sheetShownAt = null
        update { it.copy(importUi = null) }
    }

    /** The app is in the foreground again, maybe after the user freed space: a waiting pick carries on. */
    fun resumed() {
        val pick = waitingPick ?: return
        if (snapshot.importUi !is MyImportUi.NotEnoughSpace || importJob?.isActive == true) return
        waitingPick = null
        // The sheet is up already, so the conversion that follows keeps it up for the minimum time.
        run(pick, sheetUp = true)
    }

    /** Neither storage screen opened: the button goes away. */
    fun freeUpSpaceUnavailable() {
        canFreeUpSpace = false
        update { current -> (current.importUi as? MyImportUi.NotEnoughSpace)?.let { current.copy(importUi = it.copy(canFreeUpSpace = false)) } ?: current }
    }

    // ---- Rename ----

    /** Saves the typed name; [onResult] says whether the sheet may go back (an empty name stays, a failed write toasts). */
    fun rename(id: String, typed: String, onResult: (RenameOutcome) -> Unit) {
        scope.launch {
            val outcome = try {
                tools.rename(id, typed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onSaveFailed()
                return@launch
            }
            if (outcome == RenameOutcome.Missing) onSaveFailed()
            onResult(outcome)
        }
    }

    // ---- Delete ----

    /** Deletes [id] now; its file stays for [UNDO_WINDOW]. [onDeleted] runs once the row is gone. */
    fun delete(id: String, onDeleted: (MySound) -> Unit) {
        scope.launch {
            when (val result = tools.delete(id, MY_SOUND_UNDO_WINDOW)) {
                is Outcome.Failure -> onSaveFailed()
                is Outcome.Success -> onDeleted(result.value)
            }
        }
    }

    /** The Undo on the toast: the row comes back in place. */
    fun undoDelete() {
        val deleted = snapshot.deleted ?: return
        scope.launch {
            if (tools.delete.undo(deleted.soundId) is Outcome.Failure) onSaveFailed()
        }
    }

    private companion object {
        val SHEET_DELAY = 300.milliseconds
        val MIN_SHEET_TIME = 500.milliseconds
        val PROGRESS_INTERVAL = 100.milliseconds
        const val PERCENT = 100f
        const val MAX_PERCENT_WHILE_COPYING = 99
    }
}
