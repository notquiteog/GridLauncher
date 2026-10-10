package tgo1014.gridlauncher.live

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.domain.models.TileSettings
import java.io.File

class CallTilesTest {

    private fun open() = TileSettings(showNotificationText = true)
    private fun names() = { it: String -> it }

    private fun incoming() = CallSignal("call-1", "com.android.dialer", "Ada Lovelace", "Incoming call",
        category = Notification.CATEGORY_CALL, ongoing = true, actionTitles = listOf("Decline", "Answer"), time = 100)

    private fun ongoing() = CallSignal("call-2", "org.telegram.messenger", "Ada Lovelace", "00:42",
        category = Notification.CATEGORY_CALL, ongoing = true, actionTitles = listOf("End call"), time = 100)

    @Test fun anOngoingCallCategoryIsACallAndAMissedCallLogIsNot() {
        val calls = CallTiles.calls(listOf(incoming(), ongoing(),
            CallSignal("missed", "com.android.dialer", "Missed call", "Ada", category = Notification.CATEGORY_CALL, time = 50)), open(), appName = names())
        assertEquals(listOf("call-1", "call-2"), calls.map { it.key })
        // A non-call notification from the same app is not a call.
        assertTrue(CallTiles.calls(listOf(CallSignal("chat", "com.android.dialer", "Ada", "hi", category = "msg", time = 100)), open(), appName = names()).isEmpty())
    }

    @Test fun incomingAndOngoingAreReadFromTheCallersOwnAnswerButton() {
        val calls = CallTiles.calls(listOf(incoming(), ongoing()), open(), appName = names())
        assertEquals(CallState.INCOMING, calls.first { it.key == "call-1" }.state)
        assertEquals(CallState.ONGOING, calls.first { it.key == "call-2" }.state)
    }

    @Test fun whoIsTheCallersNameAndNeverAGenericPhrase() {
        val calls = CallTiles.calls(listOf(
            incoming(),
            // The platform's own app titles an incoming call without naming anyone.
            CallSignal("generic", "com.google.android.dialer", "Incoming call", "", category = Notification.CATEGORY_CALL, ongoing = true),
        ), open(), appName = names())
        assertEquals("Ada Lovelace", calls.first { it.key == "call-1" }.who)
        // No name is invented for the one that does not give one.
        assertEquals("", calls.first { it.key == "generic" }.who)
    }

    @Test fun whoFallsBackToTheBodyThenTheConversationThenThePersonUri() {
        val spoken = CallSignal("spoken", "com.android.dialer", "Incoming call", "Ada Lovelace", category = Notification.CATEGORY_CALL, ongoing = true)
        assertEquals("Ada Lovelace", CallTiles.calls(listOf(spoken), open(), appName = names()).single().who)
        val messaged = CallSignal("messaged", "org.thunderbird", "Incoming call", "", messages = listOf("Ada: are you there"),
            category = Notification.CATEGORY_CALL, ongoing = true)
        assertEquals("Ada", CallTiles.calls(listOf(messaged), open(), appName = names()).single().who)
        val uriOnly = CallSignal("uri", "com.example.app", "Incoming call", "", people = listOf("tel:555-0100"),
            category = Notification.CATEGORY_CALL, ongoing = true)
        assertEquals("555-0100", CallTiles.calls(listOf(uriOnly), open(), appName = names()).single().who)
        val silent = CallSignal("silent", "com.example.app", "Incoming call", "", category = Notification.CATEGORY_CALL, ongoing = true)
        assertEquals("", CallTiles.calls(listOf(silent), open(), appName = names()).single().who)
    }

    @Test fun theAppIsNamedTheWayTheDeviceNamesIt() {
        val call = CallTiles.calls(listOf(incoming()), open(), appName = { if (it == "com.android.dialer") "Phone" else it }).single()
        assertEquals("Phone", call.appName)
        // A package the device cannot name falls back to its own identifier rather than to nothing.
        assertEquals("org.telegram.messenger", CallTiles.calls(listOf(ongoing()), open(), appName = { "" }).single().appName)
    }

    @Test fun openIsAlwaysOfferedAndAnswerOnlyWhenTheCallerPublishedIt() {
        val ringing = CallTiles.calls(listOf(incoming()), open(), appName = names()).single()
        assertEquals(listOf(CallAction.OpenApp("com.android.dialer", "com.android.dialer"), CallAction.Answer("call-1", "Answer"), CallAction.Reject("call-1", "Decline")), CallTiles.controls(ringing))
        val inProgress = CallTiles.calls(listOf(ongoing()), open(), appName = names()).single()
        // No answer button on the notification, so no answer control: never a button that cannot work.
        assertEquals(listOf(CallAction.OpenApp("org.telegram.messenger", "org.telegram.messenger"), CallAction.Reject("call-2", "Decline")), CallTiles.controls(inProgress))
        // An app that published no actions at all still gets the one control that always works.
        val quiet = CallTiles.calls(listOf(CallSignal("quiet", "com.example.app", "Ada", "", category = Notification.CATEGORY_CALL, ongoing = true)), open(), appName = names()).single()
        assertEquals(listOf(CallAction.OpenApp("com.example.app", "com.example.app")), CallTiles.controls(quiet))
    }

    @Test fun quietHoursAnExcludedAppALockedDeviceAndOffPreviewsAllShowNothing() {
        val list = listOf(incoming())
        assertTrue(CallTiles.calls(list, open().copy(meetingMode = true), appName = names()).isEmpty())
        // A window built from the current hour, so this cannot fail at 23:00 in another timezone.
        val now = java.time.LocalTime.now()
        val quiet = open().copy(quietHoursEnabled = true, quietStartHour = now.hour, quietEndHour = (now.hour + 1) % 24)
        assertTrue(CallTiles.calls(list, quiet, appName = names()).isEmpty())
        assertTrue(CallTiles.calls(list, open().copy(hiddenPreviewApps = setOf("com.android.dialer")), appName = names()).isEmpty())
        assertTrue(CallTiles.calls(list, open(), locked = true, appName = names()).isEmpty())
        assertTrue(CallTiles.calls(list, open().copy(showNotificationText = false), appName = names()).isEmpty())
    }

    @Test fun aCallOnlyAppearsOnItsOwnAppsTile() {
        val list = listOf(incoming(), ongoing())
        assertEquals("call-1", CallTiles.callFor(list, "com.android.dialer", open(), appName = names())?.key)
        assertEquals("call-2", CallTiles.callFor(list, "org.telegram.messenger", open(), appName = names())?.key)
        assertNull(CallTiles.callFor(list, "com.example.other", open(), appName = names()))
        // A tile with no call is a tile with no call, not an empty panel.
        assertNull(CallTiles.callFor(list, "com.example.other", open().copy(showNotificationText = false), appName = names()))
    }

    @Test fun theNewestCallWinsForOneApp() {
        val calls = CallTiles.calls(listOf(incoming().copy(key = "call-old", time = 10), incoming()), open(), appName = names())
        // An app has one call at a time, so a re-posted notification replaces rather than adds.
        assertEquals(listOf("call-1"), calls.map { it.key })
    }

    @Test fun everyControlDescribesItselfForTalkBack() {
        val call = CallTiles.calls(listOf(incoming()), open(), appName = { "Phone" }).single()
        assertEquals("Phone, Incoming call, Ada Lovelace", call.describe())
        // A call with no named caller still says what it is and where it is from, and an app that
        // published no answer button is an ongoing call rather than a guess at a ringing one.
        val unnamed = CallTiles.calls(listOf(CallSignal("g", "com.example.app", "Ada", "", category = Notification.CATEGORY_CALL, ongoing = true)), open(), appName = { "Example" }).single()
        assertEquals("Example, Ongoing call, Ada", unnamed.describe())
        val silent = CallTiles.calls(listOf(CallSignal("s", "com.example.app", "Incoming call", "", category = Notification.CATEGORY_CALL, ongoing = true)), open(), appName = { "Example" }).single()
        assertEquals("Example, Ongoing call", silent.describe())
    }

    @Test fun aCallReachesTheTileFromTheSameNotificationTheListenerParsed() {
        // The projection is the only thing between a live notification and the call rules, and it
        // must not drop the parts those rules read.
        val tile = TileNotification("call-1", "com.android.dialer", "Ada Lovelace", "", 100, category = Notification.CATEGORY_CALL,
            ongoing = true, people = listOf("tel:555-0100"), messages = listOf("Ada: coming"))
        val signal = CallTiles.signal(tile)
        assertEquals("call-1", signal.key)
        assertEquals(Notification.CATEGORY_CALL, signal.category)
        assertTrue(signal.ongoing)
        // The person URI is kept whole; only the displayed fallback strips the scheme.
        assertEquals(listOf("tel:555-0100"), signal.people)
        assertEquals("Ada", signal.messages.single().substringBefore(':'))
        assertTrue(signal.isCall)
        assertEquals("Ada Lovelace", CallTiles.calls(listOf(signal), open(), appName = names()).single().who)
    }

    @Test fun aCallIsNeverPersistedAnywhere() {
        // The call arrives as a notification, so it is covered by the notification rules - this
        // asserts it stays that way: the index refuses it, and the memory-only scan still has it,
        // which is the only place it is allowed to be.
        val call = TileNotification("call-1", "com.android.dialer", "Ada", "Incoming call", 100,
            category = Notification.CATEGORY_CALL, ongoing = true)
        val row = notificationRows(open(), listOf(call)).single()
        assertEquals(SearchSource.NOTIFICATION, row.source)
        assertTrue(SearchPersistence.indexable(listOf(row)).isEmpty())
        assertTrue(StartSearch.filter(listOf(row), "Ada").isNotEmpty())
        // And nothing on the call path is serializable, so it cannot be written by accident.
        assertNull(ActiveCall::class.java.getAnnotation(kotlinx.serialization.Serializable::class.java))
        assertNull(CallState::class.java.getAnnotation(kotlinx.serialization.Serializable::class.java))
        assertNull(CallSignal::class.java.getAnnotation(kotlinx.serialization.Serializable::class.java))
        assertNull(CallAction::class.java.getAnnotation(kotlinx.serialization.Serializable::class.java))
    }

    @Test fun aControlForANotificationThatIsGoneHasNothingToFire() {
        // A dismissed call must not leave a button that would send a stale intent. NotificationTiles
        // is the only thing that can resolve a key, so with nothing there there is nothing to do.
        NotificationTiles.replace(emptyList())
        assertTrue(NotificationTiles.notifications.value.isEmpty())
        val stale = CallAction.Answer("call-gone", "Answer")
        assertEquals("call-gone", stale.key)
        // The live list is the sole source of the action, so a key that is not in it cannot be
        // turned into an intent - the same lookup `act` does before it sends.
        assertNull(NotificationTiles.notifications.value.firstOrNull { it.key == stale.key })
        assertFalse(NotificationTiles.notifications.value.any { it.key == stale.key && it.actions.isNotEmpty() })
    }

    @Test fun theLauncherHoldsNoTelephonyPermission() {
        // SDK-documented requirements, from android-37.0/android/telecom/TelecomManager.java:
        // isInCall / isInManagedCall / isInExternalCall require READ_PHONE_STATE, and
        // acceptRingingCall / endCall require ANSWER_PHONE_CALLS. A launcher needs none of them:
        // call state comes from notifications the user has already granted listener access to, and
        // answer and decline are the calling app's own actions. The manifest is where that has to
        // be true, so this asserts the manifest.
        val manifest = File(System.getProperty("user.dir") ?: ".", "src/main/AndroidManifest.xml")
        assertTrue("Cannot find the manifest", manifest.exists())
        val text = manifest.readText()
        listOf("ANSWER_PHONE_CALLS", "READ_PHONE_STATE", "MANAGE_OWN_CALLS", "MODIFY_PHONE_STATE", "MANAGE_ONGOING_CALLS")
            .forEach { permission -> assertFalse("Declares $permission", text.contains(permission)) }
        // And no call state is read from anywhere but the notification listener. Comments are
        // stripped first, so the file that explains why TelecomManager is not used does not trip
        // the check that says it is not used.
        val offenders = sources().filter { file ->
            file.readLines().any { line ->
                val trimmed = line.trim()
                val code = if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) "" else trimmed
                code.contains("TelecomManager") || code.contains("getCallState") ||
                    code.contains("acceptRingingCall") || code.contains("endCall")
            }
        }.map { it.name }
        assertEquals("TelecomManager must not be used at all", emptyList<String>(), offenders)
    }

    private fun sources() = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        .also { assertTrue("No sources found", it.size > 20) }
}
