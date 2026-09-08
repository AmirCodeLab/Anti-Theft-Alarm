package com.antitheft.guard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antitheft.guard.core.audio.AlarmPlayer
import com.antitheft.guard.domain.model.ProtectionSettings
import com.antitheft.guard.domain.repository.SettingsRepository
import com.antitheft.guard.service.GuardServiceController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val serviceController: GuardServiceController,
    alarmPlayer: AlarmPlayer,
) : ViewModel() {

    val settings: StateFlow<ProtectionSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProtectionSettings(),
        )

    val isAlarmPlaying: StateFlow<Boolean> = alarmPlayer.isPlaying

    fun setChargerAlertsEnabled(enabled: Boolean) = arm {
        settingsRepository.setChargerAlertsEnabled(enabled)
    }

    fun setMotionDetectionEnabled(enabled: Boolean) = arm {
        settingsRepository.setMotionDetectionEnabled(enabled)
    }

    fun stopAlarm() = serviceController.stopAlarm()

    private fun arm(persist: suspend () -> Unit) {
        viewModelScope.launch {
            // Persist first: settings are the source of truth, so the service must never be asked
            // to start before the state it reads has been written.
            persist()
            serviceController.startIfArmed()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
