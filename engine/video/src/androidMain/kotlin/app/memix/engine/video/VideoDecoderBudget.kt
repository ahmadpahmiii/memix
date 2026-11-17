package app.memix.engine.video

import android.media.MediaCodecList
import androidx.media3.common.MimeTypes
import app.memix.core.domain.Logger

/**
 * How many videos this phone can decode at the same time, for [OverlayAdmission]: the main clip and every overlay video showing
 * at once each hold a hardware decoder. Read once per process from the phone's hardware decoders, the smaller of what its H.264
 * and HEVC decoders report ([android.media.MediaCodecInfo.CodecCapabilities.getMaxSupportedInstances], a hint, not a promise),
 * less [RESERVED_INSTANCES] for the thumbnails and sound previews that decode while the preview plays. The most it ever returns is the
 * ticket's target, the main video and five overlay videos ([OverlayAdmission.DEFAULT_VIDEO_DECODER_BUDGET]).
 *
 * Reads the codec list, so ask off the main thread the first time.
 */
internal class VideoDecoderBudget(private val logger: Logger) {
    val videoDecoders: Int by lazy { measure() }

    private fun measure(): Int {
        val reported = listOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265).mapNotNull(::maxHardwareInstances)
        val budget = reported.minOrNull()
            ?.let { (it - RESERVED_INSTANCES).coerceIn(MIN_BUDGET, OverlayAdmission.DEFAULT_VIDEO_DECODER_BUDGET) }
            ?: NO_HARDWARE_BUDGET
        logger.debug(TAG, "Video decoder budget $budget (hardware decoders report $reported instances)")
        return budget
    }

    // The best hardware decoder of the type: the player picks the first one that works, which is usually that one.
    private fun maxHardwareInstances(mimeType: String): Int? =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && it.isHardwareAccelerated && mimeType in it.supportedTypes }
            .maxOfOrNull { it.getCapabilitiesForType(mimeType).maxSupportedInstances }

    private companion object {
        const val TAG = "MemixVideoEngine"

        /** Left free for the editor's own decoding: the timeline thumbnails and a sound preview. */
        const val RESERVED_INSTANCES = 2

        /** The main video and one overlay video: the least that makes overlays worth having. */
        const val MIN_BUDGET = 2

        /** A phone with no hardware decoder (an emulator) decodes in software, so the budget is kept low. */
        const val NO_HARDWARE_BUDGET = 4
    }
}
