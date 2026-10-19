package com.example.standbyclock

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

/** Every setting in the app: clock face, corners, orientation, quick launch, night and burn-in, weather, music, radio, tools. */
class SettingsActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var calendarStatus: TextView
    private lateinit var chargingStatus: TextView
    private lateinit var weatherStatus: TextView
    private lateinit var musicStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildUi(): View {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        content.addView(section("Clock face"))
        val faceValues = Settings.FACE_OPTIONS.map { it.first }
        val faceLabels = Settings.FACE_OPTIONS.map { it.second }.toTypedArray()
        content.addView(spinner(faceLabels, faceValues.indexOf(settings.face)) {
            settings.face = faceValues[it]
        })
        content.addView(label("Screensaver face"))
        content.addView(spinner(faceLabels, faceValues.indexOf(settings.screensaverFace)) {
            settings.screensaverFace = faceValues[it]
        })
        content.addView(button("Choose photo for the photo face") { pickPhoto() })
        content.addView(button("Edit panels (for the Duo and panels face)") {
            startActivity(Intent(this, PanelEditorActivity::class.java))
        })

        content.addView(section("Corner complications"))
        val complicationValues = Complications.OPTIONS.map { it.first }
        val complicationLabels = Complications.OPTIONS.map { it.second }.toTypedArray()
        content.addView(label("Top-left"))
        content.addView(spinner(complicationLabels, complicationValues.indexOf(settings.cornerLeft)) {
            settings.cornerLeft = complicationValues[it]
        })
        content.addView(label("Top-right"))
        content.addView(spinner(complicationLabels, complicationValues.indexOf(settings.cornerRight)) {
            settings.cornerRight = complicationValues[it]
        })

        content.addView(section("Orientation"))
        val orientationValues = arrayOf(
            Settings.ORIENTATION_LANDSCAPE,
            Settings.ORIENTATION_PORTRAIT,
            Settings.ORIENTATION_AUTO,
        )
        content.addView(
            spinner(
                arrayOf("Landscape (stand)", "Portrait", "Auto-rotate"),
                orientationValues.indexOf(settings.orientation),
            ) {
                settings.orientation = orientationValues[it]
                Toast.makeText(this, "Applies the next time the clock opens", Toast.LENGTH_SHORT).show()
            },
        )

        content.addView(section("Weather"))
        weatherStatus = label("")
        content.addView(weatherStatus)
        content.addView(button("Use my location for weather") { useLocation() })

        content.addView(section("Music controls"))
        musicStatus = label("")
        content.addView(musicStatus)
        content.addView(button("Allow music controls (notification access)") {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        content.addView(label("Works with Spotify, YouTube Music, Apple Music, Deezer and other apps that show media controls."))

        content.addView(section("Radio"))
        val radioField = EditText(this).apply {
            hint = "Stream URL, for example https://.../stream.mp3"
            setSingleLine()
            setText(settings.radioUrl.orEmpty())
        }
        content.addView(radioField)
        content.addView(button("Save radio stream") {
            settings.radioUrl = radioField.text.toString()
            Toast.makeText(this, "Radio stream saved", Toast.LENGTH_SHORT).show()
        })
        content.addView(label("Plays while the clock is open, from the Radio button on the player card. Use a direct stream address, not a web page."))

        content.addView(section("Night mode"))
        val hours = Array(24) { "%02d:00".format(it) }
        content.addView(label("Night starts"))
        content.addView(spinner(hours, settings.nightStartHour) { settings.nightStartHour = it })
        content.addView(label("Night ends"))
        content.addView(spinner(hours, settings.nightEndHour) { settings.nightEndHour = it })
        content.addView(toggle("Also night when the room is dark (light sensor)", settings.lightSensorNight) {
            settings.lightSensorNight = it
        })
        content.addView(toggle("Tint the screen at night", settings.redTintAtNight) {
            settings.redTintAtNight = it
        })
        val tintValues = arrayOf(0xFFFF0000.toInt(), 0xFFFFB000.toInt(), 0xFFFFFFFF.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt())
        content.addView(label("Tint colour"))
        content.addView(spinner(
            arrayOf("Deep red", "Amber", "Dim white", "Blue", "Green"),
            tintValues.indexOf(settings.tintColor).coerceAtLeast(0),
        ) {
            settings.tintColor = tintValues[it]
        })
        content.addView(slider("Tint strength", 0, 100, settings.tintStrength) { settings.tintStrength = it })
        content.addView(slider("Day brightness", 10, 100, settings.dayBrightness) { settings.dayBrightness = it })
        content.addView(slider("Night brightness", 1, 50, settings.nightBrightness) { settings.nightBrightness = it })

        content.addView(section("Burn-in protection"))
        content.addView(toggle("Checkerboard pixel shift (flips every minute)", settings.checkerboard) {
            settings.checkerboard = it
        })
        content.addView(slider("Checkerboard dimming", 0, 100, settings.checkerDim) { settings.checkerDim = it })

        content.addView(section("Quick launch"))
        val launchValues = arrayOf(Settings.LAUNCH_OFF, Settings.LAUNCH_CHARGING, Settings.LAUNCH_LANDSCAPE)
        content.addView(spinner(
            arrayOf("Off", "When charging", "When charging in landscape"),
            launchValues.indexOf(settings.launchMode),
        ) {
            settings.launchMode = launchValues[it]
            if (settings.launchMode != Settings.LAUNCH_OFF) requestNotificationPermission()
            refreshStatus()
        })
        content.addView(toggle("Close when the phone is turned upright (launched from charging)", settings.closeWhenUpright) {
            settings.closeWhenUpright = it
        })
        content.addView(toggle("Only when plugged in wirelessly", settings.launchWirelessOnly) {
            settings.launchWirelessOnly = it
        })
        content.addView(toggle("Only when the screen is off", settings.launchScreenOff) {
            settings.launchScreenOff = it
        })
        content.addView(toggle("Only when the lock screen is showing", settings.launchLockedOnly) {
            settings.launchLockedOnly = it
        })
        val anyHour = arrayOf("Any time") + Array(24) { "%02d:00".format(it) }
        content.addView(label("Launch only after"))
        content.addView(spinner(anyHour, settings.launchStartHour + 1) { settings.launchStartHour = it - 1 })
        content.addView(label("and before"))
        content.addView(spinner(anyHour, settings.launchEndHour + 1) { settings.launchEndHour = it - 1 })
        content.addView(label("Wi-Fi networks (one per line). Leave empty for any network. Needs location access to read the network name."))
        val wifiField = EditText(this).apply {
            minLines = 2
            setText(settings.wifiWhitelist)
        }
        content.addView(wifiField)
        content.addView(button("Save Wi-Fi list") {
            settings.wifiWhitelist = wifiField.text.toString()
            Toast.makeText(this, "Wi-Fi list saved", Toast.LENGTH_SHORT).show()
        })
        chargingStatus = label("")
        content.addView(chargingStatus)
        content.addView(button("Allow notifications") { requestNotificationPermission() })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            content.addView(button("Allow full-screen alerts (needed to open from the background)") {
                startActivity(Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName"),
                ))
            })
        }
        content.addView(button("Test the charging launch now") {
            val posted = ChargingLauncher.show(this)
            Toast.makeText(
                this,
                if (posted) "Test posted. The clock should open." else "Notifications are blocked.",
                Toast.LENGTH_LONG,
            ).show()
        })
        content.addView(label("Samsung: in Settings > Apps > Standby Clock > Battery, choose Unrestricted. Also make sure Standby Clock is not listed under Settings > Battery > Background usage limits (Sleeping or Deep sleeping apps)."))

        content.addView(section("Display and sound"))
        val nightValues = arrayOf(Settings.NIGHT_SCHEDULE, Settings.NIGHT_ON, Settings.NIGHT_OFF)
        content.addView(label("Night mode"))
        content.addView(spinner(
            arrayOf("By schedule and light sensor", "Always on", "Always off"),
            nightValues.indexOf(settings.nightMode),
        ) {
            settings.nightMode = nightValues[it]
        })
        val sleepValues = intArrayOf(0, 1, 2, 5, 10, 30)
        content.addView(label("Screen goes black after"))
        content.addView(spinner(
            arrayOf("Never", "1 minute", "2 minutes", "5 minutes", "10 minutes", "30 minutes"),
            sleepValues.indexOf(settings.sleepMinutes).coerceAtLeast(0),
        ) {
            settings.sleepMinutes = sleepValues[it]
        })
        content.addView(toggle("Double-tap the clock to close the app", settings.doubleTapClose) {
            settings.doubleTapClose = it
        })
        content.addView(toggle("Falling snow (seasonal effect)", settings.snow) {
            settings.snow = it
        })
        val noiseValues = arrayOf(Settings.NOISE_OFF, Settings.NOISE_WHITE, Settings.NOISE_PINK, Settings.NOISE_BROWN)
        content.addView(label("Background sound"))
        content.addView(spinner(
            arrayOf("Off", "White noise", "Pink noise", "Brown noise"),
            noiseValues.indexOf(settings.noiseStation),
        ) {
            val previous = settings.noiseStation
            settings.noiseStation = noiseValues[it]
            // Only react to a real change, not the spinner's initial selection.
            if (previous != settings.noiseStation) NoisePlayer.start(settings.noiseStation, settings.noiseMinutes)
        })
        val noiseMinuteValues = intArrayOf(0, 15, 30, 60, 120)
        content.addView(label("Sound stops after"))
        content.addView(spinner(
            arrayOf("Until I turn it off", "15 minutes", "30 minutes", "1 hour", "2 hours"),
            noiseMinuteValues.indexOf(settings.noiseMinutes).coerceAtLeast(0),
        ) {
            val previous = settings.noiseMinutes
            settings.noiseMinutes = noiseMinuteValues[it]
            if (previous != settings.noiseMinutes && NoisePlayer.isPlaying) {
                NoisePlayer.start(settings.noiseStation, settings.noiseMinutes)
            }
        })

        content.addView(section("Touch and tools"))
        content.addView(toggle("Haptic feedback on taps and counters", settings.haptics) {
            settings.haptics = it
        })
        content.addView(button("Open tools (tally counters, sober counter)") {
            startActivity(Intent(this, ToolsActivity::class.java))
        })
        content.addView(button("Open timer and stopwatch") {
            startActivity(Intent(this, TimerActivity::class.java))
        })
        content.addView(label("Home-screen widget: long-press an empty spot on the home screen, choose Widgets, then pick Standby Clock."))

        content.addView(section("Calendar"))
        calendarStatus = label("")
        content.addView(calendarStatus)
        content.addView(button("Allow calendar events on the clock") {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CALENDAR)
        })

        content.addView(section("More"))
        content.addView(button("More settings (spaces, fonts, weather search, sound, radio, photos, world clock)") {
            startActivity(Intent(this, MoreSettingsActivity::class.java))
        })
        content.addView(button("All screens (pinch overview)") {
            startActivity(Intent(this, OverviewActivity::class.java))
        })

        content.addView(section("Preview"))
        content.addView(button("Open the clock now") {
            startActivity(Intent(this, MainActivity::class.java))
        })

        return ScrollView(this).apply { addView(content) }
    }

    private fun refreshStatus() {
        val notifications = if (ChargingLauncher.notificationsAllowed(this)) "allowed" else "NOT allowed"
        val fullScreen = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> "not required on this Android version"
            ChargingLauncher.fullScreenAllowed(this) -> "allowed"
            else -> "NOT allowed (the clock will only show as a notification)"
        }
        chargingStatus.text = "Notifications: $notifications\nFull-screen alerts: $fullScreen"

        weatherStatus.text = if (settings.location() != null) {
            "Weather: location saved"
        } else {
            "Weather: no location yet (weather stays hidden)"
        }

        val listening = getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(ComponentName(this, MusicListenerService::class.java)) == true
        musicStatus.text = if (listening) "Music controls: allowed" else "Music controls: not allowed"

        val calendar = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        calendarStatus.text = if (calendar) "Calendar access: allowed" else "Calendar access: not allowed"
    }

    private fun useLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
            return
        }
        fetchLocation()
    }

    /** Uses the last known network fix, or asks for a new one when there is none. */
    @Suppress("MissingPermission")
    private fun fetchLocation() {
        val manager = getSystemService(LocationManager::class.java) ?: return
        val last = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (last != null) {
            saveLocation(last)
            return
        }
        weatherStatus.text = "Finding your location..."
        manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, mainExecutor) { location ->
            if (location != null) {
                saveLocation(location)
            } else {
                weatherStatus.text = "No location found. Turn on location and try again."
            }
        }
    }

    private fun saveLocation(location: Location) {
        settings.saveLocation(location.latitude, location.longitude)
        Toast.makeText(this, "Weather location saved", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PHOTO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PHOTO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            // Keep read access across reboots so the photo face still works.
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            settings.photoUri = uri.toString()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
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

    private fun spinner(labels: Array<String>, selected: Int, onSelect: (Int) -> Unit): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, labels)
            setSelection(selected.coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onSelect(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

    companion object {
        private const val REQUEST_PHOTO = 1
        private const val REQUEST_CALENDAR = 2
        private const val REQUEST_NOTIFICATIONS = 3
        private const val REQUEST_LOCATION = 4
    }
}
