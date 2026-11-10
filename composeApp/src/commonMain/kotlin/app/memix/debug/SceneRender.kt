package app.memix.debug

import androidx.compose.ui.graphics.ImageBitmap
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PixelSize
import app.memix.engine.photo.PhotoSceneRenderer
import app.memix.engine.photo.UnreadablePictureException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The debug render hook (spec docs/ux/specs/P2-01-photo-editor-shell.md → Requests 5; builds with hand checks):
 * `--es memix.renderScene <project id> --ei memix.renderWidth 1080` draws the saved photo project through the one
 * scene-drawing function, at that pixel width, into a PNG at `cache/scene-renders/<id>-<width>.png`, and logs where
 * (`--ei memix.renderShortSide 1080|2160` sizes the canvas's shorter side instead, for the meme formats' two export sizes: `<id>-s<size>.png`)
 * (logcat tag [TAG]). The editor draws the same function at screen size, so the two can be compared; the export (P2-08) calls
 * the same [PhotoSceneRenderer] at export size, with the mark. Pictures are decoded at their own size (up to the decode cap) as software bitmaps.
 */
class SceneRender : KoinComponent {
    private val getProject: GetProjectUseCase by inject()
    private val renderer: PhotoSceneRenderer by inject()
    private val logger: Logger by inject()

    /** Draws [projectId] [widthPx] wide, or, when [shortSidePx] is above 0, with the canvas's shorter side that many pixels (1080 or 2160 for a format check). */
    suspend fun run(projectId: String, widthPx: Int, shortSidePx: Int = 0) {
        val project = (getProject(projectId) as? Outcome.Success)?.value
        if (project?.photo == null) return logger.error(TAG, "renderScene: '$projectId' isn't a saved photo project")
        // The export's own size rule, so a hook render and an export of the same size have the same pixels.
        val output = if (shortSidePx > 0) {
            PhotoExportCatalog.outputSize(project.canvas, shortSidePx)
        } else {
            PixelSize(widthPx, (widthPx.toLong() * project.canvas.heightPx / project.canvas.widthPx).toInt())
        }
        val fileName = if (shortSidePx > 0) "$projectId-s$shortSidePx.png" else "$projectId-${output.widthPx}.png"
        // The hook compares with the editor, which never draws the mark.
        val bitmap = try {
            renderer.render(project, output, watermark = false)
        } catch (e: UnreadablePictureException) {
            return logger.error(TAG, "renderScene: a picture can't be read", e)
        }
        val written = writeDebugPng(bitmap, fileName)
        logger.debug(TAG, "renderScene: ${written ?: "writing failed"} (${output.widthPx} x ${output.heightPx})")
    }

    companion object {
        const val TAG = "MemixSceneRender"
    }
}

/** Writes [image] as a PNG under the app's `cache/scene-renders/` and returns its path, or null when it can't. */
internal expect suspend fun writeDebugPng(image: ImageBitmap, fileName: String): String?
