package app.memix.feature.videoeditor

/**
 * Which "a file is missing" toast an open editor shows, once per open (spec P4-01b → States): a main clip's, or, when only overlay
 * files are missing, the overlay's. When both kinds are missing, the main clip's shows and the overlay's waits for the next open.
 */
internal class MissingMediaNotice {
    private var told = false

    /** The toast to show for a preview that reports [missingMedia] ([onlyOverlays]: every missing file is an overlay's), or null when nothing is new. */
    fun toastFor(missingMedia: Boolean, onlyOverlays: Boolean): ToastMessage? {
        if (!missingMedia || told) return null
        told = true
        return if (onlyOverlays) ToastMessage.MissingOverlay else ToastMessage.MissingMedia
    }
}

/**
 * Tells the user that some overlay videos are left out (the phone can't decode that many at once), once for each overlay: a new
 * edit that leaves the same ones out says nothing again, and one that leaves out another says so (spec P4-01b → States; the copy
 * was proposed by the engineer, the designer reviews it).
 */
internal class SkippedOverlayNotice {
    private var told: Set<String> = emptySet()

    /** True when [skipped] holds an overlay the user hasn't been told about; remembers what is skipped now. */
    fun hasNewlySkipped(skipped: List<String>): Boolean {
        val now = skipped.toSet()
        val isNew = (now - told).isNotEmpty()
        told = now
        return isNew
    }
}
