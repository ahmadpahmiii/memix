package app.memix.core.data.sound

/** Where the bytes of a sound file come from. [KtorSoundFileSource] is the real one; the checks use a fake. */
internal interface SoundFileSource {
    /**
     * Reads the whole file at [url] into memory (sounds are about 46 KB), calling [onProgress] with the bytes received
     * and the total when the server says it. Throws [SoundFileUnavailableException] when the file is not there or is
     * larger than [maxBytes]; any other exception (no network, a timeout, a server error) means a retry may work.
     */
    suspend fun fetch(url: String, maxBytes: Long, onProgress: (received: Long, total: Long?) -> Unit): ByteArray
}

/** The server has no such file (or refused it), or it is too big to be a sound. A retry would get the same answer. */
internal class SoundFileUnavailableException(message: String) : Exception(message)
