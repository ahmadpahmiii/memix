package app.memix.engine.video

import android.media.MediaCodecInfo
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EncoderUtil
import app.memix.core.domain.video.EXPORT_FRAME_RATES
import app.memix.core.domain.video.ExportCapabilities
import app.memix.core.domain.video.ExportChoice
import app.memix.core.domain.video.ExportOption
import app.memix.core.domain.video.ExportResolution
import app.memix.core.domain.video.VideoCodec
import app.memix.core.domain.video.requestedVideoBitrateBps
import app.memix.core.model.project.PixelSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What this phone's video encoders can do for a canvas (spec P1-12 → What the phone can do; research
 * docs/research/P1-12-encoder-facts.md → 1). For each resolution and frame rate: the codec (H.264 on a hardware
 * encoder, else HEVC on one, else H.264 on any encoder, which is all an emulator has), the frame size aligned to that
 * encoder, and the bitrate clamped to its range.
 *
 * Sizes are checked with the long side as width, because Transformer encodes portrait frames turned and writes the
 * rotation into the file. The encoder list is read once per process (Media3 caches it); each canvas size's answer is
 * kept too. Runs on [dispatcher]: the first `MediaCodecList` scan isn't free.
 */
@OptIn(UnstableApi::class)
internal class ExportCapabilityScanner(private val dispatcher: CoroutineDispatcher, private val writesStraightToGallery: Boolean) {
    private val mutex = Mutex()
    private val byCanvas = HashMap<PixelSize, ExportCapabilities>()
    private var standardShape: Set<ExportResolution>? = null

    suspend fun capabilitiesFor(canvas: PixelSize): ExportCapabilities = withContext(dispatcher) {
        mutex.withLock {
            byCanvas.getOrPut(canvas) {
                val standard = standardShape ?: resolutionsAt(STANDARD_SHAPE).also { standardShape = it }
                ExportCapabilities(optionsFor(canvas), standard, writesStraightToGallery)
            }
        }
    }

    private fun optionsFor(canvas: PixelSize): Map<ExportChoice, ExportOption> = buildMap {
        for (resolution in ExportResolution.entries) {
            val frame = scaledToShortSide(canvas, resolution.shortSidePx)
            for (frameRate in EXPORT_FRAME_RATES) {
                optionFor(frame, frameRate, requestedVideoBitrateBps(resolution, frameRate))?.let { put(ExportChoice(resolution, frameRate), it) }
            }
        }
    }

    private fun resolutionsAt(canvas: PixelSize): Set<ExportResolution> =
        ExportResolution.entries.filterTo(HashSet()) { resolution ->
            val frame = scaledToShortSide(canvas, resolution.shortSidePx)
            EXPORT_FRAME_RATES.any { optionFor(frame, it, requestedVideoBitrateBps(resolution, it)) != null }
        }

    private fun optionFor(frame: PixelSize, frameRate: Int, bitrateBps: Int): ExportOption? {
        val portrait = frame.heightPx > frame.widthPx
        // The encoder sees the long side as width.
        val encoded = if (portrait) PixelSize(frame.heightPx, frame.widthPx) else frame
        for ((codec, hardwareOnly) in CODEC_ORDER) {
            val mimeType = codec.mimeType()
            for (encoder in EncoderUtil.getSupportedEncoders(mimeType)) {
                if (hardwareOnly && !EncoderUtil.isHardwareAccelerated(encoder, mimeType)) continue
                val aligned = alignedSize(encoder, mimeType, encoded) ?: continue
                if (!supports(encoder, mimeType, aligned, frameRate)) continue
                val bitrate = EncoderUtil.getSupportedBitrateRange(encoder, mimeType).clamp(bitrateBps)
                val frameSize = if (portrait) PixelSize(aligned.heightPx, aligned.widthPx) else aligned
                return ExportOption(codec, frameSize, bitrate)
            }
        }
        return null
    }

    // Each side rounded to the encoder's alignment (at least even), which moves the shape by under 1%.
    private fun alignedSize(encoder: MediaCodecInfo, mimeType: String, size: PixelSize): PixelSize? {
        val capabilities = encoder.getCapabilitiesForType(mimeType).videoCapabilities ?: return null
        val widthAlignment = maxOf(EVEN, capabilities.widthAlignment)
        val heightAlignment = maxOf(EVEN, capabilities.heightAlignment)
        return PixelSize(roundToMultiple(size.widthPx, widthAlignment), roundToMultiple(size.heightPx, heightAlignment))
    }

    private fun supports(encoder: MediaCodecInfo, mimeType: String, size: PixelSize, frameRate: Int): Boolean {
        if (!EncoderUtil.isSizeSupported(encoder, mimeType, size.widthPx, size.heightPx)) return false
        val capabilities = encoder.getCapabilitiesForType(mimeType).videoCapabilities ?: return false
        // The size check above also passes some under-reporting encoders through CamcorderProfile; the frame rate check
        // is level-based and can't, so a phone that records 1080p or 4K is taken at its word for 30 fps.
        return capabilities.areSizeAndRateSupported(size.widthPx, size.heightPx, frameRate.toDouble()) ||
            (frameRate <= STANDARD_FPS && isCamcorderSize(size))
    }

    private fun isCamcorderSize(size: PixelSize) = size == PixelSize(FULL_HD_WIDTH, FULL_HD_HEIGHT) || size == PixelSize(UHD_WIDTH, UHD_HEIGHT)

    private companion object {
        /** 16:9 at 1080, the shape every phone's encoder is built for: tells "the phone can't" from "this shape can't". */
        val STANDARD_SHAPE = PixelSize(1920, 1080)

        /** H.264 on hardware first (plays everywhere), then HEVC on hardware, then H.264 in software (emulators). */
        val CODEC_ORDER = listOf(VideoCodec.H264 to true, VideoCodec.HEVC to true, VideoCodec.H264 to false)

        const val EVEN = 2
        const val STANDARD_FPS = 30
        const val FULL_HD_WIDTH = 1920
        const val FULL_HD_HEIGHT = 1080
        const val UHD_WIDTH = 3840
        const val UHD_HEIGHT = 2160
    }
}

internal fun VideoCodec.mimeType(): String = when (this) {
    VideoCodec.H264 -> MimeTypes.VIDEO_H264
    VideoCodec.HEVC -> MimeTypes.VIDEO_H265
}

private fun roundToMultiple(value: Int, multiple: Int): Int = maxOf(multiple, ((value + multiple / 2) / multiple) * multiple)
