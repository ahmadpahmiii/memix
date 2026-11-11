package app.memix.core.data.sound

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray

private const val CONNECT_TIMEOUT_MILLIS = 8_000L

// A whole transfer is far under this; the stall rule (no bytes for 15 s) is the repository's, not Ktor's.
private const val REQUEST_TIMEOUT_MILLIS = 120_000L
private const val CHUNK_BYTES = 8 * 1024L
private const val FIRST_SERVER_ERROR = 500

internal class KtorSoundFileSource : SoundFileSource {
    // Created on first download: browsing never costs a client.
    private val http by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
        }
    }

    override suspend fun fetch(url: String, maxBytes: Long, onProgress: (received: Long, total: Long?) -> Unit): ByteArray =
        http.prepareGet(url).execute { response ->
            val status = response.status.value
            if (status >= FIRST_SERVER_ERROR) throw IOException("Server answered $status")
            if (!response.status.isSuccess()) throw SoundFileUnavailableException("Server answered $status")
            val total = response.contentLength()
            if (total != null && total > maxBytes) throw SoundFileUnavailableException("File is $total bytes")
            val channel = response.bodyAsChannel()
            val received = Buffer()
            var count = 0L
            onProgress(0L, total)
            while (true) {
                val chunk = channel.readRemaining(CHUNK_BYTES).readByteArray()
                if (chunk.isEmpty()) break
                count += chunk.size
                if (count > maxBytes) throw SoundFileUnavailableException("File is over $maxBytes bytes")
                received.write(chunk)
                onProgress(count, total)
            }
            if (total != null && count < total) throw IOException("Transfer ended after $count of $total bytes")
            received.readByteArray()
        }
}
