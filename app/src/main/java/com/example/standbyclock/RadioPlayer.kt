package com.example.standbyclock

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/** Plays one internet radio stream at a time. Stops when the clock stops, so it never plays unattended. */
object RadioPlayer {

    private const val TAG = "StandbyRadio"
    private var player: MediaPlayer? = null

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    fun play(url: String, onError: () -> Unit) {
        stop()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Radio error $what/$extra")
                    stop()
                    onError()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the radio stream: ${e.message}")
            stop()
            onError()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
