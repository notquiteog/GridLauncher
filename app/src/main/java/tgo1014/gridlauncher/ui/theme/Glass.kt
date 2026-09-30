package tgo1014.gridlauncher.ui.theme

import android.app.KeyguardManager
import android.content.*
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.domain.models.TileSettings

data class GlassEnvironment(
    val settings: TileSettings = TileSettings(), val accent: Color = Color(0xFF86BCFF),
    val lowPower: Boolean = false, val locked: Boolean = true, val quiet: Boolean = false,
    /** Android's own animation scale and contrast preference, read from Settings.Global and AccessibilityManager. */
    val animationsEnabled: Boolean = android.animation.ValueAnimator.areAnimatorsEnabled(),
    val highContrast: Boolean = false,
) {
    // Reduce motion, battery saver, the system animation scale and the a11y switch all say the same thing.
    val motion get() = !lowPower && !settings.reduceMotion && animationsEnabled
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
    /**
     * Android's own wallpaper is used when the user has not picked one, including a live wallpaper's
     * current frame, so Start looks the same as the rest of the system rather than only its own choice.
     */
    val systemWallpaper by produceState<java.io.File?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val id = "wallpaper"
                if (context.packageManager.resolveContentProvider("content://$id", 0) == null) null
                else context.contentResolver.openInputStream(android.net.Uri.parse("content://$id"))?.use {
                    val file = java.io.File(context.cacheDir, "system-wallpaper.png")
                    if (!file.exists()) it.copyTo(file.outputStream())
                    file.takeIf { f -> f.length() > 0 }
                }
            }.getOrNull()
        }
    }
    val effectiveWallpaper: java.io.File? = settings.wallpaperPath?.let { java.io.File(it) } ?: systemWallpaper
    val tint by produceState<Color?>(null, effectiveWallpaper, settings.wallpaperTint) {
        value = if (!settings.wallpaperTint || effectiveWallpaper == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = android.graphics.BitmapFactory.decodeFile(effectiveWallpaper.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 32 }) ?: return@runCatching null
                var r = 0L; var g = 0L; var b = 0L; var count = 0
                for (x in 0 until bitmap.width step 4) for (y in 0 until bitmap.height step 4) { val p = bitmap.getPixel(x,y); r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p); count++ }
                bitmap.recycle()
                if (count == 0) null else Color((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
            }.getOrNull()
        }
    }
    // Re-evaluated each minute so a quiet-hours boundary takes effect without a restart.
    val quiet by produceState(tgo1014.gridlauncher.live.QuietHours.active(settings), settings.meetingMode, settings.quietHoursEnabled, settings.quietStartHour, settings.quietEndHour) {
        while (true) {
            value = tgo1014.gridlauncher.live.QuietHours.active(settings)
            // Keep Android's own Do Not Disturb in step, not just the settings sheet.
            tgo1014.gridlauncher.live.QuietHours.apply(context, settings)
            delay(30_000)
        }
    }
    // Android's own preferences: a zeroed animation scale and the high-contrast a11y switch both
    // mean the user has asked for less movement or more contrast. These change rarely, so they are
    // read on resume rather than on a timer, which keeps the launcher off the critical path.
    fun readSystemPrefs(): Pair<Float, Boolean> = Pair(
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f),
        runCatching {
            android.provider.Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1 ||
                android.provider.Settings.Global.getFloat(context.contentResolver, "high_text_contrast", 0f) > 0f
        }.getOrDefault(false),
    )
    var systemPrefs by remember { mutableStateOf(readSystemPrefs()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) systemPrefs = readSystemPrefs() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return GlassEnvironment(settings, tint ?: Color(settings.accentColor), power, locked, quiet,
        animationsEnabled = android.animation.ValueAnimator.areAnimatorsEnabled() && systemPrefs.first > 0f,
        highContrast = systemPrefs.second)
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
    val finish = glass.settings.glassFinish
    // Acrylic is the sharpest, most transparent material: heavy blur, no grain, a specular sheen.
    val blur = when (finish) { "clear" -> 12.dp; "acrylic" -> 52.dp; else -> 28.dp }
    val tint = when (finish) { "clear" -> .45f; "acrylic" -> .26f; else -> .72f }
    val acrylic = finish == "acrylic" && !glass.solid
    var result = clip(shape)
    if (!glass.solid && haze != null) result = result.hazeChild(haze, style = HazeStyle(backgroundColor = base,
        tint = HazeTint.Color(base.copy(alpha = tint)), blurRadius = blur, noiseFactor = if (acrylic) 0f else .015f))
    result = result.background(if (glass.solid) base else base.copy(alpha = if (haze == null) .84f else .14f))
        .background(Brush.linearGradient(listOf(glass.accent.copy(alpha = .18f), Color.Transparent, glass.accent.copy(alpha = .07f))))
    if (acrylic) result = result.background(Brush.linearGradient(listOf(Color.White.copy(alpha = .17f), Color.Transparent, Color.White.copy(alpha = .05f))))
    result = result.border(if (acrylic) 1.5.dp else 1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (glass.settings.darkTheme) .32f else .8f), Color.White.copy(alpha = .03f), glass.accent.copy(alpha = .13f))), shape)
    return result
}

/**
 * The Windows Phone live-tile flip: when a tile's content changes it turns away on its vertical
 * axis and comes back, rather than cross-fading in place. Paused by the system animation scale and
 * by the launcher's own Reduce motion switch.
 */
@Composable
fun TileTurn(key: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glass = LocalGlass.current
    val flip = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(key) }
    LaunchedEffect(key, glass.motion, glass.settings.isTileFlipEnabled) {
        if (previous != key && glass.motion && glass.settings.isTileFlipEnabled) {
            flip.snapTo(0f); flip.animateTo(-180f, tween(150, easing = FastOutSlowInEasing))
            flip.snapTo(180f); flip.animateTo(360f, tween(180, easing = FastOutSlowInEasing))
        }
        previous = key
    }
    Box(modifier.graphicsLayer {
        rotationY = flip.value; cameraDistance = 12 * density
        // Past 90 degrees the tile is showing its back, so hide it rather than draw it mirrored.
        // At rest, and at the end of a turn, the angle is a whole turn and the tile faces the user.
        val turned = ((flip.value % 360) + 360) % 360
        alpha = if (turned > 90f && turned < 270f) 0f else 1f
    }) { content() }
}
