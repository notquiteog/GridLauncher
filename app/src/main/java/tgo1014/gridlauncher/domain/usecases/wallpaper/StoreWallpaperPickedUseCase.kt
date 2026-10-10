package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.data.reduceBitmapBrightness
import tgo1014.gridlauncher.data.saveToFile
import tgo1014.gridlauncher.data.toBitmap
import java.io.File
import javax.inject.Inject

/**
 * Stores a chosen wallpaper twice, once as picked and once darkened, and returns the path to use.
 * Which variant is active is decided later from the launcher's own dark setting, so toggling
 * Dark background swaps the wallpaper without re-picking it.
 */
class StoreWallpaperPickedUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(uri: Uri, dark: Boolean): String? = withContext(Dispatchers.IO) {
        runCatching {
            val drawable = context.contentResolver.openInputStream(uri)?.use { Drawable.createFromStream(it, uri.toString()) }
                ?: return@runCatching null
            val bitmap = drawableToBitmap(drawable)
            bitmap.saveToFile(context.wallpaperFile)
            bitmap.reduceBitmapBrightness().saveToFile(context.wallpaperDarkFile)
            (if (dark) context.wallpaperDarkFile else context.wallpaperFile).absolutePath
        }.getOrNull()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createBitmap(drawable.bitmap)
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { canvas ->
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(Canvas(canvas))
        }
    }
}
