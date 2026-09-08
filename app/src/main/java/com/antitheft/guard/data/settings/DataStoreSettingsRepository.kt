package com.antitheft.guard.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.antitheft.guard.domain.model.ProtectionSettings
import com.antitheft.guard.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.protectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "protection_settings",
)

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val dataStore = context.protectionDataStore

    override val settings: Flow<ProtectionSettings> = dataStore.data
        // A corrupt or unreadable file must not take protection down with it: fall back to the
        // disarmed defaults and let the user switch things back on.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { preferences ->
            ProtectionSettings(
                chargerAlertsEnabled = preferences[CHARGER_ALERTS] == true,
            )
        }

    override suspend fun setChargerAlertsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[CHARGER_ALERTS] = enabled }
    }

    override suspend fun disableAll() {
        // Clearing rather than unsetting each key keeps this correct as features are added: every
        // feature reads back as its disarmed default.
        dataStore.edit { preferences -> preferences.clear() }
    }

    private companion object {
        val CHARGER_ALERTS = booleanPreferencesKey("charger_alerts_enabled")
    }
}
