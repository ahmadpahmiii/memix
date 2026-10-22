package app.memix.feature.drafts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import app.memix.core.designsystem.Icon
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixShapes
import app.memix.core.designsystem.MemixSize
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.IconButton
import app.memix.core.designsystem.component.Menu
import app.memix.core.designsystem.component.MenuItem
import app.memix.core.designsystem.focusRing
import app.memix.core.designsystem.pressedColor
import app.memix.core.domain.drafts.EditedTime
import app.memix.core.model.project.ProjectType
import memix.feature.drafts.generated.resources.Res
import memix.feature.drafts.generated.resources.a11y_draft_row
import memix.feature.drafts.generated.resources.drafts_delete
import memix.feature.drafts.generated.resources.drafts_duplicate
import memix.feature.drafts.generated.resources.drafts_edited_date
import memix.feature.drafts.generated.resources.drafts_edited_days
import memix.feature.drafts.generated.resources.drafts_edited_hours
import memix.feature.drafts.generated.resources.drafts_edited_minutes
import memix.feature.drafts.generated.resources.drafts_edited_now
import memix.feature.drafts.generated.resources.drafts_edited_yesterday
import memix.feature.drafts.generated.resources.drafts_more_a11y
import memix.feature.drafts.generated.resources.drafts_needs_update
import memix.feature.drafts.generated.resources.drafts_rename
import memix.feature.drafts.generated.resources.drafts_type_photo
import memix.feature.drafts.generated.resources.drafts_type_video
import memix.feature.drafts.generated.resources.drafts_untitled_photo
import memix.feature.drafts.generated.resources.drafts_untitled_video
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One draft (spec P1-14 → Draft row): thumbnail with its type badge, name (two lines, then an ellipsis), edit time and
 * More options. A tap opens the draft; a long-press or More options opens the row's menu (Rename, Duplicate, Delete),
 * which screen readers get as the row's actions. The row is one screen-reader node; More options is its own focus
 * stop for keyboards and Switch Access. [focusRequester] lets the list move focus to the row.
 */
@Composable
internal fun DraftRow(
    row: DraftRowUi,
    thumbnails: DraftThumbnailImages,
    undoWindowMillis: Long,
    focusRequester: FocusRequester,
    onIntent: (DraftsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = row.name.ifEmpty { untitledName(row.type) }
    val typeLabel = stringResource(if (row.type == ProjectType.PHOTO) Res.string.drafts_type_photo else Res.string.drafts_type_video)
    val meta = metaText(row)
    val moreLabel = stringResource(Res.string.drafts_more_a11y)
    val actions = rowActions(row.id, undoWindowMillis, onIntent)
    val spoken = stringResource(Res.string.a11y_draft_row, name, typeLabel, meta)
    val interactionSource = remember { MutableInteractionSource() }
    val pressedFill = pressedColor(interactionSource, MemixColors.surface.copy(alpha = 0f), MemixColors.surface)
    val anchors = remember { MenuAnchors() }
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }

    Box(
        modifier
            .fillMaxWidth()
            .onPlaced { anchors.row = it }
            // The pressed fill sits space-2 inside the screen edges, behind the whole row, More options included.
            .drawWithCache {
                val inset = MemixSpacing.space2.toPx()
                val outline = MemixShapes.radiusMd.createOutline(Size(size.width - 2 * inset, size.height), layoutDirection, this)
                onDrawBehind { translate(left = inset) { drawOutline(outline, pressedFill.value) } }
            },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = MemixSize.listThumbSize + MemixSpacing.space2 * 2)
                    .focusRequester(focusRequester)
                    .focusRing(interactionSource, MemixShapes.radiusMd)
                    .pointerInput(Unit) { notePressPoints(anchors) }
                    .onPreviewKeyEvent { event ->
                        // The Menu key or Shift+F10 opens the row's menu (spec → Accessibility → Keyboard).
                        val opensMenu = event.key == Key.Menu || (event.key == Key.F10 && event.isShiftPressed)
                        if (opensMenu && event.type == KeyEventType.KeyUp) menuAt = anchors.rowCenter()
                        opensMenu
                    }
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        // combinedClickable gives the LongPress haptic itself (hapticFeedbackEnabled, on by default).
                        onLongClick = { menuAt = anchors.lastPress.round() },
                        onClick = { onIntent(DraftsIntent.Open(row.id)) },
                    )
                    // One node: "Cat fail, Video meme, Edited 2 hours ago", with Rename, Duplicate and Delete as actions.
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        customActions = actions.map { action -> CustomAccessibilityAction(action.label) { action.onClick(); true } }
                    }
                    .padding(start = MemixSpacing.space4, top = MemixSpacing.space2, bottom = MemixSpacing.space2),
                horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space3),
            ) {
                DraftThumbnail(row.thumbnailPath, row.type, thumbnails)
                Column(
                    Modifier.weight(1f).heightIn(min = MemixSize.listThumbSize),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(name, MemixTheme.type.bodyStrong, maxLines = 2)
                    Text(meta, MemixTheme.type.label, color = MemixColors.textSecondary)
                }
            }
            IconButton(
                MemixIcons.More,
                moreLabel,
                onClick = { menuAt = anchors.moreCenter() },
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = MemixSpacing.space1, end = MemixSpacing.space1)
                    .onPlaced { anchors.more = it },
                tint = MemixColors.textSecondary,
            )
        }
        Menu(
            expanded = menuAt != null,
            pressPoint = menuAt ?: IntOffset.Zero,
            title = moreLabel,
            items = { actions.map { MenuItem(it.label, it.onClick) } },
            onDismiss = { menuAt = null },
        )
    }
}

/** The thumbnail, a placeholder until it's read (fading in over 120 ms), and the type badge in its corner. */
@Composable
private fun DraftThumbnail(path: String?, type: ProjectType, thumbnails: DraftThumbnailImages) {
    val image by produceState(path?.let(thumbnails::cached), path) {
        if (value == null && path != null) value = thumbnails.load(path)
    }
    val alpha = animateFloatAsState(if (image != null) 1f else 0f, tween(MemixMotion.durationPress), label = "thumbnail")
    val typeIcon = if (type == ProjectType.PHOTO) MemixIcons.Photo else MemixIcons.Video
    Box(Modifier.size(MemixSize.listThumbSize).clip(MemixShapes.radiusSm).background(MemixColors.surfaceRaised)) {
        val shown = image
        if (shown == null) {
            Icon(typeIcon, contentDescription = null, tint = MemixColors.textSecondary, modifier = Modifier.align(Alignment.Center))
        } else {
            Image(
                shown,
                contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha.value },
                contentScale = ContentScale.Crop,
            )
        }
        Icon(
            typeIcon,
            contentDescription = null,
            tint = MemixColors.text,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(MemixSpacing.space1)
                .clip(MemixShapes.radiusSm)
                .background(MemixColors.scrim),
            size = MemixSize.iconSmall,
        )
    }
}

@Composable
private fun untitledName(type: ProjectType): String =
    stringResource(if (type == ProjectType.PHOTO) Res.string.drafts_untitled_photo else Res.string.drafts_untitled_video)

@Composable
private fun metaText(row: DraftRowUi): String {
    if (row.needsNewerApp) return stringResource(Res.string.drafts_needs_update)
    return when (val edited = row.edited) {
        EditedTime.JustNow, null -> stringResource(Res.string.drafts_edited_now)
        is EditedTime.MinutesAgo -> pluralStringResource(Res.plurals.drafts_edited_minutes, edited.minutes, edited.minutes)
        is EditedTime.HoursAgo -> pluralStringResource(Res.plurals.drafts_edited_hours, edited.hours, edited.hours)
        EditedTime.Yesterday -> stringResource(Res.string.drafts_edited_yesterday)
        is EditedTime.DaysAgo -> pluralStringResource(Res.plurals.drafts_edited_days, edited.days, edited.days)
        is EditedTime.OnDate -> stringResource(Res.string.drafts_edited_date, edited.date)
    }
}

/** Rename · Duplicate · Delete, for the menu and the row's screen-reader actions alike. */
@Composable
private fun rowActions(draftId: String, undoWindowMillis: Long, onIntent: (DraftsIntent) -> Unit): List<RowAction> = listOf(
    RowAction(stringResource(Res.string.drafts_rename)) { onIntent(DraftsIntent.Rename(draftId)) },
    RowAction(stringResource(Res.string.drafts_duplicate)) { onIntent(DraftsIntent.Duplicate(draftId)) },
    RowAction(stringResource(Res.string.drafts_delete)) { onIntent(DraftsIntent.Delete(draftId, undoWindowMillis)) },
)

private class RowAction(val label: String, val onClick: () -> Unit)

/** Where the row's menu opens: the last press point (long-press), More options' center, or the row's center (keyboard). */
private class MenuAnchors {
    var row: LayoutCoordinates? = null
    var more: LayoutCoordinates? = null
    var lastPress: Offset = Offset.Zero

    fun moreCenter(): IntOffset {
        val rowCoordinates = row ?: return IntOffset.Zero
        val moreCoordinates = more?.takeIf { it.isAttached } ?: return rowCenter()
        val size = moreCoordinates.size.toSize()
        return rowCoordinates.localPositionOf(moreCoordinates, Offset(size.width / 2, size.height / 2)).round()
    }

    fun rowCenter(): IntOffset {
        val size = row?.size ?: return IntOffset.Zero
        return IntOffset(size.width / 2, size.height / 2)
    }
}

// Notes where each touch goes down, without consuming it, so a long-press opens the menu at the finger. The clickable
// area starts at the row's top-left, so its positions are the row's.
private suspend fun PointerInputScope.notePressPoints(anchors: MenuAnchors) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { anchors.lastPress = it.position }
        }
    }
}
