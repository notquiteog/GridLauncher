package tgo1014.gridlauncher.screenshots

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * The canary for the screenshot harness.
 *
 * A comparator that is subtly broken - comparing the wrong buffer, comparing nothing, comparing in a
 * way that always agrees - fails *silently*: every visual regression keeps shipping and the suite
 * stays green. So these tests do not test the arithmetic. They test the arithmetic's ability to fail,
 * which is the only property that makes a screenshot suite worth having.
 */
class PixelDiffTest {

    private fun image(width: Int, height: Int, colour: Int = 0xFF3366CC.toInt()) =
        IntArray(width * height) { colour }

    @Test fun identicalImagesAgree() {
        val result = PixelDiff.compare(image(40, 30), image(40, 30), 40, 30)
        assertEquals(0, result.changedPixels)
        assertEquals(0, result.maxChannelDelta)
        assertTrue(result.isAcceptable())
    }

    @Test fun aSingleChangedPixelIsStillFound() {
        val expected = image(40, 30)
        val actual = expected.copyOf()
        actual[617] = 0xFF000000.toInt()
        val result = PixelDiff.compare(expected, actual, 40, 30)
        assertEquals(1, result.changedPixels)
        assertEquals(204, result.maxChannelDelta) // blue: 0xCC against 0x00
        assertEquals(617, PixelDiff.diffImage(expected, actual, 40, 30).indexOfFirst { it == 0xFFFF00FF.toInt() })
        // On a 1200-pixel image one changed pixel is over budget, because the budget is a fraction
        // and this is a toy-sized image. See oneChangedGlyphIsRejectedButAStrayPixelIsNot for the
        // rule at the size these scenes are actually captured.
    }

    @Test fun oneChangedGlyphIsRejectedButAStrayPixelIsNot() {
        // 1080x2400 is the size these scenes are captured at.
        val screen = 1080 * 2400
        val glyph = 40 * 40
        assertTrue("a single stray pixel must not fail a build",
            PixelDiff.compare(IntArray(screen), IntArray(screen).also { it[7] = 0xFFFFFFFF.toInt() }, 1080, 2400).isAcceptable())
        val changed = IntArray(screen)
        repeat(glyph) { changed[it] = 0xFFFFFFFF.toInt() }
        assertFalse("one glyph of pixels changing colour is a regression",
            PixelDiff.compare(IntArray(screen), changed, 1080, 2400).isAcceptable())
    }

    @Test fun aStructuralChangeIsRejectedByTheAcceptanceRule() {
        // A whole tile turning the wrong colour is ~1% of this image: over the allowed fraction.
        val expected = image(100, 100, 0xFF0078D7.toInt())
        val actual = IntArray(100 * 100) { i ->
            if (i % 100 in 20 until 60) 0xFFB4009E.toInt() else 0xFF0078D7.toInt()
        }
        val result = PixelDiff.compare(expected, actual, 100, 100)
        assertEquals(40 * 100, result.changedPixels)
        assertFalse(result.isAcceptable())
        assertTrue(result.describe().contains("pixels changed"))
    }

    @Test fun roundingNoiseIsToleratedButRealColourChangeIsNot() {
        val expected = IntArray(4) { 0xFF808080.toInt() }
        // Two units of drift on each channel: dithering, not a design change.
        val rounding = IntArray(4) { 0xFF828282.toInt() }
        assertTrue(PixelDiff.compare(expected, rounding, 2, 2).isAcceptable())
        val real = IntArray(4) { 0xFF8282B4.toInt() }
        val result = PixelDiff.compare(expected, real, 2, 2)
        assertEquals(4, result.changedPixels)
        assertFalse(result.isAcceptable())
    }

    @Test fun alphaIsComparedSoATransparentTileCannotHide() {
        val expected = IntArray(1) { 0xFF112233.toInt() }
        // Same RGB, no alpha: a tile that went transparent is a bug, not a rounding difference.
        val transparent = IntArray(1) { 0x00112233 }
        assertEquals(255, PixelDiff.channelDelta(expected[0], transparent[0]))
        assertFalse(PixelDiff.compare(expected, transparent, 1, 1).isAcceptable())
    }

    @Test fun mismatchedBufferLengthsAreNotTreatedAsNearlyIdentical() {
        val expected = image(10, 10)
        val actual = image(10, 10).copyOf(60)
        val result = PixelDiff.compare(expected, actual, 10, 10)
        assertTrue("a truncated capture is never 'close enough'", !result.isAcceptable())
        assertFalse(PixelDiff.matches(expected, actual, 10, 10))
    }

    @Test fun theDiffImageMarksDifferencesAndPreservesNothingElse() {
        val expected = IntArray(4) { 0xFF3366CC.toInt() }
        val actual = IntArray(4) { 0xFF3366CC.toInt() }
        actual[2] = 0xFFFF0000.toInt()
        val diff = PixelDiff.diffImage(expected, actual, 2, 2)
        assertEquals(0xFFFF00FF.toInt(), diff[2])
        val r = (diff[0] ushr 16) and 0xFF
        val g = (diff[0] ushr 8) and 0xFF
        val b = diff[0] and 0xFF
        assertEquals("matching pixels are greyed, not left in colour", r, g)
        assertEquals(r, b)
    }

    @Test fun channelDeltaIsTheWorstChannelNotTheAverage() {
        assertEquals(200, PixelDiff.channelDelta(0xFF000000.toInt(), 0xFFC8C8C8.toInt()))
        assertEquals(0, PixelDiff.channelDelta(0xFF123456.toInt(), 0xFF123456.toInt()))
    }

    @Test fun contrastRatioMatchesKnownWcagValues() {
        // Black on white is the 21:1 maximum; the pair that was 1.19:1 is the bug this guards.
        assertEquals(21.0, PixelDiff.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.05)
        assertEquals(1.0, PixelDiff.contrastRatio(0xFF808080.toInt(), 0xFF808080.toInt()), 0.001)
        val unreadable = PixelDiff.contrastRatio(0xFF2A2A2A.toInt(), 0xFF3A3A3A.toInt())
        assertTrue("dark ink on a dark scrim must read as unreadable here", unreadable < 1.5)
        assertTrue("the measured fix has to clear 4.5:1", PixelDiff.contrastRatio(0xFF1B1B1B.toInt(), 0xFFF2F2F2.toInt()) > 4.5)
    }
}