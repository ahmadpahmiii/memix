package app.memix.core.domain.project

import app.memix.core.domain.video.ExportSettings
import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.EffectItem
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.Project
import app.memix.core.model.project.StickerItem
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind

/*
 * Meme sound edits (spec docs/ux/specs/P1-06-clip-edits-and-audio.md → Meme sounds, Volume panel). Like every edit in
 * TimelineEdits.kt, each returns a new Project, or this one unchanged when it can't apply. Sounds keep their own
 * times: nothing here moves the main video track, and main-track edits never move sounds (owner, 8 Oct 2026).
 */

/** The loudest a sound or clip can be set: 200% of its own level (spec P1-06 → Volume panel). */
const val MAX_VOLUME = 2f

/** A playhead this close to the end of the video counts as at the end: one frame at the export's frame rate. */
internal const val END_TOLERANCE_US = 1_000_000L / ExportSettings.DEFAULT_FRAME_RATE

/**
 * Where an item [lengthUs] long goes when it's placed at the playhead [atUs]: there, or, with the playhead at the end
 * of a video [videoLengthUs] long (within a frame), early enough to end at the end, never before 0:00. A sound dropped
 * at the very end would otherwise be silent (spec P1-06 → Meme sounds → Landing; Move here).
 */
fun startAtPlayhead(atUs: Long, lengthUs: Long, videoLengthUs: Long): Long =
    if (atUs >= videoLengthUs - END_TOLERANCE_US) (videoLengthUs - lengthUs).coerceAtLeast(0) else atUs.coerceAtLeast(0)

/**
 * Lands [sound] at the playhead [atUs] (see [startAtPlayhead]) as a new clip [clipId]: the whole file at 100%, on the
 * first meme-sound lane that's free for its whole length, else on a new lane [newTrackId] under the last one. Needs
 * the sound's measured length; without one, nothing changes.
 */
fun Project.addSound(sound: MediaRef, clipId: String, atUs: Long, newTrackId: String): Project {
    val lengthUs = sound.durationUs?.takeIf { it > 0 } ?: return this
    val startUs = startAtPlayhead(atUs, lengthUs, videoLengthUs())
    val clip = AudioClip(id = clipId, startUs = startUs, source = sound, trimInUs = 0, trimOutUs = lengthUs)
    return withItemOnFreeLane(clip, TrackKind.MEME_SOUND, tracks().filter { it.kind == TrackKind.MEME_SOUND }, newTrackId)
}

/** Sets a sound's or a clip's own volume, from 0 (silent) to [MAX_VOLUME]; 1 is the level it was recorded at. */
fun Project.setVolume(itemId: String, volume: Float): Project {
    val (track, item) = findItem(itemId) ?: return this
    val level = volume.coerceIn(0f, MAX_VOLUME)
    val changed = when (item) {
        is AudioClip -> item.copy(volume = level)
        is MediaClip -> item.copy(volume = level)
        is TextItem, is StickerItem, is EffectItem -> return this
    }
    return replaceTrack(track.copy(items = track.items.map { if (it.id == itemId) changed else it }))
}

/** The volume of a sound or clip, or null for items that have none (captions, stickers, effects). */
fun Project.volumeOf(itemId: String): Float? = when (val item = findItem(itemId)?.item) {
    is AudioClip -> item.volume
    is MediaClip -> item.volume
    is TextItem, is StickerItem, is EffectItem, null -> null
}

/**
 * [item] on the first of [candidates] (lanes of [kind], in the order to try) that's free for its whole length, else on
 * a new lane [newTrackId] right after the last lane of [kind] (or at the end, when there's none). [item] must not be on
 * any lane yet.
 */
internal fun Project.withItemOnFreeLane(item: TimelineItem, kind: TrackKind, candidates: List<Track>, newTrackId: String): Project {
    val endUs = item.startUs + item.durationUs
    val free = candidates.firstOrNull { it.isFree(item.startUs, endUs) }
    if (free != null) return replaceTrack(free.copy(items = free.items + item))
    val all = tracks()
    val insertAt = all.indexOfLast { it.kind == kind }.let { if (it < 0) all.size else it + 1 }
    return withTracks(all.toMutableList().apply { add(insertAt, Track(newTrackId, kind, listOf(item))) })
}
