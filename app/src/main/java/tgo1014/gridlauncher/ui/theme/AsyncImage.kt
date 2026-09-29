package tgo1014.gridlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage as CoilAsyncImage

/**
 * Renders an app icon in a single flat color, the way themed icons look. Each output row keeps only
 * one channel, so the whole artwork collapses to that color while the alpha still decides the shape.
 */
fun appIconTint(color: Color): ColorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0f, 0f, 0f, 0f, color.red,
    0f, 0f, 0f, 0f, color.green,
    0f, 0f, 0f, 0f, color.blue,
    0f, 0f, 0f, 0f, color.alpha,
)))

/**
 * Loads the launcher's own cached icon. [fill] is how much of the icon's canvas the app actually
 * paints, so an icon drawn small inside a large frame is scaled back to a consistent size instead of
 * looking lost in its tile. The artwork itself is never redrawn or recoloured unless [tint] is set.
 */
@Composable
fun AppIconImage(model: Any?, modifier: Modifier, fill: Float = 1f, tint: Color? = null) {
    val scale = 1f / fill.coerceIn(0.35f, 1f)
    CoilAsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale),
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { appIconTint(it) },
    )
}

@Composable
fun AsyncImage(
    model: Any?, modifier: Modifier = Modifier, contentDescription: String? = null,
    alignment: Alignment = Alignment.Center, contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f, colorFilter: ColorFilter? = null,
) = CoilAsyncImage(
    model = model, contentDescription = contentDescription, modifier = modifier, alignment = alignment,
    contentScale = contentScale, alpha = alpha, colorFilter = colorFilter,
)
