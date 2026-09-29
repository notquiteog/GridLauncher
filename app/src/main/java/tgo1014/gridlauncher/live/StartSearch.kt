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

    fun label(source: String) = when (source) {
        PERSON -> "People"
        NOTIFICATION -> "Notifications"
        EVENT -> "Calendar"
        else -> "Results"
    }
}

data class SearchSection(val source: String, val rows: List<SearchRow>)

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
 * merely hidden: it is absent from the index, so it cannot come back through search either.
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
                text = notification.text.take(1000),
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
