package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Analog face: a ring of ticks with hour and minute hands. An optional digital sub-dial sits below the centre. */
class AnalogClockView(context: Context) : View(context) {

    private var hour = 0
    private var minute = 0
    private var subDial: String? = null

    private val ticks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }
    private val hands = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private val subText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    fun setTime(hourOfDay: Int, minuteOfHour: Int) {
        hour = hourOfDay % 12
        minute = minuteOfHour
        invalidate()
    }

    /** Shows [text] in a small window under the centre, or hides it when null. */
    fun setSubDial(text: String?) {
        subDial = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.95f

        ticks.strokeWidth = radius * 0.02f
        canvas.drawCircle(cx, cy, radius, ticks)
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0 - 90.0)
            val inner = if (i % 3 == 0) 0.80 else 0.88
            canvas.drawLine(
                cx + (cos(angle) * radius * inner).toFloat(),
                cy + (sin(angle) * radius * inner).toFloat(),
                cx + (cos(angle) * radius * 0.96).toFloat(),
                cy + (sin(angle) * radius * 0.96).toFloat(),
                ticks,
            )
        }

        hands.strokeWidth = radius * 0.05f
        val hourAngle = Math.toRadians((hour + minute / 60.0) * 30.0 - 90.0)
        canvas.drawLine(
            cx, cy,
            cx + (cos(hourAngle) * radius * 0.5).toFloat(),
            cy + (sin(hourAngle) * radius * 0.5).toFloat(),
            hands,
        )

        hands.strokeWidth = radius * 0.03f
        val minuteAngle = Math.toRadians(minute * 6.0 - 90.0)
        canvas.drawLine(
            cx, cy,
            cx + (cos(minuteAngle) * radius * 0.8).toFloat(),
            cy + (sin(minuteAngle) * radius * 0.8).toFloat(),
            hands,
        )

        subDial?.let { text ->
            subText.textSize = radius * 0.16f
            canvas.drawText(text, cx, cy + radius * 0.42f, subText)
        }
    }
}
