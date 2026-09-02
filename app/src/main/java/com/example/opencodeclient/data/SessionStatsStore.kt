package com.example.opencodeclient.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SessionStatsRecord(
    val anchorId: String? = null,
    val anchorTime: Long = 0L,
    val lastUserTime: Long = 0L,
    val totalElapsed: Long = 0L,
    val messageCount: Long = 0L,
    val firstUserText: String? = null,
)

@Serializable
data class SessionStatsFile(
    val sessions: MutableMap<String, SessionStatsRecord> = mutableMapOf(),
)

class SessionStatsStore(context: Context) {
    private val file = File(context.filesDir, "session_stats.json")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Synchronized
    fun record(sessionId: String): SessionStatsRecord =
        load().sessions[sessionId] ?: SessionStatsRecord()

    @Synchronized
    fun save(sessionId: String, record: SessionStatsRecord) {
        val data = load().also { it.sessions[sessionId] = record }
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(data))
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    private fun load(): SessionStatsFile {
        if (!file.exists()) return SessionStatsFile()
        return try {
            json.decodeFromString<SessionStatsFile>(file.readText())
        } catch (_: Throwable) {
            SessionStatsFile()
        }
    }
}
