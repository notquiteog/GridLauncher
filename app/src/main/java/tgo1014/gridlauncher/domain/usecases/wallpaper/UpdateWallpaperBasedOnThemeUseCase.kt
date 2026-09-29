package tgo1014.gridlauncher.domain.usecases.wallpaper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import tgo1014.gridlauncher.data.reduceBitmapBrightness
import tgo1014.gridlauncher.data.saveToFile
import tgo1014.gridlauncher.data.toBitmap
import tgo1014.gridlauncher.domain.SettingsRepository
import java.io.File
import javax.inject.Inject

class UpdateWallpaperBasedOnThemeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() = runCatching {
        val settings = settingsRepository.tileSettingsFlow.first()
        val file = getWallpaperFileForTheme(settings.darkTheme)
        if (file.absolutePath == settings.wallpaperPath) return@runCatching
        settingsRepository.updateSettings(
            settings.copy(wallpaperPath = file.absolutePath)
        )
    }.onFailure(::println)

    /** Keyed on the launcher's own dark setting, so toggling it actually swaps the wallpaper. */
    private fun getWallpaperFileForTheme(dark: Boolean): File = when {
        !dark -> context.wallpaperFile
        context.wallpaperDarkFile.exists() -> context.wallpaperDarkFile
        else -> {
            val bitmap = context.wallpaperFile.toBitmap() ?: return context.wallpaperFile
            bitmap.reduceBitmapBrightness().saveToFile(context.wallpaperDarkFile)
            context.wallpaperDarkFile
        }
    }

}