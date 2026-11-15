package com.example.jkt48bonusmonitor.data.repository

import com.example.jkt48bonusmonitor.data.api.BonusApiClient
import com.example.jkt48bonusmonitor.data.datastore.AppPreferences
import com.example.jkt48bonusmonitor.data.history.HistoryEntry
import com.example.jkt48bonusmonitor.data.history.HistoryRepository
import com.example.jkt48bonusmonitor.domain.model.ApiResult
import com.example.jkt48bonusmonitor.domain.model.MemberSlot
import com.example.jkt48bonusmonitor.notification.NotificationHelper
import com.example.jkt48bonusmonitor.notification.QUOTA_THRESHOLD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

class BonusMonitorRepository(
    private val appPreferences: AppPreferences,
    private val apiClient: BonusApiClient,
    private val historyRepository: HistoryRepository,
    private val notificationHelper: NotificationHelper
) {

    /**
     * Full check cycle:
     * 1. Fetches all Astrella slots from the API.
     * 2. Evaluates per-slot notification with deduplication.
     * 3. Writes one history entry per slot.
     * 4. Updates DataStore.
     *
     * @return Human-readable status for the UI.
     */
    suspend fun performCheck(): CheckResult = withContext(Dispatchers.IO) {
        val exclusiveId = appPreferences.getExclusiveId()
        val now = LocalDateTime.now().format(TIMESTAMP_FMT)

        when (val result = apiClient.fetchBonusQuota(exclusiveId)) {
            is ApiResult.Error -> {
                appPreferences.setLastCheckTimestamp(now)
                CheckResult(slots = emptyList(), statusMessage = "Error: ${result.message}")
            }

            is ApiResult.Success -> {
                appPreferences.setLastCheckTimestamp(now)
                val slots = result.slots

                if (slots.isEmpty()) {
                    return@withContext CheckResult(
                        slots = emptyList(),
                        statusMessage = "Astrella not in exclusive $exclusiveId"
                    )
                }

                // Per-slot notification deduplication
                val notifiedMap = appPreferences.getNotifiedQuotaMap()
                val updatedMap = notificationHelper.evaluateAndNotify(slots, notifiedMap)
                if (updatedMap != notifiedMap) {
                    appPreferences.setNotifiedQuotaMap(updatedMap)
                }

                // Write one history entry per slot
                slots.forEach { slot ->
                    historyRepository.addEntry(
                        HistoryEntry(
                            exclusiveId = exclusiveId,
                            memberName = slot.memberName,
                            availableQuota = slot.availableQuota,
                            timestamp = now,
                            status = slot.quotaStatus(),
                            sessionCode = slot.sessionCode,
                            sessionDetailCode = slot.sessionDetailCode,
                            sessionDate = slot.sessionDate,
                            sessionLabel = slot.sessionLabel,
                            slotLabel = slot.slotLabel
                        )
                    )
                }

                val lowCount = slots.count { it.availableQuota < QUOTA_THRESHOLD }
                val statusMsg = if (lowCount > 0) {
                    "⚠ $lowCount slot(s) low — checked ${slots.size} slots"
                } else {
                    "OK — ${slots.size} slot(s) checked, all normal"
                }

                CheckResult(slots = slots, statusMessage = statusMsg)
            }
        }
    }
}

data class CheckResult(
    val slots: List<MemberSlot>,
    val statusMessage: String
)

private fun MemberSlot.quotaStatus(): String = when {
    availableQuota == 0 -> "SOLD_OUT"
    availableQuota < QUOTA_THRESHOLD -> "LOW"
    else -> "NORMAL"
}
