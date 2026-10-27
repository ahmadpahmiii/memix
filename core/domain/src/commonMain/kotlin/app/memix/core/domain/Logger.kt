package app.memix.core.domain

/** Logcat on Android, the Xcode console on iOS. Never log personal data or file paths from the gallery. */
interface Logger {
    fun debug(tag: String, message: String)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}
