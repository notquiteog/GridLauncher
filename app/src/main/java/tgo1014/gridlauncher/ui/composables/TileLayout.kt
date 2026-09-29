package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import tgo1014.gridlauncher.ui.theme.LocalGlass
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.HazeState
import eu.wewox.lazytable.*
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.glassSurface

@Composable
fun TileLayout(
    grid: List<GridItem>, modifier: Modifier = Modifier, hazeState: HazeState = remember { HazeState() }, columns: Int = 3,
    tileSettings: TileSettings = TileSettings(), itemBeingEdited: GridItem? = null,
    editingLayout: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onItemClicked: (GridItem) -> Unit = {}, onItemDropped: (GridItem, Int, Int) -> Unit = { _, _, _ -> },
    onItemLongClicked: (GridItem) -> Unit = {}, focusedId: Int? = null, onFocusTile: (GridItem) -> Unit = {}, profile: String = "Personal",
) = BoxWithConstraints(modifier) {
    // LazyTable requires at least one item; an empty profile is intentionally blank.
    if (grid.isEmpty()) return@BoxWithConstraints
    val motion = LocalGlass.current.motion
    val unit = maxWidth / columns
    val pixels = with(LocalDensity.current) { unit.toPx() }
    // A drill-down path, so a folder can hold another folder.
    var openPath by remember(profile) { mutableStateOf<List<GridItem>>(emptyList()) }
    val folder = openPath.lastOrNull()?.takeIf { it.childCount > 0 }
    val contents = folder?.let { it.children.map { child -> GridItem(-1_000_000 - child.packageName.hashCode(), child, 1) } + it.childFolders } ?: emptyList()
    // Choose a row boundary no tile crosses, preserving every saved grid coordinate.
    val boundary = remember(grid, folder?.id) {
        var edge = folder?.let { it.y + it.height } ?: Int.MAX_VALUE
        if (folder != null) repeat(grid.size) { edge = grid.filter { it.y < edge && it.y + it.height > edge }.maxOfOrNull { it.y + it.height } ?: edge }
        edge
    }
    val folderRows = if (folder == null) 0 else 1 + ((folder.childCount + columns - 1) / columns)
    LazyTable(scrollDirection = LazyTableScrollDirection.VERTICAL, contentPadding = contentPadding,
        dimensions = lazyTableDimensions({ unit }, { unit })) {
        items(items = grid, key = { it.id }, layoutInfo = { tile -> LazyTableItem(column = tile.x, row = tile.y + if (tile.y >= boundary) folderRows else 0, columnsCount = tile.width, rowsCount = tile.height) }) { tile ->
            val target = IntOffset((tile.x * pixels).roundToInt(), ((tile.y + if (tile.y >= boundary) folderRows else 0) * pixels).roundToInt())
            val size = IntSize((tile.width * pixels).roundToInt(), (tile.height * pixels).roundToInt())
            val position by animateIntOffsetAsState(target, if (motion) spring(dampingRatio = .86f, stiffness = 420f) else snap(), label = "Tile position")
            val dimensions by animateIntSizeAsState(size, if (motion) spring(dampingRatio = .9f, stiffness = 400f) else snap(), label = "Tile size")
            GridTile(tile, hazeState = hazeState, tileSettings = tileSettings, isEditMode = editingLayout,
                isKeyboardFocused = tile.id == focusedId,
                onFocus = { onFocusTile(tile) },
                onFolder = { openPath = openPath + it }, onItemClicked = onItemClicked,
                onItemLongClicked = onItemLongClicked, onItemDropped = { item, dx, dy -> onItemDropped(item, kotlin.math.round(dx / pixels).toInt(), kotlin.math.round(dy / pixels).toInt()) },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = (position.x - target.x).toFloat(); translationY = (position.y - target.y).toFloat()
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = dimensions.width.toFloat() / size.width.coerceAtLeast(1); scaleY = dimensions.height.toFloat() / size.height.coerceAtLeast(1)
                })
        }
        if (folder != null) {
            items(count = 1, layoutInfo = { LazyTableItem(column = 0, row = boundary, columnsCount = columns, rowsCount = folderRows) }) {
                Column(Modifier.fillMaxSize().glassSurface(hazeState)) {
                    Row(Modifier.fillMaxWidth().height(unit), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(folder.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
                        TextButton(onClick = { openPath = if (openPath.size > 1) openPath.dropLast(1) else emptyList() }) {
                            Text(if (openPath.size > 1) "Up" else "Close")
                        }
                    }
                    contents.chunked(columns).forEach { row -> Row(Modifier.height(unit)) {
                        row.forEach { child -> GridTile(child, hazeState = hazeState, tileSettings = tileSettings,
                            onItemClicked = onItemClicked,
                            onFolder = { nested -> openPath = openPath + (contents.firstOrNull { it.id == nested.id } ?: nested) },
                            modifier = Modifier.weight(1f).fillMaxHeight()) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    } }
                }
            }
        }
    }
}
