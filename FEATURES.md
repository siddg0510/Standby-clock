# Standby Clock: feature checklist

Standby Clock is a free app. Every feature is included at no cost, with no accounts, purchases, ads or locked features.

Status key:
- [x] built
- [~] partly built (what is missing is noted)
- [ ] not built yet
- [-] not planned: needs a service, account, or third-party content this app doesn't use

## 1. Core UX, navigation and profiles

- [x] Swipe left and right to move between groups of screens (Clocks, Dashboards, Duo and panels, Photos, Weather)
- [x] Swipe up and down, or tap, to move between the screens in the group
- [x] Pinch to open the overview of every screen
- [~] Long-press opens settings. The Panels editor is the edit mode for panels; there is no live preview editor for the other faces.
- [ ] Landscape preview panel shrinks for the edit drawer
- [~] Single-swipe exit. Android's system gesture closes the app; the app doesn't intercept it.
- [x] Double-tap the clock to close the app (can be turned off)
- [~] Haptic feedback on taps, swipes, long-press, laps and counters. Widget picker snaps are not haptic.
- [x] Spaces: named profiles, each with its own screens and settings (Home is the default)
- [x] First-run walkthrough: five pages, also available from More settings

## 2. Settings

### A. App launch and automation

- [x] Quick launch: off, when charging, or when charging in landscape
- [-] Hiding the persistent notification. Android requires a notification for a full-screen launch.
- [x] Any charger, or wireless charging only
- [x] Landscape only (launches when the phone is sideways)
- [x] Auto-close when the phone is turned upright (only unplugging closed the clock before)
- [x] Portrait and landscape allowed (charging mode)
- [x] Only when the screen is off; only on the lock screen
- [x] Wi-Fi network list (reading the name needs location access)
- [x] Time window; auto-close on unplug
- [~] Screensaver (DreamService) registered in Settings > Display > Screen saver
- [x] Screensaver face picker
- [-] Command Center inside the screensaver

### B. Night mode

- [x] Night mode: by schedule and light sensor, sunset to sunrise, always on, or always off
- [x] Automatic night from the ambient light sensor
- [x] Scheduled night (start and end hour)
- [x] Sunset to sunrise (uses your saved location; falls back to the schedule without one)
- [~] Tint colour: five presets, not any colour
- [x] Tint strength; separate day and night brightness
- [x] Overall colour balance on top of the tint: warm to cool slider

### C. OLED burn-in and sleep

- [x] Screen goes black after a set time, and the next touch wakes it
- [x] Checkerboard pixel shift that flips every minute, with a dimming slider
- [x] Periodic spatial pixel shift of the whole block

### D. Corner complications

- [x] Top-left and top-right slots
- [x] Battery (full and minimal)
- [x] Date; greeting; date and greeting combined
- [x] Next alarm
- [x] Time with seconds
- [x] Protocol, rotating through the list you write
- [~] Year, month and week progress as text or a 10-block bar. Ring style is not built.
- [x] Quotes, rotating through the list you write

### E. Focus, extras and seasonal effects

- [x] Focus mode: only the apps you choose show notifications in the Tools list
- [x] Falling snow
- [x] Walkthrough: five pages, shown on first launch and from More settings
- [-] Community voting board and support links (these belong to the original app)

## 3. Multi-panel layouts

- [x] Duo: two panels (clock and cards), fixed layout
- [x] Panels face: two, three or four panels, side by side in landscape and stacked in portrait
- [~] Swipe between panels. Panels are shown together; each panel's stack moves on by itself.
- [x] Adjustable split ratios (for example 2,1,1)
- [x] Yoga layout: the first panel grows while nothing is playing
- [~] Vertical widget stacks that loop. Stacks loop with the rotator; reordering uses up and down buttons, not drag-and-drop.
- [x] Searchable widget picker: built-in items and installed widgets, filtered as you type
- [x] Per-panel corner roundness, opacity, border colour and thickness, text zoom and spacing
- [x] Widget rotator: each stack moves to its next item after a set number of seconds
- [x] Third-party home-screen widgets, hosted in the panels. A widget may need its own setup and sizes to the panel it is in.

## 4. Clocks

- [~] Customisable clocks: 22 faces, with a font setting. Colour palette and seconds are set for all faces, not per face.
- [~] Colour palettes and gradients, and show or hide seconds. Six palettes and a gradient, but the settings apply to every face rather than each face on its own.
- [x] 12/24-hour format (follows the phone setting)
- [x] Retroflip: three style presets, card background on or off
- [x] AMOLED clock: white digits on pure black
- [x] Pure-black mode: hides photos, rain, snow and the sound canvas
- [~] Clock with date and a serif style (choose the serif font in settings)
- [x] Segmented LCD: blinking colon
- [~] Big crop: large text, not the Pixel-style crop
- [x] Solar: sun arc with the time, and a sky that changes from night to dawn, day and dusk
- [~] Neon: green glow that pulses each second, no animated tick ring
- [x] Matrix rain behind the time
- [x] Radial: minute ring with the time inside; the setting dims the passed minutes instead
- [x] Analog clock, with an optional digital sub-dial
- [x] Word clock; Roman numerals; pixel art
- [x] Dementia clock: time of day, full date and large text
- [x] World clock: up to five cities with time differences
- [x] Striking calendar: today's date behind a month grid
- [~] International Space Station clock: live position, altitude, speed and daylight from a free public service, and a world grid with the station marked. No satellite photographs.

## 5. Dashboards

- [x] Command Center: five live tiles (greeting and time, weather, next event with countdown, music, battery). Tap a tile to make it the large one; the choice is remembered.
- [x] Command Center fades to a dim state when nothing is playing and no event is near
- [x] Calendar agenda with a countdown (Tools and the Command Center)
- [~] System dashboard: model, battery, memory and storage, as text
- [~] Starship console: text-based telemetry, not the sci-fi art
- [x] Charging screen: large percentage, progress bar, time to full, time to the milestone, red "On battery", three styles, and a hide-clock option
- [~] Battery widget on the home screen: percentage, bar and charging state, in the colour of the charging style. One design only.

## 6. Weather

- [x] Current temperature and conditions (Open-Meteo, which needs no key)
- [x] Find a city by name
- [x] Temperature unit: by region, Celsius, or Fahrenheit
- [x] Offline: the last reading is shown when the network is unavailable
- [x] Weather clock face with three layouts (centred, jumbo temperature, and an hourly strip for the next six hours)
- [x] Pure-black mode applies to the weather clock
- [x] Location saved once from the phone, or by city name

## 7. Photos

- [x] Photo face with one or several pictures
- [x] Slideshow with an interval from 15 seconds to 10 minutes
- [~] Face-aware crop using the platform face detector
- [x] This month's calendar over the photo
- [x] Caption under the photo
- [~] Albums: several pictures are chosen at once from the system picker, which can open Google Photos. Named albums inside the app are not built.
- [x] GIF pictures keep their animation, with a caption. No meme text editor.

## 8. Media, radio and sound

- [x] Now-playing card for apps with media controls (Spotify, YouTube Music, Apple Music, Deezer and others)
- [x] Album art, play and pause, previous and next, progress bar, and a background colour from the album art
- [~] Like: sends a heart rating, which only players that use ratings act on. Shuffle and repeat are not built, because Android does not let other apps change them.
- [-] Synced lyrics (lyrics text is copyrighted, so this needs a licensed source)
- [x] Internet radio: stations you enter, played in turn
- [~] Curated radio list: the list is the one you enter
- [x] Background white, pink and brown noise, rain, ocean waves, fireplace, wind and stream, generated on the phone
- [x] Drifting colour glow behind the sound
- [x] Sound sleep timer that fades out over the last 30 seconds
- [x] Six-position clock overlay with font and outline choices
- [-] Curated YouTube video scenes and custom YouTube links

## 9. Tools

- [x] Pomodoro: 25-minute preset, completion alert (sound, vibrate or both), tap to see when the phase ends, and a count of today's Pomodoros
- [x] Pomodoro history: the last seven days, and an optional automatic 25-minute work and 5-minute break cycle
- [x] Timer with presets, and the same alert choice
- [x] Stopwatch with tenths and laps
- [x] Countdown to a date
- [x] Calendar: next event on the clock, and the agenda in Tools
- [x] Striking calendar face
- [x] To-do list: add, tick off, clear finished items
- [x] Protocols: your list, shown in Tools and rotating in a corner
- [x] Tally counters: four, with haptic taps and reset
- [x] Sober counter: days, hours, minutes, money saved, the next milestone, and a dimmer screen at night
- [x] Calculator
- [x] Notifications list, limited by focus apps
- [x] Simon memory game

## Not built, and why

- Live edit mode for every face, and the landscape preview that shrinks for the edit drawer. The Panels editor covers the panel layouts only.
- Hiding the notification required for the charging launch. Android doesn't allow this for a full-screen launch.
- Synced lyrics. Lyrics text is copyrighted and needs a licensed source.
- Shuffle and repeat on the player card. Android's media controls API does not let other apps set them.
- Ring-style progress for the year, month and week; the Starship console art; the Pixel-style big crop; per-face colour and seconds settings; drag-and-drop reordering of panel items.
- Named albums inside the app and Google Photos albums, which need a Google account API.
- Curated YouTube scenes and custom YouTube links, which need a video service.

## Implementation notes

- Weather and city search: Open-Meteo, no key. The hourly strip uses the same service.
- Charging launch: a manifest receiver on power connected, with a full-screen-intent notification. Auto-close when upright uses the device orientation sensor, which keeps working while the screen is locked to landscape.
- Screensaver: a DreamService that uses the same clock as the activity.
- Media and notifications: a NotificationListenerService with MediaSessionManager.
- Panels: a PanelsView that hosts built-in text items and home-screen widgets through one AppWidgetHost. Widgets are bound through the system bind and configure screens.
- ISS: wheretheiss.at, which needs no key, read at most once a minute and cached.
- Sound: generated on the phone with AudioTrack. It is not a foreground service, so strict battery settings can stop it.
- Spaces: each space keeps its own settings file.
- Burn-in: a shifting layout, a one-pixel checkerboard overlay, and a black sleep screen.
