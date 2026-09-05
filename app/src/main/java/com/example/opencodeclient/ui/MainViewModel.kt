package com.example.opencodeclient.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.opencodeclient.BuildConfig
import com.example.opencodeclient.R
import java.util.Locale
import com.example.opencodeclient.data.CapabilityCatalog
import com.example.opencodeclient.data.CapabilityReport
import com.example.opencodeclient.data.Command
import com.example.opencodeclient.data.FeatureStatus
import com.example.opencodeclient.data.Message
import com.example.opencodeclient.data.ModelInfo
import com.example.opencodeclient.data.OpenCodeClient
import com.example.opencodeclient.data.Part
import com.example.opencodeclient.data.PermissionRequest
import com.example.opencodeclient.data.Project
import com.example.opencodeclient.data.QuestionRequest
import com.example.opencodeclient.data.ServerProfile
import com.example.opencodeclient.data.Session
import com.example.opencodeclient.data.SettingsRepository
import com.example.opencodeclient.data.StoredHistoryStats
import com.example.opencodeclient.data.Tokens
import com.example.opencodeclient.data.Updater
import com.example.opencodeclient.data.promptTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

data class HistoryStats(
    val totalElapsed: Long,
    val messageCount: Long,
    val userMessages: Long = 0L,
    val assistantMessages: Long = 0L,
    val exchanges: Long = 0L,
    val toolCalls: Long = 0L,
    val firstMessages: List<String> = emptyList(),
    val computed: Boolean,
    val error: String? = null,
    val fallbackSpanMs: Long = 0L,
    val lastTimestamp: Long = 0L,
    val lastMessageId: String = "",
)

data class HistoryProgress(
    val fetched: Int,
    val lastTimestamp: Long,
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

    private val _collapsedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedMessageIds: StateFlow<Set<String>> = _collapsedMessageIds.asStateFlow()

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

    private val _sessionTotalElapsed = MutableStateFlow<Long?>(null)
    val sessionTotalElapsed: StateFlow<Long?> = _sessionTotalElapsed.asStateFlow()

    private val _historyStats = MutableStateFlow<HistoryStats?>(null)
    val historyStats: StateFlow<HistoryStats?> = _historyStats.asStateFlow()
    private val _computingHistory = MutableStateFlow(false)
    val computingHistory: StateFlow<Boolean> = _computingHistory.asStateFlow()
    private val _historyProgress = MutableStateFlow<HistoryProgress?>(null)
    val historyProgress: StateFlow<HistoryProgress?> = _historyProgress.asStateFlow()
    private var historyJob: Job? = null

    private val _storedStats = MutableStateFlow<Map<String, StoredHistoryStats>>(emptyMap())
    val storedStats: StateFlow<Map<String, StoredHistoryStats>> = _storedStats.asStateFlow()
    private val _autoTiming = MutableStateFlow(false)
    val autoTiming: StateFlow<Boolean> = _autoTiming.asStateFlow()
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _tokenHistory = MutableStateFlow<Map<String, Long>>(emptyMap())
    val tokenHistory: StateFlow<Map<String, Long>> = _tokenHistory.asStateFlow()
    private val _tokenHistoryLoading = MutableStateFlow(false)
    val tokenHistoryLoading: StateFlow<Boolean> = _tokenHistoryLoading.asStateFlow()
    private val _tokenElapsed = MutableStateFlow<Map<String, Long>>(emptyMap())
    val tokenElapsed: StateFlow<Map<String, Long>> = _tokenElapsed.asStateFlow()
    private val _hourByMonth = MutableStateFlow<Map<String, Map<Int, Long>>>(emptyMap())
    val hourByMonth: StateFlow<Map<String, Map<Int, Long>>> = _hourByMonth.asStateFlow()
    private val _hourByWeek = MutableStateFlow<Map<String, Map<Int, Long>>>(emptyMap())

    val hourByWeek: StateFlow<Map<String, Map<Int, Long>>> = _hourByWeek.asStateFlow()

    private val _tokenSync = MutableStateFlow(0L)

    private val _tokenSyncedAt = MutableStateFlow(0L)

    val tokenSyncedAt: StateFlow<Long> = _tokenSyncedAt.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private var sendingWatchdog: Job? = null
    private val SENDING_WATCHDOG_MS = 60_000L

    private val json = Json { ignoreUnknownKeys = true }

    private var eventJob: Job? = null

    private val _authUsername = MutableStateFlow<String?>(null)
    val authUsername: StateFlow<String?> = _authUsername.asStateFlow()

    private val _authPassword = MutableStateFlow<String?>(null)
    val authPassword: StateFlow<String?> = _authPassword.asStateFlow()

    private val _servers = MutableStateFlow<List<ServerProfile>>(emptyList())
    val servers: StateFlow<List<ServerProfile>> = _servers.asStateFlow()

    private val _pendingQuestions = MutableStateFlow<List<QuestionRequest>>(emptyList())
    private val _exportMarkdown = MutableStateFlow<String?>(null)
    val exportMarkdown = _exportMarkdown.asStateFlow()
    val pendingQuestions: StateFlow<List<QuestionRequest>> = _pendingQuestions.asStateFlow()

    private val _commands = MutableStateFlow<List<Command>>(emptyList())
    val commands: StateFlow<List<Command>> = _commands.asStateFlow()

    private val _capabilities = MutableStateFlow<CapabilityReport?>(null)
    val capabilities: StateFlow<CapabilityReport?> = _capabilities.asStateFlow()

    private val _capabilitiesDetecting = MutableStateFlow(false)
    val capabilitiesDetecting: StateFlow<Boolean> = _capabilitiesDetecting.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    private val _commandsLoaded = MutableStateFlow(false)

    private val _downloadPercent = MutableStateFlow(-1)
    val downloadPercent: StateFlow<Int> = _downloadPercent.asStateFlow()
    private val _downloadDone = MutableStateFlow(0L)
    val downloadDone: StateFlow<Long> = _downloadDone.asStateFlow()
    private val _downloadTotal = MutableStateFlow(0L)
    val downloadTotal: StateFlow<Long> = _downloadTotal.asStateFlow()
    private val _downloadSpeed = MutableStateFlow(0L)
    val downloadSpeed: StateFlow<Long> = _downloadSpeed.asStateFlow()

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
    private val _mirror = MutableStateFlow(false)
    val mirror: StateFlow<Boolean> = _mirror.asStateFlow()

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

    val featureStatus: StateFlow<List<FeatureStatus>> =
        combine(_capabilities, _commands, _commandsLoaded, _serverVersion) {
                report, cmds, loaded, version ->
            CapabilityCatalog.stateForAll(
                report,
                if (loaded) cmds.map { it.name }.toSet() else null,
                version,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            settings.mirror.collect { _mirror.value = it }
        }
        viewModelScope.launch {
            settings.userBubbleColor.collect { _userBubbleColor.value = it }
        }
        viewModelScope.launch {
            settings.assistantBubbleColor.collect { _assistantBubbleColor.value = it }
        }
        viewModelScope.launch {
            settings.historyStats.collect { _storedStats.value = it }
        }
        viewModelScope.launch {
            settings.autoUpdateTiming.collect { _autoTiming.value = it }
        }
        viewModelScope.launch {
            settings.favorites.collect { _favorites.value = it }
        }
        viewModelScope.launch {
            settings.tokenHistory.collect { _tokenHistory.value = it }
        }
        viewModelScope.launch {
            settings.tokenElapsed.collect { _tokenElapsed.value = it }
        }
        viewModelScope.launch {
            settings.tokenMonth.collect { _hourByMonth.value = it }
        }
        viewModelScope.launch {
            settings.tokenWeek.collect { _hourByWeek.value = it }
        }
        viewModelScope.launch {
            settings.tokenSync.collect { _tokenSync.value = it }
        }
        viewModelScope.launch {
            settings.tokenSyncedAt.collect { _tokenSyncedAt.value = it }
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
        _downloadPercent.value = progress
        _downloadDone.value = downloaded
        _downloadTotal.value = total
        _downloadSpeed.value = speed
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

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.example.opencodeclient.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, com.example.opencodeclient.DownloadCancelReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = android.app.Notification.Builder(context, "download")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_title))
            .setContentText(fullText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.cancel), cancelIntent)
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

    fun dismissDownload() {
        _downloadPercent.value = -1
        _downloadDone.value = 0L
        _downloadTotal.value = 0L
        _downloadSpeed.value = 0L
        cancelDownloadNotification()
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

    private fun showStreamingNotification() {
        val context = getApplication<Application>()
        val notification = android.app.Notification.Builder(context, "session_status")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getAppString(R.string.notify_streaming_title))
            .setContentText(getAppString(R.string.notify_streaming))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            context.getSystemService(Context.NOTIFICATION_SERVICE)?.let { service ->
                (service as? NotificationManager)?.notify(1003, notification)
            }
        } catch (_: Exception) {
            // permission not granted
        }
    }

    private fun cancelStreamingNotification() {
        val context = getApplication<Application>()
        try {
            context.getSystemService(Context.NOTIFICATION_SERVICE)?.let { service ->
                (service as? NotificationManager)?.cancel(1003)
            }
        } catch (_: Exception) {
        }
    }

    fun loadTokenHistory() = runTokenLoad(incremental = false)

    fun incrementTokenHistory() = runTokenLoad(incremental = true)

    private fun runTokenLoad(incremental: Boolean) {
        if (_tokenHistoryLoading.value) return
        _tokenHistoryLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val c = client ?: return@withContext
                    val zone = java.time.ZoneId.systemDefault()
                    val now = java.time.LocalDate.now(zone)
                    val firstDow = java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek.value
                    val currentWeekStart = now.with(
                        java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(firstDow))
                    )
                    val monthKeys = (0 until 12).map { java.time.YearMonth.now().minusMonths(it.toLong()).toString() }
                    val weekStartDates = (0 until 16).map { currentWeekStart.minusWeeks(it.toLong()) }
                    val weekStartDatesStr = weekStartDates.map { it.toString() }
                    val weekStartSet = weekStartDatesStr.toSet()

                    val baseSync = if (incremental) _tokenSync.value else 0L
                    val hasFreshCache = incremental && baseSync > 0L

                    val tokens = (if (incremental) _tokenHistory.value else emptyMap()).toMutableMap()
                    val elapsed = (if (incremental) _tokenElapsed.value else emptyMap()).toMutableMap()
                    val month = (if (incremental) _hourByMonth.value else emptyMap())
                        .mapValues { (_, m) -> m.toMutableMap() }.toMutableMap()
                    val week = (if (incremental) _hourByWeek.value else emptyMap())
                        .mapValues { (_, m) -> m.toMutableMap() }.toMutableMap()

                    var maxMsgMs = baseSync

                    c.sessions().forEach { s ->
                        coroutineContext.ensureActive()
                        val messages = if (hasFreshCache) {
                            runCatching { c.sessionMessagesSince(s.id, baseSync) }.getOrNull()
                        } else {
                            runCatching { c.sessionMessagesAll(s.id) }.getOrNull()
                        } ?: return@forEach
                        var turnStart = 0L
                        var turnEnd = 0L
                        for ((msg, _) in messages) {
                            coroutineContext.ensureActive()
                            val created = serverTimeToMillis(msg.time?.created)
                            val completed = serverTimeToMillis(msg.time?.completed)
                            if (created <= 0L) continue
                            if (created > maxMsgMs) maxMsgMs = created
                            val zdt = java.time.Instant.ofEpochMilli(created).atZone(zone)
                            val day = zdt.toLocalDate()
                            val toks = msg.tokens
                            val total = toks?.total
                                ?: ((toks?.input ?: 0L) + (toks?.output ?: 0L) + (toks?.reasoning ?: 0L))
                            if (total > 0L) {
                                tokens[day.toString()] = (tokens[day.toString()] ?: 0L) + total
                                val hour = zdt.hour
                                val mKey = day.toString().substring(0, 7)
                                val mBuckets = month.getOrPut(mKey) { mutableMapOf() }
                                mBuckets[hour] = (mBuckets[hour] ?: 0L) + total
                                val ws = day.with(
                                    java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(firstDow))
                                ).toString()
                                if (ws in weekStartSet) {
                                    val wBuckets = week.getOrPut(ws) { mutableMapOf() }
                                    wBuckets[hour] = (wBuckets[hour] ?: 0L) + total
                                }
                            }
                            if (msg.role == "user") {
                                if (turnStart > 0L && turnEnd > turnStart) {
                                    val d = java.time.Instant.ofEpochMilli(turnStart).atZone(zone).toLocalDate().toString()
                                    elapsed[d] = (elapsed[d] ?: 0L) + (turnEnd - turnStart)
                                }
                                turnStart = created
                                turnEnd = created
                            } else if (turnStart > 0L && completed > turnEnd) {
                                turnEnd = completed
                            }
                        }
                        if (turnStart > 0L && turnEnd > turnStart) {
                            val d = java.time.Instant.ofEpochMilli(turnStart).atZone(zone).toLocalDate().toString()
                            elapsed[d] = (elapsed[d] ?: 0L) + (turnEnd - turnStart)
                        }
                    }

                    val monthOut = month.filterKeys { it in monthKeys }
                    val weekOut = week.filterKeys { it in weekStartDatesStr }

                    _tokenHistory.value = tokens
                    _tokenElapsed.value = elapsed
                    _hourByMonth.value = monthOut
                    _hourByWeek.value = weekOut
                    _tokenSync.value = maxMsgMs
                    if (coroutineContext.isActive) {
                        val syncedAtMs = System.currentTimeMillis()
                        _tokenSyncedAt.value = syncedAtMs
                        settings.saveTokenHistory(tokens, elapsed)
                        settings.saveTokenCalendar(monthOut, weekOut, maxMsgMs, syncedAtMs)
                    }
                }
            } finally {
                _tokenHistoryLoading.value = false
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
                _serverVersion.value = health.version
                client = cli
                _activeSession.value = null
                _messages.value = emptyList()
                _sessionTokens.value = null
                _contextWindow.value = 0L
                _promptTokens.value = 0L
                _cumulativeTokens.value = 0L
                lastUserSendTime = null
                _sessionElapsed.value = null
                _sessionTotalElapsed.value = null
                setSending(false)
                settings.setServerUrl(serverUrl)
                settings.setAuth(username, password)
                _serverUrl.value = serverUrl
                _authUsername.value = username
                _authPassword.value = password
                saveServerProfile(ServerProfile(serverUrl, username, password))
                _connectionState.value = UiState.Idle
                observeEvents()
                loadWorkspace()
                probeCapabilities(cli)
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

    private fun probeCapabilities(cli: OpenCodeClient) {
        viewModelScope.launch {
            _capabilitiesDetecting.value = true
            try {
                val report = withContext(Dispatchers.IO) { cli.probeCapabilities() }
                cli.applyCapabilities(report)
                _capabilities.value = report
            } catch (e: Exception) {
                _capabilities.value = null
            } finally {
                _capabilitiesDetecting.value = false
            }
        }
    }

    fun refreshCapabilities() {
        val c = client ?: return
        probeCapabilities(c)
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

    fun setMirror(enabled: Boolean) {
        viewModelScope.launch { settings.setMirror(enabled) }
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
                    val baseUrl = rel.apkUrl ?: rel.htmlUrl
                    _updateInfo.value = UpdateInfo(version = rel.tagName, url = Updater.mirrorApkUrl(baseUrl, _mirror.value))
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
                    _serverVersion.value = cli.health().version
                    client = cli
                    probeCapabilities(cli)
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
            _commandsLoaded.value = true
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

    suspend fun currentWorkingDir(): String? {
        val c = client ?: return null
        return try {
            withContext(Dispatchers.IO) { c.currentProject()?.worktree }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun listServerFiles(locationDir: String?, path: String? = null): List<com.example.opencodeclient.data.FileNode> {
        val c = client ?: return emptyList()
        return try {
            withContext(Dispatchers.IO) { c.listDirectory(locationDir, path) }
        } catch (_: Exception) {
            emptyList()
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

    fun toggleMessageCollapsed(id: String?) {
        if (id == null) return
        val current = _collapsedMessageIds.value
        _collapsedMessageIds.value = if (id in current) current - id else current + id
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
                recomputeSessionTotalElapsed()
            } catch (_: Exception) {
                // ignore, keep current
            }
        }
    }

    fun refreshSession() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
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

    private fun recomputeSessionTotalElapsed() {
        var lastUser = 0L
        var total = 0L
        for (m in _messages.value) {
            if (m.time <= 0L) continue
            if (m.role == "user") {
                lastUser = m.time
            } else if (lastUser > 0L && m.time > lastUser) {
                total += m.time - lastUser
            }
        }
        _sessionTotalElapsed.value = if (total > 0L) total else null
    }

    private fun setSending(value: Boolean) {
        _sending.value = value
        sendingWatchdog?.cancel()
        if (value) {
            showStreamingNotification()
            sendingWatchdog = viewModelScope.launch {
                delay(SENDING_WATCHDOG_MS)
                _sending.value = false
                cancelStreamingNotification()
            }
        } else {
            cancelStreamingNotification()
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            setSending(true)
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
                setSending(false)
            }
        }
    }

    fun regenerate() {
        val lastUserMessage = _messages.value.lastOrNull { it.role == "user" } ?: return
        send(lastUserMessage.text)
    }

    fun compactSession() {
        val c = client ?: return
        val s = _activeSession.value ?: return
        val sid = s.id
        if (_sending.value) return
        val providerId = s.model?.providerId?.takeIf { it.isNotBlank() }
        val modelId = s.model?.id?.takeIf { it.isNotBlank() } ?: _currentModelId.value?.takeIf { it.isNotBlank() }
        if (providerId == null || modelId == null) {
            _messages.value = _messages.value + ChatMessage(
                id = "err-compact-${System.currentTimeMillis()}",
                role = "error",
                text = getAppString(R.string.compact_no_model),
            )
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.summarizeSession(sid, providerId, modelId) }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    id = "err-compact-${System.currentTimeMillis()}",
                    role = "error",
                    text = e.message ?: getAppString(R.string.compact_failed),
                )
            }
        }
    }

    fun sendWithFile(text: String, fileName: String, content: String) {
        if (text.isBlank() && content.isBlank()) return
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        val combined = buildString {
            if (text.isNotBlank()) appendLine(text.trim())
            appendLine("\n[File: $fileName]")
            appendLine(content)
        }
        val prompt = if (text.isBlank()) "Read the attached file `$fileName` and respond." else text.trim()
        viewModelScope.launch {
            setSending(true)
            lastUserSendTime = System.currentTimeMillis()
            _sessionElapsed.value = null
            _messages.value = _messages.value + ChatMessage(
                id = "user-${System.currentTimeMillis()}",
                role = "user",
                text = prompt,
                time = System.currentTimeMillis(),
            )
            try {
                withContext(Dispatchers.IO) { c.sendPromptAsync(sid, combined) }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    id = "err-${System.currentTimeMillis()}",
                    role = "error",
text = e.message ?: getAppString(R.string.send_failed),
                )
                setSending(false)
            }
        }
    }

    fun runCommand(command: Command) {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            setSending(true)
            try {
                withContext(Dispatchers.IO) { c.executeCommand(sid, command.name) }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    id = "err-${System.currentTimeMillis()}",
                    role = "error",
                    text = e.message ?: getAppString(R.string.send_failed),
                )
                setSending(false)
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

    fun exportChatAsMarkdown() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        val messages = _messages.value
        viewModelScope.launch {
            val sb = StringBuilder()
            try {
                val all = withContext(Dispatchers.IO) { c.sessionMessagesAll(sid) }
                if (all.isNotEmpty()) {
                    all.forEach { (msg, parts) ->
                        appendV2Export(sb, msg, parts)
                    }
                } else {
                    messages.forEach { msg -> appendChatExport(sb, msg) }
                }
            } catch (_: Exception) {
                messages.forEach { msg -> appendChatExport(sb, msg) }
            }
            _exportMarkdown.value = sb.toString()
        }
    }

    fun computeHistoryStats() {
        if (_computingHistory.value) return
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        _computingHistory.value = true
        _historyProgress.value = HistoryProgress(0, 0L)
        historyJob = viewModelScope.launch {
            try {
                val tail = withContext(Dispatchers.IO) {
                    c.sessionMessagesAll(sid) { fetched, lastTime ->
                        _historyProgress.value = HistoryProgress(fetched, lastTime)
                    }
                }
                if (!coroutineContext.isActive) return@launch
                val stats = if (tail.isEmpty()) {
                    HistoryStats(totalElapsed = 0L, messageCount = 0L, computed = true, lastMessageId = "")
                } else {
                    computeElapsedStats(tail)
                }
                _historyStats.value = stats
                persistStats(sid, stats)
            } finally {
                _computingHistory.value = false
            }
        }
    }

    fun incrementHistoryStats() {
        if (_computingHistory.value) return
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        val stored = _storedStats.value[sid]
        _computingHistory.value = true
        _historyProgress.value = HistoryProgress(0, 0L)
        historyJob = viewModelScope.launch {
            try {
                val validBoundary = stored != null && stored.lastMessageId.isNotEmpty() && stored.lastTimestamp > 0L
                val newTail = withContext(Dispatchers.IO) {
                    if (validBoundary) {
                        c.sessionMessagesSince(sid, stored.lastTimestamp, onProgress = { fetched, earliest ->
                            _historyProgress.value = HistoryProgress(fetched, earliest)
                        })
                    } else {
                        c.sessionMessagesAll(sid, onProgress = { fetched, lastTime ->
                            _historyProgress.value = HistoryProgress(fetched, lastTime)
                        })
                    }
                }
                if (!coroutineContext.isActive) return@launch
                val stats: HistoryStats = when {
                    !validBoundary -> if (newTail.isEmpty())
                        HistoryStats(totalElapsed = 0L, messageCount = 0L, computed = true, lastMessageId = "")
                    else computeElapsedStats(newTail)

                    newTail.isEmpty() -> stored.toHistoryStats()
                    else -> incrementalFromSince(newTail, stored)
                }
                _historyStats.value = stats
                persistStats(sid, stats)
            } finally {
                _computingHistory.value = false
            }
        }
    }

    private fun StoredHistoryStats.toHistoryStats() = HistoryStats(
        totalElapsed = totalElapsed,
        messageCount = messageCount,
        userMessages = userMessages,
        assistantMessages = assistantMessages,
        exchanges = userMessages,
        toolCalls = toolCalls,
        firstMessages = firstMessages,
        computed = true,
        lastTimestamp = lastTimestamp,
        lastMessageId = lastMessageId,
    )

    private fun incrementalFromSince(newTail: List<Pair<Message, List<Part>>>, stored: StoredHistoryStats): HistoryStats {
        var total = 0L
        var turnStart = 0L
        var turnEnd = 0L
        var latest = 0L
        var userCount = 0L
        var assistantCount = 0L
        var toolCount = 0L
        for ((msg, parts) in newTail) {
            val created = serverTimeToMillis(msg.time?.created)
            val completed = serverTimeToMillis(msg.time?.completed)
            if (created > latest) latest = created
            if (completed > latest) latest = completed
            toolCount += parts.count { it.type == "tool" }
            if (msg.role == "user") {
                userCount++
                if (turnStart > 0L && turnEnd > turnStart) total += turnEnd - turnStart
                turnStart = created
                turnEnd = created
            } else {
                if (msg.role == "assistant") assistantCount++
                if (turnStart > 0L && completed > turnEnd) turnEnd = completed
            }
        }
        val lastMsg = newTail.last().first
        val lastCompleted = serverTimeToMillis(lastMsg.time?.completed)
        val lastCreated = serverTimeToMillis(lastMsg.time?.created)
        if (lastMsg.role != "user" && lastCompleted > 0L && turnStart > 0L && turnEnd > turnStart) {
            total += turnEnd - turnStart
        }
        return HistoryStats(
            totalElapsed = stored.totalElapsed + total,
            messageCount = stored.messageCount + newTail.size.toLong(),
            userMessages = stored.userMessages + userCount,
            assistantMessages = stored.assistantMessages + assistantCount,
            exchanges = stored.userMessages + userCount,
            toolCalls = stored.toolCalls + toolCount,
            firstMessages = stored.firstMessages,
            computed = true,
            lastTimestamp = maxOf(stored.lastTimestamp, latest, lastCompleted, lastCreated),
            lastMessageId = lastMsg.id,
        )
    }

    private fun computeElapsedStats(tail: List<Pair<Message, List<Part>>>): HistoryStats {
        var total = 0L
        var turnStart = 0L
        var turnEnd = 0L
        var latest = 0L
        val firstMessages = mutableListOf<String>()
        var userCount = 0L
        var assistantCount = 0L
        var toolCount = 0L
        for ((msg, parts) in tail) {
            val created = serverTimeToMillis(msg.time?.created)
            val completed = serverTimeToMillis(msg.time?.completed)
            if (created > latest) latest = created
            if (completed > latest) latest = completed
            toolCount += parts.count { it.type == "tool" }
            if (msg.role == "user") {
                userCount++
                if (turnStart > 0L && turnEnd > turnStart) total += turnEnd - turnStart
                turnStart = created
                turnEnd = created
                if (firstMessages.size < 5) {
                    val t = parts.firstOrNull { it.type == "text" }?.text ?: msg.id
                    firstMessages.add(t.take(120))
                }
            } else {
                if (msg.role == "assistant") assistantCount++
                if (turnStart > 0L && completed > turnEnd) turnEnd = completed
            }
        }
        val lastMsg = tail.last().first
        val lastCompleted = serverTimeToMillis(lastMsg.time?.completed)
        val lastCreated = serverTimeToMillis(lastMsg.time?.created)
        if (lastMsg.role != "user" && lastCompleted > 0L && turnStart > 0L && turnEnd > turnStart) {
            total += turnEnd - turnStart
        }
        return HistoryStats(
            totalElapsed = total, messageCount = tail.size.toLong(),
            userMessages = userCount, assistantMessages = assistantCount,
            exchanges = userCount, toolCalls = toolCount,
            firstMessages = firstMessages, computed = true,
            lastTimestamp = maxOf(latest, lastCompleted, lastCreated),
            lastMessageId = lastMsg.id,
        )
    }

    private fun incrementalStats(tail: List<Pair<Message, List<Part>>>, stored: StoredHistoryStats): HistoryStats {
        val idx = tail.indexOfFirst { it.first.id == stored.lastMessageId }
        if (idx < 0) return computeElapsedStats(tail)
        val newMessages = tail.subList(idx + 1, tail.size)
        if (newMessages.isEmpty()) {
            return HistoryStats(
                totalElapsed = stored.totalElapsed,
                messageCount = tail.size.toLong(),
                userMessages = stored.userMessages,
                assistantMessages = stored.assistantMessages,
                exchanges = stored.userMessages,
                toolCalls = stored.toolCalls,
                firstMessages = stored.firstMessages,
                computed = true,
                lastTimestamp = stored.lastTimestamp,
                lastMessageId = stored.lastMessageId,
            )
        }
        var total = 0L
        var turnStart = 0L
        var turnEnd = 0L
        var latest = 0L
        var userCount = 0L
        var assistantCount = 0L
        var toolCount = 0L
        for ((msg, parts) in newMessages) {
            val created = serverTimeToMillis(msg.time?.created)
            val completed = serverTimeToMillis(msg.time?.completed)
            if (created > latest) latest = created
            if (completed > latest) latest = completed
            toolCount += parts.count { it.type == "tool" }
            if (msg.role == "user") {
                userCount++
                if (turnStart > 0L && turnEnd > turnStart) total += turnEnd - turnStart
                turnStart = created
                turnEnd = created
            } else {
                if (msg.role == "assistant") assistantCount++
                if (turnStart > 0L && completed > turnEnd) turnEnd = completed
            }
        }
        val lastMsg = newMessages.last().first
        val lastCompleted = serverTimeToMillis(lastMsg.time?.completed)
        val lastCreated = serverTimeToMillis(lastMsg.time?.created)
        if (lastMsg.role != "user" && lastCompleted > 0L && turnStart > 0L && turnEnd > turnStart) {
            total += turnEnd - turnStart
        }
        return HistoryStats(
            totalElapsed = stored.totalElapsed + total,
            messageCount = tail.size.toLong(),
            userMessages = stored.userMessages + userCount,
            assistantMessages = stored.assistantMessages + assistantCount,
            exchanges = stored.userMessages + userCount,
            toolCalls = stored.toolCalls + toolCount,
            firstMessages = stored.firstMessages,
            computed = true,
            lastTimestamp = maxOf(stored.lastTimestamp, latest, lastCompleted, lastCreated),
            lastMessageId = tail.last().first.id,
        )
    }

    private suspend fun persistStats(sid: String, stats: HistoryStats) {
        settings.saveHistoryStats(
            sid,
            StoredHistoryStats(
                totalElapsed = stats.totalElapsed,
                messageCount = stats.messageCount,
                userMessages = stats.userMessages,
                assistantMessages = stats.assistantMessages,
                toolCalls = stats.toolCalls,
                firstMessages = stats.firstMessages,
                lastTimestamp = stats.lastTimestamp,
                lastMessageId = stats.lastMessageId,
            ),
        )
    }

    fun cancelHistoryStats() {
        historyJob?.cancel()
        historyJob = null
        _historyStats.value = null
        _computingHistory.value = false
    }

    fun dismissHistoryStats() {
        _historyStats.value = null
    }

    fun setAutoUpdateTiming(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoUpdateTiming(enabled) }
    }

    fun toggleFavorite(sessionId: String) {
        viewModelScope.launch { settings.toggleFavorite(sessionId) }
    }

    fun autoUpdateTimingForSession(sid: String) {
        if (!_autoTiming.value) return
        val c = client ?: return
        if (_computingHistory.value) return
        val stored = _storedStats.value[sid]
        historyJob = viewModelScope.launch {
            try {
                val tail = withContext(Dispatchers.IO) {
                    c.sessionMessagesAll(sid) { fetched, lastTime ->
                        _historyProgress.value = HistoryProgress(fetched, lastTime)
                    }
                }
                if (!coroutineContext.isActive) return@launch
                val stats = if (stored != null && stored.messageCount > 0 && stored.lastMessageId.isNotEmpty()) {
                    incrementalStats(tail, stored)
                } else {
                    if (tail.isEmpty()) HistoryStats(totalElapsed = 0L, messageCount = 0L, computed = true, lastMessageId = "")
                    else computeElapsedStats(tail)
                }
                if (coroutineContext.isActive) {
                    if (sid == _activeSession.value?.id) {
                        recomputeSessionElapsed()
                        recomputeSessionTotalElapsed()
                    }
                    persistStats(sid, stats)
                }
            } finally {
                _computingHistory.value = false
            }
        }
    }
    private fun appendChatExport(sb: StringBuilder, msg: ChatMessage) {
        val role = when (msg.role) {
            "user" -> "**User**"
            "assistant" -> "**Assistant**"
            "tool" -> "**Tool: ${msg.parts.firstOrNull()?.tool ?: "unknown"}**"
            else -> "**${msg.role}**"
        }
        sb.appendLine("$role\n")
        sb.appendLine(msg.text)
        if (msg.reasoning != null) {
            sb.appendLine("\n> Reasoning: ${msg.reasoning}")
        }
        for (part in msg.parts) {
            if (part.tool != null && part.toolOutput != null) {
                sb.appendLine("\n> Tool `${part.tool}` output:\n> ```\n> ${part.toolOutput.replace("\n", "\n> ")}\n> ```")
            }
        }
        sb.appendLine("\n---\n")
    }

    private fun appendV2Export(sb: StringBuilder, msg: Message, parts: List<Part>) {
        val role = msg.role ?: "unknown"
        val roleLabel = when (role) {
            "user" -> "**User**"
            "assistant" -> "**Assistant**"
            else -> "**$role**"
        }
        sb.appendLine("$roleLabel\n")
        val text = parts.filter { it.type == "text" }.joinToString("") { it.text ?: "" }
        if (text.isNotBlank()) sb.appendLine(text)
        val reasoning = parts.filter { it.type == "reasoning" }.mapNotNull { it.text }.joinToString("\n")
        if (reasoning.isNotBlank()) sb.appendLine("\n> Reasoning: $reasoning")
        for (part in parts) {
            if (part.type == "tool" && part.state?.output != null) {
                sb.appendLine("\n> Tool `${part.tool}` output:\n> ```\n> ${part.state.output.replace("\n", "\n> ")}\n> ```")
            }
        }
        sb.appendLine("\n---\n")
    }

    fun clearExportMarkdown() {
        _exportMarkdown.value = null
    }

    suspend fun listSessionDirFiles(relPath: String, locationDir: String): List<com.example.opencodeclient.data.FileNode> {
        val c = client ?: return emptyList()
        return try {
            withContext(Dispatchers.IO) { c.listDirectory(locationDir, relPath) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun readSessionFileContent(relPath: String, locationDir: String): String? {
        val c = client ?: return null
        return try {
            withContext(Dispatchers.IO) { c.readFileContent(locationDir, relPath) }
        } catch (_: Exception) {
            null
        }
    }

    fun sendFileInChat(path: String, content: String) {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            setSending(true)
            val prompt = if (content.isBlank()) "Read $path" else "Read the file `$path` and summarize its purpose:\n\n$content"
            _messages.value = _messages.value + ChatMessage(
                id = "user-${System.currentTimeMillis()}",
                role = "user",
                text = prompt,
                time = System.currentTimeMillis(),
            )
            try {
                withContext(Dispatchers.IO) { c.sendPromptAsync(sid, prompt) }
            } catch (_: Exception) {
                setSending(false)
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
            coroutineScope {
                launch {
                    while (true) {
                        refreshPendingPermissions()
                        delay(4000)
                    }
                }
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
    }

    private suspend fun refreshPendingPermissions() {
        val c = client ?: return
        val dir = _activeSession.value?.directory
        val fetched = runCatching {
            withContext(Dispatchers.IO) { c.pendingPermissions(dir) }
        }.getOrNull() ?: return
        _pendingPermissions.value = (_pendingPermissions.value + fetched).distinctBy { it.id }
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
                        setSending(false)
                        val elapsed = lastUserSendTime?.let { System.currentTimeMillis() - it }
                        if (elapsed != null && elapsed > 0) _sessionElapsed.value = elapsed
                        recomputeSessionTotalElapsed()
                        autoUpdateTimingForSession(sid)
                    }
                    if (wasBusy) notifySessionDone(sid, _sessionTokens.value?.total ?: 0L)
                } else {
                    val st = props?.get("status")?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                    sessionBusy[sid] = st == "busy"
                    if (sid == active) {
                        setSending(st == "busy")
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
                setSending(false)
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
