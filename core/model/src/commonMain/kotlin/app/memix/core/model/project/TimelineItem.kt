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
) : TimelineItem {
    // Derived, not saved, so it can never disagree with the trim points.
    override val durationUs: Long get() = trimOutUs - trimInUs
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

/** An effect applied to the whole canvas for as long as the item lasts. */
@Serializable
@SerialName("effect_item")
data class EffectItem(
    override val id: String,
    override val startUs: Long,
    override val durationUs: Long,
    val effect: EffectSpec,
) : TimelineItem
