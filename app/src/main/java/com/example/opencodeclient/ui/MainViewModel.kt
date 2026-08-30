package com.example.opencodeclient.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.opencodeclient.data.OpenCodeClient
import com.example.opencodeclient.data.Project
import com.example.opencodeclient.data.Session
import com.example.opencodeclient.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val parts: List<PartUi> = emptyList(),
)

data class PartUi(
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val toolTitle: String? = null,
    val toolState: String? = null,
)

data class ProjectUi(
    val id: String,
    val worktree: String,
    val name: String,
    val sessions: List<Session> = emptyList(),
)

sealed interface UiState {
    data object Idle : UiState
    data class Loading(val message: String = "Loading...") : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)

    var client: OpenCodeClient? = null
        private set

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _connectionState = MutableStateFlow<UiState>(UiState.Idle)
    val connectionState: StateFlow<UiState> = _connectionState.asStateFlow()

    private val _workspaceState = MutableStateFlow<UiState>(UiState.Idle)
    val workspaceState: StateFlow<UiState> = _workspaceState.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectUi>>(emptyList())
    val projects: StateFlow<List<ProjectUi>> = _projects.asStateFlow()

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession: StateFlow<Session?> = _activeSession.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    init {
        viewModelScope.launch {
            settings.serverUrl.collect { _serverUrl.value = it }
        }
    }

    fun connect(serverUrl: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _connectionState.value = UiState.Loading("Connecting...")
            try {
                val cli = OpenCodeClient(serverUrl)
                val health = withContext(Dispatchers.IO) { cli.health() }
                if (!health.healthy) throw IllegalStateException("Server is not healthy")
                client = cli
                settings.setServerUrl(serverUrl)
                _serverUrl.value = serverUrl
                _connectionState.value = UiState.Idle
                onSuccess()
            } catch (e: Exception) {
                _connectionState.value = UiState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun ensureLoaded(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _connectionState.value = UiState.Loading("Loading...")
            try {
                if (client == null) {
                    val saved = _serverUrl.value
                    if (saved == null) {
                        _connectionState.value = UiState.Error("No server configured")
                        return@launch
                    }
                    val cli = OpenCodeClient(saved)
                    cli.health()
                    client = cli
                }
                loadWorkspace()
                val last = settings.getLastSessionId()
                if (last != null) {
                    openSession(last)
                }
                _connectionState.value = UiState.Idle
                onDone()
            } catch (e: Exception) {
                _connectionState.value = UiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    private suspend fun loadWorkspace() {
        val c = client ?: return
        val rawProjects = withContext(Dispatchers.IO) { c.projects() }
        val allSessions = withContext(Dispatchers.IO) { c.sessions() }
        _projects.value = groupProjects(rawProjects, allSessions)
        if (_selectedProjectId.value == null && _projects.value.isNotEmpty()) {
            _selectedProjectId.value = _projects.value.first().id
        }
    }

    private fun groupProjects(projects: List<Project>, all: List<Session>): List<ProjectUi> {
        // Map a worktree directory to its Project for name/id resolution.
        val worktrees = projects
            .filter { it.id != "global" && it.worktree.isNotBlank() }
            .sortedByDescending { it.worktree.length }
            .map { it.worktree.trimEnd('/') to it }
            .toMap()

        fun resolve(dir: String): Project? {
            if (dir.isEmpty()) return null
            return worktrees[dir]
                ?: worktrees.entries.firstOrNull { dir.startsWith(it.key + "/") }?.value
        }

        // Group sessions by their own directory.
        val byDir = LinkedHashMap<String, MutableList<Session>>()
        val orphaned = mutableListOf<Session>()
        for (s in all) {
            val dir = s.directory.trimEnd('/')
            if (dir.isEmpty()) orphaned.add(s)
            else byDir.getOrPut(dir) { mutableListOf() }.add(s)
        }

        val result = mutableListOf<ProjectUi>()
        for ((dir, sessions) in byDir.toList().sortedBy { it.first }) {
            val proj = resolve(dir)
            result.add(
                ProjectUi(
                    id = proj?.id ?: "dir-$dir",
                    worktree = dir,
                    name = dir.substringAfterLast('/').ifBlank { dir },
                    sessions = sessions.sortedByDescending { it.time?.created ?: 0L },
                )
            )
        }

        if (orphaned.isNotEmpty() || all.isEmpty()) {
            result.add(
                ProjectUi(
                    id = "global",
                    worktree = "/",
                    name = "Global",
                    sessions = orphaned.sortedByDescending { it.time?.created ?: 0L },
                )
            )
        }
        return result
    }

    fun refresh() {
        if (client == null) return
        viewModelScope.launch {
            _workspaceState.value = UiState.Loading()
            try {
                loadWorkspace()
                _workspaceState.value = UiState.Idle
            } catch (e: Exception) {
                _workspaceState.value = UiState.Error(e.message ?: "Failed to load workspace")
            }
        }
    }

    fun selectProject(id: String) {
        _selectedProjectId.value = id
        viewModelScope.launch { settings.setLastSessionId(null) }
    }

    fun newSession(directory: String, onDone: () -> Unit = {}) {
        val c = client ?: return
        viewModelScope.launch {
            _workspaceState.value = UiState.Loading("Creating session...")
            try {
                val s = withContext(Dispatchers.IO) { c.createSession(directory = directory) }
                selectProjectByWorktree(directory)
                addSessionToProject(s)
                activateSession(s)
                _workspaceState.value = UiState.Idle
                onDone()
            } catch (e: Exception) {
                _workspaceState.value = UiState.Error(e.message ?: "Failed to create session")
            }
        }
    }

    private fun addSessionToProject(s: Session) {
        _projects.value = _projects.value.map { p ->
            if (p.worktree == s.directory) {
                p.copy(sessions = listOf(s) + p.sessions.filterNot { it.id == s.id })
            } else p
        }
    }

    fun openSession(id: String) {
        val c = client ?: return
        viewModelScope.launch {
            _workspaceState.value = UiState.Loading()
            try {
                val s = withContext(Dispatchers.IO) { c.session(id) }
                activateSession(s)
                _workspaceState.value = UiState.Idle
            } catch (e: Exception) {
                _workspaceState.value = UiState.Error(e.message ?: "Failed to open session")
            }
        }
    }

    private fun selectProjectByWorktree(worktree: String) {
        _projects.value.firstOrNull { it.worktree == worktree }?.let {
            _selectedProjectId.value = it.id
        }
    }

    private suspend fun activateSession(s: Session) {
        _activeSession.value = s
        settings.setLastSessionId(s.id)
        loadMessages()
    }

    fun loadMessages() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            try {
                val pairs = withContext(Dispatchers.IO) { c.sessionMessages(sid, 100) }
                _messages.value = pairs.map { (msg, parts) ->
                    val text = parts.joinToString("") { p -> p.text ?: "" }
                    ChatMessage(
                        id = msg.id,
                        role = msg.role ?: "unknown",
                        text = text,
                        parts = parts.map { p ->
                            PartUi(
                                type = p.type,
                                text = p.text,
                                tool = p.tool,
                                toolTitle = p.title,
                                toolState = p.state?.status,
                            )
                        },
                    )
                }
            } catch (_: Exception) {
                // ignore, keep current
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            _sending.value = true
            _messages.value = _messages.value + ChatMessage(
                id = "user-${System.currentTimeMillis()}",
                role = "user",
                text = text,
            )
            try {
                val (msg, parts) = withContext(Dispatchers.IO) { c.sendPrompt(sid, text) }
                val replyText = parts.joinToString("") { p -> p.text ?: "" }
                _messages.value = _messages.value + ChatMessage(
                    id = msg.id,
                    role = msg.role ?: "assistant",
                    text = replyText,
                    parts = parts.map { p ->
                        PartUi(p.type, p.text, p.tool, p.title, p.state?.status)
                    },
                )
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    id = "err-${System.currentTimeMillis()}",
                    role = "error",
                    text = e.message ?: "Send failed",
                )
            } finally {
                _sending.value = false
            }
        }
    }

    fun abort() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { c.abortSession(sid) } }
        }
    }

    fun reset() {
        _activeSession.value = null
        _messages.value = emptyList()
        viewModelScope.launch { settings.setLastSessionId(null) }
    }
}
