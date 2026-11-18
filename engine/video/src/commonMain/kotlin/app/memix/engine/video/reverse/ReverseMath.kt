package app.memix.engine.video.reverse

/**
 * The arithmetic of making a reversed copy (P4-10), kept apart from the codecs so it can be checked on its own: how the frames of an all-intra file are
 * laid out backward in time, how the audio is turned around, and how the file is read in blocks that fit in memory.
 */
internal object ReverseMath {
    /**
     * The presentation time of each frame of the reversed file. A frame shown over [start, end) in the forward file is shown over [span - end, span - start) in the
     * reversed one, so frame i starts at `span - end(i)`, where end(i) is the next frame's start (the last frame ends at [spanUs]). Writing the frames last to first
     * gives ascending times. [startsUs] must be ascending.
     */
    fun reversedStartsUs(startsUs: LongArray, spanUs: Long): LongArray =
        LongArray(startsUs.size) { i -> spanUs - (if (i + 1 < startsUs.size) startsUs[i + 1] else spanUs) }

    /** How long the video lasts, µs: the last frame's start plus the gap before it (a one-frame file lasts [fallbackFrameUs]). */
    fun spanOf(startsUs: LongArray, fallbackFrameUs: Long): Long = when {
        startsUs.isEmpty() -> 0L
        startsUs.size == 1 -> startsUs[0] + fallbackFrameUs
        else -> startsUs.last() + (startsUs.last() - startsUs[startsUs.size - 2])
    }

    /** Turns interleaved 16-bit [channels]-channel [pcm] around in place, frame by frame (a stereo pair keeps its left and right). Only the first [frameCount] frames count. */
    fun reverseFrames(pcm: ShortArray, channels: Int, frameCount: Int) {
        var low = 0
        var high = frameCount - 1
        while (low < high) {
            for (c in 0 until channels) {
                val a = low * channels + c
                val b = high * channels + c
                val held = pcm[a]
                pcm[a] = pcm[b]
                pcm[b] = held
            }
            low++
            high--
        }
    }

    /**
     * Splits the frames of a file of [sizes] bytes into blocks from the END backward, each at most [maxBytes] (a frame bigger than that is a block of its own), so
     * the file can be read one block at a time and written in reverse. Blocks are returned last first; each is an index range into [sizes].
     */
    fun blocksFromEnd(sizes: IntArray, maxBytes: Long): List<IntRange> {
        val blocks = mutableListOf<IntRange>()
        var end = sizes.size - 1
        while (end >= 0) {
            var start = end
            var bytes = sizes[end].toLong()
            while (start > 0 && bytes + sizes[start - 1] <= maxBytes) {
                start--
                bytes += sizes[start]
            }
            blocks += start..end
            end = start - 1
        }
        return blocks
    }
}
