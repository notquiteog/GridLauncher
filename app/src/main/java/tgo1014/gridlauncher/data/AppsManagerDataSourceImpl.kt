package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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
                json.decodeFromString<List<GridItem>>(it[gridKey]!!)
            }.getOrDefault(emptyList())
        }

    override suspend fun setAppList(appList: List<App>) {
        dataStore.edit {
            it[appListKey] = json.encodeToString(appList)
            if (it[gridKey] == null && appList.isNotEmpty()) {
                val builtIns = tgo1014.gridlauncher.live.BuiltInTiles.apps
                val initial = listOf(
                    GridItem(id = 0, app = builtIns[0], width = 2),
                    GridItem(id = 1, app = builtIns[1], width = 4, height = 2, x = 2)
                ) + appList.take(8).mapIndexed { index, app ->
                    GridItem(id = index + 2, app = app, width = 2, x = (index % 3) * 2, y = 2 + (index / 3) * 2)
                }
                it[gridKey] = json.encodeToString(initial)
            } else if (it[gridKey] != null) {
                val installed = appList.associateBy { app -> app.packageName }
                val current = runCatching { json.decodeFromString<List<GridItem>>(it[gridKey]!!) }.getOrDefault(emptyList())
                val refreshed = current.mapNotNull { tile ->
                    val app = if (tile.app.packageName.startsWith("grid://")) tile.app else installed[tile.app.packageName]
                    app?.let { tile.copy(app = app, children = tile.children.mapNotNull { child -> installed[child.packageName] }) }
                }
                it[gridKey] = json.encodeToString(refreshed)
            }
        }
    }

    override suspend fun setGrid(grid: List<GridItem>) {
        dataStore.edit {
            it[gridKey] = json.encodeToString(grid)
        }
    }
}