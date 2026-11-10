package app.memix.feature.stickerpack

import androidx.lifecycle.viewModelScope
import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent
import app.memix.core.domain.EditorTool
import app.memix.core.domain.stickerpack.CheckPackInWhatsAppUseCase
import app.memix.core.domain.stickerpack.DeleteStickerPackUseCase
import app.memix.core.domain.stickerpack.ListStickerPacksUseCase
import app.memix.core.domain.stickerpack.PackPreparation
import app.memix.core.domain.stickerpack.PackRequest
import app.memix.core.domain.stickerpack.PackStickerRequest
import app.memix.core.domain.stickerpack.PrepareStickerPackUseCase
import app.memix.core.domain.stickerpack.StickerPackStore
import app.memix.core.domain.stickerpack.WhatsAppCheck
import app.memix.core.domain.sticker.ListStickersUseCase
import app.memix.core.model.project.ProjectType
import app.memix.core.model.stickerpack.StickerPack
import app.memix.core.model.stickerpack.StickerPackLimits
import app.memix.core.ui.MemixViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The WhatsApp packs flow (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md). Selections and emojis made on the builder stay here until
 * the flow closes, so back never loses them; a pack is saved when Add succeeds in preparing, not before.
 */
class StickerPackViewModel(
    private val listStickers: ListStickersUseCase,
    private val listPacks: ListStickerPacksUseCase,
    private val preparePack: PrepareStickerPackUseCase,
    private val deletePack: DeleteStickerPackUseCase,
    private val checkInWhatsApp: CheckPackInWhatsAppUseCase,
    private val analytics: Analytics,
) : MemixViewModel<StickerPackUiState, StickerPackIntent>(StickerPackUiState()) {
    // The saved packs behind the rows, for editing.
    private var packsById: Map<String, StickerPack> = emptyMap()
    private var prepareJob: Job? = null
    private var toastJob: Job? = null
    private var deleteJob: Job? = null
    private var pendingDelete: String? = null
    private var launchSerial = 0L

    init {
        viewModelScope.launch {
            // Before anything is written: a delete whose window never ended and a save that was cut short go.
            deletePack.cleanUp()
            val stickers = listStickers().toImmutableList()
            updateState { it.copy(stickers = stickers) }
            reloadPacks()
            if (packsById.isEmpty()) openBuilder(editing = null)
            updateState { it.copy(loaded = true) }
            markPacksInWhatsApp()
        }
    }

    override fun onIntent(intent: StickerPackIntent) {
        when (intent) {
            StickerPackIntent.NewPack -> newPack()
            is StickerPackIntent.OpenPack -> openBuilder(packsById[intent.packId])
            is StickerPackIntent.ToggleSticker -> toggleSticker(intent.path, intent.readable)
            is StickerPackIntent.Add -> add(intent.name, intent.defaultName)
            StickerPackIntent.OpenEmojis -> updateBuilder { it.copy(emojiSheet = EmojiSheetUi()) }
            is StickerPackIntent.PickEmojisFor -> updateBuilder { it.copy(emojiSheet = EmojiSheetUi(pickingFor = intent.path)) }
            is StickerPackIntent.ToggleEmoji -> toggleEmoji(intent.emoji)
            StickerPackIntent.EmojisBack -> emojisBack()
            is StickerPackIntent.DeletePack -> deleteWithUndo(intent.packId)
            StickerPackIntent.UndoDelete -> undoDelete()
            is StickerPackIntent.LaunchHandled -> launchHandled(intent.opened)
            StickerPackIntent.ReturnedFromWhatsApp -> returnedFromWhatsApp()
            StickerPackIntent.Back -> back()
            StickerPackIntent.Done -> close()
        }
    }

    // Packs and the builder ---------------------------------------------------------------------------------------------

    private fun newPack() {
        // A pack waiting out its Undo window still counts: Undo would bring it back beside the new one and go over WhatsApp's 10.
        val waitingForUndo = if (pendingDelete != null) 1 else 0
        if (packsById.size + waitingForUndo >= StickerPackLimits.MAX_PACKS) return showToast(PackToast.MaxPacks)
        openBuilder(editing = null)
    }

    private fun openBuilder(editing: StickerPack?) {
        // Editing starts from all of the pack's stickers; those deleted from My stickers show from the pack's own copy.
        val kept = editing?.stickers.orEmpty()
        analytics.log(AnalyticsEvent.ToolUse(ProjectType.PHOTO, EditorTool.MAKE_PACK))
        updateState { current ->
            current.copy(
                page = PackPage.BUILDER,
                builder = BuilderUi(
                    packId = editing?.id,
                    addedBefore = editing?.addedToWhatsAppAtEpochUs != null,
                    openCount = current.builder.openCount + 1,
                    initialName = editing?.name,
                    fromPacks = packsById.isNotEmpty(),
                    keptFromPack = editing?.let(::keptFromPack) ?: persistentMapOf(),
                    selected = kept.map { it.sourcePath }.toImmutableSet(),
                    emojis = kept.associate { it.sourcePath to it.emojis.toImmutableList() }.toImmutableMap(),
                ),
            )
        }
    }

    private fun toggleSticker(path: String, readable: Boolean) {
        val builder = state.value.builder
        if (builder.preparing != null) return
        if (!readable) return showToast(PackToast.FileMissing)
        val selecting = path !in builder.selected
        if (selecting && builder.selected.size >= StickerPackLimits.MAX_STICKERS) return showToast(PackToast.MaxStickers)
        updateBuilder {
            // A changed selection ends the "too detailed" and WhatsApp notices: they were about the last one.
            it.copy(selected = (if (selecting) it.selected + path else it.selected - path).toImmutableSet(), tooDetailed = persistentSetOf(), notice = null)
        }
    }

    private fun add(name: String, defaultName: String) {
        val builder = state.value.builder
        if (builder.preparing != null) return
        if (builder.selected.size < StickerPackLimits.MIN_STICKERS) return showToast(PackToast.MinStickers)
        val ordered = (state.value.stickers + builder.keptFromPack.keys).filter { it in builder.selected }
        val request = PackRequest(
            existing = builder.packId?.let(packsById::get),
            name = name.trim().ifEmpty { defaultName },
            stickers = ordered.map {
                PackStickerRequest(it, builder.emojis[it] ?: listOf(StickerPackLimits.DEFAULT_EMOJI), encodeFrom = builder.keptFromPack[it] ?: it)
            },
        )
        updateBuilder { it.copy(preparing = PreparingUi(0, ordered.size), notice = null, tooDetailed = persistentSetOf()) }
        prepareJob = viewModelScope.launch {
            val result = try {
                preparePack(request) { done, total -> updateBuilder { if (it.preparing == null) it else it.copy(preparing = PreparingUi(done, total)) } }
            } catch (e: CancellationException) {
                updateBuilder { it.copy(preparing = null) }
                throw e
            }
            updateBuilder { it.copy(preparing = null) }
            handle(result)
        }
    }

    private suspend fun handle(result: PackPreparation) {
        when (result) {
            is PackPreparation.Ready -> packReady(result.pack)
            is PackPreparation.TooDetailed -> updateBuilder { it.copy(tooDetailed = result.sourcePaths.toImmutableSet()) }
            is PackPreparation.Missing -> showToast(PackToast.FileMissing)
            PackPreparation.NoSpace, PackPreparation.Failed -> showToast(PackToast.SaveFailed)
        }
    }

    // The pack is saved. WhatsApp is asked only when it is there and new enough; otherwise the pack waits, saved, for later.
    private suspend fun packReady(pack: StickerPack) {
        reloadPacks()
        // The prepared copies are new files now (the old version's went), so the kept stickers point at the new ones.
        updateBuilder { it.copy(packId = pack.id, addedBefore = pack.addedToWhatsAppAtEpochUs != null, keptFromPack = keptFromPack(pack)) }
        analytics.log(AnalyticsEvent.ToolUse(ProjectType.PHOTO, EditorTool.ADD_TO_WHATSAPP))
        when (checkInWhatsApp(pack.id)) {
            WhatsAppCheck.NOT_INSTALLED -> updateBuilder { it.copy(notice = BuilderNotice.NO_WHATSAPP) }
            WhatsAppCheck.TOO_OLD -> updateBuilder { it.copy(notice = BuilderNotice.WHATSAPP_TOO_OLD) }
            WhatsAppCheck.NOT_ADDED, WhatsAppCheck.ADDED -> updateState { it.copy(launch = LaunchRequest(pack.id, pack.name, ++launchSerial)) }
        }
    }

    private fun launchHandled(opened: Boolean) {
        updateState { current ->
            val builder = if (opened) current.builder else current.builder.copy(notice = BuilderNotice.NO_WHATSAPP)
            current.copy(launch = null, builder = builder)
        }
    }

    // WhatsApp documents no result code, so its own check says what the user did there.
    private fun returnedFromWhatsApp() {
        val packId = state.value.builder.packId ?: return
        viewModelScope.launch {
            when (checkInWhatsApp(packId)) {
                WhatsAppCheck.ADDED -> {
                    analytics.log(AnalyticsEvent.ShareTarget(SHARE_TARGET_WHATSAPP))
                    reloadPacks()
                    val row = state.value.packs.firstOrNull { it.id == packId }
                    updateState { it.copy(page = PackPage.ADDED, added = row, builder = it.builder.copy(addedBefore = true, notice = null)) }
                }
                WhatsAppCheck.NOT_ADDED -> updateBuilder { it.copy(notice = BuilderNotice.NOT_ADDED) }
                WhatsAppCheck.NOT_INSTALLED -> updateBuilder { it.copy(notice = BuilderNotice.NO_WHATSAPP) }
                WhatsAppCheck.TOO_OLD -> updateBuilder { it.copy(notice = BuilderNotice.WHATSAPP_TOO_OLD) }
            }
        }
    }

    // Emojis ------------------------------------------------------------------------------------------------------------

    private fun toggleEmoji(emoji: String) {
        val builder = state.value.builder
        val path = builder.emojiSheet?.pickingFor ?: return
        val current = builder.emojis[path] ?: listOf(StickerPackLimits.DEFAULT_EMOJI)
        val next = when {
            emoji in current -> if (current.size <= StickerPackLimits.MIN_EMOJIS) return showToast(PackToast.MinEmojis) else current - emoji
            current.size >= StickerPackLimits.MAX_EMOJIS -> return showToast(PackToast.MaxEmojis)
            else -> current + emoji
        }
        updateBuilder { it.copy(emojis = (it.emojis + (path to next.toImmutableList())).toImmutableMap()) }
    }

    private fun emojisBack() {
        updateBuilder { builder ->
            val sheet = builder.emojiSheet
            builder.copy(emojiSheet = if (sheet?.pickingFor != null) EmojiSheetUi() else null)
        }
    }

    // Deleting ----------------------------------------------------------------------------------------------------------

    // The pack leaves the list and WhatsApp's provider at once; its files go when the 8 s window ends. WhatsApp keeps its own copy.
    private fun deleteWithUndo(packId: String) {
        if (packsById[packId] == null) return
        finishPendingDelete()
        pendingDelete = packId
        packsById = packsById - packId
        updateState { it.copy(packs = it.packs.filterNot { row -> row.id == packId }.toImmutableList()) }
        showToast(PackToast.Deleted(packId), millis = UNDO_MILLIS)
        deleteJob = viewModelScope.launch {
            deletePack.hide(packId)
            delay(UNDO_MILLIS)
            finishPendingDelete()
        }
    }

    private fun undoDelete() {
        val packId = pendingDelete ?: return
        deleteJob?.cancel()
        pendingDelete = null
        clearToast()
        viewModelScope.launch {
            deletePack.undo(packId)
            reloadPacks()
        }
    }

    private fun finishPendingDelete() {
        val packId = pendingDelete ?: return
        deleteJob?.cancel()
        pendingDelete = null
        viewModelScope.launch { deletePack.finish(packId) }
    }

    // Leaving -----------------------------------------------------------------------------------------------------------

    private fun back() {
        val current = state.value
        when {
            current.builder.emojiSheet != null -> emojisBack()
            current.builder.preparing != null -> prepareJob?.cancel()
            current.page == PackPage.ADDED -> updateState { it.copy(page = PackPage.BUILDER) }
            current.page == PackPage.BUILDER && current.builder.fromPacks -> updateState { it.copy(page = PackPage.PACKS) }
            else -> close()
        }
    }

    private fun close() {
        finishPendingDelete()
        updateState { it.copy(isClosing = true) }
    }

    // Helpers -----------------------------------------------------------------------------------------------------------

    private suspend fun reloadPacks() {
        val packs = listPacks()
        packsById = packs.associateBy { it.id }
        val previous = state.value.packs.associate { it.id to it.inWhatsApp }
        updateState { it.copy(packs = packs.map { pack -> pack.toRow(inWhatsApp = previous[pack.id] ?: false) }.toPersistentList()) }
    }

    // "In WhatsApp" shows when WhatsApp reports the pack, one pack at a time: the check is a call into another app.
    private suspend fun markPacksInWhatsApp() {
        for (pack in packsById.values.toList()) {
            val added = checkInWhatsApp(pack.id) == WhatsAppCheck.ADDED
            if (!added) continue
            updateState { current -> current.copy(packs = current.packs.map { if (it.id == pack.id) it.copy(inWhatsApp = true) else it }.toImmutableList()) }
        }
    }

    private fun keptFromPack(pack: StickerPack): kotlinx.collections.immutable.ImmutableMap<String, String> {
        val inStore = state.value.stickers.toSet()
        return pack.stickers.filter { it.sourcePath !in inStore }
            .associate { it.sourcePath to StickerPackStore.pathOf(pack.id, it.file) }.toImmutableMap()
    }

    private fun StickerPack.toRow(inWhatsApp: Boolean) =
        PackRowUi(id, name, stickers.size, StickerPackStore.pathOf(id, trayFile), inWhatsApp)

    private fun updateBuilder(transform: (BuilderUi) -> BuilderUi) = updateState { it.copy(builder = transform(it.builder)) }

    private fun showToast(toast: PackToast, millis: Long = TOAST_MILLIS) {
        toastJob?.cancel()
        updateState { it.copy(toast = toast) }
        toastJob = viewModelScope.launch {
            delay(millis)
            updateState { it.copy(toast = null) }
        }
    }

    private fun clearToast() {
        toastJob?.cancel()
        updateState { it.copy(toast = null) }
    }

    private companion object {
        const val TOAST_MILLIS = 3_000L
        const val UNDO_MILLIS = 8_000L
        const val SHARE_TARGET_WHATSAPP = "whatsapp"
    }
}
