package app.memix.core.ui.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.memix.core.designsystem.MemixColors
import app.memix.core.designsystem.MemixIcons
import app.memix.core.designsystem.MemixMotion
import app.memix.core.designsystem.MemixSpacing
import app.memix.core.designsystem.MemixTheme
import app.memix.core.designsystem.Text
import app.memix.core.designsystem.component.Button
import app.memix.core.designsystem.component.Swatch
import app.memix.core.model.project.CanvasBackground
import app.memix.core.ui.scene.SceneThumbnails
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.canvas_background
import memix.core.ui.generated.resources.canvas_background_image
import memix.core.ui.generated.resources.canvas_image_label
import memix.core.ui.generated.resources.canvas_image_missing
import memix.core.ui.generated.resources.canvas_image_missing_a11y
import memix.core.ui.generated.resources.canvas_image_pick_a11y
import memix.core.ui.generated.resources.canvas_image_remove
import memix.core.ui.generated.resources.canvas_image_remove_a11y
import memix.core.ui.generated.resources.canvas_image_replace_a11y
import memix.core.ui.generated.resources.tool_replace
import org.jetbrains.compose.resources.stringResource

/*
 * The Canvas panel's Background group, both editors (spec docs/ux/specs/P2-12-canvas-background-image.md → Layout): the
 * swatch row (Blur for video, Image, then the caption palette), the image row (Replace and Remove) while a picture is
 * the background, and the line under them. What the panel's other rows hold is each editor's own.
 */

/** What the canvas's background is now, as the Background group shows it. */
@Immutable
sealed interface BackgroundUi {
    /** A color; [argb] is 0xAARRGGBB. */
    data class Solid(val argb: Long) : BackgroundUi

    /** A blurred copy of the playing clip (video only). */
    data object Blur : BackgroundUi

    /** A picture: [path] is the app's copy (relative to the files directory), [missing] when its file can't be read. */
    data class Image(val path: String?, val missing: Boolean) : BackgroundUi
}

/** [this] canvas background for the Background group; [imageMissing] is whether a picture's file couldn't be read. */
fun CanvasBackground.toUi(imageMissing: Boolean): BackgroundUi = when (this) {
    is CanvasBackground.Solid -> BackgroundUi.Solid(color.argb)
    CanvasBackground.Blur -> BackgroundUi.Blur
    is CanvasBackground.Image -> BackgroundUi.Image(source.cachedCopyPath, imageMissing)
}

/** What the Background group tells its editor. */
interface BackgroundActions {
    fun onBlur()
    fun onColor(argb: Long)

    /** The Image swatch with no picture yet, or Replace: the editor opens the picker. */
    fun onPickImage()

    /** Remove: the editor's default color goes back. */
    fun onRemoveImage()
}

/**
 * The Background group: [heading], the swatches, the image row and [hint] (the line that says no clip or layer lets the
 * background show; null when something does). [blurName] is the Blur swatch's spoken name in the video editor, null in the
 * photo editor (no Blur there).
 */
@Composable
fun BackgroundGroup(
    background: BackgroundUi,
    blurName: String?,
    thumbnails: SceneThumbnails,
    hint: String?,
    actions: BackgroundActions,
) {
    val image = background as? BackgroundUi.Image
    val picturePath = image?.path
    LaunchedEffect(picturePath) { picturePath?.let(thumbnails::request) }
    // One answer for the swatch and the line, so they never disagree: the editor says so, or the thumbnail can't be decoded.
    val missing = image != null && (image.missing || (picturePath != null && thumbnails.failed(picturePath)))
    ChoiceRow(stringResource(Res.string.canvas_background)) {
        if (blurName != null) {
            Swatch(
                null,
                blurName,
                selected = background == BackgroundUi.Blur,
                onClick = actions::onBlur,
                icon = MemixIcons.Blur,
            )
        }
        ImageSwatch(image, missing, thumbnails, actions)
        CanvasColorSwatches((background as? BackgroundUi.Solid)?.argb, onPick = actions::onColor)
    }
    ImageRow(visible = image != null, actions)
    if (missing) {
        Text(stringResource(Res.string.canvas_image_missing), MemixTheme.type.caption, color = MemixColors.textSecondary)
    }
    if (hint != null) Text(hint, MemixTheme.type.caption, color = MemixColors.textSecondary)
}

// No picture yet: the icon swatch, a tap opens the picker. A picture: its thumbnail, ringed. Its file gone: the icon in
// text-muted, ringed (it is still the background), and the line below says what to do.
@Composable
private fun ImageSwatch(image: BackgroundUi.Image?, missing: Boolean, thumbnails: SceneThumbnails, actions: BackgroundActions) {
    val picture = image?.path?.let(thumbnails::pictureOf)
    val name = stringResource(if (missing) Res.string.canvas_image_missing_a11y else Res.string.canvas_background_image)
    Swatch(
        null,
        name,
        selected = image != null,
        onClick = actions::onPickImage,
        icon = MemixIcons.Photo,
        iconTint = if (missing) MemixColors.textMuted else MemixColors.textSecondary,
        picture = picture.takeUnless { missing },
        clickLabel = if (image == null) stringResource(Res.string.canvas_image_pick_a11y) else null,
    )
}

// "Image" at the start, Replace and Remove at the end; they wrap under the label at 200% font, Remove under Replace if the pair
// is still too wide. Fades in and out in 120 ms (spec → Motion).
@Composable
private fun ImageRow(visible: Boolean, actions: BackgroundActions) {
    AnimatedVisibility(visible, enter = fadeIn(tween(MemixMotion.durationPress)), exit = fadeOut(tween(MemixMotion.durationPress))) {
        val label = stringResource(Res.string.canvas_image_label)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, MemixTheme.type.body, color = MemixColors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MemixSpacing.space2), verticalArrangement = Arrangement.spacedBy(MemixSpacing.space2)) {
                val replace = stringResource(Res.string.canvas_image_replace_a11y)
                val remove = stringResource(Res.string.canvas_image_remove_a11y)
                Button(stringResource(Res.string.tool_replace), actions::onPickImage, Modifier.semantics { contentDescription = replace })
                Button(stringResource(Res.string.canvas_image_remove), actions::onRemoveImage, Modifier.semantics { contentDescription = remove })
            }
        }
    }
}
