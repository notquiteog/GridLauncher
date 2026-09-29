package tgo1014.gridlauncher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tgo1014.gridlauncher.ui.theme.LocalGlass

/**
 * Wallpaper parallax. The grid scrolls a little faster than the wallpaper behind it, and the depth
 * settles as you reach the top, so Start reads as a window onto the wallpaper rather than a flat
 * image pinned behind tiles. Respects the system animation scale through [LocalGlass].
 */
@Composable
fun Modifier.wallpaperParallax(maxOffset: Dp = 26.dp, strength: Float = 0.18f): Modifier {
    val glass = LocalGlass.current
    val density = LocalDensity.current
    val range = remember(maxOffset, density) { with(density) { maxOffset.toPx() } }
    var offset by remember { mutableFloatStateOf(0f) }
    return this
        .graphicsLayer { translationY = if (glass.motion) offset * strength else 0f }
        .nestedScroll(object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) offset = (offset - consumed.y).coerceIn(0f, range)
                return Offset.Zero
            }
        })
}
