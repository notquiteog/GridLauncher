package tgo1014.gridlauncher.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.data.AppsManagerDataSourceImpl
import tgo1014.gridlauncher.data.SettingsRepositoryImpl
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.Icon
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.ui.models.GridItem
import java.io.File

/**
 * The folder editor's whole persistence path, on the JVM: the write the sheet asks for, and every
 * pass the storage layer makes over the stored grid afterwards.
 */
class FolderPersistenceTest {

    private fun dataSource(name: String): Pair<AppsManagerDataSourceImpl, SettingsRepositoryImpl> {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
            File.createTempFile(name, ".preferences_pb").absoluteFile.toPath().toOkioPath()
        }
        return AppsManagerDataSourceImpl(Json, store) to SettingsRepositoryImpl(Json, store)
    }

    private fun folder(children: List<App>, pinned: Boolean = false) =
        GridItem(1, App("Tools", BuiltInTiles.FOLDER), 2, children = children, positionPinned = pinned)
    private fun battery() = GridItem(2, App("Battery", BuiltInTiles.BATTERY), 1, x = 2)
    private val clock = App("Clock", BuiltInTiles.CLOCK)
    private val batteryApp = App("Battery", BuiltInTiles.BATTERY)

    /** What the sheet's row does, then what the view model does with the result. */
    private suspend fun AppsManagerDataSourceImpl.toggle(folderId: Int, app: App): List<GridItem> {
        val shown = homeGridFlow.first().first { it.id == folderId }
        setGrid(GridPlacement.update(homeGridFlow.first(), FolderEdit.toggled(shown, app), TileSettings().gridColumns))
        return homeGridFlow.first()
    }

    @Test fun addingAnAppToAFolderIsStoredAndObserversSeeIt() = runTest {
        val (apps, _) = dataSource("folder-add")
        apps.setGrid(listOf(folder(listOf(clock)), battery()))

        val stored = apps.toggle(folderId = 1, app = batteryApp).first { it.id == 1 }

        assertEquals(listOf("grid://clock", "grid://battery"), stored.children.map { it.packageName })
        assertTrue("the edit must replace the tile, not add one", apps.homeGridFlow.first().count { it.id == 1 } == 1)
    }

    /**
     * The regression. `MainActivity.onResume` calls `updateAppListUseCase`, which rewrites every
     * stored grid to prune uninstalled apps. That pass used to drop hub tiles from folder contents
     * too, so a folder looked edited for a moment and then reverted on the next app-list refresh.
     */
    @Test fun aFolderKeepsItsHubChildrenAcrossAPackageRefresh() = runTest {
        val (apps, _) = dataSource("folder-refresh")
        val installed = listOf(App("Settings", "com.android.settings"), App("Camera", "com.android.camera2"))
        apps.setAppList(installed)
        apps.setGrid(listOf(folder(listOf(clock)), battery()))
        apps.toggle(folderId = 1, app = batteryApp)

        apps.setAppList(installed + App("Files", "com.android.documentsui"))

        val children = apps.homeGridFlow.first().first { it.id == 1 }.children
        assertEquals(listOf("grid://clock", "grid://battery"), children.map { it.packageName })
    }

    @Test fun aFolderStillLosesChildrenThatWereUninstalled() = runTest {
        val (apps, _) = dataSource("folder-uninstall")
        val settings = App("Settings", "com.android.settings")
        apps.setAppList(listOf(settings))
        apps.setGrid(listOf(folder(listOf(settings, clock))))

        apps.setAppList(listOf(App("Camera", "com.android.camera2")))

        assertEquals(listOf("grid://clock"), apps.homeGridFlow.first().first { it.id == 1 }.children.map { it.packageName })
    }

    @Test fun removingAnAppFromAFolderIsStored() = runTest {
        val (apps, _) = dataSource("folder-remove")
        apps.setGrid(listOf(folder(listOf(clock)), battery()))
        apps.toggle(folderId = 1, app = batteryApp)

        val stored = apps.toggle(folderId = 1, app = batteryApp).first { it.id == 1 }

        assertEquals(listOf("grid://clock"), stored.children.map { it.packageName })
    }

    @Test fun aChildIsRemovedByPackageEvenWhenItsStoredIconIsStale() {
        val stored = batteryApp.copy(icon = Icon(edgeColor = 0xFF112233L))

        assertTrue(FolderEdit.contains(folder(listOf(stored)), batteryApp))
        assertEquals(listOf(clock), FolderEdit.toggled(folder(listOf(clock, stored)), batteryApp).children)
    }

    @Test fun addingAndRemovingNeverDuplicatesAnEntry() {
        val once = FolderEdit.toggled(folder(listOf(clock)), batteryApp)
        assertEquals(listOf(clock, batteryApp), once.children)
        assertEquals(listOf(clock), FolderEdit.toggled(once, batteryApp).children)
    }

    @Test fun folderCandidatesAreEveryInstalledAppPlusTheHubTiles() {
        val installed = listOf(App("Settings", "com.android.settings"), App("Settings", "com.android.settings"))
        val candidates = FolderEdit.candidates(installed)

        assertEquals((BuiltInTiles.apps.map { it.packageName } + "com.android.settings").toSet(),
            candidates.map { it.packageName }.toSet())
        // A hub tile and an installed app can share a name; they are still two rows, matched by package.
        assertEquals(candidates.size, candidates.map { it.packageName }.distinct().size)
    }

    /** `update`, `place` and `firstFree` all rebuild tiles with `copy`, which must not drop children. */
    @Test fun packingAndRepackingAFolderKeepItsChildren() {
        val children = listOf(clock, batteryApp)
        val crowded = listOf(folder(children), GridItem(3, App("a", "grid://a"), 1), GridItem(4, App("b", "grid://b"), 1, x = 1), GridItem(5, App("c", "grid://c"), 1, x = 2))
        // A pinned tile is an anchor, so each of these is only repacked into a grid wide enough to hold it.
        val anchored = listOf(
            listOf(folder(children, pinned = true), battery()),
            listOf(folder(children), battery().copy(positionPinned = true)),
        )
        listOf(listOf(folder(children), battery()), crowded).forEach { grid ->
            listOf(2, 3, 6).forEach { columns -> assertKeepsChildren(children, grid, columns) }
        }
        anchored.forEach { grid -> listOf(3, 6).forEach { columns -> assertKeepsChildren(children, grid, columns) } }
        assertEquals(children, GridPlacement.place(folder(children), crowded.drop(1), 3).children)
    }

    private fun assertKeepsChildren(children: List<App>, grid: List<GridItem>, columns: Int) {
        listOf(GridPlacement.update(grid, grid.first(), columns), GridPlacement.reflow(grid, columns), GridPlacement.compact(grid, columns))
            .forEach { packed -> assertEquals("packed at $columns", children, packed.first { it.id == 1 }.children) }
    }

    /** The full device-test sequence, including the `onResume` refresh that used to undo the edit. */
    @Test fun theSheetEditSurvivesTheResumeThatFollowsIt() = runTest {
        val (apps, settings) = dataSource("folder-resume")
        val installed = listOf(App("Settings", "com.android.settings"))
        apps.setAppList(installed)
        settings.updateSettings(TileSettings())
        apps.setGrid(listOf(folder(listOf(clock)), battery()))

        apps.toggle(folderId = 1, app = batteryApp)
        apps.setAppList(installed)
        assertTrue(apps.homeGridFlow.first().first { it.id == 1 }.children.any { it.packageName == BuiltInTiles.BATTERY })

        apps.toggle(folderId = 1, app = batteryApp)
        apps.setAppList(installed)
        assertEquals(listOf(clock), apps.homeGridFlow.first().first { it.id == 1 }.children)
    }
}
