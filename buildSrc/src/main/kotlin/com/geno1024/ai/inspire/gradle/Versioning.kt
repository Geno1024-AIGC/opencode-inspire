package com.geno1024.ai.inspire.gradle

import org.gradle.api.Project
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * A build stamp of the form `0.1.<env>.<pack>.<sha8>`, per `RULES/VERSION.md`.
 *
 * - `env` is the commit count and the only segment that sorts. The same checkout
 *   yields the same `env`.
 * - `pack` is the module total returned by the org's build-count service: every
 *   packaging request POSTs, locally and on CI alike. Attempts count, not
 *   successes.
 * - `sha1` ties the artifact back to its revision; it never sorts.
 */
data class Stamp(
    val env: String,
    val pack: String,
    val sha1: String,
) {
    val versionName: String get() = "$BASE_VERSION.$env.$pack.$sha1"

    /** Android `versionCode`; ordering authority is `env`, never `pack`. */
    val versionCode: Int get() = (env.toIntOrNull() ?: 0).coerceAtLeast(1)

    companion object {
        /** Only moves when the capability surface does. */
        const val BASE_VERSION = "0.1"
    }
}

object Versioning {
    private const val NO_HEAD = "00000000"

    /**
     * Temporary floor from the `build_count.txt` → build-count-server migration.
     *
     * Installed builds carried a versionCode taken from `build_count.txt` (up to
     * 700), while `env` is the commit count. Using `env` alone would be a
     * downgrade and the platform would refuse the update. Keep the floor until
     * the commit count passes it, then delete this constant and use
     * `stamp.versionCode` directly.
     */
    const val LEGACY_VERSION_CODE_FLOOR = 700

    /** `build` is an aggregate every module has; as a prefix it also hits `buildEnvironment`. */
    private val PACKAGING_EXACT = setOf("build")

    /** Android: every variant reaches the APK/AAB through one of these. */
    private val ANDROID_PACKAGING_PREFIXES = listOf("assemble", "bundle", "package", "install")

    /** JVM: exact names only, so a bare `assembleDebug` does not count here. */
    private val JVM_PACKAGING_EXACT = setOf("assemble", "jar", "installDist", "distTar", "distZip")

    /** JVM: names that only ever appear with a suffix. */
    private val JVM_PACKAGING_PREFIXES = listOf("shadowJar", "dist", "publish")

    /** Packaging builds: replay what is owed, POST this one, use the returned total. */
    fun stamp(root: File, project: Project): Stamp = Stamp(
        env = env(root),
        pack = packOf(root, project),
        sha1 = sha1(root),
    )

    /** Read-only stamp for inspection: GET the current total, never increment. */
    fun current(root: File, project: Project): Stamp = Stamp(
        env = env(root),
        pack = currentPack(root, project),
        sha1 = sha1(root),
    )

    /** Commit count first, so local and CI agree. */
    fun env(root: File): String =
        git(root, "rev-list", "--count", "HEAD")
            ?: System.getenv("GITHUB_RUN_NUMBER")
            ?: "0"

    fun sha1(root: File): String =
        git(root, "rev-parse", "--short=8", "HEAD") ?: NO_HEAD

    /**
     * Decides whether this invocation is a packaging build and, if so, counts it
     * before the stamp is constructed.
     *
     * Counting happens at configuration time, so it records *requests* and not
     * successes: a build that fails to compile, cannot sign or is interrupted
     * after configuration already has its number. Query-only tasks and
     * `--dry-run` stay out and stamp `pack = 0`.
     *
     * `-Ppack.bump=false` is a CI-only escape hatch for when the service is down
     * and a run still has to ship. A local build never needs it: without network
     * the build counts locally and replays later; skipping would silently drop a
     * number from a shared counter with no record.
     */
    private fun packOf(root: File, project: Project): String {
        val params = project.gradle.startParameter
        val forced = params.projectProperties["pack.bump"]?.toBoolean()
        if (forced == false) {
            check(System.getenv("GITHUB_ACTIONS") == "true") {
                "-Ppack.bump=false is a CI escape hatch; a local packaging build must count. " +
                    "Without a token the build fails; with one but no network it counts " +
                    "locally and replays when the service is back."
            }
            project.logger.warn(
                "packaging build NOT counted (-Ppack.bump=false): the stamp claims pack=" +
                    (params.projectProperties["pack.value"] ?: "0") + " but the service was not asked",
            )
            return params.projectProperties["pack.value"] ?: "0"
        }
        if (params.isDryRun) return "0"
        if (params.taskNames.none { requestsPackaging(project, it) }) return "0"
        return report(root, project)
    }

    /**
     * Replays builds counted while the service was unreachable, then POSTs this
     * one. One POST is one build, and the log is updated after each success, so
     * a flush that dies halfway neither double counts nor loses anything.
     */
    private fun report(root: File, project: Project): String {
        val state = CountState(root, project.rootProject.name, moduleName(project))
        var known = state.known
        var pending = state.pending

        while (pending > 0) {
            val total = post(root, project) ?: return defer(project, state, known, pending)
            known = total
            pending -= 1
            state.write(known, pending)
        }

        val total = post(root, project)
        if (total != null) {
            state.write(total, 0)
            return total
        }
        return defer(project, state, known, 0)
    }

    /**
     * Network-level failure: count locally and stamp a provisional number. A CI
     * runner's disk dies with the job, so there is nothing to replay later and
     * the build fails instead of pretending.
     */
    private fun defer(project: Project, state: CountState, known: String, pending: Int): String {
        check(System.getenv("GITHUB_ACTIONS") != "true") {
            "build-count service unreachable, and a CI runner cannot replay the count later; " +
                "fix the service or pass -Ppack.bump=false"
        }
        val next = pending + 1
        state.write(known, next)
        project.logger.warn(
            "build-count service unreachable: counted locally as ${known.toLong() + next} " +
                "($next pending), will replay on the next reachable build",
        )
        return (known.toLong() + next).toString()
    }

    /** Read-only total: the service when reachable, otherwise "known + pending". */
    private fun currentPack(root: File, project: Project): String {
        fetch(root, project)?.let { return it }
        val state = CountState(root, project.rootProject.name, moduleName(project))
        return (state.known.toLong() + state.pending).toString()
    }

    /**
     * POSTs one build and returns the new total; `null` means the request never
     * left (safe to count locally). An HTTP error, or a request whose response
     * was lost, fails loudly instead of slipping into the offline path.
     */
    private fun post(root: File, project: Project): String? {
        val token = setting(root, "BUILD_COUNT_TOKEN")
            ?: error(
                "BUILD_COUNT_TOKEN is required to count a packaging build: set the " +
                    "environment variable or put it in ${File(root, "local.properties").path}",
            )
        val connection = (URI("${url(root, project)}/builds").toURL().openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
            }

        try {
            connection.outputStream.close()
        } catch (_: IOException) {
            return null
        }

        val code = try {
            connection.responseCode
        } catch (e: IOException) {
            throw IllegalStateException(lostResponse(root, project), e)
        }
        if (code != HttpURLConnection.HTTP_CREATED) {
            val body = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            error("build-count service returned HTTP $code: $body")
        }
        val pack = try {
            connection.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (e: IOException) {
            throw IllegalStateException(lostResponse(root, project), e)
        }
        require(pack.matches(Regex("\\d+"))) {
            "build-count service returned a non-numeric pack: $pack"
        }
        return pack
    }

    /** The request went out, so the server may or may not have counted it. Do not guess. */
    private fun lostResponse(root: File, project: Project) =
        "build-count POST was sent but no response came back; the count may or may not have been " +
            "recorded. Check GET ${url(root, project)} before rebuilding."

    /** GETs the current total; `null` when the service cannot be reached. */
    private fun fetch(root: File, project: Project): String? {
        val connection = (URI(url(root, project)).toURL().openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }

        val code = try {
            connection.responseCode
        } catch (_: IOException) {
            return null
        }
        if (code != HttpURLConnection.HTTP_OK) {
            val body = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            error("build-count service returned HTTP $code: $body")
        }
        val json = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            return null
        }
        return Regex("\"total\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)
            ?: error("build-count service returned no total: $json")
    }

    private fun url(root: File, project: Project): String {
        val base = setting(root, "BUILD_COUNT_URL")
            ?: error(
                "BUILD_COUNT_URL is required to count a packaging build: set the " +
                    "environment variable or put it in ${File(root, "local.properties").path}",
            )
        return "$base/projects/${segment(project.rootProject.name)}/modules/${segment(moduleName(project))}"
    }

    /**
     * Environment first, then `local.properties`: CI only has the environment,
     * and a developer can keep the token next to the keystore entries instead of
     * exporting it in every shell. `local.properties` is gitignored.
     */
    private fun setting(root: File, key: String): String? =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: localProperty(root, key)

    private fun localProperty(root: File, key: String): String? {
        val file = File(root, "local.properties")
        if (!file.isFile) return null
        val properties = java.util.Properties()
        try {
            file.inputStream().use { properties.load(it) }
        } catch (e: IOException) {
            error("cannot read ${file.path}: ${e.message}")
        }
        return properties.getProperty(key)?.takeIf { it.isNotBlank() }
    }

    /** `project` is the root name; `module` is the Gradle path with `:` as `.`. */
    private fun moduleName(project: Project): String =
        project.path.removePrefix(":").replace(':', '.').ifEmpty { project.name }

    private fun segment(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9._-]{1,64}"))) {
            "build-count segment must match [A-Za-z0-9._-]{1,64}: $value"
        }
        return value
    }

    /**
     * A qualified name (`:app:assembleDebug`) belongs to one module only, and
     * every module sees the same `taskNames`, so without the path check a
     * sibling module's build would also bump this counter. A bare name
     * (`assembleDebug`) is how Gradle asks every module that has the task, so it
     * counts in each of them — provided the module actually has such a task.
     */
    private fun requestsPackaging(project: Project, taskName: String): Boolean {
        val name = taskName.substringAfterLast(':')
        if (!packagesAnything(project, name)) return false
        if (!taskName.contains(':')) return true
        val path = taskName.removePrefix(":")
        val owner = if (path.contains(':')) ":" + path.substringBeforeLast(':') else ":"
        return owner == project.path
    }

    private fun packagesAnything(project: Project, name: String): Boolean {
        if (name in PACKAGING_EXACT) return true
        val android = project.plugins.hasPlugin("com.android.application") ||
            project.plugins.hasPlugin("com.android.library")
        return if (android) {
            ANDROID_PACKAGING_PREFIXES.any(name::startsWith)
        } else {
            name in JVM_PACKAGING_EXACT || JVM_PACKAGING_PREFIXES.any(name::startsWith)
        }
    }

    private fun git(root: File, vararg args: String): String? = try {
        val process = ProcessBuilder(listOf("git", "-C", root.absolutePath) + args)
            .redirectErrorStream(false)
            .start()
        val out = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && out.isNotEmpty()) out else null
    } catch (_: Exception) {
        null
    }
}

/**
 * The pending log, `<root>/.gradle/build-count/<project>/<module>.state`, holding
 * `<known service total> <pending count>`. It is not a second source of truth:
 * losing it only means those builds were never reported, and `pack` never sorts,
 * so no version number becomes wrong because of it.
 */
private class CountState(root: File, project: String, module: String) {
    private val file = File(File(root, ".gradle/build-count/$project"), "$module.state")

    val known: String
    val pending: Int

    init {
        val fields = if (file.isFile) {
            file.readText().trim().split(' ').filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        known = fields.getOrNull(0)?.takeIf { it.matches(Regex("\\d+")) } ?: "0"
        pending = fields.getOrNull(1)?.toIntOrNull() ?: 0
    }

    fun write(known: String, pending: Int) {
        file.parentFile.mkdirs()
        file.writeText("$known $pending\n")
    }
}
