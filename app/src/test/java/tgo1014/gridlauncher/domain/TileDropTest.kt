package tgo1014.gridlauncher.domain

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.ui.models.GridItem

class TileDropTest {
    private val a = GridItem(1, App("A", "app.a"), 1)
    private val b = GridItem(2, App("B", "app.b"), 1, x = 1)

    @Test fun alignedAppsBecomeOneFolderWithBothChildren() {
        val result = TileDrop.apply(listOf(a, b), a.id, 1, 0, 3).single()
        assertEquals(BuiltInTiles.FOLDER, result.app.packageName)
        assertEquals(listOf(b.app, a.app), result.children)
        assertEquals(b.id, result.id)
    }

    @Test fun appCanJoinAnEmptyFolderWithoutDuplicates() {
        val folder = b.copy(app = App("Tools", BuiltInTiles.FOLDER))
        val joined = TileDrop.apply(listOf(a, folder), a.id, 1, 0, 3).single()
        assertEquals("Tools", joined.app.name)
        assertEquals(listOf(a.app), joined.children)
        assertEquals(listOf(a.app), TileDrop.apply(listOf(a.copy(x = 1), joined), a.id, -1, 0, 3).single().children)
    }

    @Test fun pinnedSourceAndZeroDistanceDropsDoNothing() {
        val anchored = listOf(a.copy(positionPinned = true), b)
        assertEquals(anchored, TileDrop.apply(anchored, a.id, 1, 0, 3))
        assertEquals(listOf(a, b), TileDrop.apply(listOf(a, b), a.id, 0, 0, 3))
    }

    @Test fun groupingKeepsTargetAnchorAndOtherTiles() {
        val anchor = b.copy(positionPinned = true, tileColor = 0xFF0078D7)
        val neighbour = GridItem(3, App("C", "app.c"), 1, x = 2)
        val result = TileDrop.apply(listOf(a, anchor, neighbour), a.id, 1, 0, 3)
        val folder = result.first { it.id == b.id }
        assertTrue(folder.positionPinned)
        assertEquals(1, folder.x)
        assertEquals(anchor.tileColor, folder.tileColor)
        assertEquals(neighbour.app, result.first { it.id == neighbour.id }.app)
    }

    @Test fun richTilesNeverLoseTheirPayloadInAFolderDrop() {
        val rich = listOf(a.copy(widgetId = 9), a.copy(shortcutId = "shortcut"),
            a.copy(destination = "https://example.com"), a.copy(photoUris = listOf("content://photos/1")),
            a.copy(app = App("Folder", BuiltInTiles.FOLDER), children = listOf(a.app)),
            a.copy(app = App("Clock", BuiltInTiles.CLOCK)))
        for (tile in rich) {
            val moved = TileDrop.apply(listOf(tile, b), tile.id, 1, 0, 3)
            assertEquals(2, moved.size)
            assertEquals(tile.copy(x = 1), moved.first { it.id == tile.id })
            val ontoRich = TileDrop.apply(listOf(b.copy(x = 0), tile.copy(x = 1)), b.id, 1, 0, 3)
            if (tile.app.packageName != BuiltInTiles.FOLDER) assertEquals(2, ontoRich.size)
        }
    }

    @Test fun partialOverlapMovesInsteadOfGroupingAndDuplicateAppStaysATile() {
        val wide = b.copy(width = 2)
        assertEquals(2, TileDrop.apply(listOf(a, wide), a.id, 2, 0, 3).size)
        assertEquals(2, TileDrop.apply(listOf(a, b.copy(app = a.app)), a.id, 1, 0, 3).size)
    }
}
