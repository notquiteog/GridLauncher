package tgo1014.gridlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shape of the launcher window right now, which is what decides whether Start and the app list
 * are two pages you swipe between or two panes you can see at once.
 */
@Immutable
sealed interface WindowPosture {
    /** A phone-sized window. Start and the app list are separate pages in the pager, exactly as they
     * have always been, and nothing about that path is allowed to depend on anything but the width. */
    data object Compact : WindowPosture

    /** A window wide enough to show both at once: Start on the left, the app list on the right. */
    data object Expanded : WindowPosture
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
 * The posture of a window [windowWidth] wide.
 *
 * The width is measured against the launcher's own window rather than the screen behind it, so a
 * narrow window on a wide screen - a split-screen half, a freeform window - is still the phone
 * experience it has always been.
 *
 * A note on what this does *not* know. The platform's real answer to this question is
 * `WindowInfoTracker` and `FoldingFeature` from `androidx.window`: they report the hinge itself, its
 * bounds, whether it occludes the display and whether it is flat or half-open, which is what a
 * foldable ought to be laid out around. That artifact is not a dependency of this app, and it does
 * not resolve offline, so no hinge is reported here and the split is a plain midpoint of the window.
 * On a device with a physical hinge in the middle of the panel, which is where the divider goes,
 * the two panes meet at that midpoint: the divider lands on the hinge rather than cutting a pane
 * in half beside it. Making that a guarantee rather than a coincidence means reading the real
 * `FoldingFeature` bounds and insetting the panes, which is a change to this function alone.
 */
@Composable
fun postureFor(windowWidth: Dp): WindowPosture = remember(windowWidth) {
    if (windowWidth >= ExpandedPostureWidth) WindowPosture.Expanded else WindowPosture.Compact
}
