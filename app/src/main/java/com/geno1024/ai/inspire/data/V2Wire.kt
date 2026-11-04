package com.geno1024.ai.inspire.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------------------
// OpenCode v2 线上模型（wire model）与归一化函数。
//
// UI / ViewModel 只认 [Session]、[Message]、[Part] 这套内部模型；v2 的
// tagged-union 消息在这里被压平成内部结构，因此界面无需感知协议版本。
// ---------------------------------------------------------------------------

@Serializable
data class V2Info(
    val version: String? = null,
    val pid: Int? = null,
    val hostname: String? = null,
    val urls: List<String> = emptyList(),
)

@Serializable
data class V2Location(val directory: String = "")

@Serializable
data class V2SessionTime(val created: Double = 0.0, val updated: Double = 0.0)

@Serializable
data class V2TokenCache(val read: Double = 0.0, val write: Double = 0.0)

@Serializable
data class V2Tokens(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val reasoning: Double = 0.0,
    val cache: V2TokenCache? = null,
)

@Serializable
data class V2ModelRef(
    val id: String? = null,
    @SerialName("providerID") val providerId: String? = null,
    val variant: String? = null,
)

@Serializable
data class V2Revert(
    @SerialName("messageID") val messageId: String = "",
    val partID: String? = null,
)

@Serializable
data class V2Session(
    val id: String = "",
    @SerialName("projectID") val projectId: String = "",
    @SerialName("parentID") val parentId: String? = null,
    val title: String = "",
    val time: V2SessionTime? = null,
    val tokens: V2Tokens? = null,
    val cost: Double = 0.0,
    val model: V2ModelRef? = null,
    val agent: String? = null,
    val revert: V2Revert? = null,
    val location: V2Location? = null,
)

@Serializable
data class V2SessionEnvelope(val data: V2Session = V2Session())

@Serializable
data class V2SessionsEnvelope(
    val data: List<V2Session> = emptyList(),
    val cursor: V2Cursor = V2Cursor(),
)

/** 消息 union 用 [JsonElement] 承载，再交给 [v2MessageToPair] 手动归一化。 */
@Serializable
data class V2SessionMessagesEnvelope(
    val data: List<JsonElement> = emptyList(),
    val cursor: V2Cursor = V2Cursor(),
)

@Serializable
data class V2ProjectTime(
    val created: Double = 0.0,
    val updated: Double = 0.0,
    val active: Double = 0.0,
)

@Serializable
data class V2Project(
    val id: String = "",
    val canonical: String = "",
    val name: String? = null,
    val vcs: String? = null,
    val time: V2ProjectTime? = null,
)

@Serializable
data class V2LocationInfo(
    val directory: String = "",
    val project: V2Project? = null,
)

@Serializable
data class V2Entry(val path: String = "", val type: String = "file")

@Serializable
data class V2EntriesEnvelope(
    val location: V2Location? = null,
    val data: List<V2Entry> = emptyList(),
)

@Serializable
data class V2Provider(
    val id: String = "",
    val name: String? = null,
    val activation: String? = null,
)

@Serializable
data class V2ProvidersEnvelope(
    val location: V2Location? = null,
    val data: List<V2Provider> = emptyList(),
)

@Serializable
data class V2Command(val name: String = "", val description: String = "")

@Serializable
data class V2CommandsEnvelope(
    val location: V2Location? = null,
    val data: List<V2Command> = emptyList(),
)

@Serializable
data class V2Active(val type: String = "")

@Serializable
data class V2ActiveEnvelope(val data: Map<String, V2Active> = emptyMap())

@Serializable
data class V2PermissionRequest(
    val id: String = "",
    @SerialName("sessionID") val sessionId: String = "",
    val action: String = "",
    val resources: List<String> = emptyList(),
    val save: List<String> = emptyList(),
    val metadata: JsonObject? = null,
    val message: String? = null,
)

@Serializable
data class V2PermissionEnvelope(
    val location: V2Location? = null,
    val data: List<V2PermissionRequest> = emptyList(),
)

@Serializable
data class V2FormInfo(
    val id: String = "",
    @SerialName("sessionID") val sessionId: String = "",
    val title: String = "",
    val fields: List<V2FormField> = emptyList(),
)

@Serializable
data class V2FormField(
    val key: String = "",
    val type: String = "",
    val title: String? = null,
    val description: String? = null,
    val required: Boolean = false,
    val hidden: Boolean = false,
    val options: List<V2FormOption> = emptyList(),
    val custom: Boolean = false,
    @SerialName("minItems") val minItems: Int? = null,
    @SerialName("maxItems") val maxItems: Int? = null,
)

@Serializable
data class V2FormOption(
    val value: String = "",
    val label: String = "",
    val description: String = "",
)

@Serializable
data class V2FormsEnvelope(
    val location: V2Location? = null,
    val data: List<V2FormInfo> = emptyList(),
)

@Serializable
data class V2InboxUser(
    val id: String = "",
    @SerialName("sessionID") val sessionId: String = "",
    val time: V2Created? = null,
    val type: String = "",
    val payload: V2InboxPayload? = null,
)

@Serializable
data class V2Created(val created: Double = 0.0)

@Serializable
data class V2InboxPayload(val text: String = "")

@Serializable
data class V2InboxEnvelope(val data: V2InboxUser = V2InboxUser())

@Serializable
data class V2Interrupt(val interrupted: Boolean = false)

@Serializable
data class V2ShellInfo(
    val id: String = "",
    val status: String = "",
    val command: String = "",
    val exit: Double? = null,
)

@Serializable
data class V2ShellEnvelope(val data: V2ShellInfo? = null)

@Serializable
data class V2ShellOutput(val output: String = "", val truncated: Boolean = false)

@Serializable
data class V2ShellOutputEnvelope(val data: V2ShellOutput? = null)

fun V2Tokens?.toInternalTokens(): Tokens? {
    if (this == null) return null
    val input = input.toLong()
    val output = output.toLong()
    val reasoning = reasoning.toLong()
    val cacheRead = cache?.read?.toLong() ?: 0L
    val cacheWrite = cache?.write?.toLong() ?: 0L
    return Tokens(
        total = input + output + reasoning + cacheRead + cacheWrite,
        input = input,
        output = output,
        reasoning = reasoning,
        cache = TokenCache(read = cacheRead, write = cacheWrite),
    )
}

fun V2SessionTime?.toInternalTime(): SessionTime? =
    this?.let { SessionTime(created = it.created.toLong(), updated = it.updated.toLong()) }

fun V2Session.toSession(): Session = Session(
    id = id,
    projectId = projectId,
    directory = location?.directory.orEmpty(),
    parentId = parentId,
    title = title,
    time = time.toInternalTime(),
    tokens = tokens.toInternalTokens(),
    model = model?.let { ModelV2Ref(id = it.id, providerId = it.providerId, variant = it.variant) },
    agent = agent,
    revert = revert?.let { SessionRevert(messageId = it.messageId, partID = it.partID) },
)

fun V2Session.toSessionV2Info(): SessionV2Info = SessionV2Info(
    id = id,
    parentId = parentId,
    projectId = projectId,
    title = title,
    time = time.toInternalTime(),
    location = LocationRef(directory = location?.directory.orEmpty()),
    tokens = tokens.toInternalTokens(),
    cost = cost,
    model = model?.let { ModelV2Ref(id = it.id, providerId = it.providerId, variant = it.variant) },
    agent = agent,
)

fun V2Project.toProject(): Project = Project(
    id = id,
    worktree = canonical,
    vcsDir = canonical,
    vcs = vcs,
    time = time?.let { SessionTime(created = it.created.toLong(), updated = it.updated.toLong()) },
)

fun V2PermissionRequest.toInternal(): PermissionRequest = PermissionRequest(
    id = id,
    sessionId = sessionId,
    permission = action,
    patterns = resources,
    metadata = metadata,
    always = save,
    tool = null,
)

/** 把 v2 的 `/api/session/{id}/message` 响应压平为内部 (Message, parts) 对。 */
fun parseV2Messages(sessionId: String, text: String, json: Json): List<Pair<Message, List<Part>>> {
    if (text.isBlank()) return emptyList()
    val array = runCatching {
        json.parseToJsonElement(text).jsonObject["data"]?.jsonArray
    }.getOrNull() ?: return emptyList()
    return array.mapNotNull { el -> (el as? JsonObject)?.let { v2MessageToPair(sessionId, it) } }
}

fun v2MessageToPair(sessionId: String, obj: JsonObject): Pair<Message, List<Part>>? {
    val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val timeObj = obj["time"]?.jsonObject
    val created = timeObj?.get("created")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong()
    val completed = timeObj?.get("completed")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong()

    return when (type) {
        "user" -> {
            val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            Message(
                id = id,
                sessionID = sessionId,
                role = "user",
                time = MessageTime(created = created, completed = completed),
            ) to listOf(Part(type = "text", text = text))
        }
        "assistant" -> {
            val modelRef = obj["model"]?.jsonObject
            val modelId = modelRef?.get("id")?.jsonPrimitive?.contentOrNull
            val providerId = modelRef?.get("providerID")?.jsonPrimitive?.contentOrNull
            val content = obj["content"]?.jsonArray ?: JsonArray(emptyList())
            val parts = content.mapNotNull { (it as? JsonObject)?.let(::v2ContentToPart) }
            Message(
                id = id,
                sessionID = sessionId,
                role = "assistant",
                provider = providerId?.let { ProviderRef(id = it) },
                model = modelId?.let { ModelRef(id = it) },
                providerID = providerId,
                modelID = modelId,
                tokens = v2TokensElement(obj["tokens"]),
                time = MessageTime(created = created, completed = completed),
                error = obj["error"],
            ) to parts
        }
        "system" -> {
            Message(
                id = id,
                sessionID = sessionId,
                role = "system",
                time = MessageTime(created = created, completed = completed),
            ) to listOf(Part(type = "text", text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()))
        }
        "synthetic" -> {
            Message(
                id = id,
                sessionID = sessionId,
                role = "synthetic",
                time = MessageTime(created = created, completed = completed),
            ) to listOf(Part(type = "text", text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()))
        }
        "shell" -> {
            val command = obj["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val status = obj["status"]?.jsonPrimitive?.contentOrNull
            val output = obj["output"]?.jsonObject?.get("output")?.jsonPrimitive?.contentOrNull
            val part = Part(
                type = "tool",
                tool = "shell",
                title = "shell",
                state = ToolState(
                    status = status,
                    input = JsonPrimitive(command),
                    output = output,
                ),
            )
            Message(
                id = id,
                sessionID = sessionId,
                role = "shell",
                time = MessageTime(created = created, completed = completed),
            ) to listOf(part)
        }
        "compaction" -> {
            val summary = obj["summary"]?.jsonPrimitive?.contentOrNull
                ?: obj["error"]?.let(::v2ErrorText)
                ?: ""
            Message(
                id = id,
                sessionID = sessionId,
                role = "assistant",
                time = MessageTime(created = created, completed = completed),
            ) to listOf(Part(type = "text", text = summary))
        }
        // idle 是回合终止标记；agent/model/location 切换与 skill 不是对话内容。
        else -> null
    }
}

private fun v2ContentToPart(obj: JsonObject): Part? = when (obj["type"]?.jsonPrimitive?.contentOrNull) {
    "text" -> Part(type = "text", text = obj["text"]?.jsonPrimitive?.contentOrNull)
    "reasoning" -> Part(type = "reasoning", text = obj["text"]?.jsonPrimitive?.contentOrNull)
    "tool" -> {
        val state = obj["state"]?.jsonObject
        val status = state?.get("status")?.jsonPrimitive?.contentOrNull
        val output = v2ToolContentText(state)
        val error = state?.get("error")?.let(::v2ErrorText)
        Part(
            type = "tool",
            tool = obj["name"]?.jsonPrimitive?.contentOrNull,
            title = obj["name"]?.jsonPrimitive?.contentOrNull,
            callID = obj["id"]?.jsonPrimitive?.contentOrNull,
            state = ToolState(
                status = status,
                input = state?.get("input"),
                output = output ?: error,
            ),
        )
    }
    else -> null
}

private fun v2ToolContentText(state: JsonObject?): String? {
    val content = state?.get("content")?.jsonArray ?: return null
    val text = content.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        when (o["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> o["text"]?.jsonPrimitive?.contentOrNull
            "file" -> o["name"]?.jsonPrimitive?.contentOrNull
                ?: o["uri"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }.joinToString("")
    return text.ifBlank { null }
}

fun v2ErrorText(el: JsonElement?): String? {
    if (el == null) return null
    val obj = el as? JsonObject ?: return (el as? JsonPrimitive)?.contentOrNull
    obj["message"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank()) return it }
    obj["data"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        ?.let { if (it.isNotBlank()) return it }
    return obj.toString()
}

private fun v2TokensElement(el: JsonElement?): Tokens? {
    if (el == null) return null
    return runCatching {
        val obj = el.jsonObject
        val input = obj["input"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
        val output = obj["output"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
        val reasoning = obj["reasoning"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
        val cache = obj["cache"]?.jsonObject
        val cacheRead = cache?.get("read")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
        val cacheWrite = cache?.get("write")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
        Tokens(
            total = input + output + reasoning + cacheRead + cacheWrite,
            input = input,
            output = output,
            reasoning = reasoning,
            cache = TokenCache(read = cacheRead, write = cacheWrite),
        )
    }.getOrNull()
}
