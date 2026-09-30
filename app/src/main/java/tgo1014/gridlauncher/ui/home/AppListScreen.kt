package tgo1014.gridlauncher.ui.home

import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.data.builtinProfileNames
import tgo1014.gridlauncher.data.defaultProfileName
import tgo1014.gridlauncher.data.resolveScheduledLayout
import tgo1014.gridlauncher.ui.theme.LocalGlass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.abs
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.SearchRow
import tgo1014.gridlauncher.live.SearchSource
import tgo1014.gridlauncher.live.StartSearch
import tgo1014.gridlauncher.ui.composables.JumpRail
import tgo1014.gridlauncher.ui.composables.jumpLetterOf
import tgo1014.gridlauncher.ui.theme.AppIconImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListScreen(
    state: HomeState, hazeState: HazeState = remember { HazeState() }, onAppClicked: (App) -> Unit = {},
    onAddToGrid: (App) -> Unit = {}, onFilterTextChanged: (String) -> Unit = {},
    onSettingsEvent: (SettingsEvent) -> Unit = {}, onProfile: (String) -> Unit = {}, onEditLayout: (Boolean) -> Unit = {},
    onFilterClearPressed: () -> Unit = {}, onUninstall: (App) -> Unit = {}, onBackPressed: () -> Unit = {},
    onCreateLayout: (String, Boolean) -> Unit = { _, _ -> }, onRenameLayout: (String, String) -> Unit = { _, _ -> },
    onDeleteLayout: (String) -> Unit = {}, onReorderLayouts: (List<String>) -> Unit = {},
    frequent: List<String> = emptyList(),
    onPinToHotseat: (String) -> Unit = {}, onSearch: () -> Unit = {}, onAskHandled: () -> Unit = {},
    onSearchRowClicked: (SearchRow) -> Unit = {},
) {
    BackHandler(onBack = onBackPressed)
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current as? tgo1014.gridlauncher.ui.MainActivity
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var alphabet by remember { mutableStateOf(false) }

    // The drawer sorts over real usage counts, not a guess: most used or most recent first.
    val ordered = remember(state.appList, state.tileSettings.drawerSort, frequent) {
        when (state.tileSettings.drawerSort) {
            "frequent" -> state.appList.sortedWith(compareByDescending<App> { app -> frequent.indexOf(app.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
                .thenBy { it.name.lowercase() })
            "recent" -> state.appList.sortedByDescending { app -> frequent.indexOf(app.packageName).let { if (it < 0) -1 else -it } }
            else -> state.appList.sortedBy { it.name.lowercase() }
        }
    }
    val grouped = remember(ordered, state.tileSettings.drawerSort) {
        if (state.tileSettings.drawerSort == "alphabetical") ordered.groupBy { it.nameFirstLetter.uppercase() }
        else mapOf<String, List<App>>((if (state.tileSettings.drawerSort == "recent") "Recent" else "Most used") to ordered)
    }
    // The jump rail belongs here rather than on Start: Windows Phone kept the alphabet beside the app
    // list, and it only has letters to offer while the list is actually grouped by letter.
    val letters = remember(grouped, state.tileSettings.drawerSort) {
        if (state.tileSettings.drawerSort == "alphabetical") grouped.keys.mapTo(mutableSetOf()) { jumpLetterOf(it) }
        else emptySet()
    }
    // Apps first, then the people, notifications and calendar the same query found. Identical
    // either way: these rows are what the search returns whether or not an index is behind it.
    val found = remember(state.searchResults) { StartSearch.sections(state.searchResults) }
    val ink = if (state.tileSettings.darkTheme) Color.White else Color(0xFF142C42)
    // A letter header is a caption that happens to head a group, so it has to be quieter than the
    // app names under it without ever reading as switched off. One alpha cannot do that on both
    // drawers, because the two inks sit at opposite ends of the scale: on the dark drawer white at
    // 78% is 10.6:1 against #101E30 and on the light drawer the pale ink at 72% is 5.4:1 against
    // #EDF4FA, which are both far above the 4.5:1 a 15sp line needs, and each still sits a clear
    // step below the names' 20:1 and 12.7:1. At 60% the dark header was 5.9:1, which is where it
    // stopped reading as a heading and started reading as something switched off.
    val headerInk = ink.copy(alpha = if (state.tileSettings.darkTheme) .78f else .72f)
    // Translucent when a wallpaper is set, so the glass controls still have something to sample.
    val background = if (state.tileSettings.isTransparencyEnabled) Color.Transparent else if (state.tileSettings.darkTheme) Color(0xFF101E30) else Color(0xFFEDF4FA)
    CompositionLocalProvider(LocalContentColor provides ink) {
    Column(Modifier.fillMaxSize().background(background).systemBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        // The one-handed gap stays the first thing in the column, which is the whole of what it is:
        // a thumb-reach offset applied to the page. With it off - the default - nothing is above the
        // search box, so the box is the first thing on the page, as it is in the reference.
        if (state.tileSettings.oneHanded) Spacer(Modifier.height(80.dp))
        // The reference puts the search box at the very top of the app list, above everything else
        // on the page. It is a plain child of this column, so the date row, the layout chips, the
        // frequent row and the jump rail are all laid out below it in the normal flow and none of
        // them can overlap it at any pane height. It is the first weighted-height sibling, so
        // BOARD_SHARE still divides exactly the same remainder it did before: the field is a
        // fixed-height child wherever in the column it sits.
        OutlinedTextField(state.filterString, onFilterTextChanged,
            // The visible placeholder is the reference's short "Search". What a screen reader is
            // told, and what the instrumented tests type into, is "Search apps": that is the phrase
            // that says what the box is for, while the visible word stays the reference's.
            placeholder = { Text("Search", modifier = Modifier.semantics { text = AnnotatedString("Search apps") }) }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink,
                focusedPlaceholderColor = ink.copy(alpha = .7f), unfocusedPlaceholderColor = ink.copy(alpha = .7f),
                focusedBorderColor = ink.copy(alpha = .55f), unfocusedBorderColor = ink.copy(alpha = .38f),
                // The drawer is drawn straight onto the wallpaper, so the box itself stays a hole in
                // it and only its outline is ink.
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
            // The magnifier sits on the right-hand end of the box, where the reference has it, and
            // the clear affordance takes that same end once there is something to clear. The glyph
            // itself is described by nothing: it shares the field's node, which already says what
            // the box is, and a second label on it would only be read out twice.
            trailingIcon = { if (state.filterString.isEmpty()) Icon(Icons.Filled.Search, null, tint = ink.copy(alpha = .8f), modifier = Modifier.size(22.dp)) else TextButton(onClick = onFilterClearPressed) { Text("Clear", color = ink) } },
            // 8dp, not 12: it is the only gap between the search box and the date row, and every
            // dp of it is a dp of app list the user does not have to scroll to reach.
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        // The date row and the way back to Start are one row. The reference's app list has no
        // heading for a back control to sit under - the list simply starts under the search box -
        // so the "All apps" title is gone, and a row left holding nothing but a back button would
        // push the list a whole row further down for one arrow. The arrow goes here instead,
        // leading the row the way it led the reference's own.
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBackPressed, contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.semantics { contentDescription = "Back to Start" }) {
                Text("Start", color = ink, fontSize = 14.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = ink, modifier = Modifier.size(18.dp))
            }
            Text(tgo1014.gridlauncher.live.Clock.inZone(java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEMMMd"), java.util.Locale.getDefault())).format(tgo1014.gridlauncher.live.Clock.date()),
                color = ink, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onClick = { onEditLayout(!state.isEditingLayout) }) { Text(if (state.isEditingLayout) "Done" else "Edit layout", color = ink) }
            IconButton(onClick = { onSettingsEvent(SettingsEvent.OnSettingsIconClicked) }) { Icon(Icons.Default.Settings, "Customize Start", tint = ink) }
        }
        LayoutSelector(state, ink, onProfile, onCreateLayout, onRenameLayout, onDeleteLayout, onReorderLayouts)
        tgo1014.gridlauncher.ui.composables.FrequentRow(state.appList, frequent, state.tileSettings.drawerSort, onAppClicked, { onPinToHotseat(it.packageName) })
        if (grouped.isEmpty() && found.isEmpty()) Text(if (state.filterString.isBlank()) "Looking for apps…" else "No apps found", Modifier.padding(16.dp))
        // The board and the app list are the only things still competing for height here, and the
        // board is the secondary one, so it is handed a weighted share of what the header, the
        // layout chips, the frequent row and the search field have left and the list is handed the
        // rest. Compose divides a Column's spare height between the weighted children before it
        // measures any of them, so the list's share does not move when cards arrive: with the board
        // present the list is (1 - BOARD_SHARE) / BOARD_SHARE, or 2.85, times its height, and with
        // no cards the board contributes no node at all and the list has all of it.
        NowArea(hazeState, Modifier.weight(BOARD_SHARE))
        // The rail gets 40dp rather than 32dp and 6dp of air after it, because a column of letters
        // 32dp wide is what made it read as squeezed: the glyph is only about 7dp of that, so the
        // letters sat hard against the screen edge and 20dp from the first icon. The extra width
        // moves them into a column of their own and the 6dp keeps them off the icons.
        Row(Modifier.fillMaxWidth().weight(1f - BOARD_SHARE), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (letters.isNotEmpty()) DrawerRail(letters, grouped, listState, onShowAlphabet = { alphabet = true },
                modifier = Modifier.width(40.dp).fillMaxHeight())
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                grouped.forEach { (letter, apps) ->
                    // A group header is a caption that heads a group, not a headline: 15sp, so it
                    // is a step under the 20sp names it introduces, and semibold, so at that size
                    // it still reads as a heading rather than as a stray letter.
                    item(key = "letter:$letter") { Text(letter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = headerInk,
                        modifier = Modifier.clickable { alphabet = true }.padding(top = 10.dp, bottom = 4.dp)) }
                    items(apps, key = { it.packageName }) { app ->
                        var menu by remember { mutableStateOf(false) }
                        val launcher = context.getSystemService(LauncherApps::class.java)
                        val shortcuts by produceState(emptyList(), menu, app.packageName) {
                            value = if (menu && launcher.hasShortcutHostPermission()) runCatching { launcher.getShortcuts(
                                LauncherApps.ShortcutQuery().setPackage(app.packageName).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST),
                                Process.myUserHandle())?.take(4).orEmpty() }.getOrDefault(emptyList()) else emptyList()
                        }
                        Box {
                            Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onAppClicked(app) }, onLongClick = { menu = true }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                // A 60dp mark and a 20sp name. The name is what the list is read
                                // for, but at 28sp against a 56dp icon it out-scaled the thing it
                                // was naming and the row read as two fights; at 20sp it is still the
                                // largest type on the page and the icon has the row's measure. The
                                // name takes the rest of the row so a long one ellipsises instead
                                // of pushing the row taller.
                                AppIconImage(app.icon.iconFile, Modifier.size(60.dp), app.icon.fill)
                                Text(app.name, color = ink, fontSize = 20.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 16.dp))
                            }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Pin to Start") }, onClick = { menu = false; onAddToGrid(app) })
                                shortcuts.forEach { shortcut ->
                                    DropdownMenuItem(text = { Text("Pin: ${shortcut.shortLabel}") }, onClick = { menu = false; activity?.pinShortcut(app, shortcut) })
                                    DropdownMenuItem(text = { Text(shortcut.shortLabel?.toString().orEmpty()) }, onClick = {
                                    menu = false; runCatching { launcher.startShortcut(shortcut, null, null) }
                                }) }
                                DropdownMenuItem(text = { Text("App info") }, onClick = {
                                    menu = false; context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                                })
                                if (!app.isSystemApp) DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; onUninstall(app) })
                            }
                        }
                    }
                }
                found.forEach { section ->
                    item(key = "found:${section.source}") { Text(SearchSource.label(section.source), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = headerInk,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                    items(section.rows, key = { "found:${it.id}" }) { row ->
                        SearchResultRow(row, ink) { onSearchRowClicked(row) }
                    }
                }
            }
        }
    }
    if (alphabet && state.tileSettings.drawerSort == "alphabetical") AlertDialog(onDismissRequest = { alphabet = false }, title = { Text("Jump to letter") }, text = {
        Column { grouped.keys.toList().chunked(5).forEach { row -> Row {
            row.forEach { letter -> TextButton(onClick = {
                val index = headerAt(grouped) { it == letter }
                alphabet = false; scope.launch { listState.scrollToItem(index) }
            }, modifier = Modifier.weight(1f)) { Text(letter, fontSize = 22.sp) } }
        } } }
    }, confirmButton = { TextButton(onClick = { alphabet = false }) { Text("Close") } })
}
}

/**
 * The jump rail beside the list. The letter at the top of the list is derived here rather than in
 * the drawer itself, so scrolling the list only recomposes the rail.
 */
@Composable
private fun DrawerRail(
    letters: Set<Char>, grouped: Map<String, List<App>>, listState: LazyListState,
    onShowAlphabet: () -> Unit, modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val current by remember(grouped) { derivedStateOf { keyAt(grouped, listState.firstVisibleItemIndex)?.let { jumpLetterOf(it) } } }
    JumpRail(letters, current,
        onJump = { letter -> scope.launch { listState.scrollToItem(headerAt(grouped) { jumpLetterOf(it) == letter }) } },
        onShowAlphabet = onShowAlphabet, modifier = modifier)
}

/**
 * The item index of the group header [match] selects. Every group before it contributes its header
 * plus its apps, which is the arithmetic the drawer list actually lays out.
 */
private fun headerAt(grouped: Map<String, List<App>>, match: (String) -> Boolean): Int {
    var index = 0
    for ((key, apps) in grouped) { if (match(key)) return index; index += apps.size + 1 }
    return 0
}

/**
 * The group header [index] falls under, or null once the search results start. The top of the list
 * is usually an app row rather than a header - any scroll past a letter puts one there - and a rail
 * that only lights a letter when a header is exactly first would go dark for most of a scroll. The
 * letter is the group the first item in view belongs to, which is also the group the reader is in.
 */
private fun keyAt(grouped: Map<String, List<App>>, index: Int): String? {
    var offset = 0
    var current: String? = null
    for ((key, apps) in grouped) {
        if (index < offset) return current
        current = key
        offset += apps.size + 1
    }
    return null
}

/** A person, a notification or a calendar event, drawn like an app row without an icon file. */
@Composable
private fun SearchResultRow(row: SearchRow, ink: Color, onClick: () -> Unit) {
    // The same measure as an app row, because it is read in the same list: same 60dp mark, same
    // 20sp name, same leading gap.
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Text(row.title.take(1).uppercase(), color = ink, fontSize = 20.sp)
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(row.title, color = ink, fontSize = 20.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (row.subtitle.isNotBlank()) Text(row.subtitle, color = ink.copy(alpha = .7f), fontSize = 13.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/**
 * Layout chips, plus create, rename and delete for every layout there is: a built-in one is renamed
 * and deleted exactly like your own, and none of them can be lost.
 *
 * A chip is one gesture surface carrying three things. A tap selects it. A hold decides on the lift
 * whether it opened the menu or carried the chip somewhere else, so a long press and a drag never
 * both claim the same finger. Nothing is consumed before the hold completes, which is what leaves
 * the row free to scroll under a finger that is still deciding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayoutSelector(
    state: HomeState, ink: Color, onProfile: (String) -> Unit,
    onCreate: (String, Boolean) -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit = {},
) {
    val accent = LocalGlass.current.accent
    var newLayout by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val layouts = state.layouts.ifEmpty { builtinProfileNames }
    val listState = rememberLazyListState()
    val order = remember { mutableStateOf(layouts) }
    val dragging = remember { mutableStateOf<String?>(null) }
    val travel = remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = remember(density) { with(density) { 48.dp.toPx() } }
    val nudge = remember(density) { with(density) { 12.dp.toPx() } }
    // The bar keeps showing the order the finger left it in until the store hands the same one
    // back, so the chips never jump to the old order for the frame the write takes.
    LaunchedEffect(layouts) { if (dragging.value == null) order.value = layouts }
    // A chip held near either end walks the row along under it, so a bar wider than the screen can
    // still be reordered without letting go.
    LaunchedEffect(dragging.value) {
        if (dragging.value == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val name = dragging.value ?: return@LaunchedEffect
            val info = listState.layoutInfo
            val chip = info.visibleItemsInfo.firstOrNull { it.key == layoutChipKey(name) } ?: continue
            val centre = chip.offset - listState.firstVisibleItemScrollOffset + travel.floatValue + chip.size / 2f
            val width = info.viewportSize.width.toFloat()
            val overshoot = when {
                centre > width - edge -> centre - (width - edge)
                centre < edge -> centre - edge
                else -> 0f
            }
            if (overshoot == 0f) continue
            val before = listState.firstVisibleItemScrollOffset
            listState.scrollBy(overshoot.coerceIn(-nudge, nudge))
            travel.floatValue += listState.firstVisibleItemScrollOffset - before
        }
    }
    LazyRow(modifier = Modifier.fillMaxWidth(), state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(order.value, key = ::layoutChipKey) { name ->
            var menu by remember(name) { mutableStateOf(false) }
            val lifted = dragging.value == name
            Box(Modifier
                .zIndex(if (lifted) 1f else 0f)
                // Every layer property is set on every frame, lifted or not: a layer that keeps the
                // last scale and shadow it was given would leave the chip looking picked up for good.
                .graphicsLayer {
                    translationX = travel.floatValue
                    scaleX = if (lifted) 1.06f else 1f
                    scaleY = scaleX
                    shadowElevation = if (lifted) 12.dp.toPx() else 0f
                    shape = if (lifted) RoundedCornerShape(10.dp) else RectangleShape
                }
                .pointerInput(name) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (awaitLongPressOrCancellation(down.id) == null) return@awaitEachGesture
                        var draft: List<String>? = null
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                // The lift is what tells a plain hold from a drag, so the menu opens
                                // here and the chip's own click is held off by eating the lift.
                                if (!moved) { change.consume(); menu = true }
                                break
                            }
                            // The row scrolling sideways under the finger is a scroll, never a reorder.
                            if (!moved && change.isConsumed) break
                            val delta = change.positionChange()
                            if (!moved && (abs(travel.floatValue) + delta.getDistance()) > viewConfiguration.touchSlop) {
                                moved = true
                                dragging.value = name
                                draft = order.value
                                travel.floatValue = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (!moved) continue
                            change.consume()
                            travel.floatValue += delta.x
                            // The chip trades places with its neighbour the moment the finger carries
                            // its centre past theirs, and the finger's own offset is corrected by the
                            // distance the slot moved so the chip stays under the finger.
                            val swap = draft?.let { dragPast(it, name, travel.floatValue, listState.layoutInfo.visibleItemsInfo) }
                            if (swap != null) {
                                draft = swap.first
                                order.value = swap.first
                                travel.floatValue -= swap.second
                            }
                        }
                        if (moved) {
                            dragging.value = null
                            travel.floatValue = 0f
                            onReorder(draft ?: order.value)
                        }
                    }
                }) {
                FilterChip(selected = state.profile == name, onClick = { onProfile(name) },
                    label = { Text(name, color = ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, selectedContainerColor = accent.copy(alpha = .22f)))
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename $name") }, onClick = { menu = false; renaming = name })
                    DropdownMenuItem(text = { Text("Delete $name") }, enabled = layouts.size > 1, onClick = { menu = false; deleting = name })
                    if (layouts.size <= 1) Text("Start always keeps one layout", style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = .7f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
        item(layoutChipKey("add")) {
            AssistChip(onClick = { newLayout = true }, label = { Text("New", color = ink) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent, labelColor = ink),
                leadingIcon = { Icon(Icons.Default.Add, null, tint = ink, modifier = Modifier.size(18.dp)) })
        }
    }
    if (newLayout) {
        var name by remember { mutableStateOf("") }
        var copy by remember { mutableStateOf(true) }
        AlertDialog(onDismissRequest = { newLayout = false }, title = { Text("New layout") }, text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Layout name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(copy, null)
                    Column { Text("Copy current tiles"); Text("On for a copy of Start, off for an empty layout.", style = MaterialTheme.typography.bodySmall) }
                }
                Text("${state.layouts.count { it !in builtinProfileNames }} of 12 custom layouts used.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, copy); newLayout = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { newLayout = false }) { Text("Cancel") } })
    }
    renaming?.let { current ->
        var name by remember(current) { mutableStateOf(current) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename $current") }, text = {
            OutlinedTextField(name, { name = it }, label = { Text("Layout name") }, singleLine = true)
        }, confirmButton = { TextButton(enabled = name.isNotBlank() && name != current, onClick = { onRename(current, name); renaming = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
    deleting?.let { target ->
        val moves = target == state.profile
        val fallback = resolveScheduledLayout(defaultProfileName, layouts.filterNot { it == target })
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete $target?") },
            text = { Column {
                Text("This removes the $target layout and its tiles. It cannot be undone. Copy it to another layout first if you want to keep it.")
                // Deleting the layout being shown is allowed, but only by leaving it first, and
                // that has to be said here rather than discovered afterwards.
                if (moves) Text("You are in $target, so Start moves to $fallback first.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { onDelete(target); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

/**
 * A chip's key in the row. `sanitizeLayoutName` turns control characters into spaces, so a leading
 * one cannot appear in a layout name and no layout can ever collide with a key of its own.
 */
private fun layoutChipKey(name: String) = "\u0000$name"

/**
 * The order after the chip called [name] has been carried [travel] pixels sideways, and how far the
 * finger's own offset has to travel with the slot so the chip stays under it. A neighbour that is
 * not on screen is not a swap, which is what stops a drag inventing a position nothing was drawn
 * at, and the row is never shorter than the layouts in it.
 */
private fun dragPast(draft: List<String>, name: String, travel: Float, visible: List<LazyListItemInfo>): Pair<List<String>, Float>? {
    val from = draft.indexOf(name)
    if (from < 0) return null
    val me = visible.firstOrNull { it.key == layoutChipKey(name) } ?: return null
    fun neighbour(step: Int) = draft.getOrNull(from + step)?.let { key -> visible.firstOrNull { it.key == layoutChipKey(key) } }
    val centre = me.offset + travel + me.size / 2f
    val right = neighbour(1)
    val left = neighbour(-1)
    val (swap, to) = when {
        right != null && centre > right.offset + right.size / 2f -> right to from + 1
        left != null && centre < left.offset + left.size / 2f -> left to from - 1
        else -> return null
    }
    return draft.toMutableList().apply { add(to, removeAt(from)) }.toList() to (swap.offset - me.offset).toFloat()
}
