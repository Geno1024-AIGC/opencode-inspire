package com.geno1024.ai.inspire.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** A release that carries an installable APK. */
data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val prerelease: Boolean,
    val htmlUrl: String,
    val apkUrl: String? = null,
    val apkName: String? = null,
    val apkSize: Long = 0L,
    val version: Updater.Version? = null,
    val publishedAt: String? = null,
)

enum class Mirror(val id: String, val prefix: String) {
    NONE("github", ""),
    GH_PROXY("ghproxy", "https://gh-proxy.com/"),
    MIRROR_GHPROXY("mirror_ghproxy", "https://mirror.ghproxy.com/"),
    GHPROXY_NET("ghproxy_net", "https://ghproxy.net/"),
    GH_PROXY_COM("gh_proxy_com", "https://gh-proxy.com/");
}

object Updater {
    private const val OWNER = "Geno1024-AIGC"
    private const val REPO = "opencode-inspire"
    private const val USER_AGENT = "opencode-inspire"
    private const val BUFFER_BYTES = 64 * 1024

    class Version(val base: String, val env: Int, val pack: Int, val sha: String) :
        Comparable<Version> {

        override fun compareTo(other: Version): Int = env.compareTo(other.env)

        override fun equals(other: Any?): Boolean =
            other is Version && base == other.base && env == other.env &&
                pack == other.pack && sha == other.sha

        override fun hashCode(): Int = ((base.hashCode() * 31 + env) * 31 + pack) * 31 + sha.hashCode()

        override fun toString(): String = "$base.$env.$pack.$sha"
    }

    /**
     * Any `<major>.<minor>` base is accepted: a future 1.0 must still be readable
     * by every 0.1 device. Changing the stamp's shape is the only thing that needs
     * an explicit compatibility shim.
     */
    fun parseVersion(text: String): Version? {
        val match = Regex("""(\d+\.\d+)\.(\d+)\.(\d+)\.([0-9a-fA-F]{8})""").find(text)
            ?: return null
        return Version(
            base = match.groupValues[1],
            env = match.groupValues[2].toInt(),
            pack = match.groupValues[3].toInt(),
            sha = match.groupValues[4].lowercase(),
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Metadata comes only from GitHub's API; mirrors are for file transfer. */
    suspend fun fetchReleases(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val arr = JSONArray(resp.body?.string().orEmpty())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val assets = o.optJSONArray("assets")
                val apk = if (assets != null) {
                    (0 until assets.length())
                        .map { j -> assets.getJSONObject(j) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                } else null
                val tag = o.optString("tag_name")
                val name = o.optString("name").ifEmpty { tag }
                ReleaseInfo(
                    tagName = tag,
                    name = name,
                    prerelease = o.optBoolean("prerelease"),
                    htmlUrl = o.optString("html_url"),
                    apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotEmpty() },
                    apkName = apk?.optString("name")?.takeIf { it.isNotEmpty() },
                    apkSize = apk?.optLong("size") ?: 0L,
                    version = parseVersion(name) ?: parseVersion(tag),
                    publishedAt = o.optString("published_at").takeIf { it.isNotEmpty() },
                )
            }.filter { it.apkUrl != null }
        }
    }

    fun releaseFor(releases: List<ReleaseInfo>, channel: String): ReleaseInfo? =
        when (channel) {
            "canary" -> releases.firstOrNull { it.prerelease }
            else -> releases.firstOrNull { !it.prerelease }
        }

    /** The newest installable release on [channel], or null when installed is current. */
    fun newer(currentVersion: String, releases: List<ReleaseInfo>, channel: String): ReleaseInfo? {
        val current = parseVersion(currentVersion)
        return releases
            .filter { if (channel == "canary") it.prerelease else !it.prerelease }
            .filter { it.apkUrl != null }
            .mapNotNull { release -> release.version?.let { it to release } }
            .filter { (version, _) -> current == null || version > current }
            .maxByOrNull { (version, _) -> version }
            ?.second
    }

    /** Kept for callers that only have two stamp strings. */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remote = parseVersion(remoteVersion) ?: return false
        val current = parseVersion(currentVersion) ?: return true
        return remote > current
    }

    fun mirrorApkUrl(url: String, mirror: Mirror): String {
        if (mirror == Mirror.NONE || !url.startsWith("https://github.com/")) return url
        return "${mirror.prefix}$url"
    }

    /** The installed version, read from the platform rather than BuildConfig. */
    fun installedVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty()
    }.getOrDefault("")

    fun downloadDir(context: Context): File = File(context.cacheDir, "update")

    /**
     * Streams [url] into [target] via a sibling `.part`, then renames it into place.
     * An interrupted download is never mistaken for a finished APK.
     */
    fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.android.package-archive")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code from ${URL(url).host}")
            val total = connection.contentLengthLong
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "could not put the download in place" }
        } finally {
            connection.disconnect()
        }
    }
}
