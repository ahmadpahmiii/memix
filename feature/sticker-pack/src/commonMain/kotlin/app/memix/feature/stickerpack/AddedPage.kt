package app.memix.feature.stickerpack

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import memix.feature.sticker_pack.generated.resources.Res
import memix.feature.sticker_pack.generated.resources.pack_added_body
import memix.feature.sticker_pack.generated.resources.pack_added_pill
import memix.feature.sticker_pack.generated.resources.pack_added_title
import memix.feature.sticker_pack.generated.resources.pack_done
import org.jetbrains.compose.resources.stringResource

// The result (spec → Result screen "Added"): the tray sticker, a green pill, where to find the pack in WhatsApp, and Done.
@Composable
internal fun AddedPage(state: StickerPackUiState, onIntent: (StickerPackIntent) -> Unit, thumbnails: PackThumbnails, modifier: Modifier = Modifier) {
    val pack = state.added
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PageHeader(stringResource(Res.string.pack_added_title), onClose = { onIntent(StickerPackIntent.Back) })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MemixSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space4, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (pack != null) {
                TrayPicture(pack.trayPath, thumbnails)
                AddedPill(stringResource(Res.string.pack_added_pill))
                Text(stringResource(Res.string.pack_added_body, pack.name), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
            }
        }
        Button(
            stringResource(Res.string.pack_done),
            { onIntent(StickerPackIntent.Done) },
            Modifier.fillMaxWidth().padding(MemixSpacing.space4),
            variant = ButtonVariant.Primary,
        )
    }
}

@Composable
private fun TrayPicture(path: String, thumbnails: PackThumbnails) {
    val image by produceState(thumbnails.cached(path).second, path) { value = thumbnails.load(path) }
    Box(Modifier.size(TRAY_SIZE).clip(MemixShapes.radiusLg).background(MemixColors.surfaceRaised), contentAlignment = Alignment.Center) {
        image?.let { Image(BitmapPainter(it), null, Modifier.fillMaxSize().padding(MemixSpacing.space2), contentScale = ContentScale.Fit) }
    }
}

// A `success` pill with a check; one merged live node, so the result is announced once.
@Composable
private fun AddedPill(text: String) {
    Row(
        Modifier
            .clip(MemixShapes.radiusFull)
            .background(MemixColors.success)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = MemixSpacing.space3, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MemixIcons.Check, null, MemixColors.onTrack, size = MemixSize.iconSmall)
        Text(text, MemixTheme.type.label, color = MemixColors.onTrack)
    }
}

private val TRAY_SIZE = MemixSize.packTraySize
