package app.memix.core.domain.share

import app.memix.core.domain.Analytics
import app.memix.core.domain.AnalyticsEvent

/** Hands a finished video or picture to the phone's share sheet (Android: `ACTION_SEND` in a chooser). P5-05 adds direct targets. */
interface Sharer {
    /** Opens the system share sheet for the video at [location] with [chooserTitle]. False when nothing could open it. */
    fun shareVideo(location: String, chooserTitle: String): Boolean

    /** Opens the system share sheet for the picture at [location] (a gallery content URI) of [mimeType] with [chooserTitle]. False when nothing could open it. */
    fun sharePicture(location: String, mimeType: String, chooserTitle: String): Boolean
}

/** Share on the Exported screen: the system share sheet, counted as `share_target` `more` (owner, 21 Oct 2026). */
class ShareVideoUseCase(private val sharer: Sharer, private val analytics: Analytics) {
    operator fun invoke(location: String, chooserTitle: String): Boolean {
        val opened = sharer.shareVideo(location, chooserTitle)
        if (opened) analytics.log(AnalyticsEvent.ShareTarget(TARGET_MORE))
        return opened
    }

    private companion object {
        const val TARGET_MORE = "more"
    }
}

/** Share on the photo Exported screen: the same share sheet with the picture attached, counted as `share_target` `more`. */
class SharePictureUseCase(private val sharer: Sharer, private val analytics: Analytics) {
    operator fun invoke(location: String, mimeType: String, chooserTitle: String): Boolean {
        val opened = sharer.sharePicture(location, mimeType, chooserTitle)
        if (opened) analytics.log(AnalyticsEvent.ShareTarget(TARGET_MORE))
        return opened
    }

    private companion object {
        const val TARGET_MORE = "more"
    }
}
