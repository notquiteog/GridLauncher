package tgo1014.gridlauncher.ui.models

import kotlinx.serialization.Serializable
import tgo1014.gridlauncher.domain.models.App

@Serializable
data class GridItem(
    val id: Int = -1,
    val app: App,
    val width: Int,
    val height: Int = width,
    val x: Int = 0,
    val y: Int = 0,
    val children: List<App> = emptyList(),
    val widgetId: Int = -1,
    val photoUris: List<String> = emptyList(),
    val positionPinned: Boolean = false,
    val shortcutId: String? = null,
    val destination: String? = null,
    val contact: tgo1014.gridlauncher.live.PinnedContact? = null,
    val contacts: List<tgo1014.gridlauncher.live.PinnedContact> = emptyList(),
    /** A per-tile colour that beats the icon's own edge colour, as Windows Phone allowed. */
    val tileColor: Long? = null,
    /** Nested folders, so a folder can hold another folder. */
    val childFolders: List<GridItem> = emptyList(),
    val groupLabel: String = "",
) {
    val isGroup get() = app.packageName == tgo1014.gridlauncher.live.BuiltInTiles.GROUP
    val childCount get() = children.size + childFolders.size
}