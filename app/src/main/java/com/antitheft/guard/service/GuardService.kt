package com.antitheft.guard.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.antitheft.guard.R
import com.antitheft.guard.core.audio.AlarmPlayer
import com.antitheft.guard.core.notification.GuardNotifier
import com.antitheft.guard.detector.ThreatDetector
import com.antitheft.guard.domain.model.GuardEvent
import com.antitheft.guard.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The only component that reacts to events. It merges the flows of the armed detectors and
 * re-merges them whenever the armed set changes, which cancels a switched-off detector's flow and
 * so releases its hardware.
 */
class GuardService : Service() {

    private val settingsRepository: SettingsRepository by inject()
    private val notifier: GuardNotifier by inject()
    private val alarmPlayer: AlarmPlayer by inject()
    private val detectors: List<ThreatDetector> by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        enterForeground(getString(R.string.notification_status_starting), NO_DETECTOR_TYPES)
        observeSettings()
        observeThreats()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Silencing an alarm is not the same intent as giving up on protection: a single false
            // alarm must not leave the phone unwatched for the rest of the night.
            ACTION_STOP_ALARM -> silenceAlarm()
            ACTION_DISARM -> scope.launch { settingsRepository.disableAll() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        silenceAlarm()
        scope.cancel()
        super.onDestroy()
    }

    private fun observeSettings() {
        settingsRepository.settings
            .onEach { settings ->
                if (settings.isArmed) {
                    enterForeground(statusText(settings), detectors.typesRequiredBy(settings.armedDetectors))
                } else {
                    stopSelf()
                }
            }
            .launchIn(scope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeThreats() {
        settingsRepository.settings
            .map { it.armedDetectors }
            .distinctUntilChanged()
            .flatMapLatest { armed -> detectors.filter { it.id in armed }.map { it.events() }.merge() }
            .onEach(::handleEvent)
            .launchIn(scope)
    }

    /**
     * Re-declared on every settings change so the capabilities claimed always match what is armed.
     * Android takes a dim view of a service that holds the microphone type while nothing is
     * listening, and rightly so.
     */
    private fun enterForeground(text: String, detectorTypes: Int) {
        ServiceCompat.startForeground(
            this,
            GuardNotifier.ONGOING_ID,
            notifier.ongoingNotification(
                text = text,
                contentIntent = openAppIntent(),
                turnOffIntent = disarmIntent(),
            ),
            foregroundTypeMask(detectorTypes),
        )
    }

    private fun handleEvent(event: GuardEvent) {
        val copy = event.alertCopy()
        when (event) {
            GuardEvent.MotionDetected, GuardEvent.ClapDetected -> {
                alarmPlayer.start()
                notifier.showAlarmAlert(
                    id = ALARM_ALERT_ID,
                    title = getString(copy.title),
                    text = getString(copy.text),
                    contentIntent = openAppIntent(),
                    stopLabel = getString(R.string.notification_action_stop_alarm),
                    stopIntent = stopAlarmIntent(),
                )
            }

            GuardEvent.ChargerConnected, GuardEvent.ChargerDisconnected -> notifier.showAlert(
                id = PASSIVE_ALERT_ID_BASE + event.source.ordinal,
                title = getString(copy.title),
                text = getString(copy.text),
                contentIntent = openAppIntent(),
            )
        }
    }

    /** Takes the alert down with the noise, so the stop control never outlives the alarm. */
    private fun silenceAlarm() {
        alarmPlayer.stop()
        notifier.cancel(ALARM_ALERT_ID)
    }

    private companion object {
        /** One alarm can sound at a time, so one notification carries its stop control. */
        const val ALARM_ALERT_ID = 100

        const val PASSIVE_ALERT_ID_BASE = 200
    }
}
