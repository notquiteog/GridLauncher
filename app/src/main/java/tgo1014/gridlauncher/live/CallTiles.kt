package tgo1014.gridlauncher.live

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.widget.Toast
import tgo1014.gridlauncher.domain.models.TileSettings

/**
 * A call that is happening right now, as far as this launcher can honestly know.
 *
 * There is no call state of our own. A call is only ever something an app has said out loud in a
 * notification the user has already granted us listener access to, so this is a reading of that
 * notification and not an independent source: [key] points back into [NotificationTiles], and [who]
 * and [state] are the app's own words, or the platform's own answer button.
 */
data class ActiveCall(
    val key: String,
    val packageName: String,
    val appName: String,
    val who: String,
    val state: CallState,
    val canAnswer: Boolean,
    val canDecline: Boolean,
) {
    /** One string for TalkBack: which app, what is happening, and who it is about. */
    fun describe() = listOf(appName, state.label, who).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

enum class CallState(val label: String) {
    INCOMING("Incoming call"),
    ONGOING("Ongoing call"),
}

/**
 * What a call tile can do. Every action here either launches an app or fires an action the calling
 * app itself put on its own notification, so there is no control that can appear and then quietly
 * do nothing.
 */
sealed interface CallAction {
    data class OpenApp(val packageName: String, val appName: String) : CallAction
    data class Answer(val key: String, val title: String) : CallAction
    data class Reject(val key: String, val title: String) : CallAction
}

/**
 * What a live notification says, reduced to the parts a call tile can honestly use.
 *
 * The platform-free half of [TileNotification], so the call rules - which notification counts, who
 * is calling, which way it is going, and what the privacy contract allows - stay testable on the
 * JVM, exactly as `NowBoard` and `StartSearch` do. Nothing here is stored.
 */
data class CallSignal(
    val key: String, val packageName: String, val title: String, val text: String,
    val messages: List<String> = emptyList(), val people: List<String> = emptyList(),
    val category: String? = null, val ongoing: Boolean = false,
    val actionTitles: List<String> = emptyList(), val time: Long = 0,
) {
    internal val isCall: Boolean get() = category == Notification.CATEGORY_CALL
}

/**
 * A call on one of the device's live notifications, shown on that app's tile.
 *
 * Detection is notifications only. `TelecomManager` is not used, and cannot honestly be: reading
 * call state through it - `isInCall`, `isInManagedCall`, `isInExternalCall` - is documented in the
 * SDK as requiring `READ_PHONE_STATE`, and answering or ending a call is documented as requiring
 * `ANSWER_PHONE_CALLS`. A launcher has no business holding either, so this app declares neither,
 * offers the app-launch route, and offers answer and decline only where the calling app published
 * those buttons on the notification it is still showing - the listener can fire those with no
 * permission at all, because they are the app's own intents.
 *
 * The privacy contract is the launcher's existing one, applied whole: quiet hours show nothing at
 * all, an app excluded from previews is not on a tile, a locked device or switched-off previews say
 * nothing about a call, and nothing here is ever written down.
 */
object CallTiles {

    /** The caller's own button says this. Matching the app's own action is how we know. */
    private val answerWords = setOf("answer", "answer call", "accept", "accept call", "pick up")
    private val declineWords = setOf("decline", "reject", "end call", "end", "hang up", "ignore")

    /**
     * Titles Android's own phone app uses when the notification is about the call rather than about
     * a person, so a tile does not read "Incoming call" back to the user as if it were a name.
     */
    private val generic = setOf("incoming call", "incoming voice call", "ongoing call", "outgoing call", "call in progress", "ringing")

    /** What the platform-free notification model says, with no platform object retained. */
    fun signal(notification: TileNotification) = CallSignal(
        key = notification.key, packageName = notification.packageName,
        title = notification.title, text = notification.text,
        messages = notification.messages, people = notification.people,
        category = notification.category, ongoing = notification.ongoing,
        actionTitles = notification.actions.map { it.title.toString() }, time = notification.time,
    )

    /**
     * Every call this device is currently telling us about, newest first.
     *
     * A notification counts as a call when the app categorised it as one, and it is either flagged
     * ongoing or carries an answer button. A missed-call log entry is categorised as a call too and
     * is neither, so it never becomes a call tile.
     */
    fun calls(
        notifications: List<CallSignal>,
        settings: TileSettings,
        locked: Boolean = false,
        appName: (String) -> String = { it },
    ): List<ActiveCall> {
        if (QuietHours.active(settings)) return emptyList()
        // A locked device or a switched-off preview says nothing about a call, the same as it says
        // nothing about a message: no call tile, no count, no name.
        if (locked || !settings.showNotificationText) return emptyList()
        return notifications.asSequence()
            .filter { it.isCall && it.packageName !in settings.hiddenPreviewApps }
            .filter { it.ongoing || it.actionTitles.any(::isAnswer) }
            .sortedByDescending { it.time }
            .map { call(it, appName) }
            // One app, one call: a tile shows the newest thing that app is doing, not a list.
            .distinctBy { it.packageName }
            .toList()
    }

    /** The call on one app's tile, or null. A tile never shows someone else's call. */
    fun callFor(
        notifications: List<CallSignal>,
        packageName: String,
        settings: TileSettings,
        locked: Boolean = false,
        appName: (String) -> String = { it },
    ): ActiveCall? = calls(notifications, settings, locked, appName).firstOrNull { it.packageName == packageName }

    /**
     * What this call can honestly offer. "Open in <app>" always, because launching an app always
     * works. Answer and decline only when the calling app put those buttons on the notification it
     * is still showing.
     */
    fun controls(call: ActiveCall): List<CallAction> = buildList {
        add(CallAction.OpenApp(call.packageName, call.appName))
        if (call.canAnswer) add(CallAction.Answer(call.key, "Answer"))
        if (call.canDecline) add(CallAction.Reject(call.key, "Decline"))
    }

    /** The action a control would fire, or null once the notification is gone. Never a stale intent. */
    fun actionFor(notification: TileNotification, answer: Boolean): Notification.Action? = notification.actions
        .firstOrNull { if (answer) isAnswer(it.title.toString()) else isDecline(it.title.toString()) }
        // The app's own answer button carries a PendingIntent, not a remote input, so this never asks
        // for text the user has not typed. Anything else is a button that would not work.
        ?.takeIf { it.remoteInputs?.none { input -> input.allowFreeFormInput } ?: true }

    /** Runs a control. Returns false rather than pretending when the notification has gone. */
    fun act(context: Context, action: CallAction): Boolean = when (action) {
        is CallAction.OpenApp -> open(context, action.packageName)
        is CallAction.Answer -> send(context, action.key, answer = true)
        is CallAction.Reject -> send(context, action.key, answer = false)
    }

    private fun call(notification: CallSignal, appName: (String) -> String): ActiveCall {
        val canAnswer = notification.actionTitles.any(::isAnswer)
        val canDecline = notification.actionTitles.any(::isDecline)
        return ActiveCall(
            key = notification.key,
            packageName = notification.packageName,
            appName = appName(notification.packageName).ifBlank { notification.packageName },
            who = who(notification),
            // An app that published an answer button is telling us the phone is ringing. Anything
            // else categorised as a call and still going is a call already in progress.
            state = if (canAnswer) CallState.INCOMING else CallState.ONGOING,
            canAnswer = canAnswer,
            canDecline = canDecline,
        )
    }

    /**
     * Who is calling, if the notification says. The title is the caller's name for every messaging
     * app and a generic phrase for the platform's own; the body, the conversation's last sender and
     * the platform's person URI are the honest fallbacks, and an app that says none of them gets
     * nothing invented for it.
     */
    private fun who(notification: CallSignal): String {
        val title = notification.title.trim()
        if (title.isNotBlank() && title.lowercase() !in generic) return title.take(80)
        val body = notification.text.trim()
        if (body.isNotBlank() && body.lowercase() !in generic) return body.take(80)
        val sender = notification.messages.lastOrNull()?.substringBefore(':')?.trim().orEmpty()
        if (sender.isNotBlank()) return sender.take(80)
        return notification.people.lastOrNull { it.startsWith("tel:") || it.startsWith("mailto:") }
            ?.substringAfter(':')?.take(80).orEmpty()
    }

    private fun isAnswer(title: String) = title.trim().lowercase() in answerWords
    private fun isDecline(title: String) = title.trim().lowercase() in declineWords

    private fun send(context: Context, key: String, answer: Boolean): Boolean {
        val notification = NotificationTiles.notifications.value.firstOrNull { it.key == key } ?: return false
        val action = actionFor(notification, answer) ?: return false
        return NotificationTiles.act(context, key, action)
    }

    private fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent == null) return unavailable(context)
        return runCatching { context.startActivity(intent) }.fold(onSuccess = { true }, onFailure = { unavailable(context) })
    }

    /** An app the device cannot start, or one that has just been uninstalled: say so, do not lie. */
    private fun unavailable(context: Context): Boolean {
        runCatching { Toast.makeText(context, "That app is unavailable", Toast.LENGTH_SHORT).show() }
        return false
    }
}
