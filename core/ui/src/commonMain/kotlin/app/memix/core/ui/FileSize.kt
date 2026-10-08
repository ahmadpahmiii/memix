package app.memix.core.ui

import androidx.compose.runtime.Composable
import memix.core.ui.generated.resources.Res
import memix.core.ui.generated.resources.file_size_bytes
import memix.core.ui.generated.resources.file_size_decimal_separator
import memix.core.ui.generated.resources.file_size_gigabytes
import memix.core.ui.generated.resources.file_size_kilobytes
import memix.core.ui.generated.resources.file_size_megabytes
import memix.core.ui.generated.resources.file_size_terabytes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Which way a shown size rounds. */
enum class SizeRounding {
    NEAREST,

    /** For an amount that's needed, so it's never understated. */
    UP,

    /** For an amount that's free, so it's never overstated. */
    DOWN,
}

/**
 * A short file size in the user's language, like "48 MB" or "1.2 GB": steps of 1,000 like Android's own storage
 * screens, one decimal under 10, whole numbers above. Unit symbols and the decimal mark are string resources.
 */
@Composable
fun fileSizeText(bytes: Long, rounding: SizeRounding = SizeRounding.NEAREST): String {
    val size = ShortFileSize.of(bytes, rounding)
    val number = if (size.tenthsDigit == null) {
        size.whole.toString()
    } else {
        size.whole.toString() + stringResource(Res.string.file_size_decimal_separator) + size.tenthsDigit
    }
    return stringResource(size.unit.format, number)
}

internal enum class FileSizeUnit(val format: StringResource) {
    BYTES(Res.string.file_size_bytes),
    KILOBYTES(Res.string.file_size_kilobytes),
    MEGABYTES(Res.string.file_size_megabytes),
    GIGABYTES(Res.string.file_size_gigabytes),
    TERABYTES(Res.string.file_size_terabytes),
}

/** A size in its largest unit below 1,000; [tenthsDigit] is null when it shows as a whole number. */
internal data class ShortFileSize(val whole: Long, val tenthsDigit: Long?, val unit: FileSizeUnit) {
    companion object {
        private const val STEP = 1_000L
        private const val TENTHS = 10L

        fun of(bytes: Long, rounding: SizeRounding): ShortFileSize {
            val positiveBytes = bytes.coerceAtLeast(0)
            val units = FileSizeUnit.entries
            var unitIndex = 0
            var divisor = 1L
            while (unitIndex < units.lastIndex && positiveBytes >= divisor * STEP) {
                unitIndex++
                divisor *= STEP
            }
            val size = inUnit(positiveBytes, divisor, units[unitIndex], rounding)
            // Rounding can reach 1,000 of a unit ("1000 kB"), which reads better as 1 of the next one.
            val roundsToNextUnit = size.whole >= STEP && unitIndex < units.lastIndex
            return if (roundsToNextUnit) inUnit(positiveBytes, divisor * STEP, units[unitIndex + 1], rounding) else size
        }

        private fun inUnit(bytes: Long, divisor: Long, unit: FileSizeUnit, rounding: SizeRounding): ShortFileSize {
            if (unit == FileSizeUnit.BYTES) return ShortFileSize(bytes, tenthsDigit = null, unit)
            if (bytes < TENTHS * divisor) {
                val tenths = divideRounded(bytes * TENTHS, divisor, rounding)
                // 9.96 rounds to 10.0, shown as "10" like every other value from 10 up.
                return if (tenths >= TENTHS * TENTHS) {
                    ShortFileSize(tenths / TENTHS, tenthsDigit = null, unit)
                } else {
                    ShortFileSize(tenths / TENTHS, tenths % TENTHS, unit)
                }
            }
            return ShortFileSize(divideRounded(bytes, divisor, rounding), tenthsDigit = null, unit)
        }

        // Whole-number arithmetic: 1.2 GB as a Double is 1.2000000000000002, which rounding up would turn into 1.3.
        private fun divideRounded(dividend: Long, divisor: Long, rounding: SizeRounding): Long = when (rounding) {
            SizeRounding.DOWN -> dividend / divisor
            SizeRounding.UP -> (dividend + divisor - 1) / divisor
            SizeRounding.NEAREST -> (dividend + divisor / 2) / divisor
        }
    }
}
