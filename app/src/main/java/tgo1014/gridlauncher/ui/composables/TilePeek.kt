package tgo1014.gridlauncher.ui.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.LocalGlass
import androidx.compose.ui.unit.min

/**
 * What a tile is allowed to magnify, decided before anything is drawn.
 *
 * The flags are the launcher's own answers rather than a fresh judgement: `locked` and `quiet` are
 * the ones the tile already obeys, and `previewsHidden` is the per-app privacy switch. Keeping them
 * here is what makes the rule provable - a peek composes the very same [GridTile], so the one way
 * to show something in the overlay that the tile itself would not show is for this to say yes.
 */
internal data class PeekVisibility(
    val liveTiles: Boolean = true,
    val hub: Boolean = false,
    val group: Boolean = false,
    val widget: Boolean = false,
    val locked: Boolean = false,
    val quiet: Boolean = false,
    val previewsHidden: Boolean = false,
    val notification: Boolean = false,
    val call: Boolean = false,
    val people: Boolean = false,
    val photos: Boolean = false,
)

/**
 * Whether a double tap is allowed to peek this tile instead of opening it.
 *
 * A tile with nothing live has nothing to magnify: peeking it would open a sheet of the same icon
 * and the same label the user is already looking at, so those tiles fall through to a normal tap.
 * A group header is not a tile at all, and a widget host must not be composed twice - the peek
 * draws the same widget id a second time, which Android does not allow - so neither can be peeked.
 */
internal fun PeekVisibility.peekable(): Boolean {
    if (!liveTiles || locked || quiet || previewsHidden || group || widget) return false
    return hub || call || people || photos || notification
}

/**
 * The Windows Phone second state: one tile, larger, showing itself. Not a new screen and not a
 * summary - it is the same [GridTile] with the same colour, glyph, live text and notification
 * actions, at a size you can read from across the room, over a dimmed Start.
 *
 * Tapping outside, pressing back, or double tapping the magnified tile again all collapse it. The
 * collapse is a real animation only when motion is on: with it off the tile is simply there and
 * gone, which is the point of the switch - a flash is a visual event, not an animation.
 */
@Composable
fun TilePeek(item: GridItem, tileSettings: TileSettings, onDismiss: () -> Unit) {
    val motion = LocalGlass.current.motion
    // 0 is collapsed and 1 is magnified. With motion off both ends are the same frame, so there is
    // nothing to animate and nothing to flash.
    var expanded by remember { mutableStateOf(!motion) }
    var collapsing by remember { mutableStateOf(false) }
    fun collapse() {
        if (motion) collapsing = true else onDismiss()
    }
    val progress by animateFloatAsState(
        targetValue = if (collapsing) 0f else if (expanded) 1f else 0f,
        animationSpec = if (motion) spring(dampingRatio = .9f, stiffness = 420f) else snap(),
        label = "Tile peek",
    )
    LaunchedEffect(Unit) { expanded = true }
    LaunchedEffect(progress) { if (collapsing && progress <= 0f) onDismiss() }
    Dialog(onDismissRequest = ::collapse, properties = DialogProperties(
        // The peek owns the whole screen so it can sit in the middle of it, the way a magnified
        // tile did on the Windows Phone Start screen.
        usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true,
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .68f * progress)),
            contentAlignment = Alignment.Center) {
            val unit = min(maxWidth, maxHeight) * .54f
            GridTile(item, tileSettings = tileSettings, peekDismiss = ::collapse,
                modifier = Modifier.size(unit * item.width.coerceIn(1, 2), unit * item.height.coerceIn(1, 2))
                    .graphicsLayer { scaleX = progress; scaleY = progress; alpha = progress })
        }
    }
}
