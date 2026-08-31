package com.example.opencodeclient.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.opencodeclient.data.OpenCodeClient
import com.example.opencodeclient.data.Part
import com.example.opencodeclient.data.Project
import com.example.opencodeclient.data.QuestionRequest
import com.example.opencodeclient.data.ServerProfile
import com.example.opencodeclient.data.Session
import com.example.opencodeclient.data.SettingsRepository
import com.example.opencodeclient.data.Tokens
import com.example.opencodeclient.data.promptTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val reasoning: String? = null,
    val parts: List<PartUi> = emptyList(),
)

data class PartUi(
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val toolTitle: String? = null,
    val toolState: String? = null,
    val toolInput: String? = null,
    val toolOutput: String? = null,
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

    private val _sessionTokens = MutableStateFlow<Tokens?>(null)
    val sessionTokens: StateFlow<Tokens?> = _sessionTokens.asStateFlow()

    private val _contextWindow = MutableStateFlow(0L)
    val contextWindow: StateFlow<Long> = _contextWindow.asStateFlow()

    private val _promptTokens = MutableStateFlow(0L)
    val promptTokens: StateFlow<Long> = _promptTokens.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private var eventJob: Job? = null

    private val _authUsername = MutableStateFlow<String?>(null)
    val authUsername: StateFlow<String?> = _authUsername.asStateFlow()

    private val _authPassword = MutableStateFlow<String?>(null)
    val authPassword: StateFlow<String?> = _authPassword.asStateFlow()

    private val _servers = MutableStateFlow<List<ServerProfile>>(emptyList())
    val servers: StateFlow<List<ServerProfile>> = _servers.asStateFlow()

    private val _pendingQuestions = MutableStateFlow<List<QuestionRequest>>(emptyList())
    val pendingQuestions: StateFlow<List<QuestionRequest>> = _pendingQuestions.asStateFlow()

    private val _shortTokens = MutableStateFlow(true)
    val shortTokens: StateFlow<Boolean> = _shortTokens.asStateFlow()

    private val _theme = MutableStateFlow("system")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _userBubbleColor = MutableStateFlow(-1L)
    val userBubbleColor: StateFlow<Long> = _userBubbleColor.asStateFlow()

    private val _assistantBubbleColor = MutableStateFlow(-1L)
    val assistantBubbleColor: StateFlow<Long> = _assistantBubbleColor.asStateFlow()

    init {
        viewModelScope.launch {
            settings.serverUrl.collect { _serverUrl.value = it }
        }
        viewModelScope.launch {
            settings.authUsername.collect { _authUsername.value = it }
        }
        viewModelScope.launch {
            settings.authPassword.collect { _authPassword.value = it }
        }
        viewModelScope.launch {
            settings.servers.collect { _servers.value = it }
        }
        viewModelScope.launch {
            settings.shortTokens.collect { _shortTokens.value = it }
        }
        viewModelScope.launch {
            settings.theme.collect { _theme.value = it }
        }
        viewModelScope.launch {
            settings.userBubbleColor.collect { _userBubbleColor.value = it }
        }
        viewModelScope.launch {
            settings.assistantBubbleColor.collect { _assistantBubbleColor.value = it }
        }
    }

    fun connect(serverUrl: String, username: String? = null, password: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _connectionState.value = UiState.Loading("Connecting...")
            try {
                val cli = OpenCodeClient(serverUrl, username, password)
                val health = withContext(Dispatchers.IO) { cli.health() }
                if (!health.healthy) throw IllegalStateException("Server is not healthy")
                client = cli
                _activeSession.value = null
                _messages.value = emptyList()
                _sessionTokens.value = null
                _contextWindow.value = 0L
                _promptTokens.value = 0L
                _sending.value = false
                settings.setServerUrl(serverUrl)
                settings.setAuth(username, password)
                _serverUrl.value = serverUrl
                _authUsername.value = username
                _authPassword.value = password
                saveServerProfile(ServerProfile(serverUrl, username, password))
                _connectionState.value = UiState.Idle
                observeEvents()
                loadWorkspace()
                onSuccess()
            } catch (e: Exception) {
                _connectionState.value = UiState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun saveServerProfile(profile: ServerProfile) {
        viewModelScope.launch { settings.saveServer(profile) }
    }

    fun removeServerProfile(url: String) {
        viewModelScope.launch { settings.removeServer(url) }
    }

    fun replyQuestions(q: QuestionRequest, answers: List<List<String>>) {
        val c = client ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.replyQuestion(q.id, answers) } }
            _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == q.id }
        }
    }

    fun rejectQuestion(q: QuestionRequest) {
        val c = client ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.rejectQuestion(q.id) } }
            _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == q.id }
        }
    }

    fun setShortTokens(enabled: Boolean) {
        viewModelScope.launch { settings.setShortTokens(enabled) }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { settings.setTheme(value) }
    }

    fun setUserBubbleColor(color: Long) {
        viewModelScope.launch { settings.setUserBubbleColor(color) }
    }

    fun setAssistantBubbleColor(color: Long) {
        viewModelScope.launch { settings.setAssistantBubbleColor(color) }
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
                    val cli = OpenCodeClient(saved, _authUsername.value, _authPassword.value)
                    cli.health()
                    client = cli
                }
                observeEvents()
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
        runCatching {
            _pendingQuestions.value = withContext(Dispatchers.IO) { c.pendingQuestions() }
                .filter { it.sessionId == _activeSession.value?.id }
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
                    id = "dir-$dir",
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
                rollSessionStats(c, id)
                _workspaceState.value = UiState.Idle
            } catch (e: Exception) {
                _workspaceState.value = UiState.Error(e.message ?: "Failed to open session")
            }
        }
    }

    private suspend fun rollSessionStats(c: OpenCodeClient, id: String) {
        try {
            val detail = withContext(Dispatchers.IO) { c.sessionDetail(id) }
            if (detail != null) {
                _sessionTokens.value = detail.tokens
                val modelId = detail.model?.id
                _contextWindow.value = withContext(Dispatchers.IO) { c.contextWindow(modelId) }
            }
        } catch (_: Exception) {
            // ignore stats failure
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

    private fun buildText(parts: List<Part>): String =
        parts.filter { it.type != "reasoning" }.joinToString("") { p -> p.text ?: "" }

    private fun buildReasoning(parts: List<Part>): String? =
        parts.filter { it.type == "reasoning" }.mapNotNull { it.text }.joinToString("\n").ifBlank { null }

    fun loadMessages() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            try {
                val pairs = withContext(Dispatchers.IO) { c.sessionMessages(sid, 100) }
                _messages.value = pairs.map { (msg, parts) ->
                    ChatMessage(
                        id = msg.id,
                        role = msg.role ?: "unknown",
                        text = buildText(parts),
                        reasoning = buildReasoning(parts),
                        parts = parts.map { p ->
                            PartUi(
                                type = p.type,
                                text = p.text,
                                tool = p.tool,
                                toolTitle = p.title ?: p.tool,
                                toolState = p.state?.status,
                                toolInput = p.state?.input?.let {
                                    if (it is kotlinx.serialization.json.JsonPrimitive) it.contentOrNull else it.toString()
                                },
                                toolOutput = p.state?.output,
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
                withContext(Dispatchers.IO) { c.sendPromptAsync(sid, text) }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    id = "err-${System.currentTimeMillis()}",
                    role = "error",
                    text = e.message ?: "Send failed",
                )
                _sending.value = false
            }
        }
    }

    private fun buildPartUi(part: JsonObject): PartUi? {
        val type = part["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val text = part["text"]?.jsonPrimitive?.contentOrNull
        val tool = part["tool"]?.jsonPrimitive?.contentOrNull
        val title = part["title"]?.jsonPrimitive?.contentOrNull
        val state = part["state"]?.jsonObject
        val status = state?.get("status")?.jsonPrimitive?.contentOrNull
        val output = state?.get("output")?.let { if (it is JsonPrimitive) it.contentOrNull else it.toString() }
        val input = state?.get("input")?.let {
            if (it is JsonPrimitive) it.contentOrNull else it.toString()
        }
        return PartUi(type, text, tool, title ?: tool, status, input, output)
    }

    private fun observeEvents() {
        val c = client ?: return
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            while (true) {
                try {
                    c.eventStream().collect { raw -> handleEvent(raw) }
                } catch (_: Exception) {
                    // stream ended, retry
                }
                delay(2000)
            }
        }
    }

    private fun handleEvent(raw: String) {
        val dataStr = raw.substringAfter("data:").trim()
        if (dataStr.isEmpty()) return
        val obj = runCatching { json.parseToJsonElement(dataStr).jsonObject }.getOrNull() ?: return
        val payload = obj["payload"]?.jsonObject ?: return
        val type = payload["type"]?.jsonPrimitive?.contentOrNull ?: return
        val props = payload["properties"]?.jsonObject
        val active = _activeSession.value?.id
        if (active == null) return

        when (type) {
            "question.asked" -> {
                val sid = props?.get("sessionID")?.jsonPrimitive?.contentOrNull
                if (sid == active) {
                    runCatching {
                        val q = json.decodeFromString(QuestionRequest.serializer(), props.toString())
                        _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == q.id } + q
                    }
                }
            }
            "session.status", "session.idle" -> {
                val sid = props?.get("sessionID")?.jsonPrimitive?.contentOrNull
                if (sid != active) return
                if (type == "session.idle") {
                    _sending.value = false
                } else {
                    val st = props?.get("status")?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                    _sending.value = st == "busy"
                }
            }
            "message.updated" -> {
                val info = props?.get("info")?.jsonObject ?: return
                val sid = info["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
                if (sid != active) return
                val mid = info["id"]?.jsonPrimitive?.contentOrNull ?: return
                val role = info["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
                if (role != "user" && _messages.value.none { it.id == mid }) {
                    _messages.value = _messages.value + ChatMessage(mid, role, "")
                }
            }
            "message.part.updated" -> {
                val part = props?.get("part")?.jsonObject ?: return
                val sid = part["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
                if (sid != active) return
                val mid = part["messageID"]?.jsonPrimitive?.contentOrNull ?: return
                val partType = part["type"]?.jsonPrimitive?.contentOrNull
                if (partType == "step-finish") {
                    runCatching {
                        val toks = json.decodeFromString(Tokens.serializer(), part["tokens"].toString())
                        _promptTokens.value = toks.promptTokens
                    }
                    return
                }
                val ui = buildPartUi(part) ?: return
                _messages.value = _messages.value.map {
                    if (it.id != mid) it
                    else when (ui.type) {
                        "text" -> if ((ui.text?.length ?: 0) > it.text.length) it.copy(text = ui.text ?: it.text) else it
                        "reasoning" -> if ((ui.text?.length ?: 0) > (it.reasoning?.length ?: 0)) it.copy(reasoning = ui.text ?: it.reasoning) else it
                        else -> {
                            val parts = it.parts.filterNot { existing ->
                                ui.tool != null && existing.tool == ui.tool && existing.toolTitle == ui.toolTitle
                            } + ui
                            it.copy(parts = parts)
                        }
                    }
                }
            }
            "message.part.delta" -> {
                val sid = props?.get("sessionID")?.jsonPrimitive?.contentOrNull ?: return
                if (sid != active) return
                val mid = props["messageID"]?.jsonPrimitive?.contentOrNull ?: return
                val field = props["field"]?.jsonPrimitive?.contentOrNull ?: return
                val delta = props["delta"]?.jsonPrimitive?.contentOrNull ?: return
                if (field != "text") return
                val updated = if (_messages.value.none { it.id == mid }) {
                    _messages.value + ChatMessage(mid, "assistant", delta)
                } else {
                    _messages.value.map {
                        if (it.id != mid) it
                        else it.copy(text = it.text + delta)
                    }
                }
                _messages.value = updated
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
