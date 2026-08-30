package com.example.opencodeclient.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.opencodeclient.data.FileNode
import com.example.opencodeclient.data.OpenCodeClient
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

    private val _projectPath = MutableStateFlow<String?>(null)
    val projectPath: StateFlow<String?> = _projectPath.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _connectionState = MutableStateFlow<UiState>(UiState.Idle)
    val connectionState: StateFlow<UiState> = _connectionState.asStateFlow()

    private val _browserState = MutableStateFlow<UiState>(UiState.Idle)
    val browserState: StateFlow<UiState> = _browserState.asStateFlow()

    private val _dirs = MutableStateFlow<List<FileNode>>(emptyList())
    val dirs: StateFlow<List<FileNode>> = _dirs.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    init {
        viewModelScope.launch {
            settings.serverUrl.collect { _serverUrl.value = it }
        }
        viewModelScope.launch {
            settings.projectPath.collect { _projectPath.value = it }
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
            _connectionState.value = UiState.Loading("Loading project...")
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
            val proj = withContext(Dispatchers.IO) { client!!.currentProject() }
            val root = proj?.path
            if (root != null) {
                _projectPath.value = root
                settings.setProjectPath(root)
            }
            _connectionState.value = UiState.Idle
            onDone()
            } catch (e: Exception) {
                _connectionState.value = UiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun loadDir(path: String? = null) {
        val c = client ?: return
        viewModelScope.launch {
            _browserState.value = UiState.Loading()
            try {
                val files = withContext(Dispatchers.IO) { c.listFiles(path) }
                _dirs.value = files
                _browserState.value = UiState.Idle
            } catch (e: Exception) {
                _browserState.value = UiState.Error(e.message ?: "Failed to list files")
            }
        }
    }

    fun createSessionAndStart(): Boolean {
        val c = client ?: return false
        viewModelScope.launch {
            _browserState.value = UiState.Loading("Starting session...")
            try {
                val session = withContext(Dispatchers.IO) { c.createSession() }
                _sessionId.value = session.id
                _messages.value = listOf()
                _browserState.value = UiState.Idle
            } catch (e: Exception) {
                _browserState.value = UiState.Error(e.message ?: "Failed to create session")
            }
        }
        return true
    }

    fun loadMessages() {
        val c = client ?: return
        val sid = _sessionId.value ?: return
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
        val sid = _sessionId.value ?: return
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
        val sid = _sessionId.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { c.abortSession(sid) } }
        }
    }

    fun reset() {
        _sessionId.value = null
        _messages.value = emptyList()
    }
}
