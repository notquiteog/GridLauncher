package tgo1014.gridlauncher.ui.composables

import androidx.activity.compose.LocalActivity
import tgo1014.gridlauncher.ui.theme.LocalGlass
import tgo1014.gridlauncher.ui.theme.glassSurface
import tgo1014.gridlauncher.ui.theme.TileTurn
import tgo1014.gridlauncher.live.ContactTiles
import tgo1014.gridlauncher.live.PinnedContact
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.clickable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

import android.Manifest
import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.live.NotificationTiles
import tgo1014.gridlauncher.ui.MainActivity
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridTile(
    item: GridItem, hazeState: HazeState? = null, onFolder: ((GridItem) -> Unit)? = null, modifier: Modifier = Modifier, tileSettings: TileSettings = TileSettings(),
    isEditMode: Boolean = false, onItemDropped: (GridItem, Float, Float) -> Unit = { _, _, _ -> }, onItemClicked: (GridItem) -> Unit = {}, onItemLongClicked: (GridItem) -> Unit = {},
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    var showNativeActions by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var people by remember(item) { mutableStateOf(item.contacts.ifEmpty { listOfNotNull(item.contact) }) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    val matching = if (tileSettings.liveTilesEnabled) notifications.filter { n ->
        n.packageName == item.app.packageName || item.children.any { it.packageName == n.packageName } || people.any { it.matches(n) }
    } else emptyList()
    var detail by remember(item.app.packageName) { mutableStateOf<Pair<String, String>?>(null) }
    var page by remember { mutableIntStateOf(0) }
    val locked = glass.locked
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    var showFolder by remember { mutableStateOf(false) }
    val builtIn = item.app.packageName.startsWith("grid://")
    LaunchedEffect(item.app.packageName, tileSettings.liveTilesEnabled, tileSettings.isTileFlipEnabled, glass.motion, permissionRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (item.app.packageName == BuiltInTiles.PEOPLE) people = withContext(Dispatchers.IO) { ContactTiles.favorites(context) }
                detail = if (tileSettings.liveTilesEnabled) withContext(Dispatchers.IO) { BuiltInTiles.detail(context, item.app.packageName) } else null
                if (tileSettings.isTileFlipEnabled && glass.motion) page++
                delay(8_000)
            }
        }
    }
    fun open() {
        when {
            item.children.isNotEmpty() -> if (onFolder != null) onFolder(item) else { showFolder = true }
            people.isNotEmpty() -> showPeople = true
            item.app.packageName == BuiltInTiles.CALENDAR && !BuiltInTiles.granted(context, Manifest.permission.READ_CALENDAR) -> permission.launch(Manifest.permission.READ_CALENDAR)
            item.app.packageName == BuiltInTiles.PEOPLE && !BuiltInTiles.granted(context, Manifest.permission.READ_CONTACTS) -> permission.launch(Manifest.permission.READ_CONTACTS)
            item.photoUris.isNotEmpty() -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(item.photoUris[page.mod(item.photoUris.size)]), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            else -> onItemClicked(item)
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && glass.motion) .965f else 1f, if (glass.motion) spring(dampingRatio = .75f, stiffness = 650f) else snap(), label = "Tile press")
    BoxWithConstraints(modifier = modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale }.glassSurface(hazeState)
        .semantics { contentDescription = item.app.name + if (matching.isNotEmpty()) ", ${matching.size} notifications" else ""; customActions = listOf(CustomAccessibilityAction(if (isEditMode) "Edit tile" else "App shortcuts") { if (isEditMode) onItemLongClicked(item) else showNativeActions = true; true }, CustomAccessibilityAction("Preview notifications") { showPreview = true; true }) }
        .combinedClickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = { if (isEditMode) onItemClicked(item) else open() }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (isEditMode) onItemLongClicked(item) else showNativeActions = true })) {
        if (item.widgetId >= 0) {
            val activity = LocalActivity.current as? MainActivity
            val manager = AppWidgetManager.getInstance(context)
            val info = remember(item.widgetId) { manager.getAppWidgetInfo(item.widgetId) }
            if (activity != null && info != null) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = maxWidth.value.toInt(); val h = maxHeight.value.toInt()
                    AndroidView(factory = { activity.widgetHost.createView(it, item.widgetId, info) },
                        update = { it.updateAppWidgetSize(null, w, h, w, h) }, modifier = Modifier.fillMaxSize())
                }
                TextButton(onClick = { if (isEditMode) onItemLongClicked(item) else showNativeActions = true }, modifier = Modifier.align(Alignment.TopEnd)) { Text("Actions", color = glass.ink) }
            } else Text("Widget unavailable\nRemove in Edit layout", color = glass.ink, modifier = Modifier.padding(12.dp))
        } else {
            if (item.photoUris.isNotEmpty()) {
                Crossfade(targetState = if (tileSettings.liveTilesEnabled) page.mod(item.photoUris.size) else 0, label = "Photo tile") { index ->
                    AsyncImage(item.photoUris[index], Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)))
            }
            val expanded = maxWidth >= 86.dp && maxHeight >= 86.dp
            val preview = matching.getOrNull(page.mod(matching.size.coerceAtLeast(1)))
            val art by produceState<android.graphics.Bitmap?>(null, preview?.key, preview?.artworkIcon) {
                value = withContext(Dispatchers.IO) { preview?.artwork ?: runCatching { preview?.artworkIcon?.loadDrawable(context)?.toBitmap(192,192) }.getOrNull() }
            }
            if (!locked && tileSettings.showNotificationText && preview?.packageName !in tileSettings.hiddenPreviewApps && art != null) {
                Image(art!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(if (tileSettings.darkTheme) Color.Black.copy(alpha = .70f) else Color.White.copy(alpha = .85f)))
            }
            val live = if (!locked && tileSettings.showNotificationText && preview?.packageName !in tileSettings.hiddenPreviewApps && preview?.title?.isNotBlank() == true)
                preview.title to preview.text else detail
            if (expanded && live != null && people.isEmpty() && item.children.isEmpty()) {
                TileTurn(key = live, modifier = Modifier.fillMaxSize().padding(12.dp).padding(bottom = if (preview?.actions?.isNotEmpty() == true && item.width >= 2) 60.dp else 24.dp)) {
                    val content = live
                    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        Text(content.first, color = glass.ink, fontSize = if (item.app.packageName == BuiltInTiles.CLOCK || item.app.packageName == BuiltInTiles.BATTERY) 32.sp else 20.sp,
                            fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(content.second, color = glass.ink, fontSize = 13.sp, maxLines = if (item.width >= 2) 3 else 2, overflow = TextOverflow.Ellipsis)
                        if (preview != null && live == (preview.title to preview.text)) {
                            if (preview.semantic > 0) Text(semanticLabel(preview.semantic), color = glass.ink, fontSize = 11.sp)
                            LiveProgress(preview, Modifier.padding(top = 5.dp))
                        }
                    }
                }
            } else if (people.isNotEmpty()) {
                PeopleMosaic(people, page, Modifier.fillMaxSize().padding(bottom = 24.dp))
            } else if (item.children.isNotEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(14.dp)) {
                    item.children.take(4).chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { app -> Box {
                            AsyncImage(app.icon.iconFile, Modifier.size(32.dp))
                            val count = matching.count { it.packageName == app.packageName }
                            if (count > 0) Text(count.toString(), color = glass.ink, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).background(glass.accent))
                        } }
                    } }
                }
            } else if (item.photoUris.isEmpty()) {
                if (builtIn) Text(when (item.app.packageName) { BuiltInTiles.CLOCK -> "◷"; BuiltInTiles.CALENDAR -> java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH).toString(); BuiltInTiles.PEOPLE -> "● ●"; BuiltInTiles.BATTERY -> "▰"; else -> "▦" },
                    color = glass.ink, fontSize = if (expanded) 38.sp else 22.sp, modifier = Modifier.align(Alignment.Center))
                else AsyncImage(item.app.icon.iconFile, Modifier.align(Alignment.Center).fillMaxSize(.48f))
            }
            if (expanded && !locked && tileSettings.showNotificationText && preview != null && preview.packageName !in tileSettings.hiddenPreviewApps && maxWidth >= 180.dp && preview.actions.isNotEmpty()) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp)) { NotificationActions(preview, compact = true) }
            }
            if (!tileSettings.isAppLabelsHidden) Text(item.app.name, color = glass.ink, fontSize = if (expanded) 13.sp else 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(if (expanded) 10.dp else 3.dp).padding(end = if (matching.isNotEmpty()) 26.dp else 0.dp))
            if (matching.isNotEmpty()) Text(if (matching.size > 99) "99+" else matching.size.toString(), color = glass.ink, fontSize = if (expanded) 24.sp else 16.sp,
                modifier = Modifier.align(Alignment.BottomEnd).sizeIn(minWidth = 44.dp, minHeight = 44.dp).clickable { showPreview = true }.padding(6.dp))
        }
        if (isEditMode) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .12f)).clickable { onItemClicked(item) })
            if (item.positionPinned) Text("Pinned", color = glass.ink, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = .6f)).padding(3.dp))
        }
        NativeTileActions(item, showNativeActions, { showNativeActions = false }, { open() })
    }
    if (showPreview) NotificationPreview(item.app.name, (item.children.map { it.packageName } + item.app.packageName + matching.map { it.packageName }).toSet(), { showPreview = false })
    if (showPeople) PeoplePreview(people, { showPeople = false })
    if (showFolder) AlertDialog(onDismissRequest = { showFolder = false }, title = { Text(item.app.name) },
        text = { LazyColumn { items(item.children) { app -> TextButton(onClick = {
            showFolder = false
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) runCatching { context.startActivity(intent) }
        }) { AsyncImage(app.icon.iconFile, Modifier.size(36.dp)); Spacer(Modifier.width(12.dp)); Text(app.name) } } } },
        confirmButton = { TextButton(onClick = { showFolder = false }) { Text("Close") } })
}
