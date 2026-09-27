package tgo1014.gridlauncher

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tgo1014.gridlauncher.ui.MainActivity
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.live.*

@RunWith(AndroidJUnit4::class)
class LauncherTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun seed(tiles: List<GridItem>) {
        NotificationTiles.replace(emptyList())
        runBlocking {
            compose.activity.profiles.select("Personal")
            compose.activity.settingsRepository.updateSettings(TileSettings())
            compose.activity.appsManager.setGrid(tiles)
        }
        compose.waitForIdle()
    }
    @Test fun homeIntentAndAppDrawerWork() {
        val context = compose.activity
        val homes = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        assertTrue(homes.any { it.activityInfo.packageName == context.packageName })
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Search apps").performTextInput("zzzz-no-such-app")
        compose.onNodeWithText("No apps found").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Start ←").performClick()
        compose.onNodeWithContentDescription("Clock").assertIsDisplayed()
    }
    @Test fun tileResizeMoveAndUnpinPersist() {
        seed(listOf(GridItem(1, App("Battery", BuiltInTiles.BATTERY), 1)))
        compose.onNodeWithText("Edit layout").performClick()
        compose.onNodeWithContentDescription("Battery").performClick()
        compose.onNodeWithText("2×1").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().first().width == 2 } }
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().isEmpty() } }
    }
    @Test fun builtInTilesSurviveActivityRecreationAndPackageRefresh() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Clock").assertIsDisplayed()
        runBlocking { compose.activity.updateAppListUseCase() }
        compose.waitForIdle()
        assertEquals(BuiltInTiles.CLOCK, runBlocking { compose.activity.appsManager.homeGridFlow.first().single().app.packageName })
    }
    @Test fun notificationsUpdateBadgeAndPreviewThenClear() {
        seed(listOf(GridItem(1, App("Settings", "com.android.settings"), 2, height = 1)))
        runBlocking { compose.activity.settingsRepository.updateSettings(TileSettings(showNotificationText = true)) }
        NotificationTiles.post(TileNotification("message", "com.android.settings", "Hello from Android", "A real tile update", 1))
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hello from Android").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("A real tile update").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings, 1 notifications").assertIsDisplayed()
        NotificationTiles.remove("message")
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("Hello from Android").assertDoesNotExist()
    }
    @Test fun notificationListenerReceivesAndroidPostedNotification() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        val tag = "grid-test-${System.currentTimeMillis()}"
        val component = "io.github.notquiteog.gridlauncher/tgo1014.gridlauncher.live.LiveNotificationService"
        fun shell(command: String) {
            val output = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        }
        try {
            shell("cmd notification allow_listener $component")
            shell("cmd notification post -t GridLauncher $tag Android-notification")
            compose.waitUntil(15000) { NotificationTiles.notifications.value.any { it.packageName == "com.android.shell" && it.key.contains(tag) } }
            assertTrue(NotificationTiles.notifications.value.first { it.key.contains(tag) }.time > 0)
            compose.onNodeWithContentDescription("Clock").assertIsDisplayed()
        } finally { shell("cmd notification disallow_listener $component"); NotificationTiles.replace(emptyList()) }
    }
    @Test fun longPressShowsAppActionsAndMovementRequiresExplicitEditMode() {
        seed(listOf(GridItem(1, App("Settings", "com.android.settings"), 1)))
        compose.onNodeWithContentDescription("Settings").performTouchInput { longClick() }
        compose.onNodeWithText("App info").assertIsDisplayed()
        compose.onNodeWithText("Move tile").assertDoesNotExist()
        assertEquals(0, runBlocking { compose.activity.appsManager.homeGridFlow.first().single().x })
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Edit layout").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Edit tile").assertIsDisplayed()
        compose.onNodeWithText("Move tile").performClick()
        compose.onNodeWithText("Right").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().single().x == 1 } }
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().single().positionPinned } }
        compose.onNodeWithText("Done moving").assertIsNotEnabled()
    }
    @Test fun settingsOpenAndFolderContentsDisplay() {
        seed(listOf(GridItem(1, App("Favorites", BuiltInTiles.FOLDER), 1, children = listOf(App("Settings", "com.android.settings")))))
        compose.onNodeWithContentDescription("Favorites").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Customize Start").performClick()
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        compose.onNodeWithText("Live tiles").assertIsDisplayed()
    }
    @Test fun appDrawerUsesWhiteTextInDarkMode() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Search apps").performTextInput("Settings")
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNode(hasText("Settings") and !hasSetTextAction(), useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(androidx.compose.ui.graphics.Color.White, results.single().layoutInput.style.color)
    }
    @Test fun profileLayoutsAndColumnChangesPersistWithoutCollisions() {
        val grid = (0 until 12).map { GridItem(it, App("Clock", BuiltInTiles.CLOCK), 1, x = it % 3, y = it / 3) }
        seed(grid)
        runBlocking {
            compose.activity.settingsRepository.updateSettings(TileSettings(tilesAcross = 6))
            compose.activity.profiles.duplicateInto("Work")
            compose.activity.profiles.select("Work")
            compose.activity.appsManager.setGrid(listOf(GridItem(99, App("Battery", BuiltInTiles.BATTERY), 2, height = 1, x = 4)))
            compose.activity.settingsRepository.updateSettings(TileSettings(tilesAcross = 2))
            val work = compose.activity.appsManager.homeGridFlow.first()
            assertEquals(1, work.size); assertTrue(work.single().x + work.single().width <= 2)
            compose.activity.profiles.select("Personal")
            val personal = compose.activity.appsManager.homeGridFlow.first()
            assertEquals(12, personal.size)
            personal.forEachIndexed { i, a ->
                assertTrue(a.x + a.width <= 2)
                personal.drop(i+1).forEach { b -> assertFalse(tgo1014.gridlauncher.domain.GridPlacement.overlaps(a,b)) }
            }
        }
        compose.activityRule.scenario.recreate()
        assertEquals(2, runBlocking { compose.activity.settingsRepository.tileSettingsFlow.first().tilesAcross })
    }
    @Test fun notificationActionsDeliverRemoteInputAndRejectStaleActions() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        val context = compose.activity
        var received: String? = null
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) { received = android.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply")?.toString() }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, android.content.IntentFilter("grid.test.REPLY"), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val pending = android.app.PendingIntent.getBroadcast(context, 901, Intent("grid.test.REPLY").setPackage(context.packageName), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
            val action = android.app.Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_send), "Reply", pending)
                .addRemoteInput(android.app.RemoteInput.Builder("reply").setLabel("Reply").build()).build()
            NotificationTiles.post(TileNotification("reply-test", context.packageName, "Alex", "Hello", System.currentTimeMillis(), actions = listOf(action)))
            assertTrue(NotificationTiles.act(context, "reply-test", action, "On my way"))
            compose.waitUntil(5000) { received == "On my way" }
            NotificationTiles.remove("reply-test")
            assertFalse(NotificationTiles.act(context, "reply-test", action, "Must not send"))
        } finally { context.unregisterReceiver(receiver); NotificationTiles.replace(emptyList()) }
    }
    @Test fun semanticProgressAndMessageParsingRespectSecretNotifications() {
        val context = compose.activity
        val person = android.app.Person.Builder().setName("Alex").setUri("tel:12345").build()
        val notification = android.app.Notification.Builder(context, "tests").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setStyle(android.app.Notification.MessagingStyle(person).addMessage("See you soon", System.currentTimeMillis(), person)).build()
        val parsed = notificationTile("message", context.packageName, notification, 1)!!
        assertTrue(parsed.messages.any { it.contains("See you soon") })
        assertTrue(PinnedContact("person", "Alex", phones = listOf("12345")).matches(parsed))
        notification.visibility = android.app.Notification.VISIBILITY_SECRET
        assertNull(notificationTile("secret", context.packageName, notification, 1))
        val warning = android.text.SpannableString("Road closed").apply { setSpan(android.app.Notification.createSemanticStyleAnnotation(android.app.Notification.SEMANTIC_STYLE_CAUTION), 0, length, 0) }
        val progress = android.app.Notification.Builder(context, "tests").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(warning)
            .setOngoing(true).setProgress(100, 40, false).build()
        val tile = notificationTile("progress", context.packageName, progress, 2)!!
        assertTrue(tile.isNow); assertEquals(40, tile.progress); assertEquals(3, tile.semantic)
    }
    @Test fun notificationPreviewPrivacyCanBeDisabledPerApp() {
        seed(listOf(GridItem(1, App("Settings", "com.android.settings"), 2, height = 1)))
        runBlocking { compose.activity.settingsRepository.updateSettings(TileSettings(showNotificationText = true, hiddenPreviewApps = setOf("com.android.settings"))) }
        NotificationTiles.post(TileNotification("private", "com.android.settings", "Hidden sender", "Hidden text", System.currentTimeMillis()))
        compose.onNodeWithContentDescription("Settings, 1 notifications").assertIsDisplayed()
        compose.onNodeWithText("Hidden text").assertDoesNotExist()
        compose.onNodeWithText("Hidden sender").assertDoesNotExist()
        NotificationTiles.replace(emptyList())
    }
    @Test fun nowAreaTracksRealOngoingStateAndRemovesFinishedActivity() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        runBlocking { compose.activity.settingsRepository.updateSettings(TileSettings(showNotificationText = true)) }
        NotificationTiles.post(TileNotification("ride", "com.android.settings", "Ride arriving", "4 minutes", System.currentTimeMillis(), ongoing = true, progressMax = 100, progress = 60))
        compose.onNodeWithText("Now").assertIsDisplayed()
        compose.onNodeWithText("Ride arriving").assertIsDisplayed()
        NotificationTiles.remove("ride")
        compose.onNodeWithText("Now").assertDoesNotExist()
    }
    @Test fun android17PickerAndHandoffUseOnlySelectedDataAndLayoutName() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        val intent = ContactTiles.picker()
        assertEquals(android.provider.ContactsPickerSessionContract.ACTION_PICK_CONTACTS, intent.action)
        assertEquals(2, intent.getStringArrayListExtra(android.provider.ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS)!!.size)
        val data = compose.activity.onHandoffActivityDataRequested(android.app.HandoffActivityDataRequestInfo(true))
        assertEquals(setOf("grid.profile"), data.extras!!.keySet())
        assertEquals("Personal", data.extras!!.getString("grid.profile"))
    }

}
