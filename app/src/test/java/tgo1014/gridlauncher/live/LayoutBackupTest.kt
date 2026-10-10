package tgo1014.gridlauncher.live

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem

class LayoutBackupTest {
    @Test fun roundTripRehydratesAppsAndRemovesDeviceBoundTiles() {
        val installed = App("Current label", "org.example.app")
        val tiles = listOf(GridItem(9, App("Old label", installed.packageName), 2),
            GridItem(10, App("Widget", BuiltInTiles.WIDGET), 4, widgetId = 4),
            GridItem(11, App("Photos", BuiltInTiles.PHOTOS), 4, photoUris = listOf("content://photo/1")))
        val backup = LayoutBackup(tiles = tiles, settings = TileSettings())
        val decoded = Json.decodeFromString(LayoutBackup.serializer(), Json.encodeToString(LayoutBackup.serializer(), backup))
        val restored = decoded.restore(listOf(installed))
        assertEquals(1, restored.size)
        assertEquals(installed, restored.single().app)
        assertEquals(0, restored.single().id)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownBackupVersion() {
        LayoutBackup(version = 9, tiles = emptyList(), settings = TileSettings()).restore(emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsPathologicalDimensions() {
        val app = App("App", "org.example.app")
        LayoutBackup(tiles = listOf(GridItem(1, app, Int.MAX_VALUE)), settings = TileSettings()).restore(listOf(app))
    }
    @Test fun legacyGridWithoutNewFieldsStillDeserializes() {
        val item = Json.decodeFromString(GridItem.serializer(), """{"id":1,"app":{"name":"Example","packageName":"org.example"},"width":2}""")
        assertTrue(item.children.isEmpty()); assertEquals(-1, item.widgetId)
    }
    @Test fun restorePreservesValidPositionPins() {
        val app = App("App", "org.example.app")
        val backup = LayoutBackup(tiles = listOf(GridItem(12, app, 1, x = 2, y = 3, positionPinned = true)), settings = TileSettings())
        val restored = backup.restore(listOf(app)).single()
        assertTrue(restored.positionPinned); assertEquals(2, restored.x); assertEquals(3, restored.y)
    }

}
