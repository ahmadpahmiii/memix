package app.memix.core.model.project

/** Whether this file is a voiceover take recorded in Memix (P3-08): its origin is [MediaOrigin.Recording]. */
fun MediaRef.isVoiceover(): Boolean = origin is MediaOrigin.Recording
