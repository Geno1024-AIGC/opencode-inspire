package com.geno1024.ai.inspire.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OpenCode 客户端的自动探测门面。
 *
 * 连接时先探测 `GET /api/info`：返回含 `version` 与 `pid` 的 JSON 即为 v2；
 * 否则回落到 `GET /global/health`（v1 返回 JSON，v2 返回 SPA HTML）。
 * 选定后把全部调用委托给 [OpenCodeV1Client] 或 [OpenCodeV2Client]，
 * 因此上层（[AgentClient] / ViewModel / UI）完全不必感知协议版本。
 */
class OpenCodeClient(
    serverUrl: String,
    username: String? = null,
    password: String? = null,
) : AgentClient {

    private val transport = HttpTransport(serverUrl, username, password)
    private val v1 = OpenCodeV1Client(transport)
    private val v2 = OpenCodeV2Client(transport)

    private val mutex = Mutex()

    @Volatile
    private var resolved: AgentClient? = null

    private var lastCapabilities: CapabilityReport? = null

    /** 当前实际使用的协议版本：`"v1"` / `"v2"`（未探测时为 null）。 */
    val protocol: String?
        get() = when (resolved) {
            v1 -> "v1"
            v2 -> "v2"
            else -> null
        }

    private suspend fun target(): AgentClient {
        resolved?.let { return it }
        return mutex.withLock {
            resolved ?: detect().also { client ->
                lastCapabilities?.let { report -> client.applyCapabilities(report) }
                resolved = client
            }
        }
    }

    private suspend fun detect(): AgentClient {
        val info = transport.getJsonObject("/api/info")
        if (info != null && info["version"] != null && info["pid"] != null) return v2
        val health = transport.getJsonObject("/global/health")
        if (health != null && (health["healthy"] != null || health["version"] != null)) return v1
        // 探测失败时按 v2 处理：新服务器是默认目标，且 v2 的 health 会安全地返回未就绪。
        return v2
    }

    override suspend fun health(): HealthResponse = target().health()

    override suspend fun projects(): List<Project> = target().projects()

    override suspend fun currentProject(): Project? = target().currentProject()

    override suspend fun listDirectory(locationDir: String?, path: String?): List<FileNode> =
        target().listDirectory(locationDir, path)

    override suspend fun readFileContent(locationDir: String?, path: String): String? =
        target().readFileContent(locationDir, path)

    override suspend fun readFileBytes(
        locationDir: String?,
        path: String,
        onRead: (Long, Long) -> Unit,
    ): ByteArray? = target().readFileBytes(locationDir, path, onRead)

    override suspend fun createSession(directory: String?, parentId: String?, title: String?): Session =
        target().createSession(directory, parentId, title)

    override suspend fun runShell(sessionId: String, command: String, agent: String): ShellResult =
        target().runShell(sessionId, command, agent)

    override suspend fun sessions(): List<Session> = target().sessions()

    override suspend fun sessionStatuses(directory: String?): Map<String, SessionStatusInfo> =
        target().sessionStatuses(directory)

    override suspend fun models(): List<ModelInfo> = target().models()

    override suspend fun providers(directory: String?): ProvidersV2Response =
        target().providers(directory)

    override suspend fun agents(directory: String?): List<AgentInfo> = target().agents(directory)

    override suspend fun switchAgent(sessionId: String, agent: String) =
        target().switchAgent(sessionId, agent)

    override suspend fun integrations(directory: String?): List<IntegrationInfo> =
        target().integrations(directory)

    override suspend fun connectIntegration(
        integrationId: String,
        key: String,
        label: String?,
        directory: String?,
    ) = target().connectIntegration(integrationId, key, label, directory)

    override suspend fun sessionDetail(id: String): SessionV2Info? = target().sessionDetail(id)

    override suspend fun contextWindow(modelId: String?): Long = target().contextWindow(modelId)

    override suspend fun pendingQuestions(directory: String?): List<QuestionRequest> =
        target().pendingQuestions(directory)

    override suspend fun replyQuestion(
        requestId: String,
        answers: List<List<String>>,
        directory: String?,
    ) = target().replyQuestion(requestId, answers, directory)

    override suspend fun rejectQuestion(requestId: String, directory: String?) =
        target().rejectQuestion(requestId, directory)

    override suspend fun session(id: String): Session = target().session(id)

    override suspend fun sessionMessages(
        sessionId: String,
        limit: Int,
    ): List<Pair<Message, List<Part>>> = target().sessionMessages(sessionId, limit)

    override suspend fun sessionMessagesPage(
        sessionId: String,
        limit: Int,
        before: String?,
    ): Pair<List<Pair<Message, List<Part>>>, String?> =
        target().sessionMessagesPage(sessionId, limit, before)

    override suspend fun sessionMessagesAll(
        sessionId: String,
        onProgress: (fetched: Int, lastTimestamp: Long) -> Unit,
    ): List<Pair<Message, List<Part>>> = target().sessionMessagesAll(sessionId, onProgress)

    override suspend fun sessionMessagesSince(
        sessionId: String,
        sinceMs: Long,
        onProgress: (fetched: Int, earliestSeen: Long) -> Unit,
    ): List<Pair<Message, List<Part>>> = target().sessionMessagesSince(sessionId, sinceMs, onProgress)

    override suspend fun commands(directory: String?): List<Command> = target().commands(directory)

    override suspend fun executeCommand(sessionId: String, command: String, arguments: String) =
        target().executeCommand(sessionId, command, arguments)

    override suspend fun pendingPermissions(directory: String?): List<PermissionRequest> =
        target().pendingPermissions(directory)

    override suspend fun replyPermission(
        requestId: String,
        reply: String,
        message: String?,
        directory: String?,
    ) = target().replyPermission(requestId, reply, message, directory)

    override suspend fun switchModel(sessionId: String, providerId: String, modelId: String) =
        target().switchModel(sessionId, providerId, modelId)

    override suspend fun renameSession(sessionId: String, title: String) =
        target().renameSession(sessionId, title)

    override suspend fun deleteSession(sessionId: String): Boolean = target().deleteSession(sessionId)

    override suspend fun sessionTodos(sessionId: String): List<TodoInfo> = target().sessionTodos(sessionId)

    override suspend fun sendPromptAsync(sessionId: String, text: String) =
        target().sendPromptAsync(sessionId, text)

    override suspend fun sendPrompt(sessionId: String, text: String): Pair<Message, List<Part>> =
        target().sendPrompt(sessionId, text)

    override suspend fun abortSession(sessionId: String): Boolean = target().abortSession(sessionId)

    override suspend fun summarizeSession(sessionId: String, providerId: String, modelId: String) =
        target().summarizeSession(sessionId, providerId, modelId)

    override suspend fun revertMessage(sessionId: String, messageID: String): Session =
        target().revertMessage(sessionId, messageID)

    override suspend fun unrevertMessage(sessionId: String): Session =
        target().unrevertMessage(sessionId)

    override fun eventStream(): Flow<String> = flow { emitAll(target().eventStream()) }

    override suspend fun searchFiles(query: String, limit: Int): List<String> =
        target().searchFiles(query, limit)

    override suspend fun probeCapabilities(
        cachedPaths: List<String>,
        cachedVersion: String?,
    ): CapabilityReport = target().probeCapabilities(cachedPaths, cachedVersion)

    override fun applyCapabilities(report: CapabilityReport) {
        lastCapabilities = report
        resolved?.applyCapabilities(report)
    }
}
