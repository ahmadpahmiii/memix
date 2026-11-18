package app.memix.engine.video

import app.memix.core.model.project.AudioClip
import app.memix.core.model.project.CanvasBackground
import app.memix.core.model.project.Keyframe
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.look
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.SpeedCatalog
import app.memix.core.model.project.SpeedSound
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TimelineItem
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.peakVolume

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
 *
 * Each overlay track becomes one overlay layer (P4-01), in track order, bottom to top, cut to the main track's length and padded
 * with gaps to it. Overlay videos that the phone can't decode at the same time as the rest are left out by [OverlayAdmission],
 * against [videoDecoderBudget] decoders for the main clip and the overlay videos together.
 */
internal object CompositionPlanner {
    fun plan(
        project: Project,
        keepSilentItems: Boolean = false,
        videoDecoderBudget: Int = OverlayAdmission.DEFAULT_VIDEO_DECODER_BUDGET,
    ): PlanResult {
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
        val visuals = mainClips.associateWith { it.toVisualClip(mainTrack.muted, keepSilentItems) }
        val transitions = TransitionPlanner.plan(mainClips, { visuals.getValue(it) }, durationUs)
        // A transition's partner sequence holds one more video decoder while a window plays.
        val decoderBudget = if (transitions.layer != null) videoDecoderBudget - 1 else videoDecoderBudget
        val overlays = overlayLayers(tracks, mainClips, durationUs, decoderBudget, keepSilentItems)
        val plan = CompositionPlan(
            canvasWidthPx = project.canvas.widthPx,
            canvasHeightPx = project.canvas.heightPx,
            background = when (val background = project.canvas.background) {
                is CanvasBackground.Solid -> PlanBackground.Color(background.color.argb)
                CanvasBackground.Blur -> PlanBackground.Blur
                is CanvasBackground.Image -> PlanBackground.Image(background.source)
            },
            durationUs = durationUs,
            mainVideo = backToBack(mainClips, ::Gap) { clip -> visuals.getValue(clip).copy(transitions = transitions.windowsByClipId[clip.id].orEmpty()) },
            transitionLayer = transitions.layer,
            overlays = overlays.layers,
            audioLanes = tracks.filter { it.kind.carriesSound() && !it.muted }.flatMap { audioLanes(it, durationUs, keepSilentItems) },
            captions = captions(tracks, durationUs),
            effects = effectSpansOf(project),
            skippedOverlayIds = overlays.skippedIds,
            notRendered = notRendered(tracks) + unbuiltEffectLines(project) + overlays.skippedLines,
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
        playsOwnAudio = playsOwnSound(trackMuted, keepSilent),
        volume = plannedVolume(),
        keyframes = keyframes,
        chromaKey = chromaKey,
        mask = mask,
        look = look,
        speed = SpeedCatalog.speedOf(this),
    )

    // The clip's own sound plays when it has one, isn't detached, isn't muted (the track, or Mute in Speed) and isn't at 0% (a preview keeps silent items so a raise is heard live).
    private fun MediaClip.playsOwnSound(trackMuted: Boolean, keepSilent: Boolean): Boolean =
        hasOwnSound() && !audioDetached && !trackMuted && soundMode == SpeedSound.KEEP && (peakVolume() > 0f || keepSilent)

    // With keyframes they carry the level (P4-02), so the fixed volume stays at 1 and never switches the clip off or on by itself.
    private fun MediaClip.plannedVolume(): Float = if (keyframes.isEmpty()) volume else 1f

    // A video measured without a sound track this phone can decode (P1-02) is left silent. Media added before
    // schema 2 isn't measured (null); Media3 fills a sound track it can't find with silence.
    private fun MediaClip.hasOwnSound(): Boolean = source.kind == MediaKind.VIDEO && source.hasAudio != false

    private fun audioLanes(track: Track, timelineEndUs: Long, keepSilent: Boolean): List<AudioLane> {
        val clips = track.items
            .filterIsInstance<AudioClip>()
            .filter { it.peakVolume() > 0f || keepSilent }
            .mapNotNull { it.cutTo(timelineEndUs) }
            .sortedBy { it.clip.startUs }
        return packIntoLanes(clips).map { laneClips ->
            AudioLane(track.id, track.kind, soundSegments(laneClips))
        }
    }

    // The lane's sounds back to back from time 0, with a gap for each empty stretch (like [backToBack]).
    private fun soundSegments(clips: List<CutClip>): List<AudioSegment> = buildList {
        var cursorUs = 0L
        for (cut in clips) {
            if (cut.clip.startUs > cursorUs) add(Gap(cut.clip.startUs - cursorUs))
            add(cut.toSoundClip())
            cursorUs = cut.busyUntilUs
        }
    }

    /** What [overlayLayers] found: the layers to draw, and the overlay clips left out with a log line each. */
    private class OverlayLayers(val layers: List<OverlayLayer>, val skippedIds: List<String>, val skippedLines: List<String>)

    private fun overlayLayers(tracks: List<Track>, mainClips: List<MediaClip>, durationUs: Long, videoDecoderBudget: Int, keepSilent: Boolean): OverlayLayers {
        val skipped = mutableListOf<Pair<String, String>>() // item id to its log line
        // The first overlay track is the lowest layer.
        val overlayTracks = tracks.filter { it.kind == TrackKind.OVERLAY }
        val cutByLayer = overlayTracks.map { track ->
            cutOverlays(track, durationUs, keepSilent) { clip, reason -> skipped += clip.id to "Not rendered: overlay ${clip.id} ($reason)" }
        }
        val admitted = admittedVideos(cutByLayer, mainClips, videoDecoderBudget)
        val layers = overlayTracks.zip(cutByLayer).mapNotNull { (track, cuts) ->
            val (kept, left) = cuts.partition { it.clip.isStill || it.clip.itemId in admitted }
            left.forEach { skipped += it.clip.itemId to overBudgetLine(it.clip.itemId, videoDecoderBudget) }
            if (kept.isEmpty()) null else OverlayLayer(track.id, overlaySegments(kept, durationUs))
        }
        return OverlayLayers(layers, skipped.map { it.first }, skipped.map { it.second })
    }

    private fun admittedVideos(cutByLayer: List<List<CutOverlay>>, mainClips: List<MediaClip>, videoDecoderBudget: Int): Set<String> {
        val candidates = cutByLayer.flatMapIndexed { layer, cuts ->
            cuts.filterNot { it.clip.isStill }.map { OverlayAdmission.Candidate(it.clip.itemId, layer, it.startUs, it.startUs + it.clip.durationUs) }
        }
        val mainVideoSpans = mainClips.filter { it.source.kind == MediaKind.VIDEO }.map { it.startUs until it.endUs }
        return OverlayAdmission.admit(candidates, mainVideoSpans, videoDecoderBudget)
    }

    private fun overBudgetLine(itemId: String, budget: Int) =
        "Not rendered: overlay $itemId (more videos at once than this phone decodes: main video and overlay videos together, at most $budget)"

    /** An overlay clip cut to the video, with where it starts on the timeline. */
    private class CutOverlay(val clip: OverlayClip, val startUs: Long)

    // The clips of one overlay track that can be drawn: cut to 0..durationUs, in time order, none overlapping the one before.
    private fun cutOverlays(track: Track, durationUs: Long, keepSilent: Boolean, skip: (MediaClip, String) -> Unit): List<CutOverlay> {
        val cuts = mutableListOf<CutOverlay>()
        var cursorUs = 0L
        for (clip in track.items.filterIsInstance<MediaClip>().sortedBy { it.startUs }) {
            if (clip.durationUs <= 0 || clip.startUs >= durationUs) continue
            val pixelSize = clip.source.pixelSize?.takeIf { it.widthPx > 0 && it.heightPx > 0 }
            if (pixelSize == null) {
                skip(clip, "its picture size isn't known")
                continue
            }
            val cut = clip.cutTo(durationUs, pixelSize, plays = clip.playsOwnSound(track.muted, keepSilent)) ?: continue
            if (cut.startUs < cursorUs) {
                skip(clip, "it overlaps the clip before it on its layer")
                continue
            }
            cuts += cut
            cursorUs = cut.startUs + cut.clip.durationUs
        }
        return cuts
    }

    // The part of the clip that lies between 0 and the end of the video; a photo only has a length, so it is cut from 0 as well.
    private fun MediaClip.cutTo(timelineEndUs: Long, pixelSize: PixelSize, plays: Boolean): CutOverlay? {
        val headCutUs = (-startUs).coerceAtLeast(0)
        val cutStartUs = startUs + headCutUs
        val lengthUs = minOf(durationUs - headCutUs, timelineEndUs - cutStartUs)
        if (lengthUs <= 0) return null
        val isPhoto = source.kind == MediaKind.IMAGE
        // The cuts are timeline time; the clip's speed turns them into the source span that plays (a photo has none).
        val speed = SpeedCatalog.speedOf(this)
        val cutTrimInUs = if (isPhoto) 0L else trimInUs + SpeedCatalog.sourceSpanOf(headCutUs, speed)
        val cutTrimOutUs = if (isPhoto) lengthUs else minOf(trimOutUs, cutTrimInUs + SpeedCatalog.sourceSpanOf(lengthUs, speed))
        val clip = OverlayClip(
            id, source, cutTrimInUs, cutTrimOutUs, pixelSize, transform, opacity,
            playsOwnAudio = plays,
            volume = plannedVolume(),
            keyframes = keyframes.shiftedBy(headCutUs),
            chromaKey = chromaKey,
            mask = mask,
            look = look,
            speed = speed,
        )
        return CutOverlay(clip, cutStartUs)
    }

    // The layer's clips back to back from time 0, a gap for each empty stretch and one after the last up to the end of the video.
    private fun overlaySegments(clips: List<CutOverlay>, durationUs: Long): List<OverlaySegment> = buildList {
        var cursorUs = 0L
        for (cut in clips) {
            if (cut.startUs > cursorUs) add(Gap(cut.startUs - cursorUs))
            add(cut.clip)
            cursorUs = cut.startUs + cut.clip.durationUs
        }
        if (cursorUs < durationUs) add(Gap(durationUs - cursorUs))
    }

    // Text lanes from the first to the last, so a lower lane draws over a higher one (spec P1-10 → Draw order); blank
    // text draws nothing, and a caption past the end of the video isn't shown.
    private fun captions(tracks: List<Track>, timelineEndUs: Long): List<CaptionClip> =
        tracks.filter { it.kind == TrackKind.TEXT }.flatMap { track ->
            track.items.filterIsInstance<TextItem>()
                .filter { it.text.isNotBlank() && it.startUs < timelineEndUs && it.durationUs > 0 }
                .sortedBy { it.startUs }
                .map {
                    CaptionClip(it.id, it.startUs.coerceAtLeast(0), minOf(it.endUs, timelineEndUs), it.text, it.style, it.transform, it.keyframes.shiftedBy((-it.startUs).coerceAtLeast(0)))
                }
        }

    /**
     * A clip cut to the video, and how long it was before the cut ([fullLengthUs]: where its Fade out counts back from). [tailUs] is
     * how long its echo rings after it: up to the effects' tail, and never past the end of the video (spec P3-09 → Echo), so a clip
     * the video cuts has none.
     */
    private class CutClip(val clip: AudioClip, val fullLengthUs: Long, val tailUs: Long) {
        /** Where its lane is free again: a sound that starts before this goes on another lane, so the tail rings under it. */
        val busyUntilUs: Long get() = clip.endUs + tailUs

        /** This clip with its tail cut so it is over by [limitUs]. */
        fun withTailEndingBy(limitUs: Long) = CutClip(clip, fullLengthUs, minOf(tailUs, (limitUs - clip.endUs).coerceAtLeast(0)))

        fun toSoundClip() = SoundClip(
            clip.id, clip.source, clip.trimInUs, clip.trimOutUs,
            // With keyframes they carry the level (P4-02), so the fixed volume stays at 1.
            volume = if (clip.keyframes.isEmpty()) clip.volume else 1f,
            effects = clip.effects,
            clipLengthUs = fullLengthUs,
            tailUs = tailUs,
            keyframes = clip.keyframes,
        )
    }

    /**
     * The part of this clip that lies between 0 and [timelineEndUs], or null when none of it does. The cuts are timeline time;
     * the clip's speed turns them into source time. A clip that starts before 0 (the editor never makes one) loses the start of its Fade in
     * with the cut head: the fade is not shifted, so the sound would begin a little louder than the timeline shows.
     */
    private fun AudioClip.cutTo(timelineEndUs: Long): CutClip? {
        val headCutUs = (-startUs).coerceAtLeast(0)
        val cutStartUs = startUs + headCutUs
        val cutTrimInUs = trimInUs + effects.sourceSpanOf(headCutUs)
        val cutTrimOutUs = minOf(trimOutUs, cutTrimInUs + effects.sourceSpanOf(timelineEndUs - cutStartUs))
        if (cutTrimOutUs <= cutTrimInUs) return null
        val cut = copy(startUs = cutStartUs, trimInUs = cutTrimInUs, trimOutUs = cutTrimOutUs, keyframes = keyframes.shiftedBy(headCutUs))
        val tailUs = minOf(effects.tailUs, timelineEndUs - cut.endUs).coerceAtLeast(0)
        return CutClip(cut, fullLengthUs = durationUs, tailUs = tailUs)
    }

    // Clips on one track normally follow each other. When two overlap, the later one goes to the first
    // lane that is free at its start, so both are heard instead of one cutting the other off. An echo's tail keeps its lane busy too,
    // but a tail is not drawn and never worth many lanes (each lane is one more audio sequence to decode and mix): once tails have
    // opened [MAX_TAIL_LANES] more lanes than the clips alone need, the earlier sound's tail is cut at the next sound's start instead.
    private fun packIntoLanes(sortedClips: List<CutClip>): List<List<CutClip>> {
        val lanesWithoutTails = laneCountIgnoringTails(sortedClips)
        val lanes = mutableListOf<MutableList<CutClip>>()
        for (cut in sortedClips) {
            val freeLane = lanes.firstOrNull { it.last().busyUntilUs <= cut.clip.startUs }
            if (freeLane != null) {
                freeLane.add(cut)
                continue
            }
            val tailBlockedLane = lanes.firstOrNull { it.last().clip.endUs <= cut.clip.startUs }
            if (tailBlockedLane != null && lanes.size >= lanesWithoutTails + MAX_TAIL_LANES) {
                tailBlockedLane.add(tailBlockedLane.removeAt(tailBlockedLane.lastIndex).withTailEndingBy(cut.clip.startUs))
                tailBlockedLane.add(cut)
            } else {
                lanes.add(mutableListOf(cut))
            }
        }
        return lanes
    }

    private fun laneCountIgnoringTails(sortedClips: List<CutClip>): Int {
        val laneEnds = mutableListOf<Long>()
        for (cut in sortedClips) {
            val free = laneEnds.indexOfFirst { it <= cut.clip.startUs }
            if (free >= 0) laneEnds[free] = cut.clip.endUs else laneEnds.add(cut.clip.endUs)
        }
        return laneEnds.size
    }

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

    // Keyframes count from where the clip now starts: a cut head (an item that begins before 0, which the editor never makes) moves them back.
    private fun List<Keyframe>.shiftedBy(headCutUs: Long): List<Keyframe> =
        if (headCutUs == 0L || isEmpty()) this else map { it.copy(timeUs = it.timeUs - headCutUs) }

    private fun notRendered(tracks: List<Track>): List<String> = mainMotionNotRendered(tracks) + tracks.mapNotNull { track ->
        val ticket = ticketThatAddsRendering(track.kind)
        if (ticket == null || track.items.isEmpty()) return@mapNotNull null
        "Not rendered yet: ${track.kind.name.lowercase()} track ${track.id} (${itemCount(track)}), arrives with $ticket"
    }

    // A main clip's framing (position, size, turn) is drawn from P4-16 on; its keyframed volume already plays.
    private fun mainMotionNotRendered(tracks: List<Track>): List<String> =
        tracks.filter { it.kind == TrackKind.MAIN_VIDEO }.flatMap { it.items }.filterIsInstance<MediaClip>().filter { it.keyframes.isNotEmpty() }
            .map { "Not rendered yet: keyframed position, size and turn of main clip ${it.id}, arrives with P4-16 (its keyframed volume plays)" }

    /** The ticket that renders a track kind this engine skips today, or null when it renders it. */
    private fun ticketThatAddsRendering(kind: TrackKind): String? = when (kind) {
        TrackKind.MAIN_VIDEO, TrackKind.OVERLAY, TrackKind.MEME_SOUND, TrackKind.AUDIO, TrackKind.TEXT, TrackKind.EFFECT -> null
        TrackKind.STICKER -> "P4-12"
    }

    private fun unbuiltEffectLines(project: Project): List<String> =
        unbuiltEffectIdsOf(project).map { "Not rendered yet: effect $it, arrives with P4-06b" }

    private fun itemCount(track: Track): String = if (track.items.size == 1) "1 item" else "${track.items.size} items"

    private fun TrackKind.carriesSound(): Boolean = this == TrackKind.MEME_SOUND || this == TrackKind.AUDIO

    private val TimelineItem.endUs: Long get() = startUs + durationUs

    /** Tails may open this many more lanes than the clips alone need (each lane is one more audio sequence in the composition). */
    private const val MAX_TAIL_LANES = 2
}
