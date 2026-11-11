package app.memix.core.data.catalog

import app.memix.core.model.catalog.Waveform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

private const val SUPPORTED_VERSION = 1

/** The file holds 64 peaks today; more than this is not one of ours. */
internal const val MAX_WAVEFORM_PEAKS = 1024

/**
 * Reads a waveform file, `{"v":1,"peaks":[...]}` (TECHNICAL_DESIGN → Waveform file). The number of peaks is whatever
 * the file holds, unknown fields are ignored, and peaks outside 0 to 1 are clamped. Returns null, never throws, when
 * the version is not 1, the text is not that shape, no peak is left, or there are more than [MAX_WAVEFORM_PEAKS]: the sound then shows the plain rail.
 */
internal fun decodeWaveform(text: String): Waveform? {
    val root = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (e: IllegalArgumentException) { // SerializationException is one
        null
    } ?: return null
    if (root["v"].numberOrNull()?.intOrNull != SUPPORTED_VERSION) return null
    val peaks = (root["peaks"] as? JsonArray)?.map { it.numberOrNull()?.floatOrNull ?: return null }
    if (peaks.isNullOrEmpty() || peaks.size > MAX_WAVEFORM_PEAKS) return null
    return Waveform(peaks.map { it.coerceIn(0f, 1f) })
}

// A quoted "1" is text, not a number: the file contract has numbers only.
private fun JsonElement?.numberOrNull(): JsonPrimitive? = (this as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }
