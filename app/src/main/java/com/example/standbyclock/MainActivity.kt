package com.example.standbyclock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import android.view.GestureDetector
import android.view.OrientationEventListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.abs

/**
 * Full-screen clock. Opened from the launcher, the charging notification, or the settings screen.
 *
 * Gestures: tap cycles faces, swipe up or down goes to the next or previous face, hold opens settings,
 * and double-tap closes the app (when that is turned on). After a set time without touches the
 * screen goes black; the next touch wakes it.
 */
class MainActivity : Activity() {

    private lateinit var clock: ClockUi
    private lateinit var sleepOverlay: View
    private lateinit var gestures: GestureDetector
    private lateinit var pinch: ScaleGestureDetector
    private val settings by lazy { Settings(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable { sleep() }
    private var asleep = false
    private var nightNow = false
    private var fromCharging = false
    private var unpluggedRegistered = false
    private var uprightWatch: OrientationEventListener? = null

    private val unplugged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // A charging launch closes as soon as the charger is removed.
            if (intent.action == Intent.ACTION_POWER_DISCONNECTED) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fromCharging = intent.getBooleanExtra(EXTRA_FROM_CHARGING, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (fromCharging) {
            // The charging launch shows over the lock screen and wakes the panel.
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        requestedOrientation = when (settings.orientation) {
            Settings.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            Settings.ORIENTATION_AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        clock = ClockUi(this)
        clock.onMinute = { night ->
            nightNow = night
            applyBrightness(night)
        }
        sleepOverlay = View(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            setOnTouchListener { _, _ ->
                wake()
                true
            }
        }
        val frame = FrameLayout(this).apply {
            addView(clock.root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(sleepOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(frame)

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (settings.doubleTapClose) finish()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                // Swipe left or right moves between groups of screens.
                if (abs(dx) > abs(dy) && abs(dx) > SWIPE_PX) {
                    changeGroup(forward = dx < 0)
                    return true
                }
                if (abs(dy) < SWIPE_PX) return false
                // Swipe up goes forward through the screens in the group; swipe down goes back.
                changeFace(forward = dy < 0)
                return true
            }
        })

        // A pinch with two fingers opens the overview of every screen.
        pinch = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                startActivity(Intent(this@MainActivity, OverviewActivity::class.java))
            }
        })

        // Every touch on the clock counts as activity; the gesture and pinch detectors see them too.
        clock.root.setOnTouchListener { _, event ->
            resetSleepTimer()
            pinch.onTouchEvent(event)
            gestures.onTouchEvent(event)
            false
        }
        clock.root.setOnClickListener {
            Haptics.tap(this)
            changeFace(forward = true)
        }
        clock.root.setOnLongClickListener {
            Haptics.tap(this)
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        // The sensor works while the activity is locked to landscape, so a charging launch can close when the phone stands upright.
        uprightWatch = object : OrientationEventListener(this) {
            override fun onOrientationChanged(angle: Int) {
                val upright = angle >= 0 && (angle <= 25 || angle >= 335)
                if (upright && fromCharging && settings.closeWhenUpright) finish()
            }
        }
        if (!settings.hintShown) {
            settings.hintShown = true
            showTour(this, 0)
        }
    }

    private fun changeFace(forward: Boolean) {
        val face = settings.cycleFace(forward)
        clock.reloadFace()
        Toast.makeText(this, faceName(face), Toast.LENGTH_SHORT).show()
    }

    private fun changeGroup(forward: Boolean) {
        val face = settings.switchGroup(forward)
        clock.reloadFace()
        Toast.makeText(this, faceName(face), Toast.LENGTH_SHORT).show()
    }

    private fun faceName(face: String): String =
        Settings.FACE_OPTIONS.firstOrNull { it.first == face }?.second ?: face

    override fun onStart() {
        super.onStart()
        hideSystemBars()
        ChargingLauncher.cancel(this)
        if (fromCharging) registerUnplugged()
        if (fromCharging && settings.closeWhenUpright) uprightWatch?.enable()
        clock.start()
        resetSleepTimer()
    }

    override fun onStop() {
        clock.stop()
        uprightWatch?.disable()
        unregisterUnplugged()
        handler.removeCallbacks(sleepRunnable)
        super.onStop()
    }

    /** After the set number of minutes without touches, the screen goes black. Zero means never. */
    private fun resetSleepTimer() {
        handler.removeCallbacks(sleepRunnable)
        val minutes = settings.sleepMinutes
        if (minutes > 0 && !asleep) handler.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    /** Black screen, minimum brightness, and no keep-awake flag, so the phone's own timeout can turn the panel off. */
    private fun sleep() {
        asleep = true
        sleepOverlay.visibility = View.VISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val params = window.attributes
        params.screenBrightness = MIN_BRIGHTNESS
        window.attributes = params
    }

    private fun wake() {
        if (asleep) {
            asleep = false
            sleepOverlay.visibility = View.GONE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            applyBrightness(nightNow)
        }
        resetSleepTimer()
    }

    private fun registerUnplugged() {
        if (unpluggedRegistered) return
        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unplugged, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unplugged, filter)
        }
        unpluggedRegistered = true
    }

    private fun unregisterUnplugged() {
        if (!unpluggedRegistered) return
        unregisterReceiver(unplugged)
        unpluggedRegistered = false
    }

    /** Night uses the night brightness; day uses the day brightness, or the system setting at 100%. */
    private fun applyBrightness(night: Boolean) {
        if (asleep) return
        val params = window.attributes
        params.screenBrightness = when {
            night -> settings.nightBrightness.coerceIn(1, 50) / 100f
            settings.dayBrightness >= 100 -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else -> settings.dayBrightness.coerceIn(10, 100) / 100f
        }
        window.attributes = params
    }

    private fun hideSystemBars() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_BARS_BY_SWIPE
        }
    }

    companion object {
        const val EXTRA_FROM_CHARGING = "from_charging"
        private const val SWIPE_PX = 120
        private const val MIN_BRIGHTNESS = 0.01f

        /** The walkthrough, shown on first launch and from More settings. Each page is one dialog. */
        fun showTour(activity: Activity, step: Int = 0) {
            val pages = listOf(
                "Tap the clock to change the face. Swipe up or down to move through the faces in a group, and swipe left or right to change group.",
                "Pinch to see every screen at once. Hold the clock to open settings. Double-tap closes the app, if that is turned on.",
                "Quick launch: in settings, choose When charging to open the clock when the charger goes in. Allow notifications and full-screen alerts so it can open from the lock screen.",
                "Screensaver: in Settings, Display, Screen saver, choose Standby Clock. Widget: long-press an empty spot on the home screen, choose Widgets, then pick Standby Clock.",
                "Music controls need notification access, which you can turn on in settings. The Duo and panels face lets you put several items side by side.",
            )
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Getting started (${step + 1} of ${pages.size})")
                .setMessage(pages[step])
                .setPositiveButton(if (step == pages.lastIndex) "Done" else "Next") { _, _ ->
                    if (step < pages.lastIndex) showTour(activity, step + 1)
                }
            if (step > 0) {
                dialog.setNegativeButton("Back") { _, _ -> showTour(activity, step - 1) }
            }
            dialog.show()
        }
    }
}
