package com.example.standbyclock

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.min

/**
 * Sizes digits by what is drawn, not by the font's line box. A text size is picked so the digits are
 * [targetDigitHeightPx] tall (cap height of the digits) unless the string would then be wider than [maxWidthPx].
 * The empty space a line box keeps above the digits and below the baseline is reported so a view can pull it in with
 * negative margins and the digits sit exactly where the layout grid puts them.
 */
internal object DigitFit {

    class Fit(val textSizePx: Float, val widthPx: Float, val digitHeightPx: Float, val topSlackPx: Int, val bottomSlackPx: Int)

    fun fit(typeface: Typeface, sample: String, targetDigitHeightPx: Float, maxWidthPx: Float): Fit {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = REFERENCE
            fontFeatureSettings = "tnum"
        }
        val bounds = Rect()
        paint.getTextBounds("0123456789", 0, 10, bounds)
        val digitHeight = (-bounds.top).toFloat().coerceAtLeast(1f)
        val width = paint.measureText(sample).coerceAtLeast(1f)
        val size = min(targetDigitHeightPx / digitHeight * REFERENCE, maxWidthPx / width * REFERENCE)
        val scale = size / REFERENCE
        val metrics = paint.fontMetrics
        return Fit(
            textSizePx = size,
            widthPx = width * scale,
            digitHeightPx = digitHeight * scale,
            topSlackPx = ((-metrics.ascent - digitHeight) * scale).toInt().coerceAtLeast(0),
            bottomSlackPx = ((metrics.descent - bounds.bottom) * scale).toInt().coerceAtLeast(0),
        )
    }

    private const val REFERENCE = 100f
}
