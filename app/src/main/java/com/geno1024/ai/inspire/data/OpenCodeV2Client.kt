package com.geno1024.ai.inspire.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OpenCode 2.x（/api 全量路由、tagged-union 消息、forms、staged revert）实现。
 */
class OpenCodeV2Client(transport: HttpTransport) : OpenCodeClientBase(transport) {

    private val formCache = HashMap<String, V2FormInfo>()
    private val permissionSessions = HashMap<String, String>()
    private val sessionDirs = HashMap<String, String>()

    override suspend fun health(): HealthResponse {
        val info = transport.getJsonObject("/api/info")
        return HealthResponse(
            healthy = info != null,
            version = info?.get("version")?.jsonPrimitive?.contentOrNull,
        )
    }

    override suspend fun projects(): List<Project> =
        transport.execute("GET", "/api/project") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(V2Project.serializer()), text).map { it.toProject() }
        }

    override suspend fun currentProject(): Project? {
        val info = transport.execute(
            "GET",
            "/api/location",
        ) { text ->
            if (text.isBlank()) null
            else runCatching { json.decodeFromString(V2LocationInfo.serializer(), text) }.getOrNull()
        } ?: return null
        return info.project?.toProject()
            ?: Project(id = "", worktree = info.directory, vcsDir = info.directory, vcs = null)
    }

    override suspend fun listDirectory(locationDir: String?, path: String?): List<FileNode> =
        transport.execute(
            "GET",
            "/api/fs/list${transport.query(mapOf("path" to path))}",
            headers = transport.headersFor(locationDir),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(V2EntriesEnvelope.serializer(), text).data.map { entry ->
                FileNode(
                    name = entry.path.trimEnd('/').substringAfterLast('/'),
                    type = entry.type,
                    path = entry.path,
                )
            }
        }

    override suspend fun readFileContent(locationDir: String?, path: String): String? =
        transport.execute(
            "GET",
            "/api/fs/read/${path.trimStart('/')}",
            headers = transport.headersFor(locationDir),
            readTimeoutSeconds = 60L,
        ) { text -> text.ifBlank { null } }

    override suspend fun createSession(
        directory: String?,
        parentId: String?,
        title: String?,
    ): Session {
        val body = buildJsonObject {
            if (parentId != null) put("parentID", parentId)
            if (title != null) put("title", title)
            if (!directory.isNullOrBlank()) {
                put("location", buildJsonObject { put("directory", directory) })
            }
        }
        return transport.execute(
            "POST",
            "/api/session",
            body = body.toString(),
            readTimeoutSeconds = 120L,
        ) { text ->
            json.decodeFromString(V2SessionEnvelope.serializer(), text).data.toSession()
        }
    }

    override suspend fun runShell(sessionId: String, command: String, agent: String): ShellResult {
        val cwd = resolveSessionDir(sessionId)
        val body = buildJsonObject {
            put("command", command)
            if (!cwd.isNullOrBlank()) put("cwd", cwd)
        }
        val started = runCatching {
            transport.execute("POST", "/api/shell", body = body.toString()) { text ->
                if (text.isBlank()) null
                else json.decodeFromString(V2ShellEnvelope.serializer(), text).data
            }
        }.getOrNull() ?: return ShellResult("error", "")

        val deadline = System.currentTimeMillis() + 600_000L
        var info = started
        while (info.status == "running" && System.currentTimeMillis() < deadline) {
            delay(400)
            val next = runCatching {
                transport.execute("GET", "/api/shell/${info.id}") { text ->
                    if (text.isBlank()) null
                    else json.decodeFromString(V2ShellEnvelope.serializer(), text).data
                }
            }.getOrNull()
            if (next != null) info = next
        }
        val output = runCatching {
            transport.execute("GET", "/api/shell/${info.id}/output") { text ->
                if (text.isBlank()) null
                else json.decodeFromString(V2ShellOutputEnvelope.serializer(), text).data?.output
            }
        }.getOrNull().orEmpty()
        runCatching { transport.execute("DELETE", "/api/shell/${info.id}") {} }

        val status = when {
            info.status == "exited" && (info.exit ?: 0.0) == 0.0 -> "completed"
            info.status == "running" -> "error"
            else -> "error"
        }
        return ShellResult(status, output)
    }

    private suspend fun resolveSessionDir(sessionId: String): String? {
        sessionDirs[sessionId]?.let { return it }
        val dir = runCatching { session(sessionId).directory }.getOrNull()
        if (!dir.isNullOrBlank()) sessionDirs[sessionId] = dir
        return dir
    }

    override suspend fun sessions(): List<Session> =
        transport.execute("GET", "/api/session?limit=1000&order=desc") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(V2SessionsEnvelope.serializer(), text).data.map { it.toSession() }
        }

    override suspend fun sessionStatuses(directory: String?): Map<String, SessionStatusInfo> =
        transport.execute(
            "GET",
            "/api/session/active",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyMap()
            else json.decodeFromString(V2ActiveEnvelope.serializer(), text).data.mapValues { (_, v) ->
                SessionStatusInfo(type = if (v.type == "running") "busy" else v.type)
            }
        }

    override suspend fun providers(directory: String?): ProvidersV2Response {
        val providers = transport.execute(
            "GET",
            "/api/provider",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(V2ProvidersEnvelope.serializer(), text).data
        }
        val models = runCatching { models() }.getOrDefault(emptyList())
        val byProvider = models.groupBy { it.providerId.orEmpty() }
        val all = providers.map { p ->
            ProviderV2Info(
                id = p.id,
                name = p.name,
                source = p.activation,
                models = byProvider[p.id].orEmpty().mapNotNull { m ->
                    val id = m.id ?: return@mapNotNull null
                    id to ProviderV2Model(
                        id = id,
                        providerId = p.id,
                        name = m.name,
                        status = m.status,
                    )
                }.toMap(),
            )
        }
        return ProvidersV2Response(all = all, connected = all.filter { it.models.isNotEmpty() }.map { it.id })
    }

    override suspend fun sessionDetail(id: String): SessionV2Info? =
        transport.execute("GET", "/api/session/$id") { text ->
            if (text.isBlank()) null
            else runCatching {
                json.decodeFromString(V2SessionEnvelope.serializer(), text).data.toSessionV2Info()
            }.getOrNull()
        }

    override suspend fun pendingQuestions(directory: String?): List<QuestionRequest> {
        val forms = fetchForms(directory)
        return forms.map { form -> form.toQuestionRequest() }
    }

    private suspend fun fetchForms(directory: String?): List<V2FormInfo> =
        transport.execute(
            "GET",
            "/api/form",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(V2FormsEnvelope.serializer(), text).data
        }.also { forms -> forms.forEach { formCache[it.id] = it } }

    private fun V2FormInfo.toQuestionRequest(): QuestionRequest {
        val fields = mappableFields()
        val questions = fields.map { f ->
            QuestionInfo(
                question = f.description?.takeIf { it.isNotBlank() }
                    ?: f.title?.takeIf { it.isNotBlank() }
                    ?: f.key,
                header = f.title?.takeIf { it.isNotBlank() } ?: title,
                options = f.options.map { QuestionOption(label = it.label, description = it.description) },
                multiple = (f.maxItems ?: 1) > 1,
                custom = f.custom || f.type == "string" || f.type == "number" || f.type == "integer",
            )
        }
        val effective = if (questions.isNotEmpty()) questions else listOf(
            QuestionInfo(question = title, header = title, multiple = false, custom = true)
        )
        return QuestionRequest(id = id, sessionId = sessionId, questions = effective)
    }

    private fun V2FormInfo.mappableFields(): List<V2FormField> =
        fields.filter { !it.hidden && it.type in setOf("multiselect", "string", "number", "integer", "boolean") }

    override suspend fun replyQuestion(requestId: String, answers: List<List<String>>, directory: String?) {
        val form = formCache[requestId] ?: fetchForms(directory).firstOrNull { it.id == requestId } ?: return
        val fields = form.mappableFields()
        val answer = buildJsonObject {
            fields.forEachIndexed { index, field ->
                val labels = answers.getOrNull(index).orEmpty()
                when (field.type) {
                    "multiselect" -> put(field.key, JsonArray(labels.mapNotNull { label ->
                        field.options.firstOrNull { it.label == label || it.value == label }?.let { JsonPrimitive(it.value) }
                            ?: label.takeIf { field.custom }?.let { JsonPrimitive(it) }
                    }))
                    "boolean" -> {
                        val v = labels.firstOrNull()
                        put(field.key, JsonPrimitive(v == "true" || v.equals("yes", true)))
                    }
                    "number", "integer" -> {
                        val v = labels.firstOrNull()
                        val d = v?.toDoubleOrNull()
                        if (d != null) put(field.key, JsonPrimitive(d)) else v?.let { put(field.key, it) }
                    }
                    else -> labels.firstOrNull()?.let { label ->
                        val value = field.options.firstOrNull { it.label == label || it.value == label }?.value
                        put(field.key, value ?: label)
                    }
                }
            }
        }
        transport.execute(
            "POST",
            "/api/session/${form.sessionId}/form/${form.id}/reply",
            body = buildJsonObject { put("answer", answer) }.toString(),
        ) {}
        formCache.remove(requestId)
    }

    override suspend fun rejectQuestion(requestId: String, directory: String?) {
        val form = formCache[requestId] ?: fetchForms(directory).firstOrNull { it.id == requestId } ?: return
        transport.execute("DELETE", "/api/session/${form.sessionId}/form/${form.id}") {}
        formCache.remove(requestId)
    }

    override suspend fun session(id: String): Session =
        transport.execute("GET", "/api/session/$id") { text ->
            json.decodeFromString(V2SessionEnvelope.serializer(), text).data.toSession()
        }

    override suspend fun sessionMessages(sessionId: String, limit: Int): List<Pair<Message, List<Part>>> {
        val text = transport.execute("GET", "/api/session/$sessionId/message?limit=${messageLimit(limit)}") { it }
        return parseV2Messages(sessionId, text, json).reversed()
    }

    /** v2 的 `/message` 路由把 limit 硬限制在 200 以内，超出会返回 400 InvalidRequestError。 */
    private fun messageLimit(limit: Int): Int = limit.coerceIn(1, 200)

    override suspend fun sessionMessagesPage(
        sessionId: String,
        limit: Int,
        before: String?,
    ): Pair<List<Pair<Message, List<Part>>>, String?> {
        val path = "/api/session/$sessionId/message?limit=${messageLimit(limit)}" +
            (before?.let { "&cursor=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "")
        val text = transport.execute("GET", path) { it }
        val envelope = runCatching {
            json.decodeFromString(V2SessionMessagesEnvelope.serializer(), text)
        }.getOrNull()
        val page = if (envelope != null) {
            envelope.data.mapNotNull { (it as? JsonObject)?.let { o -> v2MessageToPair(sessionId, o) } }
        } else {
            emptyList()
        }
        // v2 默认倒序；App 的消息列表是升序渲染，所以反转页内顺序。
        return page.reversed() to envelope?.cursor?.next
    }

    override suspend fun sessionMessagesAll(
        sessionId: String,
        onProgress: (fetched: Int, lastTimestamp: Long) -> Unit,
    ): List<Pair<Message, List<Part>>> {
        val all = mutableListOf<Pair<Message, List<Part>>>()
        var cursor: String? = null
        while (true) {
            val (page, next) = sessionMessagesPage(sessionId, 500, cursor)
            if (page.isEmpty()) break
            // page 已升序，拼接时保持整体升序。
            all.addAll(0, page)
            val lastTime = page.lastOrNull()?.first?.time?.created ?: 0L
            onProgress(all.size, lastTime)
            cursor = next
            if (next == null) break
        }
        return all.sortedBy { it.first.time?.created ?: Long.MIN_VALUE }
    }

    override suspend fun sessionMessagesSince(
        sessionId: String,
        sinceMs: Long,
        onProgress: (fetched: Int, earliestSeen: Long) -> Unit,
    ): List<Pair<Message, List<Part>>> {
        val result = mutableListOf<Pair<Message, List<Part>>>()
        var cursor: String? = null
        var done = false
        var earliestMs = 0L
        while (!done) {
            val (page, next) = sessionMessagesPage(sessionId, 500, cursor)
            if (page.isEmpty()) break
            for (item in page) {
                val created = item.first.time?.created ?: 0L
                if (created > 0L && sinceMs > 0L && created <= sinceMs) {
                    done = true
                    break
                }
                if (created > 0L) {
                    result.add(item)
                    if (earliestMs == 0L || created < earliestMs) earliestMs = created
                }
            }
            onProgress(result.size, earliestMs)
            cursor = next
            if (next == null) break
        }
        return result.sortedBy { it.first.time?.created ?: Long.MIN_VALUE }
    }

    override suspend fun commands(directory: String?): List<Command> =
        transport.execute(
            "GET",
            "/api/command",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(V2CommandsEnvelope.serializer(), text).data.map {
                Command(name = it.name, description = it.description)
            }
        }

    override suspend fun executeCommand(sessionId: String, command: String, arguments: String) {
        transport.execute(
            "POST",
            "/api/session/$sessionId/command",
            body = buildJsonObject {
                put("name", command)
                put("text", arguments)
            }.toString(),
        ) {}
    }

    override suspend fun pendingPermissions(directory: String?): List<PermissionRequest> {
        val list = transport.execute(
            "GET",
            "/api/permission/request",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(V2PermissionEnvelope.serializer(), text).data
        }
        list.forEach { permissionSessions[it.id] = it.sessionId }
        return list.map { it.toInternal() }
    }

    override suspend fun replyPermission(
        requestId: String,
        reply: String,
        message: String?,
        directory: String?,
    ) {
        val sessionId = permissionSessions[requestId]
            ?: pendingPermissions(directory).firstOrNull { it.id == requestId }?.sessionId
            ?: return
        val decision = when (reply.lowercase()) {
            "always" -> "always"
            "reject", "never", "deny" -> "reject"
            else -> "once"
        }
        transport.execute(
            "POST",
            "/api/session/$sessionId/permission/$requestId/reply",
            body = buildJsonObject {
                put("decision", decision)
                if (!message.isNullOrBlank()) put("message", message)
            }.toString(),
        ) {}
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        transport.execute(
            "PATCH",
            "/api/session/$sessionId",
            body = buildJsonObject { put("title", title) }.toString(),
        ) {}
    }

    override suspend fun deleteSession(sessionId: String): Boolean =
        runCatching {
            transport.execute("DELETE", "/api/session/$sessionId") {}
            true
        }.getOrDefault(false)

    override suspend fun sessionTodos(sessionId: String): List<TodoInfo> = emptyList()

    override suspend fun sendPromptAsync(sessionId: String, text: String) {
        transport.execute(
            "POST",
            "/api/session/$sessionId/prompt",
            body = promptBody(text),
        ) {}
    }

    override suspend fun sendPrompt(sessionId: String, text: String): Pair<Message, List<Part>> =
        transport.execute(
            "POST",
            "/api/session/$sessionId/prompt",
            body = promptBody(text),
        ) { body ->
            val inbox = runCatching {
                json.decodeFromString(V2InboxEnvelope.serializer(), body).data
            }.getOrNull()
            val id = inbox?.id?.takeIf { it.isNotBlank() } ?: "user-${System.currentTimeMillis()}"
            val created = inbox?.time?.created?.toLong() ?: System.currentTimeMillis()
            Message(
                id = id,
                sessionID = sessionId,
                role = "user",
                time = MessageTime(created = created),
            ) to listOf(Part(type = "text", text = text))
        }

    private fun promptBody(text: String): String = buildJsonObject {
        put("text", text)
    }.toString()

    override suspend fun abortSession(sessionId: String): Boolean =
        runCatching {
            transport.execute("POST", "/api/session/$sessionId/interrupt") { body ->
                if (body.isBlank()) true
                else json.decodeFromString(V2Interrupt.serializer(), body).interrupted
            }
        }.getOrDefault(false)

    override suspend fun summarizeSession(sessionId: String, providerId: String, modelId: String) {
        transport.execute(
            "POST",
            "/api/session/$sessionId/compact",
            body = "{}",
        ) {}
    }

    override suspend fun revertMessage(sessionId: String, messageID: String): Session {
        transport.execute(
            "POST",
            "/api/session/$sessionId/revert/stage",
            body = buildJsonObject { put("messageID", messageID) }.toString(),
        ) {}
        return session(sessionId)
    }

    override suspend fun unrevertMessage(sessionId: String): Session {
        transport.execute("DELETE", "/api/session/$sessionId/revert") {}
        return session(sessionId)
    }

    override fun eventStream(): Flow<String> = transport.stream("/api/event")

    override suspend fun searchFiles(query: String, limit: Int): List<String> =
        transport.execute(
            "GET",
            "/api/fs/find${transport.query(mapOf("query" to query, "limit" to limit.toString()))}",
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(V2EntriesEnvelope.serializer(), text).data.map { it.path }
        }

    override suspend fun probeCapabilities(
        cachedPaths: List<String>,
        cachedVersion: String?,
    ): CapabilityReport {
        val info = transport.getJsonObject("/api/info")
        val version = info?.get("version")?.jsonPrimitive?.contentOrNull
        val cacheValid = cachedPaths.isNotEmpty() && cachedVersion != null && cachedVersion == version
        val paths = if (cacheValid) cachedPaths else {
            withTimeoutOrNull(8_000) { fetchOpenApiPaths() } ?: emptyList()
        }
        return CapabilityReport(
            version = version,
            fsListV2 = true,
            fileListV1 = false,
            fileContentV1 = false,
            commands = true,
            eventStream = true,
            projectsV1 = false,
            sessionsListV2 = true,
            sessionsListV1 = false,
            permissions = true,
            apiPaths = paths,
            probedV1 = false,
        )
    }

    private suspend fun fetchOpenApiPaths(): List<String>? =
        transport.execute("GET", "/openapi.json", readTimeoutSeconds = 8L) { text ->
            if (text.isBlank()) emptyList()
            else json.parseToJsonElement(text).jsonObject["paths"]?.jsonObject?.keys?.toList() ?: emptyList()
        }

    override fun applyCapabilities(report: CapabilityReport) {
        // v2 的路径固定，无需按探测结果切换。
    }
}
