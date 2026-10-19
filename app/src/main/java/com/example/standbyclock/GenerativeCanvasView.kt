package com.example.standbyclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Slowly drifting coloured glows that play behind the sounds. Runs on the frame clock while shown. Each glow's
 * gradient is built once around the origin and the canvas is moved to the glow's position, so the gradients are
 * only rebuilt about once a second (when the hue has drifted) or when the size changes.
 */
class GenerativeCanvasView(context: Context) : View(context) {

    private val count = 6
    private val speed = FloatArray(count) { 0.2f + Random.nextFloat() * 0.5f }
    private val phase = FloatArray(count) { Random.nextFloat() * 6.28f }
    private val hue = FloatArray(count) { Random.nextFloat() * 360f }
    private val hsv = FloatArray(3)
    private val shaders = arrayOfNulls<RadialGradient>(count)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var time = 0f
    private var sinceRebuild = REBUILD_SECONDS
    private var radius = 0f
    private val loop = FrameLoop(this) { dt ->
        time += 0.5f * dt
        // Two degrees of hue a second.
        for (i in hue.indices) hue[i] = (hue[i] + 2f * dt) % 360f
        sinceRebuild += dt
        if (sinceRebuild >= REBUILD_SECONDS) rebuildShaders()
    }

    fun setRunning(on: Boolean) = loop.setRunning(on)

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        loop.visibilityChanged(isVisible)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        radius = w.coerceAtLeast(h) * 0.35f
        rebuildShaders()
    }

    private fun rebuildShaders() {
        sinceRebuild = 0f
        if (radius <= 0f) return
        for (i in 0 until count) {
            hsv[0] = hue[i]
            hsv[1] = 0.7f
            hsv[2] = 1f
            val color = Color.HSVToColor(0x44, hsv)
            shaders[i] = RadialGradient(0f, 0f, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        for (i in 0 until count) {
            val shader = shaders[i] ?: continue
            val cx = width * (0.5f + 0.4f * sin(time * speed[i] + phase[i]))
            val cy = height * (0.5f + 0.4f * sin(time * speed[i] * 0.7f + phase[i] * 2f))
            paint.shader = shader
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, radius, paint)
            canvas.restore()
        }
        paint.shader = null
    }

    override fun onDetachedFromWindow() {
        loop.detach()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REBUILD_SECONDS = 1f
    }
}
