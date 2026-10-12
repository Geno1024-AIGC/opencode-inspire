package com.geno1024.ai.inspire.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    val time: SessionTime? = null,
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
data class SessionStatusInfo(
    val type: String? = null,
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
    val name: String? = null,
    val family: String? = null,
    val api: ModelApi? = null,
    val capabilities: ModelCapabilities? = null,
    val request: JsonObject? = null,
    val variants: List<JsonObject> = emptyList(),
    val time: ModelTime? = null,
    val cost: List<ModelCost> = emptyList(),
    val status: String? = null,
    val enabled: Boolean = true,
    val limit: ModelLimit? = null,
)

@Serializable
data class ModelApi(
    val id: String? = null,
    val type: String? = null,
    @SerialName("package") val packageName: String? = null,
    val url: String? = null,
    val settings: JsonObject? = null,
)

@Serializable
data class ModelCapabilities(
    val tools: Boolean = false,
    val input: List<String> = emptyList(),
    val output: List<String> = emptyList(),
)

@Serializable
data class ModelTime(
    val released: Long? = null,
)

@Serializable
data class ModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cache: ModelCostCache? = null,
)

@Serializable
data class ModelCostCache(
    val read: Double = 0.0,
    val write: Double = 0.0,
)

@Serializable
data class ModelLimit(
    val context: Long = 0L,
    val input: Long? = null,
    val output: Long? = null,
)

@Serializable
data class ProviderV2Info(
    val id: String,
    val name: String? = null,
    val source: String? = null,
    val env: List<String> = emptyList(),
    val options: JsonObject = JsonObject(emptyMap()),
    val models: Map<String, ProviderV2Model> = emptyMap(),
)

@Serializable
data class ProviderV2Model(
    val id: String,
    @SerialName("providerID") val providerId: String = "",
    val name: String? = null,
    val status: String? = null,
)

@Serializable
data class ProvidersV2Response(
    val all: List<ProviderV2Info> = emptyList(),
    val default: JsonObject = JsonObject(emptyMap()),
    val connected: List<String> = emptyList(),
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
    val metadata: Map<String, JsonElement>? = null,
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
    val sid: String = "",
    val fingerprint: String = "",
)

@Serializable
data class TokenStore(
    val history: Map<String, TokenDay> = emptyMap(),
    val elapsed: Map<String, Long> = emptyMap(),
    val month: Map<String, Map<Int, TokenDay>> = emptyMap(),
    val week: Map<String, Map<Int, TokenDay>> = emptyMap(),
    val dayHours: Map<String, Map<Int, TokenDay>> = emptyMap(),
    val modelStats: Map<String, TokenModelStats> = emptyMap(),
    val projectStats: Map<String, TokenModelStats> = emptyMap(),
    val sessionModelTokens: Map<String, Map<String, TokenDay>> = emptyMap(),
    val sync: Long = 0L,
    val syncedAt: Long = 0L,
    val rawBuckets: List<TokenRawBucket> = emptyList(),
    val rawSync: Long = 0L,
    val sessionSync: Map<String, Long> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = history.isEmpty() && rawBuckets.isEmpty() && modelStats.isEmpty() &&
            projectStats.isEmpty() && sessionModelTokens.isEmpty()
}

private fun <K> unionTokenDayMapsPreserving(a: Map<K, TokenDay>, b: Map<K, TokenDay>): Map<K, TokenDay> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val out = HashMap<K, TokenDay>(a.size + b.size)
    out.putAll(a)
    for ((k, v) in b) out.putIfAbsent(k, v)
    return out
}

private fun unionHourMapsPreserving(
    a: Map<String, Map<Int, TokenDay>>,
    b: Map<String, Map<Int, TokenDay>>,
): Map<String, Map<Int, TokenDay>> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val out = HashMap<String, Map<Int, TokenDay>>(a.size + b.size)
    out.putAll(a)
    for ((k, v) in b) {
        out[k] = if (out.containsKey(k)) unionTokenDayMapsPreserving(out.getValue(k), v) else v
    }
    return out
}

private fun unionLongMapsPreserving(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val out = HashMap<String, Long>(a.size + b.size)
    out.putAll(a)
    for ((k, v) in b) out.merge(k, v, ::maxOf)
    return out
}

private fun aggregateTokenModelStats(maps: Collection<Map<String, TokenModelStats>>): Map<String, TokenModelStats> {
    val out = HashMap<String, TokenModelStats>()
    for (m in maps) for ((k, v) in m) out[k] = out[k]?.plus(v) ?: v
    return out
}

fun aggregateTokenStores(stores: Collection<TokenStore>): TokenStore {
    if (stores.isEmpty()) return TokenStore()
    if (stores.size == 1) return stores.first()
    val history = HashMap<String, TokenDay>()
    val elapsed = HashMap<String, Long>()
    val month = HashMap<String, Map<Int, TokenDay>>()
    val week = HashMap<String, Map<Int, TokenDay>>()
    val dayHours = HashMap<String, Map<Int, TokenDay>>()
    val modelStats = HashMap<String, TokenModelStats>()
    val projectStats = HashMap<String, TokenModelStats>()
    val sessionModelTokens = HashMap<String, Map<String, TokenDay>>()
    val rawByKey = HashMap<Triple<Long, String, String>, TokenRawBucket>()
    val sessionSync = HashMap<String, Long>()
    var sync = 0L
    var syncedAt = 0L
    var rawSync = 0L
    for (s in stores) {
        for ((k, v) in s.history) history.merge(k, v) { x, y -> x + y }
        for ((k, v) in s.elapsed) elapsed.merge(k, v) { x, y -> x + y }
        for ((k, v) in s.month) month[k] = mergeTokenDayMap(month[k] ?: emptyMap(), v)
        for ((k, v) in s.week) week[k] = mergeTokenDayMap(week[k] ?: emptyMap(), v)
        for ((k, v) in s.dayHours) dayHours[k] = mergeTokenDayMap(dayHours[k] ?: emptyMap(), v)
        for ((k, v) in s.modelStats) modelStats[k] = modelStats[k]?.plus(v) ?: v
        for ((k, v) in s.projectStats) projectStats[k] = projectStats[k]?.plus(v) ?: v
        for ((k, v) in s.sessionModelTokens) sessionModelTokens[k] = mergeTokenDayMap(sessionModelTokens[k] ?: emptyMap(), v)
        for (b in s.rawBuckets) {
            val key = Triple(b.epochBucket, b.model, b.projectId)
            rawByKey.merge(key, b) { x, y -> x + y }
        }
        for ((k, v) in s.sessionSync) sessionSync.merge(k, v, ::maxOf)
        if (s.sync > sync) sync = s.sync
        if (s.syncedAt > syncedAt) syncedAt = s.syncedAt
        if (s.rawSync > rawSync) rawSync = s.rawSync
    }
    return TokenStore(
        history = history,
        elapsed = elapsed,
        month = month,
        week = week,
        dayHours = dayHours,
        modelStats = modelStats,
        projectStats = projectStats,
        sessionModelTokens = sessionModelTokens,
        sync = sync,
        syncedAt = syncedAt,
        rawBuckets = rawByKey.values.sortedWith(compareBy({ it.epochBucket }, { it.model }, { it.projectId })),
        rawSync = rawSync,
        sessionSync = sessionSync,
    )
}

fun mergeTokenStoreDedup(a: TokenStore, b: TokenStore): TokenStore {
    if (a.isEmpty) return b
    if (b.isEmpty) return a
    val rawByKey = HashMap<Triple<Long, String, String>, TokenRawBucket>()
    for (x in a.rawBuckets) rawByKey[Triple(x.epochBucket, x.model, x.projectId)] = x
    for (x in b.rawBuckets) {
        val key = Triple(x.epochBucket, x.model, x.projectId)
        val existing = rawByKey[key]
        rawByKey[key] = when {
            existing == null -> x
            x.total > existing.total -> x
            else -> existing
        }
    }
    return TokenStore(
        history = unionTokenDayMapsPreserving(a.history, b.history),
        elapsed = unionLongMapsPreserving(a.elapsed, b.elapsed),
        month = unionHourMapsPreserving(a.month, b.month),
        week = unionHourMapsPreserving(a.week, b.week),
        dayHours = unionHourMapsPreserving(a.dayHours, b.dayHours),
        modelStats = unionTokenModelStatsPreserving(a.modelStats, b.modelStats),
        projectStats = unionTokenModelStatsPreserving(a.projectStats, b.projectStats),
        sessionModelTokens = unionSessionMapsPreserving(a.sessionModelTokens, b.sessionModelTokens),
        sync = maxOf(a.sync, b.sync),
        syncedAt = maxOf(a.syncedAt, b.syncedAt),
        rawBuckets = rawByKey.values.sortedWith(compareBy({ it.epochBucket }, { it.model }, { it.projectId })),
        rawSync = maxOf(a.rawSync, b.rawSync),
        sessionSync = unionLongMapsPreserving(a.sessionSync, b.sessionSync),
    )
}

private fun unionTokenModelStatsPreserving(
    a: Map<String, TokenModelStats>,
    b: Map<String, TokenModelStats>,
): Map<String, TokenModelStats> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val out = HashMap<String, TokenModelStats>(a.size + b.size)
    out.putAll(a)
    for ((k, v) in b) out.putIfAbsent(k, v)
    return out
}

private fun unionSessionMapsPreserving(
    a: Map<String, Map<String, TokenDay>>,
    b: Map<String, Map<String, TokenDay>>,
): Map<String, Map<String, TokenDay>> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val out = HashMap<String, Map<String, TokenDay>>(a.size + b.size)
    out.putAll(a)
    for ((k, v) in b) {
        out[k] = if (out.containsKey(k)) unionTokenDayMapsPreserving(out.getValue(k), v) else v
    }
    return out
}

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
