package tgo1014.gridlauncher.domain.models

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class TileSettings(
    val isTileFlipEnabled: Boolean = true,
    val liveTilesEnabled: Boolean = true,
    val showNotificationText: Boolean = true,
    val accentColor: Long = 0xFF0078D7,
    val darkTheme: Boolean = true,
    val tilesAcross: Int = 3,
    val glassFinish: String = "frosted",
    val wallpaperTint: Boolean = true,
    val reduceMotion: Boolean = false,
    val oneHanded: Boolean = false,
    val showNow: Boolean = true,
    val meetingMode: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val showTileCounts: Boolean = true,
    val stackNotifications: Boolean = true,
    val showStartHeader: Boolean = true,
    /** Package names for the hotseat, in order. Lives outside the packed grid on purpose. */
    val hotseat: List<String> = emptyList(),
    /** A1Z26, most used, or recently used. */
    val drawerSort: String = "alphabetical",
    val iconTint: Boolean = false,
    val fullscreen: Boolean = false,
    /** Windows Phone could show a bare dot instead of a count. */
    val badgeAsDot: Boolean = false,
    val hiddenPreviewApps: Set<String> = emptySet(),
    val workSchedule: Boolean = false,
    val workStartHour: Int = 9,
    val workEndHour: Int = 17,
    val handoffEnabled: Boolean = false,
    val isAppLabelsHidden: Boolean = false,
    val wallpaperPath: String? = null,
    /**
     * SECURITY: off by default, and opt-in means exactly one thing - the starred people and the
     * calendar titles you have already granted access to are written to a private on-device search
     * database so the drawer can answer a query before it has read anything. Notification text,
     * conversation content and call state are never written there under any setting; see
     * `SearchPersistence`, which is where that is enforced rather than here.
     */
    val searchIndexEnabled: Boolean = false,
) {

    val gridColumns: Int get() = tilesAcross.coerceIn(2, 6)

    val wallpaperFile: File?
        get() = wallpaperPath?.let { File(it) }

    val isTransparencyEnabled: Boolean
        get() = true
}