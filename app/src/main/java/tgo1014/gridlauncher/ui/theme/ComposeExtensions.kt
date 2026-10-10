package tgo1014.gridlauncher.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImagePainter

operator fun PaddingValues.plus(that: PaddingValues): PaddingValues = object : PaddingValues {
    override fun calculateBottomPadding(): Dp =
        this@plus.calculateBottomPadding() + that.calculateBottomPadding()

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        this@plus.calculateLeftPadding(layoutDirection) + that.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        this@plus.calculateRightPadding(layoutDirection) + that.calculateRightPadding(
            layoutDirection
        )

    override fun calculateTopPadding(): Dp =
        this@plus.calculateTopPadding() + that.calculateTopPadding()
}

fun Modifier.modifyIf(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier =
    if (condition) this.then(modifier(Modifier)) else this

fun Modifier.conditional(
    condition: Boolean,
    ifTrue: Modifier.() -> Modifier,
    ifFalse: (Modifier.() -> Modifier)? = null
): Modifier {
    return if (condition) {
        then(ifTrue(Modifier))
    } else if (ifFalse != null) {
        then(ifFalse(Modifier))
    } else {
        this
    }
}


@Composable
fun AsyncImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    transform: (AsyncImagePainter.State) -> AsyncImagePainter.State = AsyncImagePainter.DefaultTransform,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
) = coil3.compose.AsyncImage(
    model = model,
    contentDescription = contentDescription,
    modifier,
    transform,
    onState,
    alignment,
    contentScale,
    alpha,
    colorFilter,
    filterQuality
)

/**
 * Makes paging between Start and All apps deliberate: a horizontal wobble is swallowed until the
 * finger has travelled [fraction] of the page, so the screen only changes on a real swipe.
 */
fun Modifier.pageSwipeGuard(pageWidth: Dp, fraction: Float = 0.35f): Modifier = composed {
    val density = LocalDensity.current
    val threshold = remember(pageWidth, fraction) { with(density) { pageWidth.toPx() * fraction } }
    // A plain holder, not snapshot state: this only gates the gesture and must not recompose
    // anything, or every pointer move would rebuild the whole screen.
    val gesture = remember { IntArray(1) }
    nestedScroll(object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.x == 0f) return Offset.Zero
            val travelled = java.lang.Float.intBitsToFloat(gesture[0])
            val remaining = threshold - abs(travelled)
            if (abs(available.x) <= remaining.coerceAtLeast(0f)) {
                gesture[0] = java.lang.Float.floatToRawIntBits(travelled + available.x)
                return Offset(available.x, 0f)
            }
            gesture[0] = 0
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.x == 0f && available.x == 0f) gesture[0] = 0
            return Offset.Zero
        }
    })
}
