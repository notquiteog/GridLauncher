package tgo1014.gridlauncher.domain.usecases

import kotlinx.coroutines.flow.first
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPlacement
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import javax.inject.Inject

class AddToGridUseCase @Inject constructor(private val appsManager: AppsManager) {
    suspend operator fun invoke(app: App, columns: Int = 3) = runCatching {
        val grid = appsManager.homeGridFlow.first()
        val item = GridItem(id = (grid.maxOfOrNull { it.id } ?: -1) + 1, app = app, width = 1)
        appsManager.setGrid(grid + GridPlacement.place(item, grid, columns))
    }
}
