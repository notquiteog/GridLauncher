package tgo1014.gridlauncher

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
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
    @Test fun appIconsUseCompleteDefaultSystemDrawable() {
        val context = compose.activity
        val packageName = "com.android.settings"
        runBlocking { context.updateAppListUseCase() }
        compose.waitUntil(5000) { runBlocking { context.appsManager.installedAppsFlow.first().any { it.packageName == packageName && it.icon.iconFilePath?.contains("_default_") == true } } }
        val icon = runBlocking { context.appsManager.installedAppsFlow.first().first { it.packageName == packageName }.icon }
        val cached = android.graphics.BitmapFactory.decodeFile(icon.iconFilePath)
        val expected = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        context.packageManager.getApplicationIcon(packageName).apply {
            setBounds(0, 0, 256, 256)
            draw(android.graphics.Canvas(expected))
        }
        assertNull(icon.bgFilePath)
        assertTrue("Keep the app's complete default icon, including adaptive background", expected.sameAs(cached))
    }
    @Test fun flatTileColorsAndLabelsKeepContrastInDarkMode() {
        seed(listOf(
            GridItem(1, App("Light tile", "grid://light-test", tgo1014.gridlauncher.domain.models.Icon(edgeColor = 0xFFFFFFFFL)), 1),
            GridItem(2, App("Dark tile", "grid://dark-test", tgo1014.gridlauncher.domain.models.Icon(edgeColor = 0xFF003366L)), 1, x = 1)
        ))
        listOf("Light tile" to androidx.compose.ui.graphics.Color.White, "Dark tile" to androidx.compose.ui.graphics.Color(0xFF003366)).forEach { (name, background) ->
            val image = compose.onNodeWithContentDescription(name).captureToImage().toPixelMap()
            assertEquals("Flat color reaches the square corners", background, image[2, 2])
            assertEquals("No gradient or glass border", background, image[image.width - 3, 2])
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(name, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(if (name == "Light tile") androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White, results.single().layoutInput.style.color)
        }
    }
    @Test fun updaterRejectsInstalledVersionAndOnlySharesItsOwnCachePath() {
        val context = compose.activity
        assertThrows(IllegalArgumentException::class.java) {
            tgo1014.gridlauncher.updates.verifyApk(context.packageManager, context.packageName,
                java.io.File(context.applicationInfo.sourceDir), BuildConfig.VERSION_CODE.toLong())
        }
        val file = java.io.File(context.cacheDir, "updates/provider-test.apk").apply { parentFile!!.mkdirs(); writeText("provider check") }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            assertEquals("content", uri.scheme)
            assertEquals("provider check", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            assertThrows(IllegalArgumentException::class.java) {
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", java.io.File(context.filesDir, "private-data"))
            }
        } finally { file.delete() }
    }
    @Test fun homeIsMinimalAndAllAppsOwnsLayoutControls() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        compose.onNodeWithText("Edit layout").assertDoesNotExist()
        compose.onNodeWithText("Personal").assertDoesNotExist()
        compose.onNodeWithContentDescription("Customize Start").assertDoesNotExist()
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Edit layout").assertIsDisplayed()
        compose.onNodeWithContentDescription("Customize Start").assertIsDisplayed()
        compose.onNodeWithText("Work").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.profiles.active.first() == "Work" } }
        compose.onNodeWithText("Edit layout").assertDoesNotExist()
        compose.onNodeWithText("Work").assertDoesNotExist()
        compose.onNodeWithText("All apps").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Back to Start").performClick()
        compose.onNodeWithContentDescription("Clock").assertIsDisplayed()
    }
    @Test fun tileResizeMoveAndUnpinPersist() {
        seed(listOf(GridItem(1, App("Battery", BuiltInTiles.BATTERY), 1)))
        compose.onNodeWithText("All apps").performClick()
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
        try { compose.waitUntil(5000) { compose.onAllNodesWithText("Hello from Android").fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) {
            compose.onRoot().printToLog("TileTest")
            throw AssertionError("Preview state: notifications=${NotificationTiles.notifications.value}, settings=${runBlocking { compose.activity.settingsRepository.tileSettingsFlow.first() }}, grid=${runBlocking { compose.activity.appsManager.homeGridFlow.first() }}, locked=${compose.activity.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked}", failure)
        }
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
        } finally {
            shell("cmd notification disallow_listener $component")
            compose.waitUntil(10000) {
                @Suppress("DEPRECATION")
                compose.activity.getSystemService(android.app.ActivityManager::class.java).getRunningServices(100).none { it.service.className == LiveNotificationService::class.java.name }
            }
            compose.waitForIdle()
            NotificationTiles.replace(emptyList())
        }
    }
    @Test fun longPressShowsAppActionsAndMovementRequiresExplicitEditMode() {
        seed(listOf(GridItem(1, App("Settings", "com.android.settings"), 1)))
        compose.onNodeWithContentDescription("Settings").performTouchInput { longClick() }
        compose.onNodeWithText("App info").assertIsDisplayed()
        compose.onNodeWithText("Move tile").assertDoesNotExist()
        assertEquals(0, runBlocking { compose.activity.appsManager.homeGridFlow.first().single().x })
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("All apps").performClick()
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
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithContentDescription("Customize Start").performClick()
        compose.onNodeWithText("Make it yours").assertIsDisplayed()
        compose.onNodeWithText("Live tiles").performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("Now").assertDoesNotExist()
        compose.onNodeWithText("All apps").performClick()
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

    @Test fun realAndroidWidgetCanResizeToWholeCellRectangles() {
        seed(emptyList())
        val activity = compose.activity
        val manager = android.appwidget.AppWidgetManager.getInstance(activity)
        // Bind our own live-tile provider: it is always present and has no configure activity,
        // so binding cannot pull another app's configuration screen in front of the launcher.
        val component = android.content.ComponentName(activity, tgo1014.gridlauncher.live.GridLiveTileProvider::class.java)
        assertTrue(manager.installedProviders.any { it.provider == component })
        val id = activity.widgetHost.allocateAppWidgetId()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, component))
            automation.dropShellPermissionIdentity()
            seed(listOf(GridItem(81, App("Test widget", BuiltInTiles.WIDGET), 1, widgetId = id)))
            compose.onNodeWithText("All apps").performClick()
            compose.onNodeWithText("Edit layout").performClick()
            compose.onNodeWithContentDescription("Test widget").performClick()
            for ((w,h) in listOf(1 to 2, 2 to 1, 2 to 2, 1 to 1)) {
                compose.onNodeWithText("${w}×${h}").performClick()
                compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().single().let { it.width == w && it.height == h } } }
            }
            assertTrue(manager.getAppWidgetOptions(id).getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0)
        } finally { automation.dropShellPermissionIdentity(); activity.widgetHost.deleteAppWidgetId(id) }
    }


    @Test fun frequentRowOffersTheAppsYouActuallyOpen() {
        val context = compose.activity
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        val installed = runBlocking { context.appsManager.installedAppsFlow.first() }
        runBlocking { context.usage.clear(); repeat(5) { context.usage.record(installed[0].packageName) }; context.usage.record(installed[1].packageName) }
        compose.onNodeWithText("All apps").performClick()
        runBlocking { context.settingsRepository.updateSettings(TileSettings(drawerSort = "frequent")) }
        compose.waitForIdle()
        compose.onNodeWithText("Most used").assertIsDisplayed()
        assertEquals(installed[0].packageName, runBlocking { context.usage.frequent.first() }.first())
        // Only real opens are recorded; a hub tile is not an app launch.
        runBlocking { context.usage.record(BuiltInTiles.CLOCK) }
        assertFalse(runBlocking { context.usage.frequent.first() }.contains(BuiltInTiles.CLOCK))
    }

    @Test fun aHotseatPinsUpToFourAppsOutsideTheGrid() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        val context = compose.activity
        val installed = runBlocking { context.appsManager.installedAppsFlow.first() }
        val chosen = installed.take(4)
        runBlocking { context.settingsRepository.updateSettings(TileSettings(hotseat = chosen.map { it.packageName })) }
        compose.waitForIdle()
        try { chosen.forEach { app -> compose.onNodeWithContentDescription("Hotseat ${app.name}").assertIsDisplayed() } }
        catch (e: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("Hotseat")
            throw AssertionError("chosen=${chosen.map { it.name }} nodes=${compose.onAllNodesWithContentDescription("Hotseat", useUnmergedTree = true).fetchSemanticsNodes().size}", e)
        }
        // The hotseat is not part of the packed grid, so it survives a column change.
        runBlocking { context.settingsRepository.updateSettings(TileSettings(tilesAcross = 2, hotseat = chosen.map { it.packageName })) }
        compose.waitForIdle()
        chosen.forEach { app -> compose.onNodeWithContentDescription("Hotseat ${app.name}").assertIsDisplayed() }
        assertEquals(1, runBlocking { context.appsManager.homeGridFlow.first().size })
    }

    @Test fun customLayoutsCanBeCreatedRenamedAndDeleted() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("New").performClick()
        compose.onNodeWithText("Copy current tiles").performClick()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("Reading")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.profiles.active.first() == "Reading" } }
        assertTrue(runBlocking { compose.activity.profiles.layouts.first() }.contains("Reading"))
        // A copy keeps the current tiles.
        assertEquals(1, runBlocking { compose.activity.appsManager.homeGridFlow.first().size })
        // A custom layout's own actions live behind a long press, like the app list rows.
        // Creating a layout returns to Start, so wait for the dialog to clear and page back.
        compose.onNodeWithText("Layout name").assertDoesNotExist()
        compose.waitForIdle()
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Edit layout").assertIsDisplayed()
        compose.onNodeWithText("Reading").assertIsDisplayed()
        compose.onNodeWithText("Reading").performTouchInput { longClick() }
        compose.onNodeWithText("Rename Reading").performClick()
        // The rename field starts on the current name, so replace rather than append.
        compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("Evening")
        compose.onNodeWithText("Rename").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.profiles.layouts.first() }.contains("Evening") }
        // Renaming keeps the grid attached to the layout.
        assertEquals(1, runBlocking { compose.activity.appsManager.homeGridFlow.first().size })
        compose.onNodeWithText("Evening").assertIsDisplayed()
        compose.onNodeWithText("Evening").performTouchInput { longClick() }
        compose.onNodeWithText("Delete Evening").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.profiles.layouts.first() }.none { it == "Evening" } }
        // Built-in layouts are never deletable.
        assertTrue(runBlocking { compose.activity.profiles.layouts.first() }.containsAll(listOf("Personal", "Work", "Travel")))
    }

    @Test fun aTileDragsToANewCellAndPinnedTilesStayPut() {
        seed(listOf(
            GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1, x = 0),
            GridItem(2, App("Battery", BuiltInTiles.BATTERY), 1, x = 1)
        ))
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Edit layout").performClick()
        compose.onNodeWithContentDescription("Clock").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(120f, 0f)); moveBy(androidx.compose.ui.geometry.Offset(160f, 0f)); moveBy(androidx.compose.ui.geometry.Offset(120f, 0f)); up()
        }
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().first { it.app.name == "Clock" }.x == 1 } }
        // A pinned tile refuses to move.
        runBlocking { compose.activity.appsManager.setGrid(listOf(
            GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1, x = 0, positionPinned = true),
            GridItem(2, App("Battery", BuiltInTiles.BATTERY), 1, x = 1))) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Clock").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(120f, 0f)); moveBy(androidx.compose.ui.geometry.Offset(160f, 0f)); moveBy(androidx.compose.ui.geometry.Offset(120f, 0f)); up()
        }
        compose.waitForIdle()
        assertEquals(0, runBlocking { compose.activity.appsManager.homeGridFlow.first().first { it.app.name == "Clock" }.x })
    }

    @Test fun groupHeadersAndTileColoursSurviveARestart() {
        val settings = TileSettings()
        runBlocking { compose.activity.settingsRepository.updateSettings(settings.copy(accentColor = 0xFF0078D7)) }
        seed(listOf(
            GridItem(1, App("Work", BuiltInTiles.GROUP), 3, 1, groupLabel = "Work"),
            GridItem(2, App("Clock", BuiltInTiles.CLOCK), 1, x = 0, y = 1),
            GridItem(3, App("Battery", BuiltInTiles.BATTERY), 1, x = 1, y = 1)
        ))
        compose.onNodeWithText("Work").assertIsDisplayed()
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Edit layout").performClick()
        compose.onNodeWithContentDescription("Clock").performClick()
        compose.onNodeWithText("Edit tile").assertIsDisplayed()
        // Pick a per-tile colour rather than the icon's own edge colour.
        compose.onNodeWithContentDescription("Tile color B4009E").performClick()
        compose.waitUntil(5000) { runBlocking { compose.activity.appsManager.homeGridFlow.first().any { it.tileColor != null } } }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        val restored = runBlocking { compose.activity.appsManager.homeGridFlow.first() }
        assertTrue(restored.any { it.tileColor != null })
        assertTrue(restored.any { it.isGroup && it.groupLabel == "Work" })
    }

    @Test fun quietHoursHideCountsAndTheNowBoard() {
        seed(listOf(GridItem(1, App("Settings", "com.android.settings"), 2, height = 1)))
        NotificationTiles.post(TileNotification("quiet", "com.android.settings", "Hidden while quiet", "body", System.currentTimeMillis(), ongoing = true, progressMax = 10, progress = 5))
        compose.waitForIdle()
        compose.onNodeWithText("Hidden while quiet").assertIsDisplayed()
        runBlocking { compose.activity.settingsRepository.updateSettings(TileSettings(meetingMode = true)) }
        compose.waitForIdle()
        compose.onNodeWithText("Hidden while quiet").assertDoesNotExist()
        compose.onNodeWithText("Quiet").assertIsDisplayed()
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Now").assertDoesNotExist()
        NotificationTiles.replace(emptyList())
    }

    @Test fun themePacksAndGroupHeaderCreationRoundTripThroughSettings() {
        seed(listOf(GridItem(1, App("Clock", BuiltInTiles.CLOCK), 1)))
        val original = runBlocking { compose.activity.settingsRepository.tileSettingsFlow.first() }
        val code = tgo1014.gridlauncher.live.ThemePacks.encode(original.copy(accentColor = 0xFFB4009E, glassFinish = "acrylic"))
        val decoded = tgo1014.gridlauncher.live.ThemePacks.decode(code)!!
        runBlocking { compose.activity.settingsRepository.updateSettings(decoded) }
        val applied = runBlocking { compose.activity.settingsRepository.tileSettingsFlow.first() }
        assertEquals(0xFFB4009EL, applied.accentColor)
        assertEquals("acrylic", applied.glassFinish)
        // A bad paste changes nothing.
        assertNull(tgo1014.gridlauncher.live.ThemePacks.decode("not a pack"))
    }

}
