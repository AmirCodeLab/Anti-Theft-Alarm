package com.antitheft.guard.detector

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.antitheft.guard.domain.model.DetectorId
import com.antitheft.guard.domain.model.GuardEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Reports the phone being picked up or shifted.
 *
 * Deliberately the plain accelerometer rather than `TYPE_SIGNIFICANT_MOTION`: that one is a
 * one-shot trigger sensor that must be re-registered after every fire, and it is both too slow and
 * too coarse to catch a phone being lifted off a table.
 */
class MotionDetector(private val context: Context) : ThreatDetector {

    override val id = DetectorId.MOTION

    override fun events(): Flow<GuardEvent> {
        val sensors = ContextCompat.getSystemService(context, SensorManager::class.java)
            ?: return emptyFlow()
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return emptyFlow()
        val power = ContextCompat.getSystemService(context, PowerManager::class.java)
            ?: return emptyFlow()

        return callbackFlow {
            val armedAt = SystemClock.elapsedRealtime()
            var consecutiveOverThreshold = 0
            // Null until the first emission: "never fired" is not the same as "fired at time zero",
            // which would swallow the first event on a device armed seconds after booting.
            var lastEmittedAt: Long? = null

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - armedAt < ARMING_GRACE_MILLIS) return
                    val previous = lastEmittedAt
                    if (previous != null && now - previous < COOLDOWN_MILLIS) return

                    // Deviation of the acceleration magnitude from gravity. Taking the magnitude
                    // removes the need for a filter and works at any orientation, so the phone can
                    // be flat on a table or propped against something.
                    val magnitude = sqrt(
                        event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2],
                    )
                    if (abs(magnitude - SensorManager.GRAVITY_EARTH) < MOVEMENT_THRESHOLD_MS2) {
                        consecutiveOverThreshold = 0
                        return
                    }

                    consecutiveOverThreshold++
                    if (consecutiveOverThreshold < REQUIRED_CONSECUTIVE_SAMPLES) return

                    consecutiveOverThreshold = 0
                    lastEmittedAt = now
                    trySend(GuardEvent.MotionDetected)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            acquireForLifeOfFlow(wakeLock)
            sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)

            awaitClose {
                sensors.unregisterListener(listener)
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    /**
     * No timeout on purpose. The accelerometer is a non-wakeup sensor on most devices, so once the
     * CPU suspends it stops delivering events entirely — a timeout here would silently switch the
     * feature off in exactly the situation it exists for. The lock's life is bounded by the flow:
     * `awaitClose` releases it when the detector is disarmed or the service dies.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireForLifeOfFlow(wakeLock: PowerManager.WakeLock) = wakeLock.acquire()

    private companion object {
        /**
         * m/s² away from gravity. A phone lifted off a table swings several times past this;
         * resting sensor noise sits under 0.3.
         */
        const val MOVEMENT_THRESHOLD_MS2 = 2.0f

        /** At SENSOR_DELAY_UI samples land ~60 ms apart, so one stray spike is noise, two is real. */
        const val REQUIRED_CONSECUTIVE_SAMPLES = 2

        /** The user is still holding the phone in the seconds after tapping the switch. */
        const val ARMING_GRACE_MILLIS = 4_000L

        /** The alarm is already sounding; re-firing it only drains the battery. */
        const val COOLDOWN_MILLIS = 8_000L

        const val WAKE_LOCK_TAG = "guard:motion"
    }
}
