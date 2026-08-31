package com.example.opencodeclient.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object TokenHistoryStore {
    private fun historyFile(context: Context): File =
        File(context.filesDir, "token_history.json")

    private fun toJsonString(map: Map<String, Long>): String {
        val sb = StringBuilder()
        sb.append('{')
        map.entries.joinTo(sb, ",", transform = { (k, v) -> "\"$k\":$v" })
        sb.append('}')
        return sb.toString()
    }

    fun loadAll(context: Context): Map<String, Long> {
        val file = historyFile(context)
        if (!file.exists()) return emptyMap()
        val text = file.readText().trim()
        if (text.isEmpty() || text == "{}") return emptyMap()
        val inner = text.removePrefix("{").removeSuffix("}")
        if (inner.isEmpty()) return emptyMap()
        return inner.split(",").associate { part ->
            val eq = part.indexOf(':')
            if (eq < 0) "" to 0L
            else {
                val key = part.substring(0, eq).trim().trim('"')
                val value = part.substring(eq + 1).trim().toLongOrNull() ?: 0L
                key to value
            }
        }
    }

    suspend fun addToday(context: Context, tokens: Long) {
        if (tokens <= 0L) return
        withContext(Dispatchers.IO) {
            val today = java.time.LocalDate.now().toString()
            val existing = loadAll(context)
            val updated = existing.toMutableMap().apply {
                this[today] = (this[today] ?: 0L) + tokens
            }
            historyFile(context).writeText(toJsonString(updated))
        }
    }
}