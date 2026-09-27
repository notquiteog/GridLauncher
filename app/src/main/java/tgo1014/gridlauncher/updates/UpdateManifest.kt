package tgo1014.gridlauncher.updates

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

const val RELEASE_REPO = "notquiteog/GridLauncher"
const val RELEASE_API = "https://api.github.com/repos/$RELEASE_REPO/releases/latest"
const val RELEASE_PAGE = "https://github.com/$RELEASE_REPO/releases/latest"

@Serializable
data class UpdateManifest(val schema: Int = 1, val packageName: String, val versionCode: Long,
    val versionName: String, val minSdk: Int, val apkUrl: String, val sha256: String, val size: Long)
@Serializable
internal data class ReleaseAsset(val name: String, val browser_download_url: String)
@Serializable
internal data class GitHubRelease(val draft: Boolean = false, val prerelease: Boolean = false, val assets: List<ReleaseAsset>)
internal val updateJson = Json { ignoreUnknownKeys = true }
internal fun usesGitHubUpdates(channel: String, installers: List<String?>) = channel == "github" && "com.android.vending" !in installers
internal fun isReleaseAsset(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 && uri.fragment == null && uri.query == null &&
        uri.path.startsWith("/$RELEASE_REPO/releases/download/") && !uri.path.contains("..")
}.getOrDefault(false)
internal fun parseUpdate(releaseText: String, metadata: (String) -> String, currentVersion: Long, sdk: Int): UpdateManifest? {
    val release = updateJson.decodeFromString<GitHubRelease>(releaseText)
    if (release.draft || release.prerelease) return null
    val descriptor = release.assets.singleOrNull { it.name == "update.json" } ?: return null
    require(isReleaseAsset(descriptor.browser_download_url)) { "Invalid release metadata URL" }
    val update = updateJson.decodeFromString<UpdateManifest>(metadata(descriptor.browser_download_url))
    require(update.schema == 1 && update.packageName == "io.github.notquiteog.gridlauncher") { "Wrong update package" }
    require(update.size in 1..150_000_000 && update.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid APK metadata" }
    require(isReleaseAsset(update.apkUrl) && release.assets.any { it.name == "GridLauncher-Android17.apk" && it.browser_download_url == update.apkUrl }) { "APK is not part of this release" }
    return update.takeIf { it.versionCode > currentVersion && it.minSdk <= sdk }
}

internal fun validateApkIdentity(packageName: String, archivePackage: String?, installedVersion: Long, archiveVersion: Long, expectedVersion: Long, installedSigners: Set<String>, archiveSigners: Set<String>) {
    require(archivePackage == packageName && archiveVersion == expectedVersion && expectedVersion > installedVersion) { "Wrong package or version" }
    require(installedSigners.isNotEmpty() && archiveSigners == installedSigners) { "Update is signed by another publisher" }
}
