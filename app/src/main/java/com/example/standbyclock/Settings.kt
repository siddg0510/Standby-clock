package com.example.standbyclock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User preferences. Each space (see [Spaces]) has its own file. The activity, the screensaver,
 * the charging receiver and the tools all read the same values.
 */
/**
 * Look of one panel: corner radius (dp), background opacity (%), border colour index into
 * [Settings.BORDER_COLOURS], border width (dp), text zoom (%) and gap around the panel (dp).
 */
data class PanelStyle(
    val corner: Int = 16,
    val alpha: Int = 100,
    val border: Int = 0,
    val thickness: Int = 0,
    val zoom: Int = 100,
    val spacing: Int = 8,
) {
    fun encode(): String = listOf(corner, alpha, border, thickness, zoom, spacing).joinToString(",")

    companion object {
        fun parse(text: String): PanelStyle {
            val parts = text.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size < 6) return PanelStyle()
            return PanelStyle(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
        }
    }
}

class Settings(context: Context) {

    private val prefs = context.getSharedPreferences(Spaces.prefsName(context), Context.MODE_PRIVATE)

    // ---- Clock face and layout ----

    var face: String
        get() = prefs.getString(KEY_FACE, FACE_DIGITAL) ?: FACE_DIGITAL
        set(value) = prefs.edit().putString(KEY_FACE, value).apply()

    /** The face the screensaver shows. */
    var screensaverFace: String
        get() = prefs.getString(KEY_DREAM_FACE, FACE_DIGITAL) ?: FACE_DIGITAL
        set(value) = prefs.edit().putString(KEY_DREAM_FACE, value).apply()

    /** One of [ORIENTATION_LANDSCAPE], [ORIENTATION_PORTRAIT] or [ORIENTATION_AUTO]. */
    var orientation: String
        get() = prefs.getString(KEY_ORIENTATION, ORIENTATION_LANDSCAPE) ?: ORIENTATION_LANDSCAPE
        set(value) = prefs.edit().putString(KEY_ORIENTATION, value).apply()

    /** Font for the clock text: system, serif, mono, rounded or condensed. */
    var fontStyle: String
        get() = prefs.getString(KEY_FONT, FONT_SYSTEM) ?: FONT_SYSTEM
        set(value) = prefs.edit().putString(KEY_FONT, value).apply()

    /** Hides the background effects (photo, rain, snow, sound canvas) and keeps the screen pure black. */
    var pureBlack: Boolean
        get() = prefs.getBoolean(KEY_PURE_BLACK, false)
        set(value) = prefs.edit().putBoolean(KEY_PURE_BLACK, value).apply()

    /** Shows the time in small type under the analog face. */
    var analogSubDial: Boolean
        get() = prefs.getBoolean(KEY_SUB_DIAL, false)
        set(value) = prefs.edit().putBoolean(KEY_SUB_DIAL, value).apply()

    /** Retroflip style: 0 dark, 1 light, 2 warm. */
    var retroStyle: Int
        get() = prefs.getInt(KEY_RETRO_STYLE, 0)
        set(value) = prefs.edit().putInt(KEY_RETRO_STYLE, value).apply()

    /** Retroflip card background: opaque card, or transparent. */
    var retroOpaque: Boolean
        get() = prefs.getBoolean(KEY_RETRO_OPAQUE, true)
        set(value) = prefs.edit().putBoolean(KEY_RETRO_OPAQUE, value).apply()

    /** Battery style on the charging face: pixel, material or neon. */
    var batteryStyle: String
        get() = prefs.getString(KEY_BATTERY_STYLE, "pixel") ?: "pixel"
        set(value) = prefs.edit().putString(KEY_BATTERY_STYLE, value).apply()

    /** Hides the clock on the charging face, leaving only the battery. */
    var batteryHideClock: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_HIDE_CLOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_HIDE_CLOCK, value).apply()

    /** Charge target for the "time to milestone" line, 80 or 100. */
    var chargeMilestone: Int
        get() = prefs.getInt(KEY_MILESTONE, 80)
        set(value) = prefs.edit().putInt(KEY_MILESTONE, value).apply()

    /** Ambient falling snow behind the clock. */
    var snow: Boolean
        get() = prefs.getBoolean(KEY_SNOW, false)
        set(value) = prefs.edit().putBoolean(KEY_SNOW, value).apply()

    /** Corner complication ids, see [Complications]. */
    var cornerLeft: String
        get() = prefs.getString(KEY_CORNER_LEFT, Complications.NONE) ?: Complications.NONE
        set(value) = prefs.edit().putString(KEY_CORNER_LEFT, value).apply()

    var cornerRight: String
        get() = prefs.getString(KEY_CORNER_RIGHT, Complications.NONE) ?: Complications.NONE
        set(value) = prefs.edit().putString(KEY_CORNER_RIGHT, value).apply()

    /** Command Center: which tile is shown large, 0 to 4. */
    var commandHero: Int
        get() = prefs.getInt(KEY_COMMAND_HERO, 0)
        set(value) = prefs.edit().putInt(KEY_COMMAND_HERO, value).apply()

    /** World clock cities as zone ids, comma separated. */
    var worldZones: String
        get() = prefs.getString(KEY_WORLD, "Europe/London,America/New_York,Asia/Tokyo") ?: ""
        set(value) = prefs.edit().putString(KEY_WORLD, value).apply()

    fun worldZoneIds(): List<String> = worldZones.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(5)

    // ---- Photos ----

    var photoUri: String?
        get() = prefs.getString(KEY_PHOTO, null)
        set(value) = prefs.edit().putString(KEY_PHOTO, value).apply()

    /** Slideshow pictures, one URI per line. When empty, [photoUri] is used. */
    var photoList: String
        get() = prefs.getString(KEY_PHOTO_LIST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PHOTO_LIST, value).apply()

    /** Seconds each picture stays on screen. */
    var photoInterval: Int
        get() = prefs.getInt(KEY_PHOTO_INTERVAL, 60)
        set(value) = prefs.edit().putInt(KEY_PHOTO_INTERVAL, value).apply()

    /** Shows this month's calendar over the photo. */
    var photoCalendar: Boolean
        get() = prefs.getBoolean(KEY_PHOTO_CALENDAR, false)
        set(value) = prefs.edit().putBoolean(KEY_PHOTO_CALENDAR, value).apply()

    fun photoUris(): List<String> {
        val list = photoList.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (list.isNotEmpty()) list else listOfNotNull(photoUri)
    }

    // ---- Quick launch ----

    /** One of [LAUNCH_OFF], [LAUNCH_CHARGING] or [LAUNCH_LANDSCAPE]. Older builds stored a boolean, which maps to charging. */
    var launchMode: String
        get() = prefs.getString(KEY_LAUNCH, null)
            ?: if (prefs.getBoolean(KEY_OPEN_CHARGING_OLD, false)) LAUNCH_CHARGING else LAUNCH_OFF
        set(value) = prefs.edit().putString(KEY_LAUNCH, value).apply()

    var launchWirelessOnly: Boolean
        get() = prefs.getBoolean(KEY_LAUNCH_WIRELESS, false)
        set(value) = prefs.edit().putBoolean(KEY_LAUNCH_WIRELESS, value).apply()

    var launchScreenOff: Boolean
        get() = prefs.getBoolean(KEY_LAUNCH_SCREEN_OFF, false)
        set(value) = prefs.edit().putBoolean(KEY_LAUNCH_SCREEN_OFF, value).apply()

    var launchLockedOnly: Boolean
        get() = prefs.getBoolean(KEY_LAUNCH_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_LAUNCH_LOCKED, value).apply()

    /** Launch window start hour, or -1 for any time. */
    var launchStartHour: Int
        get() = prefs.getInt(KEY_LAUNCH_START, -1)
        set(value) = prefs.edit().putInt(KEY_LAUNCH_START, value).apply()

    /** Launch window end hour, or -1 for any time. */
    var launchEndHour: Int
        get() = prefs.getInt(KEY_LAUNCH_END, -1)
        set(value) = prefs.edit().putInt(KEY_LAUNCH_END, value).apply()

    /** Wi-Fi network names, one per line. Empty means any network. */
    var wifiWhitelist: String
        get() = prefs.getString(KEY_WIFI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WIFI, value).apply()

    fun wifiNetworks(): List<String> = wifiWhitelist.lines().map { it.trim() }.filter { it.isNotEmpty() }

    // ---- Night and burn-in ----

    /** Night mode: [NIGHT_SCHEDULE], [NIGHT_SUN], [NIGHT_ON] or [NIGHT_OFF]. */
    var nightMode: String
        get() = prefs.getString(KEY_NIGHT_MODE, NIGHT_SCHEDULE) ?: NIGHT_SCHEDULE
        set(value) = prefs.edit().putString(KEY_NIGHT_MODE, value).apply()

    var redTintAtNight: Boolean
        get() = prefs.getBoolean(KEY_RED_TINT, true)
        set(value) = prefs.edit().putBoolean(KEY_RED_TINT, value).apply()

    var tintColor: Int
        get() = prefs.getInt(KEY_TINT_COLOR, 0xFFFF0000.toInt())
        set(value) = prefs.edit().putInt(KEY_TINT_COLOR, value).apply()

    /** Overlay strength, 0 to 100 percent. */
    var tintStrength: Int
        get() = prefs.getInt(KEY_TINT_STRENGTH, 33)
        set(value) = prefs.edit().putInt(KEY_TINT_STRENGTH, value).apply()

    /** Daytime brightness, 10 to 100 percent. 100 leaves the system brightness alone. */
    var dayBrightness: Int
        get() = prefs.getInt(KEY_DAY_BRIGHTNESS, 100)
        set(value) = prefs.edit().putInt(KEY_DAY_BRIGHTNESS, value).apply()

    /** Night brightness, 1 to 50 percent. */
    var nightBrightness: Int
        get() = prefs.getInt(KEY_NIGHT_BRIGHTNESS, 5)
        set(value) = prefs.edit().putInt(KEY_NIGHT_BRIGHTNESS, value).apply()

    var lightSensorNight: Boolean
        get() = prefs.getBoolean(KEY_LIGHT_NIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_LIGHT_NIGHT, value).apply()

    var nightStartHour: Int
        get() = prefs.getInt(KEY_NIGHT_START, 22)
        set(value) = prefs.edit().putInt(KEY_NIGHT_START, value).apply()

    var nightEndHour: Int
        get() = prefs.getInt(KEY_NIGHT_END, 7)
        set(value) = prefs.edit().putInt(KEY_NIGHT_END, value).apply()

    var checkerboard: Boolean
        get() = prefs.getBoolean(KEY_CHECKER, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECKER, value).apply()

    var checkerDim: Int
        get() = prefs.getInt(KEY_CHECKER_DIM, 35)
        set(value) = prefs.edit().putInt(KEY_CHECKER_DIM, value).apply()

    /** Minutes without touches before the screen goes black. 0 means never. */
    var sleepMinutes: Int
        get() = prefs.getInt(KEY_SLEEP, 10)
        set(value) = prefs.edit().putInt(KEY_SLEEP, value).apply()

    var doubleTapClose: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_TAP, true)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_TAP, value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    // ---- Weather ----

    /** Temperature unit: "auto" (by region), "c" or "f". */
    var weatherUnit: String
        get() = prefs.getString(KEY_WEATHER_UNIT, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_WEATHER_UNIT, value).apply()

    /** Weather layout: "centered" or "jumbo". */
    var weatherLayout: String
        get() = prefs.getString(KEY_WEATHER_LAYOUT, "centered") ?: "centered"
        set(value) = prefs.edit().putString(KEY_WEATHER_LAYOUT, value).apply()

    var weatherCity: String
        get() = prefs.getString(KEY_WEATHER_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEATHER_CITY, value).apply()

    /** Last weather reading, kept for when the network is unavailable: "temperature|unit|description". */
    var weatherCache: String
        get() = prefs.getString(KEY_WEATHER_CACHE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEATHER_CACHE, value).apply()

    /** Saved coordinates for weather, or null when no location has been set. */
    fun location(): Pair<Double, Double>? =
        if (prefs.contains(KEY_LAT) && prefs.contains(KEY_LON)) {
            prefs.getFloat(KEY_LAT, 0f).toDouble() to prefs.getFloat(KEY_LON, 0f).toDouble()
        } else {
            null
        }

    fun saveLocation(latitude: Double, longitude: Double) {
        prefs.edit().putFloat(KEY_LAT, latitude.toFloat()).putFloat(KEY_LON, longitude.toFloat()).apply()
    }

    // ---- Sound ----

    /** One of the NOISE_ ids. */
    var noiseStation: String
        get() = prefs.getString(KEY_NOISE, NOISE_OFF) ?: NOISE_OFF
        set(value) = prefs.edit().putString(KEY_NOISE, value).apply()

    /** Sleep timer for the sound in minutes. 0 plays until turned off. */
    var noiseMinutes: Int
        get() = prefs.getInt(KEY_NOISE_MINUTES, 60)
        set(value) = prefs.edit().putInt(KEY_NOISE_MINUTES, value).apply()

    /** Coloured glows drifting behind the sound. */
    var visualCanvas: Boolean
        get() = prefs.getBoolean(KEY_VISUAL, false)
        set(value) = prefs.edit().putBoolean(KEY_VISUAL, value).apply()

    /** Internet radio stream the user typed in, played by the radio button on the player card. */
    var radioUrl: String?
        get() = prefs.getString(KEY_RADIO, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_RADIO, value?.trim()).apply()

    /** Radio stations, one per line as "Name|URL". */
    var radioStations: String
        get() = prefs.getString(KEY_RADIO_STATIONS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RADIO_STATIONS, value).apply()

    fun radioList(): List<Pair<String, String>> = radioStations.lines().mapNotNull { line ->
        val parts = line.split("|", limit = 2)
        if (parts.size == 2 && parts[1].isNotBlank()) parts[0].trim() to parts[1].trim() else null
    }

    /** Alert when a timer or Pomodoro ends: "sound", "vibrate" or "both". */
    var alertMode: String
        get() = prefs.getString(KEY_ALERT, "both") ?: "both"
        set(value) = prefs.edit().putString(KEY_ALERT, value).apply()

    // ---- Tools ----

    var soberStart: Long
        get() = prefs.getLong(KEY_SOBER_START, 0L)
        set(value) = prefs.edit().putLong(KEY_SOBER_START, value).apply()

    var soberCostPerDay: Float
        get() = prefs.getFloat(KEY_SOBER_COST, 0f)
        set(value) = prefs.edit().putFloat(KEY_SOBER_COST, value).apply()

    /** Protocols or principles, one per line. */
    var protocols: String
        get() = prefs.getString(KEY_PROTOCOLS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROTOCOLS, value).apply()

    /** To-do items as "1|text" (done) or "0|text" (not done), one per line. */
    var todo: String
        get() = prefs.getString(KEY_TODO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TODO, value).apply()

    /** Countdown as "yyyy-MM-dd|label", or blank. */
    var countdown: String
        get() = prefs.getString(KEY_COUNTDOWN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COUNTDOWN, value).apply()

    /** Package names allowed to show notifications in the stack (focus mode). Empty means every app. */
    var focusApps: String
        get() = prefs.getString(KEY_FOCUS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FOCUS, value).apply()

    fun focusPackages(): Set<String> = focusApps.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** Pomodoros completed today. */
    fun pomodorosToday(): Int = prefs.getInt(pomodoroKey(), 0)

    fun addPomodoro() {
        prefs.edit().putInt(pomodoroKey(), pomodorosToday() + 1).apply()
    }

    private fun pomodoroKey(): String = "pomo_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** Tally counter value, index 0 to 3. */
    fun tally(index: Int): Int = prefs.getInt("tally_$index", 0)

    fun setTally(index: Int, value: Int) {
        prefs.edit().putInt("tally_$index", value).apply()
    }

    // ---- Look and feel ----

    /** Index into [PALETTES] for the clock colour. */
    var clockPalette: Int
        get() = prefs.getInt(KEY_PALETTE, 0)
        set(value) = prefs.edit().putInt(KEY_PALETTE, value).apply()

    /** Colour theme id from [Themes], or "off" to keep the palette and black background. */
    var themeId: String
        get() = prefs.getString(KEY_THEME, "off") ?: "off"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Moves to the next theme (off, then each theme) and returns its id. */
    fun cycleTheme(): String {
        themeId = Themes.nextId(themeId)
        return themeId
    }

    /** Shows a row of buttons along the bottom: style, face, group and settings. */
    var quickControls: Boolean
        get() = prefs.getBoolean(KEY_QUICK_CONTROLS, false)
        set(value) = prefs.edit().putBoolean(KEY_QUICK_CONTROLS, value).apply()

    /** Paints the clock digits with a gradient from the palette colour to a lighter shade. */
    var clockGradient: Boolean
        get() = prefs.getBoolean(KEY_GRADIENT, false)
        set(value) = prefs.edit().putBoolean(KEY_GRADIENT, value).apply()

    /** Shows seconds on the digital, big, neon and matrix faces. */
    var showSeconds: Boolean
        get() = prefs.getBoolean(KEY_SECONDS, false)
        set(value) = prefs.edit().putBoolean(KEY_SECONDS, value).apply()

    /** Warm (positive) or cool (negative) tint over the whole screen, from -100 to 100. */
    var colourBalance: Int
        get() = prefs.getInt(KEY_BALANCE, 0)
        set(value) = prefs.edit().putInt(KEY_BALANCE, value).apply()

    /** Command Center tiles fade out when nothing is playing and no event is near. */
    var commandIdleFade: Boolean
        get() = prefs.getBoolean(KEY_CMD_IDLE, false)
        set(value) = prefs.edit().putBoolean(KEY_CMD_IDLE, value).apply()

    /** The colon on the segmented clock blinks each second. */
    var segmentBlink: Boolean
        get() = prefs.getBoolean(KEY_BLINK, true)
        set(value) = prefs.edit().putBoolean(KEY_BLINK, value).apply()

    /** The neon face's glow pulses each second. */
    var neonPulse: Boolean
        get() = prefs.getBoolean(KEY_NEON_PULSE, true)
        set(value) = prefs.edit().putBoolean(KEY_NEON_PULSE, value).apply()

    /** Radial face: the minutes that have passed are dim and the rest are bright. */
    var radialInvert: Boolean
        get() = prefs.getBoolean(KEY_RADIAL_INVERT, false)
        set(value) = prefs.edit().putBoolean(KEY_RADIAL_INVERT, value).apply()

    /** Text shown under the photo, for example a caption or a meme line. */
    var photoCaption: String
        get() = prefs.getString(KEY_CAPTION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CAPTION, value).apply()

    /** Quotes, one per line. The quote complication and panel rotate through them every five minutes. */
    var quotes: String
        get() = prefs.getString(KEY_QUOTES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_QUOTES, value).apply()

    /** Year, month and week complications show a bar as well as the percentage. */
    var progressBars: Boolean
        get() = prefs.getBoolean(KEY_PROGRESS_BARS, false)
        set(value) = prefs.edit().putBoolean(KEY_PROGRESS_BARS, value).apply()

    /** A charging launch closes when the phone is turned upright, not only when it is unplugged. */
    var closeWhenUpright: Boolean
        get() = prefs.getBoolean(KEY_UPRIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_UPRIGHT, value).apply()

    /** Where the overlay time sits: -1 is off, 0 to 5 run top-left, top-centre, top-right, bottom-left, bottom-centre, bottom-right. */
    var overlayPosition: Int
        get() = prefs.getInt(KEY_OVERLAY, -1)
        set(value) = prefs.edit().putInt(KEY_OVERLAY, value).apply()

    /** Font for the overlay time, one of the FONT_ ids. */
    var overlayFont: String
        get() = prefs.getString(KEY_OVERLAY_FONT, FONT_SYSTEM) ?: FONT_SYSTEM
        set(value) = prefs.edit().putString(KEY_OVERLAY_FONT, value).apply()

    /** Dark outline behind the overlay time, for reading it over photos. */
    var overlayOutline: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_OUTLINE, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_OUTLINE, value).apply()

    /** The Pomodoro timer moves between a 25-minute work phase and a 5-minute break. */
    var pomodoroCycle: Boolean
        get() = prefs.getBoolean(KEY_POMO_CYCLE, false)
        set(value) = prefs.edit().putBoolean(KEY_POMO_CYCLE, value).apply()

    // ---- Multi-panel layouts ----

    /** Two, three or four panels. */
    var panelCount: Int
        get() = prefs.getInt(KEY_PANEL_COUNT, 2).coerceIn(2, 4)
        set(value) = prefs.edit().putInt(KEY_PANEL_COUNT, value.coerceIn(2, 4)).apply()

    /** Relative panel sizes, comma separated, for example "2,1,1". */
    var panelSplit: String
        get() = prefs.getString(KEY_PANEL_SPLIT, "1,1") ?: "1,1"
        set(value) = prefs.edit().putString(KEY_PANEL_SPLIT, value).apply()

    /** Panel sizes from [panelSplit], one per panel, padded with 1 where the text is short. */
    fun panelWeights(): List<Float> {
        val parsed = panelSplit.split(",").mapNotNull { it.trim().toFloatOrNull()?.takeIf { w -> w > 0f } }
        return List(panelCount) { parsed.getOrNull(it) ?: 1f }
    }

    /** What each panel shows, one stack per panel. Panels are separated by "|" and items by ",". */
    var panelItems: String
        get() = prefs.getString(KEY_PANEL_ITEMS, "clock|weather") ?: "clock|weather"
        set(value) = prefs.edit().putString(KEY_PANEL_ITEMS, value).apply()

    /** The stacks, one list of item ids per panel. An item is a built-in id or "widget:ID" for a home-screen widget. */
    fun panelStacks(): List<List<String>> {
        val stacks = panelItems.split("|").map { stack ->
            stack.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return List(panelCount) { stacks.getOrNull(it).orEmpty() }
    }

    /** Saves the stacks, keeping the panel count in step with them. */
    fun savePanelStacks(stacks: List<List<String>>) {
        panelItems = stacks.joinToString("|") { it.joinToString(",") }
    }

    /** Seconds before a stacked panel moves to its next item. 0 holds the first item. */
    var panelRotateSeconds: Int
        get() = prefs.getInt(KEY_PANEL_ROTATE, 0)
        set(value) = prefs.edit().putInt(KEY_PANEL_ROTATE, value).apply()

    /** Yoga layout: the first panel grows while no music is playing. */
    var panelYoga: Boolean
        get() = prefs.getBoolean(KEY_PANEL_YOGA, false)
        set(value) = prefs.edit().putBoolean(KEY_PANEL_YOGA, value).apply()

    /** Look of one panel. */
    fun panelStyle(index: Int): PanelStyle =
        prefs.getString("panel_style_$index", null)?.let { PanelStyle.parse(it) } ?: PanelStyle()

    fun setPanelStyle(index: Int, style: PanelStyle) {
        prefs.edit().putString("panel_style_$index", style.encode()).apply()
    }

    // ---- Pomodoro history ----

    /** Pomodoros for each of the last [days] days, today first, as "EEE d MMM" to count. */
    fun pomodoroHistory(days: Int): List<Pair<String, Int>> {
        val now = System.currentTimeMillis()
        val keyFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val labelFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        return (0 until days).map { offset ->
            val date = Date(now - offset * 86_400_000L)
            labelFormat.format(date) to prefs.getInt("pomo_" + keyFormat.format(date), 0)
        }
    }

    // ---- Hints ----

    var hintShown: Boolean
        get() = prefs.getBoolean(KEY_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_HINT, value).apply()

    // ---- Navigation ----

    /** The group of faces that [face] belongs to. */
    fun groupOf(current: String): List<String> =
        FACE_GROUPS.firstOrNull { current in it.second }?.second ?: listOf(current)

    private fun usable(face: String): Boolean = face != FACE_PHOTO || photoUris().isNotEmpty()

    /** Moves to the next or previous face within the current group, and returns it. */
    fun cycleFace(forward: Boolean = true): String {
        val faces = groupOf(face).filter { usable(it) }.ifEmpty { listOf(FACE_DIGITAL) }
        val current = faces.indexOf(face)
        val step = if (forward) 1 else faces.size - 1
        val next = faces[(current.coerceAtLeast(0) + step) % faces.size]
        face = next
        return next
    }

    /** Moves to the next or previous group, landing on its first usable face. */
    fun switchGroup(forward: Boolean): String {
        val index = FACE_GROUPS.indexOfFirst { face in it.second }.coerceAtLeast(0)
        val step = if (forward) 1 else FACE_GROUPS.size - 1
        val group = FACE_GROUPS[(index + step) % FACE_GROUPS.size].second
        val next = group.firstOrNull { usable(it) } ?: FACE_DIGITAL
        face = next
        return next
    }

    /** True when [hour] falls inside the night window. A window that starts and ends at the same hour is off. */
    fun isNightAt(hour: Int): Boolean = inWindow(nightStartHour, nightEndHour, hour)

    companion object {
        const val FACE_DIGITAL = "digital"
        const val FACE_ANALOG = "analog"
        const val FACE_SEGMENTED = "segmented"
        const val FACE_WORD = "word"
        const val FACE_NEON = "neon"
        const val FACE_BIG = "big"
        const val FACE_RETROFLIP = "retroflip"
        const val FACE_SOLAR = "solar"
        const val FACE_RADIAL = "radial"
        const val FACE_MATRIX = "matrix"
        const val FACE_DEMENTIA = "dementia"
        const val FACE_CHARGE = "charge"
        const val FACE_ROMAN = "roman"
        const val FACE_PIXEL = "pixel"
        const val FACE_STRIKING = "striking"
        const val FACE_WORLD = "world"
        const val FACE_COMMAND = "command"
        const val FACE_SYSTEM = "system"
        const val FACE_STARSHIP = "starship"
        const val FACE_WEATHER = "weather"
        const val FACE_DUO = "duo"
        const val FACE_PHOTO = "photo"
        const val FACE_ISS = "iss"
        const val FACE_PANELS = "panels"

        /** Clock colours. The first is the default. */
        val PALETTES = listOf(
            "White" to 0xFFFFFFFF.toInt(),
            "Warm amber" to 0xFFFFD58A.toInt(),
            "Ice blue" to 0xFFA8E0FF.toInt(),
            "Mint" to 0xFF9FF5D0.toInt(),
            "Rose" to 0xFFFFB3C7.toInt(),
            "Lavender" to 0xFFD0B8FF.toInt(),
        )

        /** Panel border colours. Index 0 means no border. */
        val BORDER_COLOURS = listOf(
            "None" to 0,
            "White" to 0xFFFFFFFF.toInt(),
            "Amber" to 0xFFFFB000.toInt(),
            "Blue" to 0xFF2196F3.toInt(),
            "Green" to 0xFF4CAF50.toInt(),
        )

        /** Items a panel can show, with the label shown in the panel editor. */
        val PANEL_ITEMS = listOf(
            "clock" to "Clock",
            "weather" to "Weather",
            "agenda" to "Calendar card (up to three events)",
            "music" to "Music card (with controls)",
            "battery" to "Battery",
            "protocol" to "Protocol (rotating)",
            "quote" to "Quote (rotating)",
            "pomodoro" to "Pomodoros today",
            "system" to "System",
        )

        /** Face value and the label shown in settings. */
        val FACE_OPTIONS = listOf(
            FACE_DIGITAL to "Digital",
            FACE_ANALOG to "Analog",
            FACE_SEGMENTED to "Segmented (LCD)",
            FACE_WORD to "Word clock",
            FACE_NEON to "Neon",
            FACE_BIG to "Big crop",
            FACE_RETROFLIP to "Retroflip",
            FACE_SOLAR to "Solar",
            FACE_RADIAL to "Radial",
            FACE_MATRIX to "Matrix rain",
            FACE_DEMENTIA to "Dementia (large text)",
            FACE_ROMAN to "Roman numerals",
            FACE_PIXEL to "Pixel art",
            FACE_WORLD to "World clock",
            FACE_STRIKING to "Striking calendar",
            FACE_CHARGE to "Charging (battery)",
            FACE_COMMAND to "Command Center",
            FACE_SYSTEM to "System dashboard",
            FACE_STARSHIP to "Starship console",
            FACE_WEATHER to "Weather clock",
            FACE_DUO to "Duo (clock + widgets)",
            FACE_PHOTO to "Photo (your pictures)",
        )

        /** Groups for horizontal swipes. Vertical swipes and taps move within the group. */
        val FACE_GROUPS: List<Pair<String, List<String>>> = listOf(
            "Clocks" to listOf(
                FACE_DIGITAL, FACE_ANALOG, FACE_SEGMENTED, FACE_WORD, FACE_NEON, FACE_BIG, FACE_RETROFLIP,
                FACE_SOLAR, FACE_RADIAL, FACE_MATRIX, FACE_DEMENTIA, FACE_ROMAN, FACE_PIXEL, FACE_WORLD,
                FACE_STRIKING, FACE_ISS,
            ),
            "Dashboards" to listOf(FACE_COMMAND, FACE_SYSTEM, FACE_STARSHIP, FACE_CHARGE),
            "Duo" to listOf(FACE_DUO, FACE_PANELS),
            "Photos" to listOf(FACE_PHOTO),
            "Weather" to listOf(FACE_WEATHER),
        )

        const val LAUNCH_OFF = "off"
        const val LAUNCH_CHARGING = "charging"
        const val LAUNCH_LANDSCAPE = "landscape"

        const val NIGHT_SCHEDULE = "schedule"
        const val NIGHT_SUN = "sun"
        const val NIGHT_ON = "on"
        const val NIGHT_OFF = "off"

        const val NOISE_OFF = "off"
        const val NOISE_WHITE = "white"
        const val NOISE_PINK = "pink"
        const val NOISE_BROWN = "brown"
        const val NOISE_RAIN = "rain"
        const val NOISE_OCEAN = "ocean"
        const val NOISE_FIRE = "fire"
        const val NOISE_WIND = "wind"
        const val NOISE_STREAM = "stream"

        const val ORIENTATION_LANDSCAPE = "landscape"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val ORIENTATION_AUTO = "auto"

        const val FONT_SYSTEM = "system"
        const val FONT_SERIF = "serif"
        const val FONT_MONO = "mono"
        const val FONT_ROUNDED = "rounded"
        const val FONT_CONDENSED = "condensed"

        /** True when [hour] is inside the window from [start] to [end]. Negative bounds mean any time. */
        fun inWindow(start: Int, end: Int, hour: Int): Boolean {
            if (start < 0 || end < 0 || start == end) return start < 0 || end < 0
            return if (start < end) hour in start until end else hour >= start || hour < end
        }

        private const val KEY_FACE = "face"
        private const val KEY_DREAM_FACE = "dream_face"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_FONT = "font"
        private const val KEY_PURE_BLACK = "pure_black"
        private const val KEY_SUB_DIAL = "analog_sub_dial"
        private const val KEY_RETRO_STYLE = "retro_style"
        private const val KEY_RETRO_OPAQUE = "retro_opaque"
        private const val KEY_BATTERY_STYLE = "battery_style"
        private const val KEY_BATTERY_HIDE_CLOCK = "battery_hide_clock"
        private const val KEY_MILESTONE = "charge_milestone"
        private const val KEY_SNOW = "snow"
        private const val KEY_CORNER_LEFT = "corner_left"
        private const val KEY_CORNER_RIGHT = "corner_right"
        private const val KEY_COMMAND_HERO = "command_hero"
        private const val KEY_WORLD = "world_zones"
        private const val KEY_PHOTO = "photo_uri"
        private const val KEY_PHOTO_LIST = "photo_list"
        private const val KEY_PHOTO_INTERVAL = "photo_interval"
        private const val KEY_PHOTO_CALENDAR = "photo_calendar"
        private const val KEY_OPEN_CHARGING_OLD = "open_when_charging"
        private const val KEY_LAUNCH = "launch_mode"
        private const val KEY_LAUNCH_WIRELESS = "launch_wireless"
        private const val KEY_LAUNCH_SCREEN_OFF = "launch_screen_off"
        private const val KEY_LAUNCH_LOCKED = "launch_locked"
        private const val KEY_LAUNCH_START = "launch_start"
        private const val KEY_LAUNCH_END = "launch_end"
        private const val KEY_WIFI = "wifi_whitelist"
        private const val KEY_NIGHT_MODE = "night_mode"
        private const val KEY_RED_TINT = "red_tint_at_night"
        private const val KEY_TINT_COLOR = "tint_color"
        private const val KEY_TINT_STRENGTH = "tint_strength"
        private const val KEY_DAY_BRIGHTNESS = "day_brightness"
        private const val KEY_NIGHT_BRIGHTNESS = "night_brightness"
        private const val KEY_LIGHT_NIGHT = "light_sensor_night"
        private const val KEY_NIGHT_START = "night_start_hour"
        private const val KEY_NIGHT_END = "night_end_hour"
        private const val KEY_CHECKER = "checkerboard"
        private const val KEY_CHECKER_DIM = "checker_dim"
        private const val KEY_SLEEP = "sleep_minutes"
        private const val KEY_DOUBLE_TAP = "double_tap_close"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_WEATHER_UNIT = "weather_unit"
        private const val KEY_WEATHER_LAYOUT = "weather_layout"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_WEATHER_CACHE = "weather_cache"
        private const val KEY_LAT = "weather_lat"
        private const val KEY_LON = "weather_lon"
        private const val KEY_NOISE = "noise_station"
        private const val KEY_NOISE_MINUTES = "noise_minutes"
        private const val KEY_VISUAL = "visual_canvas"
        private const val KEY_RADIO = "radio_url"
        private const val KEY_RADIO_STATIONS = "radio_stations"
        private const val KEY_ALERT = "alert_mode"
        private const val KEY_SOBER_START = "sober_start"
        private const val KEY_SOBER_COST = "sober_cost"
        private const val KEY_PROTOCOLS = "protocols"
        private const val KEY_TODO = "todo"
        private const val KEY_COUNTDOWN = "countdown"
        private const val KEY_FOCUS = "focus_apps"
        private const val KEY_HINT = "hint_shown"
        private const val KEY_PALETTE = "clock_palette"
        private const val KEY_GRADIENT = "clock_gradient"
        private const val KEY_SECONDS = "show_seconds"
        private const val KEY_BALANCE = "colour_balance"
        private const val KEY_CMD_IDLE = "command_idle_fade"
        private const val KEY_BLINK = "segment_blink"
        private const val KEY_NEON_PULSE = "neon_pulse"
        private const val KEY_RADIAL_INVERT = "radial_invert"
        private const val KEY_CAPTION = "photo_caption"
        private const val KEY_QUOTES = "quotes"
        private const val KEY_PROGRESS_BARS = "progress_bars"
        private const val KEY_UPRIGHT = "close_when_upright"
        private const val KEY_OVERLAY = "overlay_position"
        private const val KEY_OVERLAY_FONT = "overlay_font"
        private const val KEY_OVERLAY_OUTLINE = "overlay_outline"
        private const val KEY_POMO_CYCLE = "pomodoro_cycle"
        private const val KEY_PANEL_COUNT = "panel_count"
        private const val KEY_PANEL_SPLIT = "panel_split"
        private const val KEY_PANEL_ITEMS = "panel_items"
        private const val KEY_PANEL_ROTATE = "panel_rotate"
        private const val KEY_PANEL_YOGA = "panel_yoga"
        private const val KEY_THEME = "theme_id"
        private const val KEY_QUICK_CONTROLS = "quick_controls"
    }
}
