package tgo1014.gridlauncher.screenshots

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.live.Clock
import tgo1014.gridlauncher.live.NotificationTiles
import tgo1014.gridlauncher.ui.MainActivity
import tgo1014.gridlauncher.ui.models.GridItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking

/**
 * Screenshot comparison for the launcher, on a real emulator, in CI.
 *
 * This exists because of a specific and repeated failure of this project. Every visual regression
 * that ever shipped here - light-theme text at 1.19:1 over the wallpaper, Start's tile pane painting
 * across the drawer, live tiles rendering blank, the alphabet rail on the wrong page - passed a fully
 * green build, because nothing was looking at the pixels. A test can assert that a node exists; only
 * an image says what the user sees.
 *
 * ## What makes an image comparable
 *
 * An emulator is not a renderer, it is a rendering *of* something, and any of these will produce a
 * different image tomorrow for reasons that have nothing to do with the launcher:
 *
 *  * the clock and the dates drawn on screen, which change every day;
 *  * the system wallpaper, which changes with the system image;
 *  * 12- versus 24-hour formatting, which is a device setting;
 *  * whatever the previous test left in the launcher's storage;
 *  * an app list that depends on which apps the image happens to ship.
 *
 * So a scene pins the clock, installs a committed wallpaper fixture instead of the system one,
 * forces the clock format, resets every store the launcher reads, and waits for the app list before
 * capturing. What is left is the launcher, which is the thing worth comparing.
 *
 * ## Modes
 *
 * `compare` (default) diffs against `src/androidTest/assets/screenshots/<name>.png` and fails with a
 * written actual and a magenta diff on the device. `update` writes the current rendering to the same
 * folder for the caller to promote; `scripts/screenshot-baselines.sh` does that and pulls it back.
 *
 * A missing baseline is a *failure*, never a silent record. A baseline that writes itself is a
 * baseline nobody reviewed, and the whole value here is that a human looked at the change.
 */
object Screenshots {

    /**
     * A fixed Wednesday afternoon. Any instant works; a weekday afternoon means the date in the
     * header and the date above the app list are ordinary values rather than edge cases.
     */
    val PINNED_INSTANT: Long = Instant.parse("2026-01-14T09:41:00Z").toEpochMilli()

    /** The clock tile's rendering of [PINNED_INSTANT], and the marker that live content has landed. */
    const val PINNED_TEXT = "09:41"

    private const val ASSET_DIR = "screenshots"

    /**
     * Where captures, actuals and diffs are left for `adb` to collect.
     *
     * The launcher's own external directory is the obvious place and the wrong one: AGP uninstalls
     * the app under test when the run ends, which deletes it along with everything in it. So each
     * file is copied out to a shell-owned directory as it is written - a short `cp`, no need to feed
     * a megabyte of pixels down a pipe.
     */
    private const val PUBLISHED_DIR = "/data/local/tmp/grid-screenshots"

    fun outputDir(): File =
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), ASSET_DIR)
            .apply { mkdirs() }

    /** Copies one written file somewhere that survives the uninstall. */
    private fun publish(file: File) {
        // One command, no quoting, no chaining: UiAutomation stops at the first `;`, so anything
        // chained after the copy is silently never run. The directory is created by the caller.
        val out = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp ${file.absolutePath} $PUBLISHED_DIR/")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
    }

    private fun write(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        publish(target)
    }

    private fun updating(): Boolean =
        InstrumentationRegistry.getArguments().getString("grid.screenshots") == "update"

    private fun target() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun assets() = InstrumentationRegistry.getInstrumentation().context.assets

    /** Copies a committed fixture into the launcher's own storage, where `wallpaperPath` can reach it. */
    fun installWallpaper(fixture: String): String {
        val file = File(target().filesDir, "screenshot-wallpaper.png")
        assets().open("$ASSET_DIR/$fixture.png").use { it.copyTo(file.outputStream()) }
        return file.absolutePath
    }

    /**
     * Puts the device into a state where two runs an hour apart render the same thing.
     *
     * Everything the launcher reads is reset here rather than in each scene, so a new scene cannot
     * accidentally inherit state from whatever ran before it.
     */
    fun settle(compose: AndroidComposeTestRule<*, out ComponentActivity>, wallpaper: String, settings: TileSettings = TileSettings()) {
        val activity = compose.activity as MainActivity
        Clock.pinned = Clock.Fixed(PINNED_INSTANT, "UTC")
        NotificationTiles.replace(emptyList())
        runBlocking {
            activity.profiles.select("Personal")
            activity.settingsRepository.updateSettings(
                settings.copy(wallpaperPath = installWallpaper(wallpaper))
            )
            activity.usage.clear()
        }
        compose.waitForIdle()
    }

    /**
     * A grid built only from things that render the same way every time: no battery, no storage, no
     * steps, no clock of the real device's making. A 2x1 tile is here on purpose - the live-tile flip
     * has to have something to be a flip of, and that is the tile that once rendered blank.
     *
     * Every position fits inside three columns. A grid that overflows its own column count still
     * "renders", it just renders off the edge, and a baseline is the wrong place to discover that.
     *
     * The per-tile colours are not decoration. Left at the accent, every hub tile and every group
     * header is the same blue, the grid fuses into one field, and a tile that quietly changed colour
     * - or vanished into the header above it - is invisible in the diff. Distinct colours mean one
     * changed tile is one obviously wrong block.
     *
     * It is also deliberately taller than the screen. A grid that fits has nothing to scroll, so the
     * scrolled scene would capture exactly the same picture as the unscrolled one and quietly protect
     * nothing - which is how a regression test becomes a test that passes.
     */
    fun stableGrid(settings: App): List<GridItem> {
        fun clock(id: Int, x: Int, y: Int, w: Int = 1, colour: Long = 0xFFB4009E) =
            GridItem(id, App("Clock", BuiltInTiles.CLOCK), w, 1, x = x, y = y, tileColor = colour)
        return listOf(
            GridItem(1, App("Work", BuiltInTiles.GROUP), 3, 1, groupLabel = "Work"),
            clock(2, 0, 1),
            GridItem(3, settings, 1, 1, x = 1, y = 1, tileColor = 0xFF107C10),
            clock(4, 2, 1, 2).copy(tileColor = 0xFF8764B8),
            GridItem(5, App("Greeting", BuiltInTiles.GREETING), 2, 1, x = 0, y = 2, tileColor = 0xFF0063B1),
            GridItem(6, App("Evening", BuiltInTiles.GROUP), 3, 1, y = 3, groupLabel = "Evening"),
            clock(7, 0, 4),
            clock(8, 1, 4),
            clock(9, 2, 4),
            GridItem(10, App("Greeting", BuiltInTiles.GREETING), 2, 1, x = 0, y = 5, tileColor = 0xFF0063B1),
            clock(11, 0, 6),
            GridItem(12, App("Weekend", BuiltInTiles.GROUP), 3, 1, y = 7, groupLabel = "Weekend"),
            clock(13, 0, 8),
            GridItem(14, settings, 1, 1, x = 1, y = 8, tileColor = 0xFF107C10),
            clock(15, 2, 8),
        )
    }


    /**
     * Seeds the grid and waits for the launcher to stop changing.
     *
     * Waiting matters more than it looks: app icons arrive asynchronously from a disk cache, and a
     * capture taken before they land differs from the same capture taken a second later - which is
     * indistinguishable, from the diff, from a regression that un-drew the icons.
     */
    fun seed(compose: AndroidComposeTestRule<*, out ComponentActivity>, tiles: List<GridItem>, settings: TileSettings = TileSettings(), wallpaper: String = "wallpaper-dark") {
        val activity = compose.activity as MainActivity
        settle(compose, wallpaper, settings)
        runBlocking {
            activity.updateAppListUseCase()
            activity.appsManager.setGrid(tiles)
        }
        compose.waitUntil(15000) { runBlocking { activity.appsManager.installedAppsFlow.first().isNotEmpty() } }
        compose.waitForIdle()
        // Icons are decoded off the Compose clock, so idleness alone can arrive before the pixels
        // have landed. A real pause is the honest way to wait for I/O that is not a recomposition.
        Thread.sleep(600)
        compose.waitForIdle()
    }

    /**
     * Waits until the live tiles have finished loading their content.
     *
     * A hub tile reads its content asynchronously, and until it lands the tile draws only its hub
     * mark. That is correct behaviour and a useless baseline: the scene photographs whichever tiles
     * happen to have loaded, so one run records a grid of clocks and the next records a grid of marks,
     * and the difference looks exactly like a regression.
     *
     * Waiting for a *count* would be worse than waiting for stability. The tile grid is virtualized,
     * so only what is on screen ever loads: five of this scene's eight clock tiles, and a different
     * five once it is scrolled. A scene that demanded all eight would simply never pass, and one that
     * demanded the visible ones would be asserting the scroll position rather than the picture. So
     * this waits for the number of loaded tiles to stop moving, which is the actual precondition for
     * the frame to be meaningful, and lets [stableCapture] settle whatever is still drawing.
     */
    fun awaitLiveContent(
        compose: AndroidComposeTestRule<*, out ComponentActivity>,
        text: String = PINNED_TEXT,
        timeoutMillis: Long = 20_000,
    ) {
        var previous = -1
        var waited = 0L
        while (waited < timeoutMillis) {
            val loaded = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size
            if (loaded > 0 && loaded == previous) return
            previous = loaded
            Thread.sleep(250)
            waited += 250
        }
        throw AssertionError("no live tile content appeared within ${timeoutMillis}ms of seeding")
    }

    /** [seed] with the launcher's own idea of the Settings app, so its icon really renders. */
    fun seedRealIcons(
        compose: AndroidComposeTestRule<*, out ComponentActivity>,
        settings: TileSettings = TileSettings(),
        wallpaper: String = "wallpaper-dark",
    ) {
        val activity = compose.activity as MainActivity
        settle(compose, wallpaper, settings)
        runBlocking { activity.updateAppListUseCase() }
        val settingsApp = runBlocking {
            withTimeoutOrNull(20000) {
                activity.appsManager.installedAppsFlow
                    .first { apps -> apps.any { it.packageName == "com.android.settings" } }
                    .first { it.packageName == "com.android.settings" }
            }
        } ?: run {
            val seen = runBlocking { activity.appsManager.installedAppsFlow.first() }.map { it.packageName }
            error("com.android.settings never reached the installed list; saw ${seen.size}: ${seen.take(12)}")
        }
        seed(compose, stableGrid(settingsApp), settings, wallpaper)
        awaitLiveContent(compose)
    }

    /** A frame of the launcher's own window, as comparable pixels. */
    class Capture(val width: Int, val height: Int, val pixels: IntArray)


    /**
     * Captures the launcher's own window.
     *
     * The window, not the display: `onRoot()` is the app's surface, so the status bar's own clock and
     * battery icons are not part of the comparison. They are the platform's, they are not ours, and
     * they would make every baseline stale within the hour.
     */
    fun capture(compose: AndroidComposeTestRule<*, out ComponentActivity>): Capture {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Capture(bitmap.width, bitmap.height, pixels)
    }

    /**
     * Captures the window only once it has stopped changing.
     *
     * A frame is only worth comparing if it is the same frame twice. The live-tile flip is the reason
     * this exists: a tile whose content just changed is still a frame or two into its rotation after
     * the composition goes idle, so two honest captures of one screen differ by a few degrees of
     * perspective on that tile's text - a diff of thousands of pixels that is not a regression and
     * would still fail a build. Waiting for two identical captures in a row settles whatever is still
     * moving - a flip, an icon decode, a fade - without the harness needing to know which it was.
     *
     * If it never settles, the last capture is returned and the comparison fails loudly rather than
     * quietly grading a moving picture.
     */
    fun stableCapture(compose: AndroidComposeTestRule<*, out ComponentActivity>, attempts: Int = 8): Capture {
        var previous = capture(compose)
        repeat(attempts) {
            Thread.sleep(300)
            compose.waitForIdle()
            val next = capture(compose)
            if (PixelDiff.matches(previous.pixels, next.pixels, previous.width, previous.height, 0)) return next
            previous = next
        }
        return previous
    }

    /** Compares the current window against its baseline, or records it when asked to. */
    fun verify(compose: AndroidComposeTestRule<*, out ComponentActivity>, name: String) {
        val shot = stableCapture(compose)
        val width = shot.width
        val height = shot.height
        val actual = shot.pixels

        if (updating()) {
            write(File(outputDir(), "$name.png"), png(actual, width, height))
            return
        }

        val expected = runCatching { assets().open("$ASSET_DIR/$name.png").use { it.readBytes() } }.getOrNull()
        if (expected == null) {
            write(File(outputDir(), "$name.actual.png"), png(actual, width, height))
            fail(
                "No baseline for '$name'. There is deliberately no silent record here: a baseline " +
                    "nobody reviewed is not evidence of anything. Run scripts/screenshot-baselines.sh " +
                    "to capture it on a matching emulator, then look at the image before committing it."
            )
            return
        }
        val expectedPixels = BitmapFactory.decodeByteArray(expected, 0, expected.size)
        if (expectedPixels.width != width || expectedPixels.height != height) {
            write(File(outputDir(), "$name.actual.png"), png(actual, width, height))
            fail(
                "'$name' is ${width}x$height but its baseline is ${expectedPixels.width}x${expectedPixels.height}. " +
                    "That is a layout change, not noise. Refresh the baseline after confirming it is intended."
            )
        }
        val expectedBuffer = IntArray(width * height)
        expectedPixels.getPixels(expectedBuffer, 0, width, 0, 0, width, height)

        val result = PixelDiff.compare(expectedBuffer, actual, width, height)
        if (!result.isAcceptable()) {
            write(File(outputDir(), "$name.actual.png"), png(actual, width, height))
            write(
                File(outputDir(), "$name.diff.png"),
                png(PixelDiff.diffImage(expectedBuffer, actual, width, height), width, height),
            )
            fail(
                "'$name' changed: ${result.describe()}. " +
                    "Changed pixels are magenta in $name.diff.png, and the untouched capture is $name.actual.png. " +
                    "Both are pulled into build/device-evidence/screenshots/. " +
                    "If the change is intended, refresh the baselines; if not, this is the regression " +
                    "that the previous green builds did not catch."
            )
        }
    }

    /**
     * Measures the worst contrast inside a piece of text, straight out of the rendered pixels.
     *
     * A screenshot says whether a scene changed; this says whether a scene is *legible*, which is
     * the one visual bug here that a screenshot cannot judge - a scrim drawn at exactly the wrong
     * alpha is pixel-perfectly stable and completely unreadable.
     *
     * The extremes of the text's own rectangle are used rather than a colour the theme declares,
     * because the composited result over a wallpaper is the only thing the user ever sees. Those
     * extremes include antialiased edges, which is what makes this a floor rather than an average.
     */
    fun worstTextContrast(compose: AndroidComposeTestRule<*, out ComponentActivity>, label: String): Pair<Double, String> {
        val bounds = compose.onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val left = bounds.left.toInt().coerceIn(0, maxOf(0, bitmap.width - 1))
        val top = bounds.top.toInt().coerceIn(0, maxOf(0, bitmap.height - 1))
        val right = bounds.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = bounds.bottom.toInt().coerceIn(top + 1, bitmap.height)
        var lightest = 0
        var darkest = 0
        for (y in top until bottom) for (x in left until right) {
            val pixel = pixels[y * bitmap.width + x]
            if (luminance(pixel) > luminance(lightest)) lightest = pixel
            if (darkest == 0 || luminance(pixel) < luminance(darkest)) darkest = pixel
        }
        return PixelDiff.contrastRatio(lightest, darkest) to
            "lightest ${hex(lightest)} over darkest ${hex(darkest)}"
    }

    private fun luminance(packed: Int): Double =
        0.2126 * relative((packed shr 16) and 0xFF) +
            0.7152 * relative((packed shr 8) and 0xFF) +
            0.0722 * relative(packed and 0xFF)

    private fun relative(raw: Int): Double {
        val value = raw / 255.0
        return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }

    private fun hex(packed: Int) = "#%06X".format(0xFFFFFF and packed)

    private fun png(pixels: IntArray, width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}