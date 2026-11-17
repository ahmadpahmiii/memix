package app.memix.debug

/**
 * Writes the mask check picture of P4-03b (`mask-grid.png`, drawn by [MaskCheckFixture]) into `debug-media/`, once: a file already
 * there is kept. Returns false where it can't (iOS).
 */
internal expect suspend fun writeMaskCheckPicture(): Boolean
