package tgo1014.gridlauncher.domain

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import kotlin.random.Random

class GridPlacementTest {
    @Test fun resizingAtRightEdgeKeepsEveryTileInBoundsAndDisplacesNeighbors() {
        val tiles = (0..11).map { GridItem(it, App("App $it"), 1, x = it % 3, y = it / 3) }
        assertValid(GridPlacement.update(tiles, tiles[2].copy(width = 2)))
    }
    @Test fun movingToOccupiedCellPreservesRequestedLocation() {
        val a = GridItem(0, App(), 1); val b = GridItem(1, App(), 1, x = 1)
        val result = GridPlacement.update(listOf(a, b), a.copy(x = 1))
        assertEquals(1, result.first { it.id == 0 }.x); assertValid(result)
    }
    @Test fun randomizedResizeAndMoveNeverLosesOrOverlapsTiles() {
        val random = Random(17)
        var grid = emptyList<GridItem>()
        repeat(60) { id -> grid = grid + GridPlacement.place(GridItem(id, App(), 1), grid) }
        repeat(300) {
            val tile = grid.random(random)
            grid = GridPlacement.update(grid, tile.copy(width = listOf(1,2,3).random(random), height = listOf(1,2,3).random(random), x = random.nextInt(-3, 10), y = random.nextInt(-3,30)))
            assertEquals(60, grid.map { it.id }.toSet().size); assertValid(grid)
        }
    }
    @Test fun removalFillsGapsWithoutMovingPositionPinnedTiles() {
        val pinned = GridItem(2, App(), 1, x = 2, positionPinned = true)
        val before = listOf(GridItem(1, App(), 1, x = 1), pinned, GridItem(3, App(), 1, y = 1))
        val after = GridPlacement.compact(before)
        assertEquals(pinned, after.first { it.id == 2 })
        assertEquals(0, after.first { it.id == 1 }.x)
        assertEquals(1, after.first { it.id == 3 }.x)
        assertValid(after)
        assertEquals(after, GridPlacement.update(after, pinned.copy(x = 0)))
    }
    @Test(expected = IllegalArgumentException::class) fun narrowingCannotSilentlyMovePinnedPosition() {
        GridPlacement.reflow(listOf(GridItem(1, App(), 1, x = 2, positionPinned = true)), 2)
    }
    @Test fun legacyHalfCellsMigrateOnceToWholeGridCells() {
        val old = listOf(GridItem(1, App(), 2, x = 1), GridItem(2, App(), 4, height = 2, x = 2), GridItem(3, App(), 1, x = 5, y = 1))
        val migrated = tgo1014.gridlauncher.data.migrateWholeCells(old)
        assertEquals(setOf(1,2,3), migrated.map { it.id }.toSet()); assertValid(migrated)
        assertEquals(1, migrated.first { it.id == 1 }.width)
        assertEquals(2, migrated.first { it.id == 2 }.width)
    }
    private fun assertValid(grid: List<GridItem>) {
        grid.forEachIndexed { index, tile ->
            assertTrue(tile.x >= 0 && tile.x + tile.width <= 3 && tile.y >= 0)
            grid.drop(index + 1).forEach { assertFalse(GridPlacement.overlaps(tile, it)) }
        }
    }
}
