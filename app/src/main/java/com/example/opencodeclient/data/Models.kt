package com.example.opencodeclient.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val healthy: Boolean,
    val version: String? = null,
)

@Serializable
data class Project(
    val worktree: String? = null,
    val path: String? = null,
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
    val title: String? = null,
    @SerialName("time") val createdAt: String? = null,
)

@Serializable
data class SessionInfo(
    val info: Message,
    val parts: List<Part>,
)

@Serializable
data class Message(
    val id: String,
    val sessionID: String? = null,
    val role: String? = null,
    val provider: ProviderRef? = null,
    val model: ModelRef? = null,
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
    val toolInput: String? = null,
    val error: String? = null,
)

@Serializable
data class ToolState(
    val status: String? = null,
    val input: JsonHolder? = null,
    val inputState: JsonHolder? = null,
    val output: String? = null,
)

@Serializable
data class JsonHolder(val value: String? = null)
