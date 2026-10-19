package com.example.standbyclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flip-card digits: four tiles with a split line, like a mechanical flip clock. When a digit changes the tile folds:
 * the top half of the old digit swings down over the new one, then the new digit's bottom half swings down over the
 * old one. One animator of about 300 ms drives the fold; each half is a clipped, vertically scaled draw.
 */
class RetroflipView(context: Context) : View(context) {

    private var digits = "    "
    private var previous = "    "
    /** 0 at the start of a fold and 1 when it is done (or when nothing is folding). */
    private var fold = 1f
    private var folder: ValueAnimator? = null
    private val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1B.toInt() }
    private val seam = Paint().apply { color = Color.BLACK }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 700, false)
        fontFeatureSettings = "tnum"
    }
    private val rect = RectF()
    private val charBuf = CharArray(1)
    private val fontMetrics = Paint.FontMetrics()
    private var opaque = true
    private var style = 0
    private var theme: ThemePreset? = null

    /** Style 0 is dark cards, 1 is light cards, 2 is warm. Opaque off drops the card background. */
    fun setStyle(style: Int, cardBackground: Boolean) {
        this.style = style
        opaque = cardBackground
        applyColours()
    }

    /** A theme replaces the card style's colours (cards from its background and accent, digits in its foreground). */
    fun setTheme(value: ThemePreset?) {
        theme = value
        applyColours()
    }

    private fun applyColours() {
        val t = theme
        if (t != null) {
            tile.color = Themes.mix(t.background, t.accent, 0.2f)
            glyph.color = t.foreground
            seam.color = t.background
        } else {
            val (cardColor, digitColor) = when (style) {
                1 -> 0xFFEDEDED.toInt() to Color.BLACK
                2 -> 0xFF3A2A1A.toInt() to 0xFFFFD58A.toInt()
                else -> 0xFF1B1B1B.toInt() to Color.WHITE
            }
            tile.color = cardColor
            glyph.color = digitColor
            seam.color = if (style == 1) 0xFFBDBDBD.toInt() else Color.BLACK
        }
        glyph.shader = Themes.digitShader(t, 0f, height.toFloat())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        glyph.shader = Themes.digitShader(theme, 0f, h.toFloat())
    }

    /**
     * Four characters: two hour digits (a leading space for a blank) then two minute digits. With [animate] the
     * tiles whose digit changed fold; otherwise the new digits appear at once and any fold in flight is dropped.
     */
    fun setTime(hour24: Int, minute: Int, use24Hour: Boolean, animate: Boolean = false) {
        val hour = if (use24Hour) hour24 else (hour24 % 12).let { if (it == 0) 12 else it }
        val hourText = if (use24Hour || hour >= 10) "%02d".format(hour) else " $hour"
        val next = hourText + "%02d".format(minute)
        if (next == digits) return
        folder?.cancel()
        folder = null
        if (animate && isAttachedToWindow) {
            previous = digits
            digits = next
            fold = 0f
            folder = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = FOLD_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    fold = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            digits = next
            previous = next
            fold = 1f
        }
        invalidate()
    }

    /** Ends a fold in flight with the new digits showing. */
    fun finishFold() {
        folder?.cancel()
        folder = null
        previous = digits
        fold = 1f
        invalidate()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) finishFold()
    }

    override fun onDetachedFromWindow() {
        finishFold()
        super.onDetachedFromWindow()
    }

    private fun drawGlyph(canvas: Canvas, ch: Char, x: Float, baseline: Float) {
        charBuf[0] = ch
        canvas.drawText(charBuf, 0, 1, x, baseline, glyph)
    }

    /** One half of a tile (card and digit), squashed vertically towards the seam by [scaleY]. */
    private fun drawHalf(
        canvas: Canvas, left: Float, tileWidth: Float, tileHeight: Float, radius: Float,
        ch: Char, baseline: Float, top: Boolean, scaleY: Float,
    ) {
        if (scaleY <= 0.01f) return
        canvas.save()
        canvas.scale(1f, scaleY, left + tileWidth / 2, tileHeight / 2)
        if (top) {
            canvas.clipRect(left, 0f, left + tileWidth, tileHeight / 2)
        } else {
            canvas.clipRect(left, tileHeight / 2, left + tileWidth, tileHeight)
        }
        rect.set(left, 0f, left + tileWidth, tileHeight)
        if (opaque) canvas.drawRoundRect(rect, radius, radius, tile)
        drawGlyph(canvas, ch, left + tileWidth / 2, baseline)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = width * 0.02f
        val tileWidth = (width - gap * 3) / 4f
        val tileHeight = height.toFloat()
        glyph.textSize = tileHeight * 0.72f
        val radius = tileWidth * 0.08f
        glyph.getFontMetrics(fontMetrics)
        val baseline = tileHeight / 2 - (fontMetrics.ascent + fontMetrics.descent) / 2
        for (i in 0 until 4) {
            val left = i * (tileWidth + gap)
            if (fold < 1f && previous[i] != digits[i]) {
                // Behind the flap: the new digit above, the old digit below.
                drawHalf(canvas, left, tileWidth, tileHeight, radius, digits[i], baseline, top = true, scaleY = 1f)
                drawHalf(canvas, left, tileWidth, tileHeight, radius, previous[i], baseline, top = false, scaleY = 1f)
                if (fold < 0.5f) {
                    drawHalf(canvas, left, tileWidth, tileHeight, radius, previous[i], baseline, top = true, scaleY = 1f - 2f * fold)
                } else {
                    drawHalf(canvas, left, tileWidth, tileHeight, radius, digits[i], baseline, top = false, scaleY = 2f * fold - 1f)
                }
            } else {
                rect.set(left, 0f, left + tileWidth, tileHeight)
                if (opaque) canvas.drawRoundRect(rect, radius, radius, tile)
                drawGlyph(canvas, digits[i], left + tileWidth / 2, baseline)
            }
            canvas.drawRect(left, tileHeight / 2 - 3f, left + tileWidth, tileHeight / 2 + 3f, seam)
        }
    }

    private companion object {
        const val FOLD_MS = 300L
    }
}

/** Sun path for the time of day: sunrise at 06:00 on the left, noon at the top, sunset at 18:00 on the right. */
class SolarView(context: Context) : View(context) {

    private var minutesOfDay = 0
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC857.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFC857 }
    private val sky = Paint()
    private var theme: ThemePreset? = null
    private val arcPath = Path()
    private val arcBounds = RectF()
    private var skyTop = 0
    private var skyHorizon = 0
    private var skyHeight = -1

    /** Recolours the sun and arc from a theme, and tints the sky towards its colours, or back to the default for null. */
    fun setTheme(value: ThemePreset?) {
        theme = value
        arc.color = value?.let { Color.argb(0x55, Color.red(it.foreground), Color.green(it.foreground), Color.blue(it.foreground)) } ?: 0x55FFFFFF
        sun.color = value?.accent ?: 0xFFFFC857.toInt()
        glow.color = value?.let { Color.argb(0x44, Color.red(it.accent), Color.green(it.accent), Color.blue(it.accent)) } ?: 0x44FFC857
        sky.shader = null
        rebuildSky()
        invalidate()
    }

    fun setTime(hour24: Int, minute: Int) {
        minutesOfDay = hour24 * 60 + minute
        rebuildSky()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        sky.shader = null
        skyHeight = -1
        rebuildSky()
        val radiusX = w / 2f - 24f
        val cx = w / 2f
        val baseY = h * 0.9f
        val radiusY = h * 0.85f
        arcBounds.set(cx - radiusX, baseY - radiusY, cx + radiusX, baseY + radiusY)
        arcPath.rewind()
        arcPath.moveTo(cx - radiusX, baseY)
        arcPath.arcTo(arcBounds, 180f, 180f)
    }

    /** Rebuilds the sky gradient when the band of the day, the theme or the height changed: from deep blue at night, through orange at dawn and dusk, to pale blue in the day. */
    private fun rebuildSky() {
        if (height <= 0) return
        val m = minutesOfDay
        var top: Int
        var horizon: Int
        when {
            m in 350..440 || m in 1000..1100 -> { top = 0xFF1F3A68.toInt(); horizon = 0xFFFF9A5C.toInt() } // dawn and dusk
            m in 441..999 -> { top = 0xFF2B6CB0.toInt(); horizon = 0xFFBFE3FF.toInt() }                     // day
            else -> { top = 0xFF050A1F.toInt(); horizon = 0xFF1B2A4A.toInt() }                             // night
        }
        theme?.let {
            top = Themes.mix(top, it.background, 0.5f)
            horizon = Themes.mix(horizon, it.secondary, 0.35f)
        }
        if (sky.shader != null && top == skyTop && horizon == skyHorizon && skyHeight == height) return
        skyTop = top
        skyHorizon = horizon
        skyHeight = height
        sky.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, horizon, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)
        val radiusX = width / 2f - 24f
        val cx = width / 2f
        val baseY = height * 0.9f
        val radiusY = height * 0.85f
        canvas.drawPath(arcPath, arc)
        canvas.drawLine(cx - radiusX, baseY, cx + radiusX, baseY, arc)

        // Map 06:00 -> 0 and 18:00 -> pi; the sun dips below the horizon overnight.
        val theta = Math.PI * (((minutesOfDay - 360 + 1440) % 1440) / 720.0)
        val x = cx - radiusX * cos(theta).toFloat()
        val y = baseY - radiusY * sin(theta).toFloat()
        canvas.drawCircle(x, y, 34f, glow)
        canvas.drawCircle(x, y, 18f, sun)
    }
}

/** Ring of 60 minute dots that fill up as the minutes pass, with the time in the middle. */
class RadialView(context: Context) : View(context) {

    private var hour = 0
    private var minute = 0
    private var use24Hour = false
    private var invert = false
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var theme: ThemePreset? = null
    private var timeText = ""
    private val labelMetrics = Paint.FontMetrics()

    /** Recolours the ring from a theme (lit dots in its accent, time in its foreground, with its gradient), or back to white. */
    fun setTheme(value: ThemePreset?) {
        theme = value
        val fg = value?.foreground ?: Color.WHITE
        dot.color = Color.argb(0x33, Color.red(fg), Color.green(fg), Color.blue(fg))
        lit.color = value?.accent ?: Color.WHITE
        label.color = fg
        applyShader()
        invalidate()
    }

    private fun applyShader() {
        label.shader = Themes.digitShader(theme, height * 0.3f, height * 0.7f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        applyShader()
    }

    /** Inverted: the minutes that have passed are dim and the remaining minutes are bright. */
    fun setInvert(on: Boolean) {
        if (on == invert) return
        invert = on
        invalidate()
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 100, false)
        fontFeatureSettings = "tnum"
    }

    fun setTime(hour24: Int, minuteOfHour: Int, use24: Boolean) {
        hour = hour24
        minute = minuteOfHour
        use24Hour = use24
        val h = if (use24) hour24 else (hour24 % 12).let { if (it == 0) 12 else it }
        timeText = if (use24) "%02d:%02d".format(h, minuteOfHour) else "%d:%02d".format(h, minuteOfHour)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val ring = min(cx, cy) * 0.88f
        for (i in 0 until 60) {
            val angle = Math.toRadians(i * 6.0 - 90.0)
            val x = cx + (cos(angle) * ring).toFloat()
            val y = cy + (sin(angle) * ring).toFloat()
            val radius = if (i % 5 == 0) ring * 0.05f else ring * 0.025f
            val passed = i < minute
            val bright = if (invert) !passed else passed
            canvas.drawCircle(x, y, radius, if (bright) lit else dot)
        }
        label.textSize = ring * 0.42f
        label.getFontMetrics(labelMetrics)
        canvas.drawText(timeText, cx, cy - (labelMetrics.ascent + labelMetrics.descent) / 2, label)
    }
}

/** Falling green digit columns behind the time, run on the frame clock while shown. */
class MatrixRainView(context: Context) : View(context) {

    private val glyphs = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF66.toInt()
        textSize = 28f
        typeface = Typeface.MONOSPACE
        fontFeatureSettings = "tnum"
    }
    private val columnWidth = 28f
    private var heads = FloatArray(0)
    private var speeds = FloatArray(0)
    // One bit per glyph (column by trail position), re-picked on a slow timer rather than on every draw.
    private var bits = BooleanArray(0)
    private var sinceShuffle = 0f
    private val loop = FrameLoop(this) { dt ->
        advance(dt)
        sinceShuffle += dt
        if (sinceShuffle >= SHUFFLE_SECONDS) {
            sinceShuffle = 0f
            for (i in bits.indices) bits[i] = Random.nextBoolean()
        }
    }

    fun setRunning(on: Boolean) = loop.setRunning(on)

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        loop.visibilityChanged(isVisible)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        val count = (w / columnWidth).toInt() + 1
        heads = FloatArray(count) { -Random.nextFloat() * h }
        // Pixels per second.
        speeds = FloatArray(count) { 220f + Random.nextFloat() * 440f }
        bits = BooleanArray(count * TRAIL) { Random.nextBoolean() }
    }

    private fun advance(dt: Float) {
        for (i in heads.indices) {
            heads[i] += speeds[i] * dt
            if (heads[i] > height + 300f) heads[i] = -Random.nextFloat() * height
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in heads.indices) {
            val x = i * columnWidth
            for (k in 0 until TRAIL) {
                glyphs.alpha = (255 - k * 18).coerceAtLeast(20)
                val y = heads[i] - k * 28f
                canvas.drawText(if (bits[i * TRAIL + k]) "1" else "0", x, y, glyphs)
            }
        }
    }

    override fun onDetachedFromWindow() {
        loop.detach()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TRAIL = 14
        const val SHUFFLE_SECONDS = 0.15f
    }
}
