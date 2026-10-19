package com.example.standbyclock

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.text.format.DateFormat
import android.util.Log
import android.view.Display
import android.view.Surface
import java.util.Calendar

/**
 * Opens the clock when the charger is connected, if every condition the user set is met:
 * the quick-launch mode, wireless-only, a time window, screen off, lock screen, and Wi-Fi network.
 */
class ChargingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return
        val settings = Settings(context)
        val mode = settings.launchMode
        Log.i(TAG, "Power connected; quick launch mode=$mode")
        if (mode == Settings.LAUNCH_OFF) return

        val modeAllows = when (mode) {
            Settings.LAUNCH_LANDSCAPE -> isLandscape(context)
            else -> true
        }
        val reasons = listOfNotNull(
            "not landscape".takeIf { !modeAllows },
            "not wireless".takeIf { settings.launchWirelessOnly && !isWireless(context) },
            "outside time window".takeIf {
                !Settings.inWindow(settings.launchStartHour, settings.launchEndHour, currentHour())
            },
            "screen on".takeIf { settings.launchScreenOff && isScreenOn(context) },
            "not on lock screen".takeIf { settings.launchLockedOnly && !isLocked(context) },
            "wrong Wi-Fi".takeIf { !wifiAllowed(context, settings.wifiNetworks()) },
        )
        if (reasons.isNotEmpty()) {
            Log.i(TAG, "Not launching: ${reasons.joinToString()}")
            return
        }
        ChargingLauncher.show(context)
    }

    /** True when the phone is held sideways. Reads the default display's rotation. */
    private fun isLandscape(context: Context): Boolean {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return false
        val rotation = display.rotation
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    private fun isWireless(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    private fun isScreenOn(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun isLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** Empty list means any network. Otherwise the current Wi-Fi name must be on the list. */
    private fun wifiAllowed(context: Context, networks: List<String>): Boolean {
        if (networks.isEmpty()) return true
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val ssid = wifi.connectionInfo?.ssid?.removeSurrounding("\"") ?: return false
        return networks.any { it.equals(ssid, ignoreCase = true) }
    }

    private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    private companion object {
        const val TAG = "StandbyCharging"
    }
}

/**
 * Posts a high-priority notification whose full-screen intent opens the clock. Android only
 * lets a full-screen intent bring up an activity from the background when the app has
 * full-screen alert access (Android 14+) and notifications are allowed. Otherwise the
 * notification still appears, and tapping it opens the clock.
 */
object ChargingLauncher {

    private const val TAG = "StandbyCharging"
    private const val CHANNEL_ID = "charging"
    private const val NOTIFICATION_ID = 1

    /** Posts the charging notification. Returns false when notifications are blocked. */
    fun show(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!notificationsAllowed(context)) {
            Log.w(TAG, "Notifications are blocked; the charging clock was not shown")
            return false
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Charging clock", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_FROM_CHARGING, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Standby Clock")
            .setContentText("Charging: tap to open the clock")
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        Log.i(TAG, "Charging notification posted; full-screen allowed=${fullScreenAllowed(context)}")
        return true
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** True when the app may post notifications (runtime permission on Android 13+, and the system switch). */
    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return runtimeGranted && manager.areNotificationsEnabled()
    }

    /** True when full-screen alerts are allowed. Always true before Android 14, where no separate grant exists. */
    fun fullScreenAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }
}
