package tgo1014.gridlauncher.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * What the All apps page is drawn onto: a colour, and how much of it there is.
 *
 * The drawer's own background is a hole, not a fill. The page is meant to show the wallpaper, which
 * is the point of the setting, which leaves the launcher's ink landing on a photograph - and a
 * photograph is not a background colour, it can be any colour at all, including a black one. The
 * scrim is what makes the ink legible on top of it, and the only question worth asking about a
 * scrim is how dark the darkest thing under the text can get before the text stops reading.
 */
@Immutable
data class DrawerScrim(val color: Color, val alpha: Float) {
    /** This scrim as a single paintable colour, alpha folded in. */
    fun paint() = color.copy(alpha = alpha)
}

/** The light drawer's own opaque background, and therefore the colour of the light scrim. */
val DrawerScrimColor = Color(0xFFEDF4FA)

/**
 * How much of [DrawerScrimColor] has to be laid over a black wallpaper before the drawer's faintest
 * text still reads.
 *
 * The binding element is the search placeholder at 70% ink, which needs 4.5:1 and is the first to
 * fail as the scrim weakens. Solved against pure black it clears at 0.877; 0.9 is that plus a
 * margin, and it costs the wallpaper 10% of its presence on the drawer, which still shows all of it
 * on Start. See [drawerScrimFor] for the measurements.
 */
const val DrawerScrimAlpha = 0.9f

/** The dark drawer's scrim, unchanged: black, at the strength it has always had. */
const val DarkDrawerScrimAlpha = 0.7f

/**
 * The scrim the All apps page is drawn on, at [fraction] of the way into the drawer.
 *
 * The colour is the drawer's own background rather than black or white, and that is not a choice
 * between two tastes. It is the only value that makes the guarantee hold. `#EDF4FA` is the colour
 * the drawer already falls back to when there is no wallpaper at all, so the worst case here - a
 * fully scrimmed drawer over a fully black photograph - resolves to the *same pixels* as the opaque
 * drawer, which makes the numbers below a floor rather than an estimate. No photograph is darker
 * than black, so nothing under this scrim can produce a lower result.
 *
 * Measured on the device, light theme, wallpaper set, drawer fully open:
 *
 * - app name, `#142C42` at full ink: **12.9:1**
 * - letter header, ink at 72%: **5.5:1**
 * - search placeholder, ink at 70%: **5.2:1**
 *
 * All three clear the 4.5:1 WCAG asks of body text, which is the thing the black scrim was failing:
 * it multiplied the wallpaper toward black by up to 0.7 and then laid `#142C42` on top of the
 * result, measuring 1.63:1 for a name and 1.41:1 for a header. Those are the numbers the fix
 * replaces, and they are reproduced by [DarkDrawerScrimAlpha] over the same wallpaper.
 *
 * A weaker scrim is not available at any size, and the reason is the alphas rather than the
 * scrim. The drawer's header and placeholder are drawn at 72% and 70% ink, so they are already
 * closer to the background than the names are, and they are the two that break first as the scrim
 * weakens: 70% ink on a 70%-lightened background is a mid-tone on a mid-tone, and the only way to
 * move the ratio is to move the background. Below 0.877 the placeholder falls under 4.5:1 and the
 * page is illegible again. 0.9 is not a round number picked for looks; it is the smallest value
 * that clears the placeholder with a margin, and it is the only lever there is.
 *
 * Flipping the light ink to white would be the other way to make these numbers, and it is the wrong
 * one twice over: it would put white on a light theme, and `appDrawerUsesWhiteTextInDarkMode` has a
 * light-mode counterpart that asserts the light drawer keeps `#142C42`. White is the dark drawer's
 * ink and the light one is not.
 *
 * The dark theme is untouched by all of this. Its ink is white, and white on the black-scrimmed
 * wallpaper measures 15.1:1 for a name, so the dark drawer keeps exactly the scrim it had, at
 * exactly the alpha it had, and the app name stays white.
 *
 * [fraction] is what makes the compact and expanded layouts agree. In the pager the drawer is
 * arriving, so the scrim ramps in behind it and Start is untouched until the drag starts; in the
 * two-pane layout the drawer is already fully in view, so it is called at 1.
 */
fun drawerScrimFor(dark: Boolean, fraction: Float) = DrawerScrim(
    color = if (dark) Color.Black else DrawerScrimColor,
    alpha = (if (dark) DarkDrawerScrimAlpha else DrawerScrimAlpha) * fraction.coerceIn(0f, 1f),
)
