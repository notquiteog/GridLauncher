package tgo1014.gridlauncher.ui.models

import tgo1014.gridlauncher.domain.models.Direction

sealed class TileEvent {
    data class OnCellSize(val width: Int, val height: Int) : TileEvent()
    data object OnTogglePositionPin : TileEvent()
    data class OnTileMoved(val direction: Direction) : TileEvent()
    data class OnTileColorChanged(val color: Long?) : TileEvent()
    data object OnRemoveClicked : TileEvent()
    data object OnTileSettingsSheetDismissed : TileEvent()
}