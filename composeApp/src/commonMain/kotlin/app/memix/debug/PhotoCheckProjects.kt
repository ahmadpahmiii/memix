package app.memix.debug

import app.memix.core.domain.project.photoCanvasFor
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.TextLayer
import app.memix.core.model.project.StickerLayer
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.Layer
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PhotoScene
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.Transform

/** One generated test picture (see [writeDebugPhotos]); a [sticker] is a PNG with a transparent background. */
internal class DebugPicture(val fileName: String, val widthPx: Int, val heightPx: Int, val label: String, val sticker: Boolean = false)

/**
 * The photo editor's hand-check scenes (spec docs/ux/specs/P2-01-photo-editor-shell.md → Requests 1), opened with
 * `--es memix.openEditor <name>` in builds with hand checks:
 * - `photo-layers-10`: ten visible image layers plus an 11th, hidden. A 12 MP photo at the back (the canvas takes its 4:3
 *   shape), five more 12 MP-class pictures and four smaller ones; three at 60%, 40% and 10% opacity, two turned (30° and
 *   90°), one partly off the frame, one locked; all overlapping, so every row state has an example.
 * - `photo-empty`: no layers, the blank canvas (1:1, white), made in memory like a Create row start.
 * - `photo-missing-media`: two layers, one whose file is gone.
 * - `photo-background-image` (P2-12): a 1:1 canvas with a patterned picture (3000 x 2000, a grid and marked corners, so any
 *   shift shows) behind three smaller layers, one at 60%: the picture covers the canvas, cropped at the sides.
 * - `photo-background-missing` (P2-12): the same scene whose background picture's file is gone: black, a toast, a line in the panel.
 * - `photo-text-check` (P2-03): a 9:16 1080 x 1920 canvas with a plain full-bleed picture and the same six captions as the video
 *   editor's `text-check` (Classic Hindi and emoji over the top, Modern accented Latin at 50% and -15°, Bubbly two lines at 250%
 *   and 30° partly off the right edge, Handwritten with Devanagari, Serif "THE END", and Classic red "OVERLAP" above it in the
 *   stack): compare the screen, the render hook and the video export frame (spec P2-03 → Measured).
 * - `photo-stickers` (P2-03): a photo, one text and five stickers with transparent backgrounds: a 512 px square, a 3:1 (locked),
 *   a 1:3 (hidden), a 96 px tiny one and one whose file is gone.
 * - `photo-layers-10` also carries three text layers on top (Classic at the top, Bubbly at 250% and 30°, a Hindi line).
 * The pictures for the photo-shape check (4:3, 3:2, 1:1, 9:16, 6:13, 3:1) are written too, for picking from the gallery.
 */
internal object PhotoCheckProjects {
    const val LAYERS_10 = "photo-layers-10"
    const val EMPTY = "photo-empty"
    const val MISSING_MEDIA = "photo-missing-media"
    const val BACKGROUND_IMAGE = "photo-background-image"
    const val BACKGROUND_MISSING = "photo-background-missing"
    const val TEXT_CHECK = "photo-text-check"
    const val STICKERS = "photo-stickers"
    val NAMES = listOf(LAYERS_10, EMPTY, MISSING_MEDIA, BACKGROUND_IMAGE, BACKGROUND_MISSING, TEXT_CHECK, STICKERS)

    const val PICTURES_DIR = "${ExportCheckProject.MEDIA_DIR}/photo-scenes"

    private const val BIG = 4000
    private const val BIG_HEIGHT = 3000
    private val big = (1..5).map { DebugPicture("big-$it.jpg", BIG, BIG_HEIGHT, "BIG $it") }
    private val mid = (1..4).map { DebugPicture("mid-$it.jpg", 3000, 2000, "MID $it") }
    private val base = DebugPicture("base-12mp.jpg", BIG, BIG_HEIGHT, "BASE")
    private val hidden = DebugPicture("hidden.jpg", 3000, 2000, "HIDDEN")

    /** The patterned picture of the background checks, in the photo scenes and the video editor's `canvas-image-check`. */
    val backgroundPattern = DebugPicture("background-pattern.jpg", 3000, 2000, "BG")
    private val shapes = listOf(
        DebugPicture("shape-4x3.jpg", 4032, 3024, "4:3"),
        DebugPicture("shape-3x2.jpg", 3000, 2000, "3:2"),
        DebugPicture("shape-1x1.jpg", 2000, 2000, "1:1"),
        DebugPicture("shape-9x16.jpg", 2160, 3840, "9:16"),
        DebugPicture("shape-6x13.jpg", 1170, 2532, "6:13"),
        DebugPicture("shape-3x1.jpg", 6000, 2000, "3:1"),
    )

    private val stickerSquare = DebugPicture("sticker-square.png", 512, 512, "SQ", sticker = true)
    private val stickerWide = DebugPicture("sticker-wide.png", 1536, 512, "WIDE 3:1", sticker = true)
    private val stickerTall = DebugPicture("sticker-tall.png", 512, 1536, "TALL 1:3", sticker = true)
    private val stickerTiny = DebugPicture("sticker-tiny.png", 96, 96, "S", sticker = true)

    /** Every picture the scenes and the photo-shape check use. */
    val pictures: List<DebugPicture> = listOf(base) + big + mid + hidden + backgroundPattern + shapes + stickerSquare + stickerWide + stickerTall + stickerTiny

    private fun image(id: String, picture: DebugPicture, transform: Transform, locked: Boolean = false, hidden: Boolean = false, opacity: Float = 1f): Layer =
        ImageLayer(id, mediaOf(picture.fileName, PixelSize(picture.widthPx, picture.heightPx)), transform, locked, hidden, opacity)

    private val WHITE = ArgbColor(0xFFFFFFFF)
    private val BLACK = ArgbColor(0xFF000000)
    private val YELLOW = ArgbColor(0xFFFFD60A)
    private val CAPTION = CaptionStyle(fontId = "anton", color = WHITE, outlineColor = BLACK)

    private fun text(id: String, words: String, style: CaptionStyle, transform: Transform): Layer = TextLayer(id, words, style, transform)

    private fun sticker(id: String, picture: DebugPicture, transform: Transform, locked: Boolean = false, hidden: Boolean = false): Layer =
        StickerLayer(id, mediaOf(picture.fileName, PixelSize(picture.widthPx, picture.heightPx)), transform, locked, hidden)

    // Pushed by hand or generated, so the origin is a stand-in; the editor reads the copy.
    private fun mediaOf(fileName: String, size: PixelSize) = MediaRef(
        MediaOrigin.GalleryUri("content://app.memix.debug/$fileName"),
        MediaKind.IMAGE,
        cachedCopyPath = "$PICTURES_DIR/$fileName",
        pixelSize = size,
    )

    /** The media of the background checks: the patterned picture, or one whose file isn't there. */
    fun backgroundMedia(missing: Boolean): MediaRef =
        if (missing) mediaOf("gone-background.jpg", PixelSize(3000, 2000)) else mediaOf(backgroundPattern.fileName, PixelSize(backgroundPattern.widthPx, backgroundPattern.heightPx))

    /** The scene [name] as a project, or null for an unknown name or `photo-empty` (a blank canvas isn't saved). */
    fun build(name: String): Project? = when (name) {
        LAYERS_10 -> layers10()
        MISSING_MEDIA -> missingMedia()
        TEXT_CHECK -> textCheck()
        STICKERS -> stickers()
        BACKGROUND_IMAGE -> backgroundImage(BACKGROUND_IMAGE, missing = false)
        BACKGROUND_MISSING -> backgroundImage(BACKGROUND_MISSING, missing = true)
        else -> null
    }

    private fun layers10(): Project {
        val layers = listOf(
            image("l-base", base, Transform()),
            image("l-big-1", big[0], Transform(0.35f, 0.4f, 0.8f), opacity = 0.6f),
            image("l-big-2", big[1], Transform(0.65f, 0.35f, 0.6f, rotationDegrees = 30f), opacity = 0.4f),
            image("l-big-3", big[2], Transform(0.5f, 0.6f, 0.7f), opacity = 0.1f),
            image("l-big-4", big[3], Transform(0.3f, 0.7f, 0.5f, rotationDegrees = 90f)),
            // Partly off the frame's right edge: clipped there, never lost (its center is inside).
            image("l-big-5", big[4], Transform(0.95f, 0.2f, 0.6f)),
            image("l-mid-1", mid[0], Transform(0.7f, 0.7f, 0.5f), locked = true),
            image("l-mid-2", mid[1], Transform(0.2f, 0.2f, 0.4f)),
            image("l-mid-3", mid[2], Transform(0.5f, 0.5f, 0.45f)),
            image("l-mid-4", mid[3], Transform(0.8f, 0.5f, 0.35f)),
            image("l-hidden", hidden, Transform(0.5f, 0.5f, 0.5f), hidden = true),
            // Three text layers on top (spec P2-03 request 1): the drag-speed bars must hold with text in the scene.
            text("l-text-classic", "WHEN THE BUILD PASSES FIRST TRY", CAPTION, Transform(0.5f, 0.12f)),
            text("l-text-bubbly", "BUBBLY\nTWO LINES", CaptionStyle("baloo2", WHITE, ArgbColor(0xFFAF52DE)), Transform(0.7f, 0.65f, 2.5f, rotationDegrees = 30f)),
            text("l-text-hindi", "जब BUILD PASS हो", CaptionStyle("kalam", ArgbColor(0xFF64D2FF), BLACK), Transform(0.4f, 0.85f)),
        )
        return project(LAYERS_10, photoCanvasFor(PixelSize(base.widthPx, base.heightPx)), layers)
    }

    /** The six captions of the video's `text-check` on a 9:16 photo canvas, in its lane order (a lower lane draws over a higher one). */
    private fun textCheck(): Project {
        val canvas = photoCanvasFor(PixelSize(2160, 3840))
        val layers = listOf(
            image("t-photo", shapes[3], Transform()),
            text("text-classic", "जब BUILD PASS हो 😂", CAPTION, Transform(0.5f, 0.2f)),
            text("text-modern", "Ação rápida, ¿sí? ¡Ñandú!", CaptionStyle("poppins", YELLOW, null), Transform(0.5f, 0.45f, scale = 0.5f, rotationDegrees = -15f)),
            text("text-serif", "THE END", CaptionStyle("rozha", WHITE, null), Transform(0.5f, 0.45f)),
            text("text-handwritten", "me: हाँ, sure", CaptionStyle("kalam", ArgbColor(0xFF64D2FF), BLACK), Transform(0.5f, 0.75f)),
            // Over THE END, later in the stack, so it is drawn on top.
            text("text-over", "OVERLAP", CaptionStyle("anton", ArgbColor(0xFFFF3B30), BLACK), Transform(0.55f, 0.47f)),
            // Partly off the frame's right edge: clipped there.
            text("text-bubbly", "BUBBLY\nTWO LINES", CaptionStyle("baloo2", WHITE, ArgbColor(0xFFAF52DE)), Transform(0.85f, 0.7f, scale = 2.5f, rotationDegrees = 30f)),
        )
        return project(TEXT_CHECK, canvas, layers)
    }

    /** A photo, one text and five stickers (spec P2-03 request 1): the landing size, a locked, a hidden and a missing one. */
    private fun stickers(): Project {
        val gone = DebugPicture("gone-sticker.png", 512, 512, "GONE", sticker = true)
        val layers = listOf(
            image("s-photo", shapes[0], Transform()),
            text("s-text", "STICKERS", CAPTION, Transform(0.5f, 0.12f)),
            sticker("s-square", stickerSquare, Transform(0.25f, 0.4f)),
            sticker("s-wide-locked", stickerWide, Transform(0.7f, 0.35f), locked = true),
            sticker("s-tall-hidden", stickerTall, Transform(0.5f, 0.55f), hidden = true),
            sticker("s-tiny", stickerTiny, Transform(0.3f, 0.75f)),
            sticker("s-missing", gone, Transform(0.75f, 0.75f)),
        )
        return project(STICKERS, photoCanvasFor(PixelSize(shapes[0].widthPx, shapes[0].heightPx)), layers)
    }

    private fun missingMedia(): Project {
        val gone = DebugPicture("gone.jpg", 3000, 2000, "GONE")
        val layers = listOf(
            image("m-here", base, Transform()),
            image("m-gone", gone, Transform(0.5f, 0.5f, 0.6f)),
        )
        return project(MISSING_MEDIA, photoCanvasFor(PixelSize(base.widthPx, base.heightPx)), layers)
    }

    private fun backgroundImage(id: String, missing: Boolean): Project {
        val layers = listOf(
            image("b-mid-1", mid[0], Transform(0.3f, 0.3f, 0.5f)),
            image("b-mid-2", mid[1], Transform(0.7f, 0.5f, 0.45f), opacity = 0.6f),
            image("b-big-1", big[0], Transform(0.4f, 0.75f, 0.4f, rotationDegrees = 20f)),
        )
        val canvas = photoCanvasFor(PixelSize(1080, 1080)).copy(background = CanvasBackground.Image(backgroundMedia(missing)))
        return project(id, canvas, layers)
    }

    private fun project(id: String, canvas: Canvas, layers: List<Layer>) = Project(
        id = id,
        type = ProjectType.PHOTO,
        name = "Editor check: $id",
        canvas = canvas,
        photo = PhotoScene(layers),
        createdAtEpochUs = EDITED_AT_EPOCH_US,
        updatedAtEpochUs = EDITED_AT_EPOCH_US,
    )

    // 27 Oct 2026, 12:00:00 UTC; saving sets the real edit time.
    private const val EDITED_AT_EPOCH_US = 1_793_102_400_000_000L
}
