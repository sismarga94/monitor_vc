package com.example.jkt48bonusmonitor.data.repository

import android.content.Context
import com.example.jkt48bonusmonitor.data.api.BonusApiClient
import com.example.jkt48bonusmonitor.data.datastore.AppPreferences
import com.example.jkt48bonusmonitor.data.history.HistoryEntry
import com.example.jkt48bonusmonitor.data.history.HistoryRepository
import com.example.jkt48bonusmonitor.domain.model.ApiResult
import com.example.jkt48bonusmonitor.domain.model.MemberQuota
import com.example.jkt48bonusmonitor.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TARGET_MEMBER = "Astrella Virgiananda"
private const val QUOTA_THRESHOLD = 5
private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

/**
 * Coordinates API calls, history writes, notification evaluation, and DataStore updates.
 */
class BonusMonitorRepository(
    private val appPreferences: AppPreferences,
    private val apiClient: BonusApiClient,
    private val historyRepository: HistoryRepository,
    private val notificationHelper: NotificationHelper
) {

    /**
     * Performs a full monitoring check cycle:
     * 1. Reads the current exclusive ID from DataStore.
     * 2. Calls the API.
     * 3. Updates DataStore with latest quota and timestamp.
     * 4. Evaluates notification condition with deduplication.
     * 5. Writes result to history.
     *
     * Thread-safe: network I/O is dispatched to [Dispatchers.IO].
     *
     * @return A human-readable status message for the UI.
     */
    suspend fun performCheck(): String = withContext(Dispatchers.IO) {
        val exclusiveId = appPreferences.getExclusiveId()
        val now = LocalDateTime.now().format(TIMESTAMP_FMT)

        val result = apiClient.fetchBonusQuota(exclusiveId)

        when (result) {
            is ApiResult.Error -> {
                appPreferences.setLastCheckTimestamp(now)
                "Error: ${result.message}"
            }

            is ApiResult.Success -> {
                val memberQuota: MemberQuota? = result.memberQuota
                appPreferences.setLastCheckTimestamp(now)

                if (memberQuota == null) {
                    // Member not found in this exclusive
                    appPreferences.setLatestAvailableQuota(null)
                    return@withContext "Member '$TARGET_MEMBER' not found in exclusive $exclusiveId"
                }

                val quota = memberQuota.availableQuota
                appPreferences.setLatestAvailableQuota(quota)

                // Evaluate notification with deduplication
                val lastNotifiedQuota = appPreferences.getLastNotifiedQuota()
                val notified = notificationHelper.evaluateAndNotify(quota, lastNotifiedQuota)
                if (notified) {
                    appPreferences.setLastNotifiedQuota(quota)
                }

                // Write history entry
                val status = quotaStatus(quota)
                historyRepository.addEntry(
                    HistoryEntry(
                        exclusiveId = exclusiveId,
                        memberName = TARGET_MEMBER,
                        availableQuota = quota,
                        timestamp = now,
                        status = status
                    )
                )

                "OK – quota: $quota"
            }
        }
    }

    private fun quotaStatus(quota: Int): String = when {
        quota == 0 -> "SOLD_OUT"
        quota < QUOTA_THRESHOLD -> "LOW"
        else -> "NORMAL"
    }
}
