package app.memix.feature.photoeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.Menu
import app.memix.core.designsystem.component.MenuItem
import app.memix.core.designsystem.component.Sheet
import app.memix.core.designsystem.component.Tabs
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import kotlinx.collections.immutable.persistentListOf
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.edit_delete
import memix.feature.photo_editor.generated.resources.Res
import memix.feature.photo_editor.generated.resources.a11y_sticker_tile
import memix.feature.photo_editor.generated.resources.stickers_add_failed
import memix.feature.photo_editor.generated.resources.tool_add_photo
import memix.feature.photo_editor.generated.resources.stickers_deleted
import memix.feature.photo_editor.generated.resources.stickers_deleted_a11y
import memix.feature.photo_editor.generated.resources.stickers_empty_action
import memix.feature.photo_editor.generated.resources.stickers_empty_body
import memix.feature.photo_editor.generated.resources.stickers_empty_title
import memix.feature.photo_editor.generated.resources.stickers_file_missing
import memix.feature.photo_editor.generated.resources.stickers_tab_mine
import memix.feature.photo_editor.generated.resources.stickers_title
import memix.feature.photo_editor.generated.resources.stickers_undo
import org.jetbrains.compose.resources.stringResource

/** The sticker sources the sheet can show. Today only My stickers; P4-15 adds a pack as one more entry and the tab row appears (spec P2-14 → Layout). */
private enum class StickerSource { MY_STICKERS }

private val SOURCES = persistentListOf(StickerSource.MY_STICKERS)

// The Stickers sheet (spec docs/ux/specs/P2-14-sticker-picker.md): the grid of My stickers, newest first; a tap lands one, a long press offers Delete
// with Undo. Lives at the root of the editor screen, so its Menu and toast are placed in window coordinates.
@Composable
internal fun StickersSheet(state: PhotoEditorUiState, bitmaps: SceneBitmaps, onIntent: (PhotoEditorIntent) -> Unit) {
    val sheet = state.stickers
    // Kept for the sheet's slide-out, once the state is gone.
    val last = remember { arrayOfNulls<StickersUi>(1) }
    if (sheet != null) last[0] = sheet
    // Keyed on open/closed: the effect of the open sheet is disposed when it closes, and that is when the thumbnails go.
    DisposableEffect(sheet != null) { onDispose { bitmaps.releaseStickerThumbnails() } }
    var menu by remember { mutableStateOf<Pair<String, IntOffset>?>(null) }
    Box(Modifier.fillMaxSize()) {
        Sheet(visible = sheet != null, onDismiss = { onIntent(PhotoEditorIntent.CloseStickers) }, title = stringResource(Res.string.stickers_title)) {
            val shown = sheet ?: last[0]
            if (shown != null) StickersContent(shown, state.layers.any { it.kind == LayerKind.IMAGE }, bitmaps, onIntent, onMenu = { path, at -> menu = path to at })
        }
        val pressed = menu
        val deleteLabel = stringResource(UiRes.string.edit_delete)
        Menu(
            expanded = pressed != null && sheet != null,
            pressPoint = pressed?.second ?: IntOffset.Zero,
            title = deleteLabel,
            items = { listOf(MenuItem(deleteLabel, { pressed?.first?.let { onIntent(PhotoEditorIntent.DeleteSticker(it)) } })) },
            onDismiss = { menu = null },
        )
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(MemixSpacing.space4), contentAlignment = Alignment.TopCenter) {
            FadingToast(sheet?.toast) { toast -> StickersToastView(toast, onIntent) }
        }
    }
}

@Composable
private fun StickersToastView(toast: StickersToast, onIntent: (PhotoEditorIntent) -> Unit) {
    val deletedA11y = stringResource(Res.string.stickers_deleted_a11y)
    when (toast) {
        is StickersToast.Deleted -> Toast(
            stringResource(Res.string.stickers_deleted),
            action = ToastAction(stringResource(Res.string.stickers_undo)) { onIntent(PhotoEditorIntent.UndoDeleteSticker) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = deletedA11y },
        )
        StickersToast.FileMissing -> Toast(stringResource(Res.string.stickers_file_missing), Modifier.fillMaxWidth())
        StickersToast.AddFailed -> Toast(stringResource(Res.string.stickers_add_failed), Modifier.fillMaxWidth())
    }
}

@Composable
private fun StickersContent(sheet: StickersUi, hasImage: Boolean, bitmaps: SceneBitmaps, onIntent: (PhotoEditorIntent) -> Unit, onMenu: (String, IntOffset) -> Unit) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Column(Modifier.fillMaxWidth().heightIn(max = windowHeight * SHEET_MAX_SHARE), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        // With one source there is no tab row: the sheet just reads "Stickers" over the grid.
        if (SOURCES.size > 1) Tabs(SOURCES.map { stringResource(Res.string.stickers_tab_mine) }, selectedIndex = 0, onSelect = {})
        when {
            !sheet.loaded -> Unit
            sheet.items.isEmpty() -> EmptyState(hasImage, onIntent)
            else -> StickerGrid(sheet, bitmaps, onIntent, onMenu)
        }
    }
}

@Composable
private fun StickerGrid(sheet: StickersUi, bitmaps: SceneBitmaps, onIntent: (PhotoEditorIntent) -> Unit, onMenu: (String, IntOffset) -> Unit) {
    LazyVerticalGrid(
        GridCells.Adaptive(TILE_MIN_WIDTH),
        // The sheet already pads its content by `space-4` on each side, which is what makes three tiles fit at 360 dp.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = MemixSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
    ) {
        itemsIndexed(sheet.items, key = { _, path -> path }, contentType = { _, _ -> "sticker" }) { index, path ->
            StickerTile(path, index + 1, sheet.items.size, bitmaps, copying = sheet.placing == path, onTap = { onIntent(PhotoEditorIntent.PickSticker(path)) }, onDelete = { onIntent(PhotoEditorIntent.DeleteSticker(path)) }, onMenu = onMenu)
        }
    }
}

// A `surface-raised` square, the picture fitted inside with `space-2` padding; a file that can't be read shows the photo icon (spec → States).
@Composable
private fun StickerTile(path: String, position: Int, count: Int, bitmaps: SceneBitmaps, copying: Boolean, onTap: () -> Unit, onDelete: () -> Unit, onMenu: (String, IntOffset) -> Unit) {
    val picture by produceState<Pair<Boolean, ImageBitmap?>>(false to null, path) { value = true to bitmaps.stickerThumbnail(path) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val name = stringResource(Res.string.a11y_sticker_tile, position, count)
    val deleteLabel = stringResource(UiRes.string.edit_delete)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MemixShapes.radiusMd)
            .background(if (pressed || copying) MemixColors.hairline else MemixColors.surfaceRaised)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(path) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
                    },
                    onTap = { onTap() },
                    onLongPress = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMenu(path, IntOffset((origin.x + offset.x).toInt(), (origin.y + offset.y).toInt()))
                    },
                )
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = name
                onClick { onTap(); true }
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        val (decoded, image) = picture
        when {
            image != null -> Image(BitmapPainter(image), null, Modifier.fillMaxSize().padding(MemixSpacing.space2), contentScale = ContentScale.Fit)
            decoded -> Icon(MemixIcons.Photo, null, MemixColors.textMuted)
        }
    }
}

// The tool exists before the first cut-out, so the empty list is the way to make one: Remove background (the blue one) when there is a photo,
// Add photo (no blue) when there is not (spec → Empty state).
@Composable
private fun EmptyState(hasImage: Boolean, onIntent: (PhotoEditorIntent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(MemixIcons.Sticker, null, MemixColors.textSecondary, size = EMPTY_ICON_SIZE)
        Text(stringResource(Res.string.stickers_empty_title), MemixTheme.type.title, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.stickers_empty_body), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
        if (hasImage) {
            Button(stringResource(Res.string.stickers_empty_action), { onIntent(PhotoEditorIntent.StickersEmptyAction) }, variant = ButtonVariant.Primary)
        } else {
            Button(stringResource(Res.string.tool_add_photo), { onIntent(PhotoEditorIntent.StickersEmptyAction) }, variant = ButtonVariant.Secondary)
        }
    }
}

private val TILE_MIN_WIDTH = 96.dp
private val EMPTY_ICON_SIZE = 32.dp
private const val SHEET_MAX_SHARE = 0.7f
