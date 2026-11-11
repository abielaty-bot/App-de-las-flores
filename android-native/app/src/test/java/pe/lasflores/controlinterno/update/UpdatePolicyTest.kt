package pe.lasflores.controlinterno.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val sha = "ab".repeat(32)
    private fun release(version: Long) = AppRelease(version, "v$version", "", 
        "https://github.com/abielaty-bot/App-de-las-flores/releases/download/android-beta-v$version/las-flores-beta.apk",
        sha, 12_000_000)

    @Test fun versionTagsMustMatchOurReleaseChannel() {
        assertEquals(4L, UpdatePolicy.versionFromTag("android-beta-v4"))
        assertEquals(40001L, UpdatePolicy.versionFromTag("android-beta-v40001"))
        assertEquals("4.0001", UpdatePolicy.versionNameForCode(40001))
        assertEquals("4.0010", UpdatePolicy.versionNameForCode(40010))
        assertEquals("5.0000", UpdatePolicy.versionNameForCode(50000))
        assertNull(UpdatePolicy.versionFromTag("v4"))
        assertNull(UpdatePolicy.versionFromTag("android-beta-v0"))
        assertNull(UpdatePolicy.versionFromTag("android-beta-v4-unsafe"))
    }

    @Test fun onlyNewerReleasesAreSelected() {
        assertEquals(5L, UpdatePolicy.latestCompatible(3, listOf(release(3), release(5), release(4)))?.versionCode)
        assertNull(UpdatePolicy.latestCompatible(5, listOf(release(4), release(5))))
    }

    @Test fun checksumMustBeSha256() {
        assertEquals(sha, UpdatePolicy.validSha256("sha256:$sha"))
        assertNull(UpdatePolicy.validSha256("sha256:aaaa"))
        assertNull(UpdatePolicy.validSha256(null))
    }

    @Test fun refuseUntrustedDownloadLocations() {
        assertTrue(UpdatePolicy.isTrustedDownloadUrl(release(9).apkUrl))
        assertFalse(UpdatePolicy.isTrustedDownloadUrl("http://github.com/abielaty-bot/App-de-las-flores/releases/download/android-beta-v9/las-flores-beta.apk"))
        assertFalse(UpdatePolicy.isTrustedDownloadUrl("https://evil.example/releases/download/android-beta-v9/las-flores-beta.apk"))
        assertFalse(UpdatePolicy.isTrustedDownloadUrl("https://github.com/other/repo/releases/download/android-beta-v9/las-flores-beta.apk"))
        assertFalse(UpdatePolicy.isTrustedDownloadUrl("https://github.com/abielaty-bot/App-de-las-flores/releases/download/android-beta-v9/las-flores-beta.apk?download=1"))
    }
}
