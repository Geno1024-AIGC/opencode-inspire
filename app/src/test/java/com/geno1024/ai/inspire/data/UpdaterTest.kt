package com.geno1024.ai.inspire.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    private fun release(tag: String, prerelease: Boolean = false) = ReleaseInfo(
        tagName = tag,
        name = tag,
        prerelease = prerelease,
        htmlUrl = "https://example.com",
        apkUrl = "https://github.com/Geno1024-AIGC/opencode-inspire/releases/download/$tag/app.apk",
        apkName = "app.apk",
        version = Updater.parseVersion(tag),
    )

    @Test
    fun `stable channel ignores prereleases and never falls back`() {
        val releases = listOf(
            release("0.1.400.100.aaaaaaaa", prerelease = true),
            release("0.1.401.101.bbbbbbbb", prerelease = true),
        )
        assertNull(Updater.releaseFor(releases, "release"))
    }

    @Test
    fun `stable channel picks the stable release`() {
        val releases = listOf(
            release("0.1.401.101.bbbbbbbb", prerelease = true),
            release("1.0.400.100.cccccccc"),
        )
        assertEquals("1.0.400.100.cccccccc", Updater.releaseFor(releases, "release")?.tagName)
    }

    @Test
    fun `canary channel picks the first prerelease`() {
        val releases = listOf(
            release("0.1.401.101.aaaaaaaa", prerelease = true),
            release("0.1.400.100.bbbbbbbb", prerelease = true),
            release("1.0.400.100.cccccccc"),
        )
        assertEquals("0.1.401.101.aaaaaaaa", Updater.releaseFor(releases, "canary")?.tagName)
    }

    @Test
    fun `unknown channel is treated as stable and empty list yields nothing`() {
        val releases = listOf(release("0.1.400.100.aaaaaaaa", prerelease = true))
        assertNull(Updater.releaseFor(releases, "whatever"))
        assertNull(Updater.releaseFor(emptyList(), "canary"))
        assertNull(Updater.releaseFor(emptyList(), "release"))
    }

    @Test
    fun `parseVersion accepts any base and normalizes sha`() {
        val version = Updater.parseVersion("1.0.407.153.733E3679")
        assertEquals("1.0", version?.base)
        assertEquals(407, version?.env)
        assertEquals(153, version?.pack)
        assertEquals("733e3679", version?.sha)
    }

    @Test
    fun `parseVersion rejects non stamps`() {
        assertNull(Updater.parseVersion("junk"))
        assertNull(Updater.parseVersion("0.1.400.100"))
        assertNull(Updater.parseVersion("0.1.400.100.zzzzzzzz"))
    }

    @Test
    fun `ordering uses env only`() {
        val a = Updater.parseVersion("0.1.5.100.aaaaaaaa")!!
        val b = Updater.parseVersion("0.1.5.50.bbbbbbbb")!!
        val newer = Updater.parseVersion("0.2.6.1.cccccccc")!!
        assertEquals(0, a.compareTo(b))
        assertTrue(newer > a)
        assertFalse(a > newer)
        assertFalse(a == b)
    }

    @Test
    fun `isNewer compares env only and rejects malformed remote`() {
        assertFalse(Updater.isNewer("0.1.5.100.aaaaaaaa", "0.1.5.50.bbbbbbbb"))
        assertFalse(Updater.isNewer("0.1.5.50.aaaaaaaa", "0.1.5.100.bbbbbbbb"))
        assertTrue(Updater.isNewer("0.2.6.1.aaaaaaaa", "0.1.5.1000.bbbbbbbb"))
        assertFalse(Updater.isNewer("0.1.5.100.aaaaaaaa", "0.1.5.100.aaaaaaaa"))
        assertFalse(Updater.isNewer("junk", "0.1.5.100.aaaaaaaa"))
        assertTrue(Updater.isNewer("0.1.5.100.aaaaaaaa", "junk"))
    }

    @Test
    fun `newer picks the highest env on the selected channel`() {
        val current = "0.1.531.700.ee21f4bb"
        val releases = listOf(
            release("0.1.530.999.aaaaaaaa"),
            release("0.1.532.1.bbbbbbbb", prerelease = true),
            release("0.1.532.2.cccccccc"),
            release("junk"),
        )
        assertEquals("0.1.532.2.cccccccc", Updater.newer(current, releases, "release")?.tagName)
        assertEquals("0.1.532.1.bbbbbbbb", Updater.newer(current, releases, "canary")?.tagName)
    }

    @Test
    fun `mirrorApkUrl prefixes only github file urls`() {
        val url = "https://github.com/Geno1024-AIGC/opencode-inspire/releases/download/v1.0.0/app-release.apk"
        assertEquals("https://gh-proxy.com/$url", Updater.mirrorApkUrl(url, Mirror.GH_PROXY))
        assertEquals(url, Updater.mirrorApkUrl(url, Mirror.NONE))
        assertEquals("https://example.com/app.apk", Updater.mirrorApkUrl("https://example.com/app.apk", Mirror.GH_PROXY))
    }
}
