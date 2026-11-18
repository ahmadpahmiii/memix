package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Anything placed on a [Track]. Times are microseconds on the project timeline (CLAUDE.md rule 4). */
@Serializable
sealed interface TimelineItem {
    val id: String
    val startUs: Long
    val durationUs: Long
}

/**
 * A video or photo from the gallery on the main video or an overlay track.
 * For a photo, [trimInUs]..[trimOutUs] is simply how long it shows.
 *
 * On an overlay track (P4-01) the clip floats over the main video: [transform] and [opacity] place and fade it, [fit] and the
 * sound fields are not used (an overlay is silent until a later ticket gives it sound). On the main track the clip always fills
 * the canvas by [fit]; [transform] is its framing, which P4-16 will draw (identity until then; the engine ignores it today).
 */
@Serializable
@SerialName("media_clip")
data class MediaClip(
    override val id: String,
    override val startUs: Long,
    val source: MediaRef,
    /** Where the clip starts inside the source file. */
    val trimInUs: Long,
    /** Where the clip ends inside the source file. */
    val trimOutUs: Long,
    /** 1 is the original loudness of the clip's own audio. */
    val volume: Float = 1f,
    /** True once the clip's audio was moved to its own clip on an audio track, so it no longer plays here. */
    val audioDetached: Boolean = false,
    val fit: FrameFit = FrameFit.FIT,
    /**
     * Where an overlay sits on the canvas (the same spec text and stickers use). Scale 1 is the picture fitted whole inside the
     * canvas ([OverlayCatalog.SCALE_RANGE]), so the shape of the picture is always kept.
     */
    val transform: Transform = Transform(),
    /** 1 draws an overlay fully, [OverlayCatalog.OPACITY_RANGE]'s start is the most see-through it gets. */
    val opacity: Float = 1f,
    /**
     * The clip's crop, quarter turns and mirrors (P4-16, the photo editor's [ImageEdit] reused). Saved from schema 14 so P4-16
     * needs no second bump, and ignored by every engine until that ticket; null is none.
     */
    val edit: ImageEdit? = null,
    /**
     * The clip's motion over time (P4-02); empty means [transform], [opacity] and [volume] hold for the whole clip. With keyframes
     * they take over and those three fields are not used (see [Keyframe]). A main clip has no opacity to key.
     */
    val keyframes: List<Keyframe> = emptyList(),
    /**
     * The clip's green screen (P4-04, schema 16): the color removed from the picture before anything else is drawn; null is off. Main
     * clips and overlays alike, video and photo. Split and Duplicate copy it, Replace keeps it.
     */
    val chromaKey: ChromaKey? = null,
    /**
     * The clip's shape mask (P4-03b, schema 17): only the part inside the shape shows (or outside, inverted); null is none. Applied after the
     * key and before framing, so it moves with the clip. Main clips and overlays alike. Split and Duplicate copy it, Replace keeps it.
     */
    val mask: ClipMask? = null,
    /**
     * How this main-track clip leads into the next one (P4-08, schema 20); null is a plain cut. It belongs to the clip before the cut, never moves
     * anything and never touches sound (see [Transition]). Only main-track clips carry one; the last clip's is always null.
     */
    val transitionOut: Transition? = null,
    /**
     * How fast the clip plays (P4-09a, schema 21): 0.1 to 10, 1 is as filmed. Video only: a photo and a freeze frame ignore it ([SpeedCatalog.speedOf]).
     * The clip's length on the timeline is its trimmed span divided by it; the trim points stay in source time.
     */
    val speed: Float = 1f,
    /**
     * The speed over the clip as 2 to 8 points (P4-09b, saved from schema 21): where in the clip's own picture, in playing order, and how fast. When set it
     * wins over [speed]; [SpeedCatalog.retimeOf] is the one reader. Edits keep the points in step with the trim (split and trim re-base them).
     */
    val speedCurve: List<SpeedPoint>? = null,
    /** Whether the clip's own sound follows its speed or is off (P4-09a). Independent of [volume], [audioDetached] and the track's mute: the sound plays only if all allow it. */
    val soundMode: SpeedSound = SpeedSound.KEEP,
    /** Plays the trimmed span from its last frame to its first, the sound too (P4-10, schema 21). Video only; trims stay in source time, so the clip's start shows [trimOutUs]. */
    val reversed: Boolean = false,
    /** Set on a clip that holds one frame of its video (P4-10): a photo clip with a marker, see [FreezeSource]. */
    val freeze: FreezeSource? = null,
) : TimelineItem {
    /** How much of the source plays, µs: the part between the trim points. */
    val sourceSpanUs: Long get() = trimOutUs - trimInUs

    /**
     * The source time shown [offsetUs] of timeline time after the clip starts: counted up from [trimInUs], or down from [trimOutUs] when [reversed]. The one
     * place that turns a place in the clip into a place in its file, so a reversed clip is handled the same everywhere (thumbnails, picks, a freeze frame).
     */
    fun sourceTimeAt(offsetUs: Long): Long {
        val span = retime.sourceOf(offsetUs)
        return if (reversed) trimOutUs - span else trimInUs + span
    }

    /** How the trimmed picture maps to the timeline (constant speed or curve), built once per clip value. Not saved. */
    val retime: Retime by lazy { SpeedCatalog.retimeOf(this) }

    // Derived, not saved, so it can never disagree with the trim points, the speed and the curve.
    override val durationUs: Long get() = retime.timelineLengthUs
}

/** How a clip whose shape differs from the canvas fills it. */
@Serializable
enum class FrameFit {
    /** The whole frame shows; the background fills the rest. */
    @SerialName("fit") FIT,

    /** The frame covers the canvas; the edges that don't fit are cropped. */
    @SerialName("fill") FILL,
}

/** A meme sound, imported audio, detached audio or voiceover. */
@Serializable
@SerialName("audio_clip")
data class AudioClip(
    override val id: String,
    override val startUs: Long,
    val source: MediaRef,
    val trimInUs: Long,
    val trimOutUs: Long,
    /** 1 is the sound's original loudness. */
    val volume: Float = 1f,
    /** The Audio effects panel's settings (P3-09); all off by default. The source file is never changed. */
    val effects: AudioEffects = AudioEffects(),
    /** The volume over time (P4-02); empty means [volume] holds. Only the volume of a keyframe's look is used. */
    val keyframes: List<Keyframe> = emptyList(),
) : TimelineItem {
    /** How much of the source plays, µs. */
    val sourceSpanUs: Long get() = trimOutUs - trimInUs

    // Derived, not saved: the part of the source that plays, at the effects' speed (P3-09: a speed change moves the end).
    override val durationUs: Long get() = effects.timelineLengthOf(sourceSpanUs)
}

@Serializable
@SerialName("text_item")
data class TextItem(
    override val id: String,
    override val startUs: Long,
    override val durationUs: Long,
    val text: String,
    val style: CaptionStyle,
    val transform: Transform = Transform(),
    /** The caption's place, size and turn over time (P4-02); empty means [transform] holds. */
    val keyframes: List<Keyframe> = emptyList(),
) : TimelineItem

@Serializable
@SerialName("sticker_item")
data class StickerItem(
    override val id: String,
    override val startUs: Long,
    override val durationUs: Long,
    val source: MediaRef,
    val transform: Transform = Transform(),
    /** The sticker's look over time (P4-02); empty means [transform] holds. Rendered with the sticker lane (P4-12). */
    val keyframes: List<Keyframe> = emptyList(),
) : TimelineItem

/**
 * A video effect applied to the whole composed frame, captions included, for as long as the item lasts (P4-06). [effectId] is one of the
 * [EffectCatalog] ids, saved forever; an id this build can't draw is kept and skipped. [intensity] is 0 (no effect) to 1.
 */
@Serializable
@SerialName("effect_item")
data class EffectItem(
    override val id: String,
    override val startUs: Long,
    override val durationUs: Long,
    val effectId: String,
    val intensity: Float,
) : TimelineItem
