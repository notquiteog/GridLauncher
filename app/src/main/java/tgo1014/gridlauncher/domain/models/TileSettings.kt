package tgo1014.gridlauncher.domain.models

import kotlinx.serialization.Serializable
import tgo1014.gridlauncher.app.Constants.defaultRadius
import java.io.File

@Serializable
data class TileSettings(
    val isTileFlipEnabled: Boolean = true,
    val liveTilesEnabled: Boolean = true,
    val showNotificationText: Boolean = false,
    val accentColor: Long = 0xFF0078D7,
    val darkTheme: Boolean = true,
    val cornerRadius: Int = defaultRadius,
    val isAppLabelsHidden: Boolean = false,
    val wallpaperPath: String? = null,
) {

    val wallpaperFile: File?
        get() = wallpaperPath?.let { File(it) }

    val isTransparencyEnabled: Boolean
        get() = wallpaperPath != null
}