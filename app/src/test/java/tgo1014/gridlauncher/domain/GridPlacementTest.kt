package tgo1014.gridlauncher.domain

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import kotlin.random.Random

class GridPlacementTest {
    @Test fun resizingAtRightEdgeKeepsEveryTileInBoundsAndDisplacesNeighbors() {
        val tiles = (0..11).map { GridItem(it, App("App $it"), 2, x = (it % 3) * 2, y = (it / 3) * 2) }
        val result = GridPlacement.update(tiles, tiles[2].copy(width = 4))
        assertEquals(12, result.size)
        assertValid(result)
    }
    @Test fun movingToOccupiedCellPreservesRequestedLocation() {
        val a = GridItem(0, App(), 2)
        val b = GridItem(1, App(), 2, x = 2)
        val result = GridPlacement.update(listOf(a, b), a.copy(x = 2))
        assertEquals(2, result.first { it.id == 0 }.x)
        assertValid(result)
    }
    @Test fun randomizedResizeAndMoveNeverLosesOrOverlapsTiles() {
        val random = Random(17)
        var grid = emptyList<GridItem>()
        repeat(60) { id -> grid = grid + GridPlacement.place(GridItem(id, App(), 2), grid) }
        repeat(300) {
            val tile = grid.random(random)
            grid = GridPlacement.update(grid, tile.copy(width = listOf(1, 2, 4).random(random), x = random.nextInt(-3, 10), y = random.nextInt(-3, 30)))
            assertEquals(60, grid.map { it.id }.toSet().size)
            assertValid(grid)
        }
    }
    private fun assertValid(grid: List<GridItem>) {
        grid.forEachIndexed { index, tile ->
            assertTrue(tile.x >= 0 && tile.x + tile.width <= 6 && tile.y >= 0)
            grid.drop(index + 1).forEach { assertFalse(GridPlacement.overlaps(tile, it)) }
        }
    }
}
