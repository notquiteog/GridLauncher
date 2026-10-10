package tgo1014.gridlauncher.live

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/**
 * Every "what time is it" the launcher *draws* goes through here.
 *
 * The reason is screenshot testing. A clock tile, the date in the Start header and the date above
 * the app list are all real time, so a rendered baseline containing one of them would be stale by
 * tomorrow morning and would have to be thrown away every single day - at which point nobody trusts
 * the baseline and the whole exercise is decoration. Pinning the instant and the zone makes those
 * pixels ordinary, comparable pixels.
 *
 * [pinned] is null in a real install and every caller falls through to the system clock, so this
 * costs the shipped launcher nothing. It is a plain field rather than a build flag so a test can set
 * it directly: instrumentation runs inside the app's own process, so there is no channel to build.
 */
object Clock {

    data class Fixed(val epochMillis: Long, val zone: String) {
        init {
            require(epochMillis > 0) { "A pinned clock needs a real instant" }
            require(zone.isNotBlank()) { "A pinned clock needs a zone" }
        }
    }

    @Volatile
    var pinned: Fixed? = null

    fun millis(): Long = pinned?.epochMillis ?: System.currentTimeMillis()

    fun zone(): ZoneId = pinned
        ?.let { runCatching { ZoneId.of(it.zone) }.getOrNull() }
        ?: ZoneId.systemDefault()

    fun date(): Date = Date(millis())

    fun localDate(): LocalDate = Instant.ofEpochMilli(millis()).atZone(zone()).toLocalDate()

    fun localTime(): LocalTime = Instant.ofEpochMilli(millis()).atZone(zone()).toLocalTime()

    /**
     * A date format that reads [zone] rather than the device's own.
     *
     * `SimpleDateFormat("HH:mm").format(millis)` is formatted in the *device's* zone, which quietly
     * means the clock tile reads 03:41 on one emulator and 09:41 on another for the same instant.
     * Two machines, two baselines, and no way to tell a time-zone difference from a regression. Every
     * formatter that draws something for the user goes through here.
     */
    fun <F : java.text.DateFormat> inZone(format: F): F =
        format.apply { timeZone = java.util.TimeZone.getTimeZone(zone()) }
}