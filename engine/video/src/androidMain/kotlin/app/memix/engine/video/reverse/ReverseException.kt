package app.memix.engine.video.reverse

/** Making a reversed copy failed (P4-10). [noSpace] is true when the phone ran out of room, which the editor words differently ("Free up space"). */
internal class ReverseException(message: String, val noSpace: Boolean = false, val tooLong: Boolean = false, cause: Throwable? = null) : Exception(message, cause)
