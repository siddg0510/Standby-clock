package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.View
import java.util.Calendar
import kotlin.math.min

/**
 * This month as a grid, with today highlighted. With [bigDay] on, today's number is drawn huge
 * behind the grid (the striking calendar). Used on its own and as an overlay on photos.
 */
class CalendarGridView(context: Context) : View(context) {

    var bigDay = false

    private val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val huge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }
    private val todayCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val labels = arrayOf("M", "T", "W", "T", "F", "S", "S")

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstOffset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Monday is column 0
        val columnWidth = width / 7f

        if (bigDay) {
            huge.textSize = height * 0.95f
            canvas.drawText(today.toString(), width / 2f, height * 0.9f, huge)
        }

        cell.textSize = height * 0.07f
        cell.color = Color.WHITE
        canvas.drawText(DateFormat.format("MMMM yyyy", calendar).toString(), width / 2f, height * 0.1f, cell)

        val headerY = height * 0.2f
        cell.textSize = height * 0.05f
        for (i in 0 until 7) canvas.drawText(labels[i], columnWidth * i + columnWidth / 2, headerY, cell)

        val rowStep = (height - headerY - height * 0.04f) / 6f
        for (day in 1..daysInMonth) {
            val index = firstOffset + day - 1
            val row = index / 7
            val column = index % 7
            val x = columnWidth * column + columnWidth / 2
            val y = headerY + rowStep * (row + 1)
            if (day == today) {
                val radius = min(columnWidth, rowStep) * 0.42f
                canvas.drawCircle(x, y - cell.textSize * 0.35f, radius, todayCircle)
                cell.color = Color.BLACK
            } else {
                cell.color = Color.WHITE
            }
            canvas.drawText(day.toString(), x, y, cell)
        }
    }
}
