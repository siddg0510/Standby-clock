package com.example.standbyclock

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise and sunset from a location, using the NOAA approximation (within a few minutes). */
object SolarTimes {

    /** Sunrise and sunset as minutes after local midnight, or null in polar day or night. */
    fun sunriseAndSunset(latitude: Double, longitude: Double, now: Long): Pair<Int, Int>? {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val gamma = 2 * Math.PI / 365.0 * (calendar.get(Calendar.DAY_OF_YEAR) - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val latRad = Math.toRadians(latitude)
        val zenith = Math.toRadians(90.833)
        val cosHour = cos(zenith) / (cos(latRad) * cos(decl)) - tan(latRad) * tan(decl)
        if (cosHour < -1.0 || cosHour > 1.0) return null
        val hourAngle = Math.toDegrees(acos(cosHour))
        val offsetMinutes = TimeZone.getDefault().getOffset(now) / 60_000.0
        val riseUtc = 720 - 4 * (longitude + hourAngle) - eqTime
        val setUtc = 720 - 4 * (longitude - hourAngle) - eqTime
        val rise = wrap(riseUtc + offsetMinutes)
        val set = wrap(setUtc + offsetMinutes)
        return rise to set
    }

    /** Night runs from sunset until sunrise. */
    fun isNight(minuteOfDay: Int, rise: Int, set: Int): Boolean = minuteOfDay < rise || minuteOfDay >= set

    private fun wrap(minutes: Double): Int = ((minutes.roundToInt() % 1440) + 1440) % 1440
}
