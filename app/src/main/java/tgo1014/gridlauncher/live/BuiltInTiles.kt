package tgo1014.gridlauncher.live

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import tgo1014.gridlauncher.domain.models.App
import java.text.DateFormat
import java.util.Date

object BuiltInTiles {
    const val CLOCK = "grid://clock"
    const val CALENDAR = "grid://calendar"
    const val PEOPLE = "grid://people"
    const val BATTERY = "grid://battery"
    const val PHOTOS = "grid://photos"
    const val FOLDER = "grid://folder"
    const val WIDGET = "grid://widget"
    val apps = listOf(App("Clock", CLOCK), App("Calendar", CALENDAR), App("People", PEOPLE), App("Battery", BATTERY))

    fun intent(id: String): Intent? = when (id) {
        CLOCK -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
        CALENDAR -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build())
        PEOPLE -> Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        else -> null
    }

    fun detail(context: Context, id: String): Pair<String, String>? = when (id) {
        CLOCK -> android.text.format.DateFormat.getTimeFormat(context).format(Date()) to
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
        BATTERY -> {
            val bm = context.getSystemService(BatteryManager::class.java)
            "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%" to if (bm.isCharging) "Charging" else "Battery remaining"
        }
        CALENDAR -> agenda(context)
        PEOPLE -> people(context)
        else -> null
    }

    private fun agenda(context: Context): Pair<String, String> {
        val today = DateFormat.getDateInstance(DateFormat.FULL).format(Date())
        if (!granted(context, Manifest.permission.READ_CALENDAR)) return today to "Tap to connect your calendar"
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
