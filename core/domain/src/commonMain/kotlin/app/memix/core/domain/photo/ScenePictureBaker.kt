package app.memix.core.domain.photo

import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project

/** One scene drawn into a JPEG: [jpegBytes] hold a picture of exactly [size] pixels. */
class BakedPicture(val jpegBytes: ByteArray, val size: PixelSize)

/**
 * Draws a photo project's scene into one picture for "Make it a video meme" (spec docs/ux/specs/P2-10-make-it-a-video-meme.md →
 * What happens to the scene). It is the same scene drawing the export uses ([PhotoRenderer]), minus the "Memix" mark, which belongs
 * to the video export.
 */
interface ScenePictureBaker {
    /**
     * [project]'s scene at [output] pixels, with no mark. Runs off the main thread and cancels at once. Null when the phone can't make
     * it (out of memory, a picture that exists but can't be read); a picture whose file is missing is left out, as in export.
     */
    suspend fun bake(project: Project, output: PixelSize): BakedPicture?
}
