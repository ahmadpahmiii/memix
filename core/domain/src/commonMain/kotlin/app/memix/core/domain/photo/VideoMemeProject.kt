package app.memix.core.domain.photo

import app.memix.core.domain.media.PHOTO_CLIP_DURATION_US
import app.memix.core.model.project.Canvas
import app.memix.core.model.project.FrameFit
import app.memix.core.model.project.MediaClip
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import app.memix.core.model.project.PixelSize
import app.memix.core.model.project.Project
import app.memix.core.model.project.ProjectType
import app.memix.core.model.project.TextItem
import app.memix.core.model.project.TextLayer
import app.memix.core.model.project.Track
import app.memix.core.model.project.TrackKind
import app.memix.core.model.project.VideoTimeline
import kotlin.uuid.Uuid

/**
 * The new video project of "Make it a video meme" (spec P2-10 → Arriving in the video editor): the photo's [canvas], one main clip
 * showing [picture] for [PHOTO_CLIP_DURATION_US] filling the frame, and each of [lifted] as a caption over the whole clip with the
 * same words, style and place. Every caption gets its own text lane, in the photo's order, because they all overlap: a later lane
 * draws over an earlier one, as a higher layer does.
 */
internal fun videoMemeProject(id: String, canvas: Canvas, picture: MediaRef, lifted: List<TextLayer>, nowEpochUs: Long): Project {
    val clip = MediaClip(
        id = Uuid.random().toString(),
        startUs = 0,
        source = picture,
        trimInUs = 0,
        trimOutUs = PHOTO_CLIP_DURATION_US,
        fit = FrameFit.FILL,
    )
    val captionLanes = lifted.map { layer ->
        val caption = TextItem(Uuid.random().toString(), startUs = 0, durationUs = PHOTO_CLIP_DURATION_US, text = layer.text, style = layer.style, transform = layer.transform)
        Track(Uuid.random().toString(), TrackKind.TEXT, listOf(caption))
    }
    return Project(
        id = id,
        type = ProjectType.VIDEO,
        // Unnamed, like every new draft: the drafts list shows a stand-in in the user's language.
        name = "",
        canvas = canvas,
        video = VideoTimeline(listOf(Track(Uuid.random().toString(), TrackKind.MAIN_VIDEO, listOf(clip))) + captionLanes),
        createdAtEpochUs = nowEpochUs,
        updatedAtEpochUs = nowEpochUs,
    )
}

/** The baked picture as media: an image the app made itself, so it has no gallery origin (the catalog id names where it came from). */
internal fun bakedPictureRef(projectId: String, path: String, size: PixelSize): MediaRef = MediaRef(
    origin = MediaOrigin.CatalogItem("$BAKED_SCENE_ORIGIN/$projectId"),
    kind = MediaKind.IMAGE,
    cachedCopyPath = path,
    pixelSize = size,
    hasAudio = false,
)

private const val BAKED_SCENE_ORIGIN = "video-meme-picture"
