package app.memix.core.domain.project

import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.TrackKind

/**
 * True for a video project whose main video track has no clip left, for example a new project undone back to
 * nothing. Such a draft can't play or export, so leaving the editor deletes it (owner, 8 Oct 2026). Photo
 * projects are never treated as empty here; the photo editor's tickets decide that for themselves.
 */
fun Project.hasNoClips(): Boolean = type == ProjectType.VIDEO && mainVideoClips().isEmpty()

/** How long a video project plays: where its last main video clip ends, in µs. 0 when it has no clip. */
fun Project.videoLengthUs(): Long = mainVideoClips().maxOfOrNull { it.startUs + it.durationUs } ?: 0L

private fun Project.mainVideoClips(): List<MediaClip> =
    video?.tracks.orEmpty()
        .filter { it.kind == TrackKind.MAIN_VIDEO }
        .flatMap { it.items }
        .filterIsInstance<MediaClip>()
        .filter { it.durationUs > 0 }
