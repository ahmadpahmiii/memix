package app.memix.debug

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

internal actual suspend fun writeChromaCheckPictures(): Boolean = withContext(Dispatchers.IO) {
    val folder = File(KoinPlatform.getKoin().get<Context>().filesDir, ExportCheckProject.MEDIA_DIR).apply { mkdirs() }
    // PNG, so the pixels reach the key exactly as drawn (no compression noise on the backdrop).
    mapOf("chroma-check.png" to ChromaCheckFixture.GREEN, "chroma-check-blue.png" to ChromaCheckFixture.BLUE, "chroma-check-offgreen.png" to ChromaCheckFixture.OFF_GREEN)
        .forEach { (fileName, backdrop) ->
            val file = File(folder, fileName)
            if (!file.isFile) writePicture(file, backdrop)
        }
    true
}

private fun writePicture(file: File, backdrop: Int) {
    val picture = ChromaCheckFixture.render(backdrop = backdrop)
    val bitmap = Bitmap.createBitmap(picture.pixels, picture.width, picture.height, Bitmap.Config.ARGB_8888)
    try {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    } finally {
        bitmap.recycle()
    }
}

private const val PNG_QUALITY = 100
