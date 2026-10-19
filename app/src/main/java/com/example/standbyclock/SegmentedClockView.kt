package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Seven-segment LCD style time, drawn with rectangles. Unlit segments show faintly. */
class SegmentedClockView(context: Context) : View(context) {

    // Four digits; -1 means blank (leading zero of a 12-hour clock).
    private var digits = intArrayOf(-1, 0, 0, 0)
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
    private var colonOn = true

    /** Shows or hides the colon between the hours and minutes. The clock turns it off on odd seconds when blinking. */
    fun setColonOn(on: Boolean) {
        if (on == colonOn) return
        colonOn = on
        invalidate()
    }

    fun setTime(hour24: Int, minute: Int, use24Hour: Boolean) {
        val hour = if (use24Hour) hour24 else (hour24 % 12).let { if (it == 0) 12 else it }
        digits = intArrayOf(
            if (use24Hour || hour >= 10) hour / 10 else -1,
            hour % 10,
            minute / 10,
            minute % 10,
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val slot = minOf(width / 4.6f, height * 0.5f)
        val digitHeight = height.toFloat()
        var x = 0f
        for (i in digits.indices) {
            drawDigit(canvas, digits[i], x, slot, digitHeight)
            x += slot
            if (i == 1) {
                if (colonOn) drawColon(canvas, x + slot * 0.3f, digitHeight, slot)
                x += slot * 0.6f
            }
        }
    }

    private fun drawDigit(canvas: Canvas, digit: Int, x: Float, w: Float, h: Float) {
        val mask = if (digit in SEGMENTS.indices) SEGMENTS[digit] else 0
        val t = w * 0.14f
        val mid = h / 2f
        val segments = arrayOf(
            RectF(x + t, 0f, x + w - t, t),                    // a: top
            RectF(x + w - t, t, x + w, mid - t / 2),           // b: top right
            RectF(x + w - t, mid + t / 2, x + w, h - t),       // c: bottom right
            RectF(x + t, h - t, x + w - t, h),                 // d: bottom
            RectF(x, mid + t / 2, x + t, h - t),               // e: bottom left
            RectF(x, t, x + t, mid - t / 2),                   // f: top left
            RectF(x + t, mid - t / 2, x + w - t, mid + t / 2), // g: middle
        )
        segments.forEachIndexed { index, segment ->
            val isLit = (mask and (1 shl index)) != 0
            canvas.drawRect(segment, if (isLit) lit else ghost)
        }
    }

    private fun drawColon(canvas: Canvas, x: Float, h: Float, slot: Float) {
        val dot = slot * 0.12f
        canvas.drawRect(x, h * 0.33f - dot / 2, x + dot, h * 0.33f + dot / 2, lit)
        canvas.drawRect(x, h * 0.67f - dot / 2, x + dot, h * 0.67f + dot / 2, lit)
    }

    private companion object {
        // Bit 0 = a (top) through bit 6 = g (middle): the standard seven-segment encoding for 0-9.
        val SEGMENTS = intArrayOf(63, 6, 91, 79, 102, 109, 125, 7, 127, 111)
    }
}
