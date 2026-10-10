package tgo1014.gridlauncher.ui

import androidx.compose.material3.DividerDefaults
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The posture and the fold avoidance, on a machine that has never unfolded anything.
 *
 * Every value below is synthetic and every one is a shape `androidx.window` really reports, so this
 * covers the decisions that a foldable would otherwise be the only way to reach. What it cannot
 * cover is whether the platform reports the right fold in the first place, which is the one part
 * that still needs hardware.
 */
class WindowPostureTest {

    private fun hinge(
        vertical: Boolean = true, occludes: Boolean = true, flat: Boolean = true,
        separating: Boolean = false, left: Float = 0f, top: Float = 0f,
        right: Float = 0f, bottom: Float = 0f,
    ) = hingeFrom(
        orientation = if (vertical) FoldingFeature.Orientation.VERTICAL else FoldingFeature.Orientation.HORIZONTAL,
        occlusionType = if (occludes) FoldingFeature.OcclusionType.FULL else FoldingFeature.OcclusionType.NONE,
        state = if (flat) FoldingFeature.State.FLAT else FoldingFeature.State.HALF_OPENED,
        isSeparating = separating, left = left, top = top, right = right, bottom = bottom,
    )!!.toHinge(ONE_PIXEL_IS_ONE_DP)

    // --- the cases that have nothing to do with folding, and must not move ---

    @Test fun aPhoneSizedWindowWithNoFoldIsStillThePager() {
        assertEquals(WindowPosture.Compact, postureFor(411.dp, 1004.dp, null))
    }

    @Test fun aWideWindowWithNoFoldStillGetsTheEvenTwoPaneSplit() {
        val split = (postureFor(841.dp, 694.dp, null) as WindowPosture.Expanded).split
        assertNull("no fold reported, so no hinge to lay out around", split.hinge)
        assertDp(420.5.dp, split.start)
        assertDp(420.5.dp, split.appList)
        // The line a VerticalDivider has always drawn, and the reason the two panes above are each
        // 420.5dp rather than 420.5dp plus half of it: a zero-width divider here would move the
        // whole grid by a pixel on every device that reports no fold.
        assertDp(DividerDefaults.Thickness, split.divider)
        assertTrue("a window with no fold is not stacked", !split.stacked)
    }

    @Test fun aPhoneSizedWindowIsThePagerEvenWithABookModeHingeAgainstOneEdge() {
        // Book mode on its outer screen: the fold is reported hard against the right edge, leaving
        // no second panel. The width rule settles it before the fold is even consulted.
        val fold = hinge(left = 0f, top = 0f, right = 411f, bottom = 1004f)
        assertEquals(WindowPosture.Compact, postureFor(411.dp, 1004.dp, fold))
    }

    @Test fun aPhoneSizedWindowIsThePagerEvenWithAHingeAcrossItsMiddle() {
        // A half-open clamshell on its inner screen. The launcher keeps the pager it has always
        // had rather than stacking two panes on a device that is a phone in every other respect;
        // the trade is named in postureFor rather than hidden.
        val fold = hinge(vertical = false, left = 0f, top = 492f, right = 411f, bottom = 512f)
        assertEquals(WindowPosture.Compact, postureFor(411.dp, 1004.dp, fold))
    }

    // --- a hinge that separates the window ---

    @Test fun aVerticalHingeOccludingTheWindowPutsTheDividerOnItAndInsetsBothPanes() {
        // 841dp wide, hinge occupying 410dp..430dp dead centre, which is a real hardware gap.
        val fold = hinge(vertical = true, occludes = true, left = 410f, right = 430f, top = 0f, bottom = 694f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertDp(410.dp, split.start)
        assertDp(411.dp, split.appList)
        assertDp("the divider is the hinge itself, not the midpoint", 20.dp, split.divider)
        assertTrue("a vertical hinge gives two panes side by side", !split.stacked)
        assertDp(410.dp, split.hinge!!.left)
    }

    @Test fun aSeparatingHingeThatDoesNotOccludeIsStillASeamNothingMayCross() {
        // Two panels with no hardware in the join: a separating fold still splits the window.
        val fold = hinge(vertical = true, occludes = false, separating = true, left = 405f, right = 435f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertDp(405.dp, split.start)
        assertDp(406.dp, split.appList)
        assertDp(30.dp, split.divider)
    }

    @Test fun anOffCentreHingeMovesTheSplitWithIt() {
        // The reason the panes are not weighted: a seam that is not in the middle has to put the
        // divider on the seam, or the divider ends up inside one of the panes.
        val fold = hinge(vertical = true, left = 300f, right = 320f, top = 0f, bottom = 694f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertDp(300.dp, split.start)
        assertDp(521.dp, split.appList)
        assertDp(20.dp, split.divider)
    }

    // --- a fold that is a crease rather than a gap ---

    @Test fun aFlatCreaseLetsTheTwoPanesMeet() {
        // A flexible display opened out: one continuous surface with a zero-width crease down it.
        // There is no hole to avoid, so the panes abut and the crease is the only mark between them.
        val fold = hinge(vertical = true, occludes = false, flat = true, left = 420.5f, right = 420.5f, top = 0f, bottom = 694f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertDp(420.5.dp, split.start)
        assertDp(420.5.dp, split.appList)
        assertDp(0.dp, split.divider)
    }

    @Test fun aFlatHingeThatStillSeparatesKeepsItsGap() {
        // Book mode, fully open: the panels are separate surfaces even though the device is flat,
        // so the gap is kept rather than treated as a crease.
        val fold = hinge(vertical = true, occludes = true, flat = true, separating = true, left = 405f, right = 435f, top = 0f, bottom = 694f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertDp(30.dp, split.divider)
    }

    @Test fun aHalfOpenedBookHingeIsTreatedAsTheSeamItIs() {
        val fold = hinge(vertical = true, occludes = true, flat = false, left = 405f, right = 435f, top = 0f, bottom = 694f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertDp(405.dp, split.start)
        assertDp(30.dp, split.divider)
    }

    // --- a hinge that runs the other way ---

    @Test fun aHorizontalHingeStacksThePanesRatherThanCuttingTheDividerAlongIt() {
        // Tabletop posture, 841x694dp with the seam running across the middle.
        val fold = hinge(vertical = false, occludes = true, left = 0f, top = 337f, right = 841f, bottom = 357f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertTrue("a hinge across the window means stacked panes", split.stacked)
        assertDp(337.dp, split.start)
        assertDp(337.dp, split.appList)
        assertDp(20.dp, split.divider)
    }

    // --- degenerate features ---

    @Test fun aFeatureThatIsAPointIsNotASeam() {
        assertNull(hingeFrom(
            orientation = FoldingFeature.Orientation.VERTICAL,
            occlusionType = FoldingFeature.OcclusionType.FULL,
            state = FoldingFeature.State.FLAT, isSeparating = true,
            left = 420f, top = 300f, right = 420f, bottom = 300f,
        ))
    }

    @Test fun aHingeTooCloseToAnEdgeToCarryAPaneFallsBackToTheEvenSplit() {
        // A fold leaving 200dp on one side and 621dp on the other: the window is still a wide one,
        // so it still gets two panes, but the fold is refused rather than cutting a column of app
        // names down to a sliver.
        val fold = hinge(vertical = true, left = 200f, right = 220f, top = 0f, bottom = 694f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertNull("a fold this close to an edge is not split at", split.hinge)
        assertDp(420.5.dp, split.start)
        assertDp(420.5.dp, split.appList)
    }

    @Test fun theNarrowestPaneStillAcceptedIsTheOneTheRuleNames() {
        val fold = hinge(vertical = true, left = 240f, right = 260f, top = 0f, bottom = 694f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertDp(MinimumPaneExtent, split.start)
        assertDp(581.dp, split.appList)
    }

    @Test fun aHingeThatLeavesAStackedPaneTooShortIsRefusedToo() {
        // The same floor applies along the axis the panes run on, which for a horizontal hinge is
        // the window's height: 120dp of drawer above a seam is not a pane.
        val fold = hinge(vertical = false, left = 0f, top = 120f, right = 841f, bottom = 140f)
        val split = (postureFor(841.dp, 694.dp, fold) as WindowPosture.Expanded).split
        assertNull(split.hinge)
        assertTrue(!split.stacked)
    }

    /**
     * Two dp values, compared on the number inside them.
     *
     * `Dp` is a value class, so it never boxes into the `assertEquals` overloads that take a
     * `Float` and there is no tolerance in any of them. Every measurement in this file goes through
     * a float anyway, so the comparison is done on the number and carries a hair of slack.
     */
    private fun assertDp(expected: Dp, actual: Dp) =
        assertEquals(expected.value, actual.value, TOLERANCE)

    private fun assertDp(message: String, expected: Dp, actual: Dp) =
        assertEquals(message, expected.value, actual.value, TOLERANCE)

    companion object {
        private const val TOLERANCE = 0.01f

        /**
         * Every bound in this file is written in pixels, which is what the platform reports, and a
         * density of 1 makes a pixel a dp so the numbers in the assertions can be read against the
         * numbers in the feature. Nothing here depends on a real screen.
         */
        private val ONE_PIXEL_IS_ONE_DP = Density(1f)
    }
}
