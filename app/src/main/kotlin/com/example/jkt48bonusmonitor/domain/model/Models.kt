package com.example.jkt48bonusmonitor.domain.model

/**
 * Represents a single member slot across a specific session.
 * Astrella can appear in multiple sessions — each is a separate MemberSlot.
 */
data class MemberSlot(
    val memberName: String,
    val availableQuota: Int,
    val sessionCode: String,       // e.g. "EX79CB-SNBEED"
    val sessionDetailCode: String, // e.g. "EX79CB-SNBEED-SD392E" — unique per slot
    val sessionDate: String,       // e.g. "2026-11-22"
    val sessionLabel: String,      // e.g. "Sesi 1"
    val sessionStartTime: String,  // e.g. "11:45:00"
    val slotLabel: String          // e.g. "Jalur 4"
)

/**
 * Sealed result of an API fetch operation.
 */
sealed class ApiResult {
    /** All slots found for the target member. Empty list = member not in this exclusive. */
    data class Success(val slots: List<MemberSlot>) : ApiResult()
    data class Error(val message: String) : ApiResult()
}

/**
 * Status of the monitoring system.
 */
enum class MonitoringStatus {
    ACTIVE,
    STOPPED
}

/**
 * Overall application state surfaced to the UI.
 */
data class AppState(
    val exclusiveId: String = "EX79CB",
    val monitoringStatus: MonitoringStatus = MonitoringStatus.STOPPED,
    val lastCheckTimestamp: String? = null,
    val slots: List<MemberSlot> = emptyList(),
    val lastRequestStatus: String? = null,
    val isLoading: Boolean = false
)
