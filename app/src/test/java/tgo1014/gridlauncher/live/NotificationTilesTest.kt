package tgo1014.gridlauncher.live

import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class NotificationTilesTest {
    @After fun clear() = NotificationTiles.replace(emptyList())
    @Test fun updateReplacesSameKeyAndRemovalDoesNotLeaveStaleCount() {
        NotificationTiles.post(TileNotification("one", "app", "First", "", 1))
        NotificationTiles.post(TileNotification("one", "app", "Updated", "", 2))
        NotificationTiles.post(TileNotification("two", "other", "Other", "", 3))
        assertEquals(2, NotificationTiles.notifications.value.size)
        assertEquals("Updated", NotificationTiles.notifications.value.last().title)
        NotificationTiles.remove("one")
        assertEquals(listOf("other"), NotificationTiles.notifications.value.map { it.packageName })
        NotificationTiles.replace(emptyList())
        assertTrue(NotificationTiles.notifications.value.isEmpty())
    }
}
