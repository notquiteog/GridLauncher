package tgo1014.gridlauncher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.MainActivity

/** Runs only on the disposable CI emulator: creates and removes its own Android private profile. */
@RunWith(AndroidJUnit4::class)
class ProfileIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    @Test fun privateAppsDisappearOnLockAndStayOutOfPersonalSearch() {
        val context = compose.activity
        assertTrue("Run profile provisioning tests on an emulator", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        shell("cmd package set-home-activity ${context.packageName}/tgo1014.gridlauncher.ui.MainActivity")
        val result = shell("pm create-user --profileOf 0 --user-type android.os.usertype.profile.PRIVATE GridLauncherIsolationTest")
        val id = Regex("created user id (\\d+)").find(result)?.groupValues?.get(1)
            ?: error("Could not create test profile: $result")
        try {
            assertTrue(shell("cmd package install-existing --user $id com.android.settings").contains("installed"))
            shell("am start-user -w $id")
            runBlocking { context.settingsRepository.updateSettings(TileSettings()) }
            compose.waitForIdle()
            compose.onNodeWithText("All apps").performClick()
            compose.onNodeWithContentDescription("Android profiles").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Lock", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Search unlocked profiles").performTextInput("Settings")
            compose.waitUntil(5000) { compose.onAllNodesWithText("Info").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Lock").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Locked · apps are hidden").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Info").assertDoesNotExist()
            compose.onNodeWithText("Unlock").assertIsDisplayed()
            compose.onNode(isToggleable()).performClick()
            compose.onNodeWithText("Private space").assertDoesNotExist()
            compose.onNode(isToggleable()).performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Unlock").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Search apps").performTextInput("sett")
            // There is one personal Settings entry; the private copy must not join ordinary search.
            compose.onAllNodesWithText("Settings").assertCountEquals(1)
        } finally {
            assertTrue(shell("pm remove-user $id").contains("Success"))
        }
    }
}
