package app.memix.engine.video

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.Project
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind

/** What [CompositionPlanner.plan] returns. */
internal sealed interface PlanResult {
    data class Ready(val plan: CompositionPlan) : PlanResult

    data class Unplayable(val problem: PlanProblem) : PlanResult
}

/** Why a project can't be rendered. The editor's own edits never lead here; a damaged project can. */
internal sealed interface PlanProblem {
    /** The main video track has no clip with a positive length. */
    data object EmptyMainTrack : PlanProblem

    /** A main video clip starts before time 0 or before the previous clip ends. */
    data class MainClipOutOfPlace(val itemId: String) : PlanProblem
}

/**
 * Turns a [Project] into a [CompositionPlan]. The main video track becomes one sequence, with a gap
 * for any empty time between clips. Each unmuted meme sound and audio track becomes one or more audio
 * lanes, cut to the main track's length. Captions are drawn over the video in lane order, a lower text lane over a
 * higher one, also cut to that length (P1-10). Every item is placed by its start time, never by its index
 * in the track (CLAUDE.md rule 4).
 *
 * Items at 0% are left out of an export's plan (they'd only add silence). A preview's plan keeps them
 * ([keepSilentItems]), so the Volume slider can raise one and be heard while it moves (P1-09).
 */
internal object CompositionPlanner {
    fun plan(project: Project, keepSilentItems: Boolean = false): PlanResult {
        val tracks = project.video?.tracks.orEmpty()
        val mainTrack = tracks.firstOrNull { it.kind == TrackKind.MAIN_VIDEO }
        val mainClips = mainTrack?.items.orEmpty()
            .filterIsInstance<MediaClip>()
            .filter { it.durationUs > 0 }
            .sortedBy { it.startUs }
        if (mainTrack == null || mainClips.isEmpty()) return PlanResult.Unplayable(PlanProblem.EmptyMainTrack)
        firstMisplacedClip(mainClips)?.let { return PlanResult.Unplayable(PlanProblem.MainClipOutOfPlace(it.id)) }

        // Sorted by start and not overlapping, so the last clip ends last.
        val durationUs = mainClips.last().endUs
        val plan = CompositionPlan(
            canvasWidthPx = project.canvas.widthPx,
            canvasHeightPx = project.canvas.heightPx,
            background = when (val background = project.canvas.background) {
                is CanvasBackground.Solid -> PlanBackground.Color(background.color.argb)
                CanvasBackground.Blur -> PlanBackground.Blur
                is CanvasBackground.Image -> PlanBackground.Image(background.source)
            },
            durationUs = durationUs,
            mainVideo = backToBack(mainClips, ::Gap) { it.toVisualClip(mainTrack.muted, keepSilentItems) },
            audioLanes = tracks.filter { it.kind.carriesSound() && !it.muted }.flatMap { audioLanes(it, durationUs, keepSilentItems) },
            captions = captions(tracks, durationUs),
            notRendered = notRendered(tracks),
        )
        return PlanResult.Ready(plan)
    }

    private fun firstMisplacedClip(sortedClips: List<MediaClip>): MediaClip? {
        var previousEndUs = 0L
        for (clip in sortedClips) {
            if (clip.startUs < previousEndUs) return clip
            previousEndUs = clip.endUs
        }
        return null
    }

    private fun MediaClip.toVisualClip(trackMuted: Boolean, keepSilent: Boolean) = VisualClip(
        itemId = id,
        source = source,
        trimInUs = trimInUs,
        trimOutUs = trimOutUs,
        fit = fit,
        pixelSize = source.pixelSize,
        playsOwnAudio = hasOwnSound() && !audioDetached && !trackMuted && (volume > 0f || keepSilent),
        volume = volume,
    )

    // A video measured without a sound track this phone can decode (P1-02) is left silent. Media added before
    // schema 2 isn't measured (null); Media3 fills a sound track it can't find with silence.
    private fun MediaClip.hasOwnSound(): Boolean = source.kind == MediaKind.VIDEO && source.hasAudio != false

    private fun audioLanes(track: Track, timelineEndUs: Long, keepSilent: Boolean): List<AudioLane> {
        val clips = track.items
            .filterIsInstance<AudioClip>()
            .filter { it.volume > 0f || keepSilent }
            .mapNotNull { it.cutTo(timelineEndUs) }
            .sortedBy { it.startUs }
        return packIntoLanes(clips).map { laneClips ->
            AudioLane(track.id, track.kind, backToBack(laneClips, ::Gap) { it.toSoundClip() })
        }
    }

    // Text lanes from the first to the last, so a lower lane draws over a higher one (spec P1-10 → Draw order); blank
    // text draws nothing, and a caption past the end of the video isn't shown.
    private fun captions(tracks: List<Track>, timelineEndUs: Long): List<CaptionClip> =
        tracks.filter { it.kind == TrackKind.TEXT }.flatMap { track ->
            track.items.filterIsInstance<TextItem>()
                .filter { it.text.isNotBlank() && it.startUs < timelineEndUs && it.durationUs > 0 }
                .sortedBy { it.startUs }
                .map { CaptionClip(it.id, it.startUs.coerceAtLeast(0), minOf(it.endUs, timelineEndUs), it.text, it.style, it.transform) }
        }

    /** The part of this clip that lies between 0 and [timelineEndUs], or null when none of it does. */
    private fun AudioClip.cutTo(timelineEndUs: Long): AudioClip? {
        val headCutUs = (-startUs).coerceAtLeast(0)
        val cutStartUs = startUs + headCutUs
        val cutTrimInUs = trimInUs + headCutUs
        val cutTrimOutUs = minOf(trimOutUs, cutTrimInUs + (timelineEndUs - cutStartUs))
        if (cutTrimOutUs <= cutTrimInUs) return null
        return copy(startUs = cutStartUs, trimInUs = cutTrimInUs, trimOutUs = cutTrimOutUs)
    }

    // Clips on one track normally follow each other. When two overlap, the later one goes to the first
    // lane that is free at its start, so both are heard instead of one cutting the other off.
    private fun packIntoLanes(sortedClips: List<AudioClip>): List<List<AudioClip>> {
        val lanes = mutableListOf<MutableList<AudioClip>>()
        for (clip in sortedClips) {
            val freeLane = lanes.firstOrNull { it.last().endUs <= clip.startUs }
            if (freeLane != null) freeLane.add(clip) else lanes.add(mutableListOf(clip))
        }
        return lanes
    }

    private fun AudioClip.toSoundClip() = SoundClip(id, source, trimInUs, trimOutUs, volume)

    /** [sortedItems] (not overlapping) as back-to-back segments from time 0, with a gap for each empty stretch. */
    private fun <T : TimelineItem, S> backToBack(sortedItems: List<T>, gap: (Long) -> S, segment: (T) -> S): List<S> =
        buildList {
            var cursorUs = 0L
            for (item in sortedItems) {
                if (item.startUs > cursorUs) add(gap(item.startUs - cursorUs))
                add(segment(item))
                cursorUs = item.endUs
            }
        }

    private fun notRendered(tracks: List<Track>): List<String> = tracks.mapNotNull { track ->
        val ticket = ticketThatAddsRendering(track.kind)
        if (ticket == null || track.items.isEmpty()) return@mapNotNull null
        "Not rendered yet: ${track.kind.name.lowercase()} track ${track.id} (${itemCount(track)}), arrives with $ticket"
    }

    /** The ticket that renders a track kind this engine skips today, or null when it renders it. */
    private fun ticketThatAddsRendering(kind: TrackKind): String? = when (kind) {
        TrackKind.MAIN_VIDEO, TrackKind.MEME_SOUND, TrackKind.AUDIO, TrackKind.TEXT -> null
        TrackKind.OVERLAY -> "P4-01"
        TrackKind.STICKER -> "P4-12"
        TrackKind.EFFECT -> "P4-06"
    }

    private fun itemCount(track: Track): String = if (track.items.size == 1) "1 item" else "${track.items.size} items"

    private fun TrackKind.carriesSound(): Boolean = this == TrackKind.MEME_SOUND || this == TrackKind.AUDIO

    private val TimelineItem.endUs: Long get() = startUs + durationUs
}
