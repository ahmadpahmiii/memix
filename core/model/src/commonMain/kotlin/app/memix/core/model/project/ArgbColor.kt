package app.memix.core.model.project

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/** A color the user picked, packed as 0xAARRGGBB (for example `ArgbColor(0xFFFFFFFF)` is opaque white). */
@Serializable
@JvmInline
value class ArgbColor(val argb: Long) {
    init {
        require(argb in 0..MAX_ARGB) { "Not a 32-bit ARGB color: $argb" }
    }

    private companion object {
        const val MAX_ARGB = 0xFFFFFFFFL
    }
}
