package tgo1014.gridlauncher.ui

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.ui.composables.filedTile
import tgo1014.gridlauncher.ui.composables.jumpRows
import tgo1014.gridlauncher.ui.models.GridItem

class FolderJumpListTest {
    private fun folder(children: List<App>, folders: List<GridItem> = emptyList()) =
        GridItem(1, App("Tools", BuiltInTiles.FOLDER), 1, children = children, childFolders = folders)

    @Test fun theJumpListOffersAppsFirstAndThenTheFoldersItHolds() {
        val clock = App("Clock", BuiltInTiles.CLOCK)
        val battery = App("Battery", BuiltInTiles.BATTERY)
        val inner = GridItem(-1, App("Reading", BuiltInTiles.FOLDER), 1, children = listOf(clock))
        val rows = jumpRows(folder(listOf(clock, battery), listOf(inner)))
        // The order the tile's own mosaic previews: the apps, in the order they were filed, then the
        // folders. A menu that reordered them would be showing a folder the user has not seen.
        assertEquals(listOf("Clock", "Battery", "Reading"), rows.map { it.app.name })
        // A folder row opens and an app row launches, which is the only thing that tells them apart.
        assertEquals(listOf(null, null, inner), rows.map { it.open })
    }

    @Test fun anEmptyFolderOffersNothingRatherThanAnEmptyMenu() {
        assertTrue(jumpRows(folder(emptyList())).isEmpty())
        assertEquals(0, jumpRows(GridItem(1, App("Empty", BuiltInTiles.FOLDER), 1)).size)
    }

    @Test fun aFiledAppBecomesTheSameTileWhereverItIsShown() {
        val app = App("Settings", "com.android.settings")
        val filed = filedTile(app)
        assertEquals(app, filed.app)
        assertEquals(1, filed.width)
        // The id is derived from the package, so the same app in two folders cannot collide in a
        // list keyed by id, and re-filing an app lands on the same tile again.
        assertEquals(filed.id, filedTile(app).id)
        assertEquals(filed.id, filedTile(app.copy(name = "Renamed")).id)
        assertNotEquals(filed.id, filedTile(App("Camera", "com.android.camera2")).id)
    }
}
