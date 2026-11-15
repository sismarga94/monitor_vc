package com.example.jkt48bonusmonitor.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

/**
 * Keys for all persisted preferences.
 */
object PreferencesKeys {
    val EXCLUSIVE_ID = stringPreferencesKey("exclusive_id")
    val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
    val PREVIOUS_AVAILABLE_QUOTA = intPreferencesKey("previous_available_quota")
    val LAST_NOTIFIED_QUOTA = intPreferencesKey("last_notified_quota")
    val LAST_CHECK_TIMESTAMP = stringPreferencesKey("last_check_timestamp")
    val LATEST_AVAILABLE_QUOTA = intPreferencesKey("latest_available_quota")
}

private const val DEFAULT_EXCLUSIVE_ID = "EX79CB"
private const val QUOTA_UNSET = -999

/**
 * Abstraction over DataStore Preferences for all app settings.
 */
class AppPreferences(private val context: Context) {

    val exclusiveIdFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[PreferencesKeys.EXCLUSIVE_ID] ?: DEFAULT_EXCLUSIVE_ID }

    val monitoringEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[PreferencesKeys.MONITORING_ENABLED] ?: false }

    val lastCheckTimestampFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[PreferencesKeys.LAST_CHECK_TIMESTAMP] }

    val latestAvailableQuotaFlow: Flow<Int?> = context.dataStore.data
        .map { prefs ->
            val v = prefs[PreferencesKeys.LATEST_AVAILABLE_QUOTA]
            if (v == null || v == QUOTA_UNSET) null else v
        }

    suspend fun getExclusiveId(): String =
        exclusiveIdFlow.first()

    suspend fun getMonitoringEnabled(): Boolean =
        monitoringEnabledFlow.first()

    suspend fun getPreviousAvailableQuota(): Int? {
        val v = context.dataStore.data.first()[PreferencesKeys.PREVIOUS_AVAILABLE_QUOTA]
        return if (v == null || v == QUOTA_UNSET) null else v
    }

    suspend fun getLastNotifiedQuota(): Int? {
        val v = context.dataStore.data.first()[PreferencesKeys.LAST_NOTIFIED_QUOTA]
        return if (v == null || v == QUOTA_UNSET) null else v
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

    suspend fun setPreviousAvailableQuota(quota: Int?) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PREVIOUS_AVAILABLE_QUOTA] = quota ?: QUOTA_UNSET
        }
    }

    suspend fun setLastNotifiedQuota(quota: Int?) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_NOTIFIED_QUOTA] = quota ?: QUOTA_UNSET
        }
    }

    suspend fun setLastCheckTimestamp(timestamp: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_CHECK_TIMESTAMP] = timestamp
        }
    }

    suspend fun setLatestAvailableQuota(quota: Int?) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LATEST_AVAILABLE_QUOTA] = quota ?: QUOTA_UNSET
        }
    }
}
