package tgo1014.gridlauncher.ui.composables.sheets

import androidx.activity.compose.LocalActivity

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import tgo1014.gridlauncher.ui.theme.AsyncImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
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
    currentProfile: String = "Personal", onCopyProfile: (String) -> Unit = {}, layouts: List<String> = tgo1014.gridlauncher.data.builtinProfileNames,
    apps: List<App> = emptyList(), onAddApp: (App) -> Unit = {}, onAddSpecial: (GridItem) -> Unit = {},
) {
    if (!isShowing) return
    val context = LocalContext.current
    var quietTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(isShowing) { if (isShowing) quietTick++ }
    val activity = LocalActivity.current as? MainActivity
    var privacyDialog by remember { mutableStateOf(false) }
    var destinationDialog by remember { mutableStateOf(false) }
    var copyTarget by remember { mutableStateOf<String?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var contactTileDialog by remember { mutableStateOf(false) }
    var groupDialog by remember { mutableStateOf(false) }
    var exportTheme by remember { mutableStateOf(false) }
    var importTheme by remember { mutableStateOf(false) }
    var messageSent by remember { mutableStateOf(false) }
    var snack by remember { mutableStateOf<String?>(null) }
    val quietGranted = remember(quietTick) { tgo1014.gridlauncher.live.QuietHours.granted(context) }
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
                listOf("frosted", "clear", "acrylic", "solid").forEach { finish -> FilterChip(selected = tileSettings.glassFinish == finish, onClick = { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(glassFinish = finish))) }, label = { Text(finish.replaceFirstChar { it.uppercase() }) }) }
            }
            Text("Acrylic is the sharpest material; Frosted is the softest.", style = MaterialTheme.typography.bodySmall)
            Text("Tiles across · ${tileSettings.tilesAcross.coerceIn(2, 6)}", style = MaterialTheme.typography.titleMedium)
            HourOrStepSlider(tileSettings.tilesAcross.coerceIn(2, 6).toFloat(), 2f..6f, 3, "Tiles") { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(tilesAcross = it))) }
            Text("2–6 whole cells per row. Default: 3. Pinned positions stay fixed; unpin them before choosing a width they cannot fit.", style = MaterialTheme.typography.bodySmall)
            Text("Accent color", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF0078D7, 0xFF008A00, 0xFFB4009E, 0xFFD24726, 0xFF643EBF, 0xFF006D77).forEach { color ->
                    val name = tgo1014.gridlauncher.ui.theme.accentName(color)
                    Box(Modifier.size(48.dp).selectable(selected = tileSettings.accentColor == color,
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                        onClick = { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(accentColor = color))) })
                        .semantics { contentDescription = name }
                        .background(Color(color)), contentAlignment = Alignment.Center) {
                        if (tileSettings.accentColor == color) Text("✓", color = if (Color(color).luminance() > .5f) Color.Black else Color.White)
                    }
                }
            }
            SettingSwitch("Use wallpaper colors", tileSettings.wallpaperTint) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(wallpaperTint = it))) }
            SettingSwitch("Reduce motion", tileSettings.reduceMotion) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(reduceMotion = it))) }
            SettingSwitch("One-handed layout", tileSettings.oneHanded) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(oneHanded = it))) }
            SettingSwitch("Start header", tileSettings.showStartHeader) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(showStartHeader = it))) }
            SettingSwitch("Start jump list", tileSettings.semiPanoramic) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(semiPanoramic = it))) }
            Text("Puts an A\u2013Z jump list down the left edge of Start. Tap a letter to scroll to the first tile that starts with it; press and hold for the whole alphabet.", style = MaterialTheme.typography.bodySmall)
            Text("All apps order", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("alphabetical" to "A–Z", "frequent" to "Most used", "recent" to "Recent").forEach { (key, label) ->
                    FilterChip(selected = tileSettings.drawerSort == key,
                        onClick = { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(drawerSort = key))) }, label = { Text(label) })
                }
            }
            Text("Most used and Recent order the drawer by the apps you actually open, counted on this device.", style = MaterialTheme.typography.bodySmall)
            SettingSwitch("Fullscreen", tileSettings.fullscreen) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(fullscreen = it))) }
            Text("Hides the status and navigation bars. Android brings them back with a swipe from the edge.", style = MaterialTheme.typography.bodySmall)
            SettingSwitch("Tinted icons", tileSettings.iconTint) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(iconTint = it))) }
            Text("Renders every app icon in one flat color, drawn by the launcher from its own cache.", style = MaterialTheme.typography.bodySmall)
            SettingSwitch("Hotseat", tileSettings.hotseat.isNotEmpty()) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(hotseat = if (it) apps.take(4).map { app -> app.packageName } else emptyList()))) }
            if (tileSettings.hotseat.isNotEmpty()) {
                Text("The hotseat holds ${tileSettings.hotseat.size} of 4 apps at the foot of Start, outside the tile grid. Use the + on a frequent app to add it.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    apps.filter { it.packageName in tileSettings.hotseat }.forEach { app ->
                        AssistChip(onClick = { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(hotseat = tileSettings.hotseat - app.packageName))) },
                            label = { Text("Remove ${app.name}") })
                    }
                }
            }
            SettingSwitch("Show Now activities", tileSettings.showNow) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(showNow = it))) }
            SettingSwitch("Dark background", tileSettings.darkTheme) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(darkTheme = it))) }
            SettingSwitch("Live tiles", tileSettings.liveTilesEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(liveTilesEnabled = it))) }
            SettingSwitch("Rotate live content", tileSettings.isTileFlipEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(isTileFlipEnabled = it))) }
            SettingSwitch("Show notification previews", tileSettings.showNotificationText) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(showNotificationText = it))) }
            SettingSwitch("Stack notifications on wide tiles", tileSettings.stackNotifications) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(stackNotifications = it))) }
            SettingSwitch("Show tile counts", tileSettings.showTileCounts) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(showTileCounts = it))) }
            SettingSwitch("Counts as dots", tileSettings.badgeAsDot) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(badgeAsDot = it))) }
            TextButton(onClick = { privacyDialog = true }) { Text("Choose apps allowed to show previews") }
            Text("Notification access enables counts. Previews stay on this device. Android may hide sensitive content.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Manage notification access") }
            TextButton(onClick = {
                if (tgo1014.gridlauncher.live.LiveTileWidget.requestPin(context)) messageSent = true
                else runCatching { context.startActivity(Intent("android.intent.action.APPWIDGET_PICK").putExtra("appWidgetId", -1).putExtra("appWidgetProvider", "io.github.notquiteog.gridlauncher/tgo1014.gridlauncher.live.GridLiveTileProvider")) }
                    .onFailure { messageSent = false }
            }) { Text("Add live tile to your home screen") }
            if (messageSent) Text("Live tile added. It follows the same privacy rules as your Start tiles.", style = MaterialTheme.typography.bodySmall)
            SettingSwitch("Hide tile labels", tileSettings.isAppLabelsHidden) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(isAppLabelsHidden = it))) }
            Row {
                TextButton(onClick = { wallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose your own wallpaper") }
                if (tileSettings.wallpaperPath != null) TextButton(onClick = { onSettingsEvent(SettingsEvent.OnWallpaperRemoved) }) { Text("Use the system wallpaper") }
            }
            Text("Without one of your own, Start uses Android's current wallpaper, live wallpaper included.", style = MaterialTheme.typography.bodySmall)
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
            TextButton(onClick = { contactTileDialog = true }) { Text("Pin a person\u2019s photo") }
            Text("Puts their photo and latest conversation on Start, the way a face sat on the Windows Phone grid.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { destinationDialog = true }) { Text("Pin a website or route") }
            TextButton(onClick = { folderDialog = true }) { Text("Create an app folder") }
            TextButton(onClick = { groupDialog = true }) { Text("Add a group header") }
            Text("Group headers span the full width of your grid and separate sections, the way Windows Phone grouped its tiles.", style = MaterialTheme.typography.bodySmall)
            Text("Calendar and People ask for access when first opened. Use an Android weather or music widget for updates from your preferred provider.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Layouts", style = MaterialTheme.typography.titleLarge)
            Text("Current: $currentProfile. Every layout keeps its own tiles, and you can create, rename or delete your own.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                layouts.filter { it != currentProfile }.forEach { name -> TextButton(onClick = { copyTarget = name }) { Text("Copy to $name") } }
            }
            SettingSwitch("Weekday Work schedule", tileSettings.workSchedule) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(workSchedule = it))) }
            if (tileSettings.workSchedule) {
                Text("Work: ${tileSettings.workStartHour}:00–${tileSettings.workEndHour}:00 · local time")
                Text("Start hour")
                HourOrStepSlider(tileSettings.workStartHour.toFloat(), 0f..23f, 22, "Start hour") { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(workStartHour = it))) }
                Text("End hour")
                HourOrStepSlider(tileSettings.workEndHour.toFloat(), 0f..23f, 22, "End hour") { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(workEndHour = it))) }
                Text("Outside work hours: Personal. Choosing a layout manually turns the schedule off.", style = MaterialTheme.typography.bodySmall)
            }
            SettingSwitch("Continue Start on another device", tileSettings.handoffEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(handoffEnabled = it))) }
            Text("Android Handoff shares the selected layout name and the tile you were on with your other compatible devices running GridLauncher. Each device keeps its own tiles. Nearby app suggestions depend on the system launcher.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Quiet hours", style = MaterialTheme.typography.titleLarge)
            SettingSwitch("Meeting mode", tileSettings.meetingMode) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(meetingMode = it))) }
            SettingSwitch("Schedule quiet hours", tileSettings.quietHoursEnabled) { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(quietHoursEnabled = it))) }
            if (tileSettings.quietHoursEnabled) {
                Text("${tgo1014.gridlauncher.live.QuietHours.hours(tileSettings)} · ${tgo1014.gridlauncher.live.QuietHours.nextChange(tileSettings)}")
                Text("Quiet from")
                HourOrStepSlider(tileSettings.quietStartHour.toFloat(), 0f..23f, 22, "Quiet from") { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(quietStartHour = it))) }
                Text("Quiet until")
                HourOrStepSlider(tileSettings.quietEndHour.toFloat(), 0f..23f, 22, "Quiet until") { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(quietEndHour = it))) }
            }
            if (quietGranted) {
                Text("Android Do Not Disturb follows this schedule. Tiles hide counts and live text while quiet.", style = MaterialTheme.typography.bodySmall)
            } else {
                TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                    .onFailure { snack = "Open Android's Do Not Disturb access settings to silence notifications too." } }) { Text("Allow Do Not Disturb access") }
                Text("Without the grant, Start still hides counts and live text on your own schedule.", style = MaterialTheme.typography.bodySmall)
            }
            if (snack != null) Text(snack!!, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Your layout", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = { activity?.exportLayout() }) { Text("Back up") }
                TextButton(onClick = { activity?.importLayout() }) { Text("Restore…") }
            }
            Text("Restoring replaces Start. Widgets and photo permissions must be added again on a new device.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Theme pack", style = MaterialTheme.typography.titleLarge)
            Text("Share the accent, background and tile size without sharing your apps.")
            Row {
                TextButton(onClick = { onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(accentColor = 0xFF0078D7, glassFinish = "frosted", tilesAcross = 3, darkTheme = true, wallpaperPath = null))) }) { Text("Reset look") }
                TextButton(onClick = { exportTheme = true }) { Text("Copy theme code") }
                TextButton(onClick = { importTheme = true }) { Text("Paste theme code") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (exportTheme) {
        val code = tgo1014.gridlauncher.live.ThemePacks.encode(tileSettings)
        AlertDialog(onDismissRequest = { exportTheme = false }, title = { Text("Theme code") }, text = {
            Column {
                SelectionContainer { Text(code, style = MaterialTheme.typography.bodyMedium) }
                Text("Paste this into someone else's Customize Start. It carries your look only, never your apps.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            TextButton(onClick = {
                runCatching { context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("GridLauncher theme", code)) }
                snack = "Theme code copied"
                exportTheme = false
            }) { Text("Copy") }
        }, dismissButton = { TextButton(onClick = { exportTheme = false }) { Text("Close") } })
    }
    if (importTheme) {
        var code by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { importTheme = false }, title = { Text("Apply theme code") }, text = {
            Column {
                OutlinedTextField(code, { code = it.take(200) }, label = { Text("Theme code") }, singleLine = true)
                Text("Only the look changes: accent, material, background, tile width and live-tile behaviour. Your apps, tiles, wallpaper, quiet hours and preview privacy are left alone.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            val decoded = remember(code) { tgo1014.gridlauncher.live.ThemePacks.decode(code) }
            TextButton(enabled = decoded != null, onClick = {
                // Merge onto the current settings: a theme code carries a look, not your whole profile.
                decoded?.let { patch -> onSettingsEvent(SettingsEvent.OnSettingsUpdated(tileSettings.copy(
                    accentColor = patch.accentColor, glassFinish = patch.glassFinish, tilesAcross = patch.tilesAcross,
                    darkTheme = patch.darkTheme, isTileFlipEnabled = patch.isTileFlipEnabled,
                    showNotificationText = patch.showNotificationText, reduceMotion = patch.reduceMotion,
                    isAppLabelsHidden = patch.isAppLabelsHidden, stackNotifications = patch.stackNotifications))) }
                importTheme = false
            }) { Text("Apply") }
        }, dismissButton = { TextButton(onClick = { importTheme = false }) { Text("Cancel") } })
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
    if (groupDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { groupDialog = false }, title = { Text("Add group header") }, text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Group name") }, singleLine = true)
                Text("The header spans all ${tileSettings.gridColumns} columns. Tiles added after it land underneath.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            onAddSpecial(GridItem(app = App(name.trim(), BuiltInTiles.GROUP), width = tileSettings.gridColumns, height = 1, groupLabel = name.trim()))
            groupDialog = false
        }) { Text("Add") } }, dismissButton = { TextButton(onClick = { groupDialog = false }) { Text("Cancel") } })
    }
    if (contactTileDialog) {
        val favorites = androidx.compose.runtime.remember { tgo1014.gridlauncher.live.ContactTiles.favorites(context) }
        AlertDialog(onDismissRequest = { contactTileDialog = false }, title = { Text("Pin a person") }, text = {
            if (favorites.isEmpty()) Text("Star a contact, or choose people in Customize Start, and they can be pinned here.")
            else LazyColumn(Modifier.heightIn(max = 340.dp)) { items(favorites, key = { it.key }) { person ->
                Row(Modifier.fillMaxWidth().clickable {
                    onAddSpecial(GridItem(app = App(person.name, BuiltInTiles.CONTACTS), width = 2, contact = person))
                    contactTileDialog = false
                }, verticalAlignment = Alignment.CenterVertically) {
                    if (person.photo != null) AsyncImage(person.photo, Modifier.size(40.dp).clip(CircleShape))
                    else Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase(), color = Color.White) }
                    Text(person.name, Modifier.padding(start = 12.dp))
                }
            } }
        }, confirmButton = { TextButton(onClick = { contactTileDialog = false }) { Text("Close") } })
    }
    if (folderDialog) {
        var name by remember { mutableStateOf("") }
        var selected by remember { mutableStateOf(setOf<String>()) }
        var nested by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { folderDialog = false }, title = { Text("Create folder") }, text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Folder name") }, singleLine = true)
                TextButton(onClick = { nested = if (nested == null) "" else null }) {
                    Text(if (nested == null) "Put a folder inside" else "Remove the inner folder")
                }
                if (nested != null) {
                    OutlinedTextField(nested.orEmpty(), { nested = it.take(40) }, label = { Text("Inner folder name") }, singleLine = true)
                    Text("Built-in tiles you tick here land in the inner folder. Apps go in the outer one.", style = MaterialTheme.typography.bodySmall)
                    BuiltInTiles.apps.forEach { hub ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (hub.packageName in selected) selected - hub.packageName else selected + hub.packageName
                        }) { Checkbox(hub.packageName in selected, null); Text(hub.name) }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) { items(apps.filter { nested == null || !it.packageName.startsWith("grid://") }, key = { it.packageName }) { app ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                    }) { Checkbox(app.packageName in selected, null); Text(app.name) }
                } }
            }
        }, confirmButton = { TextButton(enabled = name.isNotBlank() && (selected.isNotEmpty() || nested != null), onClick = {
            val inner = nested
            val hubs = if (inner != null) BuiltInTiles.apps.filter { it.packageName in selected } else emptyList()
            onAddSpecial(GridItem(app = App(name.trim(), BuiltInTiles.FOLDER), width = 1,
                children = apps.filter { it.packageName in selected && it.packageName !in hubs.map { hub -> hub.packageName } },
                childFolders = if (inner != null) listOf(GridItem(app = App(inner.ifBlank { "Folder" }, BuiltInTiles.FOLDER), width = 1, children = hubs)) else emptyList()))
            folderDialog = false
        }) { Text("Pin folder") } }, dismissButton = { TextButton(onClick = { folderDialog = false }) { Text("Cancel") } })
    }
}

/** The whole row is the control, so the label is spoken and tapping it toggles. */
@Composable
private fun SettingSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(vertical = 8.dp))
        Switch(checked, null)
    }
}

/** Holds the value locally so a drag writes the setting once, on release. */
@Composable
private fun HourOrStepSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: String, commit: (Int) -> Unit) {
    var held by remember(value) { mutableStateOf(value) }
    Slider(held, onValueChange = { held = it }, valueRange = range, steps = steps,
        modifier = Modifier.semantics { contentDescription = label },
        onValueChangeFinished = { commit(kotlin.math.round(held).toInt()) })
}
