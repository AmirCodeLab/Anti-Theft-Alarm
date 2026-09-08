package com.antitheft.guard.detector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.antitheft.guard.BuildConfig
import kotlin.math.max

/**
 * Which microphone feed the clap path listens to.
 *
 * [MIC] is what the detector ships with. The other two exist because the platform's capture chain
 * is not neutral: automatic gain control lifts the floor while a room is quiet and compresses
 * transients when it is not, which squeezes a clap's rise ratio from both ends. Measuring the same
 * sound on all three is the only way to tell how much of what the telemetry prints is the room and
 * how much is the device.
 */
internal enum class ClapAudioSource(val key: String, val value: Int) {
    MIC("mic", MediaRecorder.AudioSource.MIC),
    VOICE_RECOGNITION("voice_recognition", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    UNPROCESSED("unprocessed", MediaRecorder.AudioSource.UNPROCESSED),
    ;

    companion object {

        /** `adb shell settings put global guard_clap_audio_source unprocessed` */
        const val OVERRIDE_SETTING = "guard_clap_audio_source"

        /**
         * Debug builds take the source from [OVERRIDE_SETTING], so all three can be compared on
         * one install. A setting rather than a build constant, because a rebuild between takes
         * changes the room as well as the source. Release builds ignore it and record from [MIC].
         */
        fun selected(context: Context): ClapAudioSource {
            if (!BuildConfig.DEBUG) return MIC
            val requested = runCatching {
                Settings.Global.getString(context.contentResolver, OVERRIDE_SETTING)
            }.getOrNull() ?: return MIC
            return entries.firstOrNull { it.key == requested.trim().lowercase() } ?: MIC
        }

        /**
         * Whether the device promises that UNPROCESSED really is unprocessed. When this is false
         * the platform is free to hand back the same processed feed as [MIC], and a measurement
         * taken on it says nothing about what AGC is doing.
         */
        fun unprocessedSupported(context: Context): Boolean =
            context.getSystemService(AudioManager::class.java)
                ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
                .toBoolean()
    }
}

/** A recorder together with the source it actually opened, which may not be the one asked for. */
internal class ClapRecording(val recorder: AudioRecord, val source: ClapAudioSource)

/**
 * Opens the microphone, falling back to [ClapAudioSource.MIC] when the requested source is refused
 * so an unavailable override degrades to a measurement rather than to no detector at all.
 */
internal fun openClapRecording(context: Context): ClapRecording? {
    // Re-checked at the point of use, not just on the way in: the user can revoke the microphone
    // from system settings while a detector is running.
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }

    val minBuffer = AudioRecord.getMinBufferSize(
        ClapAudioConfig.SAMPLE_RATE_HZ,
        ClapAudioConfig.CHANNEL,
        ClapAudioConfig.ENCODING,
    )
    if (minBuffer <= 0) {
        Log.w(TAG, "This device will not record at ${ClapAudioConfig.SAMPLE_RATE_HZ}Hz mono.")
        return null
    }

    val requested = ClapAudioSource.selected(context)
    open(requested, minBuffer)?.let { return ClapRecording(it, requested) }
    if (requested == ClapAudioSource.MIC) return null

    Log.w(TAG, "$requested is unavailable on this device; recording from MIC instead.")
    return open(ClapAudioSource.MIC, minBuffer)?.let { ClapRecording(it, ClapAudioSource.MIC) }
}

private fun open(source: ClapAudioSource, minBuffer: Int): AudioRecord? {
    val recorder = runCatching {
        AudioRecord(
            source.value,
            ClapAudioConfig.SAMPLE_RATE_HZ,
            ClapAudioConfig.CHANNEL,
            ClapAudioConfig.ENCODING,
            max(minBuffer, ClapAudioConfig.WINDOW_SAMPLES * ClapAudioConfig.BYTES_PER_SAMPLE),
        )
    }.getOrNull() ?: return null

    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        // Another app holds the microphone, or the device refused the configuration.
        recorder.release()
        return null
    }
    return recorder
}

private const val TAG = "ClapRecorder"
