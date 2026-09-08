package com.antitheft.guard.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The noise an alarm makes. A single instance is shared, so the service that starts it and the
 * screen that offers to stop it are looking at the same state.
 */
class AlarmPlayer(private val context: Context) {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var player: MediaPlayer? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.getSystemService(context, VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ContextCompat.getSystemService(context, Vibrator::class.java)
    }

    /**
     * Routed at the alarm stream rather than the notification stream, so a phone left on silent
     * still shouts. An anti-theft alarm that honours the ringer switch is no alarm at all.
     */
    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Synchronized
    fun start() {
        if (_isPlaying.value) return

        val tone = alarmTone()
        if (tone == null) {
            Log.w(TAG, "No alarm or ringtone sound on this device; vibrating only.")
        } else {
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(alarmAttributes)
                    setDataSource(context, tone)
                    isLooping = true
                    prepare()
                    start()
                }
            }.onFailure { cause ->
                Log.e(TAG, "Could not play the alarm tone", cause)
            }.getOrNull()
        }

        startVibrating()
        _isPlaying.value = true
    }

    @Synchronized
    fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
        vibrator?.cancel()
        _isPlaying.value = false
    }

    /** Falls back to the ringtone because some devices ship without a default alarm sound. */
    private fun alarmTone(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /**
     * Tagged as an alarm at every API level, which is what lets the buzz through Do Not Disturb
     * and a silenced ringer.
     */
    private fun startVibrating() {
        val vibrator = vibrator ?: return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> vibrator.vibrate(
                VibrationEffect.createWaveform(PATTERN, REPEAT_FROM),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, REPEAT_FROM), alarmAttributes)
            }

            else -> {
                @Suppress("DEPRECATION")
                vibrator.vibrate(PATTERN, REPEAT_FROM, alarmAttributes)
            }
        }
    }

    private companion object {
        const val TAG = "AlarmPlayer"

        /** Wait, buzz, pause — long enough to be felt through a pocket or a bag. */
        val PATTERN = longArrayOf(0L, 700L, 400L)

        /** Repeat the whole pattern from the start, for as long as the alarm is up. */
        const val REPEAT_FROM = 0
    }
}
