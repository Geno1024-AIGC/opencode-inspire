package com.geno1024.ai.inspire.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthResponse(
    val healthy: Boolean,
    val version: String? = null,
)

@Serializable
data class Project(
    val id: String = "",
    val worktree: String = "",
    val vcsDir: String? = null,
    val vcs: String? = null,
)

@Serializable
data class FileNode(
    val name: String = "",
    val type: String = "",
    val path: String = "",
    val children: List<FileNode>? = null,
)

@Serializable
data class Session(
    val id: String,
    @SerialName("projectID") val projectId: String = "",
    val directory: String = "",
    @SerialName("parentID") val parentId: String? = null,
    val title: String = "",
    val time: SessionTime? = null,
    val tokens: Tokens? = null,
    val model: ModelV2Ref? = null,
    val agent: String? = null,
)

@Serializable
data class SessionTime(
    val created: Long = 0L,
    val updated: Long = 0L,
)

@Serializable
data class LocationRef(
    val directory: String = "",
    @SerialName("workspaceID") val workspaceId: String? = null,
)

@Serializable
data class SessionV2Info(
    val id: String,
    @SerialName("parentID") val parentId: String? = null,
    @SerialName("projectID") val projectId: String = "",
    val title: String = "",
    val time: SessionTime? = null,
    val location: LocationRef? = null,
    val tokens: Tokens? = null,
    val cost: Double = 0.0,
    val model: ModelV2Ref? = null,
    val agent: String? = null,
)

@Serializable
data class ModelV2Ref(
    val id: String? = null,
    @SerialName("providerID") val providerId: String? = null,
    val variant: String? = null,
)

@Serializable
data class ModelInfo(
    val id: String? = null,
    @SerialName("providerID") val providerId: String? = null,
    val limit: ModelLimit? = null,
)

@Serializable
data class ModelLimit(
    val context: Long = 0L,
    val input: Long? = null,
    val output: Long? = null,
)

@Serializable
data class ModelsV2Response(
    val location: LocationRef? = null,
    val data: List<ModelInfo> = emptyList(),
)

@Serializable
data class AgentInfo(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val mode: String? = null,
    val hidden: Boolean = false,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: id
}

@Serializable
data class AgentsV2Response(
    val location: LocationRef? = null,
    val data: List<AgentInfo> = emptyList(),
)

@Serializable
data class IntegrationMethod(
    val type: String = "",
    val names: List<String> = emptyList(),
)

@Serializable
data class IntegrationConnection(
    val type: String? = null,
    val id: String? = null,
    val label: String? = null,
    val value: String? = null,
)

@Serializable
data class IntegrationInfo(
    val id: String = "",
    val name: String? = null,
    val methods: List<IntegrationMethod> = emptyList(),
    val connections: List<IntegrationConnection> = emptyList(),
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: id
    val supportsKey: Boolean
        get() = methods.any { it.type == "key" }
}

@Serializable
data class IntegrationsV2Response(
    val location: LocationRef? = null,
    val data: List<IntegrationInfo> = emptyList(),
)

@Serializable
data class Tokens(
    val total: Long? = null,
    val input: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val cache: TokenCache? = null,
)

@Serializable
data class TokenCache(
    val read: Long = 0L,
    val write: Long = 0L,
)

val Tokens.promptTokens: Long
    get() = input + (cache?.read ?: 0L)

val Tokens.freshTokens: Long
    get() = input + output + reasoning

@Serializable
data class TokenDay(
    val total: Long = 0L,
    val input: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val msgs: Long = 0L,
    val msgsSent: Long = 0L,
    val msgsReceived: Long = 0L,
    val cost: Double = 0.0,
) {
    val fresh: Long
        get() = input + output + reasoning

    operator fun plus(other: TokenDay): TokenDay = TokenDay(
        total = total + other.total,
        input = input + other.input,
        output = output + other.output,
        reasoning = reasoning + other.reasoning,
        cacheRead = cacheRead + other.cacheRead,
        cacheWrite = cacheWrite + other.cacheWrite,
        msgs = msgs + other.msgs,
        msgsSent = msgsSent + other.msgsSent,
        msgsReceived = msgsReceived + other.msgsReceived,
        cost = cost + other.cost,
    )
}

@Serializable
data class TokenRawBucket(
    val epochBucket: Long = 0L,
    val model: String = "",
    val projectId: String = "",
    val total: Long = 0L,
    val input: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val cacheRead: Long = 0L,
    val cacheWrite: Long = 0L,
    val msgs: Long = 0L,
    val msgsSent: Long = 0L,
    val msgsReceived: Long = 0L,
    val cost: Double = 0.0,
    val elapsedMs: Long = 0L,
) {
    operator fun plus(other: TokenRawBucket): TokenRawBucket = TokenRawBucket(
        epochBucket = epochBucket,
        model = model,
        projectId = projectId,
        total = total + other.total,
        input = input + other.input,
        output = output + other.output,
        reasoning = reasoning + other.reasoning,
        cacheRead = cacheRead + other.cacheRead,
        cacheWrite = cacheWrite + other.cacheWrite,
        msgs = msgs + other.msgs,
        msgsSent = msgsSent + other.msgsSent,
        msgsReceived = msgsReceived + other.msgsReceived,
        cost = cost + other.cost,
        elapsedMs = elapsedMs + other.elapsedMs,
    )
}

@Serializable
data class TokenModelStats(
    val history: Map<String, TokenDay> = emptyMap(),
    val elapsed: Map<String, Long> = emptyMap(),
    val hourByDay: Map<String, Map<Int, TokenDay>> = emptyMap(),
    val hourByWeek: Map<String, Map<Int, TokenDay>> = emptyMap(),
    val hourByMonth: Map<String, Map<Int, TokenDay>> = emptyMap(),
)

fun TokenModelStats.plus(other: TokenModelStats): TokenModelStats = TokenModelStats(
    history = mergeTokenDayMap(history, other.history),
    elapsed = (elapsed.keys + other.elapsed.keys).associateWith { (elapsed[it] ?: 0L) + (other.elapsed[it] ?: 0L) },
    hourByDay = mergeHourMap(hourByDay, other.hourByDay),
    hourByWeek = mergeHourMap(hourByWeek, other.hourByWeek),
    hourByMonth = mergeHourMap(hourByMonth, other.hourByMonth),
)

private fun <K> mergeTokenDayMap(a: Map<K, TokenDay>, b: Map<K, TokenDay>): Map<K, TokenDay> =
    (a.keys + b.keys).associateWith { (a[it] ?: TokenDay()) + (b[it] ?: TokenDay()) }

private fun mergeHourMap(
    a: Map<String, Map<Int, TokenDay>>,
    b: Map<String, Map<Int, TokenDay>>,
): Map<String, Map<Int, TokenDay>> =
    (a.keys + b.keys).associateWith { key ->
        mergeTokenDayMap(a[key] ?: emptyMap(), b[key] ?: emptyMap())
    }

@Serializable
data class TodoInfo(
    val id: String = "",
    val content: String = "",
    val status: String = "pending",
    val priority: String? = null,
)

@Serializable
data class Command(
    val name: String = "",
    val description: String = "",
    val agent: String? = null,
    val model: String? = null,
    val source: String = "command",
    val template: String = "",
    val subtask: Boolean = false,
    val hints: List<String> = emptyList(),
)

@Serializable
data class PermissionRequest(
    val id: String = "",
    @SerialName("sessionID") val sessionId: String = "",
    val permission: String = "",
    val patterns: List<String> = emptyList(),
    val metadata: Map<String, String>? = null,
    val always: List<String> = emptyList(),
    val tool: PermissionTool? = null,
)

@Serializable
data class PermissionTool(
    @SerialName("messageID") val messageId: String = "",
    @SerialName("callID") val callId: String = "",
)

@Serializable
data class ServerProfile(
    val url: String,
    val type: String = "",
    val username: String? = null,
    val password: String? = null,
    val name: String = "",
)

@Serializable
data class QuestionOption(
    val label: String = "",
    val description: String = "",
)

@Serializable
data class QuestionInfo(
    val question: String = "",
    val header: String = "",
    val options: List<QuestionOption> = emptyList(),
    val multiple: Boolean = false,
    val custom: Boolean = true,
)

@Serializable
data class QuestionRequest(
    val id: String,
    @SerialName("sessionID") val sessionId: String = "",
    val questions: List<QuestionInfo> = emptyList(),
)

@Serializable
data class SessionsV2Response(
    val data: List<SessionV2Info> = emptyList(),
    val cursor: V2Cursor = V2Cursor(),
)

@Serializable
data class V2Cursor(
    val next: String? = null,
    val previous: String? = null,
)

@Serializable
data class SessionInfo(
    val info: Message,
    val parts: List<Part>,
)

@Serializable
data class Message(
    val id: String,
    @SerialName("sessionID") val sessionID: String? = null,
    val role: String? = null,
    val provider: ProviderRef? = null,
    val model: ModelRef? = null,
    @SerialName("providerID") val providerID: String? = null,
    @SerialName("modelID") val modelID: String? = null,
    val tokens: Tokens? = null,
    val time: MessageTime? = null,
    val error: JsonElement? = null,
)

@Serializable
data class MessageTime(
    val created: Long? = null,
    val completed: Long? = null,
)

@Serializable
data class ProviderRef(val id: String? = null)

@Serializable
data class ModelRef(val id: String? = null)

@Serializable
data class Part(
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val state: ToolState? = null,
    val title: String? = null,
    val error: String? = null,
    val callID: String? = null,
)

@Serializable
data class ToolState(
    val status: String? = null,
    val input: JsonElement? = null,
    val inputState: JsonElement? = null,
    val output: String? = null,
    val error: String? = null,
)

data class CapabilityReport(
    val version: String? = null,
    val fsListV2: Boolean = false,
    val fileListV1: Boolean = false,
    val fileContentV1: Boolean = false,
    val commands: Boolean = false,
    val eventStream: Boolean = false,
    val projectsV1: Boolean = false,
    val sessionsListV2: Boolean = false,
    val sessionsListV1: Boolean = false,
    val permissions: Boolean = false,
    val apiPaths: List<String> = emptyList(),
    val probedV1: Boolean = true,
) {
    fun probeValue(key: String): Boolean? = when (key) {
        "fsListV2" -> fsListV2
        "fileListV1" -> fileListV1
        "fileContentV1" -> fileContentV1
        "commands" -> commands
        "eventStream" -> eventStream
        "projectsV1" -> projectsV1
        "sessionsListV2" -> sessionsListV2
        "sessionsListV1" -> sessionsListV1
        "permissions" -> permissions
        else -> null
    }
}
