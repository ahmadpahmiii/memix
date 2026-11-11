package app.memix.feature.sounds

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.Chip
import app.memix.core.designsystem.component.SoundRowLook
import app.memix.core.designsystem.component.Toast
import app.memix.core.designsystem.component.ToastAction
import app.memix.core.designsystem.component.ToastDismiss
import app.memix.core.ui.EmptyState
import kotlinx.collections.immutable.ImmutableList
import memix.core.ui.generated.resources.Res as UiRes
import memix.core.ui.generated.resources.sound_dl_downloading
import memix.core.ui.generated.resources.sound_dl_failed
import memix.core.ui.generated.resources.sound_dl_failed_storage
import memix.core.ui.generated.resources.sound_dl_storage_toast
import memix.core.ui.generated.resources.sound_meta
import memix.core.ui.generated.resources.sound_not_saved_offline
import memix.core.ui.generated.resources.sound_unavailable
import memix.feature.sounds.generated.resources.Res
import memix.feature.sounds.generated.resources.sound_dl_free_up_space
import memix.feature.sounds.generated.resources.sound_dl_toast_dismiss
import memix.feature.sounds.generated.resources.sound_detail_source_failed
import memix.feature.sounds.generated.resources.sound_play_failed
import memix.feature.sounds.generated.resources.sound_volume_low
import memix.feature.sounds.generated.resources.sounds_browse_global
import memix.feature.sounds.generated.resources.sounds_browse_trending
import memix.feature.sounds.generated.resources.sounds_chip_all
import memix.feature.sounds.generated.resources.sounds_clear_search
import memix.feature.sounds.generated.resources.sounds_empty_body
import memix.feature.sounds.generated.resources.sounds_empty_category_body
import memix.feature.sounds.generated.resources.sounds_empty_category_title
import memix.feature.sounds.generated.resources.sounds_empty_favorites_body
import memix.feature.sounds.generated.resources.sounds_empty_favorites_title
import memix.feature.sounds.generated.resources.sounds_empty_local_body
import memix.feature.sounds.generated.resources.sounds_empty_local_title
import memix.feature.sounds.generated.resources.sounds_empty_mine_body
import memix.feature.sounds.generated.resources.sounds_empty_mine_title
import memix.feature.sounds.generated.resources.sounds_empty_title
import memix.feature.sounds.generated.resources.sounds_empty_trending_body
import memix.feature.sounds.generated.resources.sounds_empty_trending_title
import memix.feature.sounds.generated.resources.sounds_load_error_body
import memix.feature.sounds.generated.resources.sounds_load_error_title
import memix.feature.sounds.generated.resources.sounds_loading_status
import memix.feature.sounds.generated.resources.sounds_local_unknown_body
import memix.feature.sounds.generated.resources.sounds_local_unknown_title
import memix.feature.sounds.generated.resources.sounds_more_failed
import memix.feature.sounds.generated.resources.sounds_more_loading
import memix.feature.sounds.generated.resources.sounds_offline_strip
import memix.feature.sounds.generated.resources.sounds_refresh_failed
import memix.feature.sounds.generated.resources.sounds_region_line
import memix.feature.sounds.generated.resources.sounds_search_none_body
import memix.feature.sounds.generated.resources.sounds_search_none_offline_body
import memix.feature.sounds.generated.resources.sounds_search_none_offline_title
import memix.feature.sounds.generated.resources.sounds_search_none_title
import memix.feature.sounds.generated.resources.sounds_search_offline_strip
import memix.feature.sounds.generated.resources.sounds_searching
import memix.feature.sounds.generated.resources.sounds_show_all
import memix.feature.sounds.generated.resources.sounds_try_again
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** "All" plus the catalog's categories as single-select chips; they scroll sideways (spec P3-02 → Layout 4). */
@Composable
internal fun CategoryChips(
    categories: ImmutableList<CategoryChipUi>,
    selectedId: String?,
    language: String,
    onSelect: (String?) -> Unit,
    allChipFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = MemixSpacing.space4).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        Chip(stringResource(Res.string.sounds_chip_all), selected = selectedId == null, onClick = { onSelect(null) }, Modifier.focusRequester(allChipFocus))
        categories.forEach { category ->
            Chip(category.name.inLanguage(language), selected = category.id == selectedId, onClick = { onSelect(category.id) })
        }
    }
}

/** A calm one-line note above the rows; read out politely when it appears. */
@Composable
internal fun StatusStrip(notice: SoundsNotice, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (notice) {
            SoundsNotice.OFFLINE -> Res.string.sounds_offline_strip
            SoundsNotice.SEARCH_OFFLINE -> Res.string.sounds_search_offline_strip
            SoundsNotice.SEARCHING -> Res.string.sounds_searching
        },
    )
    Text(
        text,
        MemixTheme.type.label,
        modifier
            .fillMaxWidth()
            .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space1)
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .padding(MemixSpacing.space3)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The words of a toast; the screen and the details sheet each show their own. */
@Composable
internal fun soundsToastText(toast: SoundsToast): String = stringResource(
    when (toast) {
        SoundsToast.PLAY_FAILED -> Res.string.sound_play_failed
        SoundsToast.REFRESH_FAILED -> Res.string.sounds_refresh_failed
        SoundsToast.VOLUME_LOW -> Res.string.sound_volume_low
        SoundsToast.SOURCE_FAILED -> Res.string.sound_detail_source_failed
    },
)

/** A row's meta line: "0:02 · CC0" normally, else what is going on with the file (spec P3-04 → Row states). */
@Composable
internal fun soundMetaText(row: SoundItemUi, look: SoundRowLook): String = when (look) {
    SoundRowLook.NORMAL -> stringResource(UiRes.string.sound_meta, row.length, row.credit)
    SoundRowLook.DOWNLOADING -> stringResource(UiRes.string.sound_dl_downloading)
    SoundRowLook.FAILED -> stringResource(UiRes.string.sound_dl_failed)
    SoundRowLook.FAILED_STORAGE -> stringResource(UiRes.string.sound_dl_failed_storage)
    SoundRowLook.OFFLINE_NOT_SAVED -> stringResource(UiRes.string.sound_not_saved_offline)
    SoundRowLook.UNAVAILABLE -> stringResource(UiRes.string.sound_unavailable)
}

/**
 * The persistent toast for a full phone (spec P3-04): what happened, a × and, when the phone has a storage screen, the one
 * action "Free up space". It stays until the user dismisses it or a later download works.
 */
@Composable
internal fun StorageFullToast(canFreeUpSpace: Boolean, onFreeUpSpace: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Toast(
        message = stringResource(UiRes.string.sound_dl_storage_toast),
        modifier = modifier,
        action = if (canFreeUpSpace) ToastAction(stringResource(Res.string.sound_dl_free_up_space), onFreeUpSpace) else null,
        dismiss = ToastDismiss(stringResource(Res.string.sound_dl_toast_dismiss), onDismiss),
    )
}

/**
 * The picker's full-phone note (spec P3-05 → Download fails mid-add): a strip at the top of the list with its one Quiet
 * action on its own row. A toast would sit under the sheet's scrim, out of reach of TalkBack and the keyboard. It goes when a
 * download works or the sheet closes.
 */
@Composable
internal fun StorageFullStrip(canFreeUpSpace: Boolean, onFreeUpSpace: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space1)
            .clip(MemixShapes.radiusMd)
            .background(MemixColors.surfaceRaised)
            .padding(MemixSpacing.space3)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(stringResource(UiRes.string.sound_dl_storage_toast), MemixTheme.type.label)
        if (canFreeUpSpace) Button(stringResource(Res.string.sound_dl_free_up_space), onFreeUpSpace, variant = ButtonVariant.Quiet)
    }
}

/** The Local tab's first line. A label until P3-11 turns it into the Region sheet's opener. */
@Composable
internal fun RegionLine(regionName: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MemixIcons.Globe, contentDescription = null, tint = MemixColors.textSecondary)
        Text(stringResource(Res.string.sounds_region_line, regionName), MemixTheme.type.label, color = MemixColors.textSecondary)
    }
}

/** A Favorites tab section title: `label` in `text-secondary`, `space-6` above (not on the first), `space-2` below. */
@Composable
internal fun SectionHeading(title: StringResource, isFirst: Boolean, modifier: Modifier = Modifier) {
    Text(
        stringResource(title),
        MemixTheme.type.label,
        modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) MemixSpacing.space2 else MemixSpacing.space6)
            .padding(start = MemixSpacing.space4, end = MemixSpacing.space4, bottom = MemixSpacing.space2)
            .semantics { heading() },
        color = MemixColors.textSecondary,
    )
}

/** The line under an empty Favorites section while Recently used has rows. */
@Composable
internal fun SectionHint(text: StringResource, modifier: Modifier = Modifier) {
    Text(
        stringResource(text),
        MemixTheme.type.body,
        modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space4),
        color = MemixColors.textSecondary,
    )
}

/** Six static rows while the first load has nothing saved: no shimmer and no spinner (spec P3-02 → States). */
@Composable
internal fun PlaceholderRow(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(MemixSize.soundDiscSize).clip(MemixShapes.radiusFull).background(MemixColors.surfaceRaised))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            Bar(NAME_BAR_FRACTION)
            Bar(META_BAR_FRACTION)
        }
    }
}

@Composable
private fun Bar(fraction: Float) {
    Box(Modifier.fillMaxWidth(fraction).height(MemixSpacing.space3).clip(MemixShapes.radiusFull).background(MemixColors.surfaceRaised))
}

/** Spoken only: the placeholders say nothing to a screen reader on their own. */
@Composable
internal fun LoadingAnnouncement(modifier: Modifier = Modifier) {
    val text = stringResource(Res.string.sounds_loading_status)
    // Real bounds, nothing drawn: TalkBack ignores live regions on a node with no size.
    Box(modifier.fillMaxWidth().height(MemixStroke.strokeHairline).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = text })
}

/** The last list item: "Loading more", or the failure with a way to retry. */
@Composable
internal fun ListFooterItem(footer: ListFooter, onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    when (footer) {
        ListFooter.NONE -> Unit
        ListFooter.LOADING_MORE -> Text(
            stringResource(Res.string.sounds_more_loading),
            MemixTheme.type.label,
            modifier.padding(MemixSpacing.space4),
            color = MemixColors.textMuted,
        )
        ListFooter.MORE_FAILED -> Column(
            modifier.fillMaxWidth().padding(MemixSpacing.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        ) {
            Text(stringResource(Res.string.sounds_more_failed), MemixTheme.type.label, color = MemixColors.textSecondary)
            Button(stringResource(Res.string.sounds_try_again), onTryAgain)
        }
    }
}

/** The catalog or the search can't be reached and nothing is saved to show. */
@Composable
internal fun LoadErrorBlock(onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        stringResource(Res.string.sounds_load_error_title),
        stringResource(Res.string.sounds_load_error_body),
        modifier,
        action = { Button(stringResource(Res.string.sounds_try_again), onTryAgain) },
    )
}

/** Why a list is empty and, where it helps, the one Secondary button that gets the user somewhere useful. */
@Composable
internal fun EmptyBlock(reason: EmptyReason, query: String, regionName: String?, onIntent: (SoundsIntent) -> Unit, modifier: Modifier = Modifier) {
    when (reason) {
        EmptyReason.TRENDING -> EmptyState(
            stringResource(Res.string.sounds_empty_trending_title),
            stringResource(Res.string.sounds_empty_trending_body),
            modifier,
            action = { GoToButton(Res.string.sounds_browse_global) { onIntent(SoundsIntent.SelectTab(SoundsTab.GLOBAL)) } },
        )
        EmptyReason.LOCAL -> EmptyState(
            stringResource(Res.string.sounds_empty_local_title),
            stringResource(Res.string.sounds_empty_local_body, regionName.orEmpty()),
            modifier,
            action = { GoToButton(Res.string.sounds_browse_global) { onIntent(SoundsIntent.SelectTab(SoundsTab.GLOBAL)) } },
        )
        // TODO(P3-11): the Region sheet's opener goes here; until then the phone's region is the only source.
        EmptyReason.LOCAL_UNKNOWN -> EmptyState(
            stringResource(Res.string.sounds_local_unknown_title),
            stringResource(Res.string.sounds_local_unknown_body),
            modifier,
        )
        EmptyReason.GLOBAL -> EmptyState(stringResource(Res.string.sounds_empty_title), stringResource(Res.string.sounds_empty_body), modifier)
        EmptyReason.CATEGORY -> EmptyState(
            stringResource(Res.string.sounds_empty_category_title),
            stringResource(Res.string.sounds_empty_category_body),
            modifier,
            action = { GoToButton(Res.string.sounds_show_all) { onIntent(SoundsIntent.SelectCategory(null)) } },
        )
        EmptyReason.FAVORITES -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(MemixIcons.Heart, contentDescription = null, tint = MemixColors.textSecondary, modifier = Modifier.padding(top = MemixSpacing.space10), size = MemixSpacing.space8)
            EmptyState(
                stringResource(Res.string.sounds_empty_favorites_title),
                stringResource(Res.string.sounds_empty_favorites_body),
                action = { GoToButton(Res.string.sounds_browse_trending) { onIntent(SoundsIntent.SelectTab(SoundsTab.TRENDING)) } },
            )
        }
        EmptyReason.MINE -> EmptyState(stringResource(Res.string.sounds_empty_mine_title), stringResource(Res.string.sounds_empty_mine_body), modifier)
        EmptyReason.SEARCH_NONE -> EmptyState(
            stringResource(Res.string.sounds_search_none_title, query.trim()),
            stringResource(Res.string.sounds_search_none_body),
            modifier,
            action = { GoToButton(Res.string.sounds_clear_search) { onIntent(SoundsIntent.ClearQuery) } },
        )
        EmptyReason.SEARCH_NONE_OFFLINE -> EmptyState(
            stringResource(Res.string.sounds_search_none_offline_title, query.trim()),
            stringResource(Res.string.sounds_search_none_offline_body),
            modifier,
        )
    }
}

@Composable
private fun GoToButton(label: StringResource, onClick: () -> Unit) {
    Button(stringResource(label), onClick)
}

private const val NAME_BAR_FRACTION = 0.6f
private const val META_BAR_FRACTION = 0.35f
