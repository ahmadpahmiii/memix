package app.memix.core.data.report

/** The report table's server side. [SupabaseSoundReportRemote] is the real one. */
internal interface SoundReportRemote {
    /**
     * Files one report of a sound. [reason] is the full `reports.reason` text. Throws [ReportUnreachableException] when
     * the server can't be reached, [ReportRefusedException] when it answered with a refusal or an error.
     */
    suspend fun file(soundId: String, reason: String)
}

/** No network, a timeout, or the connection dropped. The exception holds no report text. */
internal class ReportUnreachableException(message: String) : Exception(message)

/**
 * The server answered with an error (a rate limit, a bad request, an outage). It keeps the status code only: the answer
 * of a refused insert can quote the row, and the row holds the person's note.
 */
internal class ReportRefusedException(val statusCode: Int) : Exception("The report was refused with status $statusCode")
