package tgo1014.gridlauncher.live

import android.app.NotificationManager
import android.content.Context
import tgo1014.gridlauncher.domain.models.TileSettings
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Windows Phone's Action Center quiet hours. Start always honours the schedule itself, so tiles
 * quiet down even without permission; the optional system grant additionally silences Android.
 */
object QuietHours {

    fun granted(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }.getOrDefault(false)

    /** Overnight windows are anchored to the evening they start. */
    fun scheduled(settings: TileSettings, time: LocalTime = Clock.localTime()): Boolean {
        if (!settings.quietHoursEnabled) return false
        val start = settings.quietStartHour
        val end = settings.quietEndHour
        if (start !in 0..23 || end !in 0..23 || start == end) return false
        val hour = time.hour
        return if (start > end) hour >= start || hour < end else hour in start until end
    }

    fun active(settings: TileSettings, time: LocalTime = Clock.localTime()): Boolean = settings.meetingMode || scheduled(settings, time)

    /**
     * Applies the system-wide filter when the user has granted Do Not Disturb access. Without the
     * grant this does nothing; Start still quiets its own tiles.
     */
    fun apply(context: Context, settings: TileSettings) {
        if (!granted(context)) return
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.setInterruptionFilter(if (active(settings))
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    fun hours(settings: TileSettings): String {
        fun label(hour: Int) = "%02d:00".format(hour)
        return "${label(settings.quietStartHour)}–${label(settings.quietEndHour)}"
    }

    fun nextChange(settings: TileSettings, time: LocalDateTime = LocalDateTime.now()): String {
        if (!settings.quietHoursEnabled) return "Off"
        val active = scheduled(settings, time.toLocalTime())
        val edge = if (active) settings.quietEndHour else settings.quietStartHour
        val delta = ((edge - time.hour + 24) % 24).let { if (it == 0) 24 else it }
        return if (active) "Quiet for another ${delta}h" else "Quiet in ${delta}h"
    }
}
