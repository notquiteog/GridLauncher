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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.data.profileNames
import tgo1014.gridlauncher.ui.theme.LocalGlass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    onFilterClearPressed: () -> Unit = {}, onUninstall: (App) -> Unit = {}, onBackPressed: () -> Unit = {}, onFabClosed: () -> Unit = {},
) {
    BackHandler(onBack = onBackPressed)
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current as? tgo1014.gridlauncher.ui.MainActivity
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var alphabet by remember { mutableStateOf(false) }
    val groups = remember(state.appList) { state.appList.sortedBy { it.name.lowercase() }.groupBy { it.nameFirstLetter.uppercase() } }
    val ink = if (state.tileSettings.darkTheme) Color.White else Color(0xFF142C42)
    val background = if (state.tileSettings.darkTheme) Color(0xFF101E30) else Color(0xFFEDF4FA)
    CompositionLocalProvider(LocalContentColor provides ink) {
    Column(Modifier.fillMaxSize().background(background).systemBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        if (state.tileSettings.oneHanded) Spacer(Modifier.height(80.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEMMMd"), java.util.Locale.getDefault()).format(java.util.Date()),
                color = ink, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = { onEditLayout(!state.isEditingLayout) }) { Text(if (state.isEditingLayout) "Done" else "Edit layout", color = ink) }
            IconButton(onClick = { onSettingsEvent(SettingsEvent.OnSettingsIconClicked) }) { Icon(Icons.Default.Settings, "Customize Start", tint = ink) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            profileNames.forEach { name -> FilterChip(selected = state.profile == name, onClick = { onProfile(name) }, label = { Text(name) },
                colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, selectedContainerColor = LocalGlass.current.accent.copy(alpha = .22f), labelColor = ink, selectedLabelColor = ink)) }
        }
        NowArea(hazeState)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("All apps", color = ink, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBackPressed) { Text("Start ←") }
        }
        OutlinedTextField(state.filterString, onFilterTextChanged, placeholder = { Text("Search apps") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink, focusedPlaceholderColor = ink.copy(alpha = .7f), unfocusedPlaceholderColor = ink.copy(alpha = .7f)),
            trailingIcon = { if (state.filterString.isNotEmpty()) TextButton(onClick = onFilterClearPressed) { Text("Clear") } }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        if (groups.isEmpty()) Text("No apps found", Modifier.padding(16.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            groups.forEach { (letter, apps) ->
                item(key = "letter:$letter") { Text(letter, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { alphabet = true }.padding(vertical = 14.dp, horizontal = 8.dp)) }
                items(apps, key = { it.packageName }) { app ->
                    var menu by remember { mutableStateOf(false) }
                    val launcher = context.getSystemService(LauncherApps::class.java)
                    val shortcuts = remember(menu, app.packageName) {
                        if (menu && launcher.hasShortcutHostPermission()) runCatching { launcher.getShortcuts(
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
    if (alphabet) AlertDialog(onDismissRequest = { alphabet = false }, title = { Text("Jump to letter") }, text = {
        Column { groups.keys.toList().chunked(5).forEach { row -> Row {
            row.forEach { letter -> TextButton(onClick = {
                var index = 0
                for ((key, apps) in groups) { if (key == letter) break; index += apps.size + 1 }
                alphabet = false; scope.launch { listState.scrollToItem(index) }
            }, modifier = Modifier.weight(1f)) { Text(letter, fontSize = 22.sp) } }
        } } }
    }, confirmButton = { TextButton(onClick = { alphabet = false }) { Text("Close") } })
}

}
