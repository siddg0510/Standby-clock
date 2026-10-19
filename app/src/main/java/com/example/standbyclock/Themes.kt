package com.example.standbyclock

// Colour themes and the ambient background are adapted from Standby Pro
// (https://github.com/Ghostzy0/standby-pro), MIT License, Copyright (c) 2026 TheFadGhost.
// The colours and layout are reimplemented here with Android views. See NOTICE.md.

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** A colour theme: background, main text, accent for glows, and a secondary colour for the ambient wash. */
data class ThemePreset(
    val id: String,
    val name: String,
    val background: Int,
    val foreground: Int,
    val accent: Int,
    val secondary: Int,
    /** How strong the glow behind the time is, from 0 to 1. */
    val glow: Float,
)

object Themes {

    val ALL = listOf(
        ThemePreset("aurora", "Aurora", 0xFF020207.toInt(), 0xFFF8FAFC.toInt(), 0xFF7DD3FC.toInt(), 0xFFA78BFA.toInt(), 0.38f),
        ThemePreset("night-red", "Night Red", 0xFF030000.toInt(), 0xFFFF2424.toInt(), 0xFFFF2D2D.toInt(), 0xFF7F1D1D.toInt(), 0.22f),
        ThemePreset("solar", "Solar", 0xFF070401.toInt(), 0xFFFFF7ED.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0.26f),
        ThemePreset("matrix", "Matrix", 0xFF000403.toInt(), 0xFF7CFF8A.toInt(), 0xFF22C55E.toInt(), 0xFF064E3B.toInt(), 0.32f),
    )

    /** The theme for [id], or null for "off" (the app's own black background and palette). */
    fun byId(id: String): ThemePreset? = ALL.firstOrNull { it.id == id }

    /** The id after [id] in the cycle off, then each theme, then off again. */
    fun nextId(id: String): String {
        val ids = listOf("off") + ALL.map { it.id }
        return ids[(ids.indexOf(id).coerceAtLeast(0) + 1) % ids.size]
    }

    fun mix(from: Int, to: Int, amount: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
    )
}

/**
 * A diagonal gradient from the theme's background towards black, with nine faint arcs in the accent
 * colour across the top. The arcs are static, so the view draws once per change and costs nothing else.
 */
class AmbientView(context: Context) : View(context) {

    var theme: ThemePreset? = null
        set(value) {
            field = value
            invalidate()
        }

    private val fill = Paint()
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val theme = theme ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        fill.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(theme.background, Themes.mix(theme.background, theme.secondary, 0.22f), Color.BLACK),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, fill)

        arc.color = Color.argb(10, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
        for (i in 0 until 9) {
            val cx = w * (0.12f + i * 0.09f)
            val cy = h * 0.14f
            val r = (90f + i * 26f) * density
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 27f, 162f, false, arc)
        }
    }
}

/**
 * When the clock needs to redraw. A port of the cadence policy in Standby Pro: once a second only when
 * something on screen shows seconds or moves every second, otherwise once a minute on the minute.
 */
object ClockCadence {

    private const val SECOND_MS = 1_000L
    private const val MINUTE_MS = 60_000L

    /** Milliseconds until the next wake-up. */
    fun nextDelay(now: Long, needsSeconds: Boolean): Long =
        if (needsSeconds) SECOND_MS - now % SECOND_MS else MINUTE_MS - now % MINUTE_MS
}
