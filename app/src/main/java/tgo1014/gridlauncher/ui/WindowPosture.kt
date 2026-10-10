package tgo1014.gridlauncher.ui

import android.app.Activity
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * The shape of the launcher window right now, which is what decides whether Start and the app list
 * are two pages you swipe between or two panes you can see at once.
 */
@Immutable
sealed interface WindowPosture {
    /** A phone-sized window. Start and the app list are separate pages in the pager, exactly as they
     * have always been, and nothing about that path is allowed to depend on anything but the width. */
    data object Compact : WindowPosture

    /** A window wide enough to show both at once: Start in [split]'s first pane, the app list in
     * its second, with whatever sits between them being the seam itself. */
    data class Expanded(val split: PaneSplit) : WindowPosture
}

/**
 * Where the two panes of an [WindowPosture.Expanded] window are, and what is between them.
 *
 * The three numbers are measured along the window's own axis, so a vertical hinge gives left,
 * middle, right and a horizontal one gives top, middle, bottom. [divider] is the width of the
 * hardware seam and may be zero on a fold that is a crease rather than a gap, in which case the
 * panes meet and the fold is the only thing between them.
 */
@Immutable
data class PaneSplit(
    val start: Dp,
    val divider: Dp,
    val appList: Dp,
    /** True when the hinge runs across the window, so the panes are stacked rather than side by side. */
    val stacked: Boolean,
    /** The hinge the split was taken from, or null when nothing reported one and the split is a
     * plain midpoint of the window. */
    val hinge: Hinge?,
)

/**
 * A fold in the window, as the platform describes it, with the pixel bounds already in dp so the
 * layout decisions below are pure arithmetic and can be checked without a foldable.
 *
 * [flat] is a single continuous surface bent along the fold - a flexible display opened out, with a
 * crease down it - rather than two panels with a gap between them. That is the difference between a
 * seam content may cross and one it may not, and it is why [PaneSplit.divider] can be zero.
 */
@Immutable
data class Hinge(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp,
    /** True when the fold runs top to bottom and the two panes are therefore side by side. */
    val vertical: Boolean,
    /** True when the fold is a hardware gap or a separating hinge: nothing may be drawn across it. */
    val separating: Boolean,
    val flat: Boolean,
) {
    val width: Dp get() = right - left
    val height: Dp get() = bottom - top
}

/**
 * The width at which a window stops being a phone window.
 *
 * This is the same 840dp the rest of the code has always used, and it is deliberately not derived
 * from the device: it is a property of the window you are looking at. A desktop-mode window or a
 * split-screen half of a tablet is wide too, and Continuum gave a phone the same two-pane shell at
 * the same width, so the number is the rule rather than the device class behind it.
 */
val ExpandedPostureWidth: Dp = 840.dp

/**
 * The smallest a pane may be before a fold is not worth splitting at.
 *
 * This is measured along the axis the panes run on, so it is a width for a vertical fold and a
 * height for a horizontal one, and it is the drawer's own content that sets it: a row is a 60dp
 * icon, a 16dp gap and a name, inside the page's 20dp padding on each side. At 240dp that leaves
 * 124dp of name, which is several words before anything has to ellipsise.
 *
 * A fold nearer an edge than this is not a two-pane window, so it is declined and the even split is
 * used instead. The window is still a wide one either way; what is refused is cutting a column of
 * app names down to a sliver because the seam happens to be off centre.
 */
val MinimumPaneExtent: Dp = 240.dp

/**
 * The posture of a window [windowWidth] wide by [windowHeight] high, with [hinge] describing the
 * fold in it or null when the platform reported none.
 *
 * The width decides whether this window shows two panes at all, and it is the same 840dp test as
 * before: a phone-sized window is the pager, and no fold can talk it out of that. A fold decides
 * only *where* the seam goes, and only once the window is already two-pane. That order is the whole
 * design, and it is the conservative one - the phone layout is the thing most worth protecting, and
 * a book-mode fold at 411dp reports a seam hard against an edge that would split a 5dp pane if the
 * fold were allowed to overrule the width.
 *
 * The cost of that order is stated rather than hidden: a narrow window with a *horizontal* hinge -
 * a half-open clamshell on its inner screen - keeps the pager and does not inset the page away from
 * the fold. Insulating every window from every fold is the alternative, and it is the one that would
 * put a seam across the launcher on a device behaving like a phone in every other respect. The
 * two-pane window, which is the one wide enough to have a seam worth arguing about, is fully
 * insulated below.
 */
fun postureFor(windowWidth: Dp, windowHeight: Dp, hinge: Hinge?): WindowPosture {
    if (windowWidth < ExpandedPostureWidth) return WindowPosture.Compact
    return WindowPosture.Expanded(hingeSplit(windowWidth, windowHeight, hinge) ?: midpointSplit(windowWidth))
}

/**
 * An even split down the middle, for a window whose fold was not worth splitting at.
 *
 * The divider keeps the default width a `VerticalDivider` has always drawn rather than going to
 * zero, because a zero-width divider would steal half a pixel from each pane and shift the whole
 * grid: on a window that reports no fold this layout has to land on the same pixels it landed on
 * before any of this was fold-aware, and a 1dp line is part of what that was.
 */
private fun midpointSplit(windowWidth: Dp) = PaneSplit(
    start = windowWidth / 2, divider = DividerDefaults.Thickness, appList = windowWidth / 2,
    stacked = false, hinge = null,
)

/**
 * The split a fold demands, or null when there is no fold or the fold cannot carry two panes.
 *
 * The rule is one sentence: the divider goes on the hinge, along the hinge's own axis, and each
 * pane is inset to the hinge's bounds. A vertical hinge therefore gives Start the panel to its left
 * and the app list the panel to its right, and a horizontal one - the tabletop posture, where the
 * seam runs the other way and a left-right split would run the divider straight along it - gives
 * Start the panel above and the app list the panel below. Either way neither pane's content nor the
 * divider can land on the seam, which is the entire reason to ask the platform where the seam is
 * rather than measuring the window and halving it.
 *
 * A fold that is only a crease - [Hinge.flat] and not [Hinge.separating] - is a line on one
 * continuous surface rather than a hole, and the layout is chosen for that rather than by width: the
 * panes are allowed to meet, so the divider is zero and the crease is the only mark between them.
 * A hinge with a real gap keeps its full width as the divider, because that gap is already there
 * and no line needs to be drawn over it.
 */
private fun hingeSplit(windowWidth: Dp, windowHeight: Dp, hinge: Hinge?): PaneSplit? {
    if (hinge == null) return null
    val start: Dp
    val appList: Dp
    val thickness: Dp
    if (hinge.vertical) {
        start = hinge.left
        appList = windowWidth - hinge.right
        thickness = hinge.width
    } else {
        start = hinge.top
        appList = windowHeight - hinge.bottom
        thickness = hinge.height
    }
    if (start < MinimumPaneExtent || appList < MinimumPaneExtent) return null
    val crease = hinge.flat && !hinge.separating
    return PaneSplit(
        start = start,
        divider = if (crease) 0.dp else thickness,
        appList = appList,
        stacked = !hinge.vertical,
        hinge = hinge,
    )
}

/**
 * The fold in the window [windowWidth] by [windowHeight], or null when the platform reported none.
 *
 * `androidx.window` is the only source of this. It is a flow off the window manager rather than a
 * measurement taken once, because the answer changes while the launcher is running: unfolding a
 * device sends a new [WindowLayoutInfo], and so does rotating one. It is a flow of the platform's
 * own answer rather than a width compared against a table, which is the whole difference: the
 * platform reports where the seam is and what it does, not merely that the window is large.
 */
@Composable
fun rememberHinge(windowWidth: Dp, windowHeight: Dp, activity: Activity?): Hinge? {
    val layoutInfo by (activity?.let { WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) } ?: emptyFlow())
        .map { it.foldingHinge() }
        .collectAsStateWithLifecycle(initialValue = null)
    val density = LocalDensity.current
    val hinge = layoutInfo ?: return null
    return hinge.toHinge(density).clampTo(windowWidth, windowHeight)
}

/**
 * The same fold in dp, at [density].
 *
 * The platform counts pixels and the layout counts dp, and the conversion is a pure function of the
 * density rather than of anything measured, so it is pulled out here and checked at a density of 1
 * where a pixel and a dp are the same number and the split arithmetic underneath can be read off
 * the test directly.
 */
internal fun PixelHinge.toHinge(density: Density) = with(density) {
    Hinge(left.toDp(), top.toDp(), right.toDp(), bottom.toDp(), vertical, separating, flat)
}

/** The first fold in this window, in pixels, or null if the platform reported no fold. */
private fun WindowLayoutInfo.foldingHinge(): PixelHinge? {
    val fold = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() ?: return null
    val rect = fold.bounds
    return hingeFrom(
        orientation = fold.orientation, occlusionType = fold.occlusionType, state = fold.state,
        isSeparating = fold.isSeparating,
        left = rect.left.toFloat(), top = rect.top.toFloat(),
        right = rect.right.toFloat(), bottom = rect.bottom.toFloat(),
    )
}

/** A fold in the window as the platform reports it, in the window's own pixels. */
@Immutable
internal data class PixelHinge(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val vertical: Boolean,
    val separating: Boolean,
    val flat: Boolean,
)

/**
 * The fold as a [PixelHinge], from the values `androidx.window` reports.
 *
 * The bounds arrive as raw numbers rather than a `Rect` so that this half of the decision - which
 * features are worth laying out around, which axis they run along, and what counts as a seam - can
 * be checked on a plain JVM with synthetic values, on a machine that has never unfolded anything.
 * Only the four numbers above come off the platform.
 *
 * A feature that is a single point - no width and no height - is not a seam and is declined: there
 * is nothing to avoid and nothing to put a divider on. A crease, which has no width but does run
 * the length of the window, is kept, because that is exactly the case where the panes should meet.
 */
internal fun hingeFrom(
    orientation: FoldingFeature.Orientation,
    occlusionType: FoldingFeature.OcclusionType,
    state: FoldingFeature.State,
    isSeparating: Boolean,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): PixelHinge? {
    if (right - left <= 0f && bottom - top <= 0f) return null
    return PixelHinge(
        left = left, top = top, right = right, bottom = bottom,
        vertical = orientation == FoldingFeature.Orientation.VERTICAL,
        separating = isSeparating || occlusionType == FoldingFeature.OcclusionType.FULL,
        flat = state == FoldingFeature.State.FLAT,
    )
}

/**
 * The fold trimmed to the window it is in.
 *
 * The platform reports the fold in window coordinates, but a fold that is mostly off the edge of
 * the window the launcher actually occupies - a book-mode device on its outer screen, say - would
 * otherwise be read as leaving a negative pane and split the layout down a line that is not there.
 * Clamping cannot invent a hinge that is not reported, only stop a reported one from describing
 * pixels this window does not have.
 */
private fun Hinge.clampTo(windowWidth: Dp, windowHeight: Dp): Hinge = copy(
    left = left.coerceIn(0.dp, windowWidth), right = right.coerceIn(0.dp, windowWidth),
    top = top.coerceIn(0.dp, windowHeight), bottom = bottom.coerceIn(0.dp, windowHeight),
)
