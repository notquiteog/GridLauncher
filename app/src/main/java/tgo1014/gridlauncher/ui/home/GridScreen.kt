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
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.wewox.lazytable.rememberLazyTableState
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.live.QuietHours
import tgo1014.gridlauncher.ui.composables.Hotseat
import tgo1014.gridlauncher.ui.composables.JumpRail
import tgo1014.gridlauncher.ui.composables.StartHeader
import tgo1014.gridlauncher.ui.composables.TileLayout
import tgo1014.gridlauncher.ui.composables.jumpLetterOf
import tgo1014.gridlauncher.ui.composables.sheets.TileSettingsBottomSheet
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.TileEvent
import tgo1014.gridlauncher.ui.theme.LocalGlass

@Composable
fun GridScreenScreen(
    state: HomeState, hazeState: HazeState = remember { HazeState() },
    onItemClicked: (GridItem) -> Unit = {}, onItemDropped: (GridItem, Int, Int) -> Unit = { _, _, _ -> },
    onItemLongClicked: (GridItem) -> Unit = {}, onFooterClicked: () -> Unit = {},
    onTileEvent: (TileEvent) -> Unit = {}, onEditLayout: (Boolean) -> Unit = {},
    onSettingsEvent: (SettingsEvent) -> Unit = {}, showAllAppsLink: Boolean = true,
    onOpenApp: (App) -> Unit = {},
    onHandoffFocusHandled: () -> Unit = {},
) {
    val glass = LocalGlass.current
    BackHandler(enabled = state.isEditingLayout && state.itemBeingEdited == null) { onEditLayout(false) }
    var focusedId by remember(state.profile) { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.handoffFocus) { if (state.handoffFocus != null) { focusedId = state.handoffFocus; onHandoffFocusHandled() } }
    val focused = state.grid.firstOrNull { it.id == focusedId }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val key = when (event.key) {
            Key.DirectionUp -> GridKeyboard.Key.Up
            Key.DirectionDown -> GridKeyboard.Key.Down
            Key.DirectionLeft -> GridKeyboard.Key.Left
            Key.DirectionRight, Key.Tab -> GridKeyboard.Key.Right
            else -> null
        }
        val moved = key?.let { GridKeyboard.move(state.grid, focusedId, it) }
        return when {
            // Only consume the key if the cursor actually moved, so focus can leave the grid.
            moved != null && moved != focusedId -> { focusedId = moved; true }
            event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar -> {
                focused?.let { onItemClicked(it) }; true
            }
            else -> false
        }
    }
    // Start fades back in after an app closes, the way the stock launchers do. A plain state flag
    // rather than a suspending Animatable, so nothing has to block the main thread from a lifecycle
    // callback.
    var inForeground by remember { mutableStateOf(true) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> inForeground = false
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> inForeground = true
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val returnAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (inForeground) 1f else 0f,
        animationSpec = if (glass.motion) androidx.compose.animation.core.tween(200) else androidx.compose.animation.core.snap(),
        label = "Return")
    val tableState = rememberLazyTableState()
    // The first tile of every letter, in the grid's own visual order rather than its list order.
    val anchors = remember(state.grid) {
        LinkedHashMap<Char, GridItem>().apply {
            state.grid.sortedBy { it.y * 1000 + it.x }.forEach { putIfAbsent(jumpLetterOf(it.app.name), it) }
        }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().graphicsLayer { alpha = returnAlpha }
        .focusRequester(focusRequester).focusable().onPreviewKeyEvent(::handleKey)) {
        Box(Modifier.fillMaxWidth().animateContentSize(if (glass.motion) spring(dampingRatio = .9f, stiffness = 350f) else snap())) {
            if (state.tileSettings.oneHanded) Spacer(Modifier.height(100.dp))
            StartHeader(visible = state.tileSettings.showStartHeader && !state.tileSettings.oneHanded)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // The rail only earns its width when every cell stays big enough to hit. One-handed mode
            // pushes Start down into thumb reach, so a rail pinned to the top edge has no place there.
            val rail = state.tileSettings.semiPanoramic && !state.tileSettings.oneHanded &&
                maxWidth >= (RAIL_WIDTH_DP + state.tileSettings.gridColumns * MIN_TILE_DP).dp
            Row(Modifier.fillMaxWidth()) {
                if (rail) JumpRail(anchors, onJump = { tile ->
                    if (glass.motion) tableState.animateToCell(tile.x, tile.y, tile.width, tile.height)
                    else tableState.snapToCell(tile.x, tile.y, tile.width, tile.height)
                }, modifier = Modifier.width(RAIL_WIDTH_DP.dp).fillMaxHeight())
                Column(Modifier.weight(1f)) {
                    TileLayout(grid = state.grid, columns = state.tileSettings.gridColumns, tileSettings = state.tileSettings,
                        hazeState = hazeState, itemBeingEdited = state.itemBeingEdited, editingLayout = state.isEditingLayout,
                        onItemLongClicked = onItemLongClicked, onItemClicked = onItemClicked, onItemDropped = onItemDropped,
                        focusedId = focusedId, onFocusTile = { focusedId = it.id }, profile = state.profile,
                        contentPadding = if (state.itemBeingEdited == null) PaddingValues(0.dp) else PaddingValues(bottom = 200.dp),
                        state = tableState, modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Hotseat(state.appList, state.tileSettings.hotseat, onOpenApp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (QuietHours.active(state.tileSettings)) {
                    Text("Quiet", color = glass.accent, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
                if (showAllAppsLink) TextButton(onClick = onFooterClicked, modifier = Modifier.weight(1f)) {
                    Text("All apps", color = glass.ink, fontSize = 14.sp)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = glass.ink, modifier = Modifier.size(18.dp))
                } else Spacer(Modifier.weight(1f))
            }
        }
    }
    TileSettingsBottomSheet(isShowing = state.isEditingLayout && state.itemBeingEdited != null,
        item = state.itemBeingEdited, onTileEvent = onTileEvent,
        )
}

private const val RAIL_WIDTH_DP = 36
/** The narrowest a cell may get before the rail would cost more than the letters are worth. */
private const val MIN_TILE_DP = 76
