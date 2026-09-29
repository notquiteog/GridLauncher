package tgo1014.gridlauncher.live

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem

class StartSearchTest {
    private val apps = listOf(App("Camera", "org.example.camera"), App("Calculator", "org.example.calc"))
    private val tiles = listOf(GridItem(1, App("People", BuiltInTiles.PEOPLE), 2), GridItem(2, App("Music", BuiltInTiles.MUSIC), 1))
    private val contacts = listOf(PinnedContact("k1", "Alex Rivera", phones = listOf("5551234")))
    private val notifications = listOf(TileNotification("n1", "org.example.chat", "Alex", "See you at six", 10))

    @Test fun appsWinAndThenTilesPeopleAndNotifications() {
        val results = StartSearch.search("e", apps, tiles, contacts, notifications, allowWeb = false)
        assertTrue(results.first() is SearchResult.AppResult)
        assertTrue(results.any { it is SearchResult.TileResult })
        assertTrue(results.any { it is SearchResult.NotificationResult })
        assertTrue(results.none { it is SearchResult.WebResult })
        assertTrue(StartSearch.search("people", apps, tiles, allowWeb = false).any { it is SearchResult.TileResult })
        assertTrue(StartSearch.search("rivera", apps, tiles, contacts, allowWeb = false).any { it is SearchResult.PersonResult })
    }
    @Test fun appMatchesAreAccentInsensitiveAndWebIsOnlyAFallback() {
        assertTrue(StartSearch.search("CAMÉRA", apps, allowWeb = false).any { (it as? SearchResult.AppResult)?.app?.packageName == "org.example.camera" })
        assertTrue(StartSearch.search("zzzz", apps).any { it is SearchResult.WebResult })
        assertTrue(StartSearch.search("zzzz", apps, allowWeb = false).none { it is SearchResult.WebResult })
        assertTrue(StartSearch.search("   ", apps).isEmpty())
    }
    @Test fun settingsAreCuratedActionsNotGuessedIntents() {
        val result = StartSearch.search("bluetooth", apps, allowWeb = false).filterIsInstance<SearchResult.SettingResult>().single()
        assertEquals("Bluetooth", result.label)
        assertEquals("android.settings.BLUETOOTH_SETTINGS", result.action)
        assertEquals(listOf("Bluetooth" to "android.settings.BLUETOOTH_SETTINGS"), StartSearch.matchingSettings("bluetooth"))
    }
    @Test fun webSearchIsDuckDuckGoAndEncoded() {
        val url = StartSearch.webUrl("hello world & more")
        assertTrue(url.startsWith("https://duckduckgo.com/?q="))
        assertTrue(url.contains("hello+world"))
        assertFalse(url.contains(" "))
    }
    @Test fun resultsAreCapped() {
        val many = (0 until 200).map { TileNotification("k$it", "pkg$it", "Title $it", "Body $it", it.toLong()) }
        assertTrue(StartSearch.search("Title", apps, notifications = many, limit = 30).size <= 30)
    }
}

class StartQueryTest {
    private val now = 1_757_000_000_000L
    private val person = PinnedContact("k1", "Alex Rivera", phones = listOf("5551234"), emails = listOf("alex@example.com"))
    private val facts = StartFacts(
        apps = listOf("Camera", "Calculator"),
        tiles = listOf("People", "Music"),
        people = listOf(person),
        notifications = listOf(TileNotification("n1", "chat", "Alex", "See you at six", now - 60_000)),
        events = listOf(CalendarEvent("Standup", now + 3_600_000, false, "Room 2")),
        nowPlaying = "Artist · Album", battery = "80% · Battery remaining", storage = "12.0 GB used",
        clock = "9:41 AM")

    @Test fun calendarQuestionsAnswerFromRealEvents() {
        val answers = StartQuery.answer("what's on today", facts, now)
        assertTrue(answers.first().text.startsWith("Standup"))
        assertTrue(StartQuery.answer("when is standup", facts, now).first().text.contains("in 1 h"))
        assertTrue(StartQuery.answer("what's on today", StartFacts(), now).first().text.contains("Nothing scheduled"))
    }
    @Test fun notificationQuestionsGroupByTheNamedPerson() {
        assertTrue(StartQuery.answer("who's messaging me", facts, now).first().text.contains("See you at six"))
        assertTrue(StartQuery.answer("unread", StartFacts(), now).first().text.contains("No matching"))
    }
    @Test fun deviceStateQuestionsReadTheRealValues() {
        assertEquals("80% · Battery remaining", StartQuery.answer("how much battery", facts, now).first().text)
        assertEquals("12.0 GB used", StartQuery.answer("how much space", facts, now).first().text)
        assertEquals("Artist · Album", StartQuery.answer("what's playing", facts, now).first().text)
        assertEquals("9:41 AM", StartQuery.answer("what time is it", facts, now).first().text)
    }
    @Test fun contactActionsAndUnknownQueries() {
        assertEquals(Answer.Action.Call, StartQuery.answer("call alex", facts, now).first().action)
        assertEquals(Answer.Action.Text, StartQuery.answer("text alex", facts, now).first().action)
        assertEquals(Answer.Action.Email, StartQuery.answer("email alex", facts, now).first().action)
        assertEquals(Answer.Action.OpenApp, StartQuery.answer("open camera", facts, now).first().action)
        assertEquals(Answer.Action.OpenTile, StartQuery.answer("open music", facts, now).first().action)
        assertEquals(Answer.Action.Web, StartQuery.answer("banana bread recipe", facts, now).first().action)
        assertTrue(StartQuery.answer("", facts, now).isEmpty())
    }
    @Test fun aPersonWithNoNumberIsNotOfferedACall() {
        val emailOnly = facts.copy(people = listOf(person.copy(phones = emptyList())))
        assertEquals(Answer.Action.Nothing, StartQuery.answer("call alex", emailOnly, now).first().action)
        assertEquals(Answer.Action.Email, StartQuery.answer("email alex", emailOnly, now).first().action)
    }
    @Test fun relativeTimesAreReadable() {
        assertEquals("now", StartQuery.relative(now, now))
        assertEquals("in 5 min", StartQuery.relative(now + 5 * 60_000, now))
        assertEquals("in 2 h 5 min", StartQuery.relative(now + 125 * 60_000, now))
    }
}
