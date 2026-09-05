package com.example.opencodeclient.data

import com.example.opencodeclient.R

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(raw: String?): Version? {
            if (raw.isNullOrBlank()) return null
            val m = Regex("""(\d+)\.(\d+)\.(\d+)""").find(raw) ?: return null
            return Version(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].toInt(),
            )
        }
    }
}

enum class FeatureGroup {
    API,
    SLASH,
    AGENTS,
    MODELS,
}

enum class CapabilityState {
    VERIFIED_SUPPORTED,
    VERIFIED_UNSUPPORTED,
    ESTIMATED_SUPPORTED,
    ESTIMATED_UNSUPPORTED,
    BUILTIN,
    UNKNOWN,
}

data class FeatureSpec(
    val id: String,
    val labelRes: Int,
    val group: FeatureGroup,
    val minVersion: Version? = null,
    val routeKey: String? = null,
    val reportKey: String? = null,
    val slashName: String? = null,
    val builtin: Boolean = false,
)

data class FeatureStatus(
    val spec: FeatureSpec,
    val state: CapabilityState,
)

object CapabilityCatalog {

    private val V015 = Version(1, 15, 0)

    private fun feature(
        id: String,
        labelRes: Int,
        group: FeatureGroup,
        minVersion: Version? = null,
        routeKey: String? = null,
        reportKey: String? = null,
        slashName: String? = null,
        builtin: Boolean = false,
    ) = FeatureSpec(
        id = id,
        labelRes = labelRes,
        group = group,
        minVersion = minVersion,
        routeKey = routeKey,
        reportKey = reportKey,
        slashName = slashName,
        builtin = builtin,
    )

    val features: List<FeatureSpec> = listOf(
        // API
        feature("doc", R.string.capability_doc, FeatureGroup.API, minVersion = V015, routeKey = "/doc"),
        feature(
            "fsListV2",
            R.string.capability_fs_list_v2,
            FeatureGroup.API,
            minVersion = V015,
            routeKey = "api/fs/list",
            reportKey = "fsListV2",
        ),
        feature(
            "fileListV1",
            R.string.capability_file_list_v1,
            FeatureGroup.API,
            minVersion = Version(0, 0, 0),
            routeKey = "/file",
            reportKey = "fileListV1",
        ),
        feature(
            "fileContentV1",
            R.string.capability_file_content_v1,
            FeatureGroup.API,
            minVersion = Version(0, 0, 0),
            routeKey = "file/content",
            reportKey = "fileContentV1",
        ),
        feature(
            "summarize",
            R.string.capability_summarize,
            FeatureGroup.API,
            minVersion = V015,
            routeKey = "summarize",
        ),
        feature(
            "commands",
            R.string.capability_commands,
            FeatureGroup.API,
            minVersion = Version(0, 0, 0),
            routeKey = "/command",
            reportKey = "commands",
        ),
        feature(
            "eventStream",
            R.string.capability_event_stream,
            FeatureGroup.API,
            minVersion = Version(0, 0, 0),
            routeKey = "global/event",
            reportKey = "eventStream",
        ),
        feature(
            "projects",
            R.string.capability_projects,
            FeatureGroup.API,
            minVersion = Version(0, 0, 0),
            routeKey = "/project",
            reportKey = "projectsV1",
        ),
        feature(
            "sessionsListV2",
            R.string.capability_sessions_v2,
            FeatureGroup.API,
            minVersion = V015,
            routeKey = "api/session",
            reportKey = "sessionsListV2",
        ),
        feature(
            "sessionsListV1",
            R.string.capability_sessions_v1,
            FeatureGroup.API,
            minVersion = Version(0, 0, 0),
            routeKey = "/session",
            reportKey = "sessionsListV1",
        ),
        feature(
            "permissions",
            R.string.capability_permissions,
            FeatureGroup.API,
            minVersion = V015,
            routeKey = "permissions",
            reportKey = "permissions",
        ),
        feature(
            "find",
            R.string.capability_find,
            FeatureGroup.API,
            minVersion = Version(0, 0, 0),
            routeKey = "find/file",
        ),
        // Slash commands
        feature(
            "cmdInit",
            R.string.capability_cmd_init,
            FeatureGroup.SLASH,
            minVersion = Version(0, 0, 0),
            slashName = "init",
        ),
        feature(
            "cmdReview",
            R.string.capability_cmd_review,
            FeatureGroup.SLASH,
            minVersion = Version(0, 0, 0),
            slashName = "review",
        ),
        feature("cmdUndo", R.string.capability_cmd_undo, FeatureGroup.SLASH, builtin = true),
        feature("cmdRedo", R.string.capability_cmd_redo, FeatureGroup.SLASH, builtin = true),
        // Agents
        feature("agentMentions", R.string.capability_agent, FeatureGroup.AGENTS, routeKey = "/agent"),
        // Models
        feature(
            "models",
            R.string.capability_models,
            FeatureGroup.MODELS,
            minVersion = V015,
            routeKey = "api/model",
        ),
    )

    fun stateFor(spec: FeatureSpec, report: CapabilityReport?, commandNames: Set<String>): CapabilityState {
        if (spec.builtin) return CapabilityState.BUILTIN
        report?.let { r ->
            val verified = when {
                spec.slashName != null -> if (spec.slashName in commandNames) true else false
                spec.reportKey != null && r.apiPaths.isEmpty() && r.probedV1 -> r.probeValue(spec.reportKey)
                spec.routeKey != null && r.apiPaths.isNotEmpty() ->
                    r.apiPaths.any { it.contains(spec.routeKey) }
                else -> null
            }
            if (verified != null) {
                return if (verified) CapabilityState.VERIFIED_SUPPORTED else CapabilityState.VERIFIED_UNSUPPORTED
            }
        }
        val version = Version.parse(report?.version)
        if (version != null && spec.minVersion != null) {
            return if (version >= spec.minVersion) CapabilityState.ESTIMATED_SUPPORTED
            else CapabilityState.ESTIMATED_UNSUPPORTED
        }
        return CapabilityState.UNKNOWN
    }

    fun stateForAll(report: CapabilityReport?, commandNames: Set<String>): List<FeatureStatus> =
        features.map { FeatureStatus(it, stateFor(it, report, commandNames)) }
}