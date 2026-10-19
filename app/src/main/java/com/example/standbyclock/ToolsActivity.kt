package com.example.standbyclock

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.util.Calendar
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Tools: agenda, tally counters, sober counter, countdown to a date, to-do, protocols, notifications, and games. */
class ToolsActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var soberTime: TextView
    private lateinit var soberSaved: TextView
    private lateinit var countdownText: TextView
    private lateinit var todoList: LinearLayout
    private val tallyViews = mutableListOf<TextView>()
    private val SOBER_MILESTONES = listOf(1, 7, 30, 90, 180, 365, 730)
    private val handler = Handler(Looper.getMainLooper())
    private val soberTick = object : Runnable {
        override fun run() {
            updateSober()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        handler.post(soberTick)
        // Dim this screen at night, using the same night rule as the clock.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = settings.nightMode == Settings.NIGHT_ON ||
            (settings.nightMode == Settings.NIGHT_SCHEDULE && settings.isNightAt(hour))
        window.attributes = window.attributes.apply {
            screenBrightness = if (night) {
                settings.nightBrightness.coerceIn(1, 50) / 100f
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    override fun onPause() {
        handler.removeCallbacks(soberTick)
        super.onPause()
    }

    private fun buildUi(): View {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        content.addView(heading("Agenda"))
        val agenda = Agenda.upcoming(this, days = 7, limit = 5)
        if (agenda.isEmpty()) {
            content.addView(label("No upcoming events, or calendar access is off (allow it in Settings, under Tools and about)."))
        } else {
            agenda.forEach { entry ->
                content.addView(label(
                    "${DateFormat.format("EEE HH:mm", entry.begin)}  ${entry.title}  (${Agenda.countdown(entry.begin - System.currentTimeMillis())})",
                ))
            }
        }

        content.addView(heading("Tally counters"))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (index in 0 until 4) {
            row.addView(tallyColumn(index), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(row)

        content.addView(heading("Sober counter"))
        soberTime = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
        }
        content.addView(soberTime)
        soberSaved = label("")
        content.addView(soberSaved)
        content.addView(button("Start counting now") {
            settings.soberStart = System.currentTimeMillis()
            updateSober()
        })
        content.addView(button("Reset to zero") {
            settings.soberStart = System.currentTimeMillis()
            Haptics.tap(this)
            updateSober()
        })
        val cost = EditText(this).apply {
            hint = "Money you would have spent per day"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (settings.soberCostPerDay > 0f) settings.soberCostPerDay.toString() else "")
        }
        content.addView(cost)
        content.addView(button("Save cost per day") {
            settings.soberCostPerDay = cost.text.toString().toFloatOrNull() ?: 0f
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            updateSober()
        })

        content.addView(heading("Countdown to a date"))
        countdownText = label("")
        content.addView(countdownText)
        val countdownDate = EditText(this).apply { hint = "Date as yyyy-mm-dd, for example 2027-06-01" }
        val countdownLabel = EditText(this).apply { hint = "What it is, for example Holiday" }
        content.addView(countdownDate)
        content.addView(countdownLabel)
        content.addView(button("Save countdown") {
            val date = countdownDate.text.toString().trim()
            val name = countdownLabel.text.toString().trim().ifEmpty { "Countdown" }
            try {
                LocalDate.parse(date)
                settings.countdown = "$date|$name"
                updateCountdown()
            } catch (e: DateTimeParseException) {
                Toast.makeText(this, "Use the form yyyy-mm-dd", Toast.LENGTH_SHORT).show()
            }
        })

        content.addView(heading("To-do"))
        todoList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(todoList)
        val newItem = EditText(this).apply { hint = "New item" }
        content.addView(newItem)
        content.addView(button("Add item") {
            val text = newItem.text.toString().trim()
            if (text.isNotEmpty()) {
                settings.todo = (settings.todo.lines().filter { it.isNotBlank() } + "0|$text").joinToString("\n")
                newItem.setText("")
                renderTodo()
            }
        })
        content.addView(button("Clear finished items") {
            settings.todo = settings.todo.lines().filter { it.startsWith("0|") }.joinToString("\n")
            renderTodo()
        })

        content.addView(heading("Protocols"))
        val protocols = settings.protocols.lines().filter { it.isNotBlank() }
        if (protocols.isEmpty()) {
            content.addView(label("None yet. Add them in Settings, under Tools and about."))
        } else {
            protocols.forEach { content.addView(label("• $it")) }
        }

        content.addView(heading("Notifications"))
        val recent = NotificationsStack.recent()
        if (recent.isEmpty()) {
            content.addView(label("No notifications yet. Allow notification access in Settings, under Charging and launch. Focus apps limit which ones appear."))
        } else {
            recent.take(15).forEach { content.addView(label("• $it")) }
        }

        content.addView(heading("Pomodoros"))
        content.addView(label("Completed today: ${settings.pomodorosToday()}"))
        val history = settings.pomodoroHistory(7)
        content.addView(label("Last 7 days: ${history.sumOf { it.second }}"))
        history.forEach { (day, count) ->
            content.addView(label("$day  ${"▮".repeat(count.coerceAtMost(12))} $count"))
        }

        content.addView(heading("Games and calculator"))
        content.addView(button("Calculator") { startActivity(android.content.Intent(this, CalculatorActivity::class.java)) })
        content.addView(button("Simon memory game") { startActivity(android.content.Intent(this, SimonActivity::class.java)) })

        updateTallies()
        updateSober()
        updateCountdown()
        renderTodo()
        return ScrollView(this).apply { addView(content) }
    }

    private fun tallyColumn(index: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val count = TextView(this@ToolsActivity).apply {
            textSize = 44f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        tallyViews.add(count)
        addView(count)
        addView(button("+1") {
            settings.setTally(index, settings.tally(index) + 1)
            Haptics.tap(this@ToolsActivity)
            updateTallies()
        })
        addView(button("Reset") {
            settings.setTally(index, 0)
            Haptics.tap(this@ToolsActivity)
            updateTallies()
        })
    }

    private fun updateTallies() {
        tallyViews.forEachIndexed { index, view -> view.text = settings.tally(index).toString() }
    }

    private fun updateSober() {
        val start = settings.soberStart
        if (start == 0L) {
            soberTime.text = "Not started"
            soberSaved.text = "Press Start when you begin."
            return
        }
        val minutes = (System.currentTimeMillis() - start) / 60_000L
        val days = minutes / (24 * 60)
        val hours = (minutes / 60) % 24
        val mins = minutes % 60
        soberTime.text = "$days d $hours h $mins min"
        val saved = days * settings.soberCostPerDay
        val moneyLine = if (settings.soberCostPerDay > 0f) {
            "Money saved: %.2f".format(saved)
        } else {
            "Set a cost per day to see money saved."
        }
        val milestone = SOBER_MILESTONES.firstOrNull { it > days }
        val milestoneLine = if (milestone == null) {
            "Every milestone reached."
        } else {
            "Next milestone: $milestone days, ${milestone - days} to go."
        }
        soberSaved.text = "$moneyLine\n$milestoneLine"
    }

    private fun updateCountdown() {
        val parts = settings.countdown.split("|", limit = 2)
        if (parts.size != 2) {
            countdownText.text = "No countdown set."
            return
        }
        try {
            val days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(parts[0]))
            countdownText.text = when {
                days > 0 -> "$days days until ${parts[1]}"
                days == 0L -> "${parts[1]} is today"
                else -> "${parts[1]} was ${-days} days ago"
            }
        } catch (e: DateTimeParseException) {
            countdownText.text = "The saved date is not valid."
        }
    }

    /** One button per to-do item. Tap to mark it done or not done. */
    private fun renderTodo() {
        todoList.removeAllViews()
        val items = settings.todo.lines().filter { it.length > 2 }
        if (items.isEmpty()) {
            todoList.addView(label("Nothing on the list."))
        }
        items.forEachIndexed { index, line ->
            val done = line.startsWith("1|")
            val text = line.substring(2)
            todoList.addView(button(if (done) "✓ $text" else text) {
                val updated = settings.todo.lines().filter { it.length > 2 }.toMutableList()
                updated[index] = (if (done) "0|" else "1|") + text
                settings.todo = updated.joinToString("\n")
                renderTodo()
            })
        }
    }

    private fun heading(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 20f
        setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.WHITE)
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }
}
