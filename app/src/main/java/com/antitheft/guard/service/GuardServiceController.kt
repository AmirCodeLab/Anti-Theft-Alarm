package com.antitheft.guard.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.antitheft.guard.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * The single place that starts [GuardService].
 *
 * There is deliberately no stop method: settings are the source of truth, so the service watches
 * them and stops itself once nothing is armed. Callers only ever say "settings changed, take a
 * look".
 */
class GuardServiceController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun startIfArmed() {
        if (!settingsRepository.settings.first().isArmed) return
        ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
    }
}
