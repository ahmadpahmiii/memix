package app.memix.core.ui.look

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Slider
import app.memix.core.model.project.FilterRef
import app.memix.core.model.project.ImageAdjust
import app.memix.core.model.project.ImageToolCatalog
import kotlin.math.roundToInt
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.adjust_brightness
import memix.core.ui.generated.resources.adjust_contrast
import memix.core.ui.generated.resources.adjust_saturation
import memix.core.ui.generated.resources.adjust_sharpen
import memix.core.ui.generated.resources.adjust_vignette
import memix.core.ui.generated.resources.adjust_warmth
import memix.core.ui.generated.resources.filter_aqua
import memix.core.ui.generated.resources.filter_bleach
import memix.core.ui.generated.resources.filter_chrome
import memix.core.ui.generated.resources.filter_cinema
import memix.core.ui.generated.resources.filter_cool
import memix.core.ui.generated.resources.filter_drama
import memix.core.ui.generated.resources.filter_dusk
import memix.core.ui.generated.resources.filter_fade
import memix.core.ui.generated.resources.filter_film
import memix.core.ui.generated.resources.filter_matte
import memix.core.ui.generated.resources.filter_mono
import memix.core.ui.generated.resources.filter_neon
import memix.core.ui.generated.resources.filter_noir
import memix.core.ui.generated.resources.filter_pastel
import memix.core.ui.generated.resources.filter_punch
import memix.core.ui.generated.resources.filter_sepia
import memix.core.ui.generated.resources.filter_sunset
import memix.core.ui.generated.resources.filter_vintage
import memix.core.ui.generated.resources.filter_vivid
import memix.core.ui.generated.resources.filter_warm
import memix.core.ui.generated.resources.filters_intensity
import memix.core.ui.generated.resources.filters_none
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The two tabs of the look panel both editors host (spec docs/ux/specs/P2-04-image-tools.md → The Edit photo panel; P4-07 lifts it): the
 * Filters strip with its Intensity slider, and the six Adjust sliders. The caller owns the tabs row, Reset, the title and where a value
 * lives (the photo editor's layer, the video editor's clip or clips); these take values and report changes. A slider drag is reported
 * live ([onIntensityChange], [onChange]) and let go once ([onIntensityEnd], [onEnd]); a tile is reported once ([onPick]).
 */

/**
 * The Filters tab: None and the 20 filters as tiles of the user's picture ([tiles]), then Intensity while a filter is on. With [mixed]
 * (video clips with different looks) no tile is selected and there is no Intensity row.
 */
@Composable
fun FiltersStrip(
    filter: FilterRef?,
    mixed: Boolean,
    tiles: FilterThumbnails,
    onPick: (String?) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onIntensityEnd: () -> Unit,
) {
    val ids = remember { listOf<String?>(null) + ImageToolCatalog.filters.map { it.id } }
    val names = filterNames()
    val none = stringResource(Res.string.filters_none)
    val listState = rememberLazyListState()
    val selectedId = filter?.id.takeUnless { mixed }
    val selectedIndex = ids.indexOf(selectedId).coerceAtLeast(0)
    // The selected tile scrolls into view when the panel opens (and after an undo brings another one back).
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }
    LazyRow(Modifier.fillMaxWidth().selectableGroup(), state = listState, horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        items(ids, key = { it ?: "none" }) { id ->
            val name = if (id == null) none else stringResource(names.getValue(id))
            val index = ids.indexOf(id) + 1
            // In the mixed state nothing is selected, not even None: every tile is a choice that applies to all.
            val selected = !mixed && selectedId == id
            FilterTile(name, tiles.tileOf(id), selected, position = "$index / ${ids.size}") { onPick(id) }
        }
    }
    if (filter != null && !mixed) {
        Slider(
            label = stringResource(Res.string.filters_intensity),
            value = filter.intensity,
            onValueChange = onIntensityChange,
            valueText = percentText(filter.intensity),
            defaultValue = 1f,
            step = SLIDER_STEP,
            onValueChangeFinished = onIntensityEnd,
            snapValue = 1f,
            snapDistance = SNAP_DISTANCE,
        )
    }
}

/**
 * The Adjust tab's six rows in their order, with the label and range of each. `:core:ui` can't see the domain's `AdjustPart` (the
 * same six, same order, same names), so a caller maps one to the other by name.
 */
enum class AdjustRow(val label: StringResource, val range: ClosedFloatingPointRange<Float>) {
    BRIGHTNESS(Res.string.adjust_brightness, -1f..1f),
    CONTRAST(Res.string.adjust_contrast, -1f..1f),
    SATURATION(Res.string.adjust_saturation, -1f..1f),
    WARMTH(Res.string.adjust_warmth, -1f..1f),
    SHARPEN(Res.string.adjust_sharpen, 0f..1f),
    VIGNETTE(Res.string.adjust_vignette, 0f..1f),
    ;

    /** This row's value in [adjust]. */
    fun valueIn(adjust: ImageAdjust): Float = when (this) {
        BRIGHTNESS -> adjust.brightness
        CONTRAST -> adjust.contrast
        SATURATION -> adjust.saturation
        WARMTH -> adjust.warmth
        SHARPEN -> adjust.sharpen
        VIGNETTE -> adjust.vignette
    }
}

/** The Adjust tab: the six sliders, each live while it drags and let go once with the row that moved. */
@Composable
fun AdjustRows(adjust: ImageAdjust, onChange: (AdjustRow, Float) -> Unit, onEnd: (AdjustRow) -> Unit) {
    for (part in AdjustRow.entries) {
        val value = part.valueIn(adjust)
        Slider(
            label = stringResource(part.label),
            value = value,
            onValueChange = { onChange(part, it) },
            valueText = signedText(value, bipolar = part.range.start < 0f),
            valueRange = part.range,
            defaultValue = 0f,
            step = SLIDER_STEP,
            onValueChangeFinished = { onEnd(part) },
            snapValue = 0f,
            snapDistance = SNAP_DISTANCE,
            origin = if (part.range.start < 0f) 0f else null,
        )
    }
}

// 72 dp square with the user's picture and the filter on, the name under it; a white ring when selected (spec → Filters tab).
@Composable
private fun FilterTile(name: String, tile: ImageBitmap?, selected: Boolean, position: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(MemixSize.filterTileSize)
            .selectable(selected, role = Role.RadioButton, onClick = { if (!selected) onClick() })
            .semantics { contentDescription = "$name, $position" },
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(MemixSize.filterTileSize)
                .clip(MemixShapes.radiusMd)
                .background(MemixColors.surfaceRaised)
                .then(if (selected) Modifier.border(MemixStroke.strokeSelection, MemixColors.selection, MemixShapes.radiusMd) else Modifier),
        ) {
            if (tile != null) Image(BitmapPainter(tile), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Centered under the picture, one line or several, the same in the Filters and Transition strips (design review P4-08).
        Text(name, MemixTheme.type.label, Modifier.fillMaxWidth(), color = MemixColors.text, textAlign = TextAlign.Center)
    }
}

/** "70%": a slider's fraction as a percentage. */
fun percentText(value: Float) = "${(value * PERCENT).roundToInt()}%"

// "+30", "-20" and "0": a bipolar slider's value with its sign; a one-sided one without.
internal fun signedText(value: Float, bipolar: Boolean): String {
    val percent = (value * PERCENT).roundToInt()
    return when {
        percent > 0 && bipolar -> "+$percent"
        percent < 0 -> "−${-percent}"
        else -> percent.toString()
    }
}

/** The name resource of each filter id (`filter_<id>`). */
@Composable
fun filterNames(): Map<String, StringResource> = remember {
    mapOf(
        "vivid" to Res.string.filter_vivid, "warm" to Res.string.filter_warm, "cool" to Res.string.filter_cool, "mono" to Res.string.filter_mono,
        "noir" to Res.string.filter_noir, "fade" to Res.string.filter_fade, "vintage" to Res.string.filter_vintage, "film" to Res.string.filter_film,
        "sepia" to Res.string.filter_sepia, "cinema" to Res.string.filter_cinema, "pastel" to Res.string.filter_pastel, "punch" to Res.string.filter_punch,
        "drama" to Res.string.filter_drama, "sunset" to Res.string.filter_sunset, "neon" to Res.string.filter_neon, "aqua" to Res.string.filter_aqua,
        "matte" to Res.string.filter_matte, "chrome" to Res.string.filter_chrome, "dusk" to Res.string.filter_dusk, "bleach" to Res.string.filter_bleach,
    )
}

// The slider step for keyboard and TalkBack: 10% (spec P2-04).
private const val SLIDER_STEP = 0.1f
private const val SNAP_DISTANCE = 0.03f
private const val PERCENT = 100f
