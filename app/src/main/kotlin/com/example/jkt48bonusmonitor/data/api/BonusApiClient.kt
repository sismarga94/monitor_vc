package com.example.jkt48bonusmonitor.data.api

import com.example.jkt48bonusmonitor.domain.model.ApiResult
import com.example.jkt48bonusmonitor.domain.model.MemberQuota
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://jkt48.com/api/v1/exclusives/%s/bonus?lang=id"
private const val TARGET_MEMBER = "Astrella Virgiananda"

/**
 * HTTP client for fetching bonus quota data from the JKT48 API.
 * Uses OkHttp with explicit timeouts. All network I/O must be called
 * from a background thread / coroutine.
 */
class BonusApiClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Fetches the bonus quota for [TARGET_MEMBER] from the API.
     *
     * @param exclusiveId The exclusive ID to query (e.g. "EX79CB").
     * @return [ApiResult.Success] with the matching [MemberQuota] (nullable if member not found),
     *         or [ApiResult.Error] on any failure.
     */
    fun fetchBonusQuota(exclusiveId: String): ApiResult {
        if (exclusiveId.isBlank()) {
            return ApiResult.Error("Exclusive ID must not be empty")
        }

        val url = BASE_URL.format(exclusiveId.trim())
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
            )
            .addHeader("Referer", "https://jkt48.com/")
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                return ApiResult.Error("HTTP ${response.code}: ${response.message}")
            }

            if (body.isNullOrBlank()) {
                return ApiResult.Error("Empty response body")
            }

            parseResponse(body)
        } catch (e: IOException) {
            ApiResult.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            ApiResult.Error("Unexpected error: ${e.message}")
        }
    }

    /**
     * Parses the JSON response body and finds [TARGET_MEMBER].
     * Searches all sessions and all session_members for exact name match.
     */
    fun parseResponse(jsonBody: String): ApiResult {
        return try {
            val apiResponse = json.decodeFromString<BonusApiResponse>(jsonBody)

            if (!apiResponse.status) {
                return ApiResult.Error("API returned status=false: ${apiResponse.message}")
            }

            val memberDto = apiResponse.data
                .flatMap { it.sessionMembers }
                .firstOrNull { it.memberName == TARGET_MEMBER }

            if (memberDto == null) {
                return ApiResult.Success(null)
            }

            ApiResult.Success(
                MemberQuota(
                    memberName = memberDto.memberName,
                    availableQuota = memberDto.availableQuota
                )
            )
        } catch (e: Exception) {
            ApiResult.Error("JSON parse error: ${e.message}")
        }
    }
}
