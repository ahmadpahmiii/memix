package app.memix.core.domain.voiceover

import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's microphone as the voiceover tool uses it (spec docs/ux/specs/P3-08-voiceover.md). Implemented in
 * `:engine:video` (androidMain; an iOS stub until P7-05).
 *
 * Every function is safe to call from the main thread: disk and codec work run on the recorder's own threads. The
 * recorder never asks for the microphone permission (the screen does, at the first Record tap) and has no foreground
 * service: it records only while the editor is on screen.
 */
interface VoiceRecorder {
    /** Where the project's sound goes right now, which decides whether the preview plays silent during a take. */
    fun route(): VoiceRoute

    /**
     * Opens the microphone and starts writing an AAC `.m4a` for [destination], a path relative to the app's files
     * directory (the file appears under that name only when the take finishes). Returns once the first sample has been
     * captured; the caller starts the preview after that, and [RecordedTake.timing] says when that sample was captured. A Bluetooth headset's
     * microphone is never used, whatever the route.
     */
    suspend fun open(destination: String): VoiceOpening
}

/** Where the project's sound plays while a take records (spec P3-08 → What the user hears while recording). */
enum class VoiceRoute {
    /** The phone's own speaker (or earpiece): the preview must play silent, or the speaker's sound goes into the take. */
    PHONE_SPEAKER,

    /** Wired, USB or other non-Bluetooth headphones: the project plays as usual. */
    HEADPHONES,

    /** A Bluetooth headset or earbuds: the project plays in it, the phone's microphone records, and a note says so. */
    BLUETOOTH,
}

/** What [VoiceRecorder.open] came to. */
sealed interface VoiceOpening {
    class Opened(val take: VoiceTake) : VoiceOpening

    /** Another app holds the microphone (a call, another recorder). */
    data object Busy : VoiceOpening

    /** The microphone permission is off (turned off in Settings after the screen checked it). */
    data object PermissionMissing : VoiceOpening

    /** Any other failure (no input device, encoder refused). The recorder has logged the class of the error. */
    data object Failed : VoiceOpening
}

/** One take being recorded. Main-thread API; finish or discard it exactly once. */
interface VoiceTake {
    /** The input level from 0 to 1, updated at most 20 times a second; 0 until the first sample. */
    val level: StateFlow<Float>

    /** Why the take can no longer go on (the system silenced it, the phone is full, the encoder failed); null while it can. */
    val interruption: StateFlow<TakeInterruption?>

    /** Stops the microphone, finishes the file and returns what was recorded. A take with no samples ends as zero length. Safe to call again: it returns the same result. */
    suspend fun finish(): RecordedTake

    /** Stops the microphone and deletes the unfinished file; returns at once, the recorder's thread does the work. Use it instead of [finish]; after [finish] it does nothing. */
    fun discard()
}

/**
 * When the take's first sample was captured. [firstFrameNanos] is on the same monotonic clock as [ClockSample.atNanos]
 * (Android: `System.nanoTime()`). [source] says whether the phone measured it or the recorder estimated it, which the
 * clap test needs to know (spec P3-08 → Sync). It is the phone's own delivery time of that sample, so what the
 * microphone hardware adds on top stays in the clap test's correction ([VoiceoverTuning]).
 */
data class TakeTiming(
    val firstFrameNanos: Long,
    val source: TimingSource,
    /** The time from asking the microphone to start to its first sample, µs: how long the take would have been late without [firstFrameNanos]. */
    val startDelayUs: Long,
    /** How long after capture the first samples reached the app, µs (the buffer and driver latency the timestamp removes). */
    val inputLatencyUs: Long,
)

enum class TimingSource {
    /** `AudioRecord.getTimestamp`: when the phone's capture pipeline delivered a frame, at its earliest point (the hardware's own delay is not in it). */
    DEVICE_TIMESTAMP,

    /** The time the first read returned, minus the samples read and a typical input latency; used when the phone gives no timestamp. */
    ESTIMATED,
}

/** Why a take stopped on its own. */
enum class TakeInterruption {
    /** The system silenced the microphone for another app (a call, an alarm) or took it away. */
    SILENCED,

    /** The headset or microphone the take recorded from went away. */
    ROUTE_CHANGED,

    /** The phone ran out of space; what was written is kept. */
    NO_SPACE,

    /** The encoder or the file failed. */
    FAILED,
}

/** A finished take: the file at [path] (relative to the app's files directory), or none when nothing could be written. */
data class RecordedTake(
    val path: String?,
    val durationUs: Long,
    /** When the first sample was captured, on the clock the preview's [ClockSample] uses. */
    val timing: TakeTiming,
    /** The loudest sample as a share of full scale, 0 to 1; 0 means silence. Tells a covered microphone from a take. */
    val peak: Float,
    /**
     * The encoder's start-up delay in µs (1024 samples at the file's rate), which the file carries as leading silence unless
     * its edit list removes it; 0 when the recorder knows the file has none. [placeTake] subtracts it.
     */
    val primingUs: Long = 0,
)

/** Where the preview stood at a moment: one `positionUs` and the monotonic clock's reading when it was taken. */
data class ClockSample(val positionUs: Long, val atNanos: Long)
