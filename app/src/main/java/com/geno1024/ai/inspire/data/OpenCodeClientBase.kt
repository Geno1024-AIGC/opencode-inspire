package com.geno1024.ai.inspire.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * v1 / v2 两个 OpenCode 客户端共享的协议无关实现。
 *
 * 传输层（[HttpTransport]）与这些在 1.15+ 与 2.x 中路径/结构一致的接口放在这里；
 * 只有真正随版本变化的接口才由两个子类分别实现。
 */
abstract class OpenCodeClientBase(protected val transport: HttpTransport) : AgentClient {

    protected val json: Json get() = transport.json

    override suspend fun models(): List<ModelInfo> =
        transport.execute("GET", "/api/model") { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ModelsV2Response.serializer(), text).data
        }

    override suspend fun agents(directory: String?): List<AgentInfo> =
        transport.execute(
            "GET",
            "/api/agent${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(AgentsV2Response.serializer(), text).data
        }

    override suspend fun switchAgent(sessionId: String, agent: String) {
        transport.execute(
            "POST",
            "/api/session/$sessionId/agent",
            body = buildJsonObject {
                put("agent", agent)
            }.toString(),
        ) {}
    }

    override suspend fun integrations(directory: String?): List<IntegrationInfo> =
        transport.execute(
            "GET",
            "/api/integration${transport.query(mapOf("directory" to directory))}",
            headers = transport.headersFor(directory),
        ) { text ->
            if (text.isBlank()) emptyList()
            else json.decodeFromString(IntegrationsV2Response.serializer(), text).data
        }

    override suspend fun connectIntegration(
        integrationId: String,
        key: String,
        label: String?,
        directory: String?,
    ) {
        transport.execute(
            "POST",
            "/api/integration/$integrationId/connect/key",
            headers = transport.headersFor(directory),
            body = buildJsonObject {
                put("key", key)
                if (!label.isNullOrBlank()) put("label", label)
            }.toString(),
        ) {}
    }

    override suspend fun switchModel(sessionId: String, providerId: String, modelId: String) {
        transport.execute(
            "POST",
            "/api/session/$sessionId/model",
            body = buildJsonObject {
                put("model", buildJsonObject {
                    put("providerID", providerId)
                    put("id", modelId)
                })
            }.toString(),
        ) {}
    }

    override suspend fun readFileBytes(
        locationDir: String?,
        path: String,
        onRead: (Long, Long) -> Unit,
    ): ByteArray? =
        transport.executeBytes(
            "/api/fs/read/${path.trimStart('/')}",
            headers = transport.headersFor(locationDir),
            onRead = onRead,
            readTimeoutSeconds = 300L,
        )

    override suspend fun contextWindow(modelId: String?): Long {
        val models = models()
        val m = models.firstOrNull { it.id == modelId } ?: models.firstOrNull()
        return m?.limit?.context ?: 0L
    }
}
