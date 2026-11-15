package app.memix.engine.video

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import app.memix.core.domain.Logger
import app.memix.core.domain.media.MediaPaths
import app.memix.core.domain.voiceover.VoiceOpening
import app.memix.core.domain.voiceover.VoiceRecorder
import app.memix.core.domain.voiceover.VoiceRoute
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Android [VoiceRecorder] (P3-08): [AudioRecord] on the phone's microphone into an AAC `.m4a` ([VoiceCapture]). It uses
 * the plain microphone source, which keeps the phone's normal path (no voice-call processing, no Bluetooth call route), and
 * pins the built-in microphone whenever a Bluetooth headset is connected, so a headset's low-quality, delayed microphone is
 * never used (spec P3-08 → Which microphone). It runs only while the editor is on screen, so it needs no foreground service.
 */
internal class AndroidVoiceRecorder(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    /** Hand-check builds: extra log lines (dropped frames). */
    private val logDetails: Boolean = false,
) : VoiceRecorder {
    private val audioManager: AudioManager get() = context.getSystemService(AudioManager::class.java)

    override fun route(): VoiceRoute {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        return when {
            outputs.any { it in WIRED_TYPES } -> VoiceRoute.HEADPHONES
            outputs.any(::isBluetooth) -> VoiceRoute.BLUETOOTH
            else -> VoiceRoute.PHONE_SPEAKER
        }
    }

    override suspend fun open(destination: String): VoiceOpening = withContext(ioDispatcher) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@withContext VoiceOpening.PermissionMissing
        val input = try {
            SAMPLE_RATES.firstNotNullOfOrNull(::newInput)
        } catch (e: SecurityException) {
            // The grant was revoked between the check above and here.
            return@withContext VoiceOpening.PermissionMissing
        } ?: return@withContext VoiceOpening.Failed
        pinBuiltInMicrophoneForBluetooth(input.record)
        openCapture(input.record, input.sampleRate, destination)
    }

    private suspend fun openCapture(record: AudioRecord, sampleRate: Int, destination: String): VoiceOpening {
        val finalFile = File(context.filesDir, destination)
        val partFile = File(finalFile.path + MediaPaths.UNFINISHED_SUFFIX)
        val writer = try {
            finalFile.parentFile?.mkdirs()
            AacM4aWriter(partFile, sampleRate)
        } catch (e: java.io.IOException) {
            record.release()
            return VoiceOpening.Failed
        } catch (e: RuntimeException) {
            logger.error(VoiceCapture.TAG, "No AAC encoder: ${e::class.simpleName}")
            record.release()
            return VoiceOpening.Failed
        }
        val askedAtNanos = System.nanoTime()
        val started = try {
            record.startRecording()
            record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (e: IllegalStateException) {
            false
        }
        if (!started) {
            writer.abort()
            record.release()
            return VoiceOpening.Busy
        }
        val capture = VoiceCapture(record, sampleRate, writer, partFile, finalFile, destination, audioManager, askedAtNanos, logger, logDetails)
        // Every exit but Opened (no sample in time, a cancelled caller) lets the microphone go; nothing else holds the take.
        var opened = false
        try {
            val heard = withTimeoutOrNull(FIRST_SAMPLE_TIMEOUT_MS) { capture.start() }
            if (heard != true) return VoiceOpening.Busy
            opened = true
            return VoiceOpening.Opened(capture)
        } finally {
            if (!opened) capture.discard()
        }
    }

    // Permission is checked in open(); a grant revoked since then throws a SecurityException, which open() reports as a missing permission.
    @SuppressLint("MissingPermission")
    private fun newInput(sampleRate: Int): MicrophoneInput? {
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return null
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        return try {
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBytes, sampleRate * BYTES_PER_SAMPLE * BUFFER_SECONDS))
                .build()
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                MicrophoneInput(record, sampleRate)
            } else {
                record.release()
                null
            }
        } catch (e: UnsupportedOperationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun pinBuiltInMicrophoneForBluetooth(record: AudioRecord) {
        if (route() != VoiceRoute.BLUETOOTH) return
        val builtIn = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        // If the system refuses, the take may be recorded from the headset's call route; the log line is how a phone check finds out.
        if (builtIn == null || !record.setPreferredDevice(builtIn)) logger.debug(VoiceCapture.TAG, "The built-in microphone could not be pinned while Bluetooth is connected")
    }

    private class MicrophoneInput(val record: AudioRecord, val sampleRate: Int)

    private fun isBluetooth(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_HEARING_AID ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    private companion object {
        // 48 kHz is the native rate of nearly every phone (the fast path); 44.1 kHz is the one rate every phone must support.
        val SAMPLE_RATES = listOf(48_000, 44_100)
        val WIRED_TYPES = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)

        // One second of audio: a slow encoder step or muxer write on the capture thread cannot overrun it.
        const val BUFFER_SECONDS = 1
        const val BYTES_PER_SAMPLE = 2
        const val FIRST_SAMPLE_TIMEOUT_MS = 2_000L

    }
}
