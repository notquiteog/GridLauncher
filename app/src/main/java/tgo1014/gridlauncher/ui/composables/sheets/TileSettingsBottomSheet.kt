package tgo1014.gridlauncher.ui.composables.sheets

import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tgo1014.gridlauncher.domain.models.Direction
import tgo1014.gridlauncher.live.NotificationTiles
import tgo1014.gridlauncher.ui.composables.NotificationActions
import tgo1014.gridlauncher.ui.composables.NotificationPreview
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.TileEvent
import tgo1014.gridlauncher.ui.theme.LocalGlass

/** Long press opens actions. Movement is an explicit, separate choice; pinned positions are anchors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileSettingsBottomSheet(isShowing: Boolean, item: GridItem? = null, onOpen: (GridItem) -> Unit = {}, onTileEvent: (TileEvent) -> Unit = {}) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<GridItem?>(null) }
    var moving by remember(item?.id) { mutableStateOf(false) }
    if (isShowing && item != null) {
        ModalBottomSheet(onDismissRequest = { onTileEvent(TileEvent.OnTileSettingsSheetDismissed) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.app.name, style = MaterialTheme.typography.headlineLarge)
                Text("Edit tile", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pin position", modifier = Modifier.padding(top = 12.dp))
                    Switch(checked = item.positionPinned, onCheckedChange = { onTileEvent(TileEvent.OnTogglePositionPin) })
                }
                Text(if (item.positionPinned) "This tile stays in its cell when others are added or removed." else "Unpinned tiles fill available gaps when a tile is removed.", style = MaterialTheme.typography.bodySmall)
                Text("Size · ${item.width}×${item.height}", style = MaterialTheme.typography.titleMedium)
                listOf(listOf(1 to 1, 1 to 2, 2 to 1), listOf(2 to 2, 2 to 3, 3 to 2), listOf(3 to 3, 4 to 2, 4 to 4)).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.filter { it.first <= glass.settings.gridColumns }.forEach { (w,h) ->
                        FilterChip(selected = item.width == w && item.height == h, onClick = { onTileEvent(TileEvent.OnCellSize(w,h)) }, label = { Text("${w}×${h}") })
                    }
                } }
                TextButton(enabled = !item.positionPinned, onClick = { moving = !moving }) { Text(if (moving) "Done moving" else "Move tile") }
                if (moving && !item.positionPinned) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Left" to Direction.Left, "Up" to Direction.Up, "Down" to Direction.Down, "Right" to Direction.Right).forEach { (label,direction) ->
                        OutlinedButton(onClick = { onTileEvent(TileEvent.OnTileMoved(direction)) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(label) }
                    }
                }
                TextButton(onClick = { onTileEvent(TileEvent.OnRemoveClicked) }) { Text("Remove") }
            }
        }
    }
    preview?.let { tile -> NotificationPreview(tile.app.name, (listOf(tile.app.packageName) + tile.children.map { it.packageName }).toSet(), { preview = null }) }
}
