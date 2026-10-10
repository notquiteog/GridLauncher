package tgo1014.gridlauncher.ui.home

import androidx.compose.ui.focus.FocusDirection
import tgo1014.gridlauncher.ui.models.GridItem
import kotlin.math.abs

/**
 * Windows Phone had real keyboard support. This picks the tile a key press should land on, by
 * choosing the nearest candidate in the requested direction, with a bias towards the row or
 * column the cursor is already on.
 */
object GridKeyboard {
    enum class Key { Up, Down, Left, Right }

    fun move(grid: List<GridItem>, currentId: Int?, key: Key): Int? {
        if (grid.isEmpty()) return null
        val current = grid.firstOrNull { it.id == currentId } ?: return grid.minByOrNull { it.y * 100 + it.x }?.id
        val centreX = current.x + current.width / 2.0
        val centreY = current.y + current.height / 2.0
        val candidates = grid.filter { it.id != current.id }
        fun score(candidate: GridItem): Double {
            val dx = (candidate.x + candidate.width / 2.0) - centreX
            val dy = (candidate.y + candidate.height / 2.0) - centreY
            return when (key) {
                // Distance along the axis of travel, plus a penalty for drifting off the line.
                Key.Right -> if (dx <= 0) Double.MAX_VALUE else abs(dx) * 2 + abs(dy)
                Key.Left -> if (dx >= 0) Double.MAX_VALUE else abs(dx) * 2 + abs(dy)
                Key.Down -> if (dy <= 0) Double.MAX_VALUE else abs(dy) * 2 + abs(dx)
                Key.Up -> if (dy >= 0) Double.MAX_VALUE else abs(dy) * 2 + abs(dx)
            }
        }
        return candidates.minByOrNull { score(it) }?.takeIf { score(it) < Double.MAX_VALUE }?.id ?: current.id
    }
}
