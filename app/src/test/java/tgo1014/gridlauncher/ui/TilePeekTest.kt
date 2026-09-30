package tgo1014.gridlauncher.ui

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.ui.composables.PeekVisibility
import tgo1014.gridlauncher.ui.composables.peekable

class TilePeekTest {
    private val hub = PeekVisibility(hub = true)
    private val posted = PeekVisibility(notification = true)
    private val plain = PeekVisibility()

    @Test fun onlyATileWithSomethingLiveCanBePeeked() {
        assertTrue(hub.peekable())
        assertTrue(posted.peekable())
        assertTrue(PeekVisibility(call = true).peekable())
        assertTrue(PeekVisibility(people = true).peekable())
        assertTrue(PeekVisibility(photos = true).peekable())
        // An icon and a label are all this tile has, so there is nothing to magnify: it must open.
        assertFalse(plain.peekable())
    }

    @Test fun quietHoursALockedDeviceAndHiddenPreviewsAllCloseThePeek() {
        assertFalse(hub.copy(quiet = true).peekable())
        assertFalse(hub.copy(locked = true).peekable())
        assertFalse(posted.copy(previewsHidden = true).peekable())
        assertFalse(posted.copy(quiet = true, locked = true, previewsHidden = true).peekable())
        // The peek can only ever narrow what a tile shows. It is never the way round.
        assertFalse(plain.copy(quiet = false, locked = false, previewsHidden = false).peekable())
    }

    @Test fun turningLiveTilesOffLeavesNothingToMagnifyAndNoPeek() {
        assertFalse(hub.copy(liveTiles = false).peekable())
        assertFalse(posted.copy(liveTiles = false).peekable())
    }

    @Test fun aGroupHeaderAndAWidgetHostAreNotPeeked() {
        // A widget is drawn by Android, which will not hand the same widget id to a second view.
        assertFalse(plain.copy(widget = true, notification = true).peekable())
        assertFalse(plain.copy(group = true, hub = true).peekable())
    }
}
