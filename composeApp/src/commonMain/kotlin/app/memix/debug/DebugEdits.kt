package app.memix.debug

import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.Project
import app.memix.core.model.project.TrackKind
import app.memix.feature.videoeditor.EditorDebugEdit

/**
 * Test edits for builds with hand checks (debug, benchmark), until P1-06 brings the real ones. A long-press on the
 * editor's timecode runs [shortenLastClip] (TECHNICAL_DESIGN → Debug hand checks).
 */
internal object DebugEdits {
    private const val STEP_US = 1_000_000L

    /**
     * Takes 1 s off the end of the last main video clip, or removes the clip when it's 1 s or shorter. Pressed
     * often enough it empties the project, which checks that leaving then deletes the draft.
     */
    val shortenLastClip = EditorDebugEdit { project -> project.withLastClipShortened() }

    private fun Project.withLastClipShortened(): Project {
        val timeline = video ?: return this
        val mainTrack = timeline.tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO } ?: return this
        val last = mainTrack.items.filterIsInstance<MediaClip>().maxByOrNull { it.startUs } ?: return this
        val items = if (last.durationUs <= STEP_US) {
            mainTrack.items - last
        } else {
            mainTrack.items.map { if (it.id == last.id) last.copy(trimOutUs = last.trimOutUs - STEP_US) else it }
        }
        val tracks = timeline.tracks.map { if (it.id == mainTrack.id) it.copy(items = items) else it }
        return copy(video = timeline.copy(tracks = tracks))
    }
}
