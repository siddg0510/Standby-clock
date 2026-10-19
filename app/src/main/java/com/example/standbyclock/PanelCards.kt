package com.example.standbyclock

// The calendar and now-playing cards are adapted from the widget layouts in Standby Pro
// (https://github.com/Ghostzy0/standby-pro), MIT License, Copyright (c) 2026 TheFadGhost. See NOTICE.md.

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** A card with a small heading and a list of up to three upcoming events, left-aligned and without a fill. */
class AgendaCard(context: Context) : LinearLayout(context) {

    private val rows = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        addView(heading(context, "Calendar"))
        addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Reloads the events. Called once a minute by the panel, since the calendar query is not cheap. */
    fun update(now: Long) {
        rows.removeAllViews()
        val events = Agenda.upcoming(context, days = 1, limit = 3)
        if (events.isEmpty()) {
            rows.addView(TextView(context).apply {
                text = "No events today"
                setTextColor(0xFF8E8E93.toInt())
                textSize = 20f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                gravity = Gravity.START
            })
            return
        }
        events.forEach { event ->
            val minutes = (event.begin - now) / 60_000L
            val soon = if (minutes in 0..60) "  in ${minutes} min" else ""
            rows.addView(pill(context, "${DateFormat.format("HH:mm", event.begin)}  ${event.title}$soon"))
        }
    }
}

/** A now-playing card: title, artist, a progress bar, and previous, play or pause and next buttons. */
class MusicCard(context: Context) : LinearLayout(context) {

    private val title = TextView(context).apply {
        textSize = 40f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START
    }
    private val artist = TextView(context).apply {
        textSize = 20f
        setTextColor(0xFF8E8E93.toInt())
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START
    }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(Color.WHITE)
        progressBackgroundTintList = ColorStateList.valueOf(0xFF3A3A3C.toInt())
    }
    private val status = TextView(context).apply {
        textSize = 15f
        setTextColor(0xFF8E8E93.toInt())
        gravity = Gravity.START
    }
    private val playPause = Button(context).apply {
        text = "Play"
        setOnClickListener {
            MusicSession.playPause()
            update()
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        addView(heading(context, "Music"))
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(artist, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(progress, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 12 })
        val controls = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.START
            addView(button(context, "Prev") { MusicSession.previous(); update() })
            addView(playPause)
            addView(button(context, "Next") { MusicSession.next(); update() })
        }
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 8 })
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 6 })
    }

    /** Reads the current track. The transport buttons still work without a track, through the media keys. */
    fun update() {
        val hasTrack = MusicSession.title() != null
        title.text = MusicSession.title() ?: "Nothing playing"
        artist.text = MusicSession.artist().orEmpty()
        progress.progress = (MusicSession.progress() * 1000).toInt()
        playPause.text = if (MusicSession.isPlaying()) "Pause" else "Play"
        status.text = if (hasTrack) {
            MusicSession.sourceLabel(context)?.let { "From $it" } ?: ""
        } else {
            "Controls work through the media keys"
        }
    }

    private fun button(context: Context, label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            setOnClickListener { onClick() }
        }
}

/** The card label: 15sp semibold, #8E8E93, top-left. */
internal fun heading(context: Context, label: String): TextView = TextView(context).apply {
    text = label
    textSize = 15f
    setTextColor(0xFF8E8E93.toInt())
    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    gravity = Gravity.START
}

/** One event row: bold white text, left-aligned, no pill and no fill. */
private fun pill(context: Context, label: String): TextView = TextView(context).apply {
    text = label
    textSize = 20f
    setTextColor(Color.WHITE)
    typeface = Typeface.create("sans-serif", Typeface.BOLD)
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END
    gravity = Gravity.START
    val dp = context.resources.displayMetrics.density
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = (8 * dp).toInt()
    }
}
