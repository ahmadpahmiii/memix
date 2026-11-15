package app.memix.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.OptionTile
import app.memix.core.designsystem.component.Sheet
import app.memix.core.domain.region.RegionNote
import app.memix.core.domain.region.RegionOption
import app.memix.core.model.AppLanguage
import app.memix.core.ui.panels.ChoiceRow
import app.memix.core.ui.panels.keptInViewWhen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import memix.feature.onboarding.generated.resources.Res
import memix.feature.onboarding.generated.resources.region_changed_a11y
import memix.feature.onboarding.generated.resources.region_continue
import memix.feature.onboarding.generated.resources.region_first_hint
import memix.feature.onboarding.generated.resources.region_heading_language
import memix.feature.onboarding.generated.resources.region_heading_region
import memix.feature.onboarding.generated.resources.region_note_language
import memix.feature.onboarding.generated.resources.region_note_outside
import memix.feature.onboarding.generated.resources.region_note_unknown
import memix.feature.onboarding.generated.resources.region_save
import memix.feature.onboarding.generated.resources.region_sheet_title
import org.jetbrains.compose.resources.stringResource

// At this font scale and above (the project's LARGE_FONT_SCALE) the grid becomes one column. A tile never cuts its name: a long one wraps and its tile grows.
private const val ANNOUNCEMENT_MILLIS = 1_000L
private const val ONE_COLUMN_FONT_SCALE = 1.5f

/**
 * Region and language (spec P3-11): the blocking first-launch step and, later, the standard sheet. Place it at the root of
 * the screen tree, over the bottom nav, and put `coveredByModal` on what it covers while [RegionSheetUiState.visible].
 */
@Composable
fun RegionSheet(state: RegionSheetUiState, onIntent: (RegionSheetIntent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        state.announcedRegionName?.let { RegionChangedAnnouncement(it) { onIntent(RegionSheetIntent.AnnouncementShown) } }
        Sheet(
            visible = state.visible,
            // Back on the blocking sheet accepts the visible answers, the same as Continue (spec P3-11 → Entry and exit).
            onDismiss = { onIntent(if (state.firstRun) RegionSheetIntent.Confirm else RegionSheetIntent.Dismiss) },
            title = stringResource(Res.string.region_sheet_title),
            blocking = state.firstRun,
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (state.firstRun) {
                    Text(stringResource(Res.string.region_first_hint), MemixTheme.type.body, Modifier.padding(bottom = MemixSpacing.space4), color = MemixColors.textSecondary)
                }
                RegionGroup(state.regions, state.selectedRegionCode, state.regionNote) { onIntent(RegionSheetIntent.SelectRegion(it)) }
                // Where the platform cannot switch the language (iOS until P7-02) the group is left out.
                if (state.languageEditable) LanguageGroup(state.language, state.languageNote) { onIntent(RegionSheetIntent.SelectLanguage(it)) }
            }
            Button(
                stringResource(if (state.firstRun) Res.string.region_continue else Res.string.region_save),
                onClick = { onIntent(RegionSheetIntent.Confirm) },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Primary,
            )
        }
    }
}

@Composable
private fun RegionGroup(regions: ImmutableList<RegionOption>, selectedCode: String, note: RegionNote?, onSelect: (String) -> Unit) {
    val heading = stringResource(Res.string.region_heading_region)
    GroupHeading(heading)
    val columns = if (LocalDensity.current.fontScale >= ONE_COLUMN_FONT_SCALE) 1 else 2
    val rows = remember(regions, columns) { regions.chunked(columns) }
    Column(
        Modifier
            .padding(top = MemixSpacing.space2)
            .selectableGroup()
            .semantics { contentDescription = heading; collectionInfo = CollectionInfo(rowCount = regions.size, columnCount = 1) },
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
                row.forEachIndexed { column, option ->
                    val position = rowIndex * columns + column
                    OptionTile(
                        label = option.name,
                        selected = option.code == selectedCode,
                        onClick = { onSelect(option.code) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { collectionItemInfo = CollectionItemInfo(position, 1, 0, 1) },
                    )
                }
                // An odd last row keeps its tile at half width instead of stretching.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    when (note) {
        is RegionNote.Outside -> GroupNote(stringResource(Res.string.region_note_outside, note.countryName))
        RegionNote.Unknown -> GroupNote(stringResource(Res.string.region_note_unknown))
        null -> Unit
    }
}

@Composable
private fun LanguageGroup(selected: AppLanguage, phoneLanguageNote: String?, onSelect: (AppLanguage) -> Unit) {
    val heading = stringResource(Res.string.region_heading_language)
    Column(Modifier.padding(top = MemixSpacing.space6)) {
        ChoiceRow(heading) {
            AppLanguage.entries.forEachIndexed { index, language ->
                val isSelected = language == selected
                OptionTile(
                    label = language.nativeName,
                    selected = isSelected,
                    onClick = { onSelect(language) },
                    modifier = Modifier
                        .keptInViewWhen(isSelected, margin = MemixSpacing.space4)
                        .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) },
                )
            }
        }
        phoneLanguageNote?.let { GroupNote(stringResource(Res.string.region_note_language, it)) }
    }
}

@Composable
private fun GroupHeading(text: String) =
    Text(text, MemixTheme.type.label, Modifier.semantics { heading() }, color = MemixColors.textSecondary)

@Composable
private fun GroupNote(text: String) =
    Text(text, MemixTheme.type.label, Modifier.fillMaxWidth().padding(top = MemixSpacing.space2), color = MemixColors.textSecondary)

// Real bounds, nothing drawn: TalkBack ignores live regions on a node with no size.
@Composable
private fun RegionChangedAnnouncement(regionName: String, onShown: () -> Unit) {
    val spoken = stringResource(Res.string.region_changed_a11y, regionName)
    // Long enough for a screen reader to pick it up, then gone, so a later change to the same region speaks again.
    LaunchedEffect(regionName) {
        delay(ANNOUNCEMENT_MILLIS)
        onShown()
    }
    Box(Modifier.fillMaxWidth().height(MemixStroke.strokeHairline).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = spoken })
}
