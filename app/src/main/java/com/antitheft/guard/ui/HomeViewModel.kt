package com.antitheft.guard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antitheft.guard.core.audio.AlarmPlayer
import com.antitheft.guard.domain.model.ProtectionSettings
import com.antitheft.guard.domain.repository.SettingsRepository
import com.antitheft.guard.service.GuardServiceController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

    init {
        // Settings are written first and the service follows, so every route into being armed is
        // covered by one rule: a flipped switch, an app opened after a reboot, or a microphone
        // permission granted over in system Settings while the app sat in the background.
        settingsRepository.settings
            .map { it.isArmed }
            .distinctUntilChanged()
            .filter { armed -> armed }
            .onEach { serviceController.startIfArmed() }
            .launchIn(viewModelScope)
    }

    fun setChargerAlertsEnabled(enabled: Boolean) = persist {
        settingsRepository.setChargerAlertsEnabled(enabled)
    }

    fun setMotionDetectionEnabled(enabled: Boolean) = persist {
        settingsRepository.setMotionDetectionEnabled(enabled)
    }

    fun setClapDetectionEnabled(enabled: Boolean) = persist {
        settingsRepository.setClapDetectionEnabled(enabled)
    }

    fun stopAlarm() = serviceController.stopAlarm()

    private fun persist(write: suspend () -> Unit) {
        viewModelScope.launch { write() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
