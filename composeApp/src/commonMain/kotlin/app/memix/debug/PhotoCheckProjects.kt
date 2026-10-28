package app.memix.debug

import app.memix.core.domain.project.photoCanvasFor
import app.memix.core.model.project.Canvas
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

/** One generated test picture (see [writeDebugPhotos]). */
internal class DebugPicture(val fileName: String, val widthPx: Int, val heightPx: Int, val label: String)

/**
 * The photo editor's hand-check scenes (spec docs/ux/specs/P2-01-photo-editor-shell.md → Requests 1), opened with
 * `--es memix.openEditor <name>` in builds with hand checks:
 * - `photo-layers-10`: ten visible image layers plus an 11th, hidden. A 12 MP photo at the back (the canvas takes its 4:3
 *   shape), five more 12 MP-class pictures and four smaller ones; three at 60%, 40% and 10% opacity, two turned (30° and
 *   90°), one partly off the frame, one locked; all overlapping, so every row state has an example.
 * - `photo-empty`: no layers, the blank canvas (1:1, white), made in memory like a Create row start.
 * - `photo-missing-media`: two layers, one whose file is gone.
 * The pictures for the photo-shape check (4:3, 3:2, 1:1, 9:16, 6:13, 3:1) are written too, for picking from the gallery.
 */
internal object PhotoCheckProjects {
    const val LAYERS_10 = "photo-layers-10"
    const val EMPTY = "photo-empty"
    const val MISSING_MEDIA = "photo-missing-media"
    val NAMES = listOf(LAYERS_10, EMPTY, MISSING_MEDIA)

    const val PICTURES_DIR = "${ExportCheckProject.MEDIA_DIR}/photo-scenes"

    private const val BIG = 4000
    private const val BIG_HEIGHT = 3000
    private val big = (1..5).map { DebugPicture("big-$it.jpg", BIG, BIG_HEIGHT, "BIG $it") }
    private val mid = (1..4).map { DebugPicture("mid-$it.jpg", 3000, 2000, "MID $it") }
    private val base = DebugPicture("base-12mp.jpg", BIG, BIG_HEIGHT, "BASE")
    private val hidden = DebugPicture("hidden.jpg", 3000, 2000, "HIDDEN")
    private val shapes = listOf(
        DebugPicture("shape-4x3.jpg", 4032, 3024, "4:3"),
        DebugPicture("shape-3x2.jpg", 3000, 2000, "3:2"),
        DebugPicture("shape-1x1.jpg", 2000, 2000, "1:1"),
        DebugPicture("shape-9x16.jpg", 2160, 3840, "9:16"),
        DebugPicture("shape-6x13.jpg", 1170, 2532, "6:13"),
        DebugPicture("shape-3x1.jpg", 6000, 2000, "3:1"),
    )

    /** Every picture the scenes and the photo-shape check use. */
    val pictures: List<DebugPicture> = listOf(base) + big + mid + hidden + shapes

    private fun image(id: String, picture: DebugPicture, transform: Transform, locked: Boolean = false, hidden: Boolean = false, opacity: Float = 1f): Layer =
        ImageLayer(id, mediaOf(picture.fileName, PixelSize(picture.widthPx, picture.heightPx)), transform, locked, hidden, opacity)

    // Pushed by hand or generated, so the origin is a stand-in; the editor reads the copy.
    private fun mediaOf(fileName: String, size: PixelSize) = MediaRef(
        MediaOrigin.GalleryUri("content://app.memix.debug/$fileName"),
        MediaKind.IMAGE,
        cachedCopyPath = "$PICTURES_DIR/$fileName",
        pixelSize = size,
    )

    /** The scene [name] as a project, or null for an unknown name or `photo-empty` (a blank canvas isn't saved). */
    fun build(name: String): Project? = when (name) {
        LAYERS_10 -> layers10()
        MISSING_MEDIA -> missingMedia()
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
        )
        return project(LAYERS_10, photoCanvasFor(PixelSize(base.widthPx, base.heightPx)), layers)
    }

    private fun missingMedia(): Project {
        val gone = DebugPicture("gone.jpg", 3000, 2000, "GONE")
        val layers = listOf(
            image("m-here", base, Transform()),
            image("m-gone", gone, Transform(0.5f, 0.5f, 0.6f)),
        )
        return project(MISSING_MEDIA, photoCanvasFor(PixelSize(base.widthPx, base.heightPx)), layers)
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
