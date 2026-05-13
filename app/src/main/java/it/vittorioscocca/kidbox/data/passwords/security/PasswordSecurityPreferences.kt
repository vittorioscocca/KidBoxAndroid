package it.vittorioscocca.kidbox.data.passwords.security

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PasswordSecurityPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("password_security.preferences_pb") },
    )

    val weeklyScanEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_WEEKLY_SCAN_ENABLED] ?: true }
    val pushAlertsEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_PUSH_ALERTS_ENABLED] ?: true }
    val lastScanAt: Flow<Long?> = dataStore.data.map { it[KEY_LAST_SCAN_AT] }
    val previousCompromisedIds: Flow<Set<String>> = dataStore.data.map { it[KEY_PREVIOUS_COMPROMISED_IDS] ?: emptySet() }

    suspend fun setWeeklyScanEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WEEKLY_SCAN_ENABLED] = enabled }
    }

    suspend fun setPushAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PUSH_ALERTS_ENABLED] = enabled }
    }

    suspend fun setLastScanAt(epochMillis: Long) {
        dataStore.edit { it[KEY_LAST_SCAN_AT] = epochMillis }
    }

    suspend fun setPreviousCompromisedIds(ids: Set<String>) {
        dataStore.edit { it[KEY_PREVIOUS_COMPROMISED_IDS] = ids }
    }

    companion object {
        private val KEY_WEEKLY_SCAN_ENABLED = booleanPreferencesKey("weekly_scan_enabled")
        private val KEY_PUSH_ALERTS_ENABLED = booleanPreferencesKey("push_alerts_enabled")
        private val KEY_LAST_SCAN_AT = longPreferencesKey("last_scan_at")
        private val KEY_PREVIOUS_COMPROMISED_IDS = stringSetPreferencesKey("previous_compromised_ids")
    }
}
