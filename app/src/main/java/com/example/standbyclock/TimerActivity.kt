package com.example.standbyclock

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Countdown timer and stopwatch. A 25-minute timer counts as a Pomodoro. Tap the time to see when
 * the current timer ends. The stopwatch keeps laps. Values reset when the screen closes.
 */
class TimerActivity : Activity() {

    private companion object {
        const val WORK_MS = 25 * 60_000L
        const val BREAK_MS = 5 * 60_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: Settings
    private var onBreak = false        // Pomodoro cycle: false is the work phase, true the break
    private var stopwatch = false
    private var presetMs = 5 * 60_000L
    private var elapsedMs = presetMs   // remaining (timer) or accumulated (stopwatch) while paused
    private var runningSince = 0L      // elapsedRealtime when started; 0 while paused
    private var ringtone: Ringtone? = null
    private val laps = mutableListOf<Long>()

    private lateinit var display: TextView
    private lateinit var startPause: Button
    private lateinit var presetRow: LinearLayout
    private lateinit var lapRow: LinearLayout
    private lateinit var lapText: TextView
    private lateinit var pomodoroText: TextView

    private val tick = object : Runnable {
        override fun run() {
            render()
            if (runningSince != 0L) handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildUi())
        render()
    }

    override fun onStop() {
        pause()
        super.onStop()
    }

    private fun buildUi(): LinearLayout {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad, pad, pad)
        }
        display = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 120f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setOnClickListener { showEndTime() }
        }
        root.addView(display)
        root.addView(row(listOf(
            button("Timer") { setMode(false) },
            button("Stopwatch") { setMode(true) },
        )))
        presetRow = row(listOf(1, 5, 10, 25).map { minutes ->
            button(if (minutes == 25) "25 min (Pomodoro)" else "$minutes min") { setPreset(minutes * 60_000L) }
        })
        root.addView(presetRow)
        startPause = button("Start") { toggle() }
        root.addView(row(listOf(startPause, button("Reset") { reset() })))

        lapRow = row(listOf(button("Lap") { addLap() }))
        lapRow.visibility = View.GONE
        root.addView(lapRow)
        lapText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        root.addView(lapText)

        pomodoroText = TextView(this).apply {
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
        }
        root.addView(pomodoroText)
        return root
    }

    /** Elapsed time for the stopwatch, or time left for the timer. */
    private fun value(): Long {
        val delta = if (runningSince == 0L) 0L else SystemClock.elapsedRealtime() - runningSince
        return if (stopwatch) elapsedMs + delta else maxOf(0L, elapsedMs - delta)
    }

    private fun toggle() {
        if (runningSince == 0L) start() else pause()
    }

    private fun start() {
        if (!stopwatch && elapsedMs <= 0L) return   // a finished timer needs a reset or a preset first
        stopRinging()
        runningSince = SystemClock.elapsedRealtime()
        startPause.text = "Pause"
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun pause() {
        if (runningSince == 0L) return
        elapsedMs = value()
        runningSince = 0L
        handler.removeCallbacks(tick)
        startPause.text = "Start"
        render()
    }

    private fun reset() {
        pause()
        stopRinging()
        laps.clear()
        elapsedMs = if (stopwatch) 0L else presetMs
        render()
    }

    private fun setPreset(ms: Long) {
        pause()
        stopRinging()
        presetMs = ms
        elapsedMs = ms
        render()
    }

    private fun setMode(isStopwatch: Boolean) {
        pause()
        stopRinging()
        stopwatch = isStopwatch
        laps.clear()
        elapsedMs = if (stopwatch) 0L else presetMs
        presetRow.visibility = if (stopwatch) View.GONE else View.VISIBLE
        lapRow.visibility = if (stopwatch) View.VISIBLE else View.GONE
        render()
    }

    private fun addLap() {
        if (!stopwatch) return
        laps += value()
        Haptics.tap(this)
        render()
    }

    /** Tapping the time tells you when the countdown will finish. */
    private fun showEndTime() {
        if (stopwatch) {
            Toast.makeText(this, "Stopwatch counts up. Use Lap to note split times.", Toast.LENGTH_SHORT).show()
            return
        }
        val endsAt = System.currentTimeMillis() + value()
        Toast.makeText(this, "Ends at ${DateFormat.format("HH:mm", endsAt)}", Toast.LENGTH_SHORT).show()
    }

    private fun render() {
        val now = value()
        if (!stopwatch && runningSince != 0L && now == 0L) {
            // Timer reached zero while running: stop, alert, and count a Pomodoro if this was one.
            pause()
            alert()
            if (presetMs == WORK_MS) settings.addPomodoro()
            if (settings.pomodoroCycle && (presetMs == WORK_MS || presetMs == BREAK_MS)) {
                // Move to the other phase and start it after the alert has rung for a moment.
                onBreak = !onBreak
                presetMs = if (onBreak) BREAK_MS else WORK_MS
                elapsedMs = presetMs
                handler.postDelayed({ start() }, 3000L)
            }
        }
        display.text = if (stopwatch) format(now, withTenths = true) else format(now + 999, withTenths = false)
        lapText.text = if (stopwatch && laps.isNotEmpty()) {
            laps.mapIndexed { index, lap -> "Lap ${index + 1}: ${format(lap, withTenths = true)}" }
                .takeLast(5)
                .joinToString("\n")
        } else {
            ""
        }
        val phase = if (settings.pomodoroCycle) " · ${if (onBreak) "break" else "work"}" else ""
        pomodoroText.text = "Pomodoros today: ${settings.pomodorosToday()}$phase"
    }

    /** Sound, vibration, or both, as chosen in settings. */
    private fun alert() {
        val mode = settings.alertMode
        if (mode == "sound" || mode == "both") {
            ringtone = RingtoneManager.getRingtone(
                this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            )?.also { it.play() }
        }
        if (mode == "vibrate" || mode == "both") {
            val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
            }
        }
    }

    private fun format(ms: Long, withTenths: Boolean): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds / 60) % 60
        val seconds = totalSeconds % 60
        val base = if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
        return if (withTenths) base + ".%d".format((ms % 1000) / 100) else base
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun row(views: List<View>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        views.forEach { view ->
            addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(8, 8, 8, 8) },
            )
        }
    }
}
