package com.geno1024.ai.inspire.data

import com.geno1024.ai.inspire.R

/** 服务器配置表单中的一个字段，由工厂声明、UI 渲染。 */
data class ServerField(
    val key: String,
    val labelRes: Int? = null,
    val hintRes: Int? = null,
    val required: Boolean = false,
    val password: Boolean = false,
    val numeric: Boolean = false,
    val widthDp: Int? = null,
)

/** 某类服务器的连接引导文案；为 null 时不显示引导区块。 */
data class ServerGuide(
    val titleRes: Int,
    val step1Res: Int,
    val command: String,
    val pointsRes: List<Int>,
)

/** 工厂从表单字段合成出的连接配置。 */
data class ConnectionConfig(
    val serverUrl: String,
    val username: String? = null,
    val password: String? = null,
)

interface AgentClientFactory {
    val id: String

    /** 类型显示名（如 OpenCode），不做本地化。 */
    val label: String

    /** 添加服务器时的表单字段。 */
    val fields: List<ServerField>

    /** 接入引导；为 null 表示该类型没有专门的引导。 */
    val guide: ServerGuide?

    /** 将表单字段值合成为连接配置。 */
    fun buildConfig(values: Map<String, String>): ConnectionConfig

    fun create(config: ConnectionConfig): AgentClient

    fun supports(url: String): Boolean
}

class OpenCodeClientFactory : AgentClientFactory {
    override val id: String = "opencode"
    override val label: String = "OpenCode"

    override val fields: List<ServerField> = listOf(
        ServerField("host", labelRes = R.string.host_label, required = true),
        ServerField("port", hintRes = R.string.port_hint_default, numeric = true, widthDp = 132),
        ServerField("username", labelRes = R.string.username_label_optional, hintRes = R.string.username_hint_default),
        ServerField("password", labelRes = R.string.password_label_optional, password = true),
    )

    override val guide: ServerGuide = ServerGuide(
        titleRes = R.string.connect_guide_title,
        step1Res = R.string.connect_guide_step1,
        command = "agent serve --hostname 0.0.0.0 --port 4096",
        pointsRes = listOf(
            R.string.connect_guide_point1,
            R.string.connect_guide_point2,
            R.string.connect_guide_point3,
            R.string.connect_guide_point4,
        ),
    )

    override fun buildConfig(values: Map<String, String>): ConnectionConfig {
        val host = values["host"]?.trim().orEmpty()
        val port = values["port"]?.trim().orEmpty().ifBlank { "4096" }
        val base = if (host.startsWith("http://") || host.startsWith("https://")) {
            host
        } else {
            "http://$host"
        }
        val username = values["username"]?.trim().orEmpty().takeIf { it.isNotEmpty() }
            ?: if (values["password"].isNullOrEmpty().not()) "opencode" else null
        return ConnectionConfig(
            serverUrl = "$base:$port",
            username = username,
            password = values["password"]?.takeIf { it.isNotEmpty() },
        )
    }

    override fun create(config: ConnectionConfig): AgentClient =
        AgentHttpClient(config.serverUrl, config.username, config.password)

    override fun supports(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}

object AgentClientRegistry {
    private val factories = mutableMapOf<String, AgentClientFactory>()

    init {
        register(OpenCodeClientFactory())
    }

    fun register(factory: AgentClientFactory) {
        factories[factory.id] = factory
    }

    fun all(): List<AgentClientFactory> = factories.values.toList()

    fun byId(id: String): AgentClientFactory? = factories[id]

    fun create(type: String? = null, serverUrl: String, username: String?, password: String?): AgentClient {
        val factory = type?.let { factories[it] }
            ?: factories.values.firstOrNull { it.supports(serverUrl) }
            ?: factories.values.firstOrNull()
            ?: error("No AgentClient implementation registered")
        return factory.create(ConnectionConfig(serverUrl, username, password))
    }
}