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
import app.memix.core.model.project.ImageLayer
import app.memix.core.ui.scene.SceneBitmap
import app.memix.core.ui.scene.SceneImageSource
import app.memix.core.ui.scene.SceneImageUse
import app.memix.core.ui.scene.SceneImages
import app.memix.core.model.project.PhotoCatalog
import app.memix.core.ui.scene.drawPhotoScene
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The debug render hook (spec docs/ux/specs/P2-01-photo-editor-shell.md → Requests 5; builds with hand checks):
 * `--es memix.renderScene <project id> --ei memix.renderWidth 1080` draws the saved photo project through the one
 * scene-drawing function, at that pixel width, into a PNG at `cache/scene-renders/<id>-<width>.png`, and logs where
 * (logcat tag [TAG]). The editor draws the same function at screen size, so the two can be compared; export (P2-08)
 * will call it at export size. Pictures are decoded at their own size (up to the decode cap) as software bitmaps.
 */
class SceneRender : KoinComponent {
    private val getProject: GetProjectUseCase by inject()
    private val imageSource: SceneImageSource by inject()
    private val logger: Logger by inject()

    suspend fun run(projectId: String, widthPx: Int) {
        val project = (getProject(projectId) as? Outcome.Success)?.value
        if (project?.photo == null) return logger.error(TAG, "renderScene: '$projectId' isn't a saved photo project")
        val heightPx = (widthPx.toLong() * project.canvas.heightPx / project.canvas.widthPx).toInt()
        val pictures = HashMap<String, SceneBitmap>()
        for (layer in project.layers().filterIsInstance<ImageLayer>()) {
            val path = layer.source.cachedCopyPath ?: continue
            if (path in pictures) continue
            imageSource.decode(path, PhotoCatalog.MAX_DECODE_PX, SceneImageUse.OFFSCREEN)?.let { pictures[path] = it }
        }
        val images = object : SceneImages {
            override fun imageOf(path: String): SceneBitmap? = pictures[path]

            override fun appearanceOf(path: String): Float = 1f
        }
        val bitmap = ImageBitmap(widthPx, heightPx)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
            drawPhotoScene(project.canvas, project.layers(), images)
        }
        val written = writeDebugPng(bitmap, "$projectId-$widthPx.png")
        logger.debug(TAG, "renderScene: ${written ?: "writing failed"} (${pictures.size} pictures, ${widthPx} x $heightPx)")
    }

    companion object {
        const val TAG = "MemixSceneRender"
    }
}

/** Writes [image] as a PNG under the app's `cache/scene-renders/` and returns its path, or null when it can't. */
internal expect suspend fun writeDebugPng(image: ImageBitmap, fileName: String): String?
