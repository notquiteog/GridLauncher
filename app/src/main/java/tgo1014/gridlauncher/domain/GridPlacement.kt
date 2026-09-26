package tgo1014.gridlauncher.domain

import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.ui.models.GridItem

/** All mutations share this packing rule: within the grid, no overlapping rectangles. */
object GridPlacement {
    fun overlaps(a: GridItem, b: GridItem) = a.x < b.x + b.width && a.x + a.width > b.x &&
        a.y < b.y + b.height && a.y + a.height > b.y

    fun place(item: GridItem, others: List<GridItem>, columns: Int = Constants.gridColumns): GridItem {
        val bounded = item.copy(width = item.width.coerceIn(1, columns), height = item.height.coerceIn(1, 4),
            x = item.x.coerceIn(0, columns - item.width.coerceIn(1, columns)), y = item.y.coerceAtLeast(0))
        if (others.none { overlaps(bounded, it) }) return bounded
        val bottom = others.maxOfOrNull { it.y + it.height } ?: 0
        for (y in 0..bottom) for (x in 0..columns - bounded.width) {
            val candidate = bounded.copy(x = x, y = y)
            if (others.none { overlaps(candidate, it) }) return candidate
        }
        return bounded.copy(x = 0, y = bottom)
    }

    /** Preserve the moved tile's requested location and relocate any displaced tiles. */
    fun update(grid: List<GridItem>, item: GridItem): List<GridItem> {
        val result = mutableListOf(place(item, emptyList()))
        grid.filterNot { it.id == item.id }.sortedWith(compareBy({ it.y }, { it.x })).forEach {
            result += place(it, result)
        }
        return result.sortedWith(compareBy({ it.y }, { it.x }))
    }
}
