package app.memix.feature.export

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.IconButton
import app.memix.core.ui.scene.SavedPictureSource
import kotlin.math.max
import memix.feature.export.generated.resources.Res
import memix.feature.export.generated.resources.export_back_to_editor
import memix.feature.export.generated.resources.export_done
import memix.feature.export.generated.resources.export_done_title
import memix.feature.export.generated.resources.export_make_another
import memix.feature.export.generated.resources.export_share
import memix.feature.export.generated.resources.export_share_sheet_title
import memix.feature.export.generated.resources.photo_export_result_a11y
import memix.feature.export.generated.resources.photo_export_saved
import org.jetbrains.compose.resources.stringResource

/**
 * The Exported screen for a photo (spec docs/ux/specs/P2-08-photo-export.md → Exported screen): P1-12's screen with the saved
 * picture in place of the player (no play disc, no press state: the picture is not a control), "Saved to your gallery · JPG ·
 * 1080 × 1350", Share, and Make another with Done. [pictures] decodes the saved file at the size it is shown; [onExit] leaves.
 */
@Composable
fun PhotoExportedScreen(
    state: PhotoExportedUiState,
    onIntent: (PhotoExportedIntent) -> Unit,
    pictures: SavedPictureSource,
    onExit: (ExportExit) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.exit) { state.exit?.let(onExit) }
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = state.exit == null) {
        onIntent(PhotoExportedIntent.Back)
    }
    val titleFocus = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    val saved = state.picture != null
    // One Confirm when the file is saved and the screen shows (spec P1-12 → Haptics).
    LaunchedEffect(saved) { if (saved) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    BoxWithConstraints(modifier.fillMaxSize().background(MemixColors.canvas)) {
        val screenHeight = maxHeight
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(MemixSpacing.space4),
        ) {
            val picture = state.picture ?: return@BoxWithConstraints
            PictureScreen(
                ScreenSizes(area = maxHeight, screen = screenHeight),
                floor = RESULT_FLOOR,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Title(stringResource(Res.string.export_done_title), titleFocus, Modifier.weight(1f))
                        IconButton(MemixIcons.Close, stringResource(Res.string.export_back_to_editor), { onIntent(PhotoExportedIntent.BackToEditor) })
                    }
                },
                picture = { SavedPicture(picture, pictures) },
            ) {
                StatusPill(stringResource(Res.string.photo_export_saved, picture.format.label, "${picture.widthPx} × ${picture.heightPx}"))
                val chooserTitle = stringResource(Res.string.export_share_sheet_title)
                Button(
                    stringResource(Res.string.export_share),
                    { onIntent(PhotoExportedIntent.Share(chooserTitle)) },
                    Modifier.fillMaxWidth(),
                    leadingIcon = MemixIcons.Share,
                )
                Footer(
                    first = { Button(stringResource(Res.string.export_make_another), { onIntent(PhotoExportedIntent.MakeAnother) }, it) },
                    last = { Button(stringResource(Res.string.export_done), { onIntent(PhotoExportedIntent.Done) }, it, ButtonVariant.Primary) },
                )
            }
        }
    }
}

// The saved file in the canvas shape, decoded at the size it is shown. Not a control: no press state, a description only.
@Composable
private fun SavedPicture(picture: SavedPictureUi, pictures: SavedPictureSource) {
    val description = stringResource(Res.string.photo_export_result_a11y)
    val aspect = picture.aspectRatio
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val longSidePx = with(LocalDensity.current) { max(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        val decoded: ImageBitmap? by produceState<ImageBitmap?>(null, picture.location, longSidePx) {
            value = pictures.decode(picture.location, longSidePx)
        }
        val shape = Modifier
            .aspectRatio(aspect, matchHeightConstraintsFirst = maxWidth / maxHeight > aspect)
            .clip(MemixShapes.radiusLg)
            .background(MemixColors.stage)
            .semantics { contentDescription = description }
        val image = decoded
        if (image != null) {
            Image(image, contentDescription = null, modifier = shape, contentScale = ContentScale.Fit)
        } else {
            // The frame holds its place while the picture is decoded, so nothing jumps when it arrives.
            Box(shape)
        }
    }
}
