package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The layouts the app ships with. They are not special: each one leaves this list the moment it is
 * renamed or deleted, and carries its tiles on as an ordinary custom layout. Nothing anywhere has
 * to know a name used to be built in, which is what makes Personal, Work and Travel behave like
 * every other chip in the bar.
 */
val builtinProfileNames = listOf("Personal", "Work", "Travel")
const val defaultProfileName = "Personal"
/** The two layouts the weekday schedule points at before anyone has chosen their own. */
const val defaultWorkLayoutName = "Work"
const val maxCustomLayouts = 12

val activeProfileKey = stringPreferencesKey("activeProfile")
private val customLayoutsKey = stringPreferencesKey("customLayouts")
private val removedBuiltinsKey = stringPreferencesKey("removedBuiltinLayouts")
private const val gridKeyPrefix = "gridKey"

fun profileGridKey(name: String) = stringPreferencesKey(if (name == defaultProfileName) gridKeyPrefix else "$gridKeyPrefix.$name")

/**
 * Layout names reach a preferences key, so keep them single-line, printable and short. Whether a
 * name is *free* is a question about the layouts this device already has, and only that: refusing
 * the shipped names here is what used to make a layout called Work impossible to have.
 */
fun sanitizeLayoutName(raw: String): String? = raw.replace(Regex("[\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(40)
    .takeIf { it.isNotBlank() }

/** Every stored grid key, so reflow and pruning survive a stale or damaged name list. */
fun Preferences.gridKeyNames(): List<String> = asMap().keys.mapNotNull { it.name }
    .filter { it == gridKeyPrefix || it.startsWith("$gridKeyPrefix.") }

fun Preferences.customLayouts(): List<String> =
    runCatching { Json.decodeFromString<List<String>>(this[customLayoutsKey] ?: "[]") }.getOrDefault(emptyList())
        .mapNotNull { sanitizeLayoutName(it) }.distinct()

/** The built-in names this device has renamed or deleted, which is how it remembers they are gone. */
fun Preferences.removedBuiltins(): List<String> =
    runCatching { Json.decodeFromString<List<String>>(this[removedBuiltinsKey] ?: "[]") }.getOrDefault(emptyList())
        .filter { it in builtinProfileNames }.distinct()

fun Preferences.builtinLayouts(): List<String> = builtinProfileNames - removedBuiltins()

fun Preferences.knownLayouts(): List<String> = (builtinLayouts() + customLayouts()).distinct()

/**
 * The layout the launcher is showing. A deleted layout must never leave the launcher reading
 * another layout's grid, and a deleted *active* layout must never leave it reading nothing at all,
 * so an unknown name lands on the default and a default that is itself gone lands on whatever is
 * left.
 */
fun Preferences.activeLayout(): String {
    val known = knownLayouts()
    return this[activeProfileKey]?.takeIf { it in known }
        ?: known.firstOrNull { it == defaultProfileName } ?: known.firstOrNull() ?: defaultProfileName
}

fun Preferences.activeGridKey() = profileGridKey(activeLayout())

/**
 * The bar's order. The saved one leads, and anything the store has added since keeps the order it
 * was created in at the end, so a layout can be moved anywhere but never off the end of existence.
 */
fun orderLayouts(layouts: List<String>, order: List<String>): List<String> =
    order.distinct().filter { it in layouts } + layouts.filterNot { it in order }

/**
 * The weekday schedule names two layouts rather than being two layouts. Both names are checked
 * against what this device really has before anything is selected, because a name the schedule
 * points at can be renamed or deleted like any other.
 */
fun resolveScheduledLayout(due: String, layouts: List<String>): String =
    due.takeIf { it in layouts } ?: layouts.firstOrNull { it == defaultProfileName }
        ?: layouts.firstOrNull() ?: defaultProfileName

/**
 * A rename has to travel with everything that names layouts, or the rename would quietly undo
 * itself: the schedule would stop switching and the bar's saved order would lose the chip.
 */
fun renameLayoutRefs(settings: TileSettings, current: String, name: String): TileSettings = settings.copy(
    workLayout = if (settings.workLayout == current) name else settings.workLayout,
    personalLayout = if (settings.personalLayout == current) name else settings.personalLayout,
    layoutOrder = settings.layoutOrder.map { if (it == current) name else it },
)

/** A deleted layout is dropped from the order, and the schedule is left pointing at [fallback]. */
fun dropLayoutRefs(settings: TileSettings, gone: String, fallback: String): TileSettings = settings.copy(
    workLayout = if (settings.workLayout == gone) fallback else settings.workLayout,
    personalLayout = if (settings.personalLayout == gone) fallback else settings.personalLayout,
    layoutOrder = settings.layoutOrder.filterNot { it == gone },
)

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
            require(prefs.knownLayouts().none { it.equals(name, true) }) { "That layout already exists" }
            require(custom.size < maxCustomLayouts) { "You can keep up to $maxCustomLayouts custom layouts" }
            prefs[customLayoutsKey] = json.encodeToString(custom + name)
            if (copyCurrent) prefs[profileGridKey(name)] = prefs[prefs.activeGridKey()] ?: "[]"
            prefs[activeProfileKey] = name
        }
        return name
    }

    /** Every layout can be renamed. A built-in one is renamed by retiring the old name for good. */
    suspend fun rename(current: String, rawName: String) {
        val name = sanitizeLayoutName(rawName) ?: throw IllegalArgumentException("Enter a name for this layout")
        store.edit { prefs ->
            val known = prefs.knownLayouts()
            require(current in known) { "That layout no longer exists" }
            require(known.none { it != current && it.equals(name, true) }) { "That layout already exists" }
            // Read before the old name is retired: afterwards the active layout is a different one.
            val wasActive = prefs.activeLayout() == current
            if (current in prefs.builtinLayouts()) {
                prefs[removedBuiltinsKey] = json.encodeToString((prefs.removedBuiltins() + current).distinct())
                prefs[customLayoutsKey] = json.encodeToString(prefs.customLayouts() + name)
            } else {
                prefs[customLayoutsKey] = json.encodeToString(prefs.customLayouts().map { if (it == current) name else it })
            }
            val grid = prefs[profileGridKey(current)]
            prefs.remove(profileGridKey(current))
            if (grid != null) prefs[profileGridKey(name)] = grid
            if (wasActive) prefs[activeProfileKey] = name
        }
    }

    /**
     * Removes a layout, its grid and nothing else. Two rules hold no matter which layout it is:
     * the launcher is never left with no layout at all, and a layout is never pulled out from under
     * the one being shown - the caller has to move off it first, which is what the confirmation
     * says it is about to do.
     */
    suspend fun delete(name: String) = store.edit { prefs ->
        val known = prefs.knownLayouts()
        require(name in known) { "That layout no longer exists" }
        require(known.size > 1) { "Start always keeps one layout" }
        require(prefs.activeLayout() != name) { "You are in $name right now. Pick another layout, then delete it." }
        if (name in prefs.builtinLayouts()) {
            prefs[removedBuiltinsKey] = json.encodeToString((prefs.removedBuiltins() + name).distinct())
        } else {
            prefs[customLayoutsKey] = json.encodeToString(prefs.customLayouts() - name)
        }
        prefs.remove(profileGridKey(name))
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
fun scheduledProfile(
    time: LocalDateTime, start: Int, end: Int,
    workLayout: String = defaultWorkLayoutName, personalLayout: String = defaultProfileName,
): String {
    if (start !in 0..23 || end !in 0..23 || start == end) return personalLayout
    val overnight = start > end
    val startDay = if (overnight && time.hour < end) time.minusDays(1) else time
    val workday = startDay.dayOfWeek.value <= 5
    val inHours = if (overnight) time.hour >= start || time.hour < end else time.hour in start until end
    return if (workday && inHours) workLayout else personalLayout
}
