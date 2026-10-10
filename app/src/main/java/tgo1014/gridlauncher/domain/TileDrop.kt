package tgo1014.gridlauncher.domain

import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.ui.models.GridItem

/** Folder drops only consume plain app tiles; a widget or shortcut must keep its payload. */
object TileDrop {
    private fun GridItem.plainApp() = !app.packageName.startsWith("grid://") &&
        childCount == 0 && widgetId < 0 && shortcutId == null && destination == null &&
        photoUris.isEmpty() && contact == null && contacts.isEmpty()

    fun apply(grid: List<GridItem>, id: Int, dx: Int, dy: Int, columns: Int): List<GridItem> {
        val current = grid.firstOrNull { it.id == id } ?: return grid
        if (current.positionPinned || dx == 0 && dy == 0) return grid
        val landing = current.copy(x = current.x + dx, y = current.y + dy)
        // An aligned drop is intentional grouping. Brushing a large neighbouring tile still moves.
        val target = grid.firstOrNull { it.id != id && it.x == landing.x && it.y == landing.y }
        val folder = target?.app?.packageName == BuiltInTiles.FOLDER
        if (current.plainApp() && target != null && (folder || target.plainApp()) &&
            (folder || current.app.packageName != target.app.packageName)) {
            val updated = if (folder) target.copy(children = (target.children + current.app).distinctBy { it.packageName })
                else target.copy(app = App("Folder", BuiltInTiles.FOLDER), children = listOf(target.app, current.app))
            return GridPlacement.compact(grid.filterNot { it.id == id }.map { if (it.id == target.id) updated else it }, columns)
        }
        return GridPlacement.update(grid, landing, columns)
    }
}
