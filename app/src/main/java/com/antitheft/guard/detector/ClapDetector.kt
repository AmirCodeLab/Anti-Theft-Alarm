package com.antitheft.guard.detector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.antitheft.guard.domain.model.DetectorId
import com.antitheft.guard.domain.model.GuardEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Listens for a clap.
 *
 * Raw PCM through [AudioRecord] rather than `MediaRecorder.getMaxAmplitude()`: MediaRecorder
 * demands an output file just to hand back an amplitude, and gives no say over the window size
 * this needs.
 *
 * The audio is never written to disk, never buffered beyond the window being measured, and never
 * leaves the process. Each window is reduced to a single RMS number and then overwritten by the
 * next read; nothing recognisable as speech survives the loop.
 */
class ClapDetector(private val context: Context) : ThreatDetector {

    override val id = DetectorId.CLAP

    override val foregroundServiceType: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            NO_TYPE
        }

    override fun events(): Flow<GuardEvent> {
        if (!hasMicrophonePermission()) return emptyFlow()

        return callbackFlow {
            val recorder = openRecorder()
            if (recorder == null) {
                close()
                return@callbackFlow
            }

            // Reading, and therefore the recorder's whole life, stays on one coroutine: nothing
            // can be released out from under a read in progress.
            val reader = launch(Dispatchers.IO) {
                val analyzer = ClapAnalyzer()
                val window = ShortArray(WINDOW_SAMPLES)
                try {
                    recorder.startRecording()
                    while (isActive) {
                        val read = recorder.read(window, 0, window.size)
                        if (read <= 0) continue
                        if (analyzer.accept(window, read, SystemClock.elapsedRealtime())) {
                            trySend(GuardEvent.ClapDetected)
                        }
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                }
            }

            awaitClose { reader.cancel() }
        }
    }

    private fun openRecorder(): AudioRecord? {
        // Re-checked at the point of use, not just on the way in: the user can revoke the
        // microphone from system settings while a detector is running.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL, ENCODING)
        if (minBuffer <= 0) {
            Log.w(TAG, "This device will not record at ${SAMPLE_RATE_HZ}Hz mono.")
            return null
        }

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                CHANNEL,
                ENCODING,
                maxOf(minBuffer, WINDOW_SAMPLES * BYTES_PER_SAMPLE),
            )
        }.getOrNull() ?: return null

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            // Another app holds the microphone, or the device refused the configuration.
            recorder.release()
            return null
        }
        return recorder
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "ClapDetector"

        const val NO_TYPE = 0

        /** Speech and claps live well below 8 kHz, so 16 kHz costs half the samples for no loss. */
        const val SAMPLE_RATE_HZ = 16_000

        /** 512 samples is 32 ms at 16 kHz — inside the 20–50 ms window a clap's attack fits. */
        const val WINDOW_SAMPLES = 512

        const val BYTES_PER_SAMPLE = 2
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}

/**
 * Single switch for the clap telemetry: set to false and every line below goes away. Debug builds
 * are gated separately in [ClapAnalyzer], so a release build never logs regardless of this value.
 */
internal const val CLAP_TELEMETRY_ENABLED = true

/** One tag for every telemetry line, so a single logcat filter catches all of them. */
internal const val CLAP_TELEMETRY_TAG = "ClapTelemetry"
