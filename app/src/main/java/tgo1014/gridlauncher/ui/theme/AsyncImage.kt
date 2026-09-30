package tgo1014.gridlauncher.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage as CoilAsyncImage
import tgo1014.gridlauncher.domain.models.IconShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

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
 *
 * The launcher's icon shape is applied here rather than at each caller, so the tile, the folder
 * preview mosaic, the folder jump list, the album-less Now mark and the money picker all get the
 * same mask and cannot drift apart. Only app icons come through: photographs, album art, widgets and
 * hub marks are drawn elsewhere and are never masked, because a mask over a photograph is a crop
 * rather than a shape.
 */
@Composable
fun AppIconImage(model: Any?, modifier: Modifier, fill: Float = 1f, tint: Color? = null) {
    val scale = 1f / fill.coerceIn(0.35f, 1f)
    val mask = IconShape.of(LocalGlass.current.settings.iconShape).shape()
    CoilAsyncImage(
        model = model,
        contentDescription = null,
        // The mask goes on before the optical scale, so it always covers the size the caller asked
        // for and only the artwork is scaled inside it. The other way round, a low-fill icon would
        // carry its own mask down with it and sit in the middle of the tile as a shrunken badge.
        modifier = modifier.then(if (mask == null) Modifier else Modifier.clip(mask))
            .graphicsLayer(scaleX = scale, scaleY = scale),
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { appIconTint(it) },
    )
}

/** The chosen shape as a mask, or nothing at all for full bleed, which is how icons were drawn before. */
fun IconShape.shape(): Shape? = when (this) {
    IconShape.BLEED -> null
    IconShape.CIRCLE -> CircleShape
    // A little over a fifth of the width, the proportion a masked launcher icon is usually drawn at:
    // enough to read as rounded rather than as a lozenge, without eating into the artwork.
    IconShape.ROUNDED -> RoundedCornerShape(percent = 22)
    IconShape.SQUIRCLE -> SuperellipseShape()
}

/**
 * A superellipse, the shape a rounded square never quite reaches: the edge stays curved right into
 * the corner instead of meeting a straight side. Traced as a fine polygon rather than fitted with
 * Beziers so the outline and the maths behind it are the same thing - at icon sizes the segments
 * are below a pixel, and a shape whose drawing disagreed with its own hit test would be worse.
 */
private class SuperellipseShape(private val exponent: Float = 4f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val points = superellipse(size.width, size.height, exponent)
        var index = 0
        while (index + 1 < points.size) {
            if (index == 0) path.moveTo(points[index], points[index + 1])
            else path.lineTo(points[index], points[index + 1])
            index += 2
        }
        return Outline.Generic(path)
    }
}

/**
 * The outline as flat x then y pairs, [steps] + 1 of them, walking the shape once round from the
 * middle of its right edge. Kept free of Compose so the curve can be checked on its own.
 */
internal fun superellipse(width: Float, height: Float, exponent: Float, steps: Int = 48): FloatArray {
    if (width <= 0f || height <= 0f || steps < 1) return FloatArray(0)
    val power = 2f / exponent.coerceAtLeast(0.1f)
    return FloatArray((steps + 1) * 2).also { points ->
        for (step in 0..steps) {
            val angle = step.toFloat() / steps * (2f * PI.toFloat())
            val cosine = cos(angle)
            val sine = sin(angle)
            // The signed power of each term is what turns a circle into a squircle: the further from
            // an axis, the harder the curve is pulled out to the corner.
            points[step * 2] = width / 2f + width / 2f * (abs(cosine).pow(power) * if (cosine < 0f) -1f else 1f)
            points[step * 2 + 1] = height / 2f + height / 2f * (abs(sine).pow(power) * if (sine < 0f) -1f else 1f)
        }
    }
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
