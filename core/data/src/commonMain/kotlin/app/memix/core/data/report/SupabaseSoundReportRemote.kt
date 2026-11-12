package app.memix.core.data.report

import app.memix.core.data.supabase.SupabaseClientSource
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reports go into `public.reports` with the publishable key: row level security lets the app insert a row whose status
 * is `open` and nothing else (it can't read, change or delete reports). The insert asks for no row back, because the
 * app has no right to read one and a returned row would be refused. The rule has no rate limit: anyone with the
 * publishable key could post many rows, which is the price of an account-free form (TECHNICAL_DESIGN → Reports).
 */
internal class SupabaseSoundReportRemote(private val source: SupabaseClientSource) : SoundReportRemote {
    override suspend fun file(soundId: String, reason: String) {
        check(source.isConfigured) { "This build has no Supabase settings." }
        try {
            source.client.from(REPORTS_TABLE).insert(ReportRow(itemKind = KIND_SOUND, itemId = soundId, reason = reason))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            throw ReportRefusedException(e.statusCode)
        } catch (e: HttpRequestException) {
            throw ReportUnreachableException("The report request failed")
        } catch (e: HttpRequestTimeoutException) {
            throw ReportUnreachableException("The report request timed out")
        } catch (e: IOException) {
            throw ReportUnreachableException("The report request failed")
        }
    }
}

private const val REPORTS_TABLE = "reports"
private const val KIND_SOUND = "sound"

// Exactly the columns the app may set; `status` is left to its default, `open`, which is what the insert rule demands.
@Serializable
private class ReportRow(
    @SerialName("item_kind") val itemKind: String,
    @SerialName("item_id") val itemId: String,
    val reason: String,
)
