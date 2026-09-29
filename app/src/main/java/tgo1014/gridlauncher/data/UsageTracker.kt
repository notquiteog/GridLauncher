package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which apps you actually open, and how often. Stays on this device, holds package names only,
 * and exists so Start can suggest the apps you reach for instead of guessing.
 */
@Singleton
class UsageTracker @Inject constructor(private val store: DataStore<Preferences>, private val json: Json) {

    private val countsKey = stringPreferencesKey("appUsage")

    val frequent: Flow<List<String>> = store.data.map { prefs -> ranked(prefs[countsKey]) }

    private fun ranked(stored: String?): List<String> {
        val counts = runCatching { json.decodeFromString<Map<String, Int>>(stored ?: "{}") }.getOrDefault(emptyMap())
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
    }

    /** Records one open. Names only, no timestamps, so there is nothing to reconstruct a day from. */
    suspend fun record(packageName: String) {
        if (packageName.isBlank() || packageName.startsWith("grid://")) return
        store.edit { prefs ->
            val counts = runCatching { json.decodeFromString<Map<String, Int>>(prefs[countsKey] ?: "{}") }.getOrDefault(emptyMap()).toMutableMap()
            counts[packageName] = (counts[packageName] ?: 0) + 1
            // Keep the store small: only the apps still worth suggesting.
            val trimmed = counts.entries.sortedByDescending { it.value }.take(60).associate { it.key to it.value }
            prefs[countsKey] = json.encodeToString(trimmed)
        }
    }

    suspend fun clear() = store.edit { it.remove(countsKey) }
}
