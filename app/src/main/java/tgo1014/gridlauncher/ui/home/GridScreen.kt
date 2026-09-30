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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import tgo1014.gridlauncher.domain.FolderEdit
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.live.QuietHours
import tgo1014.gridlauncher.ui.composables.Hotseat
import tgo1014.gridlauncher.ui.composables.StartHeader
import tgo1014.gridlauncher.ui.composables.TileLayout
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
    onOpenApp: (App) -> Unit = {}, onFolderChanged: (GridItem) -> Unit = {},
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
    Column(Modifier.fillMaxSize().systemBarsPadding().graphicsLayer { alpha = returnAlpha }
        .focusRequester(focusRequester).focusable().onPreviewKeyEvent(::handleKey)) {
        Box(Modifier.fillMaxWidth().animateContentSize(if (glass.motion) spring(dampingRatio = .9f, stiffness = 350f) else snap())) {
            if (state.tileSettings.oneHanded) Spacer(Modifier.height(100.dp))
            StartHeader(visible = state.tileSettings.showStartHeader && !state.tileSettings.oneHanded)
        }
        TileLayout(grid = state.grid, columns = state.tileSettings.gridColumns, tileSettings = state.tileSettings,
            hazeState = hazeState, itemBeingEdited = state.itemBeingEdited, editingLayout = state.isEditingLayout,
            onItemLongClicked = onItemLongClicked, onItemClicked = onItemClicked, onItemDropped = onItemDropped,
            focusedId = focusedId, onFocusTile = { focusedId = it.id }, profile = state.profile,
            contentPadding = if (state.itemBeingEdited == null) PaddingValues(0.dp) else PaddingValues(bottom = 200.dp),
            modifier = Modifier.fillMaxWidth().weight(1f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Hotseat(state.appList, state.tileSettings.hotseat, onOpenApp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Windows Phone put the way into the app list at the bottom left of Start, and left
                // it as a bare arrow with nothing written beside it. So the name is carried by the
                // button's semantics rather than drawn next to it: a content description is what
                // TalkBack reads, and the text property is what anything matching this button by
                // name - the device tests among them - already looks for.
                if (showAllAppsLink) IconButton(
                    onClick = onFooterClicked,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "All apps"
                            text = AnnotatedString("All apps")
                        },
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = glass.ink, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                // Quiet is a state of the launcher, not a label for the arrow, so it sits at the end
                // of the row and keeps the leading corner to the one control Windows Phone put there.
                if (QuietHours.active(state.tileSettings)) {
                    Text("Quiet", color = glass.accent, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
    }
    val folderApps = remember(state.appList) { FolderEdit.candidates(state.appList) }
    TileSettingsBottomSheet(isShowing = state.isEditingLayout && state.itemBeingEdited != null,
        item = state.itemBeingEdited, onTileEvent = onTileEvent,
        folderApps = folderApps, onFolderChanged = onFolderChanged)
}
