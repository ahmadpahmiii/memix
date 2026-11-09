package app.memix.feature.stickerpack

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.Menu
import app.memix.core.designsystem.component.MenuItem
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.edit_delete
import memix.feature.sticker_pack.generated.resources.Res
import memix.feature.sticker_pack.generated.resources.packs_empty_body
import memix.feature.sticker_pack.generated.resources.packs_empty_title
import memix.feature.sticker_pack.generated.resources.packs_in_whatsapp
import memix.feature.sticker_pack.generated.resources.packs_new
import memix.feature.sticker_pack.generated.resources.packs_sticker_count
import memix.feature.sticker_pack.generated.resources.packs_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The packs list (spec → Packs screen): one 72 dp row per pack, newest first; a long press offers Delete (an action for TalkBack too).
@Composable
internal fun PacksPage(state: StickerPackUiState, onIntent: (StickerPackIntent) -> Unit, thumbnails: PackThumbnails, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf<Pair<String, IntOffset>?>(null) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            PageHeader(stringResource(Res.string.packs_title), onClose = { onIntent(StickerPackIntent.Back) })
            if (state.packs.isEmpty()) {
                // The last pack was just deleted: the screen stays (Undo brings it back) and says what to do next.
                EmptyPacks(Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(state.packs, key = { it.id }, contentType = { "pack" }) { pack ->
                        PackRow(pack, thumbnails, onOpen = { onIntent(StickerPackIntent.OpenPack(pack.id)) }, onDelete = { onIntent(StickerPackIntent.DeletePack(pack.id)) }, onMenu = { id, at -> menu = id to at })
                    }
                }
            }
            Button(
                stringResource(Res.string.packs_new),
                { onIntent(StickerPackIntent.NewPack) },
                Modifier.fillMaxWidth().padding(MemixSpacing.space4),
                variant = ButtonVariant.Primary,
            )
        }
        val pressed = menu
        val deleteLabel = stringResource(UiRes.string.edit_delete)
        Menu(
            expanded = pressed != null,
            pressPoint = pressed?.second ?: IntOffset.Zero,
            title = deleteLabel,
            items = { listOf(MenuItem(deleteLabel, { pressed?.first?.let { onIntent(StickerPackIntent.DeletePack(it)) } })) },
            onDismiss = { menu = null },
        )
    }
}

@Composable
private fun EmptyPacks(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(MemixIcons.Sticker, null, MemixColors.textSecondary, size = MemixSpacing.space8)
        Text(stringResource(Res.string.packs_empty_title), MemixTheme.type.title, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.packs_empty_body), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PackRow(pack: PackRowUi, thumbnails: PackThumbnails, onOpen: () -> Unit, onDelete: () -> Unit, onMenu: (String, IntOffset) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val deleteLabel = stringResource(UiRes.string.edit_delete)
    val count = pluralStringResource(Res.plurals.packs_sticker_count, pack.stickerCount, pack.stickerCount)
    val inWhatsApp = stringResource(Res.string.packs_in_whatsapp)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ROW_HEIGHT)
                .background(if (pressed) MemixColors.hairline else MemixColors.surface)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(pack.id) {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interactionSource.emit(press)
                            tryAwaitRelease()
                            interactionSource.emit(PressInteraction.Release(press))
                        },
                        onTap = { onOpen() },
                        onLongPress = { offset ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMenu(pack.id, IntOffset((origin.x + offset.x).toInt(), (origin.y + offset.y).toInt()))
                        },
                    )
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick { onOpen(); true }
                    customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                }
                .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space3),
            horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrayThumbnail(pack.trayPath, thumbnails)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
                Text(pack.name, MemixTheme.type.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (pack.inWhatsApp) "$count · $inWhatsApp" else count, MemixTheme.type.label, color = MemixColors.textSecondary)
            }
        }
        Box(Modifier.fillMaxWidth().height(MemixStroke.strokeHairline).background(MemixColors.hairline))
    }
}

// The pack's tray sticker, 48 dp on `surface-raised`; decorative (the row's text names the pack).
@Composable
private fun TrayThumbnail(path: String, thumbnails: PackThumbnails) {
    val image: ImageBitmap? by produceState(thumbnails.cached(path).second, path) { value = thumbnails.load(path) }
    Box(Modifier.size(TRAY_SIZE).clip(MemixShapes.radiusSm).background(MemixColors.surfaceRaised), contentAlignment = Alignment.Center) {
        image?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize().padding(MemixSpacing.space1), contentScale = ContentScale.Fit) }
    }
}

private val ROW_HEIGHT = MemixSize.packRowHeight
private val TRAY_SIZE = MemixSize.touchTarget
