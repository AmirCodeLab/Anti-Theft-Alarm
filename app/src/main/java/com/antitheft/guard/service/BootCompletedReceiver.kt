package com.antitheft.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antitheft.guard.core.notification.GuardNotifier
import com.antitheft.guard.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Re-arms protection after a reboot. Without this, power-cycling the phone would quietly defeat
 * the app — the one thing a thief is most likely to do.
 */
class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {

    private val controller: GuardServiceController by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val notifier: GuardNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Reading settings suspends, so hold the broadcast open until the decision is made.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = settingsRepository.settings.first()
                controller.startIfArmed()

                // Clap detection may not have come back with the rest. Say which reason it was,
                // rather than leave the user believing the phone is listening.
                settings.clapPauseReason.noticeCopy()?.let { copy ->
                    notifier.showNotice(
                        id = GuardNotifier.NOTICE_ID,
                        title = context.getString(copy.title),
                        text = context.getString(copy.text),
                        contentIntent = context.openAppIntent(),
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
