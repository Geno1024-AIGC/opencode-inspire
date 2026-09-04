package com.example.opencodeclient.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class OpenCodeClient(
    serverUrl: String,
    private val username: String? = null,
    private val password: String? = null,
) {
    private val base = serverUrl.trim().trimEnd('/')
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val authHeader: String? =
        if (username.isNullOrEmpty() && password.isNullOrEmpty()) null
        else {
            val token = java.util.Base64.getEncoder().encodeToString(
                "${username ?: ""}:${password ?: ""}".toByteArray(Charsets.UTF_8)
            )
            "Basic $token"
        }

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String? = null,
        deserialize: (String) -> T,
    ): T = suspendCancellableCoroutine { cont ->
        val reqBuilder = Request.Builder().url("$base$path")
        authHeader?.let { reqBuilder.header("Authorization", it) }
        val bodyProvider: () -> okhttp3.RequestBody =
            { (body ?: "").toRequestBody(jsonMedia) }
        when (method) {
            "GET" -> reqBuilder.get()
            "POST", "PUT", "PATCH", "DELETE" -> reqBuilder.method(method, bodyProvider())
            else -> throw IllegalArgumentException("Unsupported method: $method")
        }
        val request = reqBuilder.build()
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isCancelled) return
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        cont.resumeWithException(IOException("HTTP ${it.code}: $text"))
                        return
                    }
                    try {
                        cont.resume(deserialize(text))
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            }
        })
    }

    suspend fun health(): HealthResponse =
        execute("GET", "/global/health") { json.decodeFromString(HealthResponse.serializer(), it) }

    suspend fun projects(): List<Project> =
        execute("GET", "/project") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(Project.serializer()), text)
        }

    suspend fun currentProject(): Project? =
        execute("GET", "/project/current") { text ->
            if (text.isBlank() || text == "{}") null
            else json.decodeFromString(Project.serializer(), text)
        }

    suspend fun listFiles(path: String? = null): List<FileNode> =
        execute("GET", "/file?path=${java.net.URLEncoder.encode(path ?: "", "UTF-8")}") { text ->
            if (text.isBlank()) emptyList()
            else {
                android.util.Log.d("FileBrowser", "raw /file body: $text")
                val root = json.parseToJsonElement(text)
                if (root is JsonArray) {
                    json.decodeFromJsonElement(ListSerializer(FileNode.serializer()), root)
                } else {
                    val node = json.decodeFromJsonElement(FileNode.serializer(), root)
                    node.children ?: listOf(node)
                }
            }
        }

    suspend fun createSession(directory: String? = null, parentId: String? = null, title: String? = null): Session =
        execute(
            "POST",
            "/session${queryOf(mapOf("directory" to directory))}",
            body = buildJsonObject {
                if (parentId != null) put("parentID", parentId)
                if (title != null) put("title", title)
            }.toString(),
        ) { json.decodeFromString(Session.serializer(), it) }

    suspend fun sessions(): List<Session> =
        execute("GET", "/api/session?limit=1000&order=desc") { text ->
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
                )
            }
        }

    suspend fun models(): List<ModelInfo> =
        execute("GET", "/api/model") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ModelsV2Response.serializer(), text).data
        }

    suspend fun sessionDetail(id: String): SessionV2Info? =
        execute("GET", "/api/session/$id") { text ->
            if (text.isBlank()) null
            else runCatching {
                val data = json.decodeFromString<JsonObject>(text)["data"]?.jsonObject
                json.decodeFromString(SessionV2Info.serializer(), data.toString())
            }.getOrNull()
        }

    suspend fun contextWindow(modelId: String?): Long {
        val models = models()
        val m = models.firstOrNull { it.id == modelId } ?: models.firstOrNull()
        return m?.limit?.context ?: 0L
    }

    suspend fun pendingQuestions(directory: String? = null): List<QuestionRequest> =
        execute("GET", "/question${queryOf(mapOf("directory" to directory))}") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(QuestionRequest.serializer()), text)
        }

    suspend fun replyQuestion(requestId: String, answers: List<List<String>>, directory: String? = null) {
        execute("POST", "/question/$requestId/reply${queryOf(mapOf("directory" to directory))}", buildJsonObject {
            put("answers", buildJsonArray {
                answers.forEach { labels ->
                    add(buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
                }
            })
        }.toString()) { it }
    }

    suspend fun rejectQuestion(requestId: String, directory: String? = null) {
        execute("POST", "/question/$requestId/reject${queryOf(mapOf("directory" to directory))}", "{}".toString()) { it }
    }

    suspend fun session(id: String): Session =
        execute("GET", "/session/$id") { json.decodeFromString(Session.serializer(), it) }

    suspend fun sessionMessages(sessionId: String, limit: Int = 50): List<Pair<Message, List<Part>>> =
        execute("GET", "/session/$sessionId/message?limit=$limit") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(SessionInfo.serializer()), text)
                .map { it.info to it.parts }
        }

    suspend fun sessionMessagesAll(
        sessionId: String,
        onProgress: (fetched: Int, lastTimestamp: Long) -> Unit = { _, _ -> },
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

    suspend fun sessionMessagesSince(
        sessionId: String,
        sinceMs: Long,
        onProgress: (fetched: Int, earliestSeen: Long) -> Unit = { _, _ -> },
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

    private suspend fun fetchPageWithCursor(sessionId: String, limit: Int, before: String?): Pair<String, String?> =
        suspendCancellableCoroutine { cont ->
            val path = "/session/$sessionId/message?limit=$limit" + (before?.let { "&before=$it" } ?: "")
            val reqBuilder = Request.Builder().url("$base$path")
            authHeader?.let { reqBuilder.header("Authorization", it) }
            val request = reqBuilder.build()
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isCancelled) return
                    response.use {
                        val text = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            cont.resumeWithException(IOException("HTTP ${it.code}: $text"))
                            return
                        }
                        cont.resume(text to it.header("x-next-cursor"))
                    }
                }
            })
        }

    suspend fun commands(directory: String? = null): List<Command> =
        execute("GET", "/command${queryOf(mapOf("directory" to directory))}") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(Command.serializer()), text)
        }

    suspend fun executeCommand(sessionId: String, command: String, arguments: String = "") {
        execute(
            "POST",
            "/session/$sessionId/command${queryOf(mapOf("directory" to null))}",
            body = buildJsonObject {
                put("command", JsonPrimitive(command))
                put("arguments", JsonPrimitive(arguments))
            }.toString(),
        ) {}
    }

    suspend fun pendingPermissions(directory: String? = null): List<PermissionRequest> =
        execute("GET", "/permission${queryOf(mapOf("directory" to directory))}") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(PermissionRequest.serializer()), text)
        }

    suspend fun replyPermission(requestId: String, reply: String, message: String? = null, directory: String? = null) {
        execute(
            "POST",
            "/permission/$requestId/reply${queryOf(mapOf("directory" to directory))}",
            body = buildJsonObject {
                put("reply", JsonPrimitive(reply))
                if (!message.isNullOrBlank()) put("message", JsonPrimitive(message))
            }.toString(),
        ) {}
    }

    suspend fun switchModel(sessionId: String, providerId: String, modelId: String) {
        execute(
            "POST",
            "/api/session/$sessionId/model",
            body = buildJsonObject {
                put("model", buildJsonObject {
                    put("providerID", JsonPrimitive(providerId))
                    put("id", JsonPrimitive(modelId))
                })
            }.toString(),
        ) {}
    }

    suspend fun renameSession(sessionId: String, title: String) {
        execute(
            "PATCH",
            "/session/$sessionId",
            body = buildJsonObject { put("title", JsonPrimitive(title)) }.toString(),
        ) {}
    }

    suspend fun deleteSession(sessionId: String): Boolean =
        execute("DELETE", "/session/$sessionId") { it == "true" }

    suspend fun sessionTodos(sessionId: String): List<TodoInfo> =
        execute("GET", "/session/$sessionId/todo") { text ->
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

    suspend fun sendPromptAsync(sessionId: String, text: String) {
        execute("POST", "/session/$sessionId/prompt_async", body = promptBody(text)) {}
    }

    suspend fun sendPrompt(sessionId: String, text: String): Pair<Message, List<Part>> =
        execute("POST", "/session/$sessionId/message", body = promptBody(text)) {
            json.decodeFromString(SessionInfo.serializer(), it).let { it.info to it.parts }
        }

    suspend fun abortSession(sessionId: String): Boolean =
        execute("POST", "/session/$sessionId/abort") { it == "true" }

    fun eventStream(): Flow<String> = flow {
        val req = Request.Builder().url("$base/global/event")
        authHeader?.let { req.header("Authorization", it) }
        val call = client.newCall(req.build())
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                throw IOException("Event stream HTTP ${response.code}")
            }
            val source = response.body?.source() ?: return@flow
            val buf = StringBuilder()
            while (!call.isCanceled()) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) {
                    if (buf.isNotEmpty()) {
                        emit(buf.toString())
                        buf.clear()
                    }
                } else {
                    if (buf.isEmpty()) buf.append(line) else buf.append('\n').append(line)
                }
            }
            response.close()
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun searchFiles(query: String, limit: Int = 50): List<String> =
        execute("GET", "/find/file${queryOf(mapOf("query" to query, "limit" to limit.toString()))}") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<String>>(text)
        }

    private fun queryOf(params: Map<String, String?>): String {
        val sb = StringBuilder("?")
        var first = true
        for ((k, v) in params) {
            if (v.isNullOrBlank()) continue
            if (!first) sb.append('&')
            first = false
            sb.append(k).append('=').append(java.net.URLEncoder.encode(v, "UTF-8"))
        }
        return if (first) "" else sb.toString()
    }
}

@kotlinx.serialization.Serializable
data class SessionConfig(
    val model: String? = null,
    val agent: String? = null,
) {
    fun asJson(): JsonObject = buildJsonObject {
        model?.let { put("model", JsonPrimitive(it)) }
        agent?.let { put("agent", JsonPrimitive(it)) }
    }
}
