package com.geno1024.ai.occ.data

interface AgentClientFactory {
    val id: String

    fun supports(url: String): Boolean

    fun create(serverUrl: String, username: String?, password: String?): AgentClient
}

class OpenCodeClientFactory : AgentClientFactory {
    override val id: String = "opencode"

    override fun supports(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    override fun create(serverUrl: String, username: String?, password: String?): AgentClient =
        OpenCodeClient(serverUrl, username, password)
}

object AgentClientRegistry {
    private val factories = mutableMapOf<String, AgentClientFactory>()

    init {
        register(OpenCodeClientFactory())
    }

    fun register(factory: AgentClientFactory) {
        factories[factory.id] = factory
    }

    fun create(serverUrl: String, username: String?, password: String?): AgentClient {
        val factory = factories.values.firstOrNull { it.supports(serverUrl) }
            ?: factories.values.firstOrNull()
            ?: error("No AgentClient implementation registered")
        return factory.create(serverUrl, username, password)
    }
}