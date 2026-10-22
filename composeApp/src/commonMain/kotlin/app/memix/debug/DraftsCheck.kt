package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.drafts.UpdateDraftThumbnailUseCase
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.LeftoverMediaCleaner
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.media.MediaPaths
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.VideoTimeline
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The P1-14 hand check (debug and benchmark builds): `--ez memix.draftsCheck true` saves [COUNT] drafts with spread-out
 * edit times, straight through the repository so the times stay as given: one unnamed, one with a 60-character name,
 * one saved by a "newer Memix" (schema version above this app's), one whose media folder is missing; the rest each get
 * their own copy of the pushed `debug-media/clip-a.mp4` and a thumbnail. Running it again replaces them. Logcat tag
 * [TAG]; steps in docs/qa/phase-1/engineer-hand-checks.md → P1-14. Restart the app afterwards to time the launch
 * cleanup with 25 drafts ("Cleanup checked 25 drafts in N ms", tag MediaImport).
 */
class DraftsCheck : KoinComponent {
    private val repository: ProjectRepository by inject()
    private val mediaFiles: MediaFiles by inject()
    private val leftoverCleaner: LeftoverMediaCleaner by inject()
    private val updateThumbnail: UpdateDraftThumbnailUseCase by inject()
    private val clock: Clock by inject()
    private val logger: Logger by inject()

    suspend fun run() {
        // Copies made before the launch cleanup ends would look like an unsaved project's.
        leftoverCleaner.awaitDone()
        val nowUs = clock.now().toEpochMilliseconds() * MICROS_PER_MILLI
        var saved = 0
        for (index in 0 until COUNT) {
            val project = draft(index, nowUs)
            if (index != MISSING_MEDIA_INDEX && !copyClip(project.id)) {
                logger.error(TAG, "Couldn't copy ${ExportCheckProject.MEDIA_DIR}/$CLIP_FILE: push it first (engineer-hand-checks.md → P1-03)")
            }
            if (repository.save(project) is Outcome.Success) saved++
            if (index != MISSING_MEDIA_INDEX) updateThumbnail(project)
        }
        logger.debug(TAG, "Saved $saved of $COUNT drafts. Open the Drafts tab; restart the app to time the launch cleanup.")
    }

    private suspend fun copyClip(projectId: String): Boolean =
        mediaFiles.copyFile("${ExportCheckProject.MEDIA_DIR}/$CLIP_FILE", clipPath(projectId)) { } == CopyResult.COPIED

    private fun draft(index: Int, nowUs: Long): Project {
        val id = "drafts-check-${(index + 1).toString().padStart(2, '0')}"
        val editedAtUs = nowUs - EDITED_AGO_SECONDS[index] * MICROS_PER_SECOND
        val clip = MediaClip(
            id = "$id-clip",
            startUs = 0,
            source = MediaRef(MediaOrigin.GalleryUri("content://app.memix.debug/$CLIP_FILE"), MediaKind.VIDEO, cachedCopyPath = clipPath(id)),
            trimInUs = 0,
            trimOutUs = CLIP_LENGTH_US,
        )
        return Project(
            id = id,
            type = ProjectType.VIDEO,
            name = nameFor(index),
            canvas = Canvas(CanvasRatio.RATIO_9_16, widthPx = 1080, heightPx = 1920, background = CanvasBackground.Solid(ArgbColor(0xFF000000))),
            video = VideoTimeline(listOf(Track("$id-main", TrackKind.MAIN_VIDEO, listOf(clip)))),
            createdAtEpochUs = editedAtUs - CREATED_BEFORE_EDIT_US,
            updatedAtEpochUs = editedAtUs,
            schemaVersion = if (index == NEWER_APP_INDEX) Project.CURRENT_SCHEMA_VERSION + 1 else Project.CURRENT_SCHEMA_VERSION,
        )
    }

    private fun nameFor(index: Int): String = when (index) {
        UNNAMED_INDEX -> ""
        LONG_NAME_INDEX -> LONG_NAME
        NEWER_APP_INDEX -> "From a newer Memix"
        MISSING_MEDIA_INDEX -> "Media folder missing"
        else -> "Check draft ${index + 1}"
    }

    private fun clipPath(projectId: String) = "${MediaPaths.projectFolder(projectId)}/$CLIP_FILE"

    companion object {
        const val TAG = "MemixDraftsCheck"
        private const val COUNT = 25
        private const val CLIP_FILE = "clip-a.mp4"
        private const val CLIP_LENGTH_US = 3_000_000L
        private const val MICROS_PER_MILLI = 1_000L
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val CREATED_BEFORE_EDIT_US = 600_000_000L
        private const val UNNAMED_INDEX = 2
        private const val LONG_NAME_INDEX = 3
        private const val NEWER_APP_INDEX = 4
        private const val MISSING_MEDIA_INDEX = 5

        // 60 user-perceived characters, Devanagari and an emoji included, to check the two-line ellipsis and the counter.
        private const val LONG_NAME = "जब build pass हो गया 😂 and the whole team cheered for an hour!"

        // How long ago each draft was edited, newest first: every edit-time wording appears (now, minutes, hours,
        // yesterday, days, a date this year, a date with the year).
        private val EDITED_AGO_SECONDS = longArrayOf(
            10, 90, 300, 3_000, 7_200, 30_000, 82_000, 100_000, 180_000, 260_000,
            430_000, 520_000, 700_000, 1_300_000, 2_600_000, 4_000_000, 6_000_000, 8_000_000, 10_000_000, 12_000_000,
            15_000_000, 20_000_000, 25_000_000, 33_000_000, 40_000_000,
        )
    }
}
