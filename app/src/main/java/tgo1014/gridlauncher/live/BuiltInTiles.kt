package tgo1014.gridlauncher.live

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import tgo1014.gridlauncher.domain.models.App
import java.text.DateFormat
import java.util.Date

/**
 * The Windows Phone Start tiles GridLauncher draws itself. Every value here comes from the
 * system or from the user's own library; nothing is invented.
 */
object BuiltInTiles {
    const val CLOCK = "grid://clock"
    const val CALENDAR = "grid://calendar"
    const val PEOPLE = "grid://people"
    const val BATTERY = "grid://battery"
    const val PHOTOS = "grid://photos"
    const val FOLDER = "grid://folder"
    const val WIDGET = "grid://widget"
    const val MUSIC = "grid://music"
    const val STORAGE = "grid://storage"
    const val STEPS = "grid://steps"
    const val WALLET = "grid://wallet"
    const val CONTACTS = "grid://contacts"
    const val DESTINATION = "grid://destination"
    const val GROUP = "grid://group"

    val apps = listOf(
        App("Clock", CLOCK), App("Calendar", CALENDAR), App("People", PEOPLE), App("Music", MUSIC),
        App("Photos", PHOTOS), App("Battery", BATTERY), App("Storage", STORAGE),
        App("Steps", STEPS), App("Wallet", WALLET),
    )
    fun isHub(id: String) = id in setOf(CLOCK, CALENDAR, PEOPLE, MUSIC, PHOTOS, BATTERY, STORAGE, STEPS, WALLET)

    /** Every built-in a backup may bring back, including the ones not offered in Add to Start. */
    val restorable: Map<String, App> = (apps + App("Folder", FOLDER) + App("Group", GROUP)).associateBy { it.packageName }

    fun intent(id: String): Intent? = when (id) {
        CLOCK -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
        CALENDAR -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build())
        PEOPLE, CONTACTS -> Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        MUSIC -> mediaIntent()
        PHOTOS -> Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        STORAGE -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        STEPS -> Intent("android.settings.ACTIVITY_RECOGNIZATION")
        WALLET -> Intent(android.service.quickaccesswallet.QuickAccessWalletService.ACTION_VIEW_WALLET)
        else -> null
    }

    /** The platform's media-player action; no constant is exposed on every supported API level. */
    private fun mediaIntent(): Intent? = Intent("android.intent.action.MEDIA_PLAYER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun detail(context: Context, id: String): Pair<String, String>? = when (id) {
        CLOCK -> clock(context)
        BATTERY -> battery(context)
        CALENDAR -> agenda(context)
        PEOPLE -> people(context)
        MUSIC -> MediaTiles.now.value?.let { now ->
            (if (now.playing) "Now playing" else "Paused") to now.label
        } ?: ("Music" to if (granted(context, Manifest.permission.ACCESS_NOTIFICATION_POLICY)) "Nothing playing" else "Tap to connect media")
        PHOTOS -> recentPhotos(context)
        STORAGE -> storage(context)
        STEPS -> steps(context)
        WALLET -> ("Tap to pay" to if (WalletTiles.available(context)) "Open your wallet" else "Not available on this device")
        else -> null
    }

    private fun clock(context: Context): Pair<String, String> {
        val now = Date()
        val use24 = android.text.format.DateFormat.is24HourFormat(context)
        val time = java.text.SimpleDateFormat(if (use24) "HH:mm" else "h:mm", java.util.Locale.getDefault()).format(now)
        return time to DateFormat.getDateInstance(DateFormat.MEDIUM).format(now)
    }

    private fun battery(context: Context): Pair<String, String> {
        val manager = context.getSystemService(BatteryManager::class.java)
        val capacity = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val cycles = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val sticky = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperature = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val status = when {
            temperature > 450 -> "Too hot · $temperature/10 °C"
            capacity >= 100 -> "Fully charged"
            manager.isCharging -> manager.computeChargeTimeRemaining().let { if (it >= 0) "Full in about ${minutes(it)}" else "Charging" }
            else -> "Battery remaining"
        }
        val title = if (cycles > 0) "$capacity% · $cycles cycles" else "$capacity%"
        return title to status
    }

    private fun minutes(millis: Long) = "${(millis / 60000).coerceAtLeast(1)} min"

    private fun storage(context: Context): Pair<String, String> {
        val volume = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull() ?: return "Storage" to "Unavailable"
        val total = volume.blockCountLong * volume.blockSizeLong
        val free = volume.availableBlocksLong * volume.blockSizeLong
        val used = (total - free).coerceAtLeast(0)
        return "${gigabytes(used)} used" to "${gigabytes(free)} free of ${gigabytes(total)}"
    }

    private fun steps(context: Context): Pair<String, String> {
        if (!SensorTiles.available(context)) return "Steps" to "No step sensor"
        val reading = SensorTiles.start(context)
        val count = reading.steps
        return if (count == null) "Steps" to "Waiting for a reading"
        else "$count steps" to (reading.bpm?.let { "$it bpm" } ?: "Today")
    }

    private fun gigabytes(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 100) "${gb.toInt()} GB" else String.format(java.util.Locale.getDefault(), "%.1f GB", gb)
    }

    private fun recentPhotos(context: Context): Pair<String, String> {
        if (!PhotoTiles.permission(context)) return "Photos" to "Tap to see your library"
        val count = runCatching { context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { it.count } ?: 0 }.getOrDefault(0)
        val latest = PhotoTiles.recent(context, 1).firstOrNull()
        return "Photos" to when {
            count == 0 -> "No photos yet"
            latest != null -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(latest.takenAt))
            else -> "$count photos"
        }
    }

    private fun agenda(context: Context): Pair<String, String> {
        val today = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(Date())
        if (!granted(context, Manifest.permission.READ_CALENDAR)) return today to "Tap to connect calendar"
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(uri, now)
        android.content.ContentUris.appendId(uri, now + 7 * 86400000L)
        return runCatching {
            context.contentResolver.query(uri.build(), arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                "${CalendarContract.Instances.END} >= ?", arrayOf(now.toString()), "${CalendarContract.Instances.BEGIN} ASC")?.use {
                if (it.moveToFirst()) {
                    it.getString(0).orEmpty() to if (it.getInt(2) == 1) "All day · $today" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.getLong(1)))
                } else today to "No upcoming events"
            } ?: (today to "No upcoming events")
        }.getOrDefault(today to "Calendar unavailable")
    }

    private fun people(context: Context): Pair<String, String> {
        if (!granted(context, Manifest.permission.READ_CONTACTS)) return "People" to "Tap to connect your contacts"
        return runCatching {
            context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), "${ContactsContract.Contacts.STARRED} = 1", null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC")?.use { cursor ->
                val names = mutableListOf<String>()
                while (cursor.moveToNext() && names.size < 6) names += cursor.getString(0).orEmpty()
                "People" to if (names.isEmpty()) "Star contacts to see them here" else names.joinToString(" · ")
            } ?: ("People" to "No favorite contacts")
        }.getOrDefault("People" to "Contacts unavailable")
    }

    fun granted(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
