package app.memix.core.domain

/** Runs once per app start. A failed refresh isn't shown to the user: the last values still work. */
class RefreshAppConfigUseCase(private val appConfig: AppConfig, private val logger: Logger) {
    suspend operator fun invoke() {
        when (val outcome = appConfig.refresh()) {
            is Outcome.Success -> logger.debug(TAG, "Remote config ready, media_base_url='${appConfig.mediaBaseUrl}'")
            is Outcome.Failure -> logger.error(TAG, "Remote config refresh failed: ${outcome.error}")
        }
    }

    private companion object {
        const val TAG = "AppConfig"
    }
}
