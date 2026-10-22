package app.memix.core.domain.drafts

import app.memix.core.domain.text.graphemeCount
import app.memix.core.domain.text.takeGraphemes

/** The longest draft name, in user-perceived characters (spec P1-14 → Rename sheet). */
const val MAX_DRAFT_NAME_CHARACTERS = 60

/**
 * A typed name as it's saved: line breaks become spaces (a paste can bring them), the ends are trimmed and it keeps
 * at most [MAX_DRAFT_NAME_CHARACTERS]. An empty result makes the draft unnamed again.
 */
fun cleanDraftName(typed: String): String =
    typed.replace(LINE_BREAKS, " ").trim().takeGraphemes(MAX_DRAFT_NAME_CHARACTERS).trimEnd()

/**
 * The name of a copy of a draft named [original]: [withSuffix] of it with copy number 1 ("Cat fail (copy)"), else
 * 2, 3 and on ("(copy 2)"), the first that no draft in [takenNames] has. The original's part is shortened, never the
 * suffix, so the whole name keeps [MAX_DRAFT_NAME_CHARACTERS]. A copy of an unnamed draft stays unnamed.
 */
internal suspend fun copyName(original: String, takenNames: Set<String>, withSuffix: suspend (name: String, number: Int) -> String): String {
    if (original.isBlank()) return ""
    // One more number than there are names always finds a free one.
    for (number in 1..takenNames.size + 1) {
        val candidate = fittedCopyName(original, number, withSuffix)
        if (candidate !in takenNames) return candidate
    }
    return fittedCopyName(original, takenNames.size + 2, withSuffix)
}

private suspend fun fittedCopyName(original: String, number: Int, withSuffix: suspend (name: String, number: Int) -> String): String {
    val full = withSuffix(original, number)
    val overflow = full.graphemeCount() - MAX_DRAFT_NAME_CHARACTERS
    if (overflow <= 0) return full
    val shortened = original.takeGraphemes(original.graphemeCount() - overflow).trimEnd()
    return withSuffix(shortened, number)
}

private val LINE_BREAKS = Regex("[\\r\\n\\u2028\\u2029]+")
