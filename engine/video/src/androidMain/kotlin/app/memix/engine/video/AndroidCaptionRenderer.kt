package app.memix.engine.video

import android.annotation.SuppressLint
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import app.memix.core.model.project.ArgbColor
import app.memix.core.model.project.CaptionBox
import app.memix.core.model.project.CaptionCatalog
import app.memix.core.model.project.CaptionFont
import app.memix.core.model.project.CaptionLook
import app.memix.core.model.project.CaptionShadow
import app.memix.core.model.project.CaptionStyle
import app.memix.core.model.project.Transform
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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

    override fun measure(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int, look: CaptionLook): CaptionBlockSize {
        val layout = layout(text, style, canvasWidthPx, canvasHeightPx, look)
        return CaptionBlockSize(layout.blockWidthPx, layout.blockHeightPx)
    }

    /**
     * [text] in [style] laid out in canvas pixels for a canvas [canvasWidthPx] × [canvasHeightPx]; any thread. [look] is the
     * size and wrap of the words (a caption's, or a speech bubble's).
     */
    fun layout(text: String, style: CaptionStyle, canvasWidthPx: Int, canvasHeightPx: Int, look: CaptionLook = CaptionLook.Text): CaptionLayout {
        val shortSidePx = min(canvasWidthPx, canvasHeightPx).toFloat()
        val fontSizePx = shortSidePx * CaptionCatalog.FONT_SIZE_OF_SHORT_SIDE * look.sizeFactor
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = typefaceOf(style.fontId)
            textSize = fontSizePx
            // Letter spacing stays 0: tracking breaks the Devanagari headline bar (spec → Geometry).
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = fontSizePx / CaptionCatalog.OUTLINE_WIDTH_DIVISOR
        }
        val wrapWidthPx = ceil(shortSidePx * look.wrapOfShortSide).toInt()
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, wrapWidthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            // A line with Devanagari from the fallback font gets that font's taller ascent and descent.
            .setUseLineSpacingFromFallbacks(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        return CaptionLayout(staticLayout, paint, style, canvasWidthPx, canvasHeightPx, look)
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
 * One caption laid out in canvas pixels. Not thread-safe: it owns its paints, so each user (an overlay, a stand-in)
 * makes its own.
 *
 * The block is what the editors select, hit-test and keep inside the frame: the words, plus the box around them when the
 * style has one (P2-11; a bubble's words never get one). The shadow is decoration and is not part of it.
 */
internal class CaptionLayout(
    private val layout: StaticLayout,
    private val paint: TextPaint,
    private val style: CaptionStyle,
    private val canvasWidthPx: Int,
    private val canvasHeightPx: Int,
    look: CaptionLook = CaptionLook.Text,
) {
    // The em of every shadow and box rule: the caption's font size at scale 1.
    private val emPx: Float = paint.textSize
    private val box: CaptionBox? = style.box.takeIf { look.drawsBox }
    private val boxPaddingX: Float = if (box != null) CaptionCatalog.BOX_PADDING_X_EM * emPx else 0f
    private val boxPaddingY: Float = if (box != null) CaptionCatalog.BOX_PADDING_Y_EM * emPx else 0f

    // Made once per layout, not per frame: the blur filter and the paint it sits on.
    private val shadow: ShadowLook? = style.shadow?.let { ShadowLook(it, paint, emPx, style.outlineColor != null) }
    private val boxPaint: Paint? = box?.let { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it.color.opaqueArgb() } }

    /** The widest line, canvas pixels. */
    private val wordsWidthPx: Float = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
    private val wordsHeightPx: Float = layout.height.toFloat()

    /** The block's width at scale 1, canvas pixels: the words and the box's padding. */
    val blockWidthPx: Float = wordsWidthPx + 2f * boxPaddingX

    val blockHeightPx: Float = wordsHeightPx + 2f * boxPaddingY

    /**
     * Draws the caption on [canvas], whose units are canvas pixels: its block centered at [transform]'s center, scaled
     * and turned clockwise around it; bottom to top the box, the shadow of the words, the outline and the fill, each line
     * centered (spec P2-11 → How a shadow and a box draw).
     */
    // core-ktx's withTranslation isn't a declared dependency of this module; save and restore do the same.
    @SuppressLint("UseKtx")
    fun draw(canvas: Canvas, transform: Transform) {
        canvas.save()
        canvas.translate(transform.centerX * canvasWidthPx, transform.centerY * canvasHeightPx)
        canvas.rotate(transform.rotationDegrees)
        canvas.scale(transform.scale, transform.scale)
        canvas.translate(-blockWidthPx / 2f, -blockHeightPx / 2f)
        drawBox(canvas)
        canvas.translate(boxPaddingX, boxPaddingY)
        shadow?.let { drawShadow(canvas, it) }
        style.outlineColor?.let { outline ->
            paint.style = Paint.Style.STROKE
            paint.color = outline.argb.toInt()
            drawLines(canvas, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = style.color.argb.toInt()
        drawLines(canvas, paint)
        canvas.restore()
    }

    private fun drawBox(canvas: Canvas) {
        val boxPaint = boxPaint ?: return
        val corners = box ?: return
        // Never rounder than half of the shorter side: a pill is as round as the box allows.
        val radius = min(CaptionCatalog.boxCornerEm(corners.corners) * emPx, min(blockWidthPx, blockHeightPx) / 2f)
        canvas.drawRoundRect(0f, 0f, blockWidthPx, blockHeightPx, radius, radius, boxPaint)
    }

    // The words' silhouette, outline included, in one color at the shadow opacity. Drawn into a group so overlapping glyphs
    // and the outline never darken each other; the group's bounds hold the offset and the blur's reach, so nothing clips.
    @SuppressLint("UseKtx")
    private fun drawShadow(canvas: Canvas, look: ShadowLook) {
        val bounds = RectF(-look.reachPx, -look.reachPx, wordsWidthPx + look.reachPx, wordsHeightPx + look.reachPx)
        val saved = canvas.saveLayerAlpha(bounds, SHADOW_ALPHA)
        canvas.translate(look.offsetXPx, look.offsetYPx)
        drawLines(canvas, look.paint)
        canvas.restoreToCount(saved)
    }

    // Each line centered in the words' block (StaticLayout's own centering would center in the wrap width instead).
    private fun drawLines(canvas: Canvas, linePaint: Paint) {
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            val end = layout.getLineVisibleEnd(line)
            val x = (wordsWidthPx - layout.getLineWidth(line)) / 2f - layout.getLineLeft(line)
            canvas.drawText(layout.text, start, end, x, layout.getLineBaseline(line).toFloat(), linePaint)
        }
    }

    private companion object {
        val SHADOW_ALPHA = (CaptionCatalog.SHADOW_OPACITY * 255f).roundToInt()
    }
}

/** A shadow ready to draw: its paint (with the blur) and offset in canvas pixels at scale 1, and how far it reaches past the words. */
private class ShadowLook(shadow: CaptionShadow, wordsPaint: TextPaint, emPx: Float, outlined: Boolean) {
    private val strokeReachPx = if (outlined) wordsPaint.strokeWidth else 0f
    val offsetXPx = CaptionCatalog.SHADOW_OFFSET_X_EM * emPx
    val offsetYPx = CaptionCatalog.SHADOW_OFFSET_Y_EM * emPx
    private val blurPx = CaptionCatalog.shadowBlurEm(shadow.softness) * emPx
    val reachPx = max(offsetXPx, offsetYPx) + CaptionCatalog.SHADOW_BLUR_REACH * blurPx + strokeReachPx
    val paint = TextPaint(wordsPaint).apply {
        // Fill and stroke in one draw is the silhouette of the words with their outline: one shadow of the outer edge.
        style = if (outlined) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
        color = shadow.color.opaqueArgb()
        if (blurPx > 0f) maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }
}

// Palette colors are opaque; the shadow's and box's opacity is the rules', never the color's own alpha.
private fun ArgbColor.opaqueArgb(): Int = (argb.toInt() or OPAQUE_ALPHA)

private const val OPAQUE_ALPHA = 0xFF000000.toInt()
