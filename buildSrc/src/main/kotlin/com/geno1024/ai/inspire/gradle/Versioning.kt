package com.geno1024.ai.inspire.gradle

import org.gradle.api.Project
import java.io.File
import java.io.RandomAccessFile

/**
 * A build stamp of the form `<base>.<env>.<pack>.<sha1>`, currently `0.1.…`.
 *
 * [env] is the build's position in the release stream and the only ordered field;
 * [pack] counts packaging builds of the module; [sha1] ties the artifact to its
 * source revision.
 */
data class Stamp(
    val env: String,
    val pack: String,
    val sha1: String,
) {
    val versionName: String get() = "$BASE_VERSION.$env.$pack.$sha1"

    /** Android `versionCode`; strictly monotonic within a release stream. */
    val versionCode: Int get() = (env.toIntOrNull() ?: 0).coerceAtLeast(1)

    companion object {
        const val BASE_VERSION = "0.1"
    }
}

object Versioning {
    private val NO_HEAD = "00000000"

    /**
     * Temporary floor from the `build_count.txt` → `count.pack` migration.
     *
     * Installed builds carry a versionCode taken from `build_count.txt` (up to
     * 700), while `env` is the commit count (currently 531). Using `env` alone
     * would be a downgrade and the platform would refuse the update. Keep the
     * floor until the commit count passes it, then delete this constant and
     * use `stamp.versionCode` directly.
     */
    const val LEGACY_VERSION_CODE_FLOOR = 700

    /** `build` is an aggregate every module has; as a prefix it would also catch `buildEnvironment`. */
    private val PACKAGING_EXACT = setOf("build")

    /** Android: every variant reaches the APK/AAB through one of these. */
    private val ANDROID_PACKAGING_PREFIXES = listOf("assemble", "bundle", "package", "install")

    /** JVM: exact names only, so a bare `assembleDebug` does not count here. */
    private val JVM_PACKAGING_EXACT = setOf("assemble", "jar", "installDist", "distTar", "distZip")

    /** JVM: names that only ever appear with a suffix. */
    private val JVM_PACKAGING_PREFIXES = listOf("shadowJar", "dist", "publish")

    fun stamp(root: File, moduleDir: File): Stamp = Stamp(
        env = env(root),
        pack = packOf(moduleDir),
        sha1 = sha1(root),
    )

    /**
     * The build's position in the stream: the number of commits on the branch.
     *
     * This must be the same quantity locally and on CI. A run number restarts with
     * the workflow and can sit below the commit count, which would make newer CI
     * builds look older. It is only a fallback for a checkout without history.
     */
    fun env(root: File): String =
        git(root, "rev-list", "--count", "HEAD")
            ?: System.getenv("GITHUB_RUN_NUMBER")
            ?: "0"

    fun sha1(root: File): String = git(root, "rev-parse", "--short=8", "HEAD") ?: NO_HEAD

    fun packFile(moduleDir: File) = File(moduleDir, "count.pack")

    private fun packOf(moduleDir: File): String {
        val file = packFile(moduleDir)
        return if (file.exists()) file.readText().trim().ifEmpty { "0" } else "0"
    }

    /**
     * Records this session's packaging of the module. Call before [stamp] so the
     * artifact carries its own number.
     *
     * Counting happens at configuration time, so it records requests rather than
     * successes: compile failures and interrupted builds still count, while query
     * tasks and `--dry-run` do not. `-Ppack.bump=true|false` forces it on or off;
     * CI skips it by default because its increment is never written back.
     */
    fun bumpOnPackaging(project: Project, moduleDir: File) {
        val params = project.gradle.startParameter
        if (params.isDryRun) return
        val forced = params.projectProperties["pack.bump"]?.toBoolean()
        if (forced == false) return
        if (forced == null && !System.getenv("CI").isNullOrEmpty()) return
        if (params.taskNames.none { requestsPackaging(project, it) }) return
        incrementPack(moduleDir)
    }

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

    /** Serialised across concurrent builds of the same checkout. */
    private fun incrementPack(moduleDir: File) {
        val file = packFile(moduleDir)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.channel.lock().use {
                val bytes = ByteArray(raf.length().toInt())
                raf.seek(0L)
                raf.readFully(bytes)
                val current = String(bytes, Charsets.UTF_8).trim().toIntOrNull() ?: 0
                raf.setLength(0L)
                raf.seek(0L)
                raf.write("${current + 1}\n".toByteArray())
            }
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
