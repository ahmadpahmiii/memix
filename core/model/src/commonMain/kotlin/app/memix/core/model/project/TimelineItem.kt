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
) : TimelineItem {
    override val durationUs: Long get() = trimOutUs - trimInUs
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
) : TimelineItem

@Serializable
@SerialName("sticker_item")
data class StickerItem(
    override val id: String,
    override val startUs: Long,
    override val durationUs: Long,
    val source: MediaRef,
    val transform: Transform = Transform(),
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
