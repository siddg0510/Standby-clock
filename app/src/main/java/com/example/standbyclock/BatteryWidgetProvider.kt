package com.example.standbyclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews

/**
 * Home-screen battery widget: the percentage, a bar, and whether the phone is charging. Its colours
 * follow the charging-screen style chosen in settings (pixel, material or neon).
 */
class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = readBattery(context)
        val style = Settings(context).batteryStyle
        val accent = when (style) {
            "material" -> 0xFF26A69A.toInt()
            "neon" -> 0xFFFF2BD6.toInt()
            else -> Color.WHITE
        }
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_battery).apply {
                setTextViewText(R.id.battery_percent, state?.let { "${it.percent}%" } ?: "--")
                setTextColor(R.id.battery_percent, accent)
                setProgressBar(R.id.battery_bar, 100, state?.percent ?: 0, false)
                setTextViewText(
                    R.id.battery_state,
                    when {
                        state == null -> "Battery unknown"
                        state.charging && state.minutesToFull != null -> "Full in ${state.minutesToFull} min"
                        state.charging -> "Charging"
                        else -> "On battery"
                    },
                )
            }
            manager.updateAppWidget(id, views)
        }
    }
}
