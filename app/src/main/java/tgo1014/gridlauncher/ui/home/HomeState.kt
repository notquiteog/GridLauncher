package tgo1014.gridlauncher.ui.home

import tgo1014.gridlauncher.data.builtinProfileNames
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem

data class HomeState(
    val profile: String = "Personal",
    val layouts: List<String> = builtinProfileNames,
    val appList: List<App> = emptyList(),
    val grid: List<GridItem> = emptyList(),
    val goToHome: Boolean = false,
    val filterString: String = "",
    val isEditingLayout: Boolean = false,
    val itemBeingEdited: GridItem? = null,
    val tileSettings: TileSettings = TileSettings(),
    val isSettingsSheetShowing: Boolean = false,
    /** Set by a handed-off layout so the receiving device can put the cursor on the same tile. */
    val handoffFocus: Int? = null,
)