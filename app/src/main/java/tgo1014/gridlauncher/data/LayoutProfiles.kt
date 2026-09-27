package tgo1014.gridlauncher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

val profileNames = listOf("Personal", "Work", "Travel")
val activeProfileKey = stringPreferencesKey("activeProfile")
fun profileGridKey(name: String) = stringPreferencesKey(if (name == "Personal") "gridKey" else "gridKey.$name")
fun Preferences.activeGridKey() = profileGridKey(this[activeProfileKey]?.takeIf { it in profileNames } ?: "Personal")

@Singleton
class LayoutProfiles @Inject constructor(private val store: DataStore<Preferences>) {
    val active = store.data.map { it[activeProfileKey] ?: "Personal" }
    suspend fun select(name: String) { require(name in profileNames); store.edit { it[activeProfileKey] = name } }
    suspend fun widgetInUse(id: Int): Boolean {
        val data = store.data.first()
        return profileNames.any { name -> runCatching { kotlinx.serialization.json.Json.decodeFromString<List<tgo1014.gridlauncher.ui.models.GridItem>>(data[profileGridKey(name)] ?: "[]").any { it.widgetId == id } }.getOrDefault(false) }
    }
    suspend fun duplicateInto(name: String) { require(name in profileNames); store.edit { it[profileGridKey(name)] = it[it.activeGridKey()] ?: "[]" } }
}

/** Local time, weekdays; supports overnight shifts anchored to the day they start. */
fun scheduledProfile(time: LocalDateTime, start: Int, end: Int): String {
    if (start !in 0..23 || end !in 0..23 || start == end) return "Personal"
    val overnight = start > end
    val startDay = if (overnight && time.hour < end) time.minusDays(1) else time
    val workday = startDay.dayOfWeek.value <= 5
    val inHours = if (overnight) time.hour >= start || time.hour < end else time.hour in start until end
    return if (workday && inHours) "Work" else "Personal"
}
