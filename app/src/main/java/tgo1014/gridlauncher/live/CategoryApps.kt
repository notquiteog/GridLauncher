package tgo1014.gridlauncher.live

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.domain.AppIconManager
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.Icon
import java.util.concurrent.ConcurrentHashMap

/**
 * Which app actually answers a board category on this device.
 *
 * There is no provider contract to bind to here, so a category is resolved twice: first by asking
 * the platform which apps handle that category's own intent, and then - when the device says
 * nothing about it - by a curated list of well-known packages. Either way the answer is an app the
 * user really has installed, and null when there is none, so the board leaves that card out rather
 * than showing an empty one.
 *
 * Package visibility is why the curated list matters as much as the query does: a query only ever
 * returns apps this launcher is allowed to see, which on a modern release is the apps with a
 * launcher entry plus whatever the manifest asks about.
 */
enum class NowCategory(val label: String) {
    NEWS("News"),
    WEATHER("Weather"),
    MAPS("Maps"),
    MEDIA("Music"),
}

/** The platform's own action for a category, where one exists at all. */
private fun NowCategory.intent(): Intent? = when (this) {
    // Every maps app answers the geo: scheme, and the media action is one the manifest already asks about.
    NowCategory.MAPS -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
    NowCategory.MEDIA -> Intent("android.intent.action.MEDIA_PLAYER")
    // News and weather have no platform action, and another company's content provider is not a contract.
    NowCategory.NEWS, NowCategory.WEATHER -> null
}

/** Well-known packages, best first. A device with none of them has no app for that category. */
private val wellKnown: Map<NowCategory, List<String>> = mapOf(
    NowCategory.NEWS to listOf(
        "com.google.android.apps.magazine", "com.google.android.apps.news", "com.bbc.mobile.news.bbccnews",
        "com.reuters.rc", "com.cnn.android", "com.nytimes.mobile", "de.m.tagesschau.android.app",
    ),
    NowCategory.WEATHER to listOf(
        "com.android.weather", "com.google.android.apps.weather", "com.accuweather.android",
        "com.weather2.android.weatherchannel", "com.more.fun", "org.mozilla.weather",
    ),
    NowCategory.MAPS to listOf(
        "com.google.android.apps.maps", "com.waze", "com.mapbox.mapboxnav", "com.here.app.maps",
        "com.transit.app", "org.openstreetmap.maps",
    ),
    NowCategory.MEDIA to listOf(
        "com.spotify.music", "com.google.android.apps.youtube.music", "com.google.android.music",
        "com.google.android.youtube", "com.amazon.mp3", "com.deezer.deezer", "com.netflix.mediaclient",
    ),
)

object CategoryApps {

    /** Resolving an icon is the expensive half, so an answer is kept for a couple of minutes. */
    private const val CACHE_MS = 120_000L

    private data class Resolution(val app: App, val at: Long)

    private val resolutions = ConcurrentHashMap<NowCategory, Resolution>()
    private val icons = ConcurrentHashMap<String, Icon>()

    /**
     * The order a category's candidates are tried in, kept free of the platform so it can be
     * reasoned about on its own. A well-known app the device has is the best answer whatever the
     * query said - every browser answers a geo: link, and a browser is not a maps app - then
     * anything else the device answers for, which is how an app we do not know by name is found.
     * A package the device does not have is never returned.
     */
    fun pick(installed: Set<String>, queried: List<String>, wellKnown: List<String>): String? =
        wellKnown.firstOrNull { it in installed && it in queried }
            ?: wellKnown.firstOrNull { it in installed }
            ?: queried.firstOrNull { it in installed }

    /** The one app for [category], or null when this device has none. */
    suspend fun resolve(context: Context, category: NowCategory): App? {
        val manager = context.packageManager
        val now = System.currentTimeMillis()
        resolutions[category]?.takeIf { now - it.at < CACHE_MS && installed(manager, it.app.packageName) }
            ?.let { return it.app }
        val packageName = pick(launcherPackages(manager, context.packageName), query(context, category), wellKnown[category].orEmpty())
            ?: return null
        val app = build(context, manager, packageName) ?: return null
        resolutions[category] = Resolution(app, now)
        return app
    }

    /** Every category this device can answer, so the board can show them in a fixed order. */
    suspend fun resolveAll(context: Context): Map<NowCategory, App> = withContext(Dispatchers.IO) {
        NowCategory.entries.mapNotNull { category -> resolve(context, category)?.let { category to it } }.toMap()
    }

    /** The app behind any package, or null once it is gone. This is how the money slot resolves. */
    suspend fun app(context: Context, packageName: String): App? = withContext(Dispatchers.IO) {
        build(context, context.packageManager, packageName)
    }

    /** The app's own name, the only thing a card without a notification can honestly say about it. */
    fun label(context: Context, packageName: String): String? = runCatching {
        context.packageManager.getApplicationLabel(applicationInfo(context.packageManager, packageName)!!).toString().take(60)
    }.getOrNull()

    /** The launcher's own cached icon for a package, so a card can show where it came from. */
    suspend fun icon(context: Context, packageName: String): Icon = cachedIcon(context, packageName)

    /**
     * Every app the user can start, with the name the drawer would show, for the user to choose a
     * money app from. Icons are left to the caller so the list itself costs one package query.
     */
    suspend fun launchable(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        queryPackages(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), context.packageName)
            .map { it to (label(context, it) ?: it) }
            .sortedBy { it.second.lowercase() }
            .map { (packageName, name) -> InstalledApp(packageName, name) }
    }

    private suspend fun build(context: Context, manager: PackageManager, packageName: String): App? {
        val info = applicationInfo(manager, packageName) ?: return null
        return App(
            name = label(context, packageName) ?: packageName,
            packageName = packageName,
            icon = cachedIcon(context, packageName),
            isSystemApp = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        )
    }

    private suspend fun cachedIcon(context: Context, packageName: String): Icon =
        icons[packageName] ?: runCatching { appIconManager(context).getIcon(packageName) }.getOrElse { Icon() }
            .also { icons[packageName] = it }

    private fun query(context: Context, category: NowCategory): List<String> =
        category.intent()?.let { intent ->
            runCatching { queryPackages(context, intent, context.packageName) }.getOrDefault(emptyList())
        }.orEmpty()

    private fun queryPackages(context: Context, intent: Intent, ownPackage: String?): List<String> = runCatching {
        context.packageManager
            .queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            .map { it.activityInfo.packageName }.filter { it != ownPackage }.distinct()
    }.getOrDefault(emptyList())

    private fun launcherPackages(manager: PackageManager, ownPackage: String): Set<String> = runCatching {
        manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            .map { it.activityInfo.packageName }.filter { it != ownPackage }.toSet()
    }.getOrDefault(emptySet())

    private fun installed(manager: PackageManager, packageName: String) = applicationInfo(manager, packageName) != null

    @Suppress("DEPRECATION")
    private fun applicationInfo(manager: PackageManager, packageName: String) = runCatching {
        manager.getApplicationInfo(packageName, 0)
    }.getOrNull()

    private fun appIconManager(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, BoardEntryPoint::class.java).appIconManager()
}

/** An app the user can start, named the way the drawer names it. */
data class InstalledApp(val packageName: String, val name: String)

/** The launcher's own icon cache, reached the same way the live tile reaches the DataStore. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface BoardEntryPoint {
    fun appIconManager(): AppIconManager
}
