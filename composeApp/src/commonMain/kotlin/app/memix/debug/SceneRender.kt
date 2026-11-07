package app.memix.debug

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.domain.project.layers
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.ImageLayer
import app.memix.core.model.project.PanelsFormat
import app.memix.core.domain.text.PrepareCaptionsUseCase
import app.memix.core.model.project.StickerLayer
import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.ui.scene.SceneCaptions
import app.memix.core.ui.scene.ImageToolRenderer
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import app.memix.core.ui.scene.SceneImages
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.editOrNull
import app.memix.core.model.project.fittedImageSize
import app.memix.core.model.project.shownSize
import app.memix.core.ui.scene.editedLongSidePx
import app.memix.core.ui.scene.renderEditedPicture
import kotlin.math.max
import app.memix.core.ui.scene.drawPhotoScene
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The debug render hook (spec docs/ux/specs/P2-01-photo-editor-shell.md → Requests 5; builds with hand checks):
 * `--es memix.renderScene <project id> --ei memix.renderWidth 1080` draws the saved photo project through the one
 * scene-drawing function, at that pixel width, into a PNG at `cache/scene-renders/<id>-<width>.png`, and logs where
 * (`--ei memix.renderShortSide 1080|2160` sizes the canvas's shorter side instead, for the meme formats' two export sizes: `<id>-s<size>.png`)
 * (logcat tag [TAG]). The editor draws the same function at screen size, so the two can be compared; export (P2-08)
 * will call it at export size. Pictures are decoded at their own size (up to the decode cap) as software bitmaps.
 */
class SceneRender : KoinComponent {
    private val getProject: GetProjectUseCase by inject()
    private val imageSource: SceneImageSource by inject()
    private val imageTools: ImageToolRenderer by inject()
    private val captions: SceneCaptions by inject()
    private val prepareCaptions: PrepareCaptionsUseCase by inject()
    private val logger: Logger by inject()

    /** Draws [projectId] [widthPx] wide, or, when [shortSidePx] is above 0, with the canvas's shorter side that many pixels (1080 or 2160 for a format check). */
    suspend fun run(projectId: String, widthPx: Int, shortSidePx: Int = 0) {
        val project = (getProject(projectId) as? Outcome.Success)?.value
        if (project?.photo == null) return logger.error(TAG, "renderScene: '$projectId' isn't a saved photo project")
        val shortSide = minOf(project.canvas.widthPx, project.canvas.heightPx)
        val widthPx = if (shortSidePx > 0) (shortSidePx.toLong() * project.canvas.widthPx / shortSide).toInt() else widthPx
        val heightPx = (widthPx.toLong() * project.canvas.heightPx / project.canvas.widthPx).toInt()
        val fileName = if (shortSidePx > 0) "$projectId-s$shortSidePx.png" else "$projectId-$widthPx.png"
        val pictures = HashMap<String, SceneBitmap>()
        // The layers' pictures and the background picture (P2-12), each decoded once.
        val cellPhotos = (project.photo?.format as? PanelsFormat)?.cells?.mapNotNull { it.source }.orEmpty()
        val sources = project.layers().mapNotNull { (it as? ImageLayer)?.source ?: (it as? StickerLayer)?.source } + cellPhotos +
            listOfNotNull(project.canvas.background.let { (it as? CanvasBackground.Image)?.source })
        for (source in sources) {
            val path = source.cachedCopyPath ?: continue
            if (path in pictures) continue
            imageSource.decode(path, PhotoCatalog.MAX_DECODE_PX, SceneImageUse.OFFSCREEN)?.let { pictures[path] = it }
        }
        // Edited layers with color tools (P2-04): the same picture the editor draws, made at this output's size.
        val processed = HashMap<String, SceneBitmap>()
        for (layer in project.layers().filterIsInstance<ImageLayer>()) {
            val edit = layer.editOrNull()?.takeIf { it.hasColorEdits } ?: continue
            val picture = layer.source.cachedCopyPath?.let(pictures::get) ?: continue
            val canvasSize = PixelSize(project.canvas.widthPx, project.canvas.heightPx)
            val shown = edit.shownSize(layer.source.pixelSize ?: picture.sourceSize)
            val fitted = fittedImageSize(shown, canvasSize)
            val longSide = editedLongSidePx(max(fitted.width, fitted.height), layer.transform.scale, project.canvas.widthPx, widthPx, shown)
            renderEditedPicture(picture, edit, longSide, imageTools)?.let { processed[layer.id] = SceneBitmap(it, picture.sourceSize) }
        }
        val images = object : SceneImages {
            override fun processedOf(layerId: String): SceneBitmap? = processed[layerId]

            override fun imageOf(path: String): SceneBitmap? = pictures[path]

            override fun appearanceOf(path: String): Float = 1f
        }
        // The caption fonts load before text layers draw (P2-03).
        prepareCaptions()
        val bitmap = ImageBitmap(widthPx, heightPx)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
            drawPhotoScene(project.canvas, project.layers(), images, captions = captions, format = project.photo?.format)
        }
        val written = writeDebugPng(bitmap, fileName)
        logger.debug(TAG, "renderScene: ${written ?: "writing failed"} (${pictures.size} pictures, ${widthPx} x $heightPx)")
    }

    companion object {
        const val TAG = "MemixSceneRender"
    }
}

/** Writes [image] as a PNG under the app's `cache/scene-renders/` and returns its path, or null when it can't. */
internal expect suspend fun writeDebugPng(image: ImageBitmap, fileName: String): String?
