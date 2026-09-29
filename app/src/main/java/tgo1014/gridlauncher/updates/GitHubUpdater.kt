package tgo1014.gridlauncher.updates

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tgo1014.gridlauncher.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class UpdateState(val checking: Boolean = false, val downloading: Boolean = false, val progress: Int = 0,
    val update: UpdateManifest? = null, val apk: File? = null, val message: String? = null)

class GitHubUpdater(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val prefs = app.getSharedPreferences("github-updates", 0)
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val automaticMutable = MutableStateFlow(prefs.getBoolean("automatic", true))
    val automatic = automaticMutable.asStateFlow()
    val eligible: Boolean get() = runCatching {
        val source = app.packageManager.getInstallSourceInfo(app.packageName)
        val installers = listOf(source.installingPackageName, source.initiatingPackageName, source.updateOwnerPackageName)
        usesGitHubUpdates(BuildConfig.DISTRIBUTION_CHANNEL, installers)
    }.getOrDefault(false)

    fun setAutomatic(value: Boolean) { automaticMutable.value = value; prefs.edit().putBoolean("automatic", value).apply() }
    fun check(manual: Boolean = false) {
        if (!eligible || mutable.value.checking || mutable.value.downloading) return
        val now = System.currentTimeMillis()
        if (!manual && (BuildConfig.DEBUG || !automaticMutable.value || mutable.value.update != null ||
                    now - prefs.getLong("lastSuccess", 0) in 0 until 86_400_000L || now - prefs.getLong("lastAttempt", 0) in 0 until 3_600_000L)) return
        prefs.edit().putLong("lastAttempt", now).apply()
        mutable.value = UpdateState(checking = true)
        viewModelScope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    parseUpdate(readText(RELEASE_API), ::readText, BuildConfig.VERSION_CODE.toLong(), sdk = 37)
                }
                prefs.edit().putLong("lastSuccess", System.currentTimeMillis()).apply()
                mutable.value = UpdateState(update = update, message = if (manual && update == null) "You have the latest compatible GitHub release." else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = UpdateState(message = if (manual) "Could not check GitHub. Check your connection and try again." else null) }
        }
    }
    fun dismiss() { if (!mutable.value.downloading) mutable.value = UpdateState() }
    fun download() {
        if (!eligible || mutable.value.downloading) return
        val update = mutable.value.update ?: return
        mutable.value = mutable.value.copy(downloading = true, progress = 0, message = null)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                    val partial = File(dir, "update.part.apk")
                    val target = File(dir, "update.apk")
                    target.delete()
                    try {
                        val connection = openConnection(update.apkUrl)
                        try {
                            connection.inputStream.use { input -> partial.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024); var count = 0L
                                while (true) {
                                    ensureActive()
                                    val read = input.read(buffer); if (read == -1) break
                                    count += read; require(count <= update.size) { "APK too large" }
                                    output.write(buffer, 0, read)
                                    mutable.value = mutable.value.copy(progress = ((count * 100) / update.size).toInt())
                                }
                                require(count == update.size) { "Incomplete APK" }
                            } }
                        } finally { connection.disconnect() }
                        val hash = MessageDigest.getInstance("SHA-256")
                        partial.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
                        require(hash.digest().joinToString("") { "%02x".format(it) }.equals(update.sha256, true)) { "APK checksum mismatch" }
                        verifyApk(app.packageManager, app.packageName, partial, update.versionCode)
                        check(partial.renameTo(target)) { "Could not save update" }
                        target
                    } finally { partial.delete() }
                }
                mutable.value = mutable.value.copy(downloading = false, apk = file, progress = 100)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(downloading = false, apk = null, message = "Download or verification failed. No update was installed. Try again on a reliable connection.") }
        }
    }
}

@Suppress("DEPRECATION")
internal fun verifyApk(pm: PackageManager, packageName: String, file: File, expectedVersion: Long) {
    val flags = PackageManager.GET_SIGNING_CERTIFICATES
    val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Invalid APK")
    val installed = pm.getPackageInfo(packageName, flags)
    fun version(info: PackageInfo) = info.longVersionCode
    fun signers(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners.orEmpty()
        .orEmpty().map { it.toCharsString() }.toSet()
    validateApkIdentity(packageName, archive.packageName, version(installed), version(archive), expectedVersion, signers(installed), signers(archive))
}

private fun readText(url: String): String {
    val connection = openConnection(url)
    return try { connection.inputStream.use { input ->
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; require(bytes.size() + n <= 1_000_000) { "Response too large" }; bytes.write(buffer, 0, n) }
        bytes.toString("UTF-8")
    } } finally { connection.disconnect() }
}
private fun openConnection(url: String): HttpURLConnection {
    var next = url
    repeat(6) {
        val uri = URI(next)
        require(uri.scheme == "https" && uri.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com") && uri.userInfo == null && uri.port == -1) { "Untrusted download URL" }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 30_000; connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "GridLauncher/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*")
        val code = connection.responseCode
        if (code in listOf(301, 302, 303, 307, 308)) {
            val location = connection.getHeaderField("Location"); connection.disconnect()
            require(location != null) { "Missing redirect" }; next = uri.resolve(location).toString()
        } else {
            if (code != 200) { connection.disconnect(); error("GitHub returned HTTP $code") }
            return connection
        }
    }
    error("Too many redirects")
}
