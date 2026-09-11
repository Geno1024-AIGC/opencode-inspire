package com.geno1024.ai.inspire.data

import kotlinx.coroutines.flow.Flow

/**
 * 后端 AI 代理的客户端契约。
 *
 * 这是 UI/ViewModel 层与具体代理实现解耦的边界。每个代理（OpenCode、其他 AI 代理等）
 * 提供自己的实现。当前唯一实现为 [AgentHttpClient]。
 */
interface AgentClient {

    suspend fun health(): HealthResponse

    suspend fun projects(): List<Project>

    suspend fun currentProject(): Project?

    suspend fun listDirectory(locationDir: String? = null, path: String? = null): List<FileNode>

    suspend fun readFileContent(locationDir: String?, path: String): String?

    suspend fun createSession(directory: String? = null, parentId: String? = null, title: String? = null): Session

    suspend fun runShell(sessionId: String, command: String, agent: String = "general"): ShellResult

    suspend fun sessions(): List<Session>

    suspend fun models(): List<ModelInfo>

    suspend fun agents(directory: String? = null): List<AgentInfo>

    suspend fun switchAgent(sessionId: String, agent: String)

    suspend fun integrations(directory: String? = null): List<IntegrationInfo>

    suspend fun connectIntegration(integrationId: String, key: String, label: String? = null, directory: String? = null)

    suspend fun sessionDetail(id: String): SessionV2Info?

    suspend fun contextWindow(modelId: String?): Long

    suspend fun pendingQuestions(directory: String? = null): List<QuestionRequest>

    suspend fun replyQuestion(requestId: String, answers: List<List<String>>, directory: String? = null)

    suspend fun rejectQuestion(requestId: String, directory: String? = null)

    suspend fun session(id: String): Session

    suspend fun sessionMessages(sessionId: String, limit: Int = 50): List<Pair<Message, List<Part>>>

    suspend fun sessionMessagesPage(
        sessionId: String,
        limit: Int,
        before: String?,
    ): Pair<List<Pair<Message, List<Part>>>, String?>

    suspend fun sessionMessagesAll(
        sessionId: String,
        onProgress: (fetched: Int, lastTimestamp: Long) -> Unit = { _, _ -> },
    ): List<Pair<Message, List<Part>>>

    suspend fun sessionMessagesSince(
        sessionId: String,
        sinceMs: Long,
        onProgress: (fetched: Int, earliestSeen: Long) -> Unit = { _, _ -> },
    ): List<Pair<Message, List<Part>>>

    suspend fun commands(directory: String? = null): List<Command>

    suspend fun executeCommand(sessionId: String, command: String, arguments: String = "")

    suspend fun pendingPermissions(directory: String? = null): List<PermissionRequest>

    suspend fun replyPermission(requestId: String, reply: String, message: String? = null, directory: String? = null)

    suspend fun switchModel(sessionId: String, providerId: String, modelId: String)

    suspend fun renameSession(sessionId: String, title: String)

    suspend fun deleteSession(sessionId: String): Boolean

    suspend fun sessionTodos(sessionId: String): List<TodoInfo>

    suspend fun sendPromptAsync(sessionId: String, text: String)

    suspend fun sendPrompt(sessionId: String, text: String): Pair<Message, List<Part>>

    suspend fun abortSession(sessionId: String): Boolean

    suspend fun summarizeSession(sessionId: String, providerId: String, modelId: String)

    fun eventStream(): Flow<String>

    suspend fun searchFiles(query: String, limit: Int = 50): List<String>

    suspend fun probeCapabilities(): CapabilityReport

    fun applyCapabilities(report: CapabilityReport)
}
