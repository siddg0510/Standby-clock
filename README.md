# Standby Clock (Samsung S24 Ultra)

A StandBy-style clock for a phone stand. It is free to use, and every feature is included. Built from public Android APIs only; it reuses no code from any other app.

**The full feature checklist, with what is built and what is not, is in [FEATURES.md](FEATURES.md).** This README covers setup and testing.

## What's in the app

- **Clock faces (12 plus photo):** digital, analog, segmented LCD, word clock, neon, big crop, retroflip, solar, radial, matrix rain, dementia (large text), charging (battery), duo (clock plus cards), and photo. Tap the clock to cycle.
- **Corner complications:** top-left and top-right, each one of battery, date, greeting, next alarm, or year, month or week progress.
- **Now playing:** a Spotify-style card for any app that shows media controls. Needs notification access.
- **Weather:** Open-Meteo, no key needed. Uses a saved location.
- **Calendar:** the next event in the next 12 hours, and the next alarm.
- **Radio:** a direct stream URL you enter, from the Radio button on the player card.
- **Quick launch:** on charging, or on charging in landscape. Optional conditions: wireless only, screen off, lock screen, a time window, and a Wi-Fi list. Closes when you unplug.
- **Screensaver:** the same clock as a DreamService, with its own face setting.
- **Night mode:** a schedule, a dark-room light sensor, a tint colour and strength, and separate day and night brightness.
- **Burn-in protection:** a shifting layout, plus a checkerboard overlay that flips every minute with a dimming slider.
- **Tools:** timer and stopwatch; tally counters with haptic taps; a sober counter with money saved.
- **Orientation:** landscape, portrait or auto-rotate.

## Using it

- **Tap** the clock to cycle faces.
- **Hold** the clock to open settings. Settings has no launcher icon of its own.
- Settings opens the tools, the timer and the stopwatch.

## Permissions and one-time setup on the S24 Ultra

- **Notifications:** asked when you turn on quick launch.
- **Full-screen alerts (Android 14 and newer):** needed for the charging launch. Tap "Allow full-screen alerts" in settings and turn it on for Standby Clock.
- **Location:** for weather, and for reading the Wi-Fi network name when the Wi-Fi list is used.
- **Music controls:** tap "Allow music controls" and turn on Standby Clock under notification access.
- **Calendar:** tap "Allow calendar events on the clock" if you want the next event shown.
- **Home-screen widget:** long-press an empty spot on the home screen, choose Widgets, then pick Standby Clock.
- **Screen saver:** Settings > Display > Screen saver (use search if the path differs), pick "Standby Clock", and enable starting while charging.

## Why quick launch might not open the clock

1. **Notifications allowed.** Pick a quick-launch option and accept the prompt.
2. **Full-screen alerts allowed** (Android 14 and newer). Without it the notification appears but the clock doesn't open by itself; tapping it still opens the clock.
3. **Conditions met.** If you set wireless only, a time window, the screen-off or lock-screen condition, or a Wi-Fi list, the launch is skipped when one of them isn't met. The log says which one (`adb logcat -s StandbyCharging`).
4. **Not restricted by Samsung.** Set Standby Clock to Unrestricted under the app's Battery settings, and keep it out of Sleeping or Deep sleeping apps.

Use "Test the charging launch now" in settings to check items 1 and 2 without unplugging.

## Build

The project includes the Gradle wrapper. You need JDK 17 and the Android SDK (platform 35, build-tools). Then, from this folder:

```bash
gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the S24 Ultra

1. Settings > About phone > Software information: tap Build number seven times to enable Developer options.
2. Settings > Developer options: turn on USB debugging.
3. Connect the phone and run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and install it from Files.

## Not verified on hardware

The build compiles, but nothing has run on a phone yet. Check these on the device:

- Each face's layout, especially Duo, the portrait orientation, and the Matrix and charging faces.
- The corner complications and the checkerboard (on OLED, look for any shimmer or visible pattern).
- The player card with a real media session, including album art and progress.
- The quick-launch conditions, the charging launch over the lock screen, and Samsung's battery settings.
- The light-sensor night mode, the screensaver trigger, weather, the widget, and the tools.
