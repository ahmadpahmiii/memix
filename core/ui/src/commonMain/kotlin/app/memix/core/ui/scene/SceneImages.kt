package app.memix.core.ui.scene

import androidx.compose.ui.graphics.ImageBitmap
import app.memix.core.model.project.PixelSize

/** A picture decoded for the scene: [image] at the size it was decoded for, and the whole picture's [sourceSize] as shown. */
class SceneBitmap(val image: ImageBitmap, val sourceSize: PixelSize)

/** What a decoded picture is for: the screen can use a GPU-backed bitmap; reading pixels back (the render hook, export) needs a software one. */
enum class SceneImageUse { SCREEN, OFFSCREEN }

/**
 * Decodes the app's copy of a picture at the size it will be drawn at (spec docs/ux/specs/P2-01-photo-editor-shell.md →
 * Performance). Implemented in `:engine:photo` with the phone's own decoder; EXIF orientation is applied.
 */
interface SceneImageSource {
    /**
     * [path] (relative to the app's files directory) scaled down so its long side is at most [maxLongSidePx], never
     * scaled up. Null when the file is missing or can't be decoded. Runs off the main thread; safe to call from it.
     */
    suspend fun decode(path: String, maxLongSidePx: Int, use: SceneImageUse): SceneBitmap?
}

/** The pictures the scene draws, by the app's copy of the file. Read while drawing, so a picture that arrives redraws only the scene. */
interface SceneImages {
    /** The decoded picture of [path], or null while it isn't ready (or when the file is missing). */
    fun imageOf(path: String): SceneBitmap?

    /** How far [path]'s picture has faded in, 0 to 1 (spec → Before the first draw: 120 ms). */
    fun appearanceOf(path: String): Float
}
