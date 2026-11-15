package com.example.jkt48bonusmonitor.data.history

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single monitoring history entry persisted as JSON.
 */
@Serializable
data class HistoryEntry(
    @SerialName("exclusive_id") val exclusiveId: String,
    @SerialName("member_name") val memberName: String,
    @SerialName("available_quota") val availableQuota: Int,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("status") val status: String  // "NORMAL", "LOW", "SOLD_OUT", "ERROR"
)
