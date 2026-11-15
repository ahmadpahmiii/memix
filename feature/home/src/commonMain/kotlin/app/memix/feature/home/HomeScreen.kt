package app.memix.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.DisplayText
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.EntryCard
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import app.memix.core.model.Region
import memix.feature.home.generated.resources.Res
import memix.feature.home.generated.resources.entry_photo_body
import memix.feature.home.generated.resources.entry_photo_title
import memix.feature.home.generated.resources.entry_video_body
import memix.feature.home.generated.resources.entry_video_title
import memix.feature.home.generated.resources.home_hero
import memix.feature.home.generated.resources.home_region_a11y
import memix.feature.home.generated.resources.home_region_change
import memix.feature.home.generated.resources.region_global
import memix.feature.home.generated.resources.wordmark
import org.jetbrains.compose.resources.stringResource

// From this font scale (the project's LARGE_FONT_SCALE) the two entry cards stack in one column.
private const val STACK_CARDS_FONT_SCALE = 1.5f

/**
 * [onStartVideoMeme] opens the gallery picker (P1-02). [onOpenRegion] opens the Region sheet (P3-11). [trendingSounds] is the
 * "Trending sounds" section (P3-14): the app fills it, so this module needs no dependency on the sounds feature; it is drawn under
 * the entry cards and draws nothing when there is nothing to show. [onOpenCatalog] is non-null only in debug builds; it opens the
 * component catalog on a long-press of the wordmark.
 *
 * Home is one top-aligned column that scrolls, so the section never pushes the cards off the first screen.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartVideoMeme: () -> Unit,
    onStartPhotoMeme: () -> Unit,
    onOpenRegion: () -> Unit,
    trendingSounds: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCatalog: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())) {
        TopRow(state.region, onOpenRegion, onOpenCatalog, Modifier.padding(horizontal = MemixSpacing.space4).padding(top = MemixSpacing.space5))
        DisplayText(
            stringResource(Res.string.home_hero),
            Modifier.padding(horizontal = MemixSpacing.space4).padding(top = MemixSpacing.space5).semantics { heading() },
            style = MemixTheme.type.displayXl,
        )
        EntryCards(onStartVideoMeme, onStartPhotoMeme, Modifier.padding(horizontal = MemixSpacing.space4).padding(top = MemixSpacing.space5))
        trendingSounds()
    }
}

@Composable
private fun EntryCards(onStartVideoMeme: () -> Unit, onStartPhotoMeme: () -> Unit, modifier: Modifier = Modifier) {
    // At large font the two cards stack, so a title never breaks mid-word in a half-width card (P3-11 review).
    val stacked = LocalDensity.current.fontScale >= STACK_CARDS_FONT_SCALE
    val cards: @Composable (Modifier) -> Unit = { cardModifier ->
        EntryCard(
            title = stringResource(Res.string.entry_video_title),
            description = stringResource(Res.string.entry_video_body),
            icon = MemixIcons.Video,
            emphasized = true,
            onClick = onStartVideoMeme,
            modifier = cardModifier,
        )
        EntryCard(
            title = stringResource(Res.string.entry_photo_title),
            description = stringResource(Res.string.entry_photo_body),
            icon = MemixIcons.Photo,
            emphasized = false,
            onClick = onStartPhotoMeme,
            modifier = cardModifier,
        )
    }
    if (stacked) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) { cards(Modifier.fillMaxWidth()) }
    } else {
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
            cards(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun TopRow(region: Region, onOpenRegion: () -> Unit, onOpenCatalog: (() -> Unit)?, modifier: Modifier = Modifier) {
    val debugGesture = if (onOpenCatalog == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { onOpenCatalog() })
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.wordmark), MemixTheme.type.wordmark, debugGesture)
        RegionButton(regionName(region), onOpenRegion, Modifier.weight(1f, fill = false))
    }
}

/** The globe, the region's name and a chevron in a 48 dp target (spec P3-11 → Home's region button). Opens the Region sheet. */
@Composable
private fun RegionButton(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val container by pressedColor(interactionSource, Color.Transparent, MemixColors.surfaceRaised)
    val changeLabel = stringResource(Res.string.home_region_change)
    val spokenName = stringResource(Res.string.home_region_a11y, name)
    Row(
        modifier
            .focusRing(interactionSource, MemixShapes.radiusMd)
            .defaultMinSize(minHeight = MemixSize.touchTarget)
            .clip(MemixShapes.radiusMd)
            .drawBehind { drawRect(container) }
            .clickable(interactionSource, indication = null, onClickLabel = changeLabel, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spokenName }
            .padding(horizontal = MemixSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
    ) {
        Icon(MemixIcons.Globe, contentDescription = null, tint = MemixColors.textSecondary, size = MemixSize.iconSmall)
        Text(name, MemixTheme.type.label, Modifier.weight(1f, fill = false), color = MemixColors.textSecondary, maxLines = 1)
        Icon(MemixIcons.ChevronRight, contentDescription = null, tint = MemixColors.textSecondary, size = MemixSize.iconSmall)
    }
}

@Composable
private fun regionName(region: Region) = when (region) {
    Region.Global -> stringResource(Res.string.region_global)
    is Region.Country -> region.displayName
}
