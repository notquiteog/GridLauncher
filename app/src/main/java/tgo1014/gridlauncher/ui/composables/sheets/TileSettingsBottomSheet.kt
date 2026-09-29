package tgo1014.gridlauncher.ui.composables.sheets

import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tgo1014.gridlauncher.domain.FolderEdit
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.Direction
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.live.NotificationTiles
import tgo1014.gridlauncher.ui.composables.NotificationActions
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.TileEvent
import tgo1014.gridlauncher.ui.theme.LocalGlass

/** Long press opens actions. Movement is an explicit, separate choice; pinned positions are anchors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileSettingsBottomSheet(isShowing: Boolean, item: GridItem? = null, onTileEvent: (TileEvent) -> Unit = {},
    accentColor: Long = 0xFF0078D7, folderApps: List<App> = emptyList(), onFolderChanged: (GridItem) -> Unit = {}) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    var moving by remember(item?.id) { mutableStateOf(false) }
    if (isShowing && item != null) {
        ModalBottomSheet(onDismissRequest = { onTileEvent(TileEvent.OnTileSettingsSheetDismissed) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.app.name, style = MaterialTheme.typography.headlineLarge)
                Text("Edit tile", style = MaterialTheme.typography.labelLarge)
                if (!item.isGroup) {
                    Text("Tile color", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val options = listOf<Long?>(null, accentColor) +
                            listOf(0xFF0078D7, 0xFF008A00, 0xFFB4009E, 0xFFD24726, 0xFF643EBF, 0xFF006D77, 0xFF1C1C1C).distinct()
                        options.forEach { color ->
                            val selected = item.tileColor == color
                            Box(Modifier.size(36.dp)
                                .background(if (color == null) Color.Transparent else Color(color))
                                .border(1.dp, if (selected) Color.White else Color.White.copy(alpha = .35f), RectangleShape)
                                .clickable { onTileEvent(TileEvent.OnTileColorChanged(color)) }
                                .semantics { contentDescription = if (color == null) "Tile color Auto" else "Tile color ${"%06X".format(color and 0xFFFFFF)}" },
                                contentAlignment = Alignment.Center) {
                                if (color == null) Text("Auto", color = glass.ink, fontSize = 9.sp, textAlign = TextAlign.Center)
                                if (selected) Text("✓", color = if (color == null || Color(color).luminance() > .5f) Color.Black else Color.White)
                            }
                        }
                    }
                    Text("Auto uses the dominant color of the app's own icon.", style = MaterialTheme.typography.bodySmall)
                }
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
                if (item.childCount > 0 || item.app.packageName == BuiltInTiles.FOLDER) {
                    Text("Inside this folder", style = MaterialTheme.typography.titleMedium)
                    var editing by remember(item.id) { mutableStateOf(false) }
                    val inside = (item.children + item.childFolders.map { it.app }).joinToString(", ") { it.name }
                    Text(if (inside.isBlank()) "Empty" else inside, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (editing) {
                        // A plain column inside the sheet's own scroll, so every candidate keeps its
                        // semantics: a lazy list would only compose the handful of rows on screen.
                        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            folderApps.forEach { app ->
                                val inFolder = FolderEdit.contains(item, app)
                                Row(Modifier.fillMaxWidth().clickable { onFolderChanged(FolderEdit.toggled(item, app)) }
                                    .semantics { contentDescription = (if (inFolder) "Remove " else "Add ") + app.name + " to folder" },
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(inFolder, null); Text(app.name, Modifier.weight(1f))
                                }
                            }
                        }
                        TextButton(onClick = { editing = false }) { Text("Done") }
                    } else TextButton(onClick = { editing = true }) { Text("Add or remove apps") }
                }
                TextButton(onClick = { onTileEvent(TileEvent.OnRemoveClicked) }) { Text("Remove") }
            }
        }
    }
}
