package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.View
import org.json.JSONObject

/** Where the International Space Station is right now. Filled in by [IssClient.refresh]. */
data class IssPosition(
    val latitude: Double,
    val longitude: Double,
    val altitudeKm: Double,
    val velocityKmh: Double,
    val daylight: Boolean,
)

/**
 * The ISS position from wheretheiss.at, a free service that needs no key. The last reading is kept
 * in memory so the face still shows something when the network drops.
 */
object IssClient {

    private const val TAG = "StandbyIss"
    private const val URL = "https://api.wheretheiss.at/v1/satellites/25544"
    private const val MAX_AGE_MS = 60_000L

    @Volatile
    var last: IssPosition? = null
        private set

    @Volatile
    private var fetchedAt = 0L

    /** Blocking. Call from a background thread. Returns the cached reading when it is recent enough. */
    fun refresh(): IssPosition? {
        if (last != null && System.currentTimeMillis() - fetchedAt < MAX_AGE_MS) return last
        val body = WeatherClient.getJson(URL) ?: return last
        return try {
            val json = JSONObject(body)
            IssPosition(
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
                altitudeKm = json.getDouble("altitude"),
                velocityKmh = json.getDouble("velocity"),
                daylight = json.optString("visibility", "") == "daylight",
            ).also {
                last = it
                fetchedAt = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected ISS reply: ${e.message}")
            last
        }
    }

    /** Text lines for the ISS panel. */
    fun lines(position: IssPosition?): String {
        if (position == null) return "Finding the station..."
        val ns = if (position.latitude >= 0) "N" else "S"
        val ew = if (position.longitude >= 0) "E" else "W"
        return "ISS  %.2f°%s  %.2f°%s\n".format(
            Math.abs(position.latitude), ns, Math.abs(position.longitude), ew,
        ) +
            "%.0f km up · %,.0f km/h\n".format(position.altitudeKm, position.velocityKmh) +
            if (position.daylight) "In daylight" else "In Earth's shadow"
    }
}

/** A plain world map with a grid and a dot for the station. Drawn with lines, not photographs. */
class IssMapView(context: Context) : View(context) {

    private var latitude = 0.0
    private var longitude = 0.0
    private var known = false
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        strokeWidth = 1.5f
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD58A.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFD58A }

    fun setPosition(lat: Double, lon: Double) {
        latitude = lat
        longitude = lon
        known = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w - 1f, h - 1f, edge)
        // Lines every 30 degrees of longitude and latitude.
        for (i in 1 until 12) {
            val x = w * i / 12f
            canvas.drawLine(x, 0f, x, h, grid)
        }
        for (i in 1 until 6) {
            val y = h * i / 6f
            canvas.drawLine(0f, y, w, y, grid)
        }
        if (!known) return
        val x = ((longitude + 180.0) / 360.0 * w).toFloat()
        val y = ((90.0 - latitude) / 180.0 * h).toFloat()
        canvas.drawCircle(x, y, 22f, glow)
        canvas.drawCircle(x, y, 8f, dot)
    }
}
