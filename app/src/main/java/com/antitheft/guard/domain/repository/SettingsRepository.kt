package com.antitheft.guard.domain.repository

import com.antitheft.guard.domain.model.ProtectionSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val settings: Flow<ProtectionSettings>

    suspend fun setChargerAlertsEnabled(enabled: Boolean)

    suspend fun setMotionDetectionEnabled(enabled: Boolean)

    /** Disarms every feature at once, which is how the ongoing notification's "Turn off" works. */
    suspend fun disableAll()
}
