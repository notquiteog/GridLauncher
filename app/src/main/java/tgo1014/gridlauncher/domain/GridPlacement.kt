package tgo1014.gridlauncher.domain

import tgo1014.gridlauncher.app.Constants
import tgo1014.gridlauncher.ui.models.GridItem

/** Integer cells only. Position-pinned tiles are anchors for every packing operation. */
object GridPlacement {
    fun overlaps(a: GridItem, b: GridItem) = a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y
    fun place(item: GridItem, others: List<GridItem>, columns: Int = Constants.gridColumns): GridItem {
        val width = item.width.coerceIn(1, columns)
        val bounded = item.copy(width = width, height = item.height.coerceIn(1, 4), x = item.x.coerceIn(0, columns - width), y = item.y.coerceAtLeast(0))
        if (others.none { overlaps(bounded, it) }) return bounded
        return firstFree(bounded, others, columns)
    }
    private fun firstFree(item: GridItem, others: List<GridItem>, columns: Int): GridItem {
        val bottom = others.maxOfOrNull { it.y + it.height } ?: 0
        for (y in 0..bottom) for (x in 0..columns - item.width) {
            val candidate = item.copy(x = x, y = y)
            if (others.none { overlaps(candidate, it) }) return candidate
        }
        return item.copy(x = 0, y = bottom)
    }
    private fun anchors(grid: List<GridItem>, columns: Int): MutableList<GridItem> {
        val anchors = grid.filter { it.positionPinned }.toMutableList()
        require(anchors.all { it.x >= 0 && it.y >= 0 && it.width in 1..columns && it.height in 1..4 && it.x + it.width <= columns }) { "Unpin tile positions before reducing the grid width" }
        anchors.forEachIndexed { i, a -> require(anchors.drop(i + 1).none { overlaps(a, it) }) { "Pinned tiles cannot overlap" } }
        return anchors
    }
    fun reflow(grid: List<GridItem>, columns: Int): List<GridItem> {
        val result = anchors(grid, columns)
        grid.filterNot { it.positionPinned }.sortedWith(compareBy({ it.y }, { it.x })).forEach { result += place(it, result, columns) }
        return result.sortedWith(compareBy({ it.y }, { it.x }))
    }
    fun compact(grid: List<GridItem>, columns: Int = Constants.gridColumns): List<GridItem> {
        val result = anchors(grid, columns)
        grid.filterNot { it.positionPinned }.sortedWith(compareBy({ it.y }, { it.x })).forEach {
            result += firstFree(it.copy(width = it.width.coerceIn(1, columns), height = it.height.coerceIn(1, 4)), result, columns)
        }
        return result.sortedWith(compareBy({ it.y }, { it.x }))
    }
    fun update(grid: List<GridItem>, item: GridItem, columns: Int = Constants.gridColumns): List<GridItem> {
        val original = grid.firstOrNull { it.id == item.id }
        if (original?.positionPinned == true && (item.x != original.x || item.y != original.y)) return grid
        val pinned = anchors(grid.filterNot { it.id == item.id }, columns)
        if (original?.positionPinned == true && (item.x + item.width > columns || pinned.any { overlaps(it, item) })) return grid
        val requested = if (item.positionPinned) item else place(item, pinned, columns)
        val result = (pinned + requested).toMutableList()
        grid.filterNot { it.id == item.id || it.positionPinned }.sortedWith(compareBy({ it.y }, { it.x })).forEach { result += place(it, result, columns) }
        return result.sortedWith(compareBy({ it.y }, { it.x }))
    }
}
