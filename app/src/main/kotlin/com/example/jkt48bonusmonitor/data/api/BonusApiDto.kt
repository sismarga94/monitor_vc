package com.example.jkt48bonusmonitor.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root API response.
 * Actual structure confirmed:
 * {
 *   "status": true,
 *   "message": "...",
 *   "data": [ { "session_members": [ { "member_name": "...", "available_quota": 5 } ] } ]
 * }
 */
@Serializable
data class BonusApiResponse(
    @SerialName("status") val status: Boolean = false,
    @SerialName("message") val message: String = "",
    @SerialName("data") val data: List<SessionDto> = emptyList()
)

@Serializable
data class SessionDto(
    @SerialName("exclusive_session_code") val exclusiveSessionCode: String = "",
    @SerialName("date") val date: String = "",
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
    @SerialName("label") val label: String = "",
    @SerialName("session_members") val sessionMembers: List<SessionMemberDto> = emptyList()
)

@Serializable
data class SessionMemberDto(
    @SerialName("label") val label: String = "",
    @SerialName("price") val price: Int = 0,
    @SerialName("member_name") val memberName: String = "",
    @SerialName("session_detail_code") val sessionDetailCode: String = "",
    @SerialName("available_quota") val availableQuota: Int = 0
)
