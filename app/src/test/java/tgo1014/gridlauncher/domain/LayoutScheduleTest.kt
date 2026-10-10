package tgo1014.gridlauncher.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.data.AppsManagerDataSourceImpl
import tgo1014.gridlauncher.data.LayoutProfiles
import tgo1014.gridlauncher.data.dropLayoutRefs
import tgo1014.gridlauncher.data.orderLayouts
import tgo1014.gridlauncher.data.renameLayoutRefs
import tgo1014.gridlauncher.data.resolveScheduledLayout
import tgo1014.gridlauncher.data.scheduledProfile
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.live.BuiltInTiles
import tgo1014.gridlauncher.ui.models.GridItem
import java.io.File
import java.time.LocalDateTime

/**
 * The two things that point at layouts by name - the weekday schedule and the layout bar - now
 * that any layout, including the three the app ships with, can be renamed or deleted underneath
 * them. Pure rules on the JVM, plus the real store for what a rename and a delete do to a layout.
 */
class LayoutScheduleTest {

    /** The launcher and its layout store over one preferences file, the way the app wires them. */
    private fun store(name: String): Pair<AppsManagerDataSourceImpl, LayoutProfiles> {
        val preferences: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
            File.createTempFile(name, ".preferences_pb").absoluteFile.toPath().toOkioPath()
        }
        return AppsManagerDataSourceImpl(Json, preferences) to LayoutProfiles(preferences, Json)
    }
    private fun at(text: String) = LocalDateTime.parse(text)
    private val clock = App("Clock", BuiltInTiles.CLOCK)

    /** What a refused write says, so the message the user sees is part of what is being checked. */
    private suspend fun refusal(block: suspend () -> Unit): String? =
        try { block(); null } catch (refused: IllegalArgumentException) { refused.message }

    @Test fun aDeletedTargetResolvesToTheFallbackInsteadOfThrowing() {
        val layouts = listOf("Personal", "Work", "Travel")
        assertEquals("Work", resolveScheduledLayout("Work", layouts))
        // The named layout is gone, so the schedule lands on the default rather than on nothing.
        assertEquals("Personal", resolveScheduledLayout("Work", listOf("Personal", "Travel")))
        // Personal deleted too: still a real layout, just not the one that was asked for.
        assertEquals("Travel", resolveScheduledLayout("Work", listOf("Travel")))
        assertEquals("Travel", resolveScheduledLayout("Personal", listOf("Travel")))
        assertEquals("Personal", resolveScheduledLayout("Work", emptyList()))
    }

    @Test fun aRenamedBuiltInKeepsTheScheduleWorking() {
        val mondayMorning = at("2026-09-25T09:00")
        // The schedule asks for a name, and the name is the user's to change.
        assertEquals("Office", scheduledProfile(mondayMorning, 9, 17, workLayout = "Office"))
        assertEquals("Home", scheduledProfile(at("2026-09-25T20:00"), 9, 17, workLayout = "Office", personalLayout = "Home"))
        val renamed = renameLayoutRefs(TileSettings(workLayout = "Work"), "Work", "Office")
        assertEquals("Office", renamed.workLayout)
        assertEquals("Personal", renamed.personalLayout)
        assertEquals("Office", resolveScheduledLayout(scheduledProfile(mondayMorning, 9, 17, renamed.workLayout, renamed.personalLayout),
            listOf("Personal", "Office", "Travel")))
    }

    @Test fun aDeletedTargetLeavesTheSchedulePointedAtSomethingReal() {
        val dropped = dropLayoutRefs(TileSettings(workLayout = "Work"), "Work", "Personal")
        assertEquals("Personal", dropped.workLayout)
        assertEquals("Personal", dropped.personalLayout)
        assertEquals("Personal", resolveScheduledLayout(scheduledProfile(at("2026-09-25T09:00"), 9, 17, dropped.workLayout, dropped.personalLayout),
            listOf("Personal", "Travel")))
    }

    @Test fun theBarOrderRoundTripsAndNeverLosesALayout() {
        val layouts = listOf("Personal", "Work", "Travel", "Reading")
        assertEquals(layouts, orderLayouts(layouts, emptyList()))
        assertEquals(listOf("Reading", "Travel", "Work", "Personal"), orderLayouts(layouts, listOf("Reading", "Travel", "Work", "Personal")))
        // A layout created since the order was saved joins at the end, and a name that is gone is
        // dropped, so the two together always come back to exactly the layouts there are.
        assertEquals(listOf("Reading", "Travel", "Personal", "Work"),
            orderLayouts(listOf("Travel", "Personal", "Work", "Reading"), listOf("Reading", "Travel")))
        assertEquals(layouts.toSet(), orderLayouts(layouts, listOf("Reading", "Travel", "Work", "Personal")).toSet())
    }

    @Test fun aBuiltInLayoutIsRenamedLikeAnyOtherAndKeepsItsTiles() = runTest {
        val (apps, profiles) = store("rename-builtin")
        apps.setGrid(listOf(GridItem(1, clock, 1)))
        profiles.duplicateInto("Work")
        profiles.select("Work")

        profiles.rename("Work", "Office")

        assertEquals(listOf("Personal", "Travel", "Office"), profiles.layouts.first())
        assertEquals("Office", profiles.active.first())
        // The tiles travel with the name rather than being left behind under the old one.
        assertEquals(1, apps.homeGridFlow.first().size)
        assertEquals(setOf("Personal", "Office"), profiles.allGrids().keys)
        // The schedule named the old layout, so the rename has to reach it or the schedule stops
        // switching and the bar quietly loses the chip.
        val settings = renameLayoutRefs(TileSettings(workLayout = "Work"), "Work", "Office")
        assertEquals("Office", resolveScheduledLayout(scheduledProfile(at("2026-09-25T09:00"), 9, 17, settings.workLayout, settings.personalLayout),
            profiles.layouts.first()))
        assertTrue(refusal { profiles.rename("Office", "Travel") }!!.contains("already exists"))
        // The name the built-in gave up is free again, and the layout that took it is not.
        profiles.rename("Office", "Work")
        assertEquals(listOf("Personal", "Travel", "Work"), profiles.layouts.first())
        assertEquals(1, apps.homeGridFlow.first().size)
    }

    @Test fun aBuiltInLayoutIsDeletedAndTheLauncherIsNeverLeftWithNone() = runTest {
        val (apps, profiles) = store("delete-builtin")
        apps.setGrid(listOf(GridItem(1, clock, 1)))
        profiles.duplicateInto("Work")
        profiles.select("Work")

        profiles.select("Travel")
        profiles.delete("Work")

        assertEquals(listOf("Personal", "Travel"), profiles.layouts.first())
        assertEquals("Travel", profiles.active.first())
        // A layout the schedule named is gone, so the schedule resolves to what is really there.
        assertEquals("Personal", resolveScheduledLayout("Work", profiles.layouts.first()))
        // The last layout standing is the one that cannot be deleted.
        profiles.select("Personal")
        profiles.delete("Travel")
        assertEquals(listOf("Personal"), profiles.layouts.first())
        assertTrue(refusal { profiles.delete("Personal") }!!.contains("one layout"))
    }

    @Test fun theLayoutBeingShownIsNeverDeletedOutFromUnderIt() = runTest {
        val (_, profiles) = store("delete-active")
        profiles.select("Work")

        assertTrue(refusal { profiles.delete("Work") }!!.contains("Pick another layout"))
        assertEquals("Work", profiles.active.first())
        assertTrue("Work" in profiles.layouts.first())
    }

    @Test fun theBarOrderIsPreservedByARenameAndByADelete() {
        val saved = TileSettings(layoutOrder = listOf("Travel", "Personal", "Work"))

        val renamed = renameLayoutRefs(saved, "Work", "Office")

        assertEquals(listOf("Travel", "Personal", "Office"), orderLayouts(listOf("Travel", "Personal", "Office"), renamed.layoutOrder))
        val dropped = dropLayoutRefs(renamed, "Travel", "Personal")
        assertEquals(listOf("Personal", "Office"), orderLayouts(listOf("Personal", "Office"), dropped.layoutOrder))
        // Renaming and deleting touch the order in place: no chip is reordered out of existence.
        assertEquals(listOf("Personal", "Office"), dropped.layoutOrder)
    }
}
