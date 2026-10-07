package app.memix.core.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The video editor's content. A video project always has exactly one [TrackKind.MAIN_VIDEO] track. */
@Serializable
data class VideoTimeline(
    /** In the order the timeline shows them. */
    val tracks: List<Track>,
)

@Serializable
data class Track(
    val id: String,
    val kind: TrackKind,
    /** Each item's place comes from its [TimelineItem.startUs], never from its index in this list (CLAUDE.md rule 4). */
    val items: List<TimelineItem>,
    val muted: Boolean = false,
    val locked: Boolean = false,
)

/** Which [TimelineItem] type a track holds, and its color on the timeline. */
@Serializable
enum class TrackKind {
    /** [MediaClip]s played one after another. */
    @SerialName("main_video") MAIN_VIDEO,

    /** [MediaClip]s shown over the main video (picture in picture). */
    @SerialName("overlay") OVERLAY,

    /** [TextItem]s. */
    @SerialName("text") TEXT,

    /** [StickerItem]s. */
    @SerialName("sticker") STICKER,

    /** [AudioClip]s from the meme sound library. */
    @SerialName("meme_sound") MEME_SOUND,

    /** [AudioClip]s from the user's own files, detached audio and voiceover. */
    @SerialName("audio") AUDIO,

    /** [EffectItem]s applied to the whole canvas. */
    @SerialName("effect") EFFECT,
}
