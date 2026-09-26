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
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val backupJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching {
                val backup = tgo1014.gridlauncher.live.LayoutBackup(tiles = appsManager.homeGridFlow.first(), settings = settingsRepository.tileSettingsFlow.first())
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
                val restored = backup.restore(appsManager.installedAppsFlow.first())
                val previous = appsManager.homeGridFlow.first()
                appsManager.setGrid(restored)
                previous.filter { it.widgetId >= 0 }.forEach { widgetHost.deleteAppWidgetId(it.widgetId) }
                settingsRepository.updateSettings(backup.settings.copy(wallpaperPath = null, cornerRadius = backup.settings.cornerRadius.coerceIn(0, 32)))
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidget = savedInstanceState?.getInt("pendingWidget", -1) ?: -1
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { GridLauncherTheme { HomeScreen(homeViewModel) } }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("pendingWidget", pendingWidget); super.onSaveInstanceState(outState) }
    override fun onStart() { super.onStart(); runCatching { widgetHost.startListening() } }
    override fun onStop() { widgetHost.stopListening(); super.onStop() }
    override fun onResume() { super.onResume(); lifecycleScope.launch { updateAppListUseCase() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (intent.action == Intent.ACTION_MAIN) homeViewModel.onGoToHome() }

    fun chooseDefaultLauncher() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)); return
            }
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
                app = App(info.loadLabel(packageManager), BuiltInTiles.WIDGET), width = 4, height = 2, widgetId = id)
            appsManager.setGrid(grid + GridPlacement.place(tile, grid))
        }
    }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
