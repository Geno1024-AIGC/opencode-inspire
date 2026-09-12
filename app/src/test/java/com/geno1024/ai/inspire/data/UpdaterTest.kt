package com.geno1024.ai.inspire.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdaterTest {

    private fun release(tag: String, prerelease: Boolean = false) =
        ReleaseInfo(tagName = tag, prerelease = prerelease, htmlUrl = "https://example.com")

    @Test
    fun `stable channel ignores prereleases and never falls back`() {
        val releases = listOf(
            release("v1.0.0", prerelease = true),
            release("0.1.400.100", prerelease = true),
        )
        val result = Updater.releaseFor(releases, "release")
        assertNull(result)
    }

    @Test
    fun `stable channel picks the stable release`() {
        val releases = listOf(
            release("0.1.400.100", prerelease = true),
            release("v1.0.0"),
        )
        val result = Updater.releaseFor(releases, "release")
        assertEquals("v1.0.0", result?.tagName)
    }

    @Test
    fun `canary channel picks the first prerelease`() {
        val releases = listOf(
            release("0.1.401.101", prerelease = true),
            release("0.1.400.100", prerelease = true),
            release("v1.0.0"),
        )
        val result = Updater.releaseFor(releases, "canary")
        assertEquals("0.1.401.101", result?.tagName)
    }

    @Test
    fun `unknown channel is treated as stable`() {
        val releases = listOf(release("0.1.400.100", prerelease = true))
        assertNull(Updater.releaseFor(releases, "whatever"))
    }

    @Test
    fun `empty list yields nothing on any channel`() {
        assertNull(Updater.releaseFor(emptyList(), "canary"))
        assertNull(Updater.releaseFor(emptyList(), "release"))
    }

    @Test
    fun `isNewer compares build first then pack`() {
        assertTrue(Updater.isNewer("0.1.5.100", "0.1.5.50"))
        assertFalse(Updater.isNewer("0.1.5.50", "0.1.5.100"))
        assertTrue(Updater.isNewer("0.1.4.1000", "0.1.5.1"))
        assertFalse(Updater.isNewer("0.1.5.1", "0.1.4.1000"))
        assertFalse(Updater.isNewer("0.1.5.100", "0.1.5.100"))
    }

    @Test
    fun `isNewer rejects malformed versions`() {
        assertFalse(Updater.isNewer("junk", "0.1.5.100"))
        assertTrue(Updater.isNewer("0.1.5.100", "junk"))
    }

    @Test
    fun `mirrorApkUrl prefixes gh-proxy when mirror enabled`() {
        val url = "https://github.com/Geno1024-AIGC/opencode-inspire/releases/download/v1.0.0/app-release.apk"
        assertEquals("https://gh-proxy.com/$url", Updater.mirrorApkUrl(url, enabled = true))
        assertEquals(url, Updater.mirrorApkUrl(url, enabled = false))
    }
}