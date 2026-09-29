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
import tgo1014.gridlauncher.ui.models.GridItem
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
        val safe = tileSettings.copy(tilesAcross = tileSettings.tilesAcross.coerceIn(2, 6))
        dataStore.edit { prefs ->
            migrateStoredGrids(prefs, json)
            val old = runCatching { json.decodeFromString<TileSettings>(prefs[key]!!) }.getOrDefault(TileSettings())
            if (old.gridColumns != safe.gridColumns) prefs.gridKeyNames().forEach { name ->
                val gridKey = stringPreferencesKey(name)
                if (prefs[gridKey] == null) return@forEach
                val grid = runCatching { json.decodeFromString<List<GridItem>>(prefs[gridKey]!!) }.getOrDefault(emptyList())
                // Narrowing must never fail the write: an anchor that no longer fits is unpinned.
                prefs[gridKey] = json.encodeToString(reflowSafely(grid, safe.gridColumns))
            }
            prefs[key] = json.encodeToString(safe)
        }
    }

    /** Same packing as GridPlacement, except an impossible anchor is dropped instead of throwing. */
    internal fun reflowSafely(grid: List<GridItem>, columns: Int): List<GridItem> = runCatching {
        tgo1014.gridlauncher.domain.GridPlacement.reflow(grid, columns)
    }.getOrElse {
        tgo1014.gridlauncher.domain.GridPlacement.reflow(grid.filterNot { it.positionPinned }, columns)
    }
}