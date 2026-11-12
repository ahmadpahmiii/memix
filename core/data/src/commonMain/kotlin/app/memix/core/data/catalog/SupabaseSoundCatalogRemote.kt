package app.memix.core.data.catalog

import app.memix.core.data.supabase.CONNECT_TIMEOUT_MILLIS
import app.memix.core.data.supabase.REQUEST_TIMEOUT_MILLIS
import app.memix.core.data.supabase.SupabaseClientSource
import app.memix.core.domain.Logger
import app.memix.core.model.catalog.LibrarySound
import app.memix.core.model.catalog.LocalizedText
import app.memix.core.model.catalog.SoundCategory
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MICROS_PER_MILLI = 1_000L

/** A waveform file is under 1 KB today; anything past this is refused unread. */
private const val MAX_WAVEFORM_BYTES = 64 * 1024L
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val FIRST_SERVER_ERROR = 500
private const val TAG = "SoundCatalog"
private const val SOUND_COLUMNS =
    "id,title,category_id,tags,file_path,duration_ms,waveform_path,source_url,license_type,credit"

/**
 * The catalog on Supabase, read with the publishable key through the auto-generated API (TECHNICAL_DESIGN → Backend,
 * app-call table). Nothing here writes. Waveform files are plain public files, fetched with Ktor.
 */
internal class SupabaseSoundCatalogRemote(
    private val source: SupabaseClientSource,
    private val logger: Logger,
) : SoundCatalogRemote {
    private var loggedIncompleteConfig = false
    private val client get() = source.client
    private val http by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
        }
    }

    override suspend fun categories(): List<SoundCategory> = reachable {
        client.from("categories")
            .select(Columns.list("id", "name", "sort")) {
                filter {
                    eq("kind", "sound")
                    eq("is_active", true)
                }
                order("sort", Order.ASCENDING)
            }
            .decodeList<CategoryRow>()
            .map { SoundCategory(id = it.id, name = LocalizedText(it.name), sort = it.sort) }
    }

    override suspend fun sounds(regionTag: String, categoryId: String?, offset: Int, limit: Int): List<RemoteSound> = reachable {
        client.from("sounds")
            .select(Columns.raw(SOUND_COLUMNS)) {
                filter {
                    eq("is_active", true)
                    overlaps("regions", listOf(regionTag))
                    if (categoryId != null) eq("category_id", categoryId)
                }
                order("created_at", Order.DESCENDING)
                range(offset.toLong() until (offset + limit).toLong())
            }
            .decodeList<SoundRow>()
            .map(SoundRow::toRemoteSound)
    }

    override suspend fun trendingRanks(month: String, regionTags: List<String>): List<TrendingRank> = reachable {
        client.from("trending")
            .select(Columns.list("region", "rank", "item_id")) {
                filter {
                    eq("month", month)
                    eq("kind", "sound")
                    isIn("region", regionTags)
                }
                order("rank", Order.ASCENDING)
            }
            .decodeList<TrendingRow>()
            .map { TrendingRank(regionTag = it.region, rank = it.rank, soundId = it.itemId) }
    }

    override suspend fun soundsByIds(ids: List<String>): List<RemoteSound> = reachable {
        if (ids.isEmpty()) return@reachable emptyList()
        client.from("sounds")
            .select(Columns.raw(SOUND_COLUMNS)) {
                filter {
                    eq("is_active", true)
                    isIn("id", ids)
                }
            }
            .decodeList<SoundRow>()
            .map(SoundRow::toRemoteSound)
    }

    override suspend fun search(query: String, regionTag: String, offset: Int, limit: Int): List<RemoteSound> = reachable {
        val parameters = buildJsonObject {
            put("q", query)
            put("region", regionTag)
            put("lim", limit)
            put("off", offset)
        }
        client.postgrest.rpc("search_sounds", parameters).decodeList<SoundRow>().map(SoundRow::toRemoteSound)
    }

    // Refuses a file over MAX_WAVEFORM_BYTES by its Content-Length, and reads at most one byte more than the limit when
    // the server does not say, so a wrong file never fills memory.
    override suspend fun fetchText(url: String): String? = reachable {
        http.prepareGet(url).execute { response ->
            val declared = response.contentLength()
            if (!response.status.isSuccess() || (declared != null && declared > MAX_WAVEFORM_BYTES)) return@execute null
            val bytes = response.bodyAsChannel().readRemaining(MAX_WAVEFORM_BYTES + 1).readByteArray()
            if (bytes.size > MAX_WAVEFORM_BYTES) null else bytes.decodeToString()
        }
    }

    // Turns "no network" in all its forms into one exception the repository maps to Offline. Everything else (a bad
    // response, a server error) passes through and is reported as unexpected.
    private suspend fun <T> reachable(call: suspend () -> T): T {
        if (!source.isConfigured) {
            if (!loggedIncompleteConfig) {
                loggedIncompleteConfig = true
                logger.debug(TAG, "No usable Supabase settings in this build (memix.supabase.* in local.properties): serving the saved catalog only")
            }
            throw CatalogUnreachableException("This build has no Supabase settings.")
        }
        return try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestException) {
            throw CatalogUnreachableException("Catalog request failed", e)
        } catch (e: HttpRequestTimeoutException) {
            throw CatalogUnreachableException("Catalog request timed out", e)
        } catch (e: IOException) {
            throw CatalogUnreachableException("Catalog request failed", e)
        } catch (e: RestException) {
            // A paused free-plan project, an overloaded server or rate limiting: the catalog is not there right now.
            // Other 4xx answers are our own mistakes and stay unexpected.
            if (e.statusCode >= FIRST_SERVER_ERROR || e.statusCode == HTTP_TOO_MANY_REQUESTS) {
                throw CatalogUnreachableException("Catalog answered ${e.statusCode}", e)
            }
            throw e
        }
    }
}

@Serializable
private class CategoryRow(val id: String, val name: Map<String, String>, val sort: Int)

@Serializable
private class TrendingRow(val region: String, val rank: Int, @SerialName("item_id") val itemId: String)

// search_sounds returns whole rows, so unknown columns (license_proof, regions, ...) must be ignored, which the
// supabase-kt JSON configuration does by default.
@Serializable
private class SoundRow(
    val id: String,
    val title: Map<String, String>,
    @SerialName("category_id") val categoryId: String,
    val tags: List<String> = emptyList(),
    @SerialName("file_path") val filePath: String,
    @SerialName("duration_ms") val durationMs: Int,
    @SerialName("waveform_path") val waveformPath: String? = null,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("license_type") val licenseType: String,
    val credit: String? = null,
) {
    fun toRemoteSound() = RemoteSound(
        sound = LibrarySound(
            id = id,
            title = LocalizedText(title),
            categoryId = categoryId,
            filePath = filePath,
            durationUs = durationMs * MICROS_PER_MILLI,
            waveformPath = waveformPath,
            licenseType = licenseType,
            sourceUrl = sourceUrl,
            credit = credit,
        ),
        tags = tags,
    )
}
