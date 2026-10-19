package com.example.standbyclock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract

/** Upcoming calendar events, with a countdown. Needs calendar permission; returns nothing without it. */
object Agenda {

    data class Entry(val begin: Long, val title: String)

    fun upcoming(context: Context, days: Int = 7, limit: Int = 5): List<Entry> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val now = System.currentTimeMillis()
        val end = now + days * 24L * 60 * 60_000L
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
        return try {
            CalendarContract.Instances.query(context.contentResolver, projection, now, end)?.use { cursor ->
                val entries = mutableListOf<Entry>()
                while (cursor.moveToNext()) {
                    val begin = cursor.getLong(1)
                    if (begin >= now) entries += Entry(begin, cursor.getString(0) ?: "")
                }
                entries.sortedBy { it.begin }.take(limit)
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** "in 2 h 10 min", "in 35 min", or "now". */
    fun countdown(ms: Long): String {
        if (ms <= 60_000L) return "now"
        val minutes = ms / 60_000L
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "in $hours h $rest min" else "in $rest min"
    }
}
