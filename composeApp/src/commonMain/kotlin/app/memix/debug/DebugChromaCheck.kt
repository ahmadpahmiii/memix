package app.memix.debug

/**
 * Writes the green screen test pictures of P4-04 (`chroma-check.png`, the blue `chroma-check-blue.png` and the off-green
 * `chroma-check-offgreen.png`, all drawn by [ChromaCheckFixture]) into `debug-media/`, once: files already there are kept.
 * Returns false where it can't (iOS).
 */
internal expect suspend fun writeChromaCheckPictures(): Boolean
