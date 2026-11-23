package com.geno1024.ai.inspire.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 与具体 OpenCode 协议版本无关的 HTTP 传输层。
 *
 * v1 / v2 两个客户端共享同一个 [HttpTransport]（连接池、认证、重试、SSE、探测），
 * 只在「协议编解码」上分叉。
 */
class HttpTransport(
    serverUrl: String,
    private val username: String? = null,
    private val password: String? = null,
) {
    val base: String = serverUrl.trim().trimEnd('/')

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val connectionPool = ConnectionPool(5, 30, TimeUnit.SECONDS)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(connectionPool)
        .addInterceptor { chain ->
            val request = chain.request()
            val safeMethod = request.method == "GET" || request.method == "HEAD"
            val maxAttempts = if (safeMethod) 3 else 1
            var last: IOException? = null
            for (attempt in 0 until maxAttempts) {
                try {
                    return@addInterceptor chain.proceed(request)
                } catch (e: IOException) {
                    val msg = e.message.orEmpty()
                    val transient = e is java.io.EOFException ||
                        msg.contains("unexpected end of stream") ||
                        msg.contains("Connection reset") ||
                        msg.contains("broken pipe")
                    if (!transient || attempt == maxAttempts - 1) throw e
                    last = e
                    connectionPool.evictAll()
                }
            }
            throw last ?: IOException("request failed")
        }
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

    fun headersFor(directory: String?, extra: Map<String, String> = emptyMap()): Map<String, String> {
        val h = HashMap<String, String>(extra)
        if (!directory.isNullOrBlank()) h["x-opencode-directory"] = directory
        return h
    }

    suspend fun <T> execute(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        readTimeoutSeconds: Long = 30L,
        deserialize: (String) -> T,
    ): T = deserialize(executeRaw(method, path, body, headers, readTimeoutSeconds).first)

    /** 与 [execute] 相同，但同时返回响应头（v1 游标分页需要 `x-next-cursor`）。 */
    suspend fun executeRaw(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        readTimeoutSeconds: Long = 30L,
    ): Pair<String, Map<String, String>> {
        // Errors must not be resumed into a cancelled continuation from an OkHttp
        // callback: that dispatches them on OkHttp's thread and can crash the app.
        // Carry them as a value and throw inside the coroutine instead.
        val result = suspendCancellableCoroutine<Result<Pair<String, Map<String, String>>>> { cont ->
            val reqBuilder = Request.Builder().url("$base$path")
            authHeader?.let { reqBuilder.header("Authorization", it) }
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            val bodyProvider: () -> okhttp3.RequestBody =
                { (body ?: "").toRequestBody(jsonMedia) }
            when (method) {
                "GET" -> reqBuilder.get()
                "POST", "PUT", "PATCH", "DELETE" -> reqBuilder.method(method, bodyProvider())
                else -> throw IllegalArgumentException("Unsupported method: $method")
            }
            val request = reqBuilder.build()
            val call = newCall(request, readTimeoutSeconds)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeIfActive(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val value = runCatching {
                        response.use {
                            val text = it.body.string().orEmpty()
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}: $text")
                            val respHeaders = HashMap<String, String>()
                            for (name in it.headers.names()) {
                                it.header(name)?.let { v -> respHeaders[name] = v }
                            }
                            text to respHeaders
                        }
                    }
                    cont.resumeIfActive(value)
                }
            })
        }
        return result.getOrThrow()
    }

    suspend fun executeBytes(
        path: String,
        headers: Map<String, String> = emptyMap(),
        onRead: (Long, Long) -> Unit = { _, _ -> },
        readTimeoutSeconds: Long = 30L,
    ): ByteArray? {
        val result = suspendCancellableCoroutine<Result<ByteArray?>> { cont ->
            val reqBuilder = Request.Builder().url("$base$path")
            authHeader?.let { reqBuilder.header("Authorization", it) }
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            val call = newCall(reqBuilder.get().build(), readTimeoutSeconds)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeIfActive(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val value: Result<ByteArray?> = runCatching {
                        response.use {
                            if (!it.isSuccessful) {
                                val text = it.body.string().orEmpty()
                                throw IOException("HTTP ${it.code}: $text")
                            }
                            val body = it.body
                            val total = body.contentLength()
                            val buf = java.io.ByteArrayOutputStream()
                            val sink = body.source()
                            val chunk = ByteArray(1024 * 1024)
                            var read = 0L
                            while (true) {
                                val n = sink.read(chunk, 0, chunk.size)
                                if (n < 0) break
                                buf.write(chunk, 0, n)
                                read += n
                                onRead(read, total)
                            }
                            buf.toByteArray()
                        }
                    }
                    cont.resumeIfActive(value)
                }
            })
        }
        return result.getOrThrow()
    }

    fun stream(path: String, headers: Map<String, String> = emptyMap()): Flow<String> = flow {
        val req = Request.Builder().url("$base$path")
        authHeader?.let { req.header("Authorization", it) }
        headers.forEach { (k, v) -> req.header(k, v) }
        val call = client.newCall(req.build())
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                throw IOException("Event stream HTTP ${response.code}")
            }
            val source = response.body.source()
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

    /** 用短超时探测一个路径，返回 HTTP 状态与内容类型；HTML（SPA 兜底）视为不存在。 */
    suspend fun probe(path: String): Probe? {
        val result = suspendCancellableCoroutine<Result<Probe?>> { cont ->
            val reqBuilder = Request.Builder().url("$base$path")
            authHeader?.let { reqBuilder.header("Authorization", it) }
            val call = newCall(reqBuilder.get().build(), 8L)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeIfActive(Result.success(null))
                }

                override fun onResponse(call: Call, response: Response) {
                    val value = runCatching {
                        val probe = Probe(response.code, response.header("Content-Type"))
                        response.close()
                        probe
                    }
                    cont.resumeIfActive(value)
                }
            })
        }
        return result.getOrThrow()
    }

    fun probeOk(probe: Probe?): Boolean {
        val r = probe ?: return false
        val ct = r.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (ct == "text/html") return false
        return r.code in 200..399 || r.code == 400
    }

    /** 读取 JSON 对象；网络失败或返回 HTML 时返回 null。 */
    suspend fun getJsonObject(path: String, headers: Map<String, String> = emptyMap()): JsonObject? =
        runCatching {
            execute("GET", path, headers = headers, readTimeoutSeconds = 8L) { text ->
                if (text.isBlank()) null else json.parseToJsonElement(text).jsonObject
            }
        }.getOrNull()

    fun query(params: Map<String, String?>): String {
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

    private fun newCall(request: Request, readTimeoutSeconds: Long): Call =
        if (readTimeoutSeconds == 30L) client.newCall(request)
        else client.newBuilder().readTimeout(readTimeoutSeconds, TimeUnit.SECONDS).build().newCall(request)
}

/**
 * Resuming a cancelled continuation is a no-op, but resuming it *with an
 * exception* hands that exception to the coroutine's handler on whichever
 * thread calls resume — here OkHttp's pool, where it is uncaught and fatal.
 * Callbacks therefore always carry a [Result] and use this helper instead.
 */
internal fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}

data class Probe(val code: Int, val contentType: String?)
