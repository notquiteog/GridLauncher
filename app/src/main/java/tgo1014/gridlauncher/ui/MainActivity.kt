package tgo1014.gridlauncher.ui

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.R
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.GridPlacement
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.usecases.UpdateAppListUseCase
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.ui.home.HomeScreen
import tgo1014.gridlauncher.ui.home.HomeScreenViewModel
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var updateAppListUseCase: UpdateAppListUseCase
    @Inject lateinit var appsManager: AppsManager
    @Inject lateinit var settingsRepository: tgo1014.gridlauncher.domain.SettingsRepository
    @Inject lateinit var profiles: tgo1014.gridlauncher.data.LayoutProfiles
    @Inject lateinit var usage: tgo1014.gridlauncher.data.UsageTracker
    val updater: tgo1014.gridlauncher.updates.GitHubUpdater by viewModels()
    private val allowUpdates = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) installGitHubUpdate()
    }
    fun installGitHubUpdate() {
        if (!updater.eligible) return
        val apk = updater.state.value.apk ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            runCatching { allowUpdates.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:$packageName"))) }
                .onFailure { message("Allow installs from GridLauncher in Android settings, then try again.") }
            return
        }
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.updates", apk)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { message("Android could not open the package installer.") }
    }
    private val backupJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching {
                val settings = settingsRepository.tileSettingsFlow.first()
                fun portable(grid: List<tgo1014.gridlauncher.ui.models.GridItem>) = grid.filter { it.contact == null && it.contacts.isEmpty() && it.destination == null && it.shortcutId == null && it.widgetId < 0 && it.photoUris.isEmpty() }
                val everyLayout = profiles.allGrids().mapValues { (_, grid) -> portable(grid) }
                val backup = tgo1014.gridlauncher.live.LayoutBackup(
                    tiles = portable(appsManager.homeGridFlow.first()),
                    settings = settings, layouts = profiles.layouts.first(),
                    theme = tgo1014.gridlauncher.live.ThemePacks.encode(settings), layoutGrids = everyLayout)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backupJson.encodeToString(tgo1014.gridlauncher.live.LayoutBackup.serializer(), backup)) }
                        ?: error("Cannot write backup")
                }
            }.onSuccess { message("Layout backed up") }.onFailure { message("Backup failed: ${it.message}") }
        }
    }
    private val importDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching {
                val backup = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) { val count = stream.read(chunk); if (count < 0) break; require(buffer.size() + count <= 2_000_000) { "Backup is too large" }; buffer.write(chunk, 0, count) }
                        buffer.toByteArray()
                    } ?: error("Cannot read backup")
                    backupJson.decodeFromString(tgo1014.gridlauncher.live.LayoutBackup.serializer(), bytes.decodeToString())
                }
                profiles.importCustom(backup.layouts)
                val installed = appsManager.installedAppsFlow.first()
                val restored = backup.allGrids().mapValues { (_, grid) -> backup.restore(listOf(), grid, installed) }
                val previous = appsManager.homeGridFlow.first()
                appsManager.setGrid(restored[profiles.active.first()] ?: backup.restore(installed))
                if (restored.isNotEmpty()) profiles.replaceGrids(restored)
                previous.filter { it.widgetId >= 0 }.forEach { if (!profiles.widgetInUse(it.widgetId)) widgetHost.deleteAppWidgetId(it.widgetId) }
                val themed = backup.theme.takeIf { it.isNotBlank() }?.let(tgo1014.gridlauncher.live.ThemePacks::decode)
                settingsRepository.updateSettings((themed?.let { tileSettings -> tileSettings.copy(wallpaperPath = null) }
                    ?: backup.settings.copy(wallpaperPath = null)))
            }.onSuccess { message("Layout restored. Re-add photos and widgets if needed.") }.onFailure { message("Restore failed: ${it.message}") }
        }
    }
    fun exportLayout() { exportDocument.launch("gridlauncher-layout.json") }
    fun importLayout() {
        android.app.AlertDialog.Builder(this).setTitle("Replace your Start layout?")
            .setMessage("Choose a GridLauncher backup. Your current tiles will be replaced. Widgets and photo tiles must be added again.")
            .setNegativeButton("Cancel", null).setPositiveButton("Choose backup") { _, _ -> importDocument.launch(arrayOf("application/json", "text/plain")) }.show()
    }
    private val homeViewModel: HomeScreenViewModel by viewModels()
    val widgetHost by lazy { AppWidgetHost(this, 1701) }
    private var pendingWidget = -1
    private var returningFromApp = false

    /** Lets Start fade itself back in the next time this activity resumes. */
    fun onAppLaunched() { returningFromApp = true }
    private val packageUpdates = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) { lifecycleScope.launch { updateAppListUseCase() } }
    }
    private val configureWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) finishWidget() else cancelWidget()
    }
    private val pickWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != RESULT_OK) { cancelWidget(); return@registerForActivityResult }
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(pendingWidget)
        if (info == null) { cancelWidget(); return@registerForActivityResult }
        if (info.configure != null) {
            runCatching { configureWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget)) }
                .onFailure { cancelWidget(); message("This widget could not be configured") }
        } else finishWidget()
    }
    private val pickContacts = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) lifecycleScope.launch {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { tgo1014.gridlauncher.live.ContactTiles.read(this@MainActivity, uri) } }
                .onSuccess { contacts ->
                    if (contacts.isNotEmpty()) {
                        homeViewModel.addSpecialTile(GridItem(app = App(if (contacts.size == 1) contacts.first().name else "People", "grid://contacts"), width = 1,
                            contact = contacts.singleOrNull(), contacts = contacts))
                    }
                }.onFailure { message("Could not read selected contacts") }
        }
    }
    fun chooseContacts() { runCatching { pickContacts.launch(tgo1014.gridlauncher.live.ContactTiles.picker()) }.onFailure { message("The contact picker is unavailable on this device") } }
    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Document"
                homeViewModel.addSpecialTile(GridItem(app = App(name.take(80), "grid://destination"), width = 1, destination = uri.toString()))
            }.onFailure { message("This document cannot be pinned") }
        }
    }
    fun chooseDocument() { pickDocument.launch(arrayOf("*/*")) }
    fun pinShortcut(app: App, shortcut: android.content.pm.ShortcutInfo) {
        val launcher = getSystemService(android.content.pm.LauncherApps::class.java)
        runCatching {
            val existing = launcher.getShortcuts(android.content.pm.LauncherApps.ShortcutQuery().setPackage(app.packageName).setQueryFlags(android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), android.os.Process.myUserHandle()).orEmpty().map { it.id }
            launcher.pinShortcuts(app.packageName, (existing + shortcut.id).distinct(), android.os.Process.myUserHandle())
            homeViewModel.addSpecialTile(GridItem(app = app.copy(name = shortcut.shortLabel?.toString() ?: app.name), width = 1, shortcutId = shortcut.id))
        }.onFailure { message("Set GridLauncher as your default home app to pin shortcuts") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidget = savedInstanceState?.getInt("pendingWidget", -1) ?: -1
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        lifecycleScope.launch { settingsRepository.tileSettingsFlow.collect { settings ->
            setHandoffEnabled(settings.handoffEnabled, android.app.HandoffActivityParams.Builder().build())
        } }
        receiveHandoff(intent)
        receivePinRequest(intent)
        setContent {
            val settings by settingsRepository.tileSettingsFlow.collectAsState(initial = tgo1014.gridlauncher.domain.models.TileSettings())
            GridLauncherTheme(accent = androidx.compose.ui.graphics.Color(settings.accentColor.toInt()), dark = settings.darkTheme) {
                HomeScreen(homeViewModel)
                tgo1014.gridlauncher.updates.UpdatePrompt(updater, this)
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("pendingWidget", pendingWidget); super.onSaveInstanceState(outState) }
    override fun onStart() {
        super.onStart(); runCatching { widgetHost.startListening() }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(this, packageUpdates, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }
    override fun onStop() { unregisterReceiver(packageUpdates); widgetHost.stopListening(); tgo1014.gridlauncher.live.SensorTiles.stop(this); super.onStop() }
    override fun onResume() {
        super.onResume()
        if (returningFromApp) returningFromApp = false
        updater.check()
        tgo1014.gridlauncher.live.MediaTiles.refresh(this)
        tgo1014.gridlauncher.live.SensorTiles.start(this)
        lifecycleScope.launch { updateAppListUseCase() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); receiveHandoff(intent); receivePinRequest(intent); if (intent.action == Intent.ACTION_MAIN) homeViewModel.onGoToHome() }

    private fun receivePinRequest(intent: Intent?) {
        if (intent?.action != android.content.pm.LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) return
        val launcher = getSystemService(android.content.pm.LauncherApps::class.java)
        val request = launcher.getPinItemRequest(intent) ?: return
        val shortcut = request.shortcutInfo ?: return
        if (!request.isValid) return
        android.app.AlertDialog.Builder(this).setTitle("Pin ${shortcut.shortLabel}?")
            .setMessage("Add this app destination to your current Start layout.")
            .setNegativeButton("Cancel", null).setPositiveButton("Pin") { _, _ ->
                runCatching {
                    if (request.accept()) lifecycleScope.launch {
                        val app = appsManager.installedAppsFlow.first().firstOrNull { it.packageName == shortcut.`package` }
                            ?: App(shortcut.shortLabel?.toString() ?: "Shortcut", shortcut.`package`)
                        homeViewModel.addSpecialTile(GridItem(app = app.copy(name = shortcut.shortLabel?.toString() ?: app.name), width = 1, shortcutId = shortcut.id))
                    }
                }.onFailure { message("This shortcut is no longer available") }
            }.show()
    }

    override fun onHandoffActivityDataRequested(requestInfo: android.app.HandoffActivityDataRequestInfo): android.app.HandoffActivityData =
        android.app.HandoffActivityData.Builder(android.content.ComponentName(this, MainActivity::class.java))
            .setExtras(android.os.PersistableBundle().apply {
                putString("grid.profile", homeViewModel.stateFlow.value.profile)
                homeViewModel.stateFlow.value.grid.firstOrNull { it.id == homeViewModel.stateFlow.value.handoffFocus }?.let { putInt("grid.focus", it.id) }
            }).build()
    private fun receiveHandoff(intent: Intent?) {
        val name = intent?.getStringExtra("grid.profile")?.takeIf { it.isNotBlank() } ?: return
        homeViewModel.adoptHandoffLayout(name, intent.getIntExtra("grid.focus", -1).takeIf { it >= 0 })
    }

    private fun animationsReduced() = runCatching {
        android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false) || !android.animation.ValueAnimator.areAnimatorsEnabled()

    fun chooseDefaultLauncher() {
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)); return
        }
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }
    fun addWidget() {
        cancelWidget()
        pendingWidget = widgetHost.allocateAppWidgetId()
        runCatching { pickWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget)) }
            .onFailure { cancelWidget(); message("No widget picker is available on this device") }
    }
    private fun cancelWidget() { if (pendingWidget != -1) widgetHost.deleteAppWidgetId(pendingWidget); pendingWidget = -1 }
    private fun finishWidget() {
        val id = pendingWidget
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id) ?: return cancelWidget()
        pendingWidget = -1
        lifecycleScope.launch {
            val grid = appsManager.homeGridFlow.first()
            val tile = GridItem(id = (grid.maxOfOrNull { it.id } ?: -1) + 1,
                app = App(info.loadLabel(packageManager), BuiltInTiles.WIDGET), width = 2, height = 1, widgetId = id)
            appsManager.setGrid(grid + GridPlacement.place(tile, grid, settingsRepository.tileSettingsFlow.first().gridColumns))
        }
    }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
