package com.example.jkt48bonusmonitor.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

object PreferencesKeys {
    val EXCLUSIVE_ID = stringPreferencesKey("exclusive_id")
    val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
    val LAST_CHECK_TIMESTAMP = stringPreferencesKey("last_check_timestamp")
    // JSON map: sessionDetailCode -> lastNotifiedQuota (Int)
    // e.g. {"EX79CB-SNBEED-SD392E": 4, "EX79CB-SNA240-SDD701": 3}
    val NOTIFIED_QUOTA_MAP = stringPreferencesKey("notified_quota_map")
}

private const val DEFAULT_EXCLUSIVE_ID = "EX79CB"

class AppPreferences(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val exclusiveIdFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[PreferencesKeys.EXCLUSIVE_ID] ?: DEFAULT_EXCLUSIVE_ID }

    val monitoringEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[PreferencesKeys.MONITORING_ENABLED] ?: false }

    val lastCheckTimestampFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[PreferencesKeys.LAST_CHECK_TIMESTAMP] }

    suspend fun getExclusiveId(): String =
        exclusiveIdFlow.first()

    suspend fun getMonitoringEnabled(): Boolean =
        monitoringEnabledFlow.first()

    /**
     * Returns the map of sessionDetailCode -> lastNotifiedQuota.
     * Used for per-slot deduplication.
     */
    suspend fun getNotifiedQuotaMap(): Map<String, Int> {
        val raw = context.dataStore.data.first()[PreferencesKeys.NOTIFIED_QUOTA_MAP]
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, Int>>(raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Saves the full deduplication map back to DataStore.
     */
    suspend fun setNotifiedQuotaMap(map: Map<String, Int>) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.NOTIFIED_QUOTA_MAP] = json.encodeToString(map)
        }
    }

    suspend fun setExclusiveId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.EXCLUSIVE_ID] = id
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.MONITORING_ENABLED] = enabled
        }
    }

    suspend fun setLastCheckTimestamp(timestamp: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_CHECK_TIMESTAMP] = timestamp
        }
    }
}
