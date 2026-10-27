package app.memix.core.domain.project

import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.CanvasRatio
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.VideoTimeline
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Starts an empty video project in memory. It isn't saved until something lands in it, so backing out
 * right away leaves no empty draft behind.
 */
class CreateVideoProjectUseCase(private val clock: Clock) {
    /**
     * The name stays empty until the user gives one: screens show a translated stand-in instead, so an
     * unnamed draft follows the user's language (P1-02 spec, P1-14).
     */
    operator fun invoke(): Project {
        val now = clock.nowEpochUs()
        return Project(
            id = Uuid.random().toString(),
            type = ProjectType.VIDEO,
            name = "",
            canvas = DEFAULT_CANVAS,
            video = VideoTimeline(
                tracks = listOf(Track(id = Uuid.random().toString(), kind = TrackKind.MAIN_VIDEO, items = emptyList())),
            ),
            createdAtEpochUs = now,
            updatedAtEpochUs = now,
        )
    }

    private companion object {
        // 9:16 at 1080p, the shape of TikTok, Reels and Shorts, on a black stage until the user picks otherwise.
        val DEFAULT_CANVAS = Canvas(
            ratio = CanvasRatio.RATIO_9_16,
            widthPx = 1080,
            heightPx = 1920,
            background = CanvasBackground.Solid(ArgbColor(0xFF000000)),
        )
    }
}
