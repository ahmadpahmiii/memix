package app.memix.core.ui.scene

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Small pictures for lists and swatches (the photo editor's Layers rows, the Canvas panel's Image swatch in both editors):
 * each file decoded once at [THUMBNAIL_LONG_SIDE_PX] (never the original), the picture whole; the caller fits or crops it.
 * Read while drawing, so a picture that arrives redraws only what shows it; a file that can't be read is reported as
 * [failed]. Lives with the editor screen, which calls [release] when it goes.
 */
@Stable
class SceneThumbnails(private val source: SceneImageSource, private val scope: CoroutineScope) {
    private val pictures = mutableStateMapOf<String, ImageBitmap>()
    private val failed = mutableStateMapOf<String, Boolean>()
    private val asked = HashSet<String>()

    fun pictureOf(path: String): ImageBitmap? = pictures[path]

    fun failed(path: String): Boolean = failed[path] == true

    /** Starts decoding [path] once. */
    fun request(path: String) {
        if (!asked.add(path)) return
        scope.launch {
            val bitmap = source.decode(path, THUMBNAIL_LONG_SIDE_PX, SceneImageUse.SCREEN)
            if (bitmap == null) failed[path] = true else pictures[path] = bitmap.image
        }
    }

    fun release() {
        pictures.clear()
        failed.clear()
        asked.clear()
    }
}

/** Sharp at a row's 40 dp or a swatch's circle on a 3x screen (120 px) with a little to spare; far below the 192 px the spec allows. */
private const val THUMBNAIL_LONG_SIDE_PX = 128
