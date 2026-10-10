package tgo1014.gridlauncher.updates

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class UpdateManifestTest {
    private val base = "https://github.com/notquiteog/GridLauncher/releases/download/android17-20/"
    private val manifest = UpdateManifest(packageName = "io.github.notquiteog.gridlauncher", versionCode = 21020, versionName = "2.1.0", minSdk = 26, apkUrl = base + "GridLauncher-Android17.apk", sha256 = "a".repeat(64), size = 12000000)
    private val release = GitHubRelease(assets = listOf(ReleaseAsset("update.json", base + "update.json"), ReleaseAsset("GridLauncher-Android17.apk", manifest.apkUrl)))
    private fun parse(update: UpdateManifest = manifest, version: Long = 21000, sdk: Int = 37, data: GitHubRelease = release) =
        parseUpdate(updateJson.encodeToString(data), { updateJson.encodeToString(update) }, version, sdk)

    @Test fun githubBuildChecksUnlessAnyInstallerIsPlay() {
        assertTrue(usesGitHubUpdates("github", listOf(null)))
        assertTrue(usesGitHubUpdates("github", listOf("com.android.packageinstaller", "com.android.chrome")))
        assertFalse(usesGitHubUpdates("github", listOf("com.android.vending", null)))
        assertFalse(usesGitHubUpdates("github", listOf(null, "com.android.vending")))
        assertFalse(usesGitHubUpdates("play", listOf(null)))
    }
    @Test fun newerCompatibleStableReleaseIsOffered() { assertEquals(manifest, parse()) }
    @Test fun olderSameUnsupportedAndPreviewReleasesAreSkipped() {
        assertNull(parse(version = 21020)); assertNull(parse(version = 21021)); assertNull(parse(sdk = 25))
        assertNull(parse(data = release.copy(draft = true))); assertNull(parse(data = release.copy(prerelease = true)))
    }
    @Test fun manifestMustMatchThisReleaseAndPackage() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest.copy(packageName = "another.app")) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest.copy(apkUrl = base + "another.apk")) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest.copy(sha256 = "invalid")) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest.copy(size = 151000000)) }
    }
    @Test fun metadataCannotRedirectToAnotherRepositoryOrInsecureHost() {
        assertFalse(isReleaseAsset("http://github.com/notquiteog/GridLauncher/releases/download/x/update.json"))
        assertFalse(isReleaseAsset("https://github.com/other/repo/releases/download/x/update.json"))
        assertFalse(isReleaseAsset("https://github.com@evil.example/notquiteog/GridLauncher/releases/download/x/update.json"))
        assertFalse(isReleaseAsset(base + "../update.json"))
        assertTrue(isReleaseAsset(base + "update.json"))
    }
    @Test fun installationRequiresNewerVersionExactPackageAndSameNonemptySigner() {
        fun validate(pkg: String = manifest.packageName, code: Long = 21020, signers: Set<String> = setOf("official"), installed: Set<String> = setOf("official")) =
            validateApkIdentity(manifest.packageName, pkg, 21000, code, 21020, installed, signers)
        validate()
        assertThrows(IllegalArgumentException::class.java) { validate(pkg = "another.app") }
        assertThrows(IllegalArgumentException::class.java) { validate(code = 21000) }
        assertThrows(IllegalArgumentException::class.java) { validate(signers = setOf("other-key")) }
        assertThrows(IllegalArgumentException::class.java) { validate(signers = emptySet(), installed = emptySet()) }
    }
}
