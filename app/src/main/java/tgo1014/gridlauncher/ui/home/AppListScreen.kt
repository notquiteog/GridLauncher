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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.data.builtinProfileNames
import tgo1014.gridlauncher.ui.theme.LocalGlass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.theme.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListScreen(
    state: HomeState, hazeState: HazeState = remember { HazeState() }, onAppClicked: (App) -> Unit = {},
    onAddToGrid: (App) -> Unit = {}, onFilterTextChanged: (String) -> Unit = {},
    onSettingsEvent: (SettingsEvent) -> Unit = {}, onProfile: (String) -> Unit = {}, onEditLayout: (Boolean) -> Unit = {},
    onFilterClearPressed: () -> Unit = {}, onUninstall: (App) -> Unit = {}, onBackPressed: () -> Unit = {},
    onCreateLayout: (String, Boolean) -> Unit = { _, _ -> }, onRenameLayout: (String, String) -> Unit = { _, _ -> },
    onDeleteLayout: (String) -> Unit = {}, frequent: List<String> = emptyList(),
    onPinToHotseat: (String) -> Unit = {}, onSearch: () -> Unit = {}, onAskHandled: () -> Unit = {},
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
    val ink = if (state.tileSettings.darkTheme) Color.White else Color(0xFF142C42)
    // Translucent when a wallpaper is set, so the glass controls still have something to sample.
    val background = if (state.tileSettings.isTransparencyEnabled) Color.Transparent else if (state.tileSettings.darkTheme) Color(0xFF101E30) else Color(0xFFEDF4FA)
    CompositionLocalProvider(LocalContentColor provides ink) {
    Column(Modifier.fillMaxSize().background(background).systemBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        if (state.tileSettings.oneHanded) Spacer(Modifier.height(80.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEMMMd"), java.util.Locale.getDefault()).format(java.util.Date()),
                color = ink, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = { onEditLayout(!state.isEditingLayout) }) { Text(if (state.isEditingLayout) "Done" else "Edit layout", color = ink) }
            IconButton(onClick = { onSettingsEvent(SettingsEvent.OnSettingsIconClicked) }) { Icon(Icons.Default.Settings, "Customize Start", tint = ink) }
        }
        LayoutSelector(state, ink, onProfile, onCreateLayout, onRenameLayout, onDeleteLayout)
        NowArea(hazeState)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("All apps", color = ink, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBackPressed, modifier = Modifier.semantics { contentDescription = "Back to Start" }) {
                    Text("Start"); Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = ink, modifier = Modifier.size(18.dp))
                }
        }
        OutlinedTextField(state.filterString, onFilterTextChanged, placeholder = { Text("Search apps") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink, focusedPlaceholderColor = ink.copy(alpha = .7f), unfocusedPlaceholderColor = ink.copy(alpha = .7f)),
            trailingIcon = { if (state.filterString.isNotEmpty()) TextButton(onClick = onFilterClearPressed) { Text("Clear") } }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        if (grouped.isEmpty()) Text(if (state.filterString.isBlank()) "Looking for apps…" else "No apps found", Modifier.padding(16.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            grouped.forEach { (letter, apps) ->
                item(key = "letter:$letter") { Text(letter, fontSize = if (state.tileSettings.drawerSort == "alphabetical") 30.sp else 14.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { alphabet = true }.padding(vertical = if (state.tileSettings.drawerSort == "alphabetical") 14.dp else 4.dp, horizontal = 8.dp)) }
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
                            AsyncImage(app.icon.iconFile, Modifier.size(48.dp))
                            Text(app.name, color = ink, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp))
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
        }
    }
    if (alphabet && state.tileSettings.drawerSort == "alphabetical") AlertDialog(onDismissRequest = { alphabet = false }, title = { Text("Jump to letter") }, text = {
        Column { grouped.keys.toList().chunked(5).forEach { row -> Row {
            row.forEach { letter -> TextButton(onClick = {
                var index = 0
                for ((key, apps) in grouped) { if (key == letter) break; index += apps.size + 1 }
                alphabet = false; scope.launch { listState.scrollToItem(index) }
            }, modifier = Modifier.weight(1f)) { Text(letter, fontSize = 22.sp) } }
        } } }
    }, confirmButton = { TextButton(onClick = { alphabet = false }) { Text("Close") } })
}
}

/** Layout chips plus create, rename and delete for the user's own layouts. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayoutSelector(
    state: HomeState, ink: Color, onProfile: (String) -> Unit,
    onCreate: (String, Boolean) -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
) {
    val accent = LocalGlass.current.accent
    var newLayout by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val layouts = state.layouts.ifEmpty { builtinProfileNames }
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(layouts, key = { it }) { name ->
            var menu by remember(name) { mutableStateOf(false) }
            Box {
                FilterChip(selected = state.profile == name, onClick = { onProfile(name) },
                    label = { Text(name, color = ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, selectedContainerColor = accent.copy(alpha = .22f)))
                if (name !in builtinProfileNames) Box(Modifier.matchParentSize().combinedClickable(onClick = {}, onLongClick = { menu = true }))
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename $name") }, onClick = { menu = false; renaming = name })
                    DropdownMenuItem(text = { Text("Delete $name") }, onClick = { menu = false; deleting = name })
                }
            }
        }
        item("add") {
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
    deleting?.let { target -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete $target?") },
        text = { Text("This removes the $target layout and its tiles. It cannot be undone. Copy it to another layout first if you want to keep it.") },
        confirmButton = { TextButton(onClick = { onDelete(target); deleting = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}
