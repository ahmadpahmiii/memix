package app.memix.feature.stickerpack

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixStroke
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.ButtonVariant
import app.memix.core.designsystem.component.ProgressBar
import app.memix.core.model.stickerpack.StickerPackLimits
import app.memix.core.model.text.graphemeCount
import app.memix.core.model.text.takeGraphemes
import memix.feature.sticker_pack.generated.resources.Res
import memix.feature.sticker_pack.generated.resources.a11y_pack_progress_milestone
import memix.feature.sticker_pack.generated.resources.a11y_pack_tile
import memix.feature.sticker_pack.generated.resources.a11y_pack_tile_selected
import memix.feature.sticker_pack.generated.resources.a11y_pack_too_big
import memix.feature.sticker_pack.generated.resources.pack_add
import memix.feature.sticker_pack.generated.resources.pack_builder_edit
import memix.feature.sticker_pack.generated.resources.pack_builder_new
import memix.feature.sticker_pack.generated.resources.pack_count
import memix.feature.sticker_pack.generated.resources.pack_count_max
import memix.feature.sticker_pack.generated.resources.pack_count_need
import memix.feature.sticker_pack.generated.resources.pack_emojis
import memix.feature.sticker_pack.generated.resources.pack_name_default
import memix.feature.sticker_pack.generated.resources.pack_name_helper
import memix.feature.sticker_pack.generated.resources.pack_name_label
import memix.feature.sticker_pack.generated.resources.pack_need_add_photo
import memix.feature.sticker_pack.generated.resources.pack_need_body
import memix.feature.sticker_pack.generated.resources.pack_need_remove_background
import memix.feature.sticker_pack.generated.resources.pack_need_title
import memix.feature.sticker_pack.generated.resources.pack_no_whatsapp
import memix.feature.sticker_pack.generated.resources.pack_not_added
import memix.feature.sticker_pack.generated.resources.pack_preparing
import memix.feature.sticker_pack.generated.resources.pack_too_big
import memix.feature.sticker_pack.generated.resources.pack_update
import memix.feature.sticker_pack.generated.resources.pack_whatsapp_old
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The builder (spec → Builder screen): name, grid of My stickers (tap toggles), counter, and the bottom bar with Emojis and Add to WhatsApp.
// Under 3 stickers in the store the grid, name and bar give way to the "you need 3 stickers" state.
@Composable
internal fun BuilderPage(
    state: StickerPackUiState,
    onIntent: (StickerPackIntent) -> Unit,
    thumbnails: PackThumbnails,
    hasImage: Boolean,
    onEmptyAction: () -> Unit,
    nameField: TextFieldState,
    modifier: Modifier = Modifier,
) {
    val builder = state.builder
    val title = stringResource(if (builder.packId != null) Res.string.pack_builder_edit else Res.string.pack_builder_new)
    val total = state.stickers.size + builder.keptFromPack.size
    val needMore = total < StickerPackLimits.MIN_STICKERS
    // Large font (review): when the pinned counter and buttons would pass 40% of the window, the counter scrolls with the content and only the buttons stay pinned.
    var counterInContent by remember { mutableStateOf(false) }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PageHeader(title, onClose = { onIntent(StickerPackIntent.Back) })
        if (needMore) {
            NeedMoreStickers(total, hasImage, onEmptyAction, Modifier.weight(1f))
        } else {
            StickerGrid(state, onIntent, thumbnails, nameField, counterInContent, Modifier.weight(1f))
            BottomBar(
                state, onIntent, nameField, counterInContent,
                Modifier.onSizeChanged { if (it.height > windowHeightPx * PINNED_MAX_SHARE) counterInContent = true },
            )
        }
    }
}

/** Puts the pack's name into the field each time the builder opens; it lives above the pages so back from Added keeps what was typed. */
@Composable
internal fun rememberNameField(builder: BuilderUi): TextFieldState {
    val defaultName = stringResource(Res.string.pack_name_default)
    val field = rememberTextFieldState(builder.initialName ?: defaultName)
    LaunchedEffect(builder.openCount) { field.setTextAndPlaceCursorAtEnd(builder.initialName ?: defaultName) }
    return field
}

@Composable
private fun StickerGrid(state: StickerPackUiState, onIntent: (StickerPackIntent) -> Unit, thumbnails: PackThumbnails, nameField: TextFieldState, counterInContent: Boolean, modifier: Modifier) {
    val builder = state.builder
    val gridState = rememberLazyGridState()
    val stickers = remember(state.stickers, builder.keptFromPack) { state.stickers + builder.keptFromPack.keys }
    val notice = builder.noticeText()
    val hasNotice = notice != null
    // A notice sits at the end of the content, just above the counter: bring it into view when it appears.
    LaunchedEffect(hasNotice, builder.tooDetailed, builder.notice) {
        // Items: the name, the stickers, then the notice.
        if (hasNotice) gridState.animateScrollToItem(stickers.size + 1)
    }
    LazyVerticalGrid(
        GridCells.Adaptive(TILE_MIN_WIDTH),
        modifier.fillMaxWidth(),
        gridState,
        contentPadding = PaddingValues(horizontal = MemixSpacing.space4, vertical = MemixSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "name") { NameSection(nameField, Modifier.padding(bottom = MemixSpacing.space2)) }
        itemsIndexed(stickers, key = { _, path -> path }, contentType = { _, _ -> "sticker" }) { index, path ->
            StickerTile(
                path = builder.keptFromPack[path] ?: path,
                position = index + 1,
                selected = path in builder.selected,
                tooDetailed = path in builder.tooDetailed,
                thumbnails = thumbnails,
                inert = builder.preparing != null,
                onToggle = { readable -> onIntent(StickerPackIntent.ToggleSticker(path, readable)) },
            )
        }
        if (notice != null) item(span = { GridItemSpan(maxLineSpan) }, key = "notice", contentType = "notice") { Notice(notice) }
        if (counterInContent) item(span = { GridItemSpan(maxLineSpan) }, key = "counter", contentType = "counter") { Counter(builder.selected.size) }
    }
}

// "Pack name" label, the single-line field in P1-10's look (cap 30, the counter from 25), and the legal helper.
@Composable
private fun NameSection(field: TextFieldState, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.pack_name_label)
    val count = field.text.toString().graphemeCount()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MemixSpacing.space1)) {
        Text(label, MemixTheme.type.label, color = MemixColors.textSecondary)
        BasicTextField(
            state = field,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MemixSize.touchTarget)
                .clip(MemixShapes.radiusMd)
                .background(MemixColors.surfaceRaised)
                .semantics { contentDescription = label },
            inputTransformation = NameLimit,
            textStyle = MemixTheme.type.body.copy(color = MemixColors.text),
            cursorBrush = SolidColor(MemixColors.text),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            lineLimits = TextFieldLineLimits.SingleLine,
            decorator = { innerField -> Box(Modifier.padding(MemixSpacing.space3), contentAlignment = Alignment.CenterStart) { innerField() } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
            Text(stringResource(Res.string.pack_name_helper), MemixTheme.type.caption, Modifier.weight(1f), color = MemixColors.textSecondary)
            if (count >= COUNTER_FROM) Text("$count/${StickerPackLimits.NAME_MAX_CHARS}", MemixTheme.type.caption, color = MemixColors.textMuted)
        }
    }
}

// One line, at most 30 user-perceived characters; a paste that would pass it keeps what fits.
private val NameLimit = InputTransformation {
    val text = asCharSequence().toString()
    val oneLine = text.replace('\n', ' ').replace('\r', ' ')
    val limited = if (oneLine.graphemeCount() > StickerPackLimits.NAME_MAX_CHARS) oneLine.takeGraphemes(StickerPackLimits.NAME_MAX_CHARS) else oneLine
    if (limited != text) replace(0, length, limited)
}

// A `surface-raised` square, the picture fitted inside; selected: a white 2 dp ring outside it and a white check disc. A file that can't
// be read shows the photo icon and can't be picked.
@Composable
private fun StickerTile(path: String, position: Int, selected: Boolean, tooDetailed: Boolean, thumbnails: PackThumbnails, inert: Boolean, onToggle: (readable: Boolean) -> Unit) {
    val picture by produceState(thumbnails.cached(path), path) { value = true to thumbnails.load(path) }
    val (decoded, image) = picture
    // A tile still being decoded can be tapped; only a file that was tried and failed is "missing".
    val readable = !decoded || image != null
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tileName = stringResource(Res.string.a11y_pack_tile, position)
    val name = if (selected) stringResource(Res.string.a11y_pack_tile_selected, tileName) else tileName
    val tooBigName = stringResource(Res.string.a11y_pack_too_big)
    val spokenName = if (tooDetailed) "$name, $tooBigName" else name
    val ringWidth = MemixStroke.strokeSelection
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // Outside the tile (the gaps are space-2 wide): half the stroke sits on each side of an outline grown by one stroke width.
            .drawBehind {
                if (!selected) return@drawBehind
                val width = ringWidth.toPx()
                drawRoundRect(
                    MemixColors.selection,
                    topLeft = Offset(-width / 2, -width / 2),
                    size = Size(size.width + width, size.height + width),
                    cornerRadius = CornerRadius(MemixShapes.radiusMd.topStart.toPx(Size(size.width, size.height), this) + width / 2),
                    style = Stroke(width),
                )
            }
            .clip(MemixShapes.radiusMd)
            .background(if (pressed) MemixColors.hairline else MemixColors.surfaceRaised)
            .clickable(interactionSource, indication = null, enabled = !inert, role = Role.Button) { onToggle(readable) }
            .semantics(mergeDescendants = true) { contentDescription = spokenName },
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> Image(BitmapPainter(image), null, Modifier.fillMaxSize().padding(MemixSpacing.space2), contentScale = ContentScale.Fit)
            decoded -> Icon(MemixIcons.Photo, null, MemixColors.textMuted)
        }
        CheckDisc(visible = selected, Modifier.align(Alignment.TopEnd))
        if (tooDetailed) {
            Box(
                Modifier.align(Alignment.TopStart).padding(MemixSpacing.space1).size(MARK_SIZE).clip(MemixShapes.radiusFull).background(MemixColors.danger),
                contentAlignment = Alignment.Center,
            ) {
                // Drawn, not typeset: a glyph is cut off in the 24 dp disc at 200% font.
                Box(Modifier.fillMaxSize().drawBehind {
                    val bar = size.width / EXCLAMATION_BAR_DIVISOR
                    drawRoundRect(MemixColors.onPrimary, Offset((size.width - bar) / 2, size.height * 0.2f), Size(bar, size.height * 0.42f), CornerRadius(bar / 2))
                    drawCircle(MemixColors.onPrimary, bar * 0.6f, Offset(size.width / 2, size.height * 0.76f))
                })
            }
        }
    }
}

// The white check disc fades in and out over 120 ms (instant with reduce motion).
@Composable
private fun CheckDisc(visible: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = MemixTheme.reduceMotion
    AnimatedVisibility(
        visible,
        modifier.padding(MemixSpacing.space1),
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(MemixMotion.durationPress)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(MemixMotion.durationPress)),
    ) {
        Box(Modifier.size(MARK_SIZE).clip(MemixShapes.radiusFull).background(MemixColors.selection), contentAlignment = Alignment.Center) {
            Icon(MemixIcons.Check, null, MemixColors.onPrimary, size = MARK_ICON_SIZE)
        }
    }
}

// The counter (unless it scrolls with the content) and the buttons, pinned on `surface`; while preparing, the progress.
@Composable
private fun BottomBar(state: StickerPackUiState, onIntent: (StickerPackIntent) -> Unit, nameField: TextFieldState, counterInContent: Boolean, modifier: Modifier = Modifier) {
    val builder = state.builder
    val defaultName = stringResource(Res.string.pack_name_default)
    Column(
        modifier.fillMaxWidth().background(MemixColors.surface).padding(MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
    ) {
        val preparing = builder.preparing
        if (preparing != null) {
            Preparing(preparing)
            return@Column
        }
        if (!counterInContent) Counter(builder.selected.size)
        val addLabel = stringResource(if (builder.addedBefore) Res.string.pack_update else Res.string.pack_add)
        ButtonPair(
            emojis = { Button(stringResource(Res.string.pack_emojis), { onIntent(StickerPackIntent.OpenEmojis) }, it) },
            add = { Button(addLabel, { onIntent(StickerPackIntent.Add(nameField.text.toString(), defaultName)) }, it, ButtonVariant.Primary) },
        )
    }
}

// Too detailed, or one of WhatsApp's calm notices; null when there is none.
@Composable
private fun BuilderUi.noticeText(): String? = when {
    tooDetailed.isNotEmpty() -> pluralStringResource(Res.plurals.pack_too_big, tooDetailed.size, tooDetailed.size)
    notice == BuilderNotice.NOT_ADDED -> stringResource(Res.string.pack_not_added)
    notice == BuilderNotice.NO_WHATSAPP -> stringResource(Res.string.pack_no_whatsapp)
    notice == BuilderNotice.WHATSAPP_TOO_OLD -> stringResource(Res.string.pack_whatsapp_old)
    else -> null
}

@Composable
private fun Notice(message: String) {
    Text(message, MemixTheme.type.body, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun Counter(selected: Int) {
    val text = when {
        selected < StickerPackLimits.MIN_STICKERS -> stringResource(Res.string.pack_count_need)
        selected >= StickerPackLimits.MAX_STICKERS -> pluralStringResource(Res.plurals.pack_count_max, selected, selected)
        else -> pluralStringResource(Res.plurals.pack_count, selected, selected)
    }
    Text(text, MemixTheme.type.body, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MemixColors.textSecondary)
}

// "Getting your pack ready · 12 of 30" over a determinate bar; screen readers hear a milestone at most every 25%.
@Composable
private fun Preparing(preparing: PreparingUi) {
    val text = stringResource(Res.string.pack_preparing, preparing.done, preparing.total)
    val quarter = if (preparing.total == 0) 0 else preparing.done * QUARTERS / preparing.total
    val milestone = stringResource(Res.string.a11y_pack_progress_milestone, quarter * PERCENT_PER_QUARTER)
    Column(verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
        Text(text, MemixTheme.type.body, color = MemixColors.textSecondary)
        ProgressBar(if (preparing.total == 0) 0f else preparing.done.toFloat() / preparing.total, text)
        // Changes only at 25%, 50%, 75% and 100%, so the polite announcement doesn't repeat for every sticker.
        key(quarter) { Box(Modifier.size(0.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = milestone }) }
    }
}

/** Emojis (a third) and Add (two thirds), `space-3` apart; stacked, Emojis above Add, when a label doesn't fit its share (200% font). */
@Composable
private fun ButtonPair(emojis: @Composable (Modifier) -> Unit, add: @Composable (Modifier) -> Unit) {
    val gap = MemixSpacing.space3
    Layout(content = { emojis(Modifier); add(Modifier) }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val third = (width - gapPx) / 3
        val twoThirds = width - gapPx - third
        val sideBySide = measurables[0].maxIntrinsicWidth(Constraints.Infinity) <= third && measurables[1].maxIntrinsicWidth(Constraints.Infinity) <= twoThirds
        if (sideBySide) {
            val first = measurables[0].measure(Constraints.fixed(third, measurables[0].minIntrinsicHeight(third).coerceAtLeast(measurables[1].minIntrinsicHeight(twoThirds))))
            val second = measurables[1].measure(Constraints.fixed(twoThirds, first.height))
            layout(width, first.height) {
                first.place(0, 0)
                second.place(third + gapPx, 0)
            }
        } else {
            val first = measurables[0].measure(Constraints.fixedWidth(width))
            val second = measurables[1].measure(Constraints.fixedWidth(width))
            layout(width, first.height + gapPx + second.height) {
                first.place(0, 0)
                second.place(0, first.height + gapPx)
            }
        }
    }
}

// Fewer than 3 stickers in My stickers: how to make more (P2-14's action: Remove background with a photo on the canvas, else Add photo).
@Composable
private fun NeedMoreStickers(have: Int, hasImage: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(MemixSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MemixSpacing.space3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(MemixIcons.Sticker, null, MemixColors.textSecondary, size = EMPTY_ICON_SIZE)
        Text(stringResource(Res.string.pack_need_title), MemixTheme.type.title, textAlign = TextAlign.Center)
        Text(pluralStringResource(Res.plurals.pack_need_body, have, have), MemixTheme.type.body, color = MemixColors.textSecondary, textAlign = TextAlign.Center)
        if (hasImage) {
            Button(stringResource(Res.string.pack_need_remove_background), onAction, variant = ButtonVariant.Primary)
        } else {
            Button(stringResource(Res.string.pack_need_add_photo), onAction, variant = ButtonVariant.Secondary)
        }
    }
}

private val TILE_MIN_WIDTH = MemixSize.gridTileMin
private val MARK_SIZE = MemixSpacing.space6
private val MARK_ICON_SIZE = MemixSize.iconSmall
private val EMPTY_ICON_SIZE = MemixSpacing.space8
private const val PINNED_MAX_SHARE = MemixSize.pinnedBarMaxFraction
private const val EXCLAMATION_BAR_DIVISOR = 6f
private const val COUNTER_FROM = 25
private const val QUARTERS = 4
private const val PERCENT_PER_QUARTER = 25
