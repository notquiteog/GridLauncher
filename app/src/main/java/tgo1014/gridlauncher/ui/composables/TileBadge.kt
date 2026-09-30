package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.ui.theme.readableInk

/**
 * A tile's notification badge and the space it claims, so both are decided in one place.
 *
 * [diameter] is the filled circle and [inset] is how far it sits from the corner, so [width] is the
 * whole footprint the badge puts on the tile, which is what a label beside it has to be held clear
 * of.
 */
internal data class TileBadgeMetrics(val diameter: Dp, val inset: Dp) {
    val width: Dp get() = diameter + inset * 2
}

/**
 * The badge for a count when the user has asked for counts as dots, which is how the reference
 * draws it: a filled circle in the tile's own ink, on the corner. One notification is the bare
 * circle; anything more is the same circle carrying the count, knocked out in whatever reads on
 * the ink, which is the reference's Outlook Mail badge. Ten or more reads "9+", the one place a
 * circle is asked to hold a number it does not have room for.
 */
internal fun tileBadgeMetrics(count: Int, expanded: Boolean): TileBadgeMetrics =
    if (count <= 1) TileBadgeMetrics(if (expanded) 12.dp else 10.dp, 8.dp)
    else TileBadgeMetrics(if (expanded) 26.dp else 22.dp, 6.dp)

/** The count as it is drawn inside the circle, or nothing at all for a single notification. */
internal fun tileBadgeNumber(count: Int): String? = when {
    count <= 1 -> null
    count > 9 -> "9+"
    else -> count.toString()
}

/**
 * How much of a tile's bottom-right corner the badge takes, so a label beside it is never run over.
 *
 * The plain count is a number on the tile rather than a shape, and it keeps exactly the room it has
 * always had: the label's own inset on top of the 26dp the bare count was already given.
 */
internal fun badgeInset(count: Int, asDot: Boolean, expanded: Boolean): Dp = when {
    !asDot -> if (expanded) 36.dp else 29.dp
    else -> tileBadgeMetrics(count, expanded).width
}

/**
 * The notification badge in a tile's bottom-right corner.
 *
 * With [asDot] this is a filled circle in the tile's own ink, sitting on the corner rather than
 * inside a pill, carrying the count when there is a count to carry. Without it this is the
 * launcher's own plain count, drawn exactly as it always was, because turning a bare number into a
 * shape is the user's setting to make and not this composable's.
 *
 * The caller passes a modifier already aligned to the corner: this owns the shape, the inset and
 * the knock-out, not where on the tile the badge sits.
 */
@Composable
fun TileBadge(
    count: Int,
    asDot: Boolean,
    expanded: Boolean,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val description = "$count notifications. Double tap to preview"
    if (asDot) {
        val metrics = tileBadgeMetrics(count, expanded)
        val number = tileBadgeNumber(count)
        Box(modifier.padding(metrics.inset).size(metrics.diameter)
            .clip(CircleShape).background(ink)
            .semantics { contentDescription = description }
            .clickable { onClick() },
            contentAlignment = Alignment.Center) {
            // Knocked out in whatever reads on the ink, so the badge reads as one shape with
            // something in it rather than two marks fighting each other.
            if (number != null) Text(number, color = readableInk(ink), fontSize = if (expanded) 14.sp else 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    } else {
        Text(if (count > 99) "99+" else count.toString(), color = ink, fontSize = if (expanded) 24.sp else 16.sp,
            modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = description }
                .clickable { onClick() }.padding(6.dp))
    }
}
