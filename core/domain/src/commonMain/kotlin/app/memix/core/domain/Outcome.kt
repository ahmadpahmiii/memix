package app.memix.core.domain

/** What use cases and repositories return when an operation can fail in a way the user should see. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

/** Failures the UI maps to copy that says what happened and what to do next. */
sealed interface AppError {
    data object Offline : AppError
    data object NotFound : AppError
    data object StorageFull : AppError

    /** A saved project that can't be read back: damaged, or saved by a newer version of the app. */
    data object ProjectUnreadable : AppError

    data class Unexpected(val cause: Throwable) : AppError
}
