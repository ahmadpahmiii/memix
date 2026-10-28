package app.memix.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

internal actual suspend fun writeDebugPhotos(): Boolean = withContext(Dispatchers.IO) {
    val folder = File(KoinPlatform.getKoin().get<Context>().filesDir, PhotoCheckProjects.PICTURES_DIR).apply { mkdirs() }
    PhotoCheckProjects.pictures.forEachIndexed { index, picture ->
        val file = File(folder, picture.fileName)
        if (!file.isFile) writePicture(file, picture, hue = (index * HUE_STEP) % FULL_HUE)
    }
    true
}

// A gradient with a grid, the picture's number and size, and marked corners (a turned or moved layer shows which way is up).
private fun writePicture(file: File, picture: DebugPicture, hue: Int) {
    val bitmap = Bitmap.createBitmap(picture.widthPx, picture.heightPx, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.95f))
        val bottom = Color.HSVToColor(floatArrayOf(((hue + 40) % FULL_HUE).toFloat(), 0.75f, 0.55f))
        paint.shader = android.graphics.LinearGradient(0f, 0f, picture.widthPx.toFloat(), picture.heightPx.toFloat(), top, bottom, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawPaint(paint)
        paint.shader = null
        paint.color = Color.argb(70, 255, 255, 255)
        paint.strokeWidth = picture.widthPx.toFloat() / GRID_LINE_DIVISOR
        val step = picture.widthPx / GRID_CELLS
        var x = 0
        while (x < picture.widthPx) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), picture.heightPx.toFloat(), paint)
            x += step
        }
        var y = 0
        while (y < picture.heightPx) {
            canvas.drawLine(0f, y.toFloat(), picture.widthPx.toFloat(), y.toFloat(), paint)
            y += step
        }
        paint.color = Color.WHITE
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = minOf(picture.widthPx, picture.heightPx).toFloat() / LABEL_DIVISOR
        canvas.drawText(picture.label, picture.widthPx / 2f, picture.heightPx / 2f, paint)
        paint.textSize = minOf(picture.widthPx, picture.heightPx).toFloat() / SIZE_LABEL_DIVISOR
        canvas.drawText("${picture.widthPx} x ${picture.heightPx}", picture.widthPx / 2f, picture.heightPx / 2f + paint.textSize * 2, paint)
        val corner = minOf(picture.widthPx, picture.heightPx).toFloat() / CORNER_DIVISOR
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, corner, corner, paint)
        paint.color = Color.RED
        canvas.drawRect(picture.widthPx - corner, picture.heightPx - corner, picture.widthPx.toFloat(), picture.heightPx.toFloat(), paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    } finally {
        bitmap.recycle()
    }
}

private const val HUE_STEP = 47
private const val FULL_HUE = 360
private const val GRID_CELLS = 10
private const val GRID_LINE_DIVISOR = 400
private const val LABEL_DIVISOR = 2
private const val SIZE_LABEL_DIVISOR = 12
private const val CORNER_DIVISOR = 8
private const val JPEG_QUALITY = 85

internal actual suspend fun writeDebugPng(image: androidx.compose.ui.graphics.ImageBitmap, fileName: String): String? = withContext(Dispatchers.IO) {
    val folder = File(KoinPlatform.getKoin().get<Context>().cacheDir, "scene-renders").apply { mkdirs() }
    val file = File(folder, fileName)
    runCatching { file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) } }
        .getOrNull()?.let { file.absolutePath }
}

private const val PNG_QUALITY = 100
