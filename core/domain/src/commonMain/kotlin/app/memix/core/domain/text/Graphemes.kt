package app.memix.core.domain.text

/*
 * User-perceived characters (grapheme clusters), for the caption's 250-character limit (spec P1-10 → Typing → Limit):
 * an emoji, a flag or a Devanagari conjunct counts as one. Pure Kotlin, so Android and iOS count alike. It follows the
 * Unicode extended grapheme cluster rules (UAX #29) that matter for the five launch languages and emoji: CR LF, marks
 * and other extenders, ZWJ emoji sequences, regional-indicator pairs, emoji modifiers and tags, and Indic conjuncts
 * (consonant + virama + consonant, GB9c). Rare scripts may split a cluster the platform would keep; the limit is then
 * reached a few characters early, never late.
 */

/** How many user-perceived characters this string has. */
fun String.graphemeCount(): Int = graphemeEnds().size

/** The first [count] user-perceived characters, never cutting a cluster in two. */
fun String.takeGraphemes(count: Int): String {
    if (count <= 0) return ""
    val ends = graphemeEnds()
    return if (count >= ends.size) this else substring(0, ends[count - 1])
}

// The end index (exclusive, in chars) of each cluster, in order.
private fun String.graphemeEnds(): List<Int> {
    val ends = ArrayList<Int>()
    var index = 0
    var previous = -1
    var regionalIndicators = 0
    var afterViramaConsonant = false
    var previousConsonant = false
    while (index < length) {
        val codePoint = codePointAt(index)
        val next = index + charCount(codePoint)
        val joins = previous >= 0 && joinsPrevious(previous, codePoint, regionalIndicators, afterViramaConsonant)
        if (!joins && index > 0) ends += index
        regionalIndicators = if (isRegionalIndicator(codePoint)) (if (joins) regionalIndicators + 1 else 1) else 0
        afterViramaConsonant = when {
            isDevanagariConsonant(codePoint) -> false
            codePoint == DEVANAGARI_VIRAMA -> previousConsonant || afterViramaConsonant
            else -> afterViramaConsonant && (isExtend(codePoint) || codePoint == ZWJ)
        }
        previousConsonant = isDevanagariConsonant(codePoint) || (previousConsonant && isExtend(codePoint) && codePoint != DEVANAGARI_VIRAMA)
        previous = codePoint
        index = next
    }
    if (length > 0) ends += length
    return ends
}

private fun joinsPrevious(previous: Int, current: Int, regionalIndicators: Int, afterViramaConsonant: Boolean): Boolean = when {
    previous == CR && current == LF -> true
    previous == CR || previous == LF || current == CR || current == LF -> false
    isExtend(current) || current == ZWJ -> true
    isSpacingMark(current) -> true
    previous == ZWJ && isExtendedPictographic(current) -> true
    isRegionalIndicator(previous) && isRegionalIndicator(current) -> regionalIndicators % 2 == 1
    // GB9c: a consonant after consonant + virama stays in the conjunct.
    afterViramaConsonant && isDevanagariConsonant(current) -> true
    else -> false
}

private fun isExtend(codePoint: Int): Boolean {
    if (codePoint in 0xFE00..0xFE0F || codePoint in 0x1F3FB..0x1F3FF || codePoint in 0xE0020..0xE007F) return true
    if (codePoint > 0xFFFF) return false
    val category = codePoint.toChar().category
    return category == CharCategory.NON_SPACING_MARK || category == CharCategory.ENCLOSING_MARK
}

private fun isSpacingMark(codePoint: Int): Boolean =
    codePoint <= 0xFFFF && codePoint.toChar().category == CharCategory.COMBINING_SPACING_MARK

private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

// Emoji and pictographs that ZWJ sequences join (a close superset of Extended_Pictographic).
private fun isExtendedPictographic(codePoint: Int): Boolean =
    codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF || codePoint in 0x2300..0x23FF ||
        codePoint == 0x00A9 || codePoint == 0x00AE || codePoint in 0x2190..0x21FF || codePoint in 0x2B00..0x2BFF

private fun isDevanagariConsonant(codePoint: Int): Boolean = codePoint in 0x0915..0x0939 || codePoint in 0x0958..0x095F

private fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    }
    return high.code
}

private fun charCount(codePoint: Int): Int = if (codePoint > 0xFFFF) 2 else 1

private const val CR = 0x0D
private const val LF = 0x0A
private const val ZWJ = 0x200D
private const val DEVANAGARI_VIRAMA = 0x094D
