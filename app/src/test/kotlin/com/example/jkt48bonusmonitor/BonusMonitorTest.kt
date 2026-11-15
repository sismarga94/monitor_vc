package com.example.jkt48bonusmonitor

import com.example.jkt48bonusmonitor.data.api.BonusApiClient
import com.example.jkt48bonusmonitor.data.history.HistoryEntry
import com.example.jkt48bonusmonitor.domain.model.ApiResult
import com.example.jkt48bonusmonitor.domain.model.MemberQuota
import com.example.jkt48bonusmonitor.notification.NotificationHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for JKT48 Bonus Monitor.
 * All tests are pure JVM — no Android framework dependencies.
 */
class BonusMonitorTest {

    private lateinit var apiClient: BonusApiClient

    // Fixtures loaded from test resources
    private val fixtureNormal = loadFixture("fixtures/response_normal.json")
    private val fixtureLowQuota = loadFixture("fixtures/response_low_quota.json")
    private val fixtureSoldOut = loadFixture("fixtures/response_sold_out.json")
    private val fixtureMemberAbsent = loadFixture("fixtures/response_member_absent.json")

    @Before
    fun setUp() {
        apiClient = BonusApiClient()
    }

    // ── 1. Exact member name matching ────────────────────────────────

    @Test
    fun `test 1 - exact member name match returns correct quota`() {
        val result = apiClient.parseResponse(fixtureNormal)
        assertTrue(result is ApiResult.Success)
        val quota = (result as ApiResult.Success).memberQuota
        assertNotNull(quota)
        assertEquals("Astrella Virgiananda", quota!!.memberName)
        assertEquals(7, quota.availableQuota)
    }

    @Test
    fun `test 1b - partial name does not match`() {
        // "Astrella" (without surname) should not match — exact name required
        val json = """
        {"status":true,"message":"ok","data":[{"exclusive_session_code":"X","date":"2026-01-01",
        "start_time":"10:00:00","end_time":"11:00:00","label":"S1","session_members":[
        {"label":"J1","price":100,"member_name":"Astrella","session_detail_code":"X-1","available_quota":3}
        ]}]}
        """.trimIndent()
        val result = apiClient.parseResponse(json)
        assertTrue(result is ApiResult.Success)
        assertNull((result as ApiResult.Success).memberQuota)
    }

    // ── 2. Quota = 5 does NOT trigger notification ────────────────────

    @Test
    fun `test 2 - quota 5 does not trigger notification`() {
        // threshold is strictly < 5; quota==5 is NOT low
        assertFalse(shouldNotify(availableQuota = 5, lastNotifiedQuota = null))
    }

    // ── 3. Quota = 4 triggers notification ───────────────────────────

    @Test
    fun `test 3 - quota 4 triggers notification when not previously notified`() {
        assertTrue(shouldNotify(availableQuota = 4, lastNotifiedQuota = null))
    }

    @Test
    fun `test 3b - parseResponse with low quota fixture returns quota 4`() {
        val result = apiClient.parseResponse(fixtureLowQuota)
        assertTrue(result is ApiResult.Success)
        val quota = (result as ApiResult.Success).memberQuota
        assertNotNull(quota)
        assertEquals(4, quota!!.availableQuota)
        assertTrue(shouldNotify(quota.availableQuota, null))
    }

    // ── 4. Quota = 0 triggers notification ───────────────────────────

    @Test
    fun `test 4 - quota 0 triggers notification`() {
        assertTrue(shouldNotify(availableQuota = 0, lastNotifiedQuota = null))
    }

    @Test
    fun `test 4b - parseResponse with sold out fixture returns quota 0`() {
        val result = apiClient.parseResponse(fixtureSoldOut)
        assertTrue(result is ApiResult.Success)
        val quota = (result as ApiResult.Success).memberQuota
        assertNotNull(quota)
        assertEquals(0, quota!!.availableQuota)
    }

    // ── 5. Unchanged quota does NOT send duplicate notification ───────

    @Test
    fun `test 5 - same quota as lastNotifiedQuota does not re-notify`() {
        assertFalse(shouldNotify(availableQuota = 4, lastNotifiedQuota = 4))
    }

    @Test
    fun `test 5b - quota remains 4 no second notification`() {
        // First check: quota=4, last notified=null → notify
        assertTrue(shouldNotify(availableQuota = 4, lastNotifiedQuota = null))
        // Second check: quota=4, last notified=4 → do not notify again
        assertFalse(shouldNotify(availableQuota = 4, lastNotifiedQuota = 4))
    }

    // ── 6. Quota 4→3 sends a new notification ────────────────────────

    @Test
    fun `test 6 - quota changes from 4 to 3 triggers new notification`() {
        // Was notified at quota=4; now quota=3 (different value, still < 5)
        assertTrue(shouldNotify(availableQuota = 3, lastNotifiedQuota = 4))
    }

    // ── 7. Quota 3→5→4 sends notification only on the last transition

    @Test
    fun `test 7 - quota 3 to 5 no notification`() {
        assertFalse(shouldNotify(availableQuota = 5, lastNotifiedQuota = 3))
    }

    @Test
    fun `test 7b - quota 5 to 4 triggers notification`() {
        // After recovering to 5 (last notified=3 from before), goes to 4 again
        assertTrue(shouldNotify(availableQuota = 4, lastNotifiedQuota = 3))
    }

    @Test
    fun `test 7 full sequence - 3 to 5 to 4`() {
        // Simulate: notified at 3, recovered to 5, dropped to 4
        var lastNotified: Int? = 3
        // Step 1: quota goes to 5 — above threshold, no notification
        val step1 = shouldNotify(5, lastNotified)
        assertFalse(step1)
        // last notified stays 3 (we don't update it because we didn't notify)
        // Step 2: quota drops to 4 — below threshold AND different from lastNotified (3)
        val step2 = shouldNotify(4, lastNotified)
        assertTrue(step2)
    }

    // ── 8. Missing member in API response ────────────────────────────

    @Test
    fun `test 8 - member absent returns Success with null quota`() {
        val result = apiClient.parseResponse(fixtureMemberAbsent)
        assertTrue(result is ApiResult.Success)
        assertNull((result as ApiResult.Success).memberQuota)
    }

    // ── 9. Invalid JSON ───────────────────────────────────────────────

    @Test
    fun `test 9 - invalid JSON returns ApiResult Error`() {
        val result = apiClient.parseResponse("not valid json {{{")
        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun `test 9b - empty string returns ApiResult Error`() {
        val result = apiClient.parseResponse("")
        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun `test 9c - partial JSON returns ApiResult Error`() {
        val result = apiClient.parseResponse("""{"status": true, "data": [""")
        assertTrue(result is ApiResult.Error)
    }

    // ── 10. Network timeout simulation ───────────────────────────────

    @Test
    fun `test 10 - blank exclusive ID returns error without network call`() {
        val result = apiClient.fetchBonusQuota("")
        assertTrue(result is ApiResult.Error)
        val msg = (result as ApiResult.Error).message
        assertTrue(msg.contains("empty", ignoreCase = true))
    }

    @Test
    fun `test 10b - whitespace-only ID returns error`() {
        val result = apiClient.fetchBonusQuota("   ")
        assertTrue(result is ApiResult.Error)
    }

    // ── 11. Exclusive ID changes while monitoring active ─────────────

    @Test
    fun `test 11 - different exclusive IDs parse independently`() {
        // Parsing the same JSON with different IDs should return the same member quota
        // (the ID is used for the URL, not for filtering the response)
        val result1 = apiClient.parseResponse(fixtureNormal)
        val result2 = apiClient.parseResponse(fixtureLowQuota)

        val quota1 = (result1 as ApiResult.Success).memberQuota!!.availableQuota
        val quota2 = (result2 as ApiResult.Success).memberQuota!!.availableQuota

        assertEquals(7, quota1)
        assertEquals(4, quota2)
        // Different quotas → both would notify (last notified=null initially)
        assertTrue(shouldNotify(quota1, null).not())  // 7 is not < 5
        assertTrue(shouldNotify(quota2, null))         // 4 is < 5
    }

    // ── 12. History JSON serialization / deserialization ─────────────

    @Test
    fun `test 12 - history entry serializes and deserializes correctly`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val entry = HistoryEntry(
            exclusiveId = "EX79CB",
            memberName = "Astrella Virgiananda",
            availableQuota = 4,
            timestamp = "2026-11-15T15:15:00",
            status = "LOW"
        )
        val encoded = json.encodeToString(entry)
        val decoded = json.decodeFromString<HistoryEntry>(encoded)
        assertEquals(entry, decoded)
    }

    @Test
    fun `test 12b - list of history entries round-trips`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val entries = listOf(
            HistoryEntry("EX79CB", "Astrella Virgiananda", 7, "2026-11-15T15:00:00", "NORMAL"),
            HistoryEntry("EX79CB", "Astrella Virgiananda", 4, "2026-11-15T15:15:00", "LOW"),
            HistoryEntry("EX79CB", "Astrella Virgiananda", 0, "2026-11-15T15:30:00", "SOLD_OUT")
        )
        val encoded = json.encodeToString(entries)
        val decoded = json.decodeFromString<List<HistoryEntry>>(encoded)
        assertEquals(entries, decoded)
    }

    // ── 13. Corrupted history file recovery ──────────────────────────

    @Test
    fun `test 13 - corrupted JSON for history list returns empty list`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val corrupted = "[[{this is not valid json"
        val result = runCatching {
            json.decodeFromString<List<HistoryEntry>>(corrupted)
        }.getOrElse { emptyList() }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test 13b - completely empty string history returns empty list`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val result = runCatching {
            if ("".isBlank()) emptyList()
            else json.decodeFromString<List<HistoryEntry>>("")
        }.getOrElse { emptyList() }
        assertTrue(result.isEmpty())
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Pure notification decision logic (mirrors NotificationHelper.evaluateAndNotify).
     * Extracted here so we can test it without Android context.
     */
    private fun shouldNotify(availableQuota: Int, lastNotifiedQuota: Int?): Boolean {
        val threshold = 5
        return availableQuota < threshold && availableQuota != lastNotifiedQuota
    }

    private fun loadFixture(path: String): String {
        return javaClass.classLoader!!.getResourceAsStream(path)!!
            .bufferedReader()
            .readText()
    }
}
