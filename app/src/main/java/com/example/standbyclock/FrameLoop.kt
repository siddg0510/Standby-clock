package com.example.standbyclock

import android.view.View

/**
 * A per-frame animation loop for a view. It runs on the display's frame clock (postOnAnimation), hands the step the
 * real elapsed time in seconds (clamped to 50 ms so a stall never makes things jump), and invalidates the view.
 *
 * The loop only runs while it is wanted ([setRunning]) and the view is visible ([visibilityChanged]), and it is always
 * stopped on [detach]. The owning view forwards those three callbacks.
 */
class FrameLoop(private val view: View, private val onFrame: (Float) -> Unit) {

    private var wanted = false
    private var visible = true
    private var active = false
    private var lastNanos = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, MAX_STEP_SECONDS)
            lastNanos = now
            onFrame(dt)
            view.invalidate()
            if (active) view.postOnAnimation(this)
        }
    }

    fun setRunning(on: Boolean) {
        wanted = on
        sync()
    }

    /** Forward from the view's onVisibilityAggregated. */
    fun visibilityChanged(isVisible: Boolean) {
        visible = isVisible
        sync()
    }

    /** Forward from the view's onDetachedFromWindow. */
    fun detach() {
        wanted = false
        sync()
    }

    private fun sync() {
        val shouldRun = wanted && visible
        if (shouldRun == active) return
        active = shouldRun
        if (shouldRun) {
            lastNanos = System.nanoTime()
            view.postOnAnimation(tick)
        } else {
            view.removeCallbacks(tick)
        }
    }

    private companion object {
        const val MAX_STEP_SECONDS = 0.05f
    }
}
