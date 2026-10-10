package tgo1014.gridlauncher.ui

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.home.orderApps
import tgo1014.gridlauncher.ui.home.profileContentAllowed
import tgo1014.gridlauncher.ui.home.suggestedApps

class LauncherPolishTest {
    @Test fun suggestionsExcludeOnlyRequestedAppsAndChooseTopRankedBeforeAlphabetizing() {
        val apps = (0..11).map { App(('Z' - it).toString(), "app.$it") }
        val rank = apps.map { it.packageName } + "app.0" + "uninstalled"
        val excluded = setOf("app.1", "app.3")
        val selected = suggestedApps(apps, rank, excluded, "alphabetical")
        assertEquals(listOf(9, 8, 7, 6, 5, 4, 2, 0).map { "app.$it" }, selected.map { it.packageName })
        assertEquals(listOf(0, 2, 4, 5, 6, 7, 8, 9).map { "app.$it" },
            suggestedApps(apps, rank, excluded, "recent").map { it.packageName })
        assertEquals(12, orderApps(apps, "frequent", rank).size)
    }
    @Test fun frequentAppsPrecedeUnseenAppsAndKeepRank() {
        val apps = listOf(App("Alpha", "a"), App("Zulu", "z"), App("Beta", "b"), App("Gamma", "g"))
        assertEquals(listOf("z", "b", "a", "g"), orderApps(apps, "frequent", listOf("z", "b")).map { it.packageName })
        assertEquals(listOf("a", "b", "g", "z"), orderApps(apps, "alphabetical", listOf("z")).map { it.packageName })
    }
    @Test fun profileRequiresAllThreePrivacyConditions() {
        for (quiet in listOf(false, true)) for (unlocked in listOf(false, true)) for (deviceLocked in listOf(false, true)) {
            assertEquals(!quiet && unlocked && !deviceLocked, profileContentAllowed(quiet, unlocked, deviceLocked))
        }
    }
}
