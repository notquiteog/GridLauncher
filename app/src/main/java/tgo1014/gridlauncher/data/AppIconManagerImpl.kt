package tgo1014.gridlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.domain.AppIconManager
import tgo1014.gridlauncher.domain.models.DispatcherProvider
import tgo1014.gridlauncher.domain.models.Icon
import java.io.File
import javax.inject.Inject

class AppIconManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager,
) : AppIconManager {
    override suspend fun getIcon(packageName: String): Icon = withContext(dispatcherProvider.io) {
        // Versioned paths replace the old foreground-only cache on upgrade and refresh
        // when the app changes its icon. Draw the complete, unmodified system drawable.
        @Suppress("DEPRECATION")
        val updated = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        val prefix = "${packageName}_default_"
        val file = File(context.cacheDir, "$prefix$updated.png")
        if (!file.exists()) {
            val bitmap = packageManager.getApplicationIcon(packageName).toBitmap(256, 256)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            context.cacheDir.listFiles()?.filter {
                it != file && (it.name.startsWith(prefix) || it.name == "${packageName}_icon.png" || it.name == "${packageName}_bg.png")
            }?.forEach { it.delete() }
        }
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        val edgeColor = bitmap?.let {
            val pixels = IntArray(it.width * it.height)
            it.getPixels(pixels, 0, it.width, 0, 0, it.width, it.height)
            iconEdgeColor(pixels, it.width, it.height).also { _ -> bitmap.recycle() }
        }
        Icon(iconFilePath = file.absolutePath, edgeColor = edgeColor)
    }
}
