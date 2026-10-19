package com.example.standbyclock

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** One app-wide widget host. Home-screen widgets placed in panels are hosted through it. */
object WidgetHostHolder {

    const val HOST_ID = 4711

    @Volatile
    private var host: AppWidgetHost? = null

    fun get(context: Context): AppWidgetHost =
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }
}

/**
 * The multi-panel face: two to four panels side by side (or stacked in portrait). Each panel holds a
 * stack of items: built-in text items and home-screen widgets. A stack moves to its next item every
 * rotate interval, and the first panel can grow while nothing is playing (yoga layout).
 */
class PanelsView(private val context: Context) : LinearLayout(context) {

    private val settings = Settings(context)
    private val dp = context.resources.displayMetrics.density
    private val portrait = context.resources.displayMetrics.heightPixels > context.resources.displayMetrics.widthPixels

    private val texts = mutableListOf<TextCard>()
    private val slots = mutableListOf<FrameLayout>()
    private val params = mutableListOf<LayoutParams>()
    private val baseWeights = mutableListOf<Float>()
    private val stacks = mutableListOf<List<String>>()
    private val lastWidget = mutableListOf<String?>()
    private val widgetViews = mutableMapOf<Int, View>()
    private val textCache = mutableMapOf<String, Triple<String, String, String>>()
    private val cards = mutableMapOf<String, View>()
    private var cacheMinute = -1L
    private var yogaOn = false

    /** Builds the panels from the saved layout. Call again after the layout changes in settings. */
    fun rebuild() {
        removeAllViews()
        texts.clear(); slots.clear(); params.clear(); baseWeights.clear(); stacks.clear(); lastWidget.clear()
        orientation = if (portrait) VERTICAL else HORIZONTAL
        // 16 dp here plus half of the 16 dp gap around each card gives the 24 dp screen margin.
        val outer = (16 * dp).toInt()
        setPadding(outer, outer, outer, outer)
        val weights = settings.panelWeights()
        settings.panelStacks().forEachIndexed { index, stack ->
            val style = settings.panelStyle(index)
            val text = TextCard(context, style.zoom)
            val slot = FrameLayout(context)
            val box = FrameLayout(context).apply {
                background = boxBackground(style)
                val pad = (20 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                addView(text, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(slot, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            val weight = weights.getOrElse(index) { 1f }
            val gap = (style.spacing.coerceIn(0, 40) * dp / 2f).toInt()
            val lp = if (portrait) {
                LayoutParams(LayoutParams.MATCH_PARENT, 0, weight)
            } else {
                LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            }.apply { setMargins(gap, gap, gap, gap) }
            addView(box, lp)
            texts += text
            slots += slot
            params += lp
            baseWeights += weight
            stacks += stack
            lastWidget += null
        }
        yogaOn = false
        textCache.clear()
        cards.clear()
    }

    /** Starts widget updates. Call from the clock's start. */
    fun resume() {
        WidgetHostHolder.get(context).startListening()
    }

    /** Stops widget updates. Call from the clock's stop. */
    fun pause() {
        WidgetHostHolder.get(context).stopListening()
    }

    /** Shows the right item in every panel for [now]. Called once a second while the face is up. */
    fun tick(now: Long, weatherLine: String, hourlyLine: String) {
        val minute = now / 60_000L
        val minuteChanged = minute != cacheMinute
        if (minuteChanged) {
            cacheMinute = minute
            textCache.clear()
        }
        updateYoga()
        val rotate = settings.panelRotateSeconds
        stacks.forEachIndexed { panel, stack ->
            if (stack.isEmpty()) {
                texts[panel].show("", "", "")
                return@forEachIndexed
            }
            val step = if (rotate > 0 && stack.size > 1) ((now / 1000 / rotate) % stack.size).toInt() else 0
            val item = stack[step]
            if (item.startsWith(WIDGET_PREFIX)) {
                texts[panel].visibility = View.GONE
                slots[panel].visibility = View.VISIBLE
                if (lastWidget[panel] != item) {
                    lastWidget[panel] = item
                    showWidget(panel, item.removePrefix(WIDGET_PREFIX).toIntOrNull())
                }
            } else if (item == "agenda" || item == "music") {
                texts[panel].visibility = View.GONE
                slots[panel].visibility = View.VISIBLE
                if (lastWidget[panel] != item) {
                    lastWidget[panel] = item
                    showCard(panel, item, now)
                } else {
                    // The calendar is read once a minute; the music card follows playback every second.
                    val card = cards["$panel:$item"]
                    if (card is AgendaCard && minuteChanged) card.update(now)
                    if (card is MusicCard) card.update()
                }
            } else {
                lastWidget[panel] = null
                slots[panel].visibility = View.GONE
                texts[panel].visibility = View.VISIBLE
                val (label, value, sub) = textFor(item, now, weatherLine, hourlyLine)
                texts[panel].show(label, value, sub)
            }
        }
    }

    /** Yoga layout: the first panel grows while no music is playing, and returns when it starts. */
    private fun updateYoga() {
        if (!settings.panelYoga || baseWeights.isEmpty()) {
            if (yogaOn) {
                yogaOn = false
                params.forEachIndexed { i, lp -> lp.weight = baseWeights[i]; }
                requestLayout()
            }
            return
        }
        val idle = MusicSession.title() == null
        if (idle != yogaOn) {
            yogaOn = idle
            params.forEachIndexed { i, lp -> lp.weight = if (i == 0 && idle) baseWeights[i] * 1.8f else baseWeights[i] }
            requestLayout()
        }
    }

    /** Puts a calendar or music card in the panel's slot, creating it the first time. */
    private fun showCard(panel: Int, item: String, now: Long) {
        val slot = slots[panel]
        slot.removeAllViews()
        val card = cards.getOrPut("$panel:$item") {
            if (item == "agenda") AgendaCard(context) else MusicCard(context)
        }
        when (card) {
            is AgendaCard -> card.update(now)
            is MusicCard -> card.update()
        }
        (card.parent as? ViewGroup)?.removeView(card)
        slot.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun showWidget(panel: Int, id: Int?) {
        val slot = slots[panel]
        slot.removeAllViews()
        val view = if (id == null) null else widgetView(id)
        if (view == null) {
            slot.addView(TextView(context).apply {
                text = "Widget not available. Remove it in the panel editor."
                gravity = Gravity.CENTER
                setTextColor(0xFFBBBBBB.toInt())
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return
        }
        (view.parent as? ViewGroup)?.removeView(view)
        slot.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** The host view for a widget, created once and reused. Null when the widget is no longer installed. */
    private fun widgetView(id: Int): View? {
        widgetViews[id]?.let { return it }
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id) ?: return null
        val view: AppWidgetHostView = WidgetHostHolder.get(context).createView(context, id, info)
        widgetViews[id] = view
        return view
    }

    /** Label, value and a smaller line for a built-in item. Calendar and battery reads are kept for the rest of the minute. */
    private fun textFor(item: String, now: Long, weatherLine: String, hourlyLine: String): Triple<String, String, String> {
        val heavy = item == "agenda" || item == "battery" || item == "system"
        if (heavy) textCache[item]?.let { return it }
        val card = when (item) {
            "clock" -> Triple(
                "Clock",
                DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", now).toString(),
                "",
            )
            "weather" -> Triple("Weather", weatherLine.ifEmpty { "Weather not set" }, hourlyLine)
            "agenda" -> Agenda.upcoming(context, days = 1, limit = 1).firstOrNull()?.let { event ->
                Triple(
                    "Next event",
                    event.title,
                    "${DateFormat.format("HH:mm", event.begin)}  ${Agenda.countdown(event.begin - now)}",
                )
            } ?: Triple("Next event", "No events today", "")
            "music" -> {
                val title = MusicSession.title()
                if (title == null) Triple("Music", "Nothing playing", "") else Triple("Music", title, MusicSession.artist().orEmpty())
            }
            "battery" -> Triple("Battery", batteryLine(context).ifEmpty { "Battery unknown" }, "")
            "protocol" -> Triple("Protocol", Complications.rotate(settings.protocols, now, 60_000L).ifEmpty { "Add protocols in Settings, Tools and about" }, "")
            "quote" -> Triple("Quote", Complications.rotate(settings.quotes, now, 5 * 60_000L).ifEmpty { "Add quotes in Settings, Tools and about" }, "")
            "pomodoro" -> Triple("Pomodoros today", "${settings.pomodorosToday()}", "")
            "system" -> Triple("System", Build.MODEL, batteryLine(context))
            else -> Triple("", "", "")
        }
        if (heavy) textCache[item] = card
        return card
    }

    private fun boxBackground(style: PanelStyle): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = style.corner.coerceIn(0, 80) * dp
        val alpha = style.alpha.coerceIn(0, 100) * 255 / 100
        // #141416 at the panel style's opacity. The default opacity is 0, so a card has no fill.
        setColor(Color.argb(alpha, 0x14, 0x14, 0x16))
        val border = Settings.BORDER_COLOURS.getOrNull(style.border)?.second ?: 0
        if (style.thickness > 0 && border != 0) {
            setStroke((style.thickness.coerceIn(1, 12) * dp).toInt(), border)
        }
    }

    /**
     * One text card: a 15sp semibold grey label top-left, a bold value (40 to 64 sp) and a smaller line.
     * Three sizes at most, all left-aligned.
     */
    private inner class TextCard(context: Context, zoom: Int) : LinearLayout(context) {
        private val label = TextView(context).apply {
            textSize = 15f
            setTextColor(0xFF8E8E93.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.START
        }
        private val value = TextView(context).apply {
            textSize = (48f * zoom.coerceIn(50, 200) / 100f).coerceIn(40f, 64f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            gravity = Gravity.START or Gravity.BOTTOM
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        private val sub = TextView(context).apply {
            textSize = 20f
            setTextColor(0xFF8E8E93.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.START
        }

        init {
            orientation = VERTICAL
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(value, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(sub, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        fun show(labelText: String, valueText: String, subText: String) {
            label.text = labelText
            value.text = valueText
            sub.text = subText
            sub.visibility = if (subText.isEmpty()) GONE else VISIBLE
        }
    }

    companion object {
        /** Prefix for a home-screen widget item, followed by its app widget id. */
        const val WIDGET_PREFIX = "widget:"
    }
}
