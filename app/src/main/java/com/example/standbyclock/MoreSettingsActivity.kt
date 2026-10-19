package com.example.standbyclock

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** The newer settings: spaces, appearance, weather search, sound, radio, photos, world clock, focus, protocols. */
class MoreSettingsActivity : Activity() {

    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        content.addView(section("Spaces"))
        content.addView(label("Each space keeps its own screens and settings."))
        val current = Spaces.current(this)
        Spaces.names(this).forEach { name ->
            content.addView(button(if (name == current) "$name (current)" else "Switch to $name") {
                if (name != current) {
                    Spaces.switchTo(this, name)
                    Toast.makeText(this, "Switched to $name", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            })
        }
        val spaceName = EditText(this).apply { hint = "New space name, for example Work" }
        content.addView(spaceName)
        content.addView(button("Add space") {
            if (Spaces.add(this, spaceName.text.toString())) {
                Toast.makeText(this, "Space added", Toast.LENGTH_SHORT).show()
                recreate()
            } else {
                Toast.makeText(this, "Enter a new name", Toast.LENGTH_SHORT).show()
            }
        })

        content.addView(section("Appearance"))
        val fontValues = arrayOf(
            Settings.FONT_SYSTEM, Settings.FONT_SERIF, Settings.FONT_MONO, Settings.FONT_ROUNDED, Settings.FONT_CONDENSED,
        )
        content.addView(label("Clock font"))
        content.addView(spinner(
            arrayOf("System", "Serif", "Monospace", "Rounded", "Condensed"),
            fontValues.indexOf(settings.fontStyle),
        ) { settings.fontStyle = fontValues[it] })
        content.addView(toggle("Pure black (hides photos, rain, snow and sound canvas)", settings.pureBlack) {
            settings.pureBlack = it
        })
        content.addView(toggle("Small digital time under the analog face", settings.analogSubDial) {
            settings.analogSubDial = it
        })
        content.addView(label("Retroflip style"))
        content.addView(spinner(arrayOf("Dark", "Light", "Warm"), settings.retroStyle) { settings.retroStyle = it })
        content.addView(toggle("Retroflip card background", settings.retroOpaque) { settings.retroOpaque = it })
        val batteryValues = arrayOf("pixel", "material", "neon")
        content.addView(label("Charging screen style"))
        content.addView(spinner(
            arrayOf("Pixel (white)", "Material (teal)", "Neon (magenta)"),
            batteryValues.indexOf(settings.batteryStyle).coerceAtLeast(0),
        ) { settings.batteryStyle = batteryValues[it] })
        content.addView(toggle("Charging screen: hide the clock", settings.batteryHideClock) {
            settings.batteryHideClock = it
        })
        content.addView(label("Charging milestone"))
        content.addView(spinner(arrayOf("80%", "100%"), if (settings.chargeMilestone == 100) 1 else 0) {
            settings.chargeMilestone = if (it == 1) 100 else 80
        })
        val weatherLayouts = arrayOf("centered", "jumbo", "hourly")
        content.addView(label("Weather clock layout"))
        content.addView(spinner(
            arrayOf("Centred", "Jumbo temperature", "Hourly strip (next six hours)"),
            weatherLayouts.indexOf(settings.weatherLayout).coerceAtLeast(0),
        ) { settings.weatherLayout = weatherLayouts[it] })

        content.addView(section("Clock look"))
        val themeIds = listOf("off") + Themes.ALL.map { it.id }
        content.addView(label("Theme (a colour set with an ambient background, from Standby Pro)"))
        content.addView(spinner(
            arrayOf("Off (use the clock colour below)") + Themes.ALL.map { it.name }.toTypedArray(),
            themeIds.indexOf(settings.themeId).coerceAtLeast(0),
        ) { settings.themeId = themeIds[it] })
        content.addView(toggle("Quick controls along the bottom (style, face, group, settings)", settings.quickControls) {
            settings.quickControls = it
        })
        content.addView(label("Clock colour (used when no theme is set)"))
        content.addView(spinner(
            Settings.PALETTES.map { it.first }.toTypedArray(),
            settings.clockPalette,
        ) { settings.clockPalette = it })
        content.addView(toggle("Gradient on the digits", settings.clockGradient) { settings.clockGradient = it })
        content.addView(toggle("Show seconds on the digital, big, neon and matrix faces", settings.showSeconds) {
            settings.showSeconds = it
        })
        content.addView(toggle("Blinking colon on the segmented clock", settings.segmentBlink) {
            settings.segmentBlink = it
        })
        content.addView(toggle("Pulsing glow on the neon face", settings.neonPulse) { settings.neonPulse = it })
        content.addView(toggle("Radial face: dim the minutes that have passed", settings.radialInvert) {
            settings.radialInvert = it
        })
        content.addView(toggle("Command Center tiles fade when nothing is playing", settings.commandIdleFade) {
            settings.commandIdleFade = it
        })
        content.addView(label("Colour balance, from cool (negative) to warm (positive). 0 turns it off."))
        content.addView(slider("Colour balance", -100, 100, settings.colourBalance) { settings.colourBalance = it })
        content.addView(toggle("Show year, month and week complications as bars", settings.progressBars) {
            settings.progressBars = it
        })

        content.addView(section("Overlay time"))
        content.addView(label("A large time that sits in a corner or the middle of the edge, over any face."))
        content.addView(spinner(
            arrayOf("Off", "Top left", "Top centre", "Top right", "Bottom left", "Bottom centre", "Bottom right"),
            settings.overlayPosition + 1,
        ) { settings.overlayPosition = it - 1 })
        val overlayFonts = arrayOf(
            Settings.FONT_SYSTEM, Settings.FONT_SERIF, Settings.FONT_MONO, Settings.FONT_ROUNDED, Settings.FONT_CONDENSED,
        )
        content.addView(label("Overlay font"))
        content.addView(spinner(
            arrayOf("System", "Serif", "Monospace", "Rounded", "Condensed"),
            overlayFonts.indexOf(settings.overlayFont),
        ) { settings.overlayFont = overlayFonts[it] })
        content.addView(toggle("Dark outline behind the overlay time", settings.overlayOutline) {
            settings.overlayOutline = it
        })

        content.addView(section("Quotes and captions"))
        content.addView(label("Quotes, one per line. The quote corner and the quote panel item rotate through them every five minutes."))
        val quoteField = multiline(settings.quotes, 3)
        content.addView(quoteField)
        content.addView(button("Save quotes") {
            settings.quotes = quoteField.text.toString()
            Toast.makeText(this, "Quotes saved", Toast.LENGTH_SHORT).show()
        })
        content.addView(label("Caption shown under the photo face. Works with GIF pictures too."))
        val captionField = EditText(this).apply {
            setSingleLine()
            setText(settings.photoCaption)
        }
        content.addView(captionField)
        content.addView(button("Save caption") {
            settings.photoCaption = captionField.text.toString()
            Toast.makeText(this, "Caption saved", Toast.LENGTH_SHORT).show()
        })

        content.addView(section("Pomodoro"))
        content.addView(toggle("Cycle work (25 minutes) and break (5 minutes) automatically", settings.pomodoroCycle) {
            settings.pomodoroCycle = it
        })

        content.addView(section("Weather"))
        val units = arrayOf("auto", "c", "f")
        content.addView(label("Temperature unit"))
        content.addView(spinner(
            arrayOf("By region", "Celsius", "Fahrenheit"),
            units.indexOf(settings.weatherUnit).coerceAtLeast(0),
        ) { settings.weatherUnit = units[it] })
        val city = EditText(this).apply {
            hint = "City, for example Lisbon"
            setSingleLine()
            setText(settings.weatherCity)
        }
        content.addView(city)
        val cityStatus = label("")
        content.addView(cityStatus)
        content.addView(button("Find city and use its weather") {
            val name = city.text.toString().trim()
            if (name.isEmpty()) return@button
            cityStatus.text = "Searching..."
            Thread {
                val place = WeatherClient.geocode(name)
                handler.post {
                    if (place == null) {
                        cityStatus.text = "No city found for \"$name\"."
                    } else {
                        settings.saveLocation(place.latitude, place.longitude)
                        settings.weatherCity = place.name
                        cityStatus.text = "Weather set for ${place.name}."
                    }
                }
            }.start()
        })

        content.addView(section("Night and sleep"))
        val nightValues = arrayOf(Settings.NIGHT_SCHEDULE, Settings.NIGHT_SUN, Settings.NIGHT_ON, Settings.NIGHT_OFF)
        content.addView(label("Night mode"))
        content.addView(spinner(
            arrayOf("By schedule and light sensor", "Sunset to sunrise (needs weather location)", "Always on", "Always off"),
            nightValues.indexOf(settings.nightMode).coerceAtLeast(0),
        ) { settings.nightMode = nightValues[it] })

        content.addView(section("Sound"))
        val noiseValues = arrayOf(
            Settings.NOISE_OFF, Settings.NOISE_WHITE, Settings.NOISE_PINK, Settings.NOISE_BROWN,
            Settings.NOISE_RAIN, Settings.NOISE_OCEAN, Settings.NOISE_FIRE, Settings.NOISE_WIND, Settings.NOISE_STREAM,
        )
        content.addView(label("Background sound"))
        content.addView(spinner(
            arrayOf("Off", "White noise", "Pink noise", "Brown noise", "Rain", "Ocean waves", "Fireplace", "Wind", "Stream"),
            noiseValues.indexOf(settings.noiseStation).coerceAtLeast(0),
        ) {
            val previous = settings.noiseStation
            settings.noiseStation = noiseValues[it]
            if (previous != settings.noiseStation) NoisePlayer.start(settings.noiseStation, settings.noiseMinutes)
        })
        content.addView(toggle("Drifting colour glow behind the sound", settings.visualCanvas) {
            settings.visualCanvas = it
        })
        val alertValues = arrayOf("sound", "vibrate", "both")
        content.addView(label("When a timer or Pomodoro ends"))
        content.addView(spinner(
            arrayOf("Sound", "Vibrate", "Sound and vibrate"),
            alertValues.indexOf(settings.alertMode).coerceAtLeast(0),
        ) { settings.alertMode = alertValues[it] })

        content.addView(section("Radio stations"))
        content.addView(label("One station per line, as Name|Stream address. The Radio button on the player card plays them in turn."))
        val stations = multiline(settings.radioStations, 4)
        content.addView(stations)
        content.addView(button("Save radio stations") {
            settings.radioStations = stations.text.toString()
            Toast.makeText(this, "Radio stations saved", Toast.LENGTH_SHORT).show()
        })

        content.addView(section("Photos"))
        val pictureCount = settings.photoUris().size
        content.addView(label("$pictureCount picture(s) in the slideshow."))
        content.addView(button("Choose pictures (several at once)") { pickPictures() })
        val intervalValues = intArrayOf(15, 30, 60, 300, 600)
        content.addView(label("Show each picture for"))
        content.addView(spinner(
            arrayOf("15 seconds", "30 seconds", "1 minute", "5 minutes", "10 minutes"),
            intervalValues.indexOf(settings.photoInterval).coerceAtLeast(0),
        ) { settings.photoInterval = intervalValues[it] })
        content.addView(toggle("Show this month's calendar over the photo", settings.photoCalendar) {
            settings.photoCalendar = it
        })

        content.addView(section("World clock"))
        content.addView(label("Up to five cities as time zone names, separated by commas. For example Europe/Paris, Asia/Tokyo."))
        val zones = EditText(this).apply { setText(settings.worldZones) }
        content.addView(zones)
        content.addView(button("Save cities") {
            settings.worldZones = zones.text.toString()
            Toast.makeText(this, "Cities saved", Toast.LENGTH_SHORT).show()
        })

        content.addView(section("Focus and notifications"))
        content.addView(label("Only these apps' notifications appear in the Tools list. One package name per line; leave empty for every app. Package names look like com.whatsapp."))
        val focus = multiline(settings.focusApps, 3)
        content.addView(focus)
        content.addView(button("Save focus apps") {
            settings.focusApps = focus.text.toString()
            Toast.makeText(this, "Focus apps saved", Toast.LENGTH_SHORT).show()
        })
        val listening = getSystemService(android.app.NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(ComponentName(this, MusicListenerService::class.java)) == true
        content.addView(label(if (listening) "Notification access: allowed" else "Notification access: not allowed"))
        content.addView(button("Allow notification access") {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        content.addView(section("Protocols"))
        content.addView(label("Your rules or principles, one per line. They rotate in a corner when you choose the protocol complication."))
        val protocols = multiline(settings.protocols, 4)
        content.addView(protocols)
        content.addView(button("Save protocols") {
            settings.protocols = protocols.text.toString()
            Toast.makeText(this, "Protocols saved", Toast.LENGTH_SHORT).show()
        })

        content.addView(section("Help"))
        content.addView(button("Show the walkthrough") { MainActivity.showTour(this, 0) })

        return ScrollView(this).apply { addView(content) }
    }

    private fun pickPictures() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_PICTURES)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICTURES || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
        }
        data.data?.let { uris += it }
        uris.forEach { uri ->
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (uris.isNotEmpty()) {
            settings.photoList = uris.joinToString("\n") { it.toString() }
            settings.photoUri = uris.first().toString()
            Toast.makeText(this, "${uris.size} picture(s) chosen", Toast.LENGTH_SHORT).show()
            recreate()
        }
    }

    /** A labelled slider from [low] to [high]. Its label shows the current value. */
    private fun slider(name: String, low: Int, high: Int, current: Int, onChange: (Int) -> Unit): LinearLayout {
        val readout = label("$name: $current")
        val bar = SeekBar(this).apply {
            max = high - low
            progress = current - low
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = progress + low
                    readout.text = "$name: $value"
                    if (fromUser) onChange(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(readout)
            addView(bar)
        }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
    }

    private fun section(text: String): TextView = label(text).apply {
        textSize = 20f
        setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch =
        Switch(this).apply {
            this.text = text
            isChecked = checked
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }

    private fun multiline(initial: String, lines: Int): EditText = EditText(this).apply {
        minLines = lines
        setText(initial)
    }

    private fun spinner(labels: Array<String>, selected: Int, onSelect: (Int) -> Unit): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(this@MoreSettingsActivity, android.R.layout.simple_spinner_item, labels)
            setSelection(selected.coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onSelect(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

    companion object {
        private const val REQUEST_PICTURES = 10
    }
}
