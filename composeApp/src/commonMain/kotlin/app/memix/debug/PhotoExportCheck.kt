package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.photo.PhotoDestination
import app.memix.core.domain.photo.PhotoExportSettings
import app.memix.core.domain.photo.PhotoRenderOutcome
import app.memix.core.domain.photo.PhotoRenderRequest
import app.memix.core.domain.photo.PhotoRenderer
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.model.project.PhotoExportCatalog
import app.memix.core.model.project.PhotoExportFormat
import kotlin.time.TimeSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The debug photo-export hook (spec docs/ux/specs/P2-08-photo-export.md → Requests 9; builds with hand checks): exports a saved
 * photo project through the same [PhotoRenderer] the sheet uses, so QA can run the measurements without tapping:
 * `--es memix.exportPhoto <project id> --es memix.exportPhotoFormat jpg|png --ei memix.exportPhotoQuality 90
 * --ei memix.exportShortSide 1080|2160 --es memix.exportPhotoTo cache|gallery|clipboard --ez memix.exportWatermarkFree true`.
 * `cache` (the default) writes to `cache/photo-exports/` for the steps to pull; logcat tag [TAG].
 */
class PhotoExportCheck : KoinComponent {
    private val renderer: PhotoRenderer by inject()
    private val getProject: GetProjectUseCase by inject()
    private val logger: Logger by inject()

    /** [format] is "png" or anything else for JPG; [quality] and [shortSidePx] 0 mean the sheet's defaults; [to] is "gallery", "clipboard" or anything else for the cache. */
    suspend fun run(projectId: String, format: String?, quality: Int, shortSidePx: Int, to: String?, watermark: Boolean) {
        val exportFormat = if (format == "png") PhotoExportFormat.PNG else PhotoExportFormat.JPG
        val exportQuality = quality.takeIf { it > 0 } ?: PhotoExportCatalog.DEFAULT_QUALITY
        val exportSide = shortSidePx.takeIf { it > 0 } ?: PhotoExportCatalog.DEFAULT_SHORT_SIDE_PX
        val destination = when (to) {
            "gallery" -> PhotoDestination.GALLERY
            "clipboard" -> PhotoDestination.CLIPBOARD_FILE
            else -> PhotoDestination.APP_CACHE
        }
        val project = (getProject(projectId) as? Outcome.Success)?.value
        if (project?.photo == null) return logger.error(TAG, "exportPhoto: '$projectId' isn't a saved photo project")
        val settings = PhotoExportSettings(exportFormat, exportQuality, exportSide)
        logger.debug(TAG, "exportPhoto: ${exportFormat.label} q$exportQuality, short side $exportSide px, to ${destination.name.lowercase()}, watermark ${if (watermark) "on" else "off"}")
        val started = TimeSource.Monotonic.markNow()
        when (val outcome = renderer.render(project, PhotoRenderRequest(settings, destination, watermark))) {
            is PhotoRenderOutcome.Done -> {
                val photo = outcome.photo
                logger.debug(
                    TAG,
                    "exportPhoto: done in ${started.elapsedNow().inWholeMilliseconds} ms. path=${photo.location} " +
                        "size=${photo.sizeBytes} bytes ${photo.pixelSize.widthPx} x ${photo.pixelSize.heightPx}",
                )
            }
            is PhotoRenderOutcome.Failed -> logger.error(TAG, "exportPhoto: failed with ${outcome.error}")
        }
    }

    companion object {
        const val TAG = "MemixPhotoExport"
    }
}
