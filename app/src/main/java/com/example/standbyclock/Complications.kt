package com.example.standbyclock

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import java.util.Calendar

/** Small text complications for the top corners, and the battery reading shared by the charging face. */
object Complications {

    const val NONE = "none"
    const val BATTERY = "battery"
    const val BATTERY_MIN = "battery_min"
    const val DATE = "date"
    const val GREETING = "greeting"
    const val DATE_GREETING = "date_greeting"
    const val ALARM = "alarm"
    const val YEAR = "year"
    const val MONTH = "month"
    const val WEEK = "week"
    const val TIME_SEC = "time_sec"
    const val PROTOCOL = "protocol"
    const val QUOTE = "quote"

    /** Id and the label shown in settings. */
    val OPTIONS = listOf(
        NONE to "None",
        BATTERY to "Battery",
        BATTERY_MIN to "Battery (minimal)",
        DATE to "Date",
        GREETING to "Greeting",
        DATE_GREETING to "Date and greeting",
        ALARM to "Next alarm",
        YEAR to "Year progress",
        MONTH to "Month progress",
        WEEK to "Week progress",
        TIME_SEC to "Time with seconds",
        PROTOCOL to "Protocol (rotating)",
        QUOTE to "Quote (rotating)",
    )

    /** Text for one complication, or an empty string when it has nothing to show. */
    fun text(context: Context, id: String, now: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        return when (id) {
            BATTERY -> batteryLine(context)
            BATTERY_MIN -> readBattery(context)?.let { "${it.percent}%" } ?: ""
            DATE -> DateFormat.format("EEE d MMM", now).toString()
            GREETING -> greeting(calendar.get(Calendar.HOUR_OF_DAY))
            DATE_GREETING -> greeting(calendar.get(Calendar.HOUR_OF_DAY)) + " · " +
                DateFormat.format("EEE d MMM", now)
            ALARM -> {
                val alarm = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
                if (alarm == null) "" else "Alarm " + DateFormat.format("EEE HH:mm", alarm.triggerTime)
            }
            YEAR -> progress(context, "Year", yearFraction(calendar))
            MONTH -> progress(context, "Month", monthFraction(calendar))
            WEEK -> progress(context, "Week", weekFraction(calendar))
            TIME_SEC -> DateFormat.format("HH:mm:ss", now).toString()
            PROTOCOL -> rotate(Settings(context).protocols, now, 60_000L)
            QUOTE -> rotate(Settings(context).quotes, now, 5 * 60_000L)
            else -> ""
        }
    }

    /** Picks one line from [list], changing every [stepMs]. Empty when the list has no lines. */
    fun rotate(list: String, now: Long, stepMs: Long): String {
        val lines = list.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (lines.isEmpty()) "" else lines[((now / stepMs) % lines.size).toInt()]
    }

    /** "Year 42%", or with the progress bar setting on, "Year ▰▰▰▰▱▱▱▱▱▱ 42%". */
    private fun progress(context: Context, label: String, fraction: Float): String {
        val percent = percent(fraction)
        if (!Settings(context).progressBars) return "$label $percent%"
        val filled = (percent / 10).coerceIn(0, 10)
        return "$label " + "▰".repeat(filled) + "▱".repeat(10 - filled) + " $percent%"
    }

    fun greeting(hour: Int): String = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    /** Words for the dementia face: "Morning", "Afternoon", "Evening" or "Night". */
    fun timeOfDay(hour: Int): String = when {
        hour < 5 -> "Night"
        hour < 12 -> "Morning"
        hour < 18 -> "Afternoon"
        hour < 22 -> "Evening"
        else -> "Night"
    }

    private fun percent(fraction: Float): Int = (fraction * 100).toInt().coerceIn(0, 100)

    private fun yearFraction(calendar: Calendar): Float =
        calendar.get(Calendar.DAY_OF_YEAR).toFloat() / calendar.getActualMaximum(Calendar.DAY_OF_YEAR)

    private fun monthFraction(calendar: Calendar): Float =
        calendar.get(Calendar.DAY_OF_MONTH).toFloat() / calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Monday is the first day of the week, measured in hours. */
    private fun weekFraction(calendar: Calendar): Float {
        val dayIndex = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return (dayIndex * 24 + calendar.get(Calendar.HOUR_OF_DAY)).toFloat() / (7 * 24)
    }
}

/** Battery level, charging state and time to full, read from the sticky battery broadcast. */
data class BatteryState(val percent: Int, val charging: Boolean, val minutesToFull: Int?, val plugged: Int)

fun readBattery(context: Context): BatteryState? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val minutes = if (charging) {
        context.getSystemService(BatteryManager::class.java)
            ?.computeChargeTimeRemaining()
            ?.takeIf { it > 0 }
            ?.let { (it / 60_000L).toInt() }
    } else {
        null
    }
    return BatteryState(level * 100 / scale, charging, minutes, plugged)
}
