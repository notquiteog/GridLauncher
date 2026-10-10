package tgo1014.gridlauncher.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/** WCAG relative luminance. */
private fun luminance(color: Color): Double {
    fun channel(value: Float): Double = if (value <= 0.03928f) (value / 12.92f).toDouble() else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4)
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

private fun contrast(background: Color, foreground: Color): Double {
    val a = luminance(background); val b = luminance(foreground)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/**
 * Whichever of black or white reads better on [background]. Uses the measured ratio rather than a
 * luminance cut-off, so mid-tones get the label colour that actually passes.
 */
fun readableInk(background: Color, light: Color = Color.White, dark: Color = Color.Black): Color =
    if (contrast(background, light) >= contrast(background, dark)) light else dark

fun contrastRatio(background: Color, foreground: Color): Double = contrast(background, foreground)
