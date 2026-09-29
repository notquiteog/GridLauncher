package tgo1014.gridlauncher.live

import tgo1014.gridlauncher.data.withoutAccents

/** One answer line, with the action that performs it. */
data class Answer(val text: String, val action: Action) {
    enum class Action { OpenApp, OpenTile, OpenNotification, OpenCalendar, Call, Text, Email, Web, Clock, Nothing }
}

/** Everything the answer engine may read. All of it is the user's own on-device data. */
data class StartFacts(
    val apps: List<String> = emptyList(),
    val tiles: List<String> = emptyList(),
    val people: List<PinnedContact> = emptyList(),
    val notifications: List<TileNotification> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val nowPlaying: String? = null,
    val battery: String? = null,
    val storage: String? = null,
    val clock: String = "",
)

data class CalendarEvent(val title: String, val startsAt: Long, val allDay: Boolean, val location: String = "")

/**
 * A small, predictable natural-language layer over the launcher's own live data.
 * Deterministic on purpose: the same question always produces the same answer, and every
 * answer points at a real item. No model is involved and nothing leaves the device.
 */
object StartQuery {

    private fun String.norm() = withoutAccents.trim().lowercase()

    fun answer(query: String, facts: StartFacts, now: Long = System.currentTimeMillis()): List<Answer> {
        val q = query.norm()
        if (q.isBlank()) return emptyList()

        if (any(q, "what time", "whats the time", "what's the time", "time now")) {
            return listOf(Answer(if (facts.clock.isBlank()) "Your device shows the time in the status bar." else facts.clock, Answer.Action.Clock))
        }

        // An explicit "open X" is an app request, whatever words the app name happens to contain.
        if (any(q, "open ", "launch ", "start ", "go to ")) {
            val name = q.substringAfter(' ').trim()
            facts.apps.firstOrNull { it.norm().contains(name) || name.contains(it.norm()) }?.let { return listOf(Answer("Open $it", Answer.Action.OpenApp)) }
            facts.tiles.firstOrNull { it.norm().contains(name) || name.contains(it.norm()) }?.let { return listOf(Answer("Open $it", Answer.Action.OpenTile)) }
        }

        // Calendar questions next: "what's on today" is the common case.
        if (any(q, "what's on", "whats on", "agenda", "schedule", "calendar", "today", "tomorrow", "meeting", "event", "busy", "free", "when is", "when's")) {
            val day = if (any(q, "tomorrow")) 86_400_000L else 0L
            val start = now + day
            val end = start + 86_400_000L
            val todays = facts.events.filter { it.startsAt in start..end }.sortedBy { it.startsAt }
            val when_ = { event: CalendarEvent -> if (event.allDay) " · all day" else " · ${relative(event.startsAt, now)}" }
            if (todays.isEmpty()) return listOf(Answer("Nothing scheduled for ${if (day > 0) "tomorrow" else "today"}.", Answer.Action.Nothing))
            if (q.contains("when") || q.contains("what time")) {
                val target = todays.first()
                return listOf(Answer("${target.title} ${if (target.allDay) "is all day" else "starts ${when_(target)}"}.", Answer.Action.OpenCalendar))
            }
            return todays.take(6).map { Answer("${it.title}${when_(it)}${if (it.location.isNotBlank()) " · ${it.location}" else ""}", Answer.Action.OpenCalendar) }
        }

        if (any(q, "unread", "any messages", "who's messaging", "whos messaging", "notification", "notifications", "new message")) {
            val named = facts.people.firstOrNull { q.contains(it.name.norm()) }
            val relevant = if (named != null) facts.notifications.filter { named.matches(it) } else facts.notifications
            if (relevant.isEmpty()) return listOf(Answer("No matching notifications right now.", Answer.Action.Nothing))
            return relevant.sortedByDescending { it.time }.take(5).map {
                Answer("${it.title.ifBlank { it.packageName }}: ${it.text.take(80)}", Answer.Action.OpenNotification)
            }
        }

        if (any(q, "battery", "charge", "power")) {
            return listOfNotNull(facts.battery?.let { Answer(it, Answer.Action.OpenTile) })
        }
        if (any(q, "storage", "space", "disk", "memory")) {
            return listOfNotNull(facts.storage?.let { Answer(it, Answer.Action.OpenTile) })
        }
        if (any(q, "playing", "music", "song", "track")) {
            return listOf(Answer(facts.nowPlaying ?: "Nothing is playing.", Answer.Action.OpenTile))
        }
        if (any(q, "photo", "photos", "picture", "pictures")) {
            return listOf(Answer("Open your photo library.", Answer.Action.OpenTile))
        }

        if (any(q, "call", "ring", "phone")) {
            val person = matchPerson(q, facts) ?: return listOf(Answer("Which contact should I call?", Answer.Action.Nothing))
            val number = person.phones.firstOrNull() ?: return listOf(Answer("No number saved for ${person.name}.", Answer.Action.Nothing))
            return listOf(Answer("Call ${person.name}", Answer.Action.Call))
        }
        if (any(q, "text", "message", "sms")) {
            val person = matchPerson(q, facts) ?: return listOf(Answer("Which contact should I message?", Answer.Action.Nothing))
            if (person.phones.isEmpty()) return listOf(Answer("No number saved for ${person.name}.", Answer.Action.Nothing))
            return listOf(Answer("Message ${person.name}", Answer.Action.Text))
        }
        if (any(q, "email", "e-mail", "mail")) {
            val person = matchPerson(q, facts) ?: return listOf(Answer("Which contact should I email?", Answer.Action.Nothing))
            if (person.emails.isEmpty()) return listOf(Answer("No address saved for ${person.name}.", Answer.Action.Nothing))
            return listOf(Answer("Email ${person.name}", Answer.Action.Email))
        }

        val app = facts.apps.firstOrNull { q.contains(it.norm()) || it.norm().contains(q) }
        if (app != null) return listOf(Answer("Open $app", Answer.Action.OpenApp))
        val tile = facts.tiles.firstOrNull { q.contains(it.norm()) || it.norm().contains(q) }
        if (tile != null) return listOf(Answer("Open $tile", Answer.Action.OpenTile))
        val person = facts.people.firstOrNull { q.contains(it.name.norm()) }
        if (person != null) return person.phones.firstOrNull()?.let { listOf(Answer("Call ${person.name}", Answer.Action.Call)) }
            ?: person.emails.firstOrNull()?.let { listOf(Answer("Email ${person.name}", Answer.Action.Email)) }
            ?: listOf(Answer(person.name, Answer.Action.OpenTile))

        return listOf(Answer("Search the web for \"${query.trim().take(80)}\"", Answer.Action.Web))
    }

    private fun any(q: String, vararg phrases: String) = phrases.any { q.contains(it) }

    private fun matchPerson(q: String, facts: StartFacts): PinnedContact? = facts.people.firstOrNull { person ->
        person.name.norm().split(" ").any { it.length > 2 && q.contains(it) }
    } ?: facts.people.firstOrNull { q.contains(it.name.norm()) }

    internal fun relative(target: Long, now: Long): String {
        val minutes = ((target - now) / 60_000).toInt()
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "in $minutes min"
            minutes < 60 * 24 -> "in ${minutes / 60} h ${minutes % 60} min"
            else -> android.text.format.DateUtils.formatDateTime(null, target, android.text.format.DateUtils.FORMAT_SHOW_DATE)
        }
    }
}
