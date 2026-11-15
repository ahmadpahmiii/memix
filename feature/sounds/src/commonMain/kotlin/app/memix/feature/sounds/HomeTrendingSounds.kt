package app.memix.feature.sounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.FadingToast
import app.memix.core.ui.appLanguageCode
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.home_sounds_error
import memix.feature.sounds.generated.resources.home_sounds_loading
import memix.feature.sounds.generated.resources.home_sounds_offline
import memix.feature.sounds.generated.resources.home_sounds_see_all
import memix.feature.sounds.generated.resources.home_sounds_see_all_a11y
import memix.feature.sounds.generated.resources.home_sounds_title
import memix.feature.sounds.generated.resources.sounds_try_again
import org.jetbrains.compose.resources.stringResource

/**
 * Home's "Trending sounds" row (spec P3-14): up to [HOME_ROW_COUNT] rows, the same list as the Trending tab. Stateless: the
 * ViewModel in [SoundsMode.HOME] one level up owns [state]. The app fills Home's slot with it, so Home needs no dependency on
 * this module. The section is not drawn at all while the first load is under 300 ms and when the catalog has no trending
 * sounds; Home never says content is missing.
 *
 * Loading starts after the first frame ([SoundsIntent.ScreenShown]); leaving Home stops the preview and releases the player
 * (the app sends it when Home is left: [SoundsTabExit.homeLeft]). [onSeeAll] switches to the Sounds tab. The details sheet and the toasts are placed by the app
 * over the nav ([SoundDetailsSheet], [HomeSoundsToasts]).
 */
@Composable
fun TrendingSoundsSection(
    state: State<SoundsUiState>,
    clock: PlaybackClock,
    onSeeAll: () -> Unit,
    onIntent: (SoundsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnIntent by rememberUpdatedState(onIntent)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { currentOnIntent(SoundsIntent.AppStopped) }
    // After Home's first frame, so the catalog and the player never delay app start.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        currentOnIntent(SoundsIntent.ScreenShown)
    }

    val current = state.value
    val position = rememberPlaybackPosition(clock, current.activeId, isPlaying = current.playingId != null)
    val rowsFade = rememberTabFade(SoundsTab.TRENDING, current.regionChanges)
    val drawn = current.drawsHomeSection
    val appear = rememberAppearFade(drawn)
    if (!drawn) return

    val content = current.content
    Column(modifier.fillMaxWidth().padding(top = MemixSpacing.space6, bottom = MemixSpacing.space6).then(appear)) {
        TrendingHeading(showSeeAll = content is SoundsContent.Rows || content is SoundsContent.Loading, onSeeAll = onSeeAll)
        when (content) {
            is SoundsContent.Rows -> TrendingRows(current, content, rowsFade, position, onIntent)
            is SoundsContent.Loading -> {
                LoadingAnnouncement(message = Res.string.home_sounds_loading)
                repeat(HOME_PLACEHOLDER_COUNT) { PlaceholderRow() }
            }
            SoundsContent.LoadError ->
                if (current.online) {
                    MessageBlock(stringResource(Res.string.home_sounds_error), onTryAgain = { onIntent(SoundsIntent.TryAgain) })
                } else {
                    MessageBlock(stringResource(Res.string.home_sounds_offline), onTryAgain = null)
                }
            // Home draws neither (see drawsHomeSection); the ViewModel does not produce the other three in HOME mode.
            is SoundsContent.Empty, is SoundsContent.Starters, is SoundsContent.Favorites -> Unit
        }
    }
}

/** Nothing is drawn while a first load is quick, and nothing at all when the catalog has no trending sounds. */
private val SoundsUiState.drawsHomeSection: Boolean
    get() = when (val shown = content) {
        is SoundsContent.Loading -> shown.showPlaceholders
        is SoundsContent.Empty, is SoundsContent.Starters, is SoundsContent.Favorites -> false
        is SoundsContent.Rows, SoundsContent.LoadError -> true
    }

// "Trending sounds" at the start and See all at the end; at a large font See all wraps under the heading, never cut.
@Composable
private fun TrendingHeading(showSeeAll: Boolean, onSeeAll: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space4).defaultMinSize(minHeight = MemixSize.touchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.home_sounds_title), MemixTheme.type.title, Modifier.semantics { heading() })
        if (showSeeAll) {
            val spoken = stringResource(Res.string.home_sounds_see_all_a11y)
            Button(
                stringResource(Res.string.home_sounds_see_all),
                onSeeAll,
                Modifier.semantics { contentDescription = spoken },
                variant = ButtonVariant.Quiet,
            )
        }
    }
}

@Composable
private fun TrendingRows(
    state: SoundsUiState,
    content: SoundsContent.Rows,
    fade: Modifier,
    position: State<Long>,
    onIntent: (SoundsIntent) -> Unit,
) {
    if (state.notice == SoundsNotice.OFFLINE) StatusStrip(SoundsNotice.OFFLINE)
    val language = appLanguageCode()
    Column(fade) {
        content.rows.forEach { row ->
            key(row.id) {
                LibrarySoundRow(
                    row = row,
                    playback = state.playbackOf(row.id),
                    isFavorite = false,
                    peaks = state.peaksFor(row.id),
                    returnsFocus = state.returnsFocusTo(row.id, fromRecent = false),
                    look = state.lookOf(row.id),
                    downloadProgress = state.downloadProgressOf(row.id),
                    language = language,
                    fade = Modifier,
                    position = position,
                    onIntent = onIntent,
                    // The Home row has no heart (spec P3-14 → Layout 4): the details sheet has it.
                    showsHeart = false,
                )
            }
        }
    }
}

/** The offline or error note: one `surface` block (so the Secondary button's `surface-raised` keeps its shape), with a "Try again" only when trying again can help. */
@Composable
private fun MessageBlock(text: String, onTryAgain: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2)
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surface)
            .padding(MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        Text(text, MemixTheme.type.body)
        if (onTryAgain != null) Button(stringResource(Res.string.sounds_try_again), onTryAgain)
    }
}

// The section fades in over `duration-press` when it first has something to draw (spec P3-14 → Motion).
@Composable
private fun rememberAppearFade(drawn: Boolean): Modifier {
    val alpha = remember { Animatable(if (drawn) 1f else 0f) }
    LaunchedEffect(drawn) {
        if (!drawn) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.animateTo(1f, tween(MemixMotion.durationPress))
    }
    return remember(alpha) { Modifier.graphicsLayer { this.alpha = alpha.value } }
}

/**
 * The toasts of Home's row (a sound that won't play, the volume hint, the full-phone note), placed by the app above the bottom
 * nav while Home shows. The details sheet shows its own while it is open.
 */
@Composable
fun HomeSoundsToasts(state: State<SoundsUiState>, onFreeUpSpace: () -> Unit, onIntent: (SoundsIntent) -> Unit, modifier: Modifier = Modifier) {
    val current = state.value
    ReportSentHaptic(current.toast)
    Box(modifier.fillMaxWidth()) {
        FadingToast(current.toast.takeIf { current.details == null }, Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4)) { toast ->
            SoundsToastItem(toast, current.addedName)
        }
        if (current.storageFullNote && current.details == null) {
            StorageFullToast(
                canFreeUpSpace = current.canFreeUpSpace,
                onFreeUpSpace = onFreeUpSpace,
                onDismiss = { onIntent(SoundsIntent.DismissStorageNote) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(MemixSpacing.space4),
            )
        }
    }
}

private const val HOME_PLACEHOLDER_COUNT = 3
