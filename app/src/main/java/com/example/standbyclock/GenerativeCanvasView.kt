package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/** Slowly drifting coloured glows that play behind the sounds. Animates about ten times a second while shown. */
class GenerativeCanvasView(context: Context) : View(context) {

    private val count = 6
    private val speed = FloatArray(count) { 0.2f + Random.nextFloat() * 0.5f }
    private val phase = FloatArray(count) { Random.nextFloat() * 6.28f }
    private val hue = FloatArray(count) { Random.nextFloat() * 360f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var time = 0f
    private var running = false

    private val frame: Runnable = Runnable {
        time += 0.05f
        for (i in hue.indices) hue[i] = (hue[i] + 0.2f) % 360f
        invalidate()
        if (running) postDelayed(frame, FRAME_MS)
    }

    fun setRunning(on: Boolean) {
        if (on == running) return
        running = on
        if (on) post(frame) else removeCallbacks(frame)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val radius = width.coerceAtLeast(height) * 0.35f
        for (i in 0 until count) {
            val cx = width * (0.5f + 0.4f * sin(time * speed[i] + phase[i]))
            val cy = height * (0.5f + 0.4f * sin(time * speed[i] * 0.7f + phase[i] * 2f))
            val color = Color.HSVToColor(0x44, floatArrayOf(hue[i], 0.7f, 1f))
            paint.shader = RadialGradient(cx, cy, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, paint)
        }
        paint.shader = null
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FRAME_MS = 100L
    }
}
