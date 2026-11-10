package app.memix.debug

// The photo editor's debug scenes are Android hand checks until the iOS port (P7-09).
internal actual suspend fun writeDebugPhotos(): Boolean = false

internal actual suspend fun writeDebugPng(image: androidx.compose.ui.graphics.ImageBitmap, fileName: String): String? = null
