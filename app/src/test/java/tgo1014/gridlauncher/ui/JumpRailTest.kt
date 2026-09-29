package tgo1014.gridlauncher.ui

import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.ui.composables.jumpLetterOf

class JumpRailTest {
    @Test fun everyTileNameFallsIntoExactlyOneLetterBucket() {
        assertEquals('C', jumpLetterOf("Clock"))
        assertEquals('W', jumpLetterOf("  weather  "))
        assertEquals('Z', jumpLetterOf("zebra"))
        assertEquals('#', jumpLetterOf("7TV"))
        assertEquals('#', jumpLetterOf("+Shortcut"))
        assertEquals('#', jumpLetterOf(""))
    }
}
