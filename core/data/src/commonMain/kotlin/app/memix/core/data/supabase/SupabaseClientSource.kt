package app.memix.core.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Duration.Companion.milliseconds

internal const val CONNECT_TIMEOUT_MILLIS = 4_000L
internal const val REQUEST_TIMEOUT_MILLIS = 8_000L

/**
 * The one Supabase client the app's remote data sources share (the catalog, and since P3-10 the reports). It is created on
 * first use, so a build without Supabase settings never builds one and starting the app costs nothing.
 * The timeouts are short because search shows "Searching..." and falls back to saved sounds when the catalog is slow, and
 * the report form shows "Sending..." for no more than a few seconds before it offers to try again.
 */
// httpConfig is supabase-kt's escape hatch for the Ktor client; it is the only way to set a connect timeout.
@OptIn(SupabaseInternal::class)
internal class SupabaseClientSource(private val config: Lazy<SupabaseConfig>) {
    /** False in a build with no (or an unusable) URL and publishable key. */
    val isConfigured: Boolean get() = config.value.isComplete

    val client: SupabaseClient by lazy {
        createSupabaseClient(config.value.url, config.value.publishableKey) {
            requestTimeout = REQUEST_TIMEOUT_MILLIS.milliseconds
            // Postgrest sets its own per-request timeout (30 s by default) over the client's, so both are set.
            install(Postgrest) { timeout = REQUEST_TIMEOUT_MILLIS.milliseconds }
            httpConfig { install(HttpTimeout) { connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS } }
        }
    }
}
