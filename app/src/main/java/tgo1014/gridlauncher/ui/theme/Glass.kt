package tgo1014.gridlauncher.ui.theme

import android.app.KeyguardManager
import android.content.*
import android.os.PowerManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.domain.models.TileSettings

data class GlassEnvironment(val settings: TileSettings = TileSettings(), val accent: Color = Color(0xFF86BCFF), val lowPower: Boolean = false, val locked: Boolean = true) {
    val motion get() = !lowPower && !settings.reduceMotion && android.animation.ValueAnimator.areAnimatorsEnabled()
    val ink get() = if (settings.darkTheme) Color.White else Color(0xFF142C42)
    val solid get() = settings.glassFinish == "solid" || lowPower
}
val LocalGlass = staticCompositionLocalOf { GlassEnvironment() }

@Composable
fun rememberGlass(settings: TileSettings): GlassEnvironment {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var power by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(true) }
    fun refresh() { power = context.getSystemService(PowerManager::class.java).isPowerSaveMode; locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked }
    DisposableEffect(context, lifecycle) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, intent: Intent?) { refresh(); if (intent?.action == Intent.ACTION_SCREEN_OFF) locked = true } }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer); refresh()
        onDispose { context.unregisterReceiver(receiver); lifecycle.removeObserver(observer) }
    }
    val tint by produceState<Color?>(null, settings.wallpaperPath, settings.wallpaperTint) {
        value = if (!settings.wallpaperTint || settings.wallpaperPath == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = android.graphics.BitmapFactory.decodeFile(settings.wallpaperPath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 32 }) ?: return@runCatching null
                var r = 0L; var g = 0L; var b = 0L; var count = 0
                for (x in 0 until bitmap.width step 4) for (y in 0 until bitmap.height step 4) { val p = bitmap.getPixel(x,y); r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p); count++ }
                bitmap.recycle()
                if (count == 0) null else Color((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
            }.getOrNull()
        }
    }
    return GlassEnvironment(settings, tint ?: Color(settings.accentColor), power, locked)
}

@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    Canvas(modifier.fillMaxSize()) {
        val dark = glass.settings.darkTheme
        drawRect(brush = Brush.linearGradient(if (dark) listOf(Color(0xFF193B55), Color(0xFF0D192D), Color(0xFF493653)) else listOf(Color(0xFFEAF6FF), Color(0xFFC6DDED), Color(0xFFE5DCEF))))
        drawCircle(brush = Brush.radialGradient(listOf(glass.accent.copy(alpha = .24f), Color.Transparent), center = Offset(size.width * .05f, size.height * .15f), radius = size.height * .6f), radius = size.height * .6f, center = Offset(size.width * .05f, size.height * .15f))
        drawOval(brush = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .13f))), topLeft = Offset(size.width * .4f, -size.height * .3f), size = androidx.compose.ui.geometry.Size(size.width * .9f, size.height * 1.5f))
    }
}

@Composable
fun Modifier.glassSurface(haze: HazeState? = null): Modifier {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(0.dp)
    val base = if (glass.settings.darkTheme) Color(0xFF1C344C) else Color(0xFFE9F2FA)
    var result = clip(shape)
    if (!glass.solid && haze != null) result = result.hazeChild(haze, style = HazeStyle(backgroundColor = base,
        tint = HazeTint.Color(base.copy(alpha = if (glass.settings.glassFinish == "clear") .45f else .72f)), blurRadius = if (glass.settings.glassFinish == "clear") 12.dp else 28.dp, noiseFactor = .015f))
    result = result.background(if (glass.solid) base else base.copy(alpha = if (haze == null) .84f else .18f))
        .background(Brush.linearGradient(listOf(glass.accent.copy(alpha = .18f), Color.Transparent, glass.accent.copy(alpha = .07f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (glass.settings.darkTheme) .32f else .8f), Color.White.copy(alpha = .03f), glass.accent.copy(alpha = .13f))), shape)
    return result
}

/** Brief perspective tilt when content changes, paused by system and user motion settings. */
@Composable
fun TileTurn(key: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glass = LocalGlass.current
    var tilt by remember { mutableFloatStateOf(0f) }
    var previous by remember { mutableStateOf(key) }
    LaunchedEffect(key, glass.motion, glass.settings.isTileFlipEnabled) {
        if (previous != key && glass.motion && glass.settings.isTileFlipEnabled) { tilt = -7f; kotlinx.coroutines.delay(100); tilt = 0f }
        previous = key
    }
    val rotation by animateFloatAsState(if (glass.motion) tilt else 0f, tween(220), label = "Tile tilt")
    Box(modifier.graphicsLayer { rotationX = rotation; cameraDistance = 14 * density }) { content() }
}
