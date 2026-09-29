package tgo1014.gridlauncher.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.ui.theme.LocalGlass
import java.text.DateFormat
import java.util.Date

/**
 * The semi-panoramic Start header: large when you are at the top, shrinking and fading as the
 * grid scrolls, the way the Windows Phone Start header receded.
 */
@Composable
fun StartHeader(visible: Boolean, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    var scrolledPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val fraction = with(density) { (scrolledPx / 260.dp.toPx()).coerceIn(0f, 1f) }
    val size by animateFloatAsState(30f - 16f * fraction, label = "Header size")
    val alpha by animateFloatAsState(1f - 0.55f * fraction, label = "Header fade")
    // A medium date keeps the header on one line on any display width.
    // Re-formats when the day changes, so an open launcher never shows yesterday.
    var day by remember { mutableStateOf(java.time.LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { day = java.time.LocalDate.now(); delay(60_000) } }
    val date = remember(day) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, java.util.Locale.getDefault())
            .format(Date.from(day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()))
    }
    // Reduce motion stops the animation, it does not remove the header.
    AnimatedVisibility(visible, enter = if (glass.motion) fadeIn() else androidx.compose.animation.EnterTransition.None,
        exit = if (glass.motion) fadeOut() else androidx.compose.animation.ExitTransition.None) {
        Column(modifier.nestedScroll(headerScroll { scrolledPx = it }).padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
            Text(date, color = glass.ink, fontSize = size.sp, fontWeight = FontWeight.Light,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(alpha))
            Box(Modifier.padding(top = 6.dp).width((72 * (1f - fraction * .5f)).dp).height(3.dp)
                .background(glass.accent.copy(alpha = .85f * alpha)))
        }
    }
}

private fun headerScroll(onScrolled: (Float) -> Unit) = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput || source == NestedScrollSource.SideEffect) onScrolled(consumed.y)
        return Offset.Zero
    }
}
