package app.memix.feature.stickerpack

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/** The WhatsApp packs flow (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md): Packs, the builder, then Added; the Emojis sheet is part of the builder. */
@Immutable
data class StickerPackUiState(
    /** False until the stickers and packs are read; the screen shows nothing yet, not an empty state. */
    val loaded: Boolean = false,
    /** My stickers, newest first, as paths in the sticker store. */
    val stickers: ImmutableList<String> = persistentListOf(),
    val packs: ImmutableList<PackRowUi> = persistentListOf(),
    val page: PackPage = PackPage.PACKS,
    val builder: BuilderUi = BuilderUi(),
    /** The pack the Added page is about. */
    val added: PackRowUi? = null,
    val toast: PackToast? = null,
    /** Set once a pack is ready: the screen opens WhatsApp's add screen, then tells the ViewModel with [StickerPackIntent.LaunchHandled]. */
    val launch: LaunchRequest? = null,
    /** The flow is over: the screen leaves. */
    val isClosing: Boolean = false,
)

enum class PackPage { PACKS, BUILDER, ADDED }

/** A pack in the list. [trayPath] is the tray icon's file in the pack's folder, [inWhatsApp] is what WhatsApp's own check said. */
@Immutable
data class PackRowUi(val id: String, val name: String, val stickerCount: Int, val trayPath: String, val inWhatsApp: Boolean)

@Immutable
data class BuilderUi(
    /** The pack this builder edits or has just saved, or null for a pack not made yet. */
    val packId: String? = null,
    /** The pack was in WhatsApp before: its button reads "Update in WhatsApp". */
    val addedBefore: Boolean = false,
    /** Goes up each time the builder opens, so the screen puts the pack's name into the field again. */
    val openCount: Int = 0,
    /** The name the field starts with; null for a new pack (the screen uses "My pack"). */
    val initialName: String? = null,
    /** Back from the builder returns to the packs list (it was opened from there). */
    val fromPacks: Boolean = false,
    /**
     * Stickers of the pack being edited that were deleted from My stickers: sticker path to the pack's own prepared copy. They stay in the
     * grid after the real stickers, selected, so the user sees what the pack holds and can unselect them.
     */
    val keptFromPack: ImmutableMap<String, String> = persistentMapOf(),
    val selected: ImmutableSet<String> = persistentSetOf(),
    /** Emojis by sticker; a sticker with no entry has the default one. */
    val emojis: ImmutableMap<String, ImmutableList<String>> = persistentMapOf(),
    /** Stickers that stay over WhatsApp's size limit; cleared when the selection changes. */
    val tooDetailed: ImmutableSet<String> = persistentSetOf(),
    val notice: BuilderNotice? = null,
    val preparing: PreparingUi? = null,
    val emojiSheet: EmojiSheetUi? = null,
)

/** Calm notices under the grid: none of them is an error. */
enum class BuilderNotice { NOT_ADDED, NO_WHATSAPP, WHATSAPP_TOO_OLD }

@Immutable
data class PreparingUi(val done: Int, val total: Int)

/** The Emojis sheet: the list of picked stickers, or the picker for [pickingFor]. */
@Immutable
data class EmojiSheetUi(val pickingFor: String? = null)

/** What to ask WhatsApp to add; [serial] tells two requests for the same pack apart. */
@Immutable
data class LaunchRequest(val packId: String, val packName: String, val serial: Long)

sealed interface PackToast {
    data object MinStickers : PackToast

    data object MaxStickers : PackToast

    data object MaxEmojis : PackToast

    data object MinEmojis : PackToast

    data object MaxPacks : PackToast

    data object SaveFailed : PackToast

    data object FileMissing : PackToast

    /** [packId] waits out its Undo window. */
    data class Deleted(val packId: String) : PackToast
}

sealed interface StickerPackIntent {
    data object NewPack : StickerPackIntent

    data class OpenPack(val packId: String) : StickerPackIntent

    /** A tile tap. [readable] is false for a tile that shows the missing-file icon. */
    data class ToggleSticker(val path: String, val readable: Boolean) : StickerPackIntent

    /** Add or Update in WhatsApp. [name] is the field's text; [defaultName] stands in for an empty one. */
    data class Add(val name: String, val defaultName: String) : StickerPackIntent

    data object OpenEmojis : StickerPackIntent

    data class PickEmojisFor(val path: String) : StickerPackIntent

    data class ToggleEmoji(val emoji: String) : StickerPackIntent

    /** Back inside the Emojis sheet: the picker returns to the list, the list closes the sheet. */
    data object EmojisBack : StickerPackIntent

    data class DeletePack(val packId: String) : StickerPackIntent

    data object UndoDelete : StickerPackIntent

    /** The screen has opened WhatsApp (or tried to). [opened] false: no WhatsApp could take the request. */
    data class LaunchHandled(val opened: Boolean) : StickerPackIntent

    /** The user is back from WhatsApp's add screen. */
    data object ReturnedFromWhatsApp : StickerPackIntent

    /** System back or ×. */
    data object Back : StickerPackIntent

    /** Done on the Added page. */
    data object Done : StickerPackIntent
}
