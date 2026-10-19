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

/** Spotify-style now-playing card: album art, title, artist, progress bar and transport controls. */
class PlayerCard(context: Context) : LinearLayout(context) {

    /** Called when the radio button is pressed. */
    var onRadio: (() -> Unit)? = null

    private val dp = context.resources.displayMetrics.density
    private val accent = 0xFF1DB954.toInt()

    private val art = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = roundRect(0xFF2A2A2A.toInt(), 8f)
    }
    private val title = line(22f, Color.WHITE, bold = true)
    private val artist = line(16f, 0xFFB3B3B3.toInt(), bold = false)
    private val source = line(12f, 0xFF8A8A8A.toInt(), bold = false)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(accent)
        progressBackgroundTintList = ColorStateList.valueOf(0xFF404040.toInt())
    }
    private val previous = control("Prev", primary = false) { MusicSession.previous() }
    private val playPause = control("Play", primary = true) { MusicSession.playPause() }
    private val next = control("Next", primary = false) { MusicSession.next() }
    private val radio = control("Radio", primary = false) { onRadio?.invoke() }
    private val like = control("Like", primary = false) { MusicSession.like() }

    init {
        orientation = VERTICAL
        background = roundRect(0xFF181818.toInt(), 20f)
        val pad = (16 * dp).toInt()
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
        // The card takes a darkened version of the album's average colour.
        background = roundRect(bitmap?.let { darkAverage(it) } ?: 0xFF181818.toInt(), 20f)
        progress.progress = (MusicSession.progress() * 1000).toInt()
        playPause.text = if (MusicSession.isPlaying()) "Pause" else "Play"
        radio.text = if (RadioPlayer.isPlaying) "Stop radio" else "Radio"
    }

    private fun line(sizeSp: Float, color: Int, bold: Boolean): TextView = TextView(context).apply {
        textSize = sizeSp
        setTextColor(color)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun control(label: String, primary: Boolean, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            background = roundRect(if (primary) accent else 0xFF2A2A2A.toInt(), 999f)
            setOnClickListener { onClick() }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            }
        }

    /** The average colour of the picture, darkened so the text stays readable. */
    private fun darkAverage(bitmap: Bitmap): Int {
        val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        val r = (Color.red(pixel) * 0.35f).toInt()
        val g = (Color.green(pixel) * 0.35f).toInt()
        val b = (Color.blue(pixel) * 0.35f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun roundRect(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * dp
        setColor(color)
    }
}
