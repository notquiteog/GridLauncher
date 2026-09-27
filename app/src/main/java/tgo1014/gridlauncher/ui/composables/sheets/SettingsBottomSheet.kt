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
    currentProfile: String = "Personal", onCopyProfile: (String) -> Unit = {},
    apps: List<App> = emptyList(), onAddApp: (App) -> Unit = {}, onAddSpecial: (GridItem) -> Unit = {},
) {
    if (!isShowing) return
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    var privacyDialog by remember { mutableStateOf(false) }
    var destinationDialog by remember { mutableStateOf(false) }
    var copyTarget by remember { mutableStateOf<String?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    val wallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onSettingsEvent(SettingsEvent.OnWallpaperPicked(it)) }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        val persisted = uris.mapNotNull { uri -> runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            uri.toString()
        }.getOrNull() }
        if (persisted.isNotEmpty()) onAddSpecial(GridItem(app = App("Photos", BuiltInTiles.PHOTOS), width = 2, height = 1, photoUris = persisted))
    }
    ModalBottomSheet(onDismissRequest = { onSettingsEvent(SettingsEvent.OnSettingsSheetDismissed) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Make it yours", style = MaterialTheme.typography.headlineLarge)
            Text("Start, with a personal touch.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { activity?.chooseDefaultLauncher() }) { Text("Set as default home app") }
            if (activity?.updater?.eligible == true) {
                val updateState by activity.updater.state.collectAsState()
                val autoUpdates by activity.updater.automatic.collectAsState()
                TextButton(enabled = !updateState.checking && !updateState.downloading, onClick = { activity.updater.check(manual = true) }) { Text(if (updateState.checking) "Checking GitHub…" else "Check for updates") }
                SettingSwitch("Check GitHub automatically", autoUpdates, activity.updater::setAutomatic)
            } else Text("Updates are managed by your app store.", style = MaterialTheme.typography.bodySmall)
            Text("Glass controls", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("frosted", "clear", "solid").forEach { finish -> FilterChip(selected = tileSettings.glassFinish == finish, onClick = { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(glassFinish = finish))) }, label = { Text(finish.replaceFirstChar { it.uppercase() }) }) }
            }
            Text("Tiles across · ${tileSettings.tilesAcross.coerceIn(2, 6)}", style = MaterialTheme.typography.titleMedium)
            Slider(value = tileSettings.tilesAcross.coerceIn(2, 6).toFloat(), onValueChange = { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(tilesAcross = kotlin.math.round(it).toInt()))) }, valueRange = 2f..6f, steps = 3)
            Text("2–6 whole cells per row. Default: 3. Pinned positions stay fixed; unpin them before choosing a width they cannot fit.", style = MaterialTheme.typography.bodySmall)
            Text("Accent color", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF0078D7, 0xFF008A00, 0xFFB4009E, 0xFFD24726, 0xFF643EBF, 0xFF006D77).forEach { color ->
                    Box(Modifier.size(40.dp).background(Color(color)).clickable {
                        onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(accentColor = color)))
                    }, contentAlignment = Alignment.Center) { if (tileSettings.accentColor == color) Text("✓", color = Color.White) }
                }
            }
            SettingSwitch("Use wallpaper colors", tileSettings.wallpaperTint) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(wallpaperTint = it))) }
            SettingSwitch("Reduce motion", tileSettings.reduceMotion) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(reduceMotion = it))) }
            SettingSwitch("One-handed layout", tileSettings.oneHanded) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(oneHanded = it))) }
            SettingSwitch("Show Now activities", tileSettings.showNow) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(showNow = it))) }
            SettingSwitch("Dark background", tileSettings.darkTheme) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(darkTheme = it))) }
            SettingSwitch("Live tiles", tileSettings.liveTilesEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(liveTilesEnabled = it))) }
            SettingSwitch("Rotate live content", tileSettings.isTileFlipEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(isTileFlipEnabled = it))) }
            SettingSwitch("Show notification previews", tileSettings.showNotificationText) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(showNotificationText = it))) }
            TextButton(onClick = { privacyDialog = true }) { Text("Choose apps allowed to show previews") }
            Text("Notification access enables counts. Previews stay on this device. Android may hide sensitive content.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Manage notification access") }
            SettingSwitch("Hide tile labels", tileSettings.isAppLabelsHidden) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(isAppLabelsHidden = it))) }
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
            Row { TextButton(onClick = { activity?.chooseContacts() }) { Text("Choose people") }; TextButton(onClick = { activity?.chooseDocument() }) { Text("Pin document") } }
            TextButton(onClick = { destinationDialog = true }) { Text("Pin a website or route") }
            TextButton(onClick = { folderDialog = true }) { Text("Create an app folder") }
            Text("Calendar and People ask for access when first opened. Use an Android weather or music widget for updates from your preferred provider.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Layouts", style = MaterialTheme.typography.titleLarge)
            Text("Current: $currentProfile. Personal, Work and Travel keep separate tile arrangements.")
            Row { tgo1014.gridlauncher.data.profileNames.filter { it != currentProfile }.forEach { name -> TextButton(onClick = { copyTarget = name }) { Text("Copy to $name") } } }
            SettingSwitch("Weekday Work schedule", tileSettings.workSchedule) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(workSchedule = it))) }
            if (tileSettings.workSchedule) {
                Text("Work: ${tileSettings.workStartHour}:00–${tileSettings.workEndHour}:00 · local time")
                Text("Start hour")
                Slider(tileSettings.workStartHour.toFloat(), { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(workStartHour = it.toInt()))) }, valueRange = 0f..23f, steps = 22)
                Text("End hour")
                Slider(tileSettings.workEndHour.toFloat(), { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(workEndHour = it.toInt()))) }, valueRange = 0f..23f, steps = 22)
                Text("Outside work hours: Personal. Choosing a layout manually turns the schedule off.", style = MaterialTheme.typography.bodySmall)
            }
            if (android.os.Build.VERSION.SDK_INT >= 37) {
                SettingSwitch("Continue Start on another device", tileSettings.handoffEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(handoffEnabled = it))) }
                Text("Android Handoff shares the selected layout name with your other compatible devices running GridLauncher. Each device keeps its own tiles. Nearby app suggestions depend on the system launcher.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Your layout", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = { activity?.exportLayout() }) { Text("Back up") }
                TextButton(onClick = { activity?.importLayout() }) { Text("Restore…") }
            }
            Text("Restoring replaces Start. Widgets and photo permissions must be added again on a new device.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
    copyTarget?.let { target -> AlertDialog(onDismissRequest = { copyTarget = null }, title = { Text("Replace $target layout?") }, text = { Text("Copy the current tiles to $target. Its previous arrangement will be replaced.") }, confirmButton = { TextButton(onClick = { onCopyProfile(target); copyTarget = null }) { Text("Copy") } }, dismissButton = { TextButton(onClick = { copyTarget = null }) { Text("Cancel") } }) }
    if (privacyDialog) AlertDialog(onDismissRequest = { privacyDialog = false }, title = { Text("Notification previews") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) { items(apps, key = { it.packageName }) { app ->
            SettingSwitch(app.name, app.packageName !in tileSettings.hiddenPreviewApps) { allowed -> onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(hiddenPreviewApps = if (allowed) tileSettings.hiddenPreviewApps - app.packageName else tileSettings.hiddenPreviewApps + app.packageName))) }
        } }
    }, confirmButton = { TextButton(onClick = { privacyDialog = false }) { Text("Done") } })
    if (destinationDialog) {
        var name by remember { mutableStateOf("") }; var target by remember { mutableStateOf("") }
        val uri = android.net.Uri.parse(target)
        val valid = name.isNotBlank() && ((uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) || (uri.scheme == "geo" && !uri.schemeSpecificPart.isNullOrBlank()))
        AlertDialog(onDismissRequest = { destinationDialog = false }, title = { Text("Pin destination") }, text = { Column {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") })
            OutlinedTextField(target, { target = it.take(2000) }, label = { Text("Website or geo: route link") })
            Text("Use an https:// website or a geo: map link. App conversations and playlists can be pinned from the app's shortcut menu.", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(enabled = valid, onClick = { onAddSpecial(GridItem(app = App(name.trim(), "grid://destination"), width = 1, destination = target)); destinationDialog = false }) { Text("Pin") } }, dismissButton = { TextButton(onClick = { destinationDialog = false }) { Text("Cancel") } })
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
            onAddSpecial(GridItem(app = App(name.trim(), BuiltInTiles.FOLDER), width = 1, children = apps.filter { it.packageName in selected }))
            folderDialog = false
        }) { Text("Pin folder") } }, dismissButton = { TextButton(onClick = { folderDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, change) }
}
