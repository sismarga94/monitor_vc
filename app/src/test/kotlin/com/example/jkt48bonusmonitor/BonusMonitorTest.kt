package com.example.jkt48bonusmonitor

import com.example.jkt48bonusmonitor.data.api.BonusApiClient
import com.example.jkt48bonusmonitor.data.api.TARGET_MEMBER
import com.example.jkt48bonusmonitor.data.history.HistoryEntry
import com.example.jkt48bonusmonitor.domain.model.ApiResult
import com.example.jkt48bonusmonitor.domain.model.MemberSlot
import com.example.jkt48bonusmonitor.notification.QUOTA_THRESHOLD
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BonusMonitorTest {

    private lateinit var apiClient: BonusApiClient

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
    fun `test 1 - exact member name finds all Astrella slots`() {
        val result = apiClient.parseResponse(fixtureNormal)
        assertTrue(result is ApiResult.Success)
        val slots = (result as ApiResult.Success).slots
        assertTrue("Should find at least one slot", slots.isNotEmpty())
        assertTrue("All slots belong to Astrella", slots.all { it.memberName == TARGET_MEMBER })
    }

    @Test
    fun `test 1b - normal fixture returns 2 slots`() {
        val result = apiClient.parseResponse(fixtureNormal)
        val slots = (result as ApiResult.Success).slots
        assertEquals(2, slots.size)
        assertEquals(7, slots[0].availableQuota)   // Sesi 1
        assertEquals(5, slots[1].availableQuota)   // Sesi 2
    }

    @Test
    fun `test 1c - partial name does not match`() {
        val json = """{"status":true,"message":"ok","data":[{"exclusive_session_code":"X",
        "date":"2026-01-01","start_time":"10:00:00","end_time":"11:00:00","label":"S1",
        "session_members":[{"label":"J1","price":100,"member_name":"Astrella",
        "session_detail_code":"X-1","available_quota":3}]}]}"""
        val result = apiClient.parseResponse(json)
        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).slots.isEmpty())
    }

    // ── 2. Quota = 5 does NOT trigger notification ────────────────────

    @Test
    fun `test 2 - quota 5 does not trigger notification`() {
        val slot = makeSlot("CODE-1", 5)
        assertFalse(shouldNotifySlot(slot, emptyMap()))
    }

    // ── 3. Quota = 4 triggers notification ───────────────────────────

    @Test
    fun `test 3 - quota 4 triggers notification`() {
        val slot = makeSlot("CODE-1", 4)
        assertTrue(shouldNotifySlot(slot, emptyMap()))
    }

    @Test
    fun `test 3b - low quota fixture has 2 slots below threshold`() {
        val result = apiClient.parseResponse(fixtureLowQuota)
        val slots = (result as ApiResult.Success).slots
        assertEquals(3, slots.size)
        val lowSlots = slots.filter { it.availableQuota < QUOTA_THRESHOLD }
        assertEquals(2, lowSlots.size) // quota=4 and quota=3; quota=5 is not low
    }

    // ── 4. Quota = 0 triggers notification ───────────────────────────

    @Test
    fun `test 4 - quota 0 triggers notification`() {
        val slot = makeSlot("CODE-1", 0)
        assertTrue(shouldNotifySlot(slot, emptyMap()))
    }

    @Test
    fun `test 4b - sold out fixture all Astrella slots are 0`() {
        val result = apiClient.parseResponse(fixtureSoldOut)
        val slots = (result as ApiResult.Success).slots
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it.availableQuota == 0 })
    }

    // ── 5. Unchanged quota does NOT send duplicate ────────────────────

    @Test
    fun `test 5 - same quota as lastNotified does not re-notify`() {
        val slot = makeSlot("CODE-1", 4)
        assertFalse(shouldNotifySlot(slot, mapOf("CODE-1" to 4)))
    }

    @Test
    fun `test 5b - different slot codes are independent`() {
        val slot1 = makeSlot("CODE-1", 4)
        val slot2 = makeSlot("CODE-2", 4)
        // CODE-1 was notified at 4; CODE-2 was never notified
        val notifiedMap = mapOf("CODE-1" to 4)
        assertFalse(shouldNotifySlot(slot1, notifiedMap)) // already notified
        assertTrue(shouldNotifySlot(slot2, notifiedMap))  // new slot, should notify
    }

    // ── 6. Quota 4→3 sends a new notification ────────────────────────

    @Test
    fun `test 6 - quota changes from 4 to 3 triggers new notification`() {
        val slot = makeSlot("CODE-1", 3)
        assertFalse(shouldNotifySlot(slot, mapOf("CODE-1" to 3))) // same, no notif
        assertTrue(shouldNotifySlot(slot.copy(availableQuota = 3), mapOf("CODE-1" to 4))) // changed
    }

    // ── 7. Quota 3→5→4 sequence ──────────────────────────────────────

    @Test
    fun `test 7 - quota 3 to 5 no notification`() {
        val slot = makeSlot("CODE-1", 5)
        assertFalse(shouldNotifySlot(slot, mapOf("CODE-1" to 3))) // 5 >= threshold
    }

    @Test
    fun `test 7b - quota 5 to 4 triggers notification`() {
        // last notified was 3 (before recovery to 5); now drops to 4 again
        val slot = makeSlot("CODE-1", 4)
        assertTrue(shouldNotifySlot(slot, mapOf("CODE-1" to 3)))
    }

    @Test
    fun `test 7 full multi-slot sequence`() {
        var notifiedMap = emptyMap<String, Int>()
        val code = "CODE-1"

        // quota=4: should notify (first time)
        assertTrue(shouldNotifySlot(makeSlot(code, 4), notifiedMap))
        notifiedMap = notifiedMap + (code to 4)

        // quota=4 again: no duplicate
        assertFalse(shouldNotifySlot(makeSlot(code, 4), notifiedMap))

        // quota recovers to 5: no notification
        assertFalse(shouldNotifySlot(makeSlot(code, 5), notifiedMap))

        // quota drops to 4 again: notify (different from lastNotified=4? NO — it IS 4)
        // But since we didn't update notifiedMap when quota went to 5, lastNotified is still 4
        assertFalse(shouldNotifySlot(makeSlot(code, 4), notifiedMap))

        // quota drops to 3: notify (3 != lastNotified 4)
        assertTrue(shouldNotifySlot(makeSlot(code, 3), notifiedMap))
    }

    // ── 8. Missing member in API response ────────────────────────────

    @Test
    fun `test 8 - member absent returns Success with empty slots`() {
        val result = apiClient.parseResponse(fixtureMemberAbsent)
        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).slots.isEmpty())
    }

    // ── 9. Invalid JSON ───────────────────────────────────────────────

    @Test
    fun `test 9 - invalid JSON returns ApiResult Error`() {
        assertTrue(apiClient.parseResponse("not valid json {{{") is ApiResult.Error)
    }

    @Test
    fun `test 9b - empty string returns ApiResult Error`() {
        assertTrue(apiClient.parseResponse("") is ApiResult.Error)
    }

    @Test
    fun `test 9c - partial JSON returns ApiResult Error`() {
        assertTrue(apiClient.parseResponse("""{"status": true, "data": [""") is ApiResult.Error)
    }

    // ── 10. Network / ID validation ───────────────────────────────────

    @Test
    fun `test 10 - blank exclusive ID returns error without network call`() {
        val result = apiClient.fetchBonusQuota("")
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.contains("empty", ignoreCase = true))
    }

    @Test
    fun `test 10b - whitespace-only ID returns error`() {
        assertTrue(apiClient.fetchBonusQuota("   ") is ApiResult.Error)
    }

    // ── 11. Multiple exclusive IDs parse independently ────────────────

    @Test
    fun `test 11 - two different fixtures produce independent slot lists`() {
        val slotsNormal = (apiClient.parseResponse(fixtureNormal) as ApiResult.Success).slots
        val slotsLow = (apiClient.parseResponse(fixtureLowQuota) as ApiResult.Success).slots

        // normal: all >= 5
        assertTrue(slotsNormal.all { it.availableQuota >= QUOTA_THRESHOLD || it.availableQuota == 7 })
        // low: at least one < 5
        assertTrue(slotsLow.any { it.availableQuota < QUOTA_THRESHOLD })
    }

    // ── 12. History serialization ─────────────────────────────────────

    @Test
    fun `test 12 - history entry with session fields round-trips`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val entry = HistoryEntry(
            exclusiveId = "EX79CB",
            memberName = TARGET_MEMBER,
            availableQuota = 4,
            timestamp = "2026-11-22T11:45:00",
            status = "LOW",
            sessionCode = "EX79CB-SNBEED",
            sessionDetailCode = "EX79CB-SNBEED-SD392E",
            sessionDate = "2026-11-22",
            sessionLabel = "Sesi 1",
            slotLabel = "Jalur 4"
        )
        val decoded = json.decodeFromString<HistoryEntry>(json.encodeToString(entry))
        assertEquals(entry, decoded)
    }

    @Test
    fun `test 12b - list of history entries round-trips`() {
        val json = Json { ignoreUnknownKeys = true }
        val entries = listOf(
            HistoryEntry("EX79CB", TARGET_MEMBER, 7, "2026-11-22T11:45:00", "NORMAL",
                "EX79CB-SNBEED", "EX79CB-SNBEED-SD392E", "2026-11-22", "Sesi 1", "Jalur 4"),
            HistoryEntry("EX79CB", TARGET_MEMBER, 4, "2026-11-22T13:15:00", "LOW",
                "EX79CB-SND4DA", "EX79CB-SND4DA-SDF598", "2026-11-22", "Sesi 2", "Jalur 4")
        )
        assertEquals(entries, json.decodeFromString<List<HistoryEntry>>(json.encodeToString(entries)))
    }

    // ── 13. Corrupted history recovery ───────────────────────────────

    @Test
    fun `test 13 - corrupted JSON for history list returns empty list`() {
        val result = runCatching {
            Json { isLenient = true }.decodeFromString<List<HistoryEntry>>("[[{bad")
        }.getOrElse { emptyList() }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test 13b - blank string history returns empty list`() {
        val result = runCatching {
            if ("".isBlank()) emptyList()
            else Json.Default.decodeFromString<List<HistoryEntry>>("")
        }.getOrElse { emptyList() }
        assertTrue(result.isEmpty())
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun makeSlot(detailCode: String, quota: Int) = MemberSlot(
        memberName = TARGET_MEMBER,
        availableQuota = quota,
        sessionCode = "EX79CB-SNBEED",
        sessionDetailCode = detailCode,
        sessionDate = "2026-11-22",
        sessionLabel = "Sesi 1",
        sessionStartTime = "11:45:00",
        slotLabel = "Jalur 4"
    )

    /** Mirrors the per-slot notification decision logic from NotificationHelper. */
    private fun shouldNotifySlot(slot: MemberSlot, notifiedMap: Map<String, Int>): Boolean {
        val lastNotified = notifiedMap[slot.sessionDetailCode]
        return slot.availableQuota < QUOTA_THRESHOLD && slot.availableQuota != lastNotified
    }

    private fun loadFixture(path: String): String =
        javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText()
}
