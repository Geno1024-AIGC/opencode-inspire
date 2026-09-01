package com.example.opencodeclient.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.opencodeclient.BuildConfig
import com.example.opencodeclient.R
import java.util.Locale
import com.example.opencodeclient.data.Command
import com.example.opencodeclient.data.ModelInfo
import com.example.opencodeclient.data.OpenCodeClient
import com.example.opencodeclient.data.Part
import com.example.opencodeclient.data.PermissionRequest
import com.example.opencodeclient.data.Project
import com.example.opencodeclient.data.QuestionRequest
import com.example.opencodeclient.data.ServerProfile
import com.example.opencodeclient.data.Session
import com.example.opencodeclient.data.SettingsRepository
import com.example.opencodeclient.data.Tokens
import com.example.opencodeclient.data.Updater
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val reasoning: String? = null,
    val parts: List<PartUi> = emptyList(),
    val model: String? = null,
    val tokens: Tokens? = null,
    val time: Long = 0L,
    val cumulativeTokens: Long = 0L,
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

data class TodoUi(
    val id: String,
    val content: String,
    val status: String,
)

data class ProjectUi(
    val id: String,
    val worktree: String,
    val name: String,
    val sessions: List<Session> = emptyList(),
)

sealed interface UiState {
    data object Idle : UiState
    data class Loading(val message: String = "Loading...") : UiState    data class Error(val message: String) : UiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)

    private fun getAppString(resId: Int): String {
        val ctx = getApplication<Application>()
        val locale = when (_language.value) {
            "en" -> Locale.ENGLISH
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.getDefault()
        }
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(locale)
        val localized = ctx.createConfigurationContext(config)
        return localized.getString(resId)
    }

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

    private val _todos = MutableStateFlow<List<TodoUi>>(emptyList())
    val todos: StateFlow<List<TodoUi>> = _todos.asStateFlow()

    private val _sessionTokens = MutableStateFlow<Tokens?>(null)
    val sessionTokens: StateFlow<Tokens?> = _sessionTokens.asStateFlow()

    private val _contextWindow = MutableStateFlow(0L)
    val contextWindow: StateFlow<Long> = _contextWindow.asStateFlow()

    private val _promptTokens = MutableStateFlow(0L)
    val promptTokens: StateFlow<Long> = _promptTokens.asStateFlow()

    private val _cumulativeTokens = MutableStateFlow(0L)
    val cumulativeTokens: StateFlow<Long> = _cumulativeTokens.asStateFlow()

    private var lastUserSendTime: Long? = null
    private val _sessionElapsed = MutableStateFlow<Long?>(null)
    val sessionElapsed: StateFlow<Long?> = _sessionElapsed.asStateFlow()

    private val _tokenHistory = MutableStateFlow<Map<String, Long>>(emptyMap())
    val tokenHistory: StateFlow<Map<String, Long>> = _tokenHistory.asStateFlow()

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

    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    val commands: StateFlow<List<Command>> = _commands.asStateFlow()

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _currentModelId = MutableStateFlow<String?>(null)
    val currentModelId: StateFlow<String?> = _currentModelId.asStateFlow()

    private val _pendingPermissions = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val pendingPermissions: StateFlow<List<PermissionRequest>> = _pendingPermissions.asStateFlow()

    private val _shortTokens = MutableStateFlow(true)
    val shortTokens: StateFlow<Boolean> = _shortTokens.asStateFlow()

    private val _theme = MutableStateFlow("system")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _themePreset = MutableStateFlow("default")
    val themePreset: StateFlow<String> = _themePreset.asStateFlow()

    private val _customThemeColors = MutableStateFlow("{}")
    val customThemeColors: StateFlow<String> = _customThemeColors.asStateFlow()

    private val _language = MutableStateFlow("system")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _channel = MutableStateFlow("release")
    val channel: StateFlow<String> = _channel.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

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
            settings.themePreset.collect { _themePreset.value = it }
        }
        viewModelScope.launch {
            settings.customThemeColors.collect { _customThemeColors.value = it }
        }
        viewModelScope.launch {
            settings.language.collect { _language.value = it }
        }
        viewModelScope.launch {
            settings.channel.collect { _channel.value = it }
        }
        viewModelScope.launch {
            settings.userBubbleColor.collect { _userBubbleColor.value = it }
        }
        viewModelScope.launch {
            settings.assistantBubbleColor.collect { _assistantBubbleColor.value = it }
        }
        createNotificationChannel()
    }

    private val sessionBusy = mutableMapOf<String, Boolean>()

    private fun createNotificationChannel() {
        val context = getApplication<Application>()
        val channels = listOf(
            NotificationChannel("session_status", "Session status", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel("download", "Update download", NotificationManager.IMPORTANCE_LOW),
        )
        context.getSystemService(Context.NOTIFICATION_SERVICE)?.let { service ->
            val nm = service as? NotificationManager ?: return
            channels.forEach { runCatching { nm.createNotificationChannel(it) } }
        }
    }

    fun showDownloadProgress(downloaded: Long, total: Long, speed: Long = 0L) {
        val context = getApplication<Application>()
        val progress = if (total > 0L) (downloaded * 100L / total).toInt().coerceIn(0, 100) else -1
        val eta = if (total > 0L && speed > 0L && downloaded < total) {
            context.getString(R.string.download_eta, formatEta((total - downloaded) / speed))
        } else ""
        val detail = if (total > 0L) {
            context.getString(
                R.string.download_progress_detail,
                "$progress%",
                formatBytes(downloaded),
                formatBytes(total),
                formatSpeed(speed),
            )
        } else {
            context.getString(R.string.download_progress_unknown,
                "$progress%",
                formatBytes(downloaded),
                formatSpeed(speed))
        }
        val fullText = if (eta.isNotEmpty()) "$detail · $eta" else detail
        val builder = android.app.Notification.Builder(context, "download")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_title))
            .setContentText(fullText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (total > 0L) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        try {
            context.getSystemService(Context.NOTIFICATION_SERVICE)?.let { service ->
                (service as? NotificationManager)?.notify(1002, builder.build())
            }
        } catch (_: Exception) {
        }
    }

    fun cancelDownloadNotification() {
        val context = getApplication<Application>()
        try {
            context.getSystemService(Context.NOTIFICATION_SERVICE)?.let { service ->
                (service as? NotificationManager)?.cancel(1002)
            }
        } catch (_: Exception) {
        }
    }

    private fun notifySessionDone(sid: String, tokens: Long = 0L) {
        val context = getApplication<Application>()
        val title = sessionTitle(sid).ifBlank { "OpenCode" }
        val notification = android.app.Notification.Builder(context, "session_status")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("$title finished")
            .setContentText(getAppString(R.string.notify_session_done))
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(Context.NOTIFICATION_SERVICE)?.let { service ->
                (service as? NotificationManager)?.notify(1001, notification)
            }
        } catch (_: Exception) {
            // permission not granted
        }
    }

    fun loadTokenHistory() {
        viewModelScope.launch {
            _tokenHistory.value = withContext(Dispatchers.IO) {
                val c = client ?: return@withContext emptyMap()
                val map = mutableMapOf<String, Long>()
                val zone = java.time.ZoneId.systemDefault()
                c.sessions().forEach { s ->
                    val created = s.time?.created ?: 0L
                    if (created > 0L) {
                        val day = java.time.Instant.ofEpochMilli(created)
                            .atZone(zone).toLocalDate().toString()
                        val toks = s.tokens
                        val total = toks?.total
                            ?: ((toks?.input ?: 0L) + (toks?.output ?: 0L) + (toks?.reasoning ?: 0L))
                        if (total > 0L) map[day] = (map[day] ?: 0L) + total
                    }
                }
                map
            }
        }
    }

    private fun sessionTitle(sid: String): String {
        for (p in _projects.value) {
            for (s in p.sessions) {
                if (s.id == sid && s.title.isNotBlank()) return s.title
            }
        }
        return _activeSession.value?.title.orEmpty()
    }

    fun connect(serverUrl: String, username: String? = null, password: String? = null, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _connectionState.value = UiState.Loading(getAppString(R.string.connecting))
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
                _cumulativeTokens.value = 0L
                lastUserSendTime = null
                _sessionElapsed.value = null
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
                checkForUpdates(notifyLatest = false)
            } catch (e: Exception) {
                _connectionState.value = UiState.Error(e.message ?: getAppString(R.string.error_connect_failed))
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
        val dir = _activeSession.value?.directory
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.replyQuestion(q.id, answers, dir) } }
                .onSuccess {
                    _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == q.id }
                }
                .onFailure { e ->
                    _workspaceState.value = UiState.Error(getAppString(R.string.send_failed) + ": " + (e.message ?: ""))
                }
        }
    }

    fun rejectQuestion(q: QuestionRequest) {
        val c = client ?: return
        val dir = _activeSession.value?.directory
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.rejectQuestion(q.id, dir) } }
                .onSuccess {
                    _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == q.id }
                }
                .onFailure { e ->
                    _workspaceState.value = UiState.Error(getAppString(R.string.chat_reject) + ": " + (e.message ?: ""))
                }
        }
    }

    fun setShortTokens(enabled: Boolean) {
        viewModelScope.launch { settings.setShortTokens(enabled) }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { settings.setTheme(value) }
    }

    fun setThemePreset(value: String) {
        viewModelScope.launch { settings.setThemePreset(value) }
    }

    fun setCustomThemeColors(json: String) {
        viewModelScope.launch { settings.setCustomThemeColors(json) }
    }

    fun setLanguage(value: String) {
        viewModelScope.launch { settings.setLanguage(value) }
    }

    fun setChannel(value: String) {
        viewModelScope.launch { settings.setChannel(value) }
    }

    fun checkForUpdates(notifyLatest: Boolean = true) {
        if (_checkingUpdate.value) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            try {
                val releases = Updater.fetchReleases()
                val rel = Updater.releaseFor(releases, _channel.value)
                val current = BuildConfig.VERSION_NAME
                if (rel != null && Updater.isNewer(rel.tagName, current)) {
                    _updateInfo.value = UpdateInfo(version = rel.tagName, url = rel.apkUrl ?: rel.htmlUrl)
                } else {
                    _updateInfo.value = null
                    if (notifyLatest) _updateMessage.value = getAppString(R.string.update_latest)
                }
            } catch (_: Exception) {
                if (notifyLatest) _updateMessage.value = getAppString(R.string.update_check_failed)
            } finally {
                _checkingUpdate.value = false
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun dismissUpdateMessage() {
        _updateMessage.value = null
    }

    fun showUpdateMessage(message: String) {
        _updateMessage.value = message
    }

    fun setUserBubbleColor(color: Long) {
        viewModelScope.launch { settings.setUserBubbleColor(color) }
    }

    fun setAssistantBubbleColor(color: Long) {
        viewModelScope.launch { settings.setAssistantBubbleColor(color) }
    }

    fun ensureLoaded(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _connectionState.value = UiState.Loading(getAppString(R.string.loading))
            try {
                if (client == null) {
                    val saved = _serverUrl.value
                    if (saved == null) {
                        _connectionState.value = UiState.Error(getAppString(R.string.error_no_server))
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
                _connectionState.value = UiState.Error(e.message ?: getAppString(R.string.error_load_failed))
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
            _pendingQuestions.value = withContext(Dispatchers.IO) {
                c.pendingQuestions(_activeSession.value?.directory)
            }.filter { it.sessionId == _activeSession.value?.id }
        }
        runCatching {
            _commands.value = withContext(Dispatchers.IO) {
                c.commands(_activeSession.value?.directory)
            }
        }
        runCatching {
            _models.value = withContext(Dispatchers.IO) {
                c.models()
            }
        }
        runCatching {
            _pendingPermissions.value = withContext(Dispatchers.IO) {
                c.pendingPermissions(_activeSession.value?.directory)
            }.filter { it.sessionId == _activeSession.value?.id }
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
                    name = getAppString(R.string.global_project),
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
                _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.workspace_error))
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
            _workspaceState.value = UiState.Loading(getAppString(R.string.creating_session))
            try {
                val s = withContext(Dispatchers.IO) { c.createSession(directory = directory) }
                selectProjectByWorktree(directory)
                addSessionToProject(s)
                activateSession(s)
                _workspaceState.value = UiState.Idle
                onDone()
            } catch (e: Exception) {
                _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.create_session_error))
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
                _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.open_session_error))
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
        _currentModelId.value = s.model?.id
        settings.setLastSessionId(s.id)
        loadMessages()
    }

    private fun buildText(parts: List<Part>): String =
        parts.filter { it.type != "reasoning" }.joinToString("") { p -> p.text ?: "" }

    private fun buildReasoning(parts: List<Part>): String? =
        parts.filter { it.type == "reasoning" }.mapNotNull { it.text }.joinToString("\n").ifBlank { null }

    private fun serverTimeToMillis(value: Long?): Long =
        when {
            value == null || value <= 0L -> 0L
            value < 10_000_000_000L -> value * 1000L
            else -> value
        }

        fun loadMessages() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            try {
                val pairs = withContext(Dispatchers.IO) { c.sessionMessages(sid, 100) }
                var cum = 0L
                _messages.value = pairs.map { (msg, parts) ->
                    val tokens = msg.tokens
                    val tokenTotal = tokens?.total ?: 0L
                    if (tokenTotal > 0) cum += tokenTotal
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
                        model = msg.modelID ?: msg.model?.id,
                        tokens = tokens,
                        time = serverTimeToMillis(msg.time?.created),
                        cumulativeTokens = cum,
                    )
                }
                runCatching {
                    val todos = withContext(Dispatchers.IO) { c.sessionTodos(sid) }
                    _todos.value = todos.mapNotNull { t ->
                        if (t.content.isBlank()) null
                        else TodoUi(t.id, t.content, t.status)
                    }
                }
                recomputeSessionElapsed()
            } catch (_: Exception) {
                // ignore, keep current
            }
        }
    }

    fun refreshSession() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
         viewModelScope.launch {
            _sending.value = false
            loadMessages()
            rollSessionStats(c, sid)
        }
    }

    private fun recomputeSessionElapsed() {
        var lastUser = 0L
        var last = 0L
        for (m in _messages.value) {
            if (m.time <= 0L) continue
            if (m.role == "user" && m.time > lastUser) lastUser = m.time
            if (m.time > last) last = m.time
        }
        _sessionElapsed.value = if (lastUser > 0L && last > lastUser) last - lastUser else null
    }

     fun send(text: String) {
         if (text.isBlank()) return
         val c = client ?: return
         val sid = _activeSession.value?.id ?: return
         viewModelScope.launch {
             _sending.value = true
             lastUserSendTime = System.currentTimeMillis()
             _sessionElapsed.value = null
             _messages.value = _messages.value + ChatMessage(
                id = "user-${System.currentTimeMillis()}",
                role = "user",
                text = text,
                time = System.currentTimeMillis(),
            )
            try {
                withContext(Dispatchers.IO) { c.sendPromptAsync(sid, text) }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    id = "err-${System.currentTimeMillis()}",
                    role = "error",
                    text = e.message ?: getAppString(R.string.send_failed),
                )
                _sending.value = false
            }
        }
    }

    fun runCommand(command: Command) {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            _sending.value = true
            try {
                withContext(Dispatchers.IO) { c.executeCommand(sid, command.name) }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    id = "err-${System.currentTimeMillis()}",
                    role = "error",
                    text = e.message ?: getAppString(R.string.send_failed),
                )
                _sending.value = false
            }
        }
    }

    fun replyPermission(permission: PermissionRequest, reply: String) {
        val c = client ?: return
        val dir = _activeSession.value?.directory
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.replyPermission(permission.id, reply, null, dir) } }
                .onSuccess {
                    _pendingPermissions.value = _pendingPermissions.value.filterNot { it.id == permission.id }
                }
                .onFailure { e ->
                    _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.send_failed))
                }
        }
    }

    fun switchModel(providerId: String, modelId: String) {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.switchModel(sid, providerId, modelId) } }
                .onSuccess {
                    _currentModelId.value = modelId
                    _activeSession.value = _activeSession.value?.copy(model = com.example.opencodeclient.data.ModelV2Ref(id = modelId, providerId = providerId))
                    rollSessionStats(c, sid)
                }
                .onFailure { e ->
                    _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.send_failed))
                }
        }
    }

    fun renameSession(title: String) {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.renameSession(sid, title) } }
                .onSuccess {
                    _activeSession.value = _activeSession.value?.copy(title = title)
                    _projects.value = _projects.value.map { p ->
                        p.copy(sessions = p.sessions.map { if (it.id == sid) it.copy(title = title) else it })
                    }
                }
                .onFailure { e ->
                    _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.send_failed))
                }
        }
    }

    fun deleteSession() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        val dir = _activeSession.value?.directory
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.deleteSession(sid) } }
                .onSuccess {
                    _projects.value = _projects.value.map { p ->
                        p.copy(sessions = p.sessions.filterNot { it.id == sid })
                    }
                    reset()
                }
                .onFailure { e ->
                    _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.send_failed))
                }
        }
    }

    suspend fun listFiles(path: String): List<com.example.opencodeclient.data.FileNode> {
        val c = client ?: return emptyList()
        return withContext(Dispatchers.IO) { c.listFiles(path) }
    }

    fun openFileInChat(path: String) {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            _sending.value = true
            _messages.value = _messages.value + ChatMessage(
                id = "user-${System.currentTimeMillis()}",
                role = "user",
                text = "Read $path",
                time = System.currentTimeMillis(),
            )
            try {
                withContext(Dispatchers.IO) { c.sendPromptAsync(sid, "Read $path and summarize its purpose") }
            } catch (_: Exception) {
                _sending.value = false
            }
        }
    }

    private fun describeSessionError(error: JsonObject?): String {
        if (error == null) return getAppString(R.string.error_model_failed)
        val name = error["name"]?.jsonPrimitive?.contentOrNull
        val data = error["data"]?.jsonObject
        val message = data?.get("message")?.jsonPrimitive?.contentOrNull
        val prefix = when (name) {
            "ContextOverflowError" -> getAppString(R.string.error_token_limit)
            "ProviderAuthError" -> getAppString(R.string.error_auth_failed)
            "MessageOutputLengthError" -> getAppString(R.string.error_output_length)
            "ContentFilterError" -> getAppString(R.string.error_content_filter)
            "APIError" -> getAppString(R.string.error_api)
            "UnknownError" -> getAppString(R.string.error_model_failed)
            else -> getAppString(R.string.error_model_failed)
        }
        return if (message.isNullOrBlank()) prefix else "$prefix\n$message"
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
            "question.replied", "question.rejected" -> {
                val sendId = props?.get("requestID")?.jsonPrimitive?.contentOrNull
                if (sendId != null) {
                    _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == sendId }
                }
            }
            "permission.asked" -> {
                val sid = props?.get("sessionID")?.jsonPrimitive?.contentOrNull
                if (sid == active) {
                    runCatching {
                        val p = json.decodeFromString(PermissionRequest.serializer(), props.toString())
                        _pendingPermissions.value = _pendingPermissions.value.filterNot { it.id == p.id } + p
                    }
                }
            }
            "permission.replied" -> {
                val sendId = props?.get("requestID")?.jsonPrimitive?.contentOrNull
                if (sendId != null) {
                    _pendingPermissions.value = _pendingPermissions.value.filterNot { it.id == sendId }
                }
            }
            "session.status", "session.idle" -> {
                val sid = props?.get("sessionID")?.jsonPrimitive?.contentOrNull ?: return
                if (type == "session.idle") {
                    val wasBusy = sessionBusy.remove(sid) == true
                    if (sid == active) {
                        _sending.value = false
                        val elapsed = lastUserSendTime?.let { System.currentTimeMillis() - it }
                        if (elapsed != null && elapsed > 0) _sessionElapsed.value = elapsed
                    }
                    if (wasBusy) notifySessionDone(sid, _sessionTokens.value?.total ?: 0L)
                } else {
                    val st = props?.get("status")?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                    sessionBusy[sid] = st == "busy"
                    if (sid == active) {
                        _sending.value = st == "busy"
                    }
                }
            }
            "session.compacted" -> {
                val sid = props?.get("sessionID")?.jsonPrimitive?.contentOrNull
                if (sid != active) return
                val notice = ChatMessage(
                    id = "system-compact-${System.currentTimeMillis()}",
                    role = "system",
                    text = getAppString(R.string.compact_notice),
                )
                _messages.value = _messages.value + notice
            }
            "session.error" -> {
                val sid = props?.get("sessionID")?.jsonPrimitive?.contentOrNull
                if (sid != null && sid != active) return
                val errText = describeSessionError(props?.get("error")?.jsonObject)
                val errMsg = ChatMessage(
                    id = "session-error-${System.currentTimeMillis()}",
                    role = "error",
                    text = errText,
                )
                _messages.value = _messages.value + errMsg
                _sending.value = false
            }
            "todo.updated" -> {
                val sid = props?.get("sessionID")?.jsonPrimitive?.contentOrNull
                if (sid != active) return
                val todos = props?.get("todos")?.jsonArray ?: return
                _todos.value = todos.mapIndexed { i, t ->
                    val o = t.jsonObject
                    TodoUi(
                        id = "todo-$i-${System.currentTimeMillis()}",
                        content = o["content"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = o["status"]?.jsonPrimitive?.contentOrNull ?: "pending",
                    )
                }
            }
            "message.updated" -> {
                val info = props?.get("info")?.jsonObject ?: return
                val sid = info["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
                if (sid != active) return
                val mid = info["id"]?.jsonPrimitive?.contentOrNull ?: return
                val role = info["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
                if (role != "user" && _messages.value.none { it.id == mid }) {
                    val model = info["modelID"]?.jsonPrimitive?.contentOrNull
                    val tokens = info["tokens"]?.let {
                        runCatching { json.decodeFromString(Tokens.serializer(), it.toString()) }.getOrNull()
                    }
                    val created = info["time"]?.jsonObject?.get("created")?.jsonPrimitive?.contentOrNull
                        ?.toLongOrNull()
                    val tokenTotal = tokens?.total ?: 0L
                    if (tokenTotal > 0) {
                        _cumulativeTokens.value += tokenTotal
                    }
                    _messages.value = _messages.value + ChatMessage(
                        id = mid,
                        role = role,
                        text = "",
                        model = model,
                        tokens = tokens,
                        time = serverTimeToMillis(created),
                        cumulativeTokens = _cumulativeTokens.value,
                    )
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
                    _messages.value + ChatMessage(id = mid, role = "assistant", text = delta, time = System.currentTimeMillis(), cumulativeTokens = _cumulativeTokens.value)
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

data class UpdateInfo(
    val version: String,
    val url: String,
)
