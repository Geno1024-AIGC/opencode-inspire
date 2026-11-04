package com.geno1024.ai.inspire.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OpenCode 1.x（含 1.15+ 的 /api 路由）实现。
 */
class OpenCodeV1Client(transport: HttpTransport) : OpenCodeClientBase(transport) {

    private var fsListV2 = true

    override suspend fun health(): HealthResponse =
        transport.execute("GET", "/global/health") {
            json.decodeFromString(HealthResponse.serializer(), it)
        }

    override suspend fun projects(): List<Project> =
        transport.execute("GET", "/project") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(Project.serializer()), text)
        }

    override suspend fun currentProject(): Project? =
        transport.execute("GET", "/project/current") { text ->
            if (text.isBlank() || text == "{}") null
            else json.decodeFromString(Project.serializer(), text)
        }

    override suspend fun listDirectory(locationDir: String?, path: String?): List<FileNode> =
        if (fsListV2) listDirectoryV2(locationDir, path) else listDirectoryLegacy(locationDir, path)

    private suspend fun listDirectoryV2(locationDir: String?, path: String?): List<FileNode> =
        transport.execute(
            "GET",
            "/api/fs/list${transport.query(mapOf("directory" to locationDir, "path" to path))}",
            headers = transport.headersFor(locationDir),
        ) { text ->
            if (text.isBlank()) emptyList()
            else {
                val obj = json.parseToJsonElement(text).jsonObject
                val data = obj["data"]?.jsonArray ?: return@execute emptyList()
                data.mapNotNull { el ->
                    if (el !is JsonObject) return@mapNotNull null
                    val p = el["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    FileNode(
                        name = p.trimEnd('/').substringAfterLast('/'),
                        type = el["type"]?.jsonPrimitive?.content ?: "file",
                        path = p,
                    )
                }
            }
        }

    private suspend fun listDirectoryLegacy(locationDir: String?, path: String?): List<FileNode> =
        transport.execute(
            "GET",
            "/file${transport.query(mapOf("directory" to locationDir, "path" to path))}",
            headers = transport.headersFor(locationDir),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.parseToJsonElement(text).jsonArray.mapNotNull { el ->
                if (el !is JsonObject) return@mapNotNull null
                val title = el["title"]?.jsonPrimitive?.contentOrNull
                    ?: el["name"]?.jsonPrimitive?.contentOrNull
                    ?: el["fileName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val typeRaw = el["type"]?.jsonPrimitive?.contentOrNull ?: ""
                val isDir = typeRaw.contains("dir", ignoreCase = true)
                val p = el["path"]?.jsonPrimitive?.contentOrNull
                    ?: el["fullPath"]?.jsonPrimitive?.contentOrNull
                    ?: title
                FileNode(
                    name = title.trimEnd('/').substringAfterLast('/'),
                    type = if (isDir) "directory" else "file",
                    path = p,
                )
            }
        }

    override suspend fun readFileContent(locationDir: String?, path: String): String? =
        transport.execute(
            "GET",
            "/file/content${transport.query(mapOf("directory" to locationDir, "path" to path))}",
            headers = transport.headersFor(locationDir),
        ) { text ->
            if (text.isBlank()) null
            else json.parseToJsonElement(text).jsonObject["content"]?.jsonPrimitive?.contentOrNull
        }

    override suspend fun createSession(directory: String?, parentId: String?, title: String?): Session =
        transport.execute(
            "POST",
            "/session${transport.query(mapOf("directory" to directory))}",
            body = buildJsonObject {
                if (parentId != null) put("parentID", parentId)
                if (title != null) put("title", title)
            }.toString(),
            readTimeoutSeconds = 120L,
        ) { json.decodeFromString(Session.serializer(), it) }

    override suspend fun runShell(sessionId: String, command: String, agent: String): ShellResult =
        transport.execute(
            "POST",
            "/session/$sessionId/shell",
            body = buildJsonObject {
                put("agent", JsonPrimitive(agent))
                put("command", JsonPrimitive(command))
            }.toString(),
            readTimeoutSeconds = 600L,
        ) { text ->
            if (text.isBlank()) return@execute ShellResult("completed", "")
            runCatching {
                val parts = json.parseToJsonElement(text).jsonObject["parts"]?.jsonArray ?: emptyList()
                val tool = parts.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "tool" }
                val state = tool?.jsonObject?.get("state")?.jsonObject
                    ?: return@runCatching ShellResult("completed", "")
                val status = state["status"]?.jsonPrimitive?.contentOrNull ?: "completed"
                val output = state["metadata"]?.jsonObject?.get("output")?.jsonPrimitive?.contentOrNull.orEmpty()
                ShellResult(status, output)
            }.getOrElse { ShellResult("completed", text) }
        }

    override suspend fun sessions(): List<Session> =
        transport.execute("GET", "/api/session?limit=1000&order=desc") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(SessionsV2Response.serializer(), text).data.map { v2 ->
                Session(
                    id = v2.id,
                    projectId = v2.projectId,
                    directory = v2.location?.directory.orEmpty(),
                    parentId = v2.parentId,
                    title = v2.title,
                    time = v2.time,
                    tokens = v2.tokens,
                    model = v2.model,
                    agent = v2.agent,
                )
            }
        }

    override suspend fun sessionStatuses(directory: String?): Map<String, SessionStatusInfo> =
        transport.execute(
            "GET",
            "/session/status${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyMap()
            else json.decodeFromString(MapSerializer(String.serializer(), SessionStatusInfo.serializer()), text)
        }

    override suspend fun providers(directory: String?): ProvidersV2Response =
        transport.execute(
            "GET",
            "/provider${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) ProvidersV2Response()
            else json.decodeFromString(ProvidersV2Response.serializer(), text)
        }

    override suspend fun sessionDetail(id: String): SessionV2Info? =
        transport.execute("GET", "/api/session/$id") { text ->
            if (text.isBlank()) null
            else runCatching {
                val data = json.decodeFromString<JsonObject>(text)["data"]?.jsonObject
                json.decodeFromString(SessionV2Info.serializer(), data.toString())
            }.getOrNull()
        }

    override suspend fun pendingQuestions(directory: String?): List<QuestionRequest> =
        transport.execute(
            "GET",
            "/question${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(QuestionRequest.serializer()), text)
        }

    override suspend fun replyQuestion(requestId: String, answers: List<List<String>>, directory: String?) {
        transport.execute(
            "POST",
            "/question/$requestId/reply${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
            body = buildJsonObject {
                put("answers", buildJsonArray {
                    answers.forEach { labels ->
                        add(buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
                    }
                })
            }.toString(),
        ) {}
    }

    override suspend fun rejectQuestion(requestId: String, directory: String?) {
        transport.execute(
            "POST",
            "/question/$requestId/reject${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
            body = "{}",
        ) {}
    }

    override suspend fun session(id: String): Session =
        transport.execute("GET", "/session/$id") { json.decodeFromString(Session.serializer(), it) }

    override suspend fun sessionMessages(sessionId: String, limit: Int): List<Pair<Message, List<Part>>> =
        transport.execute("GET", "/session/$sessionId/message?limit=$limit") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(SessionInfo.serializer()), text)
                .map { it.info to it.parts }
        }

    override suspend fun sessionMessagesPage(
        sessionId: String,
        limit: Int,
        before: String?,
    ): Pair<List<Pair<Message, List<Part>>>, String?> {
        val (text, next) = fetchPageWithCursor(sessionId, limit, before)
        val page = if (text.isBlank()) {
            emptyList()
        } else {
            runCatching {
                json.decodeFromString<List<SessionInfo>>(text).map { it.info to it.parts }
            }.getOrElse { emptyList() }
        }
        return page to next
    }

    override suspend fun sessionMessagesAll(
        sessionId: String,
        onProgress: (fetched: Int, lastTimestamp: Long) -> Unit,
    ): List<Pair<Message, List<Part>>> {
        val all = mutableListOf<Pair<Message, List<Part>>>()
        var before: String? = null
        while (true) {
            val (text, next) = fetchPageWithCursor(sessionId, 500, before)
            if (text.isBlank()) break
            val page = runCatching {
                json.decodeFromString<List<SessionInfo>>(text).map { it.info to it.parts }
            }.getOrElse { emptyList() }
            if (page.isEmpty()) break
            all += page
            val lastTime = timeToMillis(page.last().first.time?.created)
            onProgress(all.size, lastTime)
            before = next
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
        var before: String? = null
        var done = false
        var earliestMs = 0L
        while (!done) {
            val (text, next) = fetchPageWithCursor(sessionId, 500, before)
            if (text.isBlank()) break
            val page = runCatching {
                json.decodeFromString<List<SessionInfo>>(text).map { it.info to it.parts }
            }.getOrElse { emptyList() }
            if (page.isEmpty()) break
            for (item in page) {
                val created = timeToMillis(item.first.time?.created)
                if (created > 0L && created <= sinceMs && sinceMs > 0L) {
                    done = true
                    break
                }
                if (created > 0L) {
                    result.add(item)
                    if (earliestMs == 0L || created < earliestMs) earliestMs = created
                }
            }
            onProgress(result.size, earliestMs)
            before = next
            if (next == null) break
        }
        return result.sortedBy { timeToMillis(it.first.time?.created) }
    }

    private fun timeToMillis(value: Long?): Long = when {
        value == null || value <= 0L -> 0L
        value < 10_000_000_000L -> value * 1000L
        else -> value
    }

    private suspend fun fetchPageWithCursor(
        sessionId: String,
        limit: Int,
        before: String?,
    ): Pair<String, String?> {
        val path = "/session/$sessionId/message?limit=$limit" + (before?.let { "&before=$it" } ?: "")
        val (text, headers) = transport.executeRaw("GET", path)
        val next = headers.entries.firstOrNull { it.key.equals("x-next-cursor", ignoreCase = true) }?.value
        return text to next
    }

    override suspend fun commands(directory: String?): List<Command> =
        transport.execute(
            "GET",
            "/command${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(Command.serializer()), text)
        }

    override suspend fun executeCommand(sessionId: String, command: String, arguments: String) {
        transport.execute(
            "POST",
            "/session/$sessionId/command",
            body = buildJsonObject {
                put("command", JsonPrimitive(command))
                put("arguments", JsonPrimitive(arguments))
            }.toString(),
        ) {}
    }

    override suspend fun pendingPermissions(directory: String?): List<PermissionRequest> =
        transport.execute(
            "GET",
            "/permission${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(PermissionRequest.serializer()), text)
        }

    override suspend fun replyPermission(
        requestId: String,
        reply: String,
        message: String?,
        directory: String?,
    ) {
        transport.execute(
            "POST",
            "/permission/$requestId/reply${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
            body = buildJsonObject {
                put("reply", JsonPrimitive(reply))
                if (!message.isNullOrBlank()) put("message", JsonPrimitive(message))
            }.toString(),
        ) {}
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        transport.execute(
            "PATCH",
            "/session/$sessionId",
            body = buildJsonObject { put("title", JsonPrimitive(title)) }.toString(),
        ) {}
    }

    override suspend fun deleteSession(sessionId: String): Boolean =
        transport.execute("DELETE", "/session/$sessionId") { it == "true" }

    override suspend fun sessionTodos(sessionId: String): List<TodoInfo> =
        transport.execute("GET", "/session/$sessionId/todo") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(TodoInfo.serializer()), text)
        }

    private fun promptBody(text: String): String = buildJsonObject {
        put("parts", buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(text))
            })
        })
    }.toString()

    override suspend fun sendPromptAsync(sessionId: String, text: String) {
        transport.execute("POST", "/session/$sessionId/prompt_async", body = promptBody(text)) {}
    }

    override suspend fun sendPrompt(sessionId: String, text: String): Pair<Message, List<Part>> =
        transport.execute("POST", "/session/$sessionId/message", body = promptBody(text)) {
            json.decodeFromString(SessionInfo.serializer(), it).let { it.info to it.parts }
        }

    override suspend fun abortSession(sessionId: String): Boolean =
        transport.execute("POST", "/session/$sessionId/abort") { it == "true" }

    override suspend fun summarizeSession(sessionId: String, providerId: String, modelId: String) {
        transport.execute(
            "POST",
            "/session/$sessionId/summarize",
            body = buildJsonObject {
                put("providerID", JsonPrimitive(providerId))
                put("modelID", JsonPrimitive(modelId))
                put("auto", JsonPrimitive(false))
            }.toString(),
        ) {}
    }

    override suspend fun revertMessage(sessionId: String, messageID: String): Session =
        transport.execute(
            "POST",
            "/session/$sessionId/revert",
            body = buildJsonObject {
                put("messageID", JsonPrimitive(messageID))
            }.toString(),
        ) { json.decodeFromString(Session.serializer(), it) }

    override suspend fun unrevertMessage(sessionId: String): Session =
        transport.execute("POST", "/session/$sessionId/unrevert", body = "{}") {
            json.decodeFromString(Session.serializer(), it)
        }

    override fun eventStream(): Flow<String> = transport.stream("/global/event")

    override suspend fun searchFiles(query: String, limit: Int): List<String> =
        transport.execute(
            "GET",
            "/find/file${transport.query(mapOf("query" to query, "limit" to limit.toString()))}",
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(String.serializer()), text)
        }

    override suspend fun probeCapabilities(
        cachedPaths: List<String>,
        cachedVersion: String?,
    ): CapabilityReport {
        var version: String? = null
        try {
            version = health().version
        } catch (_: Exception) {
        }
        val cacheValid = cachedPaths.isNotEmpty() && cachedVersion != null && cachedVersion == version
        return coroutineScope {
            val docPaths = if (cacheValid) {
                async { cachedPaths }
            } else {
                async { withTimeoutOrNull(8_000) { probeDocPaths() } }
            }
            val fsListV2 = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/api/fs/list") }) }
            val fileListV1 = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/file") }) }
            val fileContentV1 = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/file/content") }) }
            val commands = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/command") }) }
            val eventStream = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/global/event") }) }
            val projectsV1 = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/project") }) }
            val sessionsListV2 = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/api/session?limit=1") }) }
            val sessionsListV1 = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/session?limit=1") }) }
            val permissions = async { transport.probeOk(withTimeoutOrNull(8_000) { transport.probe("/permission") }) }
            CapabilityReport(
                version = version,
                fsListV2 = fsListV2.await(),
                fileListV1 = fileListV1.await(),
                fileContentV1 = fileContentV1.await(),
                commands = commands.await(),
                eventStream = eventStream.await(),
                projectsV1 = projectsV1.await(),
                sessionsListV2 = sessionsListV2.await(),
                sessionsListV1 = sessionsListV1.await(),
                permissions = permissions.await(),
                apiPaths = docPaths.await() ?: emptyList(),
            )
        }
    }

    private suspend fun probeDocPaths(): List<String>? =
        runCatching {
            transport.execute("GET", "/doc", readTimeoutSeconds = 8L) { text ->
                json.parseToJsonElement(text).jsonObject["paths"]?.jsonObject?.keys?.toList() ?: emptyList()
            }
        }.getOrNull()

    override fun applyCapabilities(report: CapabilityReport) {
        fsListV2 = report.fsListV2
    }
}
