package tgo1014.gridlauncher.ui.composables

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
    item: GridItem, modifier: Modifier = Modifier, tileSettings: TileSettings = TileSettings(),
    isEditMode: Boolean = false, onItemDropped: (GridItem, Float, Float) -> Unit = { _, _, _ -> }, onItemClicked: (GridItem) -> Unit = {}, onItemLongClicked: (GridItem) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    val matching = if (tileSettings.liveTilesEnabled) notifications.filter { n ->
        n.packageName == item.app.packageName || item.children.any { it.packageName == n.packageName }
    } else emptyList()
    var detail by remember(item.app.packageName) { mutableStateOf<Pair<String, String>?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(true) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    var showFolder by remember { mutableStateOf(false) }
    val builtIn = item.app.packageName.startsWith("grid://")
    LaunchedEffect(item.app.packageName, tileSettings.liveTilesEnabled, tileSettings.isTileFlipEnabled, permissionRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                detail = if (tileSettings.liveTilesEnabled) withContext(Dispatchers.IO) { BuiltInTiles.detail(context, item.app.packageName) } else null
                if (tileSettings.isTileFlipEnabled) page++
                delay(8_000)
            }
        }
    }
    fun open() {
        when {
            item.children.isNotEmpty() -> showFolder = true
            item.app.packageName == BuiltInTiles.CALENDAR && !BuiltInTiles.granted(context, Manifest.permission.READ_CALENDAR) -> permission.launch(Manifest.permission.READ_CALENDAR)
            item.app.packageName == BuiltInTiles.PEOPLE && !BuiltInTiles.granted(context, Manifest.permission.READ_CONTACTS) -> permission.launch(Manifest.permission.READ_CONTACTS)
            item.photoUris.isNotEmpty() -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(item.photoUris[page.mod(item.photoUris.size)]), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            else -> onItemClicked(item)
        }
    }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var holding by remember { mutableStateOf(false) }
    val gestureScope = rememberCoroutineScope()
    val accent = Color(tileSettings.accentColor)
    Box(modifier = modifier
        .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
        .pointerInput(item.id, item.x, item.y) {
            detectDragGesturesAfterLongPress(
                onDragStart = { holding = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onDragCancel = {
                    if (holding && drag == Offset.Zero) onItemLongClicked(item)
                    drag = Offset.Zero
                    gestureScope.launch { delay(150); holding = false }
                },
                onDragEnd = {
                    if (abs(drag.x) > 24 || abs(drag.y) > 24) onItemDropped(item, drag.x, drag.y)
                    else onItemLongClicked(item)
                    drag = Offset.Zero
                    gestureScope.launch { delay(150); holding = false }
                },
                onDrag = { change, amount -> change.consume(); drag += amount }
            )
        }.clip(RoundedCornerShape(tileSettings.cornerRadius))
        .background(if (tileSettings.isTransparencyEnabled) accent.copy(alpha = .70f) else accent)
        .semantics { contentDescription = item.app.name + if (matching.isNotEmpty()) ", ${matching.size} notifications" else ""; customActions = listOf(CustomAccessibilityAction("Edit tile") { onItemLongClicked(item); true }) }
        .combinedClickable(onClick = { if (!holding) { if (isEditMode) onItemClicked(item) else open() } })) {
        if (item.widgetId >= 0) {
            val activity = context as? MainActivity
            val manager = AppWidgetManager.getInstance(context)
            val info = remember(item.widgetId) { manager.getAppWidgetInfo(item.widgetId) }
            if (activity != null && info != null) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = maxWidth.value.toInt(); val h = maxHeight.value.toInt()
                    AndroidView(factory = { activity.widgetHost.createView(it, item.widgetId, info) },
                        update = { it.updateAppWidgetSize(null, w, h, w, h) }, modifier = Modifier.fillMaxSize())
                }
                TextButton(onClick = { onItemLongClicked(item) }, modifier = Modifier.align(Alignment.TopEnd)) { Text("Edit", color = Color.White) }
            } else Text("Widget unavailable\nLong press to remove", color = Color.White, modifier = Modifier.padding(12.dp))
        } else {
            if (item.photoUris.isNotEmpty()) {
                Crossfade(targetState = if (tileSettings.liveTilesEnabled) page.mod(item.photoUris.size) else 0, label = "Photo tile") { index ->
                    AsyncImage(item.photoUris[index], Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)))
            }
            val expanded = item.width > 1 && item.height > 1
            val preview = matching.getOrNull(page.mod(matching.size.coerceAtLeast(1)))
            val live = if (!locked && tileSettings.showNotificationText && preview?.title?.isNotBlank() == true)
                preview.title to preview.text else detail
            if (expanded && live != null) {
                Crossfade(targetState = live, label = "Live content", modifier = Modifier.fillMaxSize().padding(12.dp).padding(bottom = 24.dp)) { content ->
                    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        Text(content.first, color = Color.White, fontSize = if (item.app.packageName == BuiltInTiles.CLOCK || item.app.packageName == BuiltInTiles.BATTERY) 32.sp else 20.sp,
                            fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(content.second, color = Color.White, fontSize = 13.sp, maxLines = if (item.width >= 4) 3 else 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else if (item.children.isNotEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(14.dp)) {
                    item.children.take(4).chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { app -> AsyncImage(app.icon.iconFile, Modifier.size(32.dp)) }
                    } }
                }
            } else if (item.photoUris.isEmpty()) {
                if (builtIn) Text(when (item.app.packageName) { BuiltInTiles.CLOCK -> "◷"; BuiltInTiles.CALENDAR -> java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH).toString(); BuiltInTiles.PEOPLE -> "● ●"; BuiltInTiles.BATTERY -> "▰"; else -> "▦" },
                    color = Color.White, fontSize = if (expanded) 38.sp else 22.sp, modifier = Modifier.align(Alignment.Center))
                else AsyncImage(item.app.icon.iconFile, Modifier.align(Alignment.Center).fillMaxSize(.48f))
            }
            if (expanded && !tileSettings.isAppLabelsHidden) Text(item.app.name, color = Color.White, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).padding(end = if (matching.isNotEmpty()) 26.dp else 0.dp))
            if (matching.isNotEmpty()) Text(if (matching.size > 99) "99+" else matching.size.toString(), color = Color.White, fontSize = if (expanded) 24.sp else 16.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        if (isEditMode) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .15f)))
    }
    if (showFolder) AlertDialog(onDismissRequest = { showFolder = false }, title = { Text(item.app.name) },
        text = { LazyColumn { items(item.children) { app -> TextButton(onClick = {
            showFolder = false
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) runCatching { context.startActivity(intent) }
        }) { AsyncImage(app.icon.iconFile, Modifier.size(36.dp)); Spacer(Modifier.width(12.dp)); Text(app.name) } } } },
        confirmButton = { TextButton(onClick = { showFolder = false }) { Text("Close") } })
}
