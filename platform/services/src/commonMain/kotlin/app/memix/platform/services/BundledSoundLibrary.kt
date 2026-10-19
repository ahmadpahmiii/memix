package app.memix.platform.services

import app.memix.core.domain.AppError
import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.media.CopyResult
import app.memix.core.domain.media.MediaFiles
import app.memix.core.domain.sound.SoundLibrary
import app.memix.core.model.catalog.CatalogSound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.project.MediaKind
import app.memix.core.model.project.MediaOrigin
import app.memix.core.model.project.MediaRef
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import memix.platform.services.generated.resources.Res

/**
 * The starter pack bundled with the app (P1-08): the sounds and the manifest from `content/starter-sounds/`, copied into
 * this module's Compose resources (`files/starter-sounds/`), so Android and iOS read them the same way. The license
 * proofs stay in the repo and aren't bundled.
 *
 * A sound plays from a file in the app's files directory (`sounds/<catalog id>.<extension>`), written from the bundle
 * the first time it's needed in a run and shared by every project. Media players and the engine read files, not
 * resources, and the path survives app updates. It isn't under `media/`, so the leftover-media cleanup never touches it.
 */
internal class BundledSoundLibrary(
    private val mediaFiles: MediaFiles,
    private val logger: Logger,
    /** Reads the bundle and parses the manifest, off the main thread. */
    private val ioDispatcher: CoroutineDispatcher,
    private val readBundled: suspend (path: String) -> ByteArray = { Res.readBytes(it) },
) : SoundLibrary {
    private val mutex = Mutex()
    private var pack: List<PackSound>? = null
    private val writtenThisRun = HashSet<String>()

    override suspend fun starterSounds(): List<CatalogSound> = loadPack().map { it.sound }

    override suspend fun fileOf(sound: CatalogSound): Outcome<MediaRef> {
        val entry = loadPack().firstOrNull { it.sound.id == sound.id } ?: return Outcome.Failure(AppError.NotFound)
        val path = "$SOUNDS_FOLDER/${entry.sound.id}.${entry.extension}"
        mutex.withLock {
            if (entry.sound.id !in writtenThisRun) {
                val written = writeFromBundle(entry, path)
                if (written != null) return written
                writtenThisRun += entry.sound.id
            }
        }
        return Outcome.Success(MediaRef(MediaOrigin.CatalogItem(entry.sound.id), MediaKind.AUDIO, cachedCopyPath = path))
    }

    // Null when the file is in place; else the failure to return.
    private suspend fun writeFromBundle(entry: PackSound, path: String): Outcome.Failure? {
        val bytes = readOrNull(entry.bundledPath) ?: return Outcome.Failure(AppError.NotFound)
        return when (mediaFiles.write(bytes, path)) {
            CopyResult.COPIED -> null
            CopyResult.STORAGE_FULL -> Outcome.Failure(AppError.StorageFull)
            CopyResult.UNREADABLE -> Outcome.Failure(AppError.Unexpected(IllegalStateException("Couldn't write a starter sound")))
        }
    }

    private suspend fun loadPack(): List<PackSound> = mutex.withLock {
        pack ?: readPack().also { pack = it }
    }

    private suspend fun readPack(): List<PackSound> = withContext(ioDispatcher) {
        val bytes = readOrNull(MANIFEST_PATH) ?: return@withContext emptyList()
        try {
            json.decodeFromString(StarterPackManifest.serializer(), bytes.decodeToString()).sounds.map { it.toPackSound() }
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException. The sheet then shows its empty state.
            logger.error(TAG, "The starter pack's manifest can't be read", e)
            emptyList()
        }
    }

    private suspend fun readOrNull(path: String): ByteArray? = try {
        withContext(ioDispatcher) { readBundled(path) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Compose resources throw their own exception for a missing file; a build without the pack lands here too.
        logger.error(TAG, "Bundled file $path can't be read", e)
        null
    }

    /** A pack entry with where its file sits in the bundle. */
    private class PackSound(val sound: CatalogSound, val bundledPath: String, val extension: String)

    @Serializable
    private class StarterPackManifest(val sounds: List<ManifestSound>)

    /** The manifest fields the app uses; the rest (license proof, hashes, loudness) are for the repo. */
    @Serializable
    private class ManifestSound(
        val id: String,
        val file: String,
        val title: Map<String, String>,
        val durationMs: Long,
        val licenseType: String,
        /** An attribution the license asks for; null for CC0. */
        val credit: String? = null,
    ) {
        fun toPackSound() = PackSound(
            sound = CatalogSound(id, LocalizedText(title), durationMs * MICROS_PER_MILLI, credit = credit ?: licenseType),
            bundledPath = "$PACK_FOLDER/$file",
            extension = file.substringAfterLast('.', missingDelimiterValue = DEFAULT_EXTENSION),
        )
    }

    private companion object {
        const val TAG = "MemixSounds"
        const val PACK_FOLDER = "files/starter-sounds"
        const val MANIFEST_PATH = "$PACK_FOLDER/manifest.json"
        const val SOUNDS_FOLDER = "sounds"
        const val DEFAULT_EXTENSION = "m4a"
        const val MICROS_PER_MILLI = 1_000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
