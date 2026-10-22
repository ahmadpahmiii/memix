package app.memix.engine.video

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.memix.core.domain.Logger
import app.memix.core.domain.text.CaptionBlockSize
import app.memix.core.domain.text.CaptionFontSource
import app.memix.core.domain.text.CaptionMeasurer
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionFont
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one caption renderer (spec P1-10 → Caption rendering; TECHNICAL_DESIGN → Captions): the preview's and the
 * export's overlay ([CaptionOverlay]) and the editor's stand-in ([CaptionStandIn]) all draw with it, so a caption looks
 * the same everywhere. Each caption is laid out once in canvas pixels ([layout]) and drawn scaled to whatever shows it.
 *
 * Fonts: the bundled caption fonts, each with the system font as its fallback (emoji, other scripts); Classic falls
 * back to Teko Bold first, so Devanagari keeps the heavy, condensed look. Android's per-cluster fallback
 * (Typeface.CustomFallbackBuilder) keeps every cluster in one font, so Devanagari shaping never breaks.
 */
internal class AndroidCaptionRenderer(
    private val fontSource: CaptionFontSource,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) : CaptionMeasurer {
    private val loading = Mutex()

    // Written once, then only read, from any thread (the main thread, the GL thread of an overlay).
    @Volatile
    private var typefaces: Map<String, Typeface> = emptyMap()

    override suspend fun prepare() {
        if (typefaces.isNotEmpty()) return
        loading.withLock {
            if (typefaces.isNotEmpty()) return
            typefaces = withContext(ioDispatcher) { loadTypefaces() }
        }
    }

    override fun measure(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int): CaptionBlockSize {
        val layout = layout(text, style, canvasWidthPx, canvasHeightPx)
        return CaptionBlockSize(layout.blockWidthPx, layout.blockHeightPx)
    }

    /** [text] in [style] laid out in canvas pixels for a canvas [canvasWidthPx] × [canvasHeightPx]; any thread. */
    fun layout(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int): CaptionLayout {
        val shortSidePx = min(canvasWidthPx, canvasHeightPx).toFloat()
        val fontSizePx = shortSidePx * CaptionCatalog.FONT_SIZE_OF_SHORT_SIDE
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typefaceOf(style.fontId)
            textSize = fontSizePx
            // Letter spacing stays 0: tracking breaks the Devanagari headline bar (spec → Geometry).
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = fontSizePx / CaptionCatalog.OUTLINE_WIDTH_DIVISOR
        }
        val wrapWidthPx = ceil(shortSidePx * CaptionCatalog.WRAP_WIDTH_OF_SHORT_SIDE).toInt()
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, wrapWidthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            // A line with Devanagari from the fallback font gets that font's taller ascent and descent.
            .setUseLineSpacingFromFallbacks(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        return CaptionLayout(staticLayout, paint, style, canvasWidthPx, canvasHeightPx)
    }

    /** The bundled font [fontId] (Classic when unknown); call [prepare] first. Any thread. The watermark draws with it too. */
    fun typefaceOf(fontId: String): Typeface {
        val loaded = typefaces
        return loaded[fontId] ?: loaded[CaptionCatalog.classic.id] ?: Typeface.DEFAULT_BOLD
    }

    private suspend fun loadTypefaces(): Map<String, Typeface> = CaptionCatalog.fonts.mapNotNull { font ->
        try {
            font.id to typefaceFor(font)
        } catch (e: IOException) {
            logger.error(TAG, "Caption font ${font.fileName} didn't load", e)
            null
        } catch (e: IllegalArgumentException) {
            logger.error(TAG, "Caption font ${font.fileName} isn't a font", e)
            null
        }
    }.toMap()

    private suspend fun typefaceFor(font: CaptionFont): Typeface {
        val builder = Typeface.CustomFallbackBuilder(familyOf(font.fileName))
        font.devanagariFallbackFileName?.let { builder.addCustomFallback(familyOf(it)) }
        return builder.setSystemFallback(SYSTEM_FALLBACK).build()
    }

    private suspend fun familyOf(fileName: String): FontFamily {
        val bytes = fontSource.read(fileName)
        // Font.Builder needs a direct buffer.
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        return FontFamily.Builder(Font.Builder(buffer).build()).build()
    }

    private companion object {
        const val TAG = "MemixCaptions"
        const val SYSTEM_FALLBACK = "sans-serif"
    }
}

/**
 * One caption laid out in canvas pixels. Not thread-safe: it owns its paint, so each user (an overlay, a stand-in)
 * makes its own.
 */
internal class CaptionLayout(
    private val layout: StaticLayout,
    private val paint: TextPaint,
    private val style: CaptionStyle,
    private val canvasWidthPx: Int,
    private val canvasHeightPx: Int,
) {
    /** The widest line, canvas pixels: the block's width at scale 1. */
    val blockWidthPx: Float = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f

    val blockHeightPx: Float = layout.height.toFloat()

    /**
     * Draws the caption on [canvas], whose units are canvas pixels: its block centered at [transform]'s center, scaled
     * and turned clockwise around it; each line centered; the outline first, the fill over it.
     */
    // core-ktx's withTranslation isn't a declared dependency of this module; save and restore do the same.
    @SuppressLint("UseKtx")
    fun draw(canvas: Canvas, transform: Transform) {
        canvas.save()
        canvas.translate(transform.centerX * canvasWidthPx, transform.centerY * canvasHeightPx)
        canvas.rotate(transform.rotationDegrees)
        canvas.scale(transform.scale, transform.scale)
        canvas.translate(-blockWidthPx / 2f, -blockHeightPx / 2f)
        style.outlineColor?.let { outline ->
            paint.style = Paint.Style.STROKE
            paint.color = outline.argb.toInt()
            drawLines(canvas)
        }
        paint.style = Paint.Style.FILL
        paint.color = style.color.argb.toInt()
        drawLines(canvas)
        canvas.restore()
    }

    // Each line centered in the block (StaticLayout's own centering would center in the wrap width instead).
    private fun drawLines(canvas: Canvas) {
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            val end = layout.getLineVisibleEnd(line)
            val x = (blockWidthPx - layout.getLineWidth(line)) / 2f - layout.getLineLeft(line)
            canvas.drawText(layout.text, start, end, x, layout.getLineBaseline(line).toFloat(), paint)
        }
    }
}
