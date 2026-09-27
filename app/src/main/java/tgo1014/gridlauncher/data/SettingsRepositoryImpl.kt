package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tgo1014.gridlauncher.domain.SettingsRepository
import tgo1014.gridlauncher.domain.models.TileSettings
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val json: Json,
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val key = stringPreferencesKey("settingsKey")

    override val tileSettingsFlow = dataStore.data.map {
        runCatching {
            json.decodeFromString<TileSettings>(it[key]!!)
        }.getOrDefault(TileSettings())
    }

    override suspend fun updateSettings(tileSettings: TileSettings) {
        dataStore.edit {
            migrateStoredGrids(it, json)
            val safe = tileSettings.copy(tilesAcross = tileSettings.tilesAcross.coerceIn(2, 6), cornerRadius = 0)
            val old = runCatching { json.decodeFromString<TileSettings>(it[key]!!) }.getOrDefault(TileSettings())
            if (old.gridColumns != safe.gridColumns) profileNames.forEach { name ->
                val gridKey = profileGridKey(name)
                val grid = runCatching { json.decodeFromString<List<tgo1014.gridlauncher.ui.models.GridItem>>(it[gridKey]!!) }.getOrDefault(emptyList())
                if (it[gridKey] != null) it[gridKey] = json.encodeToString(tgo1014.gridlauncher.domain.GridPlacement.reflow(grid, safe.gridColumns))
            }
            it[key] = json.encodeToString(safe)
        }
    }
}