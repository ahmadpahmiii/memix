package app.memix.core.data.catalog

import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.catalog.LocalizedText
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val MICROS_PER_MILLI = 1_000L
private val textMapSerializer = MapSerializer(String.serializer(), String.serializer())

internal fun LocalizedText.toJson(): String = Json.encodeToString(textMapSerializer, byLanguage)

/** A damaged value reads as no text rather than failing the whole list. */
internal fun localizedTextOf(json: String): LocalizedText =
    LocalizedText(runCatching { Json.decodeFromString(textMapSerializer, json) }.getOrDefault(emptyMap()))

/** The mapper for SQLDelight's generated sound queries (same column order as the SELECTs in Catalog.sq). */
internal fun toLibrarySound(
    id: String,
    titleJson: String,
    categoryId: String,
    filePath: String,
    durationMs: Long,
    waveformPath: String?,
    licenseType: String,
    sourceUrl: String,
    credit: String?,
): LibrarySound = LibrarySound(
    id = id,
    title = localizedTextOf(titleJson),
    categoryId = categoryId,
    filePath = filePath,
    durationUs = durationMs * MICROS_PER_MILLI,
    waveformPath = waveformPath,
    licenseType = licenseType,
    sourceUrl = sourceUrl,
    credit = credit,
)

/** Lower-cased titles in every language and the tags, which the offline search looks through. */
internal fun searchTextOf(sound: RemoteSound): String =
    (sound.sound.title.byLanguage.values + sound.tags).joinToString(separator = " ").lowercase()

/** The LIKE pattern for "contains [query]", with the characters LIKE treats as wildcards escaped. */
internal fun containsPattern(query: String): String {
    val escaped = buildString {
        for (character in query.lowercase()) {
            if (character == '\\' || character == '%' || character == '_') append('\\')
            append(character)
        }
    }
    return "%$escaped%"
}
