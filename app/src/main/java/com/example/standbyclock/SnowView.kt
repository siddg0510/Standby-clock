package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/** Ambient falling snow, drawn behind the clock while the seasonal effect is turned on. */
class SnowView(context: Context) : View(context) {

    private val flake = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var speeds = FloatArray(0)
    private var sizes = FloatArray(0)
    private var running = false

    private val frame: Runnable = object : Runnable {
        override fun run() {
            advance()
            invalidate()
            if (running) postDelayed(this, FRAME_MS)
        }
    }

    fun setRunning(on: Boolean) {
        if (on == running) return
        running = on
        if (on) post(frame) else removeCallbacks(frame)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        val count = 90
        xs = FloatArray(count) { Random.nextFloat() * w }
        ys = FloatArray(count) { Random.nextFloat() * h }
        speeds = FloatArray(count) { 1.5f + Random.nextFloat() * 3f }
        sizes = FloatArray(count) { 2f + Random.nextFloat() * 4f }
    }

    private fun advance() {
        for (i in ys.indices) {
            ys[i] += speeds[i]
            xs[i] += sin(ys[i] / 60f) * 0.6f
            if (ys[i] > height) {
                ys[i] = -10f
                xs[i] = Random.nextFloat() * width
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in ys.indices) canvas.drawCircle(xs[i], ys[i], sizes[i], flake)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FRAME_MS = 50L
    }
}

/** Roman numerals for the time, for the Roman face. */
object RomanNumerals {

    private val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    private val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

    fun of(number: Int): String {
        var rest = number
        val out = StringBuilder()
        for (i in values.indices) {
            while (rest >= values[i]) {
                out.append(symbols[i])
                rest -= values[i]
            }
        }
        return out.toString()
    }

    /** Time as numerals, using 12-hour numbering and "N" for zero minutes: "XII : N". */
    fun time(hour24: Int, minute: Int): String {
        val hour = (hour24 % 12).let { if (it == 0) 12 else it }
        return "${of(hour)} : ${of(minute).ifEmpty { "N" }}"
    }
}
