package tgo1014.gridlauncher.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings

class NowBoardTest {
    private val weather = App("AccuWeather", "com.accuweather.android")
    private val news = App("BBC News", "com.bbc.mobile.news.bbccnews")

    private fun ride() = TileNotification("ride", "com.ride.app", "Ride arriving", "4 minutes", 10, ongoing = true, progress = 4, progressMax = 10)
    private fun forecast() = TileNotification("weather", "com.accuweather.android", "12° and raining", "Until 6pm", 20)
    private fun settings(hidden: Set<String> = emptySet(), text: Boolean = true) = TileSettings(showNotificationText = text, hiddenPreviewApps = hidden)

    @Test fun anOngoingNotificationIsACardAndAFinishedOneIsNot() {
        val cards = NowBoard.cards(BoardInput(settings = settings(), notifications = listOf(ride())))
        assertEquals(1, cards.size)
        assertEquals("Ride arriving", cards.single().title)
        assertEquals("4 minutes", cards.single().lines.single())
        assertEquals(NowAction.OpenNotification("ride"), cards.single().action)
        assertTrue(NowBoard.cards(BoardInput(settings = settings())).isEmpty())
    }

    @Test fun quietHoursAndTheNowSwitchBothTakeTheWholeBoardAway() {
        val input = BoardInput(settings = settings(), notifications = listOf(ride()))
        assertTrue(NowBoard.cards(input.copy(quiet = true)).isEmpty())
        assertTrue(NowBoard.cards(input.copy(settings = settings().copy(showNow = false, liveTilesEnabled = false))).isEmpty())
    }

    @Test fun noNotificationTextReachesTheBoardWhileItIsLockedOrPreviewsAreOff() {
        val locked = NowBoard.cards(BoardInput(settings = settings(), notifications = listOf(ride()), locked = true)).single()
        assertEquals(NowCardKind.NOTIFICATION.label, locked.title)
        assertTrue(locked.lines.isEmpty())
        assertNull(locked.notification)
        val muted = NowBoard.cards(BoardInput(settings = settings(text = false), notifications = listOf(ride()))).single()
        assertEquals(NowCardKind.NOTIFICATION.label, muted.title)
        assertTrue(muted.lines.isEmpty())
    }

    @Test fun anAppExcludedFromPreviewsIsLeftOffTheBoardEntirely() {
        val cards = NowBoard.cards(BoardInput(
            settings = settings(hidden = setOf("com.ride.app", "com.accuweather.android")),
            notifications = listOf(ride(), forecast()),
            categories = mapOf(NowCategory.WEATHER to weather)))
        assertTrue(cards.isEmpty())
    }

    @Test fun aCategoryTheDeviceCanAnswerNamesItsAppEvenWithNothingToRead() {
        val cards = NowBoard.cards(BoardInput(settings = settings(), notifications = listOf(ride()),
            categories = mapOf(NowCategory.MAPS to news)))
        val maps = cards.last()
        assertEquals(NowCardKind.CATEGORY, maps.kind)
        assertEquals("Maps", maps.source)
        assertEquals("BBC News", maps.title)
        assertEquals(listOf("Maps · BBC News"), maps.lines)
        assertEquals(NowAction.OpenApp("com.bbc.mobile.news.bbccnews"), maps.action)
    }

    @Test fun aCategoryOnItsOwnDoesNotBringTheBoardUp() {
        // A category the user has not heard from is an app shortcut; Start is where shortcuts live.
        assertTrue(NowBoard.cards(BoardInput(settings = settings(), categories = mapOf(NowCategory.MAPS to news))).isEmpty())
    }

    @Test fun aMusicAppThatIsPlayingIsNotRepeatedAsAShortcut() {
        val cards = NowBoard.cards(BoardInput(settings = settings(), notifications = listOf(ride()),
            nowPlaying = NowPlaying("com.google.android.apps.youtube.music", "Song", "Artist", "", true, null, true, true),
            categories = mapOf(NowCategory.MEDIA to App("YouTube Music", "com.google.android.apps.youtube.music"))))
        assertEquals(listOf(NowCardKind.NOTIFICATION, NowCardKind.MEDIA), cards.map { it.kind })
    }

    @Test fun aCategoryWithNothingToReadIsLeftOffTheBoard() {
        assertTrue(NowBoard.cards(BoardInput(settings = settings())).isEmpty())
        assertTrue(NowBoard.cards(BoardInput(settings = settings(), agenda = emptyList())).isEmpty())
        assertTrue(NowBoard.cards(BoardInput(settings = settings(), nowPlaying = NowPlaying("a", "", "", "", false, null, false, false))).isEmpty())
    }

    @Test fun aCategoryCardShowsWhatTheAppPostedAndNamesTheApp() {
        val cards = NowBoard.cards(BoardInput(settings = settings(),
            notifications = listOf(forecast()), categories = mapOf(NowCategory.WEATHER to weather)))
        val card = cards.single()
        assertEquals("12° and raining", card.title)
        assertEquals(listOf("Until 6pm", "Weather · AccuWeather"), card.lines)
        assertEquals(NowAction.OpenNotification("weather"), card.action)
    }

    @Test fun aHeadlineTheBoardAlreadyShowsAsALiveCardIsNotRepeated() {
        val live = forecast().copy(ongoing = true, progress = 1, progressMax = 2)
        val cards = NowBoard.cards(BoardInput(settings = settings(),
            notifications = listOf(live), categories = mapOf(NowCategory.WEATHER to weather)))
        assertEquals(2, cards.size)
        assertEquals(NowCardKind.NOTIFICATION, cards[0].kind)
        assertEquals(NowAction.OpenApp("com.accuweather.android"), cards[1].action)
    }

    @Test fun theAgendaShowsSeveralEventsAndTheFirstOneOpensTheCalendar() {
        val events = listOf(CalendarEvent("Stand-up", 1_000, false, "Room 2"), CalendarEvent("Lunch", 2_000, true, ""))
        val card = NowBoard.cards(BoardInput(settings = settings(), agenda = events)).single()
        assertEquals(NowCardKind.AGENDA, card.kind)
        assertEquals("Stand-up", card.title)
        assertEquals(events, card.events)
        assertEquals(NowAction.OpenCalendar, card.action)
    }

    @Test fun musicIsTheSessionsOwnAndCarriesItsTransport() {
        val now = NowPlaying("com.spotify.music", "Bohemian Rhapsody", "Queen", "A Night at the Opera", true, null, hasNext = true, canGoPrevious = true)
        val card = NowBoard.cards(BoardInput(settings = settings(), nowPlaying = now, mediaAppName = "Spotify")).single()
        assertEquals(NowCardKind.MEDIA, card.kind)
        assertEquals("Bohemian Rhapsody", card.title)
        assertEquals(listOf("Queen", "A Night at the Opera", "Spotify"), card.lines)
        assertEquals("com.spotify.music", card.appPackage)
        assertTrue(card.expandable)
    }

    @Test fun moneyOnlyAppearsWhenTheUserHasPinnedAnAppToTheSlot() {
        assertTrue(NowBoard.cards(BoardInput(settings = settings())).isEmpty())
        val card = NowBoard.cards(BoardInput(settings = settings(), money = App("Bank", "com.example.bank"))).single()
        assertEquals(NowCardKind.MONEY, card.kind)
        assertEquals("Bank", card.title)
        assertEquals(NowAction.OpenApp("com.example.bank"), card.action)
        assertTrue(!card.expandable)
    }

    @Test fun cardsComeOutInTheOrderTheBoardShowsThem() {
        val cards = NowBoard.cards(BoardInput(settings = settings(),
            notifications = listOf(forecast(), ride()),
            nowPlaying = NowPlaying("com.spotify.music", "Song", "Artist", "", true, null, true, true),
            agenda = listOf(CalendarEvent("Stand-up", 1_000, false, "")),
            categories = mapOf(NowCategory.WEATHER to weather),
            money = App("Bank", "com.example.bank")))
        assertEquals(listOf(NowCardKind.NOTIFICATION, NowCardKind.AGENDA, NowCardKind.MEDIA, NowCardKind.CATEGORY, NowCardKind.MONEY),
            cards.map { it.kind })
    }

    @Test fun everyCardSaysWhatItIsAndWhereItCameFrom() {
        val card = NowBoard.cards(BoardInput(settings = settings(), notifications = listOf(ride()))).single()
        assertEquals("Ongoing activity, Ride arriving, 4 minutes, collapsed", card.describe(false))
        assertEquals("Ongoing activity, Ride arriving, 4 minutes, expanded", card.describe(true))
    }
}
