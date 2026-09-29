package tgo1014.gridlauncher.live

import tgo1014.gridlauncher.domain.models.TileSettings

/**
 * A Windows Phone theme pack, reduced to the shareable part: look and behaviour, never your apps.
 * The format is a short, hand-typable code so it survives being pasted into a chat.
 */
object ThemePacks {
    const val prefix = "GL1"
    private val finishes = listOf("frosted", "clear", "acrylic", "solid")

    fun encode(settings: TileSettings): String = listOf(
        prefix,
        "%06X".format(settings.accentColor and 0xFFFFFF),
        settings.glassFinish,
        settings.tilesAcross.coerceIn(2, 6).toString(),
        if (settings.darkTheme) "d" else "l",
        bit(settings.isTileFlipEnabled),
        bit(settings.showNotificationText),
        bit(settings.reduceMotion),
        bit(settings.isAppLabelsHidden),
        bit(settings.stackNotifications),
    ).joinToString("|")

    /** Returns null for anything that is not a valid pack, so a bad paste simply does nothing. */
    fun decode(code: String): TileSettings? {
        val parts = code.trim().split("|").map { it.trim() }
        if (parts.size != 10 || parts[0] != prefix) return null
        val accent = parts[1].toLongOrNull(16)?.takeIf { parts[1].length == 6 } ?: return null
        val finish = parts[2].takeIf { it in finishes } ?: return null
        val columns = parts[3].toIntOrNull()?.takeIf { it in 2..6 } ?: return null
        val flags = (5..9).map { index -> when (parts[index]) { "1" -> true; "0" -> false; else -> null } }
        if (flags.any { it == null }) return null
        val dark = when (parts[4]) { "d" -> true; "l" -> false; else -> null } ?: return null
        return TileSettings(
            accentColor = 0xFF000000 or accent,
            glassFinish = finish,
            tilesAcross = columns,
            darkTheme = dark,
            isTileFlipEnabled = flags[0] == true,
            showNotificationText = flags[1] == true,
            reduceMotion = flags[2] == true,
            isAppLabelsHidden = flags[3] == true,
            stackNotifications = flags[4] == true,
        )
    }

    private fun bit(value: Boolean) = if (value) "1" else "0"
}
