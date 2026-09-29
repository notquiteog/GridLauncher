package tgo1014.gridlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.domain.AppIconManager
import tgo1014.gridlauncher.domain.models.DispatcherProvider
import tgo1014.gridlauncher.domain.models.Icon
import java.io.File
import javax.inject.Inject

/**
 * Caches each app's own, unmodified default icon and normalises it to a consistent optical size.
 *
 * Apps draw their icon at wildly different scales inside the same 256px canvas, so a raw copy
 * makes some look tiny and others oversized in a fixed-size tile. We keep the original artwork
 * untouched and only record the drawn bounds, so the tile can inset an under-filled icon without
 * recolouring or redrawing anything the app did not draw.
 */
class AppIconManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
) : AppIconManager {
    override suspend fun getIcon(packageName: String): Icon = withContext(dispatcherProvider.io) {
        @Suppress("DEPRECATION")
        val updated = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        val prefix = "${packageName}_default_"
        val file = File(context.cacheDir, "$prefix$updated.png")
        if (!file.exists()) {
            val bitmap = packageManager.getApplicationIcon(packageName).toBitmap(256, 256)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            context.cacheDir.listFiles()?.filter {
                it != file && (it.name.startsWith(prefix) || it.name == "${packageName}_icon.png" || it.name == "${packageName}_bg.png")
            }?.forEach { it.delete() }
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val edges = bitmap?.let { measured ->
            val pixels = IntArray(measured.width * measured.height)
            measured.getPixels(pixels, 0, measured.width, 0, 0, measured.width, measured.height)
            val edge = iconEdgeColor(pixels, measured.width, measured.height)
            measured.recycle()
            edge to opticalScale(pixels, measured.width, measured.height)
        }
        Icon(iconFilePath = file.absolutePath, edgeColor = edges?.first, fill = edges?.second ?: 1f)
    }

    /**
     * How much of the canvas the app actually painted, as a 0..1 fraction. Below 1 means the icon is
     * inset inside its own frame; the tile scales that back up so every icon reads at one size.
     */
    private fun opticalScale(pixels: IntArray, width: Int, height: Int): Float {
        val alphaThreshold = 8
        var left = width; var right = -1; var top = height; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (android.graphics.Color.alpha(pixels[y * width + x]) > alphaThreshold) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return 1f
        val longest = maxOf(right - left + 1, bottom - top + 1).toFloat()
        return (longest / maxOf(width, height)).coerceIn(0.35f, 1f)
    }
}
