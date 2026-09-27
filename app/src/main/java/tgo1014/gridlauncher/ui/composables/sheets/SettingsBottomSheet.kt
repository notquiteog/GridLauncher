package tgo1014.gridlauncher.ui.composables.sheets

import androidx.activity.compose.LocalActivity

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.ui.MainActivity
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SettingsEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    tileSettings: TileSettings, isShowing: Boolean, onSettingsEvent: (SettingsEvent) -> Unit = {},
    apps: List<App> = emptyList(), onAddApp: (App) -> Unit = {}, onAddSpecial: (GridItem) -> Unit = {},
) {
    if (!isShowing) return
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    var folderDialog by remember { mutableStateOf(false) }
    val wallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onSettingsEvent(SettingsEvent.OnWallpaperPicked(it)) }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        val persisted = uris.mapNotNull { uri -> runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            uri.toString()
        }.getOrNull() }
        if (persisted.isNotEmpty()) onAddSpecial(GridItem(app = App("Photos", BuiltInTiles.PHOTOS), width = 4, height = 2, photoUris = persisted))
    }
    ModalBottomSheet(onDismissRequest = { onSettingsEvent(SettingsEvent.OnSettingsSheetDismissed) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Make it yours", style = MaterialTheme.typography.headlineLarge)
            Text("Start, with a personal touch.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { activity?.chooseDefaultLauncher() }) { Text("Set as default home app") }
            Text("Accent color", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF0078D7, 0xFF008A00, 0xFFB4009E, 0xFFD24726, 0xFF643EBF, 0xFF006D77).forEach { color ->
                    Box(Modifier.size(40.dp).background(Color(color)).clickable {
                        onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(accentColor = color)))
                    }, contentAlignment = Alignment.Center) { if (tileSettings.accentColor == color) Text("✓", color = Color.White) }
                }
            }
            SettingSwitch("Dark background", tileSettings.darkTheme) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(darkTheme = it))) }
            SettingSwitch("Live tiles", tileSettings.liveTilesEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(liveTilesEnabled = it))) }
            SettingSwitch("Rotate live content", tileSettings.isTileFlipEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(isTileFlipEnabled = it))) }
            SettingSwitch("Show notification previews", tileSettings.showNotificationText) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(showNotificationText = it))) }
            Text("Notification access enables counts. Previews stay on this device. Android may hide sensitive content.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Manage notification access") }
            SettingSwitch("Hide tile labels", tileSettings.isAppLabelsHidden) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(isAppLabelsHidden = it))) }
            Text("Tile corners · ${tileSettings.cornerRadius}")
            Slider(value = tileSettings.cornerRadius.toFloat(), onValueChange = {
                onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(cornerRadius = it.toInt())))
            }, valueRange = 0f..32f)
            Row {
                TextButton(onClick = { wallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose wallpaper") }
                if (tileSettings.isTransparencyEnabled) TextButton(onClick = { onSettingsEvent(SettingsEvent.OnWallpaperRemoved) }) { Text("Remove") }
            }
            HorizontalDivider()
            Text("Add to Start", style = MaterialTheme.typography.titleLarge)
            BuiltInTiles.apps.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth()) {
                row.forEach { app -> OutlinedButton(onClick = { onAddApp(app) }, modifier = Modifier.weight(1f).padding(4.dp)) { Text(app.name) } }
            } }
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f).padding(4.dp)) { Text("Photos") }
                OutlinedButton(onClick = { activity?.addWidget() }, modifier = Modifier.weight(1f).padding(4.dp)) { Text("Android widget") }
            }
            TextButton(onClick = { folderDialog = true }) { Text("Create an app folder") }
            Text("Calendar and People ask for access when first opened. Use an Android weather or music widget for updates from your preferred provider.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Your layout", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = { activity?.exportLayout() }) { Text("Back up") }
                TextButton(onClick = { activity?.importLayout() }) { Text("Restore…") }
            }
            Text("Restoring replaces Start. Widgets and photo permissions must be added again on a new device.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (folderDialog) {
        var name by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(setOf<String>()) }
        AlertDialog(onDismissRequest = { folderDialog = false }, title = { Text("Create folder") }, text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Folder name") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 300.dp)) { items(apps, key = { it.packageName }) { app ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                    }) { Checkbox(app.packageName in selected, null); Text(app.name) }
                } }
            }
        }, confirmButton = { TextButton(enabled = selected.isNotEmpty() && name.isNotBlank(), onClick = {
            onAddSpecial(GridItem(app = App(name.trim(), BuiltInTiles.FOLDER), width = 2, children = apps.filter { it.packageName in selected }))
            folderDialog = false
        }) { Text("Pin folder") } }, dismissButton = { TextButton(onClick = { folderDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, change) }
}
