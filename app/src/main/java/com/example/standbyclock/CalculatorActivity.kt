package com.example.standbyclock

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** A simple calculator: add, subtract, multiply and divide, applied left to right. */
class CalculatorActivity : Activity() {

    private var accumulator = 0.0
    private var pending: Char? = null
    private var entry = "0"
    private var freshEntry = true
    private lateinit var display: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
        }
        display = TextView(this).apply {
            textSize = 56f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
        }
        root.addView(display, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val rows = listOf(
            listOf("7", "8", "9", "/"),
            listOf("4", "5", "6", "*"),
            listOf("1", "2", "3", "-"),
            listOf("0", ".", "=", "+"),
            listOf("C"),
        )
        rows.forEach { keys ->
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                keys.forEach { key ->
                    addView(Button(this@CalculatorActivity).apply {
                        text = when (key) {
                            "/" -> "÷"
                            "*" -> "×"
                            "-" -> "−"
                            else -> key
                        }
                        setOnClickListener { press(key[0]) }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            })
        }
        setContentView(root)
        show()
    }

    private fun press(key: Char) {
        when {
            key.isDigit() -> {
                entry = if (freshEntry || entry == "0") key.toString() else entry + key
                freshEntry = false
            }
            key == '.' -> {
                if (freshEntry) entry = "0"
                if (!entry.contains('.')) entry += "."
                freshEntry = false
            }
            key == 'C' -> {
                accumulator = 0.0
                pending = null
                entry = "0"
                freshEntry = true
            }
            key == '=' -> {
                commit()
                pending = null
                freshEntry = true
            }
            else -> {
                commit()
                pending = key
                freshEntry = true
            }
        }
        show()
    }

    /** Folds the current entry into the running total using the pending operator. */
    private fun commit() {
        val value = entry.toDoubleOrNull() ?: 0.0
        accumulator = when (pending) {
            null -> value
            '+' -> accumulator + value
            '-' -> accumulator - value
            '*' -> accumulator * value
            '/' -> if (value == 0.0) 0.0 else accumulator / value
            else -> value
        }
        entry = format(accumulator)
    }

    private fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return "%.8f".format(value).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    private fun show() {
        display.text = if (freshEntry && pending != null) format(accumulator) else entry
    }
}
