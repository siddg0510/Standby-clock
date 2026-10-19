package com.example.standbyclock

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The clock itself. It draws the chosen face (see [Settings.FACE_OPTIONS]), the corner
 * complications, the now-playing card, night tint and burn-in protection, and the
 * background effects (photos, rain-style canvas, snow, matrix). Shared by the activity and the
 * screensaver. [faceOverride] lets the screensaver use its own face.
 */
class ClockUi(private val context: Context, private val faceOverride: String? = null) {

    private val settings = Settings(context)
    private val handler = Handler(Looper.getMainLooper())
    private val metrics = context.resources.displayMetrics
    private val portrait = metrics.heightPixels > metrics.widthPixels
    private var running = false
    private var weatherFetchedAt = 0L
    private var weatherText = ""
    private var lastNight: Boolean? = null
    private var photoIndexShown = -1
    private var photoLoaded: String? = null
    private var radioPick = 0

    // Ambient light, used for night mode when the user turns that on. -1 means no reading yet.
    private var lux = -1f
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lux = event.values[0]
            refreshNight()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Called when night mode turns on or off, and once when the clock starts. */
    var onMinute: ((Boolean) -> Unit)? = null

    private val refresh = object : Runnable {
        override fun run() {
            update()
            // The clock only changes on minute boundaries, so wake only then.
            handler.postDelayed(this, MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
        }
    }

    // Once a second while running: progress bar, seconds, photo slideshow and sound canvas.
    private val ticker = object : Runnable {
        override fun run() {
            renderMusic()
            if (settings.cornerLeft == Complications.TIME_SEC || settings.cornerRight == Complications.TIME_SEC) {
                updateCorners(System.currentTimeMillis())
            }
            refreshPhoto()
            updateBackgrounds()
            tickFace(System.currentTimeMillis())
            if (running) handler.postDelayed(this, 1000L)
        }
    }

    private val musicListener: () -> Unit = { renderMusic() }

    val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }

    // ---- Backgrounds ----
    private val photoView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
    }
    private val photoCalendarView = CalendarGridView(context).apply {
        alpha = 0.6f
        visibility = View.GONE
    }
    private val canvasView = GenerativeCanvasView(context).apply { visibility = View.GONE }
    private val matrixView = MatrixRainView(context).apply { visibility = View.GONE }
    private val snowView = SnowView(context).apply { visibility = View.GONE }

    // ---- Faces ----
    private val analogView = AnalogClockView(context)
    private val segmentedView = SegmentedClockView(context)
    private val radialView = RadialView(context)
    private val solarView = SolarView(context)
    private val retroflipView = RetroflipView(context)
    private val pixelView = PixelClockView(context)
    private val stripingView = CalendarGridView(context).apply { bigDay = true }
    private val commandView = CommandCenter(context)
    private val timeView = text(170f, Color.WHITE, Typeface.create("sans-serif-thin", Typeface.NORMAL))
    private val panelView = TextView(context).apply {
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setShadowLayer(12f, 0f, 0f, 0xCC000000.toInt())
        setSingleLine(false)
        maxLines = 14
    }
    private val dateView = text(32f, 0xFFBBBBBB.toInt())
    private val infoView = text(26f, 0xFFBBBBBB.toInt())
    private val weatherView = text(26f, 0xFFBBBBBB.toInt())
    private val batteryView = text(24f, 0xFF888888.toInt())
    private val chargeBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        visibility = View.GONE
    }
    private val playerCard = PlayerCard(context).apply {
        onRadio = { toggleRadio() }
    }
    private val leftCorner = text(18f, 0xFF999999.toInt())
    private val rightCorner = text(18f, 0xFF999999.toInt())
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    private val duoLeft = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    private val duoRight = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val duoRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    private val tintView = View(context).apply { visibility = View.GONE }
    private val checker = CheckerboardView(context)
    private val issView = IssMapView(context).apply { visibility = View.GONE }
    private val panelsView = PanelsView(context).apply { visibility = View.GONE }
    private val balanceView = View(context).apply { visibility = View.GONE }
    private val overlayView = text(56f, Color.WHITE)
    private val photoCaptionView = text(24f, Color.WHITE)
    private var hourlyText = ""

    init {
        val shortSide = min(metrics.widthPixels, metrics.heightPixels)
        val width = metrics.widthPixels.toFloat()

        analogView.layoutParams = sized((shortSide * 0.55f).toInt(), (shortSide * 0.55f).toInt())
        radialView.layoutParams = sized((shortSide * 0.6f).toInt(), (shortSide * 0.6f).toInt())
        val segmentHeight = (shortSide * 0.42f).toInt()
        segmentedView.layoutParams = sized((segmentHeight * 2.3f).toInt(), segmentHeight)
        val solarWidth = if (portrait) width * 0.9f else width * 0.7f
        solarView.layoutParams = sized(solarWidth.toInt(), (solarWidth * 0.32f).toInt())
        val retroWidth = if (portrait) width * 0.9f else width * 0.6f
        retroflipView.layoutParams = sized(retroWidth.toInt(), (retroWidth * 0.42f).toInt())
        pixelView.layoutParams = sized((width * 0.7f).toInt(), (shortSide * 0.3f).toInt())
        val gridWidth = if (portrait) width * 0.9f else width * 0.55f
        stripingView.layoutParams = sized(gridWidth.toInt(), (gridWidth * 0.8f).toInt())
        photoCalendarView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER,
        )

        issView.layoutParams = sized((shortSide * 0.6f).toInt(), (shortSide * 0.34f).toInt())
        duoRow.addView(duoLeft, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        duoRow.addView(duoRight, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val margin = (24 * metrics.density).toInt()
        root.addView(photoView, matchParent())
        root.addView(photoCalendarView, matchParent())
        root.addView(canvasView, matchParent())
        root.addView(matrixView, matchParent())
        root.addView(snowView, matchParent())
        root.addView(column, matchParent())
        root.addView(duoRow, matchParent())
        root.addView(leftCorner, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START,
        ).apply { setMargins(margin, margin, margin, margin) })
        root.addView(rightCorner, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END,
        ).apply { setMargins(margin, margin, margin, margin) })
        root.addView(tintView, matchParent())
        root.addView(checker, matchParent())
        root.addView(panelsView, matchParent())
        root.addView(balanceView, matchParent())
        root.addView(overlayView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START,
        ))
        root.addView(photoCaptionView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { setMargins(margin, margin, margin, margin) })
        // The palette gradient needs the real height of the time, which only exists after layout.
        timeView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateGradient() }
    }

    /** Applies the chosen face, draws the current state and starts the refreshes. */
    fun start() {
        running = true
        panelsView.resume()
        applyFace()
        update()
        MusicSession.addListener(musicListener)
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        // Sound keeps playing after the clock closes, until its timer ends or it is turned off.
        if (settings.noiseStation != Settings.NOISE_OFF && !NoisePlayer.isPlaying) {
            NoisePlayer.start(settings.noiseStation, settings.noiseMinutes)
        }
        if (settings.lightSensorNight && lightSensor != null) {
            sensorManager?.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /** Stops the refreshes and releases the light sensor and radio. Call when the screen is hidden or the dream detaches. */
    fun stop() {
        running = false
        handler.removeCallbacks(refresh)
        handler.removeCallbacks(ticker)
        MusicSession.removeListener(musicListener)
        sensorManager?.unregisterListener(lightListener)
        panelsView.pause()
        RadioPlayer.stop()
    }

    /** Re-reads the face settings and redraws, e.g. after the user changes the face. */
    fun reloadFace() {
        applyFace()
        update()
    }

    private fun currentFace(): String = faceOverride ?: settings.face

    private fun applyFace() {
        val face = currentFace()
        val duo = face == Settings.FACE_DUO
        val command = face == Settings.FACE_COMMAND
        layoutChildren(duo, command)
        val panels = face == Settings.FACE_PANELS
        column.visibility = visibleIf(!duo && !panels)
        duoRow.visibility = visibleIf(duo)
        panelsView.visibility = visibleIf(panels)
        if (panels) panelsView.rebuild()

        issView.visibility = visibleIf(face == Settings.FACE_ISS)
        analogView.visibility = visibleIf(face == Settings.FACE_ANALOG)
        segmentedView.visibility = visibleIf(face == Settings.FACE_SEGMENTED)
        radialView.visibility = visibleIf(face == Settings.FACE_RADIAL)
        solarView.visibility = visibleIf(face == Settings.FACE_SOLAR)
        retroflipView.visibility = visibleIf(face == Settings.FACE_RETROFLIP)
        retroflipView.setStyle(settings.retroStyle, settings.retroOpaque)
        pixelView.visibility = visibleIf(face == Settings.FACE_PIXEL)
        stripingView.visibility = visibleIf(face == Settings.FACE_STRIKING)
        commandView.visibility = visibleIf(command)

        val timeHidden = face in setOf(
            Settings.FACE_ANALOG, Settings.FACE_SEGMENTED, Settings.FACE_RADIAL, Settings.FACE_RETROFLIP,
            Settings.FACE_PIXEL, Settings.FACE_STRIKING, Settings.FACE_COMMAND, Settings.FACE_WORLD,
        )
        timeView.visibility = visibleIf(!timeHidden)
        panelView.visibility = visibleIf(face in setOf(
            Settings.FACE_SYSTEM, Settings.FACE_STARSHIP, Settings.FACE_WEATHER, Settings.FACE_WORLD, Settings.FACE_ISS,
        ))
        chargeBar.visibility = visibleIf(face == Settings.FACE_CHARGE)
        styleTime(face, duo)

        // Backgrounds: the photo face shows a picture; the effects stay off when pure black is on.
        val photoFace = face == Settings.FACE_PHOTO
        photoCalendarView.visibility = visibleIf(photoFace && settings.photoCalendar)
        photoCalendarView.bigDay = false
        photoCaptionView.visibility = visibleIf(photoFace && settings.photoCaption.isNotBlank())
        if (!photoFace) {
            photoView.setImageBitmap(null)
            photoView.visibility = View.GONE
            photoLoaded = null
        }
        matrixView.setRunning(false)
        updateBackgrounds()
        checker.visibility = visibleIf(settings.checkerboard)
        checker.setDim(settings.checkerDim)
        refreshPhoto()
    }

    /** Shows or hides the background effects and starts or stops their animation. */
    private fun updateBackgrounds() {
        val face = currentFace()
        val pure = settings.pureBlack
        val matrixOn = face == Settings.FACE_MATRIX && !pure
        matrixView.visibility = visibleIf(matrixOn)
        matrixView.setRunning(matrixOn && running)
        snowView.visibility = visibleIf(settings.snow && !pure)
        snowView.setRunning(settings.snow && !pure && running)
        val canvasOn = settings.visualCanvas && !pure && NoisePlayer.isPlaying
        canvasView.visibility = visibleIf(canvasOn)
        canvasView.setRunning(canvasOn && running)
    }

    /** Puts each view under the right container: one column, the command centre, or the duo's two columns. */
    private fun layoutChildren(duo: Boolean, command: Boolean) {
        listOf(
            analogView, segmentedView, radialView, solarView, retroflipView, pixelView, stripingView,
            commandView, timeView, chargeBar, panelView, dateView, infoView, weatherView, playerCard, batteryView, issView,
        ).forEach { (it.parent as? ViewGroup)?.removeView(it) }

        when {
            duo -> {
                duoLeft.addView(timeView, wrapParams())
                duoLeft.addView(dateView, wrapParams())
                duoLeft.addView(batteryView, wrapParams())
                duoRight.addView(weatherView, wrapParams())
                duoRight.addView(infoView, wrapParams())
                duoRight.addView(playerCard, playerParams(duo = true))
            }
            command -> {
                column.addView(commandView, LinearLayout.LayoutParams(
                    (metrics.widthPixels * 0.94f).toInt(), (metrics.heightPixels * 0.9f).toInt(),
                ))
            }
            else -> {
                column.addView(analogView)
                column.addView(segmentedView)
                column.addView(radialView)
                column.addView(solarView)
                column.addView(retroflipView)
                column.addView(pixelView)
                column.addView(stripingView)
                column.addView(issView)
                column.addView(timeView, columnParams())
                column.addView(chargeBar, LinearLayout.LayoutParams(
                    (metrics.widthPixels * 0.5f).toInt(), (16 * metrics.density).toInt(),
                ).apply { gravity = Gravity.CENTER_HORIZONTAL })
                column.addView(panelView, columnParams())
                column.addView(dateView, columnParams())
                column.addView(infoView, columnParams())
                column.addView(weatherView, columnParams())
                column.addView(playerCard, playerParams(duo = false))
                column.addView(batteryView, columnParams())
            }
        }
    }

    /** Sets the big time's size, font and colour for the face. Portrait gets smaller sizes so the digits fit. */
    private fun styleTime(face: String, duo: Boolean) {
        timeView.setTextColor(Color.WHITE)
        timeView.setShadowLayer(12f, 0f, 0f, 0xCC000000.toInt())
        dateView.textSize = if (face == Settings.FACE_DEMENTIA) 40f else 32f
        infoView.textSize = if (face == Settings.FACE_DEMENTIA) 40f else 26f
        panelView.setTypeface(
            if (face == Settings.FACE_STARSHIP || face == Settings.FACE_SYSTEM || face == Settings.FACE_WORLD) {
                Typeface.MONOSPACE
            } else {
                Typeface.DEFAULT
            },
            Typeface.NORMAL,
        )
        panelView.textSize = if (face == Settings.FACE_WORLD) 30f else 24f
        when {
            face == Settings.FACE_WORD -> {
                timeView.textSize = 56f
                timeView.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }
            face == Settings.FACE_NEON -> {
                timeView.textSize = if (portrait) 120f else 170f
                timeView.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                timeView.setTextColor(NEON_GREEN)
                timeView.setShadowLayer(40f, 0f, 0f, NEON_GREEN)
            }
            face == Settings.FACE_BIG -> {
                timeView.textSize = if (portrait) 130f else 250f
                timeView.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            }
            face == Settings.FACE_DEMENTIA -> {
                timeView.textSize = if (portrait) 80f else 110f
                timeView.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            face == Settings.FACE_ROMAN -> {
                timeView.textSize = if (portrait) 64f else 110f
                timeView.typeface = Typeface.create("serif", Typeface.NORMAL)
            }
            face == Settings.FACE_WEATHER -> {
                val jumbo = settings.weatherLayout == "jumbo"
                timeView.textSize = if (jumbo) (if (portrait) 110f else 200f) else (if (portrait) 80f else 120f)
                timeView.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                panelView.textSize = if (jumbo) 48f else 26f
            }
            face == Settings.FACE_CHARGE -> {
                timeView.textSize = if (portrait) 150f else 220f
                timeView.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                applyBatteryStyle()
            }
            duo -> {
                timeView.textSize = 120f
                timeView.typeface = fontFor(thin = true)
            }
            else -> {
                timeView.textSize = if (portrait) 120f else 170f
                timeView.typeface = fontFor(thin = true)
            }
        }
        applyPalette(face)
    }

    /** The clock font chosen in settings, with the thin weight as the default. */
    private fun fontFor(thin: Boolean): Typeface = typefaceFor(settings.fontStyle, thin)

    /** The typeface for a font id from [Settings]. */
    private fun typefaceFor(style: String, thin: Boolean): Typeface = when (style) {
        Settings.FONT_SERIF -> Typeface.create("serif", Typeface.NORMAL)
        Settings.FONT_MONO -> Typeface.MONOSPACE
        Settings.FONT_ROUNDED -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        Settings.FONT_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        else -> Typeface.create(if (thin) "sans-serif-thin" else "sans-serif", Typeface.NORMAL)
    }

    /** Colour and glow for the charging face, from the chosen battery style. */
    private fun applyBatteryStyle() {
        val (colour, bar, glow) = when (settings.batteryStyle) {
            "material" -> Triple(0xFF26A69A.toInt(), 0xFF26A69A.toInt(), 0x0)
            "neon" -> Triple(0xFFFF2BD6.toInt(), 0xFFFF2BD6.toInt(), 0xFFFF2BD6.toInt())
            else -> Triple(Color.WHITE, Color.WHITE, 0x0)
        }
        timeView.setTextColor(colour)
        if (glow != 0) timeView.setShadowLayer(40f, 0f, 0f, glow) else timeView.setShadowLayer(12f, 0f, 0f, 0xCC000000.toInt())
        chargeBar.progressTintList = ColorStateList.valueOf(bar)
    }

    private fun update() {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val use24Hour = DateFormat.is24HourFormat(context)
        val face = currentFace()
        val clockText = clockString(now, use24Hour)

        when (face) {
            Settings.FACE_WORD -> timeView.text = WordClock.describe(hour, minute)
            Settings.FACE_DEMENTIA -> timeView.text = Complications.timeOfDay(hour)
            Settings.FACE_ROMAN -> timeView.text = RomanNumerals.time(hour, minute)
            Settings.FACE_CHARGE -> updateCharge(clockText)
            Settings.FACE_WEATHER -> {
                timeView.text = clockText
                panelView.text = weatherPanelText()
            }
            Settings.FACE_ISS -> {
                timeView.text = clockText
                panelView.text = IssClient.lines(IssClient.last)
            }
            Settings.FACE_SYSTEM -> {
                timeView.text = clockText
                panelView.text = systemLines()
            }
            Settings.FACE_STARSHIP -> {
                timeView.text = clockText
                panelView.text = starshipLines(clockText)
            }
            Settings.FACE_WORLD -> panelView.text = worldLines(calendar)
            Settings.FACE_COMMAND -> updateCommand(clockText, hour, now, use24Hour)
            else -> timeView.text = clockText
        }
        analogView.setTime(hour, minute)
        analogView.setSubDial(if (settings.analogSubDial) clockText else null)
        segmentedView.setTime(hour, minute, use24Hour)
        radialView.setTime(hour, minute, use24Hour)
        radialView.setInvert(settings.radialInvert)
        solarView.setTime(hour, minute)
        retroflipView.setTime(hour, minute, use24Hour)
        pixelView.setTime(hour, minute, use24Hour)
        stripingView.invalidate()

        dateView.text = if (face == Settings.FACE_DEMENTIA) {
            DateFormat.format("EEEE, d MMMM yyyy", now)
        } else {
            DateFormat.format("EEEE, d MMMM", now)
        }
        if (face == Settings.FACE_DEMENTIA) {
            infoView.text = clockText
            infoView.visibility = View.VISIBLE
        } else if (face == Settings.FACE_CHARGE) {
            infoView.text = clockText
            infoView.visibility = visibleIf(!settings.batteryHideClock)
        } else {
            val info = infoLine(now, use24Hour)
            infoView.text = info
            infoView.visibility = visibleIf(info.isNotEmpty())
        }

        updateCorners(now)
        refreshWeather(now)
        weatherView.text = weatherText
        weatherView.visibility = visibleIf(weatherText.isNotEmpty() && face != Settings.FACE_WEATHER)
        renderMusic()
        batteryView.text = batteryLine(context)
        shiftForBurnIn()
        checker.setPhase(((now / MINUTE_MS) % 2).toInt())
        updateOverlay(clockText)
        updateBalance()
        refreshIss()
        refreshNight()
    }

    private fun updateCorners(now: Long) {
        leftCorner.text = Complications.text(context, settings.cornerLeft, now)
        leftCorner.visibility = visibleIf(leftCorner.text.isNotEmpty())
        rightCorner.text = Complications.text(context, settings.cornerRight, now)
        rightCorner.visibility = visibleIf(rightCorner.text.isNotEmpty())
    }

    /** Charging face: the battery percentage, a progress bar, the time to full, and the milestone. */
    private fun updateCharge(clockText: String) {
        val state = readBattery(context) ?: return
        timeView.text = "${state.percent}%"
        chargeBar.progress = state.percent
        val low = !state.charging && state.percent < 20
        if (low) timeView.setTextColor(RED) else applyBatteryStyle()
        val milestone = settings.chargeMilestone
        val toMilestone = state.minutesToFull?.let { minutes ->
            if (state.percent < milestone && state.percent < 100) {
                (minutes * (milestone - state.percent) / (100 - state.percent)).coerceAtLeast(0)
            } else {
                null
            }
        }
        dateView.text = when {
            state.charging && toMilestone != null -> "$milestone% in ${formatMinutes(toMilestone)}"
            state.charging && state.minutesToFull != null -> "Full in ${formatMinutes(state.minutesToFull)}"
            state.charging -> "Charging"
            low -> "On battery: low"
            else -> "On battery"
        }
        dateView.setTextColor(if (low) RED else 0xFFBBBBBB.toInt())
        infoView.text = clockText
    }

    private fun formatMinutes(total: Int): String {
        val hours = total / 60
        val minutes = total % 60
        return if (hours > 0) "$hours h $minutes min" else "$minutes min"
    }

    private fun updateCommand(clockText: String, hour: Int, now: Long, use24Hour: Boolean) {
        val greeting = Complications.greeting(hour)
        val next = Agenda.upcoming(context, days = 7, limit = 1).firstOrNull()
        val agenda = if (next == null) {
            "No events soon"
        } else {
            "${DateFormat.format(if (use24Hour) "HH:mm" else "h:mm a", next.begin)} ${next.title}\n" +
                Agenda.countdown(next.begin - now)
        }
        val music = MusicSession.title()?.let { title ->
            val artist = MusicSession.artist()
            if (artist.isNullOrBlank()) title else "$title\n$artist"
        } ?: "Nothing playing"
        val tiles = listOf(
            "$greeting\n$clockText",
            weatherText.ifEmpty { "Weather not set" },
            agenda,
            music,
            batteryLine(context).ifEmpty { "Battery" },
        )
        val idle = MusicSession.title() == null && (next == null || next.begin - now > 60 * MINUTE_MS)
        commandView.alpha = if (settings.commandIdleFade && idle) 0.25f else 1f
        commandView.render(tiles, settings.commandHero) { index ->
            settings.commandHero = index
            Haptics.tap(context)
            update()
        }
    }

    private fun systemLines(): String {
        val battery = readBattery(context)?.percent?.let { "$it%" } ?: "--"
        val activity = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        activity?.getMemoryInfo(info)
        val usedMb = if (activity == null) 0L else (info.totalMem - info.availMem) / (1024 * 1024)
        val totalMb = (info.totalMem / (1024 * 1024)).coerceAtLeast(1L)
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / 1_000_000_000.0
        val freeGb = stat.availableBytes / 1_000_000_000.0
        return "Model     ${Build.MODEL}\n" +
            "Battery   $battery\n" +
            "Memory    $usedMb / $totalMb MB\n" +
            "Storage   %.0f / %.0f GB free".format(freeGb, totalGb)
    }

    private fun starshipLines(clockText: String): String {
        val percent = readBattery(context)?.percent ?: 0
        val bar = "#".repeat(percent / 10) + "-".repeat(10 - percent / 10)
        val next = Agenda.upcoming(context, days = 1, limit = 1).firstOrNull()?.title ?: "--"
        return "STARSHIP CONSOLE\n" +
            "T+  $clockText\n" +
            "WX  ${weatherText.ifEmpty { "--" }}\n" +
            "NXT $next\n" +
            "PWR [$bar] $percent%"
    }

    private fun worldLines(calendar: Calendar): String {
        val localOffset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        val zones = settings.worldZoneIds()
        if (zones.isEmpty()) return "Add cities in More settings"
        return zones.joinToString("\n") { id ->
            try {
                val zone = ZoneId.of(id)
                val now = ZonedDateTime.now(zone)
                val hours = (now.offset.totalSeconds * 1000 - localOffset) / 3_600_000
                val relative = when {
                    hours == 0 -> "same"
                    hours > 0 -> "+$hours h"
                    else -> "$hours h"
                }
                val name = id.substringAfterLast('/').replace('_', ' ')
                "%-12s %s  %s".format(name, String.format("%02d:%02d", now.hour, now.minute), relative)
            } catch (e: Exception) {
                "$id: unknown zone"
            }
        }
    }

    /** Next alarm and next calendar event in the next 12 hours, joined on one line. */
    private fun infoLine(now: Long, use24Hour: Boolean): String {
        val parts = mutableListOf<String>()
        val alarm = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
        if (alarm != null) {
            parts += "Alarm " + DateFormat.format(if (use24Hour) "HH:mm" else "h:mm a", alarm.triggerTime)
        }
        Agenda.upcoming(context, days = 1, limit = 1).firstOrNull()?.let { event ->
            parts += "${DateFormat.format("HH:mm", event.begin)} ${event.title}"
        }
        return parts.joinToString("   ·   ")
    }

    /** Night is the schedule, the sun (when the location is known), a dark room, or the forced setting. */
    private fun refreshNight() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minuteOfDay = hour * 60 + calendar.get(Calendar.MINUTE)
        val dark = settings.lightSensorNight && lux >= 0f && lux < DARK_LUX
        val scheduled = if (settings.nightMode == Settings.NIGHT_SUN) {
            val location = settings.location()
            val times = location?.let {
                SolarTimes.sunriseAndSunset(it.first, it.second, System.currentTimeMillis())
            }
            if (times != null) SolarTimes.isNight(minuteOfDay, times.first, times.second) else settings.isNightAt(hour)
        } else {
            settings.isNightAt(hour)
        }
        val night = when (settings.nightMode) {
            Settings.NIGHT_ON -> true
            Settings.NIGHT_OFF -> false
            else -> scheduled || dark
        }
        tintView.setBackgroundColor(withStrength(settings.tintColor, settings.tintStrength))
        tintView.visibility = visibleIf(night && settings.redTintAtNight)
        if (night != lastNight) {
            lastNight = night
            onMinute?.invoke(night)
        }
    }

    private fun withStrength(color: Int, percent: Int): Int {
        val alpha = percent.coerceIn(0, 100) * 255 / 100
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    /** Fetches weather in the background at most every 30 minutes, and keeps the last reading for offline use. */
    private fun refreshWeather(now: Long) {
        if (weatherText.isEmpty()) weatherText = cachedWeather()
        val location = settings.location()
        if (location == null) {
            weatherText = ""
            return
        }
        if (weatherFetchedAt != 0L && now - weatherFetchedAt < WEATHER_INTERVAL_MS) return
        weatherFetchedAt = now
        val fahrenheit = when (settings.weatherUnit) {
            "f" -> true
            "c" -> false
            else -> Locale.getDefault().country == "US"
        }
        Thread {
            val current = WeatherClient.fetch(location.first, location.second, fahrenheit)
            val hours = if (settings.weatherLayout == "hourly") {
                WeatherClient.fetchHourly(location.first, location.second, fahrenheit)
            } else {
                emptyList()
            }
            handler.post {
                if (!running) return@post
                if (hours.isNotEmpty()) {
                    val unit = current?.unit ?: if (fahrenheit) "°F" else "°C"
                    hourlyText = hours.joinToString("\n") { hour ->
                        "${hour.label}   ${hour.temperature.roundToInt()}$unit   ${WeatherClient.describe(hour.code)}"
                    }
                }
                if (current != null) {
                    val summary = WeatherClient.describe(current.code)
                    val line = "${current.temperature.roundToInt()}${current.unit}" +
                        if (summary.isEmpty()) "" else " · $summary"
                    weatherText = line
                    settings.weatherCache = "$line|${System.currentTimeMillis()}"
                    weatherView.text = line
                }
                if (currentFace() == Settings.FACE_WEATHER) panelView.text = weatherPanelText()
            }
        }.start()
    }

    private fun cachedWeather(): String = settings.weatherCache.substringBefore('|', "")

    private fun renderMusic() {
        val hasTrack = MusicSession.title() != null
        val face = currentFace()
        playerCard.visibility = visibleIf(hasTrack && face != Settings.FACE_COMMAND)
        playerCard.showRadio(radioStations().isNotEmpty())
        if (hasTrack) playerCard.render()
    }

    private fun radioStations(): List<Pair<String, String>> {
        val list = settings.radioList()
        if (list.isNotEmpty()) return list
        return settings.radioUrl?.let { listOf("Radio" to it) } ?: emptyList()
    }

    /** Starts the next station in the list, or stops the one playing. */
    private fun toggleRadio() {
        if (RadioPlayer.isPlaying) {
            RadioPlayer.stop()
        } else {
            val stations = radioStations()
            if (stations.isNotEmpty()) {
                val (name, url) = stations[radioPick % stations.size]
                radioPick++
                RadioPlayer.play(url) {
                    handler.post {
                        Toast.makeText(context, "$name could not be opened", Toast.LENGTH_SHORT).show()
                        playerCard.render()
                    }
                }
            }
        }
        playerCard.render()
    }

    /** Loads the photo for the current slideshow slot, only when it changes. */
    private fun refreshPhoto() {
        if (currentFace() != Settings.FACE_PHOTO) return
        val pictures = settings.photoUris()
        if (pictures.isEmpty()) {
            photoView.visibility = View.GONE
            return
        }
        val interval = settings.photoInterval.coerceAtLeast(5) * 1000L
        val index = ((System.currentTimeMillis() / interval) % pictures.size).toInt()
        if (index == photoIndexShown && photoLoaded == pictures[index]) return
        photoIndexShown = index
        photoLoaded = pictures[index]
        val drawable = loadPhoto(pictures[index])
        photoView.setImageDrawable(drawable)
        (drawable as? AnimatedImageDrawable)?.start()
        photoView.visibility = visibleIf(drawable != null)
        photoCaptionView.text = settings.photoCaption
    }

    /** A picture for the photo face. GIFs keep their animation; other pictures are cropped to the screen. */
    private fun loadPhoto(uriString: String): Drawable? {
        val uri = Uri.parse(uriString)
        if (context.contentResolver.getType(uri) == "image/gif") {
            return try {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.setTargetSampleSize(2)
                }
            } catch (e: Exception) {
                null
            }
        }
        return loadBitmap(uriString)?.let { BitmapDrawable(context.resources, it) }
    }

    /** Downsampled, then cropped to the screen shape with faces kept in frame. */
    private fun loadBitmap(uriString: String): Bitmap? {
        val decoded = try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
            }
        } catch (e: Exception) {
            null
        } ?: return null
        val aspect = metrics.widthPixels.toFloat() / metrics.heightPixels
        return try {
            PhotoCrop.cropToScreen(decoded, aspect)
        } catch (e: Exception) {
            decoded
        }
    }

    /** Nudges the whole block by a few dp each minute to protect OLED panels from burn-in. */
    private fun shiftForBurnIn() {
        val px = metrics.density * 8f
        column.translationX = Random.nextFloat() * 2 * px - px
        column.translationY = Random.nextFloat() * 2 * px - px
    }

    /** The clock text for [now], with seconds when that setting is on. */
    private fun clockString(now: Long, use24Hour: Boolean): String {
        val pattern = if (use24Hour) "HH:mm" else "h:mm"
        return DateFormat.format(if (settings.showSeconds) "$pattern:ss" else pattern, now).toString()
    }

    /** Work that runs each second: seconds on the digital faces, the neon pulse, the colon, and the panels. */
    private fun tickFace(now: Long) {
        val face = currentFace()
        if (settings.showSeconds && face in SECOND_FACES) {
            timeView.text = clockString(now, DateFormat.is24HourFormat(context))
        }
        if (face == Settings.FACE_NEON && settings.neonPulse) {
            timeView.setShadowLayer(if ((now / 1000) % 2 == 0L) 40f else 20f, 0f, 0f, NEON_GREEN)
        }
        if (face == Settings.FACE_SEGMENTED) {
            segmentedView.setColonOn(!settings.segmentBlink || (now / 1000) % 2 == 0L)
        }
        if (face == Settings.FACE_PANELS) panelsView.tick(now, weatherText, hourlyText)
    }

    /** The clock colour from the palette setting. The charging and neon faces keep their own colours. */
    private fun applyPalette(face: String) {
        if (face != Settings.FACE_CHARGE && face != Settings.FACE_NEON) {
            timeView.setTextColor(Settings.PALETTES.getOrNull(settings.clockPalette)?.second ?: Color.WHITE)
        }
        updateGradient()
    }

    /** A gradient from the palette colour at the top to a lighter shade at the bottom, when the setting is on. */
    private fun updateGradient() {
        val face = currentFace()
        val base = Settings.PALETTES.getOrNull(settings.clockPalette)?.second ?: Color.WHITE
        val plain = !settings.clockGradient || face == Settings.FACE_CHARGE || face == Settings.FACE_NEON || timeView.height == 0
        timeView.paint.shader = if (plain) {
            null
        } else {
            LinearGradient(0f, 0f, 0f, timeView.height.toFloat(), base, mix(base, Color.WHITE, 0.45f), Shader.TileMode.CLAMP)
        }
        timeView.invalidate()
    }

    private fun mix(from: Int, to: Int, amount: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
    )

    /** The overlay time sits in one of six corners and centres, with the chosen font and an optional outline. */
    private fun updateOverlay(clockText: String) {
        val position = settings.overlayPosition
        overlayView.visibility = visibleIf(position in OVERLAY_GRAVITY.indices)
        if (position !in OVERLAY_GRAVITY.indices) return
        val margin = (24 * metrics.density).toInt()
        overlayView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, OVERLAY_GRAVITY[position],
        ).apply { setMargins(margin, margin, margin, margin) }
        overlayView.typeface = typefaceFor(settings.overlayFont, thin = false)
        overlayView.setShadowLayer(if (settings.overlayOutline) 8f else 0f, 0f, 0f, 0xFF000000.toInt())
        overlayView.text = clockText
    }

    /** A warm or cool wash over the whole screen, from the colour balance setting (at most 45% strength). */
    private fun updateBalance() {
        val balance = settings.colourBalance.coerceIn(-100, 100)
        balanceView.visibility = visibleIf(balance != 0)
        if (balance == 0) return
        val colour = if (balance > 0) 0xFFFF9800.toInt() else 0xFF2196F3.toInt()
        balanceView.setBackgroundColor(withStrength(colour, abs(balance) * 45 / 100))
    }

    /** Fetches the station's position in the background, at most once a minute. */
    private fun refreshIss() {
        if (currentFace() != Settings.FACE_ISS) return
        Thread {
            val position = IssClient.refresh()
            handler.post {
                if (!running || position == null) return@post
                issView.setPosition(position.latitude, position.longitude)
                if (currentFace() == Settings.FACE_ISS) panelView.text = IssClient.lines(position)
            }
        }.start()
    }

    /** Weather face text: the current reading, then the hourly strip when that layout is chosen. */
    private fun weatherPanelText(): String {
        val current = weatherText.ifEmpty { "Weather not set yet" }
        return if (settings.weatherLayout == "hourly" && hourlyText.isNotEmpty()) "$current\n$hourlyText" else current
    }

    private fun visibleIf(visible: Boolean) = if (visible) View.VISIBLE else View.GONE

    private fun sized(width: Int, height: Int) = LinearLayout.LayoutParams(width, height).apply {
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun text(sizeSp: Float, color: Int, typeface: Typeface = Typeface.DEFAULT): TextView =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            this.typeface = typeface
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(12f, 0f, 0f, 0xCC000000.toInt())
        }

    private fun columnParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun wrapParams() = columnParams()

    /** The player card takes a fixed share of the width so its text can ellipsize. */
    private fun playerParams(duo: Boolean): LinearLayout.LayoutParams {
        val share = when {
            duo -> 0.36f
            portrait -> 0.9f
            else -> 0.6f
        }
        return LinearLayout.LayoutParams((metrics.widthPixels * share).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { gravity = Gravity.CENTER_HORIZONTAL }
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private companion object {
        const val MINUTE_MS = 60_000L
        /** Faces that show seconds when the setting is on. */
        val SECOND_FACES = setOf(Settings.FACE_DIGITAL, Settings.FACE_BIG, Settings.FACE_NEON, Settings.FACE_MATRIX)
        /** Gravity for each overlay position: top-left, top-centre, top-right, bottom-left, bottom-centre, bottom-right. */
        val OVERLAY_GRAVITY = intArrayOf(
            Gravity.TOP or Gravity.START,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            Gravity.TOP or Gravity.END,
            Gravity.BOTTOM or Gravity.START,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            Gravity.BOTTOM or Gravity.END,
        )
        const val WEATHER_INTERVAL_MS = 30 * 60_000L
        const val DARK_LUX = 5f
        val NEON_GREEN = 0xFF39FF14.toInt()
        val RED = 0xFFFF3B30.toInt()
    }
}

/** Battery percentage, with "charging" when plugged in. Shared by the clock and the widget. */
fun batteryLine(context: Context): String {
    val state = readBattery(context) ?: return ""
    return if (state.charging) "${state.percent}% · charging" else "${state.percent}%"
}
