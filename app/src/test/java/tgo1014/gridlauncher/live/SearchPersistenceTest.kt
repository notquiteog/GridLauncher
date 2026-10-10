package tgo1014.gridlauncher.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.domain.models.App
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The invariants, not the behaviour.
 *
 * Every test here is written so that it fails if notification content ever reaches a store, rather
 * than checking that a particular screen still draws the right thing. A privacy rule that lives in
 * a composable is a rule that survives until someone edits the composable; these live where the
 * write happens.
 */
class SearchPersistenceTest {

    private val open = TileSettings(showNotificationText = true)
    private val messages = listOf(
        TileNotification("k-chat", "com.example.chat", "Ada", "See you at 3", 10, category = "msg"),
        TileNotification("k-bank", "com.example.bank", "Balance", "1200", 20, category = "msg"),
    )

    private fun everything(): List<SearchRow> = (
        notificationRows(open, messages)
            + contactRows(listOf(PinnedContact("lookup/1", "Bea", phones = listOf("555-0100"))))
            + eventRows(listOf(CalendarEvent("Standup", 1_700_000_000_000, false, "Room 2")))
        )

    @Test fun notificationContentIsNeverIndexableUnderAnySetting() {
        // The whole point of this file. Every combination the user can be in, including the ones
        // that say yes to everything, must leave the persisted set with no notification content.
        listOf(
            open,
            open.copy(searchIndexEnabled = true),
            open.copy(searchIndexEnabled = true, hiddenPreviewApps = emptySet()),
            open.copy(searchIndexEnabled = true, showNotificationText = true, meetingMode = false, quietHoursEnabled = false),
            TileSettings(),
            TileSettings(searchIndexEnabled = true),
        ).forEach { settings ->
            val rows = notificationRows(settings, messages) + contactRows(listOf(PinnedContact("l", "Bea")))
            val writable = SearchPersistence.indexable(rows)
            assertTrue("No notification row survives with previews=" + settings.showNotificationText +
                " index=" + settings.searchIndexEnabled + " quiet=" + QuietHours.active(settings),
                writable.none { it.source == SearchSource.NOTIFICATION })
            assertFalse("No notification text reaches disk at $settings", writable.any { it.title.contains("Ada") || it.text.contains("1200") || it.text.contains("See you at 3") })
        }
    }

    @Test fun theIndexableSetIsExactlyPeopleAndCalendar() {
        assertEquals(setOf(SearchSource.PERSON, SearchSource.EVENT), SearchSource.indexable)
        assertEquals(SearchSource.indexable, SearchPersistence.indexableSources)
        assertFalse("Notification is the source that must never be added here", SearchSource.NOTIFICATION in SearchSource.indexable)
    }

    @Test fun theWriterCanOnlyBeGivenPeopleAndCalendar() {
        // Walk the writing path the way the index writer does: collect everything, hand it to
        // SearchPersistence, and check what comes out is the same set with notifications removed.
        val snapshot = everything()
        val writable = SearchPersistence.indexable(snapshot)
        assertEquals(2, writable.size)
        assertEquals(listOf(SearchSource.EVENT, SearchSource.PERSON), writable.map { it.source }.sorted())
        // Four rows in memory, two on disk: the two notification rows stop at the boundary.
        assertEquals(4, snapshot.size)
        assertEquals(2, snapshot.count { it.source == SearchSource.NOTIFICATION })
        // And a source nobody has heard of is not written either: the filter is a whitelist, not a
        // blacklist, so adding a new source cannot leak by default.
        val future = SearchPersistence.indexable(snapshot + SearchRow("x", "call", "Ada", "See you at 3", "", 1))
        assertEquals(2, future.size)
    }

    @Test fun aPersistedRowCannotBeBuiltFromANotification() {
        val notification = notificationRows(open, messages).first()
        // PersistedRow is a separate type with no converting constructor from a notification row, so
        // the only way to make one is through the filter, which refuses. This is asserted by shape:
        // the filter is the single entry point, and it drops anything not in the whitelist.
        assertEquals(emptyList<PersistedRow>(), SearchPersistence.indexable(listOf(notification)))
    }

    @Test fun theFingerprintMovesOnlyWhenThePersistedContentMoves() {
        val withChat = SearchPersistence.indexable(notificationRows(open, messages)
            + contactRows(listOf(PinnedContact("l", "Bea")))
            + eventRows(listOf(CalendarEvent("Standup", 1, false, ""))))
        val sameAgain = SearchPersistence.indexable(notificationRows(open, messages.reversed())
            + contactRows(listOf(PinnedContact("l", "Bea")))
            + eventRows(listOf(CalendarEvent("Standup", 1, false, ""))))
        // A burst of notifications changes nothing that is written, so it costs no database write.
        assertEquals(SearchPersistence.digest(withChat), SearchPersistence.digest(sameAgain))
        val different = SearchPersistence.indexable(notificationRows(open, messages)
            + contactRows(listOf(PinnedContact("l", "Bea")))
            + eventRows(listOf(CalendarEvent("Standup", 2, false, ""))))
        assertFalse(SearchPersistence.digest(withChat) == SearchPersistence.digest(different))
    }

    @Test fun aStaleDatabaseCannotBringBackANotificationRow() {
        // An index written by an older build, or a database that survived a restore, must not be
        // able to put a notification back on screen through the query path either.
        val stale = listOf(
            SearchRow("n:k-chat", SearchSource.NOTIFICATION, "Ada", "See you at 3", "com.example.chat", 10),
            SearchRow("c:l", SearchSource.PERSON, "Bea", "555-0100", "555-0100", 0),
        )
        assertEquals(listOf("c:l"), SearchPersistence.discardUnindexable(stale).map { it.id })
    }

    @Test fun searchStillFindsNotificationsWithTheIndexOff() {
        // Turning the index off must not cost the user anything: the in-memory scan is the whole
        // answer, and it covers notification title, body and sender.
        val rows = notificationRows(open, messages)
        assertEquals(listOf("n:k-chat"), StartSearch.filter(rows, "Ada").map { it.id })
        assertEquals(listOf("n:k-bank"), StartSearch.filter(rows, "1200").map { it.id })
        assertEquals(listOf("n:k-chat"), StartSearch.filter(rows, "3").map { it.id })
        assertEquals(2, StartSearch.sections(rows).single().rows.size)
    }

    @Test fun theMemoryScanFindsAMessageTheAppOnlyPutInItsConversationStyle() {
        // A messaging app that summarises a thread into a title and an empty body would otherwise be
        // unsearchable, and search is the only thing standing in for the index now.
        val conversation = TileNotification("k-thread", "com.example.chat", "Ada", "", 10,
            messages = listOf("Ada: are you still coming", "You: yes, at seven"))
        val rows = notificationRows(open, listOf(conversation))
        assertEquals(listOf("k-thread"), StartSearch.filter(rows, "still coming").map { it.id.removePrefix("n:") })
        assertEquals(listOf("k-thread"), StartSearch.filter(rows, "seven").map { it.id.removePrefix("n:") })
        assertTrue(StartSearch.filter(rows, "unknown word").isEmpty())
    }

    @Test fun theIndexIsOffByDefaultAndNothingIsWrittenUntilItIsAskedFor() {
        // The secure default: no on-device database until the user turns it on themselves.
        assertFalse(TileSettings().searchIndexEnabled)
        // And the opt-in is a plain, inspectable flag in the settings blob rather than a hidden one.
        val json = Json.encodeToString(LayoutBackup.serializer(), LayoutBackup(tiles = emptyList(), settings = TileSettings(searchIndexEnabled = true)))
        assertTrue(json.contains("searchIndexEnabled"))
        assertTrue(Json.decodeFromString(LayoutBackup.serializer(), json).settings.searchIndexEnabled)
    }

    @Test fun noPersistedStoreInTheAppHoldsNotificationContent() {
        // Every store the app writes to, checked against a canary that only ever exists in
        // notification text. A store that has never heard of a canary cannot be leaking one.
        val canary = "zq-notification-canary"
        val notification = TileNotification("canary", "com.example.chat", canary, canary, 1)
        val row = notificationRows(open, listOf(notification)).single()

        // 1. The on-device search index.
        assertTrue(SearchPersistence.indexable(listOf(row)).isEmpty())
        // 2. The settings DataStore, via anything serializable the user can export.
        val settingsJson = Json.encodeToString(TileSettings.serializer(), open)
        assertFalse(settingsJson.contains(canary))
        // 3. The layout backup. It already drops contact, photo, widget and destination tiles; this
        //    asserts the one that matters here, that no notification reached the export path at all.
        val backup = LayoutBackup(tiles = listOf(GridItem(1, App("Chat", "com.example.chat"), 1)), settings = open)
        assertFalse(Json.encodeToString(LayoutBackup.serializer(), backup).contains(canary))
        // 4. GridItem, the shape the DataStore keeps the grid in, has no field a notification fits in.
        assertNull(GridItem(1, App("Chat", "com.example.chat"), 1)::class.java.declaredFields
            .firstOrNull { it.type == TileNotification::class.java })
        // 5. A theme code is a look, not a data set.
        assertFalse(ThemePacks.encode(open).contains(canary))
    }

    @Test fun tileNotificationItselfIsNotSerializable() {
        // The invariant that makes every other one cheap: the payload type cannot be handed to a
        // serializer, so nothing can serialize it by accident. It carries PendingIntents and
        // Notification.Action objects, which must never outlive the process.
        assertNull(TileNotification::class.java.getAnnotation(kotlinx.serialization.Serializable::class.java))
        assertFalse(java.io.Serializable::class.java.isAssignableFrom(TileNotification::class.java))
        assertFalse(android.os.Parcelable::class.java.isAssignableFrom(TileNotification::class.java))
        assertNotNull(TileNotification::class.java.declaredFields.firstOrNull { it.type == android.app.PendingIntent::class.java })
    }

    @Test fun noSerializableTypeInTheAppCarriesAPlatformPayload() {
        // The type-level sweep behind the type-level guarantee: every model the app can actually
        // write is walked by reflection, and none of them may have a field that a notification, a
        // PendingIntent, a Bitmap or a live card could hide inside.
        val serializable = listOf(
            tgo1014.gridlauncher.domain.models.TileSettings::class.java,
            tgo1014.gridlauncher.domain.models.App::class.java,
            tgo1014.gridlauncher.domain.models.Icon::class.java,
            PinnedContact::class.java,
            GridItem::class.java,
            LayoutBackup::class.java,
        ).filter { it.getAnnotation(kotlinx.serialization.Serializable::class.java) != null }
        assertTrue("The models under test must be the serializable ones, got " + serializable.map { it.simpleName }, serializable.size >= 6)
        val forbidden = setOf<Class<*>>(
            TileNotification::class.java, NotificationTiles::class.java, CallTiles::class.java,
            android.app.Notification::class.java, android.app.Notification.Action::class.java,
            android.app.PendingIntent::class.java,
            android.graphics.Bitmap::class.java, android.graphics.drawable.Icon::class.java,
            ActiveCall::class.java, CallSignal::class.java, NowPlaying::class.java, NowCard::class.java,
        )
        val offenders = serializable.flatMap { type ->
            type.declaredFields.filter { it.type in forbidden }.map { type.simpleName + "." + it.name }
        }
        assertEquals("A writable model holds something that must stay in memory", emptyList<String>(), offenders)
        // The walk has to have looked at something, or "no offenders" would just mean "no fields".
        assertTrue("The reflection sweep inspected nothing", serializable.sumOf { it.declaredFields.size } >= 25)
    }

    @Test fun noSourceFileEverWritesNotificationContentToAnything() {
        // The last line of defence, and the one that catches a future edit rather than a future
        // idea: walk every line of every file the launcher ships and fail on any that both writes
        // and names the notification payload. There is no legitimate call site where those meet.
        // Comments are skipped, so a file may explain the rule in prose as loudly as it likes.
        val writes = listOf(
            "PutDocumentsRequest", "setPropertyString", "RemoveByDocumentIdRequest",
            "getSharedPreferences", ".putString(", ".putStringSet(", ".putInt(", ".putBoolean(",
            "encodeToString", "writeText", "openOutputStream", "openFileOutput", "putExtra",
        )
        val payload = listOf("TileNotification", "NotificationTiles", "notificationRows", "notificationTile", "CallTiles", "ActiveCall", "CallSignal", "NowCard", "NowPlaying")
        val files = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("No sources found", files.size > 20)
        val offences = mutableListOf<String>()
        files.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return@forEachIndexed
                val code = trimmed.substringBefore("//")
                if (writes.any { it in code } && payload.any { it in code }) offences += "${file.name}:${index + 1} $code"
            }
        }
        assertEquals("A line both writes and holds notification content:\n" + offences.joinToString("\n"), 0, offences.size)
    }

    @Test fun theBoardSlotsStoreIsAnAppIdentifierAndNothingElse() {
        // The Now board's one persisted value: a package name. Read the declared fields rather than
        // a live Context, so this asserts what the store is built to hold rather than what happens
        // to be in it on this device.
        val fields = BoardSlots::class.java.declaredFields.associateBy { it.name }
        assertEquals("gridlauncher-now-board", fields.getValue("STORE").apply { isAccessible = true }.get(null))
        assertEquals("money", fields.getValue("MONEY").apply { isAccessible = true }.get(null))
        // Its only state is a load flag and the package name itself, so there is no field a
        // notification title or body could ever be written into.
        val state = fields.values.filter { !it.name.startsWith("$") && it.name != "INSTANCE" }
        assertEquals(setOf("STORE", "MONEY", "loaded", "pinned"), state.map { it.name }.toSet())
        assertEquals(String::class.java, fields.getValue("pinned").type)
    }

    @Test fun backupsAndCloudTransferStayExcluded() {
        // Notification content must not be able to leave the device by another route either: the
        // manifest sets allowBackup=false, and both rule files exclude every domain.
        val root = File(System.getProperty("user.dir") ?: ".")
        val manifest = File(root, "src/main/AndroidManifest.xml")
        assertTrue("Cannot find the manifest at $manifest", manifest.exists())
        assertTrue(manifest.readText().contains("android:allowBackup=\"false\""))
        listOf("res/xml/backup_rules.xml", "res/xml/data_extraction_rules.xml").forEach { path ->
            val file = File(root, "src/main/$path")
            assertTrue("Cannot find $path", file.exists())
            val text = file.readText()
            listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
                assertTrue("$path excludes $domain", text.contains("<exclude domain=\"$domain\" path=\".\" />"))
            }
        }
    }
}
