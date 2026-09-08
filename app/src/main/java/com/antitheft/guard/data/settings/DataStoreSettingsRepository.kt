package com.antitheft.guard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.antitheft.guard.core.audio.MicrophoneGate
import com.antitheft.guard.core.audio.MicrophonePermission
import com.antitheft.guard.domain.model.ClapPauseReason
import com.antitheft.guard.domain.model.ProtectionSettings
import com.antitheft.guard.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.io.IOException

private val Context.protectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "protection_settings",
)

class DataStoreSettingsRepository(
    context: Context,
    microphoneGate: MicrophoneGate,
    microphonePermission: MicrophonePermission,
) : SettingsRepository {

    private val dataStore = context.protectionDataStore

    /**
     * What the user asked for, narrowed by what the platform will currently allow. Assembling both
     * here keeps every reader — the service and the screen alike — looking at one truthful picture
     * of what is actually armed.
     */
    override val settings: Flow<ProtectionSettings> = combine(
        dataStore.data
            // A corrupt or unreadable file must not take protection down with it: fall back to the
            // disarmed defaults and let the user switch things back on.
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause },
        microphoneGate.isOpen,
        microphonePermission.isGranted,
    ) { preferences, gateOpen, permissionGranted ->
        val clapEnabled = preferences[CLAP_DETECTION] == true
        ProtectionSettings(
            chargerAlertsEnabled = preferences[CHARGER_ALERTS] == true,
            motionDetectionEnabled = preferences[MOTION_DETECTION] == true,
            clapDetectionEnabled = clapEnabled,
            clapPauseReason = clapPauseReason(clapEnabled, gateOpen, permissionGranted),
        )
    }

    /**
     * A missing permission outranks a closed gate: opening the app would clear the gate but leave
     * the user no better off, so the message that survives is the one they can act on.
     */
    private fun clapPauseReason(
        clapEnabled: Boolean,
        gateOpen: Boolean,
        permissionGranted: Boolean,
    ): ClapPauseReason = when {
        !clapEnabled -> ClapPauseReason.NONE
        !permissionGranted -> ClapPauseReason.MICROPHONE_DENIED
        !gateOpen -> ClapPauseReason.AWAITING_APP_OPEN
        else -> ClapPauseReason.NONE
    }

    override suspend fun setChargerAlertsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[CHARGER_ALERTS] = enabled }
    }

    override suspend fun setMotionDetectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[MOTION_DETECTION] = enabled }
    }

    override suspend fun setClapDetectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[CLAP_DETECTION] = enabled }
    }

    override suspend fun disableAll() {
        // Clearing rather than unsetting each key keeps this correct as features are added: every
        // feature reads back as its disarmed default.
        dataStore.edit { preferences -> preferences.clear() }
    }

    private companion object {
        val CHARGER_ALERTS = booleanPreferencesKey("charger_alerts_enabled")
        val MOTION_DETECTION = booleanPreferencesKey("motion_detection_enabled")
        val CLAP_DETECTION = booleanPreferencesKey("clap_detection_enabled")
    }
}
