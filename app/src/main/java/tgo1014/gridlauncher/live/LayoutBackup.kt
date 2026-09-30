package tgo1014.gridlauncher.live

import kotlinx.serialization.Serializable
import tgo1014.gridlauncher.domain.GridPlacement
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem

@Serializable
data class LayoutBackup(
    val version: Int = 4,
    val tiles: List<GridItem>,
    val settings: TileSettings,
    /** Layout names to recreate, so a restore brings back every custom layout, not just one grid. */
    val layouts: List<String> = emptyList(),
    /** The shareable appearance code, as ThemePacks.encode would produce it. */
    val theme: String = "",
    /** Portable tiles for every layout, so restoring does not leave the other layouts empty. */
    val layoutGrids: Map<String, List<GridItem>> = emptyMap(),
) {
    /** Every layout's portable grid, newest format first, falling back to the single active grid. */
    fun allGrids(): Map<String, List<GridItem>> = if (layoutGrids.isNotEmpty()) layoutGrids else mapOf("" to tiles)

    fun restore(installed: List<App>): List<GridItem> = restore(listOf(), tiles, installed)

    /** Restores one layout's grid, migrating a v1 pixel grid and re-packing into whole cells. */
    fun restore(other: List<GridItem>, grid: List<GridItem>, installed: List<App>): List<GridItem> {
        require(version in 1..4) { "Unsupported backup version" }
        require(grid.size <= 500) { "Too many tiles" }
        require(other.isEmpty()) { "Use allGrids() for a multi-layout backup" }
        val apps = installed.associateBy { it.packageName }
        val builtIns = BuiltInTiles.restorable
        val restored = mutableListOf<GridItem>()
        val input = if (version == 1) tgo1014.gridlauncher.data.migrateWholeCells(grid) else grid
        input.filter { it.widgetId < 0 && it.photoUris.isEmpty() && it.contact == null && it.contacts.isEmpty() && it.shortcutId == null && it.destination == null }.forEach { tile ->
            val children = tile.children.take(100).mapNotNull { apps[it.packageName] }
            val app = apps[tile.app.packageName] ?: (if (tile.isGroup) tgo1014.gridlauncher.domain.models.App(tile.app.name.take(40), BuiltInTiles.GROUP) else null)
                ?: builtIns[tile.app.packageName]
                ?: if (tile.app.packageName == BuiltInTiles.FOLDER && (children.isNotEmpty() || tile.childFolders.isNotEmpty()))
                    App(tile.app.name.take(40), BuiltInTiles.FOLDER) else null
            if (app != null) {
                require(tile.width in 1..12 && tile.height in 1..4 && tile.x in 0..11 && tile.y in 0..2000) { "Invalid tile dimensions" }
                restored += tile.copy(id = restored.size, app = app, children = children, widgetId = -1, photoUris = emptyList(),
                    childFolders = tile.childFolders.take(20).map { nested -> nested.copy(id = -1, widgetId = -1, photoUris = emptyList()) })
            }
        }
        return GridPlacement.reflow(restored, settings.gridColumns)
    }
}
