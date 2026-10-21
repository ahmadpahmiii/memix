package app.memix.core.domain.text

import app.memix.core.model.project.CaptionStyle

/**
 * The caption renderer's layout, as the editors need it to draw a caption's selection frame and hit-test it (spec P1-10
 * → Caption rendering). Implemented by the video engine with the same code that draws captions in the preview and the
 * export, so the frame fits the drawn words exactly. Android: the bundled fonts with android.graphics text layout.
 * iOS: an estimate until P7.
 */
interface CaptionMeasurer {
    /** Loads the bundled caption fonts once; later calls return at once. Call before [measure] (it reads files). */
    suspend fun prepare()

    /**
     * The laid-out block of [text] in [style] at scale 1, in canvas pixels, on a canvas [canvasWidthPx] ×
     * [canvasHeightPx]: the widest line by all the lines' height. Cheap enough to call on every keystroke; main thread.
     * Before [prepare] has finished it measures with the system font.
     */
    fun measure(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int): CaptionBlockSize
}

/** A caption's laid-out block at scale 1, canvas pixels. */
data class CaptionBlockSize(val widthPx: Float, val heightPx: Float)

/**
 * The bundled caption font files (CaptionCatalog's file names), read whole. The composition root reads them from the
 * design system's font resources, where the Style panel's tiles load them too.
 */
fun interface CaptionFontSource {
    suspend fun read(fileName: String): ByteArray
}

/** Loads the caption fonts before the editor measures or draws a caption. */
class PrepareCaptionsUseCase(private val measurer: CaptionMeasurer) {
    suspend operator fun invoke() = measurer.prepare()
}

/** A caption's laid-out block (see [CaptionMeasurer.measure]). */
class MeasureCaptionUseCase(private val measurer: CaptionMeasurer) {
    operator fun invoke(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int): CaptionBlockSize =
        measurer.measure(text, style, canvasWidthPx, canvasHeightPx)
}
