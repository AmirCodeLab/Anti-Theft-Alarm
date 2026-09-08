package com.antitheft.guard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    val settings: StateFlow<ProtectionSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProtectionSettings(),
        )

    fun setChargerAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Persist first: settings are the source of truth, so the service must never be asked
            // to start before the state it reads has been written.
            settingsRepository.setChargerAlertsEnabled(enabled)
            serviceController.startIfArmed()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
