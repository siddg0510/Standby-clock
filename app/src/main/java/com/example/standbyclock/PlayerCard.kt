package com.example.standbyclock

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Now-playing card: album art, title, artist, progress bar and transport controls. No fill unless [filled] is set. */
class PlayerCard(context: Context) : LinearLayout(context) {

    /** Called when the radio button is pressed. */
    var onRadio: (() -> Unit)? = null

    private val dp = context.resources.displayMetrics.density
    private val accent = Color.WHITE
    private val neutral = 0xFF3A3A3C.toInt()

    /** Optional card fill (#141416). Off by default, so the card sits on the black background. */
    var filled: Boolean = false
        set(value) {
            field = value
            background = cardBackground()
        }

    private val art = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = roundRect(0xFF2C2C2E.toInt(), 12f)
    }
    private val title = line(22f, Color.WHITE, bold = true)
    private val artist = line(15f, 0xFF8E8E93.toInt(), bold = false)
    private val source = line(15f, 0xFF8E8E93.toInt(), bold = false)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(accent)
        progressBackgroundTintList = ColorStateList.valueOf(neutral)
    }
    private val previous = control("Prev", primary = false) { MusicSession.previous() }
    private val playPause = control("Play", primary = true) { MusicSession.playPause() }
    private val next = control("Next", primary = false) { MusicSession.next() }
    private val radio = control("Radio", primary = false) { onRadio?.invoke() }
    private val like = control("Like", primary = false) { MusicSession.like() }

    init {
        orientation = VERTICAL
        background = cardBackground()
        val pad = (20 * dp).toInt()
        setPadding(pad, pad, pad, pad)

        val details = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(title)
            addView(artist)
            addView(source)
        }
        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(art, LayoutParams((72 * dp).toInt(), (72 * dp).toInt()))
            addView(details, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = (12 * dp).toInt() })
        }
        addView(top)
        addView(progress, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (12 * dp).toInt()
        })
        val controls = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            addView(previous)
            addView(playPause)
            addView(next)
            addView(radio)
        }
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * dp).toInt()
        })
        val extras = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            addView(like)
        }
        addView(extras, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (4 * dp).toInt()
        })
    }

    /** Shows or hides the radio button. It appears only once a radio stream URL has been set. */
    fun showRadio(visible: Boolean) {
        radio.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Pulls the current track, art, progress and button labels from [MusicSession]. */
    fun render() {
        title.text = MusicSession.title().orEmpty()
        artist.text = MusicSession.artist().orEmpty()
        val app = MusicSession.sourceLabel(context)
        source.text = if (app == null) "" else "Playing from $app"
        val bitmap = MusicSession.albumArt()
        art.setImageBitmap(bitmap)
        progress.progress = (MusicSession.progress() * 1000).toInt()
        playPause.text = if (MusicSession.isPlaying()) "Pause" else "Play"
        radio.text = if (RadioPlayer.isPlaying) "Stop radio" else "Radio"
    }

    private fun line(sizeSp: Float, color: Int, bold: Boolean): TextView = TextView(context).apply {
        textSize = sizeSp
        setTextColor(color)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun control(label: String, primary: Boolean, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            setTextColor(if (primary) Color.BLACK else Color.WHITE)
            background = roundRect(if (primary) accent else 0xFF2C2C2E.toInt(), 999f)
            setOnClickListener { onClick() }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            }
        }

    /** Transparent by default; #141416 when [filled]. */
    private fun cardBackground(): GradientDrawable = roundRect(if (filled) 0xFF141416.toInt() else Color.TRANSPARENT, 28f)

    private fun roundRect(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * dp
        setColor(color)
    }
}
