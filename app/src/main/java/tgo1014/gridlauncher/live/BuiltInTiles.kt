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
import tgo1014.gridlauncher.R
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
    const val DATA = "grid://data"
    const val GREETING = "grid://greeting"
    const val CONTACTS = "grid://contacts"
    const val DESTINATION = "grid://destination"
    const val GROUP = "grid://group"

    val apps = listOf(
        App("Clock", CLOCK), App("Calendar", CALENDAR), App("People", PEOPLE), App("Music", MUSIC),
        App("Photos", PHOTOS), App("Battery", BATTERY), App("Storage", STORAGE),
        App("Steps", STEPS), App("Wallet", WALLET), App("Data", DATA), App("Greeting", GREETING),
    )
    fun isHub(id: String) = id in setOf(CLOCK, CALENDAR, PEOPLE, MUSIC, PHOTOS, BATTERY, STORAGE, STEPS, WALLET, DATA, GREETING)

    /**
     * The designed glyph each hub tile carries, the way Windows Phone marked a live tile with its
     * own mark rather than an app icon. Small tiles show it centred; wider tiles tuck it into the
     * bottom corner so it reads as an indicator and not as the tile's subject.
     */
    fun glyph(id: String): Int = when (id) {
        CLOCK -> R.drawable.hub_clock
        CALENDAR -> R.drawable.hub_calendar
        PEOPLE, CONTACTS -> R.drawable.hub_people
        MUSIC -> R.drawable.hub_music
        PHOTOS -> R.drawable.hub_photos
        BATTERY -> R.drawable.hub_battery
        STORAGE -> R.drawable.hub_storage
        STEPS -> R.drawable.hub_steps
        WALLET -> R.drawable.hub_wallet
        DATA -> R.drawable.hub_data
        GREETING -> R.drawable.hub_greeting
        FOLDER -> R.drawable.hub_app
        DESTINATION -> R.drawable.hub_destination
        else -> R.drawable.hub_app
    }

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
        DATA -> Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        GREETING -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
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
        DATA -> data(context)
        GREETING -> greeting(context)
        else -> null
    }

    private fun clock(context: Context): Pair<String, String> {
        val now = Clock.date()
        val use24 = android.text.format.DateFormat.is24HourFormat(context)
        val time = Clock.inZone(java.text.SimpleDateFormat(if (use24) "HH:mm" else "h:mm", java.util.Locale.getDefault())).format(now)
        return time to Clock.inZone(DateFormat.getDateInstance(DateFormat.MEDIUM)).format(now)
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

    /** A plain time-of-day greeting and whatever is next, the way Start greeted you before Cortana. */
    private fun greeting(context: Context): Pair<String, String> {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hello = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
        val next = agenda(context).second
        return hello to if (next.startsWith("No upcoming") || next.startsWith("Tap to")) "Here is your day" else next
    }

    private fun data(context: Context): Pair<String, String> {
        val mb = UsageTiles.usedMb(context)
        if (mb < 0) return "Data" to "Not available on this device"
        val network = if (UsageTiles.isOnWifi(context)) "WiFi" else "Mobile"
        return "${network}: ${formatMb(mb)} used" to "Since Android started counting"
    }

    private fun formatMb(mb: Long) = if (mb >= 1024) String.format(java.util.Locale.getDefault(), "%.1f GB", mb / 1024.0) else "$mb MB"

    private fun steps(context: Context): Pair<String, String> {
        if (!SensorTiles.available(context)) return "Steps" to "No step sensor"
        val reading = SensorTiles.start(context)
        val count = reading.steps
        if (count == null) return "Steps" to "Waiting for a reading"
        val (steps, distance, heart) = SensorTiles.detail(count, reading.bpm)
        return steps to "$distance · $heart"
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

    /**
     * The next few events, the way Windows Phone's agenda showed a day rather than a single entry.
     * An empty list is the honest answer when the calendar cannot be read or has nothing coming:
     * the tile falls back to its own wording and the board leaves the card out entirely.
     */
    fun agendaEvents(context: Context, limit: Int = 6): List<CalendarEvent> {
        if (!granted(context, Manifest.permission.READ_CALENDAR)) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(uri, now)
        android.content.ContentUris.appendId(uri, now + 7 * 86400000L)
        return runCatching {
            context.contentResolver.query(uri.build(), arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION),
                "${CalendarContract.Instances.END} >= ?", arrayOf(now.toString()), "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit.coerceIn(1, 30)) add(CalendarEvent(
                        cursor.getString(0).orEmpty().take(120), cursor.getLong(1), cursor.getInt(2) == 1, cursor.getString(3).orEmpty().take(80)))
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** The tile's own two-line reading: the next event, or why there is none. */
    private fun agenda(context: Context): Pair<String, String> {
        val today = Clock.inZone(java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())).format(Clock.date())
        if (!granted(context, Manifest.permission.READ_CALENDAR)) return today to "Tap to connect calendar"
        val event = agendaEvents(context, 1).firstOrNull() ?: return today to "No upcoming events"
        return event.title to if (event.allDay) "All day · $today"
        else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.begin))
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
