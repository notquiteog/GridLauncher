package tgo1014.gridlauncher.data

import org.junit.Assert.*
import org.junit.Test

class IconEdgeColorTest {
    @Test fun transparentPaddingAndDifferentCenterDoNotChangeBlueEdge() {
        val pixels = IntArray(100)
        for (y in 2..7) for (x in 2..7) pixels[y * 10 + x] = 0xFF4285F4.toInt()
        for (y in 3..6) for (x in 3..6) pixels[y * 10 + x] = 0xFFFFFFFF.toInt()
        assertEquals(0xFF4285F4L, iconEdgeColor(pixels, 10, 10))
    }
    @Test fun whiteAdaptiveBackgroundRemainsWhite() {
        val pixels = IntArray(64) { 0xFFFFFFFF.toInt() }
        for (y in 2..5) for (x in 2..5) pixels[y * 8 + x] = 0xFFE60000.toInt()
        assertEquals(0xFFFFFFFFL, iconEdgeColor(pixels, 8, 8))
    }
    @Test fun transparentIconUsesLauncherFallback() {
        assertNull(iconEdgeColor(IntArray(16), 4, 4))
    }
}
