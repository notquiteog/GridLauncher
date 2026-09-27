package tgo1014.gridlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import tgo1014.gridlauncher.ui.composables.TileLayout
import tgo1014.gridlauncher.ui.composables.sheets.TileSettingsBottomSheet
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.TileEvent
import tgo1014.gridlauncher.ui.theme.LocalGlass

@Composable
fun GridScreenScreen(
    state: HomeState, hazeState: HazeState = remember { HazeState() },
    onItemClicked: (GridItem) -> Unit = {}, onItemDropped: (GridItem, Int, Int) -> Unit = { _, _, _ -> },
    onItemLongClicked: (GridItem) -> Unit = {}, onFooterClicked: () -> Unit = {},
    onTileEvent: (TileEvent) -> Unit = {}, onEditLayout: (Boolean) -> Unit = {},
) {
    val glass = LocalGlass.current
    BackHandler(enabled = state.isEditingLayout && state.itemBeingEdited == null) { onEditLayout(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.fillMaxWidth().animateContentSize(if (glass.motion) spring(dampingRatio = .9f, stiffness = 350f) else snap())) {
            if (state.tileSettings.oneHanded) Spacer(Modifier.height(100.dp))
        }
        TileLayout(grid = state.grid, columns = state.tileSettings.gridColumns, tileSettings = state.tileSettings,
            hazeState = hazeState, itemBeingEdited = state.itemBeingEdited, editingLayout = state.isEditingLayout,
            onItemLongClicked = onItemLongClicked, onItemClicked = onItemClicked, onItemDropped = onItemDropped,
            contentPadding = if (state.itemBeingEdited == null) PaddingValues(0.dp) else PaddingValues(bottom = 200.dp),
            modifier = Modifier.fillMaxWidth().weight(1f))
        TextButton(onClick = onFooterClicked, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
            Text("All apps", color = glass.ink, fontSize = 14.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = glass.ink, modifier = Modifier.size(18.dp))
        }
    }
    TileSettingsBottomSheet(isShowing = state.isEditingLayout && state.itemBeingEdited != null,
        item = state.itemBeingEdited, onTileEvent = onTileEvent)
}
