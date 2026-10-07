package app.memix.debug

import app.memix.core.domain.Logger
import app.memix.core.domain.Outcome
import app.memix.core.domain.project.GetProjectUseCase
import app.memix.core.domain.project.ProjectRepository
import app.memix.core.domain.project.SaveProjectUseCase
import app.memix.core.model.project.Project
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Debug-only hand check for P1-01, "a project created, saved, force-closed and reopened comes back identical".
 * Step [SAVE] saves [SampleProject]; after the app is force-stopped, step [VERIFY] loads it and logs
 * `identical` or the first difference under the log tag [TAG]. Android starts it from MainActivity's
 * `memix.projectCheck` extra in debug builds only.
 */
class ProjectRoundTripCheck : KoinComponent {
    private val repository: ProjectRepository by inject()
    private val logger: Logger by inject()

    suspend fun run(step: String) {
        when (step) {
            SAVE -> save()
            VERIFY -> verify()
            else -> logger.error(TAG, "Unknown step '$step'. Use '$SAVE' or '$VERIFY'.")
        }
    }

    private suspend fun save() {
        val sample = SampleProject.everyTrackType()
        // The clock stands still at the sample's own edit time, so the saved copy is exactly the sample.
        val saveProject = SaveProjectUseCase(repository, FixedClock(sample.updatedAtEpochUs))
        when (val outcome = saveProject(sample)) {
            is Outcome.Success -> logger.debug(TAG, "save: saved ${sample.id}. Force-stop the app, then run verify.")
            is Outcome.Failure -> logger.error(TAG, "save: failed with ${outcome.error}")
        }
    }

    private suspend fun verify() {
        val expected = SampleProject.everyTrackType()
        when (val outcome = GetProjectUseCase(repository)(expected.id)) {
            is Outcome.Success -> logComparison(expected, outcome.value)
            is Outcome.Failure -> logger.error(TAG, "verify: load failed with ${outcome.error}")
        }
    }

    private fun logComparison(expected: Project, loaded: Project) {
        if (loaded == expected) {
            logger.debug(TAG, "verify: identical")
        } else {
            logger.error(TAG, "verify: different. ${firstDifference(expected.toString(), loaded.toString())}")
        }
    }

    // Data classes print every field, so the first character that differs points at the field that changed.
    private fun firstDifference(expected: String, loaded: String): String {
        val index = expected.indices.firstOrNull { it >= loaded.length || expected[it] != loaded[it] } ?: expected.length
        return "First difference at character $index: expected '${expected.excerptAround(index)}', loaded '${loaded.excerptAround(index)}'"
    }

    private fun String.excerptAround(index: Int): String =
        substring((index - EXCERPT_RADIUS).coerceIn(0, length), (index + EXCERPT_RADIUS).coerceIn(0, length))

    private class FixedClock(epochUs: Long) : Clock {
        private val instant = Instant.fromEpochSeconds(epochUs / MICROS_PER_SECOND, (epochUs % MICROS_PER_SECOND) * NANOS_PER_MICRO)

        override fun now(): Instant = instant
    }

    companion object {
        const val TAG = "MemixProjectCheck"
        const val SAVE = "save"
        const val VERIFY = "verify"
        private const val EXCERPT_RADIUS = 60
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val NANOS_PER_MICRO = 1_000L
    }
}
