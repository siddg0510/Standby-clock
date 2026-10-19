package com.example.standbyclock

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import java.time.ZoneId

/** One screen: its title and the views under the large title. */
class Page(val title: String, val views: List<View>)

/** One option on a choice page. [swatch] draws a colour dot in front of the label. */
class ChoiceOption(val value: String, val label: String, val swatch: Int? = null)

class ChoiceSection(val header: String?, val options: List<ChoiceOption>)

/** A choice page: used when a setting has more than four options. Selecting a row applies it and goes back. */
class ChoiceSpec(
    val title: String,
    val footer: String?,
    val sections: List<ChoiceSection>,
    val current: () -> String,
    val select: (String) -> Unit,
) {
    fun labelOf(value: String): String =
        sections.flatMap { it.options }.firstOrNull { it.value == value }?.label ?: ""

    fun swatchOf(value: String): Int? =
        sections.flatMap { it.options }.firstOrNull { it.value == value }?.swatch
}

/** A list page: the existing items, a Remove button on each, and an Add row. Every change is stored at once. */
class ListSpec(
    val title: String,
    val footer: String?,
    val empty: String,
    val addLabel: String,
    val fields: List<DialogField>,
    val items: () -> List<String>,
    val store: (List<String>) -> Unit,
    val primary: (String) -> String,
    val secondary: (String) -> String?,
    val validate: (List<String>, List<String>) -> String?,
    val make: (List<String>) -> String,
    val max: Int = Int.MAX_VALUE,
)

class Category(val key: String, val title: String, val subtitle: String, val words: String = "")

/** A searchable setting: [target] is the page it lives on ([category] itself, or a sub-page below it). */
class SearchEntry(val label: String, val category: String, val target: String = category, val words: String = "")

/**
 * Builds every settings page from code with the shared components in [SettingsUi]. Pages are looked up by key:
 * "root", a category ("clock"), a sub-page ("clock/photos", "charging/wifi") or a choice page ("pick/face").
 */
class SettingsPages(private val a: SettingsActivity, private val ui: SettingsUi) {

    private val s: Settings get() = a.settings

    private var query = ""
    private var searchBox: View? = null
    private var results: LinearLayout? = null
    private var cityNote: String? = null
    private var locationNote: String? = null

    fun build(key: String): Page? = when {
        key == ROOT -> rootPage()
        key == "clock" -> clockPage()
        key == "clock/photos" -> photosPage()
        key == "widgets" -> widgetsPage()
        key == "display" -> displayPage()
        key == "charging" -> chargingPage()
        key == "sound" -> soundPage()
        key == "tools" -> toolsPage()
        key == "tools/spaces" -> spacesPage()
        key.startsWith("pick/") -> choice(key.removePrefix("pick/"))?.let { choicePage(it) }
        else -> listSpec(key)?.let { listPage(it) }
    }

    // ---- Root and search ----

    private fun rootPage(): Page {
        if (searchBox == null) {
            val (box, _) = ui.searchBar(query) {
                query = it
                renderResults()
            }
            searchBox = box
        }
        val holder = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        results = holder
        renderResults()
        return Page("Settings", listOf(searchBox!!, holder))
    }

    private fun renderResults() {
        val holder = results ?: return
        holder.removeAllViews()
        fun add(view: View) {
            holder.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (holder.childCount > 0) topMargin = ui.dp(16)
            })
        }
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            add(ui.section(null, null, CATEGORIES.map { categoryRow(it) }))
            return
        }
        val entries = INDEX.filter { it.label.lowercase().contains(needle) || it.words.lowercase().contains(needle) }
        val categories = CATEGORIES.filter { category ->
            category.title.lowercase().contains(needle) ||
                category.subtitle.lowercase().contains(needle) ||
                category.words.lowercase().contains(needle) ||
                entries.any { it.category == category.key }
        }
        if (categories.isEmpty()) {
            add(ui.note("No settings match \"${query.trim()}\"."))
            return
        }
        add(ui.section(null, null, categories.map { categoryRow(it) }))
        if (entries.isNotEmpty()) {
            add(ui.section("Matching settings", null, entries.take(30).map { entry ->
                val categoryTitle = CATEGORIES.first { it.key == entry.category }.title
                ui.navRow(entry.label, categoryTitle, singleLineSubtitle = true) { openEntry(entry) }
            }))
        }
    }

    private fun categoryRow(category: Category): View =
        ui.navRow(category.title, category.subtitle, singleLineSubtitle = true) {
            if (category.key == "preview") a.openClock() else a.open(category.key)
        }

    private fun openEntry(entry: SearchEntry) {
        when {
            entry.category == "preview" -> a.openClock()
            entry.target == entry.category -> a.open(entry.category)
            else -> a.openPath(listOf(entry.category, entry.target))
        }
    }

    // ---- Clock ----

    private fun clockPage(): Page {
        val views = mutableListOf<View>()

        views += ui.section("Style", "Orientation applies the next time the clock opens.", listOf(
            choiceRow("Clock face", "face"),
            choiceRow("Screensaver face", "ssface"),
            ui.segmentedRow("Orientation", listOf("Landscape", "Portrait", "Auto"), ORIENTATIONS.indexOf(s.orientation)) {
                s.orientation = ORIENTATIONS[it]
            },
        ))

        views += ui.section("Colour", "A theme sets colours and an ambient background. With the theme off, the clock colour is used.", listOf(
            choiceRow("Theme", "theme"),
            choiceRow("Clock colour", "palette"),
            ui.switchRow("Gradient on the digits", null, s.clockGradient) { s.clockGradient = it },
            ui.sliderRow("Colour balance", -100, 100, s.colourBalance, { if (it > 0) "+$it" else "$it" }) { s.colourBalance = it },
            ui.switchRow("Pure black", "Hides photos, rain, snow and the sound canvas", s.pureBlack) { s.pureBlack = it },
        ))

        views += ui.section("Time", null, listOf(
            ui.valueRow("24-hour time", if (DateFormat.is24HourFormat(a)) "On" else "Off", "Follows the system clock format") {
                a.launch(Intent(android.provider.Settings.ACTION_DATE_SETTINGS))
            },
            ui.switchRow("Show seconds", "Digital, big, neon and matrix faces", s.showSeconds) { s.showSeconds = it },
            choiceRow("Font", "font"),
        ))

        views += ui.section("Face details", null, listOf(
            ui.switchRow("Analog sub-dial", "Small digital time under the analog face", s.analogSubDial) { s.analogSubDial = it },
            ui.segmentedRow("Retroflip style", listOf("Dark", "Light", "Warm"), s.retroStyle) { s.retroStyle = it },
            ui.switchRow("Retroflip card", "Opaque card behind the digits", s.retroOpaque) { s.retroOpaque = it },
            ui.switchRow("Blinking colon", "Segmented clock", s.segmentBlink) { s.segmentBlink = it },
            ui.switchRow("Pulsing glow", "Neon face", s.neonPulse) { s.neonPulse = it },
            ui.switchRow("Dim passed minutes", "Radial face", s.radialInvert) { s.radialInvert = it },
            ui.switchRow("Complications as bars", "Year, month and week progress", s.progressBars) { s.progressBars = it },
        ))

        views += ui.section("Corner complications", null, listOf(
            choiceRow("Top-left", "corner_left"),
            choiceRow("Top-right", "corner_right"),
        ))

        views += ui.section("Overlay time", "A large time that sits in a corner or the middle of an edge, over any face.", listOf(
            choiceRow("Position", "overlay_pos"),
            choiceRow("Font", "overlay_font"),
            ui.switchRow("Dark outline", "Helps reading it over photos", s.overlayOutline) { s.overlayOutline = it },
        ))

        views += ui.section("Charging screen", null, listOf(
            ui.segmentedRow("Style", listOf("Pixel", "Material", "Neon"), BATTERY_STYLES.indexOf(s.batteryStyle)) {
                s.batteryStyle = BATTERY_STYLES[it]
            },
            ui.switchRow("Hide the clock", "Leave only the battery", s.batteryHideClock) { s.batteryHideClock = it },
            ui.segmentedRow("Milestone", listOf("80%", "100%"), if (s.chargeMilestone == 100) 1 else 0) {
                s.chargeMilestone = if (it == 1) 100 else 80
            },
        ))

        val pictures = s.photoUris().size
        views += ui.section("Photos", null, listOf(
            ui.navRow("Photos", "$pictures picture${if (pictures == 1) "" else "s"}, interval, calendar, caption") { a.open("clock/photos") },
        ))
        return Page("Clock", views)
    }

    private fun photosPage(): Page {
        val count = s.photoUris().size
        val caption = s.photoCaption
        return Page("Photos", listOf(
            ui.section("Pictures", "Choose several pictures for a slideshow, or one picture for the photo face.", listOf(
                ui.valueRow("Slideshow pictures", if (count == 0) "None" else "$count chosen") { a.pickPictures() },
                ui.valueRow("Photo for the photo face", if (s.photoUri == null) "None" else "Chosen") { a.pickPhoto() },
                choiceRow("Show each picture for", "photo_interval"),
            )),
            ui.section("Over the photo", "The caption is shown under the photo and works with GIF pictures too.", listOf(
                ui.switchRow("Calendar", "Show this month's calendar over the photo", s.photoCalendar) { s.photoCalendar = it },
                ui.valueRow("Caption", if (caption.isBlank()) "None" else caption) {
                    ui.prompt("Photo caption", null, listOf(DialogField("Caption", caption)), "Done") { values ->
                        s.photoCaption = values[0]
                        a.refresh()
                    }
                },
            )),
        ))
    }

    // ---- Widgets ----

    private fun widgetsPage(): Page {
        val unit = listOf("auto", "c", "f")
        val layouts = listOf("centered", "jumbo", "hourly")
        val city = s.weatherCity
        return Page("Widgets", listOf(
            ui.section("Duo and panels", "The Duo and Panels faces show several cards side by side.", listOf(
                ui.navRow("Edit panels", "Layout, sizes, contents and the look of each panel") {
                    a.startActivity(Intent(a, PanelEditorActivity::class.java))
                },
            )),
            ui.section("Command Center", null, listOf(
                ui.switchRow("Fade tiles when idle", "When nothing is playing and no event is near", s.commandIdleFade) {
                    s.commandIdleFade = it
                },
            )),
            ui.section("Weather", "Sunrise and sunset for night mode also use the weather location.", listOf(
                ui.segmentedRow("Layout", listOf("Centred", "Jumbo", "Hourly"), layouts.indexOf(s.weatherLayout)) {
                    s.weatherLayout = layouts[it]
                },
                ui.segmentedRow("Temperature", listOf("By region", "Celsius", "Fahrenheit"), unit.indexOf(s.weatherUnit)) {
                    s.weatherUnit = unit[it]
                },
                ui.valueRow("City", if (city.isBlank()) "Not set" else city, cityNote) { searchCity() },
                ui.valueRow("Use my location", if (s.location() != null) "Saved" else "Not set", locationNote) { useLocation() },
            )),
            ui.section("On the clock", "Home-screen widget: long-press an empty spot on the home screen, choose Widgets, then pick Standby Clock.", listOf(
                ui.switchRow("Battery line", "Show the battery under the clock", s.showBatteryLine) { s.showBatteryLine = it },
                ui.switchRow("Quick controls", "Style, face, group and settings along the bottom", s.quickControls) { s.quickControls = it },
            )),
        ))
    }

    private fun searchCity() {
        ui.prompt(
            "Weather city",
            "Find a city and use its weather.",
            listOf(DialogField("City, for example Lisbon", s.weatherCity)),
            "Search",
            validate = { if (it[0].isEmpty()) "Enter a city" else null },
        ) { values ->
            val name = values[0]
            cityNote = "Searching..."
            a.refresh()
            Thread {
                val place = WeatherClient.geocode(name)
                a.runOnUiThread {
                    if (a.isDestroyed) return@runOnUiThread
                    if (place == null) {
                        cityNote = "No city found for \"$name\"."
                    } else {
                        s.saveLocation(place.latitude, place.longitude)
                        s.weatherCity = place.name
                        cityNote = "Weather set for ${place.name}."
                    }
                    a.refresh()
                }
            }.start()
        }
    }

    private fun useLocation() {
        val granted = a.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            a.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), SettingsActivity.REQUEST_LOCATION)
            return
        }
        fetchLocation()
    }

    /** Uses the last known network fix, or asks for a new one when there is none. */
    @Suppress("MissingPermission")
    fun fetchLocation() {
        val manager = a.getSystemService(LocationManager::class.java) ?: return
        val last = try {
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        }
        if (last != null) {
            storeLocation(last)
            return
        }
        locationNote = "Finding your location..."
        a.refresh()
        manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, a.mainExecutor) { location ->
            if (!a.isDestroyed) {
                if (location != null) {
                    storeLocation(location)
                } else {
                    locationNote = "No location found. Turn on location and try again."
                    a.refresh()
                }
            }
        }
    }

    private fun storeLocation(location: Location) {
        s.saveLocation(location.latitude, location.longitude)
        locationNote = "Weather location updated."
        a.refresh()
    }

    // ---- Display and night ----

    private fun displayPage(): Page {
        val nightModes = listOf(Settings.NIGHT_SCHEDULE, Settings.NIGHT_SUN, Settings.NIGHT_ON, Settings.NIGHT_OFF)
        return Page("Display and night", listOf(
            ui.section(
                "Night",
                "Schedule uses the times below. Sunset needs a weather location. The light sensor can also trigger night in a dark room.",
                listOf(
                    ui.segmentedRow("Night mode", listOf("Schedule", "Sunset", "Always on", "Always off"), nightModes.indexOf(s.nightMode)) {
                        s.nightMode = nightModes[it]
                    },
                    choiceRow("Night starts", "night_start"),
                    choiceRow("Night ends", "night_end"),
                    ui.switchRow("Light sensor", "Also night when the room is dark", s.lightSensorNight) { s.lightSensorNight = it },
                ),
            ),
            ui.section("Night tint", "At 100% every colour becomes the tint colour and black stays black.", listOf(
                ui.switchRow("Tint the screen at night", null, s.redTintAtNight) { s.redTintAtNight = it },
                choiceRow("Tint colour", "tint_color"),
                ui.sliderRow("Tint strength", 0, 100, s.tintStrength) { s.tintStrength = it },
            )),
            ui.section("Brightness", "Day brightness at 100% leaves the system brightness alone.", listOf(
                ui.sliderRow("Day brightness", 10, 100, s.dayBrightness) { s.dayBrightness = it },
                ui.sliderRow("Night brightness", 1, 50, s.nightBrightness) { s.nightBrightness = it },
            )),
            ui.section("Screen", null, listOf(
                choiceRow("Screen goes black after", "sleep"),
                ui.switchRow("Double-tap to close", "Double-tap the clock to close the app", s.doubleTapClose) { s.doubleTapClose = it },
                ui.switchRow("Falling snow", "Seasonal effect behind the clock", s.snow) { s.snow = it },
            )),
            ui.section("Burn-in protection", "The checkerboard shifts the pixels every minute.", listOf(
                ui.switchRow("Checkerboard pixel shift", null, s.checkerboard) { s.checkerboard = it },
                ui.sliderRow("Checkerboard dimming", 0, 100, s.checkerDim) { s.checkerDim = it },
            )),
        ))
    }

    // ---- Charging and launch ----

    private fun chargingPage(): Page {
        val modes = listOf(Settings.LAUNCH_OFF, Settings.LAUNCH_CHARGING, Settings.LAUNCH_LANDSCAPE)
        val networks = s.wifiNetworks().size
        val notifications = ChargingLauncher.notificationsAllowed(a)
        val fullScreen = ChargingLauncher.fullScreenAllowed(a)
        val listener = a.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(ComponentName(a, MusicListenerService::class.java)) == true
        val location = a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        return Page("Charging and launch", listOf(
            ui.section(
                "Open when charging",
                "Opens the clock when the charger goes in. Allow notifications and full-screen alerts so it can open from the lock screen.",
                listOf(
                    ui.segmentedRow("Quick launch", listOf("Off", "Charging", "Landscape"), modes.indexOf(s.launchMode)) {
                        s.launchMode = modes[it]
                        if (modes[it] != Settings.LAUNCH_OFF && !ChargingLauncher.notificationsAllowed(a)) a.fixNotifications()
                    },
                ),
            ),
            ui.section("Conditions", "Leave the Wi-Fi list empty to allow any network. Reading the network name needs location access.", listOf(
                ui.switchRow("Close when turned upright", "A charging launch closes when the phone stands upright", s.closeWhenUpright) {
                    s.closeWhenUpright = it
                },
                ui.switchRow("Wireless charging only", null, s.launchWirelessOnly) { s.launchWirelessOnly = it },
                ui.switchRow("Only when the screen is off", null, s.launchScreenOff) { s.launchScreenOff = it },
                ui.switchRow("Only on the lock screen", null, s.launchLockedOnly) { s.launchLockedOnly = it },
                choiceRow("Launch only after", "launch_start"),
                choiceRow("and before", "launch_end"),
                ui.valueRow("Wi-Fi networks", if (networks == 0) "Any" else "$networks") { a.open("charging/wifi") },
            )),
            ui.section("Permissions", "Fix opens the right system screen, or asks for the permission.", listOf(
                ui.statusRow("Notifications", null, notifications) { a.fixNotifications() },
                ui.statusRow("Full-screen alerts", "Needed to open from the background", fullScreen) { a.fixFullScreen() },
                ui.statusRow("Notification access", "Music controls and the notification list", listener) { a.fixNotificationAccess() },
                ui.statusRow("Location", "Reads the Wi-Fi network name", location) { a.fixLocation() },
            )),
            ui.section(
                "Test and battery",
                "Samsung: in Settings > Apps > Standby Clock > Battery, choose Unrestricted. Also make sure Standby Clock is not listed " +
                    "under Settings > Battery > Background usage limits (Sleeping or Deep sleeping apps).",
                listOf(
                    ui.actionRow("Test the charging launch now") {
                        val posted = ChargingLauncher.show(a)
                        Toast.makeText(
                            a,
                            if (posted) "Test posted. The clock should open." else "Notifications are blocked.",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    ui.navRow("Samsung battery tip", "Open the app's settings to set Battery to Unrestricted") { a.openAppDetails() },
                ),
            ),
        ))
    }

    // ---- Sound ----

    private fun soundPage(): Page {
        val alerts = listOf("sound", "vibrate", "both")
        val stations = s.radioStations.lines().count { it.isNotBlank() }
        val stream = s.radioUrl
        return Page("Sound", listOf(
            ui.section("Background sound", "The sound stops by itself after the chosen time.", listOf(
                choiceRow("Sound", "noise"),
                choiceRow("Sleep timer", "noise_minutes"),
                ui.switchRow("Colour glow", "Drifting colours behind the sound", s.visualCanvas) { s.visualCanvas = it },
            )),
            ui.section(
                "Radio",
                "Plays while the clock is open, from the Radio button on the player card. Use a direct stream address, not a web page.",
                listOf(
                    ui.valueRow("Radio stream", stream ?: "Not set") {
                        ui.prompt(
                            "Radio stream",
                            "A direct stream address. Leave empty to clear it.",
                            listOf(DialogField("https://.../stream.mp3", stream.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)),
                            "Done",
                        ) { values ->
                            s.radioUrl = values[0]
                            a.refresh()
                        }
                    },
                    ui.valueRow("Radio stations", if (stations == 0) "None" else "$stations") { a.open("sound/stations") },
                ),
            ),
            ui.section("Alerts", null, listOf(
                ui.segmentedRow("When a timer or Pomodoro ends", listOf("Sound", "Vibrate", "Both"), alerts.indexOf(s.alertMode)) {
                    s.alertMode = alerts[it]
                },
                ui.switchRow("Haptic feedback", "On taps and counters", s.haptics) { s.haptics = it },
            )),
        ))
    }

    // ---- Tools and about ----

    private fun toolsPage(): Page {
        val quotes = s.quotes.lines().count { it.isNotBlank() }
        val protocols = s.protocols.lines().count { it.isNotBlank() }
        val focus = s.focusPackages().size
        val zones = s.worldZoneIds().size
        val calendar = a.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        @Suppress("DEPRECATION")
        val version = try {
            a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
        return Page("Tools and about", listOf(
            ui.section("Tools", null, listOf(
                ui.navRow("Tools", "Agenda, tally counters, sober counter, countdown, to-do, games") {
                    a.startActivity(Intent(a, ToolsActivity::class.java))
                },
                ui.navRow("Timer and stopwatch") { a.startActivity(Intent(a, TimerActivity::class.java)) },
                ui.switchRow("Pomodoro cycle", "Switch between 25 minutes of work and a 5-minute break", s.pomodoroCycle) {
                    s.pomodoroCycle = it
                },
            )),
            ui.section("Lists", "Quotes and protocols rotate in a corner when you choose them as a complication.", listOf(
                ui.valueRow("Quotes", countText(quotes)) { a.open("tools/quotes") },
                ui.valueRow("Protocols", countText(protocols)) { a.open("tools/protocols") },
                ui.valueRow("Focus apps", if (focus == 0) "All apps" else "$focus") { a.open("tools/focus") },
                ui.valueRow("World clock cities", countText(zones)) { a.open("tools/world") },
            )),
            ui.section("Spaces", "Each space keeps its own screens and settings.", listOf(
                ui.valueRow("Spaces", Spaces.current(a)) { a.open("tools/spaces") },
            )),
            ui.section("Screens", null, listOf(
                ui.navRow("All screens", "Every group and face on one screen") {
                    a.startActivity(Intent(a, OverviewActivity::class.java))
                },
            )),
            ui.section("Permissions", null, listOf(
                ui.statusRow("Calendar access", "Events on the clock and in the agenda", calendar) { a.fixCalendar() },
            )),
            ui.section("Help", null, listOf(
                ui.navRow("Show the walkthrough") { MainActivity.showTour(a, 0) },
                ui.infoRow("Version", version.ifEmpty { "1.0" }),
            )),
        ))
    }

    private fun countText(count: Int) = if (count == 0) "None" else "$count"

    private fun spacesPage(): Page {
        val current = Spaces.current(a)
        val rows = Spaces.names(a).map { name ->
            ui.checkRow(name, name == current) {
                if (name != current) {
                    Spaces.switchTo(a, name)
                    a.recreate()
                }
            }
        }.toMutableList<View>()
        rows += ui.actionRow("Add a space", plus = true) {
            ui.prompt(
                "New space",
                "For example Work or Bedroom.",
                listOf(DialogField("Name")),
                "Add",
                validate = { values ->
                    if (values[0].isEmpty() || Spaces.names(a).any { it.equals(values[0], ignoreCase = true) }) "Enter a new name" else null
                },
            ) { values ->
                Spaces.add(a, values[0])
                a.refresh()
            }
        }
        return Page("Spaces", listOf(ui.section(null, "Each space keeps its own screens and settings. Tap a space to switch to it.", rows)))
    }

    // ---- Choice pages ----

    private fun choiceRow(title: String, id: String): View {
        val spec = choice(id) ?: return ui.emptyRow(title)
        val value = spec.current()
        return ui.valueRow(title, spec.labelOf(value), swatch = spec.swatchOf(value)) { a.open("pick/$id") }
    }

    private fun choicePage(spec: ChoiceSpec): Page {
        val current = spec.current()
        val last = spec.sections.lastIndex
        return Page(spec.title, spec.sections.mapIndexed { index, section ->
            ui.section(section.header, if (index == last) spec.footer else null, section.options.map { option ->
                ui.checkRow(option.label, option.value == current, option.swatch) {
                    spec.select(option.value)
                    a.back()
                }
            })
        })
    }

    private fun options(vararg pairs: Pair<String, String>) =
        listOf(ChoiceSection(null, pairs.map { ChoiceOption(it.first, it.second) }))

    private fun hourOptions(any: Boolean): List<ChoiceSection> {
        val hours = (0..23).map { ChoiceOption("$it", "%02d:00".format(it)) }
        return listOf(ChoiceSection(null, if (any) listOf(ChoiceOption("-1", "Any time")) + hours else hours))
    }

    private fun faceChoice(title: String, get: () -> String, set: (String) -> Unit): ChoiceSpec {
        val labels = Settings.FACE_OPTIONS.toMap()
        return ChoiceSpec(title, null, Settings.FACE_GROUPS.map { (group, faces) ->
            ChoiceSection(group, faces.map { ChoiceOption(it, labels[it] ?: it) })
        }, get, set)
    }

    private fun fontChoice(title: String, get: () -> String, set: (String) -> Unit) = ChoiceSpec(
        title, null,
        options(
            Settings.FONT_SYSTEM to "System", Settings.FONT_SERIF to "Serif", Settings.FONT_MONO to "Monospace",
            Settings.FONT_ROUNDED to "Rounded", Settings.FONT_CONDENSED to "Condensed",
        ),
        get, set,
    )

    private fun cornerChoice(title: String, get: () -> String, set: (String) -> Unit) = ChoiceSpec(
        title, "Small text in a top corner of the clock.",
        listOf(ChoiceSection(null, Complications.OPTIONS.map { ChoiceOption(it.first, it.second) })),
        get, set,
    )

    private fun choice(id: String): ChoiceSpec? = when (id) {
        "face" -> faceChoice("Clock face", { s.face }, { s.face = it })
        "ssface" -> faceChoice("Screensaver face", { s.screensaverFace }, { s.screensaverFace = it })
        "theme" -> ChoiceSpec(
            "Theme", "A colour set with an ambient background, from Standby Pro.",
            listOf(ChoiceSection(null, listOf(ChoiceOption("off", "Off (use the clock colour)")) +
                Themes.ALL.map { ChoiceOption(it.id, it.name, it.foreground) })),
            { s.themeId }, { s.themeId = it },
        )
        "palette" -> ChoiceSpec(
            "Clock colour", "Used when no theme is set.",
            listOf(ChoiceSection(null, Settings.PALETTES.mapIndexed { index, (name, colour) -> ChoiceOption("$index", name, colour) })),
            { "${s.clockPalette}" }, { s.clockPalette = it.toInt() },
        )
        "font" -> fontChoice("Clock font", { s.fontStyle }, { s.fontStyle = it })
        "overlay_font" -> fontChoice("Overlay font", { s.overlayFont }, { s.overlayFont = it })
        "corner_left" -> cornerChoice("Top-left", { s.cornerLeft }, { s.cornerLeft = it })
        "corner_right" -> cornerChoice("Top-right", { s.cornerRight }, { s.cornerRight = it })
        "overlay_pos" -> ChoiceSpec(
            "Overlay position", null,
            options(
                "-1" to "Off", "0" to "Top left", "1" to "Top centre", "2" to "Top right",
                "3" to "Bottom left", "4" to "Bottom centre", "5" to "Bottom right",
            ),
            { "${s.overlayPosition}" }, { s.overlayPosition = it.toInt() },
        )
        "photo_interval" -> ChoiceSpec(
            "Show each picture for", null,
            options("15" to "15 seconds", "30" to "30 seconds", "60" to "1 minute", "300" to "5 minutes", "600" to "10 minutes"),
            { "${s.photoInterval}" }, { s.photoInterval = it.toInt() },
        )
        "night_start" -> ChoiceSpec("Night starts", null, hourOptions(false), { "${s.nightStartHour}" }, { s.nightStartHour = it.toInt() })
        "night_end" -> ChoiceSpec("Night ends", null, hourOptions(false), { "${s.nightEndHour}" }, { s.nightEndHour = it.toInt() })
        "tint_color" -> ChoiceSpec(
            "Tint colour", "Deep red or amber keep your eyes best adapted to the dark.",
            listOf(ChoiceSection(null, TINTS.map { ChoiceOption("${it.second}", it.first, it.second) })),
            { "${s.tintColor}" }, { s.tintColor = it.toInt() },
        )
        "sleep" -> ChoiceSpec(
            "Screen goes black after", "Minutes without touches before the screen goes black.",
            options("0" to "Never", "1" to "1 minute", "2" to "2 minutes", "5" to "5 minutes", "10" to "10 minutes", "30" to "30 minutes"),
            { "${s.sleepMinutes}" }, { s.sleepMinutes = it.toInt() },
        )
        "launch_start" -> ChoiceSpec(
            "Launch only after", "Together with the end hour this sets the time window for quick launch.",
            hourOptions(true), { "${s.launchStartHour}" }, { s.launchStartHour = it.toInt() },
        )
        "launch_end" -> ChoiceSpec(
            "Launch only before", "Together with the start hour this sets the time window for quick launch.",
            hourOptions(true), { "${s.launchEndHour}" }, { s.launchEndHour = it.toInt() },
        )
        "noise" -> ChoiceSpec(
            "Background sound", null,
            options(
                Settings.NOISE_OFF to "Off", Settings.NOISE_WHITE to "White noise", Settings.NOISE_PINK to "Pink noise",
                Settings.NOISE_BROWN to "Brown noise", Settings.NOISE_RAIN to "Rain", Settings.NOISE_OCEAN to "Ocean waves",
                Settings.NOISE_FIRE to "Fireplace", Settings.NOISE_WIND to "Wind", Settings.NOISE_STREAM to "Stream",
            ),
            { s.noiseStation },
            {
                val previous = s.noiseStation
                s.noiseStation = it
                if (previous != it) NoisePlayer.start(it, s.noiseMinutes)
            },
        )
        "noise_minutes" -> ChoiceSpec(
            "Sleep timer", "How long the background sound plays before it stops.",
            options("0" to "Until I turn it off", "15" to "15 minutes", "30" to "30 minutes", "60" to "1 hour", "120" to "2 hours"),
            { "${s.noiseMinutes}" },
            {
                val previous = s.noiseMinutes
                s.noiseMinutes = it.toInt()
                if (previous != s.noiseMinutes && NoisePlayer.isPlaying) NoisePlayer.start(s.noiseStation, s.noiseMinutes)
            },
        )
        else -> null
    }

    // ---- List pages ----

    private fun lines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    private fun duplicate(values: List<String>, existing: List<String>): String? =
        if (existing.any { it.equals(values[0], ignoreCase = true) }) "Already in the list" else null

    private fun listSpec(key: String): ListSpec? = when (key) {
        "charging/wifi" -> ListSpec(
            "Wi-Fi networks",
            "The clock opens on a charger only when the phone is on one of these networks. With none listed, any network works. " +
                "Reading the network name needs location access.",
            "Any network", "Add a network",
            listOf(DialogField("Network name (SSID)")),
            { s.wifiNetworks() }, { s.wifiWhitelist = it.joinToString("\n") },
            { it }, { null },
            { values, existing -> if (values[0].isEmpty()) "Enter a name" else duplicate(values, existing) },
            { it[0] },
        )
        "sound/stations" -> ListSpec(
            "Radio stations",
            "The Radio button on the player card plays these in turn. Use direct stream addresses, not web pages.",
            "No stations yet", "Add a station",
            listOf(DialogField("Name"), DialogField("Stream address", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)),
            { lines(s.radioStations) }, { s.radioStations = it.joinToString("\n") },
            { it.substringBefore("|").trim().ifEmpty { it } }, { if (it.contains("|")) it.substringAfter("|").trim() else null },
            { values, existing ->
                when {
                    values[0].isEmpty() -> "Enter a name"
                    !values[1].startsWith("http") -> "Use a full address starting with http"
                    existing.any { it.substringBefore("|").trim().equals(values[0], ignoreCase = true) } -> "A station has this name"
                    else -> null
                }
            },
            { "${it[0].replace("|", " ")}|${it[1]}" },
        )
        "tools/quotes" -> ListSpec(
            "Quotes",
            "The quote corner and the quote panel item rotate through these every five minutes.",
            "No quotes yet", "Add a quote",
            listOf(DialogField("Quote", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)),
            { lines(s.quotes) }, { s.quotes = it.joinToString("\n") },
            { it }, { null },
            { values, existing -> if (values[0].isEmpty()) "Enter a quote" else duplicate(values, existing) },
            { it[0] },
        )
        "tools/protocols" -> ListSpec(
            "Protocols",
            "Your rules or principles. They rotate in a corner when you choose the protocol complication.",
            "No protocols yet", "Add a protocol",
            listOf(DialogField("Rule or principle", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)),
            { lines(s.protocols) }, { s.protocols = it.joinToString("\n") },
            { it }, { null },
            { values, existing -> if (values[0].isEmpty()) "Enter a protocol" else duplicate(values, existing) },
            { it[0] },
        )
        "tools/focus" -> ListSpec(
            "Focus apps",
            "Only these apps' notifications appear in the Tools list. With none listed, every app shows. " +
                "Package names look like com.whatsapp.",
            "Every app", "Add an app",
            listOf(DialogField("Package name, for example com.whatsapp")),
            { lines(s.focusApps) }, { s.focusApps = it.joinToString("\n") },
            { it }, { null },
            { values, existing ->
                when {
                    values[0].isEmpty() -> "Enter a package name"
                    values[0].any { it.isWhitespace() } -> "A package name has no spaces"
                    else -> duplicate(values, existing)
                }
            },
            { it[0] },
        )
        "tools/world" -> ListSpec(
            "World clock cities",
            "Up to five cities, as time zone names such as Europe/Paris or Asia/Tokyo.",
            "No cities yet", "Add a city",
            listOf(DialogField("Time zone, for example Europe/Paris")),
            { s.worldZoneIds() }, { s.worldZones = it.joinToString(",") },
            { it.substringAfterLast('/').replace('_', ' ') }, { it },
            { values, existing ->
                when {
                    values[0].isEmpty() -> "Enter a time zone"
                    values[0] !in ZoneId.getAvailableZoneIds() -> "Not a known time zone. Try Europe/Paris."
                    else -> duplicate(values, existing)
                }
            },
            { it[0] },
            max = 5,
        )
        else -> null
    }

    private fun listPage(spec: ListSpec): Page {
        val items = spec.items()
        val rows = mutableListOf<View>()
        items.forEachIndexed { index, item ->
            rows += ui.itemRow(spec.primary(item), spec.secondary(item), listOf("Remove" to {
                val fresh = spec.items().toMutableList()
                if (index in fresh.indices) {
                    fresh.removeAt(index)
                    spec.store(fresh)
                }
                a.refresh()
            }))
        }
        if (items.isEmpty()) rows += ui.emptyRow(spec.empty)
        if (items.size < spec.max) {
            rows += ui.actionRow(spec.addLabel, plus = true) {
                ui.prompt(
                    spec.addLabel,
                    null,
                    spec.fields,
                    "Add",
                    validate = { values -> spec.validate(values, spec.items()) },
                ) { values ->
                    spec.store(spec.items() + spec.make(values))
                    a.refresh()
                }
            }
        }
        return Page(spec.title, listOf(ui.section(null, spec.footer, rows)))
    }

    companion object {
        const val ROOT = "root"

        private val ORIENTATIONS = listOf(Settings.ORIENTATION_LANDSCAPE, Settings.ORIENTATION_PORTRAIT, Settings.ORIENTATION_AUTO)
        private val BATTERY_STYLES = listOf("pixel", "material", "neon")
        private val TINTS = listOf(
            "Deep red" to 0xFFFF0000.toInt(),
            "Amber" to 0xFFFFB000.toInt(),
            "Dim white" to 0xFFFFFFFF.toInt(),
            "Blue" to 0xFF2196F3.toInt(),
            "Green" to 0xFF4CAF50.toInt(),
        )

        val CATEGORIES = listOf(
            Category("clock", "Clock", "Style, colour, 24-hour, seconds, font, photos"),
            Category("widgets", "Widgets", "Duo, panels, Command Center, weather, battery line"),
            Category("display", "Display and night", "Night, red or amber, brightness, screen-off, burn-in"),
            Category("charging", "Charging and launch", "Open when charging, permissions, test launch"),
            Category("sound", "Sound", "Background sound, sleep timer, radio, alerts"),
            Category("tools", "Tools and about", "Timer, tally, Pomodoro, quotes, spaces, help", "walkthrough overview calendar"),
            Category("preview", "Preview", "Open the clock now"),
        )

        /** Every setting by name, for the search box on the root page. Words add alternative names. */
        val INDEX = listOf(
            SearchEntry("Clock face", "clock", "pick/face", "style digital analog"),
            SearchEntry("Screensaver face", "clock", "pick/ssface", "dream"),
            SearchEntry("Orientation", "clock", words = "landscape portrait auto rotate"),
            SearchEntry("Theme", "clock", "pick/theme", "aurora ambient"),
            SearchEntry("Clock colour", "clock", "pick/palette", "color white red blue"),
            SearchEntry("Gradient on the digits", "clock"),
            SearchEntry("Colour balance", "clock", words = "color warm cool"),
            SearchEntry("Pure black", "clock", words = "oled background"),
            SearchEntry("24-hour time", "clock", words = "12-hour time format"),
            SearchEntry("Show seconds", "clock"),
            SearchEntry("Font", "clock", "pick/font", "typeface serif mono rounded"),
            SearchEntry("Analog sub-dial", "clock"),
            SearchEntry("Retroflip style", "clock", words = "dark light warm"),
            SearchEntry("Retroflip card", "clock", words = "background opaque"),
            SearchEntry("Blinking colon", "clock", words = "segmented"),
            SearchEntry("Pulsing glow", "clock", words = "neon"),
            SearchEntry("Dim passed minutes", "clock", words = "radial"),
            SearchEntry("Complications as bars", "clock", words = "progress year month week"),
            SearchEntry("Top-left corner", "clock", "pick/corner_left", "complication"),
            SearchEntry("Top-right corner", "clock", "pick/corner_right", "complication"),
            SearchEntry("Overlay time position", "clock", "pick/overlay_pos"),
            SearchEntry("Overlay time font", "clock", "pick/overlay_font"),
            SearchEntry("Overlay time outline", "clock"),
            SearchEntry("Charging screen style", "clock", words = "battery pixel material neon"),
            SearchEntry("Charging screen: hide the clock", "clock"),
            SearchEntry("Charging milestone", "clock", words = "80 100"),
            SearchEntry("Photos", "clock", "clock/photos", "picture slideshow"),
            SearchEntry("Slideshow pictures", "clock", "clock/photos", "photo choose"),
            SearchEntry("Photo for the photo face", "clock", "clock/photos"),
            SearchEntry("Photo interval", "clock", "clock/photos", "show each picture for"),
            SearchEntry("Photo calendar", "clock", "clock/photos"),
            SearchEntry("Photo caption", "clock", "clock/photos", "meme gif"),

            SearchEntry("Edit panels", "widgets", words = "duo panels editor"),
            SearchEntry("Command Center", "widgets", words = "fade idle tiles"),
            SearchEntry("Weather layout", "widgets", words = "centred jumbo hourly"),
            SearchEntry("Temperature unit", "widgets", words = "celsius fahrenheit"),
            SearchEntry("Weather city", "widgets", words = "search find"),
            SearchEntry("Weather location", "widgets", words = "use my location gps"),
            SearchEntry("Battery line", "widgets"),
            SearchEntry("Quick controls", "widgets", words = "bottom bar toggle"),

            SearchEntry("Night mode", "display", words = "schedule sunset sun always on off"),
            SearchEntry("Night starts", "display", "pick/night_start", "time hour"),
            SearchEntry("Night ends", "display", "pick/night_end", "time hour"),
            SearchEntry("Light sensor", "display", words = "night dark room"),
            SearchEntry("Night tint", "display", words = "red amber"),
            SearchEntry("Tint colour", "display", "pick/tint_color", "color red amber"),
            SearchEntry("Tint strength", "display"),
            SearchEntry("Day brightness", "display"),
            SearchEntry("Night brightness", "display"),
            SearchEntry("Screen goes black after", "display", "pick/sleep", "screen-off delay sleep timer"),
            SearchEntry("Double-tap to close", "display"),
            SearchEntry("Falling snow", "display", words = "seasonal effect"),
            SearchEntry("Checkerboard pixel shift", "display", words = "burn-in"),
            SearchEntry("Checkerboard dimming", "display", words = "burn-in"),

            SearchEntry("Quick launch", "charging", words = "open when charging landscape"),
            SearchEntry("Close when turned upright", "charging"),
            SearchEntry("Wireless charging only", "charging"),
            SearchEntry("Only when the screen is off", "charging"),
            SearchEntry("Only on the lock screen", "charging"),
            SearchEntry("Launch time window", "charging", "pick/launch_start", "after before hour"),
            SearchEntry("Wi-Fi networks", "charging", "charging/wifi", "ssid"),
            SearchEntry("Notifications", "charging", words = "permission allow fix"),
            SearchEntry("Full-screen alerts", "charging", words = "permission allow fix"),
            SearchEntry("Notification access", "charging", words = "permission music listener fix"),
            SearchEntry("Location permission", "charging", words = "permission allow fix"),
            SearchEntry("Test the charging launch", "charging"),
            SearchEntry("Samsung battery tip", "charging", words = "unrestricted background usage"),

            SearchEntry("Background sound", "sound", "pick/noise", "white pink brown noise rain ocean fire wind stream"),
            SearchEntry("Sleep timer", "sound", "pick/noise_minutes", "sound stops after"),
            SearchEntry("Colour glow", "sound", words = "sound canvas visual"),
            SearchEntry("Radio stream", "sound", words = "url"),
            SearchEntry("Radio stations", "sound", "sound/stations", "radio"),
            SearchEntry("Alert mode", "sound", words = "timer pomodoro ends vibrate"),
            SearchEntry("Haptic feedback", "sound", words = "haptics vibration"),

            SearchEntry("Tools", "tools", words = "agenda tally sober countdown to-do games"),
            SearchEntry("Timer and stopwatch", "tools"),
            SearchEntry("Tally counters", "tools", words = "tools"),
            SearchEntry("Pomodoro", "tools", words = "work break cycle"),
            SearchEntry("Quotes", "tools", "tools/quotes"),
            SearchEntry("Protocols", "tools", "tools/protocols"),
            SearchEntry("Focus apps", "tools", "tools/focus", "notifications"),
            SearchEntry("World clock cities", "tools", "tools/world", "time zones"),
            SearchEntry("Spaces", "tools", "tools/spaces", "profiles add switch"),
            SearchEntry("All screens", "tools", words = "overview pinch"),
            SearchEntry("Calendar access", "tools", words = "permission events fix"),
            SearchEntry("Show the walkthrough", "tools", words = "help tour"),
            SearchEntry("Help", "tools", words = "about version"),

            SearchEntry("Open the clock now", "preview"),
        )
    }
}
