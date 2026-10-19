package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Flip-card digits: four tiles with a split line, like a mechanical flip clock. */
class RetroflipView(context: Context) : View(context) {

    private var digits = "    "
    private val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1B.toInt() }
    private val seam = Paint().apply { color = Color.BLACK }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val rect = RectF()
    private var opaque = true

    /** Style 0 is dark cards, 1 is light cards, 2 is warm. Opaque off drops the card background. */
    fun setStyle(style: Int, cardBackground: Boolean) {
        val (cardColor, digitColor) = when (style) {
            1 -> 0xFFEDEDED.toInt() to Color.BLACK
            2 -> 0xFF3A2A1A.toInt() to 0xFFFFD58A.toInt()
            else -> 0xFF1B1B1B.toInt() to Color.WHITE
        }
        tile.color = cardColor
        glyph.color = digitColor
        seam.color = if (style == 1) 0xFFBDBDBD.toInt() else Color.BLACK
        opaque = cardBackground
        invalidate()
    }

    /** Four characters: two hour digits (a leading space for a blank) then two minute digits. */
    fun setTime(hour24: Int, minute: Int, use24Hour: Boolean) {
        val hour = if (use24Hour) hour24 else (hour24 % 12).let { if (it == 0) 12 else it }
        val hourText = if (use24Hour || hour >= 10) "%02d".format(hour) else " $hour"
        digits = hourText + "%02d".format(minute)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = width * 0.02f
        val tileWidth = (width - gap * 3) / 4f
        val tileHeight = height.toFloat()
        glyph.textSize = tileHeight * 0.72f
        val radius = tileWidth * 0.08f
        for (i in 0 until 4) {
            val left = i * (tileWidth + gap)
            rect.set(left, 0f, left + tileWidth, tileHeight)
            if (opaque) canvas.drawRoundRect(rect, radius, radius, tile)
            canvas.drawRect(left, tileHeight / 2 - 3f, left + tileWidth, tileHeight / 2 + 3f, seam)
            val metrics = glyph.fontMetrics
            val baseline = tileHeight / 2 - (metrics.ascent + metrics.descent) / 2
            canvas.drawText(digits[i].toString(), left + tileWidth / 2, baseline, glyph)
        }
    }
}

/** Sun path for the time of day: sunrise at 06:00 on the left, noon at the top, sunset at 18:00 on the right. */
class SolarView(context: Context) : View(context) {

    private var minutesOfDay = 0
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC857.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFC857 }
    private val sky = Paint()

    fun setTime(hour24: Int, minute: Int) {
        minutesOfDay = hour24 * 60 + minute
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        sky.shader = null
    }

    /** Sky from deep blue at night, through orange at dawn and dusk, to pale blue in the day. */
    private fun paintSky(canvas: Canvas) {
        val m = minutesOfDay
        val (top, horizon) = when {
            m in 350..440 || m in 1000..1100 -> 0xFF1F3A68.toInt() to 0xFFFF9A5C.toInt() // dawn and dusk
            m in 441..999 -> 0xFF2B6CB0.toInt() to 0xFFBFE3FF.toInt()                     // day
            else -> 0xFF050A1F.toInt() to 0xFF1B2A4A.toInt()                             // night
        }
        sky.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, horizon, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paintSky(canvas)
        val radiusX = width / 2f - 24f
        val cx = width / 2f
        val baseY = height * 0.9f
        val radiusY = height * 0.85f
        val path = Path().apply {
            moveTo(cx - radiusX, baseY)
            arcTo(RectF(cx - radiusX, baseY - radiusY, cx + radiusX, baseY + radiusY), 180f, 180f)
        }
        canvas.drawPath(path, arc)
        canvas.drawLine(cx - radiusX, baseY, cx + radiusX, baseY, arc)

        // Map 06:00 -> 0 and 18:00 -> pi; the sun dips below the horizon overnight.
        val theta = Math.PI * (((minutesOfDay - 360 + 1440) % 1440) / 720.0)
        val x = cx - radiusX * cos(theta).toFloat()
        val y = baseY - radiusY * sin(theta).toFloat()
        canvas.drawCircle(x, y, 34f, glow)
        canvas.drawCircle(x, y, 18f, sun)
    }
}

/** Ring of 60 minute dots that fill up as the minutes pass, with the time in the middle. */
class RadialView(context: Context) : View(context) {

    private var hour = 0
    private var minute = 0
    private var use24Hour = false
    private var invert = false
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    /** Inverted: the minutes that have passed are dim and the remaining minutes are bright. */
    fun setInvert(on: Boolean) {
        if (on == invert) return
        invert = on
        invalidate()
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }

    fun setTime(hour24: Int, minuteOfHour: Int, use24: Boolean) {
        hour = hour24
        minute = minuteOfHour
        use24Hour = use24
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val ring = min(cx, cy) * 0.88f
        for (i in 0 until 60) {
            val angle = Math.toRadians(i * 6.0 - 90.0)
            val x = cx + (cos(angle) * ring).toFloat()
            val y = cy + (sin(angle) * ring).toFloat()
            val radius = if (i % 5 == 0) ring * 0.05f else ring * 0.025f
            val passed = i < minute
            val bright = if (invert) !passed else passed
            canvas.drawCircle(x, y, radius, if (bright) lit else dot)
        }
        val h = if (use24Hour) hour else (hour % 12).let { if (it == 0) 12 else it }
        val text = if (use24Hour) "%02d:%02d".format(h, minute) else "%d:%02d".format(h, minute)
        label.textSize = ring * 0.42f
        val metrics = label.fontMetrics
        canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) / 2, label)
    }
}

/** Falling green digit columns behind the time, drawn at about 11 frames a second while shown. */
class MatrixRainView(context: Context) : View(context) {

    private val glyphs = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF66.toInt()
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }
    private val columnWidth = 28f
    private var heads = FloatArray(0)
    private var speeds = FloatArray(0)
    private var running = false
    private val frame: Runnable = Runnable {
        advance()
        invalidate()
        if (running) postDelayed(frame, FRAME_MS)
    }

    fun setRunning(on: Boolean) {
        if (on == running) return
        running = on
        if (on) post(frame) else removeCallbacks(frame)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        val count = (w / columnWidth).toInt() + 1
        heads = FloatArray(count) { -Random.nextFloat() * h }
        speeds = FloatArray(count) { 20f + Random.nextFloat() * 40f }
    }

    private fun advance() {
        for (i in heads.indices) {
            heads[i] += speeds[i]
            if (heads[i] > height + 300f) heads[i] = -Random.nextFloat() * height
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in heads.indices) {
            val x = i * columnWidth
            for (k in 0 until 14) {
                glyphs.alpha = (255 - k * 18).coerceAtLeast(20)
                val y = heads[i] - k * 28f
                canvas.drawText(if (Random.nextBoolean()) "1" else "0", x, y, glyphs)
            }
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FRAME_MS = 90L
    }
}
