package com.example.jkt48bonusmonitor.domain.model

/**
 * Represents a single member's bonus quota data parsed from the API.
 */
data class MemberQuota(
    val memberName: String,
    val availableQuota: Int
)

/**
 * Sealed result of an API fetch operation.
 */
sealed class ApiResult {
    data class Success(val memberQuota: MemberQuota?) : ApiResult()
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
    val latestAvailableQuota: Int? = null,
    val lastRequestStatus: String? = null,
    val isLoading: Boolean = false
)
