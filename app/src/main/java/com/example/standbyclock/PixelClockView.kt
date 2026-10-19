package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Pixel-art time: each digit is a 3 by 5 grid of square pixels. */
class PixelClockView(context: Context) : View(context) {

    private var text = "00:00"
    private val lit = Paint().apply { color = Color.WHITE }

    fun setTime(hour24: Int, minute: Int, use24Hour: Boolean) {
        val hour = if (use24Hour) hour24 else (hour24 % 12).let { if (it == 0) 12 else it }
        text = "%02d:%02d".format(hour, minute)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pixel = min(width / (text.length * 4f), height / 5f)
        val totalWidth = text.length * 4 * pixel
        var x0 = (width - totalWidth) / 2f
        val y0 = (height - 5 * pixel) / 2f
        for (ch in text) {
            val rows = GLYPHS[ch] ?: GLYPHS.getValue(' ')
            for (row in 0 until 5) {
                for (col in 0 until 3) {
                    if ((rows[row] shr (2 - col)) and 1 == 1) {
                        val left = x0 + col * pixel
                        val top = y0 + row * pixel
                        canvas.drawRect(left, top, left + pixel * 0.9f, top + pixel * 0.9f, lit)
                    }
                }
            }
            x0 += 4 * pixel
        }
    }

    private companion object {
        // Each row is three bits, leftmost bit first.
        val GLYPHS = mapOf(
            '0' to intArrayOf(7, 5, 5, 5, 7),
            '1' to intArrayOf(2, 6, 2, 2, 7),
            '2' to intArrayOf(7, 1, 7, 4, 7),
            '3' to intArrayOf(7, 1, 7, 1, 7),
            '4' to intArrayOf(5, 5, 7, 1, 1),
            '5' to intArrayOf(7, 4, 7, 1, 7),
            '6' to intArrayOf(7, 4, 7, 5, 7),
            '7' to intArrayOf(7, 1, 2, 2, 2),
            '8' to intArrayOf(7, 5, 7, 5, 7),
            '9' to intArrayOf(7, 5, 7, 1, 7),
            ':' to intArrayOf(0, 2, 0, 2, 0),
            ' ' to intArrayOf(0, 0, 0, 0, 0),
        )
    }
}
