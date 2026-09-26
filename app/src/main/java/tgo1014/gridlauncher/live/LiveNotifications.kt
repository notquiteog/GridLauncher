package tgo1014.gridlauncher.live

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notification text stays in memory, is never logged, stored or sent over the network. */
data class TileNotification(val key: String, val packageName: String, val title: String, val text: String, val time: Long)

object NotificationTiles {
    private val mutable = MutableStateFlow<List<TileNotification>>(emptyList())
    val notifications = mutable.asStateFlow()
    @Synchronized fun replace(items: List<TileNotification>) { mutable.value = items.sortedByDescending { it.time } }
    @Synchronized fun post(item: TileNotification) { replace(mutable.value.filterNot { it.key == item.key } + item) }
    @Synchronized fun remove(key: String) { replace(mutable.value.filterNot { it.key == key }) }
}

class LiveNotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        NotificationTiles.replace(runCatching { activeNotifications.mapNotNull { it.toTile() } }.getOrDefault(emptyList()))
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val tile = sbn.toTile()
        if (tile == null) NotificationTiles.remove(sbn.key) else NotificationTiles.post(tile)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { NotificationTiles.remove(sbn.key) }
    override fun onListenerDisconnected() { NotificationTiles.replace(emptyList()) }
    override fun onDestroy() { NotificationTiles.replace(emptyList()); super.onDestroy() }

    private fun StatusBarNotification.toTile(): TileNotification? {
        val n = notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.visibility == Notification.VISIBILITY_SECRET) return null
        val public = if (n.visibility == Notification.VISIBILITY_PRIVATE) n.publicVersion else n
        return TileNotification(key, packageName,
            public?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(200).orEmpty(),
            public?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.take(500)
                ?: public?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(500).orEmpty(), postTime)
    }
}
