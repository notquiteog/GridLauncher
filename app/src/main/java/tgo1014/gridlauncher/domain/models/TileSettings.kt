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
    /** One of [IconShape]'s keys. Full bleed is the default because it is what the launcher has
     * always drawn: Windows Phone filled its tiles edge to edge, and so do we. */
    val iconShape: String = IconShape.BLEED.key,
    val fullscreen: Boolean = false,
    /** Windows Phone could show a bare dot instead of a count. */
    val badgeAsDot: Boolean = false,
    val hiddenPreviewApps: Set<String> = emptySet(),
    val workSchedule: Boolean = false,
    val workStartHour: Int = 9,
    val workEndHour: Int = 17,
    /** The two layouts the weekday schedule switches between, by name. They are names like any
     * other, so either can be renamed or deleted, and the schedule falls back when one is missing. */
    val workLayout: String = "Work",
    val personalLayout: String = "Personal",
    /** Layout names in the order the bar draws them. Empty means the order they were created in. */
    val layoutOrder: List<String> = emptyList(),
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

    /** The icon mask as a value, never as a raw string. */
    val iconMask: IconShape get() = IconShape.of(iconShape)

    val wallpaperFile: File?
        get() = wallpaperPath?.let { File(it) }

    val isTransparencyEnabled: Boolean
        get() = true
}

/**
 * The launcher-wide shape of an app icon, applied in the one place every icon is drawn.
 *
 * This is the icon, never the tile: the tile stays a flat square reaching its own corners, which is
 * the launcher's identity. [BLEED] is first because it is the look this launcher shipped with.
 */
enum class IconShape(val key: String, val label: String) {
    BLEED("bleed", "Full bleed"),
    ROUNDED("rounded", "Rounded square"),
    CIRCLE("circle", "Circle"),
    SQUIRCLE("squircle", "Squircle"),
    ;

    /** Whether this shape clips anything at all. Full bleed is exactly what was drawn before. */
    val masks: Boolean get() = this != BLEED

    companion object {
        /**
         * A key this build does not know - an older or newer pack, a hand-edited value - falls back
         * to the look that is already on screen, so a bad value can never blank the icons.
         */
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: BLEED
    }
}
