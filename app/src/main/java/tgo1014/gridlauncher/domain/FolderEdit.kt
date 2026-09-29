package tgo1014.gridlauncher.domain

import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.ui.models.GridItem

/**
 * A folder's membership, edited by package rather than by value. A stored child can carry an icon
 * the picker row does not, and `App` equality would then silently keep the entry on removal.
 */
object FolderEdit {
    fun contains(folder: GridItem, app: App) =
        folder.children.any { it.packageName == app.packageName } ||
            folder.childFolders.any { it.app.packageName == app.packageName }

    fun toggled(folder: GridItem, app: App): GridItem {
        val inside = contains(folder, app)
        return folder.copy(
            children = if (inside) folder.children.filterNot { it.packageName == app.packageName } else folder.children + app,
            childFolders = folder.childFolders.filterNot { it.app.packageName == app.packageName },
        )
    }

    /** Hub tiles belong to the launcher, not to a package, so a folder must be able to offer them. */
    fun candidates(installed: List<App>): List<App> =
        (BuiltInTiles.apps + installed).distinctBy { it.packageName }.sortedBy { it.name.lowercase() }

    /** Everything a package refresh may keep: our own hubs, plus apps still on the device. */
    fun keepsAfterRefresh(app: App, installed: Map<String, App>) =
        if (app.packageName.startsWith("grid://")) app else installed[app.packageName]
}
