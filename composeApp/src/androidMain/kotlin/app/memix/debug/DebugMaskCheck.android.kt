package app.memix.debug

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

internal actual suspend fun writeMaskCheckPicture(): Boolean = withContext(Dispatchers.IO) {
    val file = File(File(KoinPlatform.getKoin().get<Context>().filesDir, ExportCheckProject.MEDIA_DIR).apply { mkdirs() }, "mask-grid.png")
    if (!file.isFile) {
        val pixels = MaskCheckFixture.render()
        val bitmap = Bitmap.createBitmap(pixels, MaskCheckFixture.SIZE_PX, MaskCheckFixture.SIZE_PX, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        } finally {
            bitmap.recycle()
        }
    }
    true
}

private const val PNG_QUALITY = 100
