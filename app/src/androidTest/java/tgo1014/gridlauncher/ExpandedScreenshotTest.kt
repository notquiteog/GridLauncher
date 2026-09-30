package tgo1014.gridlauncher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tgo1014.gridlauncher.screenshots.Screenshots
import tgo1014.gridlauncher.ui.MainActivity

/**
 * The expanded scenes: 840dp and wider, Start beside All apps.
 *
 * CI reaches this posture the cheap way - `wm size` and `wm density` on the same phone AVD, which is
 * what a developer does to check a layout and costs no second emulator boot. The assumption below is
 * what makes the class safe in an ordinary run: on a 411dp phone it does nothing rather than
 * capturing a phone screen under a foldable's filename.
 */
@RunWith(AndroidJUnit4::class)
class ExpandedScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun widthDp(): Int {
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        return (metrics.widthPixels / metrics.density).toInt()
    }

    @Test fun expandedPosturePutsStartBesideAllApps() {
        assumeTrue("needs an 840dp-or-wider window", widthDp() >= 840)
        Screenshots.seedRealIcons(compose)
        Screenshots.verify(compose, "expanded-start")
    }

    @Test fun expandedPostureKeepsBothPanesLegible() {
        assumeTrue("needs an 840dp-or-wider window", widthDp() >= 840)
        Screenshots.seedRealIcons(
            compose,
            tgo1014.gridlauncher.domain.models.TileSettings(darkTheme = false),
            "wallpaper-light",
        )
        Screenshots.verify(compose, "expanded-start-light")
    }
}