package tgo1014.gridlauncher.live

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import tgo1014.gridlauncher.R
import tgo1014.gridlauncher.domain.models.TileSettings

/**
 * The Start live tile, rebuilt for the platform's own home screen with RemoteViews. It follows the
 * same privacy rules as the in-app tiles: opt-in, per-app exclusions, quiet hours, and never any
 * content the app itself would have redacted.
 */
class GridLiveTileProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, ids.toSet())
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context, setOf(id))

    private fun update(context: Context, ids: Set<Int>) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val app = context.applicationContext
        LiveTileWidget.scope.launch {
            try { LiveTileWidget.draw(app, AppWidgetManager.getInstance(app), ids) } finally { pending.finish() }
        }
    }
}

/** Lets the widget's own taps reach the same guarded action path as the in-app sheets. */
class ActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        when (intent.action) {
            ACTION_OPEN -> NotificationTiles.open(context, key)
            ACTION_ACT -> {
                val notification = NotificationTiles.notifications.value.firstOrNull { it.key == key } ?: return
                val action = notification.actions.getOrNull(intent.getIntExtra(EXTRA_INDEX, -1)) ?: return
                NotificationTiles.act(context, key, action)
            }
        }
        LiveTileWidget.refresh(context)
    }

    companion object {
        const val ACTION_OPEN = "tgo1014.gridlauncher.appwidget.OPEN"
        const val ACTION_ACT = "tgo1014.gridlauncher.appwidget.ACT"
        const val EXTRA_KEY = "key"
        const val EXTRA_INDEX = "index"
    }
}

object LiveTileWidget {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun ids(context: Context): Set<Int> = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, GridLiveTileProvider::class.java)).toSet()
    }.getOrDefault(emptySet())

    /** Called whenever the notification list changes, so the widget tracks the in-app tiles. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val widgetIds = ids(app)
        if (widgetIds.isEmpty()) return
        scope.launch { draw(app, AppWidgetManager.getInstance(app), widgetIds) }
    }

    /** Asks the system home screen to place the live tile. */
    fun requestPin(context: Context): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(ComponentName(context, GridLiveTileProvider::class.java), null, null)
    }.getOrDefault(false)

    internal suspend fun draw(context: Context, manager: AppWidgetManager, widgetIds: Set<Int>) {
        val settings = readSettings(context)
        val quiet = QuietHours.active(settings)
        // A live tile is drawn by the system, on whatever surface the user has put it on, so it
        // follows the same lock rule as the in-app tiles: a locked device shows no notification
        // content at all, on a widget or anywhere else.
        val locked = runCatching { context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked }.getOrDefault(true)
        val notifications = if (quiet || locked) emptyList() else NotificationTiles.notifications.value
            .filter { it.packageName !in settings.hiddenPreviewApps }
        widgetIds.forEach { runCatching { manager.updateAppWidget(it, views(context, manager, it, notifications, settings, quiet)) } }
    }

    /** Uses the app's single DataStore instance; a second factory over the same file is illegal. */
    private suspend fun readSettings(context: Context): TileSettings = runCatching {
        val store = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext, SettingsEntryPoint::class.java).dataStore()
        store.data.first()[stringPreferencesKey("settingsKey")]?.let { Json.decodeFromString<TileSettings>(it) } ?: TileSettings()
    }.getOrDefault(TileSettings())

    private fun views(context: Context, manager: AppWidgetManager, id: Int, notifications: List<TileNotification>, settings: TileSettings, quiet: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_live_tile)
        val options = manager.getAppWidgetOptions(id)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 70)
        val latest = notifications.maxByOrNull { it.time }
        if (latest == null) {
            views.setTextViewText(R.id.live_tile_app, context.getString(R.string.widget_live_tile_label))
            views.setTextViewText(R.id.live_tile_title, context.getString(if (quiet) R.string.widget_live_tile_empty else R.string.widget_live_tile_nothing))
            views.setViewVisibility(R.id.live_tile_text, View.GONE)
            views.setViewVisibility(R.id.live_tile_stack, View.GONE)
            return views
        }
        val showText = settings.showNotificationText
        val roomy = widthDp >= 250 || heightDp >= 110

        views.setTextViewText(R.id.live_tile_app, appLabel(context, latest.packageName))
        views.setTextViewText(R.id.live_tile_title, if (showText) latest.title.ifBlank { latest.packageName } else "${notifications.size} notifications")
        views.setTextViewText(R.id.live_tile_text, if (showText) latest.text else "")
        views.setViewVisibility(R.id.live_tile_text, if (showText && latest.text.isNotBlank()) View.VISIBLE else View.GONE)

        val max = latest.progressMax
        if (max > 0) {
            views.setViewVisibility(R.id.live_tile_progress, View.VISIBLE)
            views.setProgressBar(R.id.live_tile_progress, max, latest.progress.coerceIn(0, max), latest.indeterminate)
        } else if (latest.indeterminate) {
            views.setViewVisibility(R.id.live_tile_progress, View.VISIBLE)
            views.setProgressBar(R.id.live_tile_progress, 100, 0, true)
        } else views.setViewVisibility(R.id.live_tile_progress, View.GONE)

        // A wider or taller tile gets the stack of what else is waiting, in Android 17 style.
        val stack = if (roomy) notifications.drop(1).take(3) else emptyList()
        views.setViewVisibility(R.id.live_tile_stack, if (stack.isEmpty()) View.GONE else View.VISIBLE)
        if (stack.isNotEmpty()) views.setTextViewText(R.id.live_tile_stack, stack.joinToString("\n") { "· ${it.title.ifBlank { it.packageName }}" })

        val actions = if (showText && roomy) latest.actions.take(2) else emptyList()
        listOf(R.id.live_tile_action_one, R.id.live_tile_action_two).forEachIndexed { index, viewId ->
            val action = actions.getOrNull(index)
            if (action == null) views.setViewVisibility(viewId, View.GONE)
            else {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, action.title)
                views.setOnClickPendingIntent(viewId, pending(context, ActionReceiver.ACTION_ACT, latest.key, index))
            }
        }
        views.setOnClickPendingIntent(R.id.live_tile_root, pending(context, ActionReceiver.ACTION_OPEN, latest.key, -1))
        return views
    }

    private fun appLabel(context: Context, packageName: String) = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /**
     * The widget's own tap, and nothing else.
     *
     * SECURITY: a PendingIntent is handed to the system and can outlive this process, so its extras
     * are not ours to keep private in. It carries the notification key and the action's position and
     * nothing else - the action title and the notification title are looked up in memory when the
     * tap arrives, so no notification text is ever placed in an object another process can read.
     */
    private fun pending(context: Context, action: String, key: String, index: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).setAction(action)
            .putExtra(ActionReceiver.EXTRA_KEY, key).putExtra(ActionReceiver.EXTRA_INDEX, index)
            .setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, "$action$key$index".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
}

/** Gives the widget the same DataStore singleton the rest of the app uses. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface SettingsEntryPoint {
    fun dataStore(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
}
