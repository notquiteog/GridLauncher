package tgo1014.gridlauncher.live

import android.content.Context
import android.content.SharedPreferences
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import java.text.DateFormat
import java.util.Date

/** What a card is about, which is also how it is announced to TalkBack. */
enum class NowCardKind(val label: String) {
    NOTIFICATION("Ongoing activity"),
    AGENDA("Calendar"),
    MEDIA("Music"),
    CATEGORY("Category"),
    MONEY("Money"),
}

/** What a card does when it is tapped. Expanding a card in place is a second, separate control. */
sealed interface NowAction {
    /** A live notification, previewed the way every other notification in the launcher is. */
    data class OpenNotification(val key: String) : NowAction
    data class OpenApp(val packageName: String) : NowAction
    data object OpenCalendar : NowAction
    data object Expand : NowAction
}

data class NowCard(
    val key: String,
    val kind: NowCardKind,
    val source: String = kind.label,
    val title: String,
    val lines: List<String> = emptyList(),
    val action: NowAction = NowAction.Expand,
    val app: App? = null,
    val notification: TileNotification? = null,
    val media: NowPlaying? = null,
    val events: List<CalendarEvent> = emptyList(),
) {
    val appPackage: String? get() = app?.packageName ?: notification?.packageName ?: media?.packageName

    /** The live notification behind this card, when there is one. */
    val notificationKey: String? get() = notification?.key

    /** Only a card with more behind it than its summary gets the control that opens it up. */
    val expandable: Boolean get() = kind != NowCardKind.MONEY

    /** One string for TalkBack: what it is, what it says and where it came from. */
    fun describe(expanded: Boolean) = (listOf(source, title) + lines)
        .filter { it.isNotBlank() }.distinct().joinToString(", ") + if (expanded) ", expanded" else ", collapsed"
}

data class BoardInput(
    val settings: TileSettings = TileSettings(),
    val quiet: Boolean = false,
    val locked: Boolean = false,
    val notifications: List<TileNotification> = emptyList(),
    val nowPlaying: NowPlaying? = null,
    val mediaAppName: String? = null,
    val agenda: List<CalendarEvent> = emptyList(),
    val categories: Map<NowCategory, App> = emptyMap(),
    val money: App? = null,
)

/**
 * The Now board: what is happening right now, and nothing else.
 *
 * A card exists only because there is something behind it - a live notification, a playing
 * session, a real calendar event, or an app the user pinned to a slot. There is no card for a
 * category the device cannot answer, and no card whose body would have to be invented. The privacy
 * rules are the launcher's existing ones, applied here as a whole: quiet hours hide the board
 * outright, a locked device or a switched-off preview shows no notification text, and an app the
 * user excluded from previews is left off the board entirely.
 */
object NowBoard {

    const val MAX_NOTIFICATIONS = 6
    const val MAX_EVENTS = 5

    /** Cards come out in this order, whatever order the sources happened to produce them in. */
    private val order = listOf(NowCardKind.NOTIFICATION, NowCardKind.AGENDA, NowCardKind.MEDIA, NowCardKind.CATEGORY, NowCardKind.MONEY)

    fun cards(input: BoardInput): List<NowCard> {
        if (input.quiet || !input.settings.showNow || !input.settings.liveTilesEnabled) return emptyList()
        val reveal = !input.locked && input.settings.showNotificationText
        val hidden = input.settings.hiddenPreviewApps
        val visible = input.notifications.filter { it.packageName !in hidden }
        val cards = order.flatMap { kind ->
            when (kind) {
                NowCardKind.NOTIFICATION -> ongoing(visible, reveal)
                NowCardKind.AGENDA -> agenda(input)
                // A media session is only visible to us through the notification listener, so an app
                // the user excluded from previews is left off the board here as well.
                NowCardKind.MEDIA -> media(input, hidden)
                NowCardKind.CATEGORY -> categories(input, visible, reveal, hidden)
                NowCardKind.MONEY -> money(input)
            }
        }
        // A category the user has not heard from is an app shortcut, and Start is where shortcuts
        // live: it rides along on a board that is up for something else rather than bringing a
        // board up by itself. Anything with real content behind it does bring one up.
        val somethingHappening = cards.any { it.kind != NowCardKind.CATEGORY || it.notification != null }
        return if (somethingHappening) cards else emptyList()
    }

    private fun ongoing(visible: List<TileNotification>, reveal: Boolean) = visible.filter { it.isNow }
        .sortedByDescending { it.time }.take(MAX_NOTIFICATIONS)
        .map { n ->
            NowCard(
                key = "now:${n.key}", kind = NowCardKind.NOTIFICATION,
                title = if (reveal) n.title.ifBlank { NowCardKind.NOTIFICATION.label } else NowCardKind.NOTIFICATION.label,
                lines = if (reveal) listOfNotNull(n.text.takeIf { it.isNotBlank() }) else emptyList(),
                action = NowAction.OpenNotification(n.key), notification = n.takeIf { reveal },
            )
        }

    /** The next few events, the way Windows Phone's agenda showed a day rather than one entry. */
    private fun agenda(input: BoardInput): List<NowCard> {
        val events = input.agenda.filter { it.title.isNotBlank() || it.location.isNotBlank() }.take(MAX_EVENTS)
        val first = events.firstOrNull() ?: return emptyList()
        return listOf(NowCard(
            key = "agenda", kind = NowCardKind.AGENDA,
            title = first.title.ifBlank { NowCardKind.AGENDA.label },
            lines = listOf(when {
                first.allDay -> "All day"
                else -> DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(first.begin))
            }),
            action = NowAction.OpenCalendar, events = events,
        ))
    }

    /** Whatever the system's own media session is playing, however it got there. */
    private fun media(input: BoardInput, hidden: Set<String>): List<NowCard> {
        val now = input.nowPlaying?.takeIf { it.title.isNotBlank() || it.artist.isNotBlank() }?.takeIf { it.packageName !in hidden }
            ?: return emptyList()
        return listOf(NowCard(
            key = "music:${now.packageName}:${now.label}", kind = NowCardKind.MEDIA,
            title = now.title.ifBlank { now.artist },
            lines = listOfNotNull(
                now.artist.takeIf { it.isNotBlank() && it != now.title },
                now.album.takeIf { it.isNotBlank() && it != now.title },
                input.mediaAppName?.takeIf { it.isNotBlank() && it != now.title },
            ),
            action = NowAction.Expand, media = now,
        ))
    }

    /**
     * One card per category the device can answer. The card says what the app's own live
     * notification says and always names the app, so it is obvious where it came from; with no
     * notification to read it is the app and an open action, never a headline of our own.
     */
    private fun categories(input: BoardInput, visible: List<TileNotification>, reveal: Boolean, hidden: Set<String>) = NowCategory.entries.mapNotNull { category ->
        // A music app that is playing is already on the board, with its transport; a shortcut beside
        // it would say the same thing twice.
        if (category == NowCategory.MEDIA && input.nowPlaying != null) return@mapNotNull null
        val app = input.categories[category]?.takeIf { it.packageName !in hidden } ?: return@mapNotNull null
        // A notification the board already shows as a live card is not repeated here: its progress
        // and actions live on that card, where they can be used.
        val live = visible.filter { it.packageName == app.packageName && !it.isNow }.maxByOrNull { it.time }
        val shown = live?.takeIf { reveal }
        NowCard(
            key = "category:${category.name}", kind = NowCardKind.CATEGORY, source = category.label,
            title = shown?.title?.ifBlank { null } ?: app.name,
            lines = buildList {
                shown?.text?.takeIf { it.isNotBlank() }?.let { add(it) }
                add("${category.label} · ${app.name}")
            },
            action = if (shown != null) NowAction.OpenNotification(shown.key) else NowAction.OpenApp(app.packageName),
            app = app, notification = shown,
        )
    }

    /** Money is a slot the user filled, not a value we read, so it is only ever their own app. */
    private fun money(input: BoardInput): List<NowCard> = input.money?.let { app ->
        listOf(NowCard(
            key = "money", kind = NowCardKind.MONEY,
            title = app.name, lines = listOf("Open ${app.name}"),
            action = NowAction.OpenApp(app.packageName), app = app,
        ))
    }.orEmpty()
}

/**
 * The board's one user-chosen slot. It is deliberately its own tiny store rather than a field on
 * the shared settings: the money app is the user's pick, and nothing else in the launcher writes it.
 */
object BoardSlots {
    private const val STORE = "gridlauncher-now-board"
    private const val MONEY = "money"
    private var loaded = false
    private var pinned: String? = null

    /** The app the user pinned to the money slot, or null while they have not chosen one. */
    @Synchronized fun money(context: Context): String? {
        load(context)
        return pinned
    }

    /** Pins an app to the money slot, or clears it with null. */
    @Synchronized fun pin(context: Context, packageName: String?) {
        load(context)
        pinned = packageName
        runCatching {
            val editor = store(context).edit()
            if (packageName == null) editor.remove(MONEY) else editor.putString(MONEY, packageName)
            editor.apply()
        }
    }

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        pinned = runCatching { store(context).getString(MONEY, null) }.getOrNull()
    }

    private fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
}
