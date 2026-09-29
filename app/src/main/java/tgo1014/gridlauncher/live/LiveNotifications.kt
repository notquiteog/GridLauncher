package tgo1014.gridlauncher.live

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.Annotation
import android.text.Spanned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Payloads and action tokens are memory-only. Never serialize, log or back them up. */
data class TileNotification(
    val key: String, val packageName: String, val title: String, val text: String, val time: Long,
    val category: String? = null, val ongoing: Boolean = false,
    val progress: Int = 0, val progressMax: Int = 0, val indeterminate: Boolean = false,
    val artwork: Bitmap? = null, val artworkIcon: Icon? = null, val avatar: Icon? = null, val messages: List<String> = emptyList(),
    val actions: List<Notification.Action> = emptyList(), val contentIntent: PendingIntent? = null,
    val people: List<String> = emptyList(), val semantic: Int = 0, val promoted: Boolean = false,
) {
    val isNow: Boolean get() = ongoing && (progressMax > 0 || indeterminate || promoted || category in listOf(Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_STOPWATCH, Notification.CATEGORY_PROGRESS))
}

object NotificationTiles {
    private val mutable = MutableStateFlow<List<TileNotification>>(emptyList())
    val notifications = mutable.asStateFlow()
    @Synchronized fun replace(items: List<TileNotification>) { mutable.value = items.sortedByDescending { it.time }.take(200) }
    @Synchronized fun post(item: TileNotification) { replace(mutable.value.filterNot { it.key == item.key } + item) }
    @Synchronized fun remove(key: String) { replace(mutable.value.filterNot { it.key == key }) }

    fun act(context: Context, notificationKey: String, action: Notification.Action, reply: String? = null): Boolean {
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        // A dismissed/replaced notification must not retain a usable action in an open sheet.
        val current = mutable.value.firstOrNull { it.key == notificationKey } ?: return false
        if (current.actions.none { it.actionIntent == action.actionIntent }) return false
        return runCatching {
            val intent = Intent()
            if (reply != null) {
                require(reply.isNotBlank() && reply.length <= 2000)
                val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }.orEmpty()
                require(inputs.isNotEmpty())
                RemoteInput.addResultsToIntent(inputs.toTypedArray(), intent, Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, reply) } })
                RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            } else require(action.remoteInputs?.none { it.allowFreeFormInput } != false)
            action.actionIntent.send(context, 0, intent)
        }.isSuccess
    }
    fun open(context: Context, key: String): Boolean {
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        val intent = mutable.value.firstOrNull { it.key == key }?.contentIntent ?: return false
        return runCatching { intent.send() }.isSuccess
    }
}

class LiveNotificationService : NotificationListenerService() {
    override fun onListenerConnected() { NotificationTiles.replace(runCatching { activeNotifications.mapNotNull { it.toTile() } }.getOrDefault(emptyList())); LiveTileWidget.refresh(this) }
    override fun onNotificationPosted(sbn: StatusBarNotification) { val tile = sbn.toTile(); if (tile == null) NotificationTiles.remove(sbn.key) else NotificationTiles.post(tile); LiveTileWidget.refresh(this) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { NotificationTiles.remove(sbn.key); LiveTileWidget.refresh(this) }
    override fun onListenerDisconnected() { NotificationTiles.replace(emptyList()) }
    override fun onDestroy() { NotificationTiles.replace(emptyList()); super.onDestroy() }
}

internal fun StatusBarNotification.toTile(): TileNotification? = runCatching { notificationTile(key, packageName, notification, postTime) }.getOrNull()

@Suppress("DEPRECATION")
internal fun notificationTile(key: String, packageName: String, original: Notification, time: Long): TileNotification? {
    if (original.flags and Notification.FLAG_GROUP_SUMMARY != 0 || original.visibility == Notification.VISIBILITY_SECRET) return null
    // Android supplies any mandatory redaction. UI additionally requires opt-in and an unlocked device.
    val n: Notification? = original
    val extras = n?.extras ?: Bundle.EMPTY
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)
    val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)
    val messages = if (n != null) runCatching { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages.orEmpty() }.getOrDefault(emptyList()) else emptyList()
    val people = messages.mapNotNull { it.person?.uri }.distinct()
    val icon = messages.lastOrNull()?.person?.icon?.toIcon()
    val bitmap = (extras.get(Notification.EXTRA_PICTURE) as? Bitmap) ?: (extras.get(Notification.EXTRA_LARGE_ICON_BIG) as? Bitmap) ?: (extras.get(Notification.EXTRA_LARGE_ICON) as? Bitmap)
    // Bound retained artwork; avoid retaining arbitrarily large app-provided images.
    val artwork = bitmap?.let { if (it.width > 384 || it.height > 384) { val ratio = 384f / maxOf(it.width, it.height); Bitmap.createScaledBitmap(it, (it.width * ratio).toInt().coerceAtLeast(1), (it.height * ratio).toInt().coerceAtLeast(1), true) } else it }
    return TileNotification(key, packageName, title?.toString()?.take(200).orEmpty(), text?.toString()?.take(1000).orEmpty(), time,
        category = n?.category, ongoing = original.flags and Notification.FLAG_ONGOING_EVENT != 0,
        progress = extras.getInt(Notification.EXTRA_PROGRESS).coerceAtLeast(0), progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX).coerceAtLeast(0),
        indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE), artwork = artwork, artworkIcon = n?.getLargeIcon(), avatar = icon,
        messages = messages.takeLast(5).map { "${it.person?.name ?: ""}: ${it.text}".take(500) },
        actions = n?.actions?.filter { it.actionIntent != null }?.take(5).orEmpty(), contentIntent = n?.contentIntent,
        people = people, semantic = semanticStyle(title, text), promoted = extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
}

internal fun semanticStyle(vararg texts: CharSequence?): Int {
    val styles = (1..4).associateWith { Notification.createSemanticStyleAnnotation(it) }
    return texts.filterIsInstance<Spanned>().flatMap { it.getSpans(0, it.length, Annotation::class.java).toList() }
        .mapNotNull { annotation -> styles.entries.firstOrNull { it.value.key == annotation.key && it.value.value == annotation.value }?.key }.maxOrNull() ?: 0
}
