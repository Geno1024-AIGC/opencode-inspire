package com.example.opencodeclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val prerelease: Boolean,
    val htmlUrl: String,
    val publishedAt: String? = null,
)

object Updater {
    private const val REPO = "Geno1024-AIGC/opencode-inspire"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchReleases(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases")
                .header("User-Agent", "opencode-inspire")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful || resp.body == null) return@withContext emptyList()
                val arr = JSONArray(resp.body!!.string())
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    ReleaseInfo(
                        tagName = o.optString("tag_name"),
                        prerelease = o.optBoolean("prerelease"),
                        htmlUrl = o.optString("html_url"),
                        publishedAt = o.optString("published_at").takeIf { it.isNotEmpty() },
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun releaseFor(releases: List<ReleaseInfo>, channel: String): ReleaseInfo? =
        when (channel) {
            "canary" -> releases.firstOrNull { it.prerelease }
            else -> releases.firstOrNull { !it.prerelease }
        }

    private fun versionTuple(v: String): Pair<Int, Int>? {
        val parts = v.removePrefix("v").trim().split(".")
        val pack = parts.getOrNull(2)?.toIntOrNull()
        val build = parts.getOrNull(3)?.toIntOrNull()
        if (pack == null || build == null) return null
        return pack to build
    }

    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        val r = versionTuple(remoteTag) ?: return false
        val c = versionTuple(currentVersion) ?: return true
        return (r.first > c.first) || (r.first == c.first && r.second > c.second)
    }
}
