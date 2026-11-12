package app.memix.core.domain.mysound

import app.memix.core.model.text.takeGraphemes

/**
 * A sound the user brought in (P3-07): an imported audio file or the audio of a gallery video, kept as an AAC `.m4a` in the
 * app's own storage. It lives only on this phone: no catalog row, no upload, no report.
 */
data class MySound(
    val id: String,
    /**
     * What the user sees. Empty when the file had no name: the screen then draws "My sound" or "Sound from video" in the
     * app's language (the domain has no strings).
     */
    val name: String,
    val durationUs: Long,
    val source: MySoundSource,
    /** The `.m4a`, relative to the app's files directory ([MySoundPaths]). */
    val path: String,
    val createdAtEpochUs: Long,
)

/** Where a My sound came from; `code` is the `sound_import.source` value and what the table stores. */
enum class MySoundSource(val code: String) {
    FILE("file"),
    VIDEO("video"),
    ;

    companion object {
        fun ofCode(code: String): MySoundSource = entries.firstOrNull { it.code == code } ?: FILE
    }
}

/** The longest an imported sound may be: a whole song to cut a part from (spec P3-07 → Questions 1). */
const val MAX_MY_SOUND_DURATION_MINUTES = 10
const val MAX_MY_SOUND_DURATION_US = MAX_MY_SOUND_DURATION_MINUTES * 60 * 1_000_000L

/** The longest name, in user-perceived characters (spec P3-07 → Rename). */
const val MAX_MY_SOUND_NAME_CHARACTERS = 60

/** `sound_id` of every My sound in analytics: a local id means nothing to the catalog and nothing identifying leaves the phone. */
const val MY_SOUND_EVENT_ID = "my_sound"

/** Where My sounds' files live, relative to the app's files directory. Not `sounds/` (the download cache) and not `media/` (projects). */
object MySoundPaths {
    const val FOLDER = "mysounds"
    const val EXTENSION = "m4a"

    fun fileOf(id: String): String = "$FOLDER/$id.$EXTENSION"
}

/** A typed name as it's saved: line breaks become spaces, the ends are trimmed, at most [MAX_MY_SOUND_NAME_CHARACTERS]. Empty is allowed here; rename refuses it. */
fun cleanMySoundName(typed: String): String =
    typed.replace(LINE_BREAKS, " ").trim().takeGraphemes(MAX_MY_SOUND_NAME_CHARACTERS).trimEnd()

/** The file name without its extension, cleaned; empty when the file has no name (spec P3-07 → Names). */
fun mySoundNameFromFile(displayName: String?): String {
    val name = displayName.orEmpty().trim()
    val dot = name.lastIndexOf('.')
    // ".mp3" alone is an extension with no name; "a.b" loses ".b".
    val withoutExtension = if (dot > 0) name.substring(0, dot) else if (dot == 0) "" else name
    return cleanMySoundName(withoutExtension)
}

/** The sounds whose name contains [query], case-insensitively, in the list's order (the offline and the My sounds tab search). */
fun List<MySound>.matching(query: String): List<MySound> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return this
    return filter { it.name.lowercase().contains(needle) }
}

private val LINE_BREAKS = Regex("[\\r\\n\\u2028\\u2029]+")
