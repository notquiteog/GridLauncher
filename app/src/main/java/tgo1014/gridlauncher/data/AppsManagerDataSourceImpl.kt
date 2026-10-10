package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tgo1014.gridlauncher.domain.AppsManagerDataSource
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import javax.inject.Inject

class AppsManagerDataSourceImpl @Inject constructor(
    private val json: Json,
    private val dataStore: DataStore<Preferences>,
) : AppsManagerDataSource {

    private val appListKey = stringPreferencesKey("appListKey")
    private val gridKey = stringPreferencesKey("gridKey")

    override var installedAppsList: Flow<List<App>> = dataStore.data
        .map {
            runCatching {
                json.decodeFromString<List<App>>(it[appListKey]!!)
            }.getOrDefault(emptyList())
        }

    override val homeGridFlow: Flow<List<GridItem>> = dataStore.data
        .map {
            runCatching {
                val grid = json.decodeFromString<List<GridItem>>(it[it.activeGridKey()]!!)
                if (it[gridCellVersion] == 2) grid else migrateWholeCells(grid)
            }.getOrDefault(emptyList())
        }

    override suspend fun setAppList(appList: List<App>) {
        if (appList == installedAppsList.first()) return
        dataStore.edit {
            migrateStoredGrids(it, json)
            it[appListKey] = json.encodeToString(appList)
            if (it[gridKey] == null && appList.isNotEmpty()) {
                val builtIns = tgo1014.gridlauncher.live.BuiltInTiles.apps
                val initial = listOf(
                    GridItem(id = 0, app = builtIns[0], width = 1),
                    GridItem(id = 1, app = builtIns[1], width = 2, height = 1, x = 1)
                ) + appList.take(8).mapIndexed { index, app ->
                    GridItem(id = index + 2, app = app, width = 1, x = index % 3, y = 1 + index / 3)
                }
                it[gridKey] = json.encodeToString(initial)
            } else if (it[gridKey] != null) {
                val installed = appList.associateBy { app -> app.packageName }
                it.gridKeyNames().forEach { key ->
                val gridKey = stringPreferencesKey(key)
                if (it[gridKey] == null) return@forEach
                val current = runCatching { json.decodeFromString<List<GridItem>>(it[gridKey]!!) }.getOrDefault(emptyList())
                val refreshed = current.mapNotNull { tile ->
                    val app = if (tile.app.packageName.startsWith("grid://")) tile.app else installed[tile.app.packageName]
                    app?.let { tile.copy(app = if (tile.shortcutId != null) app.copy(name = tile.app.name) else app,
                        // A folder holds hub tiles too, and those are ours rather than a package:
                        // pruning them here is what made an edited folder lose its contents.
                        children = tile.children.mapNotNull { child -> tgo1014.gridlauncher.domain.FolderEdit.keepsAfterRefresh(child, installed) }) }
                }
                val columns = runCatching { json.decodeFromString<tgo1014.gridlauncher.domain.models.TileSettings>(it[stringPreferencesKey("settingsKey")]!!) }.getOrDefault(tgo1014.gridlauncher.domain.models.TileSettings()).gridColumns
                it[gridKey] = json.encodeToString(reflow(tgo1014.gridlauncher.domain.GridPlacement.compact(refreshed, columns).map { it.stampGroup(columns) }, columns))
                }
            }
        }
    }

    override suspend fun setGrid(grid: List<GridItem>) {
        dataStore.edit {
            migrateStoredGrids(it, json)
            val settings = runCatching { json.decodeFromString<tgo1014.gridlauncher.domain.models.TileSettings>(it[stringPreferencesKey("settingsKey")]!!) }.getOrDefault(tgo1014.gridlauncher.domain.models.TileSettings())
            it[it.activeGridKey()] = json.encodeToString(reflow(grid.map { it.stampGroup(settings.gridColumns) }, settings.gridColumns))
        }
    }
}
internal val gridCellVersion = androidx.datastore.preferences.core.intPreferencesKey("gridCellVersion")
/** A group header always spans the whole grid, whatever width the user last chose. */
internal fun GridItem.stampGroup(columns: Int) = if (isGroup) copy(width = columns, height = 1) else this
internal fun reflow(grid: List<GridItem>, columns: Int) = runCatching { tgo1014.gridlauncher.domain.GridPlacement.reflow(grid, columns) }
    .getOrElse { tgo1014.gridlauncher.domain.GridPlacement.reflow(grid.filterNot { it.positionPinned }, columns) }
internal fun migrateWholeCells(grid: List<GridItem>) = tgo1014.gridlauncher.domain.GridPlacement.reflow(grid.map { it.copy(width = (it.width + 1) / 2, height = (it.height + 1) / 2, x = it.x / 2, y = it.y / 2) }, 3)
internal fun migrateStoredGrids(data: androidx.datastore.preferences.core.MutablePreferences, json: Json) {
    if (data[gridCellVersion] == 2) return
    data.gridKeyNames().forEach { name ->
        val key = stringPreferencesKey(name)
        data[key]?.let { stored ->
            runCatching { json.decodeFromString<List<GridItem>>(stored) }.getOrNull()?.let { data[key] = json.encodeToString(migrateWholeCells(it)) }
        }
    }
    data[gridCellVersion] = 2
}
