package app.memix.engine.video

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import app.memix.core.model.project.PixelSize

/**
 * Tells Media3's video compositor where to draw each video sequence of an overlay composition ([OverlaySequences] lists them):
 * the stage sequence is never visible, each overlay layer is drawn by its clip's [OverlayPlacement] while a clip shows (and
 * not at all in a gap, whose frames are opaque black), and the main video fills the frame.
 *
 * Media3 asks once per frame, on its GL thread, with the frame's own time, so everything is prepared up front and a lookup
 * allocates nothing. The same settings serve the preview and the export (the export frame's size makes the difference).
 */
@OptIn(UnstableApi::class)
internal class OverlayCompositorSettings(
    /** One timeline per overlay layer, top layer first: the order of their sequences, which is the compositor's input order. */
    private val layersTopFirst: List<LayerTimeline>,
    private val frame: PixelSize,
) : VideoCompositorSettings {

    private val outputSize = Size(frame.widthPx, frame.heightPx)
    private var warnedAboutPrimary = false

    // The compositor sizes its output by this, not by the stage's 16 x 16 blank frames. The first size is the primary input's: if it
    // isn't the stage's, the stage did not register first and the stack order is wrong (hand check P4-01a §4).
    override fun getOutputSize(inputSizes: List<Size>): Size {
        val first = inputSizes.firstOrNull()
        if (!warnedAboutPrimary && first != null && (first.width != STAGE_SIZE_PX || first.height != STAGE_SIZE_PX)) {
            warnedAboutPrimary = true
            Log.e(TAG, "The primary compositor input is ${first.width} x ${first.height}, not the stage's $STAGE_SIZE_PX x $STAGE_SIZE_PX: overlays will draw in the wrong order")
        }
        return outputSize
    }

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings = when (inputId) {
        OverlaySequences.STAGE_INPUT -> HIDDEN
        in OverlaySequences.FIRST_LAYER_INPUT until OverlaySequences.FIRST_LAYER_INPUT + layersTopFirst.size ->
            layersTopFirst[inputId - OverlaySequences.FIRST_LAYER_INPUT].settingsAt(presentationTimeUs)
        else -> FILL_FRAME
    }

    /** One overlay layer in time: where each of its clips shows (µs, end excluded), in order, with the settings to draw it by. */
    class LayerTimeline(private val startsUs: LongArray, private val endsUs: LongArray, private val settings: List<OverlaySettings>) {
        fun settingsAt(timeUs: Long): OverlaySettings {
            // The last clip that starts at or before the time; clips don't overlap, so it's the only candidate.
            var low = 0
            var high = startsUs.size - 1
            var found = -1
            while (low <= high) {
                val middle = (low + high) ushr 1
                if (startsUs[middle] <= timeUs) {
                    found = middle
                    low = middle + 1
                } else {
                    high = middle - 1
                }
            }
            return if (found >= 0 && timeUs < endsUs[found]) settings[found] else HIDDEN
        }
    }

    companion object {
        private const val TAG = "MemixVideoEngine"

        // Media3's blank gap frames are 16 x 16.
        private const val STAGE_SIZE_PX = 16

        // Alpha 0: the gap frames and the stage are drawn invisible, so nothing but the layers' own clips and the main video shows.
        val HIDDEN: OverlaySettings = StaticOverlaySettings.Builder().setAlphaScale(0f).build()

        // Media3's defaults: centered, scale 1, opaque. The main video is already frame-sized, so this fills the frame.
        val FILL_FRAME: OverlaySettings = StaticOverlaySettings.Builder().build()

        /** [placement] as Media3 draws it. */
        fun settingsOf(placement: OverlayPlacement): OverlaySettings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(placement.anchorX, placement.anchorY)
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(placement.scaleX, placement.scaleY)
            .setRotationDegrees(placement.rotationDegrees)
            .setAlphaScale(placement.alpha)
            .build()
    }
}
