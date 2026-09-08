package com.antitheft.guard.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.antitheft.guard.R
import com.antitheft.guard.core.audio.AlarmPlayer
import com.antitheft.guard.core.notification.GuardNotifier
import com.antitheft.guard.detector.ThreatDetector
import com.antitheft.guard.domain.model.DetectorId
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
        startInForeground()
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

    private fun startInForeground() {
        ServiceCompat.startForeground(
            this,
            GuardNotifier.ONGOING_ID,
            notifier.ongoingNotification(
                text = getString(R.string.notification_status_starting),
                contentIntent = openAppIntent(),
                turnOffIntent = disarmIntent(),
            ),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun observeSettings() {
        settingsRepository.settings
            .onEach { settings ->
                if (settings.isArmed) {
                    notifier.showOngoing(
                        notifier.ongoingNotification(
                            text = statusText(settings),
                            contentIntent = openAppIntent(),
                            turnOffIntent = disarmIntent(),
                        ),
                    )
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

    private fun handleEvent(event: GuardEvent) {
        val copy = event.alertCopy()
        val id = ALERT_ID_BASE + event.source.ordinal
        when (event) {
            GuardEvent.MotionDetected -> {
                alarmPlayer.start()
                notifier.showAlarmAlert(
                    id = id,
                    title = getString(copy.title),
                    text = getString(copy.text),
                    contentIntent = openAppIntent(),
                    stopLabel = getString(R.string.notification_action_stop_alarm),
                    stopIntent = stopAlarmIntent(),
                )
            }

            GuardEvent.ChargerConnected, GuardEvent.ChargerDisconnected -> notifier.showAlert(
                id = id,
                title = getString(copy.title),
                text = getString(copy.text),
                contentIntent = openAppIntent(),
            )
        }
    }

    /** Takes the alert down with the noise, so the stop control never outlives the alarm. */
    private fun silenceAlarm() {
        alarmPlayer.stop()
        notifier.cancel(ALERT_ID_BASE + DetectorId.MOTION.ordinal)
    }

    private companion object {
        const val ALERT_ID_BASE = 100
    }
}
