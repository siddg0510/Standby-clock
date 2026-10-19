package com.example.standbyclock

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Command Center: five live tiles, one large "hero" tile on top and four small ones below. Tap a tile to make it the hero. */
class CommandCenter(context: Context) : LinearLayout(context) {

    private val dp = context.resources.displayMetrics.density

    init {
        orientation = VERTICAL
    }

    /** [tiles] must hold five texts. [hero] is the index shown large. [onPick] receives the tapped tile's index. */
    fun render(tiles: List<String>, hero: Int, onPick: (Int) -> Unit) {
        removeAllViews()
        if (tiles.isEmpty()) return
        val heroIndex = hero.coerceIn(0, tiles.size - 1)
        val margin = (6 * dp).toInt()
        addView(tile(tiles[heroIndex], big = true, index = heroIndex, onPick = onPick), LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f,
        ).apply { setMargins(margin, margin, margin, margin) })

        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        tiles.indices.filter { it != heroIndex }.forEach { index ->
            row.addView(tile(tiles[index], big = false, index = index, onPick = onPick), LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
            ).apply { setMargins(margin, margin, margin, margin) })
        }
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun tile(text: String, big: Boolean, index: Int, onPick: (Int) -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = if (big) 34f else 16f
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * dp
                setColor(0xFF161616.toInt())
            }
            setOnClickListener { onPick(index) }
        }
}
