package com.example.standbyclock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

/**
 * Burn-in protection: a one-pixel checkerboard that dims alternate pixels. The pattern flips
 * every minute (see [setPhase]) so no single pixel stays dimmed or lit for long.
 */
class CheckerboardView(context: Context) : View(context) {

    private val tile = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val paint = Paint().apply { isFilterBitmap = false }
    private var phase = 0
    private var dim = 0.35f

    init {
        redraw()
    }

    /** Dimming strength from 0 (off) to 100 (strongest). */
    fun setDim(percent: Int) {
        dim = percent.coerceIn(0, 100) / 100f
        redraw()
    }

    /** 0 or 1. Each change swaps which diagonal is dimmed. */
    fun setPhase(value: Int) {
        phase = value and 1
        redraw()
    }

    private fun redraw() {
        val shade = Color.argb((dim * 255).toInt(), 0, 0, 0)
        val clear = Color.TRANSPARENT
        // Pattern A dims the top-left and bottom-right pixels; pattern B dims the other two.
        val topLeft = if (phase == 0) shade else clear
        val topRight = if (phase == 0) clear else shade
        tile.setPixel(0, 0, topLeft)
        tile.setPixel(1, 0, topRight)
        tile.setPixel(0, 1, topRight)
        tile.setPixel(1, 1, topLeft)
        paint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dim <= 0f) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
