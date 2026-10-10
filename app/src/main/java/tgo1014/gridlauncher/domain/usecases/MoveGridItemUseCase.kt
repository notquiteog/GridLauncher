package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.first
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPlacement
import tgo1014.gridlauncher.domain.models.Direction
import javax.inject.Inject

/** Moves a tile one whole cell, which is also what a drag resolves to. */
class MoveGridItemUseCase @Inject constructor(
    private val appsManager: AppsManager,
) {
    suspend operator fun invoke(itemId: Int, direction: Direction, columns: Int = 3) = runCatching {
        val grid = appsManager.homeGridFlow.first()
        val item = grid.firstOrNull { it.id == itemId } ?: return@runCatching
        val moved = when (direction) {
            Direction.Left -> item.copy(x = item.x - 1)
            Direction.Right -> item.copy(x = item.x + 1)
            Direction.Up -> item.copy(y = item.y - 1)
            Direction.Down -> item.copy(y = item.y + 1)
        }
        appsManager.setGrid(GridPlacement.update(grid, moved, columns))
    }
}
