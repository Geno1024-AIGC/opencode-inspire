package com.geno1024.ai.occ.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geno1024.ai.occ.BuildConfig
import com.geno1024.ai.occ.R
import com.geno1024.ai.occ.SessionPollService
import com.geno1024.ai.occ.SessionWidgetProvider
import java.util.Locale
import com.geno1024.ai.occ.data.CapabilityCatalog
import com.geno1024.ai.occ.data.CapabilityReport
import com.geno1024.ai.occ.data.Command
import com.geno1024.ai.occ.data.FeatureStatus
import com.geno1024.ai.occ.data.HealthResponse
import com.geno1024.ai.occ.data.Message
import com.geno1024.ai.occ.data.ModelInfo
import com.geno1024.ai.occ.data.AgentInfo
import com.geno1024.ai.occ.data.IntegrationInfo
import com.geno1024.ai.occ.data.AgentClient
import com.geno1024.ai.occ.data.OpenCodeClient
import com.geno1024.ai.occ.data.Part
import com.geno1024.ai.occ.data.PermissionRequest
import com.geno1024.ai.occ.data.Project
import com.geno1024.ai.occ.data.QuestionRequest
import com.geno1024.ai.occ.data.ServerProfile
import com.geno1024.ai.occ.data.Session
import com.geno1024.ai.occ.data.SessionCache
import com.geno1024.ai.occ.data.SessionInfo
import com.geno1024.ai.occ.data.SessionV2Info
import com.geno1024.ai.occ.data.SettingsRepository
import com.geno1024.ai.occ.data.StoredHistoryStats
import com.geno1024.ai.occ.data.TokenDay
import com.geno1024.ai.occ.data.TokenRawBucket
import com.geno1024.ai.occ.data.TokenFormat
import com.geno1024.ai.occ.data.TokenModelStats
import com.geno1024.ai.occ.data.Tokens
import com.geno1024.ai.occ.data.Updater
import com.geno1024.ai.occ.data.UsageExportDoc
import com.geno1024.ai.occ.data.UsageExportResult
import com.geno1024.ai.occ.data.buildUsageCsv
import com.geno1024.ai.occ.data.buildUsageJson
import com.geno1024.ai.occ.data.promptTokens
import com.geno1024.ai.occ.data.saveTextFileToDownloads
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
    val error: String? = null,
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

data class SearchHit(
    val id: String,
    val role: String,
    val model: String? = null,
    val time: Long = 0L,
    val text: String,
    val snippet: String,
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
    private companion object {
        const val RAW_BUCKET_MS = 900_000L
    }

    private val settings = SettingsRepository(application)
    private val sessionCache = SessionCache(application)

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

    var client: AgentClient? = null
        private set

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _connectionState = MutableStateFlow<UiState>(UiState.Idle)
    val connectionState: StateFlow<UiState> = _connectionState.asStateFlow()

    private val _serverAlive = MutableStateFlow(false)
    val serverAlive: StateFlow<Boolean> = _serverAlive.asStateFlow()

    private val _workspaceState = MutableStateFlow<UiState>(UiState.Idle)
    val workspaceState: StateFlow<UiState> = _workspaceState.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectUi>>(emptyList())
    val projects: StateFlow<List<ProjectUi>> = _projects.asStateFlow()

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession: StateFlow<Session?> = _activeSession.asStateFlow()
    private val titleRefreshPending = mutableSetOf<String>()
    private val _childSessions = MutableStateFlow<List<Session>>(emptyList())
    val childSessions: StateFlow<List<Session>> = _childSessions.asStateFlow()
    private val _sessionDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val sessionDrafts: StateFlow<Map<String, String>> = _sessionDrafts.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _collapsedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedMessageIds: StateFlow<Set<String>> = _collapsedMessageIds.asStateFlow()

    private val _todos = MutableStateFlow<List<TodoUi>>(emptyList())
    val todos: StateFlow<List<TodoUi>> = _todos.asStateFlow()

    private val _sessionTokens = MutableStateFlow<Tokens?>(null)
    val sessionTokens: StateFlow<Tokens?> = _sessionTokens.asStateFlow()
    private val _sessionCost = MutableStateFlow(0.0)
    val sessionCost: StateFlow<Double> = _sessionCost.asStateFlow()

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
    private val _archived = MutableStateFlow<Set<String>>(emptySet())
    val archived: StateFlow<Set<String>> = _archived.asStateFlow()

    private val _ignoredPermissions = MutableStateFlow<Set<String>>(emptySet())
    val ignoredPermissions: StateFlow<Set<String>> = _ignoredPermissions.asStateFlow()

    private val _ignoredQuestions = MutableStateFlow<Set<String>>(emptySet())
    val ignoredQuestions: StateFlow<Set<String>> = _ignoredQuestions.asStateFlow()

    private val _offlineCacheAt = MutableStateFlow<Long?>(null)
    val offlineCacheAt: StateFlow<Long?> = _offlineCacheAt.asStateFlow()

    private val _tokenHistory = MutableStateFlow<Map<String, TokenDay>>(emptyMap())
    val tokenHistory: StateFlow<Map<String, TokenDay>> = _tokenHistory.asStateFlow()
    private val _tokenHistoryLoading = MutableStateFlow(false)
    val tokenHistoryLoading: StateFlow<Boolean> = _tokenHistoryLoading.asStateFlow()
    private val _tokenElapsed = MutableStateFlow<Map<String, Long>>(emptyMap())
    val tokenElapsed: StateFlow<Map<String, Long>> = _tokenElapsed.asStateFlow()
    private val _hourByMonth = MutableStateFlow<Map<String, Map<Int, TokenDay>>>(emptyMap())
    val hourByMonth: StateFlow<Map<String, Map<Int, TokenDay>>> = _hourByMonth.asStateFlow()
    private val _hourByWeek = MutableStateFlow<Map<String, Map<Int, TokenDay>>>(emptyMap())
    val hourByWeek: StateFlow<Map<String, Map<Int, TokenDay>>> = _hourByWeek.asStateFlow()
    private val _hourByDay = MutableStateFlow<Map<String, Map<Int, TokenDay>>>(emptyMap())
    val hourByDay: StateFlow<Map<String, Map<Int, TokenDay>>> = _hourByDay.asStateFlow()
    private val _tokenModelStats = MutableStateFlow<Map<String, TokenModelStats>>(emptyMap())
    val tokenModelStats: StateFlow<Map<String, TokenModelStats>> = _tokenModelStats.asStateFlow()
    private val _sessionModelTokens = MutableStateFlow<Map<String, Map<String, TokenDay>>>(emptyMap())
    val sessionModelTokens: StateFlow<Map<String, Map<String, TokenDay>>> = _sessionModelTokens.asStateFlow()

    private val _tokenRawBuckets = MutableStateFlow<List<TokenRawBucket>>(emptyList())
    val tokenRawBuckets: StateFlow<List<TokenRawBucket>> = _tokenRawBuckets.asStateFlow()

    private val _tokenRawSync = MutableStateFlow(0L)

    private val _dayStartOffset = MutableStateFlow<Int?>(null)
    val dayStartOffset: StateFlow<Int?> = _dayStartOffset.asStateFlow()

    private val _tokenSync = MutableStateFlow(0L)

    private val _tokenSyncedAt = MutableStateFlow(0L)

    private val _backgroundNotify = MutableStateFlow(false)
    val backgroundNotify: StateFlow<Boolean> = _backgroundNotify.asStateFlow()

    val tokenSyncedAt: StateFlow<Long> = _tokenSyncedAt.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private var sendingWatchdog: Job? = null
    private val SENDING_WATCHDOG_MS = 60_000L

    private val json = Json { ignoreUnknownKeys = true }

    private var eventJob: Job? = null
    private var heartbeatJob: Job? = null

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

    private val _olderCursor = MutableStateFlow<String?>(null)
    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()
    val hasOlderHistory: StateFlow<Boolean> =
        _olderCursor.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _searchingAll = MutableStateFlow(false)
    val searchingAll: StateFlow<Boolean> = _searchingAll.asStateFlow()
    private val _searchProgress = MutableStateFlow(0)
    val searchProgress: StateFlow<Int> = _searchProgress.asStateFlow()
    private val _searchResults = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchResults: StateFlow<List<SearchHit>> = _searchResults.asStateFlow()
    private val _searchError = MutableStateFlow(false)
    val searchError: StateFlow<Boolean> = _searchError.asStateFlow()

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
    private val _downloadDialogVisible = MutableStateFlow(false)
    val downloadDialogVisible: StateFlow<Boolean> = _downloadDialogVisible.asStateFlow()

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _currentModelId = MutableStateFlow<String?>(null)
    val currentModelId: StateFlow<String?> = _currentModelId.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val agents: StateFlow<List<AgentInfo>> = _agents.asStateFlow()

    private val _currentAgent = MutableStateFlow<String?>(null)
    val currentAgent: StateFlow<String?> = _currentAgent.asStateFlow()

    private val _integrations = MutableStateFlow<List<IntegrationInfo>>(emptyList())
    val integrations: StateFlow<List<IntegrationInfo>> = _integrations.asStateFlow()

    private val _pendingPermissions = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val pendingPermissions: StateFlow<List<PermissionRequest>> = _pendingPermissions.asStateFlow()

    private val _tokenFormat = MutableStateFlow(TokenFormat.DEFAULT)
    val tokenFormat: StateFlow<TokenFormat> = _tokenFormat.asStateFlow()
    private val _tableTimeFormat = MutableStateFlow(0)
    val tableTimeFormat: StateFlow<Int> = _tableTimeFormat.asStateFlow()
    private val _exportTransparent = MutableStateFlow(true)
    val exportTransparent: StateFlow<Boolean> = _exportTransparent.asStateFlow()
    private val _exportAuthor = MutableStateFlow("")
    val exportAuthor: StateFlow<String> = _exportAuthor.asStateFlow()

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
            val apk = settings.downloadedApk.first()
            val recorded = settings.installedVersion.first()
            if (apk != null && recorded != BuildConfig.VERSION_NAME) {
                deleteDownloadedApk(apk)
                settings.setDownloadedApk(null)
                settings.setInstalledVersion(BuildConfig.VERSION_NAME)
            }
        }
        viewModelScope.launch {
            settings.drafts.collect { _sessionDrafts.value = it }
        }
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
            settings.tokenFormat.collect { _tokenFormat.value = it }
        }
        viewModelScope.launch {
            settings.tableTimeFormat.collect { _tableTimeFormat.value = it }
        }
        viewModelScope.launch {
            settings.exportTransparent.collect { _exportTransparent.value = it }
        }
        viewModelScope.launch {
            settings.exportAuthor.collect { _exportAuthor.value = it }
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
            settings.archived.collect { _archived.value = it }
        }
        viewModelScope.launch {
            settings.ignoredPermissions.collect { _ignoredPermissions.value = it }
        }
        viewModelScope.launch {
            settings.ignoredQuestions.collect { _ignoredQuestions.value = it }
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
            settings.tokenDayHours.collect { _hourByDay.value = it }
        }
        viewModelScope.launch {
            settings.tokenModelStats.collect { _tokenModelStats.value = it }
        }
        viewModelScope.launch {
            settings.sessionModelTokens.collect { _sessionModelTokens.value = it }
        }
        viewModelScope.launch {
            settings.tokenSync.collect { _tokenSync.value = it }
        }
        viewModelScope.launch {
            settings.backgroundNotify.collect { _backgroundNotify.value = it }
        }
        viewModelScope.launch {
            settings.tokenSyncedAt.collect { _tokenSyncedAt.value = it }
        }
        viewModelScope.launch {
            settings.tokenRawBuckets.collect { _tokenRawBuckets.value = it }
        }
        viewModelScope.launch {
            settings.tokenRawSync.collect { _tokenRawSync.value = it }
        }
        viewModelScope.launch {
            settings.dayStartOffset.collect { _dayStartOffset.value = it }
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

    fun showDownloadDialog() {
        _downloadDialogVisible.value = true
    }

    fun dismissDownloadDialog() {
        _downloadDialogVisible.value = false
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
                formatBytes(downloaded),
                formatBytes(total),
            )
        } else {
            context.getString(R.string.download_progress_unknown, formatBytes(downloaded))
        }
        val fullText = if (eta.isNotEmpty()) "$detail\n${formatSpeed(speed)}\n$eta" else "$detail\n${formatSpeed(speed)}"

        val contentIntent = PendingIntent.getActivity(
            context, 1002,
            Intent(context, com.geno1024.ai.occ.MainActivity::class.java)
                .putExtra("open_download", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, com.geno1024.ai.occ.DownloadCancelReceiver::class.java),
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
        _downloadDialogVisible.value = false
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

    fun setDayStartOffset(offsetMinutes: Int?) {
        _dayStartOffset.value = offsetMinutes
        viewModelScope.launch { settings.setDayStartOffset(offsetMinutes) }
        if (_tokenRawBuckets.value.isEmpty()) loadTokenHistory() else rebuildTokenMapsFromRaw()
    }

    private fun rebuildTokenMapsFromRaw() {
        if (_tokenRawBuckets.value.isEmpty()) return
        val zone = effectiveZone()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (_tokenRawBuckets.value.isEmpty()) return@withContext
                val firstDow = java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek.value
                val now = java.time.LocalDate.now(zone)
                val currentWeekStart = now.with(
                    java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(firstDow))
                )
                val monthKeys = (0 until 12).map { java.time.YearMonth.now().minusMonths(it.toLong()).toString() }
                val weekStartDatesStr = (0 until 16).map { currentWeekStart.minusWeeks(it.toLong()) }.map { it.toString() }
                val weekStartSet = weekStartDatesStr.toSet()

                val tokens = mutableMapOf<String, TokenDay>()
                val elapsed = mutableMapOf<String, Long>()
                val month = mutableMapOf<String, MutableMap<Int, TokenDay>>()
                val week = mutableMapOf<String, MutableMap<Int, TokenDay>>()
                val dayHours = mutableMapOf<String, MutableMap<Int, TokenDay>>()
                val modelStats = mutableMapOf<String, MutableTokenModelStats>()

                for (rh in _tokenRawBuckets.value) {
                    coroutineContext.ensureActive()
                    val zdt = java.time.Instant.ofEpochMilli(rh.epochBucket * RAW_BUCKET_MS).atZone(zone)
                    val day = zdt.toLocalDate()
                    val dayKey = day.toString()
                    val hour = zdt.hour
                    val frag = TokenDay(
                        total = rh.total,
                        input = rh.input,
                        output = rh.output,
                        reasoning = rh.reasoning,
                        cacheRead = rh.cacheRead,
                        cacheWrite = rh.cacheWrite,
                        msgs = rh.msgs,
                        msgsSent = rh.msgsSent,
                        msgsReceived = rh.msgsReceived,
                        cost = rh.cost,
                    )
                    tokens[dayKey] = (tokens[dayKey] ?: TokenDay()) + frag
                    if (rh.elapsedMs > 0L) elapsed[dayKey] = (elapsed[dayKey] ?: 0L) + rh.elapsedMs
                    val dBuckets = dayHours.getOrPut(dayKey) { mutableMapOf() }
                    dBuckets[hour] = (dBuckets[hour] ?: TokenDay()) + frag
                    val mKey = dayKey.substring(0, 7)
                    val mBuckets = month.getOrPut(mKey) { mutableMapOf() }
                    mBuckets[hour] = (mBuckets[hour] ?: TokenDay()) + frag
                    val ws = day.with(
                        java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(firstDow))
                    ).toString()
                    if (ws in weekStartSet) {
                        val wBuckets = week.getOrPut(ws) { mutableMapOf() }
                        wBuckets[hour] = (wBuckets[hour] ?: TokenDay()) + frag
                    }
                    val mid = rh.model.ifBlank { "unknown" }
                    val st = modelStats.getOrPut(mid) { MutableTokenModelStats() }
                    st.history[dayKey] = (st.history[dayKey] ?: TokenDay()) + frag
                    st.hourByDay.getOrPut(dayKey) { mutableMapOf() }[hour] =
                        (st.hourByDay[dayKey]?.get(hour) ?: TokenDay()) + frag
                    st.hourByMonth.getOrPut(mKey) { mutableMapOf() }[hour] =
                        (st.hourByMonth[mKey]?.get(hour) ?: TokenDay()) + frag
                    if (ws in weekStartSet) {
                        st.hourByWeek.getOrPut(ws) { mutableMapOf() }[hour] =
                            (st.hourByWeek[ws]?.get(hour) ?: TokenDay()) + frag
                    }
                    if (rh.elapsedMs > 0L) {
                        st.elapsed[dayKey] = (st.elapsed[dayKey] ?: 0L) + rh.elapsedMs
                    }
                }

                val monthOut = month.filterKeys { it in monthKeys }
                val weekOut = week.filterKeys { it in weekStartDatesStr }
                val modelOut = modelStats.mapValues { (_, b) ->
                    TokenModelStats(
                        history = b.history,
                        elapsed = b.elapsed,
                        hourByDay = b.hourByDay,
                        hourByWeek = b.hourByWeek.filterKeys { it in weekStartDatesStr },
                        hourByMonth = b.hourByMonth.filterKeys { it in monthKeys },
                    )
                }
                _tokenHistory.value = tokens
                _tokenElapsed.value = elapsed
                _hourByMonth.value = monthOut
                _hourByWeek.value = weekOut
                _hourByDay.value = dayHours
                _tokenModelStats.value = modelOut
                if (coroutineContext.isActive) {
                    settings.saveTokenHistory(tokens, elapsed)
                    settings.saveTokenCalendar(monthOut, weekOut, dayHours, _tokenSync.value, _tokenSyncedAt.value)
                    settings.saveTokenModelStats(modelOut)
                }
            }
        }
    }

    suspend fun exportSettingsBackup(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val backup = settings.backupSettings()
            val text = Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }.encodeToString(com.geno1024.ai.occ.data.SettingsBackup.serializer(), backup)
            saveTextFileToDownloads(getApplication(), "opencodeclient-settings-backup.json", text)
        }.getOrDefault(false)
    }

    suspend fun importSettingsBackup(text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val backup = Json {
                ignoreUnknownKeys = true
            }.decodeFromString(com.geno1024.ai.occ.data.SettingsBackup.serializer(), text)
            settings.restoreSettings(backup)
        }.isSuccess
    }

    suspend fun exportUsage(): UsageExportResult? = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val okCsv = saveTextFileToDownloads(ctx, "opencodeclient-usage.csv", buildUsageCsv(_tokenHistory.value))
        val okJson = saveTextFileToDownloads(ctx, "opencodeclient-usage.json", buildUsageJson(usageDoc()))
        if (okCsv && okJson) UsageExportResult("opencodeclient-usage.csv", "opencodeclient-usage.json") else null
    }

    suspend fun exportUsageCsv(): String? = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val name = "opencodeclient-usage.csv"
        if (saveTextFileToDownloads(ctx, name, buildUsageCsv(_tokenHistory.value))) name else null
    }

    suspend fun exportUsageJson(): String? = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val name = "opencodeclient-usage.json"
        if (saveTextFileToDownloads(ctx, name, buildUsageJson(usageDoc()))) name else null
    }

    private fun usageDoc(): UsageExportDoc {
        val history = _tokenHistory.value
        val modelStats = _tokenModelStats.value
        val sessions = _sessionModelTokens.value
        val titles = _projects.value
            .flatMap { it.sessions }
            .associate { it.id to (it.title ?: "") }
        return UsageExportDoc(
            exportedAt = java.time.Instant.now().toString(),
            generatedBy = "opencode-inspire ${BuildConfig.VERSION_NAME}",
            dates = history,
            models = modelStats,
            sessions = sessions,
            sessionTitles = titles,
        )
    }

    private fun effectiveZone(): java.time.ZoneId =
        _dayStartOffset.value?.let { java.time.ZoneOffset.ofTotalSeconds(it * 60) }
            ?: java.time.ZoneId.systemDefault()

    private fun runTokenLoad(incremental: Boolean) {
        if (_tokenHistoryLoading.value) return
        _tokenHistoryLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val c = client ?: return@withContext
                    val zone = effectiveZone()
                    val backfillRaw = incremental && _tokenSync.value > 0L && _tokenHistory.value.isNotEmpty() &&
                        _tokenRawBuckets.value.isEmpty() && _tokenRawSync.value == 0L
                    val incremental = incremental && !backfillRaw
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
                    val dayHours = (if (incremental) _hourByDay.value else emptyMap())
                        .mapValues { (_, m) -> m.toMutableMap() }.toMutableMap()
                    val modelStats = (if (incremental) _tokenModelStats.value else emptyMap())
                        .mapValues { (_, st) ->
                            MutableTokenModelStats().apply {
                                history.putAll(st.history)
                                elapsed.putAll(st.elapsed)
                                st.hourByDay.forEach { (k, m) -> hourByDay[k] = m.toMutableMap() }
                                st.hourByWeek.forEach { (k, m) -> hourByWeek[k] = m.toMutableMap() }
                                st.hourByMonth.forEach { (k, m) -> hourByMonth[k] = m.toMutableMap() }
                            }
                        }.toMutableMap()
                    val sessionTokens = (if (incremental) _sessionModelTokens.value else emptyMap())
                        .mapValues { (_, m) -> m.toMutableMap() }.toMutableMap()

                    var maxMsgMs = baseSync

                    val rawWatermark = if (incremental) _tokenRawSync.value else 0L
                    val rawAccum: MutableMap<Pair<Long, String>, TokenRawBucket> = if (incremental) {
                        mutableMapOf<Pair<Long, String>, TokenRawBucket>().apply {
                            _tokenRawBuckets.value.forEach { put(it.epochBucket to it.model, it) }
                        }
                    } else mutableMapOf()
                    var maxRawMs = if (incremental) _tokenRawSync.value else 0L

                    c.sessions().forEach { s ->
                        coroutineContext.ensureActive()
                        val messages = if (hasFreshCache) {
                            runCatching { c.sessionMessagesSince(s.id, baseSync) }.getOrNull()
                        } else {
                            runCatching { c.sessionMessagesAll(s.id) }.getOrNull()
                        } ?: return@forEach
                        val sessionCost = if (hasFreshCache) 0.0
                            else runCatching { c.sessionDetail(s.id)?.cost ?: 0.0 }.getOrDefault(0.0)
                        var turnStart = 0L
                        var turnEnd = 0L
                        var lastDayKey = ""
                        var sesElapsed = 0L
                        var sesMsgs = 0L
                        var sesUser = 0L
                        var sesAssistant = 0L
                        var sesTools = 0L
                        var sesLatest = 0L
                        for ((msg, parts) in messages) {
                            coroutineContext.ensureActive()
                            val created = serverTimeToMillis(msg.time?.created)
                            val completed = serverTimeToMillis(msg.time?.completed)
                            if (created <= 0L) continue
                            if (created > maxMsgMs) maxMsgMs = created
                            sesMsgs++
                            if (msg.role == "user") sesUser++ else if (msg.role == "assistant") sesAssistant++
                            sesTools += parts.count { it.type == "tool" }
                            if (completed > sesLatest) sesLatest = completed
                            if (created > sesLatest) sesLatest = created
                            val zdt = java.time.Instant.ofEpochMilli(created).atZone(zone)
                            val day = zdt.toLocalDate()
                            val toks = msg.tokens
                            val input = toks?.input ?: 0L
                            val output = toks?.output ?: 0L
                            val reasoning = toks?.reasoning ?: 0L
                            val cacheRead = toks?.cache?.read ?: 0L
                            val cacheWrite = toks?.cache?.write ?: 0L
                            val total = toks?.total ?: (input + output + reasoning)
                            val frag = TokenDay(
                                total = total,
                                input = input,
                                output = output,
                                reasoning = reasoning,
                                cacheRead = cacheRead,
                                cacheWrite = cacheWrite,
                                msgs = 1L,
                                msgsSent = if (msg.role == "user") 1L else 0L,
                                msgsReceived = if (msg.role == "assistant") 1L else 0L,
                            )
val dayKey = day.toString()
                            lastDayKey = dayKey
                            tokens[dayKey] = (tokens[dayKey] ?: TokenDay()) + frag
                            val hour = zdt.hour
                            val dBuckets = dayHours.getOrPut(dayKey) { mutableMapOf() }
                            dBuckets[hour] = (dBuckets[hour] ?: TokenDay()) + frag
                            val mKey = dayKey.substring(0, 7)
                            val mBuckets = month.getOrPut(mKey) { mutableMapOf() }
                            mBuckets[hour] = (mBuckets[hour] ?: TokenDay()) + frag
                            val ws = day.with(
                                java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(firstDow))
                            ).toString()
                            if (ws in weekStartSet) {
                                val wBuckets = week.getOrPut(ws) { mutableMapOf() }
                                wBuckets[hour] = (wBuckets[hour] ?: TokenDay()) + frag
                            }
                            val mid = s.model?.id?.ifBlank { null } ?: "unknown"
                            val st = modelStats.getOrPut(mid) { MutableTokenModelStats() }
                            st.history[dayKey] = (st.history[dayKey] ?: TokenDay()) + frag
                            st.hourByDay.getOrPut(dayKey) { mutableMapOf() }[hour] =
                                (st.hourByDay[dayKey]?.get(hour) ?: TokenDay()) + frag
                            st.hourByMonth.getOrPut(mKey) { mutableMapOf() }[hour] =
                                (st.hourByMonth[mKey]?.get(hour) ?: TokenDay()) + frag
                            if (ws in weekStartSet) {
                                st.hourByWeek.getOrPut(ws) { mutableMapOf() }[hour] =
                                    (st.hourByWeek[ws]?.get(hour) ?: TokenDay()) + frag
                            }
                            sessionTokens.getOrPut(s.id) { mutableMapOf() }[mid] =
                                (sessionTokens[s.id]?.get(mid) ?: TokenDay()) + frag
                            if (created > rawWatermark) {
                                val key = (created / RAW_BUCKET_MS) to mid
                                rawAccum[key] = (rawAccum[key]
                                    ?: TokenRawBucket(epochBucket = key.first, model = key.second)) + TokenRawBucket(
                                    total = frag.total,
                                    input = frag.input,
                                    output = frag.output,
                                    reasoning = frag.reasoning,
                                    cacheRead = frag.cacheRead,
                                    cacheWrite = frag.cacheWrite,
                                    msgs = frag.msgs,
                                    msgsSent = frag.msgsSent,
                                    msgsReceived = frag.msgsReceived,
                                )
                                if (created > maxRawMs) maxRawMs = created
                            }
                            if (msg.role == "user") {
                                if (turnStart > 0L && turnEnd > turnStart) {
                                    val inc = turnEnd - turnStart
                                    sesElapsed += inc
                                    val d = java.time.Instant.ofEpochMilli(turnStart).atZone(zone).toLocalDate().toString()
                                    elapsed[d] = (elapsed[d] ?: 0L) + inc
                                    st.elapsed[d] = (st.elapsed[d] ?: 0L) + inc
                                    if (turnStart > rawWatermark) {
                                        val key = (turnStart / RAW_BUCKET_MS) to mid
                                        val rb = rawAccum[key] ?: TokenRawBucket(epochBucket = key.first, model = key.second)
                                        rawAccum[key] = rb.copy(elapsedMs = rb.elapsedMs + inc)
                                    }
                                }
                                turnStart = created
                                turnEnd = created
                            } else if (turnStart > 0L && completed > turnEnd) {
                                turnEnd = completed
                            }
                        }
                        if (sessionCost > 0.0 && lastDayKey.isNotEmpty()) {
                            val sMid = s.model?.id?.ifBlank { null } ?: "unknown"
                            val costFrag = TokenDay(cost = sessionCost)
                            tokens[lastDayKey] = (tokens[lastDayKey] ?: TokenDay()) + costFrag
                            val stS = modelStats.getOrPut(sMid) { MutableTokenModelStats() }
                            stS.history[lastDayKey] = (stS.history[lastDayKey] ?: TokenDay()) + costFrag
                            sessionTokens.getOrPut(s.id) { mutableMapOf() }[sMid] =
                                (sessionTokens[s.id]?.get(sMid) ?: TokenDay()) + costFrag
                            if (sesLatest > rawWatermark && sesLatest > 0L) {
                                val key = (sesLatest / RAW_BUCKET_MS) to sMid
                                val rb = rawAccum[key] ?: TokenRawBucket(epochBucket = key.first, model = key.second)
                                rawAccum[key] = rb.copy(cost = rb.cost + sessionCost)
                            }
                        }
                        if (!hasFreshCache && sesMsgs > 0L && _storedStats.value[s.id] == null) {
                            persistStats(
                                s.id,
                                HistoryStats(
                                    totalElapsed = sesElapsed,
                                    messageCount = sesMsgs,
                                    userMessages = sesUser,
                                    assistantMessages = sesAssistant,
                                    exchanges = sesUser,
                                    toolCalls = sesTools,
                                    computed = true,
                                    lastTimestamp = sesLatest,
                                ),
                            )
                        }
                        if (turnStart > 0L && turnEnd > turnStart) {
                            val d = java.time.Instant.ofEpochMilli(turnStart).atZone(zone).toLocalDate().toString()
                            elapsed[d] = (elapsed[d] ?: 0L) + (turnEnd - turnStart)
                            val midEnd = s.model?.id?.ifBlank { null } ?: "unknown"
                            val stEnd = modelStats.getOrPut(midEnd) { MutableTokenModelStats() }
                            stEnd.elapsed[d] = (stEnd.elapsed[d] ?: 0L) + (turnEnd - turnStart)
                            if (turnStart > rawWatermark) {
                                val key = (turnStart / RAW_BUCKET_MS) to midEnd
                                val rb = rawAccum[key] ?: TokenRawBucket(epochBucket = key.first, model = key.second)
                                rawAccum[key] = rb.copy(elapsedMs = rb.elapsedMs + (turnEnd - turnStart))
                            }
                        }
                    }

                    val monthOut = month.filterKeys { it in monthKeys }
                    val weekOut = week.filterKeys { it in weekStartDatesStr }
                    val dayOut = dayHours

                    val modelOut = modelStats.mapValues { (_, b) ->
                        TokenModelStats(
                            history = b.history,
                            elapsed = b.elapsed,
                            hourByDay = b.hourByDay,
                            hourByWeek = b.hourByWeek.filterKeys { it in weekStartDatesStr },
                            hourByMonth = b.hourByMonth.filterKeys { it in monthKeys },
                        )
                    }

                    _tokenHistory.value = tokens
                    _tokenElapsed.value = elapsed
                    _hourByMonth.value = monthOut
                    _hourByWeek.value = weekOut
                    _hourByDay.value = dayOut
                    _tokenModelStats.value = modelOut
                    _sessionModelTokens.value = sessionTokens
                    _tokenSync.value = maxMsgMs
                    val rawOut = rawAccum.values.sortedWith(compareBy({ it.epochBucket }, { it.model }))
                    _tokenRawBuckets.value = rawOut
                    _tokenRawSync.value = maxRawMs
                    if (coroutineContext.isActive) {
                        val syncedAtMs = System.currentTimeMillis()
                        _tokenSyncedAt.value = syncedAtMs
                        settings.saveTokenHistory(tokens, elapsed)
                        settings.saveTokenCalendar(monthOut, weekOut, dayOut, maxMsgMs, syncedAtMs)
                        settings.saveTokenModelStats(modelOut)
                        settings.saveSessionModelTokens(sessionTokens)
                        settings.saveTokenRawBuckets(rawOut)
                        settings.saveTokenRawSync(maxRawMs)
                    }
                }
            } finally {
                _tokenHistoryLoading.value = false
            }
        }
    }

private class MutableTokenModelStats {
    val history = mutableMapOf<String, TokenDay>()
    val elapsed = mutableMapOf<String, Long>()
    val hourByDay = mutableMapOf<String, MutableMap<Int, TokenDay>>()
    val hourByWeek = mutableMapOf<String, MutableMap<Int, TokenDay>>()
    val hourByMonth = mutableMapOf<String, MutableMap<Int, TokenDay>>()
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
                _currentAgent.value = null
                _agents.value = emptyList()
                _integrations.value = emptyList()
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

    private fun probeCapabilities(cli: AgentClient) {
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

    private fun questionIgnoreKey(q: QuestionRequest): String =
        listOfNotNull(q.sessionId)
            .plus(q.questions.flatMap { qq ->
                qq.options.flatMap { listOf(it.label, it.description) } + listOf(qq.header, qq.question)
            })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\u0001")

    private fun permissionIgnoreKey(p: PermissionRequest): String =
        listOfNotNull(_activeSession.value?.directory, p.permission)
            .plus(p.patterns.map { it.trim() }.filter { it.isNotBlank() }.sorted())
            .filter { it.isNotBlank() }
            .joinToString("\u0001")

    fun ignoreQuestion(q: QuestionRequest) {
        val c = client ?: return
        val dir = _activeSession.value?.directory
        val key = questionIgnoreKey(q)
        viewModelScope.launch {
            settings.addIgnoredQuestion(key)
            runCatching { withContext(Dispatchers.IO) { c.rejectQuestion(q.id, dir) } }
            _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == q.id }
        }
    }

    fun unignoreQuestion(key: String) {
        viewModelScope.launch { settings.removeIgnoredQuestion(key) }
    }

    fun ignorePermission(permission: PermissionRequest) {
        val c = client ?: return
        val dir = _activeSession.value?.directory
        val key = permissionIgnoreKey(permission)
        viewModelScope.launch {
            settings.addIgnoredPermission(key)
            runCatching { withContext(Dispatchers.IO) { c.replyPermission(permission.id, "reject", null, dir) } }
            _pendingPermissions.value = _pendingPermissions.value.filterNot { it.id == permission.id }
        }
    }

    fun unignorePermission(key: String) {
        viewModelScope.launch { settings.removeIgnoredPermission(key) }
    }

    fun clearAllIgnored() {
        val perms = _ignoredPermissions.value.toList()
        val qs = _ignoredQuestions.value.toList()
        viewModelScope.launch {
            perms.forEach { settings.removeIgnoredPermission(it) }
            qs.forEach { settings.removeIgnoredQuestion(it) }
        }
    }

    fun setTokenFormat(format: TokenFormat) {
        viewModelScope.launch { settings.setTokenFormat(format) }
    }

    fun cycleTableTimeFormat() {
        viewModelScope.launch { settings.setTableTimeFormat((_tableTimeFormat.value + 1) % 3) }
    }

    fun setExportTransparent(v: Boolean) {
        viewModelScope.launch { settings.setExportTransparent(v) }
    }

    fun setExportAuthor(v: String) {
        viewModelScope.launch { settings.setExportAuthor(v) }
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

    fun setBackgroundNotify(enabled: Boolean) {
        if (enabled == _backgroundNotify.value) return
        _backgroundNotify.value = enabled
        viewModelScope.launch { settings.setBackgroundNotify(enabled) }
        val context = getApplication<Application>()
        val service = Intent(context, SessionPollService::class.java)
        try {
            if (enabled) {
                ContextCompat.startForegroundService(context, service)
            } else {
                context.stopService(service)
            }
        } catch (_: Exception) {
            // foreground service start may be blocked
        }
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
                        _serverAlive.value = false
                        _connectionState.value = UiState.Error(getAppString(R.string.error_no_server))
                        return@launch
                    }
                    val cli = OpenCodeClient(saved, _authUsername.value, _authPassword.value)
                    client = cli
                    probeCapabilities(cli)
                }
                val health = runCatching { pingHealth(client) }.getOrNull()
                if (health != null) {
                    _serverAlive.value = true
                    _serverVersion.value = health.version
                } else {
                    _serverAlive.value = false
                }
                observeEvents()
                loadWorkspace()
                startHeartbeat()
                val last = settings.getLastSessionId()
                if (last != null && _activeSession.value == null) {
                    runCatching { openSession(last) }
                }
                _connectionState.value = UiState.Idle
                onDone()
            } catch (e: Exception) {
                _connectionState.value = UiState.Error(e.message ?: getAppString(R.string.error_load_failed))
            }
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            while (true) {
                val health = runCatching { pingHealth(client) }.getOrNull()
                if (health != null) {
                    _serverAlive.value = true
                    _serverVersion.value = health.version
                } else {
                    _serverAlive.value = false
                }
                delay(10_000)
            }
        }
    }

    private suspend fun pingHealth(c: AgentClient?): HealthResponse? =
        if (c == null) null else withTimeoutOrNull(5_000) { c.health() }

    private suspend fun loadWorkspace() {
        val c = client ?: return
        var fromCache = false
        val fetched = runCatching {
            val p = withContext(Dispatchers.IO) { c.projects() }
            val s = withContext(Dispatchers.IO) { c.sessions() }
            p to s
        }.getOrElse { e ->
            val cached = withContext(Dispatchers.IO) { sessionCache.loadWorkspace() }
            if (cached != null && cached.savedAt > 0L) {
                fromCache = true
                cached.projects to cached.sessions
            } else {
                throw e
            }
        }
        val rawProjects = fetched.first
        val allSessions = fetched.second
        if (!fromCache) {
            runCatching { withContext(Dispatchers.IO) { sessionCache.saveWorkspace(rawProjects, allSessions) } }
        }
        _projects.value = groupProjects(rawProjects, allSessions)
        if (_selectedProjectId.value == null && _projects.value.isNotEmpty()) {
            _selectedProjectId.value = _projects.value.first().id
        }
        runCatching { refreshPendingQuestions() }
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
            _agents.value = withContext(Dispatchers.IO) {
                c.agents(_activeSession.value?.directory)
            }
        }
        refreshIntegrations()
        runCatching {
            _pendingPermissions.value = withContext(Dispatchers.IO) {
                c.pendingPermissions(_activeSession.value?.directory)
            }.filter { it.sessionId == _activeSession.value?.id }
        }
        refreshWidgetCache(allSessions)
    }

    private fun refreshWidgetCache(allSessions: List<Session>) {
        val context = getApplication<Application>()
        val title = allSessions.firstOrNull()?.let { it.title.ifBlank { it.id } } ?: ""
        val questions = _pendingQuestions.value.size
        SessionWidgetProvider.updateCached(context, title, questions)
        SessionWidgetProvider.refreshAppWidgets(context)
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

    fun cloneProject(url: String, parentDir: String, onDone: () -> Unit = {}) {
        val c = client ?: return
        val repoName = repoNameFromUrl(url)
        viewModelScope.launch {
            _workspaceState.value = UiState.Loading(getAppString(R.string.cloning_project))
            try {
                val targetDir = "${parentDir.trimEnd('/')}/$repoName"
                val tempSession = withContext(Dispatchers.IO) { c.createSession(directory = parentDir) }
                val escapedUrl = escapeShell(url)
                val escapedTarget = escapeShell(targetDir)
                val result = try {
                    withContext(Dispatchers.IO) {
                        c.runShell(
                            tempSession.id,
                            "git clone $escapedUrl $escapedTarget 2>&1; printf '[EXIT=%s]' \"\$?\"",
                        )
                    }
                } finally {
                    withContext(Dispatchers.IO) { runCatching { c.deleteSession(tempSession.id) } }
                }
                if (result.status == "completed" && result.output.contains("[EXIT=0]")) {
                    newSession(targetDir, onDone)
                } else {
                    _workspaceState.value = UiState.Error(cloneErrorText(result.output))
                }
            } catch (e: Exception) {
                _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.clone_project_error))
            }
        }
    }

    private fun cloneErrorText(output: String): String {
        val line = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[EXIT=") }
            .lastOrNull()
        return line ?: getAppString(R.string.clone_project_error)
    }

    fun repoNameFromUrl(url: String): String {
        val suffix = if (url.contains('/')) url.substringAfterLast('/') else url.substringAfterLast(':')
        var name = suffix.trim()
        if (name.endsWith(".git")) name = name.dropLast(4)
        val sanitized = name.map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.') ch else '-'
        }.joinToString("").trim('-', '.')
        return sanitized.ifEmpty { "repo" }
    }

    private fun escapeShell(s: String): String = "'" + s.replace("'", "'\\''") + "'"

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
                val s = runCatching { withContext(Dispatchers.IO) { c.session(id) } }
                    .getOrElse { e ->
                        withContext(Dispatchers.IO) { sessionCache.loadWorkspace() }
                            ?.sessions?.firstOrNull { it.id == id }
                            ?: throw e
                    }
                activateSession(s)
                rollSessionStats(c, id)
                refreshChildSessions()
                _workspaceState.value = UiState.Idle
            } catch (e: Exception) {
                _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.open_session_error))
            }
        }
    }

    private suspend fun rollSessionStats(c: AgentClient, id: String) {
        try {
            val detail = withContext(Dispatchers.IO) { c.sessionDetail(id) }
            if (detail != null) {
                _sessionTokens.value = detail.tokens
                _sessionCost.value = detail.cost
                val modelId = detail.model?.id
                _contextWindow.value = withContext(Dispatchers.IO) { c.contextWindow(modelId) }
                if (!detail.agent.isNullOrBlank()) _currentAgent.value = detail.agent
                recomputeCumulativeTokens()
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

    suspend fun listServerFiles(locationDir: String?, path: String? = null): List<com.geno1024.ai.occ.data.FileNode> {
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
        _currentAgent.value = s.agent
        _sessionTokens.value = null
        _contextWindow.value = 0L
        _promptTokens.value = 0L
        _cumulativeTokens.value = 0L
        _olderCursor.value = null
        _loadingOlder.value = false
        _searchResults.value = emptyList()
        _offlineCacheAt.value = null
        settings.setLastSessionId(s.id)
        loadMessages()
        refreshPendingQuestions()
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
                val (pairs, next) = withContext(Dispatchers.IO) { c.sessionMessagesPage(sid, 100, null) }
                _offlineCacheAt.value = null
                _activeSession.value?.let { s ->
                    runCatching {
                        withContext(Dispatchers.IO) { sessionCache.save(s, pairs.map { (m, p) -> SessionInfo(m, p) }) }
                    }
                }
                _messages.value = pairs.map { toChatMessage(it.first, it.second) }
                _olderCursor.value = next
                derivePromptTokens()
                runCatching {
                    val todos = withContext(Dispatchers.IO) { c.sessionTodos(sid) }
                    _todos.value = todos.mapNotNull { t ->
                        if (t.content.isBlank()) null
                        else TodoUi(t.id, t.content, t.status)
                    }
                }
                recomputeCumulativeTokens()
                recomputeSessionElapsed()
                recomputeSessionTotalElapsed()
            } catch (_: Exception) {
                val cached = withContext(Dispatchers.IO) { sessionCache.load(sid) }
                if (cached != null && _messages.value.isEmpty()) {
                    _messages.value = cached.messages.map { toChatMessage(it.info, it.parts) }
                    _olderCursor.value = null
                    _offlineCacheAt.value = cached.savedAt
                }
            }
        }
    }

    private fun toChatMessage(msg: Message, parts: List<Part>): ChatMessage {
        val tokens = msg.tokens
        return ChatMessage(
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
            cumulativeTokens = 0L,
            error = describeMessageError(msg.error, parts),
        )
    }

    private fun describeMessageError(messageError: JsonElement?, parts: List<Part>): String? {
        val fromMessage = describeErrorElement(messageError)
        if (!fromMessage.isNullOrBlank()) return fromMessage
        val errorPart = parts.firstOrNull { it.type == "error" || it.error.isNullOrBlank().not() }
        val text = errorPart?.error?.takeIf { it.isNotBlank() } ?: errorPart?.text?.takeIf { it.isNotBlank() }
        return text
    }

    private fun describeErrorElement(el: JsonElement?): String? {
        if (el == null) return null
        return when (el) {
            is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject -> describeSessionError(el)
            else -> el.toString()
        }
    }

    private fun derivePromptTokens() {
        _promptTokens.value = _messages.value.asReversed()
            .firstOrNull { it.role == "assistant" && it.tokens != null }
            ?.tokens
            ?.promptTokens
            ?: 0L
    }

    fun loadOlderHistory() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        val cursor = _olderCursor.value ?: return
        if (_loadingOlder.value) return
        viewModelScope.launch {
            _loadingOlder.value = true
            try {
                val (pairs, next) = withContext(Dispatchers.IO) { c.sessionMessagesPage(sid, 100, cursor) }
                if (pairs.isEmpty()) {
                    _olderCursor.value = null
                    return@launch
                }
                _messages.value = pairs.reversed().map { toChatMessage(it.first, it.second) } + _messages.value
                _olderCursor.value = next
                recomputeCumulativeTokens()
            } catch (_: Exception) {
                // keep current history
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    fun jumpToMessage(id: String, onReady: (found: Boolean) -> Unit) {
        if (_messages.value.any { it.id == id }) {
            onReady(true)
            return
        }
        viewModelScope.launch {
            var found = false
            var guard = 0
            while (_olderCursor.value != null && guard < 30) {
                val c2 = client ?: break
                val sid2 = _activeSession.value?.id ?: break
                val cursor = _olderCursor.value ?: break
                val ok = try {
                    val (pairs, next) = withContext(Dispatchers.IO) { c2.sessionMessagesPage(sid2, 100, cursor) }
                    if (pairs.isEmpty()) {
                        _olderCursor.value = null
                        false
                    } else {
                        _messages.value = pairs.reversed().map { toChatMessage(it.first, it.second) } + _messages.value
                        _olderCursor.value = next
                        recomputeCumulativeTokens()
                        true
                    }
                } catch (_: Exception) {
                    false
                }
                guard++
                found = _messages.value.any { it.id == id }
                if (!ok || found) break
            }
            onReady(found)
        }
    }

    fun searchAll(query: String) {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        if (_searchingAll.value || query.isBlank()) return
        viewModelScope.launch {
            _searchingAll.value = true
            _searchProgress.value = 0
            try {
                val all = withContext(Dispatchers.IO) {
                    c.sessionMessagesAll(sid) { fetched, _ -> _searchProgress.value = fetched }
                }
                val hits = all.asReversed().mapNotNull { (msg, parts) ->
                    val text = buildText(parts)
                    val reasoning = buildReasoning(parts) ?: ""
                    val toolText = parts.filter { it.type == "tool" }.joinToString(" ") { p ->
                        listOfNotNull(p.title ?: p.tool, p.state?.input?.toString(), p.state?.output).joinToString(" ")
                    }
                    val combined = listOf(text, reasoning, toolText).joinToString("\n")
                    val idx = combined.indexOf(query, ignoreCase = true)
                    if (idx < 0) return@mapNotNull null
                    val snippet = combined.substring(
                        (idx - 80).coerceAtLeast(0),
                        (idx + 120).coerceAtMost(combined.length),
                    ).let { if (idx - 80 > 0) "…$it" else it }
                    SearchHit(
                        id = msg.id,
                        role = msg.role ?: "unknown",
                        model = msg.modelID ?: msg.model?.id,
                        time = serverTimeToMillis(msg.time?.created),
                        text = text.trim().ifEmpty { toolText.trim().take(200) },
                        snippet = snippet.replace('\n', ' ').trim(),
                    )
                }
                _searchResults.value = hits.take(200)
            } catch (_: Exception) {
                _searchError.value = true
            } finally {
                _searchingAll.value = false
            }
        }
    }

    fun refreshSession() {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            loadMessages()
            rollSessionStats(c, sid)
            refreshPendingQuestions()
        }
    }

    private fun recomputeCumulativeTokens() {
        var cum = 0L
        _messages.value = _messages.value.map { m ->
            cum += (m.tokens?.total ?: 0L).coerceAtLeast(0L)
            m.copy(cumulativeTokens = cum)
        }
        _cumulativeTokens.value = cum
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

    private fun isDefaultSessionTitle(title: String?): Boolean {
        val t = title?.trim().orEmpty()
        return t.isEmpty() || t.startsWith("New session", ignoreCase = true)
    }

    private fun refreshAutoSessionTitleIfNeeded(sid: String) {
        val c = client ?: return
        if (_activeSession.value?.id != sid) return
        if (!isDefaultSessionTitle(_activeSession.value?.title)) return
        if (!titleRefreshPending.add(sid)) return
        viewModelScope.launch {
            try {
                var detail: SessionV2Info? = null
                repeat(3) { attempt ->
                    if (attempt > 0) delay(500)
                    detail = withContext(Dispatchers.IO) {
                        runCatching { c.sessionDetail(sid) }.getOrNull()
                    }
                    val t = detail?.title?.trim().orEmpty()
                    if (!isDefaultSessionTitle(t)) return@repeat
                }
                val title = detail?.title?.trim().orEmpty()
                if (!isDefaultSessionTitle(title) && _activeSession.value?.id == sid) {
                    _activeSession.value = _activeSession.value?.copy(title = title)
                    _projects.value = _projects.value.map { p ->
                        p.copy(sessions = p.sessions.map {
                            if (it.id == sid && isDefaultSessionTitle(it.title)) it.copy(title = title) else it
                        })
                    }
                }
            } catch (_: Exception) {
            } finally {
                titleRefreshPending.remove(sid)
            }
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
                    _activeSession.value = _activeSession.value?.copy(model = com.geno1024.ai.occ.data.ModelV2Ref(id = modelId, providerId = providerId))
                    rollSessionStats(c, sid)
                }
                .onFailure { e ->
                    _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.send_failed))
                }
        }
    }

    fun switchAgent(agentId: String) {
        val c = client ?: return
        val sid = _activeSession.value?.id ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { c.switchAgent(sid, agentId) } }
                .onSuccess {
                    _currentAgent.value = agentId
                    _activeSession.value = _activeSession.value?.copy(agent = agentId)
                    _projects.value = _projects.value.map { p ->
                        p.copy(sessions = p.sessions.map { if (it.id == sid) it.copy(agent = agentId) else it })
                    }
                }
                .onFailure { e ->
                    _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.send_failed))
                }
        }
    }

    fun refreshIntegrations() {
        val c = client ?: return
        viewModelScope.launch {
            runCatching {
                _integrations.value = withContext(Dispatchers.IO) {
                    c.integrations(_activeSession.value?.directory)
                }
            }
        }
    }

    fun connectIntegration(integrationId: String, key: String, label: String?, onResult: (Boolean) -> Unit) {
        val c = client ?: return
        val dir = _activeSession.value?.directory
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { c.connectIntegration(integrationId, key, label, dir) }
            }
            .onSuccess { onResult(true); refreshIntegrations() }
            .onFailure { e -> onResult(false); _workspaceState.value = UiState.Error(e.message ?: getAppString(R.string.send_failed)) }
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
                    withContext(Dispatchers.IO) { sessionCache.clear(sid) }
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

    private fun deleteDownloadedApk(apk: String) {
        val app = getApplication<android.app.Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    runCatching {
                        app.contentResolver.delete(
                            collection,
                            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                            arrayOf(apk, Environment.DIRECTORY_DOWNLOADS + "/"),
                        )
                    }
                } else {
                    runCatching { java.io.File(app.cacheDir, apk).delete() }
                }
            }
        }
    }

    fun noteDownloadedApk(fileName: String) {
        viewModelScope.launch {
            settings.setDownloadedApk(fileName)
            settings.setInstalledVersion(BuildConfig.VERSION_NAME)
        }
    }

    fun updateDraft(sid: String, text: String) {
        if (sid.isEmpty()) return
        _sessionDrafts.value = if (text.isBlank()) {
            _sessionDrafts.value - sid
        } else {
            _sessionDrafts.value + (sid to text)
        }
    }

    fun flushDraft(sid: String) {
        if (sid.isEmpty()) return
        viewModelScope.launch { settings.setDrafts(_sessionDrafts.value) }
    }

    fun clearDraft(sid: String) {
        if (sid.isEmpty()) return
        if (sid in _sessionDrafts.value) {
            _sessionDrafts.value = _sessionDrafts.value - sid
            viewModelScope.launch { settings.setDrafts(_sessionDrafts.value) }
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

    fun toggleArchived(sessionId: String) {
        viewModelScope.launch { settings.toggleArchived(sessionId) }
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

    suspend fun listSessionDirFiles(relPath: String, locationDir: String): List<com.geno1024.ai.occ.data.FileNode> {
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
                launch {
                    while (true) {
                        refreshChildSessions()
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

    private suspend fun refreshChildSessions() {
        val c = client ?: return
        val activeId = _activeSession.value?.id ?: return
        val children = runCatching { withContext(Dispatchers.IO) { c.sessions() } }
            .getOrNull()
            ?.filter { it.parentId == activeId }
            ?: return
        _childSessions.value = children
    }

    private suspend fun refreshPendingQuestions() {
        val c = client ?: return
        val dir = _activeSession.value?.directory
        val fetched = runCatching {
            withContext(Dispatchers.IO) { c.pendingQuestions(dir) }
        }.getOrNull() ?: return
        val ignored = _ignoredQuestions.value
        val (silent, keep) = fetched.partition { questionIgnoreKey(it) in ignored }
        if (silent.isNotEmpty()) {
            withContext(Dispatchers.IO) { silent.forEach { runCatching { c.rejectQuestion(it.id, dir) } } }
        }
        _pendingQuestions.value = (_pendingQuestions.value + keep).distinctBy { it.id }
    }

    private suspend fun refreshPendingPermissions() {
        refreshPendingQuestions()
        val c = client ?: return
        val dir = _activeSession.value?.directory
        var fetched: List<PermissionRequest>? = null
        if (!dir.isNullOrBlank()) {
            fetched = runCatching {
                withContext(Dispatchers.IO) { c.pendingPermissions(dir) }
            }.getOrNull()
        }
        if (fetched == null) {
            fetched = runCatching {
                withContext(Dispatchers.IO) { c.pendingPermissions(null) }
            }.getOrNull()
        }
        val list = fetched ?: return
        val ignored = _ignoredPermissions.value
        val (silent, keep) = list.partition { permissionIgnoreKey(it) in ignored }
        if (silent.isNotEmpty()) {
            withContext(Dispatchers.IO) { silent.forEach { runCatching { c.replyPermission(it.id, "reject", null, dir) } } }
        }
        _pendingPermissions.value = (_pendingPermissions.value + keep).distinctBy { it.id }
    }

    private fun handleEvent(raw: String) {
        val dataStr = raw.substringAfter("data:").trim()
        if (dataStr.isEmpty()) return
        val obj = runCatching { json.parseToJsonElement(dataStr).jsonObject }.getOrNull() ?: return
        val payload = obj["payload"]?.jsonObject ?: return
        val type = payload["type"]?.jsonPrimitive?.contentOrNull ?: return
        val props = payload["properties"]?.jsonObject
        val active = _activeSession.value?.id

        when (type) {
            "question.asked" -> {
                runCatching {
                    val q = json.decodeFromString(QuestionRequest.serializer(), props.toString())
                    if (questionIgnoreKey(q) in _ignoredQuestions.value) {
                        val dir = _activeSession.value?.directory
                        client?.let { cc ->
                            viewModelScope.launch {
                                runCatching { withContext(Dispatchers.IO) { cc.rejectQuestion(q.id, dir) } }
                            }
                        }
                        return@runCatching
                    }
                    _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == q.id } + q
                }
            }
            "question.replied", "question.rejected" -> {
                val sendId = props?.get("requestID")?.jsonPrimitive?.contentOrNull
                if (sendId != null) {
                    _pendingQuestions.value = _pendingQuestions.value.filterNot { it.id == sendId }
                }
            }
            "permission.asked" -> {
                runCatching {
                    val p = json.decodeFromString(PermissionRequest.serializer(), props.toString())
                    if (permissionIgnoreKey(p) in _ignoredPermissions.value) {
                        val dir = _activeSession.value?.directory
                        client?.let { cc ->
                            viewModelScope.launch {
                                runCatching { withContext(Dispatchers.IO) { cc.replyPermission(p.id, "reject", null, dir) } }
                            }
                        }
                        return@runCatching
                    }
                    _pendingPermissions.value = _pendingPermissions.value.filterNot { it.id == p.id } + p
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
                        refreshAutoSessionTitleIfNeeded(sid)
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
                val errorText = describeErrorElement(info["error"])
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
                        error = errorText,
                    )
                } else if (role != "user" && _messages.value.any { it.id == mid } && errorText != null) {
                    _messages.value = _messages.value.map {
                        if (it.id != mid) it
                        else it.copy(error = errorText)
                    }
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
        eventJob?.cancel()
        eventJob = null
        stopHeartbeat()
        _activeSession.value = null
        _childSessions.value = emptyList()
        _messages.value = emptyList()
        viewModelScope.launch { settings.setLastSessionId(null) }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _serverAlive.value = false
    }
}

data class UpdateInfo(
    val version: String,
    val url: String,
)
