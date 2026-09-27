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
    val cornerRadius: Int = 0,
    val tilesAcross: Int = 3,
    val glassFinish: String = "frosted",
    val wallpaperTint: Boolean = true,
    val reduceMotion: Boolean = false,
    val oneHanded: Boolean = false,
    val showNow: Boolean = true,
    val hiddenPreviewApps: Set<String> = emptySet(),
    val workSchedule: Boolean = false,
    val workStartHour: Int = 9,
    val workEndHour: Int = 17,
    val handoffEnabled: Boolean = false,
    val isAppLabelsHidden: Boolean = false,
    val wallpaperPath: String? = null,
) {

    val gridColumns: Int get() = tilesAcross.coerceIn(2, 6)

    val wallpaperFile: File?
        get() = wallpaperPath?.let { File(it) }

    val isTransparencyEnabled: Boolean
        get() = wallpaperPath != null
}