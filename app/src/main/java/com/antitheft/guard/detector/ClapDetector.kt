package com.antitheft.guard.detector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
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
 * Raw PCM through `AudioRecord` rather than `MediaRecorder.getMaxAmplitude()`: MediaRecorder
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
            val recording = openClapRecording(context)
            if (recording == null) {
                close()
                return@callbackFlow
            }
            logClapConfiguration(recording.source, ClapAudioSource.unprocessedSupported(context))

            // Reading, and therefore the recorder's whole life, stays on one coroutine: nothing
            // can be released out from under a read in progress.
            val reader = launch(Dispatchers.IO) {
                val recorder = recording.recorder
                val analyzer = ClapAnalyzer()
                val readProfile = ClapReadProfile()
                val dryRun = clapDryRun
                val window = ShortArray(ClapAudioConfig.WINDOW_SAMPLES)
                try {
                    recorder.startRecording()
                    while (isActive) {
                        val read = recorder.read(window, 0, window.size)
                        if (read <= 0) continue
                        val nowMillis = SystemClock.elapsedRealtime()
                        readProfile.observe(read, nowMillis)
                        val clapped = analyzer.accept(window, read, nowMillis)
                        // A dry run stops here: the verdict is already in the log, and the alarm
                        // this would raise is the one thing a measurement must not hear.
                        if (clapped && !dryRun) trySend(GuardEvent.ClapDetected)
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                }
            }

            awaitClose { reader.cancel() }
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val NO_TYPE = 0
    }
}
