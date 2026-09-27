package tgo1014.gridlauncher.domain

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDateTime
import tgo1014.gridlauncher.data.scheduledProfile
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem

class LayoutProfilesTest {
    @Test fun allWidthsPreserveEveryTileWithoutOverlapThroughRepeatedChanges() {
        var grid = (0 until 60).map { GridItem(it, App("App $it"), if (it % 5 == 0) 4 else 2, 2, x = it % 3 * 2, y = it / 3 * 2) }
        for (across in listOf(3, 6, 2, 5, 4, 3)) {
            val columns = TileSettings(tilesAcross = across).gridColumns
            grid = GridPlacement.reflow(grid, columns)
            assertEquals(60, grid.map { it.id }.distinct().size)
            grid.forEach { tile -> assertTrue(tile.x >= 0 && tile.x + tile.width <= columns) }
            grid.forEachIndexed { i, a -> grid.drop(i + 1).forEach { b -> assertFalse(GridPlacement.overlaps(a,b)) } }
        }
        assertEquals(3, TileSettings().gridColumns)
    }
    @Test fun weekdayAndOvernightSchedulesRespectBoundariesAndWeekends() {
        fun at(s: String) = LocalDateTime.parse(s)
        assertEquals("Work", scheduledProfile(at("2026-09-25T09:00"), 9,17))
        assertEquals("Personal", scheduledProfile(at("2026-09-25T17:00"),9,17))
        assertEquals("Personal", scheduledProfile(at("2026-09-26T12:00"),9,17))
        assertEquals("Work", scheduledProfile(at("2026-09-26T02:00"),22,6))
        assertEquals("Personal", scheduledProfile(at("2026-09-26T22:00"),22,6))
        assertEquals("Personal", scheduledProfile(at("2026-09-25T12:00"),9,9))
    }
}
