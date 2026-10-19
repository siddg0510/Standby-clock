package com.example.standbyclock

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.Rating
import android.view.KeyEvent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Enabled by the user under Notification access. Android only lets an enabled notification
 * listener read the active media sessions, so this service is the way in.
 */
class MusicListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        MusicSession.attach(this)
    }

    override fun onListenerDisconnected() {
        MusicSession.detach()
    }

    /** Keeps a short list of notifications for the Tools screen. With focus apps set, only those apps are kept. */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val allowed = Settings(this).focusPackages()
        if (allowed.isNotEmpty() && sbn.packageName !in allowed) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        NotificationsStack.add("${sbn.packageName}: $title $text".trim())
    }
}

/**
 * Tracks the active media player (Spotify, YouTube Music, Apple Music and others that publish a
 * media session) and sends play, pause, previous and next commands to it.
 */
object MusicSession {

    private const val TAG = "StandbyMusic"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()
    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        choose(controllers)
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = notifyListeners()
        override fun onPlaybackStateChanged(state: PlaybackState?) = notifyListeners()
    }

    fun attach(service: NotificationListenerService) {
        val sessions = service.getSystemService(MediaSessionManager::class.java) ?: return
        val listener = ComponentName(service, MusicListenerService::class.java)
        manager = sessions
        component = listener
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener)
            choose(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access is not granted: ${e.message}")
        }
    }

    fun detach() {
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove session listener: ${e.message}")
        }
        manager = null
        component = null
        select(null)
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun title(): String? = controller?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

    fun artist(): String? = controller?.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

    /** Album art, if the player publishes it. */
    fun albumArt(): Bitmap? =
        controller?.metadata?.let { it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART) }

    fun isPlaying(): Boolean = controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    /** Playback progress from 0 to 1, extrapolated from the last position update while playing. */
    fun progress(): Float {
        val session = controller ?: return 0f
        val duration = session.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val state = session.playbackState ?: return 0f
        if (duration <= 0L) return 0f
        val elapsed = if (state.state == PlaybackState.STATE_PLAYING) {
            (System.currentTimeMillis() - state.lastPositionUpdateTime) * state.playbackSpeed
        } else {
            0.0
        }
        val position = state.position + elapsed.toLong()
        return (position.toFloat() / duration).coerceIn(0f, 1f)
    }

    /** Name of the app that is playing, such as "Spotify". */
    fun sourceLabel(context: Context): String? {
        val pkg = controller?.packageName ?: return null
        return try {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /**
     * Play or pause. With no media session, the same media key the headphone button sends is dispatched
     * to the system, which works without notification access. This idea is from Standby Pro's native bridge.
     */
    fun playPause() {
        val session = controller
        if (session == null) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        if (isPlaying()) session.transportControls.pause() else session.transportControls.play()
    }

    fun previous() {
        val session = controller
        if (session == null) sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) else session.transportControls.skipToPrevious()
    }

    fun next() {
        val session = controller
        if (session == null) sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) else session.transportControls.skipToNext()
    }

    /** Lets the key fallback reach the system. Called once from the clock, which runs without notification access. */
    fun useContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun sendMediaKey(code: Int) {
        val audio = appContext?.getSystemService(AudioManager::class.java) ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Sends a heart to the player. Players that do not use ratings ignore it. */
    fun like() {
        controller?.transportControls?.setRating(Rating.newHeartRating(true))
    }


    /** Prefers a session that is playing, otherwise the first one available. */
    private fun choose(sessions: List<MediaController>?) {
        val playing = sessions?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        select(playing ?: sessions?.firstOrNull())
    }

    private fun select(next: MediaController?) {
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback, mainHandler)
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }
}
