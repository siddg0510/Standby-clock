package com.example.standbyclock

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/** Simon: watch the colour sequence, then repeat it. Each round adds one colour. */
class SimonActivity : Activity() {

    private val colours = intArrayOf(0xFFE53935.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFFFDD835.toInt())
    private val sequence = mutableListOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var inputIndex = 0
    private var accepting = false
    private lateinit var status: TextView
    private lateinit var pads: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = "Press Start"
        }
        root.addView(status)
        pads = colours.indices.map { index ->
            Button(this).apply {
                setBackgroundColor(colours[index])
                setOnClickListener { press(index) }
            }
        }
        for (row in 0 until 2) {
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                for (col in 0 until 2) {
                    addView(pads[row * 2 + col], LinearLayout.LayoutParams(0, (120 * resources.displayMetrics.density).toInt(), 1f))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(Button(this).apply {
            text = "Start"
            setOnClickListener { start() }
        })
        setContentView(root)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        accepting = false
        super.onPause()
    }

    private fun start() {
        handler.removeCallbacksAndMessages(null)
        sequence.clear()
        nextRound()
    }

    private fun nextRound() {
        sequence.add(Random.nextInt(colours.size))
        inputIndex = 0
        accepting = false
        status.text = "Watch..."
        playSequence(0)
    }

    private fun playSequence(index: Int) {
        if (index >= sequence.size) {
            accepting = true
            status.text = "Your turn: ${sequence.size}"
            return
        }
        flash(sequence[index])
        handler.postDelayed({ playSequence(index + 1) }, 650L)
    }

    private fun flash(index: Int) {
        pads[index].alpha = 0.3f
        handler.postDelayed({ pads[index].alpha = 1f }, 350L)
    }

    private fun press(index: Int) {
        if (!accepting) return
        flash(index)
        if (index == sequence[inputIndex]) {
            inputIndex++
            if (inputIndex == sequence.size) {
                accepting = false
                status.text = "Good. Next round..."
                handler.postDelayed({ nextRound() }, 900L)
            }
        } else {
            accepting = false
            status.text = "Game over at ${sequence.size - 1}. Press Start to play again."
            sequence.clear()
        }
    }
}
