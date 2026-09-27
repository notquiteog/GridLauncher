package tgo1014.gridlauncher.live

import kotlinx.serialization.Serializable
import tgo1014.gridlauncher.domain.GridPlacement
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem

@Serializable
data class LayoutBackup(val version: Int = 2, val tiles: List<GridItem>, val settings: TileSettings) {
    fun restore(installed: List<App>): List<GridItem> {
        require(version in 1..2) { "Unsupported backup version" }
        require(tiles.size <= 500) { "Too many tiles" }
        val apps = installed.associateBy { it.packageName }
        val builtIns = BuiltInTiles.apps.associateBy { it.packageName }
        val restored = mutableListOf<GridItem>()
        val input = if (version == 1) tgo1014.gridlauncher.data.migrateWholeCells(tiles) else tiles
        input.filter { it.widgetId < 0 && it.photoUris.isEmpty() && it.contact == null && it.contacts.isEmpty() && it.shortcutId == null && it.destination == null }.forEach { tile ->
            val children = tile.children.take(100).mapNotNull { apps[it.packageName] }
            val app = apps[tile.app.packageName] ?: builtIns[tile.app.packageName]
                ?: if (tile.app.packageName == BuiltInTiles.FOLDER && children.isNotEmpty()) App(tile.app.name.take(40), BuiltInTiles.FOLDER) else null
            if (app != null) {
                require(tile.width in 1..12 && tile.height in 1..4 && tile.x in 0..11 && tile.y in 0..2000) { "Invalid tile dimensions" }
                restored += GridPlacement.place(tile.copy(id = restored.size, positionPinned = false, app = app, children = children, widgetId = -1, photoUris = emptyList()), restored, settings.gridColumns)
            }
        }
        return restored
    }
}
