package app.memix.feature.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.DisplayText
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.EntryCard
import app.memix.core.model.Region
import memix.feature.home.generated.resources.Res
import memix.feature.home.generated.resources.entry_photo_body
import memix.feature.home.generated.resources.entry_photo_title
import memix.feature.home.generated.resources.entry_video_body
import memix.feature.home.generated.resources.entry_video_title
import memix.feature.home.generated.resources.home_hero
import memix.feature.home.generated.resources.region_global
import memix.feature.home.generated.resources.wordmark
import org.jetbrains.compose.resources.stringResource

/**
 * [onStartVideoMeme] opens the gallery picker (P1-02). [onOpenCatalog] is non-null only in debug builds; it opens the
 * component catalog on a long-press of the wordmark.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartVideoMeme: () -> Unit,
    onOpenPhotoEditor: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCatalog: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        TopRow(state.region, onOpenCatalog, Modifier.padding(horizontal = MemixSpacing.space4).padding(top = MemixSpacing.space5))
        // While picking an editor is Home's only job, the hero and the two doors sit in the center (spec P0-05 → Home).
        BoxWithConstraints(Modifier.weight(1f)) {
            val viewportHeight = maxHeight
            val cardWidth = (maxWidth - MemixSpacing.space4 * 2 - MemixSpacing.space3) / 2
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(MemixSpacing.space5, Alignment.CenterVertically),
            ) {
                DisplayText(stringResource(Res.string.home_hero), Modifier.semantics { heading() }, style = MemixTheme.type.displayXl)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3)) {
                    // 4:5 is one of the meme canvas ratios; the cards grow past it when text needs more room.
                    val cardModifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = cardWidth * 5 / 4)
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
                        onClick = onOpenPhotoEditor,
                        modifier = cardModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopRow(region: Region, onOpenCatalog: (() -> Unit)?, modifier: Modifier = Modifier) {
    val debugGesture = if (onOpenCatalog == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { onOpenCatalog() })
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.wordmark), MemixTheme.type.display, debugGesture.weight(1f))
        // A label until region picking arrives (P3-11): a pill here would look tappable and do nothing.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
            Icon(MemixIcons.Globe, contentDescription = null, tint = MemixColors.textSecondary, size = 16.dp)
            Text(regionName(region), MemixTheme.type.label, color = MemixColors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun regionName(region: Region) = when (region) {
    Region.Global -> stringResource(Res.string.region_global)
    is Region.Country -> region.displayName
}
