package tgo1014.gridlauncher.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryAppsTest {
    private val news = listOf("com.google.android.apps.magazine", "com.bbc.mobile.news.bbccnews", "com.cnn.android")
    private val weather = listOf("com.android.weather", "com.accuweather.android")

    @Test fun aWellKnownAppTheDeviceHasIsTheOneShown() {
        assertEquals("com.google.android.apps.maps", CategoryApps.pick(
            installed = setOf("com.google.android.apps.maps", "com.waze", "com.android.chrome"),
            queried = listOf("com.waze", "com.google.android.apps.maps"), wellKnown = listOf("com.google.android.apps.maps", "com.waze")))
    }

    @Test fun aBrowserAnsweringTheQueryIsNotTheMapsApp() {
        // Every browser answers a geo: link, so a well-known app the device has outranks the query.
        assertEquals("com.google.android.apps.maps", CategoryApps.pick(
            installed = setOf("com.android.chrome", "com.google.android.apps.maps"),
            queried = listOf("com.android.chrome"), wellKnown = listOf("com.google.android.apps.maps")))
    }

    @Test fun anAppWeDoNotKnowByNameIsStillFoundThroughTheQuery() {
        assertEquals("com.example.maps", CategoryApps.pick(
            installed = setOf("com.example.maps"), queried = listOf("com.example.maps"), wellKnown = listOf("com.google.android.apps.maps")))
    }

    @Test fun theWellKnownListIsTheFallbackWhenNothingAnswersTheQuery() {
        assertEquals("com.android.weather", CategoryApps.pick(
            installed = setOf("com.android.chrome", "com.android.weather"), queried = emptyList(), wellKnown = weather))
    }

    @Test fun orderInsideTheWellKnownListIsTheOrderTheyAreOfferedIn() {
        assertEquals("com.google.android.apps.magazine", CategoryApps.pick(
            installed = setOf("com.cnn.android", "com.google.android.apps.magazine", "com.bbc.mobile.news.bbccnews"),
            queried = emptyList(), wellKnown = news))
        assertEquals("com.bbc.mobile.news.bbccnews", CategoryApps.pick(
            installed = setOf("com.bbc.mobile.news.bbccnews", "com.cnn.android"), queried = emptyList(), wellKnown = news))
    }

    @Test fun nothingIsResolvedWhenTheDeviceHasNothing() {
        assertNull(CategoryApps.pick(installed = emptySet(), queried = emptyList(), wellKnown = news))
        assertNull(CategoryApps.pick(installed = setOf("com.android.chrome"), queried = listOf("com.android.news"), wellKnown = news))
    }

    @Test fun aPackageTheQueryNamesButTheDeviceLostIsNeverReturned() {
        // An app can be uninstalled between the query and the answer; the card is left out instead.
        assertEquals("com.android.weather", CategoryApps.pick(
            installed = setOf("com.android.weather"), queried = listOf("com.removed.weather", "com.android.chrome"), wellKnown = weather))
    }

    @Test fun aCategoryWithNoQueryOfItsOwnStillResolvesFromTheList() {
        assertEquals("com.android.weather", CategoryApps.pick(
            installed = setOf("com.android.weather", "com.google.android.apps.maps"),
            queried = listOf("com.google.android.apps.maps"), wellKnown = weather))
    }
}
