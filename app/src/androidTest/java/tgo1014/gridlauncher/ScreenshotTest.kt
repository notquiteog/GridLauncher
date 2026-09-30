package tgo1014.gridlauncher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tgo1014.gridlauncher.screenshots.PixelDiff
import tgo1014.gridlauncher.screenshots.Screenshots
import tgo1014.gridlauncher.ui.MainActivity

/**
 * The compact-posture scenes, each of which exists because that exact thing broke before.
 *
 * The AVD is created by `scripts/screenshot-baselines.sh` and by CI with the same device profile,
 * so these baselines are comparable between a contributor's machine and the runner. A different
 * profile is a different picture, and the harness fails loudly rather than quietly accepting it.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /**
     * Start itself. Covers the dated header, a group header, a 1x1 hub, a real app icon and a wide
     * tile. The wide tile is here for the live-tile flip: the regression painted its front face at
     * zero alpha, and the tile went blank in the one place nobody looks closely.
     */
    @Test fun startInDarkTheme() {
        Screenshots.seedRealIcons(compose)
        Screenshots.verify(compose, "start-dark")
    }

    /** The same screen in the light theme, over a light wallpaper. */
    @Test fun startInLightTheme() {
        Screenshots.seedRealIcons(compose, tgo1014.gridlauncher.domain.models.TileSettings(darkTheme = false), "wallpaper-light")
        Screenshots.verify(compose, "start-light")
    }

    /**
     * There is deliberately no "Start, scrolled" scene here.
     *
     * Scrolling is the one thing in this launcher that cannot be photographed reproducibly: the pane
     * is driven by a parallax drag rather than a scroll container, so it exposes no scroll semantics,
     * and a gesture's *applied* distance depends on when the pane finished laying out. Across boots
     * the same gesture lands a few hundred pixels apart, which is a diff of several thousand pixels
     * and a failure that means nothing.
     *
     * A scene that fails for a reason unrelated to the code is worse than a missing scene: the only
     * way to make it green is to re-record, and a baseline people re-record without reading is a
     * baseline that protects nothing. The two-pane layout, which is where an unclipped pane does the
     * most damage, is covered by the expanded scenes instead.
     */

    /**
     * All apps in the dark theme: search, the frequent row, the alphabet rail and the list.
     *
     * The rail is in this scene and pointedly not in `start-dark`. Windows Phone kept it on the app
     * list and nowhere else, and moving it onto Start was one of the four regressions.
     */
    @Test fun allAppsInDarkTheme() {
        Screenshots.seedRealIcons(compose)
        compose.onNodeWithText("All apps").performClick()
        compose.waitForIdle()
        Thread.sleep(600)
        Screenshots.verify(compose, "drawer-dark")
    }

    /**
     * All apps in the light theme over a light wallpaper: the 1.19:1 scene.
     *
     * A single black scrim is right for white ink and unreadable for dark ink. This is the whole
     * reason the scrim follows the ink's polarity, and it is the one image where a regression is
     * invisible to the eye in a diff but obvious to anyone using the launcher.
     */
    @Test fun allAppsInLightThemeOverLightWallpaper() {
        Screenshots.seedRealIcons(compose, tgo1014.gridlauncher.domain.models.TileSettings(darkTheme = false), "wallpaper-light")
        compose.onNodeWithText("All apps").performClick()
        compose.waitForIdle()
        Thread.sleep(600)
        Screenshots.verify(compose, "drawer-light")
    }

    /** Editing a layout: the bar, the per-tile handles and the dimming Start puts behind it. */
    @Test fun editLayout() {
        Screenshots.seedRealIcons(compose)
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Edit layout").performClick()
        compose.waitForIdle()
        Thread.sleep(400)
        Screenshots.verify(compose, "drawer-editing")
    }

    /**
     * The numbers behind `drawer-light`.
     *
     * A screenshot proves the light drawer has not changed. It cannot tell you whether it is
     * *readable*, because an unreadable drawer is perfectly stable from run to run. So the contrast
     * is measured off the rendered pixels, and 4.5:1 is the WCAG AA floor for body text - not a
     * number invented to make this pass.
     */
    @Test fun lightDrawerTextIsActuallyReadableOverWallpaper() {
        Screenshots.seedRealIcons(compose, tgo1014.gridlauncher.domain.models.TileSettings(darkTheme = false), "wallpaper-light")
        compose.onNodeWithText("All apps").performClick()
        compose.waitForIdle()
        Thread.sleep(600)
        for (label in listOf("Search apps", "Edit layout")) {
            val (ratio, detail) = Screenshots.worstTextContrast(compose, label)
            assertTrue(
                "'$label' measured $ratio:1 over its own background ($detail), below the 4.5:1 floor. " +
                    "The scrim is not carrying the ink on this wallpaper.",
                ratio >= 4.5,
            )
        }
    }
}