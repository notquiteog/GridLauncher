package tgo1014.gridlauncher.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.domain.models.IconShape
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.theme.superellipse
import kotlin.math.hypot

class IconMaskTest {
    @Test fun anUnrecognisedShapeFallsBackToTheLookThatIsAlreadyOnScreen() {
        assertEquals(IconShape.BLEED, IconShape.of(""))
        assertEquals(IconShape.BLEED, IconShape.of("square"))
        assertEquals(IconShape.BLEED, IconShape.of("Circle"))
        assertEquals(IconShape.CIRCLE, IconShape.of("circle"))
        assertEquals(IconShape.SQUIRCLE, IconShape.of("squircle"))
        assertEquals(IconShape.ROUNDED, IconShape.of("rounded"))
    }

    @Test fun everyShapeHasItsOwnLabelAndOnlyFullBleedDrawsUnmasked() {
        assertEquals(IconShape.entries.size, IconShape.entries.map { it.key }.toSet().size)
        assertEquals(IconShape.entries.map { it.key }, IconShape.entries.map { IconShape.of(it.key).key })
        assertTrue(IconShape.entries.all { it.label.isNotBlank() })
        // Full bleed is what this launcher has always drawn, so it is the one that clips nothing.
        assertFalse(IconShape.BLEED.masks)
        assertTrue(IconShape.entries.filter { it != IconShape.BLEED }.all { it.masks })
    }

    @Test fun aLayoutWrittenBeforeIconShapesStillOpensOnTheFullBleedIconsItHad() {
        val stored = Json.decodeFromString(TileSettings.serializer(), """{"accentColor":4294951767,"darkTheme":true}""")
        assertEquals(IconShape.BLEED, stored.iconMask)
        // And the chosen shape is stored like the rest of the look, so it survives a restart.
        val chosen = Json.encodeToString(TileSettings.serializer(), TileSettings(iconShape = "squircle"))
        assertEquals(IconShape.SQUIRCLE, Json.decodeFromString(TileSettings.serializer(), chosen).iconMask)
    }

    @Test fun theSquircleReachesFurtherIntoItsCornersThanACircleWould() {
        val steps = 64
        val squircle = superellipse(100f, 100f, exponent = 4f, steps = steps)
        val circle = superellipse(100f, 100f, exponent = 2f, steps = steps)
        assertEquals((steps + 1) * 2, squircle.size)
        fun point(points: FloatArray, step: Int) = points[step * 2] to points[step * 2 + 1]
        // The walk starts at the middle of the right edge and goes all the way round. The four
        // extremes are a step away from an exact sample, so they are checked to a tenth of a pixel.
        fun near(expected: Pair<Float, Float>, at: Int) = assertEquals(expected.first, point(squircle, at).first, .1f).also {
            assertEquals(expected.second, point(squircle, at).second, .1f)
        }
        near(100f to 50f, 0)
        near(50f to 100f, steps / 4)
        near(0f to 50f, steps / 2)
        near(50f to 0f, steps * 3 / 4)
        // What makes it a squircle rather than a circle: at the diagonal it bulges past the circle
        // that fits the same box, which is the whole difference between the two shapes.
        fun reach(points: FloatArray) = hypot(point(points, steps / 8).first - 50f, point(points, steps / 8).second - 50f)
        assertEquals(50f, reach(circle), 0.05f)
        assertTrue("squircle reach ${reach(squircle)}", reach(squircle) > 55f)
        // Every point is on the artwork's own canvas, and the walk closes on every edge of it.
        for (index in squircle.indices step 2) {
            assertTrue("x=${squircle[index]}", squircle[index] in 0f..100f)
            assertTrue("y=${squircle[index + 1]}", squircle[index + 1] in 0f..100f)
        }
    }

    @Test fun anEmptyOrDegenerateSizeDrawsNothingRatherThanFailing() {
        assertEquals(0, superellipse(0f, 48f, 4f).size)
        assertEquals(0, superellipse(48f, -1f, 4f).size)
        assertEquals(0, superellipse(48f, 48f, 4f, steps = 0).size)
    }
}
