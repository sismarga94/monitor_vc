package com.example.jkt48bonusmonitor.data.history

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    @SerialName("exclusive_id") val exclusiveId: String,
    @SerialName("member_name") val memberName: String,
    @SerialName("available_quota") val availableQuota: Int,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("status") val status: String,           // "NORMAL", "LOW", "SOLD_OUT", "ERROR"
    @SerialName("session_code") val sessionCode: String = "",
    @SerialName("session_detail_code") val sessionDetailCode: String = "",
    @SerialName("session_date") val sessionDate: String = "",
    @SerialName("session_label") val sessionLabel: String = "",
    @SerialName("slot_label") val slotLabel: String = ""
)
