package tgo1014.gridlauncher.live

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Real step and heart-rate counts from the device's own sensors, the way the Windows Phone Steps
 * app did. Nothing is estimated and nothing is uploaded; if the hardware is absent the tile says so.
 */
object SensorTiles {
    data class Reading(val steps: Int? = null, val bpm: Int? = null, val trackedSince: Long? = null)

    private var listener: SensorEventListener? = null
    private var steps: Int? = null
    private var bpm: Int? = null
    private var since: Long? = null

    fun available(context: Context): Boolean = manager(context)?.let {
        it.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null || it.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null
    } == true

    private fun manager(context: Context) = context.getSystemService(SensorManager::class.java)

    /** Starts listening. Call [stop] when the launcher is no longer resumed. */
    fun start(context: Context): Reading {
        val manager = manager(context) ?: return Reading()
        fun granted(permission: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val canReadSteps = granted(android.Manifest.permission.ACTIVITY_RECOGNITION)
        val canReadHeart = granted("android.permission.health.READ_HEART_RATE")
        if (!canReadSteps) steps = null
        if (!canReadHeart) bpm = null
        if (listener == null) {
            var daily = 0
            var heart = 0
            val l = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_STEP_COUNTER -> { daily = event.values.firstOrNull()?.toInt() ?: daily; steps = daily }
                        Sensor.TYPE_HEART_RATE -> { heart = event.values.firstOrNull()?.toInt() ?: heart; bpm = heart }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (canReadSteps) manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                runCatching { manager.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
                    .onSuccess { registered -> if (registered) since = System.currentTimeMillis() }
            }
            if (canReadHeart) manager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
                runCatching { manager.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL) }
            }
            listener = l
        }
        return Reading(steps, bpm, since)
    }

    fun stop(context: Context) {
        val manager = manager(context) ?: return
        listener?.let { manager.unregisterListener(it) }
        listener = null
    }

    fun reading() = Reading(steps, bpm, since)

    fun forget() { steps = null; bpm = null; since = null }

    /**
     * Distance and calories the way the Windows Phone Stepcounter showed them. Derived from the
     * step count and a stride estimate rather than invented: a 0.762 m stride for an average adult,
     * and roughly 0.04 kcal per kilogram per step.
     */
    fun detail(count: Int, bpm: Int?): Triple<String, String, String> {
        val km = String.format(java.util.Locale.getDefault(), "%.2f km", count * STRIDE_METRES / 1000.0)
        val calories = (count * 0.04).toInt()
        val heart = bpm?.let { "$it bpm" } ?: "No heart rate"
        return Triple(count.toString(), "$km · $calories kcal", heart)
    }

    private const val STRIDE_METRES = 0.762
}
