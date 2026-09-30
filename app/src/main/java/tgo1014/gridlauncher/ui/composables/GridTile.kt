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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import tgo1014.gridlauncher.ui.theme.readableInk
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.zIndex
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
import tgo1014.gridlauncher.live.CallTiles
import tgo1014.gridlauncher.live.CategoryApps
import tgo1014.gridlauncher.live.MediaTiles
import tgo1014.gridlauncher.live.NowPlaying
import tgo1014.gridlauncher.live.PhotoTiles
import tgo1014.gridlauncher.live.NotificationTiles
import tgo1014.gridlauncher.ui.MainActivity
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.AppIconImage
import tgo1014.gridlauncher.ui.theme.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridTile(
    item: GridItem, hazeState: HazeState? = null, onFolder: ((GridItem) -> Unit)? = null, modifier: Modifier = Modifier, tileSettings: TileSettings = TileSettings(),
    isEditMode: Boolean = false, onItemDropped: (GridItem, Float, Float) -> Unit = { _, _, _ -> }, onItemClicked: (GridItem) -> Unit = {}, onItemLongClicked: (GridItem) -> Unit = {},
    isKeyboardFocused: Boolean = false, onFocus: () -> Unit = {},
    /**
     * Set only when this tile is being drawn magnified inside [TilePeek]. It is what turns every
     * gesture into a gesture on the peek: a tap does nothing, a long press does nothing, and a
     * double tap puts the tile back the way it was.
     */
    peekDismiss: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val tileColor = Color(item.tileColor ?: item.app.icon.edgeColor ?: tileSettings.accentColor).copy(alpha = 1f)
    var recentPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    // A Photos hub with no picks borrows the library itself; chosen photos always win.
    val photoSource = if (item.photoUris.isNotEmpty()) item.photoUris else recentPhotos
    // Photo content brings its own contrast, so its overlay text is always white.
    // High contrast is the user's own a11y preference, so the label pushes to the extremes.
    val tileInk = if (photoSource.isNotEmpty()) Color.White
    else if (glass.highContrast) (if (tileColor.luminance() > .5f) Color.Black else Color.White)
    else tgo1014.gridlauncher.ui.theme.readableInk(tileColor)
    var showNativeActions by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showPeek by remember { mutableStateOf(false) }
    var showJumpList by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var people by remember(item) { mutableStateOf(item.contacts.ifEmpty { listOfNotNull(item.contact) }) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    val matching = if (tileSettings.liveTilesEnabled && !glass.quiet) notifications.filter { n ->
        n.packageName == item.app.packageName || item.children.any { it.packageName == n.packageName } || people.any { it.matches(n) }
    } else emptyList()
    var detail by remember(item.app.packageName) { mutableStateOf<Pair<String, String>?>(null) }
    var page by remember { mutableIntStateOf(0) }
    val locked = glass.locked
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    var showFolder by remember { mutableStateOf(false) }
    var showHub by remember { mutableStateOf(false) }
    // A call takes the tile over rather than sitting beside the last message: it is the one thing
    // happening on this app right now, and it is what the user opened the launcher to deal with.
    // Both steps are remembered, because a full screen of tiles would otherwise re-project every
    // notification and re-query every package on every frame.
    val callSignals = remember(notifications) { notifications.map(CallTiles::signal) }
    val call = remember(callSignals, item.app.packageName, tileSettings, glass.quiet, glass.locked) {
        if (tileSettings.liveTilesEnabled && tileSettings.showNotificationText && !glass.quiet)
            CallTiles.callFor(callSignals, item.app.packageName, tileSettings, locked = glass.locked) { CategoryApps.label(context, it).orEmpty() }
        else null
    }
    val builtIn = item.app.packageName.startsWith("grid://")
    val hub = if (item.app.packageName == BuiltInTiles.PEOPLE) showHub else showPeople
    val nowPlaying by MediaTiles.now.collectAsStateWithLifecycle()
    val isHub = BuiltInTiles.isHub(item.app.packageName)
    // A double tap magnifies the tile, the Windows Phone second state, but only a tile that has
    // something live to magnify, and only one that is already allowed to show it: everything in
    // here is an answer the tile has already reached for itself, and edit mode is not a place to
    // start navigating from.
    val peekable = PeekVisibility(
        liveTiles = tileSettings.liveTilesEnabled, hub = isHub, group = item.isGroup, widget = item.widgetId >= 0,
        locked = locked, quiet = glass.quiet, previewsHidden = item.app.packageName in tileSettings.hiddenPreviewApps,
        notification = matching.isNotEmpty(), call = call != null, people = people.isNotEmpty(), photos = photoSource.isNotEmpty(),
    ).peekable() && !isEditMode && peekDismiss == null
    LaunchedEffect(item.app.packageName, tileSettings.liveTilesEnabled, tileSettings.isTileFlipEnabled, glass.motion, permissionRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Stagger by tile so a full screen of tiles does not all refresh in the same frame.
            val offset = (item.id * 977L) % 8_000L
            var elapsed = 0L
            while (true) {
                if (item.app.packageName == BuiltInTiles.PEOPLE) people = withContext(Dispatchers.IO) { ContactTiles.favorites(context) }
                if (item.app.packageName == BuiltInTiles.PHOTOS && item.photoUris.isEmpty()) {
                    recentPhotos = withContext(Dispatchers.IO) { PhotoTiles.recent(context).map { it.uri } }
                }
                if (item.app.packageName == BuiltInTiles.MUSIC) withContext(Dispatchers.IO) { MediaTiles.refresh(context) }
                // Only the hubs do provider work; a plain app tile reads its notifications instead.
                detail = if (tileSettings.liveTilesEnabled && isHub) withContext(Dispatchers.IO) { BuiltInTiles.detail(context, item.app.packageName) } else null
                if (tileSettings.isTileFlipEnabled && glass.motion) page++
                elapsed += 8_000
                delay((8_000 - (offset - elapsed % 8_000) % 8_000).coerceIn(2_000, 8_000))
            }
        }
    }
    fun open() {
        when {
            item.childCount > 0 -> if (onFolder != null) onFolder(item) else { showFolder = true }
            item.app.packageName == BuiltInTiles.CALENDAR && !BuiltInTiles.granted(context, Manifest.permission.READ_CALENDAR) -> permission.launch(Manifest.permission.READ_CALENDAR)
            item.app.packageName == BuiltInTiles.PEOPLE && !BuiltInTiles.granted(context, Manifest.permission.READ_CONTACTS) -> permission.launch(Manifest.permission.READ_CONTACTS)
            item.app.packageName == BuiltInTiles.PHOTOS && item.photoUris.isEmpty() && !PhotoTiles.permission(context) -> permission.launch(PhotoTiles.permissionName())
            people.isNotEmpty() -> if (item.app.packageName == BuiltInTiles.PEOPLE) showHub = true else showPeople = true
            item.photoUris.isNotEmpty() -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(item.photoUris[page.mod(item.photoUris.size)]), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            photoSource.isNotEmpty() -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(photoSource[page.mod(photoSource.size)]), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            else -> onItemClicked(item)
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && glass.motion) .965f else 1f, if (glass.motion) spring(dampingRatio = .75f, stiffness = 650f) else snap(), label = "Tile press")
    val drag = remember(item.id) { mutableStateOf(Offset.Zero) }
    val lifted = drag.value != Offset.Zero
    BoxWithConstraints(modifier = modifier.zIndex(if (lifted) 1f else 0f)
        .graphicsLayer {
            scaleX = pressScale * if (lifted) 1.03f else 1f; scaleY = pressScale * if (lifted) 1.03f else 1f
            translationX = drag.value.x; translationY = drag.value.y
            shape = RectangleShape; shadowElevation = if (lifted) 26.dp.toPx() else 0f
        }.clip(RectangleShape).background(tileColor)
        .pointerInput(isEditMode, item.id) {
            if (!isEditMode) return@pointerInput
            // Watches the Initial pass so the drag wins over the tile's own click handling.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                var total = Offset.Zero
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    // Handle the lift first: that is where the drop is committed.
                    if (change.changedToUp()) {
                        if (dragging && (total.x != 0f || total.y != 0f)) onItemDropped(item, total.x, total.y)
                        drag.value = Offset.Zero
                        break
                    }
                    val delta = change.positionChange()
                    if (!dragging && total.getDistance() + delta.getDistance() > slop) {
                        dragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (dragging) { total += delta; drag.value = total }
                }
            }
        }
        .onFocusChanged { if (it.isFocused) onFocus() }
        .semantics { contentDescription = if (call != null) call.describe() else item.app.name + if (matching.isNotEmpty()) ", ${matching.size} notifications" else ""; customActions = if (peekDismiss != null) listOf(CustomAccessibilityAction("Close peek") { peekDismiss(); true }) else listOf(CustomAccessibilityAction(if (isEditMode) "Edit tile" else "App shortcuts") { if (isEditMode) onItemLongClicked(item) else showNativeActions = true; true }, CustomAccessibilityAction("Preview notifications") { showPreview = true; true }) + if (peekable) listOf(CustomAccessibilityAction("Peek tile") { showPeek = true; true }) else emptyList() }
        .focusable()
        .combinedClickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(),
            onClick = { if (peekDismiss == null) { if (isEditMode) onItemClicked(item) else open() } },
            onLongClick = { if (peekDismiss == null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (isEditMode) onItemLongClicked(item) else if (item.childCount > 0) showJumpList = true else showNativeActions = true } },
            // Passed as nothing at all on a tile that cannot peek, which is what lets a plain tap
            // through untouched: a double click handler of any kind, even one that opens the tile,
            // would hold that tap back until the double tap timeout expired.
            onDoubleClick = if (peekDismiss != null) ({ peekDismiss() }) else if (peekable) ({ showPeek = true }) else null)) {
        if (item.isGroup) {
            Text(item.groupLabel.ifBlank { item.app.name }, color = tileInk, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp))
        } else if (item.widgetId >= 0) {
            val activity = LocalActivity.current as? MainActivity
            val manager = AppWidgetManager.getInstance(context)
            val info = remember(item.widgetId) { manager.getAppWidgetInfo(item.widgetId) }
            if (activity != null && info != null) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = maxWidth.value.toInt(); val h = maxHeight.value.toInt()
                    AndroidView(factory = { activity.widgetHost.createView(it, item.widgetId, info) },
                        update = { it.updateAppWidgetSize(null, w, h, w, h) }, modifier = Modifier.fillMaxSize())
                }
                TextButton(onClick = { if (isEditMode) onItemLongClicked(item) else showNativeActions = true }, modifier = Modifier.align(Alignment.TopEnd)) { Text("Actions", color = tileInk) }
            } else Text("Widget unavailable\nRemove in Edit layout", color = tileInk, modifier = Modifier.padding(12.dp))
        } else {
            if (photoSource.isNotEmpty()) {
                Crossfade(targetState = if (tileSettings.liveTilesEnabled) page.mod(photoSource.size) else 0, label = "Photo tile") { index ->
                    AsyncImage(photoSource[index], Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)))
            }
            val expanded = maxWidth >= 86.dp && maxHeight >= 86.dp
            val isMusic = item.app.packageName == BuiltInTiles.MUSIC
            val preview = matching.getOrNull(page.mod(matching.size.coerceAtLeast(1)))
            val previewed = preview?.takeIf { tileSettings.showNotificationText && !glass.quiet }
            if (call != null) {
                // The call replaces the tile's live content: the last message is not what the user
                // needs to see while this app is ringing. The label still shows, so the tile is
                // still the same tile, and the notification action row stays away because the call
                // panel owns the controls.
                Box(Modifier.fillMaxSize().padding(
                    start = 12.dp, end = 12.dp,
                    top = 10.dp,
                    bottom = if (tileSettings.isAppLabelsHidden) 8.dp else (if (expanded) 30.dp else 22.dp),
                ), contentAlignment = Alignment.CenterStart) { CallTilePanel(call, tileInk, expanded) }
            } else if (isMusic && expanded) {
                NowPlayingTile(nowPlaying, Modifier.fillMaxSize().padding(10.dp), ink = tileInk,
                    onToggle = { MediaTiles.togglePlayPause() }, onNext = { MediaTiles.next() }, onPrevious = { MediaTiles.previous() })
            } else {
            val live = if (!locked && previewed?.packageName !in tileSettings.hiddenPreviewApps && previewed?.title?.isNotBlank() == true)
                previewed.title to previewed.text else detail
            if (expanded && live != null && people.isEmpty() && item.childCount == 0) {
                TileTurn(key = live, modifier = Modifier.fillMaxSize().padding(12.dp).padding(bottom = if (preview?.actions?.isNotEmpty() == true && item.width >= 2) 60.dp else 24.dp)) {
                    val content = live
                    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        Text(content.first, color = tileInk, fontSize = if (item.app.packageName == BuiltInTiles.CLOCK || item.app.packageName == BuiltInTiles.BATTERY) 32.sp else 20.sp,
                            fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(content.second, color = tileInk, fontSize = 13.sp, maxLines = if (item.width >= 2) 3 else 2, overflow = TextOverflow.Ellipsis)
                        if (previewed != null && live == (previewed.title to previewed.text)) {
                            if (previewed.semantic > 0) Text(semanticLabel(previewed.semantic), color = tileInk, fontSize = 11.sp)
                            LiveProgress(previewed, Modifier.padding(top = 5.dp), ink = tileInk)
                        }
                    }
                }
                // Android 17 stacks what else is waiting rather than hiding it behind a count.
                if (tileSettings.stackNotifications && expanded && item.width >= 2 && matching.size > 1 && !item.isGroup) {
                    val rest = matching.filter { it.key != preview?.key }.take(2)
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = if (tileSettings.isAppLabelsHidden) 6.dp else 20.dp)) {
                        rest.forEach { other ->
                            Text("· ${other.title.ifBlank { other.packageName }}", color = tileInk.copy(alpha = .78f),
                                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else if (people.isNotEmpty()) {
                PeopleMosaic(people, page, Modifier.fillMaxSize().padding(bottom = 24.dp), ink = tileInk)
            } else if (item.childCount > 0) {
                val folderIconSize = (minOf(maxWidth, maxHeight) * .31f).coerceAtMost(64.dp)
                Column(Modifier.align(Alignment.Center).padding(14.dp)) {
                    (item.children.take(4) + item.childFolders.take(4 - item.children.size).map { it.app }).chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { app -> Box {
                            AppIconImage(app.icon.iconFile, Modifier.size(folderIconSize), app.icon.fill)
                            val count = matching.count { it.packageName == app.packageName }
                            if (count > 0) Text(count.toString(), color = tileInk, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).background(glass.accent))
                        } }
                    } }
                }
            } else if (photoSource.isEmpty()) {
                if (builtIn) HubGlyph(item.app.packageName, if (item.width == 1 && item.height == 1) Modifier.align(Alignment.Center).size(26.dp) else Modifier, tileInk)
                else {
                    val iconSize = minOf(maxWidth * .72f, maxHeight - if (tileSettings.isAppLabelsHidden) 12.dp else 32.dp).coerceAtLeast(24.dp)
                    AppIconImage(item.app.icon.iconFile, Modifier.align(Alignment.Center).offset(y = if (tileSettings.isAppLabelsHidden) 0.dp else (-8).dp).size(iconSize), item.app.icon.fill, if (tileSettings.iconTint) tileInk else null)
                }
            }
            }
            if (expanded && !locked && previewed != null && previewed.packageName !in tileSettings.hiddenPreviewApps && maxWidth >= 180.dp && previewed.actions.isNotEmpty() && !isMusic && call == null) {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp)) { NotificationActions(previewed, compact = true, ink = tileInk) }
            }
            if (builtIn && item.width >= 2) HubGlyph(item.app.packageName, Modifier.align(Alignment.BottomEnd).padding(10.dp).size(16.dp), tileInk)
            if (!tileSettings.isAppLabelsHidden && !isMusic && !item.isGroup) Text(item.app.name, color = tileInk, fontSize = if (expanded) 13.sp else 11.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(if (expanded) 10.dp else 3.dp).padding(end = if (matching.isNotEmpty() && tileSettings.showTileCounts) 26.dp else 0.dp))
            if (matching.isNotEmpty() && tileSettings.showTileCounts && !item.isGroup) {
                if (tileSettings.badgeAsDot) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(10.dp)
                    .clip(CircleShape).background(tileInk)
                    .semantics { contentDescription = "${matching.size} notifications. Double tap to preview" }
                    .clickable { showPreview = true })
                else Text(if (matching.size > 99) "99+" else matching.size.toString(), color = tileInk, fontSize = if (expanded) 24.sp else 16.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "${matching.size} notifications. Double tap to preview" }
                        .clickable { showPreview = true }.padding(6.dp))
            }
        }
        if (isKeyboardFocused) {
            Box(Modifier.fillMaxSize().border(3.dp, Color.White))
            Box(Modifier.fillMaxSize().border(1.dp, glass.accent))
        }
        if (isEditMode) {
            // Visual only: the tile's own click already opens the edit sheet in edit mode.
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .12f)))
            if (item.positionPinned) Text("Pinned", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = .6f)).padding(3.dp))
        }
        NativeTileActions(item, showNativeActions, { showNativeActions = false }, { open() })
        if (showJumpList) FolderJumpList(item, onOpen = { app -> onItemClicked(filedTile(app)) },
            // The drill-down the layout already owns, so a jump list and an inline expansion land in
            // the same place, nested folders included.
            onOpenFolder = { folder -> if (onFolder != null) onFolder(folder) else showFolder = true },
            onDismiss = { showJumpList = false })
    }
    if (showPreview) NotificationPreview(item.app.name, (item.children.map { it.packageName } + item.app.packageName + matching.map { it.packageName }).toSet(), { showPreview = false })
    if (showPeek) TilePeek(item, tileSettings, { showPeek = false })
    if (showPeople) PeoplePreview(people, { showPeople = false })
    if (showHub) PeopleHub(people, { showHub = false })
    if (showFolder) AlertDialog(onDismissRequest = { showFolder = false }, title = { Text(item.app.name) },
        text = { LazyColumn { items(item.children) { app -> TextButton(onClick = {
            showFolder = false
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) runCatching { context.startActivity(intent) }
        }) { AppIconImage(app.icon.iconFile, Modifier.size(36.dp), app.icon.fill); Spacer(Modifier.width(12.dp)); Text(app.name) } } } },
        confirmButton = { TextButton(onClick = { showFolder = false }) { Text("Close") } })
}

/** Artwork, title and transport for the system's current media session. */
@Composable
private fun NowPlayingTile(now: NowPlaying?, modifier: Modifier, ink: Color, onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit) {
    val playing = now?.playing == true
    BoxWithConstraints(modifier) {
        if (now?.artwork != null) {
            Crossfade(targetState = now.packageName, label = "Album art") {
                Image(bitmap = now.artwork!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(top = if (now?.artwork != null) 28.dp else 0.dp)) {
            TileTurn(key = now?.label) {
                Text(now?.title?.takeIf { it.isNotBlank() } ?: "Nothing playing", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!now?.artist.isNullOrBlank()) Text(now.artist, color = ink.copy(alpha = .8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (now?.canGoPrevious == true) TileGlyph("⏮", ink, "Previous track") { onPrevious() }
                TileGlyph(if (playing) "⏸" else "▶", ink, if (playing) "Pause" else "Play", prominent = true) { onToggle() }
                if (now?.hasNext == true) TileGlyph("⏭", ink, "Next track") { onNext() }
            }
        }
    }
}

@Composable
private fun TileGlyph(glyph: String, ink: Color, description: String, prominent: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(glyph, color = ink, fontSize = if (prominent) 22.sp else 15.sp, maxLines = 1)
    }
}

/** The designed mark a hub tile carries, so a live tile never looks like a plain app icon. */
@Composable
fun HubGlyph(id: String, modifier: Modifier, ink: Color) {
    if (modifier != Modifier) {
        Box(modifier) { Icon(painterResource(BuiltInTiles.glyph(id)), null, tint = ink, modifier = Modifier.fillMaxSize()) }
    } else {
        Icon(painterResource(BuiltInTiles.glyph(id)), null, tint = ink, modifier = modifier.size(26.dp))
    }
}
