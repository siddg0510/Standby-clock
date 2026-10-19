package com.example.standbyclock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Command Center: five live tiles, one large "hero" tile on top and four small label and value tiles below it.
 * When the hero is the time tile its digits are about 35% of the screen height. Tap a tile to make it the hero.
 */
class CommandCenter(context: Context) : LinearLayout(context) {

    private val dp = context.resources.displayMetrics.density
    private val screen = context.resources.displayMetrics
    private val bold = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 700, false)

    init {
        orientation = VERTICAL
    }

    /**
     * [tiles] must hold five label and value pairs. [hero] is the index shown large. [timeIndex] is the tile that holds
     * the time. [onPick] receives the tapped tile's index.
     */
    fun render(tiles: List<Pair<String, String>>, hero: Int, timeIndex: Int, onPick: (Int) -> Unit) {
        removeAllViews()
        if (tiles.isEmpty()) return
        val heroIndex = hero.coerceIn(0, tiles.size - 1)
        val gap = (8 * dp).toInt()
        addView(tile(tiles[heroIndex], big = true, isTime = heroIndex == timeIndex, index = heroIndex, onPick = onPick), LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f,
        ).apply { setMargins(gap, gap, gap, gap) })

        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        tiles.indices.filter { it != heroIndex }.forEach { index ->
            row.addView(tile(tiles[index], big = false, isTime = false, index = index, onPick = onPick), LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
            ).apply { setMargins(gap, gap, gap, gap) })
        }
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.3f))
    }

    private fun tile(pair: Pair<String, String>, big: Boolean, isTime: Boolean, index: Int, onPick: (Int) -> Unit): FrameLayout {
        val pad = (20 * dp).toInt()
        val label = TextView(context).apply {
            text = pair.first
            textSize = 15f
            setTextColor(0xFF8E8E93.toInt())
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val value = TextView(context).apply {
            text = pair.second
            setTextColor(Color.WHITE)
            typeface = bold
            fontFeatureSettings = "tnum"
            gravity = Gravity.START or Gravity.BOTTOM
            maxLines = if (big && !isTime) 3 else if (isTime) 1 else 4
            ellipsize = TextUtils.TruncateAt.END
        }
        val valueParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START,
        )
        if (isTime) {
            // The digits are 35% of the screen height, or as large as the tile is wide, and sit on the tile's bottom edge.
            val tileWidth = screen.widthPixels * 0.94f
            val fit = DigitFit.fit(bold, "00:00", screen.heightPixels * 0.35f, tileWidth - 2 * (pad + 8 * dp))
            value.setTextSize(TypedValue.COMPLEX_UNIT_PX, fit.textSizePx)
            value.includeFontPadding = false
            valueParams.bottomMargin = -fit.bottomSlackPx
        } else {
            value.textSize = if (big) 64f else 28f
        }
        return FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28 * dp
                setColor(0xFF141416.toInt())
            }
            addView(value, valueParams)
            addView(label, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START,
            ))
            setOnClickListener { onPick(index) }
        }
    }
}
