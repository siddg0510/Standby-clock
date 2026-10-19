package com.example.standbyclock

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** Colours shared by every settings screen. One accent colour for switches, sliders and the selected state. */
object Ios {
    val BLACK = 0xFF000000.toInt()
    val CARD = 0xFF1C1C1E.toInt()
    val CARD_PRESSED = 0xFF2C2C2E.toInt()
    val TRACK = 0xFF3A3A3C.toInt()
    val DIVIDER = 0xFF38383A.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val SECONDARY = 0xFF8E8E93.toInt()
    val CHEVRON = 0xFF636366.toInt()
    val ACCENT = 0xFF0A84FF.toInt()
    val RED = 0xFFFF453A.toInt()
}

/** Small line icons drawn in code: chevron, back arrow, check mark, plus and magnifier. */
class Glyph(
    activity: Activity,
    private val kind: Kind,
    color: Int,
    private val widthDp: Float,
    private val heightDp: Float,
) : View(activity) {

    enum class Kind { CHEVRON, BACK, CHECK, PLUS, SEARCH }

    private val density = activity.resources.displayMetrics.density
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((widthDp * density).roundToInt(), (heightDp * density).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val i = paint.strokeWidth / 2
        path.reset()
        when (kind) {
            Kind.CHEVRON -> {
                path.moveTo(i, i)
                path.lineTo(w - i, h / 2)
                path.lineTo(i, h - i)
            }
            Kind.BACK -> {
                path.moveTo(w - i, i)
                path.lineTo(i, h / 2)
                path.lineTo(w - i, h - i)
            }
            Kind.CHECK -> {
                path.moveTo(i, h * 0.55f)
                path.lineTo(w * 0.38f, h - i)
                path.lineTo(w - i, h * 0.12f)
            }
            Kind.PLUS -> {
                path.moveTo(w / 2, i)
                path.lineTo(w / 2, h - i)
                path.moveTo(i, h / 2)
                path.lineTo(w - i, h / 2)
            }
            Kind.SEARCH -> {
                canvas.drawCircle(w * 0.42f, h * 0.42f, w * 0.32f, paint)
                path.moveTo(w * 0.66f, h * 0.66f)
                path.lineTo(w - i, h - i)
            }
        }
        canvas.drawPath(path, paint)
    }
}

/** An iOS-style switch: a pill track that fills with the accent colour and a white knob that slides. */
class IosSwitch(activity: Activity) : View(activity) {

    private val density = activity.resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ios.WHITE }
    private val rect = RectF()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    var isOn = false
        private set
    var onToggle: ((Boolean) -> Unit)? = null

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun set(on: Boolean, animate: Boolean) {
        isOn = on
        animator?.cancel()
        val target = if (on) 1f else 0f
        if (!animate) {
            progress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 180
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun toggle() {
        set(!isOn, true)
        onToggle?.invoke(isOn)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((51 * density).roundToInt(), (31 * density).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        trackPaint.color = ArgbEvaluator().evaluate(progress, 0xFF39393D.toInt(), Ios.ACCENT) as Int
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2, h / 2, trackPaint)
        val radius = h / 2 - 2 * density
        val x = h / 2 + (w - h) * progress
        canvas.drawCircle(x, h / 2, radius, knobPaint)
    }
}

/** A slider with a thin track, an accent fill and a round white knob. */
class IosSlider(activity: Activity, private val low: Int, private val high: Int, initial: Int) : View(activity) {

    private val density = activity.resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ios.TRACK }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ios.ACCENT }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ios.WHITE }
    private val rect = RectF()
    private val knobRadius = 14 * density

    var value = initial.coerceIn(low, high)
        private set
    var onChange: ((Int) -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (32 * density).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val left = knobRadius
        val right = width - knobRadius
        val centre = height / 2f
        val fraction = if (high == low) 0f else (value - low).toFloat() / (high - low)
        val x = left + (right - left) * fraction
        val half = 2 * density
        rect.set(left, centre - half, right, centre + half)
        canvas.drawRoundRect(rect, half, half, trackPaint)
        rect.set(left, centre - half, x, centre + half)
        canvas.drawRoundRect(rect, half, half, fillPaint)
        canvas.drawCircle(x, centre, knobRadius - density, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x)
            }
            MotionEvent.ACTION_MOVE -> update(event.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun update(x: Float) {
        val left = knobRadius
        val right = width - knobRadius
        val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
        val next = low + (fraction * (high - low)).roundToInt()
        if (next != value) {
            value = next
            invalidate()
            onChange?.invoke(next)
        }
    }
}

/** A text field shown in an input dialog. */
class DialogField(val hint: String, val initial: String = "", val inputType: Int = InputType.TYPE_CLASS_TEXT)

/**
 * Builders for every settings component, so each page looks the same: sections, rounded cards with inset
 * dividers, rows (switch, value, chevron, status), sliders, segmented controls and input dialogs.
 */
class SettingsUi(val activity: Activity) {

    private val density = activity.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density).roundToInt()

    private fun lp(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)

    private fun text(value: String, size: Float, color: Int): TextView = TextView(activity).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
    }

    private fun roundedFill(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    private fun pressable(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(Ios.CARD_PRESSED))
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }

    // ---- Page furniture ----

    fun largeTitle(title: String): TextView = text(title, 34f, Ios.WHITE).apply {
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(8), 0, dp(4))
    }

    /** A rounded search box. Returns the box and its field. */
    fun searchBar(initial: String, onChange: (String) -> Unit): Pair<View, EditText> {
        val field = EditText(activity).apply {
            hint = "Search"
            setHintTextColor(Ios.SECONDARY)
            setTextColor(Ios.WHITE)
            textSize = 17f
            background = null
            setPadding(0, 0, 0, 0)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setText(initial)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) = onChange(s?.toString().orEmpty())
            })
            setOnEditorActionListener { view, _, _ ->
                activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            }
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedFill(Ios.CARD, 12f)
            setPadding(dp(12), 0, dp(12), 0)
            addView(Glyph(activity, Glyph.Kind.SEARCH, Ios.SECONDARY, 16f, 16f))
            addView(field, lp(0, dp(40)).apply {
                weight = 1f
                marginStart = dp(8)
            })
        }
        return box to field
    }

    // ---- Sections and cards ----

    /** A header above a card of [rows] and an optional footer note below it. Rows are separated by inset dividers. */
    fun section(header: String?, footer: String?, rows: List<View>): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        if (header != null) {
            addView(text(header.uppercase(), 13f, Ios.SECONDARY).apply {
                setPadding(dp(16), 0, dp(16), dp(7))
            })
        }
        addView(card(rows), lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (footer != null) {
            addView(text(footer, 13f, Ios.SECONDARY).apply {
                setPadding(dp(16), dp(8), dp(16), 0)
                setLineSpacing(0f, 1.1f)
            })
        }
    }

    /** The rounded #1C1C1E card that holds rows. */
    fun card(rows: List<View>): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedFill(Ios.CARD, 20f)
        clipToOutline = true
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                addView(View(activity).apply { setBackgroundColor(Ios.DIVIDER) }, lp(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                    marginStart = dp(16)
                })
            }
            addView(row, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Centred grey text for an empty list or a search with no matches. */
    fun note(message: String): TextView = text(message, 15f, Ios.SECONDARY).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(24), dp(16), dp(24))
    }

    // ---- Rows ----

    /** The base row: 56dp high, or 72dp with a subtitle. Title 17sp, subtitle 13sp, optional view on the right. */
    fun row(
        title: String,
        subtitle: String? = null,
        trailing: View? = null,
        leading: View? = null,
        singleLineSubtitle: Boolean = false,
        onClick: (() -> Unit)? = null,
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(if (subtitle != null) 72 else 56)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        if (leading != null) addView(leading, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(12)
        })
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 17f, Ios.WHITE).apply { setLineSpacing(0f, 1.08f) })
            if (subtitle != null) {
                addView(text(subtitle, 13f, Ios.SECONDARY).apply {
                    if (singleLineSubtitle) {
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                }, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
            }
        }
        addView(texts, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f })
        if (trailing != null) addView(trailing, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(12)
        })
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            background = pressable()
            setOnClickListener { onClick() }
        }
    }

    fun chevron(): View = Glyph(activity, Glyph.Kind.CHEVRON, Ios.CHEVRON, 8f, 14f)

    /** A row that opens something: title, optional subtitle and a chevron. */
    fun navRow(title: String, subtitle: String? = null, singleLineSubtitle: Boolean = false, onClick: () -> Unit): LinearLayout =
        row(title, subtitle, trailing = chevron(), singleLineSubtitle = singleLineSubtitle, onClick = onClick)

    /** A row with the current value on the right, then a chevron. */
    fun valueRow(title: String, value: String, subtitle: String? = null, swatch: Int? = null, onClick: () -> Unit): LinearLayout {
        val right = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (swatch != null) addView(swatchDot(swatch), lp(dp(18), dp(18)).apply { marginEnd = dp(8) })
            addView(text(value, 17f, Ios.SECONDARY).apply {
                maxWidth = dp(170)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(chevron(), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        }
        return row(title, subtitle, trailing = right, onClick = onClick)
    }

    private fun swatchDot(color: Int): View = View(activity).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(1), 0x55FFFFFF)
        }
    }

    /** A row that only shows a value on the right, with no action. */
    fun infoRow(title: String, value: String): LinearLayout =
        row(title, trailing = text(value, 17f, Ios.SECONDARY))

    /** A row with an iOS-style switch. Saves as soon as it flips. */
    fun switchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val toggle = IosSwitch(activity).apply {
            set(checked, false)
            onToggle = onChange
        }
        val row = row(title, subtitle, trailing = toggle, onClick = { toggle.toggle() })
        row.stateDescription = if (checked) "On" else "Off"
        toggle.onToggle = {
            row.stateDescription = if (it) "On" else "Off"
            onChange(it)
        }
        return row
    }

    /** A row that is only an action, drawn in the accent colour. */
    fun actionRow(title: String, subtitle: String? = null, color: Int = Ios.ACCENT, plus: Boolean = false, onClick: () -> Unit): LinearLayout {
        val lead = if (plus) Glyph(activity, Glyph.Kind.PLUS, color, 16f, 16f) else null
        val row = row(title, subtitle, leading = lead, onClick = onClick)
        val label = (row.getChildAt(if (lead != null) 1 else 0) as LinearLayout).getChildAt(0) as TextView
        label.setTextColor(color)
        return row
    }

    /** A row for a choice page: title, optional colour dot, and a check mark when [checked]. */
    fun checkRow(title: String, checked: Boolean, swatch: Int? = null, onClick: () -> Unit): LinearLayout {
        val mark: View? = if (checked) Glyph(activity, Glyph.Kind.CHECK, Ios.ACCENT, 18f, 18f) else null
        val dot = swatch?.let { swatchDot(it).apply { minimumWidth = dp(22) } }
        val row = row(title, trailing = mark, leading = dot, onClick = onClick)
        dot?.layoutParams = lp(dp(22), dp(22)).apply { marginEnd = dp(12) }
        row.stateDescription = if (checked) "Selected" else "Not selected"
        return row
    }

    fun emptyRow(message: String): LinearLayout = row(message).apply {
        ((getChildAt(0) as LinearLayout).getChildAt(0) as TextView).apply {
            setTextColor(Ios.SECONDARY)
            textSize = 15f
        }
    }

    /** A small rounded button for use on the right of a row. */
    fun pill(label: String, color: Int = Ios.ACCENT, description: String? = null, onClick: () -> Unit): TextView =
        text(label, 15f, color).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = dp(34)
            minWidth = dp(48)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), roundedFill(Ios.TRACK, 17f))
                addState(intArrayOf(), roundedFill(Ios.CARD_PRESSED, 17f))
            }
            isClickable = true
            isFocusable = true
            contentDescription = description ?: label
            setOnClickListener { onClick() }
        }

    /** A permission row: the status on the right ("Allowed" or "Not allowed") and a Fix button. */
    fun statusRow(title: String, subtitle: String? = null, allowed: Boolean, onFix: () -> Unit): LinearLayout {
        val right = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(if (allowed) "Allowed" else "Not allowed", 15f, if (allowed) Ios.SECONDARY else Ios.RED))
            addView(pill("Fix", description = "Fix $title", onClick = onFix), lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(10)
            })
        }
        return row(title, subtitle, trailing = right)
    }

    /** One item of a list page, with Remove (and optional extra) buttons on the right. */
    fun itemRow(title: String, subtitle: String?, actions: List<Pair<String, () -> Unit>>): LinearLayout {
        val right = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.forEachIndexed { index, (label, action) ->
            val remove = label == "Remove"
            right.addView(
                pill(label, if (remove) Ios.RED else Ios.ACCENT, description = "$label $title", onClick = action),
                lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) marginStart = dp(8)
                },
            )
        }
        return row(title, subtitle, trailing = right, singleLineSubtitle = true)
    }

    // ---- Segmented control and slider ----

    /** Toggle-style buttons in a row; the selected one is filled with the accent colour. For four options or fewer. */
    fun segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val track = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedFill(Ios.CARD_PRESSED, 10f)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val tabs = labels.map { label ->
            TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(Ios.WHITE)
                setPadding(dp(4), 0, dp(4), 0)
                setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP)
                isClickable = true
                isFocusable = true
            }
        }
        fun paint(current: Int) {
            tabs.forEachIndexed { index, tab ->
                val on = index == current
                tab.background = if (on) roundedFill(Ios.ACCENT, 8f) else null
                tab.typeface = Typeface.create(Typeface.DEFAULT, if (on) Typeface.BOLD else Typeface.NORMAL)
                tab.isSelected = on
                tab.stateDescription = if (on) "Selected" else "Not selected"
            }
        }
        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener {
                paint(index)
                onSelect(index)
            }
            track.addView(tab, lp(0, dp(34)).apply { weight = 1f })
        }
        paint(selected)
        return track
    }

    /** A row with a title above a segmented control. */
    fun segmentedRow(title: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(text(title, 17f, Ios.WHITE))
            addView(segmented(labels, selected, onSelect), lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
        }

    /** A row with a title, the current value on the right (for example "35%") and a slider below. Saves as it moves. */
    fun sliderRow(
        title: String,
        low: Int,
        high: Int,
        current: Int,
        format: (Int) -> String = { "$it%" },
        onValue: (Int) -> Unit,
    ): LinearLayout {
        val readout = text(format(current.coerceIn(low, high)), 17f, Ios.SECONDARY)
        val slider = IosSlider(activity, low, high, current).apply {
            onChange = { value ->
                readout.text = format(value)
                onValue(value)
            }
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(title, 17f, Ios.WHITE), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f })
                addView(readout)
            })
            addView(slider, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
    }

    /** A row of colour dots for choosing one of a few colours without a sub-page. [labels] are read out by screen readers. */
    fun swatchRow(title: String, labels: List<String>, colours: List<Int>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val dots = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val views = colours.mapIndexed { index, colour ->
            SwatchButton(activity, colour, index == 0).also { it.contentDescription = labels[index] }
        }
        fun paint(current: Int) = views.forEachIndexed { index, dot -> dot.ring = index == current }
        views.forEachIndexed { index, dot ->
            dot.setOnClickListener {
                paint(index)
                onSelect(index)
            }
            dots.addView(dot, lp(dp(40), dp(40)).apply { if (index > 0) marginStart = dp(10) })
        }
        paint(selected)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(text(title, 17f, Ios.WHITE))
            addView(dots, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
    }

    /** A round colour button with a ring when selected. [none] draws a slash for "no colour". */
    class SwatchButton(private val activity: Activity, private val colour: Int, private val none: Boolean) : View(activity) {
        private val density = activity.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = Ios.WHITE
        }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = Ios.SECONDARY
            strokeCap = Paint.Cap.ROUND
        }
        var ring = false
            set(value) {
                field = value
                invalidate()
            }

        init {
            isClickable = true
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = width / 2f - 5 * density
            if (none) {
                fill.color = Ios.CARD_PRESSED
                canvas.drawCircle(cx, cy, radius, fill)
                canvas.drawLine(cx - radius * 0.6f, cy + radius * 0.6f, cx + radius * 0.6f, cy - radius * 0.6f, line)
            } else {
                canvas.drawCircle(cx, cy, radius, fill)
            }
            if (ring) canvas.drawCircle(cx, cy, width / 2f - 2 * density, ringPaint)
        }
    }

    // ---- Dialogs ----

    /**
     * Asks for one or more lines of text. [validate] returns an error message to keep the dialog open, or null to accept.
     * The confirm button is labelled for what it does (Add, Search, Done), not Save: every setting applies at once.
     */
    fun prompt(
        title: String,
        message: String? = null,
        fields: List<DialogField>,
        confirm: String = "Done",
        validate: ((List<String>) -> String?)? = null,
        onConfirm: (List<String>) -> Unit,
    ) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val edits = fields.map { field ->
            EditText(activity).apply {
                hint = field.hint
                setText(field.initial)
                inputType = field.inputType
                setSingleLine()
                setSelection(text.length)
            }.also { box.addView(it, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .apply { if (message != null) setMessage(message) }
            .setView(box)
            .setPositiveButton(confirm, null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val values = edits.map { it.text.toString().trim() }
                val error = validate?.invoke(values)
                if (error != null) {
                    edits.first().error = error
                } else {
                    dialog.dismiss()
                    onConfirm(values)
                }
            }
            edits.firstOrNull()?.requestFocus()
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }
}

/**
 * The page frame shared by the settings screens: black background, a back-arrow header (not on the root page),
 * a scrolling column with the large title, and window insets.
 */
class SettingsScaffold(private val activity: Activity, private val ui: SettingsUi) {

    private val header = LinearLayout(activity)
    private val scroll = ScrollView(activity)
    val root: View

    init {
        @Suppress("DEPRECATION")
        activity.window.apply {
            setBackgroundDrawable(ColorDrawable(Ios.BLACK))
            statusBarColor = Ios.BLACK
            navigationBarColor = Ios.BLACK
        }
        header.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(48)
        }
        scroll.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
        }
        root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ios.BLACK)
            addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }
    }

    /**
     * Shows one page. [backLabel] is the title of the page behind this one, or null on the root page.
     * [direction] is 1 when moving forward, -1 when going back, 0 for a refresh. [scrollTo] restores a scroll position.
     */
    fun show(title: String, backLabel: String?, views: List<View>, onBack: () -> Unit, direction: Int, scrollTo: Int) {
        // Detach views that pages reuse between rebuilds (the search box) before they are added again.
        (scroll.getChildAt(0) as? ViewGroup)?.removeAllViews()
        scroll.removeAllViews()

        header.removeAllViews()
        if (backLabel == null) {
            header.visibility = View.GONE
        } else {
            header.visibility = View.VISIBLE
            header.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(48)
                setPadding(ui.dp(20), 0, ui.dp(24), 0)
                isClickable = true
                isFocusable = true
                contentDescription = "Back to $backLabel"
                addView(Glyph(activity, Glyph.Kind.BACK, Ios.ACCENT, 12f, 20f))
                addView(TextView(activity).apply {
                    text = backLabel
                    textSize = 17f
                    setTextColor(Ios.ACCENT)
                    maxLines = 1
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = ui.dp(6)
                })
                setOnClickListener { onBack() }
            })
        }

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), if (backLabel == null) ui.dp(12) else 0, ui.dp(20), ui.dp(48))
        }
        column.addView(ui.largeTitle(title), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        views.forEach { view ->
            column.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = ui.dp(16)
            })
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (scrollTo > 0) {
            scroll.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    scroll.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    scroll.scrollTo(0, scrollTo)
                }
            })
        }
        if (direction != 0) {
            column.alpha = 0f
            column.translationX = direction * ui.dp(40).toFloat()
            column.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    fun scrollPosition(): Int = scroll.scrollY
}
