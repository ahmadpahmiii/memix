package app.memix.core.domain.segmentation

import app.memix.core.model.project.PixelSize

/**
 * Finds the subject of a photo (people, pets, objects together) and hands back a mask of it. Android: ML Kit Subject
 * Segmentation, whose model Google Play services downloads on first use; iOS (P7-07): Vision. Implemented in
 * :engine:segmentation. Video segmentation joins this interface with P4-13.
 *
 * Nothing here throws for a failure the user can meet: each is a value of [SegmentationFailure] or one of the result
 * types below. Every function is main-safe (the work runs on the implementation's own dispatchers) and cancels cleanly.
 * Features don't call this; they go through [RemoveBackgroundUseCase].
 */
interface Segmenter {
    /** Whether the model is on the phone, can be downloaded, or can't be had. Never downloads. */
    suspend fun modelState(): ModelState

    /**
     * Downloads the model through Play services. [onProgress] gets the fraction done (0 to 1) as the download reports it,
     * on any thread; it may never be called, or only with null, where the platform gives no size. Cancelling the
     * coroutine stops waiting; Play services may finish the download on its own, which a later call finds already done.
     */
    suspend fun prepareModel(onProgress: (Float?) -> Unit): ModelPreparation

    /**
     * Segments the picture in the app file [sourcePath] (relative to the files directory). The picture is read at a
     * bounded size ([SegmentationLimits.INPUT_LONG_SIDE_PX]), so a 12 MP photo is never decoded in full. Gives up after
     * [SegmentationLimits.SLOW_AFTER_MILLIS] with [SegmentationFailure.SLOW]. Call it only when [modelState] was
     * [ModelState.Ready]; otherwise it fails with [SegmentationFailure.MODEL_UNAVAILABLE].
     */
    suspend fun segmentImage(sourcePath: String): SegmentationOutcome
}

/** Numbers the segmenter and the screens that wait for it agree on. */
object SegmentationLimits {
    /**
     * The longest side, in pixels, of the picture fed to the model, and so of the mask. ML Kit asks for at least
     * 512 x 512 and its mask comes back at the input's size: 1024 keeps the mask near 1 MP (about 4 MB as floats)
     * where a 12 MP input would make a 48 MB buffer and cost seconds. The mask is stretched to the picture afterwards.
     */
    const val INPUT_LONG_SIDE_PX = 1024

    /** How long a model download or a segmentation may run before the user is offered a retry. */
    const val SLOW_AFTER_MILLIS = 30_000L

    /** Below this share of foreground pixels (confidence of 0.5 or more) the picture counts as having no subject. */
    const val NOTHING_FOUND_BELOW_COVERAGE = 0.002f
}

sealed interface ModelState {
    data object Ready : ModelState

    /** Not on the phone yet; [Segmenter.prepareModel] downloads it. */
    data object NotInstalled : ModelState

    /** Can't be had on this phone: [SegmentationFailure.NO_PLAY_SERVICES], [SegmentationFailure.UNSUPPORTED] or an unknown error. */
    data class Unavailable(val failure: SegmentationFailure) : ModelState
}

sealed interface ModelPreparation {
    data object Ready : ModelPreparation

    /** Play services cancelled the download (the user did, in the system's own UI). */
    data object Cancelled : ModelPreparation

    data class Failed(val failure: SegmentationFailure) : ModelPreparation
}

sealed interface SegmentationOutcome {
    data class Found(val mask: CutoutMask, val timings: SegmentationTimings) : SegmentationOutcome

    /** The picture has no subject the model recognises. */
    data class NothingFound(val timings: SegmentationTimings) : SegmentationOutcome

    data class Failed(val failure: SegmentationFailure) : SegmentationOutcome
}

/**
 * Why a cut-out couldn't be made. P2-07 shows its cards from these: [OFFLINE] the offline card, [OUT_OF_MEMORY] the
 * memory card, [SLOW] "taking longer than usual", the rest "Can't remove the background right now".
 */
enum class SegmentationFailure {
    /** This platform has no segmenter yet (iOS until P7-07). */
    UNSUPPORTED,

    /** Google Play services is missing, disabled or too old, so the model can't be installed. */
    NO_PLAY_SERVICES,

    /** The model isn't installed, isn't ready, or its install failed. */
    MODEL_UNAVAILABLE,

    /** The model has to be downloaded and the phone has no connection. */
    OFFLINE,

    OUT_OF_MEMORY,

    /** It ran past [SegmentationLimits.SLOW_AFTER_MILLIS]. */
    SLOW,

    /** The picture's file is missing or can't be decoded. */
    SOURCE_UNREADABLE,

    /** The phone has no room for the model or the mask file. */
    STORAGE_FULL,

    UNEXPECTED,
}

/**
 * The subject as a grayscale PNG: white where the subject is, black elsewhere, soft in between (opaque pixels, red =
 * green = blue = the model's confidence). [size] is the PNG's size: the picture's own proportions with the long side
 * at most [SegmentationLimits.INPUT_LONG_SIDE_PX]. A plain class because it holds an array.
 */
class CutoutMask(val pngBytes: ByteArray, val size: PixelSize)

/** Where the time went, in milliseconds, for the debug hook and the `bg_remove` event. */
data class SegmentationTimings(val decodeMillis: Long, val modelMillis: Long, val maskMillis: Long)
