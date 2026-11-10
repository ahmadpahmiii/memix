package app.memix.feature.stickerpack

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.Sheet
import app.memix.core.model.stickerpack.StickerPackLimits
import kotlinx.collections.immutable.ImmutableList
import memix.feature.sticker_pack.generated.resources.Res
import memix.feature.sticker_pack.generated.resources.a11y_pack_emoji_row
import memix.feature.sticker_pack.generated.resources.pack_back
import memix.feature.sticker_pack.generated.resources.pack_done
import memix.feature.sticker_pack.generated.resources.pack_emojis
import memix.feature.sticker_pack.generated.resources.pack_emojis_change
import memix.feature.sticker_pack.generated.resources.pack_emojis_help
import memix.feature.sticker_pack.generated.resources.pack_emojis_pick_title
import org.jetbrains.compose.resources.stringResource

// The Emojis sheet (spec → Emojis sheet): two panes in one sheet. The list shows each picked sticker with its emojis; a tap opens the picker
// of 48 emojis for it (1 to 3). Changes apply at once.
@Composable
internal fun EmojisSheet(state: StickerPackUiState, onIntent: (StickerPackIntent) -> Unit, thumbnails: PackThumbnails) {
    val sheet = state.builder.emojiSheet
    // Kept for the slide-out, once the state is gone.
    val last = remember { arrayOfNulls<EmojiSheetUi>(1) }
    if (sheet != null) last[0] = sheet
    val shown = sheet ?: last[0]
    val title = stringResource(if (shown?.pickingFor != null) Res.string.pack_emojis_pick_title else Res.string.pack_emojis)
    Sheet(
        visible = sheet != null,
        onDismiss = { onIntent(StickerPackIntent.EmojisBack) },
        title = title,
        // The picker's back arrow sits at the start of the title row, where the close X is at the end.
        titleLeading = if (shown?.pickingFor != null) {
            { IconButton(MemixIcons.ArrowBack, stringResource(Res.string.pack_back), { onIntent(StickerPackIntent.EmojisBack) }) }
        } else {
            null
        },
    ) {
        val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        Column(Modifier.fillMaxWidth().heightIn(max = windowHeight * SHEET_MAX_SHARE), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
            val picking = shown?.pickingFor
            if (picking == null) {
                EmojiList(state, onIntent, thumbnails)
            } else {
                EmojiPicker(state.builder.emojis[picking] ?: DEFAULT_EMOJIS, onIntent)
            }
        }
    }
}

@Composable
private fun ColumnScope.EmojiList(state: StickerPackUiState, onIntent: (StickerPackIntent) -> Unit, thumbnails: PackThumbnails) {
    val picked = remember(state.stickers, state.builder.keptFromPack, state.builder.selected) { (state.stickers + state.builder.keptFromPack.keys).filter { it in state.builder.selected } }
    Text(stringResource(Res.string.pack_emojis_help), MemixTheme.type.caption, color = MemixColors.textSecondary)
    LazyColumn(Modifier.weight(1f, fill = false)) {
        itemsIndexed(picked, key = { _, path -> path }, contentType = { _, _ -> "row" }) { index, path ->
            EmojiRow(state.builder.keptFromPack[path] ?: path, index + 1, state.builder.emojis[path] ?: DEFAULT_EMOJIS, thumbnails) { onIntent(StickerPackIntent.PickEmojisFor(path)) }
        }
    }
    Button(stringResource(Res.string.pack_done), { onIntent(StickerPackIntent.EmojisBack) }, Modifier.fillMaxWidth(), ButtonVariant.Primary)
}

@Composable
private fun EmojiRow(path: String, position: Int, emojis: List<String>, thumbnails: PackThumbnails, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val picture by produceState(thumbnails.cached(path).second, path) { value = thumbnails.load(path) }
    val name = stringResource(Res.string.a11y_pack_emoji_row, position, emojis.joinToString(" "))
    val changeLabel = stringResource(Res.string.pack_emojis_change)
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ROW_HEIGHT)
            .clip(MemixShapes.radiusMd)
            .background(if (pressed) MemixColors.hairline else MemixColors.surface)
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = name
                onClick(changeLabel) { onClick(); true }
            },
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(MemixSize.touchTarget).clip(MemixShapes.radiusSm).background(MemixColors.surfaceRaised), contentAlignment = Alignment.Center) {
            picture?.let { Image(BitmapPainter(it), null, Modifier.padding(MemixSpacing.space1), contentScale = ContentScale.Fit) }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            emojis.forEach { Text(it, MemixTheme.type.title) }
        }
        Icon(MemixIcons.ChevronRight, null, MemixColors.textSecondary)
    }
}

@Composable
private fun ColumnScope.EmojiPicker(chosen: ImmutableList<String>, onIntent: (StickerPackIntent) -> Unit) {
    LazyVerticalGrid(GridCells.Fixed(EMOJI_COLUMNS), Modifier.weight(1f, fill = false)) {
        items(StickerPackLimits.EMOJI_CHOICES, key = { it }, contentType = { "emoji" }) { emoji ->
            EmojiTile(emoji, selected = emoji in chosen) { onIntent(StickerPackIntent.ToggleEmoji(emoji)) }
        }
    }
}

// 48 dp target; the chosen ones are filled with `selection`. TalkBack reads an emoji by its own name, so there is no string here.
@Composable
private fun EmojiTile(emoji: String, selected: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .background(if (selected) MemixColors.selection else MemixColors.surface)
            .toggleable(selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, MemixTheme.type.title)
    }
}

private val ROW_HEIGHT = MemixSize.emojiRowHeight
private const val EMOJI_COLUMNS = 6
private const val SHEET_MAX_SHARE = MemixSize.sheetMaxHeightFraction
private val DEFAULT_EMOJIS = kotlinx.collections.immutable.persistentListOf(StickerPackLimits.DEFAULT_EMOJI)
