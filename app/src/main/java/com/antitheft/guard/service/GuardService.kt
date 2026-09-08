package com.antitheft.guard.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.antitheft.guard.R
import com.antitheft.guard.core.notification.GuardNotifier
import com.antitheft.guard.detector.ThreatDetector
import com.antitheft.guard.domain.model.DetectorId
import com.antitheft.guard.domain.model.GuardEvent
import com.antitheft.guard.domain.model.ProtectionSettings
import com.antitheft.guard.domain.repository.SettingsRepository
import com.antitheft.guard.ui.MainActivity
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
    private val detectors: List<ThreatDetector> by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        observeSettings()
        observeThreats()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISARM) {
            // Turning everything off is the only command this service takes; the settings collector
            // below then sees an empty armed set and stops it.
            scope.launch { settingsRepository.disableAll() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        ServiceCompat.startForeground(
            this,
            GuardNotifier.ONGOING_ID,
            notifier.ongoingNotification(getString(R.string.notification_status_starting), openApp(), disarm()),
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
                    notifier.showOngoing(notifier.ongoingNotification(statusText(settings), openApp(), disarm()))
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
        notifier.showAlert(
            id = ALERT_ID_BASE + event.source.ordinal,
            title = getString(copy.title),
            text = getString(copy.text),
            contentIntent = openApp(),
        )
    }

    /** Names every armed feature, so the notice always says what is actually being watched. */
    private fun statusText(settings: ProtectionSettings): String = settings.armedDetectors
        .mapNotNull { detector ->
            when (detector) {
                DetectorId.CHARGING -> getString(R.string.status_watching_charger)
                // Not implemented yet, so they can never be armed and never reach this list.
                DetectorId.MOTION, DetectorId.CLAP -> null
            }
        }
        .joinToString(separator = getString(R.string.status_separator))

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun disarm(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, GuardService::class.java).setAction(ACTION_DISARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val ACTION_DISARM = "com.antitheft.guard.action.DISARM"
        const val ALERT_ID_BASE = 100
    }
}
