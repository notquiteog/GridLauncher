package tgo1014.gridlauncher.live

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.domain.models.TileSettings

class StartSearchTest {

    private fun row(id: String, source: String, title: String, text: String = "", subtitle: String = "", stamp: Long = 0) =
        SearchRow(id, source, title, text, subtitle, stamp)

    @Test fun queryOperatorsAreStrippedBeforeAppSearchSeesThem() {
        // AppSearch reads these as query syntax and throws on a malformed expression, so a user
        // typing them into the drawer must not reach it verbatim.
        assertEquals(listOf("meeting", "notes", "old"), StartSearch.terms("meeting: (notes) -old"))
        assertEquals(listOf("standup"), StartSearch.terms("  Standup  "))
        assertEquals(emptyList<String>(), StartSearch.terms("!!! ???"))
        assertEquals(emptyList<String>(), StartSearch.terms("   "))
        assertEquals("meeting OR notes OR old", StartSearch.phrase("meeting: (notes) -old"))
        assertEquals("", StartSearch.phrase("()"))
    }

    @Test fun termsAreAccentAndCaseFoldedAndCapped() {
        assertEquals(listOf("uber", "cafe"), StartSearch.terms("Uber Café"))
        assertEquals(StartSearch.MAX_TERMS, StartSearch.terms((1..20).joinToString(" ") { "w$it" }).size)
        assertEquals(listOf("one"), StartSearch.terms("one ONE"))
    }

    @Test fun anyWordMatchesAndABlankQueryMatchesNothing() {
        val target = row("1", SearchSource.PERSON, "Zoë", "555-0100", "+1 555 0100")
        assertTrue(StartSearch.matches(target, StartSearch.terms("zoe")))
        assertTrue(StartSearch.matches(target, StartSearch.terms("555-0100")))
        assertTrue(StartSearch.matches(target, StartSearch.terms("nothing here 0100")))
        assertFalse(StartSearch.matches(target, StartSearch.terms("alex")))
        assertEquals(emptyList<SearchRow>(), StartSearch.filter(listOf(target), "   "))
        assertEquals(listOf(target), StartSearch.filter(listOf(target), "0100"))
    }

    @Test fun sectionsOrderPeopleThenNotificationsThenCalendar() {
        val rows = listOf(
            row("e1", SearchSource.EVENT, "Standup", stamp = 300),
            row("e0", SearchSource.EVENT, "Retro", stamp = 100),
            row("n1", SearchSource.NOTIFICATION, "Older", stamp = 10),
            row("n2", SearchSource.NOTIFICATION, "Newer", stamp = 20),
            row("p1", SearchSource.PERSON, "Bea"),
            row("p0", SearchSource.PERSON, "Ann"),
        )
        val sections = StartSearch.sections(rows)
        assertEquals(listOf(SearchSource.PERSON, SearchSource.NOTIFICATION, SearchSource.EVENT), sections.map { it.source })
        assertEquals(listOf("p0", "p1"), sections[0].rows.map { it.id })
        // Newest notification first, soonest event first.
        assertEquals(listOf("n2", "n1"), sections[1].rows.map { it.id })
        assertEquals(listOf("e0", "e1"), sections[2].rows.map { it.id })
    }

    @Test fun sectionsAreCappedAndEmptySourcesDropOut() {
        val many = (0..20).map { row("p$it", SearchSource.PERSON, "Person $it") }
        assertEquals(8, StartSearch.sections(many).single().rows.size)
        assertEquals(1, StartSearch.sections(many, perSection = 1).single().rows.size)
        assertTrue(StartSearch.sections(listOf(row("x", SearchSource.PERSON, "Only"))).size == 1)
        assertTrue(StartSearch.sections(emptyList()).isEmpty())
        assertEquals("People", SearchSource.label(SearchSource.PERSON))
        assertEquals("Notifications", SearchSource.label(SearchSource.NOTIFICATION))
        assertEquals("Calendar", SearchSource.label(SearchSource.EVENT))
    }

    @Test fun digestMovesOnlyWhenTheContentDoes() {
        val rows = listOf(row("a", SearchSource.PERSON, "Ann"), row("b", SearchSource.EVENT, "Retro", stamp = 5))
        assertEquals(StartSearch.digest(rows), StartSearch.digest(rows.toList()))
        assertNotEquals(StartSearch.digest(rows), StartSearch.digest(rows + row("c", SearchSource.PERSON, "Bea")))
        assertNotEquals(StartSearch.digest(rows), StartSearch.digest(listOf(rows[1], rows[0])))
        assertNotEquals(StartSearch.digest(rows), StartSearch.digest(listOf(rows[0], row("b", SearchSource.EVENT, "Retro", stamp = 6))))
    }

    @Test fun notificationRowsHonourTheExistingPrivacyContract() {
        val notifications = listOf(
            TileNotification("k1", "com.example.chat", "Ada", "See you at 3", 10),
            TileNotification("k2", "com.example.bank", "Balance", "1200", 20),
        )
        val open = notificationRows(TileSettings(showNotificationText = true), notifications)
        assertEquals(listOf("n:k1", "n:k2"), open.map { it.id })
        assertEquals(SearchSource.NOTIFICATION, open[0].source)
        assertEquals("Balance", open[1].title)
        assertEquals("1200", open[1].text)

        assertTrue("Text is opt-in", notificationRows(TileSettings(showNotificationText = false), notifications).isEmpty())
        assertTrue("Meeting mode is quiet hours", notificationRows(TileSettings(meetingMode = true), notifications).isEmpty())
        val hidden = notificationRows(TileSettings(showNotificationText = true, hiddenPreviewApps = setOf("com.example.bank")), notifications)
        assertEquals(listOf("n:k1"), hidden.map { it.id })
    }

    @Test fun notificationRowsFallBackToTheSendingAppAndSkipTheEmpty() {
        val rows = notificationRows(TileSettings(showNotificationText = true), listOf(
            TileNotification("k1", "com.example.app", "", "Delivery today", 10),
            TileNotification("k2", "com.example.app", "", "", 20),
        ))
        assertEquals(listOf("n:k1"), rows.map { it.id })
        assertEquals("com.example.app", rows.single().title)
    }

    @Test fun contactRowsCarryTheNumbersAPersonWouldBeReachedOn() {
        val rows = contactRows(listOf(
            PinnedContact("lookup/1", "Ada", phones = listOf("555-0100"), emails = listOf("ada@example.com")),
            PinnedContact("lookup/2", ""),
        ))
        assertEquals(1, rows.size)
        assertEquals("c:lookup/1", rows.single().id)
        assertEquals("555-0100", rows.single().subtitle)
        assertTrue(rows.single().text.contains("ada@example.com"))
    }

    @Test fun eventRowsLabelTheDayAndKeepSameMinuteEventsApart() {
        val rows = eventRows(listOf(
            CalendarEvent("Standup", 1_700_000_000_000, false, "Room 2"),
            CalendarEvent("Retro", 1_700_000_000_000, false, ""),
            CalendarEvent("", 1_700_000_000_000, false, ""),
            CalendarEvent("No time", 0, false, ""),
        ))
        assertEquals(2, rows.size)
        assertEquals("Room 2", rows[0].text)
        assertNotEquals("Two events at the same minute need different ids", rows[0].id, rows[1].id)
        assertEquals(listOf("e:0:1700000000000", "e:1:1700000000000"), rows.map { it.id })
        val allDay = eventRows(listOf(CalendarEvent("Holiday", 1_700_000_000_000, true, ""))).single()
        assertEquals("All day", allDay.subtitle)
    }
}
