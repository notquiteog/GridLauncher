package tgo1014.gridlauncher.live

import tgo1014.gridlauncher.data.withoutAccents
import tgo1014.gridlauncher.domain.models.TileSettings
import java.text.DateFormat
import java.util.Date

/**
 * One thing Start's search can find and open: a person, a notification or a calendar event.
 *
 * Deliberately free of the platform, so the query building, the ranking and the privacy rules
 * stay testable on the JVM. The AppSearch index is only a faster way to answer the same question;
 * every answer is reproducible from a plain substring scan of these rows.
 */
data class SearchRow(
    val id: String, val source: String, val title: String, val text: String, val subtitle: String, val stamp: Long,
)

object SearchSource {
    const val PERSON = "person"
    const val NOTIFICATION = "notification"
    const val EVENT = "event"

    /** Who you are thinking of, then what arrived, then what is next. */
    val order = listOf(PERSON, NOTIFICATION, EVENT)

    /**
     * SECURITY: the only sources that may ever be written to disk.
     *
     * Notification content has no persisted form in this launcher, under any setting, at any time.
     * It is read, shown and searched in memory, and is gone with the process. Anything added here
     * starts being written to the on-device AppSearch database, so this set is the whole boundary.
     */
    val indexable = setOf(PERSON, EVENT)

    fun label(source: String) = when (source) {
        PERSON -> "People"
        NOTIFICATION -> "Notifications"
        EVENT -> "Calendar"
        else -> "Results"
    }
}

data class SearchSection(val source: String, val rows: List<SearchRow>)

/**
 * One thing the on-device search index is allowed to contain.
 *
 * Deliberately a different type from [SearchRow] so that the boundary is enforced by the compiler:
 * a notification row has no way to become a [PersistedRow], because the only function that makes
 * one refuses to be given a row it may not keep.
 */
data class PersistedRow(val id: String, val source: String, val title: String, val text: String, val subtitle: String, val stamp: Long)

/**
 * The one boundary between what this launcher shows and what it keeps.
 *
 * Everything above this line is memory: read on demand, drawn on screen, searched by a substring
 * scan, and gone when the process dies. Everything below it is on disk, and only a [PersistedRow]
 * - a person's own starred name and number, a calendar title the user already gave us permission
 * to read - can cross. Notification titles, bodies, conversations and call state never do, under any
 * setting, and there is no switch that would turn that on.
 */
object SearchPersistence {
    /** The only sources that may cross into a persisted store. */
    val indexableSources: Set<String> = SearchSource.indexable

    /**
     * The rows that may be written, and nothing else. Called by the index writer itself, so the
     * filter is the writer's own precondition rather than a promise its callers keep.
     */
    fun indexable(rows: List<SearchRow>): List<PersistedRow> = rows.mapNotNull { row ->
        if (row.source !in SearchSource.indexable) return@mapNotNull null
        PersistedRow(row.id, row.source, row.title, row.text, row.subtitle, row.stamp)
    }

    /** A fingerprint over what would be written, so a burst of notifications costs no write at all. */
    fun digest(rows: List<PersistedRow>): Int = rows.fold(11) { hash, row -> hash * 31 + row.hashCode() }

    /** Any source that made it back out of a stale database is dropped before it can be drawn. */
    fun discardUnindexable(rows: List<SearchRow>): List<SearchRow> = rows.filter { it.source in SearchSource.indexable }
}

object StartSearch {
    /** Six words is already past what a launcher's search bar is for. */
    const val MAX_TERMS = 6

    /**
     * The words a raw query actually searches on. Everything else is dropped, because AppSearch
     * reads `:`, `-`, `(`, `)` and the like as query operators and throws on a malformed string.
     */
    fun terms(query: String): List<String> = query.trim().split(Regex("\\s+")).mapNotNull { raw ->
        val token = raw.withoutAccents.lowercase()
        if (token.none { it.isLetterOrDigit() }) null
        // A leading dash is AppSearch's exclusion marker, not a word: search the word anyway.
        else token.trimStart('-', '.').filter { it.isLetterOrDigit() || it in "._-+@'" }.ifEmpty { null }
    }.distinct().take(MAX_TERMS)

    /** A launcher's search means "any of these words", not AppSearch's default all-of. */
    fun phrase(query: String): String = terms(query).joinToString(" OR ")

    fun matches(row: SearchRow, terms: List<String>): Boolean {
        if (terms.isEmpty()) return false
        val haystack = (row.title + " " + row.text + " " + row.subtitle).withoutAccents.lowercase()
        return terms.any { haystack.contains(it) }
    }

    /** The plain scan the drawer falls back to whenever the index is not there. */
    fun filter(rows: List<SearchRow>, query: String): List<SearchRow> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        return rows.filter { matches(it, terms) }
    }

    /** One shape for both paths, so the drawer draws the same rows indexed or not. */
    fun sections(rows: List<SearchRow>, perSection: Int = 8): List<SearchSection> = SearchSource.order.mapNotNull { source ->
        val here = rows.filter { it.source == source }.sortedWith(ordering(source)).take(perSection)
        if (here.isEmpty()) null else SearchSection(source, here)
    }

    private fun ordering(source: String): Comparator<SearchRow> = when (source) {
        SearchSource.EVENT -> compareBy { it.stamp }
        SearchSource.NOTIFICATION -> compareByDescending<SearchRow> { it.stamp }.thenBy { it.title.lowercase() }
        else -> compareBy { it.title.lowercase() }
    }

    /** Cheap content fingerprint, so a refresh that changed nothing writes nothing. */
    fun digest(rows: List<SearchRow>): Int = rows.fold(7) { hash, row -> hash * 31 + row.hashCode() }
}

/**
 * The launcher's existing privacy contract, in the only place search reads the data.
 *
 * Notification text reaches the screen only when the user asked for it, is not in a quiet window
 * or meeting mode, and has not excluded the sending app per app. An excluded notification is not
 * merely hidden: it is absent from the rows entirely, so it cannot come back through search either.
 *
 * SECURITY: what this returns is a memory-only row. It is scanned by [StartSearch.filter] and
 * nothing else. [SearchPersistence.indexable] drops it, so it can never reach the AppSearch
 * database, the layout backup, the DataStore or any other store. A process restart loses it,
 * which is the point.
 */
fun notificationRows(settings: TileSettings, notifications: List<TileNotification>): List<SearchRow> {
    if (!settings.showNotificationText || QuietHours.active(settings)) return emptyList()
    return notifications
        .filter { it.packageName !in settings.hiddenPreviewApps }
        .filter { it.title.isNotBlank() || it.text.isNotBlank() }
        .map { notification ->
            SearchRow(
                id = "n:${notification.key}",
                source = SearchSource.NOTIFICATION,
                title = notification.title.ifBlank { notification.packageName }.take(200),
                // A messaging app puts the conversation in the style rather than in the body, so a
                // search that only read title and text would miss every message it never summarised.
                // This row is the whole search answer, so it has to carry what the user can see.
                text = (listOf(notification.text) + notification.messages).joinToString(" ").take(1000),
                subtitle = notification.packageName,
                stamp = notification.time,
            )
        }
}

fun contactRows(contacts: List<PinnedContact>): List<SearchRow> = contacts
    .filter { it.name.isNotBlank() }
    .map { contact ->
        val number = contact.phones.firstOrNull() ?: contact.emails.firstOrNull()
        SearchRow(
            id = "c:${contact.key}",
            source = SearchSource.PERSON,
            title = contact.name.take(200),
            text = (contact.phones + contact.emails).joinToString(" ").take(1000),
            subtitle = number ?: "Contact",
            stamp = 0L,
        )
    }

data class CalendarEvent(val title: String, val begin: Long, val allDay: Boolean, val location: String)

fun eventRows(events: List<CalendarEvent>): List<SearchRow> = events
    .filter { it.title.isNotBlank() && it.begin > 0 }
    .mapIndexed { index, event ->
        val when_ = if (event.allDay) "All day" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.begin))
        SearchRow(
            // The index position keeps two events that start at the same minute apart.
            id = "e:$index:${event.begin}",
            source = SearchSource.EVENT,
            title = event.title.take(200),
            text = event.location.take(200),
            subtitle = when_,
            stamp = event.begin,
        )
    }
