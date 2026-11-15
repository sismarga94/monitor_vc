package com.example.jkt48bonusmonitor.data.history

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

private const val HISTORY_FILE_NAME = "monitoring_history.json"
private const val MAX_HISTORY_ENTRIES = 200

/**
 * Manages the monitoring history JSON file stored in internal files directory.
 * Uses atomic write (write temp then rename) to prevent corruption.
 */
class HistoryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val historyFile: File
        get() = File(context.filesDir, HISTORY_FILE_NAME)

    /**
     * Reads all history entries. Returns newest first.
     * Returns empty list if file missing or corrupted.
     */
    fun readHistory(): List<HistoryEntry> {
        val file = historyFile
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText(Charsets.UTF_8)
            if (content.isBlank()) return emptyList()
            val entries = json.decodeFromString<List<HistoryEntry>>(content)
            entries.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            // Corrupted file — return empty list, file will be overwritten on next write
            emptyList()
        }
    }

    /**
     * Appends a new entry and persists. Skips if the last entry has the same quota value
     * and status, to avoid flooding the file. Trims to [MAX_HISTORY_ENTRIES].
     */
    fun addEntry(entry: HistoryEntry) {
        val existing = readHistory().toMutableList()

        // De-duplicate consecutive identical quota + status
        val mostRecent = existing.firstOrNull()
        if (mostRecent != null &&
            mostRecent.availableQuota == entry.availableQuota &&
            mostRecent.status == entry.status &&
            mostRecent.exclusiveId == entry.exclusiveId
        ) {
            return
        }

        existing.add(0, entry) // newest first

        // Trim to max size
        val trimmed = if (existing.size > MAX_HISTORY_ENTRIES) {
            existing.take(MAX_HISTORY_ENTRIES)
        } else {
            existing
        }

        writeAtomically(trimmed)
    }

    /**
     * Clears all history by deleting the file.
     */
    fun clearHistory() {
        historyFile.delete()
    }

    /**
     * Writes the list atomically: write to a temp file, then rename.
     * This prevents partial writes from corrupting the history.
     */
    private fun writeAtomically(entries: List<HistoryEntry>) {
        val tempFile = File(context.filesDir, "$HISTORY_FILE_NAME.tmp")
        try {
            val jsonString = json.encodeToString(entries)
            tempFile.writeText(jsonString, Charsets.UTF_8)
            // Atomic rename
            tempFile.renameTo(historyFile)
        } catch (e: IOException) {
            tempFile.delete()
        }
    }
}
