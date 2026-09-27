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
)