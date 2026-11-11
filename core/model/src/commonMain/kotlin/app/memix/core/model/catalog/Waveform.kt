package app.memix.core.model.catalog

/**
 * The shape of a sound: [peaks] are linear peak amplitudes from 0 to 1, loudest bucket 1, so every sound shows its own
 * shape. Their number is whatever the file holds (64 today); the player resamples them to the bars that fit.
 */
data class Waveform(val peaks: List<Float>)
