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
        runBlocking {
            compose.activity.settingsRepository.updateSettings(TileSettings())
            compose.activity.appsManager.setGrid(tiles)
        }
        compose.waitForIdle()
    }
    @Test fun homeIntentAndAppDrawerWork() {
        val context = compose.activity
        val homes = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        assertTrue(homes.any { it.activityInfo.packageName == context.packageName })
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 2)))
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Search apps").performTextInput("zzzz-no-such-app")
        compose.onNodeWithText("No apps found").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Start ←").performClick()
        compose.onNodeWithContentDescription("Clock").assertIsDisplayed()
    }
    @Test fun tileResizeMoveAndUnpinPersist() {
        seed(listOf(GridItem(1, App("Battery", BuiltInTiles.BATTERY), 2)))
        compose.onNodeWithContentDescription("Battery").performTouchInput { longClick() }
        compose.onNodeWithText("Large").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().first().width == 4 } }
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().isEmpty() } }
    }
    @Test fun builtInTilesSurviveActivityRecreationAndPackageRefresh() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 2)))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Clock").assertIsDisplayed()
        runBlocking { compose.activity.updateAppListUseCase() }
        compose.waitForIdle()
        assertEquals(BuiltInTiles.CLOCK, runBlocking { compose.activity.appsManager.homeGridFlow.first().single().app.packageName })
    }
    @Test fun notificationsUpdateBadgeAndPreviewThenClear() {
        seed(listOf(GridItem(1, App("Settings", "com.android.settings"), 4, height = 2)))
        runBlocking { compose.activity.settingsRepository.updateSettings(TileSettings(showNotificationText = true)) }
        NotificationTiles.post(TileNotification("message", "com.android.settings", "Hello from Android", "A real tile update", 1))
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hello from Android").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("A real tile update").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings, 1 notifications").assertIsDisplayed()
        NotificationTiles.remove("message")
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("Hello from Android").assertDoesNotExist()
    }
    @Test fun notificationListenerReceivesAndroidPostedNotification() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 2)))
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
    @Test fun holdingAndDraggingMovesTileWithoutOpeningApp() {
        seed(listOf(GridItem(1, App("Battery", BuiltInTiles.BATTERY), 2)))
        compose.onNodeWithContentDescription("Battery").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(380f, 0f), delayMillis = 200)
            up()
        }
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().single().x > 0 } }
    }
    @Test fun settingsOpenAndFolderContentsDisplay() {
        seed(listOf(GridItem(1, App("Favorites", BuiltInTiles.FOLDER), 2, children = listOf(App("Settings", "com.android.settings")))))
        compose.onNodeWithContentDescription("Favorites").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Customize Start").performClick()
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        compose.onNodeWithText("Live tiles").assertIsDisplayed()
    }
}
