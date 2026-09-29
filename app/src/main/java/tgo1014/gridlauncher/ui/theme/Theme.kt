package tgo1014.gridlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The base scheme for anything outside Start, such as the update prompt. Start installs its own
 * accent-driven scheme on top, so a wallpaper-derived accent reaches the whole launcher.
 */
@Composable
fun GridLauncherTheme(accent: Color = Color(0xFF0078D7), dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
        .copy(primary = accent, background = if (dark) Color.Black else Color.White, surface = if (dark) Color(0xFF141414) else Color(0xFFFAFAFA))
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
