package app.memix.feature.stickerpack

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.memix.core.designsystem.DisplayText
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.component.CloseButton
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import app.memix.core.ui.scene.SceneImageSource
import memix.feature.sticker_pack.generated.resources.Res
import memix.feature.sticker_pack.generated.resources.explain_pack_emoji_max
import memix.feature.sticker_pack.generated.resources.explain_pack_emoji_min
import memix.feature.sticker_pack.generated.resources.explain_pack_max
import memix.feature.sticker_pack.generated.resources.explain_pack_min
import memix.feature.sticker_pack.generated.resources.explain_packs_max
import memix.feature.sticker_pack.generated.resources.pack_file_missing
import memix.feature.sticker_pack.generated.resources.pack_save_failed
import memix.feature.sticker_pack.generated.resources.packs_deleted
import memix.feature.sticker_pack.generated.resources.packs_undo
import org.jetbrains.compose.resources.stringResource

/**
 * The WhatsApp packs flow (spec docs/ux/specs/P2-09-whatsapp-sticker-pack.md): the Packs list, the builder and the Added page, one pushed
 * over the other. [imageSource] decodes the thumbnails; [hasImage] picks the action of the "need 3 stickers" state; [onEmptyAction] runs it
 * (the editor leaves this screen and opens Remove background or the photo picker); [onOpenWhatsApp] asks WhatsApp to add a pack and says
 * whether any WhatsApp took the request; [onClose] leaves the flow.
 */
@Composable
fun StickerPackScreen(
    state: StickerPackUiState,
    onIntent: (StickerPackIntent) -> Unit,
    imageSource: SceneImageSource,
    hasImage: Boolean,
    onEmptyAction: () -> Unit,
    onOpenWhatsApp: (packId: String, packName: String) -> Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(state.isClosing) { if (state.isClosing) currentOnClose() }
    val currentOpenWhatsApp by rememberUpdatedState(onOpenWhatsApp)
    LaunchedEffect(state.launch) {
        val request = state.launch ?: return@LaunchedEffect
        onIntent(StickerPackIntent.LaunchHandled(currentOpenWhatsApp(request.packId, request.packName)))
    }
    // The Emojis sheet has its own back handling while it is open.
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = !state.isClosing && state.builder.emojiSheet == null) {
        onIntent(StickerPackIntent.Back)
    }
    val thumbnails = remember(imageSource) { PackThumbnails(imageSource) }
    val nameField = rememberNameField(state.builder)
    val reduceMotion = MemixTheme.reduceMotion
    Box(modifier.fillMaxSize().background(MemixColors.canvas)) {
        if (state.loaded) {
            AnimatedContent(state.page, transitionSpec = { pageTransition(initialState, targetState, reduceMotion) }, label = "pack page") { page ->
                when (page) {
                    PackPage.PACKS -> PacksPage(state, onIntent, thumbnails)
                    PackPage.BUILDER -> BuilderPage(state, onIntent, thumbnails, hasImage, onEmptyAction, nameField)
                    PackPage.ADDED -> AddedPage(state, onIntent, thumbnails)
                }
            }
        }
        EmojisSheet(state, onIntent, thumbnails)
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(MemixSpacing.space4), contentAlignment = Alignment.TopCenter) {
            FadingToast(state.toast) { toast -> PackToastView(toast, onIntent) }
        }
    }
}

// A page is pushed over the one before it (320 ms); with reduce motion it fades (120 ms).
private fun pageTransition(from: PackPage, to: PackPage, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return fadeIn(tween(MemixMotion.durationPress)) togetherWith fadeOut(tween(MemixMotion.durationPress))
    val forward = to.ordinal > from.ordinal
    val spec = tween<androidx.compose.ui.unit.IntOffset>(MemixMotion.durationScreen)
    val enter: EnterTransition = slideInHorizontally(spec) { if (forward) it else -it / SLIDE_BACK_DIVISOR }
    val exit: ExitTransition = slideOutHorizontally(spec) { if (forward) -it / SLIDE_BACK_DIVISOR else it }
    return enter togetherWith exit
}

@Composable
private fun PackToastView(toast: PackToast, onIntent: (StickerPackIntent) -> Unit) {
    val message = when (toast) {
        PackToast.MinStickers -> stringResource(Res.string.explain_pack_min)
        PackToast.MaxStickers -> stringResource(Res.string.explain_pack_max)
        PackToast.MaxEmojis -> stringResource(Res.string.explain_pack_emoji_max)
        PackToast.MinEmojis -> stringResource(Res.string.explain_pack_emoji_min)
        PackToast.MaxPacks -> stringResource(Res.string.explain_packs_max)
        PackToast.SaveFailed -> stringResource(Res.string.pack_save_failed)
        PackToast.FileMissing -> stringResource(Res.string.pack_file_missing)
        is PackToast.Deleted -> stringResource(Res.string.packs_deleted)
    }
    val action = if (toast is PackToast.Deleted) ToastAction(stringResource(Res.string.packs_undo)) { onIntent(StickerPackIntent.UndoDelete) } else null
    Toast(message, Modifier.fillMaxWidth(), action = action)
}

/** The top of a page: × at the start and the title in `display`; screen readers start on the title (spec → Accessibility). */
@Composable
internal fun PageHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(modifier.fillMaxWidth().padding(start = MemixSpacing.space2, end = MemixSpacing.space2, top = MemixSpacing.space2), verticalAlignment = Alignment.CenterVertically) {
        CloseButton(onClose)
        DisplayText(title, Modifier.padding(start = MemixSpacing.space2).weight(1f).focusRequester(focus).focusable().semantics { heading() })
    }
}

private const val SLIDE_BACK_DIVISOR = 4
