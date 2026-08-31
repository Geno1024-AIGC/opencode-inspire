package com.example.opencodeclient.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val limit: ModelLimit? = null,
)

@Serializable
data class ModelLimit(
    val context: Long = 0L,
    val input: Long? = null,
    val output: Long? = null,
)

@Serializable
data class ModelsV2Response(
    val location: LocationRef? = null,
    val data: List<ModelInfo> = emptyList(),
)

@Serializable
data class Tokens(
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

@Serializable
data class ServerProfile(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val name: String = "",
)

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
