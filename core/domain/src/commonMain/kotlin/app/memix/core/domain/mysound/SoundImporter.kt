package app.memix.core.domain.mysound

import app.memix.core.model.project.MediaOrigin

/**
 * The phone's side of an import to My sounds (P3-07). Implemented with Media3 in :engine:video. Reads the picked item
 * straight from its provider and writes the converted file into the app's storage; the bytes are never sent anywhere.
 * Runs its work off the main thread. Neither function throws for a bad file: they report why.
 */
interface SoundImporter {
    /** Looks at the pick without converting it: its name, size and length, and whether it can be imported at all. */
    suspend fun probe(origin: MediaOrigin, source: MySoundSource): SoundProbe

    /**
     * Converts the pick's audio track to AAC in an `.m4a` at [destination] (relative to the app's files directory),
     * reporting progress from 0 to 1. The bytes go to `<destination>.part`, renamed only once complete, so a cut-short
     * conversion never sits under a real name. Cancelling the caller stops it and deletes the unfinished file.
     */
    suspend fun convert(origin: MediaOrigin, destination: String, onProgress: (Float) -> Unit): SoundConversion
}

/**
 * What [SoundImporter.probe] found. [displayName] is shown only on this phone, never logged. [durationUs] is the audio's
 * length when the file says; [failure] is set when the pick can't be imported ([MySoundFailure.TOO_LONG] is decided by the use case).
 */
data class SoundProbe(val displayName: String?, val sizeBytes: Long?, val durationUs: Long?, val failure: MySoundFailure? = null)

sealed interface SoundConversion {
    data object Converted : SoundConversion
    data class Failed(val failure: MySoundFailure) : SoundConversion
}

/** Why an import didn't happen; `code` is `sound_import.reason` (spec P3-07 → Analytics). */
enum class MySoundFailure(val code: String) {
    /** Not audio, DRM-protected, or a codec this phone can't decode. */
    UNSUPPORTED("unsupported"),

    /** A video without a sound track. */
    NO_AUDIO("no_audio"),
    TOO_LONG("too_long"),

    /** A cloud file with no connection, or a file deleted since picking. */
    UNREADABLE("unreadable"),

    /** The provider refused access. */
    DENIED("denied"),
    STORAGE("storage"),
}
