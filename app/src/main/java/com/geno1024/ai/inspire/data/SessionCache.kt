package com.geno1024.ai.inspire.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedSession(
    val session: Session,
    val messages: List<SessionInfo>,
    val savedAt: Long = 0L,
)

@Serializable
data class CachedWorkspace(
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val savedAt: Long = 0L,
)

class SessionCache(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val dir by lazy { File(context.filesDir, "session_cache") }

    fun save(session: Session, messages: List<SessionInfo>) {
        runCatching {
            dir.mkdirs()
            prune()
            val file = File(dir, "${session.id}.json")
            file.writeText(json.encodeToString(CachedSession.serializer(), CachedSession(session, messages, System.currentTimeMillis())))
        }
    }

    fun load(sessionId: String): CachedSession? {
        return runCatching {
            val file = File(dir, "$sessionId.json")
            if (!file.exists()) null
            else json.decodeFromString(CachedSession.serializer(), file.readText())
        }.getOrNull()
    }

    fun saveWorkspace(projects: List<Project>, sessions: List<Session>) {
        runCatching {
            dir.mkdirs()
            prune()
            val file = File(dir, "_workspace.json")
            file.writeText(json.encodeToString(CachedWorkspace.serializer(), CachedWorkspace(projects, sessions, System.currentTimeMillis())))
        }
    }

    fun loadWorkspace(): CachedWorkspace? {
        return runCatching {
            val file = File(dir, "_workspace.json")
            if (!file.exists()) null
            else json.decodeFromString(CachedWorkspace.serializer(), file.readText())
        }.getOrNull()
    }

    fun clear(sessionId: String) {
        runCatching { File(dir, "$sessionId.json").delete() }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.extension == "json" && f.lastModified() < cutoff) f.delete()
        }
    }
}