package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tgo1014.gridlauncher.ui.models.GridItem
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Layouts that always exist. Only user-created layouts can be renamed or deleted. */
val builtinProfileNames = listOf("Personal", "Work", "Travel")
const val defaultProfileName = "Personal"
const val maxCustomLayouts = 12

val activeProfileKey = stringPreferencesKey("activeProfile")
private val customLayoutsKey = stringPreferencesKey("customLayouts")
private const val gridKeyPrefix = "gridKey"

fun profileGridKey(name: String) = stringPreferencesKey(if (name == defaultProfileName) gridKeyPrefix else "$gridKeyPrefix.$name")

/** Layout names reach a preferences key, so keep them single-line, printable and short. */
fun sanitizeLayoutName(raw: String): String? = raw.replace(Regex("[\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(40)
    .takeIf { it.isNotBlank() && it !in builtinProfileNames }

/** Every stored grid key, so reflow and pruning survive a stale or damaged name list. */
fun Preferences.gridKeyNames(): List<String> = asMap().keys.mapNotNull { it.name }
    .filter { it == gridKeyPrefix || it.startsWith("$gridKeyPrefix.") }

fun Preferences.customLayouts(): List<String> =
    runCatching { Json.decodeFromString<List<String>>(this[customLayoutsKey] ?: "[]") }.getOrDefault(emptyList())
        .mapNotNull { sanitizeLayoutName(it) }.distinct()

fun Preferences.knownLayouts(): List<String> = (builtinProfileNames + customLayouts()).distinct()

/** A deleted layout must never leave the launcher reading another layout's grid. */
fun Preferences.activeLayout(): String = this[activeProfileKey]?.takeIf { it in knownLayouts() } ?: defaultProfileName

fun Preferences.activeGridKey() = profileGridKey(activeLayout())

@Singleton
class LayoutProfiles @Inject constructor(private val store: DataStore<Preferences>, private val json: Json) {

    val layouts: Flow<List<String>> = store.data.map { it.knownLayouts() }
    val active: Flow<String> = store.data.map { it.activeLayout() }

    suspend fun select(name: String) = store.edit { require(name in it.knownLayouts()) { "Unknown layout" }; it[activeProfileKey] = name }
    suspend fun exists(name: String): Boolean = name in store.data.first().knownLayouts()

    /** Adds a layout and makes it active. A new layout branches from Start unless it is asked to start empty. */
    suspend fun create(rawName: String, copyCurrent: Boolean = true): String {
        val name = sanitizeLayoutName(rawName) ?: throw IllegalArgumentException("Enter a name for this layout")
        store.edit { prefs ->
            val custom = prefs.customLayouts()
            require(custom.none { it.equals(name, true) }) { "That layout already exists" }
            require(custom.size < maxCustomLayouts) { "You can keep up to $maxCustomLayouts custom layouts" }
            prefs[customLayoutsKey] = json.encodeToString(custom + name)
            if (copyCurrent) prefs[profileGridKey(name)] = prefs[prefs.activeGridKey()] ?: "[]"
            prefs[activeProfileKey] = name
        }
        return name
    }

    suspend fun rename(current: String, rawName: String) {
        val name = sanitizeLayoutName(rawName) ?: throw IllegalArgumentException("Enter a name for this layout")
        store.edit { prefs ->
            val custom = prefs.customLayouts()
            require(current in custom) { "Only your own layouts can be renamed" }
            require(custom.none { it != current && it.equals(name, true) }) { "That layout already exists" }
            prefs[customLayoutsKey] = json.encodeToString(custom.map { if (it == current) name else it })
            val grid = prefs[profileGridKey(current)]
            prefs.remove(profileGridKey(current))
            if (grid != null) prefs[profileGridKey(name)] = grid
            if (prefs[activeProfileKey] == current) prefs[activeProfileKey] = name
        }
    }

    suspend fun delete(name: String) = store.edit { prefs ->
        val custom = prefs.customLayouts()
        require(name in custom) { "Only your own layouts can be deleted" }
        prefs[customLayoutsKey] = json.encodeToString(custom - name)
        prefs.remove(profileGridKey(name))
        if (prefs[activeProfileKey] == name) prefs[activeProfileKey] = defaultProfileName
    }

    /** Adds any missing custom layouts from a backup, keeping the order and the existing grids. */
    suspend fun importCustom(names: List<String>) = store.edit { prefs ->
        val existing = prefs.customLayouts()
        val added = names.mapNotNull(::sanitizeLayoutName).filterNot { it in existing || it in prefs.knownLayouts() }.distinct()
            .take((maxCustomLayouts - existing.size).coerceAtLeast(0))
        if (added.isNotEmpty()) prefs[customLayoutsKey] = json.encodeToString(existing + added)
    }

    suspend fun duplicateInto(name: String) = store.edit { prefs ->
        require(name in prefs.knownLayouts()) { "Unknown layout" }
        prefs[profileGridKey(name)] = prefs[prefs.activeGridKey()] ?: "[]"
    }

    /** Every layout's stored grid, used by backup so a restore rebuilds all of them. */
    suspend fun allGrids(): Map<String, List<GridItem>> {
        val data = store.data.first()
        return data.gridKeyNames().mapNotNull { name ->
            val layout = name.removePrefix("$gridKeyPrefix.").takeIf { name.startsWith("$gridKeyPrefix.") } ?: defaultProfileName
            layout to runCatching { Json.decodeFromString<List<GridItem>>(data[stringPreferencesKey(name)] ?: "[]") }.getOrDefault(emptyList())
        }.toMap()
    }

    suspend fun replaceGrids(grids: Map<String, List<GridItem>>) = store.edit { prefs ->
        prefs.knownLayouts().forEach { layout ->
            val grid = grids[layout] ?: return@forEach
            prefs[profileGridKey(layout)] = json.encodeToString(grid)
        }
    }

    suspend fun widgetInUse(id: Int): Boolean {
        val data = store.data.first()
        return data.gridKeyNames().any { name ->
            runCatching { Json.decodeFromString<List<GridItem>>(data[stringPreferencesKey(name)] ?: "[]").any { it.widgetId == id } }.getOrDefault(false)
        }
    }
}

/** Local time, weekdays; supports overnight shifts anchored to the day they start. */
fun scheduledProfile(time: LocalDateTime, start: Int, end: Int): String {
    if (start !in 0..23 || end !in 0..23 || start == end) return defaultProfileName
    val overnight = start > end
    val startDay = if (overnight && time.hour < end) time.minusDays(1) else time
    val workday = startDay.dayOfWeek.value <= 5
    val inHours = if (overnight) time.hour >= start || time.hour < end else time.hour in start until end
    return if (workday && inHours) "Work" else defaultProfileName
}
