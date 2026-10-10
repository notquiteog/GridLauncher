package tgo1014.gridlauncher.live

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Real data used since Android started counting, straight from the platform's own accounting. The
 * Windows Phone "WiFi: 922 MB used" tile needed no third party, and neither does this.
 */
object UsageTiles {
    fun usedMb(context: Context): Long = runCatching {
        val manager = context.getSystemService(NetworkStatsManager::class.java) ?: return@runCatching -1L
        val boot = android.os.SystemClock.elapsedRealtime()
        // Buckets carry the byte count; there is no aggregate accessor.
        val summary = manager.querySummary(ConnectivityManager.TYPE_WIFI, null, 0L, boot)
        val bucket = android.app.usage.NetworkStats.Bucket()
        var total = 0L
        while (summary.hasNextBucket() && summary.getNextBucket(bucket)) {
            total += bucket.getRxBytes() + bucket.getTxBytes()
        }
        summary.close()
        // A fresh boot has no buckets yet; report nothing rather than guess.
        if (total > 0) total / (1024L * 1024L) else -1L
    }.getOrDefault(-1L)

    fun isOnWifi(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return@runCatching false
        manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)
}
