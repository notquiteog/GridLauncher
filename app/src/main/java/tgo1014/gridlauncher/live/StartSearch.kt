package tgo1014.gridlauncher.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import tgo1014.gridlauncher.data.withoutAccents
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem

/** Everything Start can search, not only installed apps. */
sealed class SearchResult {
    abstract val title: String
    data class AppResult(val app: App) : SearchResult() { override val title get() = app.name }
    data class TileResult(val tile: GridItem) : SearchResult() { override val title get() = tile.app.name }
    data class PersonResult(val contact: PinnedContact, val latest: TileNotification?) : SearchResult() { override val title get() = contact.name }
    data class NotificationResult(val notification: TileNotification) : SearchResult() { override val title get() = notification.title.ifBlank { notification.packageName } }
    data class SettingResult(val label: String, val action: String) : SearchResult() { override val title get() = label }
    data class WebResult(val query: String) : SearchResult() { override val title get() = "Search the web for \"$query\"" }
}

object StartSearch {

    /**
     * Curated Android settings screens, so search never guesses an intent action. A few platform
     * actions have no public constant on every supported level, so they are spelled out here.
     */
    private val settingsCatalog: List<Pair<String, String>> = listOf(
        "Wi-Fi" to Settings.Panel.ACTION_WIFI,
        "Bluetooth" to "android.settings.BLUETOOTH_SETTINGS",
        "Airplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "Do Not Disturb" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
        "Notifications" to "android.settings.NOTIFICATION_SETTINGS",
        "Battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "Permissions" to "android.settings.MANAGE_ALL_APPLICATIONS_PERMISSIONS",
        "Display" to Settings.ACTION_DISPLAY_SETTINGS,
        "Sound and vibration" to Settings.ACTION_SOUND_SETTINGS,
        "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "Date and time" to Settings.ACTION_DATE_SETTINGS,
        "Language and input" to Settings.ACTION_LOCALE_SETTINGS,
        "Security" to Settings.ACTION_SECURITY_SETTINGS,
        "Privacy" to Settings.ACTION_PRIVACY_SETTINGS,
        "Home app" to Settings.ACTION_HOME_SETTINGS,
        "Passwords and accounts" to Settings.ACTION_SYNC_SETTINGS,
        "Network and internet" to Settings.ACTION_WIRELESS_SETTINGS,
        "Digital wellbeing" to "android.settings.DIGITAL_WELLBEING_SETTINGS",
        "Developer options" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "All settings" to Settings.ACTION_SETTINGS,
    )

    /** DuckDuckGo: no account, no tracking token, nothing is sent anywhere but the search engine. */
    fun webUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query.trim().take(200), "UTF-8")
        return "https://duckduckgo.com/?q=$encoded"
    }

    fun webIntent(query: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl(query)))

    /** Settings screens are always opened from an activity, so no cross-task flag is needed. */
    fun settingIntent(action: String) = Intent(action)

    fun matchingSettings(needle: String): List<Pair<String, String>> =
        settingsCatalog.filter { it.first.withoutAccents.lowercase().contains(needle) }

    fun photosIntent() = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

    fun dialIntent(number: String) = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    fun smsIntent(number: String) = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
    fun mailIntent(address: String) = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))

    fun search(
        query: String, apps: List<App>, tiles: List<GridItem> = emptyList(), contacts: List<PinnedContact> = emptyList(),
        notifications: List<TileNotification> = emptyList(), allowWeb: Boolean = true, limit: Int = 40,
    ): List<SearchResult> {
        val needle = query.withoutAccents.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        fun hit(vararg values: String?) = values.any { !it.isNullOrBlank() && it.withoutAccents.lowercase().contains(needle) }
        val results = mutableListOf<SearchResult>()
        apps.filter { hit(it.name) }.forEach { results += SearchResult.AppResult(it) }
        tiles.filter { hit(it.app.name) && it.app.packageName.startsWith("grid://") }.forEach { results += SearchResult.TileResult(it) }
        contacts.filter { hit(it.name) }.forEach {
            results += SearchResult.PersonResult(it, notifications.filter { n -> it.matches(n) }.maxByOrNull { n -> n.time })
        }
        notifications.filter { hit(it.title, it.text, it.packageName) }.take(limit / 2).forEach { results += SearchResult.NotificationResult(it) }
        matchingSettings(needle).forEach { results += SearchResult.SettingResult(it.first, it.second) }
        if (allowWeb && results.none { it is SearchResult.AppResult }) results += SearchResult.WebResult(query.trim().take(120))
        return results.take(limit)
    }
}
