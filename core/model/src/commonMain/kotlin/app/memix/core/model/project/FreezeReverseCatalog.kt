package app.memix.core.model.project

/**
 * The numbers of Reverse and Freeze frame (P4-10; spec docs/ux/specs/P4-10-reverse-and-freeze.md, design/tokens.json → freezeReverse).
 * User content, not chrome, so they live here and not in the theme.
 */
object FreezeReverseCatalog {
    /** A clip can be reversed when its trimmed source is at most this long (Question 1: 60 s); the engine prepares a copy of exactly that range. */
    const val MAX_REVERSE_SOURCE_US = 60_000_000L

    /** The status "Reversing 40%" shows only when the copy isn't ready after this long. */
    const val STATUS_AFTER_MS = 1_000L

    /** How long a new freeze frame lasts (Question 4: 2 s). */
    const val FREEZE_DEFAULT_US = 2_000_000L

    /** A freeze clip can be trimmed down to this and up to [FREEZE_MAX_US]. */
    const val FREEZE_MIN_US = 100_000L
    const val FREEZE_MAX_US = 10_000_000L

    /** A playhead closer than this to a clip's edge puts the freeze before or after the clip instead of splitting it: no sliver pieces. */
    const val NO_SLIVER_US = 100_000L

    /** The picture of a freeze frame is read at most this many pixels on its long side (the largest export edge). */
    const val FREEZE_PICTURE_LONG_SIDE_PX = 1920
}
